package dev.veltraph.client.gui;
import dev.veltraph.client.config.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SuperpairsAvoidXpTest {
    static final MenuSnapshot.Slot XP=card(10,"Reward A","minecraft:paper","{\"reward\":\"private-xp\"}");
    static MenuSnapshot.Slot card(int id,String name,String item,String components){return new MenuSnapshot.Slot(id,name,List.of("Private observed category"),false,item,1,false,components);}
    static MenuSnapshot.Slot hidden(int id){return card(id,"?","minecraft:gray_stained_glass_pane","{\"hidden\":1}");}
    static MenuSnapshot menu(int revision,MenuSnapshot.Slot...slots){return new MenuSnapshot(7,"Superpairs",List.of(slots),revision);}
    static ExperimentSettings settings(boolean avoid){return new ExperimentSettings(0,1000,"Remember","Timer:","?","Claim Rewards","Rewards Claimed","Failed").withSuperpairsRewards(avoid,new SuperpairsRewardBindings(Set.of(SuperpairsRewardBindings.Card.from(XP))));}
    static ExperimentsRuntime learned(boolean avoid){
        var r=new ExperimentsRuntime();var s=settings(avoid);
        assertEquals(10,r.tick(menu(1,hidden(10),hidden(11),hidden(12),hidden(13)),s,0).slot());
        assertEquals(11,r.tick(menu(2,XP,hidden(11),hidden(12),hidden(13)),s,1).slot());
        r.tick(menu(3,XP,card(11,"Reward A","minecraft:paper",XP.componentIdentity()),hidden(12),hidden(13)),s,2);
        return r;
    }
    @Test void knownXpPairIsFilteredButUnknownCardsAreStillExplored(){assertEquals(12,learned(true).tick(menu(4,hidden(10),hidden(11),hidden(12),hidden(13)),settings(true),3).slot());}
    @Test void disabledOptionKeepsKnownXpPair(){assertEquals(10,learned(false).tick(menu(4,hidden(10),hidden(11),hidden(12),hidden(13)),settings(false),3).slot());}
    @Test void allKnownXpCardsWaitWithoutRepeatedClicks(){var r=new ExperimentsRuntime();var s=settings(true);r.tick(menu(1,hidden(10),hidden(11)),s,0);r.tick(menu(2,XP,hidden(11)),s,1);r.tick(menu(3,XP,card(11,"Reward A","minecraft:paper",XP.componentIdentity())),s,2);assertEquals(ExperimentsRuntime.Kind.NONE,r.tick(menu(4,hidden(10),hidden(11)),s,3).kind());assertEquals(ExperimentsRuntime.Kind.NONE,r.tick(menu(4,hidden(10),hidden(11)),s,4).kind());}
    @Test void componentDriftAndSameNamedOtherItemsAreNotClassified(){var bindings=settings(true).superpairsRewards();assertFalse(bindings.isXp(SuperpairsRewardBindings.Card.from(card(10,XP.name(),XP.itemId(),"{\"reward\":\"other\"}"))));assertFalse(bindings.isXp(SuperpairsRewardBindings.Card.from(card(10,XP.name(),"minecraft:experience_bottle",XP.componentIdentity()))));}
    @Test void absentNativeIdentityRemainsUnclassified(){assertNull(SuperpairsRewardBindings.Card.from(new MenuSnapshot.Slot(10,XP.name(),XP.lore(),false,XP.itemId(),1,false)));assertFalse(settings(true).superpairsRewards().isXp(null));}
    @Test void defaultsAreOffAndBlankBindingIsExplicitlyUnbound(){var config=new MapConfigService();assertFalse(config.get(ExperimentsConfig.AVOID_XP_SUPERPAIRS));assertTrue(ExperimentsConfig.settings(config).superpairsRewards().xpCards().isEmpty());var r=new ExperimentsRuntime();var s=ExperimentSettings.defaults().withSuperpairsRewards(true,SuperpairsRewardBindings.empty());r.tick(menu(1,hidden(10)),s,0);assertTrue(r.diagnostics().contains("XP preference unbound"));}
    @Test void resetCannotCarryRememberedRewardIntoFreshBoard(){var r=learned(true);r.releaseSession();r.reset();assertEquals(10,r.tick(menu(1,hidden(10),hidden(11)),settings(true),0).slot());}
    @Test void foreignMenuIsNeverAdopted(){var r=learned(true);assertEquals(ExperimentsRuntime.Kind.NONE,r.tick(new MenuSnapshot(8,"Superpairs",List.of(hidden(10)),4),settings(true),3).kind());assertEquals(ExperimentsRuntime.State.FAILED,r.state());}
    @Test void strictBindingRejectsMissingFieldsAndDuplicateCards(){assertThrows(IllegalArgumentException.class,()->SuperpairsRewardBindings.parse("{\"schema\":1,\"xpCards\":[{\"itemId\":\"minecraft:experience_bottle\"}]}"));assertThrows(IllegalArgumentException.class,()->SuperpairsRewardBindings.parse("{\"schema\":true,\"xpCards\":[]}"));}
    @Test void unboundOptionDoesNotInventVanillaXpPredicate(){var r=new ExperimentsRuntime();var s=settings(true).withSuperpairsRewards(true,SuperpairsRewardBindings.empty());r.tick(menu(1,hidden(10),hidden(11)),s,0);assertEquals(11,r.tick(menu(2,card(10,"500 XP","minecraft:experience_bottle","{\"custom\":1}"),hidden(11)),s,1).slot());}
}
