package dev.veltraph.client.mixin;

import dev.veltraph.client.navigation.mc.GroundProfileMotionCarrier;
import dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease;
import dev.veltraph.client.navigation.mc.GroundOwnedNativeTickCarrier;
import dev.veltraph.client.navigation.LegacyTravelRetirement;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Passive original aiStep/jump/travel observations. Only this mixin stores native step identity. */
@Mixin(LivingEntity.class)
public abstract class GroundProfileNativeMotionMixin implements GroundProfileMotionCarrier {
    @Unique private LegacyTravelPublicationLease.Leg.ProfileInputFrame veltraph$expected;
    @Unique private Object veltraph$nativeBaseline,veltraph$tickIdentity;
    @Unique private boolean veltraph$aiStep,veltraph$inTravel;
    @Unique private int veltraph$jumpCalls,veltraph$travelCalls;
    @Unique private NativePose veltraph$before,veltraph$aiBefore;
    @Unique private Vec3 veltraph$travelInput;
    @Unique private ObservedStep veltraph$latest;
    @Unique private LocalPlayer veltraph$local(){var c=Minecraft.getInstance();return c!=null&&c.isSameThread()
            &&c.player!=null&&(Object)c.player==(Object)this&&c.player.getClass()==LocalPlayer.class?c.player:null;}
    @Unique private NativePose veltraph$pose(LocalPlayer p){var v=p.getDeltaMovement();
        return new NativePose(new Vec3(p.getX(),p.getY(),p.getZ()),new Vec3(v.x,v.y,v.z),p.getYRot(),p.onGround(),p.isSprinting(),p.tickCount,p.getSpeed());}
    @Unique private boolean veltraph$issued(LocalPlayer p){return p!=null&&veltraph$expected!=null&&veltraph$expected.nativeCurrent()
            &&(long)p.tickCount-veltraph$expected.issuedTick()==1;}
    public boolean expectProfileFrame(LegacyTravelPublicationLease.Leg.ProfileInputFrame frame){
        var p=veltraph$local();if(p==null||frame==null||!frame.nativeCurrent()||p.tickCount!=frame.issuedTick()
                ||veltraph$aiStep||veltraph$inTravel)return false;
        veltraph$expected=frame;veltraph$nativeBaseline=new Object();veltraph$latest=null;return true;}
    public LegacyTravelPublicationLease.Leg.ProfileInputFrame expectedProfileFrame(){return veltraph$expected;}
    public Object profileNativeBaseline(){return veltraph$nativeBaseline;}
    public ObservedStep profileNativeAfter(Object baseline){return baseline!=null&&baseline==veltraph$nativeBaseline?veltraph$latest:null;}
    public boolean ownsProfileStep(ObservedStep step){return step!=null&&step==veltraph$latest
            &&step.nativeIdentity()==veltraph$nativeBaseline&&step.issuedInputs()==veltraph$expected;}
    @Inject(method="aiStep()V",at=@At("HEAD"),require=1)
    private void veltraph$actualAiBegin(CallbackInfo ci){var p=veltraph$local();
        veltraph$tickIdentity=p!=null&&(Object)p instanceof GroundOwnedNativeTickCarrier tick?tick.nativeOwnedAiStepIdentity():null;
        veltraph$aiStep=veltraph$issued(p)&&veltraph$tickIdentity!=null;
        veltraph$jumpCalls=0;veltraph$travelCalls=0;veltraph$inTravel=false;veltraph$before=null;veltraph$travelInput=null;
        veltraph$aiBefore=veltraph$aiStep&&p!=null?veltraph$pose(p):null;}
    @Inject(method="jumpFromGround()V",at=@At("RETURN"),require=1)
    private void veltraph$actualJump(CallbackInfo ci){var p=veltraph$local();if(veltraph$aiStep&&veltraph$issued(p))veltraph$jumpCalls++;}
    @Inject(method="travel(Lnet/minecraft/world/phys/Vec3;)V",at=@At("HEAD"),require=1)
    private void veltraph$actualTravelBegin(net.minecraft.world.phys.Vec3 input,CallbackInfo ci){
        var p=veltraph$local();if(!veltraph$aiStep||!veltraph$issued(p)||veltraph$inTravel||++veltraph$travelCalls!=1){veltraph$before=null;veltraph$latest=null;return;}
        var actual=p.input.keyPresses;var keys=veltraph$expected;
        if(actual.forward()!=keys.held(LegacyTravelRetirement.Key.FORWARD)||actual.backward()!=keys.held(LegacyTravelRetirement.Key.BACK)
                ||actual.left()!=keys.held(LegacyTravelRetirement.Key.LEFT)||actual.right()!=keys.held(LegacyTravelRetirement.Key.RIGHT)
                ||actual.jump()!=keys.held(LegacyTravelRetirement.Key.JUMP)||actual.shift()!=keys.held(LegacyTravelRetirement.Key.SNEAK)
                ||actual.sprint()!=keys.held(LegacyTravelRetirement.Key.SPRINT)){veltraph$before=null;return;}
        veltraph$inTravel=true;veltraph$before=veltraph$aiBefore;veltraph$travelInput=new Vec3(input.x,input.y,input.z);}
    @Inject(method="travel(Lnet/minecraft/world/phys/Vec3;)V",at=@At("RETURN"),require=1)
    private void veltraph$actualTravelReturned(net.minecraft.world.phys.Vec3 input,CallbackInfo ci){
        var p=veltraph$local();if(veltraph$inTravel&&veltraph$before!=null&&veltraph$issued(p)
                &&(Object)p instanceof GroundOwnedNativeTickCarrier tick&&tick.nativeOwnedAiStepIdentity()==veltraph$tickIdentity
                &&veltraph$jumpCalls<=1&&(!veltraph$expected.held(LegacyTravelRetirement.Key.JUMP)
                    ||veltraph$expected.actualPress(LegacyTravelRetirement.Key.JUMP)))
            veltraph$latest=new ObservedStep(veltraph$nativeBaseline,veltraph$expected,veltraph$before,veltraph$pose(p),veltraph$travelInput,veltraph$jumpCalls);
        veltraph$inTravel=false;}
    @Inject(method="aiStep()V",at=@At("RETURN"),require=1)
    private void veltraph$actualAiReturned(CallbackInfo ci){veltraph$aiStep=false;veltraph$inTravel=false;}
}
