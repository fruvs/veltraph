package dev.veltraph.client.greenhouse;

import java.util.*;
import static dev.veltraph.client.greenhouse.GreenhouseModel.*;

/** 0.27.2 advisory dependencies. No native grammar, transition, resource grant or care authority. */
public final class GreenhouseLifecyclePolicy {
    private GreenhouseLifecyclePolicy() {}
    public static final String SOURCE="https://hypixel.net/threads/hypixel-skyblock-0-27-2-the-minister-update-greenhouse-qol-and-more.6159904/";
    public static final String RELEASE="2026-10-06 / SkyBlock 0.27.2";
    public enum Observed { YES, NO, UNKNOWN }
    public enum Need { INSPECT, WATERING_INSPECTION, DISCHARGE_INSPECTION, PRESERVE }
    /** Counters are species-group values. Per-cell copies never establish a complete group. */
    public record GroupCounter(Context context,String species,Set<Cell> participants,OptionalLong remaining,
                               long observedAt,String source,boolean completeGroup) {
        public GroupCounter {participants=Set.copyOf(participants);Objects.requireNonNull(remaining);}
    }
    /** Remaining decay is an observed duration, distinct from the group counter and water condition. */
    public record Observation(Context context,Cell cell,String species,long observedAt,String source,
            PlantState actualState,Observed droughtHalted,Observed overcharged,OptionalLong decayRemainingMillis,
            Optional<GroupCounter> mutationGroup) {
        public Observation {Objects.requireNonNull(actualState);Objects.requireNonNull(droughtHalted);Objects.requireNonNull(overcharged);
            Objects.requireNonNull(decayRemainingMillis);Objects.requireNonNull(mutationGroup);}
    }
    public record Advice(Set<Need> needs,String reason,boolean decayDependenciesObserved) {
        public Advice {needs=Set.copyOf(needs);}
        public boolean authorizesAction(){return false;}
    }
    private static boolean text(String s){return s!=null&&!s.isBlank();}
    private static boolean fresh(long at,long now,long age){return at>=0&&age>0&&now>=at&&now-at<=age;}
    public static Advice evaluate(Observation o,long now,long maxAge){
        if(o==null||o.context()==null||!o.context().identified()||o.cell()==null||!text(o.species())||!text(o.source())
                ||!fresh(o.observedAt(),now,maxAge)||o.actualState()==PlantState.UNKNOWN)
            return new Advice(Set.of(Need.INSPECT),"Refresh independently associated plant and condition receipts",false);
        var needs=EnumSet.noneOf(Need.class);var reasons=new ArrayList<String>();
        if(o.actualState()==PlantState.DEAD){needs.add(Need.PRESERVE);reasons.add("Actual DEAD donor retained; no watering, harvest or replacement inferred");}
        else if(o.droughtHalted()==Observed.YES){needs.add(Need.WATERING_INSPECTION);reasons.add("Observed drought halt: growth, spreading, shared effects and unique bonus pause; do not infer death or a watering cost");}
        else if(o.droughtHalted()==Observed.UNKNOWN){needs.add(Need.INSPECT);reasons.add("Drought/active effect eligibility unknown");}
        if(o.species().equals("Thunderling")&&o.actualState()!=PlantState.DEAD){
            if(o.overcharged()==Observed.YES){needs.add(Need.DISCHARGE_INSPECTION);reasons.add("Observed overcharge halt requires an independently observed discharge protocol; do not water or destroy as a substitute");}
            else if(o.overcharged()==Observed.UNKNOWN){needs.add(Need.INSPECT);reasons.add("Thunderling charge condition unknown");}
        }
        var group=o.mutationGroup().orElse(null);
        boolean known=group!=null&&group.context()!=null&&o.context().equals(group.context())&&o.species().equals(group.species())
            &&group.completeGroup()&&!group.participants().isEmpty()&&group.participants().contains(o.cell())
            &&group.participants().size()<=100&&text(group.source())&&fresh(group.observedAt(),now,maxAge)
            &&group.remaining().isPresent()&&group.remaining().getAsLong()>=0
            &&o.decayRemainingMillis().isPresent()&&o.decayRemainingMillis().getAsLong()>=0;
        if(!known){needs.add(Need.INSPECT);reasons.add("Decay needs both the actual remaining timer and a complete fresh species-group counter; release minimums cannot initialize that counter");}
        else if(o.decayRemainingMillis().getAsLong()==0){
            needs.add(Need.INSPECT);
            reasons.add(group.remaining().getAsLong()==0?"Both observed decay dependencies are zero: inspect actual plant transition; never infer DEAD from elapsed time":
                "Observed timer exhausted but group minimum remains: release allows 24h extensions; refresh the actual timer rather than manufacture a local extension");
        }else if(now-o.observedAt()>=o.decayRemainingMillis().getAsLong()){
            needs.add(Need.INSPECT);reasons.add("Local decay-duration estimate elapsed: refresh both dependencies; no counter decrement, extension or death inferred");
        }else {needs.add(Need.PRESERVE);reasons.add("Observed decay timer remains; keep the species-group counter separate and refresh before any transition");}
        return new Advice(needs,String.join("; ",reasons),known);
    }
    public static String summary(){return RELEASE+": drought pauses growth/spreading/effects/unique bonus; decay also depends on the shared species-group minimum counter. "
        +"Thunderling overcharge waits for discharge. PlantBoy Advance and Stoplight Petal failures preserve stage. "
        +"Dead donors also have decay dependencies. Refreshed native condition, group and resource receipts remain required; old captured menus are dated evidence. Source: "+SOURCE;}
}
