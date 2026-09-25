module com.easytranslate {
  requires javafx.controls;
  requires javafx.fxml;
  requires java.prefs;
  requires com.sun.jna;
  requires com.sun.jna.platform;
  exports com.easytranslate;
  opens com.easytranslate.view to javafx.fxml;
}