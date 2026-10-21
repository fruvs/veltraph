package dev.veltraph.client.combat.ghost;

import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.service.mc.McOwnedRotation;
import dev.veltraph.client.world.Vec3;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;

/** Qualified modern zero-input forecast, not a motion normalization or original melee algorithm. */
public final class McGhostMeleeStayForecast {
    private McGhostMeleeStayForecast(){}
    public static final class Receipt {
        private final Minecraft client;private final GhostGrindModule parent;private final GhostSourceWalkJob job;private final Creeper target;
        private final McNavigator terrain;private final GhostConstrainedMeleeStandoff.Frame frame;private final net.minecraft.world.phys.Vec3 actorMotion,eye;private final float yaw,pitch;private final AABB actorBox,targetBox;
        private Receipt(Minecraft c,GhostGrindModule p,GhostSourceWalkJob j,Creeper t,GhostConstrainedMeleeStandoff.Frame f,McNavigator terrain){this.terrain=terrain;client=c;parent=p;job=j;target=t;frame=f;actorMotion=c.player.getDeltaMovement();eye=c.player.getEyePosition();yaw=c.player.getYRot();pitch=c.player.getXRot();actorBox=c.player.getBoundingBox();targetBox=t.getBoundingBox();}
        /** Fresh native geometric reads only; does not renew the issued job or emit input. */
        public boolean revalidate(){return current(frame)&&observe(client,parent,job,target,frame,terrain).isPresent();}
        /** No renewal, token minting or control; same actual source/native frame must still be current. */
        public boolean current(GhostConstrainedMeleeStandoff.Frame f){
            var s=frame.scope();var p=client.player;
            return f==frame&&client.isSameThread()&&p==s.player()&&client.level==s.world()&&client.getConnection()==s.connection()
                &&p!=null&&p.tickCount==f.tick()&&p.isAlive()&&p.onGround()&&client.gui.screen()==null&&p.level()==client.level&&!client.isPaused()&&!p.isScoping()
                &&client.getCameraEntity()==p&&!client.level.tickRateManager().isEntityFrozen(p)&&p.getYRot()==yaw&&p.getXRot()==pitch&&eye.equals(p.getEyePosition())&&actorBox.equals(p.getBoundingBox())&&targetBox.equals(target.getBoundingBox())
                &&parent==s.parent()&&job==s.job()&&target==s.target()&&parent.isEnabled()&&parent.sourceNativeSelectedTarget(parent)==target
                &&job.issuedReceipt().scopeCurrent()&&VeltraphBootstrap.moduleManager().inputOwner().orElse(null)==parent&&McOwnedRotation.authority(client)==parent
                &&McOwnedRotation.epoch(client)==s.cameraEpoch()&&target.isAlive()&&target.level()==client.level&&client.level.getEntity(target.getId())==target
                &&f.position().equals(new Vec3(p.getX(),p.getY(),p.getZ()))&&f.target().equals(new Vec3(target.getX(),target.getY(),target.getZ()))
                &&f.targetVelocity().equals(new Vec3(target.getDeltaMovement().x,target.getDeltaMovement().y,target.getDeltaMovement().z))
                &&actorMotion.equals(p.getDeltaMovement());
        }
    }
    /** Private constructor means caller-created Boolean flags cannot create a native stay receipt. */
    public static Optional<Receipt> observe(Minecraft c,GhostGrindModule parent,GhostSourceWalkJob job,Creeper target,
                                            GhostConstrainedMeleeStandoff.Frame frame,McNavigator terrain){
        if(c==null||!c.isSameThread()||c.player==null||c.level==null||parent==null||job==null||target==null||frame==null||frame.scope()==null||terrain==null)return Optional.empty();
        var receipt=new Receipt(c,parent,job,target,frame,terrain);if(!receipt.current(frame)||!frame.exactNativeHit()||frame.knownHazard()||!frame.grounded()||!frame.standingSafe()
            ||frame.tangentLeftSafe()||frame.tangentRightSafe()||c.player.hurtTime>0||target.getSwellDir()>0||target.isIgnited())return Optional.empty();
        if(!boundedBody(c.player.getBoundingBox())||!boundedBody(target.getBoundingBox()))return Optional.empty();
        var raw=target.getDeltaMovement();var motion=c.player.getDeltaMovement();
        if(!finite(raw)||!finite(motion)||raw.y!=0||motion.y>0||motion.y<-.10||Math.hypot(raw.x,raw.z)>.65||Math.hypot(motion.x,motion.z)>.35)return Optional.empty();

        var current=frame.position();var actorNext=new Vec3(current.x()+motion.x,current.y(),current.z()+motion.z);
        var targetNext=new Vec3(frame.target().x()+raw.x,frame.target().y()+raw.y,frame.target().z()+raw.z);
        // One native tick of retained horizontal actor momentum only. No stopping-time/friction guess.
        if(Math.abs(actorNext.horizontalDistanceTo(targetNext)-frame.desiredRadius())>.60
                ||!parent.sourceForecastActorStepClear(motion.x,motion.z)||!terrain.standingClear(current)||!terrain.standingClear(actorNext)||!terrain.walkClear(current,actorNext))return Optional.empty();
        // Actual grounded support plus collision blocks the player's ordinary negative gravity
        // component; this is a forecast projection, never a write to raw movement.
        if(motion.y<0&&c.level.noCollision(c.player,c.player.getBoundingBox().move(0,motion.y,0)))return Optional.empty();
        if(!GhostMeleeForecastMath.allows(frame.position(),frame.target(),new Vec3(motion.x,0,motion.z),frame.targetVelocity(),frame.desiredRadius(),c.player.getEyePosition(),c.player.getLookAngle(),target.getBoundingBox()))return Optional.empty();
        var sweep=c.player.getBoundingBox().minmax(c.player.getBoundingBox().move(motion.x,0,motion.z));
        if(!c.level.noCollision(c.player,sweep))return Optional.empty();
        for(var actor:c.level.entitiesForRendering())if(actor!=c.player&&actor!=target&&actor instanceof LivingEntity living&&living.isAlive()&&!actor.isInvisible()&&actor.getBoundingBox().intersects(sweep))return Optional.empty();
        var eye=c.player.getEyePosition();var look=c.player.getLookAngle();
        if(!ray(c,target,target.getBoundingBox(),eye,look))return Optional.empty();
        var futureEye=eye.add(motion.x,0,motion.z);var futureBox=target.getBoundingBox().move(raw);
        if(!loadedSafeBody(c,futureBox)||!c.level.noCollision(target,futureBox))return Optional.empty();
        if(!ray(c,target,futureBox,futureEye,look)||!receipt.current(frame))return Optional.empty();
        return Optional.of(receipt);
    }
    private static boolean ray(Minecraft c,Creeper target,AABB box,net.minecraft.world.phys.Vec3 eye,net.minecraft.world.phys.Vec3 look){
        var hit=box.clip(eye,eye.add(look.scale(3)));if(hit.isEmpty()||hit.get().distanceTo(eye)>3)return false;
        int samples=Math.max(1,(int)Math.ceil(eye.distanceTo(hit.get())*10));
        for(int i=0;i<=samples;i++){var q=eye.lerp(hit.get(),i/(double)samples);var pos=net.minecraft.core.BlockPos.containing(q);
            if(!c.level.getChunkSource().hasChunk(pos.getX()>>4,pos.getZ()>>4)||!c.level.getBlockState(pos).getFluidState().isEmpty())return false;}
        var block=c.level.clip(new ClipContext(eye,hit.get(),ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,c.player));
        if(block.getType()!=HitResult.Type.MISS&&block.getLocation().distanceTo(eye)<hit.get().distanceTo(eye)-.001)return false;
        for(var actor:c.level.entitiesForRendering())if(actor!=c.player&&actor!=target&&actor instanceof LivingEntity living&&living.isAlive()&&!actor.isInvisible()){
            var other=actor.getBoundingBox().clip(eye,hit.get());if(other.isPresent()&&other.get().distanceTo(eye)<hit.get().distanceTo(eye))return false;
        }
        return true;
    }
    private static boolean loadedSafeBody(Minecraft c,AABB box){
        for(int x=(int)Math.floor(box.minX);x<=Math.floor(box.maxX);x++)for(int y=(int)Math.floor(box.minY+.001);y<=Math.floor(box.maxY);y++)for(int z=(int)Math.floor(box.minZ);z<=Math.floor(box.maxZ);z++){
            if(!c.level.getChunkSource().hasChunk(x>>4,z>>4))return false;var state=c.level.getBlockState(new net.minecraft.core.BlockPos(x,y,z));
            if(!state.getFluidState().isEmpty()||state.is(net.minecraft.world.level.block.Blocks.FIRE)||state.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)||state.is(net.minecraft.world.level.block.Blocks.COBWEB)||state.is(net.minecraft.world.level.block.Blocks.CACTUS)||state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)||state.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)||state.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)||state.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)||state.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW))return false;
        }return true;
    }
    private static boolean boundedBody(AABB b){return Double.isFinite(b.minX)&&Double.isFinite(b.minY)&&Double.isFinite(b.minZ)&&Double.isFinite(b.maxX)&&Double.isFinite(b.maxY)&&Double.isFinite(b.maxZ)
        &&b.maxX>b.minX&&b.maxY>b.minY&&b.maxZ>b.minZ&&b.maxX-b.minX<=2&&b.maxZ-b.minZ<=2&&b.maxY-b.minY<=4;}
    private static boolean finite(net.minecraft.world.phys.Vec3 p){return Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
}
