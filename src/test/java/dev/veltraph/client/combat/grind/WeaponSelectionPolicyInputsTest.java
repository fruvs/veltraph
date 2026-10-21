package dev.veltraph.client.combat.grind;
import dev.veltraph.client.config.*;
import dev.veltraph.client.fishing.mc.FishingAutoKillerWeaponType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WeaponSelectionPolicyInputsTest {
 private static java.util.Map<ConfigKey<?>,Long> capture(ConfigService c,boolean fishing,boolean slayer){return WeaponSelectionPolicyInputs.capture(c,fishing,slayer);}
 private static ConfigService revisionStore(java.util.OptionalLong revision){return new ConfigService(){public java.util.OptionalLong revision(ConfigKey<?> key){return revision;}public <T>T get(ConfigKey<T> key){return key.defaultValue();}public <T>void set(ConfigKey<T> key,T value){}public boolean isSet(ConfigKey<?> key){return false;}public void reset(ConfigKey<?> key){}};}

 @Test void fishingWeaponTypeAbaInvalidatesExactActualPolicyInputVector(){var c=new MapConfigService();var before=capture(c,true,false);var original=c.get(FishingConfig.FISHING_AUTOKILLER_WEAPON_TYPE);var changed=java.util.Arrays.stream(FishingAutoKillerWeaponType.values()).filter(v->v!=original).findFirst().orElseThrow();c.set(FishingConfig.FISHING_AUTOKILLER_WEAPON_TYPE,changed);c.set(FishingConfig.FISHING_AUTOKILLER_WEAPON_TYPE,original);assertEquals(original,c.get(FishingConfig.FISHING_AUTOKILLER_WEAPON_TYPE));assertNotEquals(before,capture(c,true,false));}
 @Test void slayerBossLabelAbaInvalidatesActualFamilyInputs(){var c=new MapConfigService();var before=capture(c,false,true);var old=c.get(SlayerConfig.SLAYER_BOSS_LABEL);c.set(SlayerConfig.SLAYER_BOSS_LABEL,"Different original family");c.set(SlayerConfig.SLAYER_BOSS_LABEL,old);assertNotEquals(before,capture(c,false,true));}
 @Test void slayerTaraPolicyAbaInvalidatesActualFamilyInputs(){var c=new MapConfigService();var before=capture(c,false,true);boolean old=c.get(TaraConfig.TARA_USE_SOUL_WHIP);c.set(TaraConfig.TARA_USE_SOUL_WHIP,!old);c.set(TaraConfig.TARA_USE_SOUL_WHIP,old);assertNotEquals(before,capture(c,false,true));}
 @Test void unrelatedConfigDoesNotInvalidateNarrowWeaponPolicyVector(){var c=new MapConfigService();var before=capture(c,true,true);boolean old=c.get(GhostConfig.GHOST_AUTO_HEAL_ENABLED);c.set(GhostConfig.GHOST_AUTO_HEAL_ENABLED,!old);assertEquals(before,capture(c,true,true));}
 @Test void originalCommonAndSvenConstantFamilyHaveOnlyDirectCommonInputs(){assertEquals(java.util.List.of(CombatConfig.COMBAT_WEAPON_SLOT,CombatConfig.COMBAT_CONTEXTUAL_WEAPON_PROFILE,CombatConfig.BESTIARY_PREFERRED_WEAPON),WeaponSelectionPolicyInputs.keys(false,false));}
 @Test void actualCaptureHelperRefusesUnknownRevision(){assertNull(WeaponSelectionPolicyInputs.capture(revisionStore(java.util.OptionalLong.empty()),true,true));}
 @Test void actualCaptureHelperRefusesNegativeRevision(){assertNull(WeaponSelectionPolicyInputs.capture(revisionStore(java.util.OptionalLong.of(-1)),true,true));}
 @Test void actualCaptureHelperRefusesExhaustedRevision(){assertNull(WeaponSelectionPolicyInputs.capture(revisionStore(java.util.OptionalLong.of(Long.MAX_VALUE)),true,true));}
 @Test void largestNonExhaustedRevisionRemainsObservable(){assertNotNull(WeaponSelectionPolicyInputs.capture(revisionStore(java.util.OptionalLong.of(Long.MAX_VALUE-1)),true,true));}
}
