package com.easytranslate.config;
import javafx.geometry.Rectangle2D;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WindowGeometryTest {
  private final Rectangle2D main = new Rectangle2D(0, 0, 1280, 720);
  private WindowGeometry.Position recover(double x, double y, double w, double h) {
    return WindowGeometry.recover(x,y,w,h,List.of(main));
  }
  @Test void unchangedWhenContained() { assertEquals(new WindowGeometry.Position(10,20), recover(10,20,400,200)); }
  @Test void detachedScreenReturnsToPrimary() { assertEquals(new WindowGeometry.Position(880,520), recover(2200,800,400,200)); }
  @Test void rightBottomClampUsesWholeWindow() { assertEquals(new WindowGeometry.Position(880,520), recover(1270,710,400,200)); }
  @Test void negativeMonitorIsSupported() {
    assertEquals(new WindowGeometry.Position(-1000,20), WindowGeometry.recover(-1000,20,400,200,
        List.of(main,new Rectangle2D(-1280,0,1280,720))));
  }
  @Test void spanningWindowUsesLargestIntersection() {
    assertEquals(new WindowGeometry.Position(1280,0), WindowGeometry.recover(1200,0,400,200,
        List.of(main,new Rectangle2D(1280,0,1280,720))));
  }
  @Test void invalidCoordinatesFallBackSafely() { assertEquals(new WindowGeometry.Position(0,0),recover(Double.NaN,Double.POSITIVE_INFINITY,400,200)); }
  @Test void oversizedWindowKeepsTopLeftReachableWithoutClaimingFullFit() { assertEquals(new WindowGeometry.Position(0,0),recover(20,20,2000,1000)); }
  @Test void fractionalVisualBoundsUseLogicalUnits() {
    assertEquals(new WindowGeometry.Position(500.5,300.25), WindowGeometry.recover(900,800,300,200,
        List.of(new Rectangle2D(0.5,0.25,800,500))));
  }
  @Test void invalidSizeAndEmptyScreens() {
    assertEquals(new WindowGeometry.Position(1279,719),recover(2000,2000,Double.NaN,-1));
    assertThrows(IllegalArgumentException.class,()->WindowGeometry.recover(0,0,1,1,List.of()));
  }
}
