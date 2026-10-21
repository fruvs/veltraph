package dev.veltraph.client.galatea;
import java.util.*;
import java.util.function.ToLongFunction;

/** Unique exact receipt-set closure with explicit local CPU bounds; no transport-order assumption. */
public final class FigHarvestReceiptAllocation {
    private FigHarvestReceiptAllocation() {}
    private record Selection<T>(int ways,List<T> items) {}
    public static <T> Optional<List<T>> unique(List<T> candidates,long amount,ToLongFunction<T> count){
        if(candidates==null||count==null||amount<=0)return Optional.empty();
        var eligible=candidates.stream().filter(s->count.applyAsLong(s)>0&&count.applyAsLong(s)<=amount).toList();
        if(eligible.size()>64)return Optional.empty();
        Map<Long,Selection<T>> sums=new HashMap<>();sums.put(0L,new Selection<>(1,List.of()));
        for(var item:eligible){long n=count.applyAsLong(item);var next=new HashMap<>(sums);
            for(var entry:sums.entrySet()){if(entry.getKey()>amount-n)continue;long target=entry.getKey()+n;
                var chain=new ArrayList<T>(entry.getValue().items());chain.add(item);var prior=next.get(target);
                int ways=Math.min(2,entry.getValue().ways()+(prior==null?0:prior.ways()));next.put(target,new Selection<>(ways,List.copyOf(chain)));}
            if(next.size()>1024)return Optional.empty();sums=next;
        }var result=sums.get(amount);return result!=null&&result.ways()==1?Optional.of(result.items()):Optional.empty();
    }
}
