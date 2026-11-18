
package chatty.gui.components;

import chatty.gui.components.textpane.ChannelTextPane;
import chatty.util.DateTime;
import chatty.util.Debugging;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Document;
import javax.swing.text.Element;
import java.awt.*;
import java.awt.event.ItemListener;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author tduva
 */
public class DebugWindow extends JFrame {
    
    private static final int MAX_NUMBER_LINES = 250;
    
    private final JCheckBox autoscroll = new JCheckBox("Autoscroll", true);
    private final JCheckBox logIrc = new JCheckBox("Irc log", false);
    private final JTextArea text;
    private final JTextArea textIrcLog;
    private final JTextArea textFFZLog;
    private final JTextArea textPubSubLog;
    private final JTextArea textEventSubLog;
    private final JTextArea otherLog;
    private final JTextArea timerLog;
    private final Map<JTextArea, ArrayDeque<String>> hiddenLines = new HashMap<>();

    public DebugWindow(ItemListener listener) {
        setTitle("Debug");

        // Normal log
        text = createLogArea();

        // Irc log
        textIrcLog = createLogArea();

        // FFZ WS log
        textFFZLog = createLogArea();
        
        // PubSub WS log
        textPubSubLog = createLogArea();
        
        // EventSub WS Log
        textEventSubLog = createLogArea();
        
        // Other Debug Stuff (Debugging class)
        otherLog = createLogArea();
        Debugging.registerForOutput(line -> SwingUtilities.invokeLater(() -> printLine(otherLog, line)));
        
        // Timer Log
        timerLog = createLogArea();
        

        // Tabs
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Log", new JScrollPane(text));
        tabs.addTab("Irc log", new JScrollPane(textIrcLog));
        tabs.addTab("FFZ-WS", new JScrollPane(textFFZLog));
        tabs.addTab("PubSub", new JScrollPane(textPubSubLog));
        tabs.addTab("EventSub", new JScrollPane(textEventSubLog));
        tabs.addTab("Other", new JScrollPane(otherLog));
        tabs.addTab("Timers", new JScrollPane(timerLog));
        
        // Settings (Checkboxes)
        logIrc.setToolTipText("Logging IRC traffic can reduce performance");
        JPanel settingsPanel = new JPanel();
        settingsPanel.add(autoscroll);
        settingsPanel.add(logIrc);
        
        // Add everything
        add(tabs, BorderLayout.CENTER);
        add(settingsPanel, BorderLayout.SOUTH);

        pack();
        
        logIrc.addItemListener(listener);
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(new Dimension(600,500));
    }
    
    private static JTextArea createLogArea() {
        // Caret to prevent scrolling
        DefaultCaret caret = new DefaultCaret();
        caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);

        JTextArea text = new JTextArea();
        text.setEditable(false);
        text.setFont(Font.decode(Font.MONOSPACED));
        text.setCaret(caret);
        return text;
    }
    
    public void printLine(String line) {
        printLine(text, line);
    }
    
    public void printLineIrc(String line) {
        printLine(textIrcLog, line);
    }
    
    public void printLineFFZ(String line) {
        printLine(textFFZLog, line);
    }
    
    public void printLinePubSub(String line) {
        printLine(textPubSubLog, line);
    }
    
    public void printLineEventSub(String line) {
        printLine(textEventSubLog, line);
    }
    
    public void printTimerLog(String line) {
        printLine(timerLog, line);
    }
    
    @Override
    public void setVisible(boolean visible) {
        if (visible) {
            for (Map.Entry<JTextArea, ArrayDeque<String>> entry : hiddenLines.entrySet()) {
                flushHiddenLines(entry.getKey(), entry.getValue());
            }
        }
        super.setVisible(visible);
    }

    private void printLine(JTextArea text, String line) {
        String formatted = "["+DateTime.currentTimeExact()+"] "+line+"\n";
        // Thread dumps showed EDT stalls of up to 79 seconds on macOS in text
        // area layout doing font fallback lookups for emoji. By elimination
        // these hidden text areas were the likely source (not proven), so
        // lines are buffered until the window is shown.
        if (!isVisible()) {
            ArrayDeque<String> pending = hiddenLines.computeIfAbsent(text, k -> new ArrayDeque<>());
            pending.addLast(formatted);
            if (pending.size() > MAX_NUMBER_LINES) {
                pending.removeFirst();
            }
            return;
        }
        try {
            Document doc = text.getDocument();
            doc.insertString(doc.getLength(), formatted, null);
            if (autoscroll.isSelected()) {
                text.setCaretPosition(doc.getLength());
            }
            clearSomeChat(doc);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void flushHiddenLines(JTextArea text, ArrayDeque<String> pending) {
        if (pending.isEmpty()) {
            return;
        }
        try {
            Document doc = text.getDocument();
            doc.insertString(doc.getLength(), String.join("", pending), null);
            pending.clear();
            int excess = doc.getDefaultRootElement().getElementCount() - MAX_NUMBER_LINES;
            if (excess > 0) {
                removeFirstLines(doc, excess);
            }
            if (autoscroll.isSelected()) {
                text.setCaretPosition(doc.getLength());
            }
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Removes some lines from the given Document so it won't exceed the maximum
     * number of lines.
     * 
     * @param doc 
     */
    public void clearSomeChat(Document doc) {
        int count = doc.getDefaultRootElement().getElementCount();
        if (count > MAX_NUMBER_LINES) {
            removeFirstLines(doc, 10);
        }
    }

    /**
     * Removes the given number of lines from the given Document.
     * 
     * @param doc
     * @param amount 
     */
    public void removeFirstLines(Document doc, int amount) {
        if (amount < 1) {
            amount = 1;
        }
        Element firstToRemove = doc.getDefaultRootElement().getElement(0);
        Element lastToRemove = doc.getDefaultRootElement().getElement(amount - 1);
        int startOffset = firstToRemove.getStartOffset();
        int endOffset = lastToRemove.getEndOffset();
        try {
            doc.remove(startOffset,endOffset);
        } catch (BadLocationException ex) {
            Logger.getLogger(ChannelTextPane.class.getName()).log(Level.SEVERE, null, ex);
        }
   }

    public JCheckBox getLogIrcCheckBox() {
        return logIrc;
    }
    
}
