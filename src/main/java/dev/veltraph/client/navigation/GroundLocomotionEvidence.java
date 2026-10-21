package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
/** Cached operands from already-required reads. Diagnostic fields never grant or revoke permission. */
public final class GroundLocomotionEvidence {
    public enum Stage { BODY, LANDING, MOTION, NATIVE_TRAITS, ENDPOINTS, TAKEOFF_SUPPORT, STEP_SWEEP, JUMP_ENVELOPE, FINAL_BUDGET, FINAL_CONTEXT, SPRINT_BODY_LANDING, SPRINT_DISTANCE, SPRINT_LEVEL, SPRINT_AVAILABLE, SPRINT_GROUND, SPRINT_HEADING, SPRINT_FROM_SUPPORT, SPRINT_WALK_SWEEP, SPRINT_HEADROOM, LANDING_FORECAST, LANDING_BRAKE_SWEEP, OBSERVED_CONTACT, CONTACT_COAST, DROP_MOTION, DROP_ORIGIN_SUPPORT, DROP_GRAPH_EDGE, DROP_STEP_SWEEP, DROP_BRAKE_FORECAST, DROP_BRAKE_BODY }
    private int tested,passed;private Vec3 velocity;private double speed=Double.NaN;private Vec3 dropVelocity,dropOrigin;private double dropSpeed=Double.NaN;private Vec3 envelopePoint;private double envelopeProgress=Double.NaN,envelopeLateral=Double.NaN;private String envelopeKind="NOT_EVALUATED";private boolean envelopeQueryPassed;private Vec3 sprintVelocity,sprintTarget,sweepFrom,sweepTo;private double sprintSpeed=Double.NaN,distance=Double.NaN,dy=Double.NaN,heading=Double.NaN;
    public boolean observe(Stage stage,boolean value){int bit=1<<stage.ordinal();tested|=bit;if(value)passed|=bit;else passed&=~bit;return value;}
    public Vec3 dropVelocity(Vec3 value){dropVelocity=value;return value;}
    public double dropSpeed(double value){dropSpeed=value;return value;}
    public void dropOrigin(Vec3 value){dropOrigin=value;}
    public boolean envelope(String kind,Vec3 point,double progress,double lateral,boolean passed){envelopeKind=kind;envelopePoint=point;envelopeProgress=progress;envelopeLateral=lateral;envelopeQueryPassed=passed;return passed;}
    public Vec3 velocityValue(){return velocity;}
    public double speedValue(){return speed;}
    public Vec3 velocity(Vec3 value){velocity=value;return value;}
    public double speed(double value){speed=value;return value;}
    public Vec3 sprintVelocity(Vec3 v){sprintVelocity=v;return v;}
    public double sprintSpeed(double v){sprintSpeed=v;return v;}
    public void lead(Vec3 target,double length,double deltaY){sprintTarget=target;distance=length;dy=deltaY;}
    public double heading(double value){heading=value;return value;}
    public void sweep(Vec3 from,Vec3 to){sweepFrom=from;sweepTo=to;}
    public Snapshot snapshot(String boundary,GroundLocomotionPolicy.Phase phase,Vec3 actual,Vec3 takeoff,Vec3 landing){return new Snapshot(boundary,phase,actual,takeoff,landing,velocity,speed,tested,passed,sprintVelocity,sprintSpeed,sprintTarget,distance,dy,heading,sweepFrom,sweepTo,dropOrigin,dropVelocity,dropSpeed,envelopeKind,envelopePoint,envelopeProgress,envelopeLateral,envelopeQueryPassed);}
    public record Snapshot(String boundary,GroundLocomotionPolicy.Phase phase,Vec3 actual,Vec3 takeoff,Vec3 landing,Vec3 velocity,double speed,int tested,int passed,Vec3 sprintVelocity,double sprintSpeed,Vec3 sprintTarget,double distance,double dy,double heading,Vec3 sweepFrom,Vec3 sweepTo,Vec3 dropOrigin,Vec3 dropVelocity,double dropSpeed,String envelopeKind,Vec3 envelopePoint,double envelopeProgress,double envelopeLateral,boolean envelopeQueryPassed){
        public boolean evaluated(Stage stage){return (tested&(1<<stage.ordinal()))!=0;}
        public boolean allowed(Stage stage){return (passed&(1<<stage.ordinal()))!=0;}
        public String describe(){var out=new StringBuilder("groundOperands boundary=").append(boundary).append(" phase=").append(phase).append(" actual=").append(actual).append(" takeoff=").append(takeoff).append(" landing=").append(landing).append(" velocity=").append(velocity).append(" speed=").append(speed).append(" sprintVelocity=").append(sprintVelocity).append(" sprintSpeed=").append(sprintSpeed).append(" sprintTarget=").append(sprintTarget).append(" leadDistance=").append(distance).append(" leadDy=").append(dy).append(" headingError=").append(heading).append(" lastSweepFrom=").append(sweepFrom).append(" lastSweepTo=").append(sweepTo).append(" dropOrigin=").append(dropOrigin).append(" dropVelocity=").append(dropVelocity).append(" dropSpeed=").append(dropSpeed).append(" envelopeKind=").append(envelopeKind).append(" envelopePoint=").append(envelopePoint).append(" envelopeProgress=").append(envelopeProgress).append(" envelopeLateral=").append(envelopeLateral).append(" envelopeQueryPassed=").append(envelopeQueryPassed);
            for(var stage:Stage.values()){int bit=1<<stage.ordinal();out.append(' ').append(stage).append('=').append((tested&bit)==0?"NOT_EVALUATED":(passed&bit)!=0?"TRUE":"FALSE");}return out.toString();}
    }
    /** Bounded cached recorder. Called after the original proof and policy decision; no world reads. */
    public static final class Trace {
        private Snapshot first,last,maxLead,firstSprint;private final Snapshot[] distinct=new Snapshot[16];private int distinctCount,frames,proofSprint,policySprint,frameAvailability;
        public void record(Snapshot value,boolean provedSprint,boolean available,boolean sprint){if(value==null)return;frames++;if(first==null)first=value;last=value;
            if(maxLead==null||value.distance()>maxLead.distance())maxLead=value;
            if(provedSprint){proofSprint++;if(firstSprint==null)firstSprint=value;}
            if(available)frameAvailability++;if(sprint)policySprint++;
            boolean known=false;for(int i=0;i<distinctCount;i++)if(distinct[i].tested()==value.tested()&&distinct[i].passed()==value.passed())known=true;
            if(!known&&distinctCount<distinct.length)distinct[distinctCount++]=value;
        }
        public int stored(){return distinctCount;}public int frames(){return frames;}
        public void reset(){first=null;last=null;maxLead=null;firstSprint=null;java.util.Arrays.fill(distinct,null);distinctCount=0;frames=0;proofSprint=0;policySprint=0;frameAvailability=0;}
        public String describe(){var out=new StringBuilder("cachedGroundTrace frames=").append(frames).append(" frameAvailability=").append(frameAvailability).append(" provedSprintSamples=").append(proofSprint).append(" policySprint=").append(policySprint).append(" distinct=").append(distinctCount);
            out.append(" first={").append(first==null?"none":first.describe()).append("} last={").append(last==null?"none":last.describe()).append("} maxLead={").append(maxLead==null?"none":maxLead.describe()).append("} firstSprint={").append(firstSprint==null?"none":firstSprint.describe()).append('}');
            for(int i=0;i<distinctCount;i++)out.append(" sample[").append(i).append("]={").append(distinct[i].describe()).append('}');return out.toString();}
    }
}
