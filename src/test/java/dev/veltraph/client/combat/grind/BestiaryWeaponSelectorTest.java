package dev.veltraph.client.combat.grind;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.combat.grind.BestiaryWeaponSelector.Kind.*;

class BestiaryWeaponSelectorTest {
    private static BestiaryWeaponSelector.Candidate item(int slot, BestiaryWeaponSelector.Kind kind, String name) {
        return new BestiaryWeaponSelector.Candidate(slot, false, kind, name);
    }
    @Test void preferredSubstringBeatsSwordClassAndDefaultFixedSlot() {
        var choice=BestiaryWeaponSelector.select(List.of(item(0,SWORD,"Default Sword"),item(2,AXE,"Epic Raider Axe")),"Raider").orElseThrow();
        assertEquals(2,choice.slot());assertEquals(502,choice.score());
    }
    @Test void sourceClassScoresAreSwordThreeAxeTwoPickaxeOneOtherZero() {
        assertEquals(3,BestiaryWeaponSelector.score(item(0,SWORD,null),""));
        assertEquals(2,BestiaryWeaponSelector.score(item(0,AXE,null),""));
        assertEquals(1,BestiaryWeaponSelector.score(item(0,PICKAXE,null),""));
        assertEquals(0,BestiaryWeaponSelector.score(item(0,OTHER,null),""));
    }
    @Test void absentPreferenceFallsBackToHighestReadableClass() {
        var items=List.of(item(0,AXE,"Axe"),item(3,SWORD,"Sword"),item(1,PICKAXE,"Pickaxe"));
        assertEquals(3,BestiaryWeaponSelector.select(items,"absent").orElseThrow().slot());
        assertEquals(3,BestiaryWeaponSelector.select(items,"").orElseThrow().slot());
    }
    @Test void preferredNonWeaponIsScoredAsSourceCustomName() {
        assertEquals(500,BestiaryWeaponSelector.score(item(4,OTHER,"Private preferred wand"),"preferred"));
        assertEquals(4,BestiaryWeaponSelector.select(List.of(item(0,SWORD,null),item(4,OTHER,"Private preferred wand")),"preferred").orElseThrow().slot());
    }
    @Test void customNamePresenceMattersNotTranslatedDisplayName() {
        assertEquals(3,BestiaryWeaponSelector.score(item(0,SWORD,null),"Sword"));
        assertEquals(503,BestiaryWeaponSelector.score(item(0,SWORD,"Diamond Sword"),"Sword"));
    }
    @Test void sourceContainsPreservesCaseAndWhitespace() {
        assertEquals(2,BestiaryWeaponSelector.score(item(0,AXE,"Raider Axe"),"raider"));
        assertEquals(2,BestiaryWeaponSelector.score(item(0,AXE,"Raider Axe")," Raider"));
        assertEquals(502,BestiaryWeaponSelector.score(item(0,AXE,"Raider Axe"),"Axe"));
    }
    @Test void sourceFormattingRegexStripsOnlyKnownFormattingCodes() {
        assertEquals(503,BestiaryWeaponSelector.score(item(0,SWORD,"§aPrivate §lBlade§r"),"Private Blade"));
        assertEquals(3,BestiaryWeaponSelector.score(item(0,SWORD,"Private §xBlade"),"Private Blade"));
        assertEquals(3,BestiaryWeaponSelector.score(item(0,SWORD,"§aPrivate Blade"),"§aPrivate"));
    }
    @Test void localTieKeepsLowestHotbarSlotEvenWhenObservationsUnsorted() {
        assertEquals(1,BestiaryWeaponSelector.select(List.of(item(7,SWORD,"Same"),item(1,SWORD,"Same")),"Same").orElseThrow().slot());
    }
    @Test void storageAndInvalidSlotsCannotTriggerInventoryTransfer() {
        assertEquals(0,BestiaryWeaponSelector.select(List.of(item(0,AXE,null),item(10,SWORD,"Preferred"),item(-1,SWORD,"Preferred")),"Preferred").orElseThrow().slot());
    }
    @Test void emptyAndZeroScoreItemsNeverResolveAnAttackTool() {
        assertTrue(BestiaryWeaponSelector.select(List.of(new BestiaryWeaponSelector.Candidate(0,true,SWORD,"Preferred"),item(1,OTHER,null)),"Preferred").isEmpty());
    }
    @Test void nullPreferenceAndAmbiguousOrInvalidObservationFailsClosed() {
        assertTrue(BestiaryWeaponSelector.select(List.of(item(0,SWORD,null)),null).isEmpty());
        assertTrue(BestiaryWeaponSelector.select(List.of(item(0,SWORD,null),item(0,AXE,"Preferred")),"Preferred").isEmpty());
        assertTrue(BestiaryWeaponSelector.select(null,"").isEmpty());
        assertEquals(0,BestiaryWeaponSelector.score(new BestiaryWeaponSelector.Candidate(0,false,null,"Preferred"),"Preferred"));
    }
}
