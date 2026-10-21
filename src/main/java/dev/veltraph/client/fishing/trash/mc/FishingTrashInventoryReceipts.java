package dev.veltraph.client.fishing.trash.mc;

import dev.veltraph.client.fishing.trash.*;
import dev.veltraph.client.inventory.NativeStackIdentity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import java.util.*;

/** Bounded copies of actual applied inventory packet payloads; local predicted clicks cannot create a receipt. */
public final class FishingTrashInventoryReceipts {
    private static long sequence;
    private static Object connection;
    private static final Deque<FishingTrashDiscard.Receipt> receipts=new ArrayDeque<>();
    private FishingTrashInventoryReceipts(){}
    public static FishingTrashProfile.Stack describe(Minecraft client,ItemStack value) {
        var copy=value.copy();
        return new FishingTrashProfile.Stack(BuiltInRegistries.ITEM.getKey(copy.getItem()).toString(),copy.getHoverName().getString(),
                copy.getTooltipLines(Item.TooltipContext.EMPTY,null,TooltipFlag.NORMAL).stream().map(line->line.getString()).toList(),
                copy.getCount(),NativeStackIdentity.capture(copy,client.level.registryAccess()));
    }
    public static long sequence(){return sequence;}
    public static void applied(Minecraft client,Object listener,int menuId,int revision,Map<Integer,ItemStack> actualPayload,boolean cursorEmpty) {
        if(!client.isSameThread()||client.player==null||client.level==null||client.getConnection()!=listener||menuId!=0)return;
        if(connection!=listener){connection=listener;receipts.clear();}
        try {
            var rows=new TreeMap<Integer,FishingTrashProfile.Stack>();
            for(var entry:actualPayload.entrySet())rows.put(entry.getKey(),describe(client,entry.getValue()));
            var context=new FishingTrashDiscard.Context(client.player,client.level,listener,client.player.containerMenu,client.gui.screen());
            receipts.addLast(new FishingTrashDiscard.Receipt(context,menuId,revision,++sequence,rows,cursorEmpty));
            while(receipts.size()>32)receipts.removeFirst();
        } catch(RuntimeException unsupported) { /* Unsupported identity cannot acknowledge a destructive request. */ }
    }
    public static FishingTrashDiscard.Receipt latest(int target,long after) {
        var iterator=receipts.descendingIterator();
        while(iterator.hasNext()){var value=iterator.next();if(value.sequence()>after&&value.authoritativeSlots().containsKey(target))return value;}
        return null;
    }
}
