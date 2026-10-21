package dev.veltraph.client.combat.heal;
import java.util.List;
/** Recovered heal enum: iron sword AND case-sensitive substring, plus instance permission. */
public final class HealLookDownPolicy {
 private HealLookDownPolicy(){}
 private static final List<String> NAMES=List.of("Astraea","Hyperion","Scylla","Valkyrie");
 public static boolean required(boolean ironSword,String displayName,boolean permission){return ironSword && permission && displayName!=null && NAMES.stream().anyMatch(displayName::contains);}
}
