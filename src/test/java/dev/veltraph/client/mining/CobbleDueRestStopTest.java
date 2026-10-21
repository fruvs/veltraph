package dev.veltraph.client.mining;
import dev.veltraph.client.config.*;
import dev.veltraph.client.mining.module.MiningModule;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CobbleDueRestStopTest {
    static final class Run {
        final MiningScheduledPausePortTest.Services services=new MiningScheduledPausePortTest.Services();
        final MapConfigService config=new MapConfigService();final MiningModule module;
        final MiningScheduledPausePort port;
        Run(){config.set(MiningConfig.COBBLE_AUTO_WALK,true);module=new MiningModule("cobblestone","Cobblestone",MiningMode.COBBLESTONE,services,config);port=new MiningScheduledPausePort(module);module.setEnabled(true);module.onTick();assertTrue(services.input.forward);services.id="minecraft:air";}
    }
    @Test void explicitDueStopPreservesFullStationaryReadinessBeforeActualPause(){
        var r=new Run();Object cause=new Object(),ticket=new Object();r.services.ready=false;
        assertTrue(r.port.prepareForDueRest(cause));assertFalse(r.services.input.forward);assertEquals(1,r.module.controller().removed());assertFalse(r.module.controller().allowsTarget(MiningControllerTest.TARGET));
        for(int i=0;i<5;i++){r.module.onTick();assertFalse(r.services.input.forward);assertFalse(r.port.pause(ticket));}
        assertEquals(0,r.services.captures);r.services.ready=true;assertTrue(r.port.pause(ticket));assertEquals(1,r.services.captures);assertTrue(r.port.owns(ticket));assertEquals(1,r.module.enableRuns());
    }
    @Test void withdrawnOrForeignCauseDoesNotCancelAnotherPreparation(){
        var r=new Run();Object actual=new Object();assertTrue(r.port.prepareForDueRest(actual));r.port.cancelDueRestPreparation(new Object());r.services.id="minecraft:cobblestone";r.module.onTick();assertFalse(r.services.input.forward);
        r.port.cancelDueRestPreparation(actual);r.module.onTick();assertTrue(r.services.input.forward);assertEquals(MiningController.State.BREAKING,r.module.controller().state());assertEquals(1,r.module.enableRuns());
    }
    @Test void oldPreparationCancellationDoesNotReleaseAReplacementActivation(){
        var r=new Run();Object old=new Object();assertTrue(r.port.prepareForDueRest(old));r.module.setEnabled(false);r.services.id="minecraft:cobblestone";r.module.setEnabled(true);r.module.onTick();r.services.id="minecraft:air";Object next=new Object();assertTrue(r.port.prepareForDueRest(next));r.port.cancelDueRestPreparation(old);r.module.onTick();assertFalse(r.services.input.forward);assertEquals(2,r.module.enableRuns());
    }
    @Test void changedBodyWorldScreenOrSettingsCannotPauseAnOldPreparation(){
        for(String change:java.util.List.of("WORLD","SCREEN","SETTINGS")){
            var r=new Run();Object cause=new Object();assertTrue(r.port.prepareForDueRest(cause));switch(change){case "WORLD"->r.services.identity="new native world";case "SCREEN"->r.services.available=false;case "SETTINGS"->r.config.set(MiningConfig.COBBLE_AUTO_SPRINT,true);}
            assertFalse(r.port.prepareForDueRest(cause));r.module.onTick();assertFalse(r.module.isEnabled());assertFalse(r.services.input.forward);assertEquals(0,r.services.captures);
        }
    }
    @Test void unsupportedModeActiveBreakAndForeignLoanDeclinePreparation(){
        var r=new Run();r.services.id="minecraft:cobblestone";assertFalse(r.port.prepareForDueRest(new Object()));
        r.services.id="minecraft:air";r.module.controller().observeCobbleInventoryBoundary();Object loan=new Object();assertTrue(r.module.controller().beginOwnedSubflow(loan));assertFalse(r.port.prepareForDueRest(new Object()));
        var other=new MiningModule("mine_down","Mine Down",MiningMode.MINE_DOWN,new MiningScheduledPausePortTest.Services(),new MapConfigService());other.setEnabled(true);assertFalse(new MiningScheduledPausePort(other).supportsDueRestPreparation());assertFalse(new MiningScheduledPausePort(other).prepareForDueRest(new Object()));
    }
}
