package dev.veltraph.client.inventory.selling;

import dev.veltraph.client.gui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import java.util.*;

/** Exact native menu opener; all dispatches re-read the immutable P/W/C/menu/item custody observation. */
final class McNpcCookieMenuPort implements NpcCookieMenuRuntime.Port {
    private final Minecraft client;private final McMenuAccess menu;private final IdentityHashMap<NpcCookieMenuRuntime.Observation,MenuSnapshot> snapshots=new IdentityHashMap<>();
    McNpcCookieMenuPort(Minecraft client){this.client=client;menu=new McMenuAccess(client);}
    public NpcCookieMenuRuntime.Observation read(){
        if(!client.isSameThread())throw new IllegalStateException("NPC menu requires client thread");
        if(client.player==null||client.level==null||client.getConnection()==null)return new NpcCookieMenuRuntime.Observation(false,null,null,null,null,"",0,List.of(),List.of(),-1,false);
        var snap=menu.read();var inventory=new ArrayList<String>();for(int i=0;i<client.player.getInventory().getContainerSize();i++){var stack=SellingMenuAccess.describeInventoryStack(client.player.getInventory().getItem(i),i);inventory.add(stack.identity()+"\n"+stack.count());}
        var rows=new ArrayList<NpcCookieMenuRuntime.Row>();if(snap!=null)for(var slot:client.player.containerMenu.slots){var stack=slot.getItem();if(stack.isEmpty())continue;var id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();rows.add(new NpcCookieMenuRuntime.Row(slot.index,slot.container==client.player.getInventory(),id.equals("minecraft:barrier")||id.equals("minecraft:bedrock"),new NpcCookieBindings.Item(id,MenuSnapshot.clean(stack.getHoverName().getString()),stack.getCount(),SellingMenuAccess.components(stack))));}
        var o=new NpcCookieMenuRuntime.Observation(client.player.isAlive(),client.player,client.level,client.getConnection(),snap==null?null:client.player.containerMenu,snap==null?"":snap.title(),snap==null?0:snap.revision(),inventory,rows,client.player.getInventory().getSelectedSlot(),client.player.containerMenu.getCarried().isEmpty());
        if(snap!=null){if(snapshots.size()>32)snapshots.clear();snapshots.put(o,snap);}return o;
    }
    private boolean current(NpcCookieMenuRuntime.Observation expected){return client.gameMode!=null&&expected.equals(read());}
    public boolean open(NpcCookieMenuRuntime.Observation expected,NpcCookieBindings b,boolean command){
        if(!current(expected)||expected.menu()!=null||client.gui.screen()!=null||!expected.cursorEmpty())return false;
        if(command){client.getConnection().sendCommand(b.command());return true;}
        var stack=client.player.getInventory().getItem(b.menuItemSlot());if(stack.isEmpty())return false;var item=new NpcCookieBindings.Item(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),MenuSnapshot.clean(stack.getHoverName().getString()),stack.getCount(),SellingMenuAccess.components(stack));
        if(!item.equals(b.menuItem()))return false;client.player.getInventory().setSelectedSlot(b.menuItemSlot());client.gameMode.useItem(client.player,InteractionHand.MAIN_HAND);return true;
    }
    public boolean click(NpcCookieMenuRuntime.Observation expected,int slot){var snap=snapshots.get(expected);return snap!=null&&current(expected)&&menu.click(snap,slot);}
    public boolean restoreSelection(NpcCookieMenuRuntime.Observation expected,int slot){if(!current(expected)||!expected.cursorEmpty()||slot<0||slot>8)return false;client.player.getInventory().setSelectedSlot(slot);return true;}
}
