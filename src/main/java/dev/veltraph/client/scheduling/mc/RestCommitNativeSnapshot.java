package dev.veltraph.client.scheduling.mc;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Original native fields only. This is a negative freshness veto, never SDK or transport permission. */
final class RestCommitNativeSnapshot {
    private final Minecraft client;
    private final net.minecraft.client.player.LocalPlayer player;
    private final Object world,connection,gameMode,menu;private final net.minecraft.client.multiplayer.ServerData server;private final String serverName,serverIp;
    private final Vec3 position;
    private final float yaw,pitch,health;
    private final Pose posture;
    private final int slot;
    private final net.minecraft.core.BlockPos floor;
    private final Object support;
    private final RawStack tool,cursor;
    private final Map<EquipmentSlot,RawStack> gear;
    private RestCommitNativeSnapshot(Minecraft client){
        this.client=client;player=client.player;world=client.level;connection=client.getConnection();gameMode=client.gameMode;server=client.getCurrentServer();serverName=server.name;serverIp=server.ip;
        menu=player.containerMenu;position=player.position();yaw=player.getYRot();pitch=player.getXRot();health=player.getHealth();posture=player.getPose();slot=player.getInventory().getSelectedSlot();
        floor=player.blockPosition().below();support=client.level.getBlockState(floor);tool=new RawStack(player.getInventory().getItem(slot));cursor=new RawStack(player.containerMenu.getCarried());
        var rows=new EnumMap<EquipmentSlot,RawStack>(EquipmentSlot.class);for(var part:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.OFFHAND))rows.put(part,new RawStack(player.getItemBySlot(part)));gear=Map.copyOf(rows);
    }
    // Ordinary installed SDK receivers only; unknown virtual getter domains decline.
    private static boolean nativeReceiverDomain(Minecraft client){return client!=null&&client.getClass()==Minecraft.class
        &&client.player!=null&&client.player.getClass()==net.minecraft.client.player.LocalPlayer.class
        &&client.level!=null&&client.level.getClass()==net.minecraft.client.multiplayer.ClientLevel.class
        &&client.player.connection!=null&&client.player.connection.getClass()==net.minecraft.client.multiplayer.ClientPacketListener.class
        &&client.level.getChunkSource().getClass()==net.minecraft.client.multiplayer.ClientChunkCache.class;}
    static RestCommitNativeSnapshot capture(Minecraft client){
        if(!nativeReceiverDomain(client)||!client.isSameThread()||client.player==null||client.level==null||client.getConnection()==null||client.gameMode==null||client.getCurrentServer()==null)return null;
        try{var original=new RestCommitNativeSnapshot(client);return original.current()?original:null;}
        catch(IllegalArgumentException unsupported){return null;}
    }
    boolean current(){
        if(!nativeReceiverDomain(client)||!client.isSameThread()||client.player!=player||client.level!=world||client.getConnection()!=connection||client.gameMode!=gameMode||client.getCurrentServer()!=server
                ||!player.isAlive()||!player.onGround()||client.gui.screen()!=null||player.containerMenu!=menu||player.containerMenu!=player.inventoryMenu
                ||!Objects.equals(server.name,serverName)||!Objects.equals(server.ip,serverIp)||player.getInventory().getSelectedSlot()!=slot||player.getPose()!=posture||Float.floatToRawIntBits(player.getYRot())!=Float.floatToRawIntBits(yaw)
                ||Float.floatToRawIntBits(player.getXRot())!=Float.floatToRawIntBits(pitch)||Float.floatToRawIntBits(player.getHealth())!=Float.floatToRawIntBits(health))return false;
        var p=player.position();
        if(Double.doubleToRawLongBits(p.x)!=Double.doubleToRawLongBits(position.x)||Double.doubleToRawLongBits(p.y)!=Double.doubleToRawLongBits(position.y)||Double.doubleToRawLongBits(p.z)!=Double.doubleToRawLongBits(position.z)
                ||!client.level.getChunkSource().hasChunk(floor.getX()>>4,floor.getZ()>>4)||client.level.getBlockState(floor)!=support
                ||!tool.current(player.getInventory().getItem(slot))||!cursor.current(player.containerMenu.getCarried())||!player.containerMenu.getCarried().isEmpty())return false;
        for(var row:gear.entrySet())if(!row.getValue().current(player.getItemBySlot(row.getKey())))return false;return true;
    }
    private static final class RawStack {
        private final ItemStack original;private final Object item;private final int count;
        private final IdentityHashMap<DataComponentType<?>,dev.veltraph.client.mining.vein.RestNativeComponentValues.Snapshot> components=new IdentityHashMap<>();
        RawStack(ItemStack stack){if(!RestNativeStackReadDomain.current(stack))throw new IllegalArgumentException("Unsupported original native stack read domain");original=stack;item=stack.getItem();count=stack.getCount();for(var row:stack.getComponents()){var value=dev.veltraph.client.mining.vein.RestNativeComponentValues.capture(row.type(),row.value());if(value==null)throw new IllegalArgumentException("Unsupported original native component value");components.put(row.type(),value);}}
        boolean current(ItemStack actual){
            if(actual!=original||!RestNativeStackReadDomain.current(actual)||actual.getItem()!=item||actual.getCount()!=count)return false;
            int seen=0;for(var row:actual.getComponents()){if(!components.containsKey(row.type())||!components.get(row.type()).current(row.value()))return false;seen++;}return seen==components.size();
        }
    }
}
