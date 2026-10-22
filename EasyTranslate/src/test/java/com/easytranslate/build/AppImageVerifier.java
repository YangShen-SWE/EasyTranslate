package com.easytranslate.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Static checks plus evidence from a separately executed bundled launcher. */
public final class AppImageVerifier {
  private AppImageVerifier() {}
  public static void verify(Path image, String version) throws IOException {
    verifyMetadata(image, version);
    BuildArtifactVerifier.verify(image.resolve("app/EasyTranslate-" + version + ".jar"), "com", "EasyTranslate", version);
  }
  static void verifyMetadata(Path image, String version) throws IOException {
    if (!version.matches("\\d+\\.\\d+\\.\\d+")) throw new IOException("Invalid version");
    for (String required : new String[]{"EasyTranslate.exe", "app/EasyTranslate.cfg", "runtime/bin/java.exe", "runtime/release", "app/native/jnidispatch.dll"})
      if (!Files.isRegularFile(image.resolve(required))) throw new IOException("Missing image entry: " + required);
    var cfg = Files.readAllLines(image.resolve("app/EasyTranslate.cfg"));
    if (!cfg.contains("app.mainclass=com.easytranslate.DesktopLauncher")
        || !cfg.contains("java-options=-Djpackage.app-version=" + version)
        || !cfg.contains("java-options=-Djna.boot.library.path=$APPDIR/native")
        || !cfg.contains("java-options=-Djna.nounpack=true")
        || !cfg.contains("java-options=-Djna.nosys=true")) throw new IOException("Launcher metadata mismatch");
    try (var entries = Files.list(image.resolve("app"))) {
      if (entries.anyMatch(p -> p.getFileName().toString().startsWith("javafx-")))
        throw new IOException("Mixed JavaFX sources in image");
    }
    var runtime = new Properties();
    try (var in = Files.newInputStream(image.resolve("runtime/release"))) { runtime.load(in); }
    if (!"\"25.0.4\"".equals(runtime.getProperty("JAVA_VERSION"))) throw new IOException("Unexpected runtime version");
    for (String required : new String[]{"javafx.controls", "javafx.fxml", "java.net.http", "java.prefs", "java.logging", "jdk.unsupported", "jdk.crypto.ec"})
      if (!java.util.Arrays.asList(runtime.getProperty("MODULES", "").replace("\"", "").split(" ")).contains(required))
        throw new IOException("Missing runtime module: " + required);
  }
  public static void verifyDiagnostic(String output, String version) throws IOException {
    if (!output.lines().anyMatch(line -> line.startsWith("INSTALLATION_CHECK_PASSED version=" + version + " java=25.0.4")
        && line.contains(" javafx=25.0.4 jna=5.18.1"))) throw new IOException("Bundled diagnostic mismatch");
    if (output.contains("INSTALLATION_CHECK_FAILED")) throw new IOException("Bundled diagnostic failed");
  }
}
