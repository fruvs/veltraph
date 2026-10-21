package dev.veltraph.client.movement.device;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
/** Actual native mixin private invocation and constructor facts. There is no public origin/packet setter. */
public interface OwnedNativeUseCarrier {
 Object ownedUseInvocation();boolean ownedUseInvocationMatches(Object exact,Player player,InteractionHand hand);
 boolean ownedUseInvocationCurrent(Object exact);
 McInstantTransmission.OwnedUseAttempt ownedUseAttempt(Object exact);
 ServerboundUseItemPacket ownedUseConstructedPacket(Object exact);
}
