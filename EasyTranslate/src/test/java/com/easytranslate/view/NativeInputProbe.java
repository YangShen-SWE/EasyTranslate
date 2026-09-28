package com.easytranslate.view;

import com.easytranslate.service.hotkey.WindowsInputActivityService;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Manual native-input check with a disposable text field; no translation or saved text. */
public class NativeInputProbe extends Application {
  private WindowsInputActivityService input;
  private AnimationTimer timer;
  @Override public void start(Stage stage) {
    input = new WindowsInputActivityService();
    Label result = new Label("等待键鼠输入");
    TextField field = new TextField();
    field.setPromptText("在此输入 a、j、空格，验证按键正常到达");
    VBox root = new VBox(15, new Label("动效输入检查（离线，不保存输入）"), field, result);
    root.setStyle("-fx-padding: 24;");
    stage.setTitle("EasyTranslate · Native Input Probe");
    stage.setScene(new Scene(root, 470, 150));
    stage.show();
    timer = new AnimationTimer() {
      private boolean left, right, mouse;
      @Override public void handle(long now) {
        int bits = input.drain();
        if ((bits & 4) != 0) mouse = true;
        else { left |= (bits & 1) != 0; right |= (bits & 2) != 0; }
        result.setText("左键区：" + left + "    右键区：" + right + "    鼠标：" + mouse);
      }
    };
    timer.start();
  }
  @Override public void stop() { timer.stop(); input.close(); }
  public static void main(String[] args) { launch(args); }
}
