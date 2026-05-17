
package chatty;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 *
 * @author tduva
 */
public class RoomManagerTest {
    
    @Test
    public void testAddRoomsIfNone() {
        RoomManager m = new RoomManager(u -> {});
        Room r1 = Room.createRegular("#channel");
        Room r2 = Room.createRegular("#channel");
        m.addRoomsIfNone(List.of(r1));
        assertSame(m.getRoom("#channel"), r1);
        m.addRoomsIfNone(List.of(r2));
        assertSame(m.getRoom("#channel"), r1);
        m.addRoom(r2);
        assertNotSame(m.getRoom("#channel"), r1);
        assertSame(m.getRoom("#channel"), r2);
        
        RoomManager m2 = new RoomManager(u -> {});
        m2.getRoom("#channel");
        m2.addRoomsIfNone(List.of(r2));
        assertNotSame(m2.getRoom("#channel"), r2);
    }
    
    @Test
    public void testOwnerChannel() {
        RoomManager m = new RoomManager(u -> {});
        Room r1 = Room.createRegular("#channel");
        m.addRoom(r1);
        assertSame(m.getRoomsByOwner("#channel").iterator().next(), r1);
        Room r2 = Room.createFromChannel("#chatrooms:7236692:832eaedf-6b94-4ba3-8c63-a0d854a0237b", "spoilers", "#channel");
        m.addRoom(r2);
        assertEquals(2, m.getRoomsByOwner("#channel").size());
        assertTrue(m.getRoomsByOwner("#otherchannel").isEmpty());
        m.addRoom(Room.createRegular("$name"));
        assertEquals(1, m.getRoomsByOwner("$name").size());
    }
    
}
