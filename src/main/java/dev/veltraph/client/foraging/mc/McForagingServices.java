package dev.veltraph.client.foraging.mc;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.ForagingConfig;
import dev.veltraph.client.foraging.BlockCoord;
import dev.veltraph.client.foraging.ForagingServices;
import dev.veltraph.client.service.BlockBreakingService;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Minecraft implementation of {@link ForagingServices}: resolves the crosshair log target + reach +
 * movement and delegates the attack-key hold to the existing {@link BlockBreakingService}. All reads are
 * try/catch-degraded so a UI/mapping hiccup never crashes the client.
 *
 * <p>MC 26.3 uses the normal hit-result, LOGS tag, registry and eye-position reads. Exact registry
 * tokens or terminal log/wood/stem tokens avoid treating unrelated block names as wood.
 *
 * <p>The break uses the attack-key hold only (no {@code updateBlockBreakingProgress}) — the exact Pass-13
 * fix that avoids server-rejected ghost breaks.
 */
public final class McForagingServices implements ForagingServices {

    private final Minecraft client;
    private final ConfigService config;
    private final BlockBreakingService breaking;

    // cached this-tick resolution (resolve() fills these; the interface getters read them)
    private boolean onLog;
    private BlockCoord target;
    private double dist = Double.NaN;
    private HitResult blockedRestHit;

    public McForagingServices(Minecraft client, ConfigService config, BlockBreakingService breaking) {
        this.client = client;
        this.config = config;
        this.breaking = breaking;
    }

    @Override
    public boolean available() {
        boolean ok = client.player != null && client.player.isAlive() && client.level != null && client.gui.screen()==null;
        resolve(); // refresh the crosshair facts each time the module samples this tick
        return ok;
    }

    @Override public boolean hasAxe() {
        return client.player!=null && dev.veltraph.client.foraging.ForagingAxePolicy.isAxe(client.player.getMainHandItem().getHoverName().getString());
    }

    private void resolve() {
        onLog = false;
        target = null;
        dist = Double.NaN;
        try {
            if (client.player == null || client.level == null) {
                return;
            }
            HitResult hit = client.hitResult;
            if(hit==blockedRestHit)return;
            if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) {
                return;
            }
            BlockPos pos = bhr.getBlockPos();
            if(!client.level.getChunkSource().hasChunk(pos.getX()>>4,pos.getZ()>>4)) return;
            BlockState state = client.level.getBlockState(pos);
            String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().toLowerCase(Locale.ROOT);
            if (!state.is(BlockTags.LOGS) || !matchesLog(id)) {
                return;
            }
            onLog = true;
            target = new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
            dist = client.player.getEyePosition().distanceTo(
                    new net.minecraft.world.phys.Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        } catch (Throwable t) {
            onLog = false;
            target = null;
            dist = Double.NaN;
        }
    }

    private boolean matchesLog(String blockId) {
        return dev.veltraph.client.foraging.LogFilter.matches(blockId,config.get(ForagingConfig.FORAGING_LOG_KEYWORDS));
    }

    @Override public boolean rebindForScheduledRest(){
        onLog=false;target=null;dist=Double.NaN;blockedRestHit=client.hitResult;
        return client.player!=null&&client.player.isAlive()&&client.level!=null&&client.gui.screen()==null
                &&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty();
    }
    @Override public boolean scheduledAbilityIdle(){return client.player!=null&&!McForagingAbilitySource.cooldownActive(client);}

    @Override
    public boolean lookingAtLog() {
        return onLog;
    }

    @Override
    public BlockCoord targetLog() {
        return target;
    }

    @Override
    public double eyeDistance() {
        return dist;
    }

    @Override
    public boolean moving() {
        try {
            if (client.player == null) {
                return false;
            }
            net.minecraft.world.phys.Vec3 v = client.player.getDeltaMovement();
            return Math.abs(v.x) > 1.0e-3 || Math.abs(v.z) > 1.0e-3;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void setBreaking(boolean breaking) {
        try {
            this.breaking.setBreaking(breaking);
        } catch (Throwable ignored) {
        }
    }
}
