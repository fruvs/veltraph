package dev.veltraph.client.combat.sven;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;
import static dev.veltraph.client.combat.sven.SvenIngressPort.*;

/** Dedicated Sven parent's owned child dispatch. Stable Travel switch offsets 336..686. */
public final class SvenIngressController {
    public enum Child { PREPARATION, WARP, BUFF, TRAVEL, COMBAT, STOPPED }
    public enum State { WARPING, ETHERWARPING, WALKING_TO_ENTRANCE, OPEN_BAZAAR, CLICK_SIGN,
        ENTER_INPUT, CLICK_TNT, CLICK_BUY_INSTANTLY, CLICK_BUY_ONE, CLOSE_BAZAAR,
        OPEN_INVENTORY, FREE_SLOT, MOVE_TNT_INTO_HOTBAR, CLOSE_INVENTORY,
        WAITING_FOR_EXPLOSION, ROTATE_TO_PLACEABLE_GROUND, WALK_INSIDE }
    private final SvenIngressPort port;
    private final RandomGenerator random;
    private final Runnable combatArrival;
    private Child child=Child.STOPPED;
    private State state;
    private SvenCaveProfile profile;
    private BlockPos gateCandidate;
    private Vec3 aimTarget;
    private long run,serial,tick,due;
    private Ticket active;
    private boolean pending,running,purchaseIssued,rotated,useIssued,autoToolSwap,travelPrerequisite;
    private int originalSlot,resourceSlot=-1;
    private String blocker="",failure="";
    private String receiptPrefix="",receiptItem="",denied="";
    private Set<String> bazaarRetry=Set.of();
    private Child buffReturn;

    public SvenIngressController(SvenIngressPort port,RandomGenerator random,Runnable combatArrival){
        this.port=Objects.requireNonNull(port);this.random=Objects.requireNonNull(random);this.combatArrival=Objects.requireNonNull(combatArrival);
    }
    public void start(SvenCaveProfile profile,boolean autoToolSwap,String receiptPrefix,String receiptItem,String denied,Set<String> retry){
        int startSlot=port.selectedSlot();stop();port.selectSlot(startSlot);this.profile=Objects.requireNonNull(profile);this.autoToolSwap=autoToolSwap;
        this.receiptPrefix=clean(receiptPrefix);this.receiptItem=clean(receiptItem);this.denied=clean(denied);bazaarRetry=Set.copyOf(retry);
        originalSlot=startSlot;running=true;blocker=failure="";state=null;child=Child.PREPARATION;
        tick=0;due=0;travelPrerequisite=false;gateCandidate=null;aimTarget=null;resourceSlot=-1;
    }
    /** Stop invalidates both this run and every issued operation before touching native controls. */
    public void stop(){run++;active=null;pending=false;running=false;port.cancel();if(originalSlot>=0&&originalSlot<=8&&profile!=null)port.selectSlot(originalSlot);child=Child.STOPPED;}
    public void tick(){
        if(!running||!blocker.isEmpty()||!failure.isEmpty())return;
        tick++;port.tick();if(!running||pending||tick<due||!blocker.isEmpty()||!failure.isEmpty())return;
        switch(child){
            case PREPARATION -> issue(Operation.PREPARE,this::prepared);
            case WARP -> issue(Operation.PARENT_WARP,r->{if(r.continuation()==Continuation.BUFF)buff(Child.TRAVEL);else enterTravel();});
            case BUFF -> issue(Operation.BUFF,r->{if(buffReturn==Child.TRAVEL)enterTravel();else if(buffReturn==Child.COMBAT)startCombat();else fail("Unknown buff continuation");});
            case TRAVEL -> dispatchTravel();
            case COMBAT, STOPPED -> {}
        }
    }
    private void prepared(Result result){
        switch(result.continuation()){
            case TRAVEL -> enterTravel();
            case PARENT_WARP -> retryWarp();
            case COMBAT -> startCombat();
            case COMBAT_AFTER_BUFF -> buff(Child.COMBAT);
            case BUFF -> buff(Child.TRAVEL);
            case DEFAULT -> block("PREPARATION: shared prerequisite, usable-location/path predicate and anchor (<15.0) continuation are unbound");
        }
    }
    private void startCombat(){child=Child.COMBAT;port.cancel();combatArrival.run();}
    private void enterTravel(){child=Child.TRAVEL;travelPrerequisite=false;state=State.WARPING;resetAction();}
    private void buff(Child continuation){buffReturn=continuation;child=Child.BUFF;resetAction();}
    private void dispatchTravel(){
        if(!travelPrerequisite){issue(Operation.TRAVEL_PREREQUISITE,r->{
            if(r.continuation()==Continuation.BUFF)buff(Child.TRAVEL);else travelPrerequisite=true;
        });return;}
        switch(state){
            case WARPING -> issue(Operation.WARP_ROUTE_START,r->enter(State.ETHERWARPING));
            case ETHERWARPING -> issue(Operation.PROFILE_ROUTE,r->{prepareEntrance();enter(State.WALKING_TO_ENTRANCE);due=tick+1;});
            case WALKING_TO_ENTRANCE -> issue(Operation.WALK_ENTRANCE,r->entranceArrived());
            case OPEN_BAZAAR -> issue(Operation.OPEN_BAZAAR,r->enter(State.CLICK_SIGN));
            case CLICK_SIGN -> issue(Operation.CLICK_SIGN,r->enter(State.ENTER_INPUT));
            case ENTER_INPUT -> issue(Operation.ENTER_INPUT,r->enter(State.CLICK_TNT));
            case CLICK_TNT -> issue(Operation.CLICK_TNT,r->enter(State.CLICK_BUY_INSTANTLY));
            case CLICK_BUY_INSTANTLY -> issue(Operation.CLICK_BUY_INSTANTLY,r->enter(State.CLICK_BUY_ONE));
            case CLICK_BUY_ONE -> {
                if(receiptPrefix.isBlank()||receiptItem.isBlank()){block("CLICK_BUY_ONE: current salted receipt prefix/item are unbound");return;}
                if(!purchaseIssued)issue(Operation.CLICK_BUY_ONE,r->purchaseIssued=true);
            }
            case CLOSE_BAZAAR -> issue(Operation.CLOSE_BAZAAR,r->placement());
            case OPEN_INVENTORY -> issue(Operation.OPEN_INVENTORY,r->enter(State.FREE_SLOT));
            case FREE_SLOT -> issue(Operation.FREE_SLOT,r->{if(port.resourceHotbarSlot().isPresent())placement();else enter(State.OPEN_BAZAAR);});
            // Source arm exists; no incoming write to MOVE_TNT_INTO_HOTBAR is established.
            case MOVE_TNT_INTO_HOTBAR -> issue(Operation.MOVE_TNT_INTO_HOTBAR,r->enter(State.CLOSE_INVENTORY));
            case CLOSE_INVENTORY -> issue(Operation.CLOSE_INVENTORY,r->enter(State.CLOSE_BAZAAR));
            case ROTATE_TO_PLACEABLE_GROUND -> {
                if(!rotated)issue(Operation.ROTATE,r->{rotated=true;due=tick+random.nextInt(3,7);});
                else if(!useIssued)issue(Operation.USE_RESOURCE,r->{useIssued=true;enter(State.WAITING_FOR_EXPLOSION);});
            }
            case WAITING_FOR_EXPLOSION -> {var gate=port.gatePassable(profile.gate());if(gate.isEmpty())block("WAITING_FOR_EXPLOSION: loaded source gate observation unavailable");else if(gate.get())walkInside();}
            case WALK_INSIDE -> issue(Operation.WALK_INSIDE,r->{startCombat();});
        }
    }
    private void prepareEntrance(){
        var candidates=port.crackedGateCandidates(profile.gate());
        gateCandidate=candidates.isEmpty()?profile.gate():candidates.get(random.nextInt(candidates.size()));
    }
    private void entranceArrived(){
        var gate=port.gatePassable(profile.gate());
        if(gate.isEmpty()){block("WALKING_TO_ENTRANCE: loaded source gate observation unavailable");return;}
        if(gate.get()){walkInside();return;}
        var unbound=port.resourceReadiness();if(unbound.isPresent()){block("WALKING_TO_ENTRANCE: "+unbound.get());return;}
        if(port.resourceHotbarSlot().isPresent()){placement();return;}
        var empty=port.hasEmptyHotbarSlot();if(empty.isEmpty()){block("WALKING_TO_ENTRANCE: hotbar inventory observation unavailable");return;}
        enter(empty.get()?State.OPEN_BAZAAR:State.OPEN_INVENTORY);
    }
    private void placement(){
        enter(State.ROTATE_TO_PLACEABLE_GROUND);
        var unbound=port.resourceReadiness();if(unbound.isPresent()){block("ROTATE_TO_PLACEABLE_GROUND: "+unbound.get());return;}
        var slot=port.resourceHotbarSlot();if(slot.isEmpty()){retryWarp();return;}
        resourceSlot=slot.get();
        if(resourceSlot<0||resourceSlot>8){fail("Resource helper returned an invalid hotbar slot");return;}
        var eye=port.eyePosition();if(eye==null||gateCandidate==null){block("ROTATE_TO_PLACEABLE_GROUND: eye/candidate observation unavailable");return;}
        List<Vec3> candidates=new ArrayList<>();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
            Vec3 p=new Vec3(gateCandidate.x()+x+.5+random.nextDouble(-.2,.2),gateCandidate.y(),gateCandidate.z()+z+.5+random.nextDouble(-.2,.2));
            if(p.distanceTo(eye)<=4)candidates.add(p);
        }
        if(candidates.isEmpty()){block("ROTATE_TO_PLACEABLE_GROUND: no source candidate within 4.0 blocks of eyes");return;}
        aimTarget=candidates.get(random.nextInt(candidates.size()));port.selectSlot(resourceSlot);
    }
    private void walkInside(){
        var gate=port.gatePassable(profile.gate());if(gate.isEmpty()||!gate.get()){block("WALK_INSIDE: gate passability proof was lost");return;}
        if(autoToolSwap){issue(Operation.PREPARE_INSIDE_TOOL,r->{enter(State.WALK_INSIDE);due=tick+1;});return;}
        port.selectSlot(originalSlot);enter(State.WALK_INSIDE);due=tick+1;
    }
    public void message(String line){
        if(!running||!blocker.isEmpty()||!failure.isEmpty()||child!=Child.TRAVEL)return;
        line=clean(line);
        if(!denied.isBlank()&&line.equals(denied)){fail("Source fast-travel denial observed");return;}
        if(bazaarRetry.contains(line)){invalidate();port.cancel();retryWarp();return;}
        if(state==State.CLICK_BUY_ONE&&purchaseIssued&&!receiptPrefix.isBlank()&&!receiptItem.isBlank()
                &&line.startsWith(receiptPrefix)&&line.contains(receiptItem)){invalidate();enter(State.CLOSE_BAZAAR);}
    }
    private void issue(Operation op,Consumer<Result> continuation){
        if(pending)return;
        var ticket=new Ticket(run,++serial);active=ticket;pending=true;Child expectedChild=child;State expectedState=state;
        port.begin(new Request(op,ticket,profile,gateCandidate,aimTarget,resourceSlot),result->{
            if(!running||!ticket.equals(active)||child!=expectedChild||state!=expectedState||!blocker.isEmpty()||!failure.isEmpty())return;
            pending=false;active=null;
            if(result==null){fail(op+": missing operation acknowledgment");return;}
            if(result.outcome()==Outcome.BLOCKED){block(op+": "+result.reason());return;}
            if(result.outcome()==Outcome.FAILED){
                if(op==Operation.WALK_INSIDE){retryWarp();return;}
                fail(op+": "+result.reason());return;
            }
            continuation.accept(result);
        });
    }
    private void retryWarp(){invalidate();port.cancel();child=Child.WARP;resetAction();}
    private void enter(State next){state=next;resetAction();}
    private void resetAction(){pending=false;active=null;purchaseIssued=rotated=useIssued=false;due=tick;}
    private void invalidate(){serial++;active=null;pending=false;}
    private void block(String reason){blocker=reason;invalidate();port.cancel();}
    private void fail(String reason){failure=reason;invalidate();port.cancel();}
    private static String clean(String s){return s==null?"":s.replaceAll("§.","").strip();}
    public Child child(){return child;}
    public State state(){return state;}
    public Optional<String> blocker(){return blocker.isEmpty()?Optional.empty():Optional.of(blocker);}
    public Optional<String> failure(){return failure.isEmpty()?Optional.empty():Optional.of(failure);}
    public int originalSlot(){return originalSlot;}
    public boolean pending(){return pending;}
    public Optional<Ticket> pendingTicket(){return pending?Optional.ofNullable(active):Optional.empty();}
    public String status(){return child+(state==null?"":" / "+state)+(blocker.isEmpty()?"":" | blocked: "+blocker)+(failure.isEmpty()?"":" | failed: "+failure);}
}
