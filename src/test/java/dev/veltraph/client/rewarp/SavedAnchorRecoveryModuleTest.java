package dev.veltraph.client.rewarp;
import dev.veltraph.client.core.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SavedAnchorRecoveryModuleTest {
    static class Driver implements SavedAnchorRecoveryModule.Driver {
        boolean active;int cancels;SavedAnchorRecovery.State state=SavedAnchorRecovery.State.IDLE;
        public boolean start(RewarpPoint p,GameArea a){active=true;state=SavedAnchorRecovery.State.WALKING;return true;}
        public void tick(GameArea a){}
        public boolean active(){return active;}
        public SavedAnchorRecovery.State state(){return state;}
        public String status(){return state.name();}
        public void cancel(){if(active){cancels++;state=SavedAnchorRecovery.State.CANCELLED;active=false;}}
    }
    private RewarpPoint point(GameArea area,int x){return new RewarpPoint("minecraft:overworld",area,new BlockPos(x,70,0),0,0,0);}
    @Test void takeoverStopsMacroAndManualStopCancelsWithoutFallback() {
        var manager=new ModuleManager();var other=new AbstractModule("other","Other",ModuleCategory.FARMING){};manager.register(other);other.setEnabled(true);
        var driver=new Driver();var fallbacks=new AtomicInteger();
        var module=new SavedAnchorRecoveryModule(manager,()->GameArea.GARDEN,driver,fallbacks::incrementAndGet);manager.register(module);
        assertTrue(module.begin(point(GameArea.GARDEN,3)));assertFalse(other.isEnabled());assertTrue(module.isEnabled());
        manager.stopAll();assertFalse(module.isEnabled());assertEquals(1,driver.cancels);assertEquals(0,fallbacks.get());
    }
    @Test void failedTravelFallsBackOnceButCancelledTravelDoesNot() {
        var manager=new ModuleManager();var driver=new Driver();var fallbacks=new AtomicInteger();
        var module=new SavedAnchorRecoveryModule(manager,()->GameArea.GARDEN,driver,fallbacks::incrementAndGet);manager.register(module);
        module.begin(point(GameArea.GARDEN,3));driver.active=false;driver.state=SavedAnchorRecovery.State.FAILED;manager.tick();manager.tick();
        assertEquals(1,fallbacks.get());module.begin(point(GameArea.GARDEN,3));driver.active=false;driver.state=SavedAnchorRecovery.State.CANCELLED;manager.tick();assertEquals(1,fallbacks.get());
    }
    @Test void localSelectionDoesNotChooseCloserAnchorInAnotherArea() {
        var service=new RewarpService();service.store().add(point(GameArea.HUB,0));service.store().add(point(GameArea.GARDEN,5));
        assertEquals(5,service.selectLocalTarget(GameArea.GARDEN,"minecraft:overworld",new Vec3(.5,70,.5)).orElseThrow().pos().x());
        assertTrue(service.selectLocalTarget(GameArea.UNKNOWN,"minecraft:overworld",new Vec3(.5,70,.5)).isEmpty());
    }
}
