package dev.veltraph.client.chocolate;

import net.fabricmc.fabric.api.client.command.v2.*;
import net.fabricmc.fabric.api.client.screen.v1.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Same-screen local command pad: capture/selection never replaces the owned native container. */
public final class ChocolateFactoryMenuControls {
    private ChocolateFactoryMenuControls() {}
    public static void register(ChocolateFactoryModule module){
        ScreenEvents.AFTER_INIT.register((client,screen,width,height)->{
            if(!(screen instanceof AbstractContainerScreen<?>))return;
            var lines=new ArrayList<String>();var offset=new int[]{0};var shown=new boolean[]{false};
            var input=new EditBox(client.font,8,32,Math.max(100,width-84),20,Component.literal("Local Chocolate Factory command"));input.setMaxLength(2000);input.setValue("vchocolate inspect");input.visible=false;input.active=false;
            var run=Button.builder(Component.literal("Run"),button->{
                if(client.gui.screen()!=screen||client.getConnection()==null)return;var dispatcher=ClientCommands.getActiveDispatcher();if(dispatcher==null)return;
                lines.clear();offset[0]=0;
                try{ChocolateFactoryCommands.executeInMenu(dispatcher,(FabricClientCommandSource)client.getConnection().getSuggestionsProvider(),input.getValue(),text->{lines.add(text);while(lines.size()>256)lines.removeFirst();});}catch(Exception error){lines.add(error.getMessage()==null?"Local command failed":error.getMessage());}
                // Keep the observed screen, menu, cursor and full item identities intact.
                input.setFocused(false);
            }).bounds(Math.max(112,width-68),32,60,20).build();run.visible=false;run.active=false;
            var older=Button.builder(Component.literal("Previous"),button->offset[0]=Math.max(0,offset[0]-5)).bounds(8,56,70,20).build();older.visible=false;older.active=false;
            var newer=Button.builder(Component.literal("Next"),button->offset[0]=Math.min(Math.max(0,lines.size()-1),offset[0]+5)).bounds(82,56,50,20).build();newer.visible=false;newer.active=false;
            var shield=Button.builder(Component.empty(),button->{}).bounds(6,78,Math.max(100,width-12),Math.max(10,Math.min(height-8,160)-78)).build();shield.visible=false;shield.active=false;
            var toggle=Button.builder(Component.literal("Factory tools"),button->{shown[0]=!shown[0];input.visible=shown[0];input.active=shown[0];run.visible=shown[0];run.active=shown[0];older.visible=shown[0];older.active=shown[0];newer.visible=shown[0];newer.active=shown[0];shield.visible=shown[0];shield.active=shown[0];}).bounds(8,8,100,20).build();
            Screens.getWidgets(screen).addAll(List.of(shield,toggle,input,run,older,newer));
            ScreenEvents.afterExtract(screen).register((s,g,mx,my,delta)->{
                if(!shown[0])return;int bottom=Math.min(height-8,160);g.fill(6,78,width-6,bottom,0xee17100d);int y=82;
                if(lines.isEmpty())g.text(client.font,"Run inspect, then show <slot> to capture actual fields",10,y,0xfff5e5d8);
                for(int i=offset[0];i<lines.size()&&y+10<bottom;i++,y+=11)g.text(client.font,client.font.plainSubstrByWidth(lines.get(i),Math.max(60,width-20)),10,y,0xfff5e5d8);
                g.text(client.font,client.font.plainSubstrByWidth(module.diagnostics(),Math.max(60,width-20)),10,bottom+2,0xffffcb8d);
            });
        });
    }
}
