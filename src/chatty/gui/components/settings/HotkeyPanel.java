
package chatty.gui.components.settings;

import chatty.lang.Language;
import chatty.util.hotkeys.Hotkey;

import javax.swing.*;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 *
 * @author tduva
 */
public class HotkeyPanel extends JPanel {

    private final JTextField hotkeyField = new JTextField(20);

    private final JButton removeButton = new JButton("Remove");
    
    private Hotkey currentHotkey;

    private Map<String, String> actions;

    // Created lazily on first use and reused for every subsequent click,
    // like HotkeyEditor itself does with the same class, instead of
    // creating (and never disposing) a new one - with its own JDialog -
    // every time "Edit" is clicked.
    private HotkeyEditor.MyItemEditor editor;

    public HotkeyPanel(JDialog owner, String actionId, Hotkey.Type type, Function<KeyStroke, Hotkey> getExistingHotkey, HotkeyHelperListener listener) {
        this.currentHotkey = new Hotkey(actionId, null, type, null, 1);

        add(hotkeyField);
        JButton editButton = new JButton("Edit");
        add(editButton);
        add(removeButton);

        hotkeyField.setEditable(false);

        editButton.addActionListener(e -> {
            if (editor == null) {
                editor = new HotkeyEditor.MyItemEditor(owner, getExistingHotkey);
            }
            editor.setActions(actions);
            editor.setFixedAction();
            Hotkey edited = editor.showEditor(currentHotkey, hotkeyField, true, 0);
            if (edited != null) {
                listener.changeHotkey(currentHotkey, edited);
                updateHotkey(currentHotkey, edited);
            }
        });
        
        removeButton.addActionListener(e -> {
            if (currentHotkey.keyStroke != null) {
                listener.deleteHotkey(currentHotkey);
                setCurrentHotkey(null);
            }
        });
        
        updateState();
    }
    
    public void updateHotkey(Hotkey current, Hotkey changed) {
        if (current == null) {
            if (changed.actionId.equals(currentHotkey.actionId)) {
                setCurrentHotkey(changed);
            }
        }
        else if (current.actionId.equals(currentHotkey.actionId)) {
            if (currentHotkey.keyStroke == null) {
                setCurrentHotkey(changed);
            }
            else if (currentHotkey.keyStroke.equals(current.keyStroke)) {
                setCurrentHotkey(changed);
            }
        }
    }
    
    public void setActions(Map<String, String> actions) {
        this.actions = actions;
    }
    
    private void setCurrentHotkey(Hotkey hotkey) {
        currentHotkey = Objects.requireNonNullElseGet(hotkey, () -> new Hotkey(currentHotkey.actionId, null, currentHotkey.type, null, currentHotkey.delay));
        updateState();
    }
    
    private void updateState() {
        if (currentHotkey == null || currentHotkey.keyStroke == null) {
            hotkeyField.setText(Language.getString("settings.hotkeys.key.empty"));
            removeButton.setEnabled(false);
        }
        else {
            hotkeyField.setText(currentHotkey.getHotkeyText());
            removeButton.setEnabled(true);
        }
    }
    
    public interface HotkeyHelperListener {
        
        void changeHotkey(Hotkey current, Hotkey changed);
        void deleteHotkey(Hotkey current);
        
    }
    
}
