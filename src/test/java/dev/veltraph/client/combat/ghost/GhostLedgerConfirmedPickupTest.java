package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GhostLedgerConfirmedPickupTest {
    @Test void confirmedPartialDeliveryDeltasAggregateOnlyExactRegisteredDrop(){var l=new GhostLedger();l.confirmedPickupUnits("SORROW",2);l.confirmedPickupUnits("SORROW",1);assertEquals(3,l.collected("Sorrow"));assertEquals(3,l.collectedTotal());}
    @Test void invalidOrNonpositiveStatisticsReceiptsCannotBecomePickupCredit(){var l=new GhostLedger();assertThrows(IllegalArgumentException.class,()->l.confirmedPickupUnits("SORROW",0));assertThrows(IllegalArgumentException.class,()->l.confirmedPickupUnits("OTHER",1));assertEquals(0,l.collectedTotal());}
}
