
package chatty.util;

import chatty.util.api.Emoticon;
import chatty.util.ffz.FrankerFaceZParsing;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

/**
 *
 * @author tduva
 */
public class FrankerFaceZTest {

    @Test
    public void testParseEmote() throws Exception {
        JSONParser parser = new JSONParser();
        JSONObject obj = (JSONObject) parser.parse(loadJSON("FFZ_emote_regular"));
        Emoticon emote = FrankerFaceZParsing.parseEmote(obj, null, null, null);
        assertNotNull(emote);
        assertEquals("joshWASTED", emote.code);
        assertEquals("Joshimuz", emote.creator);
        assertEquals(100, emote.getWidth());
        assertEquals(16, emote.getHeight());
        
        obj = (JSONObject) parser.parse(loadJSON("FFZ_emote_no_height"));
        emote = FrankerFaceZParsing.parseEmote(obj, null, null, null);
        assertNotNull(emote);
        assertEquals("joshWASTED", emote.code);
        assertEquals("Joshimuz", emote.creator);
        assertEquals(100, emote.getWidth());
        assertEquals(-1, emote.getHeight());
        
        testParseEmoteError("FFZ_emote_id_string");
    }
    
    private void testParseEmoteError(String fileName) throws Exception {
        JSONParser parser = new JSONParser();
        JSONObject obj = (JSONObject) parser.parse(loadJSON(fileName));
        Emoticon emote = FrankerFaceZParsing.parseEmote(obj, null, null, null);
        assertNull(emote);
    }
    
    private String loadJSON(String fileName) throws Exception {
        Path path = Paths.get(this.getClass().getResource(fileName).toURI());
        System.out.println(path.toAbsolutePath());
        try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            StringBuilder b = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                b.append(line);
            }
            return b.toString();
        } catch (IOException ex) {
            fail("Test failed: Error reading file");
        }
        return null;
    }
    
}
