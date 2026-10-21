package dev.veltraph.client.combat.slayer;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import java.util.function.Predicate;

/** Source-backed crypt escape distance with conservative observed-floor removal acknowledgment. */
public final class BedrockEscape {
    public record Decision(boolean active,Vec3 objective,String detail) {
        static Decision none() { return new Decision(false,null,""); }
    }
    public static final double MIN_DISTANCE=8;
    private final Set<BlockPos> footprint=new HashSet<>();
    private Vec3 origin,goal;
    private long quietUntil;
    public void reset() {footprint.clear();origin=null;goal=null;quietUntil=0;}
    public Decision observe(boolean eligible,Vec3 player,Vec3 boss,Set<BlockPos> bedrock,
                            List<Vec3> candidates,Predicate<Vec3> reachable,long now) {
        if(!eligible) {reset();return Decision.none();}
        boolean unsafe=bedrock.contains(player.toBlockPos().down());
        if(origin==null&&!unsafe)return Decision.none();
        if(origin==null)origin=player;
        // Retain the observed patch by world coordinates as player and boss move away.
        footprint.addAll(bedrock.stream().filter(p->center(p).horizontalDistanceTo(origin)<=7).toList());
        footprint.retainAll(bedrock);
        if(!footprint.isEmpty())quietUntil=now+750;
        else if(now>=quietUntil) {reset();return Decision.none();}
        if(goal==null||!candidates.contains(goal)||!reachable.test(goal)) {
            double awayX=origin.x()-boss.x(),awayZ=origin.z()-boss.z(),length=Math.max(.01,Math.hypot(awayX,awayZ));
            goal=candidates.stream().filter(p->p.horizontalDistanceTo(origin)>=MIN_DISTANCE)
                    .filter(p->!bedrock.contains(p.toBlockPos().down())).filter(reachable)
                    .min(Comparator.comparingDouble(p->p.horizontalDistanceTo(player)
                            -.7*((p.x()-origin.x())*awayX+(p.z()-origin.z())*awayZ)/length)).orElse(null);
        }
        return new Decision(true,goal,goal==null?"Crypt bedrock: no swept-clear escape; hold attacks"
                :footprint.isEmpty()?"Crypt bedrock: hold through removal quiet interval":"Crypt bedrock: escape and hold for floor removal");
    }
    public Vec3 origin() {return origin;}
    public Set<BlockPos> footprint() {return Set.copyOf(footprint);}
    private static Vec3 center(BlockPos p) {return new Vec3(p.x()+.5,p.y()+1,p.z()+.5);}
}
