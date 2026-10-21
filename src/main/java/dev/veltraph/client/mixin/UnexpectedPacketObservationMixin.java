package dev.veltraph.client.mixin;
import dev.veltraph.client.observation.unexpected.UnexpectedActions;
import dev.veltraph.client.observation.unexpected.UnexpectedBehaviorMonitor.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** TAIL receipts run after vanilla applied packets on the client thread; no packet content retention. */
@Mixin(ClientPacketListener.class)
public class UnexpectedPacketObservationMixin {
    @Unique private void veltraph$signalNative(Kind kind,Cause cause,long ttl) {if(Minecraft.getInstance().getConnection()==(Object)this)UnexpectedActions.nativeSignal(kind,cause,ttl);}
    @Inject(method="handleMovePlayer",at=@At("TAIL"))
    private void veltraph$position(ClientboundPlayerPositionPacket packet,CallbackInfo ci) {veltraph$signalNative(Kind.POSITION,Cause.SERVER_POSITION_PACKET,150);veltraph$signalNative(Kind.ROTATION,Cause.SERVER_POSITION_PACKET,150);if(Minecraft.getInstance().getConnection()==(Object)this)UnexpectedActions.ownedLandingPacketApplied();}
    @Inject(method="handleSetEntityMotion",at=@At("TAIL"))
    private void veltraph$velocity(ClientboundSetEntityMotionPacket packet,CallbackInfo ci) {var p=Minecraft.getInstance().player;if(p!=null&&p.getId()==packet.id())veltraph$signalNative(Kind.POSITION,Cause.VELOCITY_PACKET,1000);}
    @Inject(method="handleDamageEvent",at=@At("TAIL"))
    private void veltraph$damage(ClientboundDamageEventPacket packet,CallbackInfo ci) {var p=Minecraft.getInstance().player;if(p!=null&&p.getId()==packet.entityId())veltraph$signalNative(Kind.POSITION,Cause.DAMAGE_PACKET,1000);}
    @Inject(method="handleExplosion",at=@At("TAIL"))
    private void veltraph$explosion(ClientboundExplodePacket packet,CallbackInfo ci) {if(packet.playerKnockback().isPresent())veltraph$signalNative(Kind.POSITION,Cause.EXPLOSION_PACKET,1000);}
    @Inject(method={"handleLogin","handleRespawn"},at=@At("TAIL"))
    private void veltraph$lifecycle(CallbackInfo ci) {veltraph$signalNative(Kind.CONTEXT,Cause.JOIN_OR_RESPAWN,2000);}
    @Inject(method="handleGameEvent",at=@At("TAIL"))
    private void veltraph$mode(ClientboundGameEventPacket packet,CallbackInfo ci) {if(packet.getEvent()==ClientboundGameEventPacket.CHANGE_GAME_MODE)veltraph$signalNative(Kind.GAMEMODE,Cause.SERVER_GAME_MODE_PACKET,150);}
    @Inject(method={"handleContainerContent","handleContainerSetSlot"},at=@At("TAIL"))
    private void veltraph$inventory(CallbackInfo ci) {veltraph$signalNative(Kind.INVENTORY,Cause.SERVER_INVENTORY_PACKET,150);}
    @Inject(method="handleSetHeldSlot",at=@At("TAIL"))
    private void veltraph$selectedSlot(ClientboundSetHeldSlotPacket packet,CallbackInfo ci) {veltraph$signalNative(Kind.SELECTED_SLOT,Cause.SERVER_INVENTORY_PACKET,150);}
    @Inject(method="handleBlockUpdate",at=@At("TAIL"))
    private void veltraph$block(ClientboundBlockUpdatePacket packet,CallbackInfo ci) {var p=Minecraft.getInstance().player;if(p!=null&&(packet.getPos().equals(p.blockPosition())||packet.getPos().equals(p.blockPosition().below())))veltraph$signalNative(Kind.BLOCK,Cause.SERVER_BLOCK_PACKET,150);}
}
