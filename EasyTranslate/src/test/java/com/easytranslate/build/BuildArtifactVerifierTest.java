package com.easytranslate.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.util.jar.*;
import static org.junit.jupiter.api.Assertions.*;

class BuildArtifactVerifierTest {
  @TempDir Path directory;
  private Path fixture(String filename, String embeddedVersion, String manifestVersion,
                       boolean metadata, boolean resources) throws Exception {
    return fixture(filename, embeddedVersion, manifestVersion, metadata, resources, embeddedVersion);
  }
  private Path fixture(String filename, String embeddedVersion, String manifestVersion,
                       boolean metadata, boolean resources, String moduleVersion) throws Exception {
    var manifest = new Manifest();
    manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
    if (manifestVersion != null) manifest.getMainAttributes().putValue("Implementation-Version", manifestVersion);
    Path path=directory.resolve(filename);
    try(var out=new JarOutputStream(Files.newOutputStream(path),manifest)) {
      if(metadata) {
        out.putNextEntry(new JarEntry("META-INF/maven/com/EasyTranslate/pom.properties"));
        out.write(("groupId=com\nartifactId=EasyTranslate\nversion="+embeddedVersion+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));out.closeEntry();
      }
      if(resources) for(String entry:new String[]{"module-info.class","com/easytranslate/EasyTranslateApplication.class",
          "com/easytranslate/view/floating-view.fxml","com/easytranslate/view/floating-window.css"}) {
        out.putNextEntry(new JarEntry(entry));
        if (entry.equals("module-info.class")) {
          Path source=directory.resolve("module-info.java");
          Files.writeString(source,"module test.fixture {}");
          Path compiled=directory.resolve("compiled");Files.createDirectories(compiled);
          int result=java.util.spi.ToolProvider.findFirst("javac").orElseThrow().run(System.out,System.err,
              "--module-version",moduleVersion,"-d",compiled.toString(),source.toString());
          assertEquals(0,result);out.write(Files.readAllBytes(compiled.resolve("module-info.class")));
        } else out.write(0);
        out.closeEntry();
      }
    }
    return path;
  }
  private void verify(Path jar) throws IOException { BuildArtifactVerifier.verify(jar,"com","EasyTranslate","2.1.11"); }
  @Test void consistentMetadataPasses() throws Exception { verify(fixture("EasyTranslate-2.1.11.jar","2.1.11","2.1.11",true,true)); }
  @Test void staleFilenameFails() throws Exception { var p=fixture("EasyTranslate-2.1.10.jar","2.1.11","2.1.11",true,true);assertThrows(IOException.class,()->verify(p)); }
  @Test void renamedOldJarFails() throws Exception { var p=fixture("EasyTranslate-2.1.11.jar","2.1.10","2.1.11",true,true);assertThrows(IOException.class,()->verify(p)); }
  @Test void wrongManifestFails() throws Exception { var p=fixture("EasyTranslate-2.1.11.jar","2.1.11","2.1.10",true,true);assertThrows(IOException.class,()->verify(p)); }
  @Test void missingManifestVersionFails() throws Exception { var p=fixture("EasyTranslate-2.1.11.jar","2.1.11",null,true,true);assertThrows(IOException.class,()->verify(p)); }
  @Test void missingMetadataFails() throws Exception { var p=fixture("EasyTranslate-2.1.11.jar","2.1.11","2.1.11",false,true);assertThrows(IOException.class,()->verify(p)); }
  @Test void missingRequiredEntriesFails() throws Exception { var p=fixture("EasyTranslate-2.1.11.jar","2.1.11","2.1.11",true,false);assertThrows(IOException.class,()->verify(p)); }
  @Test void missingArtifactFails() { assertThrows(IOException.class,()->verify(directory.resolve("EasyTranslate-2.1.11.jar"))); }
  @Test void corruptArchiveFails() throws Exception { var p=directory.resolve("EasyTranslate-2.1.11.jar");Files.writeString(p,"not a zip");assertThrows(IOException.class,()->verify(p)); }
  @Test void staleModuleVersionFailsEvenWhenOtherMetadataMatches() throws Exception {
    var p=fixture("EasyTranslate-2.1.11.jar","2.1.11","2.1.11",true,true,"2.1.10");
    var failure=assertThrows(IOException.class,()->verify(p));
    assertEquals("Module descriptor version mismatch",failure.getMessage());
  }
  @Test void explicitStampCorrectsOnlyModuleVersionThenVerifies() throws Exception {
    var p=fixture("EasyTranslate-2.1.11.jar","2.1.11","2.1.11",true,true,"2.1.10");
    BuildArtifactVerifier.stampModuleVersion(p,"com","EasyTranslate","2.1.11"); verify(p);
  }
  @Test void stampRefusesRenamedOldArtifactWithoutChangingBytes() throws Exception {
    var p=fixture("EasyTranslate-2.1.11.jar","2.1.10","2.1.10",true,true);
    byte[] before=Files.readAllBytes(p);
    assertThrows(IOException.class,()->BuildArtifactVerifier.stampModuleVersion(p,"com","EasyTranslate","2.1.11"));
    assertArrayEquals(before,Files.readAllBytes(p));
  }
  @Test void wrongCoordinatesFail() throws Exception {
    var p=fixture("EasyTranslate-2.1.11.jar","2.1.11","2.1.11",true,true);
    assertThrows(IOException.class,()->BuildArtifactVerifier.verify(p,"wrong","EasyTranslate","2.1.11"));
  }
}
