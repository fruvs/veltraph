package dev.veltraph.client.garden.copy;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.veltraph.client.core.ModuleActivationGuard;
import dev.veltraph.client.world.BlockPos;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.minecraft.network.chat.Component;

/** Explicit local scan/save/prepare; only named start/resume enters guarded placement. */
public final class GardenCopyCommands {
    private GardenCopyCommands(){}
    public static void register(GardenAnalyzerModule analyzer,GardenCopyModule copy,ModuleActivationGuard guard){
        ClientCommandRegistrationCallback.EVENT.register((d,r)->d.register(ClientCommands.literal("vgarden")
                .executes(c->status(c,analyzer,copy))
                .then(scan(analyzer,guard))
                .then(ClientCommands.literal("save").executes(c->{try{analyzer.save();c.getSource().sendFeedback(Component.literal("Saved completed local dense blueprint"));return 1;}catch(Exception e){return error(c,e);}}))
                .then(paste("paste",analyzer,copy,false))
                .then(paste("paste-current",analyzer,copy,true))
                .then(ClientCommands.literal("start").executes(c->guard.start(copy)?1:0))
                .then(ClientCommands.literal("resume").executes(c->guard.start(copy)?1:0))
                .then(ClientCommands.literal("stop").executes(c->{copy.setEnabled(false);analyzer.setEnabled(false);return 1;}))
                .then(ClientCommands.literal("clear").executes(c->{try{copy.clear();return 1;}catch(Exception e){return error(c,e);}}))
                .then(ClientCommands.literal("status").executes(c->status(c,analyzer,copy)))));
    }
    private static ArgumentBuilder<FabricClientCommandSource,?> scan(GardenAnalyzerModule analyzer,ModuleActivationGuard guard){
        var end=ClientCommands.argument("maxZ",IntegerArgumentType.integer(-240,239)).executes(c->{try{analyzer.prepare(StringArgumentType.getString(c,"name"),new GardenRegion(i(c,"plot"),i(c,"minX"),i(c,"minY"),i(c,"minZ"),i(c,"maxX"),i(c,"maxY"),i(c,"maxZ")));return guard.start(analyzer)?1:0;}catch(Exception e){return error(c,e);}});
        return ClientCommands.literal("scan").then(ClientCommands.argument("name",StringArgumentType.word()).then(ClientCommands.argument("plot",IntegerArgumentType.integer(1,24)).then(ClientCommands.argument("minX",IntegerArgumentType.integer(-240,239)).then(ClientCommands.argument("minY",IntegerArgumentType.integer(0,319)).then(ClientCommands.argument("minZ",IntegerArgumentType.integer(-240,239)).then(ClientCommands.argument("maxX",IntegerArgumentType.integer(-240,239)).then(ClientCommands.argument("maxY",IntegerArgumentType.integer(0,319)).then(end))))))));
    }
    private static ArgumentBuilder<FabricClientCommandSource,?> paste(String literal,GardenAnalyzerModule analyzer,GardenCopyModule copy,boolean current){
        var position=ClientCommands.argument("x",IntegerArgumentType.integer(-240,239)).then(ClientCommands.argument("y",IntegerArgumentType.integer(0,319)).then(ClientCommands.argument("z",IntegerArgumentType.integer(-240,239)).then(ClientCommands.argument("turns",IntegerArgumentType.integer(0,3)).executes(c->{try{var b=current?analyzer.result().orElseThrow(()->new IllegalStateException("No coherent scan")):analyzer.load(StringArgumentType.getString(c,"name"));copy.prepare(b,new BlockPos(i(c,"x"),i(c,"y"),i(c,"z")),i(c,"turns"));c.getSource().sendFeedback(Component.literal(copy.diagnostics()+" | exact pending material budget "+copy.budget()+". Review, then /vgarden start; missing resources require explicit refill/resume."));return 1;}catch(Exception e){return error(c,e);}}))));
        return current?ClientCommands.literal(literal).then(position):ClientCommands.literal(literal).then(ClientCommands.argument("name",StringArgumentType.word()).then(position));
    }
    private static int i(CommandContext<FabricClientCommandSource> c,String key){return IntegerArgumentType.getInteger(c,key);}
    private static int error(CommandContext<FabricClientCommandSource> c,Exception e){c.getSource().sendError(Component.literal("Garden copy: "+e.getMessage()));return 0;}
    private static int status(CommandContext<FabricClientCommandSource> c,GardenAnalyzerModule a,GardenCopyModule p){c.getSource().sendFeedback(Component.literal(a.diagnostics()+" | "+p.diagnostics()+" | /vgarden scan <name> <plot> <minX minY minZ maxX maxY maxZ>; save; paste <name> <x y z> <0..3>; paste-current <x y z> <0..3>; start/resume/stop/clear/status. Exact local schema, additive ordinary-material placement; special missing states remain unsupported."));return 1;}
}
