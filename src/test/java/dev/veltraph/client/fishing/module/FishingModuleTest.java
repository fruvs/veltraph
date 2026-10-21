package dev.veltraph.client.fishing.module;

import dev.veltraph.client.config.MapConfigService;
import dev.veltraph.client.fishing.BobberFacts;
import dev.veltraph.client.fishing.FishingServices;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Light glue tests for the fishing module (the FSM itself is covered by FishingRuntimeTest). */
class FishingModuleTest {

    /** A controllable fake of the MC seam. */
    private static final class FakeServices implements FishingServices {
        boolean available = true;
        boolean rod = true;
        BobberFacts bobber = BobberFacts.none();
        int useRodCount, combatTicks, combatStops, goldenTicks;
        boolean submerged, swimming, combat, golden, returning, obstructionAllowed, worm, wormAllowed;
        boolean trash,trashAllow;String trashFailure="";int trashTicks,trashStops;
        @Override public boolean tickTrash(boolean allow){trashTicks++;trashAllow=allow;return trash;}
        @Override public boolean trashActive(){return trash;}
        @Override public String trashFailure(){return trashFailure;}
        @Override public void stopTrash(){trashStops++;trash=false;}
        boolean guardReady=true;String guardFailure="",guardConfigError="";
        @Override public boolean catchGuardReady(){return guardReady;}
        @Override public String catchGuardFailure(){return guardFailure;}
        @Override public String catchGuardConfigurationError(dev.veltraph.client.config.ConfigService cfg){return guardConfigError;}
        boolean bait;String baitFailure="";int baitTicks;
        @Override public boolean tickBait(boolean allow){baitTicks++;return bait;}
        @Override public boolean baitActive(){return bait;}
        @Override public String baitFailure(){return baitFailure;}
        @Override public void stopBait(){bait=false;}
        int totemTicks;
        boolean totem,totemAllowed;
        @Override public boolean tickTotem(boolean allow) {totemTicks++;totemAllowed=allow;return totem;}
        @Override public boolean totemActive(){return totem;}
        @Override public void stopTotem(){totem=false;}
        int wormStops;
        @Override public boolean tickWormHunting(boolean allow) {wormAllowed=allow;return worm;}
        @Override public String wormStatus() {return worm?"SURFACING":"IDLE";}
        @Override public void stopWormHunting() {wormStops++;worm=false;}
        int returnStops;
        String returnFailure="";
        @Override public boolean tickObstructionReturn(boolean allowed) { obstructionAllowed=allowed;return returning; }
        @Override public void stopObstructionReturn() {returnStops++;returning=false;}
        @Override public String obstructionFailure() {return returnFailure;}
        @Override public boolean goldenFishActive() { return golden; }
        @Override public boolean tickGoldenFish(boolean enabled) { goldenTicks++; return golden; }
        @Override public boolean submerged() { return submerged; }
        @Override public double bubbles() { return 1; }
        @Override public boolean surfaceReachable() { return true; }
        @Override public void setSwimmingUp(boolean value) { swimming=value; }
        @Override public boolean tickCombat() { combatTicks++; return combat; }
        @Override public void stopCombat() { combatStops++; rod=true; }
        @Override public boolean available() {
            return available;
        }
        @Override public boolean hasFishingRod() {
            return rod;
        }
        @Override public BobberFacts bobber() {
            return bobber;
        }
        @Override public void useRod() {
            useRodCount++;
        }
    }

    private FishingModule module(FakeServices s) {
        return new FishingModule(s, new MapConfigService(), new Random(0));
    }

    @Test
    void preStartCheckRequiresARod() {
        FakeServices s = new FakeServices();
        s.rod = false;
        assertTrue(module(s).preStartCheck().isPresent());
        s.rod = true;
        assertFalse(module(s).preStartCheck().isPresent());
    }

    @Test
    void slugGuardPreventsBothCatchAndObstructionBeforeItsPerHookThreshold() {
        var s=new FakeServices();var cfg=new MapConfigService();
        cfg.set(dev.veltraph.client.config.FishingConfig.FISHING_MIN_CATCH_SECONDS,0.0);
        var m=new FishingModule(s,cfg,new Random(0));m.setEnabled(true);m.onTick();
        s.bobber=new BobberFacts(true,0,true,true);s.guardReady=false;m.onTick();
        assertEquals(1,s.useRodCount);assertFalse(s.obstructionAllowed);
        assertTrue(m.diagnostics().contains("catches=0"));
        s.guardReady=true;m.onTick();assertEquals(2,s.useRodCount);assertTrue(s.obstructionAllowed);
    }
    @Test
    void badSlugContractDeniesStartAndReceivedStatFailureStopsSession() {
        var s=new FakeServices();var m=module(s);s.guardConfigError="Missing exact tab label";
        assertEquals("Missing exact tab label",m.preStartCheck().orElseThrow());
        s.guardConfigError="";m.setEnabled(true);m.onTick();
        s.guardFailure="Ambiguous Slug Fish stat";m.onTick();
        assertFalse(m.isEnabled());assertEquals(1,s.useRodCount);
        assertTrue(m.diagnostics().contains("Ambiguous Slug Fish stat"));
    }

    @Test
    void trashOwnerPreemptsCoreRodAndFailureStopsWithoutResuming() {
        var s=new FakeServices();var m=module(s);m.setEnabled(true);m.onTick();s.trash=true;m.onTick();
        assertEquals(1,s.useRodCount);assertFalse(s.trashAllow);assertTrue(m.isEnabled());
        s.trashFailure="Missing independent Fishing trash inventory receipt";m.onTick();
        assertFalse(m.isEnabled());assertEquals(1,s.useRodCount);assertEquals(1,s.trashStops);
        assertTrue(m.diagnostics().contains("independent Fishing trash inventory receipt"));
    }
    @Test
    void corePendingReelCannotHandOffToTrashUntilServerRemoval() {
        var s=new FakeServices();var cfg=new MapConfigService();cfg.set(dev.veltraph.client.config.FishingConfig.FISHING_MIN_CATCH_SECONDS,0d);
        cfg.set(dev.veltraph.client.config.FishingConfig.FISHING_CATCH_DELAY,0d);
        var m=new FishingModule(s,cfg,new Random(0));m.setEnabled(true);m.onTick();
        s.bobber=new BobberFacts(true,0,false,true);m.onTick();int calls=s.trashTicks;m.onTick();
        assertEquals(calls,s.trashTicks);assertEquals(2,s.useRodCount);
        s.bobber=BobberFacts.none();m.onTick();assertTrue(s.trashTicks>calls);
    }

    @Test
    void firstTickCastsWhenEnabled() {
        FakeServices s = new FakeServices();
        FishingModule m = module(s);
        m.setEnabled(true);
        m.onTick();
        assertEquals(1, s.useRodCount, "first enabled tick should cast the rod");
    }

    @Test
    void doesNothingWhenNeverEnabled() {
        FakeServices s = new FakeServices();
        FishingModule m = module(s);
        m.onTick(); // runtime never built
        assertEquals(0, s.useRodCount);
    }

    @Test
    void doesNotCastWithoutARod() {
        FakeServices s = new FakeServices();
        s.rod = false;
        FishingModule m = module(s);
        m.setEnabled(true);
        m.onTick();
        assertEquals(0, s.useRodCount, "no rod -> no cast");
    }

    @Test
    void wrongRodServerRejectionStopsAndReleasesActions() {
        var s = new FakeServices(); var m = module(s); m.setEnabled(true);
        m.onServerMessage("[MVP+] Somebody: This Fishing Rod does not work in lava!");
        assertTrue(m.isEnabled());
        m.onServerMessage("§cThis Fishing Rod does not work in lava!");
        assertFalse(m.isEnabled());
        assertTrue(m.diagnostics().contains("Wrong Rod"));
        m.onTick(); assertEquals(0, s.useRodCount);
        assertFalse(s.swimming);
    }

    @Test
    void emergencySurfacingPreemptsCombatEvenWithSwordHeld() {
        var s = new FakeServices(); s.submerged=true; s.rod=false; s.combat=true;
        var config = new MapConfigService();
        config.set(dev.veltraph.client.config.FishingConfig.FISHING_SURFACE_ON_LOW_BUBBLES, true);
        var m = new FishingModule(s, config, new Random(0)); m.setEnabled(true);
        m.onTick(); m.onTick();
        assertTrue(m.isEnabled()); assertTrue(s.swimming);
        assertEquals(0, s.combatTicks); assertEquals(1, s.combatStops);
        assertEquals(0, s.useRodCount);
        s.submerged=false; m.onTick();
        assertFalse(s.swimming); assertEquals(1, s.combatTicks);
        m.setEnabled(false); assertFalse(s.swimming);
    }

    @Test
    void goldenReturnAdoptsExistingHookRatherThanTreatingItAsAnOldCast() {
        var s=new FakeServices(); var m=module(s);m.setEnabled(true);m.onTick();
        s.golden=true;m.onTick();
        s.golden=false;s.bobber=new BobberFacts(true,0,false,true);m.onTick();
        assertEquals(1,s.useRodCount,"Subflow hook should be adopted without rod use");
        assertTrue(m.diagnostics().contains("state=WAITING_BITE"));
        m.onTick();assertEquals(1,s.useRodCount,"Fresh hook cannot inherit old cast age");
    }

    @Test
    void pendingCoreReelPreventsGoldenFromIssuingAnotherRodAction() {
        var s=new FakeServices();var cfg=new MapConfigService();
        cfg.set(dev.veltraph.client.config.FishingConfig.FISHING_MIN_CATCH_SECONDS,0.0);
        cfg.set(dev.veltraph.client.config.FishingConfig.FISHING_CATCH_DELAY,0.0);
        var m=new FishingModule(s,cfg,new Random(0));m.setEnabled(true);m.onTick();
        s.bobber=new BobberFacts(true,0,false,true);m.onTick();
        assertEquals(2,s.useRodCount);int calls=s.goldenTicks;
        s.golden=true;m.onTick();
        assertEquals(calls,s.goldenTicks,"Pending reel must retain ownership before Golden Fish starts");
        s.bobber=BobberFacts.none();m.onTick();assertEquals(calls+1,s.goldenTicks);
    }

    @Test
    void obstructionReturnPreventsCatchAccountingAndResumesWithFreshHook() {
        var s=new FakeServices();var cfg=new MapConfigService();
        cfg.set(dev.veltraph.client.config.FishingConfig.FISHING_MIN_CATCH_SECONDS,0.0);
        var m=new FishingModule(s,cfg,new Random(0));m.setEnabled(true);m.onTick();
        s.bobber=new BobberFacts(true,0,true,true);s.returning=true;m.onTick();
        assertTrue(s.obstructionAllowed);assertEquals(1,s.useRodCount);assertTrue(m.diagnostics().contains("catches=0"));
        s.returning=false;m.onTick();assertEquals(1,s.useRodCount);
        m.onTick();assertEquals(2,s.useRodCount);assertTrue(m.diagnostics().contains("catches=1"));
    }

    @Test
    void lowAirPreemptsReturnAndPathFailureStopsFishing() {
        var s=new FakeServices();var cfg=new MapConfigService();
        cfg.set(dev.veltraph.client.config.FishingConfig.FISHING_SURFACE_ON_LOW_BUBBLES,true);
        var m=new FishingModule(s,cfg,new Random(0));m.setEnabled(true);s.returning=true;m.onTick();
        s.submerged=true;m.onTick();assertTrue(s.swimming);assertFalse(s.returning);assertEquals(1,s.returnStops);
        s.submerged=false;s.returnFailure="Fishing return path blocked";m.onTick();
        assertFalse(m.isEnabled());assertTrue(m.diagnostics().contains("path blocked"));
    }

    @Test
    void goldenEncounterRetainsOwnershipBeforeItsFirstTick() {
        var s=new FakeServices();var cfg=new MapConfigService();
        cfg.set(dev.veltraph.client.config.FishingConfig.FISHING_MIN_CATCH_SECONDS,0.0);
        var m=new FishingModule(s,cfg,new Random(0));m.setEnabled(true);m.onTick();
        s.bobber=new BobberFacts(true,0,true,true);s.golden=true;m.onTick();
        assertFalse(s.obstructionAllowed,"An announced Golden Fish already owns this hooked entity");
        assertEquals(1,s.useRodCount);
    }

    @Test
    void wormOwnsSwordDuringRecoveryAndAdoptsItsHookAfterReturning() {
        var s=new FakeServices();var m=module(s);m.setEnabled(true);m.onTick();
        s.worm=true;s.rod=false;s.submerged=true;m.onTick();
        assertTrue(m.isEnabled());assertFalse(s.wormAllowed);assertEquals(1,s.useRodCount);
        assertEquals(1,s.combatTicks,"Ground killer cannot take the underwater controller's input");
        s.worm=false;s.rod=true;s.bobber=new BobberFacts(true,0,false,true);m.onTick();
        assertEquals(1,s.useRodCount);assertTrue(m.diagnostics().contains("state=WAITING_BITE"));
        m.setEnabled(false);assertEquals(1,s.wormStops);
    }

    @Test
    void pendingCoreReelAndGoldenAnnouncementForbidStartingUnderwaterFlow() {
        var s=new FakeServices();var cfg=new MapConfigService();
        cfg.set(dev.veltraph.client.config.FishingConfig.FISHING_MIN_CATCH_SECONDS,0.0);
        var m=new FishingModule(s,cfg,new Random(0));m.setEnabled(true);m.onTick();
        s.bobber=new BobberFacts(true,0,false,true);m.onTick();m.onTick();assertFalse(s.wormAllowed);
        s.bobber=BobberFacts.none();s.golden=true;m.onTick();assertFalse(s.wormAllowed);
    }

    @Test
    void optInUnderwaterHuntingRequiresAnUnderwaterStartPosition() {
        var s=new FakeServices();var cfg=new MapConfigService();
        cfg.set(dev.veltraph.client.config.FishingConfig.FISHING_WORM_HUNTING,true);
        var m=new FishingModule(s,cfg,new Random(0));assertTrue(m.preStartCheck().isPresent());
        s.submerged=true;assertTrue(m.preStartCheck().isEmpty());
    }

    @Test
    void underwaterEmergencyCanSurfaceDuringCoreReelWithoutAbandoningAcknowledgment() {
        var s=new FakeServices();var cfg=new MapConfigService();
        cfg.set(dev.veltraph.client.config.FishingConfig.FISHING_WORM_HUNTING,true);
        cfg.set(dev.veltraph.client.config.FishingConfig.FISHING_MIN_CATCH_SECONDS,0.0);
        cfg.set(dev.veltraph.client.config.FishingConfig.FISHING_CATCH_DELAY,0.0);
        var m=new FishingModule(s,cfg,new Random(0));m.setEnabled(true);m.onTick();
        s.bobber=new BobberFacts(true,0,false,true);m.onTick();assertEquals(2,s.useRodCount);
        s.submerged=true;s.worm=true;m.onTick();assertTrue(s.wormAllowed);
        assertEquals(2,s.useRodCount);assertTrue(m.diagnostics().contains("state=POST_CATCH_DELAY"));
        s.bobber=BobberFacts.none();m.onTick();assertEquals(2,s.useRodCount);
        assertTrue(m.diagnostics().contains("state=NEED_CAST"));
        s.worm=false;s.submerged=false;m.onTick();assertEquals(3,s.useRodCount);
        assertTrue(m.diagnostics().contains("catches=1"));
    }
    @Test void totemRetainsExclusiveSlotOwnershipAcrossTicks(){
        var s=new FakeServices();var m=module(s);m.setEnabled(true);s.totem=true;s.rod=false;
        m.onTick();m.onTick();assertTrue(m.isEnabled());assertEquals(0,s.useRodCount);assertEquals(0,s.combatTicks);
        assertEquals(2,s.totemTicks);m.setEnabled(false);assertFalse(s.totem);
    }

    @Test void generalLowAirRecoveryPreemptsTotemSlotOwnership(){
        var s=new FakeServices();s.submerged=true;s.rod=false;
        var cfg=new MapConfigService();cfg.set(dev.veltraph.client.config.FishingConfig.FISHING_SURFACE_ON_LOW_BUBBLES,true);
        var m=new FishingModule(s,cfg,new Random(0));m.setEnabled(true);s.totem=true;m.onTick();
        assertTrue(s.swimming);assertFalse(s.totem);assertEquals(0,s.totemTicks);assertEquals(0,s.useRodCount);m.setEnabled(false);
    }

    @Test void baitOwnsMenusAndHeldSlotUntilCompletion(){
        var s=new FakeServices();var m=module(s);m.setEnabled(true);s.bait=true;s.rod=false;m.onTick();
        assertTrue(m.isEnabled());assertEquals(1,s.baitTicks);assertEquals(0,s.useRodCount);assertEquals(0,s.combatTicks);
        m.setEnabled(false);assertFalse(s.bait);
    }
    @Test void cancelledBaitFailureCannotResumeCoreCasts(){
        var s=new FakeServices();var m=module(s);m.setEnabled(true);s.baitFailure="Bait restocking cancelled";m.onTick();
        assertFalse(m.isEnabled());assertEquals(0,s.useRodCount);assertTrue(m.diagnostics().contains("Bait restocking cancelled"));
    }

}
