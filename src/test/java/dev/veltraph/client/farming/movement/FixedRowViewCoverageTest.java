package dev.veltraph.client.farming.movement;

import dev.veltraph.client.config.*;
import dev.veltraph.client.service.*;
import dev.veltraph.client.service.stub.*;
import dev.veltraph.client.testutil.*;
import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FixedRowViewCoverageTest {
    @Test void fourthColumnHasVerticalMarginAtBothSettlingExtremes() {
        var view=FixedRowView.forMatureWheat(-90,71.62,71,4,.75);assertNotNull(view);
        double cos=Math.cos(Math.toRadians(view.yaw()+90));
        for(double pitch:new double[]{view.pitch()-.75,view.pitch()+.75})
            for(int d=1;d<=4;d++) {
                double y=71.62-(d-.5)/cos*Math.tan(Math.toRadians(pitch));
                assertTrue(y>71&&y<72,"Actual mature outline entry must remain inside crop");
            }
        double oldPitch=Math.toDegrees(Math.atan(.17))+.75;
        assertTrue(71.62-3.5*Math.tan(Math.toRadians(oldPitch))<71,"Original nearest-centre/deadband clipping counterexample");
    }
    @Test void protectedNearCellRemainsOccludingAndIsNeverForcedThrough() {
        var view=FixedRowView.forMatureWheat(-90,71.62,71,4,.75);assertNotNull(view);
        assertEquals(-90f,view.yaw());assertEquals(3f,view.pitch());
        double nearY=71.62-.5*Math.tan(Math.toRadians(view.pitch()));
        double farY=71.62-3.5*Math.tan(Math.toRadians(view.pitch()));
        assertTrue(nearY<71.875,"Protected AGE6 near outline remains a real occluder");
        assertTrue(farY>71&&farY<72,"Unoccluded mature outline stays reachable at the same held angle");
    }
    @Test void invalidOrEmptyOutlineCannotProduceViewData() {
        assertNull(FixedRowView.forMatureWheat(-90,Double.NaN,71,4,.75));
        assertNull(FixedRowView.forMatureWheat(-90,71.62,71,5,.75));
        assertNull(FixedRowView.forMatureWheat(-90,80,71,4,.75));
        assertThrows(IllegalArgumentException.class,()->new FixedRowView(Float.NaN,0));
    }
    static class Field implements WorldService {
        Map<BlockPos,String> blocks=new HashMap<>();Set<BlockPos> unloaded=new HashSet<>();
        public boolean isLoaded(BlockPos p){return !unloaded.contains(p);}
        public String blockId(BlockPos p){return blocks.getOrDefault(p,"minecraft:air");}
        public boolean isPassable(BlockPos p){return !Set.of("minecraft:farmland","minecraft:stone").contains(blockId(p));}
        public boolean isStandable(BlockPos p){return p.y()==69;}
    }
    static class Probe implements FarmCollisionProbe {
        Set<BlockPos> immature=new HashSet<>();
        public boolean loaded(Vec3 p){return true;}public boolean clear(Vec3 p,boolean water){return true;}
        public OptionalDouble supportY(double x,double z,double y,int down){return OptionalDouble.of(70);}
        public boolean water(Vec3 p){return false;}public boolean matureCrop(BlockPos p){return !immature.contains(p);}
        public OptionalDouble matureOutlineHeight(BlockPos p){return OptionalDouble.of(1);}
    }
    record Setup(MapConfigService config,FakePlayerService player,FakeRotationService rotation,RecordingInputService input,
                 FarmGeometry geometry,SShapeRowEngine engine,Field field,Probe probe,RecordingBlockBreaking breaking) {}
    Setup setup(){var c=new MapConfigService();c.set(FarmingConfig.ROTATION_USE_MOUSE_GCD,false);c.set(FarmingConfig.ROTATION_ACCELERATION,1d);
        c.set(FarmingConfig.ROTATION_MAX_YAW_DEG_PER_TICK,90d);c.set(FarmingConfig.ROTATION_MAX_PITCH_DEG_PER_TICK,90d);
        var p=new FakePlayerService();p.position=new Vec3(50.5,70,-41.5);var r=new FakeRotationService();r.yaw=-90;r.pitch=30;
        var f=new Field();for(int x=51;x<=54;x++){f.blocks.put(new BlockPos(x,70,-42),"minecraft:farmland");f.blocks.put(new BlockPos(x,71,-42),"minecraft:wheat");}
        var g=new FarmGeometry(c,p,f);g.setSelectedCrop("minecraft:wheat");var probe=new Probe();g.setCollisionProbe(probe);
        var input=new RecordingInputService();var breaking=new RecordingBlockBreaking();var services=new FarmServices(new FakeClock(),p,f,input,r,breaking,new NoOpInventoryService());
        var e=new SShapeRowEngine(c,services,g,()->true);return new Setup(c,p,r,input,g,e,f,probe,breaking);}
    @Test void actualGeometrySelectsOneOutlineViewAndUnknownCropCannotSupplyIt() {
        var s=setup();s.geometry.setHeading(Direction.EAST);var v=s.geometry.initialFixedRowView();assertNotNull(v);assertEquals(3f,v.pitch());
        s.geometry.setSelectedCrop("minecraft:carrots");assertNull(s.geometry.initialFixedRowView());
        s.geometry.setSelectedCrop("minecraft:wheat");s.field.unloaded.add(new BlockPos(51,70,-42));
        assertNull(s.geometry.initialFixedRowView(),"Unknown first support cannot establish a fresh view");
    }
    @Test void engineKeepsOriginalViewWhileCorrectingBothLaneDriftDirections() {
        var s=setup();s.engine.start();for(int i=0;i<30;i++)s.engine.tick();assertEquals(SShapeState.FARMING_SIDEWAYS,s.engine.state());
        float yaw=s.engine.targetYaw(),pitch=s.engine.targetPitch();
        s.player.position=new Vec3(50.61,70,-40.5);s.engine.tick();assertTrue(s.input.back);assertFalse(s.input.forward);
        s.player.position=new Vec3(50.39,70,-39.5);s.engine.tick();assertTrue(s.input.forward);assertFalse(s.input.back);
        assertEquals(yaw,s.engine.targetYaw());assertEquals(pitch,s.engine.targetPitch());
        s.player.position=new Vec3(62.5,70,-39.5);s.engine.resumeFromObservedPosition();
        for(int i=0;i<30;i++)s.engine.tick();assertEquals(yaw,s.engine.targetYaw());assertEquals(pitch,s.engine.targetPitch());
        assertFalse(s.input.forward||s.input.back,"Original observed return rebinds its positional lane, never drives to pre-warp anchor");
        s.engine.stop();assertFalse(s.input.forward||s.input.back||s.input.left||s.input.right||s.input.jump);
    }
    @Test void repeatedRotatedNormalisedKeysStayInOriginalLaneInBothDirections() {
        for(var side:new LaneSide[]{LaneSide.LEFT,LaneSide.RIGHT}) {
            var s=setup();s.config.set(FarmingConfig.NETHERWART_STARTING_DIRECTION,side);
            s.engine.start();for(int i=0;i<30;i++)s.engine.tick();double initialZ=s.player.position.z(),vx=0,vz=0;
            float yaw=s.rotation.yaw,pitch=s.rotation.pitch;double rad=Math.toRadians(yaw);
            for(int i=0;i<1200;i++) {
                s.engine.tick();double f=(s.input.forward?1:0)-(s.input.back?1:0),r=(s.input.right?1:0)-(s.input.left?1:0);
                double norm=Math.max(1,Math.hypot(f,r));
                // Pure conservative ground integration of actual engine keys, not a native test tick or pose setter.
                vx=vx*.546+(-Math.sin(rad)*f-Math.cos(rad)*r)/norm*.1;
                vz=vz*.546+(Math.cos(rad)*f-Math.sin(rad)*r)/norm*.1;
                s.player.position=new Vec3(s.player.position.x()+vx,70,s.player.position.z()+vz);
                assertTrue(Math.abs(s.player.position.x()-50.5)<.12,"No oblique-view drift onto crop soil");
                assertEquals(yaw,s.rotation.yaw);assertEquals(pitch,s.rotation.pitch);
            }
            assertTrue(Math.abs(s.player.position.z()-initialZ)>180,"Feedback preserves productive lateral travel rather than holding stationary");
            assertEquals(side==LaneSide.LEFT?-1d:1d,Math.signum(s.player.position.z()-initialZ));
        }
    }
    @Test void realObservedDropRebindsAnchorWithoutChangingFixedRowView() {
        class Geometry implements SShapeGeometry {
            Direction heading;boolean atEnd;Vec3 drop=new Vec3(62.5,67,-41.5);
            public void setHeading(Direction d){heading=d;}public Direction heading(){return heading;}
            public boolean nearMiddle(){return true;}public boolean blockInFront(){return true;}
            public boolean canStrafe(TurnDirection side){return !atEnd;}public boolean canStepForward(){return false;}
            public TurnDirection movableLateral(){return TurnDirection.LEFT;}
            public Vec3 targetVec(){return new Vec3(51.5,71.5,-41.5);}
            public FixedRowView initialFixedRowView(){return FixedRowView.forMatureWheat(-90,71.62,71,4,.75);}
            public Vec3 safeDropTarget(TurnDirection side){return atEnd?drop:null;}
            public boolean dropLandingValid(Vec3 p){return drop.equals(p);}
        }
        class Driver implements FarmRouteDriver {
            State state=State.ACTIVE;public boolean startDrop(Vec3 p,int bound){return true;}
            public State tick(){return state;}public void stop(){}public String status(){return state.name();}
        }
        var s=setup();var g=new Geometry();var driver=new Driver();
        var services=new FarmServices(new FakeClock(),s.player,s.field,s.input,s.rotation,new RecordingBlockBreaking(),new NoOpInventoryService());
        var e=new SShapeRowEngine(s.config,services,g,()->true);e.setDropDriver(driver);e.start();for(int i=0;i<30;i++)e.tick();
        float yaw=e.targetYaw(),pitch=e.targetPitch();g.atEnd=true;e.tick();assertEquals(SShapeState.INTENTIONAL_DROP,e.state());
        s.player.position=g.drop;driver.state=FarmRouteDriver.State.ARRIVED;e.tick();g.atEnd=false;
        for(int i=0;i<30;i++)e.tick();assertFalse(s.input.forward||s.input.back,"Changed heading-axis landing owns its new actual lane anchor");
        assertEquals(yaw,e.targetYaw());assertEquals(pitch,e.targetPitch());
    }

    @Test void allCardinalViewsRetainTheSameLocalOutlineMargin() {
        for(float yaw:new float[]{-90,0,90,180}) {
            var v=FixedRowView.forMatureWheat(yaw,71.62,71,4,.75);assertNotNull(v);
            assertEquals(0,Angles.signedDelta(yaw,v.yaw()),1e-4);
            assertTrue(v.pitch()>=2.8&&v.pitch()<=3.3);
        }
    }

    @Test void cardinalViewAlsoContainsBothLoadedCropRowEndpoints() {
        var v=FixedRowView.forMatureWheat(-90,71.62,71,4,.75);assertNotNull(v);
        for(double z:new double[]{-46.5,46.5})for(int d=1;d<=4;d++) {
            double projected=z+(d-.5)*Math.tan(Math.toRadians(v.yaw()+90));
            assertEquals(z,projected,1e-12,"No startup oblique bias pushes far rays beyond north backedge");
        }
    }
    @Test void unavailableReferencePitchDoesNotForceInvalidFieldView() {
        assertNull(FixedRowView.forMatureWheat(-90,70.1,71,4,.75));
        assertNull(FixedRowView.forMatureWheat(-90,Double.POSITIVE_INFINITY,71,4,.75));
    }
    @Test void immatureRayKeepsContinuousAttackAndOrdinaryGroundedStrafeWithoutHopOrViewChange() {
        var s=setup();s.probe.immature.add(new BlockPos(51,71,-42));
        s.engine.start();for(int i=0;i<30;i++)s.engine.tick();
        float yaw=s.engine.targetYaw(),pitch=s.engine.targetPitch();
        assertEquals(-90f,yaw);assertEquals(3f,pitch);
        for(int i=0;i<100;i++){s.engine.tick();assertTrue(s.input.left||s.input.right);assertFalse(s.input.jump||s.input.sneak);
            assertTrue(s.breaking.breaking);assertEquals(yaw,s.engine.targetYaw());assertEquals(pitch,s.engine.targetPitch());}
    }

}
