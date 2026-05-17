
package chatty.util.commands;

import chatty.util.StringUtil;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Splits up by the separator, sorts the parts alphabetically and joins them
 * back together.
 *
 * @author tduva
 */
class Sort implements Item {
    
    private final Item item;
    private final Item separator;
    private final Item type;
    private final boolean isRequired;
    
    public Sort(Item item, Item separator, Item type, boolean isRequired) {
        this.item = item;
        this.separator = separator;
        this.type = type;
        this.isRequired = isRequired;
    }

    @Override
    public String replace(Parameters parameters) {
        String value = item.replace(parameters);
        if (!Item.checkReq(isRequired, value)) {
            return null;
        }
        String sep = " ";
        if (separator != null) {
            sep = separator.replace(parameters);
            if (sep == null) {
                return null;
            }
        }
        
        String t = "abc";
        if (type != null) {
            t = type.replace(parameters);
            if (t == null) {
                return null;
            }
        }
        
        List<String> split = Arrays.asList(value.split(Pattern.quote(sep)));
        switch (t) {
            case "abc":
                split.sort(String.CASE_INSENSITIVE_ORDER);
                break;
            case "Abc":
                Collections.sort(split);
                break;
        }
        return StringUtil.join(split, sep);
    }

    @Override
    public String toString() {
        return "Sort " + item + "/" + separator;
    }

    @Override
    public Set<String> getIdentifiersWithPrefix(String prefix) {
        return Item.getIdentifiersWithPrefix(prefix, item, separator, type);
    }
    
    @Override
    public Set<String> getRequiredIdentifiers() {
        return Item.getRequiredIdentifiers(isRequired, item, separator, type);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Sort other = (Sort) obj;
        if (!Objects.equals(this.item, other.item)) {
            return false;
        }
        if (!Objects.equals(this.separator, other.separator)) {
            return false;
        }
        if (!Objects.equals(this.type, other.type)) {
            return false;
        }
        return this.isRequired == other.isRequired;
    }
    
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 83 * hash + Objects.hashCode(this.item);
        hash = 83 * hash + Objects.hashCode(this.separator);
        hash = 83 * hash + Objects.hashCode(this.type);
        hash = 83 * hash + (this.isRequired ? 1 : 0);
        return hash;
    }
    
}
