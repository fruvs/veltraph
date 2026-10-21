package dev.veltraph.client.galatea;

import dev.veltraph.client.gui.McMenuAccess;
import dev.veltraph.client.gui.MenuSnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;

/** Reads the bound native metadata once; it issues no item, menu or movement action. */
public final class McFigScaffoldHeight {
    private McFigScaffoldHeight(){}
    public static int observe(Minecraft client,boolean enabled,String binding) {
        if(!enabled)return 0; // Original source resets0 before the option gate.
        if(client.player==null||client.level==null)throw new IllegalArgumentException("Native scaffold metadata unavailable");
        var observed=FigObservedScaffoldHeight.parse(binding);var items=new ArrayList<FigObservedScaffoldHeight.Item>();
        for(int i=0;i<36;i++) {
            var stack=client.player.getInventory().getItem(i);if(stack.isEmpty())continue;
            var lore=stack.get(DataComponents.LORE);
            items.add(new FigObservedScaffoldHeight.Item(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                    MenuSnapshot.clean(stack.getHoverName().getString()),McMenuAccess.componentIdentity(stack,client.level.registryAccess()),
                    lore==null?List.of():lore.lines().stream().map(line->MenuSnapshot.clean(line.getString())).toList()));
        }
        return observed.observe(items);
    }
}
