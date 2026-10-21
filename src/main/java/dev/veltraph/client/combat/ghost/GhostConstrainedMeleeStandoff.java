package dev.veltraph.client.combat.ghost;

import dev.veltraph.client.world.Vec3;

/** Qualified modern constrained-melee motion policy; no source-planner or original melee claim. */
public final class GhostConstrainedMeleeStandoff {
    public record Scope(Object parent,Object player,Object world,Object connection,Object job,Object target,long cameraEpoch){
        public Scope{java.util.Objects.requireNonNull(parent);java.util.Objects.requireNonNull(player);java.util.Objects.requireNonNull(world);java.util.Objects.requireNonNull(connection);java.util.Objects.requireNonNull(job);java.util.Objects.requireNonNull(target);}
        boolean same(Scope s){return s!=null&&parent==s.parent&&player==s.player&&world==s.world&&connection==s.connection&&job==s.job&&target==s.target&&cameraEpoch==s.cameraEpoch;}
    }
    public record Frame(Scope scope,long tick,Vec3 position,Vec3 target,Vec3 targetVelocity,double desiredRadius,
                        boolean exactNativeHit,boolean grounded,boolean standingSafe,boolean tangentLeftSafe,boolean tangentRightSafe,boolean knownHazard){}
    public enum Outcome{MOVE,HOLD,REJECTED}
    public record Evidence(Outcome outcome,String reason,long tick,int stationaryTargetTicks,double distance,Scope scope){}
    private static final double ENTER_BAND=.35,EXIT_BAND=.60,TARGET_MOTION=.015;
    /** Immutable diagnostic inputs/results; never consulted by the motion policy. */
    public record Observation(Frame frame,long priorTick,Long tickGap,boolean sameScope,boolean continuityReset,Vec3 priorTarget,
                              Vec3 targetDisplacement,double speed3D,Double displacement3D,boolean velocityMoving,Boolean displacementMoving,
                              boolean heldBefore,int stationaryBefore,Double activeBand,Boolean withinBand,Evidence evidence,
                              boolean forecastPath,boolean actualPositionMoved,int consecutivePoseTicks,boolean safeZeroCandidate){}
    private Observation observation;
    private Scope scope;private long tick=Long.MIN_VALUE;private Vec3 target;private int stationaryTicks,observedPoseTicks;private boolean held;
    private Evidence evidence=new Evidence(Outcome.MOVE,"reset",Long.MIN_VALUE,0,Double.NaN,null);
    public void reset(){observation=null;scope=null;tick=Long.MIN_VALUE;target=null;stationaryTicks=observedPoseTicks=0;held=false;evidence=new Evidence(Outcome.MOVE,"reset",Long.MIN_VALUE,0,Double.NaN,null);}
    /** Called once by the actual validated source melee parent; render observers only read cached evidence. */
    public Evidence observe(Frame f){return observeInternal(f,null,false);}
    /** Only the independently validated native geometric receipt enables the modern zero candidate. */
    public Evidence observe(Frame f,McGhostMeleeStayForecast.Receipt forecast){return observeInternal(f,forecast,true);}
    private Evidence observeInternal(Frame f,McGhostMeleeStayForecast.Receipt forecast,boolean forecastPath){
        if(!valid(f))return reject("invalid native standoff frame",f==null?Long.MIN_VALUE:f.tick());
        boolean same=scope!=null&&scope.same(f.scope());
        // The forecast branch requires genuinely consecutive observations, unlike legacy gap<=2.
        boolean consecutive=same&&tick!=Long.MIN_VALUE&&f.tick()==tick+1;
        boolean actualPositionMoved=target!=null&&(target.distanceTo(f.target())>TARGET_MOTION||forecastPath&&Math.abs(target.y()-f.target().y())>1e-6);
        long priorTick=tick;Long gap=priorTick==Long.MIN_VALUE?null:f.tick()-priorTick;
        boolean heldBefore=held;int stationaryBefore=stationaryTicks;Vec3 priorTarget=target;
        boolean continuityReset=!same||tick==Long.MIN_VALUE||f.tick()-tick>2;
        if(same&&f.tick()<=tick)return reject("duplicate or stale standoff tick",f.tick());
        if(continuityReset){held=false;stationaryTicks=0;target=null;}
        if(forecastPath&&!consecutive){held=false;stationaryTicks=0;}
        observedPoseTicks=!consecutive||actualPositionMoved?1:Math.min(3,observedPoseTicks+1);
        boolean safeZeroCandidate=forecast!=null&&forecast.current(f)&&observedPoseTicks>=3&&!actualPositionMoved&&f.targetVelocity().y()==0;
        double actualSpeed=Math.hypot(Math.hypot(f.targetVelocity().x(),f.targetVelocity().z()),f.targetVelocity().y());
        boolean velocityMoving=actualSpeed>TARGET_MOTION;
        Boolean displacementMoving=null;Double actualDisplacement=null;
        // Preserve the original || short-circuit and Vec3.distanceTo arithmetic exactly.
        if(!velocityMoving){if(target!=null){actualDisplacement=target.distanceTo(f.target());displacementMoving=actualDisplacement>TARGET_MOTION;}else displacementMoving=false;}
        boolean moving=!forecastPath?(velocityMoving||Boolean.TRUE.equals(displacementMoving))
            :(actualPositionMoved||(velocityMoving&&!safeZeroCandidate));
        stationaryTicks=moving?0:Math.min(3,stationaryTicks+1);scope=f.scope();tick=f.tick();target=f.target();
        double distance=f.position().horizontalDistanceTo(f.target());
        boolean constrained=!f.tangentLeftSafe()&&!f.tangentRightSafe();
        if(forecastPath&&(forecast==null||!forecast.current(f))||f.knownHazard()||moving||!f.exactNativeHit()||!f.grounded()||!f.standingSafe()||!constrained){
            held=false;var result=evidence=new Evidence(Outcome.MOVE,"fresh hit/safety/motion/space requires movement",tick,stationaryTicks,distance,scope);
            diagnostic(f,priorTick,gap,same,continuityReset,priorTarget,heldBefore,stationaryBefore,actualSpeed,actualDisplacement,velocityMoving,displacementMoving,null,null,result,forecastPath,actualPositionMoved,safeZeroCandidate);return result;
        }
        // No fixed hold timer: any fresh lost hit, moving target, hazard, support or scope revokes.
        // A wider exit band lets ordinary native ground friction dissipate retained momentum.
        double band=held?EXIT_BAND:ENTER_BAND;
        boolean eligibleForHold=held||stationaryTicks>=3||safeZeroCandidate;
        Boolean withinBand=eligibleForHold?Math.abs(distance-f.desiredRadius())<=band:null;
        held=eligibleForHold&&Boolean.TRUE.equals(withinBand);
        var result=evidence=new Evidence(held?Outcome.HOLD:Outcome.MOVE,held?"safe constrained hit-volume hold":"outside standoff band or collecting stationary observations",tick,stationaryTicks,distance,scope);
        diagnostic(f,priorTick,gap,same,continuityReset,priorTarget,heldBefore,stationaryBefore,actualSpeed,actualDisplacement,velocityMoving,displacementMoving,band,withinBand,result,forecastPath,actualPositionMoved,safeZeroCandidate);return result;
    }
    private Evidence reject(String reason,long observedTick){observation=null;held=false;stationaryTicks=observedPoseTicks=0;return evidence=new Evidence(Outcome.REJECTED,reason,observedTick,0,Double.NaN,scope);}
    public Evidence cachedEvidence(){return evidence;}
    /** Passive consecutive actual-position observation count; does not authorize a native stay. */
    public int cachedForecastPoseTicks(){return observedPoseTicks;}
    public Observation cachedObservation(){return observation;}
    private void diagnostic(Frame f,long priorTick,Long gap,boolean same,boolean continuityReset,Vec3 priorTarget,boolean heldBefore,int stationaryBefore,double actualSpeed,Double actualDisplacement,boolean velocityMoving,Boolean displacementMoving,Double activeBand,Boolean withinBand,Evidence result,boolean forecastPath,boolean actualPositionMoved,boolean safeZeroCandidate){
        Vec3 displacement=priorTarget==null?null:new Vec3(f.target().x()-priorTarget.x(),f.target().y()-priorTarget.y(),f.target().z()-priorTarget.z());
        observation=new Observation(f,priorTick,gap,same,continuityReset,priorTarget,displacement,actualSpeed,actualDisplacement,velocityMoving,displacementMoving,heldBefore,stationaryBefore,activeBand,withinBand,result,forecastPath,actualPositionMoved,observedPoseTicks,safeZeroCandidate);
    }
    private static boolean valid(Frame f){return f!=null&&f.scope()!=null&&f.tick()>=0&&finite(f.position())&&finite(f.target())&&finite(f.targetVelocity())&&Double.isFinite(f.desiredRadius())&&f.desiredRadius()>=1&&f.desiredRadius()<=3;}
    private static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z());}
}
