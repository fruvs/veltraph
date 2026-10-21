package dev.veltraph.client.inventory.bottle;
import dev.veltraph.client.inventory.NativeStackIdentity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Client-thread TAIL witnesses of vanilla-applied payloads, including26.3 direct player inventory packets. */
public final class BottleFillPacketObserver {
    private BottleFillPacketObserver(){}
    private static final BottleFillReceipts JOURNAL=new BottleFillReceipts();
    public static BottleFillReceipts journal(){return JOURNAL;}
    public static BottleFillReceipts.Cell cell(Minecraft client,ItemStack value){var copy=value.copy();return new BottleFillReceipts.Cell(McBottleFillPort.describe(copy),NativeStackIdentity.capture(copy,client.level.registryAccess()));}
    public static BottleFillReceipts.Scope scope(Minecraft c){return new BottleFillReceipts.Scope(c.player,c.level,c.getConnection(),c.player.containerMenu,c.gui.screen(),c.player.containerMenu.containerId);}
    public static void player(Minecraft c,Object listener,int index,ItemStack payload){if(!valid(c,listener)||index<0||index>=36)return;try{JOURNAL.applied(scope(c),BottleFillReceipts.Kind.PLAYER_SLOT,-1,Map.of(index,cell(c,payload)),Map.of());}catch(RuntimeException unsupported){}}
    public static void container(Minecraft c,Object listener,int menuId,int revision,BottleFillReceipts.Kind kind,Map<Integer,ItemStack> payload){if(!valid(c,listener)||c.player.containerMenu.containerId!=menuId)return;try{var player=new HashMap<Integer,BottleFillReceipts.Cell>();var chest=new HashMap<Integer,BottleFillReceipts.Cell>();for(var row:c.player.containerMenu.slots)if(payload.containsKey(row.index)){var value=cell(c,payload.get(row.index));if(row.container==c.player.getInventory()){int i=row.getContainerSlot();if(i>=0&&i<36)player.put(i,value);}else chest.put(row.index,value);}JOURNAL.applied(scope(c),kind,revision,player,chest);}catch(RuntimeException unsupported){}}
    private static boolean valid(Minecraft c,Object listener){return c.isSameThread()&&c.player!=null&&c.level!=null&&c.player.level()==c.level&&c.getConnection()==listener;}
}
