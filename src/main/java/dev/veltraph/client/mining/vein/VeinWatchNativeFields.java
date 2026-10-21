package dev.veltraph.client.mining.vein;

import net.minecraft.core.Holder;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.Style;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.UUID;

/** Field readers are also installation markers on the exact native carriers. */
public final class VeinWatchNativeFields {
    private VeinWatchNativeFields() {}
    public interface EntityFields {
        Vec3 veltraph$watchPosition(); SynchedEntityData veltraph$watchData();
        Object veltraph$watchWorld(); UUID veltraph$watchUuid(); int veltraph$watchId();
        Object veltraph$watchRemoval();
    }
    public interface ItemFields { EntityDataAccessor<ItemStack> veltraph$watchItemAccessor(); }
    public interface StackFields {
        int veltraph$watchCount(); Holder<?> veltraph$watchItem(); PatchedDataComponentMap veltraph$watchComponents();
    }
    public interface DataFields { SynchedEntityData.DataItem<?>[] veltraph$watchItems(); }
    public interface DataItemFields { Object veltraph$watchValue(); }
    public interface ComponentsFields {
        net.minecraft.core.component.DataComponentMap veltraph$watchPrototype();
        it.unimi.dsi.fastutil.objects.Reference2ObjectMap<?,?> veltraph$watchPatch();
    }
    public interface PrototypeFields { it.unimi.dsi.fastutil.objects.Reference2ObjectMap<?,?> veltraph$watchMap(); }
    public interface NameFields {
        ComponentContents veltraph$watchContents(); List<?> veltraph$watchSiblings(); Style veltraph$watchStyle();
    }
    public interface TranslationFields { String veltraph$watchKey(); String veltraph$watchFallback(); Object[] veltraph$watchArgs(); }
    public interface HolderFields { ResourceKey<?> veltraph$watchKey(); }
}
