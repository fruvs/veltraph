package dev.veltraph.client.service.mc;

import dev.veltraph.client.config.MapConfigService;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.farming.domain.*;
import dev.veltraph.client.farming.module.FarmModule;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.stub.NoOpRotationService;
import dev.veltraph.client.testutil.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Actual farm dispatcher and Core invocation ordering; these tests confer no native acceptance. */
class FarmCleanupStopRoutingTest {
    static class Farm extends FarmModule {
        final ModuleManager root;
        ModuleManager.LifecycleSnapshot original;
        Runnable family = () -> {};
        int familyStops;
        Farm(ModuleManager root, String id) {
            super(id,id,new FarmServices(new FakeClock(),new FakePlayerService(),new FakeWorldService(),
                new RecordingInputService(),new NoOpRotationService(),new RecordingBlockBreaking(),
                new FakeInventoryService()),new MapConfigService(),FarmLayoutRegistry.createDefault());
            this.root=root;
        }
        public FarmLayoutProfile activeProfile(){return registry.byId(FarmLayoutRegistry.WART_CROPS_SSHAPE).orElseThrow();}
        public String debugState(){return "cleanup routing fixture";}
        protected List<String> toolKeywords(){return List.of();}
        protected void onTickFarming(){}
        protected void onDisableFarming(){familyStops++;family.run();super.onDisableFarming();}
    }
    @Test void actualDisableWrapsFamilyBeforeSuperclassAndUsesOriginalCoreInvocation(){
        var root=new ModuleManager();var farm=new Farm(root,"own");root.register(farm);farm.setEnabled(true);
        farm.original=root.lifecycleSnapshot();
        farm.family=()->{assertTrue(McFarmOwnedCleanup.stopRouteCurrent(farm));assertFalse(farm.isEnabled());
            assertTrue(root.pendingOwnDisableFromMatches(farm.original,farm));};
        farm.setEnabled(false);assertEquals(1,farm.familyStops);assertFalse(McFarmOwnedCleanup.stopping());
    }
    @Test void rawPublicDisableCanRetireLogicalFamilyButCannotSupplyCoreDisable(){
        var root=new ModuleManager();var farm=new Farm(root,"raw");root.register(farm);farm.setEnabled(true);
        farm.original=root.lifecycleSnapshot();farm.family=()->{assertTrue(McFarmOwnedCleanup.stopRouteCurrent(farm));
            assertTrue(farm.isEnabled());assertFalse(root.pendingOwnDisableFromMatches(farm.original,farm));};
        farm.onDisable();assertTrue(farm.isEnabled());assertEquals(1,farm.familyStops);assertFalse(McFarmOwnedCleanup.stopping());
    }
    @Test void recursiveRawStopRevokesBothRoutesWithoutLeavingThreadLocalScope(){
        var root=new ModuleManager();var a=new Farm(root,"a");var b=new Farm(root,"b");root.register(a);root.register(b);
        a.setEnabled(true);a.original=root.lifecycleSnapshot();
        b.family=()->{assertFalse(McFarmOwnedCleanup.stopRouteCurrent(b));assertFalse(root.pendingOwnDisableFromMatches(a.original,b));};
        a.family=()->{assertTrue(McFarmOwnedCleanup.stopRouteCurrent(a));b.onDisable();assertFalse(McFarmOwnedCleanup.stopRouteCurrent(a));};
        a.setEnabled(false);assertEquals(1,a.familyStops);assertEquals(1,b.familyStops);assertFalse(McFarmOwnedCleanup.stopping());
    }
    @Test void throwingFamilyRemovesRoutingScopeAndDoesNotFabricateNormalReturnReceipt(){
        var root=new ModuleManager();var farm=new Farm(root,"throws");root.register(farm);farm.setEnabled(true);
        farm.family=()->{assertTrue(McFarmOwnedCleanup.stopRouteCurrent(farm));throw new IllegalStateException("original family failure");};
        assertThrows(IllegalStateException.class,()->farm.setEnabled(false));assertFalse(McFarmOwnedCleanup.stopping());
        assertTrue(root.currentOwnDisableReceipt(farm).isEmpty());assertFalse(farm.isEnabled());
    }
}
