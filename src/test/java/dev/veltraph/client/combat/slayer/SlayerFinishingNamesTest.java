package dev.veltraph.client.combat.slayer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SlayerFinishingNamesTest {
 @Test void exactModernDeclaredBossesOnly(){assertTrue(SlayerFinishingNames.matches("Revenant Horror","Atoned Horror"));assertTrue(SlayerFinishingNames.matches("Tarantula Broodfather","Tarantula Broodfather"));assertTrue(SlayerFinishingNames.matches("Sven Packmaster","Sven Packmaster"));assertTrue(SlayerFinishingNames.matches("Voidgloom Seraph","Voidgloom Seraph"));}
 @Test void normalFormattingLevelAndObservedSingleHpToken(){assertTrue(SlayerFinishingNames.matches("Revenant Horror","§c☠ Atoned Horror"));assertTrue(SlayerFinishingNames.matches("Voidgloom Seraph","[Lv100] Voidgloom Seraph 10M❤"));assertTrue(SlayerFinishingNames.matches("Sven Packmaster","Sven Packmaster 1,000❤"));}
 @Test void unrelatedNamesSuffixesAndSubstringImpostorsDeny(){assertFalse(SlayerFinishingNames.matches("Revenant Horror","Fake Atoned Horror"));assertFalse(SlayerFinishingNames.matches("Revenant Horror","Atoned Horror Miniboss"));assertFalse(SlayerFinishingNames.matches("Sven Packmaster","Wolf Pup"));assertFalse(SlayerFinishingNames.matches("Tarantula Broodfather","Tarantula Broodmother"));}
 @Test void crossFamilyDeclarationCannotMatchRealOtherFamily(){assertFalse(SlayerFinishingNames.matches("Sven Packmaster","Voidgloom Seraph"));assertFalse(SlayerFinishingNames.matches("Revenant Horror","Tarantula Broodfather"));}
 @Test void unsupportedMarkerFamiliesAndMissingDeclarationsDeny(){assertFalse(SlayerFinishingNames.matches("Inferno Demonlord","Inferno Demonlord"));assertFalse(SlayerFinishingNames.matches("Riftstalker Bloodfiend","Riftstalker Bloodfiend"));assertFalse(SlayerFinishingNames.matches(null,"Atoned Horror"));assertFalse(SlayerFinishingNames.matches("Revenant Horror",null));}
 @Test void malformedOrSlashHealthAndExtraWordsDeny(){assertFalse(SlayerFinishingNames.matches("Voidgloom Seraph","Voidgloom Seraph 10M/10M❤"));assertFalse(SlayerFinishingNames.matches("Voidgloom Seraph","Voidgloom Seraph no❤"));assertFalse(SlayerFinishingNames.matches("Revenant Horror","Atoned Horror bonus 1M❤"));}
}
