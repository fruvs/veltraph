package dev.veltraph.client.fishing.module;

import dev.veltraph.client.config.*;
import dev.veltraph.client.fishing.*;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class FishingSupportCastAcquisitionTest {
    private static final class Port implements FishingServices {
        BobberFacts hook=BobberFacts.none();boolean wantsSupport,active,lastAllow,rodHeld=true;int casts,reels,prepares,activeTicks;
        final FishingTotemController support=new FishingTotemController(0,2000);
        public boolean available(){return true;}public boolean hasFishingRod(){return rodHeld;}public BobberFacts bobber(){return hook;}
        public void useRod(){if(hook.present())reels++;else casts++;}
        public boolean supportActive(){return active;}public String supportFailure(){return support.failure();}
        public boolean tickSupport(boolean allow){lastAllow=allow;
            if(!active&&(!allow||!wantsSupport))return false;
            activeTicks++;var action=support.tick(new FishingTotemController.Input(!active,hook.present(),prepares>0,false,false,false,System.currentTimeMillis()));
            if(action==FishingTotemController.Action.REEL)useRod();if(action==FishingTotemController.Action.PREPARE)prepares++;
            active=support.active();return true;
        }
        public void stopSupport(){active=false;support.reset();}
    }
    @Test void pendingCoreCastCannotTransferToTemporaryToolBeforeActualHookReceipt(){
        var port=new Port();var module=new FishingModule(port,new MapConfigService(),new Random(0));module.setEnabled(true);module.onTick();
        assertEquals(1,port.casts);assertTrue(module.awaitingCoreCastAcknowledgment());port.wantsSupport=true;
        module.onTick();module.onTick();assertFalse(port.lastAllow);assertFalse(port.active);assertEquals(0,port.prepares);assertEquals(0,port.reels);assertEquals(1,port.casts);
        port.hook=new BobberFacts(true,0,false,false);module.onTick();assertTrue(port.lastAllow);assertTrue(port.active);
        assertEquals(FishingTotemController.State.WAIT_REEL,port.support.state());assertEquals(1,port.reels);assertEquals(0,port.prepares);
        module.onTick();module.onTick();assertEquals(1,port.reels);assertEquals(0,port.prepares);
        port.hook=BobberFacts.none();module.onTick();assertEquals(FishingTotemController.State.PREPARE,port.support.state());module.onTick();assertEquals(1,port.prepares);assertEquals(1,port.casts);assertEquals(1,port.reels);
    }
    @Test void supportAlreadyActiveContinuesAfterOwnedInitialReelRemovalWithoutNewAcquisition(){
        var port=new Port();var module=new FishingModule(port,new MapConfigService(),new Random(0));module.setEnabled(true);module.onTick();
        port.wantsSupport=true;port.hook=new BobberFacts(true,0,false,false);module.onTick();assertTrue(port.active);int ticks=port.activeTicks;
        port.hook=BobberFacts.none();module.onTick();assertEquals(ticks+1,port.activeTicks);module.onTick();assertEquals(1,port.prepares);assertTrue(module.isEnabled());
    }
    @Test void missingCastReceiptKeepsOriginalCoreTimeoutAndDoesNotPermanentlyBlockCore(){
        var config=new MapConfigService();config.set(FishingConfig.FISHING_CAST_TIMEOUT_SECONDS,20.0);
        var port=new Port();var module=new FishingModule(port,config,new Random(0));module.setEnabled(true);module.onTick();port.wantsSupport=true;
        expireIssuedCast(module);module.onTick();module.onTick();assertEquals(2,port.casts);assertFalse(port.active);assertEquals(0,port.prepares);assertTrue(module.isEnabled());
    }
    private static void expireIssuedCast(FishingModule module){
        try{var runtimeField=FishingModule.class.getDeclaredField("runtime");runtimeField.setAccessible(true);var runtime=(FishingRuntime)runtimeField.get(module);
            var castField=FishingRuntime.class.getDeclaredField("castTimeMs");castField.setAccessible(true);castField.setLong(runtime,System.currentTimeMillis()-20001);
        }catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
    @Test void canceledPendingCastCannotAdoptLateNativeHookOrRestartSupport(){
        var port=new Port();var module=new FishingModule(port,new MapConfigService(),new Random(0));module.setEnabled(true);module.onTick();port.wantsSupport=true;
        module.onTick();assertFalse(port.active);module.setEnabled(false);port.hook=new BobberFacts(true,0,false,false);module.onTick();
        assertFalse(module.isEnabled());assertFalse(port.active);assertEquals(1,port.casts);assertEquals(0,port.reels);assertEquals(0,port.prepares);
    }
    @Test void manualNonRodWhileAwaitingNativeCastStopsWithoutToolTakeoverOrLateAdoption(){
        var port=new Port();var module=new FishingModule(port,new MapConfigService(),new Random(0));module.setEnabled(true);module.onTick();port.wantsSupport=true;port.rodHeld=false;
        module.onTick();assertFalse(module.isEnabled());port.hook=new BobberFacts(true,0,false,false);module.onTick();
        assertFalse(port.active);assertEquals(0,port.prepares);assertEquals(1,port.casts);assertEquals(0,port.reels);
    }
    @Test void readySupportBeforeAnyCoreCastRetainsStartupPriority(){
        var port=new Port();port.wantsSupport=true;var module=new FishingModule(port,new MapConfigService(),new Random(0));module.setEnabled(true);module.onTick();
        assertTrue(port.lastAllow);assertTrue(port.active);assertEquals(0,port.casts);module.onTick();assertEquals(1,port.prepares);assertEquals(0,port.casts);
    }
}
