package dev.veltraph.client.service.mc;

import java.util.*;
import java.util.function.BooleanSupplier;
import dev.veltraph.client.navigation.ScopedHeldKeyPublication;
import dev.veltraph.client.rotation.NormalIdleInputVersion;
import dev.veltraph.client.observation.unexpected.UnexpectedBehaviorMonitor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.observation.unexpected.UnexpectedBehaviorMonitor.*;

/** Existing pure ports only. Does not mint McHeldKeys native receipts or claim native cleanup acceptance. */
class OwnedHeldOriginCompositionTest {
    enum Callback { NONE, DISABLE, THROW, OWNER_ABA, FOREIGN_KEY_ABA }
    static final Context C=new Context(new Activation("farm",1,true),1,1,1);
    static final class F implements ScopedHeldKeyPublication.Effects {
        final NormalIdleInputVersion history=new NormalIdleInputVersion();
        final NormalIdleInputVersion.Snapshot before=history.snapshot();
        final List<Object> actual=new ArrayList<>();
        final UnexpectedBehaviorMonitor monitor=new UnexpectedBehaviorMonitor(f->{});
        NormalIdleInputVersion.Snapshot immediate;
        final Callback callback;boolean callbackRan,held,enabled=true,confirmed,failed;long ownerGeneration=1;
        int setters,sourceCalls;boolean desired;
        BooleanSupplier passiveCurrent=()->confirmed&&immediate!=null&&history.current(immediate);
        F(Callback callback,boolean held){this.callback=callback;this.held=held;monitor.context(C,100);}
        public void observe(){monitor.intent(C,Kind.INPUT_STATE,Value.number(desired?256:0),0,100,500,()->passiveCurrent.getAsBoolean());}
        boolean source(){sourceCalls++;if(setters>0&&!callbackRan){callbackRan=true;switch(callback){
            case DISABLE -> enabled=false;
            case THROW -> throw new IllegalStateException("later source callback unavailable");
            case OWNER_ABA -> {ownerGeneration++;ownerGeneration++;}
            case FOREIGN_KEY_ABA -> {raw(!held);raw(!held);}
            case NONE -> {}
        }}return true;}
        boolean nativeCurrent(){return enabled&&ownerGeneration==1&&history.advancedOnly(before,history.snapshot(),actual);}
        public boolean current(){return ScopedHeldKeyPublication.afterRefusal(this::source,this::nativeCurrent);}
        public boolean down(){return held;}
        public void setDown(boolean value){setters++;var invocation=new Object();history.begin(invocation,true);held=value;history.end(false);actual.add(invocation);immediate=history.snapshot();}
        void raw(boolean value){history.begin(null,true);held=value;history.end(false);}
        public boolean toggle(){return false;}
        public void clearRestore(){throw new AssertionError("Normal key never has toggle restore");}
        boolean run(boolean value){desired=value;try{confirmed=ScopedHeldKeyPublication.apply(value,this);return confirmed;}catch(RuntimeException unavailable){failed=true;confirmed=false;return false;}}
        boolean exactNormallyReturnedHistory(){return immediate!=null&&history.current(immediate)&&history.advancedOnly(before,immediate,actual);}
        boolean commit(){return OwnedBreakingCommitFence.current(confirmed,this::exactNormallyReturnedHistory,this::source,this::nativeCurrent);}
        Finding observedOutcome(){return monitor.observe(new Observation(C,Kind.INPUT_STATE,Value.number(desired?0:256),Value.number(desired?256:0),101));}
    }
    static void noMod(Finding finding){assertFalse(finding.evidence().contains(Cause.MOD_INTENT));assertNotEquals(Classification.EXPLAINED,finding.classification());}
    @Test void callbackDisableAfterNormalTrueRetainsExactHistoryButRefusesCommitAndModOrigin(){var f=new F(Callback.DISABLE,false);assertFalse(f.run(true));assertTrue(f.held);assertEquals(1,f.setters);assertTrue(f.exactNormallyReturnedHistory());assertFalse(f.commit());noMod(f.observedOutcome());assertTrue(f.exactNormallyReturnedHistory());}
    @Test void thrownLaterSourceCannotEraseNormalTrueHistoryOrBecomeGameplaySuccess(){var f=new F(Callback.THROW,false);assertFalse(f.run(true));assertTrue(f.failed);assertTrue(f.held);assertEquals(1,f.setters);assertTrue(f.exactNormallyReturnedHistory());assertFalse(f.commit());noMod(f.observedOutcome());}
    @Test void sameOwnerAbaRetainsOnlyExactAttemptHistoryAndNeverOrigin(){var f=new F(Callback.OWNER_ABA,false);assertFalse(f.run(true));assertTrue(f.held);assertEquals(3,f.ownerGeneration);assertTrue(f.exactNormallyReturnedHistory());assertFalse(f.commit());noMod(f.observedOutcome());}
    @Test void foreignKeyAbaInvalidatesBothCleanupHistoryAndModOriginDespiteSameHeldValue(){var f=new F(Callback.FOREIGN_KEY_ABA,false);assertFalse(f.run(true));assertTrue(f.held);assertEquals(1,f.setters);assertFalse(f.exactNormallyReturnedHistory());assertFalse(f.commit());noMod(f.observedOutcome());}
    @Test void callbackDisableAfterNormalFalsePreservesReleaseHistoryWithoutConfirmingGameplay(){var f=new F(Callback.DISABLE,true);assertFalse(f.run(false));assertFalse(f.held);assertEquals(1,f.setters);assertTrue(f.exactNormallyReturnedHistory());assertFalse(f.commit());noMod(f.observedOutcome());}
    @Test void throwingPassiveValidityCannotChangeNormalReturnOrRetainedHistoryAndCannotExplainOrigin(){var f=new F(Callback.NONE,false);assertTrue(f.run(true));assertTrue(f.commit());f.passiveCurrent=()->{throw new IllegalStateException("passive observer unavailable");};noMod(f.observedOutcome());assertTrue(f.confirmed);assertFalse(f.failed);assertTrue(f.exactNormallyReturnedHistory());assertEquals(1,f.setters);}
    @Test void pureConfirmedCurrentOriginIsConsumedOnceAndLaterForeignAbaCannotReuseIt(){var f=new F(Callback.NONE,false);assertTrue(f.run(true));assertTrue(f.commit());var finding=f.observedOutcome();assertEquals(Classification.EXPLAINED,finding.classification());assertTrue(finding.evidence().contains(Cause.MOD_INTENT));noMod(f.monitor.observe(new Observation(C,Kind.INPUT_STATE,Value.number(0),Value.number(256),102)));f.raw(false);f.raw(true);assertFalse(f.exactNormallyReturnedHistory());assertFalse(f.commit());}
    @Test void unconfirmedNativeReturnCannotRunProvidersOrTurnRetainedHistoryIntoCommit(){var f=new F(Callback.NONE,false);assertTrue(f.run(true));int calls=f.sourceCalls;assertFalse(OwnedBreakingCommitFence.current(false,f::exactNormallyReturnedHistory,f::source,f::nativeCurrent));assertEquals(calls,f.sourceCalls);assertTrue(f.exactNormallyReturnedHistory());}
}
