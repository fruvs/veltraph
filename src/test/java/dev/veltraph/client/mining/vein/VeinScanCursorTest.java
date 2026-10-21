package dev.veltraph.client.mining.vein;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class VeinScanCursorTest {
 @Test void incompleteProbeRetainsIndexAndCannotPublish(){var c=new VeinScanCursor<String>(2,"scope",2000);assertFalse(c.advance("scope",1000,false,"bad",1));assertEquals(0,c.index());assertTrue(c.best("scope",1000).isEmpty());}
 @Test void multipleFramesPreserveBestAcrossEntireScan(){var c=new VeinScanCursor<String>(3,"scope",2000);c.advance("scope",1000,true,"first",8);c.advance("scope",1050,true,"best",2);assertTrue(c.best("scope",1050).isEmpty());c.advance("scope",1100,true,"last",4);assertEquals("best",c.best("scope",1100).orElseThrow().value());}
 @Test void lateHigherPriorityCanWin(){var c=new VeinScanCursor<String>(2,"scope",2000);c.advance("scope",1000,true,"mithril",500);c.advance("scope",1050,true,"titanium",50);assertEquals("titanium",c.best("scope",1100).orElseThrow().value());}
 @Test void scopeChangeRevokesProvisionalCandidate(){var c=new VeinScanCursor<String>(1,"scope",2000);c.advance("scope",1000,true,"best",2);assertTrue(c.best("foreign",1100).isEmpty());}
 @Test void originalDeadlineExpiresWithoutReset(){var c=new VeinScanCursor<String>(2,"scope",2000);c.advance("scope",1950,true,"best",2);assertFalse(c.advance("scope",2000,true,"late",1));assertTrue(c.best("scope",2000).isEmpty());}
 @Test void deterministicEqualCostUsesFirstOrdinal(){var c=new VeinScanCursor<String>(2,"scope",2000);c.advance("scope",1000,true,"first",2);c.advance("scope",1100,true,"second",2);assertEquals("first",c.best("scope",1150).orElseThrow().value());}
 @Test void denseMaximumVantageScanFinishesAcrossSmallBudgets(){var c=new VeinScanCursor<Integer>(8112,"scope",20000);for(int frame=0;!c.complete();frame++){for(int budget=0;budget<17&&!c.complete();budget++){int index=c.index();assertTrue(c.advance("scope",1000+frame,true,index,8112-index));}}assertEquals(8111,c.best("scope",1500).orElseThrow().value());}
 @Test void noCandidateScanCompletesWithEmptyBest(){var c=new VeinScanCursor<String>(2,"scope",2000);c.advance("scope",1000,true,null,0);c.advance("scope",1100,true,null,0);assertTrue(c.complete());assertTrue(c.best("scope",1200).isEmpty());}
 @Test void emptyScanIsCompleteButDoesNotInventBest(){var c=new VeinScanCursor<String>(0,"scope",2000);assertTrue(c.complete());assertTrue(c.best("scope",1200).isEmpty());}
 @Test void oversizedScanRejected(){assertThrows(IllegalArgumentException.class,()->new VeinScanCursor<String>(16385,"scope",2000));}
 @Test void nonfiniteCostCannotPoisonRanking(){var c=new VeinScanCursor<String>(1,"scope",2000);assertThrows(IllegalArgumentException.class,()->c.advance("scope",1000,true,"bad",Double.NaN));assertEquals(0,c.index());}
}
