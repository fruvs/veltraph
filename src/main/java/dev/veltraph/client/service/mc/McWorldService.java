package dev.veltraph.client.service.mc;

import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.world.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Minecraft-backed {@link WorldService} with deterministic block-level queries:
 *
 * <ul>
 *   <li><b>passable</b> — the block's collision shape is empty (player body can occupy it).</li>
 *   <li><b>standable</b> — the block's top face is a solid full square (a player can stand on it).</li>
 *   <li><b>blockId</b> — the namespaced registry id (e.g. {@code "minecraft:nether_wart"}).</li>
 * </ul>
 *
 * Null-safe: returns conservative {@code false}/empty when no world is loaded.
 */
public final class McWorldService implements WorldService {

    private final Minecraft client;

    public McWorldService(Minecraft client) {
        this.client = client;
    }

    private ClientLevel world() {
        return client.level;
    }

    @Override
    public boolean isLoaded(BlockPos pos) {
        ClientLevel w = world();
        return w != null && w.getChunkSource().hasChunk(pos.x() >> 4,pos.z() >> 4);
    }

    @Override
    public boolean isPassable(BlockPos pos) {
        ClientLevel w = world();
        if (w == null || !isLoaded(pos)) {
            return false;
        }
        net.minecraft.core.BlockPos mcPos = McConversions.toMc(pos);
        BlockState state = w.getBlockState(mcPos);
        return state.getCollisionShape(w, mcPos).isEmpty();
    }

    @Override
    public boolean isStandable(BlockPos pos) {
        ClientLevel w = world();
        if (w == null || !isLoaded(pos)) {
            return false;
        }
        net.minecraft.core.BlockPos mcPos = McConversions.toMc(pos);
        BlockState state = w.getBlockState(mcPos);
        return state.isFaceSturdy(w, mcPos, Direction.UP);
    }

    private boolean validProbe(dev.veltraph.client.world.Vec3 feet,double halfWidth,double height) {
        return feet!=null&&Double.isFinite(feet.x())&&Double.isFinite(feet.y())&&Double.isFinite(feet.z())
                &&Math.abs(feet.x())<=30_000_000&&Math.abs(feet.z())<=30_000_000&&Math.abs(feet.y())<=4096
                &&Double.isFinite(halfWidth)&&halfWidth>=.01&&halfWidth<=2&&Double.isFinite(height)&&height>=.01&&height<=4;
    }
    /** The live actor's current pose/scale is authoritative; dimensions are conservative fallback probes. */
    private net.minecraft.world.phys.AABB actorBox(dev.veltraph.client.world.Vec3 feet,double halfWidth,double height) {
        if(client.player!=null) {
            var box=client.player.getBoundingBox();
            if(!Double.isFinite(box.minX)||!Double.isFinite(box.minY)||!Double.isFinite(box.minZ)
                    ||!Double.isFinite(box.maxX)||!Double.isFinite(box.maxY)||!Double.isFinite(box.maxZ)
                    ||box.getXsize()<.01||box.getZsize()<.01||box.getYsize()<.01
                    ||box.getXsize()>4||box.getZsize()>4||box.getYsize()>4)return null;
            return box.move(feet.x()-client.player.getX(),feet.y()-client.player.getY(),feet.z()-client.player.getZ());
        }
        return new net.minecraft.world.phys.AABB(feet.x()-halfWidth,feet.y(),feet.z()-halfWidth,
                feet.x()+halfWidth,feet.y()+height,feet.z()+halfWidth);
    }
    private boolean loadedVolume(net.minecraft.world.phys.AABB box) {
        var level=world();if(level==null||box==null)return false;
        int minX=(int)Math.floor(box.minX),maxX=(int)Math.floor(box.maxX-1e-7);
        int minY=(int)Math.floor(box.minY),maxY=(int)Math.floor(box.maxY-1e-7);
        int minZ=(int)Math.floor(box.minZ),maxZ=(int)Math.floor(box.maxZ-1e-7);
        for(int x=minX;x<=maxX;x++)for(int y=minY;y<=maxY;y++)for(int z=minZ;z<=maxZ;z++) {
            var p=new net.minecraft.core.BlockPos(x,y,z);
            if(level.isOutsideBuildHeight(p)||!level.getChunkSource().hasChunk(x >> 4,z >> 4))return false;
        }
        return true;
    }
    @Override public boolean bodyClear(dev.veltraph.client.world.Vec3 feet,double halfWidth,double height) {
        if(!validProbe(feet,halfWidth,height)||client.player==null||world()==null||client.player.level()!=world())return false;
        var box=actorBox(feet,halfWidth,height);if(box==null)return false;
        var clearBox=new net.minecraft.world.phys.AABB(box.minX,box.minY+.001,box.minZ,box.maxX,box.maxY,box.maxZ);
        // Vanilla VoxelShape collision resolution uses 1e-7 at contact boundaries. The full-cube
        // query fast path instead uses raw AABB.intersects, exposing float-width rounding at an
        // otherwise native wall contact. Keep loading on the full box; only match that horizontal
        // numerical boundary for the overlap query, without changing pose/support or thin shapes.
        var contactBox=new net.minecraft.world.phys.AABB(clearBox.minX+1e-7,clearBox.minY,clearBox.minZ+1e-7,
                clearBox.maxX-1e-7,clearBox.maxY,clearBox.maxZ-1e-7);
        return loadedVolume(clearBox)&&world().noCollision(client.player,contactBox);
    }
    @Override public boolean hasGroundSupport(dev.veltraph.client.world.Vec3 feet,double halfWidth) {
        if(!validProbe(feet,halfWidth,1.8)||client.player==null||world()==null||client.player.level()!=world())return false;
        var body=actorBox(feet,halfWidth,1.8);if(body==null)return false;
        var contact=new net.minecraft.world.phys.AABB(body.minX+.0001,body.minY-.025,body.minZ+.0001,
                body.maxX-.0001,body.minY+.0001,body.maxZ-.0001);
        return loadedVolume(contact)&&world().getBlockCollisions(client.player,contact).iterator().hasNext();
    }

    /** Read-only MKD .35-block transfer geometry; this result never grants native input permission. */
    public boolean melonKingTransferClear(dev.veltraph.client.world.Vec3 originalFeet,
            dev.veltraph.client.world.Direction heading,java.util.function.BooleanSupplier moduleStillCurrent) {
        if(!client.isSameThread()||!validProbe(originalFeet,.3,1.8)||heading==null||heading.dy()!=0
                ||moduleStillCurrent==null)return false;
        var player=client.player;var level=client.level;var mode=client.gameMode;
        if(player==null||level==null||mode==null||player.level()!=level)return false;
        var listener=player.connection;
        if(listener==null||client.getConnection()!=listener)return false;
        var transport=listener.getConnection();if(transport==null)return false;
        var position=player.position();var box=player.getBoundingBox();var pose=player.getPose();
        float yaw=player.getYRot(),pitch=player.getXRot();
        if(position==null||box==null||!Double.isFinite(position.x)||!Double.isFinite(position.y)||!Double.isFinite(position.z)
                ||!Float.isFinite(yaw)||!Float.isFinite(pitch)
                ||Double.compare(position.x,originalFeet.x())!=0||Double.compare(position.y,originalFeet.y())!=0
                ||Double.compare(position.z,originalFeet.z())!=0)return false;
        class Read {
            boolean raw(){return client.isSameThread()&&client.player==player&&client.level==level
                    &&client.gameMode==mode&&player.connection==listener;}
            boolean current(){
                if(!raw()||player.level()!=level||!raw())return false;
                var actualPosition=player.position();if(!raw()||actualPosition!=position)return false;
                var actualBox=player.getBoundingBox();if(!raw()||actualBox!=box)return false;
                var actualPose=player.getPose();if(!raw()||actualPose!=pose)return false;
                float actualYaw=player.getYRot();if(!raw()||Float.floatToRawIntBits(actualYaw)!=Float.floatToRawIntBits(yaw))return false;
                float actualPitch=player.getXRot();if(!raw()||Float.floatToRawIntBits(actualPitch)!=Float.floatToRawIntBits(pitch))return false;
                var actualListener=client.getConnection();if(!raw()||actualListener!=listener)return false;
                var actualTransport=listener.getConnection();return raw()&&actualTransport==transport;
            }
        }
        var read=new Read();
        if(!read.current()||!moduleStillCurrent.getAsBoolean()||!read.current())return false;
        var probe=new dev.veltraph.client.world.Vec3(originalFeet.x()+heading.dx()*.35,
                originalFeet.y(),originalFeet.z()+heading.dz()*.35);
        for(int x=(int)Math.floor(probe.x()-.3);x<=(int)Math.floor(probe.x()+.3-1e-7);x++)
            for(int y=(int)Math.floor(probe.y()-.025);y<=(int)Math.floor(probe.y()+1.8-1e-7);y++)
                for(int z=(int)Math.floor(probe.z()-.3);z<=(int)Math.floor(probe.z()+.3-1e-7);z++) {
                    if(!read.current()||!isLoaded(new dev.veltraph.client.world.BlockPos(x,y,z))||!read.current())return false;
                }
        if(!bodyClear(probe,.3,1.8)||!read.current())return false;
        if(!hasGroundSupport(probe,.3)||!read.current())return false;
        return moduleStillCurrent.getAsBoolean()&&read.current();
    }

    /** Read-only MKD entry soil-bank contact geometry; this result never grants native input permission. */
    public boolean melonKingEntryBankContact(dev.veltraph.client.world.Vec3 originalFeet,
            dev.veltraph.client.world.Direction heading,dev.veltraph.client.world.BlockPos originalBank,java.util.function.BooleanSupplier moduleStillCurrent) {
        if(!client.isSameThread()||!validProbe(originalFeet,.3,1.8)||heading==null||heading.dy()!=0
                ||originalBank==null||moduleStillCurrent==null)return false;
        var expectedBank=new dev.veltraph.client.world.Vec3(originalFeet.x(),originalFeet.y()+.15,originalFeet.z()).toBlockPos().offset(heading);
        if(!originalBank.equals(expectedBank))return false;
        var player=client.player;var level=client.level;var mode=client.gameMode;
        if(player==null||level==null||mode==null||player.level()!=level)return false;
        var listener=player.connection;
        if(listener==null||client.getConnection()!=listener)return false;
        var transport=listener.getConnection();if(transport==null)return false;
        var position=player.position();var box=player.getBoundingBox();var pose=player.getPose();
        float yaw=player.getYRot(),pitch=player.getXRot();
        if(position==null||box==null||!Double.isFinite(position.x)||!Double.isFinite(position.y)||!Double.isFinite(position.z)
                ||!Float.isFinite(yaw)||!Float.isFinite(pitch)
                ||Double.compare(position.x,originalFeet.x())!=0||Double.compare(position.y,originalFeet.y())!=0
                ||Double.compare(position.z,originalFeet.z())!=0)return false;
        class Read {
            boolean raw(){return client.isSameThread()&&client.player==player&&client.level==level
                    &&client.gameMode==mode&&player.connection==listener;}
            boolean current(){
                if(!raw()||player.level()!=level||!raw())return false;
                var actualPosition=player.position();if(!raw()||actualPosition!=position)return false;
                var actualBox=player.getBoundingBox();if(!raw()||actualBox!=box)return false;
                var actualPose=player.getPose();if(!raw()||actualPose!=pose)return false;
                float actualYaw=player.getYRot();if(!raw()||Float.floatToRawIntBits(actualYaw)!=Float.floatToRawIntBits(yaw))return false;
                float actualPitch=player.getXRot();if(!raw()||Float.floatToRawIntBits(actualPitch)!=Float.floatToRawIntBits(pitch))return false;
                var actualListener=client.getConnection();if(!raw()||actualListener!=listener)return false;
                var actualTransport=listener.getConnection();return raw()&&actualTransport==transport;
            }
        }
        var read=new Read();
        if(!read.current()||!moduleStillCurrent.getAsBoolean()||!read.current())return false;
        var probe=new dev.veltraph.client.world.Vec3(originalFeet.x()+heading.dx()*.0001,
                originalFeet.y(),originalFeet.z()+heading.dz()*.0001);
        for(int x=(int)Math.floor(probe.x()-.3);x<=(int)Math.floor(probe.x()+.3-1e-7);x++)
            for(int y=(int)Math.floor(probe.y()-.025);y<=(int)Math.floor(probe.y()+1.8-1e-7);y++)
                for(int z=(int)Math.floor(probe.z()-.3);z<=(int)Math.floor(probe.z()+.3-1e-7);z++) {
                    if(!read.current()||!isLoaded(new dev.veltraph.client.world.BlockPos(x,y,z))||!read.current())return false;
                }
        if(!bodyClear(originalFeet,.3,1.8)||!read.current())return false;
        if(!hasGroundSupport(originalFeet,.3)||!read.current())return false;
        var bank=new net.minecraft.core.BlockPos(originalBank.x(),originalBank.y(),originalBank.z());
        if(!level.isLoaded(bank)||!read.current())return false;
        var bankState=level.getBlockState(bank);if(!read.current())return false;
        if(!bankState.is(net.minecraft.world.level.block.Blocks.DIRT)&&!bankState.is(net.minecraft.world.level.block.Blocks.FARMLAND))return false;
        var shape=bankState.getCollisionShape(level,bank);if(!read.current())return false;
        var boxes=shape.toAabbs();if(!read.current())return false;
        var shifted=actorBox(probe,.3,1.8);if(shifted==null||!read.current())return false;
        var overlap=new net.minecraft.world.phys.AABB(shifted.minX+1e-7,shifted.minY+.001,shifted.minZ+1e-7,
                shifted.maxX-1e-7,shifted.maxY,shifted.maxZ-1e-7);
        boolean bankContact=false;
        for(var bankBox:boxes)if(bankBox.move(bank.getX(),bank.getY(),bank.getZ()).intersects(overlap)){bankContact=true;break;}
        if(!bankContact||!read.current())return false;
        if(bodyClear(probe,.3,1.8)||!read.current())return false;
        if(!hasGroundSupport(probe,.3)||!read.current())return false;
        if(level.getBlockState(bank)!=bankState||!read.current())return false;
        return moduleStillCurrent.getAsBoolean()&&read.current();
    }

    @Override public boolean envelopeProbe(dev.veltraph.client.world.Vec3 center,double margin){
        if(!client.isSameThread()||!validProbe(center,.3,1.8)||!Double.isFinite(margin)||margin<0||margin>.1
                ||client.player==null||world()==null||client.player.level()!=world())return false;
        var body=actorBox(center,.3,1.8);if(body==null||body.getXsize()<=2*margin+.0002||body.getZsize()<=2*margin+.0002)return false;
        var expanded=new net.minecraft.world.phys.AABB(body.minX-margin,body.minY+.001,body.minZ-margin,body.maxX+margin,body.maxY,body.maxZ+margin);
        var support=new net.minecraft.world.phys.AABB(body.minX+margin+.0001,body.minY-.025,body.minZ+margin+.0001,body.maxX-margin-.0001,body.minY+.0001,body.maxZ-margin-.0001);
        return loadedVolume(expanded)&&loadedVolume(support)&&world().noCollision(client.player,expanded)
                &&world().getBlockCollisions(client.player,support).iterator().hasNext();
    }

    @Override
    public boolean isWater(BlockPos pos) {
        var w=world();
        return w!=null && isLoaded(pos) && w.getFluidState(McConversions.toMc(pos))
                .is(net.minecraft.tags.FluidTags.WATER);
    }

    @Override
    public String blockId(BlockPos pos) {
        ClientLevel w = world();
        if (w == null || !isLoaded(pos)) {
            return "";
        }
        BlockState state = w.getBlockState(McConversions.toMc(pos));
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    @Override
    public java.util.Optional<String> blockProperty(BlockPos pos, String name) {
        var level=world();
        if(level==null||pos==null||name==null||!isLoaded(pos))return java.util.Optional.empty();
        var nativePos=McConversions.toMc(pos);
        if(level.isOutsideBuildHeight(nativePos))return java.util.Optional.empty();
        var state=level.getBlockState(nativePos);
        for(var property:state.getProperties()) {
            if(property.getName().equals(name))return java.util.Optional.of(propertyValue(state,property));
        }
        return java.util.Optional.empty();
    }

    private static <T extends Comparable<T>> String propertyValue(BlockState state,
            net.minecraft.world.level.block.state.properties.Property<T> property) {
        return property.getName(state.getValue(property));
    }

    @Override
    public java.util.Optional<dev.veltraph.client.world.Vec3> interactionPoint(BlockPos pos,
            dev.veltraph.client.world.Vec3 eye, double range) {
        return McBlockInteractionPoints.find(client,pos,eye,range);
    }
}
