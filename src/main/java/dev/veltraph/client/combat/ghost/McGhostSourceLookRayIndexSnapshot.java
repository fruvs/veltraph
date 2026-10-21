package dev.veltraph.client.combat.ghost;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import dev.veltraph.client.combat.ghost.GhostSourceSampledRay.*;

/** One actual native camera/target frame; read-only and no target-selection or control permission. */
public final class McGhostSourceLookRayIndexSnapshot {
    private final Minecraft client;private final LivingEntity target;
    private final Object player,world,connection;private final int tick;
    private final Vec3 eye,look,playerPosition,targetPosition;private final AABB targetBox;
    private final float width,height;private final Box deflated;private final Point targetCenter;
    private McGhostSourceLookRayIndexSnapshot(Minecraft client,LivingEntity target){
        this.client=client;this.target=target;player=client.player;world=client.level;connection=client.getConnection();tick=client.player.tickCount;
        eye=client.player.getEyePosition();look=client.player.getLookAngle();playerPosition=client.player.position();
        targetPosition=target.position();targetBox=target.getBoundingBox();width=target.getBbWidth();height=target.getBbHeight();
        var b=targetBox.inflate(-(double)width*.15,-(double)height*.1,-(double)width*.15);
        deflated=new Box(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);
        var pos=target.blockPosition();targetCenter=new Point(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);
        point(eye);point(look);point(playerPosition);point(targetPosition);
    }
    public static McGhostSourceLookRayIndexSnapshot observe(Minecraft client,LivingEntity target){
        if(client==null||!client.isSameThread()||client.player==null||client.level==null||client.getConnection()==null||target==null
                ||target.level()!=client.level||client.level.getEntity(target.getId())!=target)
            throw new IllegalStateException("Current native player/target/PWC required");
        return new McGhostSourceLookRayIndexSnapshot(client,target);
    }
    public boolean current(){return client.isSameThread()&&client.player==player&&client.level==world&&client.getConnection()==connection
            &&client.player.tickCount==tick&&client.player.getEyePosition().equals(eye)&&client.player.getLookAngle().equals(look)
            &&client.player.position().equals(playerPosition)&&target.level()==client.level&&client.level.getEntity(target.getId())==target
            &&target.position().equals(targetPosition)&&target.getBoundingBox().equals(targetBox)
            &&Float.floatToIntBits(target.getBbWidth())==Float.floatToIntBits(width)&&Float.floatToIntBits(target.getBbHeight())==Float.floatToIntBits(height);}
    public int sampleIndex(){requireCurrent();int index=GhostSourceLookRayIndex.sampleIndex(point(eye),point(look),deflated,targetCenter);requireCurrent();return index;}
    public double score(){requireCurrent();double d=client.player.distanceToSqr(target);int index=sampleIndex();requireCurrent();return GhostSourceLookRayIndex.score(d,index);}
    private void requireCurrent(){if(!current())throw new IllegalStateException("Native camera/target ranking frame changed");}
    private static Point point(Vec3 v){return new Point(v.x,v.y,v.z);}
}
