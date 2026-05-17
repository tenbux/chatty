
package chatty.util.hotkeys;

import javax.swing.*;

/**
 * An action to be performed by a hotkey, with an id, label and the actual
 * action to be performed.
 *
 * @author tduva
 */
public record HotkeyAction(String id, String label, String description, Action action) {

}
