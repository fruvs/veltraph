package dev.veltraph.client.combat.ghost;

import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.combat.ghost.GhostSourceSampledRay.Point;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.Cell;
import dev.veltraph.client.config.GhostGrindConfig;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Fresh client-thread modern clearance. Native observations only; no pose/input/world mutations. */
public final class McGhostOpenWalkClearance implements GhostOpenWalkVariation.Geometry {
    private static final long MAX_READ_NANOS=4_000_000L;
    private final Minecraft client;
    private final GhostGrindModule parent;
    private final GhostSourceWalkJob job;
    private final McGhostSourceOwnedWalkCursor cursor;
    private final Creeper target;
    private final Object player,world,connection;
    private final int tick;
    private final double halfWidth,height;
    private final long cameraEpoch,readStart;
    private final String forbiddenJson;
    private final Set<Cell> forbidden;
    private McGhostOpenWalkClearance(Minecraft client,GhostGrindModule parent,GhostSourceWalkJob job,McGhostSourceOwnedWalkCursor cursor,Creeper target){
        this.client=client;this.parent=parent;this.job=job;this.cursor=cursor;this.target=target;
        if(client==null||!client.isSameThread()||job==null||cursor==null||target==null||job.parent()!=parent||!cursor.current(client))throw new IllegalStateException("Actual installed owned source cursor required");
        player=client.player;world=client.level;connection=client.getConnection();tick=client.player.tickCount;
        var size=client.player.getDimensions(Pose.STANDING);halfWidth=size.width()/2d;height=size.height();
        if(!Double.isFinite(halfWidth)||halfWidth<.01||halfWidth>2||!Double.isFinite(height)||height<.01||height>4)throw new IllegalStateException("Actual finite standing body required");
        cameraEpoch=dev.veltraph.client.service.mc.McOwnedRotation.epoch(client);
        forbiddenJson=VeltraphBootstrap.config().get(GhostGrindConfig.GHOST_WALK_FORBIDDEN_CELLS);
        forbidden=GhostSourceWalkForbiddenCells.fromActualConfiguredJson(forbiddenJson).requireObserved();
        readStart=System.nanoTime();
    }
    public static McGhostOpenWalkClearance observe(Minecraft client,GhostGrindModule parent,GhostSourceWalkJob job,McGhostSourceOwnedWalkCursor cursor,Creeper target){return new McGhostOpenWalkClearance(client,parent,job,cursor,target);}
    public boolean current(){return client.isSameThread()&&client.player==player&&client.level==world&&client.getConnection()==connection&&client.player!=null&&client.player.tickCount==tick
        &&client.player.isAlive()&&client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty()
        &&parent.isEnabled()&&parent.sourceNativeInstalledWalkCursor()==cursor&&parent.sourceNativeSelectedTarget(parent)==target&&job.issuedReceipt().scopeCurrent()
        &&VeltraphBootstrap.moduleManager().inputOwner().orElse(null)==parent&&dev.veltraph.client.service.mc.McOwnedRotation.authority(client)==parent
        &&dev.veltraph.client.service.mc.McOwnedRotation.epoch(client)==cameraEpoch
        &&java.util.Objects.equals(VeltraphBootstrap.config().get(GhostGrindConfig.GHOST_WALK_FORBIDDEN_CELLS),forbiddenJson);}
    public boolean supportedSweep(Point from,Point to){
        if(!current()||Math.abs(from.y()-to.y())>.02||from.distance(to)>52)return false;
        int samples=Math.max(1,(int)Math.ceil(from.distance(to)*10));
        for(int i=0;i<=samples;i++){
            if(System.nanoTime()-readStart>=MAX_READ_NANOS)return false;
            double t=i/(double)samples;var p=new Point(from.x()+(to.x()-from.x())*t,from.y(),from.z()+(to.z()-from.z())*t);
            if(!supportedBody(p))return false;
        }
        return current();
    }
    private boolean supportedBody(Point p){
        var level=client.level;var box=new AABB(p.x()-halfWidth,p.y()+.001,p.z()-halfWidth,p.x()+halfWidth,p.y()+height,p.z()+halfWidth);
        for(int x=(int)Math.floor(box.minX);x<=Math.floor(box.maxX);x++)for(int y=(int)Math.floor(box.minY);y<=Math.floor(box.maxY);y++)for(int z=(int)Math.floor(box.minZ);z<=Math.floor(box.maxZ);z++){
            var pos=new net.minecraft.core.BlockPos(x,y,z);if(!level.getChunkSource().hasChunk(x>>4,z>>4))return false;
            if(y==(int)Math.floor(p.y())&&forbidden.contains(new Cell(x,y,z)))return false;
            if(hazard(level.getBlockState(pos)))return false;
        }
        if(!level.noCollision(client.player,box))return false;
        for(double dx:new double[]{-halfWidth,halfWidth})for(double dz:new double[]{-halfWidth,halfWidth}){
            int x=(int)Math.floor(p.x()+dx),y=(int)Math.floor(p.y()-.001),z=(int)Math.floor(p.z()+dz);
            var pos=new net.minecraft.core.BlockPos(x,y,z);if(!level.getChunkSource().hasChunk(x>>4,z>>4))return false;
            var state=level.getBlockState(pos);if(hazard(state))return false;
            boolean support=false;double localX=p.x()+dx-x,localZ=p.z()+dz-z;
            for(var part:state.getCollisionShape(level,pos).toAabbs())if(localX>=part.minX&&localX<=part.maxX&&localZ>=part.minZ&&localZ<=part.maxZ&&Math.abs(y+part.maxY-p.y())<.02){support=true;break;}
            if(!support)return false;
        }
        for(var actor:level.entitiesForRendering())if(actor!=client.player&&actor!=target&&actor instanceof LivingEntity living&&living.isAlive()&&!actor.isInvisible()&&actor.getBoundingBox().intersects(box))return false;
        return current();
    }
    private static boolean hazard(net.minecraft.world.level.block.state.BlockState state){return !state.getFluidState().isEmpty()||state.is(Blocks.FIRE)||state.is(Blocks.SOUL_FIRE)||state.is(Blocks.COBWEB)||state.is(Blocks.SWEET_BERRY_BUSH)||state.is(Blocks.POWDER_SNOW)||state.is(Blocks.MAGMA_BLOCK)||state.is(Blocks.CACTUS)||state.is(Blocks.CAMPFIRE)||state.is(Blocks.SOUL_CAMPFIRE);}
    /** Mandatory final native CURRENT-yaw key sweep for the chosen alternative, before any key write. */
    public boolean keysClear(GhostSourceWalkSteering.Intent intent){
        if(intent==null||intent.outcome()==GhostSourceWalkSteering.Outcome.REJECTED||intent.jump()||!current())return false;
        var p=client.player;int f=(intent.forward()?1:0)-(intent.back()?1:0),s=(intent.left()?1:0)-(intent.right()?1:0);double n=Math.hypot(f,s);
        if(n==0)return current();double yaw=Math.toRadians(p.getYRot());
        var from=new Point(p.getX(),p.getY(),p.getZ());var to=new Point(from.x()+(-Math.sin(yaw)*f+Math.cos(yaw)*s)/n*.35,from.y(),from.z()+(Math.cos(yaw)*f+Math.sin(yaw)*s)/n*.35);
        return p.onGround()&&supportedSweep(from,to)&&current();
    }
}
