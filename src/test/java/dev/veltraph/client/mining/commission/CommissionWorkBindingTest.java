package dev.veltraph.client.mining.commission;

import dev.veltraph.client.mining.vein.MiningMaterial;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.mining.commission.CommissionObservation.*;

class CommissionWorkBindingTest {
    private CommissionObservation observed(Task... tasks){return new CommissionObservation("native-1",1,100,List.of(tasks),true);}
    private Task global(String name,Work kind,double percent){return new Task(name,kind,"","",percent,true,false,-1);}
    @Test void globalOreBatchesWithTitaniumPriorityAndNoOtherResources(){
        var binding=CommissionWorkBinding.choose(observed(global("Mithril Miner",Work.MITHRIL,0),global("Titanium Miner",Work.TITANIUM,20))).orElseThrow();
        assertEquals(2,binding.tasks().size());assertEquals(20,binding.priorities().get(MiningMaterial.TITANIUM));assertFalse(binding.priorities().containsKey(MiningMaterial.HARDSTONE));
    }
    @Test void titaniumCanRevealByMiningMithrilButDoesNotFabricatePercent(){
        var observation=observed(global("Titanium Miner",Work.TITANIUM,0));var binding=CommissionWorkBinding.choose(observation).orElseThrow();
        assertTrue(binding.priorities().get(MiningMaterial.GRAY_MITHRIL)>0);assertEquals(0,observation.tasks().getFirst().percent());
    }
    @Test void nativePercentUpdateDoesNotReplaceSemanticTask(){
        var binding=CommissionWorkBinding.choose(observed(global("Mithril Miner",Work.MITHRIL,0))).orElseThrow();
        assertTrue(binding.current(observed(global("Mithril Miner",Work.MITHRIL,20))));
    }
    @Test void taskDoneStopsWorkWithoutClaimReceipt(){
        var binding=CommissionWorkBinding.choose(observed(global("Mithril Miner",Work.MITHRIL,0))).orElseThrow();
        assertFalse(binding.current(observed(global("Mithril Miner",Work.MITHRIL,100))));assertTrue(CommissionWorkBinding.unavailable(observed(global("Mithril Miner",Work.MITHRIL,100))).contains("UNKNOWN"));
    }
    @Test void regionalTasksDoNotGainTargetMembershipFromPlayerLocation(){
        var task=new Task("Rampart's Quarry Titanium",Work.TITANIUM,"Rampart's Quarry","",0,true,false,11);
        assertTrue(CommissionWorkBinding.choose(observed(task)).isEmpty());assertTrue(CommissionWorkBinding.unavailable(observed(task)).contains("membership UNKNOWN"));
    }
    @Test void mobAndTunnelIdentitiesRemainUnbound(){
        assertTrue(CommissionWorkBinding.choose(observed(new Task("Goblin Slayer",Work.MOB,"","Goblin Slayer",0,true,false,-1))).isEmpty());
        assertTrue(CommissionWorkBinding.choose(observed(new Task("Glacite Collector",Work.GLACITE,"Glacite Tunnels","Glacite",0,true,false,-1))).isEmpty());
    }
    @Test void duplicateOrIncompleteObservationsRefuse(){
        var task=global("Mithril Miner",Work.MITHRIL,0);assertTrue(CommissionWorkBinding.choose(observed(task,task)).isEmpty());
        assertTrue(CommissionWorkBinding.choose(new CommissionObservation("native",1,100,List.of(task),false)).isEmpty());
    }
    @Test void replacementAndMissingProgressRevokeExistingWork(){
        var binding=CommissionWorkBinding.choose(observed(global("Mithril Miner",Work.MITHRIL,0))).orElseThrow();
        assertFalse(binding.current(observed(global("Titanium Miner",Work.TITANIUM,0))));
        assertFalse(binding.current(observed(new Task("Mithril Miner",Work.MITHRIL,"","",0,false,false,-1))));
    }
    @Test void unrelatedDoneTaskPreemptsBeforeClaim(){
        var binding=CommissionWorkBinding.choose(observed(global("Mithril Miner",Work.MITHRIL,0))).orElseThrow();
        assertFalse(binding.current(observed(global("Mithril Miner",Work.MITHRIL,20),new Task("Lucky Raffle",Work.UNSUPPORTED,"","",100,true,true,15))));
    }
}
