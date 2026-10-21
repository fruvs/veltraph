package dev.veltraph.client.navigation.mc;

import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import java.util.Set;
import java.util.Optional;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.TransientEntitySectionManager;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Implementations are raw Mixin field reads. No virtual level/shape lookup is permitted here. */
public final class CollectionSceneNativeAccess {
    private CollectionSceneNativeAccess() {}
    public interface World {
        ClientChunkCache veltraph$sceneChunkSource();
        TransientEntitySectionManager<Entity> veltraph$sceneEntityStorage();
        WorldBorder veltraph$sceneBorder();
    }
    public interface EntityManager { EntitySectionStorage<Entity> veltraph$sceneEntitySections(); }
    public interface PaletteObserver {}
    public interface SourceObserver {}
    public interface EntityObserver {}
    public interface EntityDataObserver {}
    public interface HolderFields {
        Object veltraph$sceneHolderRevision();
        boolean veltraph$sceneTagsAssigned();
        Set<TagKey<Block>> veltraph$sceneHolderTags();
        Object veltraph$sceneHolderValue();
    }
    public interface BlockFields {
        Holder.Reference<Block> veltraph$sceneBlockHolder();
        Object veltraph$sceneBlockRevision();
    }
    public interface Prototype {
        float veltraph$sceneFriction();
        float veltraph$sceneSpeedFactor();
        float veltraph$sceneJumpFactor();
        float veltraph$sceneBounceRestitution();
        float veltraph$sceneFallDistanceReduction();
        boolean veltraph$sceneHasCollision();
        boolean veltraph$sceneDynamicShape();
    }
    public interface FluidFields { Holder.Reference<Fluid> veltraph$sceneFluidHolder(); }
    public interface NativePos {
        int veltraph$sceneX();
        int veltraph$sceneY();
        int veltraph$sceneZ();
    }
    public interface Border {
        Object veltraph$sceneBorderRevision();
        boolean veltraph$sceneStaticBorder();
    }
    public interface EntitySectionObserver {}
    public interface EntityStorageObserver {}
    public interface ChunkObserver {}
    public interface Chunk { LevelChunkSection[] veltraph$sceneSections(); }
    public interface Section { PalettedContainer<BlockState> veltraph$sceneStates(); }
    public interface State { Object veltraph$sceneCacheIdentity(); }
    public interface EntityFields {
        Level veltraph$sceneLevel();
        AABB veltraph$sceneBox();
        Vec3 veltraph$scenePosition();
        Optional<BlockPos> veltraph$sceneSupportingBlock();
        SynchedEntityData veltraph$sceneEntityData();
    }
}
