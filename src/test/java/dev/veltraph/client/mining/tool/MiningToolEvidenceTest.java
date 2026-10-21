package dev.veltraph.client.mining.tool;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.mining.tool.MiningToolEvidence.*;

class MiningToolEvidenceTest {
    final Object player = new Object(), world = new Object(), connection = new Object(), owner = new Object(), handle = new Object();
    Stamp stamp() { return new Stamp(player, world, connection, owner, 1, 2, 1, 100, 0, "tool-components"); }
    Menu menu(String title, Row... rows) { return new Menu(stamp(), handle, 7, 6, 69, title, "full-content", true, List.of(rows)); }
    Row row(int slot, String name, String... lore) { return new Row(slot, name, List.of(lore), "row-" + slot); }
    @Test void exactCapturedPickobulusMarkerIsObservationAndSlotCanMove() {
        for (int slot : new int[]{33,42}) {
            var observation = selectedAbility(menu("Heart of the Mountain", row(slot,"Pickobulus",
                    "Pickaxe Ability: Pickobulus", "Cooldown: 60s", "SELECTED"))).orElseThrow();
            assertEquals(Ability.PICKOBULUS, observation.ability()); assertEquals(slot, observation.slot());
            assertEquals(new BigDecimal("60"), observation.nominalCooldownSeconds().orElseThrow());
        }
    }
    @Test void absentPickobulusNeverBecomesBoost() {
        assertTrue(selectedAbility(menu("Heart of the Mountain", row(29,"Mining Speed Boost",
                "Pickaxe Ability: Mining Speed Boost", "Cost", "1 Token of the Mountain"))).isEmpty());
        assertTrue(selectedAbility(menu("Commissions",row(33,"Pickobulus","SELECTED"))).isEmpty());
    }
    @Test void unknownOrCompetingSelectedRowsRemainUnknown() {
        assertTrue(selectedAbility(menu("Heart of the Mountain",row(1,"Future Ability","SELECTED"))).isEmpty());
        assertTrue(selectedAbility(menu("Heart of the Mountain",
            row(1,"Pickobulus","Pickaxe Ability: Pickobulus","SELECTED"),
            row(2,"Sheer Force","Pickaxe Ability: Sheer Force","SELECTED"))).isEmpty());
    }
    @Test void malformedHeaderDuplicateSlotAndPartialSelectedMarkerRemainUnknown() {
        assertTrue(selectedAbility(menu("Heart of the Mountain",row(1,"Pickobulus","Pickaxe Ability: Sheer Force","SELECTED"))).isEmpty());
        assertTrue(selectedAbility(menu("Heart of the Mountain",row(1,"Pickobulus","Pickaxe Ability: Pickobulus","NOT SELECTED"))).isEmpty());
        assertTrue(selectedAbility(menu("Heart of the Mountain",row(1,"Pickobulus","Pickaxe Ability: Pickobulus","SELECTED"),row(1,"Blank"))).isEmpty());
    }
    @Test void allSixCapturedNamesAreRepresentedWithoutLegacyCollapse() {
        for (Ability ability : Ability.values()) assertEquals(ability, selectedAbility(menu("Heart of the Mountain",
            row(1,ability.label(),"Pickaxe Ability: " + ability.label(),"SELECTED"))).orElseThrow().ability());
        assertEquals(6, Ability.values().length);
    }
    @Test void cooldownMissingOrAmbiguousDoesNotInventReadiness() {
        assertTrue(selectedAbility(menu("Heart of the Mountain",row(1,"Pickobulus",
                "Pickaxe Ability: Pickobulus","SELECTED"))).orElseThrow().nominalCooldownSeconds().isEmpty());
        assertTrue(selectedAbility(menu("Heart of the Mountain",row(1,"Pickobulus",
                "Pickaxe Ability: Pickobulus","SELECTED","Cooldown: 60s","Cooldown: 50s"))).orElseThrow().nominalCooldownSeconds().isEmpty());
    }
    @Test void miningSpeedPreservesDecimalAndDoesNotParseMovementOrPerk() {
        assertEquals(new BigDecimal("1234.56"), miningSpeed(menu("Your Equipment and Stats",
                row(1,"Mining Stats","Mining Speed: 1,234.56"))).orElseThrow());
        assertTrue(miningSpeed(menu("Your Equipment and Stats",row(1,"Mining Stats","Speed: 400"))).isEmpty());
        assertTrue(miningSpeed(menu("Heart of the Mountain",row(1,"Mining Speed","Mining Speed: 400"))).isEmpty());
    }
    @Test void malformedAndConflictingNumericFactsAreUnknown() {
        for (String text : new String[]{"Mining Speed: 1,23","Mining Speed: -1","Mining Speed: NaN","Mining Speed: 1e9"})
            assertTrue(miningSpeed(menu("Your Equipment and Stats",row(1,"Mining Stats",text))).isEmpty());
        assertTrue(miningSpeed(menu("Your Equipment and Stats",row(1,"Mining Stats","Mining Speed: 1","Mining Speed: 2"))).isEmpty());
        assertTrue(miningSpeed(menu("Your Equipment and Stats",row(1,"Mining Stats","Mining Speed: 1"),row(2,"Mining Stats","Mining Speed: 1"))).isEmpty());
    }
    @Test void capturedBoostGlyphIsRetainedAndExactPhraseParses() {
        assertEquals(new BigDecimal("200"), nominalSpeedBoost(menu("Heart of the Mountain",
                row(29,"Mining Speed Boost","Grants +200% \ue015 Mining Speed for","10s."))).orElseThrow());
        assertTrue(nominalSpeedBoost(menu("Heart of the Mountain",row(29,"Mining Speed Boost","+200% Something Else"))).isEmpty());
    }
    @Test void fuelParsesThousandsAndDecimalKWithoutLiteralReplacementBug() {
        assertEquals(new Fuel(1200,12500),fuel(List.of("Fuel: 1,200/12.5k")).orElseThrow());
        assertEquals(new Fuel(0,3000),fuel(List.of("§7Fuel: §a0§7/3K")).orElseThrow());
        assertEquals(new Fuel(1000,3000),fuel(List.of("Fuel: 1k/3,000")).orElseThrow());
    }
    @Test void invalidFuelCannotBecomeRefuelPermission() {
        for (String text : new String[]{"Fuel: -1/3k","Fuel: 4k/3k","Fuel: 1/0","Fuel: 1/0.0001k",
                "Fuel: 1,2/3k","Fuel: 1/999999999999999999999k","Fuel: 1/3k extra"}) assertTrue(fuel(List.of(text)).isEmpty());
        assertTrue(fuel(List.of("Fuel: 1/3k","Fuel: 2/3k")).isEmpty());
    }
    @Test void freshnessRejectsRollbackExpiredForeignAndPolicyReactivation() {
        var old=stamp();assertTrue(old.currentFor(old,110,10)); assertFalse(old.currentFor(old,111,10));
        assertFalse(old.currentFor(old,99,10));assertFalse(old.currentFor(old,110,-1));
        assertFalse(old.currentFor(new Stamp(player,new Object(),connection,owner,1,2,1,105,0,"tool-components"),110,10));
        assertFalse(old.currentFor(new Stamp(player,world,connection,owner,2,2,1,105,0,"tool-components"),110,10));
        assertFalse(old.currentFor(new Stamp(player,world,connection,owner,1,3,1,105,0,"tool-components"),110,10));
        assertFalse(old.currentFor(new Stamp(player,world,connection,owner,1,2,1,105,1,"tool-components"),110,10));
        assertFalse(old.currentFor(new Stamp(player,world,connection,owner,1,2,1,105,0,"changed-tool"),110,10));
    }
    @Test void unchangedMenuIdRevisionCannotHideContentOrEpochReplacement() {
        var before=menu("Heart of the Mountain",row(33,"Pickobulus","Pickaxe Ability: Pickobulus","SELECTED"));
        assertTrue(before.currentFor(before,110,10));
        assertFalse(before.currentFor(new Menu(stamp(),handle,7,6,69,before.title(),"changed-content",true,before.rows()),110,10));
        assertFalse(before.currentFor(new Menu(stamp(),handle,8,6,69,before.title(),before.contentIdentity(),true,before.rows()),110,10));
        assertFalse(before.currentFor(new Menu(stamp(),handle,7,6,69,before.title(),before.contentIdentity(),true,
                List.of(row(33,"Pickobulus","Pickaxe Ability: Pickobulus","Cost"))),110,10));
    }
    @Test void immutableBoundedSnapshotsAndIncompleteMenuPreventParsing() {
        var lore=new ArrayList<>(List.of("Fuel: 1/3k"));var row=new Row(1,"Drill",lore,"complete");lore.clear();assertEquals(1,row.lore().size());
        assertThrows(UnsupportedOperationException.class,()->row.lore().clear());
        assertThrows(IllegalArgumentException.class,()->new Row(1,"Drill",List.of("x".repeat(4097)),"complete"));
        var good=menu("Heart of the Mountain",row(1,"Pickobulus","Pickaxe Ability: Pickobulus","SELECTED"));
        assertTrue(selectedAbility(new Menu(stamp(),handle,7,6,69,good.title(),good.contentIdentity(),false,good.rows())).isEmpty());
        assertFalse(good.currentFor(new Menu(stamp(),handle,7,6,69,good.title(),good.contentIdentity(),false,good.rows()),110,10));
    }
    @Test void validFactWithMalformedSiblingIsAlwaysUnknown() {
        for (String sibling : new String[]{"Mining Speed: NaN", "Mining Speed: -1", "Mining Speed: 1,23", "Mining Speed"})
            assertTrue(miningSpeed(menu("Your Equipment and Stats",row(1,"Mining Stats","Mining Speed: 1",sibling))).isEmpty());
        for (String sibling : new String[]{"Cooldown: -1s", "Cooldown: NaNs", "Cooldown: 1e9s", "Cooldown"})
            assertTrue(selectedAbility(menu("Heart of the Mountain",row(1,"Pickobulus",
                    "Pickaxe Ability: Pickobulus","SELECTED","Cooldown: 60s",sibling))).orElseThrow().nominalCooldownSeconds().isEmpty());
        for (String sibling : new String[]{"Grants -200% Mining Speed for", "Grants +NaN% Mining Speed for", "Grants +1,23% Mining Speed for"})
            assertTrue(nominalSpeedBoost(menu("Heart of the Mountain",row(29,"Mining Speed Boost",
                    "Grants +200% \ue015 Mining Speed for",sibling))).isEmpty());
    }
    @Test void duplicateSelectedMarkerCannotQualifyOneAbility() {
        assertTrue(selectedAbility(menu("Heart of the Mountain",row(1,"Pickobulus",
                "Pickaxe Ability: Pickobulus","SELECTED","§aSELECTED"))).isEmpty());
    }
    @Test void toolAbaGenerationRejectsIdenticalRestoredSlotAndComponents() {
        var old=stamp();
        var restored=new Stamp(player,world,connection,owner,1,2,3,105,0,"tool-components");
        assertFalse(old.currentFor(restored,110,10));
        assertThrows(IllegalArgumentException.class,()->new Stamp(player,world,connection,owner,1,2,0,100,0,"tool-components"));
    }
    @Test void menuEpochRejectsSameHandleAndContentAfterPageAba() {
        var old=menu("Heart of the Mountain",row(33,"Pickobulus","Pickaxe Ability: Pickobulus","SELECTED"));
        var restored=new Menu(stamp(),handle,9,6,69,old.title(),old.contentIdentity(),true,old.rows());
        assertFalse(old.currentFor(restored,110,10));
    }

    @Test void nonnegativePassiveDecimalsRetainZeroAndValuesBeyondIntegerRange() {
        for (String value : new String[]{"0", "2147483648", "999999999999999999999999999999.125"}) {
            var expected=new BigDecimal(value);
            assertEquals(expected,miningSpeed(menu("Your Equipment and Stats",row(1,"Mining Stats","Mining Speed: "+value))).orElseThrow());
            assertEquals(expected,selectedAbility(menu("Heart of the Mountain",row(1,"Pickobulus",
                    "Pickaxe Ability: Pickobulus","SELECTED","Cooldown: "+value+"s"))).orElseThrow().nominalCooldownSeconds().orElseThrow());
            assertEquals(expected,nominalSpeedBoost(menu("Heart of the Mountain",row(29,"Mining Speed Boost",
                    "Grants +"+value+"% Mining Speed for"))).orElseThrow());
        }
    }

}
