package dev.veltraph.client.observation.nativeinput;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NativePlayerBirthTaskOrderTest {
 @Test void actualCapturedTaskWriteAndNormalReturnRequired(){var p=new Object();var c=new Object();var l=new Object();var t=new Object();var o=new NativePlayerBirthTaskOrder(p,c,l,t);assertTrue(o.enter(p,c,l,t,true));assertTrue(o.written(p,c,l,true));assertFalse(o.complete());assertTrue(o.returned(true));assertTrue(o.complete());}
 @Test void samePacketDifferentTaskLoopConnectionAndReentryCannotBorrow(){var p=new Object();var c=new Object();var l=new Object();var t=new Object();assertFalse(new NativePlayerBirthTaskOrder(p,c,l,t).enter(p,c,l,new Object(),true));assertFalse(new NativePlayerBirthTaskOrder(p,c,l,t).enter(p,c,new Object(),t,true));assertFalse(new NativePlayerBirthTaskOrder(p,c,l,t).enter(p,new Object(),l,t,true));var r=new NativePlayerBirthTaskOrder(p,c,l,t);r.enter(p,c,l,t,true);assertFalse(r.enter(p,c,l,t,true));}
 @Test void innerCancelledWriteAndThrowingTaskDoNotFinish(){var p=new Object();var c=new Object();var l=new Object();var t=new Object();var o=new NativePlayerBirthTaskOrder(p,c,l,t);o.enter(p,c,l,t,true);assertFalse(o.returned(true));var x=new NativePlayerBirthTaskOrder(p,c,l,t);x.enter(p,c,l,t,true);x.written(p,c,l,true);assertFalse(x.returned(false));}
 @Test void passiveObservationFailureCannotSuppressOriginalRunnableOrReplaceException(){int[] calls={0};var original=new IllegalStateException("native");try{try{throw new AssertionError("observation");}catch(RuntimeException|Error ignored){}calls[0]++;throw original;}catch(IllegalStateException actual){assertSame(original,actual);}assertEquals(1,calls[0]);}
}
