package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.navigation.*;
import dev.veltraph.client.world.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Package-private, read-only candidate observation. It never publishes or admits a Leg. */
final class McSupportedFlightPreview {
    record Traits(float flyingSpeed,float nativeSpeed,boolean discardFriction,boolean noPhysics,boolean spectator,double movement,double gravity,double drag,double friction,
                  double jump,float standingWidth,float standingHeight,Map<Object,Integer> effects) {}
    record Observation(Object player,Object world,Object connection,Vec3 goal,Traits traits,double cost) {}
    private McSupportedFlightPreview() {}
    private static Traits traits(Minecraft c) {
        var p=c.player;var effects=new HashMap<Object,Integer>();
        if(p.getActiveEffects().size()>64)return null;
        for(var effect:p.getActiveEffects())effects.put(effect.getEffect(),effect.getAmplifier());
        return new Traits(p.getAbilities().getFlyingSpeed(),p.getSpeed(),p.shouldDiscardFriction(),p.noPhysics,p.isSpectator(),p.getAttributeValue(Attributes.MOVEMENT_SPEED),
            p.getAttributeValue(Attributes.GRAVITY),p.getAttributeValue(Attributes.AIR_DRAG_MODIFIER),
            p.getAttributeValue(Attributes.FRICTION_MODIFIER),p.getAttributeValue(Attributes.JUMP_STRENGTH),
            p.getDimensions(net.minecraft.world.entity.Pose.STANDING).width(),p.getDimensions(net.minecraft.world.entity.Pose.STANDING).height(),Map.copyOf(effects));
    }
    private static boolean valid(Traits t){return t!=null&&!t.discardFriction()&&!t.noPhysics()&&!t.spectator()&&Float.isFinite(t.flyingSpeed())&&t.flyingSpeed()>0&&t.flyingSpeed()<=.5f
        &&Float.isFinite(t.nativeSpeed())&&t.nativeSpeed()>=0&&Double.isFinite(t.movement())&&Double.isFinite(t.gravity())&&Double.isFinite(t.drag())&&Double.isFinite(t.friction())&&Double.isFinite(t.jump())
        &&Float.isFinite(t.standingWidth())&&Float.isFinite(t.standingHeight())&&t.standingWidth()>0&&t.standingWidth()<4&&t.standingHeight()>0&&t.standingHeight()<4&&Double.isFinite(FlightMotionEnvelope.horizontalDecay(t.drag()))&&FlightMotionEnvelope.horizontalDecay(t.drag())<1;}
    static Observation capture(Minecraft c,Vec3 goal){
        if(c.player==null||c.level==null||c.getConnection()==null)return null;
        var t=traits(c);return !valid(t)?null:new Observation(c.player,c.level,c.getConnection(),goal,t,0);
    }
    static boolean current(Minecraft c,Observation o) {
        if(o==null||!c.isSameThread()||c.player!=o.player()||c.level!=o.world()||c.getConnection()!=o.connection()
            ||c.player==null||!(c.player instanceof dev.veltraph.client.mixin.FigJumpPowerAccessor)||!c.player.isAlive()||!c.player.getAbilities().mayfly||c.player.isInWater()||c.player.isInLava()
            ||c.player.isPassenger()||c.player.isFallFlying()||c.gui.screen()!=null||c.isPaused()||c.getCameraEntity()!=c.player
            ||c.player.level()!=c.level||c.player.isScoping()||c.level.tickRateManager().isEntityFrozen(c.player)
            ||c.player.containerMenu!=c.player.inventoryMenu||!c.player.inventoryMenu.getCarried().isEmpty())return false;
        var at=c.player.position();var motion=c.player.getDeltaMovement();
        if(!Double.isFinite(at.x)||!Double.isFinite(at.y)||!Double.isFinite(at.z)||!Double.isFinite(motion.x)||!Double.isFinite(motion.y)||!Double.isFinite(motion.z))return false;
        var actual=traits(c);return valid(actual)&&o.traits()!=null&&o.traits().equals(actual);
    }
    static Observation read(Minecraft c,McNavigator terrain,Vec3 goal,int radius,int nodes) {
        if(!c.isSameThread()||c.player==null||c.level==null||c.getConnection()==null||!SharedMovementPolicy.finite(goal)
            ||!c.player.getAbilities().mayfly||c.player.isInWater()||c.player.isInLava()||c.player.isPassenger()
            ||c.player.isFallFlying()||c.gui.screen()!=null||!McNativeFlightGeometry.standing(c,terrain,goal))return null;
        var nativeTraits=traits(c);if(!valid(nativeTraits))return null;float speed=nativeTraits.flyingSpeed();
        if(!Float.isFinite(speed)||speed<=0||speed>.5f)return null;
        var origin=new Vec3(c.player.getX(),c.player.getY(),c.player.getZ());
        if(!McNativeFlightGeometry.body(c,terrain,origin,0))return null;
        var first=c.player.onGround()?new Vec3(origin.x(),origin.y()+3,origin.z()):origin;
        var last=new Vec3(goal.x(),goal.y()+3,goal.z());
        java.util.function.BiPredicate<Vec3,Vec3> sweep=(a,b)->McNativeFlightGeometry.swept(c,terrain,a,b,0);
        if(!sweep.test(origin,first)||!sweep.test(last,goal))return null;
        var result=FlightPathfinder.find(new FlightPathfinder.Airspace(){
            public boolean clear(BlockPos p){return McNativeFlightGeometry.body(c,terrain,new Vec3(p.x()+.5,p.y(),p.z()+.5),0);}
            public boolean poseClear(Vec3 p){return McNativeFlightGeometry.body(c,terrain,p,0);}
        },first,last,radius,nodes,sweep);
        if(!result.found())return null;
        double cost=origin.distanceTo(first);Vec3 previous=first;
        for(var point:result.nodes()){if(!sweep.test(previous,point))return null;cost+=previous.distanceTo(point);previous=point;}
        if(!sweep.test(previous,last))return null;cost+=previous.distanceTo(last)+last.distanceTo(goal);
        var observation=new Observation(c.player,c.level,c.getConnection(),goal,nativeTraits,cost);
        return Double.isFinite(cost)&&current(c,observation)&&McNativeFlightGeometry.standing(c,terrain,goal)?observation:null;
    }
}
