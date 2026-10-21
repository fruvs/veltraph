package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.veltraph.client.navigation.GroundPhysicsNativeRevisions;
import dev.veltraph.client.navigation.GroundNativePhysicsProfile;
import dev.veltraph.client.navigation.LegacyTravelRetirement;
import dev.veltraph.client.navigation.mc.GroundOwnedSprintCarrier;
import dev.veltraph.client.navigation.mc.GroundOwnedSpeedRefreshCarrier;
import dev.veltraph.client.navigation.mc.GroundOwnedNativeTickCarrier;
import dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LocalPlayer.class)
public abstract class GroundOwnedSprintMixin implements GroundOwnedSprintCarrier {
    @Unique private LegacyTravelPublicationLease.Leg.ProfileInputFrame veltraph$frame;
    @Unique private Observation veltraph$observed;
    @Unique private boolean veltraph$ai,veltraph$argument,veltraph$superSeen;
    @Unique private int veltraph$calls;
    @Unique private float veltraph$travelSpeed;
    @Unique private SpeedState veltraph$before;
    @Unique private Object veltraph$tickIdentity;
    @Unique private byte veltraph$beforeFlags,veltraph$nativeSprintFlags;
    @Unique private Object veltraph$flagsItem;
    @Unique private GroundPhysicsNativeRevisions.Token veltraph$beforePlayer,veltraph$beforeMovement;
    @Unique private LocalPlayer veltraph$local(){var c=Minecraft.getInstance();return c!=null&&c.isSameThread()
        &&c.player==(Object)this&&c.player.getClass()==LocalPlayer.class?c.player:null;}
    @Unique private SpeedState veltraph$speed(LocalPlayer p){var a=p.getAttribute(Attributes.MOVEMENT_SPEED);
        return new SpeedState(a.getBaseValue(),a.getValue(),p.getSpeed(),a.getModifiers().stream().map(m->
            new GroundNativePhysicsProfile.Modifier(m.id().toString(),m.amount(),m.operation().ordinal())).toList(),p.isSprinting());}
    @Unique private boolean veltraph$issued(LocalPlayer p){return p!=null&&veltraph$frame!=null&&veltraph$frame.nativeCurrent()
        &&(long)p.tickCount-veltraph$frame.issuedTick()==1;}
    @Unique private boolean veltraph$keys(LocalPlayer p){if(!veltraph$issued(p))return false;var k=p.input.keyPresses;var f=veltraph$frame;
        return k.forward()==f.held(LegacyTravelRetirement.Key.FORWARD)&&k.backward()==f.held(LegacyTravelRetirement.Key.BACK)
            &&k.left()==f.held(LegacyTravelRetirement.Key.LEFT)&&k.right()==f.held(LegacyTravelRetirement.Key.RIGHT)
            &&k.jump()==f.held(LegacyTravelRetirement.Key.JUMP)&&k.shift()==f.held(LegacyTravelRetirement.Key.SNEAK)
            &&k.sprint()==f.held(LegacyTravelRetirement.Key.SPRINT);}
    public boolean expectOwnedSprintFrame(LegacyTravelPublicationLease.Leg.ProfileInputFrame f){var p=veltraph$local();
        if(p==null||veltraph$ai||f==null||!f.nativeCurrent()||p.tickCount!=f.issuedTick())return false;
        veltraph$frame=f;veltraph$observed=null;return true;}
    public Observation ownedSprintObservation(){return veltraph$observed;}
    public boolean ownsSprintObservation(Observation o){return o!=null&&o==veltraph$observed&&o.frame()==veltraph$frame;}
    @Inject(method="aiStep()V",at=@At("HEAD"),require=1)
    private void veltraph$begin(CallbackInfo ci){var p=veltraph$local();veltraph$observed=null;veltraph$tickIdentity=p!=null&&(Object)p instanceof GroundOwnedNativeTickCarrier tick?tick.nativeOwnedAiStepIdentity():null;
        veltraph$ai=veltraph$issued(p)&&veltraph$tickIdentity!=null;
        veltraph$calls=0;veltraph$superSeen=false;veltraph$travelSpeed=Float.NaN;
        if(veltraph$ai){var flags=GroundSharedFlagsAccessor.veltraph$nativeSharedFlags();
            veltraph$beforeFlags=p.getEntityData().get(flags);
            veltraph$flagsItem=((GroundSharedFlagsItemInvoker)(Object)p.getEntityData()).veltraph$nativeSharedItem(flags);
            veltraph$before=veltraph$speed(p);veltraph$beforePlayer=GroundPhysicsNativeRevisions.watch(p);
            veltraph$beforeMovement=GroundPhysicsNativeRevisions.watch(p.getAttribute(Attributes.MOVEMENT_SPEED));}}
    @WrapOperation(method="aiStep()V",at=@At(value="INVOKE",target="Lnet/minecraft/client/player/LocalPlayer;setSprinting(Z)V"),require=4)
    private void veltraph$normalSprint(LocalPlayer receiver,boolean sprint,Operation<Void> original){
        if(!veltraph$ai||receiver!=(Object)this||!veltraph$keys(receiver)||++veltraph$calls!=1)veltraph$ai=false;
        veltraph$argument=sprint;
        original.call(receiver,sprint); // Never manufactures a call, cancels, or substitutes arguments.
        if(veltraph$ai)veltraph$nativeSprintFlags=receiver.getEntityData().get(GroundSharedFlagsAccessor.veltraph$nativeSharedFlags());
    }
    @WrapOperation(method="aiStep()V",at=@At(value="INVOKE",target="Lnet/minecraft/client/player/AbstractClientPlayer;aiStep()V"),require=1)
    private void veltraph$superAi(LocalPlayer receiver,Operation<Void> original){var p=veltraph$local();
        if(veltraph$ai&&receiver==(Object)this&&!veltraph$superSeen&&veltraph$keys(p)){
            veltraph$superSeen=true;veltraph$travelSpeed=p.getSpeed();}else veltraph$ai=false;
        original.call(receiver);
    }
    @Inject(method="aiStep()V",at=@At("RETURN"),require=1)
    private void veltraph$complete(CallbackInfo ci){var p=veltraph$local();
        if(veltraph$ai&&veltraph$superSeen&&veltraph$keys(p)
                &&(Object)p instanceof GroundOwnedNativeTickCarrier tick&&tick.nativeOwnedAiStepIdentity()==veltraph$tickIdentity
                &&veltraph$beforePlayer!=null&&veltraph$beforeMovement!=null
                &&(Object)p instanceof GroundOwnedSpeedRefreshCarrier refreshCarrier){
            var refresh=refreshCarrier.ownedSpeedRefresh();var after=veltraph$speed(p);
            var afterPlayer=GroundPhysicsNativeRevisions.watch(p);var modifier=GroundSprintModifierAccessor.veltraph$nativeSprintModifier();
            if(refresh!=null&&refresh.tick()==p.tickCount&&refresh.afterRevision().sameRevision(afterPlayer)
                &&Float.floatToIntBits(refresh.before())==Float.floatToIntBits(veltraph$travelSpeed)
                &&Float.floatToIntBits(refresh.after())==Float.floatToIntBits(after.field()))
                veltraph$observed=new Observation(veltraph$frame,p.tickCount,veltraph$beforePlayer,afterPlayer,
                    veltraph$beforeMovement,GroundPhysicsNativeRevisions.watch(p.getAttribute(Attributes.MOVEMENT_SPEED)),
                    veltraph$before,after,veltraph$travelSpeed,veltraph$argument,veltraph$calls,modifier,modifier.id(),
                    veltraph$beforeFlags,p.getEntityData().get(GroundSharedFlagsAccessor.veltraph$nativeSharedFlags()),veltraph$nativeSprintFlags,
                    GroundSharedFlagsAccessor.veltraph$nativeSharedFlags(),veltraph$flagsItem);
        }
        veltraph$ai=false;
    }
}
