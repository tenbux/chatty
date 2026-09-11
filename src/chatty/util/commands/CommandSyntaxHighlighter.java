
package chatty.util.commands;

import chatty.util.SyntaxHighlighter;

import java.text.ParseException;

/**
 *
 * @author tduva
 */
public class CommandSyntaxHighlighter extends SyntaxHighlighter {

    @Override
    public void update(String input) {
        clear();
        try {
            new Parser(input, "$", "\\").parse(this);
        }
        catch (ParseException ex) {
            int errorOffset = ex.getErrorOffset();
            if (errorOffset == input.length()) {
                errorOffset = input.length() - 1;
            }
            int errorOffset2 = errorOffset;
            items.removeIf(item -> item.start() >= errorOffset2 || item.end() >= errorOffset2);
            add(errorOffset, errorOffset + 1, Type.ERROR);
        }
    }

}
