
package chatty.util.tts;

/**
 *
 * @author tduva
 */
public record SpeakRequest(String text, String voice, int volume, int rate, int pitch, Mode mode) {

    public enum Mode {
        STOP_SAY_DIRECTLY, QUEUE
    }

}
