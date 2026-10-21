package dev.veltraph.client.combat.slayer.tara;

import java.util.*;

/** WAITING Whip skill is a separate declared contract; unreadable source readiness/effect is never true by default. */
public final class TaraWaitingWhip {
    public enum Action { WAIT, PREPARE_WHIP, USE_WHIP, COMPLETE, FAIL }
    public record Profile(TaraSoulWhip.Tool primary,TaraSoulWhip.Tool fallback,int preparationTicks,String exactReadyPlate,
                          String outcomeMessageTemplate,double minimumNativeBossHealthDelta,long retryMs,long timeoutMs,int maxUses,double nativeRayLimit){
        public Profile {Objects.requireNonNull(primary);Objects.requireNonNull(fallback);
            if(primary.itemId().equals("minecraft:bow")||fallback.itemId().equals("minecraft:bow")||preparationTicks<3||preparationTicks>7
                    ||exactReadyPlate==null||exactReadyPlate.isBlank()||outcomeMessageTemplate==null||!outcomeMessageTemplate.contains("{boss_uuid}")
                    ||!Double.isFinite(minimumNativeBossHealthDelta)||minimumNativeBossHealthDelta<=0||retryMs<=0||retryMs>10000
                    ||timeoutMs<=retryMs||timeoutMs>60000||maxUses<1||maxUses>16||!Double.isFinite(nativeRayLimit)||nativeRayLimit<=0||nativeRayLimit>5)
                throw new IllegalArgumentException("Declare exact WAITING readiness/outcome and finite native Whip contract; Bow is a separate phase");}
    }
    public record Frame(TaraSoulWhip.Custody custody,double nativeHealth,boolean exactReadyPlate,boolean nativeRay,
                        boolean loaded,boolean safetyHold,long tick,long now){}
    private Profile profile;private TaraSoulWhip.Custody custody;private long startTick,startTime,lastTick,lastTime,lastUse=-1;
    private double baseline;private int uses;private boolean prepared,planned,receipt,complete,failed;private String status="No declared WAITING Whip contract";
    public void begin(Profile value,Frame frame){reset();profile=Objects.requireNonNull(value);if(frame==null||frame.custody()==null||!frame.loaded()||!frame.exactReadyPlate()||frame.safetyHold()||!Double.isFinite(frame.nativeHealth())||frame.nativeHealth()<=0){fail("WAITING Whip has no exact declared native readiness/context");return;}
        custody=frame.custody();baseline=frame.nativeHealth();startTick=lastTick=frame.tick();startTime=lastTime=frame.now();}
    public Action tick(Frame frame){if(failed)return Action.FAIL;if(complete)return Action.COMPLETE;
        if(profile==null||frame==null||!custody.same(frame.custody())||!frame.loaded()||frame.safetyHold()||frame.tick()<lastTick||frame.now()<lastTime||!Double.isFinite(frame.nativeHealth()))return fail("WAITING Whip lost exact custody/clock/native observation");
        lastTick=frame.tick();lastTime=frame.now();if(frame.now()-startTime>=profile.timeoutMs())return fail("Declared WAITING Whip outcome timed out");
        if(uses>0&&receipt&&baseline-frame.nativeHealth()>=profile.minimumNativeBossHealthDelta()){complete=true;status="Exact UUID-bound server receipt and independent native boss health effect observed";return Action.COMPLETE;}
        if(!frame.exactReadyPlate())return fail("Declared WAITING Whip readiness disappeared without its actual outcome");
        if(!prepared){if(frame.tick()-startTick<profile.preparationTicks())return Action.WAIT;prepared=true;status="Source 3..7 tick named Whip callback";return Action.PREPARE_WHIP;}
        if(uses>0&&!frame.nativeRay())return fail("WAITING Whip lost physical native ray after use; no retry");
        if(!frame.nativeRay()||planned||uses>0&&frame.now()-lastUse<profile.retryMs())return Action.WAIT;
        if(uses>=profile.maxUses())return fail("Declared WAITING Whip retries exhausted without native effect");planned=true;status="Ordinary exact named Whip native use requested";return Action.USE_WHIP;
    }
    public void sent(long now){if(!planned||failed||complete||now<lastTime){fail("Unplanned WAITING native Whip use");return;}planned=false;lastUse=now;uses++;}
    public void message(String text,long now){if(profile!=null&&uses>0&&!failed&&!complete&&now>=lastUse&&now-startTime<profile.timeoutMs()
            &&text!=null&&text.equals(profile.outcomeMessageTemplate().replace("{boss_uuid}",custody.boss().toString())))receipt=true;}
    public void reset(){profile=null;custody=null;startTick=startTime=lastTick=lastTime=0;lastUse=-1;baseline=0;uses=0;prepared=planned=receipt=complete=failed=false;status="No declared WAITING Whip contract";}
    public void stop(String reason){fail(reason);}private Action fail(String text){failed=true;complete=false;planned=false;status=text;return Action.FAIL;}
    public boolean pending(){return profile!=null&&!complete&&!failed;}public boolean complete(){return complete;}public boolean failed(){return failed;}public int uses(){return uses;}public String status(){return status;}
}
