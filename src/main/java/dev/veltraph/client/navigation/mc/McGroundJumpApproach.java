package dev.veltraph.client.navigation.mc;
import dev.veltraph.client.navigation.*;
import dev.veltraph.client.world.Vec3;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
/** Per-navigator ground approach. Every publication needs current native geometry; timer never restarts. */
public final class McGroundJumpApproach {
    public record Result(boolean handled,boolean blocked,GroundJumpApproach.Key key,String reason,GroundJumpApproach.Plan plan){}
    private Result result(boolean handled,boolean blocked,GroundJumpApproach.Key key,String reason){commitment.refuse(episodeToken);return new Result(handled,blocked,key,reason,null);}
    private long began=-1,lastNow=-1;private dev.veltraph.client.world.BlockPos sourceCell;private Vec3 target,previousNext,previousVelocity;private int previousTick=-1;private boolean active;private GroundJumpApproach.Key previousKey;private float previousYaw=Float.NaN,pendingYaw=Float.NaN;private Witness failureWitness;private GuardWitness guardWitness;private GroundJumpApproach.Plan committedPlan;private final GroundApproachCommitment commitment=new GroundApproachCommitment();private Object episodeToken;private StepFailure stepWitness;public record StepFailure(GroundJumpApproach.Plan plan,GroundApproachStepWitness.Snapshot nativeProof,boolean accepted){}
    public record Witness(int priorTick,int actualTick,GroundJumpApproach.Key key,float yaw,Vec3 predictedPose,Vec3 actualPose,double poseError,Vec3 predictedVelocity,Vec3 actualVelocity,double velocityError,boolean tickPassed,boolean posePassed,dev.veltraph.client.world.BlockPos cell,Vec3 target,long now,long began,long lastNow){}
    public record GuardWitness(int tested,int passed,Vec3 actual,Vec3 velocity,double speed,Vec3 target,dev.veltraph.client.world.BlockPos cell,long now,long began,long lastNow,GroundJumpApproach.Plan previousPlan){}
    private static final class GuardSample {int tested,passed;double speed=Double.NaN;boolean observe(int bit,boolean value){tested|=1<<bit;if(value)passed|=1<<bit;return value;}double speed(double value){speed=value;return value;}}
    public Result tick(Minecraft c,McNavigator terrain,WalkController.Output request,GroundLocomotionPolicy.Proof proof,GroundLocomotionPolicy.Phase phase,Vec3 currentWaypoint,BooleanSupplier current){
        stepWitness=null;
        commitment.refuse(episodeToken);
        if(!GroundJumpApproach.newEdgePhase(phase))return result(false,false,GroundJumpApproach.Key.NEUTRAL,"Owned jump/drop must finish before new stance approach");
        var p=c.player;if(p==null)return result(true,true,GroundJumpApproach.Key.NEUTRAL,"Approach player unavailable");
        Vec3 actual=new Vec3(p.getX(),p.getY(),p.getZ());var v=p.getDeltaMovement();Vec3 velocity=new Vec3(v.x,v.y,v.z);
        boolean fresh=proof.evidence()!=null&&proof.bodyClear()&&proof.landingClear()
                &&proof.evidence().allowed(GroundLocomotionEvidence.Stage.FINAL_BUDGET)&&proof.evidence().allowed(GroundLocomotionEvidence.Stage.FINAL_CONTEXT)
                &&proof.target().equals(currentWaypoint)&&GroundLocomotionMotion.jumpEndpoints(actual,proof.target())&&p.onGround();
        if(!fresh){if(active)return result(true,true,GroundJumpApproach.Key.NEUTRAL,"Approach fresh rising edge revoked");return result(false,false,GroundJumpApproach.Key.NEUTRAL,"Not a fresh grounded rising edge");}
        boolean envelopeOnly=proof.evidence()!=null&&proof.evidence().evaluated(GroundLocomotionEvidence.Stage.JUMP_ENVELOPE)
                &&!proof.evidence().allowed(GroundLocomotionEvidence.Stage.JUMP_ENVELOPE)
                &&proof.evidence().allowed(GroundLocomotionEvidence.Stage.STEP_SWEEP)
                &&proof.evidence().allowed(GroundLocomotionEvidence.Stage.TAKEOFF_SUPPORT)
                &&proof.evidence().allowed(GroundLocomotionEvidence.Stage.NATIVE_TRAITS);
        if(!active&&!request.jump())return result(false,false,GroundJumpApproach.Key.NEUTRAL,"No approach needed");
        if(!active&&proof.jumpClear()&&GroundJumpApproach.lateralSettled(velocity,actual,proof.target()))return result(false,false,GroundJumpApproach.Key.NEUTRAL,"Fresh jump stance ready");
        if(!active&&!envelopeOnly&&!proof.jumpClear())return result(false,false,GroundJumpApproach.Key.NEUTRAL,"Not an approach-only refusal");
        long now=System.currentTimeMillis();
        if(!active){failureWitness=null;guardWitness=null;stepWitness=null;active=true;episodeToken=new Object();commitment.begin(episodeToken);began=lastNow=now;target=proof.target();sourceCell=GroundPathfinder.node(actual);}
        var guard=new GuardSample();
        if(!guard.observe(0,current.getAsBoolean())||!guard.observe(1,target.equals(proof.target()))||!guard.observe(2,p.onGround())||!guard.observe(3,!p.isSprinting())
                ||!guard.observe(4,GroundJumpApproach.currentClock(began,lastNow,now))||!guard.observe(5,GroundJumpApproach.sameSourceCell(sourceCell,actual))
                ||!guard.observe(6,GroundLocomotionMotion.takeoff(velocity,guard.speed(p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)),true,false))){
            try{guardWitness=new GuardWitness(guard.tested,guard.passed,actual,velocity,guard.speed,target,sourceCell,now,began,lastNow,committedPlan);}catch(Throwable diagnosticUnavailable){guardWitness=null;}
            return result(true,true,GroundJumpApproach.Key.NEUTRAL,"Ground approach scope/physics/deadline revoked");
        }
        lastNow=now;
        if(previousNext!=null){
            int tick=p.tickCount;boolean tickOk=tick==previousTick+1;
            double poseError=tickOk?actual.distanceTo(previousNext):Double.NaN;
            // Preserve short-circuit behavior: evaluate the existing velocity distance only
            // if tick and pose guards permit reaching that original operand.
            boolean poseOk=tickOk&&!(poseError>.025);
            double velocityError=poseOk?velocity.distanceTo(previousVelocity):Double.NaN;
            if(!tickOk||!poseOk||velocityError>.025){
                try{failureWitness=new Witness(previousTick,tick,previousKey,previousYaw,previousNext,actual,poseError,previousVelocity,velocity,velocityError,tickOk,poseOk,sourceCell,target,now,began,lastNow);}
                catch(Throwable diagnosticUnavailable){failureWitness=null;}
                return result(true,true,GroundJumpApproach.Key.NEUTRAL,"Ground approach native movement left certified interval");
            }
        }
        if(proof.jumpClear()&&GroundJumpApproach.lateralSettled(velocity,actual,target)){reset();return result(false,false,GroundJumpApproach.Key.NEUTRAL,"Recentered stance ready");}
        var fence=new DynamicRouteQueryFence(System::nanoTime,current,50_000_000L);
        var remainingSequence=commitment.remaining(episodeToken);
        var tail=remainingSequence.isEmpty()?null:GroundJumpApproach.remaining(actual,velocity,request.yaw(),remainingSequence);
        stepWitness=null;
        final Object selectingEpisode=episodeToken;
        var nativeProof=new GroundApproachSafeReplan.Proof(){
            private boolean definite;
            public boolean accepts(GroundJumpApproach.Plan plan){
                var nativeWitness=new GroundApproachStepWitness();var coast=GroundJumpApproach.immediateCoast(plan);
                boolean accepted=fence.query(()->terrain.jumpApproachGroundClear(actual,plan.next(),coast,target,sourceCell,nativeWitness));
                var snapshot=nativeWitness.snapshot(actual,plan.next(),coast,sourceCell);
                definite=!accepted&&!snapshot.passed()&&!snapshot.operand().equals("NOT_EVALUATED");
                if(!accepted&&active&&episodeToken==selectingEpisode){try{stepWitness=new StepFailure(plan,snapshot,false);}catch(Throwable diagnosticUnavailable){stepWitness=null;}}
                return accepted;
            }
            public boolean definiteUnsafe(){return definite;}
        };
        java.util.function.BooleanSupplier selectingCurrent=()->GroundApproachSafeReplan.sameEpisode(selectingEpisode,()->episodeToken,()->active,
                ()->fence.available()&&current.getAsBoolean()&&fence.available());
        var selected=GroundApproachSafeReplan.choose(tail,()->GroundJumpApproach.candidates(actual,velocity,request.yaw(),target),nativeProof,
                selectingCurrent,()->selectingCurrent.getAsBoolean()&&commitment.abandonTail(selectingEpisode)&&selectingCurrent.getAsBoolean()).orElse(null);
        if(selected!=null&&selectingCurrent.getAsBoolean()){
            stepWitness=null;pendingYaw=request.yaw();var offered=new Result(true,false,selected.key(),"Bounded side/back stance approach",selected);
            commitment.offer(selectingEpisode,offered,selected.sequence());return offered;
        }
        commitment.refuse(selectingEpisode);
        return new Result(true,true,GroundJumpApproach.Key.NEUTRAL,"No freshly proved approach step",null);
    }
    /** Native caller commits only after successful own camera/key publication and fresh scope. */
    public void committed(Result result,int tick){if(active&&episodeToken!=null&&result!=null&&result.handled()&&!result.blocked()&&result.plan()!=null&&commitment.commit(episodeToken,result)){previousNext=result.plan().next();previousVelocity=result.plan().nextVelocity();previousTick=tick;previousKey=result.key();previousYaw=pendingYaw;committedPlan=result.plan();}}
    public StepFailure stepWitness(){return stepWitness;}
    public GuardWitness guardWitness(){return guardWitness;}
    public Witness failureWitness(){return failureWitness;}
    public void reset(){stepWitness=null;failureWitness=null;guardWitness=null;committedPlan=null;commitment.reset();episodeToken=null;active=false;began=lastNow=-1;sourceCell=null;target=null;previousNext=null;previousVelocity=null;previousTick=-1;previousKey=null;previousYaw=pendingYaw=Float.NaN;}
}
