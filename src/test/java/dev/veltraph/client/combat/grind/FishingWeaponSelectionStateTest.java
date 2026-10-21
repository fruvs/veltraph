package dev.veltraph.client.combat.grind;
import dev.veltraph.client.navigation.ScopedSlotPublication;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FishingWeaponSelectionStateTest {
    private static final class Transaction implements ScopedSlotPublication.Effects {
        int held=5,ownSetters,packets;boolean sameSource=true,afterSetter,committed;
        final dev.veltraph.client.garden.pests.PestInventoryRestorationJournal.SlotVersions versions=new dev.veltraph.client.garden.pests.PestInventoryRestorationJournal.SlotVersions();
        long committedVersion;Runnable before=()->{},after=()->{},submit=()->{};
        final List<FishingWeaponSelectionState.Phase> observations=new ArrayList<>();
        FishingWeaponSelectionState.Phase phase(){
            var stage=committed?FishingWeaponSelectionState.Stage.COMMITTED:afterSetter?FishingWeaponSelectionState.Stage.AFTER_NATIVE_SETTER:FishingWeaponSelectionState.Stage.ORIGINAL;
            boolean history=versions.matches(committed?committedVersion:afterSetter?2:0);
            var phase=FishingWeaponSelectionState.observe(new FishingWeaponSelectionState.Frame(stage,sameSource,history,committed,5,2,held));observations.add(phase);return phase;
        }
        public int slot(){return held;}public void observe(){}
        public boolean beforeCurrent(){before.run();return phase()==FishingWeaponSelectionState.Phase.ORIGINAL_ROD;}
        public void select(int next){versions.begin();held=next;versions.finish();ownSetters++;}
        public boolean afterCurrent(){afterSetter=true;try{after.run();var p=phase();return p==FishingWeaponSelectionState.Phase.NATIVE_SETTER_RETURNED;}finally{afterSetter=false;}}
        public void submit(int next){packets++;submit.run();}
        void manual(int next){versions.begin();held=next;versions.finish();}
        ScopedSlotPublication.Outcome execute(){var result=ScopedSlotPublication.apply(5,2,this);if(result.confirmed()){committed=true;committedVersion=versions.version();}return result;}
    }
    @Test void originalProtocolAcceptsOnlyRealSetterPrefixThenOnePacketAndAdoptsConfirmedLoan(){
        var t=new Transaction();var result=t.execute();assertTrue(result.confirmed());assertEquals(1,t.ownSetters);assertEquals(1,t.packets);assertEquals(2,t.held);
        assertEquals(List.of(FishingWeaponSelectionState.Phase.ORIGINAL_ROD,FishingWeaponSelectionState.Phase.NATIVE_SETTER_RETURNED,FishingWeaponSelectionState.Phase.NATIVE_SETTER_RETURNED),t.observations);
        assertEquals(FishingWeaponSelectionState.Phase.COMMITTED_TOOL,t.phase());
    }
    @Test void actualCounterexampleOldUncommittedProxyRejectsAfterSetterButTypedReturnedPrefixDoesNot(){
        var t=new Transaction();assertEquals(FishingWeaponSelectionState.Phase.ORIGINAL_ROD,t.phase());t.select(2);
        assertFalse(t.committed);assertNotEquals(5,t.held); // The old beforeCommit=true original-rod test fails here.
        t.afterSetter=true;assertEquals(FishingWeaponSelectionState.Phase.NATIVE_SETTER_RETURNED,t.phase());assertFalse(t.committed);
        t.afterSetter=false;assertEquals(FishingWeaponSelectionState.Phase.REFUSED,t.phase());assertEquals(0,t.packets);
    }
    @Test void foreignToolSlotBeforeOriginalSetterCannotMasqueradeAsAnAfterSetterPrefix(){
        var t=new Transaction();t.before=()->t.manual(2);var result=t.execute();assertEquals(ScopedSlotPublication.Status.REFUSED,result.status());assertEquals(0,t.ownSetters);assertEquals(0,t.packets);assertEquals(2,t.held);
    }
    @Test void foreignBeforeSetterABAToOriginalRodStillRevokesNativeHistory(){
        var t=new Transaction();t.before=()->{t.manual(3);t.manual(5);};var result=t.execute();assertFalse(result.confirmed());assertEquals(0,t.ownSetters);assertEquals(0,t.packets);assertEquals(5,t.held);
    }
    @Test void foreignSameToolSetterAfterOwnSetterRevokesBeforePacketWithoutRestoringOldRod(){
        var t=new Transaction();t.after=()->t.manual(2);var result=t.execute();assertEquals(ScopedSlotPublication.Status.REFUSED,result.status());assertTrue(result.localSelected());assertFalse(result.packetSubmitted());assertEquals(1,t.ownSetters);assertEquals(0,t.packets);assertEquals(2,t.held);assertFalse(t.committed);
    }
    @Test void foreignSlotAfterOwnSetterIsPreservedAndBlocksSubmission(){
        var t=new Transaction();t.after=()->t.manual(3);var result=t.execute();assertFalse(result.confirmed());assertEquals(3,t.held);assertEquals(0,t.packets);assertFalse(t.committed);
    }
    @Test void foreignUpdateDuringPacketSubmissionMakesPartialOutcomeUnconfirmed(){
        var t=new Transaction();t.submit=()->t.manual(2);var result=t.execute();assertTrue(result.localSelected());assertTrue(result.packetSubmitted());assertFalse(result.confirmed());assertEquals(1,t.packets);assertFalse(t.committed);assertEquals(FishingWeaponSelectionState.Phase.REFUSED,t.phase());
    }
    @Test void callbackThrowClosesPrefixAndCannotForceCommitOrLatePacket(){
        var t=new Transaction();t.after=()->{throw new IllegalStateException("interference");};var result=t.execute();assertEquals(ScopedSlotPublication.Status.FAILED,result.status());assertTrue(result.localSelected());assertFalse(t.afterSetter);assertFalse(t.committed);assertEquals(0,t.packets);assertEquals(FishingWeaponSelectionState.Phase.REFUSED,t.phase());
    }
    @Test void ownerOrWorldOrSettingsRevocationDuringOwnPrefixStillBlocksPacket(){
        var t=new Transaction();t.after=()->t.sameSource=false;var result=t.execute();assertFalse(result.confirmed());assertEquals(0,t.packets);assertEquals(FishingWeaponSelectionState.Phase.REFUSED,t.phase());
    }
    @Test void committedManualSameSlotABARevokesDataPhase(){
        var t=new Transaction();assertTrue(t.execute().confirmed());assertEquals(FishingWeaponSelectionState.Phase.COMMITTED_TOOL,t.phase());t.manual(3);t.manual(2);assertEquals(FishingWeaponSelectionState.Phase.REFUSED,t.phase());
    }
    @Test void nativeSetterEntryWithoutNormalReturnCannotSupplyReturnedPrefix(){
        var t=new Transaction();t.versions.begin();t.held=2;t.afterSetter=true;assertEquals(FishingWeaponSelectionState.Phase.REFUSED,t.phase());assertEquals(1,t.versions.inFlight());assertEquals(0,t.packets);
    }
    @Test void nestedOrUnknownNativeHistoryCannotSupplyExactlyOneSetter(){
        var t=new Transaction();t.versions.begin();t.versions.begin();t.held=2;t.versions.finish();t.versions.finish();t.afterSetter=true;assertEquals(FishingWeaponSelectionState.Phase.REFUSED,t.phase());
        var bad=new Transaction();bad.versions.finish();bad.afterSetter=true;bad.held=2;assertFalse(bad.versions.known());assertEquals(FishingWeaponSelectionState.Phase.REFUSED,bad.phase());
    }
    @Test void capturedConfirmedHistoryCanRestoreOnceAfterChildDisableWithoutNewerNativeSetter(){
        var t=new Transaction();assertTrue(t.execute().confirmed());long captured=t.versions.version();var once=new WeaponSelectionReturn();int[] restoreCalls={0};
        var effects=new WeaponSelectionReturn.Effects(){public boolean current(){return t.sameSource&&t.held==2&&t.versions.matches(captured);}public boolean submit(){restoreCalls[0]++;t.manual(5);return true;}};
        assertTrue(once.restore(effects));assertEquals(5,t.held);assertFalse(once.restore(effects));assertEquals(1,restoreCalls[0]);assertFalse(t.versions.matches(captured));
    }
    @Test void sameSlotManualUpdateDuringChildDisableRevokesOldRestorationHistory(){
        var t=new Transaction();assertTrue(t.execute().confirmed());long captured=t.versions.version();t.manual(2);int[] restores={0};
        boolean result=new WeaponSelectionReturn().restore(new WeaponSelectionReturn.Effects(){public boolean current(){return t.sameSource&&t.held==2&&t.versions.matches(captured);}public boolean submit(){restores[0]++;t.manual(5);return true;}});
        assertFalse(result);assertEquals(0,restores[0]);assertEquals(2,t.held);
    }
    @Test void unconfirmedOrMissingCommittedNativeLoanNeverPermitsTool(){
        assertEquals(FishingWeaponSelectionState.Phase.REFUSED,FishingWeaponSelectionState.observe(new FishingWeaponSelectionState.Frame(FishingWeaponSelectionState.Stage.COMMITTED,true,true,false,5,2,2)));
    }
    @Test void sourceAndHistoryAndSlotBoundsAreRequiredAcrossEveryPhase(){
        for(var stage:FishingWeaponSelectionState.Stage.values()){
            int held=stage==FishingWeaponSelectionState.Stage.ORIGINAL?5:2;
            assertEquals(FishingWeaponSelectionState.Phase.REFUSED,FishingWeaponSelectionState.observe(new FishingWeaponSelectionState.Frame(stage,false,true,true,5,2,held)));
            assertEquals(FishingWeaponSelectionState.Phase.REFUSED,FishingWeaponSelectionState.observe(new FishingWeaponSelectionState.Frame(stage,true,false,true,5,2,held)));
            for(int invalid:new int[]{-1,9,Integer.MAX_VALUE})assertEquals(FishingWeaponSelectionState.Phase.REFUSED,FishingWeaponSelectionState.observe(new FishingWeaponSelectionState.Frame(stage,true,true,true,5,2,invalid)));
        }
        assertEquals(FishingWeaponSelectionState.Phase.REFUSED,FishingWeaponSelectionState.observe(null));
    }
    @Test void originalRodMayBeCheckedBeforeToolChoiceExistsAndSameSlotSetterStillNeedsActualPrefix(){
        assertEquals(FishingWeaponSelectionState.Phase.ORIGINAL_ROD,FishingWeaponSelectionState.observe(new FishingWeaponSelectionState.Frame(FishingWeaponSelectionState.Stage.ORIGINAL,true,true,false,5,-1,5)));
        assertEquals(FishingWeaponSelectionState.Phase.NATIVE_SETTER_RETURNED,FishingWeaponSelectionState.observe(new FishingWeaponSelectionState.Frame(FishingWeaponSelectionState.Stage.AFTER_NATIVE_SETTER,true,true,false,5,5,5)));
        assertEquals(FishingWeaponSelectionState.Phase.REFUSED,FishingWeaponSelectionState.observe(new FishingWeaponSelectionState.Frame(FishingWeaponSelectionState.Stage.AFTER_NATIVE_SETTER,true,false,false,5,5,5)));
    }
}
