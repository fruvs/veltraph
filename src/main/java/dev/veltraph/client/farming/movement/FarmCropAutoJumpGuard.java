package dev.veltraph.client.farming.movement;

import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.farming.module.FarmModule;
import dev.veltraph.client.farming.module.WartCropsFarmModule;
import dev.veltraph.client.farming.module.PumpkinMelonFarmModule;
import dev.veltraph.client.service.mc.McOwnedRotation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;

/** Automatic-jump refusal only. This cannot authorize movement, harvest or a native jump. */
public final class FarmCropAutoJumpGuard {
    private FarmCropAutoJumpGuard() {}

    public static boolean suppress(LocalPlayer expected) {
        var client=Minecraft.getInstance();
        if(client==null||!client.isSameThread()||expected==null||client.player!=expected
                ||client.level==null||client.options==null||client.getConnection()==null)return false;
        var level=client.level;var connection=client.getConnection();
        var manager=VeltraphBootstrap.moduleManager();
        var owner=manager==null?null:manager.inputOwner().orElse(null);
        if(!(owner instanceof FarmModule farm)||!current(client,expected,level,connection,manager,farm)||!cropPhase(farm))return false;
        if(McOwnedRotation.authority(client)!=farm)return false;
        return current(client,expected,level,connection,manager,farm)&&cropPhase(farm);
    }

    private static boolean cropPhase(FarmModule farm) {
        return farm instanceof WartCropsFarmModule wart?wart.groundedCropWorkActive()
                :farm instanceof PumpkinMelonFarmModule gourd&&gourd.groundedCropWorkActive();
    }

    private static boolean current(Minecraft client,LocalPlayer player,ClientLevel level,
                                   ClientPacketListener connection,ModuleManager manager,FarmModule farm) {
        return client.isSameThread()&&client.player==player&&client.level==level&&player.level()==level
                &&client.getConnection()==connection&&player.isAlive()&&client.getCameraEntity()==player
                &&client.gui.screen()==null&&!client.isPaused()&&!client.level.tickRateManager().isEntityFrozen(player)
                &&VeltraphBootstrap.moduleManager()==manager&&manager.get(farm.id()).orElse(null)==farm
                &&manager.inputOwner().orElse(null)==farm
                &&farm.isEnabled()&&!farm.isSuspended();
    }
}
