package dev.veltraph.client.galatea;

import dev.veltraph.client.world.BlockPos;
import java.util.*;

/** Passive record of actual candidate ranking and source ray evaluation; never issues gameplay. */
public record FigSelectionReceipt(long sequence,GalateaPlanner.AxeMode mode,List<BlockPos> ranked,
                                  List<BlockPos> rayCandidates,List<Boolean> rayAccepted,BlockPos selected) {
    public FigSelectionReceipt {ranked=List.copyOf(ranked);rayCandidates=List.copyOf(rayCandidates);rayAccepted=List.copyOf(rayAccepted);}
}
