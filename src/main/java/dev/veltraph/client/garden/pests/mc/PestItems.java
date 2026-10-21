package dev.veltraph.client.garden.pests.mc;

import dev.veltraph.client.garden.pests.PestBindings;
import dev.veltraph.client.game.LocationText;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import java.util.*;

final class PestItems {
    static String name(ItemStack s){return s.isEmpty()?"":LocationText.stripColorCodes(s.getHoverName().getString()).trim();}
    static String serverId(ItemStack s){var data=s.get(DataComponents.CUSTOM_DATA);if(data==null)return "";var tag=data.copyTag();return tag.getString("id").orElseGet(()->tag.getCompoundOrEmpty("ExtraAttributes").getStringOr("id",""));}
    static boolean matches(ItemStack s,PestBindings.Item b){return b!=null&&!s.isEmpty()&&BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(b.registryId())&&name(s).equals(b.name())&&serverId(s).equals(b.skyblockId());}
    static List<String> lore(ItemStack s){var l=s.get(DataComponents.LORE);return l==null?List.of():l.lines().stream().map(c->LocationText.stripColorCodes(c.getString()).trim()).toList();}
    static String identity(ItemStack s){return s.isEmpty()?"":BuiltInRegistries.ITEM.getKey(s.getItem())+"|"+s.getComponents().stream().map(Object::toString).sorted().toList();}
    static int count(Minecraft c,PestBindings.Item b,boolean cursor){int result=0;for(int i=0;i<36;i++){var s=c.player.getInventory().getItem(i);if(matches(s,b))result=Math.addExact(result,s.getCount());}
        if(cursor&&matches(c.player.containerMenu.getCarried(),b))result=Math.addExact(result,c.player.containerMenu.getCarried().getCount());return result;}
    static int uniqueSlot(Minecraft c,PestBindings.Item b){int found=-1;for(int i=0;i<36;i++)if(matches(c.player.getInventory().getItem(i),b)){if(found>=0)return -2;found=i;}return found;}
    static OptionalInt counter(Minecraft c,PestBindings.Item b,String prefix){int slot=uniqueSlot(c,b);return slot<0?OptionalInt.empty():PestBindings.count(lore(c.player.getInventory().getItem(slot)),prefix);}
    static boolean entityMatches(Entity e,PestBindings.Entity b){if(e==null||b==null||!e.isAlive()||!BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals(b.registryId())
            ||!LocationText.stripColorCodes(e.getName().getString()).trim().equals(b.name()))return false;
        return b.helmetIdentity().isBlank()||(e instanceof LivingEntity living&&identity(living.getItemBySlot(EquipmentSlot.HEAD)).equals(b.helmetIdentity()));}
    static Entity uniqueEntity(Minecraft c,PestBindings.Entity b,int plot,double radius){Entity found=null;for(var e:c.level.entitiesForRendering())if(e!=c.player&&entityMatches(e,b)
            &&dev.veltraph.client.garden.pests.GardenPlotBounds.contains(plot,e.getX(),e.getZ())&&e.distanceToSqr(c.player)<=radius*radius){if(found!=null)return null;found=e;}return found;}
}
