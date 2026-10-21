package dev.veltraph.client.combat.ghost;
import java.util.Objects;

/** Qualified modern delivery proof: exact native take-item witness AND full-unit inventory delta. Disappearance alone never credits. */
public final class GhostLootPickupTransaction {
    public enum State { WAITING, PARTIAL, COMPLETE, REVOKED, TIMED_OUT }
    public record Pickup(long serial,int entityId,java.util.UUID entityUuid,String unitIdentity,int amount) {}
    private final GhostSourceOwnedParentArea.Stamp scope;
    private final GhostLootCandidatePlanner.Candidate item;
    private final int beforeInventory;
    private final long deadline;
    private long afterSerial;
    private int witnessed,credited;
    private State state=State.WAITING;
    public GhostLootPickupTransaction(GhostSourceOwnedParentArea.Stamp actualScope,GhostLootCandidatePlanner.Candidate actualItem,
            int nativeBeforeInventory,long actualPacketSerial,long nowMs,long explicitlyConfiguredTimeoutMs) {
        scope=Objects.requireNonNull(actualScope);item=Objects.requireNonNull(actualItem);
        if(nativeBeforeInventory<0||actualPacketSerial<0||explicitlyConfiguredTimeoutMs<=0)throw new IllegalArgumentException("Actual inventory/serial and explicit positive timeout required");
        beforeInventory=nativeBeforeInventory;afterSerial=actualPacketSerial;deadline=Math.addExact(nowMs,explicitlyConfiguredTimeoutMs);
    }
    public synchronized boolean observeActualLocalPickup(GhostSourceOwnedParentArea.Stamp actualScope,Pickup nativeReceipt) {
        if(!current(actualScope)||nativeReceipt==null||nativeReceipt.serial()<=afterSerial)return false;
        afterSerial=nativeReceipt.serial();
        if(nativeReceipt.entityId()!=item.entityId()||!item.uuid().equals(nativeReceipt.entityUuid())
                ||!item.unitIdentity().equals(nativeReceipt.unitIdentity())||nativeReceipt.amount()<=0)return false;
        witnessed=(int)Math.min(item.count(),(long)witnessed+nativeReceipt.amount());return true;
    }
    public synchronized State observeInventory(GhostSourceOwnedParentArea.Stamp actualScope,int nativeCount,long nowMs) {
        if(!current(actualScope))return state;
        if(nativeCount<0){state=State.REVOKED;return state;}
        int delivered=(int)Math.min(witnessed,Math.max(0,(long)nativeCount-beforeInventory));
        credited=Math.max(credited,delivered);
        if(credited>=item.count())state=State.COMPLETE;
        else if(nowMs>=deadline)state=State.TIMED_OUT;
        else state=credited>0?State.PARTIAL:State.WAITING;
        return state;
    }
    private boolean current(GhostSourceOwnedParentArea.Stamp actualScope) {
        if(state==State.COMPLETE||state==State.REVOKED||state==State.TIMED_OUT)return false;
        if(!scope.same(actualScope)){state=State.REVOKED;return false;}return true;
    }
    public synchronized void revoke(){if(state!=State.COMPLETE)state=State.REVOKED;}
    public synchronized State state(){return state;}
    public synchronized int creditedUnits(){return credited;}
    public synchronized long afterSerial(){return afterSerial;}
}
