package com.easytranslate.view;

import com.easytranslate.viewmodel.FloatingViewModel;
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

  private final Preferences preferences;
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
    this(Preferences.userNodeForPackage(FloatingViewController.class).node("appearance"));
  }

  // Package-visible injection lets UI checks use isolated, disposable preferences.
  FloatingViewController(Preferences preferences) {
    this.preferences = preferences;
  }

  @FXML private void initialize() {
    windowSize = WindowSize.fromPreference(preferences.get(WINDOW_SIZE_KEY, WindowSize.LARGE.name()));
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
    motionEnabled.set(preferences.getBoolean("motionEnabled", true));
    motionEnabled.addListener((observable, before, after) -> {
      preferences.putBoolean("motionEnabled", after);
      refreshMotion();
    });
    decorations.visibleProperty().addListener((observable, before, after) -> refreshMotion());
    decorations.setVisible(preferences.getBoolean("decorations", true));
    decorations.managedProperty().bind(decorations.visibleProperty());
    showSource.set(preferences.getBoolean("showSource", true));
    sourceColumn.visibleProperty().bind(showSource);
    sourceColumn.managedProperty().bind(showSource);
    columnDivider.visibleProperty().bind(showSource);
    columnDivider.managedProperty().bind(showSource);
    showSource.addListener((observable, before, after) -> {
      preferences.putBoolean("showSource", after);
      queueResize();
    });
    setFontSize(preferences.getInt("fontSize", 22));
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
    pinButton.setSelected(preferences.getBoolean("alwaysOnTop", true));
    stage.setAlwaysOnTop(pinButton.isSelected());
    stage.setResizable(false);
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
    refreshStatus();
    queueResize();
  }

  private void refreshStatus() {
    boolean hasTranslation = viewModel != null && !viewModel.translatedTextProperty().get().isEmpty();
    statusLabel.setText(hasTranslation ? "翻译结果" : "等待划词");
    statusDot.pseudoClassStateChanged(PseudoClass.getPseudoClass("ready"), hasTranslation);
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
    ClipboardContent content = new ClipboardContent();
    content.putString(viewModel.translatedTextProperty().get());
    if (Clipboard.getSystemClipboard().setContent(content)) {
      statusLabel.setText("译文已复制");
      copyFeedback.playFromStart();
    }
  }

  @FXML private void onTogglePin() {
    getStage().setAlwaysOnTop(pinButton.isSelected());
    preferences.putBoolean("alwaysOnTop", pinButton.isSelected());
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

  @FXML private void onSettings() {
    if (settingsStage == null) {
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
        preferences.putBoolean("decorations", after);
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
        preferences.putInt("fontSize", fontSize);
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
        preferences.put(WINDOW_SIZE_KEY, windowSize.name());
        root.setPrefWidth(windowSize.width);
        queueResize();
      });
      HBox windowSizeRow = new HBox(8, small, medium, large);
      VBox windowSizeSetting = new VBox(12,
          label("窗口大小", "setting-label"), windowSizeRow);
      HBox heading = new HBox(12, WindowIcon.create("settings", 26), label("外观设置", "utility-title"));
      heading.setAlignment(Pos.CENTER_LEFT);
      VBox content = new VBox(20, heading,
          settingRow("显示原文", "关闭后仅显示译文", sourceToggle), new Separator(),
          settingRow("悬浮窗置顶", "保持在其他窗口上方", alwaysOnTop), new Separator(),
          settingRow("桌面装饰", "显示小猫、花盆和咖啡杯", showDecorations), new Separator(),
          settingRow("动态效果", "键鼠轻反馈与咖啡热气", motionToggle), new Separator(),
          windowSizeSetting, new Separator(),
          new VBox(14, sizeHeader, sliderRow), new Separator(),
          label("更改立即生效，并自动保存", "muted"));
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
    close.setOnAction(event -> stage.close());
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
    VBox panel = new VBox(header, content);
    panel.getStyleClass().add("floating-window");
    VBox shell = new VBox(companion, panel);
    shell.setPrefWidth(448);
    shell.getStyleClass().add("floating-root");
    Scene scene = new Scene(shell, Color.TRANSPARENT);
    scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("floating-window.css")).toExternalForm());
    stage.setScene(scene);
    scene.setOnKeyPressed(event -> {
      if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) stage.close();
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
      if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) stage.close();
    });
    return stage;
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
