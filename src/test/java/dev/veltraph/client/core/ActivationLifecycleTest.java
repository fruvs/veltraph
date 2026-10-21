package dev.veltraph.client.core;
import dev.veltraph.client.game.GameContext;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
class ActivationLifecycleTest {
    @Test void blockedStartDoesNotTouchAnotherMacrosToolSelection() {
        var manager=new ModuleManager();
        var first=new AbstractModule("first","First",ModuleCategory.FARMING) {};
        var second=new AbstractModule("second","Second",ModuleCategory.COMBAT) {
            @Override public Optional<String> preStartCheck() { fail("preStart must not run while another macro owns input");return Optional.empty(); }
        };
        manager.register(first);manager.register(second);first.setEnabled(true);
        assertFalse(new ModuleActivationGuard(manager,()->GameContext.NONE,message->{}).start(second));
        assertTrue(first.isEnabled());assertFalse(second.isEnabled());
    }
    @Test void failedEnableDisablesAndReleasesModule() {
        var manager=new ModuleManager();var released=new boolean[1];
        var bad=new AbstractModule("bad","Bad",ModuleCategory.COMBAT) {
            @Override public void onEnable() { throw new IllegalStateException("test failure"); }
            @Override public void onDisable() { released[0]=true; }
        };
        manager.register(bad);
        assertFalse(new ModuleActivationGuard(manager,()->GameContext.NONE,message->{}).start(bad));
        assertFalse(bad.isEnabled());assertTrue(released[0]);
    }
}
