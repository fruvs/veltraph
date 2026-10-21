package dev.veltraph.client.mining.vein;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.mining.vein.VeinAdmissionTransaction.*;
class VeinPublicationBudgetTest {
 @Test void actualLateNativeSampleYieldsWithoutAdmission(){assertEquals(PublicationResult.YIELD,publicationResult(true,1528239026L,1528250938L,10000000L,18436833L,96));}
 @Test void originalDeadlineCannotBeExtendedByYield(){assertEquals(PublicationResult.YIELD,publicationResult(true,99,100,0,8436833,96));assertEquals(PublicationResult.REVOKE,publicationResult(true,100,100,0,8436833,96));}
 @Test void mismatchNeverBecomesBudgetYield(){assertEquals(PublicationResult.REVOKE,publicationResult(false,10,100,0,8436833,96));}
 @Test void wholeTransactionCountCannotBeResetToGainPermission(){assertEquals(PublicationResult.YIELD,publicationResult(true,10,100,0,1000,4097));assertEquals(PublicationResult.YIELD,publicationResult(true,11,100,0,8436833,1));}
 @Test void newOrdinaryTransactionMustFullyReproveWithinBudget(){assertEquals(PublicationResult.YIELD,publicationResult(true,10,100,0,8436833,96));assertEquals(PublicationResult.ADMIT,publicationResult(true,20,100,10000000,11000000,96));}
 @Test void boundaryAndClockRollbackRemainExact(){assertEquals(PublicationResult.ADMIT,publicationResult(true,10,100,0,4000000,4096));assertEquals(PublicationResult.REVOKE,publicationResult(true,10,100,1000,999,96));}
}
