package dev.veltraph.client.mining.recorded;

import dev.veltraph.client.world.*;
import java.util.Set;
import static dev.veltraph.client.mining.recorded.RecordedEchoProfile.Key.*;

/** Replays actual checkpoint-held source keys; every mutation retains a native safety witness. */
public final class RecordedEchoController {
    public enum State { READY, AIMING, PLAYING, PAUSED, COMPLETE }
    private final RecordedEchoPort port;
    private RecordedEchoProfile profile;
    private int checkpoint,removed,placed;
    private State state=State.READY;
    private String identity="",reason="Ready",pendingId="";
    private BlockPos pending,previousObservedBlock;
    private boolean leftDone,rightDone,worldChanged,admitted;
    private long started,operationStarted;
    private double jumpFloor=Double.NaN;
    public RecordedEchoController(RecordedEchoPort port){this.port=port;}
    public void start(RecordedEchoProfile next,boolean remember) {
        release();var world=port.mining().worldIdentity();
        if(!remember||!next.equals(profile)||!world.equals(identity)){checkpoint=removed=placed=0;leftDone=rightDone=false;}
        profile=next;identity=world;previousObservedBlock=null;worldChanged=false;admitted=false;pending=null;started=now();operationStarted=0;state=State.READY;reason="Recorded Echo ready";
    }
    public void stop(){release();previousObservedBlock=null;}
    private long now(){return port.mining().farm().clock().nowMillis();}
    private void release(){var m=port.mining();m.stopWalking();m.farm().breaking().setBreaking(false);m.farm().input().releaseAllMovement();m.farm().rotation().cancelRotation();port.releasePlacement();pending=null;jumpFloor=Double.NaN;}
    private void pause(String why){var m=port.mining();m.stopWalking();m.farm().breaking().setBreaking(false);m.farm().input().releaseAllMovement();m.farm().rotation().cancelRotation();state=State.PAUSED;reason=why;}
    public void resetAtRewarp(){release();previousObservedBlock=null;checkpoint=removed=placed=0;leftDone=rightDone=false;admitted=false;state=State.READY;started=now();}
    public void tick(boolean skip,int reserve,long timeout,boolean autoBreak) {
        if(profile==null||state==State.COMPLETE)return;
        var m=port.mining();var f=m.farm();
        if(!identity.equals(m.worldIdentity()))worldChanged=true;
        if(worldChanged){previousObservedBlock=null;pause("Recorded world changed; inspect and restart");return;}
        if(!m.available()){previousObservedBlock=null;pause("Recorded Echo requires a loaded player and closed screen");return;}
        if(!admitted){if(!profile.checkpoints().get(checkpoint).position().equals(f.player().blockPos())){pause("Stand at the recording's initial checkpoint");return;}admitted=true;}
        // Source arrival replaces the previous complete key list. Bound outcomes must also be acknowledged.
        var observed=f.player().blockPos();
        boolean stable=observed.equals(previousObservedBlock);
        previousObservedBlock=observed;
        int arrival=checkpoint;
        // Readable source scans the remaining prefix in order and stops at its FIRST match.
        // Organic FULL timing is unbound; retain the existing local input/outcome safety gates.
        if(stable)for(int i=checkpoint+1;i<profile.checkpoints().size()&&(skip||i==checkpoint+1);i++)
            if(profile.checkpoints().get(i).position().equals(observed)){arrival=i;break;}
        var point=profile.checkpoints().get(checkpoint);
        if(arrival>checkpoint&&loaded(point.position())&&loaded(profile.checkpoints().get(arrival).position())&&(!point.keys().contains(LEFT_CLICK)||leftDone)&&(!point.keys().contains(RIGHT_CLICK)||rightDone)) {
            release();checkpoint=arrival;leftDone=rightDone=false;operationStarted=0;started=now();point=profile.checkpoints().get(checkpoint);
        }
        if(checkpoint==profile.checkpoints().size()-1){release();state=State.COMPLETE;reason="Recorded Echo final arrival";return;}
        if(now()-started>Math.max(1000,timeout)*5){pause("Recorded checkpoint timed out; inspect and restart");return;}
        if(!loaded(point.position())||!loaded(profile.checkpoints().get(checkpoint+1).position())){pause("Recorded checkpoint chunk unloaded");return;}
        var keys=point.keys();
        if(!motionSafe(keys)){pause("Recorded movement/jump lacks loaded body clearance and supported landing");return;}
        f.input().releaseAllMovement();f.breaking().setBreaking(false);
        f.rotation().track(profile.yaw(),profile.pitch());
        if(Math.abs(wrap(f.player().yaw()-profile.yaw()))>1.2||Math.abs(f.player().pitch()-profile.pitch())>1.2){state=State.AIMING;reason="Aiming at recorded design orientation";return;}
        if(keys.contains(LEFT_CLICK)&&!leftDone) {
            if(!autoBreak){pause("Auto break disabled");return;}
            var target=point.leftTarget();
            if(pending!=null&&loaded(pending)&&!pendingId.equals(f.world().blockId(pending))){removed++;leftDone=true;pending=null;operationStarted=0;}
            else {
                var issue=m.targetProblem(target,target.equals(f.player().blockPos().down()));
                if(!issue.isEmpty()){pause(issue);return;}
                issue=m.selectTool(target,Math.max(1,reserve));if(!issue.isEmpty()){pause(issue);return;}
                if(!m.hasCapacity(target)){pause("Recorded break has no drop capacity");return;}
                if(!m.aimedAt(target)){state=State.AIMING;reason="Recorded ray has not reached its bound break target";return;}
                if(pending==null){pending=target;pendingId=f.world().blockId(target);operationStarted=now();}
                if(now()-operationStarted>=Math.max(250,timeout)){pause("Recorded break outcome timed out");return;}
                f.breaking().setBreaking(true);
            }
        }
        if(keys.contains(RIGHT_CLICK)&&!rightDone) {
            if(port.placementComplete(point.placement())){placed++;rightDone=true;port.releasePlacement();operationStarted=0;}
            else {
                var issue=port.placementProblem(point.placement());if(!issue.isEmpty()){pause(issue);return;}
                if(operationStarted==0)operationStarted=now();
                if(now()-operationStarted>=Math.max(250,timeout)){pause("Recorded placement outcome timed out");return;}
                port.place(point.placement());
            }
        }
        boolean movingReady=(!keys.contains(LEFT_CLICK)||leftDone)&&(!keys.contains(RIGHT_CLICK)||rightDone);
        var input=f.input();input.setForwardHeld(movingReady&&keys.contains(FORWARD));input.setBackHeld(movingReady&&keys.contains(BACK));
        input.setLeftHeld(movingReady&&keys.contains(LEFT));input.setRightHeld(movingReady&&keys.contains(RIGHT));input.setSneakHeld(keys.contains(SNEAK));
        input.setSprintHeld(movingReady&&keys.contains(SPRINT));input.setJumpHeld(movingReady&&keys.contains(JUMP));
        state=State.PLAYING;reason="Recorded checkpoint "+checkpoint+" keys "+keys;
    }
    private boolean loaded(BlockPos pos){return port.mining().farm().world().isLoaded(pos);}
    /** Swept ordinary-input footprint; unknown chunks, unsupported jumps and hazardous cells fail closed. */
    public boolean motionSafe(Set<RecordedEchoProfile.Key> keys) {
        var f=port.mining().farm();var p=f.player().position();var w=f.world();
        if(!Float.isFinite(f.player().yaw()))return false;
        boolean jump=keys.contains(JUMP);
        if(f.player().onGround()){jumpFloor=p.y();}
        else if(!jump||!Double.isFinite(jumpFloor)||p.y()<jumpFloor-.05||p.y()>jumpFloor+1.5)return false;
        double base=jump?jumpFloor:p.y();
        double forward=(keys.contains(FORWARD)?1:0)-(keys.contains(BACK)?1:0);
        double side=(keys.contains(RIGHT)?1:0)-(keys.contains(LEFT)?1:0);
        if(keys.contains(LEFT_CLICK)&&!leftDone||keys.contains(RIGHT_CLICK)&&!rightDone){forward=side=0;}
        double yaw=Math.toRadians(f.player().yaw()),dx=-Math.sin(yaw)*forward-Math.cos(yaw)*side,dz=Math.cos(yaw)*forward-Math.sin(yaw)*side;
        double length=Math.hypot(dx,dz);if(length>0){dx/=length;dz/=length;}
        // A jump has a longer ballistic horizon. Every projected landing and overhead body must be safe.
        double horizon=length==0?0:jump?4.8:.8;
        for(double distance=0;distance<=horizon+.001;distance+=.1) {
            var foot=new Vec3(p.x()+dx*distance,base,p.z()+dz*distance);
            if(!w.hasGroundSupport(foot,.31)||!w.bodyClear(foot,.31,1.85))return false;
            // Native bodyClear uses the actor's actual pose; probe the translated apex separately.
            if(jump&&!w.bodyClear(new Vec3(foot.x(),base+1.5,foot.z()),.31,1.85))return false;
            for(int x=(int)Math.floor(foot.x()-.31);x<=(int)Math.floor(foot.x()+.31);x++)for(int z=(int)Math.floor(foot.z()-.31);z<=(int)Math.floor(foot.z()+.31);z++)
                for(int y=(int)Math.floor(base-1);y<=(int)Math.floor(base+(jump?3.3:1.85));y++) {
                    var cell=new BlockPos(x,y,z);if(!w.isLoaded(cell)||hazard(w.blockId(cell)))return false;
                }
        }
        return true;
    }
    private static boolean hazard(String id){return Set.of("minecraft:water","minecraft:lava","minecraft:fire","minecraft:soul_fire","minecraft:magma_block","minecraft:cactus","minecraft:campfire","minecraft:soul_campfire","minecraft:powder_snow","minecraft:sweet_berry_bush").contains(id);}
    private static float wrap(float value){float r=value%360;return r>180?r-360:r<-180?r+360:r;}
    public boolean allowsTarget(BlockPos actual,int reserve) {
        if(profile==null||state!=State.PLAYING||pending==null||!pending.equals(actual)||worldChanged||!identity.equals(port.mining().worldIdentity())||!port.mining().available()||!loaded(actual))return false;
        var m=port.mining();return pendingId.equals(m.farm().world().blockId(actual))&&m.aimedAt(actual)&&motionSafe(profile.checkpoints().get(checkpoint).keys())
                &&m.targetProblem(actual,actual.equals(m.farm().player().blockPos().down())).isEmpty()&&m.hasCapacity(actual)&&m.heldToolReady(actual,Math.max(1,reserve));
    }
    public State state(){return state;}public String reason(){return reason;}public int checkpoint(){return checkpoint;}public int removed(){return removed;}public int placed(){return placed;}
    public RecordedEchoProfile profile(){return profile;}
    public BlockPos startPosition(RecordedEchoProfile next,boolean remember){int index=remember&&next.equals(profile)&&identity.equals(port.mining().worldIdentity())?checkpoint:0;return next.checkpoints().get(index).position();}
}
