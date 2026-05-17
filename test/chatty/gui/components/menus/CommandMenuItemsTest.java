
package chatty.gui.components.menus;

import chatty.util.commands.CustomCommand;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author tduva
 */
public class CommandMenuItemsTest {
    
    @Test
    public void testParse() {
        
        assertEquals(new ArrayList<>(), CommandMenuItems.parse(null));
        assertEquals(new ArrayList<>(), CommandMenuItems.parse(""));
        
        String input1 = """
                \
                Test=/Hello
                Test{0}=/Hello
                Test{0}[k]=/Hello
                Test[k]=/Hello
                Test [k]=/Hello
                /Test /Test{0} /Test{0}[k] /Test[k]
                /Hello_World
                @Menu Name
                @Menu Name{0}
                @Menu Name{0}[k]
                .-
                -
                -{0}
                -{1}
                .-{0}
                1m 1m[ctrl+t] 1m[ctrl+t|ct] 1m{0}
                ./Test //Test
                
                . Test = /Test $1-\s
                60s 1m 60
                Uptime=Stream Uptime: $(streamuptime)""";
        
        List<CommandMenuItem> expected1 = new LinkedList<>();
        expected1.add(new CommandMenuItem("Test", CustomCommand.parse("/Hello"), null, -1, null, null, 1));
        expected1.add(new CommandMenuItem("Test", CustomCommand.parse("/Hello"), null, 0, null, null, 2));
        expected1.add(new CommandMenuItem("Test", CustomCommand.parse("/Hello"), null, 0, "k", null, 3));
        expected1.add(new CommandMenuItem("Test", CustomCommand.parse("/Hello"), null, -1, "k", null, 4));
        expected1.add(new CommandMenuItem("Test", CustomCommand.parse("/Hello"), null, -1, "k", null, 5));
        expected1.add(new CommandMenuItem("Test", CustomCommand.parse("/Test $1-"), null, -1, null, null, 6));
        expected1.add(new CommandMenuItem("Test", CustomCommand.parse("/Test $1-"), null, 0, null, null, 6));
        expected1.add(new CommandMenuItem("Test", CustomCommand.parse("/Test $1-"), null, 0, "k", null, 6));
        expected1.add(new CommandMenuItem("Test", CustomCommand.parse("/Test $1-"), null, -1, "k", null, 6));
        expected1.add(new CommandMenuItem("Hello World", CustomCommand.parse("/Hello_World $1-"), null, -1, null, null, 7));
        expected1.add(new CommandMenuItem("Menu Name", null, null, -1, null, null, 8));
        expected1.add(new CommandMenuItem("Menu Name", null, null, 0, null, null, 9));
        expected1.add(new CommandMenuItem("Menu Name", null, null, 0, "k", null, 10));
        expected1.add(new CommandMenuItem(null, null, "Menu Name", -1, null, null, 11));
        expected1.add(new CommandMenuItem(null, null, null, -1, null, null, 12));
        expected1.add(new CommandMenuItem(null, null, null, 0, null, null, 13));
        expected1.add(new CommandMenuItem(null, null, null, 1, null, null, 14));
        expected1.add(new CommandMenuItem(null, null, "Menu Name", 0, null, null, 15));
        expected1.add(new CommandMenuItem("1m", CustomCommand.parse("/timeout $1 60 $2-"), null, -1, null, null, 16));
        expected1.add(new CommandMenuItem("1m", CustomCommand.parse("/timeout $1 60 $2-"), null, -1, "ctrl+t", null, 16));
        expected1.add(new CommandMenuItem("1m", CustomCommand.parse("/timeout $1 60 $2-"), null, -1, "ctrl+t|ct", null, 16));
        expected1.add(new CommandMenuItem("1m", CustomCommand.parse("/timeout $1 60 $2-"), null, 0, null, null, 16));
        expected1.add(new CommandMenuItem("Test", CustomCommand.parse("/Test $1-"), "Menu Name", -1, null, null, 17));
        expected1.add(new CommandMenuItem("Test", CustomCommand.parse("/Test $1-"), CommandMenuItems.CUSTOM_COMMANDS_SUBMENU, -1, null, null, 17));
        expected1.add(new CommandMenuItem("Test", CustomCommand.parse("/Test $1-"), "Menu Name", -1, null, null, 19));
        expected1.add(new CommandMenuItem("60s", CustomCommand.parse("/timeout $1 60 $2-"), null, -1, null, null, 20));
        expected1.add(new CommandMenuItem("1m", CustomCommand.parse("/timeout $1 60 $2-"), null, -1, null, null, 20));
        expected1.add(new CommandMenuItem("1m", CustomCommand.parse("/timeout $1 60 $2-"), null, -1, null, null, 20));
        expected1.add(new CommandMenuItem("Uptime", CustomCommand.parse("Stream Uptime: $(streamuptime)"), null, -1, null, null, 21));
        
        List<CommandMenuItem> actual1 = CommandMenuItems.parse(input1);
        
        assertEquals(expected1.size(), actual1.size());
        for (int i = 0; i < expected1.size(); i++) {
            assertEquals(expected1.get(i), actual1.get(i));
        }
        
        String input2 = """
                \
                [mod $is(mystatus:M) ]
                1s 5m 30m /ban /unban
                [ $is(status:M) ]
                Warn=/me slaps $$1 around a bit with a large trout
                [/mod]""";
        
        List<CustomCommand> restriction1 = new ArrayList<>();
        restriction1.add(CustomCommand.parse("$is(mystatus:M)"));
        
        List<CustomCommand> restriction2 = new ArrayList<>();
        restriction2.add(CustomCommand.parse("$is(status:M)"));
        restriction2.add(CustomCommand.parse("$is(mystatus:M)"));
        
        List<CommandMenuItem> expected2 = new LinkedList<>();
        expected2.add(new CommandMenuItem("1s", CustomCommand.parse("/timeout $1 1 $2-"), null, -1, null, restriction1, 2));
        expected2.add(new CommandMenuItem("5m", CustomCommand.parse("/timeout $1 300 $2-"), null, -1, null, restriction1, 2));
        expected2.add(new CommandMenuItem("30m", CustomCommand.parse("/timeout $1 1800 $2-"), null, -1, null, restriction1, 2));
        expected2.add(new CommandMenuItem("ban", CustomCommand.parse("/ban $1-"), null, -1, null, restriction1, 2));
        expected2.add(new CommandMenuItem("unban", CustomCommand.parse("/unban $1-"), null, -1, null, restriction1, 2));
        expected2.add(new CommandMenuItem("Warn", CustomCommand.parse("/me slaps $$1 around a bit with a large trout"), null, -1, null, restriction2, 4));
        
        List<CommandMenuItem> actual2 = CommandMenuItems.parse(input2);
        
        assertEquals(expected2.size(), actual2.size());
        for (int i = 0; i < expected2.size(); i++) {
            assertEquals(expected2.get(i), actual2.get(i));
        }
    }
    
}
