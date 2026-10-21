package dev.veltraph.client.galatea;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import java.util.function.BooleanSupplier;
import static dev.veltraph.client.galatea.FigHarvestPickupLedger.*;

/** Qualified metadata/removal mass correlation. Protocol supplies no ancestry or merge packet. */
public final class FigHarvestMergeCorrelation {
    private FigHarvestMergeCorrelation() {}
    public record Carrier(Scope scope,UUID uuid,int entityId,Break broken,Vec3 originalSpawn,
            long spawnSerial,long spawnAt,long dataSerial,long dataAt,Object canonicalUnit,int count,
            boolean tainted,boolean alreadyTakenOrAllocated) {}
    public record Removal(UUID uuid,int entityId,long nativeSerial,long at,boolean actuallyAbsent,boolean anyObservedTake) {}
    public record Donor(Carrier carrier,Removal removal) {}
    public record Metadata(UUID destination,long nativeSerial,long at,Object canonicalUnit,int beforeCount,int afterCount,int nativeMaxStackSize) {}
    public static final class Proof {
        private final Carrier destination;private final Metadata metadata;private final List<Donor> donors;private boolean spent;
        private Proof(Carrier d,Metadata m,List<Donor> donors){destination=d;metadata=m;this.donors=List.copyOf(donors);}
        public Carrier destination(){return destination;}public Metadata metadata(){return metadata;}public List<Donor> donors(){return donors;}
        public boolean commit(Scope originalCurrent,BooleanSupplier freshNativeCustody){
            if(spent||originalCurrent==null||!destination.scope().same(originalCurrent)||freshNativeCustody==null||!freshNativeCustody.getAsBoolean())return false;
            spent=true;return true;
        }
    }
    private static boolean carrier(Carrier c,Scope scope,long deadline){return c!=null&&scope!=null&&c.scope()!=null&&c.scope().same(scope)
            &&c.uuid()!=null&&c.entityId()>=0&&c.broken()!=null&&c.broken().issueSerial()>0&&c.broken().ackSerial()>0
            &&c.broken().originalBlockIdentity()!=null&&c.originalSpawn()!=null&&Double.isFinite(c.originalSpawn().x())
            &&Double.isFinite(c.originalSpawn().y())&&Double.isFinite(c.originalSpawn().z())&&c.spawnSerial()>0&&c.dataSerial()>c.spawnSerial()
            &&c.canonicalUnit()!=null&&c.count()>0&&!c.tainted()&&!c.alreadyTakenOrAllocated()
            &&c.broken().issuedMillis()<=c.broken().ackMillis()&&c.broken().issuedMillis()<=c.spawnAt()
            &&c.broken().issuedMillis()<=c.dataAt()&&c.broken().ackMillis()<=deadline&&c.spawnAt()<=deadline&&c.dataAt()<=deadline;}
    public static Optional<Proof> correlate(Carrier destination,Metadata metadata,List<Donor> possible,Scope scope,long originalDeadline){
        if(!carrier(destination,scope,originalDeadline)||metadata==null||possible==null||!destination.uuid().equals(metadata.destination())
                ||metadata.nativeSerial()<=destination.dataSerial()||metadata.at()<destination.broken().issuedMillis()||metadata.at()>originalDeadline
                ||metadata.beforeCount()!=destination.count()||metadata.afterCount()<=metadata.beforeCount()
                ||metadata.afterCount()>Math.min(64,metadata.nativeMaxStackSize())||!Objects.equals(destination.canonicalUnit(),metadata.canonicalUnit()))return Optional.empty();
        var eligible=new ArrayList<Donor>();var seen=new HashSet<UUID>();var breaks=new HashSet<Long>();
        for(var donor:possible){if(donor==null||!carrier(donor.carrier(),scope,originalDeadline))continue;
            var c=donor.carrier();var removed=donor.removal();
            if(c.uuid().equals(destination.uuid())||!seen.add(c.uuid()))return Optional.empty();
            if(removed==null||!removed.actuallyAbsent()||removed.anyObservedTake()||removed.nativeSerial()<=c.dataSerial()
                    ||!c.uuid().equals(removed.uuid())||c.entityId()!=removed.entityId()||removed.at()<c.dataAt()||removed.at()>originalDeadline
                    ||c.broken().issueSerial()==destination.broken().issueSerial()||Objects.equals(c.broken().target(),destination.broken().target())
                    ||!Objects.equals(c.canonicalUnit(),destination.canonicalUnit()))continue;
            if(!breaks.add(c.broken().issueSerial()))return Optional.empty();
            eligible.add(donor);
        }
        var selected=FigHarvestReceiptAllocation.unique(eligible,(long)metadata.afterCount()-metadata.beforeCount(),d->d.carrier().count());
        return selected.map(donors->new Proof(destination,metadata,donors));
    }
}
