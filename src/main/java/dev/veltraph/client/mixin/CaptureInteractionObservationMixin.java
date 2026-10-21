package dev.veltraph.client.mixin;

import dev.veltraph.client.capture.CaptureGuiTools;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Read-only exact native API receipt. Caller identity and remote outcome are never inferred. */
@Mixin(MultiPlayerGameMode.class)
public abstract class CaptureInteractionObservationMixin {
    @Unique private long veltraph$captureBlockUse, veltraph$captureItemUse, veltraph$captureEntityUse, veltraph$captureBlockBreak, veltraph$captureAttack;
    @Inject(method="useItemOn", at=@At("HEAD"))
    private void veltraph$captureUseBlock(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> ci) {
        veltraph$captureBlockUse = CaptureGuiTools.nativeAction("use_item_on_block", hit, null, hand.name(), null);
    }
    @Inject(method="useItemOn", at=@At("RETURN"))
    private void veltraph$captureUseBlockReturn(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> ci) {
        CaptureGuiTools.nativeActionReturned(veltraph$captureBlockUse, ci.getReturnValue().consumesAction());
    }
    @Inject(method="useItem", at=@At("HEAD"))
    private void veltraph$captureUseItem(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> ci) {
        veltraph$captureItemUse = CaptureGuiTools.nativeAction("use_item", null, null, hand.name(), null);
    }
    @Inject(method="useItem", at=@At("RETURN"))
    private void veltraph$captureUseItemReturn(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> ci) {
        CaptureGuiTools.nativeActionReturned(veltraph$captureItemUse, ci.getReturnValue().consumesAction());
    }
    @Inject(method="interact", at=@At("HEAD"))
    private void veltraph$captureEntityUse(Player player, Entity entity, EntityHitResult hit, InteractionHand hand, CallbackInfoReturnable<InteractionResult> ci) {
        veltraph$captureEntityUse = CaptureGuiTools.nativeAction("interact_entity", null, entity, hand.name(), hit);
    }
    @Inject(method="interact", at=@At("RETURN"))
    private void veltraph$captureEntityUseReturn(Player player, Entity entity, EntityHitResult hit, InteractionHand hand, CallbackInfoReturnable<InteractionResult> ci) {
        CaptureGuiTools.nativeActionReturned(veltraph$captureEntityUse, ci.getReturnValue().consumesAction());
    }
    @Inject(method="startDestroyBlock", at=@At("HEAD"))
    private void veltraph$captureDestroy(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> ci) {
        veltraph$captureBlockBreak = CaptureGuiTools.nativeBlockBreak(pos, face);
    }
    @Inject(method="startDestroyBlock", at=@At("RETURN"))
    private void veltraph$captureDestroyReturn(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> ci) {
        CaptureGuiTools.nativeActionReturned(veltraph$captureBlockBreak, null);
    }
    @Inject(method="attack", at=@At("HEAD"))
    private void veltraph$captureAttack(Player player, Entity entity, CallbackInfo ci) {
        veltraph$captureAttack = CaptureGuiTools.nativeAction("attack_entity", null, entity, "not_supplied_by_native_api", null);
    }
    @Inject(method="attack", at=@At("RETURN"))
    private void veltraph$captureAttackReturn(Player player, Entity entity, CallbackInfo ci) { CaptureGuiTools.nativeActionReturned(veltraph$captureAttack, null); }
    @Inject(method="handleContainerInput", at=@At("HEAD"))
    private void veltraph$captureMenuInput(int syncId, int slot, int button, ContainerInput input, Player player, CallbackInfo ci) { CaptureGuiTools.nativeMenuInput(); }
}
