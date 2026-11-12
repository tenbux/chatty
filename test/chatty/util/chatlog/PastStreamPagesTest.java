package chatty.util.chatlog;

import chatty.util.chatlog.PastStreamFinder.PastStream;
import chatty.util.chatlog.StreamRangeResolver.StreamRange;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PastStreamPagesTest {

    private static PastStream page(long startMs) {
        return new PastStream(new StreamRange(startMs, startMs + 1000, false), List.of(), 0);
    }

    @Test
    public void test_newPages_atNewest_noCurrent() {
        PastStreamPages pages = new PastStreamPages();

        assertEquals(0, pages.index());
        assertNull(pages.current());
        assertFalse(pages.hasCachedOlder());
        assertEquals(5000, pages.backAnchor(5000));
    }

    @Test
    public void test_addOlderAndShow_movesBack_backAnchorIsItsStart() {
        PastStreamPages pages = new PastStreamPages();
        PastStream found = page(3000);

        pages.addOlderAndShow(found);

        assertEquals(1, pages.index());
        assertSame(found, pages.current());
        assertEquals(3000, pages.backAnchor(5000));
    }

    @Test
    public void test_forwardThenBack_usesCache_noSearchNeeded() {
        PastStreamPages pages = new PastStreamPages();
        PastStream found = page(3000);
        pages.addOlderAndShow(found);

        pages.forward();
        assertEquals(0, pages.index());
        assertTrue(pages.hasCachedOlder());
        pages.back();

        assertEquals(1, pages.index());
        assertSame(found, pages.current());
        assertFalse(pages.hasCachedOlder());
    }

    @Test
    public void test_forward_atNewest_staysZero() {
        PastStreamPages pages = new PastStreamPages();

        pages.forward();

        assertEquals(0, pages.index());
    }

    @Test
    public void test_markNoOlder_blocksSearchFromThatAnchor() {
        PastStreamPages pages = new PastStreamPages();
        pages.addOlderAndShow(page(3000));
        assertTrue(pages.canSearchOlder(5000));

        pages.markNoOlder(3000);

        assertFalse(pages.canSearchOlder(5000));
        pages.forward();
        assertTrue(pages.canSearchOlder(5000));
    }

    @Test
    public void test_reanchor_sameValue_keepsPages() {
        PastStreamPages pages = new PastStreamPages();
        pages.reanchor(5000);
        pages.addOlderAndShow(page(3000));
        pages.markNoOlder(3000);

        pages.reanchor(5000);

        assertEquals(1, pages.index());
        assertFalse(pages.canSearchOlder(5000));
    }

    @Test
    public void test_reanchor_changedOnPastPage_keepsOnlyCurrentAtIndexOne() {
        PastStreamPages pages = new PastStreamPages();
        pages.reanchor(5000);
        pages.addOlderAndShow(page(3000));
        PastStream shown = page(1000);
        pages.addOlderAndShow(shown);
        pages.markNoOlder(1000);

        pages.reanchor(6000);

        assertEquals(1, pages.index());
        assertSame(shown, pages.current());
        assertFalse(pages.canSearchOlder(6000));
        assertFalse(pages.hasCachedOlder());
        pages.forward();
        assertEquals(0, pages.index());
        pages.back();
        assertSame(shown, pages.current());
    }

    @Test
    public void test_reanchor_changedOnPastPage_dropsMarkForOtherStart() {
        PastStreamPages pages = new PastStreamPages();
        pages.reanchor(5000);
        pages.addOlderAndShow(page(3000));
        pages.markNoOlder(3000);
        pages.addOlderAndShow(page(1000));

        pages.reanchor(6000);

        assertTrue(pages.canSearchOlder(6000));
    }

    @Test
    public void test_reanchor_changedAtNewest_dropsAll() {
        PastStreamPages pages = new PastStreamPages();
        pages.reanchor(5000);
        pages.addOlderAndShow(page(3000));
        pages.markNoOlder(3000);
        pages.forward();

        pages.reanchor(6000);

        assertEquals(0, pages.index());
        assertFalse(pages.hasCachedOlder());
        assertTrue(pages.canSearchOlder(6000));
    }

    @Test
    public void test_reset_forgetsAnchor_nextReanchorOnlyRecords() {
        PastStreamPages pages = new PastStreamPages();
        pages.reanchor(5000);
        pages.reset();
        PastStream found = page(3000);
        pages.addOlderAndShow(found);
        pages.forward();

        pages.reanchor(6000);

        assertEquals(0, pages.index());
        assertTrue(pages.hasCachedOlder());
        pages.back();
        assertSame(found, pages.current());
    }

    @Test
    public void test_addOlderAndShow_afterForward_dropsPagesBeyond() {
        PastStreamPages pages = new PastStreamPages();
        pages.addOlderAndShow(page(3000));
        pages.addOlderAndShow(page(1000));
        pages.forward();
        PastStream replacement = page(2000);

        pages.addOlderAndShow(replacement);

        assertEquals(2, pages.index());
        assertSame(replacement, pages.current());
        assertFalse(pages.hasCachedOlder());
    }
}
