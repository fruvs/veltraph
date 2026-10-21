package dev.veltraph.client.greenhouse;

import com.google.gson.Gson;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.veltraph.client.core.ModuleActivationGuard;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.minecraft.network.chat.Component;

/** Local read/prepare/review surface. Only explicit start enters native cultivation. */
public final class GreenhouseCommands {
    private GreenhouseCommands() {}
    public static void register(GreenhouseModule module,ModuleActivationGuard guard){register(module,guard,"vgreenhouse",()->guard.start(module));}
    static void register(GreenhouseModule module,ModuleActivationGuard guard,String command,java.util.function.BooleanSupplier start){
        ClientCommandRegistrationCallback.EVENT.register((d,r)->d.register(ClientCommands.literal(command)
            .executes(c->say(c,module.diagnostics()+"\nstatus/inspect/environment/show/fingerprint/capture/care/rules/effects/recipes/recipe/context/diagnostic/bindings/guide/combination/plan/water/reconcile/check/start/stop/clear"))
            .then(ClientCommands.literal("inspect").executes(c->say(c,module.visibleNativeMenu())))
            .then(ClientCommands.literal("show").then(ClientCommands.argument("slot",IntegerArgumentType.integer(0,255)).executes(c->say(c,module.visibleNativeSlot(IntegerArgumentType.getInteger(c,"slot"))))))
            .then(ClientCommands.literal("diagnostic-fingerprint").then(ClientCommands.argument("slot",IntegerArgumentType.integer(0,8)).executes(c->say(c,module.canonicalInventoryFingerprint(IntegerArgumentType.getInteger(c,"slot"))))))
            .then(ClientCommands.literal("fingerprint").then(ClientCommands.argument("slot",IntegerArgumentType.integer(0,40)).executes(c->say(c,module.inventoryFingerprint(IntegerArgumentType.getInteger(c,"slot"))))))
            .then(water(module))
            .then(reconcile(module))
            .then(ClientCommands.literal("status").executes(c->say(c,module.diagnostics())))
            .then(ClientCommands.literal("capture").executes(c->say(c,module.inspectVisibleMenu()?"Captured native scoped diagnostics; close the menu and review /vgreenhouse care":"UNKNOWN: Open the real Diagnostics Tool menu and configure exact observed bindings first")))
            .then(ClientCommands.literal("care").executes(c->say(c,module.currentLifecycleSummary()+"\n"+module.care().toString())))
            .then(ClientCommands.literal("rules").executes(c->say(c,module.currentLifecycleSummary())))
            .then(json("environment",c->say(c,module.inspectEnvironment(new Gson().fromJson(value(c),GreenhouseEnvironmentCensus.Request.class)).summary())))
            .then(ClientCommands.literal("effects").executes(c->say(c,module.capturedEffectsSummary())))
            .then(ClientCommands.literal("recipes").executes(c->say(c,module.historicalRecipeDiagnostic()))
                .then(ClientCommands.argument("filter",StringArgumentType.word()).executes(c->say(c,module.recipeList(StringArgumentType.getString(c,"filter"))))))
            .then(ClientCommands.literal("recipe").then(ClientCommands.argument("name",StringArgumentType.greedyString()).executes(c->say(c,module.recipeDetails(StringArgumentType.getString(c,"name"))))))
            .then(ClientCommands.literal("context").then(ClientCommands.argument("profile",StringArgumentType.word()).then(ClientCommands.argument("plot",StringArgumentType.word()).executes(c->{module.setContext(StringArgumentType.getString(c,"profile"),StringArgumentType.getString(c,"plot"));return say(c,"Local expected profile/plot selected; refreshed native diagnostics required");}))))
            .then(json("diagnostic",c->{module.selectDiagnostic(new Gson().fromJson(value(c),GreenhouseDiagnosticAssociation.NativeInspection.class).selection());return say(c,"One native inspection selected from local scope and observed crop/tool metadata; hold its tool and target ray, then start. The received menu is isolated to this interaction; it has no server target echo.");}))
            .then(json("echo-diagnostic",c->{module.selectDiagnostic(new Gson().fromJson(value(c),GreenhouseDiagnosticAssociation.Selection.class));return say(c,"Explicit legacy echoed receipt contract selected; no native server echo is assumed.");}))
            .then(json("bindings",c->{module.setNativeBindings(value(c));return say(c,"Exact native bindings prepared; open actual diagnostic menu, then /vgreenhouse capture");}))
            .then(json("guide",c->{var result=module.inspectGuide(new Gson().fromJson(value(c),GreenhouseGuide.Binding.class));return say(c,result.map(Object::toString).orElse("Current Guide recipe UNKNOWN; exact visible menu count/proximity lines required"));}))
            .then(json("combination",c->say(c,module.validateCombination(new Gson().fromJson(value(c),GreenhouseCombinations.ObservedPattern.class)).toString())))
            .then(json("plan",c->{module.selectPlan(new Gson().fromJson(value(c),GreenhouseRuntime.Plan.class));return say(c,"Explicit cultivation plan selected. Review /vgreenhouse check, then start");}))
            .then(ClientCommands.literal("check").executes(c->say(c,module.preStartCheck().orElse("Plan is locally ready; actions still require independent native receipts"))))
            .then(ClientCommands.literal("start").executes(c->start.getAsBoolean()?1:0))
            .then(ClientCommands.literal("stop").executes(c->{module.setEnabled(false);return say(c,module.diagnostics());}))
            .then(ClientCommands.literal("clear").executes(c->{module.clearPlan();return say(c,"Prepared plan cleared; unresolved action journal retained");}))));
    }
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> water(GreenhouseModule m){
        return ClientCommands.literal("water").then(ClientCommands.argument("x",IntegerArgumentType.integer()).then(ClientCommands.argument("y",IntegerArgumentType.integer()).then(ClientCommands.argument("z",IntegerArgumentType.integer()).then(ClientCommands.argument("toolSlot",IntegerArgumentType.integer(0,8)).then(ClientCommands.argument("resourceSlot",IntegerArgumentType.integer(0,40)).then(ClientCommands.argument("units",IntegerArgumentType.integer(1,64)).executes(c->{try{m.prepareWater(cell(c),IntegerArgumentType.getInteger(c,"toolSlot"),IntegerArgumentType.getInteger(c,"resourceSlot"),IntegerArgumentType.getInteger(c,"units"));return say(c,"One watering action prepared from real diagnostics/inventory. Review check, then start");}catch(RuntimeException invalid){return fail(c,invalid);}})))))));
    }
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> reconcile(GreenhouseModule m){
        return ClientCommands.literal("reconcile").then(ClientCommands.argument("x",IntegerArgumentType.integer()).then(ClientCommands.argument("y",IntegerArgumentType.integer()).then(ClientCommands.argument("z",IntegerArgumentType.integer()).then(ClientCommands.argument("decision",StringArgumentType.word()).executes(c->say(c,m.reconcile(cell(c),StringArgumentType.getString(c,"decision"))?"Refreshed cell reconciled by user; previous plan cleared":"Refresh Diagnostics, close menu, and use applied or not-applied for the exact unresolved cell"))))));
    }
    private static GreenhouseModel.Cell cell(CommandContext<FabricClientCommandSource> c){return new GreenhouseModel.Cell(IntegerArgumentType.getInteger(c,"x"),IntegerArgumentType.getInteger(c,"y"),IntegerArgumentType.getInteger(c,"z"));}
    private static int fail(CommandContext<FabricClientCommandSource> c,RuntimeException error){c.getSource().sendError(Component.literal("Greenhouse: "+error.getMessage()));return 0;}
    private interface Run {int run(CommandContext<FabricClientCommandSource> c);}
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> json(String name,Run run){return ClientCommands.literal(name).then(ClientCommands.argument("json",StringArgumentType.greedyString()).executes(c->{try{return run.run(c);}catch(RuntimeException invalid){c.getSource().sendError(Component.literal("Greenhouse: "+invalid.getMessage()));return 0;}}));}
    private static String value(CommandContext<FabricClientCommandSource> c){return StringArgumentType.getString(c,"json");}
    private static int say(CommandContext<FabricClientCommandSource> c,String text){c.getSource().sendFeedback(Component.literal(text));return 1;}
}
