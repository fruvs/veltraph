package dev.veltraph.client.combat.ghost;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GhostLedgerTest {
    @Test void newNativeContextDropsUnacknowledgedWitnessesButPreservesSessionTotals(){var l=new GhostLedger();var dead=UUID.randomUUID();l.attack(dead,20,0);l.observe(dead,10,true,false,1);l.observe(dead,0,false,false,2);l.onMessage("RARE DROP! Sorrow");l.beginPickup("Sorrow",0);assertTrue(l.pickup("Sorrow",1));var stale=UUID.randomUUID();l.attack(stale,20,3);l.beginPickup("Sorrow",1);l.clearNativeContext();l.observe(stale,0,false,false,4);assertFalse(l.pickup("Sorrow",2));assertEquals(1,l.kills());assertEquals(1,l.sorrowNotices());assertEquals(1,l.collected("Sorrow"));}
    @Test void anUnattackedDeathDoesNotClaimCredit(){var l=new GhostLedger();l.observe(UUID.randomUUID(),0,false,false,1000);assertEquals(0,l.kills());}
    @Test void anIssuedButUnacknowledgedHitDoesNotClaimDeath(){var l=new GhostLedger();var id=UUID.randomUUID();l.attack(id,20,0);l.observe(id,20,true,false,1000);l.observe(id,0,false,false,3000);assertEquals(0,l.kills());}
    @Test void ownedHealthDropThenDeathCountsOnce(){var l=new GhostLedger();var id=UUID.randomUUID();l.attack(id,20,0);l.observe(id,14,true,false,100);l.observe(id,0,false,false,500);l.observe(id,0,false,false,600);assertEquals(1,l.kills());}
    @Test void protectionAndWaveClearPendingAttribution(){var l=new GhostLedger();var id=UUID.randomUUID();l.attack(id,20,0);l.observe(id,0,false,true,100);assertEquals(0,l.kills());l.attack(id,20,200);l.wave();l.observe(id,0,false,false,300);assertEquals(0,l.kills());assertEquals(1,l.waves());}
    @Test void receiptIsSeparateFromPhysicalPickup(){var l=new GhostLedger();l.onMessage("RARE DROP! Sorrow");assertEquals(1,l.sorrowNotices());assertEquals(0,l.collected("Sorrow"));l.beginPickup("Sorrow",2);assertFalse(l.pickup("Sorrow",2));assertTrue(l.pickup("Sorrow",3));assertEquals(1,l.collected("Sorrow"));}
}
