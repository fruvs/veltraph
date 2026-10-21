package dev.veltraph.client.combat.ghost;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Objects;
import dev.veltraph.client.combat.ghost.GhostSourceSampledRay.Point;

/** Qualified modern Ghost drop planner. The four IDs are source recovered; Ghost loot traversal was not present in its readable SEARCH. */
public final class GhostLootCandidatePlanner {
    public static final Set<String> SOURCE_REGISTERED_DROP_IDS=Set.of("SORROW","CRYSTAL_FRAGMENT","PLASMA","VOLTA");
    public enum Availability { OBSERVED, UNKNOWN }
    public record Candidate(Object nativeEntity,int entityId,UUID uuid,String serverId,String unitIdentity,int count,Point position,boolean alive) {
        public Candidate { Objects.requireNonNull(nativeEntity);Objects.requireNonNull(uuid);Objects.requireNonNull(serverId);Objects.requireNonNull(unitIdentity);Objects.requireNonNull(position);
            if(count<=0||unitIdentity.isEmpty())throw new IllegalArgumentException("Actual nonempty unit identity/count required"); }
    }
    private GhostLootCandidatePlanner() {}
    /** Nearest by actual 3D coordinates, stable in actual entity iteration order for equal distances. No rarity weights or authored route. */
    public static Candidate nearest(Availability observed,boolean actualCollectEnabled,Point actualPlayer,List<Candidate> actualItems,double declaredRadius) {
        if(observed!=Availability.OBSERVED||!actualCollectEnabled)return null;
        Objects.requireNonNull(actualPlayer);Objects.requireNonNull(actualItems);
        if(!Double.isFinite(declaredRadius)||declaredRadius<=0)throw new IllegalArgumentException("Explicit finite positive modern radius required");
        double limit=declaredRadius*declaredRadius;if(!Double.isFinite(limit))throw new IllegalArgumentException("Bounded radius required");
        Candidate selected=null;double best=limit;
        for(Candidate item:actualItems) {
            if(item==null||!item.alive()||!SOURCE_REGISTERED_DROP_IDS.contains(item.serverId()))continue;
            double distance=distanceSquared(actualPlayer,item.position());
            if(distance<best){selected=item;best=distance;}
        }
        return selected;
    }
    private static double distanceSquared(Point a,Point b) { return Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2); }
}
