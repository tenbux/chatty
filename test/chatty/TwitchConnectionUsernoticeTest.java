package chatty;

import chatty.util.irc.MsgTags;
import chatty.util.irc.UsernoticeInfo;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TwitchConnectionUsernoticeTest {

    private static String modiversaryText(String login, String displayName, String systemMsg) {
        User user = new User(login, displayName, Room.EMPTY);
        MsgTags tags = MsgTags.create("msg-id", "modiversary", "login", login, "msg-param-months", "6");
        UsernoticeInfo info = TwitchConnection.makeUsernoticeInfo(user, tags, systemMsg, "");
        return info.text();
    }

    @Test
    public void should_prependName_when_systemMsgOmitsIt() {
        assertEquals("Test has been a moderator for 6 months!",
                modiversaryText("test", "Test", "has been a moderator for 6 months!"));
    }

    @Test
    public void should_keepText_when_systemMsgStartsWithName() {
        assertEquals("Test has been a moderator for 6 months!",
                modiversaryText("test", "Test", "Test has been a moderator for 6 months!"));
    }

    @Test
    public void should_keepText_when_nameAppearsLaterInSystemMsg() {
        assertEquals("Congrats! Test has been a moderator for 6 months!",
                modiversaryText("test", "Test", "Congrats! Test has been a moderator for 6 months!"));
    }

    @Test
    public void should_keepText_when_nameDiffersOnlyInCase() {
        assertEquals("TEST has been a moderator for 6 months!",
                modiversaryText("test", "Test", "TEST has been a moderator for 6 months!"));
    }

    @Test
    public void should_prependName_when_loginIsOnlyASubstringOfAWord() {
        assertEquals("Mod has been a moderator for 6 months!",
                modiversaryText("mod", "Mod", "has been a moderator for 6 months!"));
    }
}
