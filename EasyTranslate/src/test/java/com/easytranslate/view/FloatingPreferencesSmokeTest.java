package com.easytranslate.view;

import com.easytranslate.config.ApiKeyStore;
import com.easytranslate.model.Translation;
import com.easytranslate.usecase.TranslationOperation;
import com.easytranslate.viewmodel.FloatingViewModel;
import java.util.*;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
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

/** Real FX + in-memory preferences + fake key. No personal preferences, API or clipboard. */
public class FloatingPreferencesSmokeTest extends Application {
  private static Throwable failure;
  private FakePreferences prefs = new FakePreferences();
  private Parent root;
  private Stage main, settings;
  private FloatingViewModel model;
  private FloatingViewController controller;
  private int checks;

  @Override public void start(Stage ignored) {
    Platform.setImplicitExit(false);
    try {
      prefs.putBoolean("alwaysOnTop", false); prefs.putBoolean("motionEnabled", false); prefs.flush();
      open(); later(this::changeAndSave);
    } catch (Throwable error) { fail(error); }
  }
  private void open() throws Exception {
    var loader = new FXMLLoader(getClass().getResource("floating-view.fxml"));
    loader.setControllerFactory(type -> new FloatingViewController(prefs, new ApiKeyStore(prefs), text -> true));
    root = loader.load(); controller = loader.getController(); model = new FloatingViewModel(); controller.setViewModel(model);
    main = new Stage(StageStyle.TRANSPARENT); main.setTitle("Preferences Check - synthetic");
    var scene = new Scene(root, Color.TRANSPARENT);
    scene.getStylesheets().add(getClass().getResource("floating-window.css").toExternalForm());
    main.setScene(scene); controller.configureStage(main); main.show();
    ((Button) root.lookup("#settingsButton")).fire();
    settings = (Stage) Window.getWindows().stream().filter(w -> w != main && w.isShowing()).findFirst().orElseThrow();
  }
  private void changeAndSave() {
    model.showTranslation(new Translation("synthetic previous", "上次测试结果")); model.showFailure("先前认证失败");
    toggle("showSourceToggle"); toggle("decorationsToggle");
    toggle("motionToggle"); toggle("motionToggle");
    toggle("alwaysOnTopToggle"); toggle("alwaysOnTopToggle");
    node("fontSizeSlider", Slider.class).setValue(24); node("windowSizeSmall", ToggleButton.class).fire();
    check(node("appearanceSaveStatus", Label.class).getText().contains("已保存"), "appearance persistence acknowledged");
    check(!prefs.getBoolean("showSource", true) && !prefs.getBoolean("decorations", true), "visibility persisted");
    check(!prefs.getBoolean("motionEnabled", true) && !prefs.getBoolean("alwaysOnTop", true), "motion and pin persisted");
    check(prefs.getInt("fontSize", 0) == 24 && prefs.get("windowSize", "").equals("SMALL"), "font and window saved");
    var field = node("apiKeyField", PasswordField.class);
    node("saveApiKeyButton", Button.class).fire(); check(node("apiKeyStatus", Label.class).getText().contains("请输入"), "blank save is rejected");
    field.setText("d09-fake-ui-key"); node("saveApiKeyButton", Button.class).fire();
    check(field.getText().isEmpty(), "successful save clears password field");
    check(node("apiKeyStatus", Label.class).getText().contains("未联网验证"), "save never claims online validation");
    check(model.operationProperty().get() == TranslationOperation.FAILED, "saving key does not turn failure to success");
    check(model.statusTextProperty().get().contains("上次结果"), "saving preserves retained result marker");
    check(!node("deleteApiKeyButton", Button.class).isDisabled(), "saved key can be deleted");
    check(new ApiKeyStore(prefs).load().orElseThrow().equals("d09-fake-ui-key"), "fake DPAPI roundtrip works");
    closeWindows(); prefs = prefs.restart();
    try { open(); later(this::restoredAndFailure); } catch (Throwable error) { fail(error); }
  }
  private void restoredAndFailure() {
    check(!main.isAlwaysOnTop(), "pin restored on recreated stage");
    check(!node("showSourceToggle", CheckBox.class).isSelected(), "source restored");
    check(!node("decorationsToggle", CheckBox.class).isSelected(), "decorations restored");
    check(!node("motionToggle", CheckBox.class).isSelected(), "motion restored");
    check(node("fontSizeSlider", Slider.class).getValue() == 24, "font restored");
    check(node("windowSizeSmall", ToggleButton.class).isSelected(), "window size restored");
    check(node("apiKeyStatus", Label.class).getText().contains("未联网验证"), "reloaded key is local only");
    check(!node("onlineTranslationToggle", CheckBox.class).isSelected(), "key save did not grant online consent");
    check(root.lookup("#sourceColumn").isVisible() == false, "restored visibility reaches main window");
    prefs.backend.failFlush = true;
    toggle("decorationsToggle");
    check(node("decorationsToggle", CheckBox.class).isSelected(), "appearance failure keeps current session choice");
    check(node("appearanceSaveStatus", Label.class).getText().contains("保存失败"), "appearance flush failure is visible");
    var field = node("apiKeyField", PasswordField.class); field.setText("d09-fake-replacement");
    node("saveApiKeyButton", Button.class).fire();
    check(node("apiKeyStatus", Label.class).getText().contains("保存失败"), "key save flush failure visible");
    check(field.getText().isEmpty(), "failed save also clears password field");
    check(new ApiKeyStore(prefs).load().orElseThrow().equals("d09-fake-ui-key"), "failed save restores previous memory key");
    node("deleteApiKeyButton", Button.class).fire();
    check(node("apiKeyStatus", Label.class).getText().contains("删除失败"), "delete flush failure visible");
    check(!node("deleteApiKeyButton", Button.class).isDisabled(), "failed delete remains retryable");
    prefs.backend.failFlush = false;
    toggle("decorationsToggle"); check(node("appearanceSaveStatus", Label.class).getText().contains("已保存"), "appearance retry succeeds");
    node("deleteApiKeyButton", Button.class).fire();
    check(node("apiKeyStatus", Label.class).getText().contains("无法撤回"), "delete warns in-flight text cannot be withdrawn");
    check(node("deleteApiKeyButton", Button.class).isDisabled(), "successful delete disables repeated deletion");
    check(new ApiKeyStore(prefs.restart()).load().isEmpty(), "deletion survives fake backend reconstruction");
    closeWindows(); prefs = prefs.restart();
    prefs.put("fontSize", "NaN"); prefs.put("windowSize", "FUTURE"); prefs.put("showSource", "bad");
    prefs.put("deepseekApiKey", "corrupt-not-base64!");
    try { open(); later(this::corruptRecovery); } catch (Throwable error) { fail(error); }
  }
  private void corruptRecovery() {
    check(node("fontSizeSlider", Slider.class).getValue() == 22, "corrupt font falls back to 22");
    check(node("windowSizeLarge", ToggleButton.class).isSelected(), "corrupt size falls back to large");
    check(node("showSourceToggle", CheckBox.class).isSelected(), "corrupt boolean falls back to true");
    check(node("appearanceSaveStatus", Label.class).getText().contains("默认值"), "corruption fallback reported");
    check(node("apiKeyStatus", Label.class).getText().contains("损坏或无法读取"), "corrupt saved key distinguished from absent");
    check(!node("deleteApiKeyButton", Button.class).isDisabled(), "corrupt key can be deleted");
    check(node("apiKeyField", PasswordField.class).getText().isEmpty(), "corrupt key never populates password field");
    node("deleteApiKeyButton", Button.class).fire();
    check(new ApiKeyStore(prefs).storedState() == ApiKeyStore.StoredState.ABSENT, "corrupt key cleanup works");
    check(!node("onlineTranslationToggle", CheckBox.class).isSelected(), "recovery never grants consent");
    System.out.println("PREFERENCES_SMOKE_PASSED: " + checks + " checks"); closeWindows(); Platform.exit();
  }
  private void toggle(String id) { node(id, CheckBox.class).fire(); }
  private <T> T node(String id, Class<T> type) { return type.cast(settings.getScene().lookup("#" + id)); }
  private void check(boolean okay, String message) { checks++; if (!okay) throw new AssertionError(message); }
  private void later(Runnable step) {
    var pause = new PauseTransition(Duration.millis(260));
    pause.setOnFinished(event -> { try { step.run(); } catch (Throwable error) { fail(error); } }); pause.play();
  }
  private void closeWindows() { for (Window w : List.copyOf(Window.getWindows())) if (w instanceof Stage s) s.close(); }
  private void fail(Throwable error) { failure = error; error.printStackTrace(); closeWindows(); Platform.exit(); }
  public static void main(String[] args) { launch(args); if (failure != null) System.exit(1); }

  private static class Backend {
    final Map<String, Map<String, String>> memory = new HashMap<>(), disk = new HashMap<>(); boolean failFlush;
  }
  private static final class FakePreferences extends AbstractPreferences {
    final Backend backend;
    FakePreferences() { this(null, "", new Backend()); }
    FakePreferences(AbstractPreferences parent, String name, Backend backend) { super(parent, name); this.backend = backend; }
    private Map<String, String> values() { return backend.memory.computeIfAbsent(absolutePath(), key -> new HashMap<>()); }
    FakePreferences restart() {
      var next = new FakePreferences(); backend.disk.forEach((key, values) -> {
        next.backend.memory.put(key, new HashMap<>(values)); next.backend.disk.put(key, new HashMap<>(values));
      }); return next;
    }
    protected void putSpi(String key, String value) { values().put(key, value); }
    protected String getSpi(String key) { return values().get(key); }
    protected void removeSpi(String key) { values().remove(key); }
    protected void removeNodeSpi() { values().clear(); }
    protected String[] keysSpi() { return values().keySet().toArray(String[]::new); }
    protected String[] childrenNamesSpi() { return new String[0]; }
    protected AbstractPreferences childSpi(String name) { return new FakePreferences(this, name, backend); }
    protected void syncSpi() {}
    protected void flushSpi() throws BackingStoreException {
      if (backend.failFlush) throw new BackingStoreException("synthetic flush failure");
      backend.disk.put(absolutePath(), new HashMap<>(values()));
    }
  }
}
