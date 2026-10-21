package dev.veltraph.client.garden.jacob.mc;

import dev.veltraph.client.garden.jacob.JacobRewardJournal;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.minecraft.network.chat.Component;
import java.util.UUID;

/** Explicit local unresolved-intent decisions. No GUI/input/reward/return behavior or old-flow adoption. */
public final class JacobRewardJournalCommands {
    private JacobRewardJournalCommands(){}
    public static void register(McJacobRewardModule module){
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,registry)->dispatcher.register(ClientCommands.literal("vjacob")
            .executes(c->status(c,module))
            .then(ClientCommands.literal("journal").executes(c->status(c,module)))
            .then(ClientCommands.literal("reconcile").then(ClientCommands.argument("intent",StringArgumentType.word())
                .then(ClientCommands.argument("contract",StringArgumentType.word())
                    .then(ClientCommands.literal("applied").executes(c->reconcile(c,module,JacobRewardJournal.Decision.APPLIED)))
                    .then(ClientCommands.literal("not-applied").executes(c->reconcile(c,module,JacobRewardJournal.Decision.NOT_APPLIED))))))));
    }
    private static int status(CommandContext<FabricClientCommandSource> ctx,McJacobRewardModule module){ctx.getSource().sendFeedback(Component.literal(module.journalStatus()+". /vjacob reconcile <intent UUID> <exact contract hash> applied|not-applied records an explicit local decision; it grants no ticket, medal, money, ACK or farm return. Configured farm profile is not authenticated account identity."));return 1;}
    private static int reconcile(CommandContext<FabricClientCommandSource> ctx,McJacobRewardModule module,JacobRewardJournal.Decision decision){
        try{String raw=StringArgumentType.getString(ctx,"intent");var id=UUID.fromString(raw);if(!id.toString().equals(raw))throw new IllegalArgumentException("Canonical intent UUID required");
            if(!module.reconcileIntent(id,StringArgumentType.getString(ctx,"contract"),decision)){ctx.getSource().sendError(Component.literal("Jacob reconciliation refused: stopped helper, exact unresolved intent/contract and durable Root journal required"));return 0;}
            ctx.getSource().sendFeedback(Component.literal("Recorded local reconciliation "+decision+". The old UNKNOWN flow remains stopped; no receipt, financial or farm-return credit was created."));return 1;
        }catch(RuntimeException refused){ctx.getSource().sendError(Component.literal("Jacob reconciliation refused: "+refused.getMessage()));return 0;}
    }
}
