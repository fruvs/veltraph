package dev.veltraph.client.farming.module;

import dev.veltraph.client.config.MapConfigService;
import dev.veltraph.client.farming.domain.FarmLayoutRegistry;
import dev.veltraph.client.service.*;
import dev.veltraph.client.service.stub.NoOpInventoryService;
import dev.veltraph.client.testutil.*;
import dev.veltraph.client.world.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;

/** Actual destruction coordinates remain independently constrained throughout the enabled lifetime. */
class FarmCropTargetGuardLifecycleTest {
    @Test void mushroomActualCoordinateAndMatchingOwnerLifecycle() { verify("mushroom"); }
    @Test void caneActualCoordinateBaseAndMatchingOwnerLifecycle() { verify("sugar_cane"); }
    @Test void cactusActualCoordinateBaseAndMatchingOwnerLifecycle() { verify("cactus"); }
    private void verify(String crop) {
        var world=new CropWorld();var breaking=new GuardBreaking();var input=new RecordingInputService();
        var services=new FarmServices(new FakeClock(),new FakePlayerService(),world,input,new FakeRotationService(),breaking,new NoOpInventoryService());
        var config=new MapConfigService();var registry=FarmLayoutRegistry.createDefault();
        FarmModule module=switch(crop){case "mushroom"->new MushroomFarmModule(services,config,registry);
            case "sugar_cane"->new SugarcaneFlowersFarmModule(services,config,registry);
            default->new CactusFarmModule(services,config,registry);};
        var upper=new BlockPos(0,65,0);var base=upper.down();var wall=new BlockPos(1,65,0);
        String id="minecraft:"+(crop.equals("mushroom")?"brown_mushroom":crop);
        world.ids.put(upper,id);world.ids.put(base,id);world.ids.put(base.down(),"minecraft:sand");world.ids.put(wall,"minecraft:stone");
        module.setEnabled(true);assertSame(module,breaking.owner);assertNotNull(breaking.guard);assertFalse(breaking.held);
        assertTrue(breaking.guard.test(upper));assertFalse(breaking.guard.test(wall));
        if(!crop.equals("mushroom"))assertFalse(breaking.guard.test(base),"planted base is protected");
        world.ids.put(upper,"minecraft:stone");assertFalse(breaking.guard.test(upper),"current coordinate is re-read after crop removal");
        world.ids.put(upper,id);world.loaded=false;assertFalse(breaking.guard.test(upper));world.loaded=true;
        module.suspendForRewarp();assertNotNull(breaking.guard);assertFalse(breaking.held);
        int before=breaking.installs;module.resumeAfterRewarp();assertEquals(before+1,breaking.installs,"verified rewarp refreshes world/player identity");
        module.resumeAfterRewarp();assertEquals(before+1,breaking.installs,"duplicate resume cannot replace an active guard");
        breaking.clearTargetGuard(new Object());assertNotNull(breaking.guard,"another owner cannot remove guard");
        module.setEnabled(false);assertNull(breaking.guard);assertNull(breaking.owner);assertFalse(breaking.held);assertTrue(input.releaseAllCount>0);
    }
    private static final class CropWorld extends FakeWorldService {
        final Map<BlockPos,String> ids=new HashMap<>();boolean loaded=true;
        @Override public boolean isLoaded(BlockPos p){return loaded;}
        @Override public String blockId(BlockPos p){return ids.getOrDefault(p,"minecraft:air");}
    }
    private static final class GuardBreaking implements BlockBreakingService {
        Object owner;Predicate<BlockPos> guard;boolean held;int installs;
        public void setBreaking(boolean value){held=value;}
        public boolean isBreaking(){return held;}
        public void installTargetGuard(Object newOwner,Predicate<BlockPos> predicate){owner=newOwner;guard=predicate;installs++;}
        public void clearTargetGuard(Object oldOwner){if(owner==oldOwner){owner=null;guard=null;}}
    }
}
