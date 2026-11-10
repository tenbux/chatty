package chatty.gui.components;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * A pushpin drawn in a single color, tilted so the needle points to the
 * bottom left.
 */
public class PushpinIcon implements Icon {

    private final int size;
    private final Color color;

    /**
     * @param size width and height of the icon in pixels
     * @param color the pin's color
     */
    public PushpinIcon(int size, Color color) {
        this.size = size;
        this.color = color;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.translate(x, y);
            g2.scale(size, size);
            // The shapes below are an upright pin in a 1x1 box, kept within a
            // radius of 0.5 from the center so no part is cut off when rotated.
            g2.rotate(Math.toRadians(45), 0.5, 0.5);
            g2.setColor(color);
            g2.fill(new RoundRectangle2D.Double(0.30, 0.05, 0.40, 0.15, 0.08, 0.08));
            Path2D body = new Path2D.Double();
            body.moveTo(0.38, 0.20);
            body.lineTo(0.62, 0.20);
            body.lineTo(0.66, 0.53);
            body.lineTo(0.34, 0.53);
            body.closePath();
            g2.fill(body);
            g2.fill(new RoundRectangle2D.Double(0.20, 0.52, 0.60, 0.10, 0.06, 0.06));
            g2.setStroke(new BasicStroke(0.07f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new Line2D.Double(0.5, 0.62, 0.5, 0.95));
        }
        finally {
            g2.dispose();
        }
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }
}
