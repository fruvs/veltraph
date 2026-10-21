package dev.veltraph.client.combat.grind;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.combat.grind.BestiaryWeaponSelector.Kind.*;
class ReadableWeaponScorerTest {
    private static BestiaryWeaponSelector.Candidate item(BestiaryWeaponSelector.Kind kind,String custom){return new BestiaryWeaponSelector.Candidate(0,false,kind,custom);}
    @Test void contextualResetPrecedesGlobalAndItemClass() {
        var score=ReadableWeaponScorer.score(item(SWORD,"Epic Private Blade"),"Private",List.of(new ReadableWeaponScorer.NameWeight("Blade",2000)),new ReadableWeaponScorer.ContextNames(List.of("Private")));
        assertEquals(516,score.total());assertEquals(2000,score.firstNameWeight());assertTrue(score.contextualOverride());assertEquals(500,score.globalBonus());assertEquals(3,score.itemBonus());
    }
    @Test void contextualOverrideIsSetThirteenNotAddThirteenOrFinalThirteen() {
        assertEquals(16,ReadableWeaponScorer.score(item(SWORD,"Private Blade"),"",List.of(new ReadableWeaponScorer.NameWeight("Private",99)),new ReadableWeaponScorer.ContextNames(List.of("Blade"))).total());
        assertEquals(15,ReadableWeaponScorer.score(item(AXE,"Private Axe"),"",List.of(),new ReadableWeaponScorer.ContextNames(List.of("Axe"))).total());
        assertEquals(14,ReadableWeaponScorer.score(item(PICKAXE,"Private Pickaxe"),"",List.of(),new ReadableWeaponScorer.ContextNames(List.of("Pickaxe"))).total());
        assertEquals(13,ReadableWeaponScorer.score(item(OTHER,"Private Wand"),"",List.of(),new ReadableWeaponScorer.ContextNames(List.of("Wand"))).total());
    }
    @Test void firstMatchingSuppliedMapEntryWinsWithoutSummingOrSorting() {
        var weights=List.of(new ReadableWeaponScorer.NameWeight("Private",7),new ReadableWeaponScorer.NameWeight("Blade",900));
        assertEquals(10,ReadableWeaponScorer.score(item(SWORD,"Private Blade"),"",weights,null).total());
        assertEquals(903,ReadableWeaponScorer.score(item(SWORD,"Private Blade"),"",weights.reversed(),null).total());
    }
    @Test void interfaceContextPresenceIsRequiredAndAnUnmatchedListDoesNotReset() {
        var weights=List.of(new ReadableWeaponScorer.NameWeight("Private",100));
        assertEquals(103,ReadableWeaponScorer.score(item(SWORD,"Private Blade"),"",weights,null).total());
        assertEquals(103,ReadableWeaponScorer.score(item(SWORD,"Private Blade"),"",weights,new ReadableWeaponScorer.ContextNames(List.of("other"))).total());
    }
    @Test void customNameNormalizationAppliesBeforeEveryReadableNameTerm() {
        var weights=List.of(new ReadableWeaponScorer.NameWeight("Private Blade",7));
        var s=ReadableWeaponScorer.score(item(SWORD,"§aPrivate §lBlade§r"),"Private Blade",weights,new ReadableWeaponScorer.ContextNames(List.of("Private Blade")));
        assertEquals(516,s.total());assertEquals(7,s.firstNameWeight());assertTrue(s.contextualOverride());
        assertEquals(3,ReadableWeaponScorer.score(item(SWORD,"Private §xBlade"),"Private Blade",weights,new ReadableWeaponScorer.ContextNames(List.of("Private Blade"))).total());
    }
    @Test void translatedNameCannotReceiveAnyCustomNameBonus() {
        assertEquals(3,ReadableWeaponScorer.score(item(SWORD,null),"Sword",List.of(new ReadableWeaponScorer.NameWeight("",99)),new ReadableWeaponScorer.ContextNames(List.of(""))).total());
    }
    @Test void sourceNamesRemainCaseSensitiveAndUntrimmed() {
        assertEquals(3,ReadableWeaponScorer.score(item(SWORD,"Private Blade"),"private",List.of(new ReadableWeaponScorer.NameWeight("blade",9)),new ReadableWeaponScorer.ContextNames(List.of(" Private"))).total());
        assertEquals(3,ReadableWeaponScorer.score(item(SWORD,"Private Blade"),"§aPrivate",List.of(),new ReadableWeaponScorer.ContextNames(List.of("§aPrivate"))).total());
    }
    @Test void rawRecoveredAlgorithmHandlesEmptyContextEntryButProductionContractRejectsIt() {
        assertEquals(16,ReadableWeaponScorer.score(item(SWORD,"anything"),"",List.of(),new ReadableWeaponScorer.ContextNames(List.of(""))).total());
    }
    @Test void emptyOrInvalidCandidateCannotGainContextOrGlobalPoints() {
        var context=new ReadableWeaponScorer.ContextNames(List.of("Private"));
        assertEquals(0,ReadableWeaponScorer.score(new BestiaryWeaponSelector.Candidate(0,true,SWORD,"Private"),"Private",List.of(),context).total());
        assertEquals(0,ReadableWeaponScorer.score(new BestiaryWeaponSelector.Candidate(0,false,null,"Private"),"Private",List.of(),context).total());
    }
    @Test void commonScorerKeepsAcceptedBestiaryCBehavior() {
        for(var kind:BestiaryWeaponSelector.Kind.values())for(var name:new String[]{null,"§aPrivate Blade","private",""})for(String global:List.of("Private"," Blade","private","")) {
            var candidate=item(kind,name);assertEquals(BestiaryWeaponSelector.score(candidate,global),ReadableWeaponScorer.score(candidate,global,List.of(),null).total());
        }
    }
}
