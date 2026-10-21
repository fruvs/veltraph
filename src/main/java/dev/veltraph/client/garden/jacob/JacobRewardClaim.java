package dev.veltraph.client.garden.jacob;

import dev.veltraph.client.gui.MenuSnapshot;
import java.util.*;

/** One selected claim. No reward estimate, fuzzy menu matching, pagination or ambiguous retry. */
public final class JacobRewardClaim {
    public static final long MAX_TIMEOUT_MS=60000;
    public enum State { IDLE, OPENING, WAITING_RECEIPT, CLOSING, RETURNING, COMPLETE, FAILED, UNKNOWN }
    public record Contract(String title,String row,List<String> beforeLore,List<String> afterLore,
                           String acknowledgment,long tickets,long timeoutMs) {
        public Contract {
            beforeLore=List.copyOf(beforeLore);afterLore=List.copyOf(afterLore);
            if(title==null||title.isBlank()||row==null||row.isBlank()||acknowledgment==null||acknowledgment.isBlank()
                    ||beforeLore.isEmpty()||afterLore.isEmpty()||beforeLore.equals(afterLore)||tickets<1||tickets>4096||timeoutMs<1000||timeoutMs>MAX_TIMEOUT_MS)
                throw new IllegalArgumentException("Exact selected claim/claimed row, acknowledgment, ticket count and finite deadline required");
        }
    }
    public record Observation(MenuSnapshot menu,long tickets,boolean cursorEmpty) {}
    /** Cached operands from the original failing receipt guard; never a claim or authority receipt. */
    public record ReceiptGuardTrace(int requestMenuId,int observedMenuId,int requestRevision,int observedRevision,
        boolean sameTitle,boolean cursorEmpty,long observedTickets,long beforeTickets,long expectedTicketDelta,
        long ackBaseline,long matchedAckSerial){}
    public enum Return { ACTIVE, READY, FAILED }
    public interface Port {
        boolean valid(); boolean open(); Observation observe(); boolean click(MenuSnapshot expected,int slot);
        boolean close(MenuSnapshot expected); boolean closed(); boolean beginReturn(); Return returning(); boolean resume(); void cancel();
    }
    /** Constructed only after a sent request, a newer same-menu claimed row, exact ACK and ticket delta. */
    public static final class Receipt {
        private final UUID id;private final MenuSnapshot before,after;private final long beforeTickets,afterTickets,ackSerial;
        private Receipt(MenuSnapshot before,MenuSnapshot after,long beforeTickets,long afterTickets,long ackSerial){
            id=UUID.randomUUID();this.before=before;this.after=after;this.beforeTickets=beforeTickets;this.afterTickets=afterTickets;this.ackSerial=ackSerial;
        }
        public UUID id(){return id;} public MenuSnapshot before(){return before;} public MenuSnapshot after(){return after;}
        public long tickets(){return afterTickets-beforeTickets;} public long ackSerial(){return ackSerial;}
    }
    private final Port port;private State state=State.IDLE;private Contract contract;private long deadline,lastNow,ackBaseline,ackSerial;
    private ReceiptGuardTrace lastReceiptGuardTrace;
    private boolean sent;private MenuSnapshot before;private int selected=-1;private long beforeTickets;private Receipt receipt;private String reason="Idle";
    public JacobRewardClaim(Port port){this.port=Objects.requireNonNull(port);}
    public boolean start(Contract contract,long now,long messageSerial){
        if(state!=State.IDLE||now<0||messageSerial<0||!port.valid()||!port.closed())return false;
        this.contract=Objects.requireNonNull(contract);lastNow=now;deadline=Math.addExact(now,contract.timeoutMs());ackBaseline=messageSerial;
        state=State.OPENING;reason="Waiting for selected claim menu";
        if(!port.valid()||!port.open()){fail(false,"Opening refused");return false;}return true;
    }
    /** Root's actual non-overlay system ingress. Pre-request and wrong messages never acknowledge. */
    public void message(String text,long serial){
        if(state==State.WAITING_RECEIPT&&serial>ackBaseline&&text!=null&&MenuSnapshot.clean(text).equals(contract.acknowledgment())&&port.valid())ackSerial=serial;
    }
    public void tick(long now){
        if(!active())return;
        if(now<lastNow||now>=deadline||!port.valid()){fail(state==State.WAITING_RECEIPT,"Deadline, clock or owner changed");return;}lastNow=now;
        if(state==State.OPENING){
            var observation=port.observe();if(observation==null||observation.menu()==null)return;
            var menu=observation.menu();if(!menu.title().equals(contract.title())||!observation.cursorEmpty()||observation.tickets()<0){fail(false,"Foreign menu, cursor or unavailable inventory");return;}
            var matches=menu.slots().stream().filter(s->!s.playerInventory()&&!s.locked()&&s.count()>0&&s.name().equals(contract.row())&&s.lore().equals(contract.beforeLore())).toList();
            if(matches.size()!=1){fail(false,"Selected claim row absent or ambiguous");return;}
            before=menu;selected=matches.getFirst().id();beforeTickets=observation.tickets();state=State.WAITING_RECEIPT;sent=true;reason="Claim sent; waiting for exact receipt";
            // Latch before send: a synchronous callback can never request the same click twice.
            if(!port.valid()||!port.click(before,selected))fail(true,"Claim click refused or uncertain");return;
        }
        if(state==State.WAITING_RECEIPT){
            var observation=port.observe();if(observation==null||observation.menu()==null)return;
            var after=observation.menu();if(after.id()!=before.id()||!after.title().equals(before.title())||!observation.cursorEmpty()){
                // Record the same immutable observation that failed, before cancellation changes it.
                lastReceiptGuardTrace=new ReceiptGuardTrace(before.id(),after.id(),before.revision(),after.revision(),
                    after.title().equals(before.title()),observation.cursorEmpty(),observation.tickets(),beforeTickets,contract.tickets(),ackBaseline,ackSerial);
                fail(true,"Claim menu identity/cursor changed");return;
            }
            if(after.revision()==before.revision()||ackSerial<=ackBaseline)return;
            var old=before.slots().stream().filter(s->s.id()==selected).findFirst().orElseThrow();
            var row=after.slots().stream().filter(s->s.id()==selected&&!s.playerInventory()&&!s.locked()&&s.name().equals(contract.row())&&s.itemId().equals(old.itemId())&&s.count()==old.count()&&s.lore().equals(contract.afterLore())).toList();
            if(row.size()!=1||observation.tickets()-beforeTickets!=contract.tickets())return;
            if(!port.valid())return;
            receipt=new Receipt(before,after,beforeTickets,observation.tickets(),ackSerial);state=State.CLOSING;reason="Claim acknowledged; closing owned menu";
            if(!port.valid()||!port.close(after))fail(true,"Owned close refused");return;
        }
        if(state==State.CLOSING){if(!port.closed())return;if(!port.valid()||!port.beginReturn()){fail(false,"Physical return refused");return;}state=State.RETURNING;reason="Returning to the captured farm";return;}
        if(state==State.RETURNING){var progress=port.returning();if(progress==Return.FAILED){fail(false,"Physical return failed");return;}if(progress==Return.READY){if(!port.valid()||!port.resume()){fail(false,"Same engine resume refused");return;}state=State.COMPLETE;reason="Claim receipt and same farm resumed";}}
    }
    private void fail(boolean uncertain,String reason){state=(uncertain||sent&&receipt==null)?State.UNKNOWN:State.FAILED;this.reason=reason;port.cancel();}
    /** Data-only revocation of the captured old request; no callback can cancel a replacement run. */
    public void revokeUnconfirmed(String originalReason){if(sent&&active()){state=State.UNKNOWN;reason=Objects.requireNonNull(originalReason);}}
    /** Invalidation may only stop an unconfirmed request. It cannot mint/adopt a receipt or resume. */
    public void rejectInFlight(String originalReason){if(state==State.WAITING_RECEIPT&&receipt==null){revokeUnconfirmed(originalReason);port.cancel();}}
    public void cancel(){if(active())fail(state==State.WAITING_RECEIPT,"Manual stop; no resume");}
    public boolean active(){return state==State.OPENING||state==State.WAITING_RECEIPT||state==State.CLOSING||state==State.RETURNING;}
    public Optional<ReceiptGuardTrace> lastReceiptGuardTrace(){return Optional.ofNullable(lastReceiptGuardTrace);}
    /** Read-only stored claim evidence; diagnostics never call a port or relax receipt guards. */
    public String receiptStatus(){return "requestLatched="+sent+",menu="+(before==null?-1:before.id())+",requestRevision="+(before==null?-1:before.revision())
        +",slot="+selected+",beforeTickets="+beforeTickets+",expectedTickets="+(contract==null?-1:contract.tickets())
        +",ackBaseline="+ackBaseline+",matchedAckSerial="+ackSerial+",receipt="+(receipt!=null)+",originalReceiptGuard="+lastReceiptGuardTrace;}
    public State state(){return state;}public Optional<Receipt> receipt(){return Optional.ofNullable(receipt);}public String status(){return state+": "+reason;}
}
