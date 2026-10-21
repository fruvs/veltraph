package dev.veltraph.client.galatea;

import dev.veltraph.client.gui.McMenuAccess;
import dev.veltraph.client.gui.MenuSnapshot;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;

/** Read-only actual hotbar observation; caller owns lifetime, guard and native resource receipts. */
public final class McFigScaffoldMetadata {
    private McFigScaffoldMetadata(){}
    public static FigObservedScaffoldMetadata.Observation observe(Minecraft client,String json) {
        if(client.player==null || client.level==null)throw new IllegalArgumentException("Native scaffold context unavailable");
        var binding=FigObservedScaffoldMetadata.parse(json);
        var items=new ArrayList<FigObservedScaffoldMetadata.Item>();
        for(int slot=0;slot<9;slot++) {
            var stack=client.player.getInventory().getItem(slot);if(stack.isEmpty())continue;
            var lore=stack.get(DataComponents.LORE);
            items.add(new FigObservedScaffoldMetadata.Item(slot,BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                MenuSnapshot.clean(stack.getHoverName().getString()),McMenuAccess.componentIdentity(stack,client.level.registryAccess()),
                lore==null?java.util.List.of():lore.lines().stream().map(l->MenuSnapshot.clean(l.getString())).toList()));
        }
        return binding.observe(items);
    }
}
