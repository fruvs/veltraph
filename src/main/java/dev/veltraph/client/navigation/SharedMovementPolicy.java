package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.*;

/** User-requested shared mobility policy; no archived navigation or ability availability inference. */
public final class SharedMovementPolicy {
    public enum Mode { WALK, RUN, JUMP, AOTE, AOTV, ETHERWARP, FLIGHT, GRAPPLE }
    public enum Purpose { GENERAL_TRAVEL, GENERAL_GROUND_TRAVEL, EXACT_RETURN, CROP_WORK, COMBAT_EVASION }
    public record Options(Set<Mode> allowed, Purpose purpose, int toolUses, long deadlineMillis,
                          double terminalRadius, double costAllowance) {
        public Options {
            allowed=Set.copyOf(allowed);
            if(purpose==null||allowed.isEmpty()||toolUses<0||toolUses>8||deadlineMillis<100||deadlineMillis>120000
                ||!Double.isFinite(terminalRadius)||terminalRadius<=0||terminalRadius>.35
                ||!Double.isFinite(costAllowance)||costAllowance<1||costAllowance>1.25)
                throw new IllegalArgumentException("Invalid bounded shared movement policy");
            if(purpose==Purpose.GENERAL_GROUND_TRAVEL&&!Set.of(Mode.WALK,Mode.RUN,Mode.JUMP).containsAll(allowed))
                throw new IllegalArgumentException("Reserved ground transit cannot select a device or flight");
            if(purpose==Purpose.CROP_WORK&&!allowed.equals(Set.of(Mode.WALK)))
                throw new IllegalArgumentException("Crop work requires its prescribed geometry");
            if(purpose==Purpose.EXACT_RETURN&&(!allowed.contains(Mode.WALK)
                    ||!Set.of(Mode.WALK,Mode.AOTE,Mode.AOTV).containsAll(allowed)))
                throw new IllegalArgumentException("Return transit supports proved walking or instant transmission only");
        }
    }
    /** Exact opaque geometry/capability proofs are owned and revalidated by the native provider. */
    public record Candidate(String id, Mode mode, Vec3 destination, double cost, int toolUses,
                            Object geometryProof, Object capabilityProof, Object toolIdentity) {
        public Candidate {
            if(id==null||id.isBlank()||id.length()>128||mode==null||!finite(destination)||!Double.isFinite(cost)
                ||cost<0||toolUses<0||toolUses>8||geometryProof==null||capabilityProof==null
                ||device(mode)&&(toolUses==0||toolIdentity==null)||!device(mode)&&toolUses!=0)
                throw new IllegalArgumentException("Qualified finite movement candidate required");
        }
    }
    private SharedMovementPolicy() {}
    /** Advisory mode set from the actual captured setting. Native Root/provider proof remains mandatory. */
    public static Set<Mode> allowedFor(Purpose purpose,ApproachMode approach){
        if(purpose==null||approach==null)return Set.of();
        if(purpose==Purpose.EXACT_RETURN&&approach!=ApproachMode.WALK_ONLY)return Set.of(Mode.WALK,Mode.AOTE,Mode.AOTV);
        return purpose==Purpose.GENERAL_TRAVEL&&approach!=ApproachMode.WALK_ONLY
            ?Set.of(Mode.WALK,Mode.AOTE,Mode.AOTV,Mode.FLIGHT):Set.of(Mode.WALK);
    }
    /** Intermediate own transport landing uses its existing .30 receipt envelope; final entry stays exact. */
    public static double legTerminalRadius(Candidate candidate,Options options){
        return options.purpose()==Purpose.EXACT_RETURN&&candidate!=null&&device(candidate.mode())?.30:options.terminalRadius();
    }
    public static boolean device(Mode m){return m==Mode.AOTE||m==Mode.AOTV||m==Mode.ETHERWARP||m==Mode.GRAPPLE;}
    public static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z());}
    /** Same exact lattice cube as GroundPathfinder; rejects impossible horizon without spending its node budget. */
    public static boolean withinGroundHorizon(Vec3 origin,Vec3 goal,int radius){
        if(!finite(origin)||!finite(goal)||radius<1||radius>50)return false;
        var a=GroundPathfinder.node(origin);var b=GroundPathfinder.node(goal);
        return Math.abs((long)a.x()-b.x())<=radius&&Math.abs((long)a.y()-b.y())<=radius&&Math.abs((long)a.z()-b.z())<=radius;
    }
    /** Variation is between admitted comparable journeys, never between unproved poses or cheap jitter. */
    public static Optional<Candidate> select(List<Candidate> available,Options options,int remainingUses,
                                              String previous,long journey) {
        if(available==null||available.size()>32)return Optional.empty();
        var accepted=new ArrayList<Candidate>();
        for(var c:available)if(c!=null&&options.allowed().contains(c.mode())&&c.toolUses()<=remainingUses)accepted.add(c);
        if(accepted.isEmpty())return Optional.empty();
        double best=accepted.stream().mapToDouble(Candidate::cost).min().orElseThrow();
        accepted.removeIf(c->c.cost()>Math.max(best*options.costAllowance(),best+.25));
        accepted.sort(Comparator.comparing(Candidate::id));
        // Duplicate IDs are ambiguous permission handles rather than extra variation choices.
        for(int i=1;i<accepted.size();i++)if(accepted.get(i-1).id().equals(accepted.get(i).id()))return Optional.empty();
        if(options.purpose()==Purpose.EXACT_RETURN||options.purpose()==Purpose.CROP_WORK)
            return accepted.stream().min(Comparator.comparingDouble(Candidate::cost).thenComparing(Candidate::id));
        if(accepted.size()>1&&previous!=null)accepted.removeIf(c->c.id().equals(previous));
        return Optional.of(accepted.get((int)Math.floorMod(journey,accepted.size())));
    }
}
