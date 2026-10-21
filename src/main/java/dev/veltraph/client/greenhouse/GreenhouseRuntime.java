package dev.veltraph.client.greenhouse;

import java.util.*;
import static dev.veltraph.client.greenhouse.GreenhouseModel.*;

/** Finite local cultivation transaction. An issued action cannot acknowledge or retry itself. */
public final class GreenhouseRuntime {
    public enum Kind { HARVEST, WATER, REPLANT }
    public enum State { IDLE, APPROACH, SELECT, AIM, WAIT_ACK, RETURN, COMPLETE, FAILED, CANCELLED, UNKNOWN_OUTCOME }
    public record Action(String id,Kind kind,Cell cell,Position approach,String toolIdentity,int toolSlot,
                         Plant before,Plant after,Map<String,Integer> inventoryDelta) {
        public Action { inventoryDelta=Map.copyOf(inventoryDelta); }
    }
    public record Plan(String id,Context context,List<Action> actions,String selectedByUser) {
        public Plan { actions=List.copyOf(actions); }
    }
    public record Settings(boolean enabled,int maxActions,long freshnessMs,long actionTimeoutMs,long returnTimeoutMs) {}
    public interface Port {
        boolean approach(Position destination);
        boolean arrived(Position destination);
        boolean select(int slot,String exactIdentity);
        boolean aim(Action action);
        boolean issue(Action action,Snapshot expected);
        default boolean restoreSelectionAndView(){return true;}
        void stopAction();
        void release();
    }
    private final Port port;
    private State state=State.IDLE;private String reason="No explicit cultivation plan";
    private Plan plan;private Settings settings;private Snapshot baseline;private Position savedPose;
    private int index;private long stageAt,last,lastIssuedAt=-1;private boolean travelStarted,issued;
    private final Map<String,Long> importedJournalRevision=new HashMap<>();private final Map<String,Long> importedInspectionFloor=new HashMap<>();private final Set<String> unresolved=new HashSet<>();private final Set<String> acknowledged=new HashSet<>();private final Set<String> journalResolutions=new HashSet<>();
    public GreenhouseRuntime(Port port){this.port=Objects.requireNonNull(port);}
    public Optional<String> validate(Plan p,Snapshot s,Settings c,long now){
        if(c==null||!c.enabled())return Optional.of("Enable Greenhouse and select an explicit cultivation plan");
        if(c.maxActions()<1||c.maxActions()>64||c.freshnessMs()<1||c.actionTimeoutMs()<1||c.returnTimeoutMs()<1)return Optional.of("Invalid bounded Greenhouse settings");
        if(p==null||p.id()==null||p.id().isBlank()||p.selectedByUser()==null||p.selectedByUser().isBlank()||p.actions().isEmpty()||p.actions().size()>c.maxActions())return Optional.of("Explicit nonempty user-selected bounded plan required");
        if(s==null||s.knowledge(now,c.freshnessMs())!=Knowledge.KNOWN||!s.screenFree()||!s.inventoryValid()||!p.context().equals(s.context()))return Optional.of("Fresh profile/plot/world plant and protected inventory observations required");
        Set<String> ids=new HashSet<>();
        for(Action a:p.actions()){
            if(a.id()==null||a.id().isBlank()||!ids.add(a.id())||unresolved.contains(cellKey(p,a))||acknowledged.contains(key(p,a)))return Optional.of("Duplicate, completed or unresolved action requires a new inspected plan");
            if(a.cell()==null||a.approach()==null||!a.approach().finite()||a.toolIdentity()==null||a.toolIdentity().isBlank()||a.toolSlot()<0||a.toolSlot()>8||a.before()==null||a.after()==null||!a.before().identified()||!a.after().identified())return Optional.of("Exact observed cell/tool/outcome required");
            if(!semantic(a))return Optional.of("Action does not match ordinary harvest, water or replant evidence");
            if(a.inventoryDelta().isEmpty()&&a.kind()!=Kind.WATER)return Optional.of("Harvest/replant needs exact server inventory receipt");
            if(a.inventoryDelta().entrySet().stream().anyMatch(e->e.getKey().isBlank()||e.getValue()==null||e.getValue()==0))return Optional.of("Invalid exact inventory receipt");
            if(a.kind()==Kind.REPLANT&&(a.inventoryDelta().size()!=1||a.inventoryDelta().values().iterator().next()!=-1))return Optional.of("Ordinary replant consumes exactly one selected seed");
            if(a.kind()==Kind.HARVEST&&a.inventoryDelta().values().stream().anyMatch(v->v<0))return Optional.of("Harvest receipt cannot consume protected supplies");
            if(a.kind()==Kind.WATER&&a.inventoryDelta().values().stream().anyMatch(v->v>0))return Optional.of("Water cannot grant inventory items");
        }
        var projectedInventory=new HashMap<>(s.inventory());var projectedPlants=new HashMap<>(s.plants());
        try{for(Action a:p.actions()){
            if(!a.before().equals(projectedPlants.get(a.cell())))return Optional.of("Planned before-state does not match the exact observed/sequential cell");
            for(var delta:a.inventoryDelta().entrySet()){int amount=Math.addExact(projectedInventory.getOrDefault(delta.getKey(),0),delta.getValue());if(amount<0)return Optional.of("Selected cultivation supply is unavailable");if(amount==0)projectedInventory.remove(delta.getKey());else projectedInventory.put(delta.getKey(),amount);}
            projectedPlants.put(a.cell(),a.after());
        }}catch(ArithmeticException overflow){return Optional.of("Cultivation inventory count overflow");}
        return Optional.empty();
    }
    private boolean semantic(Action a){return switch(a.kind()){
        case HARVEST->a.before().state()==PlantState.HARVEST_READY&&a.after().state()==PlantState.EMPTY;
        case WATER->(a.before().state()==PlantState.GROWING||a.before().state()==PlantState.HARVEST_READY)&&a.before().water()==WaterState.DRY&&a.after().crop().equals(a.before().crop())&&a.after().state()==a.before().state()&&a.after().water()==WaterState.WATERED&&a.after().growthStage()==a.before().growthStage()&&a.after().fingerprint().equals(a.before().fingerprint())&&a.after().source().equals(a.before().source());
        case REPLANT->a.before().state()==PlantState.EMPTY&&a.after().state()==PlantState.GROWING&&!a.after().crop().isBlank();};}
    public boolean start(Plan p,Snapshot s,Settings c,long now){
        if(active())return false;var invalid=validate(p,s,c,now);if(invalid.isPresent()){state=State.FAILED;reason=invalid.get();return false;}
        plan=p;settings=c;baseline=s;savedPose=s.position();index=0;last=stageAt=now;issued=travelStarted=false;state=State.APPROACH;reason="Scoped plan and inventory captured";return true;
    }
    public void tick(Snapshot s,Settings current,long now){
        try{tickGuarded(s,current,now);}catch(RuntimeException invalid){if(active())finish(issued?State.UNKNOWN_OUTCOME:State.FAILED,"Cultivation transport/observation failed; no retry");}
    }
    private void tickGuarded(Snapshot s,Settings current,long now){
        if(!active())return;
        if(now<last){finish(issued?State.UNKNOWN_OUTCOME:State.FAILED,"Clock moved backward; inspect before new run");return;}last=now;
        long timeout=state==State.RETURN?settings.returnTimeoutMs():settings.actionTimeoutMs();
        long originalElapsed=now-stageAt;
        if(originalElapsed>=timeout){finish(issued?State.UNKNOWN_OUTCOME:State.FAILED,"Stage timed out; no automatic retry | originalState="+state+",elapsed="+originalElapsed+",timeout="+timeout);return;}
        if(!settings.equals(current)||s==null||!s.context().equals(plan.context())||!s.alive()||!s.loaded()||!s.inventoryValid()||!s.screenFree()){
            finish(issued?State.UNKNOWN_OUTCOME:State.CANCELLED,"World/profile/plot, menu, inventory or settings changed");return;}
        if(state==State.RETURN){
            Action lastAction=plan.actions().getLast();if(!toolPresentOrConsumed(lastAction,s)){finish(State.FAILED,"Exact cultivation tool moved during return");return;}
            if(!s.inventory().equals(baseline.inventory())||!s.plants().equals(baseline.plants())){finish(State.FAILED,"Protected state changed during physical return");return;}
            if(!travelStarted){travelStarted=true;if(!port.approach(savedPose)){finish(State.FAILED,"Saved physical pose is unreachable");return;}}
            if(port.arrived(savedPose)&&s.position().distanceSquared(savedPose)<.1225&&port.restoreSelectionAndView()){finish(State.COMPLETE,"All observed receipts and physical return proven");}return;
        }
        Action a=plan.actions().get(index);
        if(s.toolSlot()!=a.toolSlot()||(!s.toolIdentity().equals(a.toolIdentity())&&!(state==State.WAIT_ACK&&toolPresentOrConsumed(a,s)))){finish(issued?State.UNKNOWN_OUTCOME:State.CANCELLED,"Exact cultivation tool changed");return;}
        if(state==State.WAIT_ACK){
            if(unexpected(a,s)){finish(State.UNKNOWN_OUTCOME,"Unexpected protected plant/inventory change; inspect without replay");return;}
            if(s.revision()<=baseline.revision()||s.observedAt()<=baseline.observedAt()||s.observedAt()<=lastIssuedAt)return;
            if(s.knowledge(now,settings.freshnessMs())!=Knowledge.KNOWN)return;
            if(matches(a,s)){journalResolutions.add(cellKey(plan,a));unresolved.remove(cellKey(plan,a));acknowledged.add(key(plan,a));port.stopAction();baseline=s;issued=false;index++;
                transition(index==plan.actions().size()?State.RETURN:State.APPROACH,now,"Exact plant and inventory receipt observed");}
            else if(unexpected(a,s))finish(State.UNKNOWN_OUTCOME,"Unexpected protected plant/inventory change; inspect without replay");
            return;
        }
        if(s.knowledge(now,settings.freshnessMs())!=Knowledge.KNOWN||!s.inventory().equals(baseline.inventory())||!s.plants().equals(baseline.plants())||!a.before().equals(s.plants().get(a.cell()))){finish(State.CANCELLED,"Fresh exact planned cell and protected inventory changed");return;}
        if(state==State.APPROACH){if(!travelStarted){travelStarted=true;if(!port.approach(a.approach())){finish(State.FAILED,"Cultivation approach unreachable");return;}}
            if(port.arrived(a.approach())&&s.position().distanceSquared(a.approach())<.1225)transition(State.SELECT,now,"Physical approach observed");return;}
        if(state==State.SELECT){if(!port.select(a.toolSlot(),a.toolIdentity())){finish(State.FAILED,"Exact hotbar tool selection rejected");return;}transition(State.AIM,now,"Awaiting real selected tool and target ray");return;}
        if(state==State.AIM){if(s.selectedSlot()!=a.toolSlot())return;if(!port.aim(a))return;
            // Record BEFORE the transport call: a false/throw outcome may already have emitted a packet.
            issued=true;lastIssuedAt=now;unresolved.add(cellKey(plan,a));transition(State.WAIT_ACK,now,"One action issued; awaiting independent server receipt");
            try{if(!port.issue(a,s))finish(State.UNKNOWN_OUTCOME,"Native action outcome unknown; no retry");}catch(RuntimeException error){finish(State.UNKNOWN_OUTCOME,"Native action threw after issue; no retry");}
        }
    }
    private boolean matches(Action a,Snapshot s){
        Plant actual=s.plants().get(a.cell());Plant target=a.after();
        if(actual==null||!actual.identified()||!target.crop().equals(actual.crop())||target.state()!=actual.state()||target.water()!=actual.water()||target.growthStage()!=actual.growthStage()||!target.fingerprint().equals(actual.fingerprint())||!target.source().equals(actual.source()))return false;
        if(a.kind()==Kind.WATER&&(!actual.waterExpiry().known()||actual.waterExpiry().kind()!=ClockKind.SERVER_EPOCH||(a.before().waterExpiry().known()&&a.before().waterExpiry().kind()==ClockKind.SERVER_EPOCH&&actual.waterExpiry().value()<=a.before().waterExpiry().value())))return false;
        for(var e:baseline.plants().entrySet())if(!e.getKey().equals(a.cell())&&!e.getValue().equals(s.plants().get(e.getKey())))return false;
        if(!s.plants().keySet().equals(baseline.plants().keySet()))return false;
        return expectedInventory(a).equals(s.inventory());
    }
    private Map<String,Integer> expectedInventory(Action a){var m=new HashMap<>(baseline.inventory());for(var e:a.inventoryDelta().entrySet()){
        int value=Math.addExact(m.getOrDefault(e.getKey(),0),e.getValue());if(value<0)return Map.of("invalid-negative",-1);if(value==0)m.remove(e.getKey());else m.put(e.getKey(),value);}return m;}
    private boolean toolPresentOrConsumed(Action a,Snapshot s){
        return s.toolSlot()==a.toolSlot()&&(s.toolIdentity().equals(a.toolIdentity())
            ||(s.toolIdentity().isEmpty()&&a.inventoryDelta().getOrDefault(a.toolIdentity(),0)<0));
    }
    private boolean unexpected(Action a,Snapshot s){
        if(!s.plants().keySet().equals(baseline.plants().keySet()))return true;
        for(var e:baseline.plants().entrySet())if(!e.getKey().equals(a.cell())&&!e.getValue().equals(s.plants().get(e.getKey())))return true;
        for(var e:s.inventory().entrySet())if(!a.inventoryDelta().containsKey(e.getKey())&&!Objects.equals(baseline.inventory().get(e.getKey()),e.getValue()))return true;
        for(var e:baseline.inventory().entrySet())if(!a.inventoryDelta().containsKey(e.getKey())&&!Objects.equals(s.inventory().get(e.getKey()),e.getValue()))return true;
        return false;
    }
    private void transition(State next,long now,String why){state=next;stageAt=now;travelStarted=false;reason=why;}
    private static String cellKey(Plan p,Action a){return p.context()+"|"+a.cell();}
    public void restoreUnresolved(Collection<String> keys){if(active())throw new IllegalStateException("Active transaction");unresolved.addAll(keys);}
    /** A cell learned from another instance requires a new inspection after that import. */
    public void mergeSharedUnresolved(Collection<String> keys,long importedAt,long journalRevision){
        if(active()||importedAt<0)throw new IllegalStateException("Invalid shared journal import");
        if(keys.isEmpty())return;
        if(journalRevision<0||journalRevision==Long.MAX_VALUE)throw new IllegalStateException("Shared pending journal history unavailable");
        for(String key:keys){
            boolean newCell=unresolved.add(key);Long originalRevision=importedJournalRevision.get(key);
            if(newCell||originalRevision==null||originalRevision.longValue()!=journalRevision){importedInspectionFloor.put(key,importedAt);importedJournalRevision.put(key,journalRevision);}
        }
    }
    public boolean reconcileInspectedCell(Context context,Cell cell,Snapshot inspected,long now,long freshnessMs,String humanDecision){
        if(active()||inspected!=null&&inspected.observedAt()<=importedInspectionFloor.getOrDefault(context+"|"+cell,-1L)||!("applied".equals(humanDecision)||"not-applied".equals(humanDecision))||inspected==null||(lastIssuedAt>=0&&inspected.observedAt()<=lastIssuedAt)||inspected.knowledge(now,freshnessMs)!=Knowledge.KNOWN||!context.equals(inspected.context())||!inspected.screenFree()||inspected.plants().get(cell)==null||!inspected.plants().get(cell).identified())return false;
        boolean removed=unresolved.remove(context+"|"+cell);if(removed){journalResolutions.add(context+"|"+cell);importedInspectionFloor.remove(context+"|"+cell);importedJournalRevision.remove(context+"|"+cell);state=State.IDLE;reason="User reconciled refreshed native cell as "+humanDecision+"; select a fresh plan";}return removed;
    }
    private static String key(Plan p,Action a){return p.context()+"|"+a.cell()+"|"+a.id();}
    public void cancel(){if(active())finish(issued?State.UNKNOWN_OUTCOME:State.CANCELLED,"Manual stop; uncertain issued actions cannot replay");}
    private void finish(State next,String why){state=next;reason=why;port.stopAction();port.release();issued=false;}
    public boolean active(){return state==State.APPROACH||state==State.SELECT||state==State.AIM||state==State.WAIT_ACK||state==State.RETURN;}
    public State state(){return state;}
    public String status(){return state+": "+reason+" | acknowledged="+acknowledged.size()+" unresolved="+unresolved.size();}
    public Set<String> unresolvedActions(){return Set.copyOf(unresolved);}
    /** Only actual matching newer action receipts or explicit fresh inspected reconciliation mint these retirements. */
    public Set<String> takeJournalResolutions(){var actual=Set.copyOf(journalResolutions);journalResolutions.clear();return actual;}
    public Optional<Action> currentAction(){return plan!=null&&!plan.actions().isEmpty()?Optional.of(plan.actions().get(Math.min(index,plan.actions().size()-1))):Optional.empty();}
}
