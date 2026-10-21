package dev.veltraph.client.chocolate;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
/** Installed direct-field readers only; no native semantic getter or permission provider. */
public final class ChocolateClickNativeFields {
    private ChocolateClickNativeFields(){}
    public interface GuiFields {Screen chocolate$clickScreen();}
    public interface ScreenFields {Component chocolate$clickTitle();}
    public interface ContainerFields {net.minecraft.world.inventory.AbstractContainerMenu chocolate$clickMenu();}
    public interface MenuFields {int chocolate$clickRevision();}
    public interface LivingFields {EntityDataAccessor<Float> chocolate$clickHealthId();}
}
