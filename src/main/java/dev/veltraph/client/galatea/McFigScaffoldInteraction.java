package dev.veltraph.client.galatea;

import dev.veltraph.client.mixin.FigScaffoldKeyMappingAccessor;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** One queued native use-key event. Outgoing use packet and server effect need separate witnesses.
 * Supplied authorization is the ordinary Root guard/custody/lease validation at issue time.
 * Source common throttle must be adapted separately; authorization cannot be a constant true.
 */
public final class McFigScaffoldInteraction {
    private final Minecraft client;
    private final BooleanSupplier authorityAndThrottle;
    private long invokedGeneration=Long.MIN_VALUE;
    public McFigScaffoldInteraction(Minecraft client,BooleanSupplier authorityAndThrottle) {
        this.client=java.util.Objects.requireNonNull(client);
        this.authorityAndThrottle=java.util.Objects.requireNonNull(authorityAndThrottle);
    }
    public FigScaffoldStateMachine.Dispatch invokeOnce(long generation,int expectedHotbarSlot) {
        if(generation<=invokedGeneration)return FigScaffoldStateMachine.Dispatch.INVOKED_WITHOUT_QUEUED_EVENT;
        invokedGeneration=generation;
        if(!authorityAndThrottle.getAsBoolean() || client.player==null || client.level==null
            || client.gui.screen()!=null || client.player.containerMenu!=client.player.inventoryMenu
            || expectedHotbarSlot<0 || expectedHotbarSlot>8
            || client.player.getInventory().getSelectedSlot()!=expectedHotbarSlot)
            return FigScaffoldStateMachine.Dispatch.INVOKED_WITHOUT_QUEUED_EVENT;
        var key=client.options.keyUse;
        if(((FigScaffoldKeyMappingAccessor)(Object)key).veltraph$figScaffoldClickCount()!=0)
            return FigScaffoldStateMachine.Dispatch.INVOKED_WITHOUT_QUEUED_EVENT;
        KeyMapping.click(KeyMappingHelper.getBoundKeyOf(key));
        return FigScaffoldStateMachine.Dispatch.NATIVE_KEY_EVENT_QUEUED;
    }
}
