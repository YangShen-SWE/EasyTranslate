package com.easytranslate.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

/** Windows-only local app-image build. Never creates an installer, runs the GUI or deletes old output. */
public final class AppImageBuilder {
  private AppImageBuilder() {}
  static List<Path> runtimeLibraries(String classpath) throws IOException {
    var result = new ArrayList<Path>();
    for (String value : classpath.strip().split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
      Path jar = Path.of(value); String name = jar.getFileName().toString();
      if (name.startsWith("javafx-")) continue; // Only JDK system JavaFX goes into this diagnostic image.
      if (!name.matches("(jackson-(annotations|core|databind)-[^/]+|jna(-platform)?-jpms-[^/]+)\\.jar"))
        throw new IOException("Unexpected runtime dependency: " + name);
      if (!Files.isRegularFile(jar)) throw new IOException("Missing runtime dependency");
      if (result.stream().anyMatch(p -> p.getFileName().equals(jar.getFileName()))) throw new IOException("Duplicate runtime dependency");
      result.add(jar);
    }
    if (result.size() != 5) throw new IOException("Expected three Jackson and two JNA jars");
    return List.copyOf(result);
  }
  private static void execute(List<String> command, Path output) throws Exception {
    var builder = new ProcessBuilder(command).redirectErrorStream(true);
    if (output == null) builder.inheritIO(); else builder.redirectOutput(output.toFile());
    Process process = builder.start();
    if (!process.waitFor(Duration.ofMinutes(3).toMillis(), TimeUnit.MILLISECONDS)) {
      process.destroyForcibly(); process.waitFor(); throw new IOException("Build command timed out");
    }
    if (process.exitValue() != 0) throw new IOException("Build command failed: " + process.exitValue());
  }
  public static void main(String[] args) throws Exception {
    if (args.length != 2 || !System.getProperty("os.name").startsWith("Windows"))
      throw new IllegalArgumentException("Windows: AppImageBuilder <full-JDK-home> <runtime-classpath-file>");
    Path jdk = Path.of(args[0]).toAbsolutePath();
    if (!jdk.equals(Path.of(System.getProperty("java.home")).toAbsolutePath())) throw new IOException("Builder must run on the selected JDK");
    if (!Runtime.version().toString().startsWith("25.0.4")) throw new IOException("Validated full JDK25.0.4 required");
    if (!ModuleLayer.boot().findModule("javafx.controls").orElseThrow().getDescriptor().rawVersion().orElse("").equals("25.0.4"))
      throw new IOException("Expected system JavaFX25.0.4");
    var factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    var root = factory.newDocumentBuilder().parse(Path.of("pom.xml").toFile()).getDocumentElement();
    String version = null;
    for (var n=root.getFirstChild(); n!=null; n=n.getNextSibling()) if (n.getNodeName().equals("version")) version=n.getTextContent().strip();
    if (version == null || !version.matches("\\d+\\.\\d+\\.\\d+")) throw new IOException("Invalid project version");
    Path jar = Path.of("target", "EasyTranslate-" + version + ".jar");
    BuildArtifactVerifier.verify(jar, "com", "EasyTranslate", version);
    var dependencies = runtimeLibraries(Files.readString(Path.of(args[1])));
    Path work = Files.createDirectory(Path.of("target", "app-image-" + version + "-" + UUID.randomUUID()));
    Path input = Files.createDirectory(work.resolve("input"));
    Files.copy(jar, input.resolve(jar.getFileName()));
    for (var dependency : dependencies) Files.copy(dependency, input.resolve(dependency.getFileName()));
    // Self-contained native input: no default JNA temp extraction or global permission changes.
    Path nativeDirectory = Files.createDirectory(input.resolve("native"));
    Path jna = dependencies.stream().filter(p -> p.getFileName().toString().startsWith("jna-jpms-")).findFirst().orElseThrow();
    try (var archive = new java.util.jar.JarFile(jna.toFile())) {
      var entry = archive.getJarEntry("com/sun/jna/win32-x86-64/jnidispatch.dll");
      if (entry == null) throw new IOException("Missing JNA Windows x64 native resource");
      try (var in = archive.getInputStream(entry)) { Files.copy(in, nativeDirectory.resolve("jnidispatch.dll")); }
    }
    Path runtime = work.resolve("runtime");
    execute(List.of(jdk.resolve("bin/jlink.exe").toString(), "--module-path", jdk.resolve("jmods").toString(),
        "--add-modules", "javafx.controls,javafx.fxml,java.net.http,java.prefs,java.xml,java.logging,jdk.unsupported,jdk.crypto.ec,jdk.localedata",
        "--strip-debug", "--no-header-files", "--no-man-pages", "--output", runtime.toString()), null);
    Path destination = work.resolve("image");
    execute(List.of(jdk.resolve("bin/jpackage.exe").toString(), "--type", "app-image", "--name", "EasyTranslate",
        "--input", input.toString(), "--main-jar", jar.getFileName().toString(), "--main-class", "com.easytranslate.DesktopLauncher",
        "--app-version", version, "--runtime-image", runtime.toString(), "--dest", destination.toString(), "--win-console",
        "--java-options", "--add-modules=javafx.controls,javafx.fxml",
        "--java-options", "--enable-native-access=javafx.graphics,ALL-UNNAMED",
        "--java-options", "-Djna.boot.library.path=$APPDIR/native",
        "--java-options", "-Djna.nounpack=true", "--java-options", "-Djna.nosys=true"), null);
    Path image = destination.resolve("EasyTranslate");
    AppImageVerifier.verify(image, version);
    if (Files.mismatch(jar, image.resolve("app").resolve(jar.getFileName())) != -1
        || Files.mismatch(nativeDirectory.resolve("jnidispatch.dll"), image.resolve("app/native/jnidispatch.dll")) != -1)
      throw new IOException("Packaged input byte mismatch");
    Path diagnostic = work.resolve("diagnostic.txt");
    execute(List.of(image.resolve("EasyTranslate.exe").toAbsolutePath().toString(), "--diagnose"), diagnostic);
    String evidence = Files.readString(diagnostic);
    System.out.print(evidence);
    AppImageVerifier.verifyDiagnostic(evidence, version);
    Files.writeString(work.resolve("verification.txt"), "APP_IMAGE_VERIFIED version=" + version + "\n" + evidence);
    System.out.println("APP_IMAGE_VERIFIED: " + image.toAbsolutePath());
  }
}
