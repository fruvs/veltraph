package dev.veltraph.client.mining.vein;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
class VeinInventoryAdoptionTest {
 List<Object> inventory(){return new ArrayList<>(Collections.nCopies(36,"same"));}
 static class P implements VeinInventoryAdoption.Permission {List<?> b,a;boolean fresh=true,spent;P(List<?> b,List<?> a){this.b=b;this.a=a;}public boolean fresh(){return fresh&&!spent;}public List<?> before(){return b;}public List<?> after(){return a;}public boolean commit(BooleanSupplier cas){if(!fresh()||!cas.getAsBoolean())return false;spent=true;return true;}}
 @Test void jointWearPickupProjectsOnlyTheirExactCells(){var b=inventory();var w=inventory();w.set(2,"wear");var a=new ArrayList<>(w);a.set(5,"loot");var wp=new P(b,w);var pp=new P(w,a);assertTrue(VeinInventoryAdoption.adopt(b,a,2,wp,pp,()->true,()->true));assertTrue(wp.spent&&pp.spent);}
 @Test void foreignOtherCellCannotHideBehindWear(){var b=inventory();var a=inventory();a.set(2,"wear");a.set(5,"foreign");assertFalse(VeinInventoryAdoption.adopt(b,a,2,new P(b,a),null,()->true,()->true));}
 @Test void pickupCannotChangeTool(){var b=inventory();var a=inventory();a.set(2,"foreign");assertFalse(VeinInventoryAdoption.adopt(b,a,2,null,new P(b,a),()->true,()->true));}
 @Test void callerCasFailureConsumesNeither(){var b=inventory();var w=inventory();w.set(2,"wear");var a=new ArrayList<>(w);a.set(5,"loot");var wp=new P(b,w);var pp=new P(w,a);assertFalse(VeinInventoryAdoption.adopt(b,a,2,wp,pp,()->true,()->false));assertFalse(wp.spent||pp.spent);}
 @Test void stalePickupCannotConsumeWear(){var b=inventory();var w=inventory();w.set(2,"wear");var pp=new P(w,w);pp.fresh=false;var wp=new P(b,w);assertFalse(VeinInventoryAdoption.adopt(b,w,2,wp,pp,()->true,()->true));assertFalse(wp.spent);}
 @Test void scopeRevokedBetweenReviewAndCasConsumesNeither(){var b=inventory();var w=inventory();w.set(2,"wear");var a=new ArrayList<>(w);a.set(5,"loot");var wp=new P(b,w);var pp=new P(w,a);var calls=new java.util.concurrent.atomic.AtomicInteger();assertFalse(VeinInventoryAdoption.adopt(b,a,2,wp,pp,()->calls.incrementAndGet()<3,()->true));assertFalse(wp.spent||pp.spent);}
 @Test void wrongPickupBeforeCannotAdoptCombinedPacket(){var b=inventory();var w=inventory();w.set(2,"wear");var a=new ArrayList<>(w);a.set(5,"loot");var wp=new P(b,w);var pp=new P(b,a);assertFalse(VeinInventoryAdoption.adopt(b,a,2,wp,pp,()->true,()->true));assertFalse(wp.spent||pp.spent);}
 @Test void unchangedBaselineNeedsNoForgedPermission(){var b=inventory();assertTrue(VeinInventoryAdoption.adopt(b,b,2,null,null,()->true,()->true));}
 @Test void unrelatedActualCellRejectsBoth(){var b=inventory();var w=inventory();w.set(2,"wear");var a=new ArrayList<>(w);a.set(8,"foreign");assertFalse(VeinInventoryAdoption.adopt(b,a,2,new P(b,w),null,()->true,()->true));}
}
