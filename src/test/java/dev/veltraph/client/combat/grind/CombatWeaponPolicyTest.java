package dev.veltraph.client.combat.grind;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.combat.grind.BestiaryWeaponSelector.Kind.*;
import static dev.veltraph.client.combat.grind.CombatWeaponPolicy.Family.*;
class CombatWeaponPolicyTest {
    private static final String PROFILE="{\"schema\":1,\"families\":{\"SLAYER_SPIDER\":{\"preferredNames\":[\"Context Blade\"]},\"SVEN_CAVE\":{\"preferredNames\":[\"Context Axe\"]}}}";
    private static BestiaryWeaponSelector.Candidate item(int slot,BestiaryWeaponSelector.Kind kind,String name){return new BestiaryWeaponSelector.Candidate(slot,false,kind,name);}
    private static final List<BestiaryWeaponSelector.Candidate> ITEMS=List.of(item(0,SWORD,"Default sword"),item(2,AXE,"Context Axe"),item(4,SWORD,"Context Blade"),item(7,OTHER,"Global skill"));
    @Test void blankAndUnselectedFamiliesKeepExplicitFixedPolicyDespiteGlobalPreference() {
        for(var policy:List.of(CombatWeaponPolicy.parse("",SVEN_CAVE).policy(),CombatWeaponPolicy.parse("{\"schema\":1,\"families\":{}}",SLAYER_SPIDER).policy(),CombatWeaponPolicy.parse(PROFILE,null).policy())) {
            assertEquals(CombatWeaponPolicy.Mode.FIXED_SLOT,policy.mode());assertEquals(0,policy.select(ITEMS,0,"Global").orElseThrow().slot());
            assertTrue(policy.select(ITEMS,-1,"Global").isEmpty());assertTrue(policy.select(ITEMS,8,"Global").isEmpty());
        }
    }
    @Test void absentSelectedFamilyKeepsFixedWhenOnlyUnrelatedContractIsMalformed() {
        for(String profile:List.of("{\"schema\":1,\"families\":{\"SVEN_CAVE\":{\"preferredNames\":42}}}","{\"schema\":1,\"families\":{\"SVEN_CAVE\":42}}","{\"schema\":1,\"families\":{\"SVEN_CAVE\":{\"unknownField\":true}}}")) {
            var absent=CombatWeaponPolicy.parse(profile,SLAYER_SPIDER);assertTrue(absent.valid(),absent.error());
            assertEquals(CombatWeaponPolicy.Mode.FIXED_SLOT,absent.policy().mode());assertEquals(0,absent.policy().select(ITEMS,0,"Global").orElseThrow().slot());
            assertFalse(CombatWeaponPolicy.parse(profile,SVEN_CAVE).valid(),"Selected malformed family must still fail");
        }
        assertTrue(CombatWeaponPolicy.parse("{\"schema\":1,\"families\":{\"SLAYER_SPIDER\":{\"preferredNames\":42}}}",SVEN_CAVE).valid());
    }
    @Test void unprovenOwnerNeverOptsInOrParsesAnIrrelevantContract() {
        assertEquals(CombatWeaponPolicy.Mode.FIXED_SLOT,CombatWeaponPolicy.parse("invalid irrelevant profile",null).policy().mode());
        assertEquals(CombatWeaponPolicy.Mode.FIXED_SLOT,CombatWeaponPolicy.parse(null,null).policy().mode());
    }
    @Test void ownerDeclaredFamilyListChangesContextSelection() {
        var spider=CombatWeaponPolicy.parse(PROFILE,SLAYER_SPIDER);var sven=CombatWeaponPolicy.parse(PROFILE,SVEN_CAVE);
        assertTrue(spider.valid());assertTrue(sven.valid());assertEquals(4,spider.policy().select(ITEMS,0,"").orElseThrow().slot());assertEquals(2,sven.policy().select(ITEMS,0,"").orElseThrow().slot());
        assertEquals(16,spider.policy().select(ITEMS,0,"").orElseThrow().score());assertEquals(15,sven.policy().select(ITEMS,0,"").orElseThrow().score());
    }
    @Test void globalKeyAppliesToEveryOptedContextAndCanBeatItsThirteenOverride() {
        for(var family:CombatWeaponPolicy.Family.values()) {
            var p=CombatWeaponPolicy.parse(PROFILE,family).policy();assertEquals(7,p.select(ITEMS,0,"Global").orElseThrow().slot());assertEquals(500,p.select(ITEMS,0,"Global").orElseThrow().score());
        }
        assertEquals(516,CombatWeaponPolicy.parse(PROFILE,SLAYER_SPIDER).policy().select(ITEMS,0,"Context Blade").orElseThrow().score());
    }
    @Test void absentContextAndGlobalMatchesFallBackToReadableClassNotFixedSlot() {
        var p=CombatWeaponPolicy.parse(PROFILE,SVEN_CAVE).policy();assertEquals(1,p.select(List.of(item(0,OTHER,"Other"),item(1,SWORD,"Not preferred")),0,"absent").orElseThrow().slot());
    }
    @Test void hotbarTieLocalStorageExcludedAndDuplicateObservationFails() {
        var p=CombatWeaponPolicy.parse(PROFILE,SLAYER_SPIDER).policy();var items=List.of(item(5,SWORD,"Context Blade"),item(2,SWORD,"Context Blade"),item(10,SWORD,"Global"));
        assertEquals(2,p.select(items,0,"Global").orElseThrow().slot());assertTrue(p.select(List.of(item(0,SWORD,""),item(0,AXE,"")),0,"").isEmpty());
    }
    @Test void exactLabelsAreNotNormalizedAsConfigurationTokens() {
        var p=CombatWeaponPolicy.parse("{\"schema\":1,\"families\":{\"SLAYER_SPIDER\":{\"preferredNames\":[\" Context\"]}}}",SLAYER_SPIDER).policy();
        assertEquals(List.of(" Context"),p.contextualNames());assertEquals(3,p.select(List.of(item(0,SWORD,"Context")),0,"").orElseThrow().score());
    }
    @Test void strictProfileRejectsUnknownFieldsWrongSchemaAndInvalidNames() {
        for(String value:List.of("{}","[]","{\"schema\":2,\"families\":{}}","{\"schema\":1,\"families\":{},\"nativeScore\":900}","{\"schema\":1,\"families\":{\"GHOST\":{\"preferredNames\":[\"A\"]}}}","{\"schema\":1,\"families\":{\"SVEN_CAVE\":{\"preferredNames\":[],\"weights\":{}}}}","{\"schema\":1,\"families\":{\"SVEN_CAVE\":{\"preferredNames\":[\"\"]}}}","{\"schema\":1,\"families\":{\"SVEN_CAVE\":{\"preferredNames\":[\"A\",\"A\"]}}}","{\"schema\":1,\"families\":{\"SVEN_CAVE\":{\"preferredNames\":[3]}}}"))assertFalse(CombatWeaponPolicy.parse(value,SVEN_CAVE).valid(),value);
        assertFalse(CombatWeaponPolicy.parse(null,SVEN_CAVE).valid());assertFalse(CombatWeaponPolicy.parse("x".repeat(16385),SVEN_CAVE).valid());
    }
    @Test void duplicateJsonKeysAndNonJsonSyntaxCannotHidePolicyChoices() {
        assertFalse(CombatWeaponPolicy.parse("{\"schema\":1,\"schema\":2,\"families\":{}}",SVEN_CAVE).valid());
        assertFalse(CombatWeaponPolicy.parse("{\"schema\":1,\"families\":{\"SVEN_CAVE\":{\"preferredNames\":[\"A\"],\"preferredNames\":[\"B\"]}}}",SVEN_CAVE).valid());
        assertFalse(CombatWeaponPolicy.parse("{schema:1,families:{}}",SVEN_CAVE).valid());
        assertFalse(CombatWeaponPolicy.parse("{\"schema\":1,\"families\":{}} trailing",SVEN_CAVE).valid());
    }
    @Test void signaturesBindFamilyContextAndGlobalWhileFixedIgnoresIrrelevantGlobal() {
        var s=CombatWeaponPolicy.parse(PROFILE,SLAYER_SPIDER).policy();var v=CombatWeaponPolicy.parse(PROFILE,SVEN_CAVE).policy();
        assertNotEquals(s.signature(0,""),v.signature(0,""));assertNotEquals(s.signature(0,""),s.signature(0,"Global"));assertEquals(s.signature(0,"Global"),s.signature(8,"Global"));
        assertEquals(CombatWeaponPolicy.fixed().signature(0,""),CombatWeaponPolicy.fixed().signature(0,"Global"));
    }
    @Test void commonPolicyPreservesAcceptedBestiaryCandidateChoice() {
        for(String global:List.of("Context","Global","missing",""))assertEquals(BestiaryWeaponSelector.select(ITEMS,global),CombatWeaponPolicy.common().select(ITEMS,8,global));
    }
}
