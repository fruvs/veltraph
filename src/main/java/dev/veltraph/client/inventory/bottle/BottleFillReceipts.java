package dev.veltraph.client.inventory.bottle;
import java.util.*;

/** Actual applied packet journal. Predicted inventory/menu reads never add a receipt. */
public final class BottleFillReceipts {
    public record Scope(Object player,Object world,Object connection,Object menu,Object screen,int menuId) {
        public boolean same(Scope other){return other!=null&&player==other.player&&world==other.world&&connection==other.connection&&menu==other.menu&&screen==other.screen&&menuId==other.menuId;}
    }
    public record Cell(BottleFillWorkflow.Stack stack,Object canonicalIdentity) {public Cell{Objects.requireNonNull(stack);Objects.requireNonNull(canonicalIdentity);}}
    public enum Kind { PLAYER_SLOT, CONTAINER_SLOT, CONTAINER_CONTENT }
    public record Receipt(long sequence,Scope scope,Kind kind,int revision,Map<Integer,Cell> player,Map<Integer,Cell> container){public Receipt{player=Map.copyOf(player);container=Map.copyOf(container);}}
    private Scope current;private long sequence;private final ArrayDeque<Receipt> receipts=new ArrayDeque<>();
    public long sequence(){return sequence;}
    public void applied(Scope scope,Kind kind,int revision,Map<Integer,Cell> player,Map<Integer,Cell> container){if(current!=null&&!current.same(scope))receipts.clear();current=scope;receipts.addLast(new Receipt(++sequence,scope,kind,revision,player,container));while(receipts.size()>256)receipts.removeFirst();}
    /** Exact applied receipts selected by the same latest-cell rule used by legacy confirmation. */
    public record Match(List<Receipt> receipts) {
        public Match { receipts=List.copyOf(receipts);if(receipts.isEmpty())throw new IllegalArgumentException("Matched native evidence required"); }
    }
    public boolean confirms(Scope scope,long after,Map<Integer,Cell> player,Map<Integer,Cell> container,int minContainerRevision){return matched(scope,after,player,container,minContainerRevision).isPresent();}
    public Optional<Match> matched(Scope scope,long after,Map<Integer,Cell> player,Map<Integer,Cell> container,int minContainerRevision){
        if(player.isEmpty()&&container.isEmpty())return Optional.empty();
        var selected=new TreeMap<Long,Receipt>();
        if(!collect(scope,after,player,true,minContainerRevision,selected)||!collect(scope,after,container,false,minContainerRevision,selected))return Optional.empty();
        return Optional.of(new Match(new ArrayList<>(selected.values())));
    }
    /** Finance requires actual container revisions; a PLAYER_SLOT revision -1 is never fabricated. */
    public Optional<Match> containerWitness(Scope scope,long after,Map<Integer,Cell> player,Map<Integer,Cell> container,int minimumRevision){
        return matched(scope,after,player,container,minimumRevision).filter(match->match.receipts().stream().allMatch(receipt->
                receipt.kind()!=Kind.PLAYER_SLOT&&receipt.revision()>=Math.max(0,minimumRevision)));
    }
    private boolean collect(Scope scope,long after,Map<Integer,Cell> wanted,boolean player,int minRevision,Map<Long,Receipt> selected){
        for(var entry:wanted.entrySet()){
            boolean found=false;var it=receipts.descendingIterator();
            while(it.hasNext()){
                var receipt=it.next();if(receipt.sequence()<=after)break;if(!receipt.scope().same(scope))continue;
                if(!player&&(receipt.kind()==Kind.PLAYER_SLOT||receipt.revision()<minRevision))continue;
                var cells=player?receipt.player():receipt.container();if(!cells.containsKey(entry.getKey()))continue;
                if(!cells.get(entry.getKey()).equals(entry.getValue()))return false;
                selected.put(receipt.sequence(),receipt);found=true;break;
            }
            if(!found)return false;
        }
        return true;
    }

}
