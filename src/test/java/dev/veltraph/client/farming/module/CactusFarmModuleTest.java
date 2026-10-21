package dev.veltraph.client.farming.module;

import dev.veltraph.client.config.MapConfigService;
import dev.veltraph.client.core.MacroState;
import dev.veltraph.client.farming.domain.FarmCard;
import dev.veltraph.client.farming.domain.FarmLayoutRegistry;
import dev.veltraph.client.farming.domain.FarmMovementMode;
import dev.veltraph.client.farming.movement.MovementState;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.stub.NoOpInventoryService;
import dev.veltraph.client.service.stub.NoOpRotationService;
import dev.veltraph.client.testutil.FakeClock;
import dev.veltraph.client.testutil.FakePlayerService;
import dev.veltraph.client.testutil.FakeWorldService;
import dev.veltraph.client.testutil.RecordingBlockBreaking;
import dev.veltraph.client.testutil.RecordingInputService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CactusFarmModuleTest {

    private FakeClock clock;
    private FakeWorldService world;
    private RecordingInputService input;
    private RecordingBlockBreaking breaking;
    private CactusFarmModule cactus;

    @BeforeEach
    void setUp() {
        clock = new FakeClock();
        world = new FakeWorldService();
        FakePlayerService player = new FakePlayerService();
        player.onGround = true;
        input = new RecordingInputService();
        breaking = new RecordingBlockBreaking();
        MapConfigService config = new MapConfigService();
        FarmServices services = new FarmServices(clock, player, world, input,
                new NoOpRotationService(), breaking, new NoOpInventoryService());
        cactus = new CactusFarmModule(services, config, FarmLayoutRegistry.createDefault());
    }

    /** Tick the shared engine until it reaches FARMING (PREPARE_TOOL → AIMING → FARMING). */
    private void driveToFarming() {
        clock.setMillis(0);
        cactus.setEnabled(true);
        for (int i = 0; i < 4 && cactus.movementState() != MovementState.FARMING; i++) {
            cactus.onTick();
        }
        assertEquals(MovementState.FARMING, cactus.movementState());
    }

    @Test
    void cactusProfileUsesSharedStraightMode() {
        assertEquals(FarmMovementMode.STRAIGHT, cactus.activeProfile().movementMode());
        assertEquals(FarmCard.CACTUS, cactus.activeProfile().card());
    }

    @Test
    void enableStartsInPrepareToolThenReachesFarming() {
        clock.setMillis(0);
        cactus.setEnabled(true);
        assertEquals(MacroState.RUNNING, cactus.state());
        assertEquals(MovementState.PREPARE_TOOL, cactus.movementState());
        driveToFarming();
    }

    @Test
    void cactusUsesSharedRowTraversalTurnAtRowEnd() {
        driveToFarming();
        world.walkable = false;     // forward lane invalid -> row-end predicate true
        clock.setMillis(20_000);    // cooldown elapsed
        cactus.onTick();
        assertEquals(MovementState.TURNING, cactus.movementState(),
                "Cactus reuses the shared MovementDirection row-end turn logic");
    }

    @Test
    void crosshairGuardProtectsCactusBaseAndStructuralBlocks() {
        var target=new dev.veltraph.client.world.BlockPos(0,65,1);
        var hit=new dev.veltraph.client.service.BlockBreakingService() {
            boolean held;String id="minecraft:cactus";
            public void setBreaking(boolean value){held=value;}
            public boolean isBreaking(){return held;}
            public String targetBlockId(){return id;}
            public java.util.Optional<dev.veltraph.client.world.BlockPos> targetBlockPos(){return java.util.Optional.of(target);}
        };
        var cropWorld=new FakeWorldService() {
            boolean base=true;
            public String blockId(dev.veltraph.client.world.BlockPos p){return base&&p.equals(target.down())?"minecraft:cactus":"minecraft:sand";}
        };
        cactus=new CactusFarmModule(new FarmServices(clock,new FakePlayerService(),cropWorld,input,new NoOpRotationService(),hit,new NoOpInventoryService()),new MapConfigService(),FarmLayoutRegistry.createDefault());
        driveToFarming();cactus.onTick();assertTrue(hit.held);
        cropWorld.base=false;cactus.onTick();assertFalse(hit.held,"plant base must survive");
        cropWorld.base=true;hit.id="minecraft:sand";cactus.onTick();assertFalse(hit.held);
        cactus.setEnabled(false);
    }

    @Test
    void aceHoldsAttackThroughStaleRenderedAirWhileActualGuardPreservesBases() {
        var upper=new dev.veltraph.client.world.BlockPos(335,75,94);
        var player=new FakePlayerService();player.position=new dev.veltraph.client.world.Vec3(335.5,73.5,95.5);
        var authored=new FakeWorldService(){
            boolean loaded=true;
            public boolean isLoaded(dev.veltraph.client.world.BlockPos p){return loaded;}
            public boolean hasGroundSupport(dev.veltraph.client.world.Vec3 feet,double width){return loaded;}
            public java.util.Optional<String> blockProperty(dev.veltraph.client.world.BlockPos p,String name){return name.equals("type")?java.util.Optional.of("bottom"):java.util.Optional.empty();}
            public String blockId(dev.veltraph.client.world.BlockPos p){
                if(p.equals(upper)||p.equals(upper.down()))return "minecraft:cactus";
                return p.y()==73?"minecraft:quartz_slab":"minecraft:air";
            }
        };
        var guarded=new dev.veltraph.client.service.BlockBreakingService(){
            boolean breaking;public void setBreaking(boolean held){breaking=held;}public boolean isBreaking(){return breaking;}
            java.util.function.Predicate<dev.veltraph.client.world.BlockPos> guard;
            public void installTargetGuard(Object owner,java.util.function.Predicate<dev.veltraph.client.world.BlockPos> predicate){guard=predicate;}
            public String targetBlockId(){return "minecraft:air";}
            public java.util.Optional<dev.veltraph.client.world.BlockPos> targetBlockPos(){return java.util.Optional.of(upper);}
        };
        cactus=new CactusFarmModule(new FarmServices(clock,player,authored,input,new NoOpRotationService(),guarded,new NoOpInventoryService()),new MapConfigService(),FarmLayoutRegistry.createDefault());
        cactus.setAcePtp(true);cactus.setEnabled(true);cactus.onTick();
        assertTrue(guarded.breaking,"A just-destroyed rendered target cannot drop held attack on the next exposed column");
        assertTrue(guarded.guard.test(upper));assertFalse(guarded.guard.test(upper.down()));assertFalse(guarded.guard.test(upper.down().down()));
        authored.loaded=false;cactus.onTick();assertFalse(guarded.breaking,"Unknown actor geometry still releases input");
        assertFalse(guarded.guard.test(upper));cactus.setEnabled(false);
    }

    @Test
    void terminalFenceDrainsReachableUpperTargetBeforeSavedResetReadiness() {
        var upper=new dev.veltraph.client.world.BlockPos(0,75,5);
        var player=new FakePlayerService();player.position=new dev.veltraph.client.world.Vec3(5.5,73.5,6.3);
        var authored=new FakeWorldService(){
            boolean predictedAir;
            public String blockId(dev.veltraph.client.world.BlockPos p){
                if(p.equals(upper))return predictedAir?"minecraft:air":"minecraft:cactus";
                if(p.equals(upper.down()))return "minecraft:cactus";
                if(p.z()==7&&p.y()==73)return "minecraft:red_stained_glass";
                if(p.z()==7&&p.y()==72)return "minecraft:red_terracotta";
                if(p.x()==0&&p.y()==73)return "minecraft:oak_fence";
                return p.y()==73?"minecraft:quartz_slab":"minecraft:air";
            }
            public boolean bodyClear(dev.veltraph.client.world.Vec3 feet,double width,double height){return feet.x()>=.925;}
            public boolean hasGroundSupport(dev.veltraph.client.world.Vec3 feet,double width){return true;}
            public java.util.Optional<String> blockProperty(dev.veltraph.client.world.BlockPos p,String name){return name.equals("type")?java.util.Optional.of("bottom"):java.util.Optional.empty();}
        };
        var guarded=new dev.veltraph.client.service.BlockBreakingService(){
            boolean held,upperVisible=true,observed=true;java.util.Set<dev.veltraph.client.world.BlockPos> pending=java.util.Set.of();
            public void setBreaking(boolean value){held=value;}public boolean isBreaking(){return held;}
            public java.util.Optional<dev.veltraph.client.world.BlockPos> freshTargetBlockPos(){return upperVisible?java.util.Optional.of(upper):java.util.Optional.empty();}
            public java.util.Optional<java.util.Set<dev.veltraph.client.world.BlockPos>> pendingBlockChanges(Object owner){return observed?java.util.Optional.of(pending):java.util.Optional.empty();}
        };
        cactus=new CactusFarmModule(new FarmServices(clock,player,authored,input,new NoOpRotationService(),guarded,new NoOpInventoryService()),new MapConfigService(),FarmLayoutRegistry.createDefault());
        cactus.setAcePtp(true);cactus.setEnabled(true);cactus.onTick();
        player.position=new dev.veltraph.client.world.Vec3(.925,73.5,6.3);cactus.onTick();
        assertTrue(guarded.held,"A native reachable upper segment must finish at the terminal fence");
        assertFalse(input.left);assertFalse(input.right,"Fence harvesting releases lateral pressure and awaits native targets/receipts");assertFalse(cactus.atAceEnd());assertFalse(cactus.cycleEndReady());
        guarded.upperVisible=false;authored.predictedAir=true;guarded.pending=java.util.Set.of(upper);cactus.onTick();
        assertTrue(guarded.held);assertFalse(cactus.atAceEnd(),"Predicted AIR cannot hide an outstanding actual target coordinate");
        guarded.observed=false;cactus.onTick();assertFalse(guarded.held);assertFalse(input.right);assertFalse(cactus.cycleEndReady(),"Unknown receipt cannot act as acknowledgment");
        guarded.observed=true;guarded.pending=java.util.Set.of();cactus.onTick();
        assertFalse(guarded.held);assertTrue(cactus.atAceEnd());assertTrue(cactus.cycleEndReady());
        cactus.setEnabled(false);
    }

    @Test
    void raisedLipCorrectionUsesOrdinaryPrecisionKeysThenResumesOrCancelsContext() {
        var player=new FakePlayerService();player.position=new dev.veltraph.client.world.Vec3(5.5,74,6.2995);
        var slab=new FakeWorldService(){
            public String blockId(dev.veltraph.client.world.BlockPos p){return p.y()==73&&p.z()==6?"minecraft:quartz_slab":"minecraft:air";}
            public java.util.Optional<String> blockProperty(dev.veltraph.client.world.BlockPos p,String name){return name.equals("type")?java.util.Optional.of("bottom"):java.util.Optional.empty();}
            public boolean hasGroundSupport(dev.veltraph.client.world.Vec3 feet,double width){return true;}
        };
        var guarded=new dev.veltraph.client.service.BlockBreakingService(){
            boolean held,known=true;public void setBreaking(boolean value){held=value;}public boolean isBreaking(){return held;}
            public java.util.Optional<java.util.Set<dev.veltraph.client.world.BlockPos>> pendingBlockChanges(Object owner){return known?java.util.Optional.of(java.util.Set.of()):java.util.Optional.empty();}
        };
        cactus=new CactusFarmModule(new FarmServices(clock,player,slab,input,new NoOpRotationService(),guarded,new NoOpInventoryService()),new MapConfigService(),FarmLayoutRegistry.createDefault());
        cactus.setAcePtp(true);cactus.setEnabled(true);cactus.onTick();assertTrue(cactus.aceRecenteringActive());assertFalse(guarded.held);
        clock.advanceTick();cactus.onTick();assertTrue(input.forward);assertTrue(input.sneak);assertFalse(input.left);assertFalse(input.right);assertFalse(guarded.held);
        player.position=new dev.veltraph.client.world.Vec3(5.5,73.92,6.435);player.onGround=false;clock.advanceTick();cactus.onTick();assertFalse(input.forward);assertFalse(input.sneak);
        player.position=new dev.veltraph.client.world.Vec3(5.5,73.5,6.6);player.onGround=true;clock.advanceTick();cactus.onTick();clock.advanceTick();cactus.onTick();
        assertFalse(cactus.aceRecenteringActive());assertTrue(input.right);assertTrue(guarded.held);assertFalse(input.forward);assertFalse(input.sneak);
        player.position=new dev.veltraph.client.world.Vec3(5.5,74,6.2995);clock.advanceTick();cactus.onTick();assertTrue(cactus.aceRecenteringActive());
        guarded.known=false;clock.advanceTick();cactus.onTick();assertFalse(cactus.isEnabled());assertFalse(input.right);assertFalse(input.forward);assertFalse(guarded.held);
    }

    @Test
    void recoveryRetainsVisibleInterruptedColumnBeforeOrdinaryTraversalResumes() {
        var target=new dev.veltraph.client.world.BlockPos(361,75,4);
        var player=new dev.veltraph.client.service.PlayerService(){
            dev.veltraph.client.world.Vec3 p=new dev.veltraph.client.world.Vec3(361.345,74,6.2995);
            public boolean isAvailable(){return true;}public dev.veltraph.client.world.Vec3 position(){return p;}
            public double blockInteractionRange(){return 4.5;}public dev.veltraph.client.world.BlockPos blockPos(){return p.toBlockPos();}
            public float yaw(){return 0;}public float pitch(){return 0;}public boolean onGround(){return true;}
            public double horizontalSpeed(){return 0;}public dev.veltraph.client.world.Direction facing(){return dev.veltraph.client.world.Direction.SOUTH;}
        };
        var world=new FakeWorldService(){
            boolean live=true;
            public String blockId(dev.veltraph.client.world.BlockPos p){if((p.equals(target)&&live)||p.equals(target.down()))return "minecraft:cactus";return p.y()==73&&p.z()==6?"minecraft:quartz_slab":"minecraft:air";}
            public java.util.Optional<String> blockProperty(dev.veltraph.client.world.BlockPos p,String name){return name.equals("type")?java.util.Optional.of("bottom"):java.util.Optional.empty();}
            public boolean hasGroundSupport(dev.veltraph.client.world.Vec3 p,double width){return true;}
        };
        var breaking=new dev.veltraph.client.service.BlockBreakingService(){
            boolean held;java.util.Set<dev.veltraph.client.world.BlockPos> pending=java.util.Set.of();
            public void setBreaking(boolean value){held=value;}public boolean isBreaking(){return held;}
            public java.util.Optional<dev.veltraph.client.world.BlockPos> freshTargetBlockPos(){return world.live&&player.p.x()>361.0625&&player.p.x()<361.9375?java.util.Optional.of(target):java.util.Optional.empty();}
            public java.util.Optional<java.util.Set<dev.veltraph.client.world.BlockPos>> pendingBlockChanges(Object owner){return java.util.Optional.of(pending);}
        };
        cactus=new CactusFarmModule(new FarmServices(clock,player,world,input,new NoOpRotationService(),breaking,new NoOpInventoryService()),new MapConfigService(),FarmLayoutRegistry.createDefault());
        cactus.setAcePtp(true);cactus.setEnabled(true);cactus.onTick();assertTrue(cactus.aceRecenteringActive());
        player.p=new dev.veltraph.client.world.Vec3(360.148,73.5,6.30876);clock.advanceTick();cactus.onTick();clock.advanceTick();cactus.onTick();
        assertTrue(cactus.aceDeferredHarvestActive());assertTrue(input.left);assertTrue(input.sneak);assertFalse(breaking.held);
        player.p=new dev.veltraph.client.world.Vec3(361.14,73.5,6.30876);clock.advanceTick();cactus.onTick();clock.advanceTick();cactus.onTick();
        assertTrue(breaking.held);assertFalse(input.left);assertFalse(input.right);
        world.live=false;breaking.pending=java.util.Set.of(target);clock.advanceTick();cactus.onTick();assertTrue(cactus.aceDeferredHarvestActive());
        breaking.pending=java.util.Set.of();clock.advanceTick();cactus.onTick();assertFalse(cactus.aceDeferredHarvestActive());assertTrue(input.right);assertTrue(breaking.held);
        cactus.setEnabled(false);
    }

    @Test
    void aceSavedEndCannotRequestResetBeforeRedBankFenceContact() {
        assertTrue(cactus.cycleEndReady());
        cactus.setAcePtp(true);
        assertFalse(cactus.cycleEndReady(),"A saved marker alone cannot complete the source course");
        cactus.setEnabled(true);
        cactus.onTick();
        assertFalse(cactus.cycleEndReady());
        assertFalse(cactus.suspendForCycleReset());
        assertFalse(cactus.isSuspendedForCycleReset());
        cactus.setEnabled(false);
    }

    @Test
    void disableReleasesInputsAndStops() {
        clock.setMillis(0);
        cactus.setEnabled(true);
        cactus.setEnabled(false);
        assertEquals(MacroState.STOPPED, cactus.state());
        assertEquals(MovementState.STOPPED, cactus.movementState());
        assertTrue(input.releaseAllCount >= 1);
        assertFalse(breaking.breaking);
        assertFalse(input.forward);
    }
}
