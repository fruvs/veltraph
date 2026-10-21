package dev.veltraph.client.combat.ghost;
import java.util.ArrayList;
import java.util.List;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.Cell;

/** Local scheduling window, not graph admission: every used cell still requires its exact native observation. */
final class GhostSourceWalkPrefetch {
    static final int MAX_WINDOW_CELLS=45;
    static List<Cell> around(Cell center){var cells=new ArrayList<Cell>(MAX_WINDOW_CELLS);
        // Local x/z candidate and foot/head/support heights. Farther source probes remain ordinary exact demands.
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=-2;y<=2;y++)
            cells.add(new Cell(Math.addExact(center.x(),x),Math.addExact(center.y(),y),Math.addExact(center.z(),z)));
        return List.copyOf(cells);
    }
}
