module com.easytranslate {
  requires javafx.controls;
  requires javafx.fxml;
  requires java.prefs;
  requires java.xml;
  requires com.sun.jna;
  requires com.sun.jna.platform;
  requires java.net.http;
  requires com.fasterxml.jackson.databind;
  exports com.easytranslate;
  opens com.easytranslate.view to javafx.fxml;
}
