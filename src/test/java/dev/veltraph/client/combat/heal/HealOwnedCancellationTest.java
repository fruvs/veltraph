package dev.veltraph.client.combat.heal;

import dev.veltraph.client.config.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealOwnedCancellationTest {
    private static final Object PLAYER=new Object(),WORLD=new Object(),CONNECTION=new Object(),OWNER=new Object();
    private static HealCameraLease lease(long epoch){return new HealCameraLease(PLAYER,WORLD,CONNECTION,OWNER,epoch);}
    @Test void onlyExactSynchronousOwnEpochIncrementCanRenew(){
        assertTrue(HealOwnedCancellation.mayRenew(lease(8),lease(9),true,true,true));
        assertFalse(HealOwnedCancellation.mayRenew(lease(8),lease(10),true,true,true));
        assertFalse(HealOwnedCancellation.mayRenew(lease(8),lease(8),true,true,true));
        assertFalse(HealOwnedCancellation.mayRenew(lease(8),lease(9),false,true,true));
        assertFalse(HealOwnedCancellation.mayRenew(lease(8),lease(9),true,true,false));
    }
    @Test void foreignScopeOrReplacementOwnerCannotBeAdopted(){
        for(var after:new HealCameraLease[]{new HealCameraLease(new Object(),WORLD,CONNECTION,OWNER,9),
                new HealCameraLease(PLAYER,new Object(),CONNECTION,OWNER,9),
                new HealCameraLease(PLAYER,WORLD,new Object(),OWNER,9),
                new HealCameraLease(PLAYER,WORLD,CONNECTION,new Object(),9)})
            assertFalse(HealOwnedCancellation.mayRenew(lease(8),after,true,true,true));
    }
    static final class Port implements HealServices {
        boolean valid=true,loseDuringCancel;int casts,cancels,restores,prepares,discards;
        public boolean available(){return true;}public boolean hasHyperion(){return true;}public double healthPct(){return 10;}
        public boolean clearAbove(){return true;}public boolean custodyValid(){return valid;}
        public void lookDown(){}public void castHyperion(){casts++;}public void restoreLook(){restores++;}public void discardSnapshot(){discards++;}
        public boolean usesSourceReadiness(){return true;}public boolean sourceReady(boolean a,boolean b){return true;}
        public boolean sourceHyperionLookDown(){return true;}public void cancelOwnedHealRotation(){cancels++;if(loseDuringCancel)valid=false;}
        public boolean prepareSourceRepeat(){prepares++;return true;}
    }
    @Test void custodyLostBeforeWatchdogCannotCancelRestoreRepeatOrWriteEnd(){
        var time=new java.util.concurrent.atomic.AtomicLong(100000);var end=new HealSourceEndLedger();var c=new MapConfigService();
        c.set(GhostConfig.GHOST_AUTO_HEAL_ENABLED,true);c.set(GhostConfig.AUTO_HEAL_STACKING,HealStackingMode.SPAM);
        var p=new Port();var h=new CombatAutoHeal(c,p,end,time::get);h.tick(true);h.tick(true);h.tick(true);
        p.valid=false;time.set(100501);h.tick(true);
        assertEquals(1,p.casts);assertEquals(0,p.cancels);assertEquals(0,p.restores);assertEquals(0,p.prepares);assertEquals(0,end.endedAt());
    }
    @Test void custodyLostDuringCancellationCannotWriteEndOrRestoreOrRepeat(){
        var time=new java.util.concurrent.atomic.AtomicLong(100000);var end=new HealSourceEndLedger();var c=new MapConfigService();
        c.set(GhostConfig.GHOST_AUTO_HEAL_ENABLED,true);c.set(GhostConfig.AUTO_HEAL_STACKING,HealStackingMode.SPAM);
        var p=new Port();p.loseDuringCancel=true;var h=new CombatAutoHeal(c,p,end,time::get);h.tick(true);h.tick(true);h.tick(true);
        time.set(100501);h.tick(true);
        assertEquals(1,p.casts);assertEquals(1,p.cancels);assertEquals(0,p.restores);assertEquals(0,p.prepares);assertEquals(0,end.endedAt());
    }
}
