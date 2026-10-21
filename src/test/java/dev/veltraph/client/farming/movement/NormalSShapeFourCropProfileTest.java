package dev.veltraph.client.farming.movement;

import dev.veltraph.client.config.*;
import dev.veltraph.client.service.*;
import dev.veltraph.client.service.stub.*;
import dev.veltraph.client.testutil.*;
import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual engine/geometry and current-ray ordering; no native permission or whole-farm credit. */
class NormalSShapeFourCropProfileTest {
    static final List<String> CROPS=List.of("minecraft:wheat","minecraft:carrots","minecraft:potatoes","minecraft:nether_wart");
    static String soil(String crop){return crop.equals("minecraft:nether_wart")?"minecraft:soul_sand":"minecraft:farmland";}
    static class Field implements WorldService {
        final Map<BlockPos,String> blocks=new HashMap<>();final Set<BlockPos> unloaded=new HashSet<>();
        public boolean isLoaded(BlockPos p){return !unloaded.contains(p);}
        public String blockId(BlockPos p){return blocks.getOrDefault(p,"minecraft:air");}
        public boolean isPassable(BlockPos p){return !Set.of("minecraft:farmland","minecraft:soul_sand","minecraft:stone").contains(blockId(p));}
        public boolean isStandable(BlockPos p){return p.y()==69;}
    }
    static class Probe implements FarmCollisionProbe {
        final Set<BlockPos> immature=new HashSet<>();double height=1,support=70;boolean ray=true,shape=true,loaded=true,clear=true;String requested;
        public boolean loaded(Vec3 p){return loaded;}public boolean clear(Vec3 p,boolean wet){return clear;}
        public OptionalDouble supportY(double x,double z,double y,int n){return Double.isFinite(support)?OptionalDouble.of(support):OptionalDouble.empty();}
        public boolean water(Vec3 p){return false;}
        public boolean matureCrop(BlockPos p){return !immature.contains(p);}
        public OptionalDouble matureOutlineHeight(BlockPos p){return shape?OptionalDouble.of(height):OptionalDouble.empty();}
        public boolean matureHarvestHit(String id){requested=id;return ray;}
    }
    record Setup(MapConfigService config,FakePlayerService player,FakeRotationService rotation,
                 RecordingInputService input,RecordingBlockBreaking breaking,Field field,Probe probe,FarmGeometry geometry,SShapeRowEngine engine){}
    static Setup setup(String crop){
        var config=new MapConfigService();config.set(FarmingConfig.ROTATION_USE_MOUSE_GCD,false);
        config.set(FarmingConfig.ROTATION_ACCELERATION,1d);config.set(FarmingConfig.ROTATION_MAX_YAW_DEG_PER_TICK,90d);config.set(FarmingConfig.ROTATION_MAX_PITCH_DEG_PER_TICK,90d);
        var player=new FakePlayerService();player.position=new Vec3(50.5,70,-41.5);
        var rotation=new FakeRotationService();rotation.yaw=-90;rotation.pitch=30;
        var field=new Field();for(int x=51;x<=54;x++){field.blocks.put(new BlockPos(x,70,-42),soil(crop));field.blocks.put(new BlockPos(x,71,-42),crop);}
        var probe=new Probe();probe.height=crop.equals("minecraft:carrots")||crop.equals("minecraft:potatoes")?.5625:crop.equals("minecraft:nether_wart")?.875:1;var geometry=new FarmGeometry(config,player,field);geometry.setSelectedCrop(crop);geometry.setCollisionProbe(probe);
        var input=new RecordingInputService();var breaking=new RecordingBlockBreaking();
        var services=new FarmServices(new FakeClock(),player,field,input,rotation,breaking,new NoOpInventoryService());
        return new Setup(config,player,rotation,input,breaking,field,probe,geometry,new SShapeRowEngine(config,services,geometry,()->true));
    }
    static void settle(Setup s){s.engine.start();for(int i=0;i<30;i++)s.engine.tick();assertEquals(SShapeState.FARMING_SIDEWAYS,s.engine.state());}
    @Test void everyNormalVarietySelectsTheSameCardinalReferenceProfile(){
        for(String crop:CROPS){var s=setup(crop);settle(s);assertEquals(-90f,s.engine.targetYaw());assertEquals(3f,s.engine.targetPitch());
            assertTrue(s.geometry.requiresNormalFixedRowView());}
    }
    @Test void actualOutlineHeightCanRefuseAnUnsupportedReferencePitch(){
        for(String crop:CROPS){var s=setup(crop);s.probe.height=.2;
            assertThrows(IllegalStateException.class,s.engine::start);assertEquals(SShapeState.STOPPED,s.engine.state());
            assertFalse(s.input.left||s.input.right||s.input.forward||s.input.back||s.input.jump);assertFalse(s.breaking.breaking);
            assertTrue(s.engine.pauseReason().contains("normal S profile unsupported"));}
    }
    @Test void missingShapeOrUnknownFirstSupportRefusesWithoutGeometricFallback(){
        for(String crop:CROPS){var s=setup(crop);s.probe.shape=false;assertThrows(IllegalStateException.class,s.engine::start);
            s=setup(crop);s.field.unloaded.add(new BlockPos(51,70,-42));assertThrows(IllegalStateException.class,s.engine::start);}
    }
    @Test void wrongSoilCannotAdmitAnyNormalCrop(){
        for(String crop:CROPS){var s=setup(crop);for(int x=51;x<=54;x++)s.field.blocks.put(new BlockPos(x,70,-42),crop.equals("minecraft:nether_wart")?"minecraft:farmland":"minecraft:soul_sand");
            assertThrows(IllegalStateException.class,s.engine::start);}
    }
    @Test void immatureOcclusionKeepsContinuousAttackGroundStrafeAndHeldView(){
        for(String crop:CROPS){var s=setup(crop);s.probe.immature.add(new BlockPos(51,71,-42));s.probe.ray=false;settle(s);
            for(int i=0;i<100;i++){s.engine.tick();assertTrue(s.input.left||s.input.right);assertFalse(s.input.jump||s.input.sneak);assertTrue(s.breaking.breaking);
                assertEquals(-90f,s.engine.targetYaw());assertEquals(3f,s.engine.targetPitch());}
            assertNull(s.probe.requested,"Normal S attack does not depend on the mature crop ray");}
    }
    @Test void stopResumeAndChangedCropDoNotRecomputeTheCapturedSessionView(){
        for(String crop:CROPS){var s=setup(crop);settle(s);s.player.position=new Vec3(62.5,70,-39.5);s.probe.height=.2;
            s.geometry.setSelectedCrop(CROPS.get((CROPS.indexOf(crop)+1)%4));s.engine.resumeFromObservedPosition();
            for(int i=0;i<30;i++)s.engine.tick();assertEquals(-90f,s.engine.targetYaw());assertEquals(3f,s.engine.targetPitch());
            s.engine.stop();assertEquals(SShapeState.STOPPED,s.engine.state());assertFalse(s.input.left||s.input.right||s.input.forward||s.input.back||s.input.jump);
            assertThrows(IllegalStateException.class,s.engine::start);}
    }
    @Test void unsupportedRestartReleasesPreviouslyHeldKeys(){
        var s=setup("minecraft:wheat");settle(s);s.engine.tick();assertTrue(s.input.left||s.input.right);
        s.probe.shape=false;assertThrows(IllegalStateException.class,s.engine::start);
        assertEquals(SShapeState.STOPPED,s.engine.state());assertFalse(s.input.left||s.input.right||s.input.forward||s.input.back||s.input.jump);assertFalse(s.breaking.breaking);
    }
    @Test void realMatureOutlineApertureUsesSuppliedHeightAndKeepsThreeDegrees(){
        for(float yaw:new float[]{-90,0,90,180})for(double height:new double[]{.5625,.875,1}){
            var v=FixedRowView.forNormalCrop(yaw,71.62,71,4,height,.1);assertNotNull(v);assertEquals(yaw,v.yaw());assertEquals(3f,v.pitch());}
        assertNotNull(FixedRowView.forNormalCrop(-90,71.62,71,1,4,.5625,.75),"3 degrees enters the short mature carrot TOP before leaving its nearest block");
        // At 71.65 the 2.25-degree settling ray enters the mature second crop before exit2.5.
        // The old71.70 value missed even at3 degrees; it was not a supported positive fixture.
        assertNotNull(FixedRowView.forNormalCrop(-90,71.65,71,2,4,.5625,.75),"An empty nearer cell is not a fictitious mature aperture constraint");
        assertNull(FixedRowView.forNormalCrop(-90,71.65,71,1,4,.5625,.75),"A real nearer mature cell has a different supported aperture");
        assertNull(FixedRowView.forNormalCrop(-90,72.49,71,4,.875,.75));
        for(double height:new double[]{Double.NaN,Double.POSITIVE_INFINITY,0,-1,1.1})assertNull(FixedRowView.forNormalCrop(-90,71.62,71,4,height,.75));
    }
    @Test void airborneStartCannotLatchAViewFromTemporaryEyeElevation(){
        for(String crop:CROPS){var s=setup(crop);s.player.position=new Vec3(50.5,71,-41.5);s.player.onGround=false;
            for(int x=51;x<=54;x++){s.field.blocks.put(new BlockPos(x,71,-42),soil(crop));s.field.blocks.put(new BlockPos(x,72,-42),crop);}
            assertThrows(IllegalStateException.class,s.engine::start);assertEquals(SShapeState.STOPPED,s.engine.state());
            assertTrue(s.engine.pauseReason().contains("dry grounded support"));assertFalse(s.input.jump||s.input.left||s.input.right);}
    }
    @Test void absentUnsupportedOrBlockedStartupBodyRefusesBeforeOutlineAdmission(){
        for(String crop:CROPS)for(int fault=0;fault<4;fault++){var s=setup(crop);
            switch(fault){case 0->s.probe.support=Double.NaN;case 1->s.probe.support=69.5;case 2->s.probe.loaded=false;case 3->s.probe.clear=false;}
            assertThrows(IllegalStateException.class,s.engine::start);assertEquals(SShapeState.STOPPED,s.engine.state());
            assertFalse(s.input.forward||s.input.back||s.input.left||s.input.right||s.input.jump||s.breaking.breaking);}
    }
    @Test void actualSettledPitchStaysInsideReferenceRangeWithAndWithoutMouseSteps(){
        for(String crop:CROPS)for(boolean gcd:new boolean[]{false,true}){
            var s=setup(crop);s.config.set(FarmingConfig.ROTATION_USE_MOUSE_GCD,gcd);s.config.set(FarmingConfig.ROTATION_MOUSE_SENSITIVITY,.5);
            s.rotation.pitch=3.4499974f;settle(s);
            assertTrue(s.rotation.pitch>=2.8f&&s.rotation.pitch<=3.3f,"Actual view must not retain default .75 deadband result");
            float pitch=s.rotation.pitch,yaw=s.rotation.yaw;
            for(int i=0;i<100;i++){s.engine.tick();assertEquals(pitch,s.rotation.pitch);assertEquals(yaw,s.rotation.yaw);}
        }
    }
    @Test void coarseMouseStepRefusesRatherThanCallingAnOutOfRangeViewSettled(){
        var s=setup("minecraft:carrots");s.config.set(FarmingConfig.ROTATION_USE_MOUSE_GCD,true);s.config.set(FarmingConfig.ROTATION_MOUSE_SENSITIVITY,1d);
        var error=assertThrows(IllegalStateException.class,s.engine::start);assertTrue(error.getMessage().contains("half-step"));
        assertEquals(SShapeState.STOPPED,s.engine.state());assertFalse(s.input.left||s.input.right||s.input.jump||s.breaking.breaking);
    }
    @Test void fractionalForwardOriginUsesActualEntryAndExitForEveryCardinal(){
        for(float yaw:new float[]{-90,0,90,180}){
            assertNotNull(FixedRowView.forNormalCrop(yaw,71.62,71,1,4,.5625,.2,.19),"Supported ordinary width boundary can keep3 degrees");
            assertNull(FixedRowView.forNormalCrop(yaw,71.62,71,1,4,.5625,.2,.45),"Actual nearest outline exit cannot be replaced by centered model");
            assertNull(FixedRowView.forNormalCrop(yaw,71.62,71,1,4,.5625,.2,Double.NaN));
        }
    }
    @Test void selectedCurrentRayUsesGenericMethodsAndPreservesEveryCallbackFence(){
        for(String crop:CROPS)for(String cut:List.of("none","range","pick","loaded","mature","soil","distance")){
            var events=new ArrayList<String>();boolean[] current={true};
            var read=new FreshHarvestRay.Read(){
                void visit(String event){events.add(event);if(event.equals(cut))current[0]=false;}
                public boolean current(){return current[0];}
                public double range(){visit("range");return 4.5;}
                public FreshHarvestRay.Hit pick(double range){visit("pick");return new FreshHarvestRay.Hit(new BlockPos(51,71,-42),new Vec3(51,71.6,-41.5));}
                public boolean loaded(BlockPos cell){visit("loaded");return true;}
                public boolean matureWheat(BlockPos cell){throw new AssertionError("selected path must not use wheat-only predicate");}
                public boolean farmland(BlockPos cell){throw new AssertionError("selected path must not use farmland-only predicate");}
                public boolean matureSelectedCrop(BlockPos cell){visit("mature");return true;}
                public boolean selectedCropSupport(BlockPos cell){visit("soil");return true;}
                public double distance(FreshHarvestRay.Hit hit){visit("distance");return 1;}
            };
            assertEquals(cut.equals("none"),FreshHarvestRay.available(read),crop+"/"+cut);
            if(!cut.equals("none"))assertEquals(cut,events.getLast(),"No later callback after original scene retirement");
        }
    }
    @Test void selectedImmatureAndWrongSoilRefuseBeforeDistance(){
        for(String crop:CROPS)for(boolean mature:new boolean[]{false,true}){
            var calls=new ArrayList<String>();var read=new FreshHarvestRay.Read(){
                public boolean current(){return true;}public double range(){return 4.5;}
                public FreshHarvestRay.Hit pick(double range){return new FreshHarvestRay.Hit(new BlockPos(51,71,-42),new Vec3(51,71.6,-41.5));}
                public boolean loaded(BlockPos cell){return true;}public boolean matureWheat(BlockPos cell){throw new AssertionError();}
                public boolean farmland(BlockPos cell){throw new AssertionError();}
                public boolean matureSelectedCrop(BlockPos cell){calls.add("mature");return mature;}
                public boolean selectedCropSupport(BlockPos cell){calls.add("soil");return false;}
                public double distance(FreshHarvestRay.Hit hit){throw new AssertionError("denied target must not reach distance");}
            };
            assertFalse(FreshHarvestRay.available(read),crop);assertEquals(mature?List.of("mature","soil"):List.of("mature"),calls);
        }
    }
}
