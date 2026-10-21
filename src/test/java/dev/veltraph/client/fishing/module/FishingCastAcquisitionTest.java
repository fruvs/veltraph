package dev.veltraph.client.fishing.module;
import dev.veltraph.client.config.MapConfigService;
import dev.veltraph.client.fishing.*;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;
final class FishingCastAcquisitionTest {
    static final class NativeLikeServices implements FishingServices {
        BobberFacts hook=BobberFacts.none();boolean active,wantsStart,lastAllow;int rodUses,activeTicks;
        public boolean available(){return true;}public boolean hasFishingRod(){return true;}public BobberFacts bobber(){return hook;}public void useRod(){rodUses++;}
        public boolean tickCombat(boolean allowStart){lastAllow=allowStart;if(!active&&allowStart&&wantsStart)active=true;if(active)activeTicks++;return active;}
    }
    @Test void pendingOwnedCastDefersOnlyAcquisitionAndLateActualHookAdmitsIt(){var s=new NativeLikeServices();var m=new FishingModule(s,new MapConfigService(),new Random(0));m.setEnabled(true);m.onTick();assertEquals(1,s.rodUses);assertTrue(m.awaitingCoreCastAcknowledgment());s.wantsStart=true;m.onTick();assertFalse(s.lastAllow);assertFalse(s.active);assertEquals(1,s.rodUses);s.hook=new BobberFacts(true,0,false,false);m.onTick();assertTrue(s.lastAllow);assertTrue(s.active);assertEquals(1,s.rodUses);}
    @Test void acquisitionGateCannotSuspendAlreadyActiveRodHelperAfterReelRemoval(){var s=new NativeLikeServices();var m=new FishingModule(s,new MapConfigService(),new Random(0));m.setEnabled(true);m.onTick();s.wantsStart=true;s.hook=new BobberFacts(true,0,false,false);m.onTick();assertTrue(s.active);s.hook=BobberFacts.none();int before=s.activeTicks;m.onTick();assertFalse(s.lastAllow);assertEquals(before+1,s.activeTicks);assertEquals(1,s.rodUses);assertTrue(m.isEnabled());}
    @Test void legacyCombatServiceRetainsOriginalTickBehavior(){var calls=new int[1];FishingServices legacy=new FishingServices(){public boolean available(){return true;}public boolean hasFishingRod(){return true;}public BobberFacts bobber(){return BobberFacts.none();}public void useRod(){}public boolean tickCombat(){calls[0]++;return true;}};assertTrue(legacy.tickCombat(false));assertEquals(1,calls[0]);}
}
