package dev.veltraph.client.fishing.trash;

import java.util.*;

/** Recovered first-nonempty/revalidate/whole-stack THROW component, with independent server receipt acceptance. */
public final class FishingTrashDiscard {
    public enum State { IDLE, READY, WAIT_RECEIPT, COMPLETE, FAILED }
    public record Context(Object player,Object world,Object connection,Object menu,Object screen) {
        boolean same(Context other){return other!=null&&player==other.player&&world==other.world&&connection==other.connection&&menu==other.menu&&screen==other.screen;}
    }
    public record Observation(Context context,int menuId,int revision,boolean cursorEmpty,Map<Integer,FishingTrashProfile.Stack> slots,Set<Integer> eligibleSlots) {
        public Observation{slots=Map.copyOf(slots);eligibleSlots=Set.copyOf(eligibleSlots);}
    }
    /** Values originate directly from an applied inventory packet, never the client's predicted menu contents. */
    public record Receipt(Context context,int menuId,int revision,long sequence,Map<Integer,FishingTrashProfile.Stack> authoritativeSlots,boolean cursorEmpty) {
        public Receipt{authoritativeSlots=Map.copyOf(authoritativeSlots);}
    }
    public record Action(int slot,int button){public Action{if(button!=1)throw new IllegalArgumentException("Source drops the whole stack");}}
    private State state=State.IDLE;
    private Observation initial;
    private FishingTrashProfile profile;
    private Map<Integer,FishingTrashProfile.Stack> expected=Map.of();
    private final List<Integer> candidates=new ArrayList<>();
    private long sentAt,nextAt,beforeReceipt;
    private int pending=-1,beforeRevision;
    private String failure="";
    public State state(){return state;}public String failure(){return failure;}
    public int pendingSlot(){return pending;}
    public long beforeReceiptSequence(){return beforeReceipt;}
    public boolean active(){return state==State.READY||state==State.WAIT_RECEIPT;}
    public boolean start(Observation observation,FishingTrashProfile contract,long receiptSequence,long now) {
        if(state!=State.IDLE||observation==null||observation.context()==null||!observation.cursorEmpty()||observation.menuId()!=0)return false;
        var found=observation.slots().entrySet().stream().filter(e->observation.eligibleSlots().contains(e.getKey())&&contract.candidate(e.getValue())).map(Map.Entry::getKey).sorted().toList();
        if(found.size()<5)return false; // exact readable fishing-parent interruption threshold
        initial=observation;profile=contract;expected=observation.slots();candidates.addAll(found);beforeReceipt=receiptSequence;nextAt=now;state=State.READY;return true;
    }
    public Optional<Action> tick(Observation observed,Receipt received,long now) {
        if(!active())return Optional.empty();
        if(observed==null||!initial.context().same(observed.context())||observed.menuId()!=initial.menuId()||!observed.cursorEmpty()) {
            fail("Fishing trash screen, menu, cursor or native context ownership changed");return Optional.empty();
        }
        if(state==State.WAIT_RECEIPT) {
            if(now<sentAt||now-sentAt>profile.requestTimeoutMillis()){fail("Missing independent Fishing trash inventory receipt; no retry");return Optional.empty();}
            boolean exact=received!=null&&received.sequence()>beforeReceipt&&initial.context().same(received.context())
                    &&received.menuId()==initial.menuId()&&received.revision()!=beforeRevision&&received.cursorEmpty()
                    &&received.authoritativeSlots().containsKey(pending)&&received.authoritativeSlots().get(pending).count()==0;
            if(!exact)return Optional.empty();
            var next=new TreeMap<>(expected);next.put(pending,observed.slots().get(pending));
            if(observed.slots().get(pending)==null||observed.slots().get(pending).count()!=0||!next.equals(observed.slots())) {
                fail("Fishing trash received outcome changed an unrelated native inventory stack");return Optional.empty();
            }
            expected=Map.copyOf(next);candidates.remove(Integer.valueOf(pending));beforeReceipt=received.sequence();pending=-1;
            nextAt=now+profile.minClickIntervalMillis();state=State.READY;
        }
        if(!expected.equals(observed.slots())){fail("Fishing trash inventory identity changed before the next drop");return Optional.empty();}
        if(candidates.isEmpty()){state=State.COMPLETE;return Optional.empty();}
        if(now<nextAt)return Optional.empty();
        int slot=candidates.getFirst();var stack=observed.slots().get(slot);
        if(stack==null||stack.count()==0||!profile.candidate(stack)){candidates.removeFirst();return Optional.empty();}
        pending=slot;beforeRevision=observed.revision();sentAt=now;state=State.WAIT_RECEIPT;
        return Optional.of(new Action(slot,1));
    }
    public void fail(String reason){state=State.FAILED;failure=reason;}
    public void reset(){state=State.IDLE;initial=null;profile=null;expected=Map.of();candidates.clear();sentAt=nextAt=beforeReceipt=0;pending=-1;failure="";}
}
