package dev.veltraph.client.input;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Scripting input-ownership lock: acquire/release semantics and the mouse/keyboard suppression rules. */
class ScriptingInputLockTest {

    @AfterEach
    void reset() {
        ScriptingInputLock.releaseAll();
        ScriptingInputLock.onRelease(()->{});
    }

    @Test
    void acquireMarksActiveWithOwner() {
        ScriptingInputLock.acquire("cactus");
        assertTrue(ScriptingInputLock.isActive());
        assertEquals("cactus", ScriptingInputLock.ownerModuleId());
    }

    @Test
    void releaseAllClearsLockAndOwner() {
        ScriptingInputLock.acquire("wart_crops");
        ScriptingInputLock.releaseAll();
        assertFalse(ScriptingInputLock.isActive());
        assertNull(ScriptingInputLock.ownerModuleId());
    }

    @Test
    void setTogglesOwnershipFromRunState() {
        ScriptingInputLock.set(true, "cactus");
        assertTrue(ScriptingInputLock.isActive());
        assertEquals("cactus", ScriptingInputLock.ownerModuleId());
        ScriptingInputLock.set(false, null);
        assertFalse(ScriptingInputLock.isActive());
        assertNull(ScriptingInputLock.ownerModuleId());
    }

    @Test
    void suppressesLookAndKeyboardOnlyWhileScriptingInWorldWithNoScreen() {
        ScriptingInputLock.acquire("cactus");
        // In-world, no screen open -> both mouse-look and keyboard are owned.
        assertTrue(ScriptingInputLock.shouldSuppressLook(false, true));
        assertTrue(ScriptingInputLock.shouldSuppressKeyboard(false, true));
        // A screen is open (Veltraph or vanilla UI) -> never suppress (typing/clicking must work).
        assertFalse(ScriptingInputLock.shouldSuppressLook(true, true));
        assertFalse(ScriptingInputLock.shouldSuppressKeyboard(true, true));
        // No world present (menu) -> never suppress.
        assertFalse(ScriptingInputLock.shouldSuppressLook(false, false));
        assertFalse(ScriptingInputLock.shouldSuppressKeyboard(false, false));
    }

    @Test
    void neverSuppressesWhenNotScripting() {
        assertFalse(ScriptingInputLock.isActive());
        assertFalse(ScriptingInputLock.shouldSuppressLook(false, true));
        assertFalse(ScriptingInputLock.shouldSuppressKeyboard(false, true));
    }
    @Test void platformReleaseRunsOnceAfterOwnershipClearsAndNotOnIdleTicks() {
        var count=new java.util.concurrent.atomic.AtomicInteger();
        ScriptingInputLock.onRelease(()->{assertFalse(ScriptingInputLock.isActive());assertNull(ScriptingInputLock.ownerModuleId());count.incrementAndGet();});
        ScriptingInputLock.releaseAll();assertEquals(0,count.get());
        ScriptingInputLock.acquire("water");ScriptingInputLock.releaseAll();ScriptingInputLock.releaseAll();
        assertEquals(1,count.get());
    }
}
