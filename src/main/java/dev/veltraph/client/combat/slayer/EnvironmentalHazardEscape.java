package dev.veltraph.client.combat.slayer;

import dev.veltraph.client.world.Vec3;
import java.util.*;
import java.util.function.Predicate;

/** Local conservative response to already observed physical hazards, not a live ability decoder. */
public final class EnvironmentalHazardEscape {
    public record Decision(boolean active, Vec3 source, String detail) {}
    private final Set<Vec3> footprint=new HashSet<>();
    private Vec3 source;
    private long quietUntil;
    // Same conservative quiet policy as the existing Revenant warning response.
    public static final long REMOVAL_QUIET_MILLIS=3000;
    public static final double THREAT_RADIUS=3;
    /** A dense warning patch cannot require moving away from every individual tile at once. */
    public static boolean permitsAdvance(Vec3 current,Vec3 next,List<Vec3> hazards){
        double currentRisk=exposure(current,hazards),nextRisk=exposure(next,hazards);
        return currentRisk==0?nextRisk==0:nextRisk<currentRisk-1e-9;
    }
    public static boolean clearOfHazards(Vec3 point,List<Vec3> hazards){return exposure(point,hazards)==0;}
    private static double exposure(Vec3 point,List<Vec3> hazards){
        double risk=0;
        for(var hazard:hazards){
            if(Math.abs(point.y()-hazard.y())>3)continue;
            double penetration=Math.max(0,THREAT_RADIUS-point.horizontalDistanceTo(hazard));
            risk+=penetration*penetration;
        }
        return risk;
    }
    public void reset(){footprint.clear();source=null;quietUntil=0;}
    public Set<Vec3> footprint(){return Set.copyOf(footprint);}
    public Decision observe(boolean eligible,Vec3 player,List<Vec3> observed,
                            Predicate<Vec3> removalObservable,long now){
        return observe(eligible,player,observed,removalObservable,now,"Revenant");
    }
    public Decision observe(boolean eligible,Vec3 player,List<Vec3> observed,
                            Predicate<Vec3> removalObservable,long now,String encounter){
        if(!eligible){reset();return new Decision(false,null,"");}
        var threats=observed.stream().filter(p->Math.abs(p.y()-player.y())<=3
                &&p.horizontalDistanceTo(player)<THREAT_RADIUS).toList();
        footprint.addAll(threats);
        // An unloaded/out-of-scan cell cannot acknowledge removal.
        footprint.removeIf(p->removalObservable.test(p)&&!observed.contains(p));
        if(!threats.isEmpty())source=threats.stream().min(Comparator.comparingDouble(p->p.horizontalDistanceTo(player))).orElseThrow();
        if(source==null)return new Decision(false,null,"");
        if(!footprint.isEmpty())quietUntil=now+REMOVAL_QUIET_MILLIS;
        else if(now>=quietUntil){reset();return new Decision(false,null,"");}
        return new Decision(true,source,footprint.isEmpty()?encounter+" physical hazard: removal quiet hold"
                :encounter+" physical hazard: escape and await observed removal");
    }
}
