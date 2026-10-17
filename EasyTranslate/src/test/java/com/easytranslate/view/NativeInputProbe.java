package com.easytranslate.view;

import com.easytranslate.service.hotkey.WindowsInputActivityService;
import com.easytranslate.service.hotkey.WindowsTabObserverService;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Manual native hooks + focus traversal probe; no selection capture, translation or saved text. */
public class NativeInputProbe extends Application {
  private WindowsInputActivityService input;
  private AnimationTimer timer;
  private WindowsTabObserverService tabObserver;
  private final AtomicInteger tabNotifications = new AtomicInteger();
  @Override public void start(Stage stage) {
    input = new WindowsInputActivityService();
    Label result = new Label("等待键鼠输入");
    TextField field = new TextField();
    field.setPromptText("在此输入 a、j、空格，验证按键正常到达");
    TextField second = new TextField();
    second.setPromptText("第二个输入框：检查Tab前进、Shift+Tab后退、候选文字正常到达");
    Label triggerResult = new Label();
    tabObserver = new WindowsTabObserverService();
    tabObserver.start(tabNotifications::incrementAndGet);
    VBox root = new VBox(15,
        new Label("离线手动检查：仅记录次数/动效位，不读取选区、不翻译、不保存输入"),
        new Label("短按/长按Tab应各计一次；Shift/Ctrl/Alt/Win+Tab不计。候选态仍须人工确认。"),
        field, second, triggerResult, result);
    root.setStyle("-fx-padding: 24;");
    stage.setTitle("EasyTranslate · Native Input Probe");
    stage.setScene(new Scene(root, 760, 290));
    stage.show();
    timer = new AnimationTimer() {
      private boolean left, right, mouse;
      @Override public void handle(long now) {
        int bits = input.drain();
        if ((bits & 4) != 0) mouse = true;
        else { left |= (bits & 1) != 0; right |= (bits & 2) != 0; }
        result.setText("左键区：" + left + "    右键区：" + right + "    鼠标：" + mouse);
        triggerResult.setText("合格Tab通知次数：" + tabNotifications.get() + "    当前焦点："
            + (field.isFocused() ? "输入框1" : second.isFocused() ? "输入框2" : "其他窗口/控件"));
      }
    };
    timer.start();
  }
  @Override public void stop() {
    if (timer != null) timer.stop();
    if (tabObserver != null) tabObserver.close();
    if (input != null) input.close();
  }
  public static void main(String[] args) { launch(args); }
}
