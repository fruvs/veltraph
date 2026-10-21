package dev.veltraph.client.foraging.mc;
import dev.veltraph.client.config.*;
import dev.veltraph.client.foraging.ForagingAxePolicy;
import dev.veltraph.client.mixin.PlayerListHudAccessor;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
/** Read-only held-item lore, actual player-tab Sweep stat, and server-synchronized item cooldown. */
public final class McForagingAbilitySource {
 private McForagingAbilitySource() {}
 public static ForagingAxePolicy.Profile profile(Minecraft client,ConfigService config) {
  if(client.player==null)return new ForagingAxePolicy.Profile(Double.NaN,false,ForagingAxePolicy.Activation.ATTACK,0);
  return profile(client,config,client.player.getInventory().getSelectedSlot());
 }
 public static ForagingAxePolicy.Profile profile(Minecraft client,ConfigService config,int slot) {
  return profile(client,config,slot,null);
 }
 public static ForagingAxePolicy.Profile profile(Minecraft client,ConfigService config,int slot,ForagingConfig.AxeMode override) {
  if(client.player==null||slot<0||slot>8)return new ForagingAxePolicy.Profile(Double.NaN,false,ForagingAxePolicy.Activation.ATTACK,0);
  var stack=client.player.getInventory().getItem(slot);var component=stack.get(DataComponents.LORE);
  var lore=component==null?List.<String>of():component.lines().stream().map(c->c.getString()).toList();
  List<String> tab=new ArrayList<>();
  if(client.getConnection()!=null)for(var entry:client.getConnection().getListedOnlinePlayers()) {
   if(tab.size()>=80)break;var display=entry.getTabListDisplayName();if(display!=null)tab.add(display.getString());
  }
  if(client.gui.hud.getTabList() instanceof PlayerListHudAccessor accessor) {
   if(accessor.getHeader()!=null)tab.add(accessor.getHeader().getString());
   if(accessor.getFooter()!=null)tab.add(accessor.getFooter().getString());
  }
  return ForagingAxePolicy.profile(stack.getHoverName().getString(),lore,tab,override==null?config.get(ForagingConfig.FORAGING_AXE_MODE):override,config.get(ForagingConfig.FORAGING_COOLDOWN_MS));
 }
 public static boolean hasThrowingAbility(Minecraft client,int slot) {
  if(client.player==null||slot<0||slot>8)return false;
  var lore=client.player.getInventory().getItem(slot).get(DataComponents.LORE);
  return lore!=null&&ForagingAxePolicy.hasThrowingAbility(lore.lines().stream().map(c->c.getString()).toList());
 }
 public static boolean cooldownActive(Minecraft client) {
  return client.player!=null&&client.player.getCooldowns().isOnCooldown(client.player.getMainHandItem());
 }
}
