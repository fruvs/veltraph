package dev.veltraph.client.combat.grind;

import dev.veltraph.client.scheduling.ScheduledPausePort;
import dev.veltraph.client.service.mc.McHeldKeys;
import dev.veltraph.client.inventory.NativeStackIdentity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import java.util.*;
import java.util.function.*;

/** Native combat lease; arrival proof is supplied by root before rebind, never a lifecycle restart. */
public final class CombatScheduledPause implements ScheduledPausePort {
    private final Minecraft client;private final BooleanSupplier enabled;private final Supplier<Optional<String>> readiness;
    private final Supplier<String> signature;private final Runnable release,rebind,onResume,stop;
    private Object token;private boolean rebound;private Snapshot saved;
    private record Snapshot(Object player,Object world,Object connection,UUID id,String dimension,Pose posture,
                            net.minecraft.world.phys.Vec3 position,float yaw,float pitch,int slot,NativeStackIdentity.Snapshot tool,
                            Map<EquipmentSlot,NativeStackIdentity.Snapshot> gear,String signature){}
    public CombatScheduledPause(Minecraft client,BooleanSupplier enabled,Supplier<Optional<String>> readiness,
                                Supplier<String> signature,Runnable release,Runnable rebind,Runnable onResume,Runnable stop){
        this.client=Objects.requireNonNull(client);this.enabled=Objects.requireNonNull(enabled);this.readiness=Objects.requireNonNull(readiness);
        this.signature=Objects.requireNonNull(signature);this.release=Objects.requireNonNull(release);this.rebind=Objects.requireNonNull(rebind);this.onResume=Objects.requireNonNull(onResume);this.stop=Objects.requireNonNull(stop);
    }
    private void thread(){if(!client.isSameThread())throw new IllegalStateException("Combat pause requires client thread");}
    public boolean paused(){return token!=null;}
    public Optional<String> readiness(){thread();if(paused()||!enabled.getAsBoolean())return Optional.of("Combat owner is stopped or already paused");var check=readiness.get();return check==null?Optional.of("Explicit combat readiness proof unavailable"):check;}
    @Override public boolean pause(Object ticket){
        thread();if(ticket==null||readiness().isPresent()||!nativeSafe())return false;
        String settings=signature.get();if(settings==null||settings.isBlank()||settings.contains("unavailable route proof"))return false;
        var p=client.player;var gear=new EnumMap<EquipmentSlot,NativeStackIdentity.Snapshot>(EquipmentSlot.class);
        try {
            var registry=client.level.registryAccess();for(var slot:EquipmentSlot.values())gear.put(slot,NativeStackIdentity.capture(p.getItemBySlot(slot),registry));
            saved=new Snapshot(p,client.level,client.getConnection(),p.getUUID(),client.level.dimension().identifier().toString(),p.getPose(),p.position(),p.getYRot(),p.getXRot(),p.getInventory().getSelectedSlot(),NativeStackIdentity.capture(p.getMainHandItem(),registry),Map.copyOf(gear),settings);
        } catch(RuntimeException unsupported){saved=null;return false;}
        token=ticket;rebound=false;
        try{release.run();return owns(ticket);}catch(RuntimeException failure){invalidate();stop.run();return false;}
    }
    /** Local protective native-health floor; source action-bar/engine readiness can be stricter. */
    private boolean nativeSafe(){return client.player!=null&&client.level!=null&&client.getConnection()!=null&&client.gameMode!=null&&client.player.isAlive()
            &&Float.isFinite(client.player.getHealth())&&Float.isFinite(client.player.getMaxHealth())&&client.player.getMaxHealth()>0
            &&client.player.getHealth()/client.player.getMaxHealth()>.5f&&!client.player.isSwinging()
            &&client.player.onGround()&&(client.player.getPose()==Pose.STANDING||client.player.getPose()==Pose.CROUCHING)
            &&client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty();}
    @Override public boolean owns(Object ticket){return ticket!=null&&token==ticket&&enabled.getAsBoolean();}
    /** Root polls this after matched physical arrival and before permitting any native-ref mutation. */
    public Optional<String> arrivalReadiness(){
        thread();if(!paused()||!enabled.getAsBoolean()||saved==null)return Optional.of("No enabled owned combat pause awaits arrival");
        if(!proof()||client.player==saved.player()||client.level==saved.world()||client.getConnection()==saved.connection())return Optional.of("Fresh healthy settled native combat arrival/tool/config proof is incomplete");
        return Optional.empty();
    }
    private boolean proof(){
        if(!nativeSafe()||saved==null)return false;var p=client.player;
        if(!p.getUUID().equals(saved.id())||!client.level.dimension().identifier().toString().equals(saved.dimension())||p.getPose()!=saved.posture()
                ||p.getInventory().getSelectedSlot()!=saved.slot()||!saved.tool().matches(p.getMainHandItem(),client.level.registryAccess())||!Objects.equals(signature.get(),saved.signature())
                ||p.position().distanceTo(saved.position())>=.35||Math.abs(dev.veltraph.client.navigation.WalkController.wrap(p.getYRot()-saved.yaw()))>=1.5
                ||Math.abs(p.getXRot()-saved.pitch())>=1.5||p.getDeltaMovement().length()>=.1||p.isUsingItem())return false;
        for(var slot:EquipmentSlot.values())if(!saved.gear().get(slot).matches(p.getItemBySlot(slot),client.level.registryAccess()))return false;
        return true;
    }
    @Override public boolean rebindAfterOwnedArrival(Object ticket){
        thread();if(!owns(ticket)||rebound||arrivalReadiness().isPresent())return false;
        rebind.run();if(!owns(ticket)||!proof())return false;rebound=true;return true;
    }
    @Override public boolean resume(Object ticket){
        thread();if(!owns(ticket)||!rebound||!proof())return false;
        onResume.run();invalidate();return enabled.getAsBoolean();
    }
    @Override public void stop(Object ticket){thread();if(owns(ticket)){invalidate();stop.run();}}
    /** Manual disable invalidates the old ticket without changing any other controller. */
    public void invalidate(){token=null;saved=null;rebound=false;}
    /** Release held inputs once at acquisition; paused ticks never compete with root's return navigator. */
    public static void releaseKeys(Minecraft client){for(var key:new net.minecraft.client.KeyMapping[]{client.options.keyUse,client.options.keyAttack,client.options.keyUp,client.options.keyDown,client.options.keyLeft,client.options.keyRight,client.options.keyJump,client.options.keyShift,client.options.keySprint})McHeldKeys.setHeld(key,false);}
}
