
package chatty.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 *
 * @author tduva
 */
public class HalfWeakSet2Test {

    @Test
    public void test() {
        // Using constructor to create objects that can be gargabe collected
        Object a = "a";
        Object b = "b";
        Object c = "c";
        
        HalfWeakSet2<Object> s = new HalfWeakSet2<>();
        s.add(a);
        checkIterator(s, a);
        checkIterator(s.strongIterator(), a);
        s.add(a);
        checkIterator(s, a);
        checkIterator(s.strongIterator(), a);
        s.markWeak(a);
        checkIterator(s, a);
        checkIterator(s.strongIterator());
        
        s.markStrong(b);
        checkIterator(s, a);
        checkIterator(s.strongIterator());
        s.markWeak(b);
        checkIterator(s, a);
        checkIterator(s.strongIterator());
        s.add(b);
        checkIterator(s, a, b);
        checkIterator(s.strongIterator(), b);
        
        s.add(a);
        checkIterator(s, a, b);
        checkIterator(s.strongIterator(), a, b);
        
        s.add(c);
        checkIterator(s, a, b, c);
        checkIterator(s.strongIterator(), a, b, c);
        
        s.clear();
        checkIterator(s);
        
        /**
         * Not sure that it makes sense to have this test, since it may not be
         * guaranteed that the gc cleans up the object. Leave commented out for
         * now.
         */
//        s.add(a);
//        s.add(b);
//        s.add(c);
//        s.markWeak(a);
//        System.gc();
//        checkIterator(s, a, b, c);
//        
//        a = null;
//        b = null;
//        c = null;
//        System.gc();
//        checkIterator(s, "b", "c");
    }
    
    private static void checkIterator(Iterable<Object> ic, Object... expected) {
        checkIterator(ic.iterator(), expected);
    }
    
    private static void checkIterator(Iterator<Object> it, Object... expected) {
        List<Object> expectedValues = new ArrayList<>(Arrays.asList(expected));
        while (it.hasNext()) {
            Object o = it.next();
            assertTrue("Unexpected value: "+o, expectedValues.remove(o));
        }
        assertTrue("Expected values not seen: "+expectedValues, expectedValues.isEmpty());
    }
    
}
