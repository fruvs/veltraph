package dev.veltraph.client.combat.grind;
import dev.veltraph.client.config.*;
import java.util.List;
import java.util.ArrayList;
/** Exact known policy-declaration inputs only; revisions are observation/refusal, never native permission. */
public final class WeaponSelectionPolicyInputs {
 private WeaponSelectionPolicyInputs(){}
 public static java.util.Map<ConfigKey<?>,Long> capture(ConfigService config,boolean fishingDeclaration,boolean slayerDeclaration){
  if(config==null)return null;var captured=new java.util.LinkedHashMap<ConfigKey<?>,Long>();
  for(var key:keys(fishingDeclaration,slayerDeclaration)){
   var revision=config.revision(key);if(revision.isEmpty()||revision.getAsLong()<0||revision.getAsLong()==Long.MAX_VALUE)return null;
   captured.put(key,revision.getAsLong());
  }
  return java.util.Map.copyOf(captured);
 }
 public static List<ConfigKey<?>> keys(boolean fishingDeclaration,boolean slayerDeclaration){
  var keys=new ArrayList<ConfigKey<?>>();keys.add(CombatConfig.COMBAT_WEAPON_SLOT);keys.add(CombatConfig.COMBAT_CONTEXTUAL_WEAPON_PROFILE);keys.add(CombatConfig.BESTIARY_PREFERRED_WEAPON);
  if(fishingDeclaration)keys.add(FishingConfig.FISHING_AUTOKILLER_WEAPON_TYPE);
  if(slayerDeclaration){keys.add(SlayerConfig.SLAYER_BOSS_LABEL);keys.add(TaraConfig.TARA_USE_SOUL_WHIP);}
  return List.copyOf(keys);
 }
}
