package dev.veltraph.client.foraging.maintenance;

import java.util.*;
import java.util.function.BooleanSupplier;

/** Pure transactional orchestration. Its native port must supply actual immutable observation proof. */
public final class ForagingMaintenanceCoordinator {
    public enum ReturnStatus { ACTIVE, READY, FAILED }
    public record Capture(Object parent,Object helper,Object engine,Object player,Object world,Object connection,
                          Object profile,Object protection,Object pose,Object invitation) {
        public Capture {for(Object value:new Object[]{parent,helper,engine,player,world,connection,profile,protection,pose,invitation})Objects.requireNonNull(value);}
    }
    public interface Port {
        /** Read only: explicit registered pair, opt-in, quiescent parent, healthy closed world and fresh unique invitation. */
        Optional<String> readiness();
        Object invitation();
        Capture capture();
        boolean helperEnabled();
        boolean parentEnabled();
        /** Must recheck exact refs/engine/profile and ALL captured native tool/gear identities. */
        boolean valid(Capture capture);
        /** True only if activation changed no keys, native pose, menu, slot, gear or invitation. */
        boolean activationUnchanged(Capture capture);
        boolean pause(ForagingMaintenanceLease.Token token);
        boolean ownsPause(ForagingMaintenanceLease.Token token);
        /** Actual COMPLETE + fresh reward counter beyond captured baseline; never enabled=false alone. */
        boolean completed(Capture capture);
        boolean cleanupFinished(Capture capture);
        ReturnStatus returnToPose(Capture capture);
        /** Recheck current return + protection immediately before exact rebind/resume. */
        boolean resume(Capture capture,ForagingMaintenanceLease.Token token);
        void rollback(Capture capture);
        /** Stop both captured controllers before cancellation releases to another owner. */
        void stopBoth(Capture capture);
    }
    private final Port port;
    private final ForagingMaintenanceLease lease=new ForagingMaintenanceLease();
    private final Set<Object> consumed=Collections.newSetFromMap(new IdentityHashMap<>());
    private Capture capture;
    private ForagingMaintenanceLease.Token token;
    private boolean activating,stopping;
    private String reason="Idle";
    public ForagingMaintenanceCoordinator(Port port){this.port=Objects.requireNonNull(port);}
    public Optional<String> readiness(){
        if(active())return Optional.of("Another foraging maintenance lease is active");
        var denied=port.readiness();if(denied.isPresent())return denied;
        Object invitation=port.invitation();
        if(invitation==null||consumed.contains(invitation))return Optional.of("Invitation missing or already consumed");
        if(consumed.size()>=256)return Optional.of("Invitation history bound reached; explicit new session required");
        return Optional.empty();
    }
    public boolean startTransaction(BooleanSupplier standardActivation,long now,long timeout){
        if(readiness().isPresent())return false;
        capture=port.capture();if(capture==null||capture.invitation()!=port.invitation()||consumed.contains(capture.invitation())){capture=null;return false;}
        token=lease.prepare(capture.parent(),capture.helper(),now,timeout);if(token==null){capture=null;return false;}
        boolean enabled=false;
        try{
            activating=true;try{enabled=standardActivation.getAsBoolean();}finally{activating=false;}
            if(!enabled||!port.helperEnabled()||!port.valid(capture)||!port.activationUnchanged(capture)){
                rollback();return false;
            }
            // Once admitted the original source Component cannot launch a second claim, even if
            // pause/commit fails or a later native outcome is uncertain.
            consumed.add(capture.invitation());
            if(!port.pause(token)||!port.ownsPause(token)||!lease.commit(token)){cancelActive("Pause/commit failed");return false;}
            reason="Claiming captured invitation";return true;
        }catch(RuntimeException error){if(lease.state()==ForagingMaintenanceLease.State.PREPARED&&!consumed.contains(capture.invitation()))rollback();else cancelActive("Activation failed");return false;}
    }
    private void rollback(){var old=capture;stopping=true;try{port.rollback(old);}finally{stopping=false;lease.rollback(token);capture=null;token=null;}reason="Activation rolled back";}
    public boolean permitsActivationConflict(Object parent,Object helper){return activating&&lease.state()==ForagingMaintenanceLease.State.PREPARED&&capture!=null&&parent==capture.parent()&&helper==capture.helper()&&port.valid(capture);}
    public boolean preparedActivation(Object helper){return activating&&capture!=null&&capture.helper()==helper&&lease.state()==ForagingMaintenanceLease.State.PREPARED;}
    public void tick(long now){
        if(!active()||lease.state()==ForagingMaintenanceLease.State.PREPARED)return;
        if(!lease.validate(token,capture.parent(),capture.helper(),now,port.parentEnabled()&&port.valid(capture)&&port.ownsPause(token))){cancelActive("Captured context/ownership/deadline changed");return;}
        if(lease.state()==ForagingMaintenanceLease.State.CLAIMING){
            if(port.completed(capture)){lease.cleanup(token);reason="Waiting for actual menu cleanup";}
            else if(!port.helperEnabled()){cancelActive("Helper stopped without confirmed reward");return;}
        }
        if(lease.state()==ForagingMaintenanceLease.State.CLEANUP){
            if(!port.completed(capture)){cancelActive("Reward proof changed during cleanup");return;}
            if(!port.cleanupFinished(capture))return;
            lease.returning(token);reason="Returning to captured fractional pose";
        }
        if(lease.state()==ForagingMaintenanceLease.State.RETURNING){
            if(!port.completed(capture)||!port.cleanupFinished(capture)){cancelActive("Cleanup/reward changed during return");return;}
            var status=port.returnToPose(capture);
            if(status==ReturnStatus.FAILED){cancelActive("Saved pose return failed");return;}
            if(status==ReturnStatus.READY){
                if(!port.valid(capture)||!port.ownsPause(token)||!port.resume(capture,token)){cancelActive("Final exact resume proof failed");return;}
                lease.resume(token);capture=null;token=null;reason="Same foraging engine resumed";
            }
        }
    }
    /** Exact successful self-stop is cleanup, whereas every manual/failsafe stop cancels both. */
    public void beforeDisable(Object module){
        if(stopping||activating||capture==null)return;
        if(module==capture.helper()&&lease.state()==ForagingMaintenanceLease.State.CLAIMING&&port.completed(capture)){lease.cleanup(token);return;}
        if(module==capture.parent()||module==capture.helper())cancelActive("Captured controller stopped");
    }
    public void cancelActive(String text){
        if(stopping||capture==null)return;
        var old=capture;lease.cancel(token);capture=null;token=null;reason=Objects.requireNonNullElse(text,"Cancelled");
        stopping=true;try{port.stopBoth(old);}finally{stopping=false;}
    }
    /** Camera identity only: no Port callbacks or tick-owned validation. */
    public Object rotationAuthorityToken(Object player,Object world,Object connection,boolean helperEnabled){
        return active()&&lease.state()!=ForagingMaintenanceLease.State.PREPARED
                &&(helperEnabled||lease.state()==ForagingMaintenanceLease.State.CLEANUP||lease.state()==ForagingMaintenanceLease.State.RETURNING)
                &&capture.player()==player&&capture.world()==world&&capture.connection()==connection?token:null;
    }
    public boolean active(){return capture!=null&&lease.active();}
    public boolean owns(Object module){return active()&&(capture.parent()==module||capture.helper()==module);}
    /** A read-only control reservation cannot outlive current native identity/protection proof. */
    public Object validatedParent(){return active()&&lease.state()!=ForagingMaintenanceLease.State.PREPARED&&port.parentEnabled()&&port.ownsPause(token)&&port.valid(capture)?capture.parent():null;}
    public Object helper(){return active()?capture.helper():null;}
    public Object parent(){return active()?capture.parent():null;}
    public boolean consumed(Object invitation){return consumed.contains(invitation);}
    public ForagingMaintenanceLease.State state(){return lease.state();}
    public String status(){return "Foraging maintenance: "+lease.state()+" | "+reason;}
}
