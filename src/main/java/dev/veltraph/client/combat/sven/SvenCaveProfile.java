package dev.veltraph.client.combat.sven;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.List;

/** Literal stable 4.0.30 cave geometry, not an inferred travel route. */
public enum SvenCaveProfile {
    DARK_OAK(new BlockPos(-390,97,-63),new BlockPos(-392,98,-63),new BlockPos(-386,85,-83),
            new BlockPos(-410,90,-35),new BlockPos(-360,72,-92),
            List.of(new BlockPos(-385,81,-72),new BlockPos(-396,82,-51),new BlockPos(-373,76,-78),new BlockPos(-372,84,-60))),
    ACACIA(new BlockPos(-436,107,46),new BlockPos(-436,108,48),new BlockPos(-426,98,47),
            new BlockPos(-400,105,-10),new BlockPos(-455,85,60),
            List.of(new BlockPos(-426,98,46),new BlockPos(-410,97,14),new BlockPos(-442,94,16),new BlockPos(-409,92,39))),
    JUNGLE(new BlockPos(-461,119,-81),new BlockPos(-464,120,-81),new BlockPos(-452,108,-91),
            new BlockPos(-435,112,-105),new BlockPos(-487,90,-30),
            List.of(new BlockPos(-451,108,-90),new BlockPos(-474,104,-42),new BlockPos(-460,97,-81)));

    public static final Vec3 PARK_SPAWN=new Vec3(-266,79,-18);
    private final BlockPos pathTarget,gate,storedPosition,firstBound,secondBound;
    private final List<BlockPos> combatWaypoints;
    SvenCaveProfile(BlockPos pathTarget,BlockPos gate,BlockPos storedPosition,BlockPos firstBound,BlockPos secondBound,List<BlockPos> waypoints) {
        this.pathTarget=pathTarget;this.gate=gate;this.storedPosition=storedPosition;
        this.firstBound=firstBound;this.secondBound=secondBound;this.combatWaypoints=List.copyOf(waypoints);
    }
    /** Source uses this identical getter for both entrance and inside pathfinding. */
    public BlockPos pathTarget(){return pathTarget;}
    public BlockPos gate(){return gate;}
    /** Stored by source; not used as a cave travel destination. */
    public BlockPos storedPosition(){return storedPosition;}
    public BlockPos firstBound(){return firstBound;}
    public BlockPos secondBound(){return secondBound;}
    public List<BlockPos> combatWaypoints(){return combatWaypoints;}
    public boolean contains(Vec3 p){return p!=null&&p.x()>=Math.min(firstBound.x(),secondBound.x())&&p.x()<=Math.max(firstBound.x(),secondBound.x())
            &&p.y()>=Math.min(firstBound.y(),secondBound.y())&&p.y()<=Math.max(firstBound.y(),secondBound.y())
            &&p.z()>=Math.min(firstBound.z(),secondBound.z())&&p.z()<=Math.max(firstBound.z(),secondBound.z());}
}
