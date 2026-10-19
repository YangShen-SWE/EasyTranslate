package com.easytranslate.view;

import com.easytranslate.config.ApiKeyStore;
import com.easytranslate.config.AppearancePreferences;
import com.easytranslate.config.AppearancePreferences.Setting;
import javafx.beans.property.SimpleStringProperty;
import com.easytranslate.viewmodel.FloatingViewModel;
import com.easytranslate.usecase.TranslationOperation;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.util.Objects;
import java.util.prefs.Preferences;

public class FloatingViewController {
  private static final String WINDOW_SIZE_KEY = "windowSize";
  private enum WindowSize {
    SMALL(420, 110), MEDIUM(500, 165), LARGE(588, 220);

    final double width;
    final double maxTextHeight;

    WindowSize(double width, double maxTextHeight) {
      this.width = width;
      this.maxTextHeight = maxTextHeight;
    }

    static WindowSize fromPreference(String value) {
      try { return WindowSize.valueOf(value); }
      catch (IllegalArgumentException | NullPointerException ignored) { return LARGE; }
    }
  }

  @FXML private VBox root;
  @FXML private VBox sourceColumn;
  @FXML private Separator columnDivider;
  @FXML private HBox decorations;
  @FXML private Pane catHost, deskHost;
  @FXML private ImageView catImage, deskImage;
  @FXML private StackPane brandIcon;
  @FXML private Label sourceLabel, translatedLabel, statusLabel;
  @FXML private Region statusDot;
  @FXML private ScrollPane sourceScroll, translatedScroll;
  @FXML private ToggleButton pinButton;
  @FXML private Button closeButton, copyButton, vocabularyButton, settingsButton;

  private final AppearancePreferences appearance;
  private AppearancePreferences.Snapshot restored;
  private final SimpleStringProperty appearanceStatus = new SimpleStringProperty("更改立即生效，保存成功后下次启动恢复");
  private final ApiKeyStore apiKeyStore;
  private final java.util.function.Predicate<String> clipboardWriter;
  private com.easytranslate.config.OnlineTranslationPolicy onlinePolicy;
  private Runnable onOnlineChange = () -> {};
  private final BooleanProperty showSource = new SimpleBooleanProperty(true);
  private final BooleanProperty motionEnabled = new SimpleBooleanProperty(true);
  private CompanionMotion motion;
  private final PauseTransition copyFeedback = new PauseTransition(Duration.seconds(1.5));
  private FloatingViewModel viewModel;
  private Stage vocabularyStage, settingsStage;
  private double dragOffsetX, dragOffsetY;
  private boolean dragging;
  private boolean resizeQueued;
  private int fontSize;
  private WindowSize windowSize;

  public FloatingViewController() {
    this(Preferences.userNodeForPackage(FloatingViewController.class).node("appearance"),
        new ApiKeyStore());
  }

  // Package-visible injection lets UI checks use isolated, disposable preferences.
  FloatingViewController(Preferences preferences) {
    this(preferences, new ApiKeyStore(preferences));
  }

  FloatingViewController(Preferences preferences, ApiKeyStore apiKeyStore) {
    this(preferences, apiKeyStore, FloatingViewController::writeClipboard);
  }

  // The UI focus checker must not read or overwrite the user's real clipboard.
  FloatingViewController(Preferences preferences, ApiKeyStore apiKeyStore,
      java.util.function.Predicate<String> clipboardWriter) {
    this.appearance = new AppearancePreferences(Objects.requireNonNull(preferences));
    this.apiKeyStore = Objects.requireNonNull(apiKeyStore);
    this.clipboardWriter = Objects.requireNonNull(clipboardWriter);
    this.onlinePolicy = new com.easytranslate.config.OnlineTranslationPolicy(preferences.node("privacy"));
  }

  private static boolean writeClipboard(String text) {
    ClipboardContent content = new ClipboardContent();
    content.putString(text);
    return Clipboard.getSystemClipboard().setContent(content);
  }

  public void configureOnlineTranslation(com.easytranslate.config.OnlineTranslationPolicy policy,
      Runnable onChange) {
    if (settingsStage != null) throw new IllegalStateException("Configure before opening settings");
    onlinePolicy = Objects.requireNonNull(policy, "policy");
    onOnlineChange = Objects.requireNonNull(onChange, "onChange");
  }

  @FXML private void initialize() {
    restored = appearance.load();
    if (restored.recovered()) appearanceStatus.set("部分外观配置损坏或无法读取，已使用安全默认值；修改后可重新保存");
    windowSize = WindowSize.fromPreference(restored.windowSize());
    root.setPrefWidth(windowSize.width);
    brandIcon.getChildren().add(WindowIcon.create("leaf", 18));
    brandIcon.getStyleClass().add("brand-icon");
    configureButton(pinButton, "pin", "窗口置顶");
    configureButton(closeButton, "x", "关闭窗口");
    configureButton(copyButton, "copy", "复制译文");
    configureButton(vocabularyButton, "book-open", "打开单词本");
    configureButton(settingsButton, "settings", "外观设置");
    loadImage(catImage, "cat.png", new Rectangle2D(162, 117, 1258, 774));
    loadImage(deskImage, "desk.png", new Rectangle2D(440, 223, 930, 492));
    motion = new CompanionMotion(catHost, catImage, deskHost, deskImage);
    for (ButtonBase button : new ButtonBase[]{pinButton, closeButton, copyButton, vocabularyButton, settingsButton}) motion.addHover(button);
    motionEnabled.set(restored.motionEnabled());
    motionEnabled.addListener((observable, before, after) -> {
      saveAppearance(Setting.MOTION_ENABLED, after.toString());
      refreshMotion();
    });
    decorations.visibleProperty().addListener((observable, before, after) -> refreshMotion());
    decorations.setVisible(restored.decorations());
    decorations.managedProperty().bind(decorations.visibleProperty());
    showSource.set(restored.showSource());
    sourceColumn.visibleProperty().bind(showSource);
    sourceColumn.managedProperty().bind(showSource);
    columnDivider.visibleProperty().bind(showSource);
    columnDivider.managedProperty().bind(showSource);
    showSource.addListener((observable, before, after) -> {
      saveAppearance(Setting.SHOW_SOURCE, after.toString());
      queueResize();
    });
    setFontSize(restored.fontSize());
    copyFeedback.setOnFinished(event -> refreshStatus());
    sourceScroll.viewportBoundsProperty().addListener((observable, before, after) -> queueResize());
    translatedScroll.viewportBoundsProperty().addListener((observable, before, after) -> queueResize());
  }

  private static void configureButton(ButtonBase button, String icon, String tooltip) {
    button.setGraphic(WindowIcon.create(icon, 18));
    button.setTooltip(new Tooltip(tooltip));
  }

  private static void loadImage(ImageView view, String name, Rectangle2D viewport) {
    var url = Objects.requireNonNull(FloatingViewController.class.getResource("assets/" + name));
    view.setImage(new Image(url.toExternalForm()));
    view.setViewport(viewport);
  }

  public void configureStage(Stage stage) {
    pinButton.setSelected(restored.alwaysOnTop());
    stage.setAlwaysOnTop(pinButton.isSelected());
    stage.setResizable(false);
    Scene scene = stage.getScene();
    // Local accelerators only: automatic result/status updates never show, raise or focus a window.
    scene.getAccelerators().put(new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN),
        copyButton::fire);
    scene.getAccelerators().put(new KeyCodeCombination(KeyCode.COMMA, KeyCombination.SHORTCUT_DOWN),
        settingsButton::fire);
    scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F6), translatedScroll::requestFocus);
    scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F6, KeyCombination.SHIFT_DOWN),
        () -> { if (showSource.get()) sourceScroll.requestFocus(); else translatedScroll.requestFocus(); });
    scene.getAccelerators().put(new KeyCodeCombination(KeyCode.ESCAPE), stage::close);
    translatedScroll.setTooltip(new Tooltip("F6进入译文，方向键/翻页键滚动；Ctrl+C复制译文"));
    sourceScroll.setTooltip(new Tooltip("Shift+F6进入原文，方向键/翻页键滚动"));
    settingsButton.setTooltip(new Tooltip("设置（Ctrl+,）；Esc关闭当前窗口"));
    stage.setOnShown(event -> { queueResize(); refreshMotion(); });
    stage.addEventHandler(javafx.stage.WindowEvent.WINDOW_HIDDEN, event -> {
      copyFeedback.stop();
      motion.close();
      if (settingsStage != null) settingsStage.close();
      if (vocabularyStage != null) vocabularyStage.close();
    });
  }

  public void enableGlobalInput() { motion.enableGlobalInput(); }

  private void refreshMotion() {
    motion.setActive(motionEnabled.get() && decorations.isVisible()
        && root.getScene() != null && root.getScene().getWindow() != null
        && root.getScene().getWindow().isShowing());
  }

  void previewInput(int bits) { motion.pulse(bits); }
  boolean nativeMotionRunning() { return motion.nativeInputRunning(); }
  String nativeMotionFailure() { return motion.inputFailure(); }

  public void setViewModel(FloatingViewModel viewModel) {
    this.viewModel = Objects.requireNonNull(viewModel);
    sourceLabel.textProperty().bind(Bindings.when(viewModel.sourceTextProperty().isEmpty())
        .then("Select a word or phrase.").otherwise(viewModel.sourceTextProperty()));
    translatedLabel.textProperty().bind(Bindings.when(viewModel.translatedTextProperty().isEmpty())
        .then("选中英文后，按 Tab 翻译").otherwise(viewModel.translatedTextProperty()));
    copyButton.disableProperty().bind(viewModel.translatedTextProperty().isEmpty());
    viewModel.translatedTextProperty().addListener((observable, before, after) -> {
      copyFeedback.stop();
      refreshStatus();
      sourceScroll.setVvalue(0);
      translatedScroll.setVvalue(0);
      queueResize();
    });
    viewModel.sourceTextProperty().addListener((observable, before, after) -> queueResize());
    viewModel.statusTextProperty().addListener((observable, before, after) -> {
      copyFeedback.stop();
      refreshStatus();
    });
    viewModel.operationProperty().addListener((observable, before, after) -> {
      copyFeedback.stop();
      refreshStatus();
    });
    refreshStatus();
    queueResize();
  }

  private void refreshStatus() {
    statusLabel.setText(viewModel == null ? "等待划词" : viewModel.statusTextProperty().get());
    statusDot.pseudoClassStateChanged(PseudoClass.getPseudoClass("ready"),
        viewModel != null && viewModel.operationProperty().get() == TranslationOperation.SUCCEEDED);
    queueResize();
  }

  public void showTranslationError(String message) {
    if (viewModel != null) viewModel.showFailure(message);
    refreshStatus();
  }

  public void clearTranslationError() {
    // Success is assigned by showTranslation, not by clearing a presentation message.
    refreshStatus();
  }

  // Grow for a few lines, then keep the floating window bounded and scroll each language.
  private void queueResize() {
    if (resizeQueued) return;
    resizeQueued = true;
    Platform.runLater(() -> {
      resizeQueued = false;
      if (root.getScene() == null || root.getScene().getWindow() == null) return;
      root.applyCss();
      root.layout();
      double translatedWidth = Math.max(120, translatedScroll.getViewportBounds().getWidth() - 8);
      double sourceHeight = showSource.get()
          ? sourceLabel.prefHeight(Math.max(120, sourceScroll.getViewportBounds().getWidth() - 8)) : 0;
      double height = Math.max(64, Math.min(windowSize.maxTextHeight,
          Math.max(sourceHeight, translatedLabel.prefHeight(translatedWidth)) + 8));
      if (Math.abs(sourceScroll.getPrefHeight() - height) > 1) {
        sourceScroll.setPrefHeight(height);
        translatedScroll.setPrefHeight(height);
      }
      Stage stage = getStage();
      double x = stage.getX(), y = stage.getY();
      stage.sizeToScene();
      if (Double.isFinite(x)) stage.setX(x);
      if (Double.isFinite(y)) stage.setY(y);
    });
  }

  private void setFontSize(int size) {
    fontSize = Math.max(16, Math.min(26, size));
    sourceLabel.setStyle("-fx-font-size: " + fontSize + "px;");
    translatedLabel.setStyle("-fx-font-size: " + (fontSize + 1) + "px;");
    queueResize();
  }

  @FXML private void onCopy() {
    if (viewModel == null || copyButton.isDisabled()) return;
    if (clipboardWriter.test(viewModel.translatedTextProperty().get())) {
      // Copy feedback must not disguise a pending/failed operation or a retained result.
      statusLabel.setText("译文已复制 · " + viewModel.statusTextProperty().get());
      queueResize();
      copyFeedback.playFromStart();
    }
  }

  @FXML private void onTogglePin() {
    getStage().setAlwaysOnTop(pinButton.isSelected());
    saveAppearance(Setting.ALWAYS_ON_TOP, Boolean.toString(pinButton.isSelected()));
  }

  @FXML private void onVocabulary() {
    if (vocabularyStage == null) {
      Label title = label("单词本", "utility-title");
      Label heading = label("单词本准备中", "empty-title");
      Label explanation = label("当前翻译尚不会保存为词目。\n词目保存与管理将在后续版本提供。", "muted");
      explanation.setWrapText(true);
      explanation.setMaxWidth(340);
      VBox empty = new VBox(WindowIcon.create("book-open", 36), heading, explanation);
      empty.getStyleClass().add("empty-state");
      vocabularyStage = utilityStage("单词本", new VBox(title, empty), 440);
    }
    showUtility(vocabularyStage);
  }

  private void saveAppearance(Setting setting, String value) {
    appearanceStatus.set(appearance.save(setting, value) ? "外观设置已保存，下次启动恢复"
        : "外观设置保存失败：当前更改已生效，未确认重启恢复，请重试更改");
  }

  @FXML private void onSettings() {
    if (settingsStage == null) {
      Label onlineNotice = label("在线翻译说明：开启后，按 Tab 读取的选区文本会通过网络发送给 DeepSeek（api.deepseek.com），并使用你的 API Key 调用服务，可能按服务商规则产生费用。不要上传密码、个人或机密内容。可拒绝并保持关闭；关闭会阻止后续请求并丢弃旧回调，但无法撤回已发送的文本或保证停止计费。", "muted");
      onlineNotice.setId("onlineTranslationNotice");
      onlineNotice.setWrapText(true);
      CheckBox onlineToggle = new CheckBox("已阅读上述说明，同意上传并开启在线翻译");
      onlineToggle.setId("onlineTranslationToggle");
      onlineToggle.setWrapText(true);
      onlineToggle.setSelected(onlinePolicy.permit().isAllowed());
      Label onlineStatus = label(onlineToggle.isSelected() ? "在线翻译已开启" : "在线翻译已关闭（默认不上传）", "muted");
      onlineStatus.setId("onlineTranslationStatus");
      onlineStatus.setWrapText(true);
      Button rejectOnline = new Button("拒绝并关闭在线翻译");
      rejectOnline.setId("rejectOnlineTranslationButton");
      java.util.function.Consumer<Boolean> chooseOnline = enable -> {
        try {
          onlinePolicy.choose(enable);
          onlineStatus.setText(enable ? "已同意并保存：在线翻译开启，需重新触发" : "已拒绝并保存：在线翻译关闭");
        } catch (RuntimeException error) {
          onlineStatus.setText("保存失败，当前已关闭在线翻译，请重试");
        } finally {
          onlineToggle.setSelected(onlinePolicy.permit().isAllowed());
          onOnlineChange.run();
        }
      };
      onlineToggle.setOnAction(event -> chooseOnline.accept(onlineToggle.isSelected()));
      rejectOnline.setOnAction(event -> chooseOnline.accept(false));
      VBox onlineSetting = new VBox(10, label("在线翻译与隐私", "setting-label"),
          onlineNotice, onlineToggle, rejectOnline, onlineStatus);
      CheckBox sourceToggle = appearanceSwitch("showSourceToggle", "显示原文");
      sourceToggle.selectedProperty().bindBidirectional(showSource);
      CheckBox alwaysOnTop = appearanceSwitch("alwaysOnTopToggle", "悬浮窗置顶");
      alwaysOnTop.selectedProperty().bindBidirectional(pinButton.selectedProperty());
      alwaysOnTop.setOnAction(event -> onTogglePin());
      CheckBox showDecorations = appearanceSwitch("decorationsToggle", "桌面装饰");
      CheckBox motionToggle = appearanceSwitch("motionToggle", "动态效果");
      motionToggle.selectedProperty().bindBidirectional(motionEnabled);
      motionToggle.setTooltip(new Tooltip("键鼠事件仅驱动动效，不保存输入内容"));
      showDecorations.setSelected(decorations.isVisible());
      showDecorations.selectedProperty().addListener((observable, before, after) -> {
        decorations.setVisible(after);
        saveAppearance(Setting.DECORATIONS, after.toString());
        queueResize();
      });
      Label sizeLabel = label(fontSize + " px", "setting-value");
      HBox sizeHeader = new HBox(label("正文字号", "setting-label"), new Region(), sizeLabel);
      HBox.setHgrow(sizeHeader.getChildren().get(1), Priority.ALWAYS);
      sizeHeader.setAlignment(Pos.CENTER_LEFT);
      Slider slider = new Slider(16, 26, fontSize);
      slider.setId("fontSizeSlider");
      slider.setAccessibleText("正文字号");
      slider.setMajorTickUnit(2);
      slider.setMinorTickCount(1);
      slider.setSnapToTicks(true);
      slider.setBlockIncrement(1);
      slider.valueProperty().addListener((observable, before, after) -> {
        setFontSize((int) Math.round(after.doubleValue()));
        sizeLabel.setText(fontSize + " px");
        saveAppearance(Setting.FONT_SIZE, Integer.toString(fontSize));
      });
      HBox sliderRow = new HBox(12, label("16", "muted"), slider, label("26", "muted"));
      sliderRow.setAlignment(Pos.CENTER_LEFT);
      HBox.setHgrow(slider, Priority.ALWAYS);
      ToggleGroup windowSizes = new ToggleGroup();
      ToggleButton small = windowSizeButton("小", "windowSizeSmall", WindowSize.SMALL, windowSizes);
      ToggleButton medium = windowSizeButton("中", "windowSizeMedium", WindowSize.MEDIUM, windowSizes);
      ToggleButton large = windowSizeButton("大", "windowSizeLarge", WindowSize.LARGE, windowSizes);
      switch (windowSize) {
        case SMALL -> small.setSelected(true);
        case MEDIUM -> medium.setSelected(true);
        case LARGE -> large.setSelected(true);
      }
      windowSizes.selectedToggleProperty().addListener((observable, before, after) -> {
        if (after == null) {
          if (before != null) before.setSelected(true);
          return;
        }
        windowSize = (WindowSize) after.getUserData();
        saveAppearance(Setting.WINDOW_SIZE, windowSize.name());
        root.setPrefWidth(windowSize.width);
        queueResize();
      });
      HBox windowSizeRow = new HBox(8, small, medium, large);
      VBox windowSizeSetting = new VBox(12,
          label("窗口大小", "setting-label"), windowSizeRow);
      PasswordField apiKeyField = new PasswordField();
      apiKeyField.setId("apiKeyField");
      apiKeyField.setAccessibleText("DeepSeek API Key");
      apiKeyField.setPromptText("输入 DeepSeek API Key");
      apiKeyField.setMaxWidth(Double.MAX_VALUE);
      ApiKeyStore.StoredState keyState = apiKeyStore.storedState();
      Label apiKeyStatus = label(switch (keyState) {
        case ABSENT -> "尚未保存 API Key";
        case PRESENT -> "已保存 API Key（仅本地可读取，未联网验证）";
        case UNREADABLE -> "已存密钥损坏或无法读取，请重新保存或删除（未联网验证）";
      }, "muted");
      apiKeyStatus.setId("apiKeyStatus");
      apiKeyStatus.setWrapText(true);
      Button saveApiKey = new Button("保存");
      saveApiKey.setId("saveApiKeyButton");
      Button deleteApiKey = new Button("删除");
      deleteApiKey.setId("deleteApiKeyButton");
      deleteApiKey.setDisable(keyState == ApiKeyStore.StoredState.ABSENT);
      saveApiKey.setOnAction(event -> {
        if (apiKeyField.getText().isBlank()) {
          apiKeyStatus.setText("请输入 API Key 后再保存");
          return;
        }
        try {
          apiKeyStore.save(apiKeyField.getText());
          apiKeyField.clear();
          apiKeyStatus.setText("API Key 已加密保存（未联网验证）；在线开启后需重新触发翻译");
          deleteApiKey.setDisable(false);
          // Saving is not a successful request: preserve existing failure/retained-result state.
        } catch (RuntimeException error) {
          apiKeyStatus.setText("保存失败，未确认持久化成功；请重新输入后重试");
          deleteApiKey.setDisable(apiKeyStore.storedState() == ApiKeyStore.StoredState.ABSENT);
        } finally {
          apiKeyField.clear();
        }
      });
      deleteApiKey.setOnAction(event -> {
        try {
          apiKeyStore.delete();
          apiKeyField.clear();
          apiKeyStatus.setText("本地 API Key 已删除；已发出的请求无法撤回，仍可能计费");
          deleteApiKey.setDisable(true);
          showTranslationError("请先在设置中保存 DeepSeek API Key");
        } catch (RuntimeException error) {
          apiKeyStatus.setText("删除失败，未确认持久化成功；密钥可能仍存在，请重试");
          deleteApiKey.setDisable(apiKeyStore.storedState() == ApiKeyStore.StoredState.ABSENT);
        } finally {
          apiKeyField.clear();
        }
      });
      HBox apiKeyButtons = new HBox(8, saveApiKey, deleteApiKey);
      VBox apiKeySetting = new VBox(10,
          label("DeepSeek API Key", "setting-label"), apiKeyField,
          label("密钥仅加密保存在当前 Windows 用户配置中", "muted"),
          apiKeyButtons, apiKeyStatus);
      HBox heading = new HBox(12, WindowIcon.create("settings", 26), label("外观设置", "utility-title"));
      heading.setAlignment(Pos.CENTER_LEFT);
      Label persistenceStatus = label("", "muted");
      persistenceStatus.setId("appearanceSaveStatus");
      persistenceStatus.setWrapText(true);
      persistenceStatus.textProperty().bind(appearanceStatus);
      VBox content = new VBox(20, heading, onlineSetting, new Separator(),
          settingRow("显示原文", "关闭后仅显示译文", sourceToggle), new Separator(),
          settingRow("悬浮窗置顶", "保持在其他窗口上方", alwaysOnTop), new Separator(),
          settingRow("桌面装饰", "显示小猫、花盆和咖啡杯", showDecorations), new Separator(),
          settingRow("动态效果", "键鼠轻反馈与咖啡热气", motionToggle), new Separator(),
          windowSizeSetting, new Separator(),
          new VBox(14, sizeHeader, sliderRow), new Separator(),
          apiKeySetting, new Separator(),
          persistenceStatus);
      settingsStage = appearanceStage(content);
    }
    showUtility(settingsStage);
  }

  private static CheckBox appearanceSwitch(String id, String name) {
    CheckBox control = new CheckBox();
    control.setId(id);
    control.setAccessibleText(name);
    control.getStyleClass().add("appearance-switch");
    return control;
  }

  private static ToggleButton windowSizeButton(String text, String id,
                                               WindowSize size, ToggleGroup group) {
    ToggleButton button = new ToggleButton(text);
    button.setId(id);
    button.setAccessibleText("窗口大小：" + text);
    button.setUserData(size);
    button.setToggleGroup(group);
    button.getStyleClass().add("window-size-option");
    return button;
  }

  private static HBox settingRow(String title, String description, CheckBox control) {
    Label detail = label(description, "muted");
    detail.setWrapText(true);
    VBox text = new VBox(6, label(title, "setting-label"), detail);
    HBox.setHgrow(text, Priority.ALWAYS);
    HBox row = new HBox(20, text, control);
    row.setAlignment(Pos.CENTER_LEFT);
    return row;
  }

  private Stage appearanceStage(VBox content) {
    Stage stage = new Stage(StageStyle.TRANSPARENT);
    stage.initOwner(getStage());
    stage.setTitle("EasyTranslate · 设置");
    stage.setResizable(false);
    ImageView cat = new ImageView(catImage.getImage());
    cat.setViewport(catImage.getViewport());
    cat.setFitWidth(58);
    cat.setPreserveRatio(true);
    cat.setMouseTransparent(true);
    HBox companion = new HBox(cat);
    companion.setAlignment(Pos.BOTTOM_LEFT);
    companion.getStyleClass().add("settings-companion");
    companion.visibleProperty().bind(decorations.visibleProperty());
    companion.managedProperty().bind(companion.visibleProperty());
    companion.managedProperty().addListener((observable, before, after) -> {
      Platform.runLater(stage::sizeToScene);
    });
    Button close = new Button();
    close.getStyleClass().addAll("icon-button", "close-button");
    configureButton(close, "x", "关闭设置");
    motion.addHover(close);
    close.setAccessibleText("关闭设置");
    close.setId("closeSettingsButton");
    close.setOnAction(event -> closeUtility(stage));
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    Node brand = WindowIcon.create("leaf", 18);
    brand.getStyleClass().add("brand-icon");
    HBox header = new HBox(9, brand, label("EasyTranslate", "window-title"), spacer, close);
    header.setAlignment(Pos.CENTER_LEFT);
    header.getStyleClass().add("drag-bar");
    double[] offset = new double[2];
    header.setOnMousePressed(event -> {
      if (event.getButton() != MouseButton.PRIMARY || isButtonTarget(event)) return;
      offset[0] = event.getScreenX() - stage.getX();
      offset[1] = event.getScreenY() - stage.getY();
    });
    header.setOnMouseDragged(event -> {
      if (!event.isPrimaryButtonDown() || isButtonTarget(event)) return;
      stage.setX(event.getScreenX() - offset[0]);
      stage.setY(event.getScreenY() - offset[1]);
    });
    content.getStyleClass().add("settings-content");
    ScrollPane settingsScroll = new ScrollPane(content);
    settingsScroll.setId("settingsScroll");
    settingsScroll.setAccessibleText("设置内容，方向键或翻页键滚动");
    settingsScroll.getStyleClass().add("settings-scroll");
    settingsScroll.setFitToWidth(true);
    settingsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    settingsScroll.setMaxHeight(610);
    VBox panel = new VBox(header, settingsScroll);
    panel.getStyleClass().add("floating-window");
    VBox shell = new VBox(companion, panel);
    shell.setPrefWidth(448);
    shell.getStyleClass().add("floating-root");
    Scene scene = new Scene(shell, Color.TRANSPARENT);
    scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("floating-window.css")).toExternalForm());
    stage.setScene(scene);
    scene.setOnKeyPressed(event -> {
      if (event.getCode() == KeyCode.ESCAPE) {
        closeUtility(stage);
        event.consume();
      }
    });
    return stage;
  }

  private static boolean isButtonTarget(MouseEvent event) {
    for (Node node = (Node) event.getTarget(); node != null; node = node.getParent()) {
      if (node instanceof ButtonBase) return true;
    }
    return false;
  }

  private static Label label(String text, String style) {
    Label label = new Label(text);
    label.getStyleClass().add(style);
    return label;
  }

  private Stage utilityStage(String title, VBox content, double width) {
    Stage stage = new Stage();
    stage.initOwner(getStage());
    stage.setTitle("EasyTranslate · " + title);
    content.getStyleClass().add("utility-root");
    content.setPrefWidth(width);
    Scene scene = new Scene(content);
    scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("floating-window.css")).toExternalForm());
    stage.setScene(scene);
    stage.setResizable(false);
    scene.setOnKeyPressed(event -> {
      if (event.getCode() == KeyCode.ESCAPE) {
        closeUtility(stage);
        event.consume();
      }
    });
    return stage;
  }

  private void closeUtility(Stage stage) {
    stage.close();
    // Return only after an explicit close action, never on a translation callback.
    if (getStage().isShowing()) {
      getStage().requestFocus();
      if (stage == settingsStage) settingsButton.requestFocus();
      else vocabularyButton.requestFocus();
    }
  }

  private void showUtility(Stage stage) {
    if (!stage.isShowing()) {
      stage.show();
      stage.centerOnScreen();
    }
    stage.toFront();
    stage.requestFocus();
  }

  @FXML private void onDragStarted(MouseEvent event) {
    dragging = false;
    if (event.getButton() != MouseButton.PRIMARY) return;
    for (Node node = (Node) event.getTarget(); node != null; node = node.getParent()) {
      if (node instanceof ButtonBase) return;
    }
    dragging = true;
    dragOffsetX = event.getScreenX() - getStage().getX();
    dragOffsetY = event.getScreenY() - getStage().getY();
  }

  @FXML private void onDragging(MouseEvent event) {
    if (!dragging || !event.isPrimaryButtonDown()) return;
    getStage().setX(event.getScreenX() - dragOffsetX);
    getStage().setY(event.getScreenY() - dragOffsetY);
  }

  @FXML private void onClose() { getStage().close(); }

  private Stage getStage() { return (Stage) root.getScene().getWindow(); }
}
