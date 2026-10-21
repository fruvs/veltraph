package dev.veltraph.client.galatea;

import java.util.List;

/** Readable source scaffold decisions. This class does not create blocks or move a player.
 * Timer observations are supplied by the existing source timer adapter; they are not guessed here.
 * A callback generation is a clean ownership boundary, not a claim about original native custody.
 */
public final class FigScaffoldStateMachine {
    public enum State { IDLE, PREPARING_CENTRAL_STAND, SWITCH_TO_SCAFFOLDING,
        START_SCAFFOLDING, WAITING_FOR_SCAFFOLDING, BREAK_ON_SCAFFOLDING,
        THROWING_AXE, STOPPED }
    public enum Command { PREPARE_CENTRAL_STAND, AIM, CLEAR_ATTACK, SELECT_SCAFFOLD,
        INVOKE_RIGHT_CLICK_ONCE, SELECT_AXE, HOLD_ATTACK, CAPTURE_CENTERED_FEET,
        ENTER_SCAFFOLD_BREAK, ENTER_THROWING, RELEASE_OWNED_INPUTS }
    public enum Dispatch { NOT_ATTEMPTED, INVOKED_WITHOUT_QUEUED_EVENT, NATIVE_KEY_EVENT_QUEUED }
    public record Metadata(int geometryHeight, int completionRise) {}
    public record TimeoutContract(long considerAxeAfterMs,long stationaryAfterMs,String provenance) {
        public TimeoutContract {
            if(considerAxeAfterMs<0 || stationaryAfterMs<0 || provenance==null || provenance.isBlank())
                throw new IllegalArgumentException("Explicit observed timeout values and provenance required");
        }
    }
    public record Admission(boolean abovePassable, int currentReachable, int raisedReachable) {}
    public record Frame(long now, boolean authorityValid, boolean scaffoldSelected,
        boolean axeSelected, boolean giftFeedbackHold, boolean switchTimerReady,
        boolean preStartTimerReady, boolean periodicAimTimerReady, boolean onGround,
        boolean feetPassable, boolean abovePassable, boolean selectedSeedVisible,
        boolean networkGapObserved, double displacementLength, double feetY,
        double randomSample) {}
    public record Decision(State state, long generation, List<Command> commands) {
        public Decision { commands=List.copyOf(commands); }
    }
    public static TimeoutContract recoveredOrdinaryCallerTimeouts() {
        return new TimeoutContract(200,1000,"Validated ordinary fixed-event caller replay; original native key mutation remains unclaimed");
    }
    public boolean sourceParentEntryHold(long now) {
        return now-switchEntered<2000;
    }
    private final TimeoutContract timeout;
    public FigScaffoldStateMachine(TimeoutContract timeout) {
        this.timeout=java.util.Objects.requireNonNull(timeout,"Source or observed operational timeout contract required");
    }
    private State state=State.IDLE;
    private Metadata metadata;
    private long generation, started, lastMotion, switchEntered;
    private int startCeilY;
    private Dispatch dispatch=Dispatch.NOT_ATTEMPTED;

    public Decision begin(Metadata values) {
        if(values==null)throw new IllegalArgumentException("Two observed scaffold integers required");
        metadata=values;generation++;dispatch=Dispatch.NOT_ATTEMPTED;
        state=State.PREPARING_CENTRAL_STAND;
        return decision(Command.PREPARE_CENTRAL_STAND);
    }
    public Decision prepared(long callbackGeneration, boolean pathSucceeded, Admission admission,
                             long now) {
        if(callbackGeneration!=generation || state!=State.PREPARING_CENTRAL_STAND)return decision();
        if(!pathSucceeded || admission==null || !admission.abovePassable()
            || admission.raisedReachable()-3<=admission.currentReachable())return throwing();
        state=State.SWITCH_TO_SCAFFOLDING;switchEntered=now;
        return decision(Command.CLEAR_ATTACK,Command.AIM);
    }
    public Decision tick(Frame f) {
        if(state==State.IDLE || state==State.STOPPED || state==State.THROWING_AXE
            || state==State.BREAK_ON_SCAFFOLDING)return decision();
        if(!f.authorityValid())return stop();
        if(state==State.SWITCH_TO_SCAFFOLDING) {
            if(f.scaffoldSelected()){state=State.START_SCAFFOLDING;return decision(Command.AIM);}
            if(f.giftFeedbackHold() || !f.switchTimerReady())return decision(Command.AIM);
            return decision(Command.AIM,Command.SELECT_SCAFFOLD);
        }
        if(state==State.START_SCAFFOLDING) {
            if(f.giftFeedbackHold() || !f.onGround() || !f.preStartTimerReady())return decision(Command.AIM);
            startCeilY=(int)Math.ceil(f.feetY());started=lastMotion=f.now();
            // Original source unconditionally enters waiting after invoking the gated click helper.
            state=State.WAITING_FOR_SCAFFOLDING;
            return decision(Command.AIM,Command.INVOKE_RIGHT_CLICK_ONCE);
        }
        if(state!=State.WAITING_FOR_SCAFFOLDING)return decision();
        var commands=new java.util.ArrayList<Command>();
        // Source WAIT polls/queues periodic aiming before every later branch, including completion.
        if(f.periodicAimTimerReady())commands.add(Command.AIM);
        if(f.networkGapObserved() || f.displacementLength()>.1)lastMotion=f.now();
        if(f.randomSample()<.05 && f.now()-started>timeout.considerAxeAfterMs()) {
            commands.add(Command.SELECT_AXE);return new Decision(state,generation,commands);
        }
        boolean attack=f.axeSelected() && f.feetPassable() && f.selectedSeedVisible();
        if(attack)commands.add(Command.HOLD_ATTACK);
        if(f.onGround() && f.displacementLength()<=.1 && (!f.abovePassable()
            || f.feetY()>=(double)(startCeilY+metadata.completionRise())
            || f.now()-lastMotion>timeout.stationaryAfterMs())) {
            state=State.BREAK_ON_SCAFFOLDING;
            commands.add(Command.CAPTURE_CENTERED_FEET);
            commands.add(Command.SELECT_AXE);
            commands.add(Command.ENTER_SCAFFOLD_BREAK);
        }
        return new Decision(state,generation,commands);
    }

    public void recordDispatch(long callbackGeneration, Dispatch actual) {
        if(callbackGeneration==generation && state==State.WAITING_FOR_SCAFFOLDING
            && dispatch==Dispatch.NOT_ATTEMPTED && actual!=null)dispatch=actual;
    }
    public Decision placementFeedback(String text) {
        if(state!=State.IDLE && state!=State.STOPPED && text!=null
            && text.startsWith("You can't place a scaffolding here!"))return throwing();
        return decision();
    }
    public Decision stop() {generation++;state=State.STOPPED;return decision(Command.RELEASE_OWNED_INPUTS);}
    private Decision throwing(){state=State.THROWING_AXE;return decision(Command.RELEASE_OWNED_INPUTS,Command.ENTER_THROWING);}
    private Decision decision(Command... c){return new Decision(state,generation,List.of(c));}
    public State state(){return state;}
    public Dispatch dispatch(){return dispatch;}
    public long started(){return started;}
    public long lastMotion(){return lastMotion;}
    public int startCeilY(){return startCeilY;}
    public long generation(){return generation;}
}
