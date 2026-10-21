package dev.veltraph.client.mining.vein;

import dev.veltraph.client.galatea.FigHarvestPickupLedger;
import dev.veltraph.client.galatea.FigHarvestReceiptAllocation;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import java.util.function.BooleanSupplier;
import static dev.veltraph.client.galatea.FigHarvestPickupLedger.*;

/** Mining receipt joiner. Native observations supply facts; only a fresh caller CAS adopts inventory. */
public final class VeinPickupCorrelation {
    public static final int MAX_ORIGINS=64, MAX_ENTITIES=64, MAX_INVENTORIES=32;
    public record Context(Scope scope,Object configuration,Object tool,int slot,long cameraEpoch,long originalDeadline) {
        public boolean same(Context other){return other!=null&&scope!=null&&scope.same(other.scope)
                &&Objects.equals(configuration,other.configuration)&&Objects.equals(tool,other.tool)
                &&slot==other.slot&&cameraEpoch==other.cameraEpoch&&originalDeadline==other.originalDeadline;}
    }
    /** Exact successful guard, subsequently submitted START and STOP; never predicted block state. */
    public record Issued(long issueSerial,long stopSerial,BlockPos target,Object originalState,Object guard,
                         Object tool,int slot,long epoch,Object configuration,long issuedAt,long stopAt) {}
    public record Air(long serial,long at,boolean appliedServerAir) {}
    public record Entity(UUID uuid,int id,Vec3 spawn,long spawnSerial,long spawnAt,Object unit,int count,
                         long dataSerial,long dataAt) {}
    public record Taken(UUID uuid,int id,long serial,Object recipient,Object unit,int count,long at) {}
    public record Inventory(long serial,long at,List<Cell> before,List<Cell> after) {
        public Inventory { before=before==null?List.of():List.copyOf(before);after=after==null?List.of():List.copyOf(after); }
    }
    private record Origin(Issued issued,Air air) {}
    private final Context bound;
    private final BooleanSupplier freshCustody;
    private final FigHarvestPickupLedger ledger=new FigHarvestPickupLedger();
    private final Map<Long,Origin> origins=new LinkedHashMap<>();
    private final Map<UUID,Entity> entities=new LinkedHashMap<>();
    private final Map<UUID,Long> lastDataSerial=new HashMap<>();
    private final Map<UUID,Taken> takes=new LinkedHashMap<>();
    private final Map<Long,Inventory> inventories=new LinkedHashMap<>();
    private final Set<UUID> tainted=new HashSet<>(),used=new HashSet<>();
    private final Set<Long> takeSerials=new HashSet<>();
    private final Set<Long> staged=new HashSet<>();
    private boolean retired;private long lastNow=-1;private boolean ledgerBound;

    public VeinPickupCorrelation(Context context,BooleanSupplier freshCustody){
        bound=Objects.requireNonNull(context);this.freshCustody=Objects.requireNonNull(freshCustody);
        if(context.scope()==null||context.configuration()==null||context.tool()==null||context.slot()<0||context.slot()>8||context.originalDeadline()<0)throw new IllegalArgumentException("Incomplete mining scope");
    }
    private boolean current(Context context,long now){
        if(retired||now<0||now<lastNow||now>bound.originalDeadline()||!bound.same(context)){retire();return false;}
        try{if(!freshCustody.getAsBoolean()){retire();return false;}}catch(RuntimeException unsupported){retire();return false;}
        lastNow=now;return true;
    }
    private boolean within(long at){return at>=0&&at<=bound.originalDeadline();}
    public boolean issued(Context context,Issued issue,long now){
        if(!current(context,now)||issue==null||issue.issueSerial()<=0||issue.stopSerial()<=issue.issueSerial()
                ||issue.target()==null||issue.originalState()==null||issue.guard()==null||!Objects.equals(issue.tool(),bound.tool())
                ||issue.slot()!=bound.slot()||issue.epoch()!=bound.cameraEpoch()||!Objects.equals(issue.configuration(),bound.configuration())
                ||!within(issue.issuedAt())||issue.stopAt()<issue.issuedAt()||issue.stopAt()>now)return false;
        if(ledgerBound||origins.containsKey(issue.issueSerial())){retire();return false;}
        if(origins.size()>=MAX_ORIGINS){retire();return false;}
        origins.put(issue.issueSerial(),new Origin(issue,null));return true;
    }
    public boolean air(Context context,long issueSerial,Air air,long now){
        if(!current(context,now)||air==null)return false;var origin=origins.get(issueSerial);
        if(origin==null||origin.air()!=null||!air.appliedServerAir()||air.serial()<=origin.issued().stopSerial()
                ||air.at()<origin.issued().stopAt()||air.at()>now)return false;
        origins.put(issueSerial,new Origin(origin.issued(),air));join();return true;
    }
    public boolean entity(Context context,Entity entity,long now){
        if(!current(context,now)||entity==null||entity.uuid()==null||entity.id()<0||entity.spawn()==null||entity.unit()==null||entity.count()<=0
                ||entity.spawnSerial()<=0||entity.dataSerial()<=entity.spawnSerial()||entity.dataAt()<entity.spawnAt()||entity.dataAt()>now
                ||!within(entity.spawnAt())||!Double.isFinite(entity.spawn().x())||!Double.isFinite(entity.spawn().y())||!Double.isFinite(entity.spawn().z()))return false;
        var previous=entities.get(entity.uuid());
        if(previous!=null){
            // Metadata broadcasts may repeat after AIR. Only the exact original entity payload is idempotent.
            // Keep its first DATA receipt and origin; a repeat adds no quantity, permit or take allowance.
            boolean same=previous.id()==entity.id()&&Objects.equals(previous.spawn(),entity.spawn())
                    &&previous.spawnSerial()==entity.spawnSerial()&&previous.spawnAt()==entity.spawnAt()
                    &&Objects.equals(previous.unit(),entity.unit())&&previous.count()==entity.count();
            boolean ordered=entity.dataSerial()>lastDataSerial.getOrDefault(entity.uuid(),previous.dataSerial())&&entity.dataAt()>=previous.dataAt();
            if(!tainted.contains(entity.uuid())&&same&&ordered){lastDataSerial.put(entity.uuid(),entity.dataSerial());join();return !retired;}
            taint(entity.uuid());return false;
        }
        if(entities.size()>=MAX_ENTITIES){retire();return false;}
        // Reused entity IDs or changed stack data cannot acquire a new origin permission.
        for(var existing:entities.values())if(existing.id()==entity.id()){taint(existing.uuid());taint(entity.uuid());}
        if(retired)return false;
        entities.put(entity.uuid(),entity);lastDataSerial.put(entity.uuid(),entity.dataSerial());join();return !tainted.contains(entity.uuid());
    }
    public boolean taken(Context context,Taken take,long now){
        if(!current(context,now)||take==null||take.uuid()==null)return false;var entity=entities.get(take.uuid());
        if(entity==null||take.id()!=entity.id()||take.serial()<=entity.dataSerial()||take.at()<entity.dataAt()||take.at()>now
                ||take.count()<=0||take.count()>entity.count()||take.recipient()!=bound.scope().player()||!Objects.equals(take.unit(),entity.unit())){
            taint(take.uuid());return false;
        }
        if(!takeSerials.add(take.serial())){retire();return false;}
        if(takes.putIfAbsent(take.uuid(),take)!=null){taint(take.uuid());return false;}join();return true;
    }
    public boolean inventory(Context context,Inventory inventory,long now){
        if(!current(context,now)||inventory==null||inventory.serial()<=0||!within(inventory.at())||inventory.at()>now
                ||!validCells(inventory.before())||!validCells(inventory.after()))return false;
        if(inventories.containsKey(inventory.serial())||inventories.size()>=MAX_INVENTORIES){retire();return false;}
        inventories.put(inventory.serial(),inventory);join();return staged.contains(inventory.serial());
    }
    private static boolean validCells(List<Cell> cells){
        if(cells.size()!=36)return false;for(int i=0;i<36;i++){var cell=cells.get(i);if(cell==null||cell.index()!=i||cell.count()<0||cell.canonicalUnitIdentity()==null||cell.canonicalFullIdentity()==null)return false;}return true;
    }
    private void taint(UUID uuid){if(used.contains(uuid)){retire();return;}tainted.add(uuid);}
    private List<CellDelta> changes(Inventory batch){
        var result=new ArrayList<CellDelta>();for(int i=0;i<36;i++){var a=batch.before().get(i);var z=batch.after().get(i);
            if(Objects.equals(a.canonicalFullIdentity(),z.canonicalFullIdentity())){if(a.count()!=z.count()||!Objects.equals(a.canonicalUnitIdentity(),z.canonicalUnitIdentity()))return List.of();continue;}
            if(i==bound.slot()||z.count()<=a.count()||a.count()>0&&!Objects.equals(a.canonicalUnitIdentity(),z.canonicalUnitIdentity()))return List.of();
            result.add(new CellDelta(i,a,z));}return result;
    }
    private Origin origin(Entity entity){
        if(tainted.contains(entity.uuid())||used.contains(entity.uuid()))return null;Origin selected=null;int eligible=0;
        for(var origin:origins.values())if(origin.issued().target().equals(entity.spawn().toBlockPos())
                &&origin.issued().issuedAt()<=entity.spawnAt()){
            // Include still-unacknowledged operations: later AIR must not turn an ambiguous drop into permission.
            eligible++;selected=origin;
        }
        return eligible==1&&selected.air()!=null?selected:null;
    }
    private void join(){
        if(retired)return;
        // One fixed scope owns all original issued cells. Binding happens once; adding cells cannot reset replay history.
        if(origins.isEmpty()||inventories.isEmpty())return;
        // One joiner belongs to one break operation. All possible origins must be observed before any inventory join.
        // New origins after binding retire this collector, so an old permit cannot outlive changed attribution.
        if(!ledgerBound){ledger.bind(bound.scope(),origins.values().stream().map(o->o.issued().target()).collect(java.util.stream.Collectors.toSet()),bound.originalDeadline());ledgerBound=true;}
        for(var batch:inventories.values()){
            if(staged.contains(batch.serial()))continue;var changes=changes(batch);if(changes.isEmpty())continue;
            var needed=new LinkedHashMap<Object,Long>();for(var delta:changes)needed.merge(delta.after().canonicalUnitIdentity(),(long)delta.after().count()-delta.before().count(),Long::sum);
            var candidates=new ArrayList<Entity>();for(var entity:entities.values()){var origin=origin(entity);var take=takes.get(entity.uuid());
                // Packet delivery order is not evidence of loot causality. Both AIR and TAKE must be actually observed
                // before review, while each independently remains inside the original issued operation deadline.
                if(origin!=null&&take!=null&&batch.at()>=origin.issued().issuedAt()&&needed.containsKey(entity.unit()))candidates.add(entity);}
            var selected=new ArrayList<Entity>();boolean complete=true;
            for(var entry:needed.entrySet()){var allocation=FigHarvestReceiptAllocation.unique(candidates.stream().filter(e->Objects.equals(e.unit(),entry.getKey())).toList(),entry.getValue(),e->takes.get(e.uuid()).count());
                if(allocation.isEmpty()){complete=false;break;}selected.addAll(allocation.get());}if(!complete)continue;
            var remaining=new HashMap<Integer,Integer>();for(var delta:changes)remaining.put(delta.index(),delta.after().count()-delta.before().count());
            var chains=new ArrayList<Correlation>();for(var entity:selected){var origin=origin(entity);var take=takes.get(entity.uuid());int amount=take.count();var allocated=new ArrayList<CellDelta>();
                for(var delta:changes)if(amount>0&&Objects.equals(entity.unit(),delta.after().canonicalUnitIdentity())){int part=Math.min(amount,remaining.get(delta.index()));if(part>0){allocated.add(new CellDelta(delta.index(),delta.before(),delta.after(),part));remaining.put(delta.index(),remaining.get(delta.index())-part);amount-=part;}}
                var issue=origin.issued();chains.add(new Correlation(Evidence.CORRELATED_NATIVE_CHAIN,bound.scope(),new Break(issue.issueSerial(),issue.target(),issue.issuedAt(),origin.air().serial(),origin.air().at(),issue.originalState()),
                        new Drop(entity.uuid(),entity.id(),issue.target(),entity.spawn(),entity.unit(),entity.count(),entity.spawnSerial(),entity.dataSerial(),entity.spawnAt(),1),
                        new Pickup(entity.uuid(),take.serial(),take.recipient(),take.count(),take.at()),new Delivery(batch.serial(),allocated,batch.at())));
            }
            if(ledger.stageBatch(chains)){staged.add(batch.serial());for(var entity:selected)used.add(entity.uuid());}
        }
    }
    public Optional<FigHarvestPickupLedger.Permit> review(Context context,long attempt,List<?> expected,List<?> actual,long now){
        if(!current(context,now))return Optional.empty();return ledger.review(bound.scope(),attempt,expected,actual,Set.of(bound.slot()));
    }
    /** Release-and-wait only: no inventory adoption or action permission while real AIR/TAKE is still missing. */
    public boolean pendingExact(Context context,List<?> expected,List<?> actual,long now){
        if(!current(context,now)||expected==null||actual==null||expected.size()!=36||actual.size()!=36)return false;
        for(var batch:inventories.values()){
            if(staged.contains(batch.serial())||!batch.before().stream().map(Cell::canonicalFullIdentity).toList().equals(expected)
                    ||!batch.after().stream().map(Cell::canonicalFullIdentity).toList().equals(actual))continue;
            var changes=changes(batch);if(changes.isEmpty())continue;
            var needed=new LinkedHashMap<Object,Long>();for(var delta:changes)needed.merge(delta.after().canonicalUnitIdentity(),(long)delta.after().count()-delta.before().count(),Long::sum);
            var candidates=new ArrayList<Entity>();for(var entity:entities.values()){
                if(tainted.contains(entity.uuid())||used.contains(entity.uuid()))continue;int count=0;
                for(var origin:origins.values())if(origin.issued().target().equals(entity.spawn().toBlockPos())&&origin.issued().issuedAt()<=entity.spawnAt())count++;
                if(count==1&&needed.containsKey(entity.unit()))candidates.add(entity);
            }
            boolean exact=true;for(var entry:needed.entrySet())if(FigHarvestReceiptAllocation.unique(candidates.stream().filter(e->Objects.equals(e.unit(),entry.getKey())).toList(),entry.getValue(),e->takes.containsKey(e.uuid())?takes.get(e.uuid()).count():e.count()).isEmpty()){exact=false;break;}
            if(exact)return true;
        }return false;
    }
    public boolean commit(Context context,FigHarvestPickupLedger.Permit permit,long now,BooleanSupplier inventoryFenceCas){
        if(!current(context,now)||permit==null||inventoryFenceCas==null)return false;
        return ledger.commit(permit,bound.scope(),()->current(context,now)&&inventoryFenceCas.getAsBoolean());
    }
    public void retire(){retired=true;origins.clear();entities.clear();lastDataSerial.clear();takes.clear();inventories.clear();tainted.clear();used.clear();takeSerials.clear();staged.clear();ledger.clear();}
    /** Actual unspent unique issued/AIR/DATA origin, only for movement attribution. */
    Optional<Entity> singlePendingCollection(Context context,long now){
        if(!current(context,now))return Optional.empty();Entity selected=null;
        for(var entity:entities.values()){
            if(tainted.contains(entity.uuid())||used.contains(entity.uuid())||takes.containsKey(entity.uuid()))continue;
            if(origin(entity)==null)return Optional.empty();
            if(selected!=null)return Optional.empty();selected=entity;
        }
        return Optional.ofNullable(selected);
    }
    boolean observedTakeFor(Entity exact){return exact!=null&&entities.get(exact.uuid())==exact&&takes.containsKey(exact.uuid())&&!tainted.contains(exact.uuid())&&!retired;}
    boolean samePendingCollection(Entity exact){return exact!=null&&!retired&&entities.get(exact.uuid())==exact
        &&!tainted.contains(exact.uuid())&&!used.contains(exact.uuid())&&!takes.containsKey(exact.uuid())&&origin(exact)!=null;}
    public boolean retired(){return retired;}
}
