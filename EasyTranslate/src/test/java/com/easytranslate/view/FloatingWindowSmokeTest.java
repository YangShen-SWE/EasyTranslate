package com.easytranslate.view;

import com.easytranslate.model.Translation;
import com.easytranslate.viewmodel.FloatingViewModel;
import com.easytranslate.service.hotkey.InputActivityBuffer;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.prefs.Preferences;

/** Standalone JavaFX smoke check, including passive effects hooks. No translation requests or vocabulary writes. */
public class FloatingWindowSmokeTest extends Application {
  private static Throwable failure;
  private Preferences preferences;
  private Parent root;
  private Stage stage;
  private FloatingViewModel model;
  private FloatingViewController controller;
  private int checks;
  private double initialWidth, initialX, initialY;

  @Override public void start(Stage stage) throws Exception {
    this.stage = stage;
    preferences = Preferences.userRoot().node("com/easytranslate/ui-smoke-" + ProcessHandle.current().pid());
    var loader = new FXMLLoader(getClass().getResource("floating-view.fxml"));
    loader.setControllerFactory(type -> new FloatingViewController(preferences));
    root = loader.load();
    controller = loader.getController();
    model = new FloatingViewModel();
    controller.setViewModel(model);
    Scene scene = new Scene(root);
    scene.setFill(Color.TRANSPARENT);
    scene.getStylesheets().add(getClass().getResource("floating-window.css").toExternalForm());
    stage.initStyle(StageStyle.TRANSPARENT);
    stage.setTitle("EasyTranslate · UI Preview");
    stage.setScene(scene);
    controller.configureStage(stage);
    stage.centerOnScreen();
    stage.show();
    later(this::emptyState);
  }

  private void emptyState() {
    check(((Button) root.lookup("#copyButton")).isDisabled(), "empty copy disabled");
    check(((Label) root.lookup("#statusLabel")).getText().equals("等待划词"), "honest empty status");
    model.showTranslation(new Translation("Take it one step at a time.", "一步一步来。"));
    later(this::shortTranslation);
  }

  private void shortTranslation() {
    check(!((Button) root.lookup("#copyButton")).isDisabled(), "copy available for translation");
    check(((Label) root.lookup("#translatedLabel")).getText().equals("一步一步来。"), "Unicode binding");
    check(((Label) root.lookup("#statusLabel")).getText().equals("翻译结果"), "translation status");
    model.showTranslation(new Translation("A long sentence for checking wrapping and scrolling. ".repeat(30),
        "用于检查长译文换行、滚动以及窗口高度上限。".repeat(30)));
    later(this::longTranslation);
  }

  private void longTranslation() {
    ScrollPane scroll = (ScrollPane) root.lookup("#translatedScroll");
    check(stage.getHeight() < 500, "long text bounded height");
    check(((Label) scroll.getContent()).getText().endsWith("窗口高度上限。"), "long text retained");
    check(scroll.getContent().getLayoutBounds().getHeight() > scroll.getViewportBounds().getHeight(), "long text scrollable");
    ((Button) root.lookup("#vocabularyButton")).fire();
    ((Button) root.lookup("#vocabularyButton")).fire();
    check(count("EasyTranslate · 单词本") == 1, "one reusable vocabulary window");
    find("EasyTranslate · 单词本").close();
    ((Button) root.lookup("#settingsButton")).fire();
    later(this::settings);
  }

  private void settings() {
    Stage settings = find("EasyTranslate · 设置");
    check(settings.isShowing(), "settings opens");
    ToggleButton large = (ToggleButton) settings.getScene().lookup("#windowSizeLarge");
    check(large.isSelected(), "large default selected");
    large.fire();
    check(large.isSelected(), "one window size remains selected");
    ((ToggleButton) settings.getScene().lookup("#windowSizeSmall")).fire();
    check(Math.abs(root.prefWidth(0) - 420) < 1, "small width applied");
    check(preferences.get("windowSize", "").equals("SMALL"), "small size saved");
    later(this::smallWindow);
  }

  private void smallWindow() {
    check(Math.abs(stage.getWidth() - 420) < 2, "small stage resized");
    Stage settings = find("EasyTranslate · 设置");
    ((ToggleButton) settings.getScene().lookup("#windowSizeMedium")).fire();
    later(this::mediumWindow);
  }

  private void mediumWindow() {
    check(Math.abs(stage.getWidth() - 500) < 2, "medium stage resized");
    Stage settings = find("EasyTranslate · 设置");
    check(preferences.get("windowSize", "").equals("MEDIUM"), "medium size saved");
    try {
      var loader = new FXMLLoader(getClass().getResource("floating-view.fxml"));
      loader.setControllerFactory(type -> new FloatingViewController(preferences));
      Parent restored = loader.load();
      check(Math.abs(restored.prefWidth(0) - 500) < 1, "window size restored in fresh controller");
    } catch (Exception e) { throw new AssertionError("reload window size", e); }
    ((ToggleButton) settings.getScene().lookup("#windowSizeLarge")).fire();
    later(this::settingsAppearance);
  }

  private void settingsAppearance() {
    Stage settings = find("EasyTranslate · 设置");
    check(Math.abs(stage.getWidth() - 588) < 2, "large stage restored");
    Slider slider = (Slider) settings.getScene().lookup("#fontSizeSlider");
    slider.setValue(24);
    check(preferences.getInt("fontSize", 0) == 24, "font preference saved");
    check(root.lookup("#sourceLabel").getStyle().contains("24px"), "font applied");
    var decorationsToggle = (CheckBox) settings.getScene().lookup("#decorationsToggle");
    decorationsToggle.fire();
    check(!root.lookup("#decorations").isVisible() && !root.lookup("#decorations").isManaged(), "decorations hide and release space");
    check(!preferences.getBoolean("decorations", true), "decorations preference saved");
    decorationsToggle.fire();
    slider.setValue(22);
    ToggleButton pin = (ToggleButton) root.lookup("#pinButton");
    pin.fire();
    check(!stage.isAlwaysOnTop() && !preferences.getBoolean("alwaysOnTop", true), "pin toggle applies and saves");
    pin.fire();
    initialWidth = stage.getWidth(); initialX = stage.getX(); initialY = stage.getY();
    ((CheckBox) settings.getScene().lookup("#showSourceToggle")).fire();
    model.showTranslation(new Translation("Hidden source must not determine window height. ".repeat(100), "只显示这条译文。"));
    later(this::translationOnly);
  }

  private void translationOnly() {
    check(!root.lookup("#sourceColumn").isVisible() && !root.lookup("#sourceColumn").isManaged(), "source removed from layout");
    check(!root.lookup("#columnDivider").isManaged(), "divider removed from layout");
    check(((ScrollPane) root.lookup("#translatedScroll")).getViewportBounds().getWidth() > 450, "translation fills available width");
    check(stage.getHeight() < 350, "hidden long source does not increase height");
    check(Math.abs(stage.getWidth() - initialWidth) < 1, "width stable across toggle");
    check(Math.abs(stage.getX() - initialX) < 1 && Math.abs(stage.getY() - initialY) < 1, "position stable across toggle");
    check(!preferences.getBoolean("showSource", true), "source visibility saved");
    check(((Label) root.lookup("#translatedLabel")).getText().equals("只显示这条译文。"), "new translation binds while source hidden");
    try {
      var loader = new FXMLLoader(getClass().getResource("floating-view.fxml"));
      loader.setControllerFactory(type -> new FloatingViewController(preferences));
      Parent restored = loader.load();
      check(!restored.lookup("#sourceColumn").isManaged(), "source setting restored in fresh controller");
    } catch (Exception e) { throw new AssertionError("reload", e); }
    model.showTranslation(new Translation("Take it one step at a time.", "一步一步来。"));
    ((CheckBox) find("EasyTranslate · 设置").getScene().lookup("#showSourceToggle")).fire();
    later(this::restoredColumns);
  }

  private void restoredColumns() {
    check(root.lookup("#sourceColumn").isVisible() && root.lookup("#columnDivider").isManaged(), "two columns restored");
    check(((Label) root.lookup("#sourceLabel")).getText().equals("Take it one step at a time."), "source text retained");
    check(((ScrollPane) root.lookup("#translatedScroll")).getViewportBounds().getWidth() < 300, "translation returns to half width");
    Stage settings = find("EasyTranslate · 设置");
    settings.close();
    later(() -> {
      check(stage.getHeight() < 350, "short content shrinks again");
      motionChecks();
    });
  }

  private void motionChecks() {
    InputActivityBuffer buffer = new InputActivityBuffer();
    buffer.key('A', true); buffer.key('A', true);
    check(buffer.drain() == InputActivityBuffer.LEFT, "left keyboard mapping");
    buffer.key('A', true);
    check(buffer.drain() == 0, "held key repeat suppressed");
    buffer.key('A', false); buffer.key('J', true);
    check(buffer.drain() == InputActivityBuffer.RIGHT, "right keyboard mapping");
    buffer.key(0x20, true);
    check(buffer.drain() == 3, "space maps both paws");
    for (int i = 0; i < 10000; i++) buffer.mouse(true);
    check(buffer.drain() == 5 && buffer.drain() == 0, "mouse activity bounded and drained");
    double width = stage.getWidth(), height = stage.getHeight();
    controller.previewInput(InputActivityBuffer.LEFT);
    PauseTransition sample = new PauseTransition(Duration.millis(75));
    sample.setOnFinished(event -> {
      try {
        check(Math.abs(root.lookup("#leftPaw").getTranslateY()) > 0.01, "left paw animates");
        check(root.lookup("#rightPaw").getTranslateY() == 0, "other paw remains still");
        check(root.lookup("#catHead").getTranslateY() == 0, "head remains still");
        check(stage.getWidth() == width && stage.getHeight() == height, "motion does not relayout window");
        later(this::motionControls);
      } catch (Throwable error) { failure = error; error.printStackTrace(); Platform.exit(); }
    });
    sample.play();
  }

  private void motionControls() {
    check(root.lookup("#leftPaw").getTranslateY() == 0, "paw returns to rest");
    ((Button) root.lookup("#settingsButton")).fire();
    Stage settings = find("EasyTranslate · 设置");
    CheckBox toggle = (CheckBox) settings.getScene().lookup("#motionToggle");
    toggle.fire();
    check(!preferences.getBoolean("motionEnabled", true), "motion preference saved");
    controller.previewInput(3);
    later(() -> {
      check(root.lookup("#leftPaw").getTranslateY() == 0 && root.lookup("#rightPaw").getTranslateY() == 0, "disabled motion remains still");
      check(root.lookup("#coffeeSteam").getOpacity() == 1, "disabled steam restored");
      toggle.fire();
      controller.enableGlobalInput();
      waitForNative(() -> {
        check(controller.nativeMotionRunning(), "native hooks installed: " + controller.nativeMotionFailure());
        toggle.fire();
        check(!controller.nativeMotionRunning(), "native hooks released on disable");
        toggle.fire();
        waitForNative(this::finishChecks, 15);
      }, 15);
    });
  }

  private void waitForNative(Runnable ready, int remaining) {
    later(() -> {
      if (controller.nativeMotionRunning()) ready.run();
      else if (remaining > 0 && controller.nativeMotionFailure() == null) waitForNative(ready, remaining - 1);
      else throw new AssertionError("Native input startup: " + controller.nativeMotionFailure());
    });
  }

  private void finishChecks() {
    check(controller.nativeMotionRunning(), "native hooks restart after reenable");
    Stage settings = find("EasyTranslate · 设置");
    System.out.println("UI_SMOKE_PASSED: " + checks + " checks");
      if (!getParameters().getRaw().contains("--preview")) stage.close();
      else {
        ((Button) root.lookup("#settingsButton")).fire();
        ((CheckBox) settings.getScene().lookup("#showSourceToggle")).fire();
        settings.setX(stage.getX() + stage.getWidth() + 20);
        settings.setY(stage.getY());
      }
  }

  private static long count(String title) {
    return Window.getWindows().stream().filter(w -> w instanceof Stage s && s.getTitle().equals(title)).count();
  }

  private static Stage find(String title) {
    return (Stage) Window.getWindows().stream().filter(w -> w instanceof Stage s && s.getTitle().equals(title)).findFirst().orElseThrow();
  }

  private void check(boolean condition, String description) {
    if (!condition) throw new AssertionError(description);
    checks++;
  }

  private void later(Runnable action) {
    PauseTransition delay = new PauseTransition(Duration.millis(350));
    delay.setOnFinished(event -> {
      try { action.run(); }
      catch (Throwable error) { failure = error; error.printStackTrace(); Platform.exit(); }
    });
    delay.play();
  }

  @Override public void stop() throws Exception {
    if (preferences != null) preferences.removeNode();
  }

  public static void main(String[] args) {
    launch(args);
    if (failure != null) throw new AssertionError("UI smoke check failed", failure);
  }
}
