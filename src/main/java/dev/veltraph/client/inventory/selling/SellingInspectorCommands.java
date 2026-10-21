package dev.veltraph.client.inventory.selling;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.minecraft.network.chat.*;

/** Ordinary local command/UI callback adapter. It has no server, inventory-write or enable method. */
public final class SellingInspectorCommands {
    private SellingInspectorCommands() {}
    /** Root attaches this branch under /vdebug and /veltraphdebug. */
    public static LiteralArgumentBuilder<FabricClientCommandSource> subtree(Supplier<GuardedSellingModule> module){
        return ClientCommands.literal("selling").executes(c->run("",module.get(),c.getSource()))
                .then(ClientCommands.argument("inspection",StringArgumentType.greedyString())
                        .executes(c->run(StringArgumentType.getString(c,"inspection"),module.get(),c.getSource())));
    }
    /** Public local callback for a future ordinary UI action; output is returned without mutation. */
    public static SellingInspector.Result inspect(String tail,GuardedSellingModule module){
        var request=SellingInspectorRequest.parse(tail);
        if(request.valid()&&request.kind()==SellingInspectorRequest.Kind.HELP)return SellingInspector.inspect(request,null);
        if(module==null)return SellingInspector.Result.error("Guarded selling module unavailable");
        return module.inspect(request);
    }
    private static int run(String tail,GuardedSellingModule module,FabricClientCommandSource source){
        var result=inspect(tail,module);if(!result.valid()){source.sendError(Component.literal(result.message()));return 0;}
        source.sendFeedback(Component.literal("[Selling inspector] "+result.message()));
        if(!result.json().isEmpty())source.sendFeedback(Component.literal(result.json()).withStyle(s->s
                .withClickEvent(new ClickEvent.CopyToClipboard(result.json()))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Copy exact identity JSON; value and quote still require explicit review")))));
        else if(!result.paths().isEmpty())source.sendFeedback(Component.literal(String.join(", ",result.paths())));
        return 1;
    }
}
