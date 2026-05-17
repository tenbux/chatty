
package chatty.gui;

import chatty.util.Timestamp;
import chatty.util.colors.ColorCorrector;
import java.awt.Color;
import java.awt.Font;
import javax.swing.text.MutableAttributeSet;

/**
 * Provide style information to other objects.
 * 
 * @author tduva
 */
public interface StyleServer {
    Color getColor(String type);
    MutableAttributeSet getStyle(String type);
    Font getFont(String type);
    Timestamp getTimestampFormat();
    ColorCorrector getColorCorrector();
}
