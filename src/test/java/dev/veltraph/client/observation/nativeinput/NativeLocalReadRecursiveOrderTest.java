package dev.veltraph.client.observation.nativeinput;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Recursive lifecycle DATA only, never native body/edge/phase/Node/bytes/origin or SDK-depth proof. */
final class NativeLocalReadRecursiveOrderTest {
 private final Object owner=new Object(),root=new Object(),body=new Object();
 @Test void eachRecursiveStepHasUniqueOrderAndUnwindsExactly(){
  Object child1=new Object(),body1=new Object(),child2=new Object(),body2=new Object();
  var outer=new NativeLocalReadOrder(owner,root,body);assertTrue(outer.claim(owner,root,body,body1));assertTrue(outer.bind(child1));
  assertTrue(outer.activeChild(child1,body1));
  var inner=new NativeLocalReadOrder(owner,child1,body1);assertTrue(inner.claim(owner,child1,body1,body2));assertTrue(inner.bind(child2));
  assertTrue(inner.activeChild(child2,body2));assertTrue(inner.returned(child2,child1,body1,true));assertFalse(inner.activeChild(child2,body2));
  assertTrue(outer.activeChild(child1,body1));assertTrue(outer.returned(child1,root,body,true));assertFalse(outer.activeChild(child1,body1));
 }
 @Test void outerOrderCannotBeReusedForGrandchild(){
  var order=new NativeLocalReadOrder(owner,root,body);var child=new Object();var childBody=new Object();
  assertTrue(order.claim(owner,root,body,childBody));assertTrue(order.bind(child));
  assertFalse(order.claim(owner,child,childBody,new Object()));assertFalse(order.activeChild(child,childBody));
 }
 @Test void foreignChildOrBodyNeverMatchesInFlightBinding(){
  var order=new NativeLocalReadOrder(owner,root,body);var child=new Object();var childBody=new Object();
  assertTrue(order.claim(owner,root,body,childBody));assertTrue(order.bind(child));
  assertFalse(order.activeChild(new Object(),childBody));assertFalse(order.activeChild(child,new Object()));assertTrue(order.activeChild(child,childBody));
 }
 @Test void unboundReturnedAndRefusedOrdersCannotOwnAnActiveChild(){
  var order=new NativeLocalReadOrder(owner,root,body);var child=new Object();var childBody=new Object();assertFalse(order.activeChild(child,childBody));
  assertTrue(order.claim(owner,root,body,childBody));assertTrue(order.bind(child));order.refuse();assertFalse(order.activeChild(child,childBody));
 }
 @Test void wrongUnwindCannotRestoreAnotherParentOrRearm(){
  var order=new NativeLocalReadOrder(owner,root,body);var child=new Object();var childBody=new Object();assertTrue(order.claim(owner,root,body,childBody));assertTrue(order.bind(child));
  assertFalse(order.returned(child,new Object(),body,true));assertFalse(order.returned(child,root,body,true));assertFalse(order.activeChild(child,childBody));
 }
 @Test void innerErrorRefusesItsOwnOrderAndCannotTurnNormal(){
  var order=new NativeLocalReadOrder(owner,root,body);var child=new Object();var childBody=new Object();assertTrue(order.claim(owner,root,body,childBody));assertTrue(order.bind(child));
  assertFalse(order.returned(child,root,body,false));assertFalse(order.returned(child,root,body,true));assertFalse(order.activeChild(child,childBody));
 }
 @Test void admittedDataChainCanRepresentEightUniqueStepsWithoutChangingSDKGuard(){
  var orders=new NativeLocalReadOrder[8];var parents=new Object[8];var bodies=new Object[8];var children=new Object[8];var childBodies=new Object[8];
  for(int i=0;i<8;i++){parents[i]=i==0?root:children[i-1];bodies[i]=i==0?body:childBodies[i-1];children[i]=new Object();childBodies[i]=new Object();
   orders[i]=new NativeLocalReadOrder(owner,parents[i],bodies[i]);assertTrue(orders[i].claim(owner,parents[i],bodies[i],childBodies[i]));assertTrue(orders[i].bind(children[i]));}
  for(int i=7;i>=0;i--){assertTrue(orders[i].activeChild(children[i],childBodies[i]));assertTrue(orders[i].returned(children[i],parents[i],bodies[i],true));assertFalse(orders[i].activeChild(children[i],childBodies[i]));}
 }
 @Test void threeLevelDataRestoresOnlyItsImmediateParent(){
  Object a=new Object(),ab=new Object(),b=new Object(),bb=new Object(),c=new Object(),cb=new Object();
  var one=new NativeLocalReadOrder(owner,root,body);assertTrue(one.claim(owner,root,body,ab));assertTrue(one.bind(a));
  var two=new NativeLocalReadOrder(owner,a,ab);assertTrue(two.claim(owner,a,ab,bb));assertTrue(two.bind(b));
  var three=new NativeLocalReadOrder(owner,b,bb);assertTrue(three.claim(owner,b,bb,cb));assertTrue(three.bind(c));
  assertTrue(three.returned(c,b,bb,true));assertTrue(two.activeChild(b,bb));assertTrue(one.activeChild(a,ab));
  assertTrue(two.returned(b,a,ab,true));assertTrue(one.returned(a,root,body,true));
 }
}
