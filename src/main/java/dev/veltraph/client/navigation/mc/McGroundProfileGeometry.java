package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.navigation.DynamicRouteQueryFence;
import dev.veltraph.client.navigation.GroundLandingBodyEnvelope;
import dev.veltraph.client.navigation.GroundLocomotionMotion;
import dev.veltraph.client.navigation.GroundProfileForecast;
import dev.veltraph.client.navigation.GroundGeometryCellJournal;
import java.util.ArrayList;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;

/** Fresh geometry for a nominal profile forecast; never an input/landing/publication receipt.
 * Full per-step body unions catch partial shapes and native axis-order corners.
 * Actual observed motion and owned neutral inputs must be proven separately by an integration. */
public final class McGroundProfileGeometry {
    private McGroundProfileGeometry() {}
    static boolean sameStart(Minecraft c, Vec3 origin, Vec3 observedVelocity, int tick, boolean grounded) {
        var p = c.player;
        if (p == null || p.tickCount != tick || p.onGround() != grounded || origin == null || observedVelocity == null) return false;
        var v = p.getDeltaMovement();
        return new Vec3(p.getX(), p.getY(), p.getZ()).equals(origin)
                && new Vec3(v.x, v.y, v.z).equals(observedVelocity);
    }
    static boolean takeoffFriction(Minecraft c, float expected) {
        var p = c.player; if (p == null || c.level == null) return false;
        var support = p.getBlockPosBelowThatAffectsMyMovement();
        return c.level.getChunkSource().hasChunk(support.getX() >> 4, support.getZ() >> 4)
                && Float.floatToIntBits(c.level.getBlockState(support).getBlock().getFriction())
                    == Float.floatToIntBits(expected);
    }
    private static boolean hazard(net.minecraft.world.level.block.state.BlockState s) {
        return !s.getFluidState().isEmpty() || s.is(Blocks.FIRE) || s.is(Blocks.SOUL_FIRE)
                || s.is(Blocks.COBWEB) || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.POWDER_SNOW)
                || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.CACTUS)
                || s.is(Blocks.CAMPFIRE) || s.is(Blocks.SOUL_CAMPFIRE);
    }
    /** Native onClimbable semantics at a future cell, without assigning a player pose or flags. */
    private static boolean futureClimbable(Minecraft c,net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state){
        if(state.is(net.minecraft.tags.BlockTags.CLIMBABLE))return true;
        if(!(state.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock)
                ||!state.getValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN))return false;
        var below=pos.below();
        if(c.level==null||below.getY()<c.level.getMinY()
                ||!c.level.getChunkSource().hasChunk(below.getX()>>4,below.getZ()>>4))return true;
        var lower=c.level.getBlockState(below);
        return lower.is(Blocks.LADDER)
                &&lower.getValue(net.minecraft.world.level.block.LadderBlock.FACING)
                    ==state.getValue(net.minecraft.world.level.block.TrapDoorBlock.FACING);
    }
    static final class Pass {
        final Minecraft client; final int[] remaining = {4096};
        final GroundGeometryCellJournal cells = new GroundGeometryCellJournal(4096);
        final ArrayList<net.minecraft.world.phys.AABB> bodies = new ArrayList<>();
        // Only the private stationary producer enables this initial native-only read scope.
        private boolean stationaryInitialRead;
        private java.util.HashSet<GroundGeometryCellJournal.Cell> stationaryPhysicalCells;
        private java.util.HashSet<GroundGeometryCellJournal.Cell> stationaryInitialSeen;
        private java.util.LinkedHashMap<GroundGeometryCellJournal.Cell,StationaryHaloObservation> stationaryHalo;
        private record StationaryHaloObservation(Object state,java.util.List<GroundGeometryCellJournal.Box> shape){
            private StationaryHaloObservation {shape=java.util.List.copyOf(shape);}
            boolean same(StationaryHaloObservation actual){return actual!=null&&state==actual.state&&shape.equals(actual.shape);}
        }
        private net.minecraft.client.player.LocalPlayer stationaryPlayer;
        private net.minecraft.client.multiplayer.ClientLevel stationaryLevel;
        private Vec3 stationaryOrigin,stationaryVelocity;
        private int stationaryTick;
        private net.minecraft.world.phys.AABB stationaryBody;
        private java.util.List<Vec3> stationaryMoves=java.util.List.of();
        Pass(Minecraft client) { this.client = client; }
        /** Halo cells are collision dependencies, not occupied actor body/foot cells. */
        private StationaryHaloObservation readStationaryHalo(GroundGeometryCellJournal.Cell cell){
            if(--remaining[0]<0||client.player!=stationaryPlayer||client.level!=stationaryLevel
                    ||!client.level.getChunkSource().hasChunk(cell.x()>>4,cell.z()>>4))return null;
            var pos=new net.minecraft.core.BlockPos(cell.x(),cell.y(),cell.z());var state=client.level.getBlockState(pos);
            var shape=state.getCollisionShape(client.level,pos,net.minecraft.world.phys.shapes.CollisionContext.of(stationaryPlayer)).toAabbs();if(shape.size()>128)return null;
            var boxes=GroundGeometryShapeSnapshot.mapNonNull(shape,b->new GroundGeometryCellJournal.Box(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ));
            // Actual actor-context collision dependencies only; no invented occupancy traits.
            return new StationaryHaloObservation(state,boxes);
        }
        boolean hasStationaryProof(){return stationaryPlayer!=null;}
        private boolean stationaryCurrent(){
            if(stationaryPlayer==null||client.player!=stationaryPlayer||client.level!=stationaryLevel
                    ||!client.isSameThread()||stationaryPlayer.tickCount!=stationaryTick
                    ||!stationaryPlayer.onGround()||stationaryPlayer.noPhysics||stationaryPlayer.isNoGravity()
                    ||stationaryPlayer.isFallFlying()||stationaryPlayer.isSprinting()||stationaryPlayer.getPose()!=Pose.STANDING
                    ||!sameStart(client,stationaryOrigin,stationaryVelocity,stationaryTick,true))return false;
            var actual=stationaryPlayer.getBoundingBox();
            return actual.minX==stationaryBody.minX&&actual.minY==stationaryBody.minY&&actual.minZ==stationaryBody.minZ
                    &&actual.maxX==stationaryBody.maxX&&actual.maxY==stationaryBody.maxY&&actual.maxZ==stationaryBody.maxZ;
        }
        /** One exact physical body covers every mathematically verified stationary frame and coast. */
        boolean stationaryNativeGroup(dev.veltraph.client.navigation.GroundStationaryNeutralProof.Descriptor descriptor,
                GroundProfileMotionCarrier.NativePose actual){
            if(descriptor==null||actual==null||stationaryPlayer!=null||client.player==null||client.level==null
                    ||!actual.onGround()||actual.sprinting()||!descriptor.origin().equals(actual.position())
                    ||!descriptor.observedVelocity().equals(actual.velocity())||descriptor.collisionMoves().isEmpty()
                    ||descriptor.collisionMoves().size()>2)return false;
            stationaryPhysicalCells=new java.util.HashSet<>();stationaryInitialSeen=new java.util.HashSet<>();stationaryHalo=new java.util.LinkedHashMap<>();
            stationaryPlayer=client.player;stationaryLevel=client.level;stationaryOrigin=descriptor.origin();
            stationaryVelocity=descriptor.observedVelocity();stationaryTick=actual.nativeTick();stationaryBody=stationaryPlayer.getBoundingBox();
            stationaryMoves=java.util.List.copyOf(descriptor.collisionMoves());
            var dimensions=stationaryPlayer.getDimensions(Pose.STANDING);
            var physical=dev.veltraph.client.navigation.GroundStationaryBodyEnvelope.bounds(stationaryOrigin,dimensions.width(),dimensions.height(),0D).orElse(null);
            if(physical==null||physical.minX()!=stationaryBody.minX||physical.minY()!=stationaryBody.minY||physical.minZ()!=stationaryBody.minZ
                    ||physical.maxX()!=stationaryBody.maxX||physical.maxY()!=stationaryBody.maxY||physical.maxZ()!=stationaryBody.maxZ
                    ||!stationaryCurrent())return false;
            stationaryInitialRead=true;
            try {
                if(!groundFriction(client,stationaryOrigin,stationaryOrigin,descriptor.friction(),this)
                        ||!union(client,stationaryOrigin,stationaryOrigin,this,.025D)
                        ||!stationaryFullBody())return false;
                for(var intended:stationaryMoves)if(!stationaryCollision(intended,true))return false;
                return remaining[0]>=0&&stationaryCurrent();
            } finally {stationaryInitialRead=false;stationaryInitialSeen.clear();}
        }
        private boolean stationaryFullBody(){
            var full=stationaryBody.inflate(.025D,0D,.025D);
            double nx=Math.floor(full.maxX)-Math.floor(full.minX)+1D,ny=Math.floor(full.maxY)-Math.floor(full.minY)+1D,nz=Math.floor(full.maxZ)-Math.floor(full.minZ)+1D;
            double volume=nx*ny*nz;
            if(!Double.isFinite(volume)||volume<1D||volume>remaining[0]||bodies.size()>=65
                    ||full.minX<Integer.MIN_VALUE||full.maxX>=Integer.MAX_VALUE||full.minZ<Integer.MIN_VALUE||full.maxZ>=Integer.MAX_VALUE
                    ||full.minY<client.level.getMinY()||full.maxY>=client.level.getMaxY())return false;
            for(int x=(int)Math.floor(full.minX);x<=(int)Math.floor(full.maxX);x++)
                for(int y=(int)Math.floor(full.minY);y<=(int)Math.floor(full.maxY);y++)
                    for(int z=(int)Math.floor(full.minZ);z<=(int)Math.floor(full.maxZ);z++){
                        if(!observe(x,y,z)||hazard(client.level.getBlockState(new net.minecraft.core.BlockPos(x,y,z))))return false;
                    }
            if(!client.level.noCollision(client.player,full))return false;
            bodies.add(full);return true;
        }
        private boolean stationaryCollision(Vec3 intended,boolean recordHalo){
            if(!stationaryCurrent()||!GroundLocomotionMotion.finite(intended)||intended.x()!=0D||intended.z()!=0D||intended.y()>0D)return false;
            var move=new net.minecraft.world.phys.Vec3(intended.x(),intended.y(),intended.z());
            var query=stationaryBody.expandTowards(move);
            // Original native block-collision iterator inspects a complete one-block halo.
            double loX=Math.floor(query.minX-1E-7D)-1D,hiX=Math.floor(query.maxX+1E-7D)+1D;
            double loY=Math.floor(query.minY-1E-7D)-1D,hiY=Math.floor(query.maxY+1E-7D)+1D;
            double loZ=Math.floor(query.minZ-1E-7D)-1D,hiZ=Math.floor(query.maxZ+1E-7D)+1D;
            double volume=(hiX-loX+1D)*(hiY-loY+1D)*(hiZ-loZ+1D);
            if(!Double.isFinite(volume)||volume<1D||volume>4096D||loX<Integer.MIN_VALUE||hiX>=Integer.MAX_VALUE
                    ||loY<Integer.MIN_VALUE||hiY>=Integer.MAX_VALUE||loZ<Integer.MIN_VALUE||hiZ>=Integer.MAX_VALUE)return false;
            for(int x=(int)loX;x<=(int)hiX;x++)for(int z=(int)loZ;z<=(int)hiZ;z++)
                if(!client.level.getChunkSource().hasChunk(x>>4,z>>4))return false;
            if(recordHalo)for(int x=(int)loX;x<=(int)hiX;x++)for(int y=(int)loY;y<=(int)hiY;y++)for(int z=(int)loZ;z<=(int)hiZ;z++){
                var cell=new GroundGeometryCellJournal.Cell(x,y,z);
                if(stationaryPhysicalCells.contains(cell)||stationaryInitialSeen.contains(cell))continue;
                var observed=readStationaryHalo(cell);
                if(observed==null||stationaryHalo.size()>=4096)return false;
                stationaryHalo.put(cell,observed);stationaryInitialSeen.add(cell);
            }
            var entities=client.level.getEntityCollisions(stationaryPlayer,query);
            if(entities.size()>128)return false;
            for(var shape:entities)if(shape.toAabbs().size()>128)return false;
            var moved=net.minecraft.world.entity.Entity.collideBoundingBox(stationaryPlayer,move,stationaryBody,client.level,entities);
            // Retain the original native collision displacement tolerance; only cache keys are exact.
            return Math.abs(stationaryOrigin.x()+moved.x-stationaryOrigin.x())<=.000001D
                    &&Math.abs(stationaryOrigin.y()+moved.y-stationaryOrigin.y())<=.000001D
                    &&Math.abs(stationaryOrigin.z()+moved.z-stationaryOrigin.z())<=.000001D&&stationaryCurrent();
        }
        GroundGeometryCellJournal.Observation read(GroundGeometryCellJournal.Cell cell) {
            if (--remaining[0] < 0 || client.player == null || client.level == null
                    || !client.level.getChunkSource().hasChunk(cell.x() >> 4, cell.z() >> 4)) return null;
            var pos = new net.minecraft.core.BlockPos(cell.x(), cell.y(), cell.z());
            var state = client.level.getBlockState(pos);
            // Every initial/final journal read repeats actual native tag/trapdoor semantics.
            // This covers complete body AND one-block foot/support bands, not only collision shapes.
            if(futureClimbable(client,pos,state))return null;
            var shape = state.getCollisionShape(client.level, pos).toAabbs();
            var supportShape = state.getBlockSupportShape(client.level, pos).toAabbs();
            if (shape.size() > 128 || supportShape.size() > 128) return null;
            var boxes = GroundGeometryShapeSnapshot.mapNonNull(shape,b -> new GroundGeometryCellJournal.Box(
                    b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ));
            return new GroundGeometryCellJournal.Observation(state, boxes,
                    state.getBlock().getFriction(), state.getBlock().getSpeedFactor(),
                    GroundGeometryShapeSnapshot.mapNonNull(supportShape,b -> new GroundGeometryCellJournal.Box(
                            b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ)),
                    state.isFaceSturdy(client.level, pos, net.minecraft.core.Direction.UP));
        }
        boolean observe(int x, int y, int z) {
            var cell = new GroundGeometryCellJournal.Cell(x, y, z);
            if(stationaryInitialRead&&stationaryInitialSeen.contains(cell))return true;
            boolean observed=cells.observe(cell,read(cell));
            if(observed&&stationaryInitialRead){stationaryInitialSeen.add(cell);stationaryPhysicalCells.add(cell);}
            return observed;
        }
        /** No module/role/current callbacks here: trusted native reads after the final actor check. */
        boolean revalidate(DynamicRouteQueryFence fence) {
            if(stationaryPlayer!=null&&!stationaryCurrent())return false;
            if (!cells.revalidate(this::read, () -> remaining[0] >= 0 && fence.available())) return false;
            if(stationaryPlayer!=null){
                for(var entry:stationaryHalo.entrySet()){
                    if(remaining[0]<0||!fence.available()||!entry.getValue().same(readStationaryHalo(entry.getKey())))return false;
                }
            }
            for (var box : bodies) {
                if (!fence.available() || client.player == null || client.level == null
                        || !client.level.noCollision(client.player, box)) return false;
            }
            if(stationaryPlayer!=null){
                for(var intended:stationaryMoves)if(!fence.available()||!stationaryCollision(intended,false))return false;
                if(!stationaryCurrent())return false;
            }
            return remaining[0] >= 0 && fence.available();
        }
    }
    static boolean union(Minecraft c, Vec3 from, Vec3 to, Pass pass) { return union(c,from,to,pass,0D); }
    static boolean union(Minecraft c, Vec3 from, Vec3 to, Pass pass,double uncertainty) {
        if (c.player == null || c.level == null || !GroundLocomotionMotion.finite(from)
                || !GroundLocomotionMotion.finite(to)) return false;
        var dimensions = c.player.getDimensions(Pose.STANDING);
        var bounds = GroundLandingBodyEnvelope.bounds(from, to, dimensions.width(), dimensions.height()).orElse(null);
        if (bounds == null) return false;
        if(!Double.isFinite(uncertainty)||uncertainty<0||uncertainty>.025)return false;
        var box = new net.minecraft.world.phys.AABB(bounds.minX()-uncertainty, bounds.minY(), bounds.minZ()-uncertainty,
                bounds.maxX()+uncertainty, bounds.maxY(), bounds.maxZ()+uncertainty);
        // Check extent before casts/loops; huge finite input cannot monopolize a native frame.
        double nx = Math.floor(box.maxX) - Math.floor(box.minX) + 1;
        double ny = Math.floor(box.maxY) - Math.floor(box.minY) + 1;
        double nz = Math.floor(box.maxZ) - Math.floor(box.minZ) + 1;
        double volume = nx * ny * nz;
        if (!Double.isFinite(volume) || volume < 1 || volume > pass.remaining[0]
                || box.minX < Integer.MIN_VALUE || box.maxX >= Integer.MAX_VALUE
                || box.minZ < Integer.MIN_VALUE || box.maxZ >= Integer.MAX_VALUE
                || box.minY < c.level.getMinY() || box.maxY >= c.level.getMaxY()) return false;
        if (pass.bodies.size() >= 65) return false;
        pass.bodies.add(box);
        for (int x = (int)Math.floor(box.minX); x <= (int)Math.floor(box.maxX); x++)
            for (int y = (int)Math.floor(box.minY); y <= (int)Math.floor(box.maxY); y++)
                for (int z = (int)Math.floor(box.minZ); z <= (int)Math.floor(box.maxZ); z++) {
                    if (!c.level.getChunkSource().hasChunk(x >> 4, z >> 4)) return false;
                    if (!pass.observe(x, y, z) || hazard(c.level.getBlockState(new net.minecraft.core.BlockPos(x, y, z)))) return false;
                }
        // Entity.move reads both the foot block and getOnPos(.500001F) support speed.
        // Include a conservative one-block foot/support band for ALL predicted native positions.
        int low = (int)Math.floor(Math.min(from.y(), to.y()) - 1D);
        int high = (int)Math.floor(Math.max(from.y(), to.y()));
        double speedCells = nx * nz * (high - low + 1D);
        if (low < c.level.getMinY() || speedCells < 1 || speedCells > pass.remaining[0]) return false;
        for (int x = (int)Math.floor(box.minX); x <= (int)Math.floor(box.maxX); x++)
            for (int y = low; y <= high; y++) for (int z = (int)Math.floor(box.minZ); z <= (int)Math.floor(box.maxZ); z++) {
                if (!pass.observe(x, y, z)) return false;
                var state = c.level.getBlockState(new net.minecraft.core.BlockPos(x, y, z));
                if (Float.floatToIntBits(state.getBlock().getSpeedFactor()) != Float.floatToIntBits(1F)) return false;
            }
        return c.level.noCollision(c.player, box);
    }
    static boolean groundFriction(Minecraft c, Vec3 from, Vec3 to, float friction, Pass pass) {
        if (!GroundLocomotionMotion.finite(from) || !GroundLocomotionMotion.finite(to)
                || Math.abs(from.y() - to.y()) > .000001 || c.level == null) return false;
        double minX = Math.floor(Math.min(from.x(), to.x()) - .3);
        double maxX = Math.floor(Math.max(from.x(), to.x()) + .3);
        double minZ = Math.floor(Math.min(from.z(), to.z()) - .3);
        double maxZ = Math.floor(Math.max(from.z(), to.z()) + .3);
        double volume = (maxX - minX + 1) * (maxZ - minZ + 1);
        if (!Double.isFinite(volume) || volume < 1 || volume > pass.remaining[0]
                || minX < Integer.MIN_VALUE || maxX >= Integer.MAX_VALUE
                || minZ < Integer.MIN_VALUE || maxZ >= Integer.MAX_VALUE
                || from.y() < c.level.getMinY() || from.y() >= c.level.getMaxY()) return false;
        int y = (int)Math.floor(from.y() - .001);
        for (int x = (int)minX; x <= (int)maxX; x++) for (int z = (int)minZ; z <= (int)maxZ; z++) {
            if (!c.level.getChunkSource().hasChunk(x >> 4, z >> 4)) return false;
            if (!pass.observe(x, y, z)) return false;
            var state = c.level.getBlockState(new net.minecraft.core.BlockPos(x, y, z));
            var pos = new net.minecraft.core.BlockPos(x, y, z);
            if (hazard(state) || Float.floatToIntBits(state.getBlock().getSpeedFactor()) != Float.floatToIntBits(1F)
                    || Float.floatToIntBits(state.getBlock().getFriction())
                    != Float.floatToIntBits(friction) || !state.isFaceSturdy(c.level, pos, net.minecraft.core.Direction.UP)) return false;
            var shape = state.getCollisionShape(c.level, pos);
            if (shape.isEmpty() || Math.abs(y + shape.max(net.minecraft.core.Direction.Axis.Y) - from.y()) > .000001) return false;
        }
        return true;
    }
    public static boolean groundBrake(Minecraft c, McGroundNativePhysicsProfile.Journey journey,
            Vec3 origin, GroundProfileForecast.Brake forecast) {
        if (c == null || journey == null || !journey.capturedClient(c)
                || forecast == null || forecast.samples().isEmpty() || forecast.samples().size() > 64
                || !forecast.origin().equals(origin) || c.player == null) return false;
        int observedTick = c.player.tickCount;
        var fence = new DynamicRouteQueryFence(System::nanoTime, () -> journey.matchesForecast(forecast.profileState(), forecast.samples().size())
                && sameStart(c, origin, forecast.observedVelocity(), observedTick, true), 50_000_000L);
        var pass = new Pass(c); Vec3 previous = origin;
        for (var sample : forecast.samples()) {
            var from = previous; var to = sample.position();
            if (!fence.query(() -> union(c, from, to, pass))
                    || !fence.query(() -> groundFriction(c, from, to, forecast.blockFriction(), pass))) return false;
            previous = to;
        }
        var last = previous; var coast = forecast.asymptoticCoast();
        return fence.query(() -> union(c, last, coast, pass))
                && fence.query(() -> groundFriction(c, last, coast, forecast.blockFriction(), pass))
                && journey.matchesForecast(forecast.profileState(), forecast.samples().size())
                && sameStart(c, origin, forecast.observedVelocity(), observedTick, true)
                && pass.revalidate(fence)
                && journey.callbackFreeForecastCurrent(forecast.profileState(), forecast.samples().size())
                && sameStart(c, origin, forecast.observedVelocity(), observedTick, true) && fence.available();
    }
    /** Flight can be blocked by the VI apex even when an ordinary 1.26-high jump clears.
     * Positive means ONLY all nominal flight bodies are clear; no supported landing is inferred. */
    public static boolean flightBodies(Minecraft c, McGroundNativePhysicsProfile.Journey journey,
            Vec3 origin, GroundProfileForecast.Jump forecast) {
        if (c == null || journey == null || !journey.capturedClient(c) || forecast == null
                || forecast.samples().isEmpty() || forecast.samples().size() > 64
                || !forecast.origin().equals(origin) || c.player == null) return false;
        int observedTick = c.player.tickCount;
        boolean grounded = forecast.phase() == GroundProfileForecast.StartPhase.GROUNDED_TAKEOFF;
        var fence = new DynamicRouteQueryFence(System::nanoTime, () -> journey.matchesForecast(forecast.profileState(), forecast.samples().size())
                && sameStart(c, origin, forecast.observedVelocity(), observedTick, grounded)
                && (!grounded || takeoffFriction(c, forecast.takeoffFriction())), 50_000_000L);
        var pass = new Pass(c); Vec3 previous = origin;
        for (var sample : forecast.samples()) {
            var from = previous; var to = sample.position();
            if (!fence.query(() -> union(c, from, to, pass))) return false;
            previous = to;
        }
        return journey.matchesForecast(forecast.profileState(), forecast.samples().size())
                && sameStart(c, origin, forecast.observedVelocity(), observedTick, grounded)
                && (!grounded || takeoffFriction(c, forecast.takeoffFriction()))
                && pass.revalidate(fence)
                && journey.callbackFreeForecastCurrent(forecast.profileState(), forecast.samples().size())
                && sameStart(c, origin, forecast.observedVelocity(), observedTick, grounded)
                && (!grounded || takeoffFriction(c, forecast.takeoffFriction())) && fence.available();
    }
}
