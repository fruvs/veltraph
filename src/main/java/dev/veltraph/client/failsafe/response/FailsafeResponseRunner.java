package dev.veltraph.client.failsafe.response;

import dev.veltraph.client.hydra.response.HydraLook;
import dev.veltraph.client.log.VeltraphLog;

/** One per-plan executor and exact sink binding. Idle reset has no native cleanup authority. */
public final class FailsafeResponseRunner {
    private static final class Run {
        final FailsafeResponsePlan plan;final FailsafeActionSink sink;final HydraLook look;final int maxTicks;
        int index,ticksLeft,ticksRun;boolean active,cleaning;
        Run(FailsafeResponsePlan plan,float cap,int maximum,FailsafeActionSink sink){
            this.plan=plan;this.sink=sink;maxTicks=maximum>0?maximum:Math.max(1,plan.totalTicks()+2);active=!plan.isEmpty();
            cap=Math.max(1,cap);look=new HydraLook(new HydraLook.Settings(cap,cap,.34,.6f));
        }
    }
    private Run current;
    private long generation;
    private boolean selected(Run run){return run!=null&&current==run&&run.active;}
    public void start(FailsafeResponsePlan plan,float lookMaxStepDeg,int maxTicks,FailsafeActionSink sink){
        long before=generation;reset(sink);if(generation!=before+1)return;
        var run=new Run(plan==null?new FailsafeResponsePlan("none",java.util.List.of()):plan,lookMaxStepDeg,maxTicks,sink);
        current=run;generation++;
        try{prime(run);}catch(RuntimeException failure){if(current==run)finish(run,"sink-failure");throw failure;}
        VeltraphLog.basic("FailsafeResponseRunner","plan queued: "+run.plan.label()+" actions="+run.plan.actions().size()+" maxTicks="+run.maxTicks);
    }
    public boolean active(){return current!=null&&current.active;}
    public String label(){return active()?current.plan.label():"none";}
    public int currentActionIndex(){return current==null?0:current.index;}
    public int remainingTicks(){return active()?Math.max(0,current.maxTicks-current.ticksRun):0;}
    private void prime(Run run){
        if(!selected(run))return;
        if(run.index>=run.plan.actions().size()){finish(run,"completed");return;}
        var a=run.plan.actions().get(run.index);run.ticksLeft=Math.max(1,a.durationTicks());var sink=run.sink;
        if(sink==null)return;
        if(a.kind()==FailsafeResponseAction.Kind.LOOK){
            float y=sink.currentYaw();if(!selected(run))return;
            float p=sink.currentPitch();if(!selected(run))return;
            run.look.begin((Float.isNaN(y)?0:y)+a.dYaw(),(Float.isNaN(p)?0:p)+a.dPitch());
            VeltraphLog.details("FailsafeResponseRunner","LOOK begin dYaw="+a.dYaw()+" dPitch="+a.dPitch());
        }else if(a.kind()==FailsafeResponseAction.Kind.COMMAND&&a.command()!=null&&!a.command().isBlank())sink.sendCommand(a.command().trim());
    }
    /** A caller cannot advance this plan through another source's sink. */
    public void tick(FailsafeActionSink sink){
        var run=current;if(!selected(run)||run.sink==null||sink!=run.sink)return;
        try{advance(run);}catch(RuntimeException failure){if(current==run)finish(run,"sink-failure");throw failure;}
    }
    private void advance(Run run){
        if(!selected(run))return;
        if(run.index>=run.plan.actions().size()||run.ticksRun>=run.maxTicks){finish(run,run.ticksRun>=run.maxTicks?"max-ticks":"completed");return;}
        var a=run.plan.actions().get(run.index);apply(run,a);if(!selected(run))return;
        VeltraphLog.details("FailsafeResponseRunner",a.kind()+" step");
        run.ticksRun++;run.ticksLeft--;
        if(run.ticksRun>=run.maxTicks){finish(run,"max-ticks");return;}
        if(run.ticksLeft<=0){
            var cleanup=run.sink.captureOwnedControls();if(!selected(run))return;
            run.sink.releaseOwnedControls(cleanup);if(!selected(run))return;
            run.index++;if(run.index>=run.plan.actions().size())finish(run,"completed");else prime(run);
        }
    }
    private void apply(Run run,FailsafeResponseAction a){
        var sink=run.sink;
        switch(a.kind()){
            case LOOK->{
                float y=sink.currentYaw();if(!selected(run))return;
                float p=sink.currentPitch();if(!selected(run))return;
                float[] next=run.look.step(y,p);if(!selected(run))return;sink.applyLook(next[0],next[1]);
            }
            case MOVE->sink.setMovement(a.forward(),a.back(),a.left(),a.right(),false,false);
            case JUMP->sink.setMovement(false,false,false,false,true,false);
            case SNEAK->sink.setMovement(false,false,false,false,false,true);
            case ATTACK_CLICK->sink.setAttack(true);
            case USE_CLICK->sink.setUse(true);
            case WAIT,COMMAND->{}
        }
    }
    private void finish(Run run,String why){
        if(current!=run)return;
        cleanup(run);
        VeltraphLog.basic("FailsafeResponseRunner","plan "+why+": "+run.plan.label());
    }
    private void cleanup(Run run){
        if(current!=run)return;
        if(run.cleaning){current=null;run.active=false;generation++;return;}
        Object captured=run.sink==null?null:run.sink.captureOwnedControls();if(current!=run)return;
        run.cleaning=true;
        try{if(run.sink!=null&&(run.active||run.sink.hasOwnedControls()))run.sink.releaseOwnedControls(captured);}
        finally{run.active=false;run.look.stop();run.cleaning=false;if(current==run){current=null;generation++;}}
    }
    /** Clean only the old active binding or independently confirmed sink-issued controls. */
    public void reset(FailsafeActionSink sink){
        var run=current;
        if(run!=null){boolean wasActive=run.active;cleanup(run);if(wasActive)VeltraphLog.basic("FailsafeResponseRunner","plan reset: "+run.plan.label());return;}
        long before=++generation;
        if(sink!=null&&sink.hasOwnedControls()){
            var captured=sink.captureOwnedControls();if(generation==before&&current==null)sink.releaseOwnedControls(captured);
        }
    }
}
