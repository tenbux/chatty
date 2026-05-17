
package chatty.gui.components.settings;

import chatty.lang.Language;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

/**
 *
 * @author tduva
 */
public class HotkeyDialog {
    
    private final JDialog dialog;
    
    private KeyStroke hotkey;
    
    public HotkeyDialog() {
        dialog = new JDialog(KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow(), Language.getString("settings.hotkeys.key.set.title"));
        dialog.setModal(true);
        dialog.addKeyListener(new KeyListener() {
            
            @Override
            public void keyPressed(KeyEvent e) {
                e.consume();
            }

            @Override
            public void keyTyped(KeyEvent e) {
                e.consume();
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE && e.getModifiersEx() == 0) {
                    dialog.setVisible(false);
                    return;
                }
                hotkey = KeyStroke.getKeyStroke(e.getKeyCode(), e.getModifiersEx());
                dialog.setVisible(false);
                e.consume();
            }
        
        });
        dialog.setFocusTraversalKeysEnabled(false);
        dialog.setLocationRelativeTo(dialog.getParent());
        JLabel info = new JLabel(Language.getString("settings.hotkeys.key.set.info"));
        info.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        dialog.add(info);
        dialog.pack();
    }
    
    private KeyStroke getKeyStrokeInternal() {
        dialog.setVisible(true);
        return hotkey;
    }
    
    public static KeyStroke getKeyStroke() {
        return new HotkeyDialog().getKeyStrokeInternal();
    }
    
}
