
package chatty.gui.components.settings;

import chatty.gui.colors.MsgColorItem;
import chatty.gui.components.LinkLabel;
import chatty.lang.Language;
import chatty.util.StringUtil;
import chatty.util.colors.HtmlColors;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author tduva
 */
public class MsgColorSettings extends SettingsPanel {
    
    private static final String INFO_TEXT = "<html><body>"
            + "Customize message colors based on Highlighting rules. "
            + "[help:Message_Colors More information..]";
    
    private final ItemColorEditor<MsgColorItem> data;
    
    public MsgColorSettings(SettingsDialog d) {
        super(true);
        
        JPanel main = addTitledPanel(Language.getString("settings.section.msgColors"), 0, true);
        JPanel other = addTitledPanel(Language.getString("settings.section.msgColorsOther"), 1);
        
        GridBagConstraints gbc;
        
        JCheckBox msgColorsEnabled = d.addSimpleBooleanSetting("msgColorsEnabled");
        gbc = SettingsDialog.makeGbc(0, 0, 1, 1);
        gbc.anchor = GridBagConstraints.WEST;
        main.add(msgColorsEnabled, gbc);
        
        data = new ItemColorEditor<>(d,
                MsgColorItem::new, true, new LinkLabel(HighlightSettings.getMatchingHelp("msgColors"), d.getLinkLabelListener()));
        data.setTableEditorEditAllHandler(new TableEditor.TableEditorEditAllHandler<>() {
            @Override
            public String toString(List<MsgColorItem> data) {
                StringBuilder b = new StringBuilder();
                for (MsgColorItem item : data) {
                    b.append(HtmlColors.getNamedColorString(item.getForeground()));
                    b.append(",");
                    b.append(item.getForegroundEnabled() ? "1" : "0");
                    b.append(",");
                    b.append(HtmlColors.getNamedColorString(item.getBackground()));
                    b.append(",");
                    b.append(item.getBackgroundEnabled() ? "1" : "0");
                    b.append(",");
                    b.append(item.getId());
                    b.append("\n");
                }
                return b.toString();
            }

            @Override
            public List<MsgColorItem> toData(String input) {
                List<MsgColorItem> result = new ArrayList<>();
                for (String line : StringUtil.splitLines(input)) {
                    String[] split = line.split(",", 5);
                    if (split.length == 5 && !split[4].isEmpty()) {
                        result.add(new MsgColorItem(split[4], HtmlColors.decode(split[0]), split[1].equals("1"), HtmlColors.decode(split[2]), split[3].equals("1")));
                    }
                }
                return result;
            }

            @Override
            public StringEditor getEditor() {
                return null;
            }

            @Override
            public String getEditorTitle() {
                return "Edit all entries";
            }

            @Override
            public String getEditorHelp() {
                return "Each line must contain:<br />"
                        + "<code>[foreground],[1/0],[background],[1/0],[match]</code><br />"
                        + "(<code>1</code> to enable foreground/background)";
            }
        });
        data.setPreferredSize(new Dimension(1,150));
        gbc = SettingsDialog.makeGbc(0, 1, 1, 1);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1;
        gbc.weighty = 1;
        main.add(data, gbc);
        
        SettingsUtil.addSubsettings(msgColorsEnabled, data);
        
        LinkLabel info = new LinkLabel(INFO_TEXT, d.getSettingsHelpLinkLabelListener());
        main.add(info, SettingsDialog.makeGbc(0, 2, 1, 1));
        
        other.add(d.addSimpleBooleanSetting("msgColorsPrefer"),
                SettingsDialog.makeGbc(0, 0, 1, 1, GridBagConstraints.WEST));
        
        other.add(d.addSimpleBooleanSetting("msgColorsLinks"),
                SettingsDialog.makeGbc(0, 1, 1, 1, GridBagConstraints.WEST));
        
        other.add(d.addSimpleBooleanSetting("actionColored"),
                SettingsDialog.makeGbc(0, 2, 1, 1, GridBagConstraints.WEST));
        
    }
    
    public void setData(List<MsgColorItem> data) {
        this.data.setData(data);
    }
    
    public List<MsgColorItem> getData() {
        return data.getData();
    }
    
    public void setDefaultForeground(Color color) {
        data.setDefaultForeground(color);
    }
    
    public void setDefaultBackground(Color color) {
        data.setDefaultBackground(color);
    }
    
    public void editItem(String item) {
        data.edit(item);
    }
    
    public void selectItem(String item) {
        data.setSelected(item);
    }
    
}
