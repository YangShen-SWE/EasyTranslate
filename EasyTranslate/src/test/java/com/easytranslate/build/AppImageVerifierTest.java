package com.easytranslate.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AppImageVerifierTest {
  @TempDir Path directory;
  private String cfg() { return "[Application]\napp.mainclass=com.easytranslate.DesktopLauncher\n[JavaOptions]\njava-options=-Djpackage.app-version=2.1.12\njava-options=-Djna.boot.library.path=$APPDIR/native\njava-options=-Djna.nounpack=true\njava-options=-Djna.nosys=true\n"; }
  private String runtime() { return "JAVA_VERSION=\"25.0.4\"\nMODULES=\"java.base javafx.controls javafx.fxml java.net.http java.prefs java.logging jdk.unsupported jdk.crypto.ec\"\n"; }
  private Path fixture() throws IOException {
    for (String file : List.of("EasyTranslate.exe", "app/EasyTranslate.cfg", "runtime/bin/java.exe", "runtime/release", "app/native/jnidispatch.dll")) {
      Path target=directory.resolve(file);Files.createDirectories(target.getParent());Files.writeString(target, "fixture");
    }
    Files.writeString(directory.resolve("app/EasyTranslate.cfg"),cfg());
    Files.writeString(directory.resolve("runtime/release"),runtime());return directory;
  }
  @Test void consistentMetadataPasses() throws Exception { AppImageVerifier.verifyMetadata(fixture(),"2.1.12"); }
  @Test void missingExecutableFails() throws Exception { fixture();Files.delete(directory.resolve("EasyTranslate.exe"));assertThrows(IOException.class,()->AppImageVerifier.verifyMetadata(directory,"2.1.12")); }
  @Test void oldLauncherVersionFails() throws Exception { fixture();Files.writeString(directory.resolve("app/EasyTranslate.cfg"),cfg().replace("2.1.12","2.1.11"));assertThrows(IOException.class,()->AppImageVerifier.verifyMetadata(directory,"2.1.12")); }
  @Test void wrongEntryPointFails() throws Exception { fixture();Files.writeString(directory.resolve("app/EasyTranslate.cfg"),cfg().replace("DesktopLauncher","EasyTranslateApplication"));assertThrows(IOException.class,()->AppImageVerifier.verifyMetadata(directory,"2.1.12")); }
  @Test void missingNativeDllFails() throws Exception { fixture();Files.delete(directory.resolve("app/native/jnidispatch.dll"));assertThrows(IOException.class,()->AppImageVerifier.verifyMetadata(directory,"2.1.12")); }
  @Test void temporaryExtractionFallbackRejected() throws Exception { fixture();Files.writeString(directory.resolve("app/EasyTranslate.cfg"),cfg().replace("-Djna.nounpack=true","-Djna.nounpack=false"));assertThrows(IOException.class,()->AppImageVerifier.verifyMetadata(directory,"2.1.12")); }
  @Test void missingModuleFails() throws Exception { fixture();Files.writeString(directory.resolve("runtime/release"),runtime().replace("jdk.crypto.ec",""));assertThrows(IOException.class,()->AppImageVerifier.verifyMetadata(directory,"2.1.12")); }
  @Test void missingJnaLoggingModuleFails() throws Exception { fixture();Files.writeString(directory.resolve("runtime/release"),runtime().replace("java.logging",""));assertThrows(IOException.class,()->AppImageVerifier.verifyMetadata(directory,"2.1.12")); }
  @Test void wrongRuntimeFails() throws Exception { fixture();Files.writeString(directory.resolve("runtime/release"),runtime().replace("25.0.4","21.0.6"));assertThrows(IOException.class,()->AppImageVerifier.verifyMetadata(directory,"2.1.12")); }
  @Test void mixedJavaFxJarFails() throws Exception { fixture();Files.writeString(directory.resolve("app/javafx-controls-21.0.6.jar"),"fixture");assertThrows(IOException.class,()->AppImageVerifier.verifyMetadata(directory,"2.1.12")); }
  @Test void metadataAloneIsNotWholeImageProof() throws Exception { assertThrows(IOException.class,()->AppImageVerifier.verify(fixture(),"2.1.12")); }
  @Test void diagnosticRequiresMatchingVersionAndRuntime() throws Exception {
    String output="INSTALLATION_CHECK_PASSED version=2.1.12 java=25.0.4+8 javafx=25.0.4 jna=5.18.1\n";
    AppImageVerifier.verifyDiagnostic(output,"2.1.12");
    for (String bad : List.of(output.replace("2.1.12","2.1.11"),output.replace("javafx=25.0.4","javafx=21.0.6"),"",output+"INSTALLATION_CHECK_FAILED"))
      assertThrows(IOException.class,()->AppImageVerifier.verifyDiagnostic(bad,"2.1.12"));
  }
  private String libraries() throws Exception {
    var paths=new java.util.ArrayList<String>();
    for(String name:List.of("jackson-annotations-2.20.jar","jackson-core-2.20.1.jar","jackson-databind-2.20.1.jar","jna-jpms-5.18.1.jar","jna-platform-jpms-5.18.1.jar","javafx-controls-21.0.6-win.jar")) {
      Path file=directory.resolve(name);Files.writeString(file,"fixture");paths.add(file.toString());
    }
    return String.join(java.io.File.pathSeparator,paths);
  }
  @Test void runtimeClasspathExcludesMavenFx() throws Exception { assertEquals(5,AppImageBuilder.runtimeLibraries(libraries()).size()); }
  @Test void testOrUnknownDependencyRejected() throws Exception { String cp=libraries()+java.io.File.pathSeparator+directory.resolve("junit-jupiter-api-5.12.1.jar");assertThrows(IOException.class,()->AppImageBuilder.runtimeLibraries(cp)); }
  @Test void duplicateDependencyRejected() throws Exception { String cp=libraries()+java.io.File.pathSeparator+directory.resolve("jna-jpms-5.18.1.jar");assertThrows(IOException.class,()->AppImageBuilder.runtimeLibraries(cp)); }
  @Test void missingDependencyRejected() throws Exception { libraries();assertThrows(IOException.class,()->AppImageBuilder.runtimeLibraries(directory.resolve("jackson-core-2.20.1.jar").toString())); }
}
