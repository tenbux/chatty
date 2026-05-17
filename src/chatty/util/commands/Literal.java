
package chatty.util.commands;

import java.util.Set;

/**
 * The simplest kind of Item, which simply returns a static String, completely
 * ignoring any given Parameters.
 *
 * @author tduva
 */
record Literal(String literal) implements Item {

    @Override
    public String replace(Parameters parameters) {
        return literal;
    }

    @Override
    public String toString() {
        return "'" + literal + "'";
    }

    @Override
    public Set<String> getIdentifiersWithPrefix(String prefix) {
        return null;
    }

    @Override
    public Set<String> getRequiredIdentifiers() {
        return null;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Literal other = (Literal) obj;
        return literal.equals(other.literal);
    }

}
