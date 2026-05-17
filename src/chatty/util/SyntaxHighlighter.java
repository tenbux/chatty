
package chatty.util;

import chatty.gui.components.settings.Editor;
import chatty.util.colors.ColorCorrection;
import chatty.util.colors.ColorCorrectionNew;
import chatty.util.commands.CommandSyntaxHighlighter;

import javax.swing.*;
import javax.swing.plaf.TextUI;
import javax.swing.text.*;
import javax.swing.text.Highlighter.HighlightPainter;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static chatty.gui.GuiUtil.addChangeListener;

/**
 * Works for JTextComponent or JTextPane, although the one for JTextPane isn't
 * currently used because for the Editor and such it works a bit different in
 * regards to sizing and stuff, although that could probably be solved somehow.
 * 
 * @author tduva
 */
public abstract class SyntaxHighlighter {

    protected final List<Item> items = new ArrayList<>();
    private boolean isEnabled = true;
    
    /**
     * Update the list of items for the given text.
     * 
     * @param text 
     */
    public abstract void update(String text);
    
    public List<Item> getItems(String input) {
        if (isEnabled) {
            update(input);
            return items;
        }
        return new ArrayList<>();
    }
    
    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
    }
    
    public void clear() {
        items.clear();
    }
    
    public void add(int start, int end) {
        add(start, end, Type.REGULAR);
    }
    
    public void add(int start, int end, Type type) {
        items.add(new Item(start, end, type));
    }
    
    private int tempStart = -1;
    private Type tempType;
    
    public void start(int start, Type type) {
        tempStart = start;
        tempType = type;
    }
    
    public void end(int end) {
        if (tempStart != -1) {
            items.add(new Item(tempStart, end, tempType));
            tempStart = -1;
            tempType = null;
        }
    }
    
    public enum Type {
            REGULAR, REGULAR2, ESCAPE, ERROR, IDENTIFIER
        }

    public record Item(int start, int end, Type type) {

        @Override
            public String toString() {
                return String.format("%d-%d[%s]", start, end, type);
            }

    }
    
    public static Runnable install(JTextPane pane, SyntaxHighlighter hl) {
        DefaultStyledDocument doc = new DefaultStyledDocument();
        pane.setDocument(doc);
        MutableAttributeSet defaultAttr = new SimpleAttributeSet();
        Runnable updateStyles = () -> {
            try {
                // Reset to default styles
                doc.setCharacterAttributes(0, doc.getLength(), defaultAttr, true);

                for (Item item : hl.getItems(pane.getText())) {
                    SimpleAttributeSet attr = new SimpleAttributeSet();
                    boolean dark = ColorCorrection.isDarkColor(pane.getBackground());
                    Color fg = ColorCorrection.correctReadability(Color.BLUE, pane.getBackground());
                    StyleConstants.setBackground(attr, ColorCorrectionNew.offset(pane.getBackground(), 0.89f));
                    switch (item.type) {
                        case REGULAR:
                            StyleConstants.setBold(attr, true);
                            StyleConstants.setForeground(attr, fg);
//                            StyleConstants.setBackground(attr, new Color(100, 100, 100));
                            break;
                        case REGULAR2:
                            StyleConstants.setBold(attr, true);
                            StyleConstants.setForeground(attr, fg);
//                            StyleConstants.setBackground(attr, ColorCorrectionNew.offset(getBackground(), 0.9f));
                            break;
                        case ESCAPE:
                            StyleConstants.setBold(attr, true);
//                            StyleConstants.setForeground(attr, new Color(120, 120, 255));
                            StyleConstants.setForeground(attr, ColorCorrectionNew.offset(pane.getForeground(), 0.7f));
                            break;
                        case IDENTIFIER:
                            StyleConstants.setForeground(attr, fg);
                            StyleConstants.setItalic(attr, true);
//                            StyleConstants.setBackground(attr, ColorCorrectionNew.offset(getBackground(), 0.84f));
                            break;
                        case ERROR:
                            StyleConstants.setForeground(attr, dark ? Color.ORANGE : Color.RED);
//                            StyleConstants.setBackground(attr, new Color(255, 180, 180));
                            StyleConstants.setUnderline(attr, true);
                            break;
                    }

                    doc.setCharacterAttributes(item.start, item.end - item.start, attr, false);
                }
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        };
        
        doc.setDocumentFilter(new DocumentFilter() {

            @Override
            public void insertString(DocumentFilter.FilterBypass fb, int offset, String string,
                                     AttributeSet attr) throws BadLocationException {
                fb.insertString(offset, StringUtil.removeLinebreakCharacters(string), attr);
                updateStyles.run();
            }

            @Override
            public void remove(DocumentFilter.FilterBypass fb, int offset, int length) throws
                    BadLocationException {
                fb.remove(offset, length);
                updateStyles.run();
            }

            @Override
            public void replace(DocumentFilter.FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                fb.replace(offset, length, StringUtil.removeLinebreakCharacters(text), attrs);
                updateStyles.run();
            }

        });
        updateStyles.run();
        return updateStyles;
    }
    
    private static HighlightPainter createPainter(Color color) {
        return new DefaultHighlighter.DefaultHighlightPainter(color);
    }
    
    public static Runnable install(JTextComponent comp, SyntaxHighlighter hl) {
        Highlighter.HighlightPainter highlightPainterError = new MyHighlightPainter();
        // For selected color to the shown, although might cause issues
        ((DefaultHighlighter)comp.getHighlighter()).setDrawsLayeredHighlights(false);
        Runnable update = () -> {
            comp.getHighlighter().removeAllHighlights();
            for (Item entry : hl.getItems(comp.getText())) {
                boolean dark = ColorCorrection.isDarkColor(comp.getBackground());
                HighlightPainter painter = switch (entry.type) {
                    case ESCAPE -> createPainter(dark ? new Color(95, 95, 255) : new Color(160, 160, 255));
                    case ERROR -> highlightPainterError;
                    case REGULAR2 -> createPainter(dark ? new Color(0, 125, 0) : new Color(50, 255, 50));
                    default -> createPainter(dark ? new Color(20, 138, 20) : new Color(125, 255, 125));
                };
                try {
                    comp.getHighlighter().addHighlight(entry.start, entry.end, painter);
                }
                catch (BadLocationException ex) {
                    // Ignore
                }
            }
            // Didn't always repaint highlights on other lines
            comp.repaint();
        };
        addChangeListener(comp.getDocument(), e -> update.run());
        return update;
    }
    
    /**
     * Draw underline. Could be problematic as it is when it goes over several
     * lines, but for marking the error position that shouldn't happen.
     */
    public static class MyHighlightPainter implements Highlighter.HighlightPainter {

        public void paint(Graphics g, int offs0, int offs1, Shape bounds, JTextComponent c) {
            try {
                TextUI mapper = c.getUI();
                Rectangle2D p0 = mapper.modelToView2D(c, offs0, Position.Bias.Forward);
                Rectangle2D p1 = mapper.modelToView2D(c, offs1, Position.Bias.Forward);
                g.setColor(ColorCorrection.isDarkColor(c.getBackground()) ? Color.ORANGE : Color.RED);
                Rectangle2D r = p0.createUnion(p1);
                g.fillRect((int) r.getX(), (int) (r.getY() + r.getHeight()) - 3, (int) r.getWidth(), 2);
            } catch (BadLocationException e) {
                // Ignore
            }
        }

    }
    
    public static void main(String[] args) {
        JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//        frame.add(create(new CommandSyntaxHighlighter()));
        Editor editor = new Editor(frame);
        editor.setSyntaxHighlighter(new CommandSyntaxHighlighter());
        frame.pack();
        frame.setVisible(true);
        editor.showDialog("abc", "", "abc");
    }
    
}
