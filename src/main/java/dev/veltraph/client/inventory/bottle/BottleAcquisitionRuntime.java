package dev.veltraph.client.inventory.bottle;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.Vec3;
import dev.veltraph.client.skills.SkillContracts;
import java.util.*;
import static dev.veltraph.client.inventory.bottle.BottleFillWorkflow.*;
import dev.veltraph.client.inventory.bottle.BottleFillWorkflow.Stack;

/** Source R57 parent. Each 64-stack purchase is verified by a different server-opened menu before another request. */
public final class BottleAcquisitionRuntime {
    public enum State { IDLE, HUB, WAIT_TRAVEL, PATH, NPC, WAIT_NPC, OPTION, WAIT_OPTION, BUY, WAIT_BUY, VERIFY_NPC, VERIFY_OPTION, VERIFY_BUY, CLOSE, ISLAND, FILL, RETURN, COMPLETE, FAILED, CANCELLED, UNKNOWN }
    public enum Progress { WAITING, COMPLETE, FAILED }
    public enum Admission { WAIT, READY, STOP }
    public record Landing(String command,GameArea area,String dimension,Vec3 position) {
        public Landing {if(command==null||!command.matches("[a-zA-Z0-9_:-]+(?: [a-zA-Z0-9_:.,-]+)*")||area!=GameArea.HUB&&area!=GameArea.ISLAND||dimension==null||dimension.isBlank()||!finite(position))throw new IllegalArgumentException("Explicit bounded native command and landing required");}
    }
    public record Profile(Identity glass,Landing hub,Landing island,List<Position> path,Position npcAnchor,Position npcStand,String npcName,String npcType,
                          String npcTitle,Identity option,String buyTitle,Identity offer,int reserveThreshold,int cycles,long maxStackCost,long maxTotalCost,int delayTicks,int timeoutTicks,int totalTicks) {
        public Profile {path=List.copyOf(path);if(glass==null||!glass.id().equals("minecraft:glass_bottle")||glass.name().isBlank()||glass.components().isBlank()||hub.area()!=GameArea.HUB||island.area()!=GameArea.ISLAND||path.size()!=3||npcAnchor==null||npcStand==null||npcName==null||npcName.isBlank()||!"minecraft:witch".equals(npcType)||npcTitle==null||npcTitle.isBlank()||buyTitle==null||buyTitle.isBlank()||npcTitle.equals(buyTitle)||option==null||offer==null||option.name().isBlank()||offer.name().isBlank()||option.components().isBlank()||offer.components().isBlank()||reserveThreshold<4||reserveThreshold>6||cycles<1||cycles>8||maxStackCost<1||maxTotalCost<1||delayTicks<1||timeoutTicks<=delayTicks||timeoutTicks>2400||totalTicks<timeoutTicks||totalTicks>200000)throw new IllegalArgumentException("Complete explicit bottle parent profile and bounded threshold/cycles/budgets/timers required");}
        public int stacks(){return reserveThreshold+1;}
    }
    public record Slot(int id,boolean player,Stack stack,List<String> lore) {public Slot{lore=List.copyOf(lore);}}
    public record Observation(Object player,Object world,Object connection,UUID uuid,String dimension,GameArea area,Vec3 position,float yaw,float pitch,boolean valid,boolean supported,Object menu,String title,int menuId,int revision,Stack cursor,int selected,List<Stack> inventory,List<Slot> rows,long teleportSerial,Vec3 teleportPosition,long nowMillis) {
        public Observation {inventory=List.copyOf(inventory);rows=List.copyOf(rows);}
        public boolean cursorEmpty(){return cursor.empty();}
        public boolean closed(){return menu==null&&cursorEmpty();}
    }
    public record Travel(long sequence,Object player,Object world,Object connection,UUID uuid,Landing landing,long serial,long issuedMillis,long expiresMillis,List<Stack> inventory) {public Travel{inventory=List.copyOf(inventory);}}
    public record Purchase(int cycle,int stack,long cost,List<Stack> before,List<Stack> after,Object verificationMenu,Object requestMenu,int requestMenuId,int requestRevision) {}
    public interface Port {
        Observation read();boolean command(Observation expected,Landing landing);Progress walk(Observation expected,Position stand);
        Progress npc(Observation expected,Profile profile,boolean issue);boolean click(Observation expected,Slot slot);boolean close(Observation expected);void release();
        boolean beginFill(int index,long tick);Progress fill(long tick);int filled();boolean returnPose(Observation expected,Vec3 position,float yaw,float pitch);void cancelFill();
    }
    private final Port port;private Profile profile;private State state=State.IDLE,afterTravel;private String reason="Idle";private Object player,world,connection,ownedMenu,retiredMenu;private UUID uuid;
    private List<Stack> ledger=List.of(),beforeBuy=List.of(),initialLedger=List.of();private final List<Purchase> receipts=new ArrayList<>();private final Set<Object> retiredMenus=Collections.newSetFromMap(new IdentityHashMap<>());private Vec3 returnPosition;private float returnYaw,returnPitch;private int originalSelected,cycle,acquired,waypoint,fillIndex=-1,filled;private Object purchaseMenu;private int purchaseMenuId,purchaseRevision;private long next,deadline,last,totalDeadline,quoted,spent,sequence,lastTravelMillis;private Travel travel;private boolean uncertain,cancelled;
    public BottleAcquisitionRuntime(Port port){this.port=Objects.requireNonNull(port);}
    public State state(){return state;}public String reason(){return reason;}public int acquired(){return acquired;}public int filled(){return filled;}public long spent(){return spent;}public List<Purchase> receipts(){return List.copyOf(receipts);}public Optional<Travel> pendingTravel(){return Optional.ofNullable(travel);}public Profile profile(){return profile;}
    public boolean active(){return switch(state){case IDLE,COMPLETE,FAILED,CANCELLED,UNKNOWN->false;default->true;};}
    public boolean start(Profile profile,long tick){if(active()||state==State.UNKNOWN)throw new IllegalStateException("Owned or uncertain bottle parent cannot restart");this.profile=profile;var o=port.read();receipts.clear();retiredMenus.clear();spent=0;acquired=filled=cycle=waypoint=0;uncertain=cancelled=false;travel=null;ownedMenu=retiredMenu=null;last=tick;totalDeadline=tick+profile.totalTicks();
        if(!valid(o)||!o.closed()||o.area()!=GameArea.ISLAND||!o.dimension().equals(profile.island().dimension())||!o.supported()||o.inventory().stream().anyMatch(s->profile.glass().matches(s))||o.inventory().stream().filter(Stack::empty).count()<profile.stacks()+2||o.inventory().subList(0,9).stream().noneMatch(Stack::empty))return stop(State.FAILED,"Closed supported Island, no matching bottles and protected output/hotbar reserve required");
        player=o.player();world=o.world();connection=o.connection();uuid=o.uuid();ledger=initialLedger=o.inventory();originalSelected=o.selected();returnPosition=o.position();returnYaw=o.yaw();returnPitch=o.pitch();step(State.HUB,tick);port.release();return true;}
    private static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z());}
    private static boolean valid(Observation o){return o!=null&&o.valid()&&o.player()!=null&&o.world()!=null&&o.connection()!=null&&o.uuid()!=null&&finite(o.position())&&Float.isFinite(o.yaw())&&Float.isFinite(o.pitch())&&o.inventory().size()==36&&o.selected()>=0&&o.selected()<9;}
    private boolean owner(Observation o){return valid(o)&&o.player()==player&&o.world()==world&&o.connection()==connection&&o.uuid().equals(uuid);}
    private boolean conserved(Observation o){return o.cursorEmpty()&&o.selected()==originalSelected&&o.inventory().equals(ledger);}
    private void step(State nextState,long tick){state=nextState;next=tick+profile.delayTicks();deadline=tick+profile.timeoutTicks();reason=nextState.name();}
    private boolean stop(State terminal,String why){state=terminal;reason=why;travel=null;port.cancelFill();port.release();return false;}
    /** Explicit operator reconciliation: exact original protected ledger restored; no unknown purchase is credited or retried. */
    public boolean acknowledgeManualRecovery(){var o=port.read();if(state!=State.UNKNOWN||!valid(o)||o.connection()!=connection||!o.uuid().equals(uuid)||!o.closed()||o.selected()!=originalSelected||!o.inventory().equals(initialLedger))return false;stop(State.FAILED,"Operator restored original protected ledger; prior unknown requests remain uncredited");return true;}
    public void cancel(){if(!active())return;cancelled=true;stop(uncertain?State.UNKNOWN:State.CANCELLED,"Stopped; no later purchase, fill or travel is authorized");}
    public void tick(long tick){if(!active())return;var o=port.read();if(tick<last||tick>totalDeadline||tick>deadline){stop(uncertain?State.UNKNOWN:State.FAILED,"Bottle deadline expired before receipt; no retry");return;}last=tick;
        if(state==State.WAIT_TRAVEL){pollTravel(o,tick);return;}
        boolean predictedCursor=ownedPrediction(o);
        if(!owner(o)||!o.cursorEmpty()&&!predictedCursor){stop(uncertain?State.UNKNOWN:State.FAILED,"Bottle native context/cursor changed; preserve new state");return;}
        var expectedLanding=state==State.HUB||state==State.FILL||state==State.RETURN?profile.island():profile.hub();
        if(o.area()!=expectedLanding.area()||!o.dimension().equals(expectedLanding.dimension())){stop(uncertain?State.UNKNOWN:State.FAILED,"Observed bottle phase area/dimension changed outside issued travel");return;}
        if(tick<next||predictedCursor)return;
        if(state!=State.FILL&&state!=State.WAIT_BUY&&state!=State.VERIFY_NPC&&state!=State.VERIFY_OPTION&&state!=State.VERIFY_BUY&&!conserved(o)){stop(uncertain?State.UNKNOWN:State.FAILED,"Protected inventory or selection changed");return;}
        switch(state){
            case HUB->travel(o,profile.hub(),State.PATH,tick);
            case ISLAND->travel(o,profile.island(),State.FILL,tick);
            case PATH->{if(!o.closed()){stop(State.FAILED,"Foreign screen during Hub route");return;}var result=port.walk(o,profile.path().get(waypoint));if(result==Progress.FAILED)stop(State.FAILED,"Source route blocked");else if(result==Progress.COMPLETE){if(++waypoint==profile.path().size())step(State.NPC,tick);}}
            case NPC->{if(!o.closed()){stop(State.FAILED,"Unrequested NPC menu");return;}var result=port.npc(o,profile,true);if(result==Progress.FAILED)stop(State.FAILED,"Exact Witch approach or interaction refused");else if(result==Progress.COMPLETE)step(State.WAIT_NPC,tick);}
            case WAIT_NPC->{if(o.menu()!=null){if(!menu(o,profile.npcTitle(),null)||!conserved(o)){stop(State.FAILED,"Foreign NPC response");return;}ownedMenu=o.menu();step(State.OPTION,tick);}}
            case OPTION->option(o,tick,false);
            case WAIT_OPTION->{if(o.menu()!=null&&o.menu()!=retiredMenu){if(!menu(o,profile.buyTitle(),null)){stop(State.FAILED,"Unexpected trading response");return;}ownedMenu=o.menu();step(State.BUY,tick);}else if(o.menu()!=null&&!menu(o,profile.npcTitle(),retiredMenu))stop(State.FAILED,"NPC menu replaced while waiting for options");}
            case BUY->{if(!menu(o,profile.buyTitle(),ownedMenu)||!conserved(o)){stop(State.FAILED,"Trading owner changed");return;}var offers=matches(o,profile.offer(),64);if(offers.size()!=1){stop(State.FAILED,"One exact64 quoted bottle offer required");return;}var quote=SkillContracts.quotedCost(offers.getFirst().lore(),64);if(quote.isEmpty()||quote.getAsLong()>profile.maxStackCost()||quote.getAsLong()>profile.maxTotalCost()-spent){stop(State.FAILED,"Unknown or over-budget bottle quote");return;}quoted=quote.getAsLong();beforeBuy=ledger;purchaseMenu=o.menu();purchaseMenuId=o.menuId();purchaseRevision=o.revision();if(!port.click(o,offers.getFirst())){stop(State.FAILED,"Native64 bottle request refused");return;}uncertain=true;step(State.WAIT_BUY,tick);}
            case WAIT_BUY->{if(!menu(o,profile.buyTitle(),ownedMenu)){stop(State.UNKNOWN,"Purchase menu replaced; no uncertain retry");return;}if(!port.close(o)){stop(State.UNKNOWN,"Owned purchase verification close refused");return;}retiredMenu=ownedMenu;retiredMenus.add(ownedMenu);ownedMenu=null;step(State.VERIFY_NPC,tick);}
            case VERIFY_NPC->{if(o.menu()!=null){if(retiredMenus.contains(o.menu())||!menu(o,profile.npcTitle(),null)){stop(State.UNKNOWN,"Foreign verification menu");return;}ownedMenu=o.menu();step(State.VERIFY_OPTION,tick);return;}var result=port.npc(o,profile,true);if(result==Progress.FAILED)stop(State.UNKNOWN,"Purchase verification NPC unavailable");else if(result==Progress.COMPLETE)step(State.VERIFY_OPTION,tick);}
            case VERIFY_OPTION->{if(o.menu()==null)return;if(ownedMenu==null)ownedMenu=o.menu();option(o,tick,true);}
            case VERIFY_BUY->{if(o.menu()==null||o.menu()==retiredMenu)return;if(retiredMenus.contains(o.menu())||!menu(o,profile.buyTitle(),null)){stop(State.UNKNOWN,"Foreign purchase receipt menu");return;}ownedMenu=o.menu();int index=acquiredDelta(beforeBuy,o.inventory(),profile.glass());if(index<0){stop(State.UNKNOWN,"Fresh server contents reject exact independent64 purchase delta");return;}ledger=o.inventory();spent=Math.addExact(spent,quoted);receipts.add(new Purchase(cycle,acquired,quoted,beforeBuy,ledger,o.menu(),purchaseMenu,purchaseMenuId,purchaseRevision));acquired++;uncertain=false;step(acquired<profile.stacks()?State.BUY:State.CLOSE,tick);}
            case CLOSE->{if(!menu(o,profile.buyTitle(),ownedMenu)||!port.close(o)){stop(State.FAILED,"Owned trading close refused");return;}ownedMenu=null;step(State.ISLAND,tick);}
            case FILL->fill(o,tick);
            case RETURN->{if(!o.closed()||!conserved(o)){stop(State.FAILED,"Return context/inventory changed");return;}if(port.returnPose(o,returnPosition,returnYaw,returnPitch))stop(State.COMPLETE,"Every64 purchase and water/chest receipt accepted; Island caller position/view/selection returned");}
            default->{}
        }
    }
    private boolean ownedPrediction(Observation o){if(o.cursorEmpty()||o.cursor().tool())return false;if(state==State.WAIT_BUY)return o.menu()==ownedMenu&&o.title().equals(profile.buyTitle())&&profile.offer().matches(o.cursor())&&o.cursor().count()==64;if(state==State.WAIT_OPTION||state==State.VERIFY_BUY)return o.menu()==retiredMenu&&o.title().equals(profile.npcTitle())&&profile.option().matches(o.cursor())&&o.cursor().count()==1;return false;}
    private void option(Observation o,long tick,boolean verify){if(!menu(o,profile.npcTitle(),ownedMenu)){stop(verify?State.UNKNOWN:State.FAILED,"NPC option owner changed");return;}var options=matches(o,profile.option(),1);if(options.size()!=1||!port.click(o,options.getFirst())){stop(verify?State.UNKNOWN:State.FAILED,"Exact native trading option refused");return;}retiredMenu=ownedMenu;retiredMenus.add(ownedMenu);ownedMenu=null;step(verify?State.VERIFY_BUY:State.WAIT_OPTION,tick);}
    private static boolean menu(Observation o,String title,Object identity){return o.menu()!=null&&o.title().equals(title)&&o.revision()>0&&o.cursorEmpty()&&(identity==null||o.menu()==identity);}
    private static List<Slot> matches(Observation o,Identity identity,int count){return o.rows().stream().filter(s->!s.player()&&identity.matches(s.stack())&&s.stack().count()==count&&!s.stack().tool()).toList();}
    public static int acquiredDelta(List<Stack> before,List<Stack> after,Identity glass){if(before.size()!=36||after.size()!=36)return -1;int index=-1;for(int i=0;i<36;i++){if(before.get(i).equals(after.get(i)))continue;if(index>=0||!before.get(i).empty()||!glass.matches(after.get(i))||after.get(i).count()!=64||after.get(i).tool())return -1;index=i;}return index;}
    private void travel(Observation o,Landing target,State after,long tick){if(!o.closed()||!conserved(o)){stop(State.FAILED,"Closed conserved travel owner required");return;}travel=new Travel(++sequence,player,world,connection,uuid,target,o.teleportSerial(),o.nowMillis(),o.nowMillis()+profile.timeoutTicks()*50L,ledger);if(!port.command(o,target)){stop(State.FAILED,"Explicit bottle travel command refused");return;}afterTravel=after;lastTravelMillis=o.nowMillis();step(State.WAIT_TRAVEL,tick);port.release();}
    private boolean travelClockValid(Travel expected,long tick,long nowMillis){return state==State.WAIT_TRAVEL&&expected!=null&&expected==travel
            &&tick>=last&&tick<=deadline&&tick<=totalDeadline&&nowMillis>=expected.issuedMillis()
            &&nowMillis>=lastTravelMillis&&nowMillis<=expected.expiresMillis();}
    /** Exact issued ticket, original clocks only. Core already proved the same-connection loading/unknown scope.
     * This does not read a port, adopt native context, reset a deadline, or dispatch gameplay. */
    public Progress waitIssuedTravel(Travel expected,long tick,long nowMillis){
        if(state!=State.WAIT_TRAVEL)return Progress.FAILED;
        if(!travelClockValid(expected,tick,nowMillis)){stop(State.FAILED,"Issued bottle wait ticket or original clock expired/reversed");return Progress.FAILED;}
        last=tick;lastTravelMillis=nowMillis;return Progress.WAITING;
    }
    /** Read-only admission. A correct fresh native packet waits for area/support without forgiving wrong evidence. */
    public Admission inspectTravel(Travel expected,Observation o,long tick){
        if(o==null||!travelClockValid(expected,tick,o.nowMillis())||o.connection()!=expected.connection()
                ||o.uuid()!=null&&!o.uuid().equals(expected.uuid())||!o.cursorEmpty()||o.menu()!=null)return Admission.STOP;
        if(valid(o)&&(!o.inventory().equals(expected.inventory())||o.selected()!=originalSelected))return Admission.STOP;
        if(o.teleportSerial()<=expected.serial())return Admission.WAIT;
        if(!valid(o)||!finite(o.teleportPosition())||o.teleportPosition().distanceTo(expected.landing().position())>.9
                ||o.position().distanceTo(expected.landing().position())>.9||!o.dimension().equals(expected.landing().dimension())
                ||!o.inventory().equals(expected.inventory())||o.selected()!=originalSelected)return Admission.STOP;
        if(o.area()!=expected.landing().area()&&o.area()!=GameArea.UNKNOWN)return Admission.STOP;
        return o.area()==GameArea.UNKNOWN||!o.supported()?Admission.WAIT:Admission.READY;
    }
    public Progress pollTravel(Observation o,long tick){
        if(state!=State.WAIT_TRAVEL||travel==null)return Progress.FAILED;
        var expected=travel;var admission=inspectTravel(expected,o,tick);
        if(admission==Admission.STOP){stop(State.FAILED,"Fresh travel ticket/clock/context or landing/protected inventory rejected");return Progress.FAILED;}
        if(waitIssuedTravel(expected,tick,o.nowMillis())==Progress.FAILED)return Progress.FAILED;
        if(admission==Admission.WAIT)return Progress.WAITING;
        player=o.player();world=o.world();connection=o.connection();travel=null;waypoint=0;fillIndex=-1;step(afterTravel,tick);return Progress.COMPLETE;
    }
    private void fill(Observation o,long tick){if(fillIndex<0){if(!conserved(o)||!o.closed()){stop(State.FAILED,"Island handoff inventory changed");return;}for(int i=0;i<36;i++)if(profile.glass().matches(ledger.get(i))&&ledger.get(i).count()==64){fillIndex=i;break;}if(fillIndex<0){if(++cycle<profile.cycles()){acquired=0;step(State.HUB,tick);}else step(State.RETURN,tick);return;}if(!port.beginFill(fillIndex,tick)){stop(State.FAILED,"Exact acquired64 stack filler refused");return;}uncertain=true;deadline=totalDeadline;}
        var result=port.fill(tick);if(result==Progress.FAILED){stop(State.UNKNOWN,"Acquired stack conversion/storage receipt unavailable");return;}if(result==Progress.COMPLETE){var fresh=port.read();var expected=new ArrayList<>(ledger);expected.set(fillIndex,Stack.EMPTY);if(port.filled()!=64||!fresh.closed()||fresh.selected()!=originalSelected||!fresh.inventory().equals(expected)){stop(State.UNKNOWN,"Independent complete64 refill receipt/protected inventory failed");return;}ledger=List.copyOf(expected);filled+=64;fillIndex=-1;uncertain=false;next=tick+profile.delayTicks();deadline=totalDeadline;}}
}
