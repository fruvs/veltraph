package dev.veltraph.client.observation.nativeinput;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Lifecycle DATA only. These objects never enter Source.LocalReadBody/Edge/Phase/Node, bytes, Scene, or native authority. */
final class NativeLocalReadOrderTest {
 private final Object owner=new Object(),parent=new Object(),body=new Object(),childBody=new Object(),child=new Object();
 private NativeLocalReadOrder order(){return new NativeLocalReadOrder(owner,parent,body);}
 @Test void normalDataLifecycleIsOneClaimBindAndExactRestoration(){var o=order();assertTrue(o.claim(owner,parent,body,childBody));assertTrue(o.bind(child));assertTrue(o.returned(child,parent,body,true));}
 @Test void wrongOwnerConsumesAttemptAndCannotRetry(){var o=order();assertFalse(o.claim(new Object(),parent,body,childBody));assertFalse(o.claim(owner,parent,body,childBody));assertFalse(o.bind(child));}
 @Test void foreignParentOrBodyCannotClaim(){assertFalse(order().claim(owner,new Object(),body,childBody));assertFalse(order().claim(owner,parent,new Object(),childBody));}
 @Test void missingOrSameChildBodyCannotClaim(){assertFalse(order().claim(owner,parent,body,null));assertFalse(order().claim(owner,parent,body,body));}
 @Test void replayClaimRevokesEvenPreviouslyMatchedData(){var o=order();assertTrue(o.claim(owner,parent,body,childBody));assertFalse(o.claim(owner,parent,body,childBody));assertFalse(o.bind(child));}
 @Test void bindRequiresClaimAndDistinctRealChildData(){assertFalse(order().bind(child));var o=order();assertTrue(o.claim(owner,parent,body,childBody));assertFalse(o.bind(parent));assertFalse(o.bind(child));}
 @Test void duplicateBindRevokes(){var o=order();assertTrue(o.claim(owner,parent,body,childBody));assertTrue(o.bind(child));assertFalse(o.bind(child));assertFalse(o.returned(child,parent,body,true));}
 @Test void foreignRestoredParentOrBodyRejectsUnwind(){var a=order();assertTrue(a.claim(owner,parent,body,childBody));assertTrue(a.bind(child));assertFalse(a.returned(child,new Object(),body,true));var b=order();assertTrue(b.claim(owner,parent,body,childBody));assertTrue(b.bind(child));assertFalse(b.returned(child,parent,new Object(),true));}
 @Test void originalErrorCannotBecomeNormalByRetry(){var o=order();assertTrue(o.claim(owner,parent,body,childBody));assertTrue(o.bind(child));assertFalse(o.returned(child,parent,body,false));assertFalse(o.returned(child,parent,body,true));}
 @Test void repeatedUnwindOrExplicitRefusalCannotSucceed(){var o=order();assertTrue(o.claim(owner,parent,body,childBody));assertTrue(o.bind(child));assertTrue(o.returned(child,parent,body,true));assertFalse(o.returned(child,parent,body,true));var bad=order();bad.refuse();assertFalse(bad.claim(owner,parent,body,childBody));}
 @Test void expiredOrFinishedParentDataCannotStartNewClaim(){var expired=order();expired.refuse();assertFalse(expired.claim(owner,parent,body,childBody));var finished=order();assertTrue(finished.claim(owner,parent,body,childBody));assertTrue(finished.bind(child));assertTrue(finished.returned(child,parent,body,true));assertFalse(finished.claim(owner,parent,body,childBody));}
 @Test void parentIdentityABAAfterFailedAttemptCannotRearm(){var o=order();assertFalse(o.claim(owner,new Object(),body,childBody));assertFalse(o.claim(owner,parent,body,childBody));}
}
