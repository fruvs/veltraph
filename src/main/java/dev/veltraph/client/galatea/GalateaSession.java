package dev.veltraph.client.galatea;

import dev.veltraph.client.world.BlockPos;
import java.util.*;
import java.util.function.Predicate;

/** Per-tree outcome accounting. Losing one trunk advances through the remaining branches. */
public final class GalateaSession {
    private final GalateaPlanner.Tree tree;
    private final Set<BlockPos> remaining=new LinkedHashSet<>();
    private final Set<BlockPos> tried=new HashSet<>();
    private final Set<BlockPos> excluded=new LinkedHashSet<>();
    private GalateaPlanner.AxeMode mode=GalateaPlanner.AxeMode.STEM;
    private int removed;
    public GalateaSession(GalateaPlanner.Tree tree) { this.tree=tree;remaining.addAll(tree.logs()); }
    public GalateaPlanner.Tree tree() { return tree; }
    public GalateaPlanner.AxeMode mode() { return mode; }
    public void setMode(GalateaPlanner.AxeMode mode) { this.mode=Objects.requireNonNull(mode);tried.clear(); }
    public void observe(Predicate<BlockPos> log) {
        // Recheck the original component as well: client prediction can be rolled back by the
        // server. A temporarily absent log is never permanently credited as removed.
        remaining.clear();
        for(BlockPos p:tree.logs()) if(log.test(p)) remaining.add(p);
        removed=tree.logs().size()-remaining.size();
    }
    public Optional<BlockPos> choose(Predicate<BlockPos> feasible,Comparator<BlockPos> order) {
        return remaining.stream().filter(p->!tried.contains(p)&&!excluded.contains(p)&&feasible.test(p)).min(order);
    }
    public boolean canAttempt(BlockPos p) { return remaining.contains(p)&&!tried.contains(p)&&!excluded.contains(p); }
    public void fail(BlockPos p) { tried.add(p); }
    public void exclude(BlockPos p) { if(p!=null)excluded.add(p); }
    public boolean excluded(BlockPos p) { return excluded.contains(p); }
    public boolean advance(boolean jump,boolean scaffold,boolean skipThrow) {
        var next=GalateaPlanner.nextMode(mode,jump,scaffold,skipThrow);
        if(next==null) return false;setMode(next);return true;
    }
    public boolean complete() { return remaining.isEmpty(); }
    public int removed() { return removed; }
    public int remaining() { return remaining.size(); }
    public List<BlockPos> remainingLogs() { return List.copyOf(remaining); }
}
