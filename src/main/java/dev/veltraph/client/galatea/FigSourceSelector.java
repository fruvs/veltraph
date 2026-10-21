package dev.veltraph.client.galatea;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import java.util.function.Predicate;

/** Per-descriptor mode selection. Mode-entry preparation captures the jumping helper origin once. */
public final class FigSourceSelector {
    public record View(BlockPos feet,Vec3 position,Vec3 eye,float yaw){}
    private final FigSourceGeometry geometry;
    private final FigSourceGeometry.World world;
    private final Set<BlockPos> jumpingSuccesses=new HashSet<>();
    private Vec3 jumpOrigin;
    private int jumpMaximum;
    private FigSelectionReceipt lastReceipt;
    private long sequence;
    private final ArrayDeque<FigSelectionReceipt> receipts=new ArrayDeque<>();
    public FigSourceSelector(FigSourceGeometry geometry,FigSourceGeometry.World world) {
        this.geometry=geometry;this.world=world;
    }
    public void enterJump(Vec3 nativePosition,int nativeJumpMaximum) {
        jumpOrigin=nativePosition;jumpMaximum=nativeJumpMaximum;jumpingSuccesses.clear();
    }
    public Optional<BlockPos> select(GalateaPlanner.AxeMode mode,List<BlockPos> remaining,Predicate<BlockPos> canAttempt,
                                     View current,boolean aimHigher,BlockPos previousTarget) {
        if(mode==GalateaPlanner.AxeMode.JUMPING&&jumpOrigin==null)throw new IllegalStateException("Capture source jump helper at mode entry");
        Set<BlockPos> live=new HashSet<>(remaining);
        Map<BlockPos,Integer> throwing=mode==GalateaPlanner.AxeMode.THROWING
                ?FigSourceGeometry.throwingCounts(remaining,world):Map.of();
        Predicate<BlockPos> eligible=p->live.contains(p)&&canAttempt.test(p)&&world.log(p)
                &&modeEligible(mode,current.eye(),p);
        Comparator<BlockPos> score=Comparator.comparingDouble(p->FigSourceCandidateScore.score(mode,p,current.feet(),geometry.base(),
                current.position(),current.yaw(),aimHigher,geometry.leafCost(p),
                FigSourceCandidateScore.localPenalty(p,previousTarget,world.passable(previousTarget),false),
                mode!=GalateaPlanner.AxeMode.JUMPING||jumpingFeasible(p),world.leaves(p.down()),throwing.get(p),previousTarget));
        var ranked=geometry.candidates(mode,Set.of()).stream().filter(eligible).sorted(score).toList();
        var rays=new ArrayList<BlockPos>();var accepted=new ArrayList<Boolean>();
        var chosen=FigCandidateSelection.selectRanked(ranked,p->{
            boolean hit=world.sourceRay(current.eye(),p,true);rays.add(p);accepted.add(hit);return hit;
        });
        lastReceipt=new FigSelectionReceipt(++sequence,mode,ranked,rays,accepted,chosen.orElse(null));
        receipts.addLast(lastReceipt);if(receipts.size()>32)receipts.removeFirst();
        return chosen;
    }
    public Optional<FigSelectionReceipt> lastReceipt(){return Optional.ofNullable(lastReceipt);}
    public List<FigSelectionReceipt> recentReceipts(){return List.copyOf(receipts);}
    private boolean jumpingFeasible(BlockPos p) {
        if(jumpingSuccesses.contains(p))return true;
        if(FigSourceGeometry.raisedReachable(world,jumpOrigin,p,jumpMaximum)){jumpingSuccesses.add(p);return true;}
        return false;
    }
    public static boolean modeEligible(GalateaPlanner.AxeMode mode,Vec3 eye,BlockPos p) {
        return switch(mode) {
            case STEM,THROWING->true;
            case JUMPING->eye.horizontalDistanceTo(FigSourceGeometry.center(p))<4.25;
            case SCAFFOLD->eye.distanceTo(FigSourceGeometry.center(p))<4.25;
        };
    }
}
