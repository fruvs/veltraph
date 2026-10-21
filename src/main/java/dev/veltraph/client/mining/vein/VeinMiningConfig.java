package dev.veltraph.client.mining.vein;
import dev.veltraph.client.config.*;
import java.util.*;
public final class VeinMiningConfig {
 private VeinMiningConfig(){}
 public static final ConfigKey<Boolean> ENABLED=ConfigKey.ofBool("VEIN_MINING_ENABLED",false);
 public static final ConfigKey<String> MATERIALS=new ConfigKey<>("VEIN_MINING_MATERIALS","GRAY_MITHRIL=1;GREEN_MITHRIL=1;BLUE_MITHRIL=1;TITANIUM=10");
 public static final ConfigKey<Integer> SCAN_RADIUS=ConfigKey.ofInt("VEIN_MINING_SCAN_RADIUS",4);
 public static final ConfigKey<Long> RESPAWN_WAIT_MS=ConfigKey.ofLong("VEIN_MINING_RESPAWN_WAIT_MS",12000);
 private static final List<ConfigKey<?>> KEYS=List.of(ENABLED,MATERIALS,SCAN_RADIUS,RESPAWN_WAIT_MS,MiningConfig.AUTO_BREAK_MINING,MiningConfig.MINING_BREAK_TIMEOUT_MS,MiningConfig.MINING_DURABILITY_RESERVE);
 public record Stamp(ConfigService store,List<Long> revisions,List<Object> values){}
 public static Stamp stamp(ConfigService config){
  var revisions=new ArrayList<Long>();var values=new ArrayList<Object>();
  for(var key:KEYS){var revision=config.revision(key);if(revision.isEmpty())throw new IllegalStateException("Mining config revisions unavailable");revisions.add(revision.getAsLong());values.add(config.get(key));}
  for(int i=0;i<KEYS.size();i++)if(config.revision(KEYS.get(i)).orElse(-1)!=revisions.get(i))throw new IllegalStateException("Mining config changed during capture");
  return new Stamp(config,List.copyOf(revisions),List.copyOf(values));
 }
 public static Map<MiningMaterial,Integer> priorities(String text){
  if(text==null||text.isBlank())throw new IllegalArgumentException("Choose mining materials");
  var result=new EnumMap<MiningMaterial,Integer>(MiningMaterial.class);
  for(var entry:text.split(";",-1)){var parts=entry.trim().split("=",-1);if(parts.length!=2)throw new IllegalArgumentException("Use MATERIAL=priority");var material=MiningMaterial.valueOf(parts[0].trim());int priority=Integer.parseInt(parts[1].trim());if(priority<0||priority>100||result.putIfAbsent(material,priority)!=null)throw new IllegalArgumentException("Invalid or duplicate material priority");}
  if(result.values().stream().noneMatch(value->value>0))throw new IllegalArgumentException("Enable a mining material");return Map.copyOf(result);
 }
}
