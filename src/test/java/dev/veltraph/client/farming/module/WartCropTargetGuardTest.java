package dev.veltraph.client.farming.module;

import dev.veltraph.client.config.MapConfigService;
import dev.veltraph.client.farming.domain.*;
import dev.veltraph.client.service.*;
import dev.veltraph.client.service.stub.NoOpInventoryService;
import dev.veltraph.client.testutil.*;
import dev.veltraph.client.world.BlockPos;
import java.util.*;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WartCropTargetGuardTest {
    private static final class GuardBreaking implements BlockBreakingService {
        boolean breaking;
        Object owner;
        Predicate<BlockPos> allowed;
        public void setBreaking(boolean value) { breaking=value; }
        public boolean isBreaking() { return breaking; }
        public void installTargetGuard(Object owner,Predicate<BlockPos> allowed) { this.owner=owner;this.allowed=allowed; }
        public void clearTargetGuard(Object owner) { if(this.owner==owner) { this.owner=null;allowed=null; } }
    }
    private static final class World implements WorldService {
        final Map<BlockPos,String> states=new HashMap<>();
        final Set<BlockPos> unloaded=new HashSet<>();
        public boolean isLoaded(BlockPos p) { return !unloaded.contains(p); }
        public boolean isPassable(BlockPos p) { return !Set.of("minecraft:stone","minecraft:farmland","minecraft:soul_sand").contains(blockId(p)); }
        public boolean isStandable(BlockPos p) { return !isPassable(p); }
        public String blockId(BlockPos p) { return states.getOrDefault(p,"minecraft:air"); }
    }
    private WartCropsFarmModule module(World world,GuardBreaking breaking) {
        return new WartCropsFarmModule(new FarmServices(new FakeClock(),new FakePlayerService(),world,
                new RecordingInputService(),new FakeRotationService(),breaking,new NoOpInventoryService()),
                new MapConfigService(),FarmLayoutRegistry.createDefault());
    }
    @Test void enabledLifetimeGuardValidatesExactSelectedCropAndLoadedSupportForEveryVariety() {
        for(var variety:WartVariety.values()) {
            var world=new World();var breaking=new GuardBreaking();var m=module(world,breaking);
            String id=switch(variety) { case WHEAT->"minecraft:wheat";case CARROT->"minecraft:carrots";
                case POTATO->"minecraft:potatoes";case NETHER_WART->"minecraft:nether_wart"; };
            var actual=new BlockPos(0,64,1);var support=actual.down();
            world.states.put(actual,id);world.states.put(support,variety==WartVariety.NETHER_WART?"minecraft:soul_sand":"minecraft:farmland");
            m.setVariety(variety);m.setEnabled(true);
            assertSame(m,breaking.owner);assertNotNull(breaking.allowed);assertTrue(breaking.allowed.test(actual));
            assertFalse(breaking.breaking,"guard must exist before attack is driven");
            assertFalse(breaking.allowed.test(support),"actual support hit cannot be harvested");
            world.unloaded.add(support);assertFalse(breaking.allowed.test(actual));world.unloaded.clear();
            world.states.put(support,"minecraft:stone");assertFalse(breaking.allowed.test(actual));
            world.states.put(support,variety==WartVariety.NETHER_WART?"minecraft:soul_sand":"minecraft:farmland");
            world.states.put(actual,id.equals("minecraft:wheat")?"minecraft:carrots":"minecraft:wheat");
            assertFalse(breaking.allowed.test(actual),"a different actual crop is never substituted");
            m.setEnabled(false);assertNull(breaking.owner);assertNull(breaking.allowed);assertFalse(breaking.breaking);
        }
    }
    @Test void failedEnableClearsGuardAndBreakInput() {
        var world=new World();var breaking=new GuardBreaking();var m=module(world,breaking);
        m.setVariety(null);
        assertThrows(NullPointerException.class,()->m.setEnabled(true));
        assertNull(breaking.owner);assertNull(breaking.allowed);assertFalse(breaking.breaking);
    }
    @Test void defaultCardDetectsLoadedWheatWhileExplicitSelectionRemainsAuthoritative(){
        var world=new World();var crop=new BlockPos(0,64,1);
        world.states.put(crop,"minecraft:wheat");world.states.put(crop.down(),"minecraft:farmland");
        var breaking=new GuardBreaking();var m=module(world,breaking);m.setEnabled(true);
        assertEquals(WartVariety.WHEAT,m.variety());assertTrue(breaking.allowed.test(crop));m.setEnabled(false);
        m=module(world,breaking);m.setVariety(WartVariety.NETHER_WART);m.setEnabled(true);
        assertEquals(WartVariety.NETHER_WART,m.variety());assertFalse(breaking.allowed.test(crop));m.setEnabled(false);
    }
    @Test void persistentDesignUsesRegisteredProfileAndLiveDesignChangeStopsTheOwner(){
        var world=new World();var breaking=new GuardBreaking();var m=module(world,breaking);
        m.setDesign(WartCropsDesign.ACE_TWO_KEY_LOOP);
        assertEquals(FarmLayoutRegistry.WART_CROPS_ACE_TWO_KEY,m.activeProfile().id());
        m.setEnabled(true);assertEquals(WartVariety.WHEAT,m.variety());
        m.setDesign(WartCropsDesign.CLASSICAL);
        assertFalse(m.isEnabled());assertNull(breaking.owner);
        assertEquals(FarmLayoutRegistry.WART_CROPS_SSHAPE,m.activeProfile().id());
    }
}
