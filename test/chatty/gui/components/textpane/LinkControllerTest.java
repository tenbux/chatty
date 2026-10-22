
package chatty.gui.components.textpane;

import chatty.Room;
import chatty.User;
import chatty.gui.components.textpane.ChannelTextPane.Attribute;
import chatty.gui.components.textpane.LinkController.SourceLine;
import org.junit.Test;

import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;

import static org.junit.Assert.*;

public class LinkControllerTest {

    private static final User USER = new User("abc", Room.createRegular("#source"));

    /**
     * Builds a single line the way ChannelTextPane prints a user message: the
     * line id as a paragraph attribute and the user on the name element.
     */
    private static Element makeMessageLine(Long lineId, User user) throws BadLocationException {
        DefaultStyledDocument doc = new DefaultStyledDocument();
        SimpleAttributeSet name = new SimpleAttributeSet();
        if (user != null) {
            name.addAttribute(Attribute.USER, user);
            name.addAttribute(Attribute.IS_USER_MESSAGE, true);
        }
        doc.insertString(0, "abc", name);
        doc.insertString(3, ": hello", new SimpleAttributeSet());
        if (lineId != null) {
            SimpleAttributeSet paragraph = new SimpleAttributeSet();
            paragraph.addAttribute(Attribute.LINE_ID, lineId);
            doc.setParagraphAttributes(0, 1, paragraph, false);
        }
        // An element of the message text, as a click on it would return
        return doc.getCharacterElement(5);
    }

    @Test
    public void should_returnSourceChannelAndLine_when_paneHasNoChannel() throws BadLocationException {
        SourceLine source = LinkController.getSourceLine(makeMessageLine(42L, USER), null);
        assertEquals(new SourceLine("#source", 42L), source);
    }

    @Test
    public void should_returnSource_when_paneChannelDiffers() throws BadLocationException {
        SourceLine source = LinkController.getSourceLine(makeMessageLine(42L, USER), "#other");
        assertEquals(new SourceLine("#source", 42L), source);
    }

    @Test
    public void should_returnNull_when_sourceIsPaneChannel() throws BadLocationException {
        assertNull(LinkController.getSourceLine(makeMessageLine(42L, USER), "#source"));
    }

    @Test
    public void should_returnNull_when_lineHasNoLineId() throws BadLocationException {
        assertNull(LinkController.getSourceLine(makeMessageLine(null, USER), null));
    }

    @Test
    public void should_returnNull_when_lineHasNoUser() throws BadLocationException {
        assertNull(LinkController.getSourceLine(makeMessageLine(42L, null), null));
    }

    @Test
    public void should_useLocalUserChannel_when_lineIsInfoMessage() throws BadLocationException {
        DefaultStyledDocument doc = new DefaultStyledDocument();
        doc.insertString(0, "info text", new SimpleAttributeSet());
        SimpleAttributeSet paragraph = new SimpleAttributeSet();
        paragraph.addAttribute(Attribute.LINE_ID, 7L);
        paragraph.addAttribute(Attribute.LOCAL_USER, USER);
        doc.setParagraphAttributes(0, 1, paragraph, false);

        SourceLine source = LinkController.getSourceLine(doc.getCharacterElement(2), null);
        assertEquals(new SourceLine("#source", 7L), source);
    }

}
