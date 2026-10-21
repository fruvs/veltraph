package dev.veltraph.client.galatea;
import dev.veltraph.client.world.*;
import java.util.*;
/** Qualified native correlation, never a proof of opaque server loot origin or a log-count whitelist. */
public final class FigHarvestPickupLedger {
    public enum Evidence { CORRELATED_NATIVE_CHAIN }
    public record Scope(Object owner,Object player,Object world,Object connection,long activationGeneration,Object treeIdentity,long treeGeneration){
        public boolean same(Scope s){return s!=null&&owner==s.owner&&player==s.player&&world==s.world&&connection==s.connection&&activationGeneration==s.activationGeneration&&treeIdentity==s.treeIdentity&&treeGeneration==s.treeGeneration;}
    }
    public record Cell(int index,Object canonicalUnitIdentity,int count,Object canonicalFullIdentity){}
    public record CellDelta(int index,Cell before,Cell after,int contributedCount){public CellDelta(int index,Cell before,Cell after){this(index,before,after,after==null||before==null?0:after.count()-before.count());}}
    public record Break(long issueSerial,BlockPos target,long issuedMillis,long ackSerial,long ackMillis,Object originalBlockIdentity){}
    public record Drop(UUID itemUuid,int nativeEntityId,BlockPos correlatedBlock,Vec3 spawnPosition,Object canonicalUnitIdentity,int availableCount,long spawnSerial,long dataSerial,long spawnedMillis,int eligibleOriginCount){}
    public record Pickup(UUID itemUuid,long nativeTakeSerial,Object recipientPlayer,int amount,long takenMillis){}
    public record Delivery(long appliedInventorySerial,List<CellDelta> cells,long appliedMillis){public Delivery{cells=cells==null?List.of():List.copyOf(cells);}}
    public record Correlation(Evidence evidence,Scope scope,Break broken,Drop drop,Pickup pickup,Delivery delivery){}
    /** Constructible only by this ledger after exact before/after native cells consume pending chain evidence. */
    public static final class Permit {
        private final Scope scope;private final long attemptGeneration;private final List<?> before,after;private final List<Correlation> receipts;
        private boolean spent;
        private Permit(Scope s,long g,List<?> before,List<?> after,List<Correlation> receipts){scope=s;attemptGeneration=g;this.before=List.copyOf(before);this.after=List.copyOf(after);this.receipts=List.copyOf(receipts);}
        public boolean matches(Object owner,Object player,Object world,Object connection,long generation,List<?> expected,List<?> actual){return !spent&&scope.owner()==owner&&scope.player()==player&&scope.world()==world&&scope.connection()==connection&&attemptGeneration==generation&&before.equals(expected)&&after.equals(actual);}
        public List<?> after(){return after;}
    }
    private Scope scope;private Set<BlockPos> treeBlocks=Set.of();private long originalDeadline;
    private final ArrayList<Correlation> pending=new ArrayList<>();
    private final Set<UUID> consumedItems=new HashSet<>();private final Set<Long> consumedTakes=new HashSet<>(),consumedDeliveries=new HashSet<>();
    public void bind(Scope next,Set<BlockPos> originalTreeBlocks,long originalDeadlineMillis){
        if(scope!=null&&scope.same(next))return;
        scope=Objects.requireNonNull(next);treeBlocks=Set.copyOf(originalTreeBlocks);originalDeadline=originalDeadlineMillis;pending.clear();consumedItems.clear();consumedTakes.clear();consumedDeliveries.clear();
    }
    public void clear(){scope=null;treeBlocks=Set.of();pending.clear();consumedItems.clear();consumedTakes.clear();consumedDeliveries.clear();}
    public boolean stageCorrelation(Correlation c){return stageBatch(c==null?List.of():List.of(c));}
    /** Entire completed native batch is checked before any pending receipt can be added. */
    public boolean stageBatch(List<Correlation> batch){
        if(batch==null||batch.isEmpty())return false;
        var items=new HashSet<UUID>();var takes=new HashSet<Long>();
        for(var c:pending){items.add(c.drop().itemUuid());takes.add(c.pickup().nativeTakeSerial());}
        for(var c:batch){if(!validChain(c)||consumedItems.contains(c.drop().itemUuid())||consumedTakes.contains(c.pickup().nativeTakeSerial())||consumedDeliveries.contains(c.delivery().appliedInventorySerial())
                ||!items.add(c.drop().itemUuid())||!takes.add(c.pickup().nativeTakeSerial()))return false;}
        var all=new ArrayList<Correlation>(pending);all.addAll(batch);
        var grouped=new HashMap<Long,List<Correlation>>();for(var c:all)grouped.computeIfAbsent(c.delivery().appliedInventorySerial(),key->new ArrayList<>()).add(c);
        for(var group:grouped.values()){
            var cells=new HashMap<Integer,CellDelta>();var amounts=new HashMap<Integer,Long>();
            for(var c:group)for(var delta:c.delivery().cells()){
                var prior=cells.putIfAbsent(delta.index(),delta);
                if(prior!=null&&(!prior.before().equals(delta.before())||!prior.after().equals(delta.after())))return false;
                long total=amounts.merge(delta.index(),(long)delta.contributedCount(),Long::sum);
                if(total>(long)delta.after().count()-delta.before().count())return false;
            }
        }
        pending.addAll(List.copyOf(batch));return true;
    }
    private boolean validChain(Correlation c){
        if(c==null||scope==null||c.evidence()!=Evidence.CORRELATED_NATIVE_CHAIN||!scope.same(c.scope())||c.broken()==null||c.drop()==null||c.pickup()==null||c.delivery()==null)return false;
        var b=c.broken();var d=c.drop();var p=c.pickup();var delivery=c.delivery();
        if(b.issueSerial()<=0||b.ackSerial()<=0||b.originalBlockIdentity()==null||!treeBlocks.contains(b.target())||!Objects.equals(b.target(),d.correlatedBlock())
                ||d.itemUuid()==null||d.nativeEntityId()<0||d.spawnPosition()==null||d.canonicalUnitIdentity()==null||d.availableCount()<=0||d.spawnSerial()<=0||d.dataSerial()<=0||d.eligibleOriginCount()!=1
                ||!d.itemUuid().equals(p.itemUuid())||p.nativeTakeSerial()<=0||p.recipientPlayer()!=scope.player()||p.amount()<=0||p.amount()>d.availableCount()||delivery.appliedInventorySerial()<=0
                ||b.issuedMillis()>b.ackMillis()||b.issuedMillis()>d.spawnedMillis()||b.issuedMillis()>p.takenMillis()||b.issuedMillis()>delivery.appliedMillis()
                ||b.ackMillis()>originalDeadline||d.spawnedMillis()>originalDeadline||p.takenMillis()>originalDeadline||delivery.appliedMillis()>originalDeadline||delivery.cells().isEmpty())return false;
        Set<Integer> slots=new HashSet<>();long amount=0;
        for(var delta:delivery.cells()){
            if(delta==null||delta.index()<0||delta.index()>35||!slots.add(delta.index())||delta.before()==null||delta.after()==null)return false;
            var before=delta.before();var after=delta.after();
            if(before.index()!=delta.index()||after.index()!=delta.index()||before.count()<0||after.count()<=before.count()||after.canonicalFullIdentity()==null||before.canonicalFullIdentity()==null
                    ||delta.contributedCount()<=0||delta.contributedCount()>(long)after.count()-before.count()||!Objects.equals(after.canonicalUnitIdentity(),d.canonicalUnitIdentity())||before.count()>0&&!Objects.equals(before.canonicalUnitIdentity(),after.canonicalUnitIdentity()))return false;
            amount+=delta.contributedCount();
        }
        return amount==p.amount();
    }
    /** Actual full-inventory snapshots must be covered exactly; unrelated cells cannot be refreshed. */
    public Optional<Permit> review(Scope current,long attemptGeneration,List<?> original,List<?> actual,Set<Integer> protectedSlots){
        if(scope==null||!scope.same(current)||original==null||actual==null||original.size()!=actual.size()||original.size()<36)return Optional.empty();
        var expected=new ArrayList<Object>(original);var used=new ArrayList<Correlation>();
        var batches=new TreeMap<Long,List<Correlation>>();for(var c:pending)batches.computeIfAbsent(c.delivery().appliedInventorySerial(),key->new ArrayList<>()).add(c);
        for(var group:batches.values()){
            var cells=new TreeMap<Integer,CellDelta>();var contributions=new HashMap<Integer,Long>();boolean applies=true;
            for(var c:group)for(var delta:c.delivery().cells()){
                var prior=cells.putIfAbsent(delta.index(),delta);
                if(prior!=null&&(!prior.before().equals(delta.before())||!prior.after().equals(delta.after()))){applies=false;break;}
                contributions.merge(delta.index(),(long)delta.contributedCount(),Long::sum);
            }
            for(var delta:cells.values())if(protectedSlots.contains(delta.index())||delta.index()>=expected.size()
                    ||!Objects.equals(expected.get(delta.index()),delta.before().canonicalFullIdentity())
                    ||contributions.get(delta.index())!=(long)delta.after().count()-delta.before().count()){applies=false;break;}
            if(!applies)continue;
            for(var delta:cells.values())expected.set(delta.index(),delta.after().canonicalFullIdentity());used.addAll(group);
        }
        if(used.isEmpty()||!expected.equals(actual))return Optional.empty();
        return Optional.of(new Permit(scope,attemptGeneration,original,actual,used));
    }
    /** Call only after the fence CAS accepts unchanged native custody and this permit's exact inventory. */
    public boolean commit(Permit permit,Scope expectedCurrent,java.util.function.BooleanSupplier fenceCas){
        if(permit==null||permit.spent||scope==null||!scope.same(expectedCurrent)||!scope.same(permit.scope)||!pending.containsAll(permit.receipts))return false;
        if(fenceCas==null||!fenceCas.getAsBoolean())return false;
        permit.spent=true;for(var c:permit.receipts){consumedItems.add(c.drop().itemUuid());consumedTakes.add(c.pickup().nativeTakeSerial());consumedDeliveries.add(c.delivery().appliedInventorySerial());pending.remove(c);}return true;
    }
}
