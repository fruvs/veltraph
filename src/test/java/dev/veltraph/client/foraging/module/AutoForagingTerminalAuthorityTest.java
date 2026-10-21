package dev.veltraph.client.foraging.module;
import dev.veltraph.client.foraging.hub.HubSourceLoop;
import dev.veltraph.client.foraging.TreeDiscovery;
import dev.veltraph.client.world.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Exercises the actual AutoForaging ownership predicate without a synthetic Minecraft context. */
class AutoForagingTerminalAuthorityTest {
    private static HubSourceLoop loop(){return new HubSourceLoop(new HubSourceLoop.Port(){
        public boolean inHub(){return false;}public boolean sourceStopRequested(){return false;}
        public BlockPos playerBlock(){throw new AssertionError("Unbound metadata must not read a player");}public Vec3 playerPosition(){throw new AssertionError("Unbound metadata must not read pose");}
        public boolean pathExists(BlockPos p){return false;}public boolean startAnchorWalk(BlockPos p){return false;}
        public HubSourceLoop.Motion anchorMotion(){return HubSourceLoop.Motion.FAILED;}public boolean requestHubWarp(){return false;}public HubSourceLoop.Motion hubWarpMotion(){return HubSourceLoop.Motion.FAILED;}
        public void release(){}public TreeDiscovery.Tree treeAt(BlockPos p){return null;}public BlockPos standingPosition(BlockPos p){return null;}public boolean blacklisted(BlockPos p,long now){return false;}
    });}
    @Test void actualAutoPredicateDropsTerminalHubButKeepsEntryAuthority(){
        var source=loop();source.start();assertTrue(AutoForagingModule.hasGameplayInputAuthority(false,false,source));source.tick(0);
        assertEquals(HubSourceLoop.Phase.BLOCKED,source.phase());assertFalse(AutoForagingModule.hasGameplayInputAuthority(false,false,source));
    }
    @Test void parkAndScopedPrivateHarvestKeepExistingAuthorityWhileRestStillDenies(){
        var source=loop();source.start();source.tick(0);
        assertTrue(AutoForagingModule.hasGameplayInputAuthority(false,false,null));assertTrue(AutoForagingModule.hasGameplayInputAuthority(false,true,source));
        assertFalse(AutoForagingModule.hasGameplayInputAuthority(true,false,null));assertFalse(AutoForagingModule.hasGameplayInputAuthority(true,true,source));
    }
}
