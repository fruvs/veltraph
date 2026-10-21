package dev.veltraph.client.mixin;
import dev.veltraph.client.movement.device.*;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
@Mixin(MultiPlayerGameMode.class)
public abstract class OwnedDeviceNativeUseMixin implements OwnedNativeUseCarrier {
 @Unique private NativeUseInvocation ownedUse$current;@Unique private long ownedUse$ordinal;@Unique private boolean ownedUse$exhausted;
 private static final class NativeUseInvocation {
  final Object owner;final Player player;final InteractionHand hand;final long ordinal;McInstantTransmission.OwnedUseAttempt attempt;ServerboundUseItemPacket packet;
  private NativeUseInvocation(Object owner,Player player,InteractionHand hand,long ordinal){this.owner=owner;this.player=player;this.hand=hand;this.ordinal=ordinal;}
 }
 public Object ownedUseInvocation(){return ownedUse$current;}
 public boolean ownedUseInvocationMatches(Object exact,Player player,InteractionHand hand){return exact!=null&&exact==ownedUse$current&&ownedUse$current.player==player&&ownedUse$current.hand==hand;}
 public boolean ownedUseInvocationCurrent(Object exact){return exact!=null&&exact==ownedUse$current&&!ownedUse$exhausted&&ownedUse$current.ordinal==ownedUse$ordinal;}
 public McInstantTransmission.OwnedUseAttempt ownedUseAttempt(Object exact){return exact instanceof NativeUseInvocation invocation&&invocation.owner==this?invocation.attempt:null;}
 public ServerboundUseItemPacket ownedUseConstructedPacket(Object exact){return exact instanceof NativeUseInvocation invocation&&invocation.owner==this?invocation.packet:null;}
 @WrapMethod(method="useItem(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",require=1)
 private InteractionResult ownedUse$nativeInvocation(Player player,InteractionHand hand,Operation<InteractionResult> original){
  var previous=ownedUse$current;if(ownedUse$ordinal==Long.MAX_VALUE)ownedUse$exhausted=true;else ownedUse$ordinal++;
  var actual=new NativeUseInvocation(this,player,hand,ownedUse$ordinal);ownedUse$current=actual;
  try{actual.attempt=NativeOwnedDeviceUseBoundary.claimActualNativeUse((MultiPlayerGameMode)(Object)this,actual,player,hand);return original.call(player,hand);}
  finally{if(ownedUse$current==actual)ownedUse$current=previous;}
 }
 @WrapOperation(method="lambda$useItem$0",at=@At(value="NEW",target="(Lnet/minecraft/world/InteractionHand;IFF)Lnet/minecraft/network/protocol/game/ServerboundUseItemPacket;"),require=1)
 private ServerboundUseItemPacket ownedUse$actualConstructor(InteractionHand hand,int sequence,float yaw,float pitch,Operation<ServerboundUseItemPacket> original){
  var packet=original.call(hand,sequence,yaw,pitch);var actual=ownedUse$current;
  if(actual!=null&&actual.attempt!=null&&actual.packet==null)actual.packet=packet;return packet;
 }
 @WrapOperation(method="lambda$useItem$0",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/ItemStack;use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"),require=1)
 private InteractionResult ownedUse$localPrediction(ItemStack stack,Level level,Player player,InteractionHand hand,Operation<InteractionResult> original){
  var actual=ownedUse$current;
  if(actual!=null&&actual.attempt!=null&&!NativeOwnedDeviceUseBoundary.beforeLocalUse((MultiPlayerGameMode)(Object)this,actual,stack,level,player,hand))return InteractionResult.PASS;
  return original.call(stack,level,player,hand);
 }
}
