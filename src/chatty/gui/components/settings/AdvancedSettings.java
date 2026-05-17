
package chatty.gui.components.settings;

import chatty.WhisperManager;
import chatty.gui.components.LinkLabel;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stuff.
 * 
 * @author tduva
 */
public class AdvancedSettings extends SettingsPanel {
    
    public AdvancedSettings(final SettingsDialog d) {

        JPanel connection = addTitledPanel("Connection", 1);

        connection.add(new JLabel("Server:"),
                SettingsDialog.makeGbc(0, 0, 1, 1, GridBagConstraints.EAST));
        connection.add(d.addSimpleStringSetting("serverDefault", 20, true),
                SettingsDialog.makeGbc(1, 0, 1, 1, GridBagConstraints.WEST));
        
        connection.add(new JLabel("Ports:"),
                SettingsDialog.makeGbc(0, 1, 1, 1, GridBagConstraints.EAST));
        connection.add(d.addSimpleStringSetting("portDefault", 14, true),
                SettingsDialog.makeGbc(1, 1, 1, 1, GridBagConstraints.WEST));
        
        connection.add(new JLabel("(These might be overridden by commandline parameters.)"),
                SettingsDialog.makeGbc(0, 2, 2, 1));
        
        connection.add(d.addSimpleBooleanSetting("membershipEnabled",
                "Correct Userlist (receives joins/parts, userlist)",
                "Enables the membership capability while connecting, which allows receiving of joins/parts/userlist"),
                SettingsDialog.makeGbc(0, 4, 2, 1, GridBagConstraints.NORTHWEST));
        
        JPanel login = addTitledPanel("Login Settings (login under <Main Menu - Login>)", 2);
        
        login.add(d.addSimpleBooleanSetting("allowTokenOverride",
                "<html><body>Allow <code>-token</code> parameter to override existing token", 
                "If enabled, the -token commandline argument will replace an existing token (which can cause issues)"),
                SettingsDialog.makeGbc(0, 5, 2, 1, GridBagConstraints.WEST));
        
        JPanel whisper = addTitledPanel("Whisper (experimental, read help!)", 3);
        
        whisper.add(
                d.addSimpleBooleanSetting("whisperEnabled", "Whisper Enabled",
                        "Connects to group chat to allow for whispering"),
                SettingsDialog.makeGbc(0, 0, 3, 1, GridBagConstraints.WEST)
        );
        
        whisper.add(
                d.addSimpleBooleanSetting("whisperWhitelist", "Whitelist",
                        "Only users in the Addressbook category 'whisper' may send messages to you."),
                SettingsDialog.makeGbc(4, 1, 1, 1, GridBagConstraints.EAST)
        );
        
        whisper.add(new JLabel("Display:"),
                SettingsDialog.makeGbc(3, 0, 1, 1));
        
        Map<Long, String> displayMode = new LinkedHashMap<>();
        displayMode.put((long) WhisperManager.DISPLAY_IN_CHAT, "Active Chat");
        displayMode.put((long) WhisperManager.DISPLAY_ONE_WINDOW, "One Window");
        displayMode.put((long) WhisperManager.DISPLAY_PER_USER, "Per User");
        ComboLongSetting displayModeSetting = new ComboLongSetting(displayMode);
        d.addLongSetting("whisperDisplayMode", displayModeSetting);
        whisper.add(displayModeSetting,
                SettingsDialog.makeGbc(4, 0, 1, 1));
        
        whisper.add(new LinkLabel("[help-whisper:top Whisper Help]", d.getLinkLabelListener()),
                SettingsDialog.makeGbc(2, 1, 2, 1));
        
        
        whisper.add(d.addSimpleBooleanSetting("whisperAutoRespond", "Auto-respond to ignored/non-whitelisted users",
                "Sends an automatic message telling users you didn't receive their message"),
                SettingsDialog.makeGbc(0, 2, 5, 1, GridBagConstraints.WEST));
        
        whisper.add(SettingsUtil.createPanel("whisperAutoRespondCustom",
                d.addEditorStringSetting("whisperAutoRespondCustom", 10, true, "Custom Message", false, null)),
                SettingsDialog.makeGbc(0, 3, 5, 1));
    }
}
