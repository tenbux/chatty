
package chatty.gui.components.srl;

import chatty.gui.GuiUtil;
import chatty.util.srl.Race;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * Dialog with a {@code RacesTable} and a reload button, also showing how long
 * ago the current data was loaded.
 * 
 * @author tduva
 */
public class SRLRaces extends JDialog {
    
    private final RacesTable table;
    private final SRL srl;
    
    private final JLabel info = new JLabel();
    private final JButton reloadButton = new JButton("reload");
  
    public SRLRaces(Window owner, final SRL srl) {
        super(owner);
        setTitle("SpeedRunsLive");
        this.srl = srl;

        table = new RacesTable(srl::openRaceInfo);
        
        // Layout
        setLayout(new GridBagLayout());
        
        GridBagConstraints gbc;
        
        gbc = GuiUtil.makeGbc(0, 0, 1, 1);
        add(info, gbc);
        
        gbc = GuiUtil.makeGbc(1, 0, 1, 1);
        GuiUtil.smallButtonInsets(reloadButton);
        reloadButton.setIcon(new ImageIcon(SRLRaces.class.getResource("view-refresh.png")));
        add(reloadButton, gbc);
        
        gbc = GuiUtil.makeGbc(0, 1, 2, 1);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1;
        gbc.weighty = 1;
        add(new JScrollPane(table), gbc);
        
        ActionListener buttonAction = new ButtonAction();
        reloadButton.addActionListener(buttonAction);
        
        setPreferredSize(new Dimension(600,400));
        
        pack();
    }
    
    protected void setStatusText(String text) {
        info.setText(text);
    }
    
    protected void setLoading(boolean loading) {
        reloadButton.setEnabled(!loading);
    }
    
    public void setRaces(List<Race> newRaces) {
        table.setData(newRaces);
        setTitle(newRaces.size() + " Races - SpeedRunsLive");
        setLoading(false);
    }
    
    public void showDialog() {
        setVisible(true);
    }
    
    private class ButtonAction implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            if (e.getSource() == reloadButton) {
                srl.reload();
            }
        }
    }
    
}
