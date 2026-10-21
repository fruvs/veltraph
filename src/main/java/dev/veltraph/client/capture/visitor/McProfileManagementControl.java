package dev.veltraph.client.capture.visitor;

import dev.veltraph.client.game.LocationText;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;

/** Fresh readonly native control observation; never sends /profiles or creates confirmed profile IDs. */
public final class McProfileManagementControl {
    private McProfileManagementControl() {}
    public static Optional<ProfileManagementControl.Observation> read(Minecraft c){
        if(c==null||!c.isSameThread()||c.player==null||c.level==null||c.getConnection()==null||!(c.gui.screen() instanceof AbstractContainerScreen<?> screen))return Optional.empty();
        var player=c.player;var world=c.level;var connection=c.getConnection();var menu=screen.getMenu();
        if(menu!=player.containerMenu||menu.slots.size()>256)return Optional.empty();
        try{
            var title=boundedText(screen.getTitle(),512);if(title.isEmpty())return Optional.empty();
            if(!title.get().equals("SkyBlock Menu")||!BuiltInRegistries.MENU.getKey(menu.getType()).toString().equals("minecraft:generic_9x6"))return Optional.empty();
            var rows=new ArrayList<ProfileManagementControl.Row>();
            for(var slot:menu.slots){
                if(slot.container==player.getInventory())continue;var item=slot.getItem();if(item.isEmpty()||!item.is(net.minecraft.world.item.Items.NAME_TAG))continue;
                var name=boundedText(item.getHoverName(),512);if(name.isEmpty())return Optional.empty();if(!name.get().equals("Profile Management"))continue;
                var lore=item.get(DataComponents.LORE);if(lore==null||lore.lines().size()>32)return Optional.empty();var lines=new ArrayList<String>();
                for(var line:lore.lines()){var text=boundedText(line,512);if(text.isEmpty())return Optional.empty();lines.add(text.get());}
                if(rows.size()>=1)return Optional.empty();
                rows.add(new ProfileManagementControl.Row(slot.index,"minecraft:name_tag",name.get(),lines,false));
            }
            var result=ProfileManagementControl.read(title.get(),"minecraft:generic_9x6",rows);
            return c.player==player&&c.level==world&&c.getConnection()==connection&&c.gui.screen()==screen&&player.containerMenu==menu?result:Optional.empty();
        }catch(RuntimeException unknown){return Optional.empty();}
    }
    /** Stop component traversal before copying an oversized text part; reject, never silently truncate binding text. */
    private static Optional<String> boundedText(net.minecraft.network.chat.Component value,int limit){
        if(value==null)return Optional.empty();var out=new StringBuilder();boolean[] exceeded={false};
        value.visit((String part)->{if(part.length()>limit-out.length()){exceeded[0]=true;return Optional.of(Boolean.TRUE);}out.append(part);return Optional.<Boolean>empty();});
        return exceeded[0]?Optional.empty():Optional.of(LocationText.stripColorCodes(out.toString()));
    }
}
