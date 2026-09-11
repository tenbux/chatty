
package chatty.util.api.usericons;

import org.junit.Test;

import java.awt.Dimension;

import static org.junit.Assert.assertEquals;

/**
 * Regression test for Usericon.toHeight(): "width / (height / targetHeight)"
 * used integer division for the inner ratio, which is 0 whenever height is
 * smaller than targetHeight (as for a small custom badge image), throwing
 * ArithmeticException (divide by zero) on the EDT during chat rendering.
 */
public class UsericonTest {

    @Test
    public void testToHeight_smallerThanTarget_doesNotThrow() {
        Dimension result = Usericon.toHeight(new Dimension(10, 8), 18);
        assertEquals(18, result.height);
        assertEquals(23, result.width);
    }

    @Test
    public void testToHeight_largerThanTarget_scalesDown() {
        Dimension result = Usericon.toHeight(new Dimension(72, 36), 18);
        assertEquals(18, result.height);
        assertEquals(36, result.width);
    }

    @Test
    public void testToHeight_equalToTarget_unchanged() {
        Dimension result = Usericon.toHeight(new Dimension(18, 18), 18);
        assertEquals(18, result.height);
        assertEquals(18, result.width);
    }

    @Test
    public void testToHeight_zeroHeight_doesNotThrow() {
        Dimension result = Usericon.toHeight(new Dimension(10, 0), 18);
        assertEquals(18, result.height);
        assertEquals(10, result.width);
    }

}
