package dev.veltraph.client.foraging.hub;

import dev.veltraph.client.core.AbstractModule;
import dev.veltraph.client.core.ModuleCategory;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.foraging.TreeDiscovery;
import dev.veltraph.client.input.ScriptingInputLock;
import dev.veltraph.client.rotation.OwnedRotationTrajectory;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure ownership integration around the actual Hub FSM, real manager and rotation trajectory.
 * This is not installed/native acceptance; no Minecraft player/pose/readiness is fabricated. */
class HubTerminalOwnershipTest {
    private static final class Port implements HubSourceLoop.Port {
        int releases,requests,walks;boolean manualHeld;
        public boolean inHub(){return true;}public boolean sourceStopRequested(){return false;}
        public BlockPos playerBlock(){return new BlockPos(-20,71,-27);}public Vec3 playerPosition(){return new Vec3(-20,71,-27);}
        public boolean pathExists(BlockPos target){return false;}public boolean startAnchorWalk(BlockPos target){walks++;return true;}
        public HubSourceLoop.Motion anchorMotion(){return HubSourceLoop.Motion.FAILED;}
        public boolean requestHubWarp(){requests++;return false;}public HubSourceLoop.Motion hubWarpMotion(){return HubSourceLoop.Motion.FAILED;}
        public void release(){releases++;manualHeld=false;}
        public TreeDiscovery.Tree treeAt(BlockPos p){return null;}public BlockPos standingPosition(BlockPos p){return null;}
        public boolean blacklisted(BlockPos p,long now){return false;}
    }
    private static final class Owner extends AbstractModule {
        final HubSourceLoop loop;
        Owner(HubSourceLoop loop){super("unit_hub","Unit Hub",ModuleCategory.FORAGING);this.loop=loop;}
        public void onEnable(){loop.start();}public void onDisable(){loop.stop();}
        public void onTick(){loop.tick(0);}
        public boolean ownsGameplayInput(){return loop.permitsGameplayInput();}
    }
    @Test void terminalFailureDropsRealManagerInputLeaseButPreservesVisibleEnabledDiagnostic(){
        var p=new Port();var loop=new HubSourceLoop(p);var owner=new Owner(loop);var manager=new ModuleManager();manager.register(owner);owner.setEnabled(true);
        assertSame(owner,manager.inputOwner().orElseThrow());manager.tick();
        assertTrue(owner.isEnabled());assertEquals(HubSourceLoop.Phase.BLOCKED,loop.phase());assertEquals("Hub warp contract unavailable",loop.reason());
        assertFalse(owner.ownsGameplayInput());assertTrue(manager.inputOwner().isEmpty());
        assertTrue(manager.enabledControllers().contains(owner),"Visible terminal controller remains explicitly stoppable");
    }
    @Test void blockedReticksCannotReleaseNewManualKeysClearDiagnosticOrReplayWarp(){
        var p=new Port();var loop=new HubSourceLoop(p);var owner=new Owner(loop);var manager=new ModuleManager();manager.register(owner);owner.setEnabled(true);manager.tick();
        int releases=p.releases,requests=p.requests;String diagnostic=loop.reason();p.manualHeld=true;
        for(int i=0;i<40;i++)manager.tick();
        assertTrue(p.manualHeld);assertEquals(releases,p.releases);assertEquals(requests,p.requests);assertEquals(0,p.walks);assertEquals(diagnostic,loop.reason());
        assertTrue(manager.inputOwner().isEmpty());
    }
    @Test void terminalAuthorityCancelsPendingFramePoseInsteadOfRestoringHistoricalAngle(){
        var p=new Port();var loop=new HubSourceLoop(p);var owner=new Owner(loop);var manager=new ModuleManager();manager.register(owner);owner.setEnabled(true);
        var nativePlayer=new Object();var nativeWorld=new Object();var nativeConnection=new Object();
        var scope=new OwnedRotationTrajectory.Scope(manager.inputOwner().orElseThrow(),nativePlayer,nativeWorld,nativeConnection);
        var rotation=new OwnedRotationTrajectory();var actual=new OwnedRotationTrajectory.Pose(85,0);long ticket=rotation.plan(scope,10,actual,new OwnedRotationTrajectory.Pose(90,0));
        manager.tick();assertTrue(manager.inputOwner().isEmpty());
        // Root's sole-module rotation predicate uses the same enabled/nonpassive/owns filter.
        assertTrue(manager.all().stream().noneMatch(m->m.isEnabled()&&!m.isPassiveObserver()&&m.ownsGameplayInput()));
        rotation.validate(null,10,actual);assertFalse(rotation.active());assertFalse(rotation.completed(scope,ticket));
        assertTrue(rotation.frame(null,10,.8f,actual).isEmpty());
    }
    @Test void rootInputLockBecomesReleasedAndOnlyExplicitRestartCreatesNewAuthority(){
        var p=new Port();var loop=new HubSourceLoop(p);var owner=new Owner(loop);var manager=new ModuleManager();manager.register(owner);owner.setEnabled(true);
        try {
            ScriptingInputLock.set(manager.inputOwner().isPresent(),manager.inputOwner().map(m->m.id()).orElse(null));assertTrue(ScriptingInputLock.isActive());
            manager.tick();ScriptingInputLock.set(manager.inputOwner().isPresent(),manager.inputOwner().map(m->m.id()).orElse(null));assertFalse(ScriptingInputLock.isActive());
            Object loopIdentity=owner.loop;owner.setEnabled(false);assertFalse(loop.permitsGameplayInput());owner.setEnabled(true);
            assertSame(loopIdentity,owner.loop);assertEquals(HubSourceLoop.Phase.ENTRY,loop.phase());assertSame(owner,manager.inputOwner().orElseThrow());
        }finally{ScriptingInputLock.releaseAll();}
    }
}
