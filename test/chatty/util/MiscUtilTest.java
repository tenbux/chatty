
package chatty.util;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 *
 * @author tduva
 */
public class MiscUtilTest {
    
    @Test
    public void testParseArgs() {
        Map<String, String> result = new HashMap<>();
        String[] input;
        
        input = new String[]{"-cd","-channel","test"};
        result.put("cd", "");
        result.put("channel","test");
        assertEquals(MiscUtil.parseArgs(input), result);
        result.clear();
        
        input = new String[]{"-cd","-channel","test, abc"};
        result.put("cd", "");
        result.put("channel","test, abc");
        assertEquals(MiscUtil.parseArgs(input), result);
        result.clear();
        
        // Empty args
        input = new String[]{};
        assertEquals(MiscUtil.parseArgs(input), result);
        result.clear();
        
        // Empty argument key
        input = new String[]{"-"};
        result.put("", "");
        assertEquals(MiscUtil.parseArgs(input), result);
        result.clear();
        
        input = new String[]{"-", "abc test"};
        result.put("", "abc test");
        assertEquals(MiscUtil.parseArgs(input), result);
        result.clear();
        
        // Repeating the same argument key
        input = new String[]{"-channel","test, abc","-channel","jfwe"};
        result.put("channel","jfwe");
        assertEquals(MiscUtil.parseArgs(input), result);
        result.clear();
        
        // No argument key at all
        input = new String[]{"cd"};
        assertEquals(MiscUtil.parseArgs(input), result);
        result.clear();
    }
    
    @Test
    public void testSplitSet() {
        // Several elements
        Set<Integer> test = new TreeSet<>();
        for (int i=0;i<32;i++) {
            test.add(i);
        }
        List<Set<Integer>> target = new ArrayList<>();
        target.add(new HashSet<>(Arrays.asList(0,1,2,3,4,5,6,7,8,9)));
        target.add(new HashSet<>(Arrays.asList(10,11,12,13,14,15,16,17,18,19)));
        target.add(new HashSet<>(Arrays.asList(20,21,22,23,24,25,26,27,28,29)));
        target.add(new HashSet<>(Arrays.asList(30,31)));
        
        assertEquals(MiscUtil.splitSetByLimit(test, 10), target);
        
        // Double-check size
        for (Set<Integer> set : MiscUtil.splitSetByLimit(test, 10)) {
            assertTrue(set.size() <= 10);
        }
        
        for (Set<Integer> set : MiscUtil.splitSetByLimit(test, 2)) {
            assertTrue(set.size() <= 2);
        }
        
        for (Set<Integer> set : MiscUtil.splitSetByLimit(test, 0)) {
            assertEquals(1, set.size());
        }
        assertEquals(32, MiscUtil.splitSetByLimit(test, 0).size());
        
        for (Set<Integer> set : MiscUtil.splitSetByLimit(test, 1)) {
            assertEquals(1, set.size());
        }
        assertEquals(32, MiscUtil.splitSetByLimit(test, 1).size());
        
        // Only one element
        Set<String> test2 = new HashSet<>();
        test2.add("abc");
        
        List<Set<String>> target2 = new ArrayList<>();
        target2.add(test2);
        
        assertEquals(MiscUtil.splitSetByLimit(test2, 0), target2);
        assertEquals(MiscUtil.splitSetByLimit(test2, 1), target2);
        assertEquals(MiscUtil.splitSetByLimit(test2, 2), target2);
        assertEquals(MiscUtil.splitSetByLimit(test2, 100), target2);
    }
    
    @Test
    public void testAddLimited() {
        Set<String> source = new HashSet<>();
        source.add("abc");
        
        Set<String> target = new HashSet<>();
        
        MiscUtil.addLimited(source, target, 0);
        assertEquals(0, target.size());
        
        MiscUtil.addLimited(source, target, 1);
        assertEquals(1, target.size());
        
        for (int i=0;i<20;i++) {
            source.add(String.valueOf(i));
        }
        
        MiscUtil.addLimited(source, target, 10);
        assertEquals(10, target.size());
        
        MiscUtil.addLimited(source, target, 20);
        assertEquals(20, target.size());
        
        MiscUtil.addLimited(source, target, 30);
        assertEquals(21, target.size());
    }
    
}
