package dev.veltraph.client.inventory.equipment;

import dev.veltraph.client.gui.MenuSnapshot;
import java.util.*;

/** Local finite workflow, adapted from the recovered compactor, pet and wardrobe menu controllers. */
public final class EquipmentWorkflow {
    public enum Kind { COMPACTOR, PERSONAL_DELETOR, PET, WARDROBE }
    public enum State { IDLE, MOVE_DEVICE, WAIT_MOVE, OPEN_MENU, WAIT_MENU, CHOOSE, WAIT_PAGE, WAIT_ACK,
        CLOSE_MENU, RESTORE_DEVICE, WAIT_RESTORE, COMPLETE, FAILED, CANCELLED }
    public record Item(String id,String name,int count,String components) {
        public boolean empty(){return count==0;}
    }
    public record Request(Kind kind,String menuTitle,String actionLabel,String actionItemId,String targetItemId,String nextPageItemId,String sourceMarker,String targetMarker,
                          String command,String deviceId,String deviceName,int wardrobeSlot,List<String> armorIds,List<String> armorNames,
                          String serverAck,String nextPageLabel,int delayTicks,int timeoutTicks,int maxPages) {
        public Request {
            Objects.requireNonNull(kind);armorIds=List.copyOf(armorIds);armorNames=List.copyOf(armorNames);
            if(menuTitle.isBlank()||actionLabel.isBlank()||actionItemId.isBlank()||targetItemId.isBlank()||sourceMarker.isBlank()||targetMarker.isBlank()
                    ||sourceMarker.equals(targetMarker)||delayTicks<1||timeoutTicks<=delayTicks||maxPages<1||maxPages>20)
                throw new IllegalArgumentException("Exact menu/button/state labels and valid timing are required");
            if(kind==Kind.COMPACTOR||kind==Kind.PERSONAL_DELETOR){
                if(deviceId.isBlank()||deviceName.isBlank())throw new IllegalArgumentException("Exact device registry ID and name required");
            }else if(command.isBlank()||!command.matches("/?[a-zA-Z0-9 _:-]+"))throw new IllegalArgumentException("Explicit local menu command required");
            if(kind==Kind.PET&&maxPages>1&&(nextPageLabel.isBlank()||nextPageItemId.isBlank()))throw new IllegalArgumentException("Explicit next-page button label required");
            if(kind==Kind.PET&&serverAck.isBlank())throw new IllegalArgumentException("Exact summoned-pet server acknowledgment required");
            if(kind==Kind.WARDROBE&&(wardrobeSlot<1||wardrobeSlot>18||armorIds.size()!=4||armorNames.size()!=4||armorIds.stream().anyMatch(String::isBlank)||armorNames.stream().anyMatch(String::isBlank)))
                throw new IllegalArgumentException("Wardrobe slot and expected head/chest/legs/feet item IDs and names required");
        }
        public boolean device(){return kind==Kind.COMPACTOR||kind==Kind.PERSONAL_DELETOR;}
        public boolean titleAllowed(String title){
            return title.equals(menuTitle)||(kind==Kind.PET&&title.matches(java.util.regex.Pattern.quote(menuTitle)+" \\([0-9]+/[0-9]+\\)"));
        }
    }
    public record Observation(Object player,Object world,Object menu,MenuSnapshot snapshot,List<Item> inventory,
                              List<Item> armor,int selectedSlot,boolean cursorEmpty,boolean ownPrediction,
                              boolean serverRevision,boolean correctionTickPassed) {
        public Observation {inventory=List.copyOf(inventory);armor=List.copyOf(armor);}
    }
    public interface Port {
        Observation read();
        boolean swap(Observation expected,int inventorySlot,int hotbarSlot);
        boolean select(Observation expected,int hotbarSlot);
        boolean use(Observation expected);
        boolean command(Observation expected,String command);
        boolean click(Observation expected,int slot);
        boolean close(Observation expected);
    }
    private final Port port;
    private Request request;private State state=State.IDLE;private String reason="Idle";
    private Object player,world,ownedMenu;private int originalSelected,originalDevice=-1,hotbar=-1,page=1;
    private Item device;private List<Item> beforeSwap;private long deadline,nextAction;private boolean clicked,messageAck;private State cleanupGoal=State.COMPLETE;private String cleanupReason="";private long lastTick;
    public EquipmentWorkflow(Port port){this.port=Objects.requireNonNull(port);}
    public State state(){return state;}public String reason(){return reason;}public boolean terminal(){return state==State.COMPLETE||state==State.FAILED||state==State.CANCELLED;}
    public boolean active(){return state!=State.IDLE&&!terminal();}
    public Request request(){return request;}
    public void start(Request request,long tick){
        if(active())throw new IllegalStateException("Workflow already active");
        this.request=Objects.requireNonNull(request);cleanupGoal=State.COMPLETE;lastTick=tick;state=State.IDLE;reason="Starting";clicked=false;messageAck=false;
        ownedMenu=null;originalDevice=-1;hotbar=-1;page=1;beforeSwap=null;
        Observation o=port.read();
        if(o.player()==null||o.world()==null||!o.cursorEmpty()||o.inventory().size()!=36){fail("Living player, empty cursor and complete inventory required");return;}
        if(o.snapshot()!=null){fail("Close unrelated menu before equipment takeover");return;}
        player=o.player();world=o.world();originalSelected=o.selectedSlot();deadline=tick+request.timeoutTicks();nextAction=tick;
        if(request.device()){
            List<Integer> matches=new ArrayList<>();for(int i=0;i<36;i++)if(!o.inventory().get(i).empty()&&o.inventory().get(i).id().equals(request.deviceId())&&o.inventory().get(i).name().equals(request.deviceName()))matches.add(i);
            if(matches.size()!=1||o.inventory().get(matches.getFirst()).count()!=1){fail("One exact single device is required; duplicate/count ambiguity refused");return;}
            originalDevice=matches.getFirst();device=o.inventory().get(originalDevice);
            if(originalDevice<9){hotbar=originalDevice;state=State.OPEN_MENU;}
            else{for(int i=0;i<9;i++)if(o.inventory().get(i).empty()){hotbar=i;break;}
                if(hotbar<0){fail("A free hotbar slot is required; tool replacement is refused");return;}state=State.MOVE_DEVICE;}
        }else state=State.OPEN_MENU;
    }
    public void onServerMessage(String line){if(state==State.WAIT_ACK&&clicked&&request.kind()==Kind.PET&&MenuSnapshot.clean(line).equals(request.serverAck()))messageAck=true;}
    public void tick(long tick){
        if(!active())return;lastTick=tick;Observation o=port.read();
        if(o.player()!=player||o.world()!=world){fail("Player/world changed; no stale restoration attempted");return;}
        if(o.inventory().size()!=36){fail("Complete inventory observation unavailable");return;}
        if(request.device()&&cleanupGoal==State.COMPLETE&&(state==State.WAIT_MENU||state==State.CHOOSE)
                &&!o.inventory().get(hotbar).equals(device)){fail("Device identity/count changed before menu action");cleanup();return;}
        if(!o.cursorEmpty()) {if(o.ownPrediction()){if(tick>deadline)fail("Server did not clear owned click prediction");return;}fail("Foreign cursor contents; no item move or close attempted");return;}
        if(tick>deadline){if(cleanupGoal!=State.COMPLETE){fail("Equipment restoration acknowledgment timed out: "+cleanupReason);return;}fail("Equipment acknowledgment timed out");cleanup();return;}
        if(ownedMenu!=null&&o.menu()!=ownedMenu&&state!=State.CLOSE_MENU&&state!=State.RESTORE_DEVICE&&state!=State.WAIT_RESTORE&&state!=State.WAIT_PAGE){fail("Owned menu was replaced/closed without acknowledgment");cleanup();return;}
        if(tick<nextAction)return;
        switch(state){
            case MOVE_DEVICE -> {
                beforeSwap=o.inventory();if(!port.swap(o,originalDevice,hotbar)){fail("Exact device move refused");return;}waitState(State.WAIT_MOVE,tick,"Waiting for vanilla correction tick before device use");
            }
            case WAIT_MOVE -> {
                if(o.correctionTickPassed()&&inventorySwapped(o.inventory(),beforeSwap,originalDevice,hotbar)){state=cleanupGoal==State.COMPLETE?State.OPEN_MENU:State.CLOSE_MENU;nextAction=tick+request.delayTicks();}
                else if(!o.inventory().equals(beforeSwap)&&!inventorySwapped(o.inventory(),beforeSwap,originalDevice,hotbar)){fail("Inventory changed unexpectedly during device move");cleanup();}
            }
            case OPEN_MENU -> {
                boolean ok;if(request.device()){
                    if(!o.inventory().get(hotbar).equals(device)){fail("Device identity/count changed before use");cleanup();return;}
                    ok=port.select(o,hotbar)&&port.use(port.read());
                }else ok=port.command(o,request.command());
                if(!ok){fail("Native item/menu open refused");cleanup();return;}waitState(State.WAIT_MENU,tick,"Waiting for exact menu");
            }
            case WAIT_MENU -> {
                if(o.snapshot()!=null){if(!request.titleAllowed(o.snapshot().title())){fail("Unexpected menu title");cleanup();return;}ownedMenu=o.menu();page=pageNumber(o.snapshot().title());state=cleanupGoal==State.COMPLETE?State.CHOOSE:State.CLOSE_MENU;nextAction=tick+request.delayTicks();}
            }
            case CHOOSE -> choose(o,tick);
            case WAIT_PAGE -> {
                if(o.snapshot()!=null&&request.titleAllowed(o.snapshot().title())&&pageNumber(o.snapshot().title())==page+1&&o.serverRevision()){
                    page++;ownedMenu=o.menu();state=cleanupGoal==State.COMPLETE?State.CHOOSE:State.CLOSE_MENU;nextAction=tick+request.delayTicks();
                }
            }
            case WAIT_ACK -> {
                if(o.snapshot()==null||!request.titleAllowed(o.snapshot().title())){fail("Menu disappeared before state acknowledgment");cleanup();return;}
                var targets=targets(o.snapshot());
                if(targets.size()==1&&marker(targets.getFirst(),request.targetMarker())&&!marker(targets.getFirst(),request.sourceMarker())&&targets.getFirst().itemId().equals(request.targetItemId())&&o.serverRevision()
                        &&(request.kind()!=Kind.PET||messageAck)&&(request.kind()!=Kind.WARDROBE||armorMatches(o))){
                    state=State.CLOSE_MENU;nextAction=tick+request.delayTicks();reason="Server state acknowledged";
                }
            }
            case CLOSE_MENU -> {
                if(o.snapshot()!=null){if(o.menu()!=ownedMenu||!request.titleAllowed(o.snapshot().title())||!port.close(o)){fail("Owned menu close refused");return;}return;}
                state=State.RESTORE_DEVICE;nextAction=tick+request.delayTicks();
            }
            case RESTORE_DEVICE -> {
                if(originalDevice>=9&&o.inventory().get(originalDevice).empty()&&o.inventory().get(hotbar).equals(device)){
                    beforeSwap=o.inventory();if(!port.swap(o,originalDevice,hotbar)){fail("Device restoration refused");return;}waitState(State.WAIT_RESTORE,tick,"Restoring original device placement");
                }else if(originalDevice>=9&&!o.inventory().get(originalDevice).equals(device)){fail("Device moved/changed; restoration cannot overwrite inventory");}
                else finish(o);
            }
            case WAIT_RESTORE -> {if(o.correctionTickPassed()&&inventorySwapped(o.inventory(),beforeSwap,originalDevice,hotbar))finish(o);else if(!o.inventory().equals(beforeSwap)&&!inventorySwapped(o.inventory(),beforeSwap,originalDevice,hotbar))fail("Unexpected inventory change during restoration");}
            default -> {}
        }
    }
    private void choose(Observation o,long tick){
        if(o.snapshot()==null||!request.titleAllowed(o.snapshot().title())){fail("Exact equipment menu required");cleanup();return;}
        List<MenuSnapshot.Slot> targets=targets(o.snapshot());
        if(targets.isEmpty()&&request.kind()==Kind.PET&&page<request.maxPages()){
            var next=o.snapshot().matching(request.nextPageLabel()).stream().filter(s->!s.locked()&&s.count()==1&&s.itemId().equals(request.nextPageItemId())).toList();
            if(next.size()!=1||!port.click(o,next.getFirst().id())){fail("Pet missing or unambiguous next page unavailable");cleanup();return;}
            waitState(State.WAIT_PAGE,tick,"Waiting for next pet page");return;
        }
        if(targets.size()!=1){fail("Equipment button identity/count ambiguous or missing");cleanup();return;}
        var target=targets.getFirst();boolean wanted=marker(target,request.targetMarker()),source=marker(target,request.sourceMarker());
        if(wanted&&!source&&target.itemId().equals(request.targetItemId())){
            if(request.kind()==Kind.WARDROBE&&!armorMatches(o)){fail("Equipped marker does not match actual armor");cleanup();return;}
            state=State.CLOSE_MENU;reason="Already in requested state";return;
        }
        if(!source||wanted||!target.itemId().equals(request.actionItemId())||!port.click(o,target.id())){fail("Unknown/contradictory menu state or guarded click refused");cleanup();return;}
        clicked=true;messageAck=false;waitState(State.WAIT_ACK,tick,"Waiting for server equipment outcome");
    }
    private List<MenuSnapshot.Slot> targets(MenuSnapshot menu){
        return menu.slots().stream().filter(s->!s.playerInventory()&&s.count()==1&&
                (request.kind()==Kind.WARDROBE?s.name().matches("Slot "+request.wardrobeSlot()+":.*"):s.name().equals(request.actionLabel()))).toList();
    }
    private boolean armorMatches(Observation observation){
        if(observation.armor().size()!=4)return false;
        for(int i=0;i<4;i++){var piece=observation.armor().get(i);if(piece.count()!=1||!piece.id().equals(request.armorIds().get(i))||!piece.name().equals(request.armorNames().get(i)))return false;}
        return true;
    }
    private static boolean marker(MenuSnapshot.Slot slot,String marker){return slot.name().contains(marker)||slot.lore().stream().anyMatch(line->line.equals(marker));}
    private static int pageNumber(String title){var m=java.util.regex.Pattern.compile("\\(([0-9]+)/([0-9]+)\\)$").matcher(title);return m.find()?Integer.parseInt(m.group(1)):1;}
    private static boolean inventorySwapped(List<Item> current,List<Item> before,int a,int b){var expected=new ArrayList<>(before);Collections.swap(expected,a,b);return current.equals(expected);}
    private void waitState(State next,long tick,String why){state=next;reason=why;nextAction=tick+request.delayTicks();}
    private void finish(Observation o){if(!port.select(o,originalSelected)){fail("Original selected slot restoration refused");return;}state=cleanupGoal;reason=cleanupGoal==State.COMPLETE?"Equipment state acknowledged and inventory restored":cleanupReason+"; original inventory/selection restored";}
    private void fail(String why){state=State.FAILED;reason=why;}
    public void cancel(){if(active()){
        if(state==State.WAIT_MOVE||state==State.WAIT_MENU||state==State.WAIT_PAGE){
            cleanupGoal=State.CANCELLED;cleanupReason="Cancelled";reason="Waiting for issued native action before restoration";
            deadline=lastTick+request.timeoutTicks();return;
        }
        state=State.CANCELLED;reason="Cancelled";cleanup();
    }}
    /** Schedules acknowledged restoration. Root may continue ticking after module disable. */
    private void cleanup(){
        if(player==null||world==null)return;Observation o=port.read();
        if(o.player()!=player||o.world()!=world||(!o.cursorEmpty()&&!o.ownPrediction())){reason+="; restoration skipped after context/cursor ownership changed";return;}
        if(o.snapshot()!=null&&(o.menu()!=ownedMenu||request==null||!request.titleAllowed(o.snapshot().title()))){reason+="; restoration skipped after menu ownership changed";return;}
        cleanupGoal=state==State.CANCELLED?State.CANCELLED:State.FAILED;cleanupReason=reason;
        state=State.CLOSE_MENU;deadline=lastTick+request.timeoutTicks();nextAction=lastTick;
    }
}
