package dev.veltraph.client.observation.nativeinput;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.observation.nativeinput.NativeOpcodeReturnTrace.Opcode.*;
import java.util.List;
final class NativeOpcodeReturnTraceTest {
 @Test void actualEntityTurnOrderRetainsOneYawAndTwoPitchIdentities(){var t=new NativeOpcodeReturnTrace(List.of(PITCH_SET,YAW_SET,PITCH_SET));var receiver=new Object();
  for(var op:List.of(PITCH_SET,YAW_SET,PITCH_SET)){var actual=t.begin(op,receiver,123);assertSame(actual,t.claim(op,receiver,123));t.normalReturn(actual,true);}assertTrue(t.finish(true));assertEquals(1,t.returned(YAW_SET).size());assertEquals(2,t.returned(PITCH_SET).size());assertNotSame(t.returned(PITCH_SET).get(0),t.returned(PITCH_SET).get(1));}
 @Test void normalKeySetterRequiresActualClaim(){var t=new NativeOpcodeReturnTrace(List.of(KEY_SET));var receiver=new Object();var a=t.begin(KEY_SET,receiver,1);t.normalReturn(a,true);assertFalse(t.finish(true));}
 @Test void failedCallbackCannotQualifyReturnedSetter(){var t=new NativeOpcodeReturnTrace(List.of(KEY_SET));var receiver=new Object();var a=t.begin(KEY_SET,receiver,1);assertSame(a,t.claim(KEY_SET,receiver,1));t.normalReturn(a,false);assertFalse(t.finish(true));}
 @Test void noOpWithoutNativeSetterCannotRenew(){var t=new NativeOpcodeReturnTrace(List.of(KEY_SET));assertFalse(t.finish(true));}
 @Test void wrongAxisCannotBorrowPitchToken(){var t=new NativeOpcodeReturnTrace(List.of(PITCH_SET));var receiver=new Object();t.begin(PITCH_SET,receiver,1);assertNull(t.claim(YAW_SET,receiver,1));assertFalse(t.finish(true));}
 @Test void sameValueForeignReceiverCannotBorrow(){var t=new NativeOpcodeReturnTrace(List.of(KEY_SET));t.begin(KEY_SET,new Object(),1);assertNull(t.claim(KEY_SET,new Object(),1));assertFalse(t.finish(true));}
 @Test void setterValueMustBeExact(){var t=new NativeOpcodeReturnTrace(List.of(PITCH_SET));var receiver=new Object();t.begin(PITCH_SET,receiver,Float.floatToIntBits(1f));assertNull(t.claim(PITCH_SET,receiver,Float.floatToIntBits(2f)));assertFalse(t.finish(true));}
 @Test void secondClaimIsNotASecondSetter(){var t=new NativeOpcodeReturnTrace(List.of(KEY_SET));var receiver=new Object();var a=t.begin(KEY_SET,receiver,1);assertSame(a,t.claim(KEY_SET,receiver,1));assertNull(t.claim(KEY_SET,receiver,1));t.normalReturn(a,true);assertFalse(t.finish(true));}
 @Test void reentryCannotRenewOriginalTrace(){var t=new NativeOpcodeReturnTrace(List.of(KEY_SET));var receiver=new Object();var a=t.begin(KEY_SET,receiver,1);assertNull(t.begin(KEY_SET,receiver,1));t.normalReturn(a,true);assertFalse(t.finish(true));}
 @Test void duplicateReturnCannotAddCredit(){var t=new NativeOpcodeReturnTrace(List.of(KEY_SET));var receiver=new Object();var a=t.begin(KEY_SET,receiver,1);t.claim(KEY_SET,receiver,1);t.normalReturn(a,true);t.normalReturn(a,true);assertFalse(t.finish(true));}
 @Test void foreignSameShapeTraceCannotReturnOriginalAttempt(){var first=new NativeOpcodeReturnTrace(List.of(KEY_SET));var second=new NativeOpcodeReturnTrace(List.of(KEY_SET));var receiver=new Object();var a=first.begin(KEY_SET,receiver,1);second.begin(KEY_SET,receiver,1);second.normalReturn(a,true);assertFalse(second.finish(true));}
 @Test void closedTraceCannotReplay(){var t=new NativeOpcodeReturnTrace(List.of(KEY_SET));var receiver=new Object();var a=t.begin(KEY_SET,receiver,1);t.claim(KEY_SET,receiver,1);t.normalReturn(a,true);assertTrue(t.finish(true));assertNull(t.begin(KEY_SET,receiver,1));assertFalse(t.finish(true));}
}
