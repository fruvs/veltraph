package dev.veltraph.client.combat.heal.mc;

import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import dev.veltraph.client.combat.heal.CapturedHudVitals;
import dev.veltraph.client.service.mc.NativeActionBarSignal;

/** Passive intake of already received overlays; no packet sender or gameplay action. */
public final class McCapturedHudVitals {
    private static final CapturedHudVitals vitals=new CapturedHudVitals();
    private static NativeActionBarSignal.Subscription subscription;
    private static CapturedHudVitals.Scope scope;
    private static long epoch;
    private static java.lang.ref.WeakReference<Minecraft> installedClient=new java.lang.ref.WeakReference<>(null);
    private McCapturedHudVitals() {}
    public static Optional<CapturedHudVitals.Fields> parseNative(Component component,NativeActionBarSignal.Origin origin){
        if(origin!=NativeActionBarSignal.Origin.SYSTEM_OVERLAY||component==null||!component.getStyle().isEmpty()
                ||!component.getSiblings().isEmpty()||!(component.getContents() instanceof PlainTextContents.LiteralContents literal))return Optional.empty();
        return CapturedHudVitals.parse(literal.text());
    }
    private static CapturedHudVitals.Scope context(Minecraft client){
        if(client==null||!client.isSameThread()){return null;}
        var game=dev.veltraph.client.VeltraphBootstrap.gameContext();
        var resolved=game==null?dev.veltraph.client.game.GameContext.NONE:game.current();
        boolean eligible=client!=null&&client.isSameThread()&&client.player!=null&&client.level!=null&&client.getConnection()!=null
                &&client.player.level()==client.level&&client.level.getChunkSource().hasChunk(client.player.blockPosition().getX()>>4,client.player.blockPosition().getZ()>>4)
                &&resolved.onHypixel()&&resolved.inSkyBlock();
        if(!eligible){scope=null;vitals.bind(null);return null;}
        if(scope==null||!scope.valid()||scope.player().get()!=client.player||scope.world().get()!=client.level||scope.connection().get()!=client.getConnection()){
            scope=CapturedHudVitals.Scope.of(client.player,client.level,client.getConnection(),++epoch);vitals.bind(scope);
        }
        return scope;
    }
    public static void install(Minecraft client){
        if(client==null||!client.isSameThread())return;
        if(subscription!=null&&installedClient.get()==client)return;
        if(subscription!=null)subscription.close();
        scope=null;vitals.bind(null);installedClient=new java.lang.ref.WeakReference<>(client);
        subscription=NativeActionBarSignal.subscribe(receipt->{
            if(!receipt.current(client))return;var current=context(client);if(current==null)return;
            vitals.receive(current,parseNative(receipt.component(),receipt.origin()),receipt.serial(),receipt.receivedAt(),client.player.isAlive()&&!client.player.isDeadOrDying());
        });
    }
    public static Optional<CapturedHudVitals.Reading> current(Minecraft client,long now){
        install(client);var current=context(client);if(current==null)return Optional.empty();
        return vitals.read(current,now,client.player.isAlive()&&!client.player.isDeadOrDying());
    }
}
