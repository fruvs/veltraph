package dev.veltraph.client.galatea;
import dev.veltraph.client.core.Module;
import dev.veltraph.client.core.ModuleCategory;
import dev.veltraph.client.core.MacroState;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual caller binding seam without a native client or invented wear admission. */
class FigScaffoldAxeDecisionRandomTest {
    static final class Owner implements Module {
        boolean enabled;
        public String id(){return "fig-rng-test";}public String displayName(){return id();}public ModuleCategory category(){return ModuleCategory.FORAGING;}
        public boolean isEnabled(){return enabled;}public void setEnabled(boolean value){enabled=value;}public void onEnable(){}public void onDisable(){}public void onTick(){}public MacroState state(){return enabled?MacroState.RUNNING:MacroState.STOPPED;}
    }
    static final class Host implements McFigScaffoldCaller.Host {
        final Owner owner=new Owner();
        public Module owner(){return owner;}public boolean permission(){return false;}public boolean networkGapObserved(){return false;}public String settings(){return "unchanged source config";}public int axeSlot(){return 1;}
        public FigHarvestPickupLedger harvestLedger(){return null;}public FigHarvestPickupLedger.Scope harvestScope(){return null;}
        public void selectSlot(int slot){}public void aim(BlockPos seed){}public void attack(boolean held){}public void enterThrowing(){}public void enterBreak(Vec3 feet){}public void failed(String reason){}
    }
    @Test void disabledBindingUsesTheStandardSeededNonconstantSequence(){
        var host=new Host();var caller=new McFigScaffoldCaller(null,null,null,host);var expected=new Random(4640);assertFalse(caller.axeDecisionRandomBound());
        caller.bindAxeDecisionRandom(new Random(4640));assertTrue(caller.axeDecisionRandomBound());
        double first=caller.nextAxeDecisionSample();assertEquals(expected.nextDouble(),first);assertTrue(first>0&&first<.05);
        double second=caller.nextAxeDecisionSample();assertEquals(expected.nextDouble(),second);assertNotEquals(first,second);
        for(int i=0;i<32;i++)assertEquals(expected.nextDouble(),caller.nextAxeDecisionSample());
    }
    @Test void enabledBindingAndRestoreRejectWithoutReplacingOrAdvancingTheSeededStream(){
        var host=new Host();var caller=new McFigScaffoldCaller(null,null,null,host);var expected=new Random(4640);caller.bindAxeDecisionRandom(new Random(4640));assertEquals(expected.nextDouble(),caller.nextAxeDecisionSample());
        host.owner.setEnabled(true);assertThrows(IllegalStateException.class,()->caller.bindAxeDecisionRandom(new Random(0)));assertThrows(IllegalStateException.class,caller::resetAxeDecisionRandom);assertTrue(caller.axeDecisionRandomBound());
        assertEquals(expected.nextDouble(),caller.nextAxeDecisionSample());host.owner.setEnabled(false);caller.resetAxeDecisionRandom();assertFalse(caller.axeDecisionRandomBound());
    }
    @Test void disabledRestoreReturnsToTheProductionUniformRange(){
        var host=new Host();var caller=new McFigScaffoldCaller(null,null,null,host);caller.bindAxeDecisionRandom(new Random(4640));caller.resetAxeDecisionRandom();assertFalse(caller.axeDecisionRandomBound());
        for(int i=0;i<64;i++){double value=caller.nextAxeDecisionSample();assertTrue(value>=0&&value<1);}
    }
    @Test void readonlyBindingAndAdmissionChecksDoNotDrawOrInventACommit(){
        var host=new Host();var caller=new McFigScaffoldCaller(null,null,null,host);var expected=new Random(4640);caller.bindAxeDecisionRandom(new Random(4640));
        for(int i=0;i<8;i++){assertTrue(caller.axeDecisionRandomBound());assertTrue(caller.wearAdmission().isEmpty());}
        assertEquals(expected.nextDouble(),caller.nextAxeDecisionSample());assertTrue(caller.wearAdmission().isEmpty());
    }
    @Test void rejectedNullBindingKeepsTheExistingStreamAndDisabledRestoreWorks(){
        var host=new Host();var caller=new McFigScaffoldCaller(null,null,null,host);var expected=new Random(4640);caller.bindAxeDecisionRandom(new Random(4640));assertThrows(NullPointerException.class,()->caller.bindAxeDecisionRandom(null));assertTrue(caller.axeDecisionRandomBound());
        assertEquals(expected.nextDouble(),caller.nextAxeDecisionSample());caller.resetAxeDecisionRandom();assertFalse(caller.axeDecisionRandomBound());assertTrue(caller.wearAdmission().isEmpty());
    }
}
