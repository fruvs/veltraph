package dev.veltraph.client.rewarp.mc;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.minecraft.network.chat.Component;

/** Explicit local START/END recording; does not change or migrate saved RETURN markers. */
public final class FarmCycleCommands {
    private FarmCycleCommands(){}
    public static void register(McFarmCycleCoordinator cycle){
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->{
            var root=ClientCommands.literal("vfarmcycle").executes(c->{c.getSource().sendFeedback(Component.literal(cycle.status()+" | profiles: "+cycle.profileKeys()));return Command.SINGLE_SUCCESS;});
            for(String role:new String[]{"start","end","clear"}) {
                root.then(ClientCommands.literal(role).then(ClientCommands.argument("profile",StringArgumentType.word())
                        .suggests((c,b)->{cycle.profileKeys().forEach(b::suggest);return b.buildFuture();})
                        .executes(c->{String profile=StringArgumentType.getString(c,"profile");
                            try{
                                switch(role){case "start"->cycle.captureStart(profile);case "end"->cycle.captureEnd(profile);case "clear"->cycle.clear(profile);}
                                c.getSource().sendFeedback(Component.literal("Farm cycle "+role+" saved for "+profile));
                            }catch(Exception failure){c.getSource().sendError(Component.literal(failure.getMessage()));}
                            return Command.SINGLE_SUCCESS;
                        })));
            }
            root.then(ClientCommands.literal("list").executes(c->{
                for(var p:cycle.plans())c.getSource().sendFeedback(Component.literal(p.profileId()+" START="+p.start().position()+" END markers="+p.ends().size()));
                return Command.SINGLE_SUCCESS;
            }));dispatcher.register(root);
        });
    }
}
