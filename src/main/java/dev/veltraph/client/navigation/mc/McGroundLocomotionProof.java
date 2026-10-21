package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.navigation.*;
import dev.veltraph.client.world.Vec3;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Read-only loaded native body/support certificates. No velocity, abilities or block mutation. */
public final class McGroundLocomotionProof {
    private McGroundLocomotionProof(){}
    public static boolean sprintAvailable(Minecraft c){return sprintAvailable(c,null);}
    private static boolean sprintAvailable(Minecraft c,GroundLocomotionEvidence evidence){
        var p=c.player;
        return p!=null&&p.isAlive()&&!p.isSpectator()&&!p.isPassenger()&&!p.isUsingItem()
                &&!p.isInWater()&&!p.isInLava()&&!p.getAbilities().flying&&!p.isScoping()
                &&!p.hasEffect(MobEffects.BLINDNESS)&&p.getFoodData().getFoodLevel()>6
                &&GroundLocomotionMotion.sprint(evidence==null?velocity(c):evidence.sprintVelocity(velocity(c)),
                        evidence==null?p.getAttributeValue(Attributes.MOVEMENT_SPEED):evidence.sprintSpeed(p.getAttributeValue(Attributes.MOVEMENT_SPEED)));
    }
    private static Vec3 velocity(Minecraft c){var v=c.player.getDeltaMovement();return new Vec3(v.x,v.y,v.z);}
    private static Vec3 velocity(Minecraft c,GroundLocomotionEvidence e){return e.velocity(velocity(c));}
    private static double speed(Minecraft c,GroundLocomotionEvidence e){return e.speed(c.player.getAttributeValue(Attributes.MOVEMENT_SPEED));}
    private static boolean ordinaryJump(Minecraft c){
        var p=c.player;if(p==null||p.hasEffect(MobEffects.JUMP_BOOST)||p.hasEffect(MobEffects.LEVITATION)
                ||p.hasEffect(MobEffects.SLOW_FALLING)||p.isPassenger()||p.isUsingItem())return false;
        var d=p.getDimensions(Pose.STANDING);
        return Math.abs(d.height()-1.8)<.00001&&Math.abs(d.width()-.6)<.00001
                &&Math.abs(p.getAttributeValue(Attributes.JUMP_STRENGTH)-.42)<.000001
                &&Math.abs(p.getAttributeValue(Attributes.GRAVITY)-.08)<.000001;
    }
    private static boolean jumpEnvelope(McNavigator terrain,Vec3 takeoff,Vec3 landing,GroundLocomotionEvidence evidence){
        double length=takeoff.horizontalDistanceTo(landing);if(length<=.01||length>1.65)return false;
        double dx=(landing.x()-takeoff.x())/length,dz=(landing.z()-takeoff.z())/length;
        double apex=takeoff.y()+1.26;
        // Longitudinal headroom includes the same .10 behind/.35 past landing allowance
        // inspected by the observed momentum gate. Lateral bound is the original centerline
        // rectangle; actual body clearance is additionally read at each native sample.
        for(double progress=-.10;progress<=length+.350001;progress+=.05){
            for(double lateral:new double[]{-.18,0,.18}){
                var point=new Vec3(takeoff.x()+dx*progress-dz*lateral,apex,takeoff.z()+dz*progress+dx*lateral);
                if(!evidence.envelope("BODY",point,progress,lateral,terrain.bodyClear(point,false)))return false;
            }
        }
        Vec3 support=new Vec3(landing.x()+dx*.35,landing.y(),landing.z()+dz*.35);
        return evidence.envelope("LANDING_SUPPORT",support,length+.35,0,terrain.standingClear(support));
    }
    private static boolean upperSweepClear(McNavigator terrain,Vec3 from,Vec3 to){
        int count=Math.max(1,(int)Math.ceil(from.distanceTo(to)*10));
        for(int i=0;i<=count;i++){
            Vec3 p=TravelPath.lerp(from,to,(double)i/count);
            if(!terrain.bodyClear(new Vec3(p.x(),p.y()+.5,p.z()),false))return false;
        }
        return true;
    }
    public static GroundLocomotionPolicy.Proof capture(Minecraft c,McNavigator terrain,
            Vec3 actual,Vec3 target,Vec3 sprintTarget,Vec3 admittedDropOrigin,float actualYaw,GroundLocomotionPolicy policy,GroundJumpObservedLanding.AirSample acceptedAir,GroundDropBrakeForecast.Episode acceptedDropBrake,BooleanSupplier current){
        var evidence=new GroundLocomotionEvidence();
        // A single native query cannot be preempted, but a late result grants no permission.
        var fence=new DynamicRouteQueryFence(System::nanoTime,current,50_000_000L);
        if(actual==null||target==null||!current.getAsBoolean())return new GroundLocomotionPolicy.Proof(target,false,false,false,false);
        boolean body=evidence.observe(GroundLocomotionEvidence.Stage.BODY,fence.query(()->terrain.bodyClear(actual,false)));
        boolean landing=evidence.observe(GroundLocomotionEvidence.Stage.LANDING,fence.query(()->terrain.standingClear(target)));
        Vec3 takeoff=policy.jumpOrigin(actual);
        boolean activeJump=policy.phase()==GroundLocomotionPolicy.Phase.TAKEOFF||policy.phase()==GroundLocomotionPolicy.Phase.AIRBORNE||policy.phase()==GroundLocomotionPolicy.Phase.LANDING_BRAKE;
        boolean ownedContact=false;
        if(activeJump&&c.player!=null&&c.player.onGround()&&acceptedAir!=null){
            var contact=GroundJumpObservedLanding.contact(acceptedAir,policy.jumpIssuedTick(),c.player.tickCount,true,c.player.isSprinting(),
                    actual,velocity(c),takeoff,target,c.player.getAttributeValue(Attributes.MOVEMENT_SPEED)).orElse(null);
            ownedContact=evidence.observe(GroundLocomotionEvidence.Stage.OBSERVED_CONTACT,contact!=null)
                    &&evidence.observe(GroundLocomotionEvidence.Stage.CONTACT_COAST,fence.query(()->terrain.observedJumpLandingClear(actual,contact.coast())));
        }
        boolean observedLanding=ownedContact||(policy.phase()==GroundLocomotionPolicy.Phase.AIRBORNE||policy.phase()==GroundLocomotionPolicy.Phase.LANDING_BRAKE||policy.phase()==GroundLocomotionPolicy.Phase.DROPPING)
                &&c.player!=null&&c.player.onGround()&&GroundLocomotionMotion.landingMotion(actual,velocity(c),target,c.player.getAttributeValue(Attributes.MOVEMENT_SPEED))
                &&fence.query(()->terrain.standingClear(actual))
                &&fence.query(()->{Vec3 v=velocity(c);return terrain.walkClear(actual,new Vec3(actual.x()+v.x()*2,actual.y(),actual.z()+v.z()*2));});
        boolean motion=evidence.observe(GroundLocomotionEvidence.Stage.MOTION,c.player!=null&&(activeJump
                ?observedLanding||GroundLocomotionMotion.airborne(actual,velocity(c,evidence),takeoff,target,speed(c,evidence))
                :GroundLocomotionMotion.takeoff(velocity(c,evidence),speed(c,evidence),c.player.onGround(),c.player.isSprinting())));
        boolean landingBrake=false;
        if(activeJump&&!motion&&c.player!=null&&!c.player.onGround()&&policy.jumpIssuedTick()>=0){
            var forecast=GroundJumpLandingForecast.forecast(actual,evidence.velocityValue(),takeoff,target,evidence.speedValue(),c.player.tickCount-policy.jumpIssuedTick()).orElse(null);
            landingBrake=evidence.observe(GroundLocomotionEvidence.Stage.LANDING_FORECAST,forecast!=null)
                    &&evidence.observe(GroundLocomotionEvidence.Stage.LANDING_BRAKE_SWEEP,fence.query(()->terrain.airborneLandingClear(actual,forecast.landing(),forecast.groundCoast())));
        }
        boolean jump=(motion||landingBrake)&&evidence.observe(GroundLocomotionEvidence.Stage.NATIVE_TRAITS,ordinaryJump(c))
                &&evidence.observe(GroundLocomotionEvidence.Stage.ENDPOINTS,GroundLocomotionMotion.jumpEndpoints(takeoff,target))
                &&evidence.observe(GroundLocomotionEvidence.Stage.TAKEOFF_SUPPORT,fence.query(()->terrain.standingClear(takeoff)))
                &&evidence.observe(GroundLocomotionEvidence.Stage.STEP_SWEEP,fence.query(()->terrain.stepClear(takeoff,target)))
                &&evidence.observe(GroundLocomotionEvidence.Stage.JUMP_ENVELOPE,fence.query(()->jumpEnvelope(terrain,takeoff,target,evidence)));
        Vec3 dropOrigin=null;GroundDropBrakeForecast.Episode dropEpisode=null;
        if(admittedDropOrigin!=null){
            boolean landed=policy.phase()==GroundLocomotionPolicy.Phase.DROPPING&&observedLanding;
            evidence.dropOrigin(admittedDropOrigin);
            boolean dropMotion=evidence.observe(GroundLocomotionEvidence.Stage.DROP_MOTION,c.player!=null&&(landed||GroundLocomotionMotion.drop(actual,
                    evidence.dropVelocity(velocity(c)),admittedDropOrigin,target,evidence.dropSpeed(c.player.getAttributeValue(Attributes.MOVEMENT_SPEED)))));
            if(policy.phase()==GroundLocomotionPolicy.Phase.DROPPING&&c.player!=null&&(acceptedDropBrake!=null||!dropMotion)){
                // Fresh reads here grant only a new neutral-input episode, never the rejected forward permission.
                Vec3 v=velocity(c);double nativeSpeed=c.player.getAttributeValue(Attributes.MOVEMENT_SPEED);
                dropEpisode=(acceptedDropBrake==null?GroundDropBrakeForecast.begin(c.player.tickCount,c.player.onGround(),actual,v,admittedDropOrigin,target,nativeSpeed)
                        :GroundDropBrakeForecast.resume(acceptedDropBrake,c.player.tickCount,c.player.onGround(),actual,v,admittedDropOrigin,target,nativeSpeed)).orElse(null);
                var candidate=dropEpisode;
                if(!evidence.observe(GroundLocomotionEvidence.Stage.DROP_BRAKE_FORECAST,candidate!=null)
                        ||!evidence.observe(GroundLocomotionEvidence.Stage.DROP_BRAKE_BODY,fence.query(()->terrain.dropBrakingClear(candidate))))dropEpisode=null;
            }
            boolean drop=(dropMotion||dropEpisode!=null)
                    &&evidence.observe(GroundLocomotionEvidence.Stage.DROP_ORIGIN_SUPPORT,fence.query(()->terrain.standingClear(admittedDropOrigin)))
                    &&evidence.observe(GroundLocomotionEvidence.Stage.DROP_GRAPH_EDGE,fence.query(()->GroundPathfinder.canStep(terrain,GroundPathfinder.node(admittedDropOrigin),GroundPathfinder.node(target))))
                    &&evidence.observe(GroundLocomotionEvidence.Stage.DROP_STEP_SWEEP,fence.query(()->terrain.stepClear(admittedDropOrigin,target)));
            if(acceptedDropBrake!=null&&dropEpisode==null)drop=false;
            if(!drop)return new GroundLocomotionPolicy.Proof(target,false,landing,false,false,null,
                    evidence.snapshot("drop_rejected",policy.phase(),actual,takeoff,target));
            dropOrigin=admittedDropOrigin;
        }
        boolean sprint=false;
        if(sprintTarget==null)sprintTarget=target;
        double distance=actual.horizontalDistanceTo(sprintTarget),dy=sprintTarget.y()-actual.y();
        evidence.lead(sprintTarget,distance,dy);
        if(evidence.observe(GroundLocomotionEvidence.Stage.SPRINT_BODY_LANDING,body&&landing)
                &&evidence.observe(GroundLocomotionEvidence.Stage.SPRINT_DISTANCE,distance>=3)
                &&evidence.observe(GroundLocomotionEvidence.Stage.SPRINT_LEVEL,Math.abs(dy)<.05)
                &&evidence.observe(GroundLocomotionEvidence.Stage.SPRINT_AVAILABLE,sprintAvailable(c,evidence))
                &&evidence.observe(GroundLocomotionEvidence.Stage.SPRINT_GROUND,c.player.onGround())){
            double dx=(sprintTarget.x()-actual.x())/distance,dz=(sprintTarget.z()-actual.z())/distance;
            float desired=(float)Math.toDegrees(Math.atan2(-dx,dz));
            if(evidence.observe(GroundLocomotionEvidence.Stage.SPRINT_HEADING,evidence.heading(Math.abs(WalkController.wrap(desired-actualYaw)))<8)){
                // Require lateral room and at least 2.5 blocks of supported braking envelope.
                // A one-block-wide or two-block-high passage remains ordinary walking.
                double span=Math.min(3,distance-.5);sprint=true;
                for(double offset:new double[]{-.45,0,.45}){
                    Vec3 from=new Vec3(actual.x()-dz*offset,actual.y(),actual.z()+dx*offset);
                    Vec3 to=new Vec3(from.x()+dx*span,from.y(),from.z()+dz*span);
                    evidence.sweep(from,to);
                    if(!evidence.observe(GroundLocomotionEvidence.Stage.SPRINT_FROM_SUPPORT,fence.query(()->terrain.standingClear(from)))
                            ||!evidence.observe(GroundLocomotionEvidence.Stage.SPRINT_WALK_SWEEP,fence.query(()->terrain.walkClear(from,to)))
                            ||!evidence.observe(GroundLocomotionEvidence.Stage.SPRINT_HEADROOM,fence.query(()->upperSweepClear(terrain,from,to)))){sprint=false;break;}
                }
            }
        }
        if(!evidence.observe(GroundLocomotionEvidence.Stage.FINAL_BUDGET,fence.available())
                ||!evidence.observe(GroundLocomotionEvidence.Stage.FINAL_CONTEXT,current.getAsBoolean()))
            return new GroundLocomotionPolicy.Proof(target,false,false,false,false,null,evidence.snapshot("final_fence",policy.phase(),actual,takeoff,target));
        return new GroundLocomotionPolicy.Proof(target,body,landing,jump,sprint,dropOrigin,evidence.snapshot("completed",policy.phase(),actual,takeoff,target),landingBrake,dropEpisode);
    }
}
