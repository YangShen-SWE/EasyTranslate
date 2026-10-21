package com.easytranslate.config;

import javafx.geometry.Rectangle2D;
import java.util.List;

/** Pure logical-coordinate placement; never changes focus or physical DPI. */
public final class WindowGeometry {
  private WindowGeometry() {}
  public record Position(double x, double y) {}

  public static Position recover(double x, double y, double width, double height,
                                 List<Rectangle2D> screens) {
    if (screens.isEmpty()) throw new IllegalArgumentException("No usable screen");
    double w = Double.isFinite(width) && width > 0 ? width : 1;
    double h = Double.isFinite(height) && height > 0 ? height : 1;
    Rectangle2D target = screens.getFirst();
    double largest = 0;
    if (Double.isFinite(x) && Double.isFinite(y)) {
      for (Rectangle2D screen : screens) {
        double area = Math.max(0, Math.min(x + w, screen.getMaxX()) - Math.max(x, screen.getMinX()))
            * Math.max(0, Math.min(y + h, screen.getMaxY()) - Math.max(y, screen.getMinY()));
        if (area > largest) { largest = area; target = screen; }
      }
    } else {
      x = target.getMinX(); y = target.getMinY();
    }
    return new Position(
        Math.max(target.getMinX(), Math.min(x, target.getMaxX() - Math.min(w, target.getWidth()))),
        Math.max(target.getMinY(), Math.min(y, target.getMaxY() - Math.min(h, target.getHeight()))));
  }
}
