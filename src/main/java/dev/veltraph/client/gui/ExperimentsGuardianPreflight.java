package dev.veltraph.client.gui;

import dev.veltraph.client.inventory.equipment.EquipmentWorkflow;
import java.util.*;

/** Guardian pet transaction inside the Experiments owner. Native port is shared with equipment,
 * but this source-shaped flow uses active lore receipts rather than inventing a chat ACK. */
public final class ExperimentsGuardianPreflight {
    public enum State { IDLE, CHECKING, OPEN_PET_MENU, WAIT_MENU, SWITCHING_TO_PET, GO_NEXT_PAGE,
        WAIT_PAGE, WAIT_PET, CLOSING_MENU, WAIT_CLOSE, READY, FAILED, CANCELLED }
    private final EquipmentWorkflow.Port port;
    private ExperimentsGuardianSettings settings;
    private State state=State.IDLE;
    private String reason="Guardian preflight idle",closeResult="";
    private Object player,world,ownedMenu;
    private List<EquipmentWorkflow.Item> inventory=List.of();
    private int selected,menuId=-1;
    private ExperimentsGuardianSettings.Page page;
    private MenuSnapshot pending;
    private MenuSnapshot.Slot pendingPet;
    private long deadline,nextAction;
    private int commands,petClicks,pageClicks;
    public ExperimentsGuardianPreflight(EquipmentWorkflow.Port port){this.port=Objects.requireNonNull(port);}
    public State state(){return state;}
    public boolean ready(){return state==State.READY;}
    public boolean terminal(){return state==State.READY||state==State.FAILED||state==State.CANCELLED;}
    public String diagnostics(){return state+": "+reason+"; pet page="+(page==null?"none":page.number()+"/"+page.total())+"; commands="+commands+", pet clicks="+petClicks+", page clicks="+pageClicks;}
    public boolean allowsScreen(String title){return settings!=null&&settings.enabled()&&!terminal()&&settings.page(title)!=null;}
    public void start(ExperimentsGuardianSettings settings,long tick){
        if(state!=State.IDLE&&!terminal())throw new IllegalStateException("Guardian preflight already active");
        this.settings=Objects.requireNonNull(settings);state=State.CHECKING;reason="Checking trimmed tab rows";
        player=null;world=null;ownedMenu=null;inventory=List.of();menuId=-1;page=null;pending=null;pendingPet=null;commands=petClicks=pageClicks=0;
        nextAction=tick;deadline=tick+settings.timeoutTicks();
        if(!settings.enabled()){state=State.READY;reason="Guardian opt-out; experiment solver ready";}
    }
    public void tick(List<String> tabRows,long tick){
        if(state==State.IDLE||terminal())return;
        var o=port.read();
        if(o.player()==null||o.world()==null){fail("Living loaded player required");return;}
        if(player==null){player=o.player();world=o.world();inventory=o.inventory();selected=o.selectedSlot();}
        if(player!=o.player()||world!=o.world()){fail("Player/world changed; Guardian receipt abandoned");return;}
        if(!o.inventory().equals(inventory)||o.inventory().size()!=36||o.selectedSlot()!=selected){fail("Inventory or selected slot changed during Guardian preflight");return;}
        if(tick>deadline){fail("Guardian receipt timed out; uncertain action is never retried");return;}
        if(!o.cursorEmpty()){
            if(o.ownPrediction()&&(state==State.WAIT_PAGE||state==State.WAIT_PET))return;
            fail("Foreign cursor contents; Guardian action/close refused");return;
        }
        if(tick<nextAction)return;
        switch(state){
            case CHECKING -> {
                long matches=tabRows.stream().filter(settings::activeTab).count();
                if(matches>1){fail("Ambiguous active-pet tab rows");return;}
                if(matches==1){state=State.READY;reason="Trimmed tab row confirms active Guardian binding";return;}
                if(o.snapshot()!=null){
                    if(!adopt(o)){fail("Close unrelated menu before Guardian preflight");return;}
                    state=State.SWITCHING_TO_PET;reason="Existing exact pet menu observed";
                }else {state=State.OPEN_PET_MENU;reason="Guardian absent from tab; opening explicit pet menu";}
                nextAction=tick+settings.delayTicks();
            }
            case OPEN_PET_MENU -> {
                if(o.snapshot()!=null){fail("A menu appeared before pet command dispatch");return;}
                commands++;
                if(!port.command(o,settings.command())){fail("Guarded explicit pet command refused");return;}
                waitFor(State.WAIT_MENU,tick,"Waiting for exact pet menu; command sent once");
            }
            case WAIT_MENU -> {
                if(o.snapshot()==null)return;
                if(!adopt(o)){fail("Unexpected pet-menu title/page; menu not adopted");return;}
                state=State.SWITCHING_TO_PET;nextAction=tick+settings.delayTicks();reason="Exact pet page observed";
            }
            case SWITCHING_TO_PET -> choose(o,tick);
            case GO_NEXT_PAGE -> {
                if(!samePage(o)){fail("Pet page changed before guarded next-page click");return;}
                var next=o.snapshot().slots().stream().filter(s->!s.playerInventory()&&!s.locked()&&s.count()==1
                        &&MenuSnapshot.clean(s.name()).equals(settings.nextPageName())&&s.itemId().equals(settings.nextPageItemId())).toList();
                if(next.size()!=1){fail("Next-page identity/count is ambiguous or unavailable");return;}
                pending=o.snapshot();pageClicks++;
                if(!port.click(o,next.getFirst().id())){fail("Guarded next-page click refused");return;}
                waitFor(State.WAIT_PAGE,tick,"Waiting for exact next page and server revision");
            }
            case WAIT_PAGE -> {
                if(o.snapshot()==null){fail("Pet menu closed before page acknowledgment");return;}
                var after=settings.page(o.snapshot().title());
                if(after==null||after.total()!=page.total()||after.number()<page.number()||after.number()>page.number()+1){fail("Pet pagination receipt changed total or skipped/reversed pages");return;}
                if(after.number()==page.number()){
                    if(o.menu()!=ownedMenu||o.snapshot().id()!=menuId){fail("Pet menu replaced without page advancement");return;}
                    return;
                }
                // Page transitions may replace the native menu. Only expected page+1 with server
                // receipt can transfer the lease; a header/client prediction alone is insufficient.
                boolean fresh=o.serverRevision()&&(o.menu()!=ownedMenu||o.snapshot().id()!=pending.id()||o.snapshot().revision()!=pending.revision());
                if(!fresh)return;
                ownedMenu=o.menu();menuId=o.snapshot().id();page=after;pending=null;
                state=State.SWITCHING_TO_PET;nextAction=tick+settings.delayTicks();reason="Exact next-page receipt observed";
            }
            case WAIT_PET -> {
                if(!samePage(o)){fail("Pet page/menu changed before pet-state receipt");return;}
                var pets=targets(o.snapshot());
                if(pets.size()!=1||pets.getFirst().id()!=pendingPet.id()){fail("Pet target changed or became ambiguous while awaiting receipt");return;}
                var pet=pets.getFirst();
                if(active(pet)&&o.serverRevision()&&o.snapshot().revision()!=pending.revision()){
                    pending=null;pendingPet=null;closeResult="Server active-pet lore receipt confirmed";
                    state=State.CLOSING_MENU;nextAction=tick+settings.delayTicks();reason=closeResult;
                }else if(!inactive(pet)&&!active(pet)){fail("Pet state became unknown/contradictory; no retry");}
            }
            case CLOSING_MENU -> {
                if(!samePage(o)){fail("Pet menu changed before close dispatch");return;}
                if(!port.close(o)){fail("Guarded owned pet-menu close refused");return;}
                waitFor(State.WAIT_CLOSE,tick,"Waiting for owned pet menu closure");
            }
            case WAIT_CLOSE -> {
                if(o.snapshot()!=null){if(!samePage(o))fail("Different menu appeared before close receipt");return;}
                state=State.READY;reason=closeResult+"; experiment solver ready";releaseLease();
            }
            default -> {}
        }
    }
    private void choose(EquipmentWorkflow.Observation o,long tick){
        if(!samePage(o)){fail("Pet page/menu lease changed before selection");return;}
        if(!populatedTail(o.snapshot())){reason="Waiting for populated final server slot before pet search";return;}
        var pets=targets(o.snapshot());
        if(pets.size()>1){fail("Exact pet identity/count ambiguous; no click");return;}
        if(pets.isEmpty()){
            if(page.number()<page.total()) {state=State.GO_NEXT_PAGE;nextAction=tick+settings.delayTicks();reason="Pet missing on current page; advancing";}
            else {state=State.CLOSING_MENU;closeResult="Pet absent after final page; source returns to solver";reason=closeResult;}
            return;
        }
        var pet=pets.getFirst();
        if(active(pet)){state=State.CLOSING_MENU;closeResult="Pet already active in exact menu lore";reason=closeResult;return;}
        if(!inactive(pet)){fail("Pet item/count/lore state unknown or contradictory");return;}
        pending=o.snapshot();pendingPet=pet;petClicks++;
        if(!port.click(o,pet.id())){fail("Guarded single pet activation click refused");return;}
        waitFor(State.WAIT_PET,tick,"Waiting for exact active-pet state receipt; click sent once");
    }
    private static boolean populatedTail(MenuSnapshot menu){
        return menu.slots().stream().filter(s->!s.playerInventory()).max(Comparator.comparingInt(MenuSnapshot.Slot::id))
                .map(s->s.count()>0&&!s.itemId().equals("minecraft:air")).orElse(false);
    }
    private List<MenuSnapshot.Slot> targets(MenuSnapshot menu){return menu.slots().stream().filter(s->!s.playerInventory()&&MenuSnapshot.clean(s.name()).equals(settings.petName())).toList();}
    private boolean active(MenuSnapshot.Slot pet){return validPet(pet,settings.activeItemId())&&lore(pet,settings.activeLore())&&!lore(pet,settings.inactiveLore());}
    private boolean inactive(MenuSnapshot.Slot pet){return validPet(pet,settings.inactiveItemId())&&lore(pet,settings.inactiveLore())&&!lore(pet,settings.activeLore());}
    private static boolean validPet(MenuSnapshot.Slot pet,String itemId){return pet.count()==1&&!pet.locked()&&pet.itemId().equals(itemId);}
    private static boolean lore(MenuSnapshot.Slot pet,String label){return pet.lore().stream().map(MenuSnapshot::clean).anyMatch(label::equals);}
    private boolean adopt(EquipmentWorkflow.Observation o){
        var found=settings.page(o.snapshot().title());if(found==null||o.menu()==null)return false;
        // Start from page one so final-page absence proves the bounded list was scanned.
        if(found.number()!=1)return false;
        page=found;ownedMenu=o.menu();menuId=o.snapshot().id();return true;
    }
    private boolean samePage(EquipmentWorkflow.Observation o){return o.snapshot()!=null&&o.menu()==ownedMenu&&o.snapshot().id()==menuId&&Objects.equals(settings.page(o.snapshot().title()),page);}
    private void waitFor(State next,long tick,String why){state=next;reason=why;nextAction=tick+settings.delayTicks();deadline=tick+settings.timeoutTicks();}
    private void fail(String why){state=State.FAILED;reason=why;releaseLease();}
    private void releaseLease(){pending=null;pendingPet=null;ownedMenu=null;player=null;world=null;inventory=List.of();}
    /** Stop never dispatches cleanup against an unacknowledged click or replaced menu. */
    public void cancel(){if(!terminal()&&state!=State.IDLE){state=State.CANCELLED;reason="Guardian cancelled; no uncertain action repeated";}releaseLease();}
}
