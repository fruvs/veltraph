package dev.veltraph.client.combat.slayer.tara;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaraWaitingWhipTest {
    private final UUID boss=UUID.randomUUID();private final TaraSoulWhip.Custody custody=new TaraSoulWhip.Custody(UUID.randomUUID(),boss,UUID.randomUUID(),new Object(),new Object(),"all components");
    private TaraWaitingWhip.Profile profile(){return new TaraWaitingWhip.Profile(new TaraSoulWhip.Tool("Private Whip","minecraft:fishing_rod"),new TaraSoulWhip.Tool("Private Fallback","minecraft:fishing_rod"),3,"Private readiness","Private "+"{boss_uuid}"+" landed",1,300,3000,1,5);}
    private TaraWaitingWhip.Frame frame(long tick,long now,double health){return new TaraWaitingWhip.Frame(custody,health,true,true,true,false,tick,now);}
    private TaraWaitingWhip started(){var w=new TaraWaitingWhip();w.begin(profile(),frame(0,0,20));assertEquals(TaraWaitingWhip.Action.PREPARE_WHIP,w.tick(frame(3,100,20)));assertEquals(TaraWaitingWhip.Action.USE_WHIP,w.tick(frame(4,110,20)));w.sent(110);return w;}
    @Test void nativeHealthChangeWithoutExactReceiptDoesNotAdvance(){var w=started();assertEquals(TaraWaitingWhip.Action.WAIT,w.tick(frame(5,120,18)));assertFalse(w.complete());}
    @Test void exactReceiptWithoutActualNativeHealthEffectDoesNotAdvance(){var w=started();w.message("Private "+boss+" landed",115);assertEquals(TaraWaitingWhip.Action.WAIT,w.tick(frame(5,120,20)));assertFalse(w.complete());}
    @Test void onlySentUseExactBossReceiptAndActualHealthEffectComplete(){var w=started();w.message("Private "+boss+" landed",115);assertEquals(TaraWaitingWhip.Action.COMPLETE,w.tick(frame(5,120,18)));assertEquals(1,w.uses());}
    @Test void foreignAndChatPrefixedReceiptsCannotAdvance(){for(String text:List.of("Private "+UUID.randomUUID()+" landed","Player: Private "+boss+" landed")){var w=started();w.message(text,115);assertEquals(TaraWaitingWhip.Action.WAIT,w.tick(frame(5,120,18)));assertFalse(w.complete());}}
    @Test void noAcknowledgmentHasABoundedTerminalOutcome(){var w=started();assertEquals(TaraWaitingWhip.Action.FAIL,w.tick(frame(6,410,20)));assertEquals(1,w.uses());}
    @Test void readyCueNativeRayAndCustodyCannotBeAssumed(){var w=started();assertEquals(TaraWaitingWhip.Action.FAIL,w.tick(new TaraWaitingWhip.Frame(custody,20,false,true,true,false,5,120)));w=started();assertEquals(TaraWaitingWhip.Action.FAIL,w.tick(new TaraWaitingWhip.Frame(custody,20,true,false,true,false,5,120)));w=started();var changed=new TaraSoulWhip.Custody(custody.player(),boss,custody.ownerPlate(),new Object(),custody.connection(),custody.loadout());assertEquals(TaraWaitingWhip.Action.FAIL,w.tick(new TaraWaitingWhip.Frame(changed,18,true,true,true,false,5,120)));}
    @Test void unresolvedSkillDescriptorCannotBecomeAPublicDefault(){assertThrows(IllegalArgumentException.class,()->new TaraWaitingWhip.Profile(profile().primary(),profile().fallback(),3,"","Unknown",1,300,3000,1,5));assertThrows(IllegalArgumentException.class,()->new TaraWaitingWhip.Profile(new TaraSoulWhip.Tool("Bow","minecraft:bow"),profile().fallback(),3,"Ready","{boss_uuid}",1,300,3000,1,5));}
}
