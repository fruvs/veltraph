package dev.veltraph.client.observation.nativeinput;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NativePlayerBirthDecodeOrderTest {
 @Test void actualCodecOutputAddedAndWholeDecodeNormalRequired(){var d=new Object();var b=new Object();var l=new Object();var p=new Object();var o=new NativePlayerBirthDecodeOrder(d,b,l);o.decoded(d,b,p,true,true);o.added(l,p,true);assertFalse(o.take(p));o.finish(true);assertTrue(o.take(p));assertFalse(o.take(p));}
 @Test void syntheticFireDuringRealReadCannotBorrowDifferentDecodedPacket(){var d=new Object();var b=new Object();var l=new Object();var p=new Object();var o=new NativePlayerBirthDecodeOrder(d,b,l);o.decoded(d,b,p,true,true);o.added(l,p,true);o.finish(true);assertFalse(o.take(new Object()));assertTrue(o.take(p));}
 @Test void cancelledCodecWrongListOrOuterDecodeThrowCannotMintOutput(){var d=new Object();var b=new Object();var l=new Object();var p=new Object();var o=new NativePlayerBirthDecodeOrder(d,b,l);assertFalse(o.decoded(d,b,p,false,true));var w=new NativePlayerBirthDecodeOrder(d,b,l);w.decoded(d,b,p,true,true);assertFalse(w.added(new Object(),p,true));var t=new NativePlayerBirthDecodeOrder(d,b,l);t.decoded(d,b,p,true,true);t.added(l,p,true);t.finish(false);assertFalse(t.take(p));}
}
