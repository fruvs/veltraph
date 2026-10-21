package dev.veltraph.client.observation.nativeinput;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NativePlayerBirthAckOrderTest {
 @Test void actualPositionAndNativeAckBodyRequired(){var p=new Object();var a=new Object();var o=new NativePlayerBirthAckOrder(p);o.position(p,true);o.ack(a,true);o.body(a,false);o.write(a,true);o.bodyReturned(a,true);assertFalse(o.complete());o.returned(a,true);assertTrue(o.complete());}
 @Test void cancelledSendWrapperAndWrongAckCannotProveInit(){var p=new Object();var a=new Object();var o=new NativePlayerBirthAckOrder(p);o.position(p,true);o.ack(a,true);assertFalse(o.returned(a,true));var f=new NativePlayerBirthAckOrder(p);f.position(p,true);assertFalse(f.ack(a,false));}
 @Test void directNativeBodyWithoutActualWriteRefuses(){var p=new Object();var a=new Object();var o=new NativePlayerBirthAckOrder(p);o.position(p,true);o.ack(a,true);o.body(a,false);assertFalse(o.bodyReturned(a,true));}
 @Test void scheduledNormalReturnIsSubmissionOnlyAndNoReplay(){var p=new Object();var a=new Object();var o=new NativePlayerBirthAckOrder(p);o.position(p,true);o.ack(a,true);o.body(a,true);o.bodyReturned(a,true);o.returned(a,true);assertTrue(o.complete());assertFalse(o.returned(a,true));}
}
