package dev.veltraph.client.combat.ghost;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.Cell;
/** Singleton lifetime: last anchor survives SEARCH entry and handler reset. Active flag is cleared on SEARCH entry. */
public final class GhostSourceFallbackLifetime {
    private Cell lastAnchor=new Cell(0,0,0);private boolean active;
    public Cell lastAnchor(){return lastAnchor;}public boolean active(){return active;}
    public void sourceSearchEntry(){active=false;}
    public void installed(Cell actualAnchor){lastAnchor=java.util.Objects.requireNonNull(actualAnchor);active=true;}
}
