package dev.veltraph.client.inventory.abiphone;

import dev.veltraph.client.gui.MenuSnapshot;
import java.util.*;

/** One native phone use and one exact contact click. The caller owns ticking and received messages. */
public final class AbiphoneCall {
    public enum State { IDLE, ACQUIRE, WAIT_ACQUIRE, USE_PHONE, WAIT_CONTACTS, CHOOSE_CONTACT, WAIT_RESPONSE,
        CLEANUP, WAIT_RESTORE, HANDOFF, COMPLETE, FAILED, CANCELLED, RECOVERY_REQUIRED }
    public record Item(String registryId,String name,int count,String components) {
        public static final Item EMPTY=new Item("","",0,"");
        public boolean empty(){return count==0;}
        public Item {Objects.requireNonNull(registryId);Objects.requireNonNull(name);Objects.requireNonNull(components);if(count<0)throw new IllegalArgumentException("Negative count");}
    }
    public record Request(Item phone,String contactsTitle,Item contact,String dialogueTitle,Item dialogueMarker,
                          String receivedReceipt,List<String> failureReceipts,int delayTicks,int timeoutTicks,boolean retainDialogue) {
        public Request {
            failureReceipts=List.copyOf(failureReceipts);
            for(Item item:List.of(phone,contact,dialogueMarker))if(item.count()!=1||!item.registryId().matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||item.name().isBlank()||item.components().isBlank())
                throw new IllegalArgumentException("Explicit full phone/contact/dialogue item identity and quantity one required");
            if(contactsTitle.isBlank()||dialogueTitle.isBlank()||contactsTitle.equals(dialogueTitle)||receivedReceipt.isBlank()
                    ||delayTicks<1||timeoutTicks<=delayTicks||timeoutTicks>1200||failureReceipts.size()>8||failureReceipts.stream().anyMatch(String::isBlank)||failureReceipts.contains(receivedReceipt))
                throw new IllegalArgumentException("Distinct exact contact/dialogue titles, receipt and bounded timing required");
        }
        public boolean allowsTitle(String title){return contactsTitle.equals(title)||dialogueTitle.equals(title);}
    }
    public record Row(int slot,boolean playerInventory,boolean locked,Item item) {}
    public record Observation(Object player,Object world,Object menu,String title,int revision,List<Item> inventory,
                              List<Row> rows,int selectedSlot,boolean cursorEmpty,boolean ownPrediction,boolean correctionTickPassed,boolean transferConfirmed) {
        public Observation {inventory=List.copyOf(inventory);rows=List.copyOf(rows);}
        public boolean valid(){return player!=null&&world!=null&&inventory.size()==36&&selectedSlot>=0&&selectedSlot<9;}
    }
    public interface Port {
        Observation read();
        boolean swap(Observation expected,int mainIndex,int hotbarIndex);
        boolean select(Observation expected,int hotbarIndex);
        boolean use(Observation expected);
        boolean click(Observation expected,int slot);
        boolean close(Observation expected);
    }
    /** Immutable menu lease, valid only while the exact menu/player/world and marker remain unchanged. */
    public record Handoff(Object player,Object world,Object menu,String title,int revision,Item marker) {}
    private final Port port;
    private State state=State.IDLE,goal=State.COMPLETE;private String reason="Idle",cleanupReason="";private Request request;
    private Object player,world,contactsMenu,dialogueMenu;private int originalSelected,phoneIndex=-1,hotbar=-1;
    private List<Item> original=List.of(),placed=List.of();private boolean useIssued,contactIssued,receipt,restoreIssued;
    private long deadline,nextAction,lastTick;private Handoff handoff;
    public AbiphoneCall(Port port){this.port=Objects.requireNonNull(port);}
    public State state(){return state;}public String reason(){return reason;}public Request request(){return request;}
    public boolean active(){return switch(state){case IDLE,HANDOFF,COMPLETE,FAILED,CANCELLED,RECOVERY_REQUIRED->false;default->true;};}
    public boolean recoveryReservesCurrentContext(){return state==State.RECOVERY_REQUIRED&&context(port.read());}
    public boolean terminal(){return state!=State.IDLE&&!active();}
    public Optional<Handoff> handoff(){return state==State.HANDOFF?Optional.ofNullable(handoff):Optional.empty();}
    public boolean handoffValid(){if(state!=State.HANDOFF||handoff==null)return false;var o=port.read();return context(o)&&o.menu()==handoff.menu()
            &&o.title().equals(handoff.title())&&o.cursorEmpty()&&o.inventory().equals(original)&&unique(o,request.dialogueMarker()).size()==1;}
    public void start(Request request,long tick){
        if(active()||state==State.HANDOFF||state==State.RECOVERY_REQUIRED)throw new IllegalStateException("Call, dialogue handoff or manual recovery already owned");
        this.request=Objects.requireNonNull(request);state=State.IDLE;reason="Starting";goal=State.COMPLETE;cleanupReason="";
        useIssued=false;contactIssued=false;receipt=false;restoreIssued=false;handoff=null;contactsMenu=null;dialogueMenu=null;phoneIndex=-1;hotbar=-1;lastTick=tick;
        var o=port.read();if(!o.valid()||o.menu()!=null||!o.cursorEmpty()){finish(State.FAILED,"Living loaded player, closed menus and empty cursor required");return;}
        player=o.player();world=o.world();originalSelected=o.selectedSlot();original=o.inventory();
        var matches=new ArrayList<Integer>();for(int i=0;i<36;i++)if(original.get(i).equals(request.phone()))matches.add(i);
        if(matches.size()!=1){finish(State.FAILED,"Exactly one full-identity phone required");return;}
        phoneIndex=matches.getFirst();if(phoneIndex<9)hotbar=phoneIndex;else{for(int i=0;i<9;i++)if(original.get(i).empty()){hotbar=i;break;}}
        if(hotbar<0){finish(State.FAILED,"A free hotbar slot is required; occupied tools are preserved");return;}
        var pair=new ArrayList<>(original);if(phoneIndex>=9)Collections.swap(pair,phoneIndex,hotbar);placed=List.copyOf(pair);
        deadline=tick+request.timeoutTicks();nextAction=tick;state=phoneIndex<9?State.USE_PHONE:State.ACQUIRE;
    }
    /** Only a received post-contact exact receipt can acknowledge this request. */
    public void onServerMessage(String line){
        if(!active()||!contactIssued||state!=State.WAIT_RESPONSE)return;
        String clean=MenuSnapshot.clean(line);if(clean.equals(request.receivedReceipt()))receipt=true;
        else if(request.failureReceipts().contains(clean))cleanup(State.FAILED,"Received configured call failure");
    }
    public void cancel(){if((active()||state==State.HANDOFF)&&goal!=State.CANCELLED){goal=State.CANCELLED;cleanupReason="Cancelled";reason="Cancelled; draining issued action";
        if(state!=State.WAIT_ACQUIRE&&state!=State.WAIT_CONTACTS&&state!=State.WAIT_RESPONSE&&state!=State.WAIT_RESTORE)state=State.CLEANUP;
        deadline=lastTick+request.timeoutTicks();}}
    /** Release a completed handoff only after its caller has consumed/closed the menu. Never closes a replacement. */
    public boolean releaseHandoff(){if(state!=State.HANDOFF)return false;var o=port.read();if(!context(o)||!o.cursorEmpty()||!phoneRestored(o))return false;
        // After handoff the consumer may legitimately change other inventory slots. Its completed
        // transaction is outside this phone lease; only the restored phone pair remains ours.
        if(o.menu()!=null){if(o.menu()!=handoff.menu()||!o.title().equals(handoff.title())||unique(o,request.dialogueMarker()).size()!=1||!port.close(o))return false;}
        handoff=null;finish(State.COMPLETE,"Acknowledged contact; dialogue lease released");return true;}
    /** Explicit local acknowledgment after the operator restored the pair. Read-only; never retries a call. */
    public boolean acknowledgeManualRecovery(){
        if(state!=State.RECOVERY_REQUIRED)return false;var o=port.read();
        if(!o.valid()||o.menu()!=null||!o.cursorEmpty()||!phoneRestored(o)||o.selectedSlot()!=originalSelected)return false;
        finish(goal==State.CANCELLED?State.CANCELLED:State.FAILED,"Original phone pair and selection manually verified; call was not retried");return true;
    }
    public void tick(long tick){
        if(!active())return;lastTick=tick;var o=port.read();
        if(!context(o)){finish(State.RECOVERY_REQUIRED,"Player/world changed; no stale item or menu operation attempted");return;}
        if(!o.cursorEmpty()){
            if(o.ownPrediction()&&contactIssued&&state==State.WAIT_RESPONSE&&tick<=deadline)return;
            finish(State.RECOVERY_REQUIRED,"Cursor ownership unavailable; phone/menu preserved for manual recovery");return;
        }
        if(tick>deadline){
            if(state==State.CLEANUP||state==State.WAIT_RESTORE){finish(State.RECOVERY_REQUIRED,cleanupReason+"; exact restoration not acknowledged");return;}
            cleanup(goal==State.CANCELLED?State.CANCELLED:State.FAILED,"Call response timed out; no retry");
        }
        if(tick<nextAction)return;
        switch(state){
            case ACQUIRE -> {if(!o.inventory().equals(original)||o.menu()!=null||!port.swap(o,phoneIndex,hotbar)){finish(State.FAILED,"Exact phone transfer refused");return;}waitFor(State.WAIT_ACQUIRE,tick,"Waiting for native phone placement");}
            case WAIT_ACQUIRE -> {
                if(o.correctionTickPassed()&&o.inventory().equals(placed)){state=goal==State.COMPLETE?State.USE_PHONE:State.CLEANUP;nextAction=tick+request.delayTicks();}
                else if(!o.inventory().equals(original)&&!o.inventory().equals(placed))finish(State.RECOVERY_REQUIRED,"Unexpected inventory during phone acquisition");
            }
            case USE_PHONE -> {
                if(!o.inventory().equals(placed)||o.menu()!=null||!port.select(o,hotbar)||!port.use(port.read())){cleanup(State.FAILED,"Native exact phone use refused");return;}
                useIssued=true;waitFor(State.WAIT_CONTACTS,tick,"Waiting for received contacts menu");
            }
            case WAIT_CONTACTS -> {
                if(o.menu()!=null){if(!o.title().equals(request.contactsTitle())){cleanup(State.FAILED,"Unexpected phone response menu");return;}
                    contactsMenu=o.menu();state=goal==State.COMPLETE?State.CHOOSE_CONTACT:State.CLEANUP;nextAction=tick+request.delayTicks();}
            }
            case CHOOSE_CONTACT -> {
                if(o.menu()!=contactsMenu||!o.title().equals(request.contactsTitle())||!o.inventory().equals(placed)){cleanup(State.FAILED,"Contact menu/inventory changed");return;}
                var matches=unique(o,request.contact());if(matches.size()!=1||!port.click(o,matches.getFirst().slot())){cleanup(State.FAILED,"Missing/ambiguous/stale full-identity contact");return;}
                contactIssued=true;receipt=false;waitFor(State.WAIT_RESPONSE,tick,"Waiting for received contact receipt and NPC dialogue");
            }
            case WAIT_RESPONSE -> {
                if(!o.inventory().equals(placed)){finish(State.RECOVERY_REQUIRED,"Phone/inventory changed while awaiting call");return;}
                if(o.menu()!=null&&o.menu()!=contactsMenu){
                    if(o.title().equals(request.dialogueTitle())&&unique(o,request.dialogueMarker()).size()==1)dialogueMenu=o.menu();
                    else {cleanup(State.FAILED,"Unexpected contact/NPC response");return;}
                }
                if(dialogueMenu!=null&&o.menu()==dialogueMenu&&receipt){state=State.CLEANUP;reason="Received exact contact and NPC dialogue";nextAction=tick+request.delayTicks();deadline=tick+request.timeoutTicks();}
                else if(goal!=State.COMPLETE&&dialogueMenu!=null)state=State.CLEANUP;
            }
            case CLEANUP -> clean(o,tick);
            case WAIT_RESTORE -> {
                if(o.inventory().equals(original)&&o.correctionTickPassed()&&o.transferConfirmed()){state=State.CLEANUP;nextAction=tick+request.delayTicks();reason="Received exact original phone placement revision";}
                else if(!o.inventory().equals(placed)&&!o.inventory().equals(original))finish(State.RECOVERY_REQUIRED,"Exact phone restoration pair changed");
            }
            default -> {}
        }
    }
    private boolean context(Observation o){return o.valid()&&o.player()==player&&o.world()==world;}
    private boolean phoneRestored(Observation o){return phoneIndex>=0&&o.inventory().get(phoneIndex).equals(request.phone())
            &&(phoneIndex<9||o.inventory().get(hotbar).equals(original.get(hotbar)));}
    private List<Row> unique(Observation o,Item item){return o.rows().stream().filter(r->!r.playerInventory()&&!r.locked()&&r.item().equals(item)).toList();}
    private boolean owned(Observation o){return o.menu()==contactsMenu&&o.title().equals(request.contactsTitle())||o.menu()==dialogueMenu&&o.title().equals(request.dialogueTitle())&&unique(o,request.dialogueMarker()).size()==1;}
    private void clean(Observation o,long tick){
        // A later response may arrive after timeout/cancel. Recognize it only by the complete contract.
        if(o.menu()!=null&&!owned(o)){
            if(useIssued&&contactsMenu==null&&o.title().equals(request.contactsTitle()))contactsMenu=o.menu();
            else if(contactIssued&&o.title().equals(request.dialogueTitle())&&unique(o,request.dialogueMarker()).size()==1)dialogueMenu=o.menu();
            else {reason=cleanupReason+"; waiting for replacement menu to close without touching it";return;}
        }
        if(!o.inventory().equals(original)&&!o.inventory().equals(placed)){finish(State.RECOVERY_REQUIRED,"Inventory changed; exact temporary phone placement cannot be restored safely");return;}
        boolean retain=goal==State.COMPLETE&&receipt&&request.retainDialogue()&&dialogueMenu!=null&&o.menu()==dialogueMenu;
        if(phoneIndex>=9&&o.inventory().equals(placed)&&!placed.equals(original)){
            if(restoreIssued||!port.swap(o,phoneIndex,hotbar)){finish(State.RECOVERY_REQUIRED,"Exact original phone slot restitution refused");return;}
            restoreIssued=true;waitFor(State.WAIT_RESTORE,tick,"Waiting for native original phone placement");return;
        }
        if(o.menu()!=null&&!retain){if(!owned(o)||!port.close(o)){finish(State.RECOVERY_REQUIRED,"Owned empty-cursor menu close refused");return;}nextAction=tick+request.delayTicks();return;}
        finishRestored(o);
    }
    private void finishRestored(Observation o){
        if(o.menu()!=null&&!owned(o)){finish(State.RECOVERY_REQUIRED,"Replacement menu preserved; selection restoration deferred for manual recovery");return;}
        if(!o.inventory().equals(original)||!o.cursorEmpty()||!port.select(o,originalSelected)){finish(State.RECOVERY_REQUIRED,"Exact inventory/selection restitution unavailable");return;}
        if(goal==State.COMPLETE&&receipt&&request.retainDialogue()&&o.menu()==dialogueMenu&&owned(o)){
            handoff=new Handoff(player,world,o.menu(),o.title(),o.revision(),request.dialogueMarker());state=State.HANDOFF;reason="Received contact and dialogue; phone and selection restored; caller owns dialogue lease";
        }else finish(goal,goal==State.COMPLETE?"Received contact and dialogue; phone, cursor, menu and selection restored":cleanupReason+"; phone and selection restored");
    }
    private void cleanup(State goal,String reason){this.goal=goal;cleanupReason=reason;this.reason=reason;state=State.CLEANUP;deadline=lastTick+request.timeoutTicks();nextAction=lastTick;}
    private void waitFor(State state,long tick,String reason){this.state=state;this.reason=reason;nextAction=tick+request.delayTicks();}
    private void finish(State state,String reason){this.state=state;this.reason=reason;}
}
