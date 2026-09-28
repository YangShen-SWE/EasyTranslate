package com.easytranslate.view;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.shape.*;
import javafx.scene.transform.Scale;
import org.w3c.dom.Element;
import javax.xml.parsers.DocumentBuilderFactory;

/** Renders bundled Lucide SVGs as JavaFX shapes, without a web view. */
final class WindowIcon {
  private WindowIcon() {}

  static Node create(String name, double size) {
    try (var stream = WindowIcon.class.getResourceAsStream("icons/" + name + ".svg")) {
      if (stream == null) throw new IllegalArgumentException("Missing icon: " + name);
      var factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      var children = factory.newDocumentBuilder().parse(stream).getDocumentElement().getChildNodes();
      Group drawing = new Group();
      for (int i = 0; i < children.getLength(); i++) {
        if (!(children.item(i) instanceof Element e)) continue;
        Shape shape = switch (e.getTagName()) {
          case "path" -> { var p = new SVGPath(); p.setContent(e.getAttribute("d")); yield p; }
          case "circle" -> new Circle(number(e, "cx"), number(e, "cy"), number(e, "r"));
          case "rect" -> {
            var r = new Rectangle(number(e, "x"), number(e, "y"), number(e, "width"), number(e, "height"));
            r.setArcWidth(number(e, "rx") * 2); r.setArcHeight(number(e, "rx") * 2); yield r;
          }
          default -> throw new IllegalArgumentException("Unsupported icon element: " + e.getTagName());
        };
        shape.getStyleClass().add("icon-stroke");
        drawing.getChildren().add(shape);
      }
      drawing.getTransforms().add(new Scale(size / 24, size / 24));
      Pane pane = new Pane(drawing);
      pane.setMinSize(size, size); pane.setPrefSize(size, size); pane.setMaxSize(size, size);
      pane.setMouseTransparent(true);
      return pane;
    } catch (Exception e) {
      throw new IllegalStateException("Cannot load icon " + name, e);
    }
  }

  private static double number(Element e, String attribute) {
    String value = e.getAttribute(attribute);
    return value.isEmpty() ? 0 : Double.parseDouble(value);
  }
}
