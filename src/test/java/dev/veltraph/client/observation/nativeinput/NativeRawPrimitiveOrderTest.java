package dev.veltraph.client.observation.nativeinput;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NativeRawPrimitiveOrderTest {
 @Test void peerCanPollPendingInsertBeforeAddReturnButNotAuthorizeIt(){
  Object writer=new Object(),peer=new Object(),queue=new Object(),out=new Object(),wrapper=new Object(),contents=new Object();
  var w=new NativeRawPrimitiveOrder.LocalWrite(writer,peer,queue,out);w.current(out,wrapper);w.retained(wrapper,wrapper);
  var pending=w.prepare(queue,wrapper,contents,0,new byte[]{1,2});
  assertNotNull(pending.poll(peer,queue,wrapper));assertFalse(pending.complete());
  pending.additionReturned(true,true);assertFalse(pending.complete());w.finish(true);assertTrue(pending.complete());
 }
 @Test void failedAddPoisonsAlreadyPolledPendingChild(){
  Object writer=new Object(),peer=new Object(),queue=new Object(),out=new Object(),wrapper=new Object(),contents=new Object();
  var w=new NativeRawPrimitiveOrder.LocalWrite(writer,peer,queue,out);w.current(out,wrapper);w.retained(wrapper,wrapper);
  var pending=w.prepare(queue,wrapper,contents,0,new byte[]{1,2});assertNotNull(pending.poll(peer,queue,wrapper));
  pending.additionReturned(false,true);w.finish(true);assertFalse(pending.complete());assertFalse(w.complete());
 }
 @Test void outerNormalReturnCannotSubstituteForMissingAddClosure(){
  Object writer=new Object(),peer=new Object(),queue=new Object(),out=new Object(),wrapper=new Object(),contents=new Object();
  var w=new NativeRawPrimitiveOrder.LocalWrite(writer,peer,queue,out);w.current(out,wrapper);w.retained(wrapper,wrapper);
  var pending=w.prepare(queue,wrapper,contents,0,new byte[]{1});w.finish(true);assertFalse(pending.complete());assertFalse(w.complete());
 }
 @Test void normalPrimitiveExactTargetAndCountConserveImmutableBytes(){Object c=new Object(),b=new Object(),s=new Object();var r=new NativeRawPrimitiveOrder.Read(c,b,s,12);byte[] expected={1,2,3};Object op=r.enter(s,b);r.returned(op,3,expected,true);expected[0]=9;r.finish(3,true);var got=r.take(c,b,3);assertNotNull(got);assertEquals(12,got.start);assertArrayEquals(new byte[]{1,2,3},got.bytes());var copy=got.bytes();copy[0]=8;assertArrayEquals(new byte[]{1,2,3},got.bytes());assertNull(r.take(c,b,3));}
 @Test void virtualPositiveWithoutActualPrimitiveIsRefused(){Object c=new Object(),b=new Object();var r=new NativeRawPrimitiveOrder.Read(c,b,new Object(),0);r.finish(3,true);assertNull(r.take(c,b,3));}
 @Test void foreignPrimitiveOrBufferCannotSupplyReceipt(){Object c=new Object(),b=new Object(),s=new Object();var r=new NativeRawPrimitiveOrder.Read(c,b,s,0);assertNull(r.enter(new Object(),b));r.finish(2,true);assertNull(r.take(c,b,2));var q=new NativeRawPrimitiveOrder.Read(c,b,s,0);assertNull(q.enter(s,new Object()));q.finish(2,true);assertNull(q.take(c,b,2));}
 @Test void duplicatePrimitiveAndWrongCountRefuseWholeRead(){Object c=new Object(),b=new Object(),s=new Object();var r=new NativeRawPrimitiveOrder.Read(c,b,s,0);var op=r.enter(s,b);r.returned(op,2,new byte[]{1,2},true);assertNull(r.enter(s,b));r.finish(2,true);assertNull(r.take(c,b,2));var q=new NativeRawPrimitiveOrder.Read(c,b,s,0);var p=q.enter(s,b);q.returned(p,2,new byte[]{1,2},true);q.finish(3,true);assertNull(q.take(c,b,3));}
 @Test void primitiveFailureAndOuterFailureNeverComplete(){Object c=new Object(),b=new Object(),s=new Object();var r=new NativeRawPrimitiveOrder.Read(c,b,s,0);var op=r.enter(s,b);r.returned(op,2,new byte[]{1,2},false);r.finish(2,true);assertNull(r.take(c,b,2));var q=new NativeRawPrimitiveOrder.Read(c,b,s,0);var p=q.enter(s,b);q.returned(p,2,new byte[]{1,2},true);q.finish(2,false);assertNull(q.take(c,b,2));}
 @Test void localActualInsertMayBePolledBeforeWholeNormalWriteClosure(){Object w=new Object(),p=new Object(),q=new Object(),o=new Object(),m=new Object(),b=new Object();var x=new NativeRawPrimitiveOrder.LocalWrite(w,p,q,o);x.current(o,m);x.retained(m,m);byte[] bytes={4,5};var i=x.insert(q,m,b,7,bytes,true);bytes[0]=9;assertNotNull(i);assertTrue(i.viable());assertFalse(i.complete());var r=i.poll(p,q,m);assertArrayEquals(new byte[]{4,5},r.bytes());assertNull(i.poll(p,q,m));x.current(o,null);x.finish(true);assertTrue(i.complete());}
 @Test void directQueueAppendHasNoCurrentRetainProducer(){Object w=new Object(),p=new Object(),q=new Object(),o=new Object();var x=new NativeRawPrimitiveOrder.LocalWrite(w,p,q,o);assertNull(x.insert(q,new Object(),new Object(),0,new byte[]{1},true));x.finish(true);assertFalse(x.complete());}
 @Test void foreignQueuePeerAndAbnormalWriteCannotCertifyLocal(){Object w=new Object(),p=new Object(),q=new Object(),o=new Object(),m=new Object();var x=new NativeRawPrimitiveOrder.LocalWrite(w,p,q,o);x.current(o,m);x.retained(m,m);var i=x.insert(q,m,new Object(),0,new byte[]{1},true);assertNull(i.poll(new Object(),q,m));assertNull(i.poll(p,new Object(),m));assertNotNull(i.poll(p,q,m));x.finish(false);assertFalse(i.viable());assertFalse(i.complete());}
 @Test void failedRetainOrAddPoisonsAllIssuedLocalReceipts(){Object w=new Object(),p=new Object(),q=new Object(),o=new Object(),m=new Object();var x=new NativeRawPrimitiveOrder.LocalWrite(w,p,q,o);x.current(o,m);x.retained(m,new Object());assertNull(x.insert(q,m,new Object(),0,new byte[]{1},true));x.finish(true);assertFalse(x.complete());var y=new NativeRawPrimitiveOrder.LocalWrite(w,p,q,o);y.current(o,m);y.retained(m,m);assertNull(y.insert(q,m,new Object(),0,new byte[]{1},false));y.finish(true);assertFalse(y.complete());}
}
