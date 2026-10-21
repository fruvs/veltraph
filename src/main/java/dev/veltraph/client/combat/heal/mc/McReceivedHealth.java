package dev.veltraph.client.combat.heal.mc;

import java.util.*;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import dev.veltraph.client.combat.heal.CapturedHudVitals;
import dev.veltraph.client.combat.heal.HealthObservation;
import dev.veltraph.client.service.mc.NativeActionBarSignal;

/** Only already received legacy overlay/native health metadata. No getter is promoted into a receipt. */
public final class McReceivedHealth {
    private static final String NUMBER="(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]+)?";
    private static final Pattern LEGACY=Pattern.compile("^(?:§[c6])?("+NUMBER+")\\s*/\\s*("+NUMBER+")\\s*([❤♥])(?:\\s.*)?$");
    public record Legacy(double health,double maximum,char glyph) {}
    private static CapturedHudVitals.Scope scope;
    private static HealthObservation legacy,nativeHealth;
    private static long epoch,serial,lastRead=-1;
    private static NativeActionBarSignal.Subscription subscription;
    private static java.lang.ref.WeakReference<Minecraft> installed=new java.lang.ref.WeakReference<>(null);
    private McReceivedHealth() {}
    public static Optional<Legacy> parseLegacy(Component component){
        if(component==null||!component.getStyle().isEmpty()||!component.getSiblings().isEmpty()||!(component.getContents() instanceof PlainTextContents.LiteralContents literal))return Optional.empty();
        String text=literal.text();if(text.length()>512||text.chars().filter(c->c=='❤'||c=='♥').count()!=1)return Optional.empty();var m=LEGACY.matcher(text);if(!m.matches())return Optional.empty();
        try{double hp=Double.parseDouble(m.group(1).replace(",","")),max=Double.parseDouble(m.group(2).replace(",",""));return Double.isFinite(hp)&&Double.isFinite(max)&&hp>=0&&max>0?Optional.of(new Legacy(hp,max,m.group(3).charAt(0))):Optional.empty();}catch(NumberFormatException invalid){return Optional.empty();}
    }
    private static CapturedHudVitals.Scope context(Minecraft client){
        if(client==null||!client.isSameThread())return null;
        if(client.player==null||client.level==null||client.getConnection()==null||client.player.level()!=client.level
                ||!client.level.getChunkSource().hasChunk(client.player.blockPosition().getX()>>4,client.player.blockPosition().getZ()>>4)){
            scope=null;legacy=null;nativeHealth=null;return null;
        }
        if(scope==null||!scope.valid()||scope.player().get()!=client.player||scope.world().get()!=client.level||scope.connection().get()!=client.getConnection()){
            scope=CapturedHudVitals.Scope.of(client.player,client.level,client.getConnection(),++epoch);legacy=null;nativeHealth=null;
        }return scope;
    }
    public static void install(Minecraft client){
        if(client==null||!client.isSameThread())return;if(subscription!=null&&installed.get()==client)return;
        if(subscription!=null)subscription.close();scope=null;legacy=null;nativeHealth=null;installed=new java.lang.ref.WeakReference<>(client);
        subscription=NativeActionBarSignal.subscribe(receipt->{
            if(!receipt.current(client))return;var current=context(client);if(current==null)return;
            var game=dev.veltraph.client.VeltraphBootstrap.gameContext();var resolved=game==null?dev.veltraph.client.game.GameContext.NONE:game.current();
            var parsed=parseLegacy(receipt.component());
            if(!resolved.onHypixel()||!resolved.inSkyBlock()||parsed.isEmpty()||receipt.receivedAt()<lastRead){legacy=null;return;}
            var value=parsed.get();legacy=new HealthObservation(HealthObservation.Format.LEGACY_HEART,"LEGACY_"+receipt.origin()+"_"+value.glyph(),value.health(),value.maximum(),0,current,receipt.serial(),receipt.receivedAt());
        });
    }
    /** TAIL of actual SetHealth/local-player SetEntityData, after vanilla's main-thread guard/update. */
    public static boolean observeNative(Minecraft client,Object listener){
        if(client==null||!client.isSameThread()||client.getConnection()!=listener||!(listener instanceof net.minecraft.client.multiplayer.ClientPacketListener packetListener)||packetListener.getLevel()!=client.level)return false;install(client);var current=context(client);if(current==null)return false;
        long at=System.currentTimeMillis();if(at<lastRead){nativeHealth=null;return false;}
        float hp=client.player.getHealth(),max=client.player.getMaxHealth(),abs=client.player.getAbsorptionAmount();
        if(!Float.isFinite(hp)||!Float.isFinite(max)||!Float.isFinite(abs)||hp<0||max<=0||abs<0){nativeHealth=null;return false;}
        nativeHealth=new HealthObservation(HealthObservation.Format.NATIVE_ENTITY,"LOCAL_NATIVE_HEALTH_FIELDS",hp,max,abs,current,++serial,at);return true;
    }
    public static Optional<HealthObservation> current(Minecraft client,long now){
        install(client);var current=context(client);if(current==null)return Optional.empty();
        if(!client.player.isAlive()||client.player.isDeadOrDying()||now<lastRead){legacy=null;nativeHealth=null;lastRead=Math.max(lastRead,now);return Optional.empty();}lastRead=now;
        var game=dev.veltraph.client.VeltraphBootstrap.gameContext();var resolved=game==null?dev.veltraph.client.game.GameContext.NONE:game.current();
        if(resolved.onHypixel()&&resolved.inSkyBlock()&&fresh(legacy,current,now))return Optional.of(legacy);
        legacy=null;
        if(fresh(nativeHealth,current,now)&&nativeHealth.health()==client.player.getHealth()&&nativeHealth.maximum()==client.player.getMaxHealth()&&nativeHealth.absorption()==client.player.getAbsorptionAmount())return Optional.of(nativeHealth);
        nativeHealth=null;return Optional.empty();
    }
    private static boolean fresh(HealthObservation value,CapturedHudVitals.Scope current,long now){return value!=null&&value.scope().same(current)&&now>=value.receivedAt()&&now-value.receivedAt()<=CapturedHudVitals.FRESHNESS_MILLIS;}
}
