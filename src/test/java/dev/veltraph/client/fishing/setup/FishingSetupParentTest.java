package dev.veltraph.client.fishing.setup;

import dev.veltraph.client.config.*;
import dev.veltraph.client.fishing.*;
import dev.veltraph.client.fishing.module.FishingModule;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class FishingSetupParentTest {
    static final class Port implements FishingServices {
        boolean held, pending=true, ready;String failure="",configuration="";int uses, helpers,stops;
        public boolean available(){return true;} public boolean hasFishingRod(){return held;}
        public BobberFacts bobber(){return BobberFacts.none();}public void useRod(){uses++;}
        public String rodSetupConfigurationError(ConfigService config){return configuration;}
        public boolean tickRodSetup(){return pending;}public boolean rodSetupReady(){return ready;}
        public String rodSetupFailure(){return failure;}public void stopRodSetup(){stops++;}
        public boolean tickCombat(){helpers++;return false;}
    }
    @Test void autoStartCanBeginWithForeignToolButPendingSetupPreemptsHelpersAndCast() {
        var port=new Port();var config=new MapConfigService();config.set(FishingSetupConfig.FISHING_AUTO_ROD,true);
        var module=new FishingModule(port,config,new Random(0));assertTrue(module.preStartCheck().isEmpty());
        module.setEnabled(true);module.onTick();assertEquals(0,port.uses);assertEquals(0,port.helpers);
        assertFalse(module.safeScheduledPauseBoundary());
        port.pending=false;port.ready=true;port.held=true;module.onTick();assertEquals(1,port.uses);
        module.setEnabled(false);assertEquals(1,port.stops);
    }
    @Test void setupFailureStopsOwnerWithoutAnyToolOrRodUse() {
        var port=new Port();var config=new MapConfigService();config.set(FishingSetupConfig.FISHING_AUTO_ROD,true);
        var module=new FishingModule(port,config,new Random(0));module.setEnabled(true);
        port.failure="Native rod identity changed";module.onTick();assertFalse(module.isEnabled());
        assertEquals(0,port.uses);assertEquals(0,port.helpers);assertEquals(1,port.stops);
    }
    @Test void missingBindingsDenyPrestartEvenWhenAValidRodIsHeld() {
        var port=new Port();port.held=true;port.configuration="Bind every recovered rod label";
        var module=new FishingModule(port,new MapConfigService(),new Random(0));
        assertEquals(port.configuration,module.preStartCheck().orElseThrow());
    }
}
