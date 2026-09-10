
package chatty.gui.components.userinfo;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for the pin/close inversion bug in UserInfoDialog: the
 * "closeUserDialogOnAction" logic in closeOnAction() used to negate the
 * pinned state where the other call site (the moderation buttons listener)
 * didn't, so "Pin message" closed the dialog exactly when it was pinned,
 * and left it open when it wasn't - the opposite of the intended behavior.
 */
public class UserInfoDialogTest {

    @Test
    public void testPinnedDialog_staysOpenAfterAction() {
        // This is the exact scenario that was broken: setting on, checkbox
        // checked (dialog pinned) -> must NOT close.
        assertFalse(UserInfoDialog.shouldCloseOnAction(true, true));
    }

    @Test
    public void testUnpinnedDialog_closesAfterAction() {
        assertTrue(UserInfoDialog.shouldCloseOnAction(true, false));
    }

    @Test
    public void testSettingDisabled_neverCloses() {
        assertFalse(UserInfoDialog.shouldCloseOnAction(false, false));
        assertFalse(UserInfoDialog.shouldCloseOnAction(false, true));
    }

}
