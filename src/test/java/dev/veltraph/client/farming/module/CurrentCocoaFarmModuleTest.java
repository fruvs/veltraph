package dev.veltraph.client.farming.module;
import dev.veltraph.client.config.*;
import dev.veltraph.client.farming.domain.FarmLayoutRegistry;
import dev.veltraph.client.service.*;
import dev.veltraph.client.service.stub.NoOpInventoryService;
import dev.veltraph.client.testutil.*;
import dev.veltraph.client.world.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CurrentCocoaFarmModuleTest {
    static class Crops implements BlockBreakingService {int guardInstalls;Object guardOwner;java.util.function.Predicate<BlockPos> guard;public void installTargetGuard(Object owner,java.util.function.Predicate<BlockPos> allowed){guardInstalls++;guardOwner=owner;guard=allowed;}public void clearTargetGuard(Object owner){if(owner==guardOwner){guardOwner=null;guard=null;}}String target="minecraft:cocoa";BlockPos fresh;public java.util.Optional<BlockPos> freshTargetBlockPos(){return java.util.Optional.ofNullable(fresh);}public java.util.Optional<BlockPos> targetBlockPos(){return java.util.Optional.of(new BlockPos(2,70,4));}boolean breaking;public void setBreaking(boolean value){breaking=value;}public boolean isBreaking(){return breaking;}public String targetBlockId(){return target;}}
    static class World implements WorldService {boolean loaded=true;String age="2";public java.util.Optional<String> blockProperty(BlockPos p,String key){return loaded&&key.equals("age")&&age!=null?java.util.Optional.of(age):java.util.Optional.empty();}public boolean isLoaded(BlockPos p){return loaded;}public boolean isPassable(BlockPos p){return p.y()!=68;}public boolean isStandable(BlockPos p){return p.y()==68;}public String blockId(BlockPos p){return p.equals(new BlockPos(2,70,4))?"minecraft:cocoa":p.y()==68?"minecraft:snow":"minecraft:air";}}
    @Test void currentModeUsesPostedAnglesOnlyADAndCropSafeAttackThenReleases(){
        var config=new MapConfigService();config.set(FarmingConfig.COCOA_FARM_MODE,CocoaFarmMode.CURRENT_TWO_KEY_LOOP);
        var player=new FakePlayerService();player.position=new Vec3(2.5,68.375,90.5);player.blockPos=player.position.toBlockPos();player.horizontalSpeed=.03;
        var rotation=new FakeRotationService();rotation.yaw=-150;rotation.pitch=-79;var input=new RecordingInputService();var breaking=new Crops();var clock=new FakeClock();var world=new World();
        var module=new CocoaFarmModule(new FarmServices(clock,player,world,input,rotation,breaking,new NoOpInventoryService()),config,FarmLayoutRegistry.createDefault());
        module.setEnabled(true);assertEquals("cocoa_two_key_current",module.activeProfile().id());
        for(int i=0;i<20;i++){clock.advanceMillis(50);player.position=new Vec3(2.5,68.375,90.5-i*.03);float yaw=rotation.yaw;module.onTick();assertTrue(Math.abs(dev.veltraph.client.farming.movement.Angles.signedDelta(yaw,rotation.yaw))<=6.2);}
        assertFalse(input.left);assertFalse(input.right);assertFalse(input.forward);assertFalse(input.back);assertFalse(input.jump);assertFalse(input.sneak);assertFalse(input.sprint);assertTrue(breaking.breaking);
        assertEquals(0,module.currentTwoKeyEnds());assertTrue(module.diagnostics().contains("native Cocoa ray harvest"));
        assertTrue(module.diagnostics().contains("-166.4/-79"));breaking.target="minecraft:jungle_log";clock.advanceMillis(50);module.onTick();assertFalse(breaking.breaking);assertFalse(input.left);clock.advanceMillis(50);module.onTick();assertTrue(input.left);assertFalse(input.right);
        module.setEnabled(false);assertFalse(input.left);assertFalse(input.right);assertFalse(breaking.breaking);
    }
    @Test void postedOrientationAnglesRemainSeparateFromClassicalMode(){
        for(float[] pose:new float[][]{{180,-166.4f},{0,13.5f},{-90,-76.5f},{90,103.5f}}){
            var cfg=new MapConfigService();cfg.set(FarmingConfig.COCOA_FARM_MODE,CocoaFarmMode.CURRENT_TWO_KEY_LOOP);
            var p=new FakePlayerService();p.position=new Vec3(0,68.375,0);var r=new FakeRotationService();r.yaw=pose[0];
            var m=new CocoaFarmModule(new FarmServices(new FakeClock(),p,new World(),new RecordingInputService(),r,new Crops(),new NoOpInventoryService()),cfg,FarmLayoutRegistry.createDefault());
            m.setEnabled(true);assertTrue(m.diagnostics().contains("posted aim="+pose[1]+"/-79"));m.setEnabled(false);
        }
    }
    @Test void failedCurrentObservationReleasesInputsAndRetainsTheReasonAfterAutomaticDisable(){
        var cfg=new MapConfigService();cfg.set(FarmingConfig.COCOA_FARM_MODE,CocoaFarmMode.CURRENT_TWO_KEY_LOOP);
        var player=new FakePlayerService();player.position=new Vec3(2.5,68.375,4.5);
        var rotation=new FakeRotationService();rotation.yaw=-166.4f;rotation.pitch=-79;
        var world=new World();world.loaded=false;
        var input=new RecordingInputService();input.left=true;var breaking=new Crops();breaking.breaking=true;
        var module=new CocoaFarmModule(new FarmServices(new FakeClock(),player,world,input,rotation,breaking,new NoOpInventoryService()),cfg,FarmLayoutRegistry.createDefault());
        module.setEnabled(true);module.onTick();
        assertFalse(module.isEnabled());assertFalse(input.left);assertFalse(input.right);assertFalse(breaking.breaking);
        assertTrue(module.diagnostics().contains("stopped: BLOCKED"));
        assertTrue(module.diagnostics().contains("Authored loaded snow/slab corridor required"));
        world.loaded=true;module.setEnabled(true);assertFalse(module.diagnostics().contains("stopped:"));module.setEnabled(false);
    }

    @Test void enabledLifetimeGuardChecksTheSuppliedLoadedCoordinateForBothCocoaModes(){
        for(var mode:CocoaFarmMode.values()){
            var cfg=new MapConfigService();cfg.set(FarmingConfig.COCOA_FARM_MODE,mode);
            var crop=new BlockPos(2,70,4);var log=new BlockPos(3,70,4);
            var world=new World(){@Override public String blockId(BlockPos p){return p.equals(crop)?"minecraft:cocoa":p.equals(log)?"minecraft:jungle_log":super.blockId(p);}};
            var player=new FakePlayerService();player.position=new Vec3(2.5,68.375,4.5);
            var breaking=new Crops();var module=new CocoaFarmModule(new FarmServices(new FakeClock(),player,world,new RecordingInputService(),new FakeRotationService(),breaking,new NoOpInventoryService()),cfg,FarmLayoutRegistry.createDefault());
            module.setEnabled(true);assertSame(module,breaking.guardOwner);assertNotNull(breaking.guard);
            assertTrue(breaking.guard.test(crop));assertFalse(breaking.guard.test(log));
            world.loaded=false;assertFalse(breaking.guard.test(crop));world.loaded=true;
            module.setEnabled(false);assertNull(breaking.guard);assertNull(breaking.guardOwner);
        }
    }

    @Test void confirmedRewarpResumeRebindsGuardOnceForBothModes(){
        for(var mode:CocoaFarmMode.values()){
            var cfg=new MapConfigService();cfg.set(FarmingConfig.COCOA_FARM_MODE,mode);
            var player=new FakePlayerService();player.position=new Vec3(2.5,68.375,4.5);
            var breaking=new Crops();var module=new CocoaFarmModule(new FarmServices(new FakeClock(),player,new World(),new RecordingInputService(),new FakeRotationService(),breaking,new NoOpInventoryService()),cfg,FarmLayoutRegistry.createDefault());
            module.setEnabled(true);assertEquals(1,breaking.guardInstalls);
            module.suspendForRewarp();assertEquals(1,breaking.guardInstalls);
            module.resumeAfterRewarp();assertEquals(2,breaking.guardInstalls);assertSame(module,breaking.guardOwner);
            module.resumeAfterRewarp();assertEquals(2,breaking.guardInstalls);
            module.setEnabled(false);assertNull(breaking.guard);
        }
    }

    @Test void bothModesProtectImmatureAndUnknownNativeAgeWithoutChangingGeometry(){
        for(var mode:CocoaFarmMode.values()){
            var cfg=new MapConfigService();cfg.set(FarmingConfig.COCOA_FARM_MODE,mode);
            var world=new World();world.age="0";var player=new FakePlayerService();player.position=new Vec3(2.5,68.375,4.5);
            var rotation=new FakeRotationService();rotation.yaw=-166.4f;rotation.pitch=-79;
            var breaking=new Crops();var input=new RecordingInputService();var clock=new FakeClock();
            var module=new CocoaFarmModule(new FarmServices(clock,player,world,input,rotation,breaking,new NoOpInventoryService()),cfg,FarmLayoutRegistry.createDefault());
            module.setEnabled(true);var target=new BlockPos(2,70,4);
            assertFalse(breaking.guard.test(target));world.age="1";assertFalse(breaking.guard.test(target));
            world.age=null;assertFalse(breaking.guard.test(target));world.age="2";assertTrue(breaking.guard.test(target));world.age="0";
            if(mode==CocoaFarmMode.CURRENT_TWO_KEY_LOOP){
                clock.advanceMillis(50);module.onTick();assertFalse(breaking.breaking);assertTrue(input.left);assertEquals(0,module.currentTwoKeyEnds());
                world.age=null;assertFalse(breaking.guard.test(target));clock.advanceMillis(50);module.onTick();assertFalse(breaking.breaking);
                world.age="2";assertTrue(breaking.guard.test(target));clock.advanceMillis(50);module.onTick();assertTrue(breaking.breaking);assertFalse(input.left);
            }
            module.setEnabled(false);
        }
    }

    @Test void freshMatureNativeRayOnlyHoldsMovementAndNeverAuthorizesCachedWoodAttack(){
        var cfg=new MapConfigService();cfg.set(FarmingConfig.COCOA_FARM_MODE,CocoaFarmMode.CURRENT_TWO_KEY_LOOP);
        var player=new FakePlayerService();player.position=new Vec3(2.5,68.375,4.5);
        var rotation=new FakeRotationService();rotation.yaw=-166.4f;rotation.pitch=-79;
        var world=new World();var input=new RecordingInputService();var breaking=new Crops();breaking.target="minecraft:jungle_log";breaking.fresh=new BlockPos(2,70,4);
        var clock=new FakeClock();var module=new CocoaFarmModule(new FarmServices(clock,player,world,input,rotation,breaking,new NoOpInventoryService()),cfg,FarmLayoutRegistry.createDefault());
        module.setEnabled(true);clock.advanceMillis(50);module.onTick();
        assertFalse(input.left);assertFalse(input.right);assertFalse(breaking.breaking);assertEquals(0,module.currentTwoKeyEnds());
        breaking.target="minecraft:cocoa";clock.advanceMillis(50);module.onTick();assertTrue(breaking.breaking);assertFalse(input.left);
        world.age="0";clock.advanceMillis(100);module.onTick();assertFalse(breaking.breaking);assertTrue(input.left);
        module.setEnabled(false);
    }

}
