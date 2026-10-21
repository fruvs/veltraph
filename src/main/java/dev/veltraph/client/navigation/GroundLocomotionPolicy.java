package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;

/** Per-journey native-key policy. A jump is useful only for an admitted rising edge,
 * never as a random gesture. Actual departure and landing, not timers, advance it. */
public final class GroundLocomotionPolicy {
    public enum Phase { READY, TAKEOFF, AIRBORNE, LANDING_BRAKE, DROPPING, LANDED, BLOCKED }
    public record Frame(Vec3 position, boolean onGround, boolean sprinting, long now,
                        boolean current, boolean sprintAvailable,int nativeTick) {
        public Frame(Vec3 position,boolean onGround,boolean sprinting,long now,boolean current,boolean sprintAvailable){this(position,onGround,sprinting,now,current,sprintAvailable,-1);}
    }
    public record Proof(Vec3 target, boolean bodyClear, boolean landingClear,
                        boolean jumpClear, boolean sprintClear, Vec3 dropOrigin,GroundLocomotionEvidence.Snapshot evidence,boolean landingBrake,GroundDropBrakeForecast.Episode dropEpisode) {
        public Proof(Vec3 target,boolean bodyClear,boolean landingClear,boolean jumpClear,boolean sprintClear,Vec3 dropOrigin,GroundLocomotionEvidence.Snapshot evidence,boolean landingBrake){this(target,bodyClear,landingClear,jumpClear,sprintClear,dropOrigin,evidence,landingBrake,null);}
        public Proof(Vec3 target,boolean bodyClear,boolean landingClear,boolean jumpClear,boolean sprintClear,Vec3 dropOrigin,GroundLocomotionEvidence.Snapshot evidence){this(target,bodyClear,landingClear,jumpClear,sprintClear,dropOrigin,evidence,false);}
        public Proof(Vec3 target,boolean bodyClear,boolean landingClear,boolean jumpClear,boolean sprintClear){this(target,bodyClear,landingClear,jumpClear,sprintClear,null,null);}
        public Proof(Vec3 target,boolean bodyClear,boolean landingClear,boolean jumpClear,boolean sprintClear,Vec3 dropOrigin){this(target,bodyClear,landingClear,jumpClear,sprintClear,dropOrigin,null);}
    }
    public record Decision(boolean forward, boolean jump, boolean sprint, boolean blocked, String reason) {
        public static Decision hold(String reason){ return new Decision(false,false,false,true,reason); }
    }
    private Phase phase=Phase.READY;
    private Vec3 takeoff,landing,lastJumpTarget;
    private boolean dropAirObserved;private int jumpIssuedTick=-1;
    private long issuedAt,lastNow=Long.MIN_VALUE;
    public Decision tick(Frame in, WalkController.Output requested, Proof proof) {
        if(in==null||requested==null||proof==null||!finite(in.position())||!finite(proof.target())
                ||!in.current()||in.now()<0||in.now()<lastNow||!proof.bodyClear())
            return block("Locomotion context, clock or body proof revoked");
        lastNow=in.now();
        if(phase==Phase.BLOCKED)return Decision.hold("Locomotion failure requires a fresh journey");
        if(requested.state()!=WalkController.State.WALKING){reset();return new Decision(false,false,false,false,"Ground route terminal");}
        if(phase==Phase.DROPPING){
            if(proof.dropOrigin()==null||!proof.dropOrigin().equals(takeoff)||!proof.target().equals(landing)||!proof.landingClear())return block("Original drop edge or landing proof revoked");
            if(in.now()-issuedAt>1800)return block("Observed original drop landing deadline exceeded");
            if(!in.onGround())dropAirObserved=true;
            else if(dropAirObserved){
                if(in.position().distanceTo(landing)>.65)return block("Original drop landed outside its admitted landing");
                phase=Phase.LANDED;return new Decision(false,false,false,false,"Actual original drop landing; neutral rearm boundary");
            }else if(in.now()-issuedAt>700)return block("Original drop departure was not observed");
            if(proof.dropEpisode()!=null)return new Decision(false,false,false,false,"Neutral certified-drop brake awaiting actual landing");
            return new Decision(requested.forward(),false,false,false,"Original certified one-block drop");
        }
        if(phase==Phase.TAKEOFF||phase==Phase.AIRBORNE||phase==Phase.LANDING_BRAKE) {
            if(!landing.equals(proof.target())||!proof.landingClear()||!proof.jumpClear())return block("Jump landing or swept envelope revoked");
            if(in.now()-issuedAt>1800)return block("Observed jump landing deadline exceeded");
            if(phase==Phase.TAKEOFF) {
                if(!in.onGround()){phase=Phase.AIRBORNE;}
                else if(in.now()-issuedAt>350)return block("Native jump departure was not observed");
                // The first decision is the sole press. Release before subsequent native samples.
                return new Decision(requested.forward(),false,false,false,"Waiting for actual native departure");
            }
            if(in.onGround()) {
                if(in.position().distanceTo(landing)>.65)return block("Jump landed outside its admitted landing");
                phase=Phase.LANDED;
                return new Decision(false,false,false,false,"Actual supported landing; release before rearming");
            }
            if(proof.landingBrake()||phase==Phase.LANDING_BRAKE){phase=Phase.LANDING_BRAKE;return new Decision(false,false,false,false,"Neutral brake awaiting actual native supported landing");}
            return new Decision(requested.forward(),false,false,false,"Actual airborne traversal");
        }
        if(proof.dropOrigin()!=null&&requested.forward()){
            if(!in.onGround())return block("Original drop lacked an observed grounded admission");
            if(in.sprinting())return new Decision(false,false,false,false,"Neutral brake before original one-block drop");
            takeoff=proof.dropOrigin();landing=proof.target();issuedAt=in.now();jumpIssuedTick=-1;dropAirObserved=false;phase=Phase.DROPPING;
            return new Decision(true,false,false,false,"Grounded original drop receipt admitted");
        }
        if(!in.onGround())return block("Airborne movement has no owned jump or original drop receipt");
        if(phase==Phase.LANDED){phase=Phase.READY;takeoff=null;landing=null;dropAirObserved=false;issuedAt=0;jumpIssuedTick=-1;}
        if(!proof.landingClear())return block("Ground landing unavailable");
        if(requested.jump()) {
            if(in.sprinting())return new Decision(false,false,false,false,"Neutral brake before bounded step jump");
            double rise=proof.target().y()-in.position().y();
            if(!requested.forward()||!in.onGround()||rise<=.6||rise>1.001
                    ||in.position().horizontalDistanceTo(proof.target())>1.65||!proof.jumpClear())
                return block("Rising edge lacks fresh bounded native jump proof");
            if(lastJumpTarget!=null&&lastJumpTarget.distanceTo(proof.target())<.001)
                return block("Same jump edge cannot be retried by holding the key");
            takeoff=in.position();landing=proof.target();lastJumpTarget=landing;issuedAt=in.now();jumpIssuedTick=in.nativeTick();phase=Phase.TAKEOFF;
            return new Decision(true,true,false,false,"One native jump press for admitted rising edge");
        }
        // Let vanilla stop sprinting through ordinary neutral input. Key release alone does
        // not clear LocalPlayer's sprint flag, and a forced setSprinting call would bypass physics.
        boolean sprint=requested.forward()&&in.onGround()&&in.sprintAvailable()&&proof.sprintClear();
        if(in.sprinting()&&!sprint)return new Decision(false,false,false,false,"Neutral brake for native sprint release");
        return new Decision(requested.forward(),false,sprint,false,sprint?"Supported wide straight sprint":"Ordinary supported walk");
    }
    private Decision block(String reason){phase=Phase.BLOCKED;return Decision.hold(reason);}
    private static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z());}
    public Phase phase(){return phase;}
    /** An earlier landing is never a later jump's native takeoff geometry. */
    public Vec3 jumpOrigin(Vec3 actual){return phase==Phase.TAKEOFF||phase==Phase.AIRBORNE||phase==Phase.LANDING_BRAKE?takeoff:actual;}
    public int jumpIssuedTick(){return jumpIssuedTick;}
    public Vec3 takeoff(){return takeoff;}
    public Vec3 landing(){return landing;}
    public void reset(){phase=Phase.READY;takeoff=null;landing=null;lastJumpTarget=null;dropAirObserved=false;jumpIssuedTick=-1;issuedAt=0;lastNow=Long.MIN_VALUE;}
}
