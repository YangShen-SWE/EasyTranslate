package com.easytranslate.view;

import com.easytranslate.config.ApiKeyStore;
import com.easytranslate.model.Translation;
import com.easytranslate.usecase.TranslationOperation;
import com.easytranslate.viewmodel.FloatingViewModel;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

/** Real FX scenes/skins/focus with synthetic local KeyEvents; no native hooks, selection or clipboard. */
public class FloatingKeyboardSmokeTest extends Application {
  private static Throwable failure;
  private Preferences preferences;
  private Stage main, source;
  private Parent root;
  private TextField sourceField;
  private FloatingViewModel model;
  private final List<String> copies = new ArrayList<>();
  private int checks;
  private final String translated = "用于检查键盘滚动及保留旧译文。".repeat(80);

  @Override public void start(Stage stage) {
    Platform.setImplicitExit(false);
    try {
      main = stage;
      preferences = Preferences.userRoot().node("com/easytranslate/keyboard-smoke-" + ProcessHandle.current().pid());
      preferences.putBoolean("alwaysOnTop", false);
      preferences.putBoolean("motionEnabled", false);
      preferences.putBoolean("decorations", false);
      preferences.put("windowSize", "SMALL");
      var loader = new FXMLLoader(getClass().getResource("floating-view.fxml"));
      loader.setControllerFactory(type -> new FloatingViewController(preferences,
          new ApiKeyStore(preferences), text -> { copies.add(text); return true; }));
      root = loader.load();
      FloatingViewController controller = loader.getController();
      model = new FloatingViewModel();
      controller.setViewModel(model);
      main.initStyle(StageStyle.TRANSPARENT);
      main.setTitle("EasyTranslate · Keyboard Check");
      var scene = new Scene(root, Color.TRANSPARENT);
      scene.getStylesheets().add(getClass().getResource("floating-window.css").toExternalForm());
      main.setScene(scene);
      controller.configureStage(main);
      main.setX(40); main.setY(40); main.show(); main.requestFocus();
      later(this::emptyTraversal);
    } catch (Throwable error) { fail(error); }
  }

  private void emptyTraversal() {
    for (String id : List.of("pinButton", "closeButton", "copyButton", "sourceScroll", "translatedScroll", "vocabularyButton", "settingsButton")) {
      check(node(id).isFocusTraversable(), id + " allows keyboard traversal");
    }
    check(((Button) node("copyButton")).isDisabled(), "empty copy disabled");
    node("pinButton").requestFocus();
    key(KeyCode.C, false, true); check(copies.isEmpty(), "empty Ctrl+C does not write clipboard boundary");
    tab(false); focused("closeButton");
    tab(false); focused("sourceScroll");
    tab(false); focused("translatedScroll"); // Disabled copy is naturally skipped by JavaFX.
    tab(false); focused("vocabularyButton");
    tab(false); focused("settingsButton");
    tab(true); focused("vocabularyButton");
    sourceField = new TextField("Synthetic source text only");
    source = new Stage();
    source.setTitle("Synthetic source focus sentinel");
    source.setScene(new Scene(new VBox(new Label("No capture or real application content"), sourceField), 320, 120));
    source.setX(650); source.setY(80); source.show(); source.requestFocus(); sourceField.requestFocus();
    later(this::passiveUpdate);
  }

  private void passiveUpdate() {
    check(source.isFocused(), "synthetic source stage owns focus before publication");
    check(source.getScene().getFocusOwner() == sourceField, "source input focused before publication");
    // TextField's first focus selects text itself; establish the sentinel selection after activation.
    sourceField.selectRange(0, 9);
    check(sourceField.getSelectedText().equals("Synthetic"), "sentinel selection established before publication");
    model.showOperation(TranslationOperation.READING);
    model.showOperation(TranslationOperation.TRANSLATING);
    model.showTranslation(new Translation("Synthetic text for the scroll check. ".repeat(20), translated));
    later(this::passiveResult);
  }

  private void passiveResult() {
    check(source.isFocused() && !main.isFocused(), "automatic result and resize leave source stage active");
    check(source.getScene().getFocusOwner() == sourceField, "source focus owner retained");
    check(sourceField.getText().equals("Synthetic source text only"), "source text unchanged");
    check(sourceField.getSelectedText().equals("Synthetic"), "source selection unchanged");
    check(((Label) node("translatedLabel")).getText().equals(translated), "automatic translation rendered");
    check(Math.abs(main.getWidth() - 420) < 2, "small window remains bounded");
    check(main.getHeight() < 500, "long text height remains bounded");
    model.showFailure("Synthetic failure advice");
    later(this::passiveFailure);
  }

  private void passiveFailure() {
    check(source.isFocused(), "automatic failure does not activate main window");
    check(((Label) node("statusLabel")).getText().contains("上次结果"), "retained result remains labelled");
    check(Window.getWindows().stream().filter(w -> w instanceof Stage s && s.getTitle().equals("EasyTranslate · 设置")).count() == 0,
        "automatic update never opens settings");
    main.requestFocus(); node("sourceScroll").requestFocus();
    later(this::activeActions);
  }

  private void activeActions() {
    check(main.isFocused(), "explicit activation enters main window");
    focused("sourceScroll");
    tab(false); focused("copyButton");
    key(KeyCode.SPACE, false, false);
    check(copies.size() == 1 && copies.getLast().equals(translated), "focused copy button Space writes exact retained text to fake boundary");
    key(KeyCode.C, false, true);
    check(copies.size() == 2 && copies.getLast().equals(translated), "local Ctrl+C copies translated text");
    check(((Label) node("statusLabel")).getText().contains("上次结果"), "copy feedback does not hide retained state");
    key(KeyCode.F6, false, false); focused("translatedScroll");
    ScrollPane scroll = (ScrollPane) node("translatedScroll");
    check(scroll.getContent().getLayoutBounds().getHeight() > scroll.getViewportBounds().getHeight(), "translation has real scroll overflow");
    check(scroll.getVvalue() == 0, "translation initially at top");
    key(KeyCode.PAGE_DOWN, false, false);
    later(this::scrollAndSettings);
  }

  private void scrollAndSettings() {
    check(((ScrollPane) node("translatedScroll")).getVvalue() > 0, "PageDown scrolls focused translation");
    key(KeyCode.F6, true, false); focused("sourceScroll");
    key(KeyCode.COMMA, false, true);
    later(this::settingsActions);
  }

  private void settingsActions() {
    Stage settings = settings();
    check(settings != null && settings.isShowing(), "Ctrl+comma opens settings");
    check(settings.isFocused(), "explicit settings action activates settings only");
    CheckBox toggle = (CheckBox) settings.getScene().lookup("#onlineTranslationToggle");
    toggle.requestFocus();
    key(settings.getScene(), KeyCode.TAB, false, false);
    check(settings.getScene().getFocusOwner() == settings.getScene().lookup("#rejectOnlineTranslationButton"), "settings Tab reaches refusal button");
    key(settings.getScene(), KeyCode.TAB, true, false);
    check(settings.getScene().getFocusOwner() == toggle, "settings Shift+Tab reverses traversal");
    ScrollPane scroll = (ScrollPane) settings.getScene().lookup("#settingsScroll");
    scroll.requestFocus(); scroll.setVvalue(0);
    key(settings.getScene(), KeyCode.PAGE_DOWN, false, false);
    later(this::closeSettings);
  }

  private void closeSettings() {
    Stage settings = settings();
    check(((ScrollPane) settings.getScene().lookup("#settingsScroll")).getVvalue() > 0, "settings keyboard PageDown reaches lower controls");
    key(settings.getScene(), KeyCode.ESCAPE, false, false);
    check(!settings.isShowing() && main.isShowing(), "Escape closes only current utility");
    later(this::returnAndHideSource);
  }

  private void returnAndHideSource() {
    check(main.isFocused(), "explicit settings close returns main focus"); focused("settingsButton");
    key(KeyCode.COMMA, false, true);
    Stage settings = settings();
    check(settings.isShowing(), "settings reusable after keyboard close");
    ((CheckBox) settings.getScene().lookup("#showSourceToggle")).fire();
    key(settings.getScene(), KeyCode.ESCAPE, false, false);
    later(this::hiddenSource);
  }

  private void hiddenSource() {
    check(!node("sourceColumn").isVisible() && !node("sourceColumn").isManaged(), "hidden source removed from layout");
    key(KeyCode.F6, true, false); focused("translatedScroll");
    node("closeButton").requestFocus(); tab(false); focused("copyButton");
    // Utility Escape must not leave a dead main-window shortcut or close the wrong window.
    key(KeyCode.ESCAPE, false, false);
    check(!main.isShowing(), "Escape closes main window");
    System.out.println("KEYBOARD_SMOKE_PASSED: " + checks + " checks");
    cleanup(); Platform.exit();
  }

  private Node node(String id) { return root.lookup("#" + id); }
  private void focused(String id) { check(main.getScene().getFocusOwner() == node(id), "focus owner is " + id); }
  private void tab(boolean shift) { key(KeyCode.TAB, shift, false); }
  private void key(KeyCode code, boolean shift, boolean control) { key(main.getScene(), code, shift, control); }
  private static void key(Scene scene, KeyCode code, boolean shift, boolean control) {
    Node target = scene.getFocusOwner() == null ? scene.getRoot() : scene.getFocusOwner();
    Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, control, false, false));
    Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, shift, control, false, false));
  }
  private Stage settings() {
    return Window.getWindows().stream().filter(w -> w instanceof Stage s && s.getTitle().equals("EasyTranslate · 设置"))
        .map(w -> (Stage) w).findFirst().orElse(null);
  }
  private void check(boolean condition, String message) {
    checks++;
    if (!condition) throw new AssertionError(message);
  }
  private void later(Runnable step) {
    PauseTransition pause = new PauseTransition(Duration.millis(220));
    pause.setOnFinished(event -> { try { step.run(); } catch (Throwable error) { fail(error); } });
    pause.play();
  }
  private void fail(Throwable error) { failure = error; error.printStackTrace(); cleanup(); Platform.exit(); }
  private void cleanup() {
    if (source != null) source.close();
    if (main != null) main.close();
    try { if (preferences != null) { preferences.removeNode(); Preferences.userRoot().flush(); } }
    catch (Exception error) { if (failure == null) failure = error; error.printStackTrace(); }
  }
  public static void main(String[] args) { launch(args); if (failure != null) System.exit(1); }
}
