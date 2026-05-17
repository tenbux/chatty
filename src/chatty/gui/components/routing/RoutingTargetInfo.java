
package chatty.gui.components.routing;

/**
 *
 * @author tduva
 */
public record RoutingTargetInfo(String name, int messages) implements Comparable<RoutingTargetInfo> {

    @Override
    public int compareTo(RoutingTargetInfo o) {
        return name.compareToIgnoreCase(o.name);
    }

}
