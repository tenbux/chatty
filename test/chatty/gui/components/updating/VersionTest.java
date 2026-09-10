
package chatty.gui.components.updating;

import chatty.util.GitHub;
import chatty.util.GitHub.Release;
import chatty.util.GitHub.Releases;
import chatty.util.settings.Settings;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 *
 * @author tduva
 */
public class VersionTest {

    @Test
    public void testVersionToIntArray() {
        testVersionToIntArray("0.8.1", new int[]{0,8,1});
        testVersionToIntArray("0.8.1b", new int[]{0,8,1});
        testVersionToIntArray("0.8.1b3", new int[]{0,8,1,-1,3});
        testVersionToIntArray("0.8.1-b3", new int[]{0,8,1,-1,3});
        testVersionToIntArray("0.8.1-beta3", new int[]{0,8,1,-1,3});
        testVersionToIntArray("0.8.1-alpha3", new int[]{0,8,1,-1,3});
        testVersionToIntArray("0.8.1b10", new int[]{0,8,1,-1,10});
        testVersionToIntArray("0", new int[]{0});
        testVersionToIntArray("1.2", new int[]{1,2});
        
        // Invalid versions
        testVersionToIntArray("a", new int[]{0});
        testVersionToIntArray("a.b", new int[]{0,0});
    }
    
    private void testVersionToIntArray(String input, int[] output) {
        assertArrayEquals(Version.versionToIntArray(input), output);
    }
    
    @Test
    public void testCompareVersions() {
        assertEquals(0, Version.compareVersions("0.8.1", "0.8.1"));
        assertEquals(1, Version.compareVersions("0.8.1", "0.8.2"));
        assertEquals(-1, Version.compareVersions("0.8.2", "0.8.1"));
        assertEquals(1, Version.compareVersions("0.8.1b1", "0.8.1"));
        assertEquals(1, Version.compareVersions("0.8.1b4", "0.8.1"));
        assertEquals(0, Version.compareVersions("0.8.1b1", "0.8.1b1"));
        assertEquals(1, Version.compareVersions("0.8.1b1", "0.8.1b2"));
        assertEquals(1, Version.compareVersions("0.8.1b1", "0.8.1.1.1"));
        assertEquals(1, Version.compareVersions("0.8.1b1", "0.8.1.1"));
        assertEquals(0, Version.compareVersions("0.8.1b1", "0.8.1.-1.1"));
        assertEquals(-1, Version.compareVersions("0.8.1b2", "0.8.1.-1.1"));
        assertEquals(1, Version.compareVersions("0.8.4.1b3", "0.8.4.1"));
        assertEquals(1, Version.compareVersions("0.8.4.1-b3", "0.8.4.1"));
        assertEquals(1, Version.compareVersions("0.8.4.1beta3", "0.8.4.1"));
        assertEquals(1, Version.compareVersions("0.8.4.1-beta3", "0.8.4.1"));
        assertEquals(-1, Version.compareVersions("0.8.10", "0.8.9"));
        assertEquals(-1, Version.compareVersions("0.12", "0.10.9"));
        assertEquals(-1, Version.compareVersions("0.22", "0.10.9"));
        assertEquals(1, Version.compareVersions("0.9", "0.10.9"));
        assertEquals(0, Version.compareVersions("0.10.0", "0.10"));
        
        // Invalid versions
        assertEquals(1, Version.compareVersions("a", "0.1"));
        assertEquals(0, Version.compareVersions("a", "b"));
    }

    /**
     * Regression test for the NPE in versionReceived(): releases.getLatest()
     * can be null (no non-beta release exists), and the beta-fallback
     * branch only assigns when a beta exists AND "checkNewBeta" is enabled,
     * so "release" can still be null when dereferenced a few lines later.
     * versionReceived() must not throw in that case.
     */
    @Test
    public void testVersionReceived_noReleasesAtAll_doesNotThrow() {
        Settings settings = new Settings(null, null);
        settings.addBoolean("checkNewBeta", false);
        settings.addString("updateAvailable", "");

        Result result = new Result();
        Version v = new Version(result, settings);

        // Empty releases list -> getLatest() and getLatestBeta() are both
        // null. Must not throw. The fix intentionally returns before
        // calling the listener when no release is found at all (both
        // callers already tolerate versionChecked simply not firing).
        v.versionReceived(new Releases(Collections.emptyList()));

        assertFalse("versionChecked must not be called when no release exists", result.called.get());
    }

    @Test
    public void testVersionReceived_onlyBetaExists_checkNewBetaDisabled_doesNotThrow() {
        Settings settings = new Settings(null, null);
        settings.addBoolean("checkNewBeta", false);
        settings.addString("updateAvailable", "");

        Result result = new Result();
        Version v = new Version(result, settings);

        // Only a beta release exists: getLatest() is null (no non-beta),
        // getLatestBeta() is non-null, but "checkNewBeta" is off, so the
        // fallback branch doesn't run either - release stays null.
        v.versionReceived(new Releases(Arrays.asList(release("v99.0-beta", true))));

        assertFalse("versionChecked must not be called when release stays null "
                + "(beta exists but checkNewBeta is off)", result.called.get());
    }

    @Test
    public void testVersionReceived_onlyBetaExists_checkNewBetaEnabled_usesBeta() {
        Settings settings = new Settings(null, null);
        settings.addBoolean("checkNewBeta", true);
        settings.addString("updateAvailable", "");

        Result result = new Result();
        Version v = new Version(result, settings);

        v.versionReceived(new Releases(Arrays.asList(release("v99.0-beta", true))));

        assertTrue(result.called.get());
        assertTrue("the beta release must be used when enabled", result.newVersion.get() != null);
    }

    private static Release release(String tag, boolean beta) {
        return new Release(tag, tag, "", beta, new ArrayList<>(), 0);
    }

    private static class Result implements Version.VersionListener {
        final AtomicBoolean called = new AtomicBoolean();
        final AtomicReference<String> newVersion = new AtomicReference<>();

        @Override
        public void versionChecked(String version, GitHub.Releases releases) {
            called.set(true);
            newVersion.set(version);
        }
    }

}
