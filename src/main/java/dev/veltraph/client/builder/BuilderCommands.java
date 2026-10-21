package dev.veltraph.client.builder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.veltraph.client.core.ModuleActivationGuard;
import dev.veltraph.client.world.BlockPos;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.minecraft.network.chat.Component;

public final class BuilderCommands {
    private BuilderCommands() {}
    public static void register(BuildLayoutStore store,BuilderModule module,ModuleActivationGuard guard) {
        ClientCommandRegistrationCallback.EVENT.register((d,a)->d.register(ClientCommands.literal("vbuild")
                .executes(c->{c.getSource().sendFeedback(Component.literal(module.preview()+" /vbuild load <name> [0-3] | recovered-endcaps [0-3] | recovered-irrigation [0-3] | generated <92|95> [0-3] | start | stop | status"));return 1;})
                .then(ClientCommands.literal("generated").then(ClientCommands.argument("source",IntegerArgumentType.integer(92,95))
                        .executes(c->generated(module,c.getSource(),IntegerArgumentType.getInteger(c,"source"),0))
                        .then(ClientCommands.argument("turns",IntegerArgumentType.integer(0,3))
                                .executes(c->generated(module,c.getSource(),IntegerArgumentType.getInteger(c,"source"),IntegerArgumentType.getInteger(c,"turns"))))))
                .then(ClientCommands.literal("bind").then(ClientCommands.argument("binding",StringArgumentType.word())
                        .then(ClientCommands.argument("id",StringArgumentType.word()).executes(c->{
                            try {module.bindGeneratedResource(StringArgumentType.getString(c,"binding"),StringArgumentType.getString(c,"id"));c.getSource().sendFeedback(Component.literal("Binding saved. Reload the generated plan to apply it."));return 1;}
                            catch(RuntimeException e){c.getSource().sendError(Component.literal("Builder: "+e.getMessage()));return 0;}
                        }))))
                .then(ClientCommands.literal("contract").then(ClientCommands.argument("binding",StringArgumentType.word())
                        .then(ClientCommands.argument("json",StringArgumentType.greedyString()).executes(c->{
                            try{module.bindGeneratedContract(StringArgumentType.getString(c,"binding"),StringArgumentType.getString(c,"json"));c.getSource().sendFeedback(Component.literal("Validated source contract saved. Reload generated plan to apply it."));return 1;}
                            catch(RuntimeException e){c.getSource().sendError(Component.literal("Builder: "+e.getMessage()));return 0;}
                        }))))
                .then(ClientCommands.literal("load").then(ClientCommands.argument("name",StringArgumentType.word())
                        .executes(c->load(store,module,c.getSource(),StringArgumentType.getString(c,"name"),0))
                        .then(ClientCommands.argument("turns",IntegerArgumentType.integer(0,3))
                                .executes(c->load(store,module,c.getSource(),StringArgumentType.getString(c,"name"),IntegerArgumentType.getInteger(c,"turns"))))))
                .then(ClientCommands.literal("recovered-endcaps")
                        .executes(c->recovered(module,c.getSource(),0))
                        .then(ClientCommands.argument("turns",IntegerArgumentType.integer(0,3))
                                .executes(c->recovered(module,c.getSource(),IntegerArgumentType.getInteger(c,"turns")))))
                .then(ClientCommands.literal("recovered-irrigation")
                        .executes(c->irrigation(module,c.getSource(),0))
                        .then(ClientCommands.argument("turns",IntegerArgumentType.integer(0,3))
                                .executes(c->irrigation(module,c.getSource(),IntegerArgumentType.getInteger(c,"turns")))))
                .then(ClientCommands.literal("start").executes(c->guard.start(module)?1:0))
                .then(ClientCommands.literal("stop").executes(c->{module.setEnabled(false);return 1;}))
                .then(ClientCommands.literal("status").executes(c->{c.getSource().sendFeedback(Component.literal(module.diagnostics()));return 1;}))));
    }
    private static int generated(BuilderModule module,FabricClientCommandSource source,int graph,int turns) {
        try {
            var p=source.getPlayer().blockPosition();module.loadGeneratedSource(graph,new BlockPos(p.getX(),p.getY(),p.getZ()),turns);
            source.sendFeedback(Component.literal(module.preview()+" /vbuild start begins or resumes; opaque source contracts stop explicitly."));return 1;
        }catch(RuntimeException e){source.sendError(Component.literal("Builder: "+e.getMessage()));return 0;}
    }
    private static int irrigation(BuilderModule module,FabricClientCommandSource source,int turns) {
        try {
            var p=source.getPlayer().blockPosition();module.loadRecoveredIrrigation(new BlockPos(p.getX(),p.getY(),p.getZ()),turns);
            source.sendFeedback(Component.literal(module.preview()+" Uses water buckets and clears only configured Building Block. /vbuild start begins or resumes."));return 1;
        }catch(RuntimeException e){source.sendError(Component.literal("Builder: "+e.getMessage()));return 0;}
    }
    private static int recovered(BuilderModule module,FabricClientCommandSource source,int turns) {
        try {
            var p=source.getPlayer().blockPosition();
            module.loadRecoveredEndcaps(new BlockPos(p.getX(),p.getY(),p.getZ()),turns);
            source.sendFeedback(Component.literal(module.preview()+" Preview this partial source layout before /vbuild start."));return 1;
        } catch(RuntimeException e) {source.sendError(Component.literal("Builder: "+e.getMessage()));return 0;}
    }
    private static int load(BuildLayoutStore store,BuilderModule module,FabricClientCommandSource source,String name,int turns) {
        try {
            var p=source.getPlayer().blockPosition();
            module.load(store.load(name).transformed(new BlockPos(p.getX(),p.getY(),p.getZ()),turns));
            source.sendFeedback(Component.literal(module.preview()+" Use /vbuild start to execute."));return 1;
        } catch(Exception e) { source.sendError(Component.literal("Builder: "+e.getMessage()));return 0; }
    }
}
