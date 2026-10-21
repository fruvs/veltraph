package dev.veltraph.client.foraging.replant;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ForagingReplantCycleTest {
    private static final ForagingReplantCycle.Settings SETTINGS=new ForagingReplantCycle.Settings(1000,100,100,2,1,true);
    private static class World implements ForagingReplantCycle.Port {
        final List<ForagingReplantCycle.Cell> cells=new ArrayList<>(Collections.nCopies(4,ForagingReplantCycle.Cell.EMPTY));
        int saplings=8,bone=8,plants,bones,clears,harvests;boolean soil=true,loaded=true,cursor=true,wrong=false,grow=true,consume=true,harvestDone=false,home=false;
        final List<String> actions=new ArrayList<>();
        ForagingReplantCycle.Observation read(long now){return new ForagingReplantCycle.Observation(true,loaded,soil,cursor,wrong,false,cells,saplings,bone,cells.stream().allMatch(c->c==ForagingReplantCycle.Cell.LOG),harvestDone,false,home,now);}
        public boolean equip(ForagingReplantCycle.Resource r){actions.add("equip:"+r);return true;}
        public boolean plant(int i){plants++;cells.set(i,ForagingReplantCycle.Cell.SAPLING);if(consume)saplings--;return true;}
        public boolean bonemeal(int i){bones++;if(consume)bone--;if(grow)Collections.fill(cells,ForagingReplantCycle.Cell.LOG);return true;}
        public boolean axe(){actions.add("axe");return true;}
        public boolean beginHarvest(){harvests++;harvestDone=false;actions.add("harvest");return true;}
        public void harvestTick(){Collections.fill(cells,ForagingReplantCycle.Cell.EMPTY);harvestDone=true;}
        public void clearSapling(int i){clears++;cells.set(i,ForagingReplantCycle.Cell.EMPTY);}
        public void release(){actions.add("release");}
        public boolean returnHome(){actions.add("return");home=true;return true;}
    }
    private void drive(ForagingReplantCycle cycle,World world,int ticks){for(int i=0;i<ticks&&!cycle.terminal();i++)cycle.tick(world.read(i*100L),SETTINGS,world);}
    @Test void acknowledgedSourceOrderCompletesOneCycle(){var c=new ForagingReplantCycle();var w=new World();drive(c,w,40);assertEquals(ForagingReplantCycle.Stage.COMPLETE,c.stage());assertEquals(4,w.plants);assertEquals(1,w.bones);assertEquals(1,w.harvests);assertEquals(1,c.cycles());assertTrue(w.actions.indexOf("equip:SAPLING")<w.actions.indexOf("equip:BONEMEAL"));assertTrue(w.actions.indexOf("equip:BONEMEAL")<w.actions.indexOf("harvest"));assertTrue(w.actions.indexOf("harvest")<w.actions.indexOf("return"));}
    @Test void placementWithoutConsumptionNeverAdvances(){var c=new ForagingReplantCycle();var w=new World();w.consume=false;drive(c,w,40);assertEquals(ForagingReplantCycle.Stage.FAILED,c.stage());assertEquals(1,w.plants);assertEquals(0,w.bones);assertEquals(0,w.harvests);assertEquals(1,w.clears);}
    @Test void bonemealConsumptionWithoutGrowthRunsBoundedOwnedReset(){var c=new ForagingReplantCycle();var w=new World();w.grow=false;drive(c,w,50);assertEquals(ForagingReplantCycle.Stage.FAILED,c.stage());assertEquals(2,w.bones);assertEquals(4,w.clears);assertEquals(0,w.harvests);assertEquals(0,c.cycles());assertTrue(c.reason().contains("without tree growth"));}
    @Test void growthWithoutBonemealConsumptionCannotHarvest(){var c=new ForagingReplantCycle();var w=new World();int i=0;while(c.stage()!=ForagingReplantCycle.Stage.USE_BONEMEAL&&i<30)c.tick(w.read(i++*100L),SETTINGS,w);w.consume=false;for(;i<80&&!c.terminal();i++)c.tick(w.read(i*100L),SETTINGS,w);assertEquals(ForagingReplantCycle.Stage.FAILED,c.stage());assertEquals(0,w.harvests);}
    @Test void invalidSoilSpeciesCacheOrCursorNeverSendsPlacement(){for(int mode=0;mode<4;mode++){var c=new ForagingReplantCycle();var w=new World();if(mode==0)w.soil=false;if(mode==1)w.wrong=true;if(mode==2)w.loaded=false;if(mode==3)w.cursor=false;drive(c,w,2);assertEquals(ForagingReplantCycle.Stage.FAILED,c.stage());assertEquals(0,w.plants);assertEquals(0,w.bones);}}
    @Test void unrelatedExtraResourceLossCannotAcknowledgeBonemeal(){var c=new ForagingReplantCycle();var w=new World(){@Override public boolean bonemeal(int i){boolean sent=super.bonemeal(i);bone--;return sent;}};drive(c,w,60);assertEquals(ForagingReplantCycle.Stage.FAILED,c.stage());assertEquals(0,w.harvests);assertEquals(0,c.cycles());}
    @Test void idleRestRebasesDeadlineWithoutChangingStageOrProgress(){var c=new ForagingReplantCycle();var w=new World(){@Override public boolean equip(ForagingReplantCycle.Resource r){return false;}};c.tick(w.read(100),SETTINGS,w);assertTrue(c.atScheduledRestBoundary());c.rebaseAfterScheduledRest(600000);c.tick(w.read(600200),SETTINGS,w);assertEquals(ForagingReplantCycle.Stage.SWITCH_TO_SAPLINGS,c.stage());assertEquals(0,w.plants);assertEquals(0,c.cycles());}
    @Test void activePlantingCannotRebaseAsScheduledIdle(){var c=new ForagingReplantCycle();var w=new World();c.tick(w.read(0),SETTINGS,w);assertFalse(c.atScheduledRestBoundary());assertThrows(IllegalStateException.class,()->c.rebaseAfterScheduledRest(1000));}
    @Test void missingResourcesAndCancelledCycleAreTerminal(){var c=new ForagingReplantCycle();var w=new World();w.saplings=3;drive(c,w,4);assertEquals(ForagingReplantCycle.Stage.SWITCH_TO_SAPLINGS,c.stage());assertEquals(Optional.of(ForagingReplantCycle.Resource.SAPLING),c.supplyRequest());assertEquals(0,w.plants);var stopped=new ForagingReplantCycle();stopped.cancel(w);stopped.tick(w.read(0),SETTINGS,w);assertEquals(ForagingReplantCycle.Stage.CANCELLED,stopped.stage());assertEquals(0,w.plants);}
    @Test void returnContinuationReleasesMovementBeforeSecondPlanting(){var c=new ForagingReplantCycle();var w=new World(){boolean moving;@Override public boolean returnHome(){moving=true;return super.returnHome();}@Override public void release(){moving=false;super.release();}@Override public boolean equip(ForagingReplantCycle.Resource r){if(r==ForagingReplantCycle.Resource.SAPLING)assertFalse(moving,"Return input leaked into next cycle");return super.equip(r);}};var two=new ForagingReplantCycle.Settings(1000,100,100,2,2,true);for(int i=0;i<80&&!c.terminal();i++)c.tick(w.read(i*100L),two,w);assertEquals(ForagingReplantCycle.Stage.COMPLETE,c.stage());assertEquals(2,c.cycles());assertEquals(8,w.plants);assertEquals(2,w.harvests);}
    @Test void shortagePreservesOriginalStageAndResumesOnlyWithEnoughResource(){
        var c=new ForagingReplantCycle();var w=new World();w.saplings=3;c.tick(w.read(0),SETTINGS,w);
        assertTrue(c.supplyBoundary());c.tick(w.read(5000),SETTINGS,w);assertEquals(0,w.plants);
        assertFalse(c.resumeAfterSupply(w.read(5000),5000));w.saplings=7;assertTrue(c.resumeAfterSupply(w.read(5000),5000));
        c.tick(w.read(5100),SETTINGS,w);assertEquals(ForagingReplantCycle.Stage.PLANTING_SAPLINGS,c.stage());assertFalse(c.supplyBoundary());
    }
    @Test void depletedBonemealSupplyKeepsOwnedPlantsAndAttemptHistory(){
        var c=new ForagingReplantCycle();var w=new World();w.bone=1;w.grow=false;int i=0;
        while(c.supplyRequest().isEmpty()&&i<40)c.tick(w.read(i++*100L),SETTINGS,w);
        assertEquals(Optional.of(ForagingReplantCycle.Resource.BONEMEAL),c.supplyRequest());assertEquals(1,c.bonemealUses());assertEquals(0,w.clears);
        w.bone=8;assertTrue(c.resumeAfterSupply(w.read(i*100L+10000),10000));
        for(;i<80&&!c.terminal();i++)c.tick(w.read(i*100L+10000),SETTINGS,w);
        assertEquals(2,w.bones);assertEquals(4,w.clears);assertEquals(ForagingReplantCycle.Stage.FAILED,c.stage());
    }
    @Test void supplyRejectionDoesNotResetOrBreakOwnedPlants(){
        var c=new ForagingReplantCycle();var w=new World();w.bone=0;int i=0;
        while(c.supplyRequest().isEmpty()&&i<40)c.tick(w.read(i++*100L),SETTINGS,w);
        assertEquals(4,w.plants);c.rejectSupply("Original context changed",w);assertEquals(0,w.clears);assertEquals(ForagingReplantCycle.Stage.FAILED,c.stage());
    }
}
