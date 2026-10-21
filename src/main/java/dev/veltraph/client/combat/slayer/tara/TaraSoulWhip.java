package dev.veltraph.client.combat.slayer.tara;

import java.util.*;

/** Readable Tara egg parent. A requested right-use is never an acknowledgment of its server effect. */
public final class TaraSoulWhip {
    public enum Phase { WAITING_FOR_EGGS, KILLING_EGGS }
    public enum Action { WAIT, AIM, PREPARE_WHIP, PREPARE_BOW, DRAW_BOW, RELEASE_BOW, CONTINUE, FAIL }
    public enum OutcomeBinding { EXACT_LOADED_BODY_REMOVAL }
    public record Tool(String exactName,String itemId) {
        public Tool {if(exactName==null||exactName.isBlank()||itemId==null||!itemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Supply the exact observed tool name and native item identity");}
    }
    /** Salted names and source buffer/retry fields remain explicit; no production gameplay defaults. */
    public record Profile(Tool primary,Tool fallback,Tool bow,int preparationTicks,int bowPreparationTicks,int bowDrawTicks,long groupBufferMs,long retryMs,long timeoutMs,int maxUses,double nativeRayLimit,OutcomeBinding outcomeBinding) {
        public Profile {
            Objects.requireNonNull(primary);Objects.requireNonNull(fallback);Objects.requireNonNull(bow);Objects.requireNonNull(outcomeBinding,"Supply an explicit independently observed server outcome binding");
            if(!bow.itemId().equals("minecraft:bow")||preparationTicks<3||preparationTicks>7||bowPreparationTicks<5||bowPreparationTicks>14||bowDrawTicks<3||bowDrawTicks>40||groupBufferMs<0||groupBufferMs>10000||retryMs<=0||retryMs>10000
                    ||timeoutMs<=groupBufferMs||timeoutMs>60000||maxUses<1||maxUses>16||!Double.isFinite(nativeRayLimit)||nativeRayLimit<=0||nativeRayLimit>5)
                throw new IllegalArgumentException("Supply bounded source-unresolved pacing and native ray profile; Tara whip preparation is 3..7 ticks");
        }
    }
    public record Custody(UUID player,UUID boss,UUID ownerPlate,Object world,Object connection,Object loadout) {
        public Custody {Objects.requireNonNull(player);Objects.requireNonNull(boss);Objects.requireNonNull(ownerPlate);Objects.requireNonNull(world);Objects.requireNonNull(connection);Objects.requireNonNull(loadout);}
        boolean same(Custody other){return other!=null&&player.equals(other.player)&&boss.equals(other.boss)&&ownerPlate.equals(other.ownerPlate)
                &&world==other.world&&connection==other.connection&&loadout.equals(other.loadout);}
    }
    public record Frame(Custody custody,UUID objective,List<UUID> bodies,Set<UUID> removedBodies,boolean ownershipVerified,
                        boolean loaded,boolean aimReady,boolean safetyHold,long tick,long now) {
        public Frame {bodies=List.copyOf(bodies);removedBodies=Set.copyOf(removedBodies);}
    }
    public record Decision(Action action,UUID target,String detail) {}
    private Profile profile;private Custody custody;private UUID objective,target;private List<UUID> bodies=List.of();
    private final Set<UUID> acknowledged=new HashSet<>();
    private Phase phase=Phase.WAITING_FOR_EGGS;private long startTick,startTime,lastTick,lastTime,lastSent=-1;
    private int sentUses,targetUses;private boolean awaiting,requestPlanned,complete,failed,whipPrepared,bowPrepared,drawing;private long bowPreparationAt,drawStarted;private String status="No declared Tara egg phase";
    public void reset(){profile=null;custody=null;objective=target=null;bodies=List.of();acknowledged.clear();phase=Phase.WAITING_FOR_EGGS;
        startTick=startTime=lastTick=lastTime=bowPreparationAt=drawStarted=0;lastSent=-1;sentUses=targetUses=0;awaiting=requestPlanned=complete=failed=whipPrepared=bowPrepared=drawing=false;status="No declared Tara egg phase";}
    public Decision begin(Profile chosen,Frame frame){
        reset();profile=Objects.requireNonNull(chosen);
        if(frame==null||frame.custody()==null||frame.objective()==null||frame.bodies().isEmpty()||!frame.removedBodies().isEmpty()
                ||!frame.ownershipVerified()||!frame.loaded()||frame.safetyHold()||new HashSet<>(frame.bodies()).size()!=frame.bodies().size())return fail("Declared egg group lacks exact native ownership/body/context proof");
        custody=frame.custody();objective=frame.objective();bodies=frame.bodies();target=bodies.getFirst();
        startTick=lastTick=frame.tick();startTime=lastTime=frame.now();return result(Action.WAIT,"Waiting for source whip preparation and explicit egg buffer");
    }
    public Decision beginAfterOwnedWhip(Profile chosen,Frame frame){var first=begin(chosen,frame);if(!failed)whipPrepared=true;return first;}
    public Decision tick(Frame frame){
        if(failed)return result(Action.FAIL,status);if(complete)return result(Action.CONTINUE,status);
        if(profile==null)return fail("Tara egg parent was not armed");
        if(frame==null||!custody.same(frame.custody())||!objective.equals(frame.objective())||!frame.ownershipVerified()
                ||!frame.loaded()||frame.safetyHold())return fail("Tara egg phase lost exact custody, loaded ownership or safe action proof");
        if(frame.tick()<lastTick||frame.now()<lastTime)return fail("Tara egg phase clock regressed");
        lastTick=frame.tick();lastTime=frame.now();
        if(frame.now()-startTime>=profile.timeoutMs())return fail("Tara egg phase expired without bounded native acknowledgment");
        var current=new HashSet<>(frame.bodies());
        if(current.size()!=frame.bodies().size()||!bodies.containsAll(current)||!bodies.containsAll(frame.removedBodies())
                ||!Collections.disjoint(current,frame.removedBodies())||!Collections.disjoint(current,acknowledged))return fail("Tara egg body roster changed or acquired an unrelated body");
        for(var body:bodies)if(!acknowledged.contains(body)&&!current.contains(body)){
            if(!body.equals(target)||!awaiting||!frame.removedBodies().contains(body))return fail("Egg disappearance has no preceding exact native right-use request");
            acknowledged.add(body);awaiting=requestPlanned=false;targetUses=0;lastSent=-1;
        }
        if(acknowledged.size()==bodies.size()){complete=true;phase=Phase.WAITING_FOR_EGGS;return result(Action.CONTINUE,"Exact owned egg bodies removed after native right-use; return to same Tara parent");}
        if(acknowledged.contains(target))target=bodies.stream().filter(body->!acknowledged.contains(body)).findFirst().orElseThrow();
        if(!whipPrepared){if(frame.tick()-startTick<profile.preparationTicks())return result(Action.WAIT,"Waiting for source named-Whip callback (3..7 ticks)");whipPrepared=true;return result(Action.PREPARE_WHIP,"Source WAITING_FOR_EGGS callback selects primary/fallback named Whip identity");}
        if(phase==Phase.WAITING_FOR_EGGS){if(frame.now()-startTime<profile.groupBufferMs())return result(Action.WAIT,"Waiting for declared source-unresolved egg group buffer");phase=Phase.KILLING_EGGS;bowPreparationAt=frame.tick();return result(Action.WAIT,"Group admitted; schedule separate native Bow callback (5..14 ticks)");}
        if(!bowPrepared){if(frame.tick()-bowPreparationAt<profile.bowPreparationTicks())return result(Action.WAIT,"Waiting for source KILLING_EGGS Bow callback");bowPrepared=true;return result(Action.PREPARE_BOW,"Source KILLING_EGGS callback selects the separate native Bow tool");}
        if((awaiting||drawing)&&!frame.aimReady())return fail("Native ray/corridor proof lost after draw/use; no release or retry");
        if(!frame.aimReady())return result(Action.AIM,"Aim at the exact native egg body; source group boxes are planning metadata");
        if(drawing){if(frame.tick()-drawStarted<profile.bowDrawTicks())return result(Action.WAIT,"Owned ordinary native Bow draw pending");drawing=false;requestPlanned=true;return result(Action.RELEASE_BOW,"Release the owned native Bow draw; await independent actual arrow/egg outcome");}
        if(requestPlanned)return result(Action.WAIT,"Ordinary native Bow release dispatch is pending");
        if(awaiting&&frame.now()-lastSent<profile.retryMs())return result(Action.WAIT,"Native right-use sent; exact loaded server removal pending");
        if(targetUses>=profile.maxUses())return fail("Bounded Tara right-use retries exhausted without actual egg removal");
        drawing=true;drawStarted=frame.tick();return result(Action.DRAW_BOW,"Begin ordinary native Bow draw with the frozen separate tool");
    }
    /** Called only after releaseUsingItem completes an owned native Bow draw; local return is never server proof. */
    public void sent(UUID body,long now){
        if(failed||complete||!requestPlanned||!Objects.equals(target,body)||now<lastTime){fail("Unplanned or stale native right-use dispatch");return;}
        requestPlanned=false;awaiting=true;lastSent=now;sentUses++;targetUses++;
    }
    public Decision stop(String reason){return fail(reason);}
    private Decision fail(String reason){failed=true;complete=false;requestPlanned=false;status=reason;return new Decision(Action.FAIL,target,status);}
    private Decision result(Action action,String detail){status=detail;return new Decision(action,target,detail);}
    public Phase phase(){return phase;}public boolean complete(){return complete;}public boolean failed(){return failed;}
    public boolean pending(){return profile!=null&&!complete&&!failed;}public int sentUses(){return sentUses;}public String status(){return status;}
    public Optional<UUID> target(){return Optional.ofNullable(target);}
}
