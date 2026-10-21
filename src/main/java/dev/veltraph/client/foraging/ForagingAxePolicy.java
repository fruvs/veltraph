package dev.veltraph.client.foraging;
import dev.veltraph.client.config.ForagingConfig;
import java.util.*;
import java.util.regex.Pattern;
/** Reads observed Sweep and distinguishes passive left-click harvest from explicit Throwing Axe use.
 * Modern AUTO has no invented legacy two-second Treecapitator cooldown. Server item cooldown wins. */
public final class ForagingAxePolicy {
 public enum Activation { ATTACK, USE }
 public record Profile(double sweep,boolean multiLog,Activation activation,long cooldownMs) {}
 private static final Pattern SWEEP=Pattern.compile("(?i)\\bSweep\\s*:\\s*\\+?([0-9][0-9,]*(?:\\.[0-9]+)?)");
 private static final Pattern COOLDOWN=Pattern.compile("(?i)Cooldown\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)\\s*s\\b");
 private ForagingAxePolicy() {}
 public static boolean isAxe(String name) {
  String n=name.toLowerCase(Locale.ROOT);return n.contains("treecapitator")||n.contains("axe")&&!n.contains("pickaxe");
 }
 public static double sweep(List<String> lines) {
  for(String line:lines) {var m=SWEEP.matcher(line.replaceAll("§.",""));if(m.find())return Double.parseDouble(m.group(1).replace(",",""));}
  return Double.NaN;
 }
 public static boolean hasThrowingAbility(List<String> lore) {
  return lore.stream().anyMatch(line->line.replaceAll("§.","").toLowerCase(Locale.ROOT).contains("throwing axe"));
 }
 public static Profile profile(String name,List<String> lore,List<String> tab,ForagingConfig.AxeMode mode,long legacyMs) {
  double observed=sweep(tab);if(Double.isNaN(observed))observed=sweep(lore);
  String lower=name.toLowerCase(Locale.ROOT);
  boolean multi=observed>0||lower.contains("treecapitator")||lower.contains("jungle axe")||lower.contains("spruce axe");
  if(mode==ForagingConfig.AxeMode.ORDINARY)return new Profile(observed,false,Activation.ATTACK,0);
  if(mode==ForagingConfig.AxeMode.THROWING) {
   long delay=1000;
   for(String line:lore) {var m=COOLDOWN.matcher(line.replaceAll("§.",""));if(m.find()){delay=(long)(Math.min(60000,Double.parseDouble(m.group(1))*1000));break;}}
   return new Profile(observed,true,Activation.USE,delay);
  }
  if(mode==ForagingConfig.AxeMode.TREECAPITATOR)return new Profile(observed,true,Activation.ATTACK,Math.max(0,legacyMs));
  return new Profile(observed,multi||mode==ForagingConfig.AxeMode.SWEEP,Activation.ATTACK,0);
 }
 public static long cooldown(String name,ForagingConfig.AxeMode mode,long configuredMs) {
  return profile(name,List.of(),List.of(),mode,configuredMs).cooldownMs();
 }
}
