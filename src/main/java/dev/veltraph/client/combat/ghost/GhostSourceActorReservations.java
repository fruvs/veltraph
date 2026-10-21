package dev.veltraph.client.combat.ghost;

import java.util.List;
import dev.veltraph.client.combat.ghost.GhostSourceSampledRay.*;

/** Qualified modern situational obstacle overlay; original source terrain/cost/frontier contracts stay separate. */
public final class GhostSourceActorReservations {
    private final List<Box> blockers;
    public GhostSourceActorReservations(List<Actor> actualActors,double actualWidth,double actualHeight,double clearanceMargin){
        if(!Double.isFinite(actualWidth)||actualWidth<=0||!Double.isFinite(actualHeight)||actualHeight<=0
                ||!Double.isFinite(clearanceMargin)||clearanceMargin<0)throw new IllegalArgumentException("Actual finite player dimensions and nonnegative margin required");
        double halfWidth=actualWidth/2+clearanceMargin;
        // Expand actual captured boxes once per frame, rather than once for every solver edge.
        blockers=List.copyOf(actualActors).stream().filter(a->a.living()&&!a.localPlayer()&&!a.selectedTarget()).map(Actor::box)
                .map(box->new Box(box.minX()-halfWidth,box.minY()-actualHeight,box.minZ()-halfWidth,
                        box.maxX()+halfWidth,box.maxY(),box.maxZ()+halfWidth)).toList();
    }
    /** Continuous swept standing-body footprint; diagonal edges cannot tunnel between node samples. */
    public boolean clear(Point from,Point to){
        for(var forbidden:blockers)if(segmentIntersects(from,to,forbidden))return false;
        return true;
    }
    public int blockerCount(){return blockers.size();}
    /** Already-custodied source cursor suffix; absent edge means all source edges were consumed. */
    static int remainingStartIndex(List<?> actualSourceEdges,java.util.Optional<?> actualRemainingEdge){
        if(actualRemainingEdge.isEmpty())return actualSourceEdges.size();
        Object identity=actualRemainingEdge.get();
        for(int i=0;i<actualSourceEdges.size();i++)if(actualSourceEdges.get(i)==identity)return i;
        return 0; // Unknown identity cannot authorize skipping any source edge.
    }
    private static boolean segmentIntersects(Point a,Point b,Box box){
        double enter=0,leave=1;
        for(int i=0;i<3;i++){
            double low=switch(i){case 0->box.minX();case 1->box.minY();default->box.minZ();};
            double high=switch(i){case 0->box.maxX();case 1->box.maxY();default->box.maxZ();};
            double start=switch(i){case 0->a.x();case 1->a.y();default->a.z();};
            double delta=switch(i){case 0->b.x()-a.x();case 1->b.y()-a.y();default->b.z()-a.z();};
            if(delta==0){if(start<=low||start>=high)return false;continue;}
            double p=(low-start)/delta,q=(high-start)/delta;if(p>q){double swap=p;p=q;q=swap;}
            enter=Math.max(enter,p);leave=Math.min(leave,q);if(enter>=leave)return false;
        }return enter<leave&&leave>0&&enter<1;
    }
}
