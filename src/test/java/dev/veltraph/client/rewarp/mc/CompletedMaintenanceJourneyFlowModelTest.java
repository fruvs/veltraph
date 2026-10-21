package dev.veltraph.client.rewarp.mc;

import dev.veltraph.client.core.AbstractModule;
import dev.veltraph.client.core.ModuleCategory;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.rewarp.FarmMaintenanceLease;
import dev.veltraph.client.rewarp.SavedAnchorRecovery;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** MODEL integration: real production lease/recovery/Core methods and real Core receipts, simulated travel observations.
 * It supplies no Minecraft player/world, RootPassiveTransition, native receipt, or native permission.
 * The pure detachment identity is also called by McSavedAnchorRecovery only after its genuine completed source check.
 */
final class CompletedMaintenanceJourneyFlowModelTest {
    private static final class ModelModule extends AbstractModule {
        final ModuleManager root;final boolean passive;final Object engine=new Object();
        boolean suspended;int enables,disables;ModuleManager.OwnEnableReceipt enableReceipt;ModuleManager.OwnDisableReceipt disableReceipt;
        ModelModule(ModuleManager root,String id,boolean passive){super(id,id,ModuleCategory.INTERNAL);this.root=root;this.passive=passive;}
        @Override public boolean isPassiveObserver(){return passive;}
        @Override public boolean ownsGameplayInput(){return !passive&&!suspended;}
        @Override public void onEnable(){enables++;enableReceipt=root.currentOwnEnableReceipt(this).orElseThrow();}
        @Override public void onDisable(){disables++;}
        @Override protected void postDisableNormalReturned(ModuleManager.OwnDisableReceipt receipt){disableReceipt=receipt;}
    }
    private static final class ModelTravel implements SavedAnchorRecovery.Actions {
        SavedAnchorRecovery.Travel observed=SavedAnchorRecovery.Travel.ACTIVE;int walks,aligns,releases;
        @Override public boolean startWalk(){walks++;return true;}
        @Override public boolean startFlight(){fail("Model exact return should remain walking");return false;}
        @Override public SavedAnchorRecovery.Travel travel(){return observed;}
        @Override public void align(){aligns++;}
        @Override public void release(){releases++;}
    }
    private static final class ModelJourney {
        final long serial;final FarmMaintenanceLease.Token token;final ModelTravel travel=new ModelTravel();
        final SavedAnchorRecovery recovery=new SavedAnchorRecovery(travel,SavedAnchorRecovery.Settings.defaults());
        final ModuleManager.LifecycleSnapshot helperEnabled;
        ModelJourney(long serial,FarmMaintenanceLease.Token token,ModuleManager.LifecycleSnapshot helperEnabled){this.serial=serial;this.token=token;this.helperEnabled=helperEnabled;}
    }
    private record ModelArrival(ModelJourney journey){}
    private static final class Flow {
        final ModuleManager root=new ModuleManager();final FarmMaintenanceLease lease=new FarmMaintenanceLease();
        final ModelModule farm=new ModelModule(root,"farm",false),helper=new ModelModule(root,"helper",false),observer=new ModelModule(root,"observer",true);
        Object selectedJourney;long generation;SavedAnchorRecovery.State terminal=SavedAnchorRecovery.State.IDLE;
        Flow(){root.register(farm);root.register(helper);root.register(observer);var before=root.lifecycleSnapshot();farm.setEnabled(true);
            assertTrue(root.ownEnableMatches(farm.enableReceipt,before,farm));assertTrue(farm.enableReceipt.current());}
        ModelJourney begin(long at){
            assertNull(selectedJourney);var token=lease.prepare(farm,helper,at,45_000);assertNotNull(token);
            farm.suspended=true;var before=root.lifecycleSnapshot();helper.setEnabled(true);
            assertTrue(root.ownEnableMatches(helper.enableReceipt,before,helper));assertTrue(helper.enableReceipt.current());
            assertSame(helper,root.inputOwner().orElseThrow());assertTrue(lease.commit(token));assertTrue(lease.beginRestore(token));assertTrue(lease.beginReturn(token));
            var journey=new ModelJourney(++generation,token,root.lifecycleSnapshot());selectedJourney=journey;
            assertTrue(journey.recovery.start(true,false,at));assertEquals(SavedAnchorRecovery.State.WALKING,journey.recovery.state());return journey;
        }
        ModelArrival observeArrival(ModelJourney j,long at){
            j.travel.observed=SavedAnchorRecovery.Travel.ARRIVED;j.recovery.tick(true,true,false,false,at+100);
            assertEquals(SavedAnchorRecovery.State.ALIGNING,j.recovery.state());j.recovery.tick(true,true,true,true,at+200);
            assertEquals(SavedAnchorRecovery.State.SETTLING,j.recovery.state());j.recovery.tick(true,true,true,true,at+699);
            assertEquals(SavedAnchorRecovery.State.SETTLING,j.recovery.state());j.recovery.tick(true,true,true,true,at+700);
            assertEquals(SavedAnchorRecovery.State.ARRIVED,j.recovery.state());assertEquals(1,j.travel.walks);assertEquals(2,j.travel.releases);
            assertTrue(root.lifecycleCurrent(j.helperEnabled));return new ModelArrival(j);
        }
        boolean ready(ModelJourney j,ModelArrival arrival,FarmMaintenanceLease.Token token){
            if(arrival==null||arrival.journey()!=j||j.recovery.state()!=SavedAnchorRecovery.State.ARRIVED
                ||!CompletedMaintenanceJourneyIdentity.matches(j,selectedJourney,j.serial,generation,false)||!root.lifecycleCurrent(j.helperEnabled))return false;
            return lease.ready(token);
        }
        boolean resume(ModelJourney j,ModelArrival arrival,FarmMaintenanceLease.Token token){
            if(arrival==null||arrival.journey()!=j||j.recovery.state()!=SavedAnchorRecovery.State.ARRIVED||lease.state()!=FarmMaintenanceLease.State.READY
                ||!lease.owns(token)||token!=j.token||!CompletedMaintenanceJourneyIdentity.matches(j,selectedJourney,j.serial,generation,false))return false;
            helper.setEnabled(false);var stopped=root.lifecycleSnapshot();
            assertNotNull(helper.disableReceipt);assertTrue(helper.disableReceipt.current());
            assertTrue(root.ownDisableFromMatches(helper.disableReceipt,j.helperEnabled,stopped,helper));
            assertSame(helper.disableReceipt,root.completedOwnDisableFrom(j.helperEnabled,helper).orElseThrow());
            if(!lease.resume(token))return false;farm.suspended=false;
            // MODEL normal completion only: real native source/restore9 is deliberately outside this fixture.
            if(lease.state()!=FarmMaintenanceLease.State.RESUMED||!CompletedMaintenanceJourneyIdentity.matches(j,selectedJourney,j.serial,generation,false))return false;
            assertFalse(root.lifecycleCurrent(j.helperEnabled));assertFalse(j.recovery.active());
            var detached=CompletedMaintenanceJourneyIdentity.detach((Object)j,selectedJourney,j.serial,generation,false);
            if(detached==null||!detached.rawCurrent(selectedJourney,generation,false))return false;
            terminal=SavedAnchorRecovery.State.ARRIVED;selectedJourney=detached.remaining();return true;
        }
        void passiveEnable(){assertNull(selectedJourney);var before=root.lifecycleSnapshot();observer.setEnabled(true);
            assertTrue(root.ownEnableMatches(observer.enableReceipt,before,observer));assertFalse(root.lifecycleCurrent(before));
            assertTrue(root.lifecycleCurrent(root.lifecycleSnapshot()));assertSame(farm,root.inputOwner().orElseThrow());assertEquals(1,root.enabledControllers().size());}
        void passiveDisable(){assertNull(selectedJourney);var before=root.lifecycleSnapshot();observer.setEnabled(false);
            assertTrue(root.ownDisableFromMatches(observer.disableReceipt,before,root.lifecycleSnapshot(),observer));
            assertSame(observer.disableReceipt,root.completedOwnDisableFrom(before,observer).orElseThrow());assertFalse(root.lifecycleCurrent(before));assertSame(farm,root.inputOwner().orElseThrow());}
    }
    @Test void twoMaintenanceCyclesWithPassiveLifecycleChangeAfterCompletedResume(){
        var f=new Flow();var engine=f.farm.engine;var first=f.begin(0);var firstArrival=f.observeArrival(first,0);
        assertTrue(f.ready(first,firstArrival,first.token));assertTrue(f.resume(first,firstArrival,first.token));assertNull(f.selectedJourney);
        long retainedGeneration=f.generation;assertEquals(first.serial,retainedGeneration);assertEquals(SavedAnchorRecovery.State.ARRIVED,f.terminal);
        f.passiveEnable();assertEquals(retainedGeneration,f.generation);var second=f.begin(2_000);assertNotSame(first.token,second.token);assertNotSame(first,second);
        assertFalse(f.lease.owns(first.token));assertFalse(f.lease.commit(first.token));assertFalse(CompletedMaintenanceJourneyIdentity.matches(first,f.selectedJourney,first.serial,f.generation,false));
        var secondArrival=f.observeArrival(second,2_000);assertTrue(f.ready(second,secondArrival,second.token));assertTrue(f.resume(second,secondArrival,second.token));
        f.passiveDisable();assertSame(engine,f.farm.engine);assertEquals(1,f.farm.enables);assertEquals(0,f.farm.disables);assertEquals(2,f.helper.enables);assertEquals(2,f.helper.disables);
        assertEquals(2,f.generation);assertNull(f.selectedJourney);assertEquals(FarmMaintenanceLease.State.RESUMED,f.lease.state());
    }
    @Test void interruptedReturnCannotProduceReadyResumeOrDetach(){var f=new Flow();var j=f.begin(0);j.recovery.tick(false,false,false,false,100);
        f.lease.cancel(j.token,"model interrupted context");assertEquals(SavedAnchorRecovery.State.CANCELLED,j.recovery.state());
        assertFalse(f.ready(j,new ModelArrival(j),j.token));assertFalse(f.resume(j,new ModelArrival(j),j.token));assertSame(j,f.selectedJourney);assertTrue(f.helper.isEnabled());assertTrue(f.farm.suspended);}
    @Test void navigatorArrivalOutsideTargetDoesNotResume(){var f=new Flow();var j=f.begin(0);j.travel.observed=SavedAnchorRecovery.Travel.ARRIVED;
        j.recovery.tick(true,false,true,true,100);assertEquals(SavedAnchorRecovery.State.FAILED,j.recovery.state());assertFalse(f.ready(j,new ModelArrival(j),j.token));
        assertFalse(f.resume(j,new ModelArrival(j),j.token));assertSame(j,f.selectedJourney);assertEquals(FarmMaintenanceLease.State.RETURNING,f.lease.state());}
    @Test void wrongArrivalObjectCannotUseAnotherCompletedCycle(){var f=new Flow();var j=f.begin(0);var actual=f.observeArrival(j,0);
        var other=new Flow();var foreign=other.begin(0);var foreignArrival=other.observeArrival(foreign,0);
        assertFalse(f.ready(j,foreignArrival,j.token));assertSame(j,f.selectedJourney);assertEquals(FarmMaintenanceLease.State.RETURNING,f.lease.state());
        assertTrue(f.ready(j,actual,j.token));assertFalse(f.resume(j,foreignArrival,j.token));assertTrue(f.helper.isEnabled());assertSame(j,f.selectedJourney);}
    @Test void wrongActualLeaseTokenCannotDisableHelperOrDetach(){var f=new Flow();var j=f.begin(0);var arrival=f.observeArrival(j,0);
        var foreignLease=new FarmMaintenanceLease();var wrong=foreignLease.prepare(new Object(),new Object(),0,45_000);assertNotNull(wrong);
        assertFalse(f.ready(j,arrival,wrong));assertEquals(FarmMaintenanceLease.State.RETURNING,f.lease.state());assertTrue(f.ready(j,arrival,j.token));
        assertFalse(f.resume(j,arrival,wrong));assertTrue(f.helper.isEnabled());assertEquals(0,f.helper.disables);assertSame(j,f.selectedJourney);}
    @Test void foreignJourneyReplacementIsPreservedAtReadyAndResume(){var f=new Flow();var j=f.begin(0);var arrival=f.observeArrival(j,0);assertTrue(f.ready(j,arrival,j.token));
        var replacement=new Object();f.selectedJourney=replacement;assertFalse(f.resume(j,arrival,j.token));assertSame(replacement,f.selectedJourney);
        assertFalse(f.ready(j,arrival,j.token));assertEquals(0,f.helper.disables);assertTrue(f.farm.suspended);}
    @Test void changedGenerationCannotClearSameJourneyReference(){var f=new Flow();var j=f.begin(0);var arrival=f.observeArrival(j,0);assertTrue(f.ready(j,arrival,j.token));
        f.generation++;assertFalse(f.resume(j,arrival,j.token));assertSame(j,f.selectedJourney);assertEquals(0,f.helper.disables);assertTrue(f.farm.suspended);}
    @Test void proposedDetachmentCannotClearAReplacementIntroducedAfterPreparation(){
        var f=new Flow();var j=f.begin(0);var arrival=f.observeArrival(j,0);assertTrue(f.ready(j,arrival,j.token));
        var detached=CompletedMaintenanceJourneyIdentity.detach((Object)j,f.selectedJourney,j.serial,f.generation,false);assertNotNull(detached);
        var foreign=new Object();f.selectedJourney=foreign;assertFalse(detached.rawCurrent(f.selectedJourney,f.generation,false));
        assertFalse(f.resume(j,arrival,j.token));assertSame(foreign,f.selectedJourney);assertEquals(0,f.helper.disables);
    }
    @Test void staleDisableReceiptDoesNotCertifyNextCycle(){var f=new Flow();var first=f.begin(0);var arrival=f.observeArrival(first,0);assertTrue(f.ready(first,arrival,first.token));assertTrue(f.resume(first,arrival,first.token));
        var oldDisable=f.helper.disableReceipt;f.passiveEnable();var second=f.begin(2_000);assertFalse(oldDisable.current());
        assertFalse(f.root.ownDisableFromMatches(oldDisable,second.helperEnabled,f.root.lifecycleSnapshot(),f.helper));assertSame(second,f.selectedJourney);assertTrue(f.helper.isEnabled());}
}
