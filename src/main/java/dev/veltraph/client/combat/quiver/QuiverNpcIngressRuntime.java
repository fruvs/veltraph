package dev.veltraph.client.combat.quiver;

import dev.veltraph.client.gui.MenuSnapshot;
import java.util.List;
import java.util.Optional;

/** Bounded, single-interaction NPC ingress; an exact title alone cannot acknowledge an unsent request. */
public final class QuiverNpcIngressRuntime {
    public enum State { APPROACH, WAIT_MENU, OPEN, FAILED, CANCELLED }
    public enum Action { APPROACH, AIM, INTERACT }
    public record Contract(String name,String type,int maxDistance,int timeout,String menuTitle) {
        public Contract {
            name=MenuSnapshot.clean(name);menuTitle=MenuSnapshot.clean(menuTitle);
            if(name.isBlank()||type==null||type.isBlank()||maxDistance<1||maxDistance>32||timeout<1||timeout>2400||menuTitle.isBlank())
                throw new IllegalArgumentException("Configure exact quiver NPC name/type, distance 1..32 and bounded timeout");
        }
    }
    public record Candidate(String identity,String name,String type,boolean customNamed,boolean alive,double distance) {}
    public record Observation(List<Candidate> candidates,boolean cursorEmpty,boolean inventoryUnchanged,
                              String screenTitle,boolean container,boolean reachable,boolean visible,boolean raycast) {}
    private final Contract contract;private final String target;private final long deadline;
    private State state=State.APPROACH;private String reason="Approaching exact quiver NPC";private boolean pending;
    public QuiverNpcIngressRuntime(Contract contract,List<Candidate> candidates,long tick) {
        this.contract=contract;deadline=tick+contract.timeout();
        var matches=matches(candidates);target=matches.size()==1?matches.getFirst().identity():null;
        if(target==null)fail("Exact quiver NPC absent or ambiguous in configured range");
    }
    private List<Candidate> matches(List<Candidate> candidates){return candidates.stream().filter(c->c.customNamed()&&c.alive()
            &&MenuSnapshot.clean(c.name()).equals(contract.name())&&c.type().equals(contract.type())
            &&Double.isFinite(c.distance())&&c.distance()>=0&&c.distance()<=contract.maxDistance()).toList();}
    public String target(){return target;}public State state(){return state;}public String reason(){return reason;}
    public boolean terminal(){return state==State.OPEN||state==State.FAILED||state==State.CANCELLED;}
    public Optional<Action> tick(Observation observed,long tick){
        if(terminal()||pending)return Optional.empty();
        if(tick>deadline){fail("Quiver NPC approach/menu timed out; no interaction retry");return Optional.empty();}
        if(!observed.cursorEmpty()||!observed.inventoryUnchanged()){cancel("Quiver opener cursor/inventory ownership changed");return Optional.empty();}
        if(observed.screenTitle()!=null){
            if(state==State.WAIT_MENU&&observed.container()&&MenuSnapshot.clean(observed.screenTitle()).equals(contract.menuTitle())){state=State.OPEN;reason="Exact menu acknowledged after one NPC interaction";}
            else cancel("Unexpected screen before owned quiver menu acknowledgment");
            return Optional.empty();
        }
        var matches=matches(observed.candidates());
        if(matches.size()!=1||!matches.getFirst().identity().equals(target)){cancel("Exact quiver NPC disappeared, changed or became ambiguous");return Optional.empty();}
        if(state==State.WAIT_MENU)return Optional.empty();
        if(!observed.reachable()||!observed.visible())return Optional.of(Action.APPROACH);
        if(!observed.raycast())return Optional.of(Action.AIM);
        pending=true;return Optional.of(Action.INTERACT);
    }
    public void applied(boolean accepted){if(!pending)return;pending=false;if(!accepted)fail("Native quiver NPC interaction rejected");else{state=State.WAIT_MENU;reason="One NPC interaction sent; waiting for exact refill menu";}}
    public void cancel(String message){if(!terminal()){state=State.CANCELLED;reason=message;pending=false;}}
    private void fail(String message){state=State.FAILED;reason=message;pending=false;}
}
