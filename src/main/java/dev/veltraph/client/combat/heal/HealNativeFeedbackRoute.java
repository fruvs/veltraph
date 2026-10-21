package dev.veltraph.client.combat.heal;

import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.service.mc.NativeHealFeedbackSignal;
import net.minecraft.client.Minecraft;

/** Typed Core hook for parent-routed native feedback; never an unscoped GAME callback. */
public final class HealNativeFeedbackRoute {
    private HealNativeFeedbackRoute() {}
    public static void receive(NativeHealFeedbackSignal.Receipt receipt) {
        var client=Minecraft.getInstance();
        if(!receipt.current(client))return;
        var global=VeltraphBootstrap.autoHeal();
        boolean enabledParent=global!=null&&global.enabled();
        var manager=VeltraphBootstrap.moduleManager();
        if(manager!=null)enabledParent|=manager.enabledModules().stream().anyMatch(module->
                module instanceof dev.veltraph.client.combat.ghost.GhostGrindModule);
        if(enabledParent)HealFloridFeedback.recordShared(receipt.component().getString(),receipt.receivedAt());
    }
}
