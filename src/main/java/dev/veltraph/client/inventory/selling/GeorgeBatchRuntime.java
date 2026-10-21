package dev.veltraph.client.inventory.selling;

import java.math.BigDecimal;
import java.util.*;

/** One parent, one cumulative deadline, once-only UUID sales. The single-pet escrow primitive remains unchanged. */
public final class GeorgeBatchRuntime {
    public enum State { IDLE, CONTACT, SELLING, COMPLETE, FAILED, CANCELLED }
    public interface Port {
        SellingObservation read();boolean valid();boolean owner();boolean closed();boolean beginContact(long tick);
        FarmAutoSellRuntime.Progress contact(long tick);boolean releaseContact();
        boolean act(SellingRuntime.Action action,SellingRule rule);void closeContact();
    }
    public record Receipt(UUID petUuid,SellingRuntime.SaleProof proof,Object menu) {}
    private final Port port;private final SellingRuntime sale=new SellingRuntime();private GeorgeBatchPlan plan;private SellingRuntime.Settings settings;
    private List<GeorgeBatchPlan.Entry> queue=List.of();private final List<Receipt> receipts=new ArrayList<>();private final Set<UUID> completed=new HashSet<>();
    private Map<Integer,String> ledger=Map.of();private BigDecimal purse=BigDecimal.ZERO,proceeds=BigDecimal.ZERO;private long last,deadline;private int index;private State state=State.IDLE;private String reason="Idle";private Object nativeMenu;
    public GeorgeBatchRuntime(Port port){this.port=Objects.requireNonNull(port);}
    public boolean start(GeorgeBatchPlan plan,List<SellingRule> rules,SellingRuntime.Settings settings,long tick){
        if(active()||sale.cleaning()||sale.escrowHeld())throw new IllegalStateException("George queue or owned restitution active");
        this.plan=plan;this.settings=settings;var o=port.read();queue=plan.freeze(rules,o,settings);receipts.clear();completed.clear();index=0;proceeds=BigDecimal.ZERO;ledger=o.ledger();purse=o.coins();last=tick;deadline=Math.addExact(tick,plan.timeoutTicks());sale.reset();
        if(!port.valid()||!port.closed())return fail("Committed closed George owner required",tick);return begin(tick);
    }
    private boolean begin(long tick){
        if(index>=queue.size()){state=State.COMPLETE;reason="All frozen pet receipts acknowledged; closed native inventory";return true;}
        var o=port.read();var entry=queue.get(index);if(!conserved(o)||!entry.rule().matches(o.inventoryStack(entry.rule().inventoryIndex()).orElse(null))||!entry.uuid().equals(GeorgeBatchPlan.nativeUuid(o.inventoryStack(entry.rule().inventoryIndex()).orElseThrow().customData().get(plan.uuidPath())))||completed.contains(entry.uuid()))return fail("Frozen next pet UUID/inventory/purse changed",tick);
        if(!port.closed()||!port.beginContact(tick))return fail("Fresh per-pet ingress refused; no retry",tick);state=State.CONTACT;reason="Waiting for fresh source contact/NPC receipt";return true;
    }
    private boolean conserved(SellingObservation o){return o.cursorEmpty()&&o.ledger().equals(ledger)&&o.coins()!=null&&o.coins().compareTo(purse)==0;}
    public void tick(long tick){
        if(!active())return;if(tick<last||tick>=deadline||!port.valid()){fail("Cumulative George deadline/owner/contract changed",tick);return;}last=tick;
        if(state==State.CONTACT){var o=port.read();if(!conserved(o)){fail("Protected inventory or purse changed during ingress",tick);return;}var p=port.contact(tick);if(p==FarmAutoSellRuntime.Progress.FAILED){fail("Exact contact/NPC receipt unavailable",tick);return;}if(p==FarmAutoSellRuntime.Progress.COMPLETE){sale.start(port.read(),queue.get(index).rule(),settings,tick);state=State.SELLING;if(sale.terminal())fail(sale.reason(),tick);}return;}
        work(tick);
        if(sale.state()==SellingRuntime.State.COMPLETE){var proof=sale.acknowledgedSale().orElseThrow();var entry=queue.get(index);var o=port.read();var expected=new HashMap<>(ledger);expected.remove(entry.rule().inventoryIndex());
            if(!port.closed()||!o.cursorEmpty()||!o.ledger().equals(expected)||o.coins()==null||o.coins().compareTo(purse.add(entry.rule().total()))!=0||!completed.add(entry.uuid())){fail("Independent per-pet closed inventory/purse/UUID proof failed",tick);return;}
            receipts.add(new Receipt(entry.uuid(),proof,nativeMenu));proceeds=proceeds.add(proof.proceeds());ledger=Map.copyOf(expected);purse=o.coins();
            if(proceeds.compareTo(plan.maxProceeds())>0||index+1>plan.maxPets()||!port.releaseContact()){fail("Cumulative budget/contact ownership failed",tick);return;}index++;begin(tick);
        }else if(sale.terminal()){fail(sale.reason(),tick);}
    }
    private void work(long tick){var o=port.read();var a=sale.tick(o,tick);if(a.isPresent()){var action=a.get();boolean accepted=sale.valid(action,port.read())&&port.act(action,queue.get(index).rule());sale.applied(action,accepted,tick);}}
    /** Native owner retains menu object identity; no guessed numeric menu ownership. */
    public void bindMenu(Object menu){nativeMenu=menu;}
    public void message(String line){if(state==State.SELLING)sale.onMessage(line);}
    private boolean fail(String why,long tick){reason=why;state=State.FAILED;sale.cancel(port.read(),why,tick);port.closeContact();return false;}
    public void cancel(long tick){if(!active())return;reason="Stopped; frozen queue cannot advance or resume";state=State.CANCELLED;sale.cancel(port.read(),reason,tick);port.closeContact();}
    public void maintenance(long tick){if(!sale.cleaning())return;if(!port.owner()){sale.abandon("Native restitution owner replaced; preserve new state");return;}work(tick);if(!sale.cleaning()&&!sale.escrowHeld())port.closeContact();}
    public boolean active(){return state==State.CONTACT||state==State.SELLING;}
    public boolean cleaning(){return sale.cleaning();}public State state(){return state;}public SellingRuntime.State saleState(){return sale.state();}
    public List<Receipt> receipts(){return List.copyOf(receipts);}public BigDecimal proceeds(){return proceeds;}public int completedCount(){return receipts.size();}
    public boolean allowsScreen(String title){return sale.allowsScreen(title)||settings!=null&&settings.menuTitle().equals(title);}
    public String reason(){return reason;}public boolean escrowHeld(){return sale.escrowHeld();}
}
