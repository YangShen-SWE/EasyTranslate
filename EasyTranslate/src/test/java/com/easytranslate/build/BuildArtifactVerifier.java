package com.easytranslate.build;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import java.util.jar.JarFile;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

/** Offline post-package gate, never starts the app or creates an installer. */
public final class BuildArtifactVerifier {
  private BuildArtifactVerifier() {}
  public static void verify(Path jar, String group, String artifact, String version) throws IOException {
    verifyCoordinates(jar, group, artifact, version);
    try (var archive = new JarFile(jar.toFile());
         var in = archive.getInputStream(archive.getJarEntry("module-info.class"))) {
      var descriptor = java.lang.module.ModuleDescriptor.read(in);
      if (!version.equals(descriptor.rawVersion().orElse("")))
        throw new IOException("Module descriptor version mismatch");
    }
  }

  private static void verifyCoordinates(Path jar, String group, String artifact, String version) throws IOException {
    if (!jar.getFileName().toString().equals(artifact + "-" + version + ".jar"))
      throw new IOException("Artifact filename/version mismatch");
    try (var archive = new JarFile(jar.toFile())) {
      String propertiesPath = "META-INF/maven/" + group + "/" + artifact + "/pom.properties";
      var entry = archive.getJarEntry(propertiesPath);
      if (entry == null) throw new IOException("Missing Maven metadata");
      var properties = new Properties();
      try (InputStream in = archive.getInputStream(entry)) { properties.load(in); }
      if (!group.equals(properties.getProperty("groupId"))
          || !artifact.equals(properties.getProperty("artifactId"))
          || !version.equals(properties.getProperty("version")))
        throw new IOException("Embedded Maven coordinates mismatch");
      var manifest = archive.getManifest();
      if (manifest == null || !version.equals(manifest.getMainAttributes().getValue("Implementation-Version")))
        throw new IOException("Manifest version mismatch");
      for (String required : new String[]{"module-info.class", "com/easytranslate/EasyTranslateApplication.class",
          "com/easytranslate/view/floating-view.fxml", "com/easytranslate/view/floating-window.css"}) {
        if (archive.getJarEntry(required) == null) throw new IOException("Missing required entry: " + required);
      }
    }
  }

  public static void stampModuleVersion(Path jar, String group, String artifact, String version) throws IOException {
    verifyCoordinates(jar, group, artifact, version);
    int result = java.util.spi.ToolProvider.findFirst("jar").orElseThrow().run(
        System.out, System.err, "--update", "--file", jar.toString(), "--module-version", version);
    if (result != 0) throw new IOException("JDK jar module stamping failed: " + result);
    verify(jar, group, artifact, version);
  }

  public static void main(String[] args) throws Exception {
    boolean stamp = args.length == 1 && args[0].equals("--stamp-module-version");
    if (args.length != 0 && !stamp) throw new IllegalArgumentException("Only --stamp-module-version is allowed; version derives from pom");
    if (Runtime.version().feature() != 25) throw new IllegalStateException("JDK25 required");
    var factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    var root = factory.newDocumentBuilder().parse(Path.of("pom.xml").toFile()).getDocumentElement();
    String group = null, artifact = null, version = null;
    for (var child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
      switch (child.getNodeName()) {
        case "groupId" -> group = child.getTextContent().strip();
        case "artifactId" -> artifact = child.getTextContent().strip();
        case "version" -> version = child.getTextContent().strip();
      }
    }
    if (group == null || artifact == null || version == null || !version.matches("\\d+\\.\\d+\\.\\d+"))
      throw new IllegalStateException("Expected explicit project coordinates and numeric patch version");
    Path jar = Path.of("target", artifact + "-" + version + ".jar");
    if (stamp) {
      // Incremental Maven compilation may retain an older module-info.class on a version-only bump.
      // Never repair coordinates/manifest or silently rename another artifact.
      stampModuleVersion(jar, group, artifact, version);
    }
    verify(jar, group, artifact, version);
    var digest = MessageDigest.getInstance("SHA-256");
    try (var input = Files.newInputStream(jar)) {
      byte[] buffer = new byte[8192]; int count;
      while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
    }
    System.out.println("ARTIFACT_VERIFIED: " + jar + " version=" + version);
    System.out.println("SHA256: " + HexFormat.of().formatHex(digest.digest()));
  }
}
