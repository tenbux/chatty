package chatty.util.chatlog;

import chatty.util.chatlog.PastStreamFinder.PastStream;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/**
 * Tracks the past streams found so far and which page is shown. Page 0 is the
 * newest stream (not held here); pages 1 and up are progressively older past
 * streams.
 * <p>
 * Mutable and not synchronized: use it from the EDT only.
 */
public class PastStreamPages {

    // Element 0 holds page 1.
    private final List<PastStream> older = new ArrayList<>();
    private int index = 0;
    private OptionalLong noOlderAnchorMs = OptionalLong.empty();
    private OptionalLong lastNewestBeforeMs = OptionalLong.empty();

    /**
     * @return the shown page, 0 for the newest
     */
    public int index() {
        return index;
    }

    /**
     * @return the shown past stream, null on page 0
     */
    public PastStream current() {
        return index == 0 ? null : older.get(index - 1);
    }

    /**
     * @param newestBeforeMs the time the newest page's search is anchored to
     * @return the time to search for an older stream before, from the shown page
     */
    public long backAnchor(long newestBeforeMs) {
        return index == 0 ? newestBeforeMs : current().range().startMs();
    }

    /**
     * @return whether an older page than the shown one is already held
     */
    public boolean hasCachedOlder() {
        return index < older.size();
    }

    /**
     * Moves to the held older page.
     *
     * @throws IllegalStateException if no older page is held
     */
    public void back() {
        if (!hasCachedOlder()) {
            throw new IllegalStateException("No cached older page");
        }
        index++;
    }

    /**
     * Adds a newly found stream as the page after the shown one, discarding any
     * pages after the shown one, and shows it.
     *
     * @param found the stream found before {@code backAnchor}
     */
    public void addOlderAndShow(PastStream found) {
        older.subList(index, older.size()).clear();
        older.add(found);
        index++;
    }

    /**
     * Moves one page toward the newest; does nothing on page 0.
     */
    public void forward() {
        if (index > 0) {
            index--;
        }
    }

    /**
     * Does not consider held pages; check {@link #hasCachedOlder()} for those.
     *
     * @param newestBeforeMs the time the newest page's search is anchored to
     * @return false once a search from the shown page's anchor found nothing
     */
    public boolean canSearchOlder(long newestBeforeMs) {
        return noOlderAnchorMs.isEmpty() || noOlderAnchorMs.getAsLong() != backAnchor(newestBeforeMs);
    }

    /**
     * @param anchorMs the anchor a search found no older stream before
     */
    public void markNoOlder(long anchorMs) {
        noOlderAnchorMs = OptionalLong.of(anchorMs);
    }

    /**
     * Reacts to the newest page's anchor moving (for example a new stream
     * started). The first call only records the value. When it differs from the
     * last one, held pages are dropped except the shown past page, which stays
     * as the only page at index 1.
     *
     * @param newestBeforeMs the time the newest page's search is anchored to
     */
    public void reanchor(long newestBeforeMs) {
        boolean isChanged = lastNewestBeforeMs.isPresent() && lastNewestBeforeMs.getAsLong() != newestBeforeMs;
        lastNewestBeforeMs = OptionalLong.of(newestBeforeMs);
        if (!isChanged) {
            return;
        }
        PastStream shown = current();
        if (shown == null) {
            reset();
            return;
        }
        older.clear();
        older.add(shown);
        index = 1;
        // The shown page is now the oldest held one, so only a mark for its own start still applies.
        if (noOlderAnchorMs.isPresent() && noOlderAnchorMs.getAsLong() != shown.range().startMs()) {
            noOlderAnchorMs = OptionalLong.empty();
        }
    }

    /**
     * Drops all pages and the no-older mark and returns to page 0.
     */
    public void reset() {
        older.clear();
        index = 0;
        noOlderAnchorMs = OptionalLong.empty();
    }
}
