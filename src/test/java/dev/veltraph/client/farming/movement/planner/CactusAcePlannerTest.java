package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.config.LaneSide;
import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.world.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CactusAcePlannerTest {
    private static final class World implements WorldService {
        boolean loaded=true,wet,blocked,finalBank;
        public boolean isLoaded(BlockPos p){return loaded;}
        public boolean isPassable(BlockPos p){return !blocked;}
        public boolean isStandable(BlockPos p){return true;}
        public String blockId(BlockPos p){
            if(!loaded)return "";
            if(p.y()==74&&wet)return "minecraft:water";
            if(p.y()==73&&(p.x()==0||p.x()==9))return "minecraft:oak_fence";
            if(p.y()==73&&p.z()==5&&finalBank)return "minecraft:red_stained_glass";
            if(p.y()==72&&p.z()==5&&finalBank)return "minecraft:red_terracotta";
            return p.y()==73?"minecraft:quartz_slab":"minecraft:air";
        }
        public java.util.Optional<String> blockProperty(BlockPos p,String name){return name.equals("type")?java.util.Optional.of("bottom"):java.util.Optional.empty();}
        public boolean bodyClear(Vec3 p,double w,double h){return loaded&&!blocked&&p.x()>=.925&&p.x()<=9.075;}
        public boolean hasGroundSupport(Vec3 p,double w){return loaded&&!wet;}
    }
    @Test void sourceCardinalAndLateralRoleRemainFixedWhileTraversing() {
        var p=new CactusAcePlanner();var w=new World();p.start(179,LaneSide.RIGHT);
        for(int t=0;t<80;t++)assertTrue(p.tick(w,new Vec3(5.5,73.5,6.3)));
        assertEquals(0f,dev.veltraph.client.farming.movement.Angles.signedDelta(180f,p.yaw()),.0001);assertEquals(LaneSide.RIGHT,p.side());assertEquals(0,p.reversals());
    }
    @Test void actualFenceContactChangesOnlyLateralSideAndDoesNotRepeatOnSameEnd() {
        var p=new CactusAcePlanner();var w=new World();p.start(180,LaneSide.RIGHT);
        assertTrue(p.tick(w,new Vec3(9.075,73.5,6.3)));assertEquals(LaneSide.LEFT,p.side());assertEquals(1,p.reversals());
        for(int t=0;t<30;t++)assertTrue(p.tick(w,new Vec3(9.075,73.5,6.3)));
        assertEquals(1,p.reversals());assertEquals(0f,dev.veltraph.client.farming.movement.Angles.signedDelta(180f,p.yaw()),.0001);
    }
    @Test void frontBankLipSupportCannotAuthorizeElevatedSlabLaneOrClimbableFence() {
        var p=new CactusAcePlanner();var w=new World();p.start(180,LaneSide.LEFT);
        assertFalse(p.tick(w,new Vec3(5.5,74,6.2995315136),true));
        assertEquals(CactusAcePlanner.State.WAITING_GEOMETRY,p.state());assertEquals(0,p.reversals());
        assertTrue(p.reason().contains("raised"));
        assertTrue(p.tick(w,new Vec3(5.5,73.5,6.321),false));
    }
    @Test void everyNativeFenceFinishesUpperHarvestBeforeReversing() {
        var p=new CactusAcePlanner();var w=new World();p.start(180,LaneSide.LEFT);
        for(int tick=0;tick<4;tick++){
            assertTrue(p.tick(w,new Vec3(.925,73.5,10.3),true));
            assertEquals(CactusAcePlanner.State.HARVESTING_FENCE,p.state());assertEquals(LaneSide.LEFT,p.side());assertEquals(0,p.reversals());
        }
        assertTrue(p.tick(w,new Vec3(.925,73.5,10.3),false));assertEquals(LaneSide.RIGHT,p.side());assertEquals(1,p.reversals());
    }
    @Test void redBankDoesNotEndBeforeItsRealFenceContact() {
        var p=new CactusAcePlanner();var w=new World();w.finalBank=true;p.start(180,LaneSide.LEFT);
        assertTrue(p.tick(w,new Vec3(5.5,73.5,6.3)));assertNotEquals(CactusAcePlanner.State.AT_END,p.state());
        assertFalse(p.tick(w,new Vec3(.925,73.5,6.3)));assertEquals(CactusAcePlanner.State.AT_END,p.state());assertEquals(0,p.reversals());
    }
    @Test void waterTransferNeedsLoadedClearActorContextButNotForcedGround() {
        var p=new CactusAcePlanner();var w=new World();w.wet=true;p.start(180,LaneSide.LEFT);
        assertTrue(p.tick(w,new Vec3(5.5,73.5,10.5)));w.loaded=false;
        assertFalse(p.tick(w,new Vec3(5.5,73.5,10.5)));assertEquals(0,p.reversals());
    }
    @Test void cropOrCeilingObstructionIsNotAnAuthorFenceTurn() {
        var p=new CactusAcePlanner();var w=new World();p.start(180,LaneSide.LEFT);w.blocked=true;
        assertFalse(p.tick(w,new Vec3(5.5,73.5,6.3)));assertEquals(CactusAcePlanner.State.WAITING_GEOMETRY,p.state());
        assertEquals(0,p.reversals());
    }
}
