package dev.veltraph.client.capture.tool;

import com.google.gson.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.veltraph.client.VeltraphBootstrap;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;

/** Manual local observation; never breaks blocks, uses items, sends packets or samples automatically. */
public final class CaptureSelectedToolTools {
    public static final Path OUTPUT=Path.of("config/veltraph/captures/selected-tools").toAbsolutePath().normalize();
    private static boolean registered;
    private SelectedToolCaptureSession session;private final SelectedToolCaptureScope scope=new SelectedToolCaptureScope();private byte[] salt;private final SelectedWateringToolProbe watering=new SelectedWateringToolProbe();
    public static synchronized void register(){if(registered)return;var tool=new CaptureSelectedToolTools();
        ClientCommandRegistrationCallback.EVENT.register((d,r)->d.register(literal("vcapture").then(literal("tool")
            .then(literal("start").then(argument("label",StringArgumentType.word()).executes(c->tool.start(c.getSource(),StringArgumentType.getString(c,"label")))))
            .then(literal("fields").executes(c->tool.fields(c.getSource())))
            .then(literal("allow-number").then(argument("path",StringArgumentType.greedyString()).executes(c->tool.allowNumber(c.getSource(),StringArgumentType.getString(c,"path")))))
            .then(literal("allow-lore").then(argument("line",IntegerArgumentType.integer(0,79)).executes(c->tool.allowLore(c.getSource(),IntegerArgumentType.getInteger(c,"line")))))
            .then(literal("sample").executes(c->tool.sample(c.getSource())))
            .then(literal("finish").executes(c->tool.finish(c.getSource())))
            .then(literal("off").executes(c->tool.off(c.getSource())))
            .then(literal("status").executes(c->tool.status(c.getSource()))))));
        ClientTickEvents.END_CLIENT_TICK.register(c->{try{if(tool.session!=null&&!tool.session.active()){tool.end("writer_terminal");return;}if(tool.session!=null&&tool.session.active()&&!tool.scope.current(frame(c)))tool.end("native_scope_changed");}catch(RuntimeException unavailable){tool.end("native_scope_unavailable");}});registered=true;
    }
    private static SelectedToolCaptureScope.Frame frame(Minecraft c){
        boolean manual=VeltraphBootstrap.moduleManager()!=null&&VeltraphBootstrap.moduleManager().enabledControllers().isEmpty()&&VeltraphBootstrap.moduleManager().inputOwner().isEmpty();
        var p=c.player;return new SelectedToolCaptureScope.Frame(p,c.level,c.getConnection(),c.getUser().getProfileId(),p==null?-1:p.getInventory().getSelectedSlot(),p==null?null:BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem()).toString(),p!=null&&c.level!=null&&c.getConnection()!=null&&p.containerMenu==p.inventoryMenu&&p.getGameProfile().id().equals(c.getUser().getProfileId())&&p.level()==c.level&&p.isAlive()&&c.getCameraEntity()==p&&(c.gui.screen()==null||c.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen)&&!c.isPaused(),manual);
    }
    private int start(FabricClientCommandSource source,String label){
        if(session!=null&&session.active()){source.sendError(Component.literal("Finish /vcapture tool first"));return 0;}
        var c=Minecraft.getInstance();if(!c.isSameThread()||!scope.start(frame(c))){source.sendError(Component.literal("Tool capture requires manual gameplay with a selected tool and no active macro"));return 0;}
        try{watering.clear();salt=new byte[32];new SecureRandom().nextBytes(salt);session=new SelectedToolCaptureSession(OUTPUT,label,System.currentTimeMillis());record(c,"start");source.sendFeedback(Component.literal("Selected-tool capture started. Use the selected tool manually, then /vcapture tool sample and /vcapture tool finish. No automatic sampling or game actions."));return 1;}catch(RuntimeException|java.io.IOException failure){end("capture_error");source.sendError(Component.literal("Selected-tool capture unavailable; existing files retained"));return 0;}
    }
    private int sample(FabricClientCommandSource source){try{boolean written=record(Minecraft.getInstance(),"sample");source.sendFeedback(Component.literal(written?"Selected-tool sample saved":"Selected-tool sample unchanged; no duplicate written"));return 1;}catch(RuntimeException|java.io.IOException failure){end("capture_error");source.sendError(Component.literal("Tool capture is off or its original scope changed"));return 0;}}
    private int finish(FabricClientCommandSource source){try{record(Minecraft.getInstance(),"finish");end("user_finish");return status(source);}catch(RuntimeException|java.io.IOException failure){end("scope_changed_or_capture_error");source.sendError(Component.literal("Tool capture finished without a final sample; existing files retained"));return 0;}}
    private int off(FabricClientCommandSource source){end("user_off");return status(source);}
    private int status(FabricClientCommandSource source){source.sendFeedback(Component.literal(session==null?"Selected-tool capture off":(session.active()?"Selected-tool capture active":"Selected-tool capture off: "+session.stopReason())+" | "+session.frames()+" samples | "+session.directory()));return 1;}
    private void end(String reason){try{if(session!=null&&session.active())session.stop(reason,System.currentTimeMillis());}catch(java.io.IOException ignored){}finally{scope.writerEnded(salt);salt=null;watering.clear();}}
    private record ProbeView(JsonObject frame,SelectedToolNativeObservation.WateringObservation observed) {}
    private ProbeView probeView(){
        var c=Minecraft.getInstance();
        if(!c.isSameThread()||session==null||!session.active()||!scope.current(frame(c)))throw new IllegalStateException("Manual selected-tool scope unavailable");
        var snapshot=frame(c);var ordinary=SelectedToolNativeObservation.probeIdentity(c.player.getMainHandItem());
        var encoded=SelectedToolCaptureSchema.encode(snapshot.nativeId(),snapshot.slot(),ordinary.components(),ordinary.lore(),salt);
        var observed=SelectedToolNativeObservation.readWatering(c.player.getMainHandItem(),Set.of());
        if(ordinary.truncated()||!observed.complete()||encoded.get("schemaBoundReached").getAsBoolean()||!scope.current(frame(c)))throw new IllegalStateException("Complete native selected-tool preview unavailable");
        return new ProbeView(encoded,observed);
    }
    private int fields(FabricClientCommandSource source){
        try{var v=probeView();var preview=watering.preview(v.frame(),v.observed().indexedLore());
            source.sendFeedback(Component.literal("Selected watering can numeric preview (no values):\n"+String.join("\n",preview.paths().stream().map(p->"number "+p).toList())
                +"\nNative lore indices with supported water/capacity/rate numbers: "+preview.loreIndices()+"\nChoose /vcapture tool allow-number <exact path> or allow-lore <native index>, then sample manually. No care permissions are inferred."));return 1;
        }catch(RuntimeException unsupported){end("watering_probe_unavailable");source.sendError(Component.literal("Numeric preview requires the same recognized watering can, stable item identity and complete manual scope"));return 0;}
    }
    private int allowNumber(FabricClientCommandSource source,String exact){
        try{var v=probeView();watering.chooseField(v.frame(),v.observed().indexedLore(),exact);source.sendFeedback(Component.literal("One observed numeric field selected. Run /vcapture tool sample before and after manual use."));return 1;}
        catch(RuntimeException unsupported){end("watering_probe_selection_refused");source.sendError(Component.literal("Numeric selection refused; choose a safe field from a fresh watering-can preview"));return 0;}
    }
    private int allowLore(FabricClientCommandSource source,int index){
        try{var v=probeView();watering.chooseLore(v.frame(),v.observed().indexedLore(),index);source.sendFeedback(Component.literal("One original native lore index selected. Run /vcapture tool sample before and after manual use; raw lore text is excluded."));return 1;}
        catch(RuntimeException unsupported){end("watering_probe_selection_refused");source.sendError(Component.literal("Lore selection refused; choose a supported numeric line from a fresh watering-can preview"));return 0;}
    }
    private boolean record(Minecraft c,String kind)throws java.io.IOException{
        if(!c.isSameThread()||session==null||!session.active()||!scope.current(frame(c)))throw new IllegalStateException("Original manual selected-tool scope unavailable");
        var nativeFrame=frame(c);if(!scope.current(nativeFrame))throw new IllegalStateException("Collected selected-tool scope changed");var observation=SelectedToolNativeObservation.read(c.player.getMainHandItem());
        var sanitized=SelectedToolCaptureSchema.encode(nativeFrame.nativeId(),nativeFrame.slot(),observation.components(),observation.lore(),salt);sanitized.add("nativeComponentTypes",observation.componentTypes());sanitized.addProperty("nativeObservationBoundReached",observation.truncated());sanitized.addProperty("manualEvent",kind);
        if(watering.active()){
            var identity=SelectedToolNativeObservation.probeIdentity(c.player.getMainHandItem());
            var boundIdentity=SelectedToolCaptureSchema.encode(nativeFrame.nativeId(),nativeFrame.slot(),identity.components(),identity.lore(),salt);
            if(identity.truncated()||boundIdentity.get("schemaBoundReached").getAsBoolean())throw new IllegalStateException("Native tool identity/schema is incomplete");
            watering.verifyIdentity(boundIdentity); // Admit this exact recognized can before reading any selected numeric value.
            var selected=SelectedToolNativeObservation.readWatering(c.player.getMainHandItem(),watering.selectedFields());
            sanitized.add("manualWateringNumericProbe",watering.sample(boundIdentity,selected.values(),selected.nativeTypes(),selected.indexedLore(),selected.complete()));
            var finalObservation=SelectedToolNativeObservation.probeIdentity(c.player.getMainHandItem());
            var finalIdentity=SelectedToolCaptureSchema.encode(nativeFrame.nativeId(),nativeFrame.slot(),finalObservation.components(),finalObservation.lore(),salt);
            if(finalObservation.truncated()||finalIdentity.get("schemaBoundReached").getAsBoolean())throw new IllegalStateException("Final selected tool identity is incomplete");
            watering.verifyIdentity(finalIdentity);
        }
        if(!scope.current(frame(c)))throw new IllegalStateException("Scope changed during observation");
        boolean written=session.record(sanitized,System.currentTimeMillis());
        if(!session.active()){end("writer_terminal");throw new IllegalStateException("Writer ended without recording sample");}
        return written;
    }

}
