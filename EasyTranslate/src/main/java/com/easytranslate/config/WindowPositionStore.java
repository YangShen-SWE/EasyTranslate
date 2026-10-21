package com.easytranslate.config;

import javafx.stage.Screen;
import javafx.stage.Stage;
import java.util.prefs.Preferences;

public class WindowPositionStore {
  private static final String X_KEY = "window.x", Y_KEY = "window.y";
  private final Preferences preferences;
  public WindowPositionStore() { this(Preferences.userNodeForPackage(WindowPositionStore.class)); }
  public WindowPositionStore(Preferences preferences) { this.preferences = preferences; }

  public void restore(Stage stage) {
    try {
      double x = preferences.getDouble(X_KEY, Double.NaN);
      double y = preferences.getDouble(Y_KEY, Double.NaN);
      if (Double.isFinite(x) && Double.isFinite(y)) { stage.setX(x); stage.setY(y); }
    } catch (RuntimeException ignored) { /* Geometry recovery still runs with default placement. */ }
  }

  /** Call after layout and on monitor topology/scale changes. No show/toFront/requestFocus. */
  public static void recover(Stage stage) {
    var screens = Screen.getScreens().stream().map(Screen::getVisualBounds).toList();
    if (screens.isEmpty()) return;
    var position = WindowGeometry.recover(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight(), screens);
    if (stage.getX() != position.x()) stage.setX(position.x());
    if (stage.getY() != position.y()) stage.setY(position.y());
  }

  public void save(Stage stage) {
    try {
      if (!Double.isFinite(stage.getX()) || !Double.isFinite(stage.getY())) return;
      preferences.putDouble(X_KEY, stage.getX());
      preferences.putDouble(Y_KEY, stage.getY());
      preferences.flush();
    } catch (Exception ignored) { /* Saving location must never prevent close/cleanup. */ }
  }
}
