
package chatty.util.tts;

import java.util.Objects;

/**
 *
 * @author tduva
 */
public record VoiceInfo(String name, String gender, String locale) {

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final VoiceInfo other = (VoiceInfo) obj;
        return Objects.equals(this.name, other.name);
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 67 * hash + Objects.hashCode(this.name);
        return hash;
    }

    @Override
    public String toString() {
        if (gender == null && locale == null) {
            return name;
        }
        return String.format("%s (%s, %s)",
                name,
                gender != null ? gender : "n/a",
                locale != null ? locale : "n/a");
    }
}
