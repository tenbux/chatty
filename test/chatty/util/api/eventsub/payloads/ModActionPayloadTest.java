package chatty.util.api.eventsub.payloads;

import chatty.util.api.eventsub.payloads.ModActionPayload.AutoModMessageUpdate;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Twitch can send "automod.message.hold" v2 events with "reason":"automod"
 * but "automod":null (observed for a held GIF message). Building the reason
 * text used to throw, which aborted displaying the held message entirely.
 */
public class ModActionPayloadTest {

    private static JSONObject heldPayload(String reasonFields) throws Exception {
        String json = "{\"event\":{"
                + "\"broadcaster_user_login\":\"somestream\","
                + "\"user_login\":\"someuser\","
                + "\"message_id\":\"00000000-0000-0000-0000-000000000001\","
                + "\"message\":{\"text\":\"[Thinking Monkey GIF by The Simpsons]\"},"
                + reasonFields
                + "}}";
        return (JSONObject) new JSONParser().parse(json);
    }

    @Test
    public void test_decodeAutomodHeld_automodNull_reasonIsPlainAutoMod() throws Exception {
        JSONObject payload = heldPayload("\"reason\":\"automod\",\"automod\":null,\"blocked_term\":null");

        ModActionPayload data = ModActionPayload.decodeAutomodHeld(payload);

        assertNotNull(data);
        assertEquals("AutoMod", ((AutoModMessageUpdate) data.action).getReason());
        assertEquals("/automod_filtered [AutoMod] <someuser> [Thinking Monkey GIF by The Simpsons]",
                     data.getPseudoCommandString());
    }

    @Test
    public void test_getReason_automodWithoutBoundaries_omitsFragments() throws Exception {
        JSONObject payload = heldPayload("\"reason\":\"automod\","
                + "\"automod\":{\"category\":\"swearing\",\"level\":4,\"boundaries\":null}");

        AutoModMessageUpdate update = new AutoModMessageUpdate(payload);

        assertEquals("AutoMod: swearing4/", update.getReason());
    }

    @Test
    public void test_getReason_blockedTermWithoutTerms_omitsFragments() throws Exception {
        JSONObject payload = heldPayload("\"reason\":\"blocked_term\","
                + "\"blocked_term\":{\"terms_found\":null}");

        AutoModMessageUpdate update = new AutoModMessageUpdate(payload);

        assertEquals("BlockedTerm: ", update.getReason());
    }

    @Test
    public void test_getReason_blockedTermWithoutBoundary_omitsFragment() throws Exception {
        JSONObject payload = heldPayload("\"reason\":\"blocked_term\","
                + "\"blocked_term\":{\"terms_found\":[{\"boundary\":null}]}");

        AutoModMessageUpdate update = new AutoModMessageUpdate(payload);

        assertEquals("BlockedTerm: ", update.getReason());
    }

}
