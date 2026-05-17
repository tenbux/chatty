
package chatty.gui.components.settings;

import chatty.gui.GuiUtil;
import chatty.lang.Language;
import chatty.util.DateTime;
import chatty.util.MiscUtil;
import chatty.util.settings.FileManager;
import chatty.util.settings.FileManager.FileInfo;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author tduva
 */
public class BackupManager extends JDialog {
    
    private final JTable table;
    private final MyTableModel data;
    private final FileManager fileManager;
    private final JButton loadButton;
    private final JButton deleteButton;
    private final JTextArea infoText;
    
    public BackupManager(Window parent, FileManager fileManager) {
        super(parent);
        setTitle(Language.getString("settings.backup.title"));
        setLayout(new GridBagLayout());
        
        this.fileManager = fileManager;
        data = new MyTableModel();
        table = new JTable(data) {
            
            @Override
            public String getToolTipText(MouseEvent e) {
                int row = rowAtPoint(e.getPoint());
                int column = columnAtPoint(e.getPoint());
                if (row == -1 || column == -1) {
                    return null;
                }
                FileInfo info = data.getRowData(convertRowIndexToModel(row));
                column = convertColumnIndexToModel(column);
                return switch (column) {
                    case 0 -> info.file().toString();
                    case 1 -> String.format("%d bytes",
                            info.size());
                    case 2 -> String.format("Modified: %s",
                            DateTime.formatFullDatetime(info.modifiedTime()));
                    case 3 -> String.format("Backup Created: %s",
                            DateTime.formatFullDatetime(info.getCreated()));
                    case 4 -> info.info();
                    default -> null;
                };
            }
            
        };
        FontMetrics measure = new JLabel().getFontMetrics(table.getFont());
        table.getColumnModel().getColumn(0).setPreferredWidth(measure.stringWidth("manual_1586904521__settings")+20);
        table.getColumnModel().getColumn(1).setPreferredWidth(measure.stringWidth("12345")+10);
        table.getColumnModel().getColumn(2).setPreferredWidth(measure.stringWidth("12 hours ago")+10);
        table.getColumnModel().getColumn(3).setPreferredWidth(measure.stringWidth("12 hours ago")+10);
        table.getColumnModel().getColumn(4).setPreferredWidth(420);
        table.getColumnModel().getColumn(2).setCellRenderer(new AgoRenderer());
        table.getColumnModel().getColumn(3).setCellRenderer(new AgoRenderer());
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> update());
        table.setAutoCreateRowSorter(true);
        table.getRowSorter().setSortKeys(Arrays.asList(new RowSorter.SortKey(2, SortOrder.DESCENDING),
                new RowSorter.SortKey(3, SortOrder.DESCENDING)));
        
        GridBagConstraints gbc = GuiUtil.makeGbc(0, 0, 3, 1);
        
        add(new JLabel("<html><body width='600px'>"+SettingsUtil.getInfo("info-backup.html", null)), gbc);
        
        gbc = GuiUtil.makeGbc(0, 1, 3, 1);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1;
        gbc.weighty = 0.8;
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(table.getPreferredSize().width, 200));
        add(tableScroll, gbc);

        JButton openDirButton = new JButton("Open Backup Directory");
        openDirButton.addActionListener(e -> MiscUtil.openFile(fileManager.getBackupPath().toFile(), this));
        gbc = GuiUtil.makeGbc(0, 2, 1, 1);
        gbc.weightx = 2.0;
        gbc.anchor = GridBagConstraints.WEST;
        add(openDirButton, gbc);
        
        deleteButton = new JButton("Delete Selected Backup");
        deleteButton.addActionListener(e -> deleteBackup());
        gbc = GuiUtil.makeGbc(2, 2, 1, 1);
        add(deleteButton, gbc);
        
        loadButton = new JButton("Load Selected Backup");
        loadButton.addActionListener(e -> loadBackup());
        gbc = GuiUtil.makeGbc(1, 2, 1, 1);
        add(loadButton, gbc);
        
        infoText = new JTextArea();
        infoText.setEditable(false);
        infoText.setRows(5);
        infoText.setMinimumSize(infoText.getPreferredSize());
        gbc = GuiUtil.makeGbc(0, 3, 3, 1);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1;
        gbc.weighty = 0.4;
        add(new JScrollPane(infoText), gbc);
    }
    
    private void update() {
        FileInfo selected = getSelected();
        loadButton.setEnabled(selected != null);
        deleteButton.setEnabled(selected != null);
        if (selected != null) {
            infoText.setText(String.format("The following file will be copied when loading the backup:\n[From]\n %s\n[To]\n %s",
                    selected.file(), selected.settings().path()));
        }
    }
    
    private FileInfo getSelected() {
        int selected = table.getSelectedRow();
        if (selected != -1) {
            return data.getRowData(table.convertRowIndexToModel(selected));
        }
        return null;
    }
    
    private void deleteBackup() {
        FileInfo selected = getSelected();
        if (selected != null) {
            try {
                int selectedRow = table.getSelectedRow();
                Files.delete(selected.file());
                infoText.setText(String.format("Deleted %s",
                        selected.file()));
                refreshList();
                // List data is reset, so set selection again
                table.getSelectionModel().setSelectionInterval(0,
                        selectedRow < table.getRowCount() ? selectedRow : table.getRowCount() - 1);
            }
            catch (IOException ex) {
                infoText.setText("Error deleting backup: "+ex);
            }
        }
    }
    
    private void loadBackup() {
        FileInfo selected = getSelected();
        if (selected != null) {
            try {
                fileManager.loadBackup(selected);
                infoText.setText(String.format("[Copied]\n %s\n[To]\n %s\nYou may restart Chatty now or load another backup.",
                        selected.file(), selected.settings().path()));
            }
            catch (IOException ex) {
                infoText.setText("Error loading backup: "+ex);
            }
        }
    }
    
    private void refreshList() {
        try {
            data.setData(fileManager.getBackupFileInfo().getList());
        }
        catch (IOException ex) {
            infoText.setText("Error loading list: "+ex);
        }
    }
    
    public void open() {
        refreshList();
        update();
        pack();
        setVisible(true);
    }
    
    private static class MyTableModel extends AbstractTableModel {
        
        private final String[] COLUMNS = {"Filename", "Size", "Modified", "Created", "Info"};
        
        private final List<FileInfo> data = new ArrayList<>();
        
        public void setData(List<FileManager.FileInfo> data) {
            this.data.clear();
            this.data.addAll(data);
            super.fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return data.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }
        
        public FileInfo getRowData(int index) {
            return data.get(index);
        }
        
        @Override
        public String getColumnName(int columnIndex) {
            return COLUMNS[columnIndex];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            FileInfo info = data.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> info.file().getFileName();
                case 1 -> info.size();
                case 2 -> info.modifiedTime();
                case 3 -> info.getCreated();
                case 4 -> info.info();
                default -> null;
            };
        }

    }
    
    private static class AgoRenderer extends DefaultTableCellRenderer {
        
        @Override
        protected void setValue(Object value) {
            if (value instanceof Long) {
                setText(DateTime.agoText((Long)value));
            }
            else {
                setText("");
            }
        }
        
    }
    
}
