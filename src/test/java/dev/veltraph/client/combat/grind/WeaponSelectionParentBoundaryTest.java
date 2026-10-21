package dev.veltraph.client.combat.grind;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Exact pure boundary invoked by actual native consumer, not a generic Effects substitute. */
class WeaponSelectionParentBoundaryTest {
 @Test void exactDeclaredAndActualRootParentReachesPrivateNativeLeaf(){Object parent=new Object();int[] calls={0};assertTrue(WeaponSelectionParentBoundary.active(parent,()->parent,()->parent,()->{calls[0]++;return true;}));assertEquals(1,calls[0]);}
 @Test void unrelatedCurrentRootCannotBorrowDeclaredParent(){Object expected=new Object(),other=new Object();int[] effects={0};assertFalse(WeaponSelectionParentBoundary.active(expected,()->expected,()->other,()->{effects[0]++;return true;}));assertEquals(0,effects[0]);}
 @Test void wrongDeclaredSupplierCannotSeedCurrentRootRelationship(){Object captured=new Object(),foreign=new Object();assertFalse(WeaponSelectionParentBoundary.active(captured,()->foreign,()->captured,()->true));}
 @Test void ownerReadChangingDeclaredBindingCannotSpendEarlierValue(){Object captured=new Object(),other=new Object();var declared=new AtomicReference<Object>(captured);int[] leaf={0};assertFalse(WeaponSelectionParentBoundary.active(captured,declared::get,()->{declared.set(other);return captured;},()->{leaf[0]++;return true;}));assertEquals(0,leaf[0]);}
 @Test void finalAuthorityCallbackSameSlotSetterRevokesAdoption(){Object parent=new Object();var history=new AtomicLong(12);int[] leaf={0};assertFalse(WeaponSelectionParentBoundary.afterFinalCallback(()->{history.addAndGet(2);return true;},()->history.get()==12,parent,()->parent,()->parent,()->{leaf[0]++;return true;}));assertEquals(0,leaf[0]);}
 @Test void finalAuthorityCallbackRootOwnerReplacementRevokesAdoption(){Object parent=new Object();var root=new AtomicReference<Object>(parent);assertFalse(WeaponSelectionParentBoundary.afterFinalCallback(()->{root.set(new Object());return true;},()->true,parent,()->parent,root::get,()->true));}
 @Test void lateLeafHistoryMutationCannotReturnSuccessfulAdoption(){Object parent=new Object();var history=new AtomicLong(12);assertFalse(WeaponSelectionParentBoundary.afterFinalCallback(()->true,()->history.get()==12,parent,()->parent,()->parent,()->{history.addAndGet(2);return true;}));}
 @Test void honestNormalCallbackHistoryAndPrivateSourceAdoptWithoutRefresh(){Object parent=new Object();var history=new AtomicLong(12);assertTrue(WeaponSelectionParentBoundary.afterFinalCallback(()->true,()->history.get()==12,parent,()->parent,()->parent,()->true));assertEquals(12,history.get());}
}
