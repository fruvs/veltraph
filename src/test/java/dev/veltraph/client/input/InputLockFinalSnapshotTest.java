package dev.veltraph.client.input;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;
class InputLockFinalSnapshotTest {
 private Runnable previousRelease;private boolean previousActive;private String previousOwner;
 @BeforeEach void preservePlatformRelease()throws ReflectiveOperationException{var field=ScriptingInputLock.class.getDeclaredField("releaseInput");field.setAccessible(true);previousRelease=(Runnable)field.get(null);previousActive=ScriptingInputLock.isActive();previousOwner=ScriptingInputLock.ownerModuleId();ScriptingInputLock.onRelease(()->{});}
 @AfterEach void restorePlatformRelease(){ScriptingInputLock.onRelease(()->{});if(previousActive)ScriptingInputLock.acquire(previousOwner);else ScriptingInputLock.releaseAll();ScriptingInputLock.onRelease(previousRelease);}

 @Test void actualReleaseReacquireSameOwnerIsNotOldControl(){ScriptingInputLock.onRelease(()->{});ScriptingInputLock.acquire("snapshot-test");try{var s=ScriptingInputLock.controlSnapshot();ScriptingInputLock.releaseAll();ScriptingInputLock.acquire("snapshot-test");assertTrue(ScriptingInputLock.isActive());assertEquals("snapshot-test",ScriptingInputLock.ownerModuleId());assertFalse(s.current());}finally{ScriptingInputLock.releaseAll();}}
 @Test void actualOwnerReplacementInvalidatesControl(){ScriptingInputLock.onRelease(()->{});ScriptingInputLock.acquire("snapshot-test");try{var s=ScriptingInputLock.controlSnapshot();ScriptingInputLock.acquire("other-test");assertFalse(s.current());}finally{ScriptingInputLock.releaseAll();}}
 @Test void unchangedAcquirePreservesExactControl(){ScriptingInputLock.onRelease(()->{});ScriptingInputLock.acquire("snapshot-test");try{var s=ScriptingInputLock.controlSnapshot();ScriptingInputLock.acquire("snapshot-test");assertTrue(s.current());}finally{ScriptingInputLock.releaseAll();}}
}
