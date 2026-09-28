package com.easytranslate.view;

import com.easytranslate.service.hotkey.InputActivityBuffer;
import com.easytranslate.service.hotkey.WindowsInputActivityService;
import javafx.animation.*;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Sprite pieces share the original PNG, so the resting artwork stays unchanged. */
final class CompanionMotion implements AutoCloseable {
  private final ImageView leftPaw, rightPaw, steam;
  private final Timeline leftTap, rightTap, steamCycle;
  private final Map<ButtonBase, FadeTransition> hover = new LinkedHashMap<>();
  private WindowsInputActivityService input;
  private boolean active, globalInput;
  private long lastPulse;
  private final AnimationTimer frames = new AnimationTimer() {
    @Override public void handle(long now) {
      if (input == null || now - lastPulse < 70_000_000L) return;
      lastPulse = now;
      pulse(input.drain());
    }
  };

  CompanionMotion(Pane catHost, ImageView cat, Pane deskHost, ImageView desk) {
    cat.setVisible(false);
    cat.setManaged(false);
    double catScale = 70.0 / 1258;
    piece(catHost, cat.getImage(), 162, 117, 1258, 573, 162, 117, catScale, "catHead");
    piece(catHost, cat.getImage(), 452, 690, 678, 201, 162, 117, catScale, "catBase");
    leftPaw = piece(catHost, cat.getImage(), 162, 690, 290, 201, 162, 117, catScale, "leftPaw");
    rightPaw = piece(catHost, cat.getImage(), 1130, 690, 290, 201, 162, 117, catScale, "rightPaw");
    desk.setVisible(false);
    desk.setManaged(false);
    double deskScale = 48.0 / 492;
    piece(deskHost, desk.getImage(), 440, 390, 930, 325, 440, 223, deskScale, "deskBase");
    piece(deskHost, desk.getImage(), 440, 223, 620, 167, 440, 223, deskScale, "plantTop");
    steam = piece(deskHost, desk.getImage(), 1060, 223, 100, 167, 440, 223, deskScale, "coffeeSteam");
    leftTap = tap(leftPaw);
    rightTap = tap(rightPaw);
    steamCycle = new Timeline(
        new KeyFrame(Duration.ZERO, new KeyValue(steam.opacityProperty(), 0.7), new KeyValue(steam.translateYProperty(), 0)),
        new KeyFrame(Duration.seconds(5), new KeyValue(steam.opacityProperty(), 0.7), new KeyValue(steam.translateYProperty(), 0)),
        new KeyFrame(Duration.seconds(6.8), new KeyValue(steam.opacityProperty(), 0), new KeyValue(steam.translateYProperty(), -5, Interpolator.EASE_OUT)),
        new KeyFrame(Duration.seconds(7), new KeyValue(steam.opacityProperty(), 0), new KeyValue(steam.translateYProperty(), 0)),
        new KeyFrame(Duration.seconds(8), new KeyValue(steam.opacityProperty(), 0.7)));
    steamCycle.setCycleCount(Animation.INDEFINITE);
  }

  private static ImageView piece(Pane host, Image image, double x, double y, double width, double height,
                                 double originX, double originY, double scale, String id) {
    ImageView view = new ImageView(image);
    view.setId(id);
    view.setViewport(new Rectangle2D(x, y, width, height));
    view.setFitWidth(width * scale);
    view.setFitHeight(height * scale);
    view.setLayoutX((x - originX) * scale);
    view.setLayoutY((y - originY) * scale);
    view.setManaged(false);
    view.setMouseTransparent(true);
    host.getChildren().add(view);
    return view;
  }

  private static Timeline tap(Node paw) {
    Timeline animation = new Timeline(
        new KeyFrame(Duration.ZERO, new KeyValue(paw.translateYProperty(), 0)),
        new KeyFrame(Duration.millis(45), new KeyValue(paw.translateYProperty(), -2.5, Interpolator.EASE_OUT)),
        new KeyFrame(Duration.millis(95), new KeyValue(paw.translateYProperty(), 1)),
        new KeyFrame(Duration.millis(160), new KeyValue(paw.translateYProperty(), 0, Interpolator.EASE_OUT)));
    animation.setOnFinished(event -> paw.setEffect(null));
    return animation;
  }

  void addHover(ButtonBase button) {
    if (hover.containsKey(button)) return;
    FadeTransition fade = new FadeTransition(Duration.millis(120), button);
    hover.put(button, fade);
    button.hoverProperty().addListener((observable, before, after) -> {
      if (!active) return;
      fade.stop();
      fade.setToValue(after ? 1 : 0.86);
      fade.play();
    });
  }

  void enableGlobalInput() {
    globalInput = true;
    if (active && input == null) input = new WindowsInputActivityService();
  }

  void setActive(boolean value) {
    if (active == value) return;
    active = value;
    if (value) {
      if (globalInput) input = new WindowsInputActivityService();
      steamCycle.playFromStart();
      frames.start();
    } else {
      frames.stop();
      if (input != null) { input.close(); input = null; }
      steamCycle.stop(); leftTap.stop(); rightTap.stop();
      leftPaw.setTranslateY(0); rightPaw.setTranslateY(0);
      leftPaw.setEffect(null); rightPaw.setEffect(null);
      steam.setTranslateY(0); steam.setOpacity(1);
      hover.forEach((button, fade) -> { fade.stop(); button.setOpacity(1); });
    }
  }

  void pulse(int bits) {
    if (!active) return;
    boolean mouse = (bits & InputActivityBuffer.MOUSE) != 0;
    if ((bits & InputActivityBuffer.LEFT) != 0) play(leftTap, leftPaw, mouse);
    if ((bits & InputActivityBuffer.RIGHT) != 0) play(rightTap, rightPaw, mouse);
  }

  private static void play(Timeline animation, ImageView paw, boolean mouse) {
    // Repeated input coalesces into a small movement, not an ever-growing animation queue.
    if (animation.getStatus() == Animation.Status.RUNNING) return;
    if (mouse) paw.setEffect(new DropShadow(3, Color.web("#8be6c0", 0.65)));
    animation.playFromStart();
  }

  boolean nativeInputRunning() { return input != null && input.isRunning(); }
  String inputFailure() { return input == null ? null : input.failure(); }
  @Override public void close() { setActive(false); }
}
