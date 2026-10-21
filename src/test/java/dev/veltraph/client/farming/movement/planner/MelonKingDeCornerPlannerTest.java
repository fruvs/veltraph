package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.service.*;
import dev.veltraph.client.service.stub.NoOpInventoryService;
import dev.veltraph.client.testutil.*;
import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MelonKingDeCornerPlannerTest {
    private final Map<BlockPos,String> blocks=new HashMap<>();
    private final FakePlayerService actor=new FakePlayerService();
    private final FakeRotationService rotation=new FakeRotationService(0,-59);
    private final RecordingInputService input=new RecordingInputService();
    private final Breaking breaking=new Breaking();
    private boolean supported=true,loaded=true,interactionBlocked;
    private final BlockPos lower=new BlockPos(30,71,11),upper=new BlockPos(30,75,13);
    private final MelonKingDeCornerPlanner planner;
    private final class Breaking implements BlockBreakingService {
        boolean held;BlockPos target;
        public void setBreaking(boolean value){held=value;}public boolean isBreaking(){return held;}
        public Optional<BlockPos> targetBlockPos(){return Optional.ofNullable(target);}
    }
    MelonKingDeCornerPlannerTest() {
        actor.position=new Vec3(31.3,69.875,10.7);
        for(int x=30;x<=40;x++)blocks.put(new BlockPos(x,69,10),"minecraft:soul_sand");
        blocks.put(new BlockPos(30,70,10),"minecraft:polished_andesite");
        for(var p:List.of(lower,upper)) {
            blocks.put(p,"minecraft:pumpkin");blocks.put(p.down(),"minecraft:dirt");
            blocks.put(p.offset(Direction.SOUTH),"minecraft:pumpkin_stem");blocks.put(p.offset(Direction.SOUTH).down(),"minecraft:farmland");
        }
        WorldService world=new WorldService(){
            public boolean isLoaded(BlockPos p){return loaded;}
            public boolean isPassable(BlockPos p){return blockId(p).equals("minecraft:air");}
            public boolean isStandable(BlockPos p){return !isPassable(p);}
            public String blockId(BlockPos p){return blocks.getOrDefault(p,"minecraft:air");}
            public boolean bodyClear(Vec3 feet,double w,double h){
                if(!supported)return false;
                for(var entry:blocks.entrySet())if(entry.getValue().equals("minecraft:polished_andesite")) {
                    var p=entry.getKey();
                    if(feet.x()+w>p.x()&&feet.x()-w<p.x()+1&&feet.z()+w>p.z()&&feet.z()-w<p.z()+1
                            &&feet.y()+h>p.y()&&feet.y()<p.y()+1)return false;
                }
                return true;
            }
            public boolean hasGroundSupport(Vec3 feet,double w){return supported;}
            public Optional<Vec3> interactionPoint(BlockPos p,Vec3 eye,double range){
                if(interactionBlocked)return Optional.empty();
                if(p.equals(upper)&&blocks.containsKey(lower))return Optional.empty();
                return Optional.of(new Vec3(p.x()+.5,p.y()+.5,p.z()+.5));
            }
        };
        planner=new MelonKingDeCornerPlanner(new FarmServices(new FakeClock(),actor,world,input,rotation,breaking,new NoOpInventoryService()));
    }
    @Test void translatedLoadedBankIsInferredAndActualTargetRemainsRequired() {
        assertTrue(planner.begin(new BlockPos(31,70,10),Direction.SOUTH));
        planner.tick(0,-59);assertEquals(MelonKingDeCornerPlanner.State.HARVEST,planner.state());
        assertFalse(breaking.held);assertFalse(input.jump||input.sneak||input.sprint);
        breaking.target=lower.offset(Direction.SOUTH);planner.tick(0,-59);assertFalse(breaking.held,"Stem crosshair cannot inherit fruit candidate");
        breaking.target=lower;planner.tick(0,-59);assertTrue(breaking.held);
        assertTrue(planner.focusAngles().isEmpty(),"End crops cannot request per-fruit camera angles");assertFalse(planner.endReady());
    }
    @Test void observedOccluderRemovalExposesUpperAndRestoreMustFinish() {
        planner.begin(new BlockPos(31,70,10),Direction.SOUTH);planner.tick(0,-59);
        blocks.remove(lower);breaking.target=upper;planner.tick(0,-59);assertTrue(breaking.held);
        blocks.remove(upper);rotation.yaw=90;planner.tick(0,-59);
        assertEquals(MelonKingDeCornerPlanner.State.RESTORE,planner.state());assertFalse(planner.endReady());
        rotation.yaw=0;planner.tick(0,-59);assertEquals(MelonKingDeCornerPlanner.State.IDLE,planner.state());assertTrue(planner.endReady());
        assertFalse(planner.begin(new BlockPos(31,70,10),Direction.SOUTH),"Completed bank must not trap the course in a focus loop");
    }
    @Test void unsupportedAndUnloadedBankNeverStarts() {
        supported=false;assertFalse(planner.begin(new BlockPos(31,70,10),Direction.SOUTH));
        supported=true;loaded=false;assertFalse(planner.begin(new BlockPos(31,70,10),Direction.SOUTH));
    }
    @Test void retainedWorkingColumnsDoNotActivateTheOuterStripAdaptation() {
        actor.position=new Vec3(34.7,69.875,10.7);
        assertFalse(planner.begin(new BlockPos(34,70,10),Direction.SOUTH));
        actor.position=new Vec3(32.7,69.875,10.7);
        assertTrue(planner.begin(new BlockPos(32,70,10),Direction.SOUTH));
    }
    @Test void wetBodyCoastsOntoTheDryFloorBeforeOutwardCapPressure() {
        blocks.put(new BlockPos(32,70,10),"minecraft:water");actor.position=new Vec3(32.7,69.875,10.7);
        planner.begin(new BlockPos(32,70,10),Direction.SOUTH);planner.tick(0,-59);
        assertEquals(MelonKingDeCornerPlanner.State.APPROACH,planner.state());
        actor.position=new Vec3(31.65,69.875,10.7);planner.tick(0,-59);
        assertFalse(input.forward||input.back||input.left||input.right,"Incoming native momentum must coast on its first observed dry tick");
        planner.tick(0,-59);assertTrue(input.right,"Only the second dry body observation permits ordinary outward cap pressure");
        actor.position=new Vec3(31.3,69.875,10.7);planner.tick(0,-59);planner.tick(0,-59);
        assertEquals(MelonKingDeCornerPlanner.State.HARVEST,planner.state());
        assertFalse(input.jump||input.sneak||input.sprint);
    }
    @Test void corridorFocusKeepsBothCoordinatesWithoutForwardBankPressure() {
        blocks.put(new BlockPos(30,70,10),"minecraft:polished_andesite");
        planner.begin(new BlockPos(32,70,10),Direction.SOUTH);planner.tick(0,-59);
        blocks.remove(lower);interactionBlocked=true;planner.tick(0,-59);
        assertEquals(MelonKingDeCornerPlanner.State.HARVEST,planner.state());
        actor.position=new Vec3(31.3,69.875,10.7);planner.tick(0,-59);planner.tick(0,-59);
        assertEquals(MelonKingDeCornerPlanner.State.HARVEST,planner.state());
        assertFalse(input.forward||input.back||input.left||input.right,"Supported dry corridor needs position feedback, not permanent forward pressure");
        assertFalse(input.jump||input.sneak||input.sprint);
        interactionBlocked=false;breaking.target=upper;planner.tick(0,-59);assertTrue(breaking.held);
    }
    @Test void nativeCapContactRetainsItsObservedSupportedForwardCoordinate() {
        blocks.put(new BlockPos(30,70,10),"minecraft:polished_andesite");
        planner.begin(new BlockPos(31,70,10),Direction.SOUTH);
        actor.position=new Vec3(31.3,69.875,10.5);planner.tick(0,-59);
        assertEquals(MelonKingDeCornerPlanner.State.APPROACH,planner.state(),"Actual forward motion must settle first");
        planner.tick(0,-59);assertEquals(MelonKingDeCornerPlanner.State.HARVEST,planner.state());
        assertFalse(input.forward||input.back||input.left||input.right,"A dry supported cap focus releases movement");
        assertFalse(input.jump||input.sneak||input.sprint);
        breaking.target=lower;planner.tick(0,-59);assertTrue(breaking.held);
        assertEquals(new Vec3(31.3,69.875,10.5),actor.position,"Planner observes the real pose without installing one");
    }
    private BlockPos occludedInnerFruit() {
        blocks.remove(lower);blocks.remove(upper);interactionBlocked=true;
        var inner=new BlockPos(32,75,13);blocks.put(inner,"minecraft:pumpkin");blocks.put(inner.down(),"minecraft:dirt");
        blocks.put(inner.offset(Direction.SOUTH),"minecraft:pumpkin_stem");blocks.put(inner.offset(Direction.SOUTH).down(),"minecraft:farmland");
        return inner;
    }
    @Test void cappedInnerFruitRequiresSupportedColumnAndObservedRemoval() {
        var inner=occludedInnerFruit();blocks.put(new BlockPos(30,70,10),"minecraft:polished_andesite");
        planner.begin(new BlockPos(31,70,10),Direction.SOUTH);planner.tick(0,-59);
        assertEquals(MelonKingDeCornerPlanner.State.APPROACH,planner.state());assertFalse(planner.endReady());
        assertEquals("minecraft:pumpkin",blocks.get(inner),"Position changes never declare a server-side harvest");
        assertFalse(breaking.held);
        interactionBlocked=false;breaking.target=inner;actor.position=new Vec3(32.5,69.875,10.7);
        planner.tick(0,-59);planner.tick(0,-59);assertTrue(breaking.held);
        blocks.remove(inner);planner.tick(0,-59);assertEquals(MelonKingDeCornerPlanner.State.RESTORE,planner.state());
        planner.tick(0,-59);assertTrue(planner.endReady());
    }
    @Test void uncappedFinalEndRequiresTheInnerFruitToActuallyBeRemoved() {
        blocks.remove(new BlockPos(30,70,10));
        var inner=occludedInnerFruit();planner.begin(new BlockPos(31,70,10),Direction.SOUTH);planner.tick(0,-59);
        assertEquals(MelonKingDeCornerPlanner.State.APPROACH,planner.state());assertFalse(planner.endReady());
        interactionBlocked=false;breaking.target=inner;actor.position=new Vec3(32.5,69.875,10.7);
        planner.tick(0,-59);planner.tick(0,-59);assertTrue(breaking.held);
        blocks.remove(inner);planner.tick(0,-59);assertEquals(MelonKingDeCornerPlanner.State.RESTORE,planner.state());
        planner.tick(0,-59);assertTrue(planner.endReady());assertEquals(MelonKingDeCornerPlanner.State.READY,planner.state());
        for(int i=0;i<650;i++)planner.tick(0,-59);
        assertTrue(planner.endReady());assertTrue(planner.active());assertFalse(breaking.held);
        assertFalse(input.forward||input.back||input.left||input.right||input.jump||input.sneak||input.sprint);
    }
    @Test void timeoutLeavesResetUnreadyAndAllKeysReleased() {
        planner.begin(new BlockPos(31,70,10),Direction.SOUTH);
        for(int n=0;n<601;n++)planner.tick(0,-59);
        assertEquals(MelonKingDeCornerPlanner.State.BLOCKED,planner.state());assertFalse(planner.endReady());assertFalse(breaking.held);
        assertFalse(input.forward||input.back||input.left||input.right||input.jump||input.sneak||input.sprint);
    }
}
