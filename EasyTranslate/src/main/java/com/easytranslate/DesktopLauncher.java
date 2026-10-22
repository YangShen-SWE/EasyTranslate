package com.easytranslate;

import java.io.PrintStream;
import java.util.Objects;

/** Non-JavaFX entry point also exposes a safe, offline installation diagnostic. */
public final class DesktopLauncher {
  private DesktopLauncher() {}

  public static void main(String[] args) {
    int result = run(args, () -> EasyTranslateApplication.main(new String[0]), System.out);
    if (result != 0) System.exit(result);
  }

  static int run(String[] args, Runnable launch, PrintStream out) {
    Objects.requireNonNull(args); Objects.requireNonNull(launch); Objects.requireNonNull(out);
    if (args.length == 0) { launch.run(); return 0; }
    if (args.length != 1 || !args[0].equals("--diagnose")) {
      out.println("Usage: EasyTranslate [--diagnose]"); return 2;
    }
    String phase = "version";
    try {
      String version = DesktopLauncher.class.getPackage().getImplementationVersion();
      if (version == null || !version.matches("\\d+\\.\\d+\\.\\d+"))
        throw new IllegalStateException("Missing packaged version");
      phase = "resources";
      for (String resource : new String[]{"floating-view.fxml", "floating-window.css"}) {
        try (var input = DesktopLauncher.class.getResourceAsStream("/com/easytranslate/view/" + resource)) {
          if (input == null || input.read() < 0) throw new IllegalStateException("Missing packaged resource");
        }
      }
      phase = "dependencies";
      // Load dependency classes, not controllers, Preferences, HTTP clients or Windows hooks.
      for (String name : new String[]{"javafx.fxml.FXMLLoader", "com.fasterxml.jackson.databind.ObjectMapper",
          "com.sun.jna.platform.win32.Crypt32Util"}) Class.forName(name, false, DesktopLauncher.class.getClassLoader());
      phase = "native";
      if (com.sun.jna.Native.POINTER_SIZE != 8) throw new IllegalStateException("Windows x64 runtime required");
      phase = "javafx";
      String fx = ModuleLayer.boot().findModule("javafx.controls").orElseThrow()
          .getDescriptor().rawVersion().orElseThrow();
      out.println("INSTALLATION_CHECK_PASSED version=" + version + " java=" + Runtime.version()
          + " javafx=" + fx + " jna=" + com.sun.jna.Native.VERSION);
      return 0;
    } catch (Exception | LinkageError failure) {
      // No arbitrary exception messages, environment paths or configuration values in output.
      out.println("INSTALLATION_CHECK_FAILED phase=" + phase + " type=" + failure.getClass().getSimpleName());
      return 1;
    }
  }
}
