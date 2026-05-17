
package chatty.util.irc;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author tduva
 */
public class MsgTagsTest {
    
    @Test
    public void test() {
        MsgTags emptyTags1 = MsgTags.EMPTY;
        assertTrue(emptyTags1.isEmpty());
        assertNull(emptyTags1.get("abc"));
        assertFalse(emptyTags1.containsKey("abc"));
        assertFalse(emptyTags1.hasInteger("abc"));
        
        MsgTags emptyTags2 = MsgTags.parse(null);
        assertTrue(emptyTags2.isEmpty());
        
        MsgTags emptyTags3 = MsgTags.parse("");
        assertTrue(emptyTags3.isEmpty());
        
        MsgTags tags1 = MsgTags.parse("abc");
        assertFalse(tags1.isEmpty());
        assertNull(tags1.get("abc"));
        assertFalse(tags1.isTrue("abc"));
        
        MsgTags tags2 = MsgTags.parse("key=value");
        assertFalse(tags2.isEmpty());
        assertEquals("value", tags2.get("key"));
        assertFalse(tags2.isTrue("key"));
        assertEquals(-1, tags2.getInteger("key", -1));
        assertEquals(-1, tags2.getInteger("abc", -1));
        
        MsgTags tags3 = MsgTags.parse("badges=turbo/1;color=#0000FF;display-name=tduva;emote-sets=0,33,130,19194,19655;mod=0;subscriber=0;user-type=");
        assertFalse(tags3.isEmpty());
        assertEquals("tduva", tags3.get("display-name"));
        assertEquals("", tags3.get("user-type"));
        assertEquals("", tags3.get("user-type", null));
        assertTrue(tags3.hasInteger("subscriber"));
        assertFalse(tags3.hasInteger("user-type"));
        assertFalse(tags3.hasInteger("color"));
        
        MsgTags tags4 = MsgTags.parse("ban-duration=1;ban-reason=test\\smessage");
        assertFalse(tags4.isEmpty());
        assertEquals("test message", tags4.get("ban-reason"));
        assertEquals(1, tags4.getLong("ban-duration", -1));
    }
    
    @Test
    public void testToTagsString() {
        MsgTags tags1 = MsgTags.parse("ban-duration=1;ban-reason=test\\smessage\\:\\stest\\\\");
        String tags1String = tags1.toTagsString();
        MsgTags tags1Reparsed = MsgTags.parse(tags1String);
        System.out.println("1-O: "+tags1);
        System.out.println("1-I: "+tags1String);
        System.out.println("1-R: "+tags1Reparsed);
        assertFalse(tags1String.contains(" "));
        assertEquals(tags1, tags1Reparsed);
    }
    
    @Test
    public void testMerge() {
        MsgTags tags1 = MsgTags.create("a", "1", "b", "2");
        MsgTags tags2 = MsgTags.create("b", "3", "c", "4");
        assertEquals(MsgTags.create("a", "1", "b", "2", "c", "4"), MsgTags.merge(tags1, tags2));
        assertEquals(MsgTags.create("a", "1", "b", "3", "c", "4"), MsgTags.merge(tags2, tags1));
    }
    
}
