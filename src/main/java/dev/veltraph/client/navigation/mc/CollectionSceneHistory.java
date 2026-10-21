package dev.veltraph.client.navigation.mc;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.TransientEntitySectionManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.core.Holder;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.EmptyFluid;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Bounded scene observation only. Issuance and all geometry proofs belong to the parent adapter. */
public final class CollectionSceneHistory {
    public static final int MAX_CELLS = 4096;
    private static final int MAX_SNAPSHOTS = 128;
    private static final ArrayList<WeakReference<Snapshot>> WATCHES = new ArrayList<>();
    private CollectionSceneHistory() {}

    /** Opaque evidence. Callers cannot manufacture, edit, reset or promote a snapshot. */
    public static final class Snapshot {
        private final Minecraft client;
        private final ClientLevel level;
        private final Entity player;
        private final Entity excludedItem;
        private final ClientChunkCache source;
        private final TransientEntitySectionManager<Entity> entities;
        private final AABB region;
        private final WorldBorder border;
        private final Object borderRevision;
        private final CollectionSceneFrozenBoxes.Border borderBounds;
        private final List<CollectionSceneFrozenBoxes.Box> collisionBoxes;
        private final List<CollectionSceneFrozenBoxes.Box> supportBoxes;
        private final List<CollectionSceneFrozenTraits.Cell> traits;
        private final CollectionSceneFrozenTraits.Scalars bodyBaseline;
        private final CollectionSceneFrozenTraits.Scalars supportBaseline;
        private final List<Cell> cells;
        private final CollectionSceneWatch watch;
        private Snapshot(Minecraft c, ClientLevel level, Entity player, Entity item, ClientChunkCache source,
                         TransientEntitySectionManager<Entity> entities, AABB region, WorldBorder border,
                         Object borderRevision, CollectionSceneFrozenBoxes.Border borderBounds,
                         List<CollectionSceneFrozenBoxes.Box> collisionBoxes, List<CollectionSceneFrozenBoxes.Box> supportBoxes,
                         List<Cell> cells, List<CollectionSceneFrozenTraits.Cell> traits,
                         CollectionSceneFrozenTraits.Scalars bodyBaseline, CollectionSceneFrozenTraits.Scalars supportBaseline,
                         IdentityHashMap<PalettedContainer<?>, BitSet> indices) {
            this.client=c; this.level=level; this.player=player; this.excludedItem=item; this.source=source; this.entities=entities;
            this.border=border; this.borderRevision=borderRevision; this.borderBounds=borderBounds;
            this.collisionBoxes=List.copyOf(collisionBoxes); this.supportBoxes=List.copyOf(supportBoxes);
            this.traits=List.copyOf(traits); this.bodyBaseline=bodyBaseline; this.supportBaseline=supportBaseline;
            this.region=region; this.cells=List.copyOf(cells); this.watch=new CollectionSceneWatch(indices);
        }
    }
    private record Cell(BlockPos pos, LevelChunk chunk, LevelChunkSection[] sections, int sectionIndex,
                        LevelChunkSection section, PalettedContainer<BlockState> container,
                        BlockState state, Object cacheIdentity, VoxelShape collisionShape,
                        Block block, Object blockRevision, Holder.Reference<Block> holder, Object holderRevision,
                        Object tagsIdentity, CollectionSceneFrozenTraits.Scalars scalars,
                        boolean hasCollision, boolean dynamicShape,
                        Fluid fluid, Holder.Reference<?> fluidHolder, Object fluidHolderRevision, Object fluidTagsIdentity) {}

    /**
     * Before any provider/config/caller callback, capture the parent's complete native query halo.
     * The parent must first prove the full nine terminal squares and source-to-goal union.
     * Null means unknown, unbound, dynamic, unloaded or bounded-bookkeeping exhaustion.
     */
    public static synchronized Snapshot capture(Minecraft c, List<BlockPos> selectedCells,
                                                AABB bodyRegion, Entity excludedOwnedItem) {
        if (c == null || c.level == null || c.player == null || selectedCells == null
            || selectedCells.isEmpty() || selectedCells.size() > MAX_CELLS || !bounded(bodyRegion)) return null;
        List<BlockPos> positions=new ArrayList<>(selectedCells.size());
        for (BlockPos supplied : selectedCells) {
            if (supplied == null) return null;
            positions.add(new BlockPos(supplied.getX(),supplied.getY(),supplied.getZ()));
            if (positions.size() > MAX_CELLS) return null;
        }
        if (positions.isEmpty()) return null;
        ClientLevel level=c.level;
        Entity player=c.player;
        if (level == null || level.getClass() != ClientLevel.class || player == null || player.getClass() != LocalPlayer.class) return null;
        if (!(level instanceof CollectionSceneNativeAccess.World world)) return null;
        WorldBorder border=world.veltraph$sceneBorder();
        if (border == null || border.getClass() != WorldBorder.class
            || !(border instanceof CollectionSceneNativeAccess.Border rawBorder)
            || !rawBorder.veltraph$sceneStaticBorder()) return null;
        Object borderRevision=rawBorder.veltraph$sceneBorderRevision();
        if (borderRevision == null) return null;
        var borderBounds=new CollectionSceneFrozenBoxes.Border(border.getMinX(),border.getMinZ(),border.getMaxX(),border.getMaxZ());
        if (!CollectionSceneFrozenBoxes.finite(borderBounds)) return null;
        ClientChunkCache source=world.veltraph$sceneChunkSource();
        if (source == null || source.getClass() != ClientChunkCache.class
            || !(source instanceof CollectionSceneNativeAccess.SourceObserver)
            || !(player instanceof CollectionSceneNativeAccess.EntityObserver)
            || !(player instanceof CollectionSceneNativeAccess.EntityFields playerFields)
            || !((Object)playerFields.veltraph$sceneEntityData() instanceof CollectionSceneNativeAccess.EntityDataObserver)) return null;
        TransientEntitySectionManager<Entity> entities=world.veltraph$sceneEntityStorage();
        if (entities == null || entities.getClass() != TransientEntitySectionManager.class
            || !(entities instanceof CollectionSceneNativeAccess.EntityManager manager)) return null;
        EntitySectionStorage<Entity> storage=manager.veltraph$sceneEntitySections();
        if (storage == null || storage.getClass() != EntitySectionStorage.class
            || !(storage instanceof CollectionSceneNativeAccess.EntityStorageObserver)) return null;
        // An actual native player section proves the universal native add/remove observer loaded.
        EntitySection<Entity> playerSection=storage.getSection(SectionPos.asLong(player.blockPosition()));
        if (playerSection == null || playerSection.getClass() != EntitySection.class
            || !(playerSection instanceof CollectionSceneNativeAccess.EntitySectionObserver)) return null;
        if (excludedOwnedItem != null && (!(excludedOwnedItem instanceof ItemEntity)
            || !(excludedOwnedItem instanceof CollectionSceneNativeAccess.EntityFields raw)
            || raw.veltraph$sceneLevel() != level)) return null;
        prune();
        if (WATCHES.size() >= MAX_SNAPSHOTS) return null;
        List<CollectionSceneFrozenBoxes.Box> collisionBoxes=new ArrayList<>();
        List<CollectionSceneFrozenBoxes.Box> supportBoxes=new ArrayList<>();
        Map<BlockPos, Cell> cells=new LinkedHashMap<>();
        IdentityHashMap<PalettedContainer<?>, BitSet> indices=new IdentityHashMap<>();
        for (BlockPos pos : positions) {
            if (cells.containsKey(pos)) continue;
            if (level.isOutsideBuildHeight(pos)) return null;
            LevelChunk chunk=source.getChunk(pos.getX() >> 4,pos.getZ() >> 4,ChunkStatus.FULL,false);
            if (chunk == null || chunk.getClass() != LevelChunk.class
                || !(chunk instanceof CollectionSceneNativeAccess.Chunk rawChunk)
                || !(chunk instanceof CollectionSceneNativeAccess.ChunkObserver)) return null;
            LevelChunkSection[] sections=rawChunk.veltraph$sceneSections();
            int sectionIndex=level.getSectionIndex(pos.getY());
            if (sections == null || sectionIndex < 0 || sectionIndex >= sections.length) return null;
            LevelChunkSection section=sections[sectionIndex];
            if (section == null || section.getClass() != LevelChunkSection.class
                || !(section instanceof CollectionSceneNativeAccess.Section rawSection)) return null;
            PalettedContainer<BlockState> container=rawSection.veltraph$sceneStates();
            if (container == null || container.getClass() != PalettedContainer.class
                || !(container instanceof CollectionSceneNativeAccess.PaletteObserver)) return null;
            BlockState state=chunk.getBlockState(pos);
            if (state == null || state.getClass() != BlockState.class) return null;
            // Actual native static collision cache, fixed SDK class and native loader identity.
            Block block=state.getBlock();
            if (!CollectionSceneKnownNativeBlocks.known(block)
                || block.hasDynamicShape() || state.hasBlockEntity()
                || !(state instanceof CollectionSceneNativeAccess.State rawState)
                || !(block instanceof CollectionSceneNativeAccess.BlockFields rawBlock)
                || !(block instanceof CollectionSceneNativeAccess.Prototype prototype)) return null;
            Holder.Reference<Block> holder=rawBlock.veltraph$sceneBlockHolder();
            if (holder == null || holder.getClass() != Holder.Reference.class
                || !(holder instanceof CollectionSceneNativeAccess.HolderFields rawHolder)
                || !rawHolder.veltraph$sceneTagsAssigned() || rawHolder.veltraph$sceneHolderValue() != block
                || !knownTags(rawHolder.veltraph$sceneHolderTags())) return null;
            Object blockRevision=rawBlock.veltraph$sceneBlockRevision(),holderRevision=rawHolder.veltraph$sceneHolderRevision();
            if (blockRevision == null || holderRevision == null) return null;
            CollectionSceneFrozenTraits.Scalars scalars=scalars(prototype);
            if (scalars == null) return null;
            Object cache=rawState.veltraph$sceneCacheIdentity();
            if (cache == null) return null;
            VoxelShape shape=state.getCollisionShape(level,pos);
            if (shape == null) return null;
            VoxelShape support=state.getBlockSupportShape(level,pos);
            if (support == null) return null;
            if (!freeze(shape,pos,collisionBoxes,supportBoxes.size())
                || !freeze(support,pos,supportBoxes,collisionBoxes.size())) return null;
            FluidState cachedFluid=state.getFluidState();
            if (cachedFluid == null || cachedFluid.getClass() != FluidState.class
                || !CollectionSceneKnownNativeFluids.known(cachedFluid.getType())
                || !(cachedFluid.getType() instanceof CollectionSceneNativeAccess.FluidFields rawFluid)) return null;
            Holder.Reference<?> fluidHolder=rawFluid.veltraph$sceneFluidHolder();
            if (fluidHolder == null || fluidHolder.getClass() != Holder.Reference.class
                || !(fluidHolder instanceof CollectionSceneNativeAccess.HolderFields fluidFields)
                || !fluidFields.veltraph$sceneTagsAssigned() || fluidFields.veltraph$sceneHolderValue() != cachedFluid.getType()
                || fluidFields.veltraph$sceneHolderRevision() == null || !knownTags(fluidFields.veltraph$sceneHolderTags())) return null;
            cells.put(pos,new Cell(pos,chunk,sections,sectionIndex,section,container,state,cache,shape,
                block,blockRevision,holder,holderRevision,rawHolder.veltraph$sceneHolderTags(),scalars,
                prototype.veltraph$sceneHasCollision(),prototype.veltraph$sceneDynamicShape(),cachedFluid.getType(),
                fluidHolder,fluidFields.veltraph$sceneHolderRevision(),fluidFields.veltraph$sceneHolderTags()));
            indices.computeIfAbsent(container,k->new BitSet(4096)).set(index(pos));
        }
        if (cells.size()+collisionBoxes.size()+supportBoxes.size() > MAX_CELLS) return null;
        // Historical support can differ from floor(position); never query an unknown support cell.
        java.util.Optional<BlockPos> historical=playerFields.veltraph$sceneSupportingBlock();
        if (historical == null || historical.isPresent() && !cells.containsKey(historical.get())) return null;
        BlockPos supportPos=c.player.getBlockPosBelowThatAffectsMyMovement();
        Cell initialSupport=cells.get(supportPos),initialBody=cells.get(c.player.blockPosition());
        if (initialSupport == null || initialBody == null) return null;
        List<CollectionSceneFrozenTraits.Cell> traits=new ArrayList<>();
        for (Cell cell : cells.values()) {
            FluidState fluid=cell.state.getFluidState();
            if (fluid == null || fluid.getClass() != FluidState.class) return null;
            boolean empty=fluid.getType() == Fluids.EMPTY && fluid.getType().getClass() == EmptyFluid.class;
            // Unknown context/owner graph refuses; native nonempty fluids are recorded as hazards.
            if (!CollectionSceneKnownNativeFluids.known(fluid.getType())) return null;
            boolean hazard=!empty || cell.block == Blocks.FIRE || cell.block == Blocks.SOUL_FIRE
                || cell.block == Blocks.COBWEB || cell.block == Blocks.SWEET_BERRY_BUSH
                || cell.block == Blocks.POWDER_SNOW || cell.block == Blocks.MAGMA_BLOCK
                || cell.block == Blocks.CACTUS || cell.block == Blocks.CAMPFIRE
                || cell.block == Blocks.SOUL_CAMPFIRE || cell.block == Blocks.SCAFFOLDING;
            boolean climbable=cell.state.is(BlockTags.CLIMBABLE);
            if (cell.block instanceof TrapDoorBlock && cell.state.getValue(TrapDoorBlock.OPEN)) {
                Cell below=cells.get(cell.pos.below());
                if (below == null) return null;
                climbable |= below.block == Blocks.LADDER
                    && below.state.getValue(LadderBlock.FACING) == cell.state.getValue(TrapDoorBlock.FACING);
            }
            traits.add(new CollectionSceneFrozenTraits.Cell(cell.pos.getX(),cell.pos.getY(),cell.pos.getZ(),
                hazard,climbable,cell.state.is(BlockTags.WALLS)
                    || cell.block instanceof FenceGateBlock,cell.scalars));
        }
        // ItemEntity uses fixed native non-collider semantics; other entity classes may depend on
        // untracked subclass state and therefore refuse capture rather than grant stale clearance.
        for (Entity entity : level.getEntities(player,bodyRegion,e -> true)) {
            if (entity == excludedOwnedItem) continue;
            if (entity.getClass() != ItemEntity.class
                || !(entity instanceof CollectionSceneNativeAccess.EntityFields)) return null;
        }
        Snapshot result=new Snapshot(c,level,player,excludedOwnedItem,source,entities,bodyRegion,border,borderRevision,borderBounds,
                                     collisionBoxes,supportBoxes,new ArrayList<>(cells.values()),traits,initialBody.scalars,initialSupport.scalars,indices);
        WATCHES.add(new WeakReference<>(result));
        return current(result) ? result : null;
    }

    /** Final leaf: stored identities, raw injected fields and one-way native write observations only. */
    public static synchronized boolean current(Snapshot s) {
        if (s == null || !s.watch.current() || s.client.level != s.level || s.client.player != s.player
            || !(s.level instanceof CollectionSceneNativeAccess.World world)
            || world.veltraph$sceneChunkSource() != s.source
            || world.veltraph$sceneEntityStorage() != s.entities
            || world.veltraph$sceneBorder() != s.border
            || !(s.border instanceof CollectionSceneNativeAccess.Border border)
            || !border.veltraph$sceneStaticBorder()
            || border.veltraph$sceneBorderRevision() != s.borderRevision) return false;
        for (Cell cell : s.cells) {
            if (!(cell.chunk instanceof CollectionSceneNativeAccess.Chunk chunk)
                || chunk.veltraph$sceneSections() != cell.sections
                || cell.sections[cell.sectionIndex] != cell.section
                || !(cell.section instanceof CollectionSceneNativeAccess.Section section)
                || section.veltraph$sceneStates() != cell.container
                || !(cell.state instanceof CollectionSceneNativeAccess.State state)
                || state.veltraph$sceneCacheIdentity() != cell.cacheIdentity
                || !(cell.block instanceof CollectionSceneNativeAccess.BlockFields block)
                || block.veltraph$sceneBlockRevision() != cell.blockRevision
                || block.veltraph$sceneBlockHolder() != cell.holder
                || !(cell.holder instanceof CollectionSceneNativeAccess.HolderFields holder)
                || !holder.veltraph$sceneTagsAssigned()
                || holder.veltraph$sceneHolderRevision() != cell.holderRevision
                || holder.veltraph$sceneHolderTags() != cell.tagsIdentity
                || holder.veltraph$sceneHolderValue() != cell.block
                || !(cell.block instanceof CollectionSceneNativeAccess.Prototype prototype)
                || !sameScalars(prototype,cell.scalars)
                || prototype.veltraph$sceneHasCollision() != cell.hasCollision
                || prototype.veltraph$sceneDynamicShape() != cell.dynamicShape
                || !(cell.fluid instanceof CollectionSceneNativeAccess.FluidFields fluid)
                || fluid.veltraph$sceneFluidHolder() != cell.fluidHolder
                || !(cell.fluidHolder instanceof CollectionSceneNativeAccess.HolderFields fluidHolder)
                || !fluidHolder.veltraph$sceneTagsAssigned()
                || fluidHolder.veltraph$sceneHolderRevision() != cell.fluidHolderRevision
                || fluidHolder.veltraph$sceneHolderValue() != cell.fluid
                || fluidHolder.veltraph$sceneHolderTags() != cell.fluidTagsIdentity) return false;
        }
        return s.watch.current();
    }

    /** Immutable observable cost; the parent charges it to the existing proof before callbacks. */
    public static int capturedSampleCount(Snapshot s) {
        return s == null ? MAX_CELLS+1 : s.cells.size()+s.collisionBoxes.size()+s.supportBoxes.size();
    }
    /** Live native body and foot check over immutable pre-callback samples. No native geometry lookup. */
    public static synchronized boolean scenePlayerClear(Snapshot s) {
        if (!current(s) || !(s.player instanceof CollectionSceneNativeAccess.EntityFields player)) return false;
        AABB body=player.veltraph$sceneBox();
        net.minecraft.world.phys.Vec3 position=player.veltraph$scenePosition();
        if (body == null || position == null) return false;
        boolean clear=CollectionSceneFrozenBoxes.clear(
            new CollectionSceneFrozenBoxes.Box(body.minX,body.minY,body.minZ,body.maxX,body.maxY,body.maxZ),
            position.x,position.y,position.z,
            new CollectionSceneFrozenBoxes.Box(s.region.minX,s.region.minY,s.region.minZ,s.region.maxX,s.region.maxY,s.region.maxZ),
            s.borderBounds,s.collisionBoxes,s.supportBoxes);
        if (!clear || !historicalSupportClear(s,player,position.x,position.y,position.z)) return false;
        return CollectionSceneFrozenTraits.clear(
            new CollectionSceneFrozenBoxes.Box(body.minX,body.minY,body.minZ,body.maxX,body.maxY,body.maxZ),
            position.y,s.traits,s.bodyBaseline,s.supportBaseline) && current(s);
    }
    private static boolean historicalSupportClear(Snapshot s,CollectionSceneNativeAccess.EntityFields player,
                                                  double x,double y,double z) {
        java.util.Optional<BlockPos> optional=player.veltraph$sceneSupportingBlock();
        if (optional == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
            || x<Integer.MIN_VALUE || x>=Integer.MAX_VALUE || y<Integer.MIN_VALUE+2D || y>=Integer.MAX_VALUE
            || z<Integer.MIN_VALUE || z>=Integer.MAX_VALUE) return false;
        int sx=(int)Math.floor(x),sy=(int)Math.floor(y-(double).500001F),sz=(int)Math.floor(z);
        if (optional.isPresent()) {
            BlockPos pos=optional.get();
            if (pos == null || (pos.getClass() != BlockPos.class && pos.getClass() != BlockPos.MutableBlockPos.class)
                || !(pos instanceof CollectionSceneNativeAccess.NativePos raw)) return false;
            sx=raw.veltraph$sceneX(); sz=raw.veltraph$sceneZ();
            var historical=CollectionSceneFrozenTraits.at(s.traits,sx,raw.veltraph$sceneY(),sz);
            if (historical == null || historical.hazard() || historical.climbable()) return false;
            sy=CollectionSceneFrozenTraits.movementSupportY(y,raw.veltraph$sceneY(),historical.historicalSupportSpecial());
        }
        var selected=CollectionSceneFrozenTraits.at(s.traits,sx,sy,sz);
        return selected != null && !selected.hazard() && !selected.climbable()
            && selected.scalars().equals(s.supportBaseline);
    }
    private static CollectionSceneFrozenTraits.Scalars scalars(CollectionSceneNativeAccess.Prototype p) {
        float f=p.veltraph$sceneFriction(),s=p.veltraph$sceneSpeedFactor(),j=p.veltraph$sceneJumpFactor(),
            b=p.veltraph$sceneBounceRestitution(),d=p.veltraph$sceneFallDistanceReduction();
        if (!Float.isFinite(f) || !Float.isFinite(s) || !Float.isFinite(j) || !Float.isFinite(b) || !Float.isFinite(d)) return null;
        return new CollectionSceneFrozenTraits.Scalars(Float.floatToRawIntBits(f),Float.floatToRawIntBits(s),
            Float.floatToRawIntBits(j),Float.floatToRawIntBits(b),Float.floatToRawIntBits(d));
    }
    private static boolean sameScalars(CollectionSceneNativeAccess.Prototype p,CollectionSceneFrozenTraits.Scalars v) {
        return Float.floatToRawIntBits(p.veltraph$sceneFriction()) == v.friction()
            && Float.floatToRawIntBits(p.veltraph$sceneSpeedFactor()) == v.speed()
            && Float.floatToRawIntBits(p.veltraph$sceneJumpFactor()) == v.jump()
            && Float.floatToRawIntBits(p.veltraph$sceneBounceRestitution()) == v.bounce()
            && Float.floatToRawIntBits(p.veltraph$sceneFallDistanceReduction()) == v.fall();
    }
    private static boolean knownTags(java.util.Set<TagKey<Block>> tags) {
        if (tags == null || tags.size()>4096 || tags.getClass().getClassLoader() != null) return false;
        String nativeSet=tags.getClass().getName();
        if (!nativeSet.equals("java.util.ImmutableCollections$SetN") && !nativeSet.equals("java.util.ImmutableCollections$Set12")) return false;
        for (Object value : tags) {
            if (!(value instanceof TagKey<?> tag) || tag.getClass() != TagKey.class
                || tag.location().getClass() != Identifier.class || tag.registry().getClass() != ResourceKey.class
                || tag.registry().identifier().getClass() != Identifier.class
                || tag.registry().registry().getClass() != Identifier.class) return false;
        }
        return true;
    }
    private static boolean freeze(VoxelShape shape,BlockPos pos,List<CollectionSceneFrozenBoxes.Box> target,int otherCount) {
        for (AABB box : shape.toAabbs()) {
            if (target.size()+otherCount >= CollectionSceneFrozenBoxes.MAX_BOXES) return false;
            var frozen=new CollectionSceneFrozenBoxes.Box(pos.getX()+box.minX,pos.getY()+box.minY,pos.getZ()+box.minZ,
                pos.getX()+box.maxX,pos.getY()+box.maxY,pos.getZ()+box.maxZ);
            if (!CollectionSceneFrozenBoxes.finite(frozen)) return false;
            target.add(frozen);
        }
        return true;
    }
    private static int index(BlockPos p) { return ((p.getY() & 15) << 8) | ((p.getZ() & 15) << 4) | (p.getX() & 15); }
    private static boolean bounded(AABB b) {
        if (b == null || !Double.isFinite(b.minX) || !Double.isFinite(b.minY) || !Double.isFinite(b.minZ)
            || !Double.isFinite(b.maxX) || !Double.isFinite(b.maxY) || !Double.isFinite(b.maxZ)) return false;
        double x=b.maxX-b.minX,y=b.maxY-b.minY,z=b.maxZ-b.minZ;
        return x > 0 && y > 0 && z > 0 && x <= 64 && y <= 64 && z <= 64 && x*y*z <= MAX_CELLS;
    }
    private static void prune() { WATCHES.removeIf(ref -> ref.get() == null); }

    /** Observation only; prevents section enumeration when no live scene is bound. */
    public static synchronized boolean nativeScenesWatched() {
        for (WeakReference<Snapshot> ref : WATCHES) {
            Snapshot s=ref.get(); if (s != null && s.watch.current()) return true;
        }
        return false;
    }
    // Native observers have veto power only. Unbound ordinary calls immediately return.
    public static synchronized void nativeCellWrite(Object container, int index) {
        if (WATCHES.isEmpty()) return;
        for (WeakReference<Snapshot> ref : WATCHES) {
            Snapshot s=ref.get(); if (s == null) continue;
            s.watch.cellWrite(container,index);
        }
    }
    public static synchronized void nativeChunkWrite(Object chunk, BlockPos pos) {
        if (WATCHES.isEmpty()) return;
        for (WeakReference<Snapshot> ref : WATCHES) {
            Snapshot s=ref.get(); if (s == null) continue;
            for (Cell cell : s.cells) if (cell.chunk == chunk && (pos == null || cell.pos.equals(pos))) {
                s.watch.revoke(); break;
            }
        }
    }
    public static synchronized void nativeChunkLifecycle(ClientChunkCache source, int x, int z, boolean all) {
        if (WATCHES.isEmpty()) return;
        for (WeakReference<Snapshot> ref : WATCHES) {
            Snapshot s=ref.get(); if (s == null || s.source != source) continue;
            for (Cell cell : s.cells) if (all || (cell.pos.getX() >> 4) == x && (cell.pos.getZ() >> 4) == z) {
                s.watch.revoke(); break;
            }
        }
    }
    public static synchronized void nativeBlockEntityWrite(Object level, BlockPos pos) {
        if (WATCHES.isEmpty()) return;
        for (WeakReference<Snapshot> ref : WATCHES) {
            Snapshot s=ref.get(); if (s == null || s.level != level) continue;
            for (Cell cell : s.cells) if (cell.pos.equals(pos)) { s.watch.revoke(); break; }
        }
    }
    public static synchronized void nativeStateCacheWrite(Object state) {
        if (WATCHES.isEmpty()) return;
        for (WeakReference<Snapshot> ref : WATCHES) {
            Snapshot s=ref.get(); if (s == null) continue;
            for (Cell cell : s.cells) if (cell.state == state) { s.watch.revoke(); break; }
        }
    }
    public static synchronized void nativeEntityWrite(Object entity) {
        if (WATCHES.isEmpty() || !(entity instanceof CollectionSceneNativeAccess.EntityFields raw)) return;
        Object level=raw.veltraph$sceneLevel();
        AABB box=raw.veltraph$sceneBox();
        if (box == null) return;
        for (WeakReference<Snapshot> ref : WATCHES) {
            Snapshot s=ref.get();
            if (s == null) continue;
            s.watch.entityWrite(s.level,level,s.player,s.excludedItem,entity,
                s.region.minX,s.region.minY,s.region.minZ,s.region.maxX,s.region.maxY,s.region.maxZ,
                box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ);
        }
    }
}
