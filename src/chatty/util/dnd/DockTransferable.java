
package chatty.util.dnd;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;

/**
 * Contains information provided by the source component of a drag operation,
 * used mainly for drawing the interface and the potential drop.
 *
 * @author tduva
 */
public record DockTransferable(DockContent content, DockChild source, Image image,
                               int sourceIndex) implements Transferable {

    public static final DataFlavor FLAVOR =
            new DataFlavor(DockTransferable.class, "DockTransferable");

    public DockTransferable(DockContent content, DockChild source, int index, Image image) {
        this(content, source, image, index);
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[]{FLAVOR};
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return FLAVOR.equals(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) {
        return this;
    }

    @Override
    public String toString() {
        return String.format("TF(%d,%s,%s)",
                sourceIndex, content, source);
    }

}
