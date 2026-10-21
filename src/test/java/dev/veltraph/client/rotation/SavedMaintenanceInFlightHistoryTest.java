package dev.veltraph.client.rotation;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
/** Real history primitive tests; synthetic objects here never represent native permission. */
class SavedMaintenanceInFlightHistoryTest {
 @Test void directPendingFieldRequiresExactRealOuterEvent(){var v=new NormalIdleInputVersion();var old=v.snapshot();Object pending=new Object();v.begin(pending,true);assertTrue(v.inFlightOnly(old,List.of(),pending,1));assertFalse(v.inFlightOnly(old,List.of(),new Object(),1));}
 @Test void rawNoopSetterCannotBorrowPendingIdentity(){var v=new NormalIdleInputVersion();var old=v.snapshot();v.begin(null,true);assertFalse(v.inFlightOnly(old,List.of(),new Object(),1));}
 @Test void genuineToggleSuperclassDepthMustBeExplicitlySelected(){var v=new NormalIdleInputVersion();var old=v.snapshot();Object pending=new Object();v.begin(pending,true);v.begin(null,true);assertFalse(v.inFlightOnly(old,List.of(),pending,1));assertTrue(v.inFlightOnly(old,List.of(),pending,2));}
 @Test void thirdNestedCallbackNeverMatchesActualSuperEdge(){var v=new NormalIdleInputVersion();var old=v.snapshot();Object pending=new Object();v.begin(pending,true);v.begin(null,true);v.begin(null,true);assertFalse(v.inFlightOnly(old,List.of(),pending,2));}
 @Test void earlierForeignAbaRemainsVisibleBeforeField(){var v=new NormalIdleInputVersion();var old=v.snapshot();v.begin(null,true);v.end(false);v.begin(null,true);v.end(false);Object pending=new Object();v.begin(pending,true);assertFalse(v.inFlightOnly(old,List.of(),pending,1));}
 @Test void returnedOwnPrefixCanPrecedeOneNewPendingNativeCall(){var v=new NormalIdleInputVersion();var old=v.snapshot();Object first=new Object(),pending=new Object();v.begin(first,true);v.end(false);v.begin(pending,true);assertTrue(v.inFlightOnly(old,List.of(first),pending,1));assertFalse(v.inFlightOnly(old,List.of(),pending,1));}
 @Test void normalReturnUsesExistingCompletedPrefixNotInFlightClaim(){var v=new NormalIdleInputVersion();var old=v.snapshot();Object pending=new Object();v.begin(pending,true);v.end(false);assertFalse(v.inFlightOnly(old,List.of(),pending,1));assertTrue(v.advancedOnly(old,v.snapshot(),List.of(pending)));}
 @Test void anotherVersionCannotSupplyOriginalContext(){var a=new NormalIdleInputVersion();var b=new NormalIdleInputVersion();var old=a.snapshot();Object pending=new Object();b.begin(pending,true);assertFalse(b.inFlightOnly(old,List.of(),pending,1));}
 @Test void ordinaryCanonicalEventDoesNotBecomePrivateSavedPending(){var v=new NormalIdleInputVersion();var old=v.snapshot();Object pending=new Object();v.begin(pending,new Object(),true);assertFalse(v.inFlightOnly(old,List.of(),pending,1));}
 @Test void offThreadNativeAttemptPermanentlyRefusesFinalField(){var v=new NormalIdleInputVersion();var old=v.snapshot();Object pending=new Object();v.begin(pending,false);assertFalse(v.inFlightOnly(old,List.of(),pending,1));}
}
