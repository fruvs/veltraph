package dev.veltraph.client.rotation;

import dev.veltraph.client.config.MapConfigService;
import dev.veltraph.client.config.RotationConfig;
import dev.veltraph.client.input.ScriptingInputLock;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Actual pure request/config/input dependencies; these cases do not certify native Root admission. */
class QueuedRotationSourceRequestTest {
    private final SourceRotationStepper.Settings settings=SourceRotationStepper.Settings.standard(.5,1);
    @Test void sourceCalculationAloneCannotRunTheCompletionEffect(){
        var request=new RotationRequest(new SourceRotationStepper());var effects=new AtomicInteger();
        request.replace(0,0,effects::incrementAndGet);
        var step=request.advance(0,0,settings,true,false);assertTrue(step.complete());assertEquals(0,effects.get());
        request.applied(step);request.applied(step);assertEquals(1,effects.get());
    }
    @Test void actualConfigurationAbaRefusesTheDeferredCompletion(){
        var store=new MapConfigService();var original=store.storedStateSnapshot();var effects=new AtomicInteger();
        var request=new RotationRequest(new SourceRotationStepper());
        request.replace(0,0,()->{if(QueuedRotationWorkflowFence.allowed(()->true,original::current,()->true))effects.incrementAndGet();});
        var step=request.advance(0,0,settings,true,false);
        store.set(RotationConfig.ROTATION_SPEED_MULTIPLIER,2d);store.reset(RotationConfig.ROTATION_SPEED_MULTIPLIER);
        assertEquals(1d,store.get(RotationConfig.ROTATION_SPEED_MULTIPLIER));request.applied(step);
        assertEquals(0,effects.get());assertFalse(request.pending());
    }
    @Test void inputReleaseAndReacquireCannotRestoreOldCompletionCustody(){
        ScriptingInputLock.releaseAll();ScriptingInputLock.acquire("queued-source-test");
        try{var held=ScriptingInputLock.controlSnapshot();var request=new RotationRequest(new SourceRotationStepper());var effects=new AtomicInteger();
            request.replace(0,0,()->{if(QueuedRotationWorkflowFence.allowed(()->true,held::current,()->true))effects.incrementAndGet();});
            var step=request.advance(0,0,settings,true,false);
            ScriptingInputLock.releaseAll();ScriptingInputLock.acquire("queued-source-test");request.applied(step);
            assertEquals(0,effects.get());assertFalse(held.current());
        }finally{ScriptingInputLock.releaseAll();}
    }
    @Test void lateWorkflowMutationMustBeSeenByTheStoredNativeLeaf(){
        var store=new MapConfigService();var original=store.storedStateSnapshot();var effects=new AtomicInteger();
        var request=new RotationRequest(new SourceRotationStepper());
        request.replace(0,0,()->{if(QueuedRotationWorkflowFence.allowed(()->{store.set(RotationConfig.ROTATION_SPEED_MULTIPLIER,2d);return true;},original::current,()->true))effects.incrementAndGet();});
        request.applied(request.advance(0,0,settings,true,false));assertEquals(0,effects.get());
    }
    @Test void completionReentryKeepsItsReplacementAndCannotReplayTheOldEffect(){
        var request=new RotationRequest(new SourceRotationStepper());var first=new AtomicInteger();var second=new AtomicInteger();
        request.replace(0,0,()->{first.incrementAndGet();request.replace(90,0,second::incrementAndGet);});
        var original=request.advance(0,0,settings,true,false);request.applied(original);request.applied(original);
        assertEquals(1,first.get());assertEquals(0,second.get());assertTrue(request.pending());
        assertTrue(request.advance(0,0,settings,true,false).yaw()>0);
    }
}
