package dev.veltraph.client.mining;

import dev.veltraph.client.config.*;
import dev.veltraph.client.service.*;
import dev.veltraph.client.service.stub.NoOpInventoryService;
import dev.veltraph.client.testutil.*;
import dev.veltraph.client.world.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class MiningControllerTest {
    static final BlockPos TARGET=new BlockPos(0,63,0);
    static class Services implements MiningServices {
        final FakeClock clock=new FakeClock();final FakePlayerService player=new FakePlayerService();
        final RecordingInputService input=new RecordingInputService();
        boolean loaded=true,available=true,capacity=true,aimed=true,wall=false,safe=true,heldTool=true;
        String id="minecraft:cobblestone",problem="",tool="",identity="world one";
        int walked,stopped,collected;BlockPos checked;
        final BlockBreakingService breaking=new BlockBreakingService(){
            boolean held;public void setBreaking(boolean b){held=b;}public boolean isBreaking(){return held;}
            @Override public Optional<BlockPos> targetBlockPos(){return Optional.of(TARGET);}
        };
        final WorldService world=new FakeWorldService(){
            @Override public boolean isLoaded(BlockPos p){return loaded;}
            @Override public String blockId(BlockPos p){return id;}
            @Override public boolean isPassable(BlockPos p){return id.equals("minecraft:air");}
        };
        final FarmServices farm=new FarmServices(clock,player,world,input,new FakeRotationService(),breaking,new NoOpInventoryService());
        public FarmServices farm(){return farm;}public boolean available(){return available;}public String worldIdentity(){return identity;}
        public String selectTool(BlockPos p,int reserve){return tool;}
        public boolean hasCapacity(BlockPos p){return capacity;}public boolean heldToolReady(BlockPos p,int r){return heldTool;}
        public String targetProblem(BlockPos p,boolean floor){checked=p;return problem;}
        public boolean aimedAt(BlockPos p){return aimed;}
        public boolean safeStep(boolean f){return safe;}public boolean endWall(boolean f){return wall;}
        public boolean walkTo(BlockPos p){walked++;return safe;}
        public void stopWalking(){stopped++;input.releaseAllMovement();}
        public String navigationStatus(){return "blocked";}
        public void collectDrops(BlockPos p){collected++;}
    }
    @Test void breaksOnlyWhenReadyAndCountsLoadedWorldRemoval(){
        var s=new Services();var c=new MiningController(MiningMode.MINE_DOWN,s,new MapConfigService());c.start();c.tick();
        assertTrue(s.breaking.isBreaking());s.id="minecraft:air";c.tick();
        assertEquals(1,c.removed());assertFalse(s.breaking.isBreaking());assertEquals(MiningController.State.SETTLING,c.state());assertEquals(1,s.collected);
    }
    @Test void inventoryPauseRecoversWithoutAttack(){
        var s=new Services();var c=new MiningController(MiningMode.COBBLESTONE,s,new MapConfigService());c.start();s.capacity=false;c.tick();
        assertEquals(MiningController.State.PAUSED,c.state());assertFalse(s.breaking.isBreaking());
        s.capacity=true;c.tick();assertTrue(s.breaking.isBreaking());
    }
    @Test void brokenToolAndHazardReleaseAndRecover(){
        var s=new Services();var c=new MiningController(MiningMode.MINE_DOWN,s,new MapConfigService());c.start();c.tick();s.tool="Worn tool";c.tick();assertFalse(s.breaking.isBreaking());
        s.tool="";s.problem="Lava beside target";c.tick();assertEquals("Lava beside target",c.reason());
        s.problem="";c.tick();assertTrue(s.breaking.isBreaking());
    }
    @Test void unloadDoesNotCountRemovalAndReleases(){
        var s=new Services();var c=new MiningController(MiningMode.MINE_DOWN,s,new MapConfigService());c.start();c.tick();s.loaded=false;s.id="minecraft:air";c.tick();
        assertEquals(0,c.removed());assertFalse(s.breaking.isBreaking());
    }
    @Test void timeoutIsLatchedUntilRestart(){
        var s=new Services();var cfg=new MapConfigService();cfg.set(MiningConfig.MINING_BREAK_TIMEOUT_MS,500L);
        var c=new MiningController(MiningMode.MINE_DOWN,s,cfg);c.start();c.tick();s.clock.advanceMillis(501);c.tick();assertFalse(s.breaking.isBreaking());
        c.tick();assertEquals(MiningController.State.PAUSED,c.state());c.start();c.tick();assertTrue(s.breaking.isBreaking());
    }
    @Test void mineDownStopsAtConfiguredDepthAndWaitsForLanding(){
        var s=new Services();var cfg=new MapConfigService();cfg.set(MiningConfig.MINING_MIN_Y,64);
        var c=new MiningController(MiningMode.MINE_DOWN,s,cfg);c.start();c.tick();assertEquals(MiningController.State.COMPLETE,c.state());
        cfg.set(MiningConfig.MINING_MIN_Y,0);c.start();s.player.onGround=false;c.tick();assertFalse(s.breaking.isBreaking());
    }
    @Test void cobbleBounceRequiresSafeStepAndStopReleases(){
        var s=new Services();var cfg=new MapConfigService();cfg.set(MiningConfig.COBBLE_AUTO_WALK,true);s.wall=true;
        var c=new MiningController(MiningMode.COBBLESTONE,s,cfg);c.start();c.tick();assertFalse(c.forward());
        s.safe=false;c.tick();assertFalse(s.input.back);c.stop();assertFalse(s.breaking.isBreaking());
    }
    @Test void echoWalksBeforeMiningAndAdvancesOnlyAfterRemoval(){
        var s=new Services();var cfg=new MapConfigService();cfg.set(MiningConfig.ECHO_ROUTE,"1,64,0>0,63,0;2,64,0>0,63,0");
        var c=new MiningController(MiningMode.ECHO,s,cfg);c.start();c.tick();assertEquals(MiningController.State.WALKING,c.state());assertFalse(s.breaking.isBreaking());
        s.player.position=new dev.veltraph.client.world.Vec3(1.5,64,.5);c.tick();assertTrue(s.breaking.isBreaking());assertEquals(0,c.checkpoint());
        s.id="minecraft:air";c.tick();assertEquals(1,c.checkpoint());c.stop();
    }
    @Test void echoRetainsSessionOnlyForSameRouteWhenEnabled(){
        var s=new Services();var cfg=new MapConfigService();cfg.set(MiningConfig.ECHO_ROUTE,"0,64,0>0,63,0");cfg.set(MiningConfig.ECHO_REMEMBER_LAST_SESSION,true);
        var c=new MiningController(MiningMode.ECHO,s,cfg);c.start();c.tick();s.id="minecraft:air";c.tick();assertEquals(1,c.checkpoint());c.start();assertEquals(1,c.checkpoint());
        c.resetAtRewarp();assertEquals(0,c.checkpoint());
    }
    @Test void cobbleFrontScanRetargetsFeetAndNeverSupportingFloor(){
        var s=new Services();var c=new MiningController(MiningMode.COBBLESTONE,s,new MapConfigService());c.start();c.tick();
        assertEquals(new BlockPos(0,64,-1),s.checked);assertTrue(s.breaking.isBreaking());
    }
    @Test void changedWorldLatchesPauseAndResetsRememberedRoute(){
        var s=new Services();var cfg=new MapConfigService();cfg.set(MiningConfig.ECHO_ROUTE,"0,64,0>0,63,0;1,64,0>0,63,0");cfg.set(MiningConfig.ECHO_REMEMBER_LAST_SESSION,true);
        var c=new MiningController(MiningMode.ECHO,s,cfg);c.start();c.tick();s.id="minecraft:air";c.tick();assertEquals(1,c.checkpoint());
        s.identity="world two";c.tick();assertEquals(MiningController.State.PAUSED,c.state());assertFalse(s.breaking.isBreaking());
        c.tick();assertEquals(MiningController.State.PAUSED,c.state());c.start();assertEquals(0,c.checkpoint());
    }
    @Test void interactionGuardRejectsRetargetsAndChangedToolHazardWorldBeforeNextTick(){
        var s=new Services();var c=new MiningController(MiningMode.MINE_DOWN,s,new MapConfigService());c.start();c.tick();
        assertTrue(c.allowsTarget(TARGET));assertFalse(c.allowsTarget(TARGET.up()));
        s.heldTool=false;assertFalse(c.allowsTarget(TARGET));s.heldTool=true;
        s.problem="Lava";assertFalse(c.allowsTarget(TARGET));s.problem="";
        s.identity="new world";assertFalse(c.allowsTarget(TARGET));s.identity="world one";
        s.id="minecraft:air";assertFalse(c.allowsTarget(TARGET));c.stop();assertFalse(c.allowsTarget(TARGET));
    }
    @Test void invalidRouteIsRejected(){assertThrows(IllegalArgumentException.class,()->EchoCheckpoint.parse("1,2>3,4,5"));assertThrows(IllegalArgumentException.class,()->EchoCheckpoint.parse("40000000,64,0>0,63,0"));}

    @Test void echoSkippingDefaultMatchesSourceEnabled(){assertTrue(new MapConfigService().get(MiningConfig.ECHO_ALLOW_CHECKPOINT_SKIPPING));}
    @Test void echoChoosesFirstFutureDuplicateOnlyOnStableBlock(){var s=new Services();var cfg=new MapConfigService();cfg.set(MiningConfig.ECHO_ALLOW_CHECKPOINT_SKIPPING,true);cfg.set(MiningConfig.ECHO_ROUTE,"0,64,0>0,63,0;0,64,2>0,63,2;0,64,0>0,63,0;0,64,2>0,63,2;2,64,2>2,63,2");var c=new MiningController(MiningMode.ECHO,s,cfg);c.start();c.tick();s.player.position=new dev.veltraph.client.world.Vec3(.5,64,2.5);s.player.blockPos=new BlockPos(0,64,2);c.tick();assertEquals(0,c.checkpoint());c.tick();assertEquals(1,c.checkpoint());}
}
