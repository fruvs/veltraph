package dev.veltraph.client.input;

import dev.veltraph.client.core.AbstractModule;
import dev.veltraph.client.core.ModuleActivationGuard;
import dev.veltraph.client.core.ModuleCategory;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.game.FixedGameContextService;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.game.GameContext;
import dev.veltraph.client.testutil.FakeChatService;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual pure guard/registry/lock lifecycle; Root ordering is separately checked in native callbacks. */
class RegisteredInputTickLifecycleTest {
    @AfterEach void reset(){ScriptingInputLock.releaseAll();ScriptingInputLock.onRelease(()->{});}
    private static final class Controller extends AbstractModule {
        int enables,ticks,actions;boolean observer;
        Controller(String id){super(id,id,ModuleCategory.INTERNAL);}
        public boolean requiresSkyBlock(){return true;}
        public Optional<GameArea> requiredArea(){return Optional.of(GameArea.GARDEN);}
        public boolean isPassiveObserver(){return observer;}
        public boolean ownsGameplayInput(){return !observer;}
        public void onEnable(){enables++;}
        public void onTick(){ticks++;if(ScriptingInputLock.isActive()&&id().equals(ScriptingInputLock.ownerModuleId()))actions++;}
    }
    private static ModuleActivationGuard gate(ModuleManager m,GameContext c){return new ModuleActivationGuard(m,new FixedGameContextService(c),new FakeChatService());}
    private static GameContext garden(){return new GameContext(true,true,GameArea.GARDEN);}
    private static void publishRegisteredOwner(ModuleManager m){var owner=m.inputOwner();ScriptingInputLock.set(owner.isPresent(),owner.map(dev.veltraph.client.core.Module::id).orElse(null));}
    @Test void freshGuardDoesNotActuateAndFirstOrdinaryCallbackNeedsPublishedLock(){
        var m=new ModuleManager();var c=new Controller("first");m.register(c);
        assertTrue(gate(m,garden()).start(c));assertEquals(1,c.enables);assertEquals(0,c.ticks);assertEquals(0,c.actions);assertFalse(ScriptingInputLock.isActive());
        publishRegisteredOwner(m);m.tick();assertEquals(1,c.ticks);assertEquals(1,c.actions);assertEquals(c.id(),ScriptingInputLock.ownerModuleId());
    }
    @Test void postCallbackOnlyPublicationMissesFirstAction(){
        var m=new ModuleManager();var c=new Controller("late");m.register(c);assertTrue(gate(m,garden()).start(c));
        m.tick();publishRegisteredOwner(m);assertEquals(1,c.ticks);assertEquals(0,c.actions);
        m.tick();assertEquals(1,c.actions);
    }
    @Test void rejectedLocationNeverAcquiresOrDispatches(){
        var m=new ModuleManager();var c=new Controller("blocked");m.register(c);
        assertFalse(gate(m,new GameContext(true,true,GameArea.HUB)).start(c));publishRegisteredOwner(m);m.tick();
        assertFalse(c.isEnabled());assertEquals(0,c.enables);assertEquals(0,c.ticks);assertFalse(ScriptingInputLock.isActive());
    }
    @Test void disabledOwnerBetweenPublicationAndCallbacksDoesNotRun(){
        var m=new ModuleManager();var c=new Controller("disabled");m.register(c);assertTrue(gate(m,garden()).start(c));publishRegisteredOwner(m);
        m.disable(c.id());m.tick();assertEquals(0,c.ticks);assertEquals(0,c.actions);publishRegisteredOwner(m);assertFalse(ScriptingInputLock.isActive());
    }
    @Test void PassiveOnlyRegistryDoesNotTakeGameplayLock(){
        var m=new ModuleManager();var c=new Controller("observer");c.observer=true;m.register(c);assertTrue(gate(m,garden()).start(c));
        publishRegisteredOwner(m);m.tick();assertEquals(1,c.ticks);assertEquals(0,c.actions);assertFalse(ScriptingInputLock.isActive());
    }
}
