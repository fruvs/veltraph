package dev.veltraph.client.galatea;
import java.util.*;
/** Immutable per-attempt native proof. Slot transitions acknowledge only an owned native selection. */
public final class FigScaffoldAttemptFence {
    public record Frame(Object player,Object world,Object connection,Object inputOwner,long epoch,
            int selectedSlot,List<?> inventory,List<?> gear,String settings,boolean nativeReady){
        public Frame{inventory=inventory==null?List.of():List.copyOf(inventory);gear=gear==null?List.of():List.copyOf(gear);}
    }
    private Frame original;private long generation,issued,deadline;private int expectedSlot=-1;private boolean active;
    public boolean begin(long generation,long issued,long originalDeadline,Frame frame){
        if(frame==null||!frame.nativeReady()||frame.player()==null||frame.world()==null||frame.connection()==null||frame.inputOwner()==null
                ||frame.selectedSlot()<0||frame.selectedSlot()>8||frame.inventory().size()<36||frame.gear().size()!=5||frame.settings()==null||originalDeadline<issued)return false;
        original=frame;this.generation=generation;this.issued=issued;deadline=originalDeadline;expectedSlot=frame.selectedSlot();active=true;return true;
    }
    public boolean valid(long generation,long now,Frame frame){return active&&this.generation==generation&&now>=issued&&now<=deadline&&same(frame)&&frame.selectedSlot()==expectedSlot;}
    private boolean same(Frame f){return f!=null&&f.nativeReady()&&f.player()==original.player()&&f.world()==original.world()&&f.connection()==original.connection()
            &&f.inputOwner()==original.inputOwner()&&f.epoch()==original.epoch()&&f.inventory().equals(original.inventory())&&f.gear().equals(original.gear())&&Objects.equals(f.settings(),original.settings());}
    public boolean selected(long generation,long now,int slot,Frame actual){
        if(!active||this.generation!=generation||now<issued||now>deadline||slot<0||slot>8||!same(actual)||actual.selectedSlot()!=slot)return false;
        expectedSlot=slot;return true;
    }
    public List<?> inventoryBaseline(){return original==null?List.of():original.inventory();}
    public int originalHeldSlot(){return original==null?-1:original.selectedSlot();}
    /** All original fences except the inventory equality being justified by native pickup receipts. */
    public boolean harvestCustody(long generation,long now,Frame f){return active&&this.generation==generation&&now>=issued&&now<=deadline&&f!=null&&f.nativeReady()
            &&sameNativeScope(f)&&f.selectedSlot()==expectedSlot&&f.gear().equals(original.gear())&&Objects.equals(f.settings(),original.settings());}
    public boolean admitHarvest(long generation,long now,Frame actual,FigHarvestPickupLedger.Permit permit,FigHarvestPickupLedger ledger,FigHarvestPickupLedger.Scope currentTreeScope){
        if(!harvestCustody(generation,now,actual)||permit==null||!permit.matches(original.inputOwner(),original.player(),original.world(),original.connection(),generation,original.inventory(),actual.inventory()))return false;
        if(ledger==null)return false;
        var before=original;var after=new Frame(before.player(),before.world(),before.connection(),before.inputOwner(),before.epoch(),before.selectedSlot(),permit.after(),before.gear(),before.settings(),before.nativeReady());
        // Ledger prevalidates pending/current tree scope before this internal nonthrowing client-thread CAS.
        return ledger.commit(permit,currentTreeScope,()->{if(original!=before)return false;original=after;return true;});
    }
    /** Complete wear receipts can change only the trusted action's owned axe cell; current selection stays guarded. */
    public boolean admitWear(long generation,long now,Frame actual,int axeSlot,FigToolWearLedger.Permit wear,
            FigToolWearLedger wearLedger,FigHarvestPickupLedger.Scope currentTreeScope,
            FigHarvestPickupLedger.Permit harvest,FigHarvestPickupLedger harvestLedger){
        if(!harvestCustody(generation,now,actual)||axeSlot<0||axeSlot>8||wear==null||wearLedger==null||currentTreeScope==null)return false;
        var before=original;var projected=wear.after();
        if(projected.size()!=before.inventory().size()||axeSlot>=projected.size()||Objects.equals(projected.get(axeSlot),before.inventory().get(axeSlot)))return false;
        // Do not let a permit replace any unrelated inventory cell, including another tool slot.
        for(int i=0;i<projected.size();i++)if(i!=axeSlot&&!Objects.equals(projected.get(i),before.inventory().get(i)))return false;
        if(!wear.boundTo(generation,before.epoch(),before.settings(),deadline)
                ||!wear.matches(before.inputOwner(),before.player(),before.world(),before.connection(),generation,before.inventory(),projected))return false;
        if(harvest==null){if(!projected.equals(actual.inventory()))return false;}
        else if(harvestLedger==null||!harvest.matches(before.inputOwner(),before.player(),before.world(),before.connection(),generation,projected,actual.inventory())
                ||!Objects.equals(harvest.after().get(axeSlot),projected.get(axeSlot)))return false;
        var committed=new ArrayList<Object>(before.inventory());committed.set(axeSlot,projected.get(axeSlot));
        if(harvest!=null){var delivered=harvest.after();for(int i=0;i<committed.size();i++)if(!Objects.equals(delivered.get(i),projected.get(i)))committed.set(i,delivered.get(i));}
        var after=new Frame(before.player(),before.world(),before.connection(),before.inputOwner(),before.epoch(),before.selectedSlot(),committed,before.gear(),before.settings(),before.nativeReady());
        java.util.function.BooleanSupplier cas=()->{if(original!=before||!harvestCustody(generation,now,actual))return false;original=after;return true;};
        // Both ledgers prevalidate before the single nonthrowing client-thread fence CAS.
        return wearLedger.commit(wear,currentTreeScope,()->harvest==null?cas.getAsBoolean():harvestLedger.commit(harvest,currentTreeScope,cas));
    }
    public boolean sameNativeScope(Frame f){return original!=null&&f!=null&&f.player()==original.player()&&f.world()==original.world()&&f.connection()==original.connection()&&f.inputOwner()==original.inputOwner()&&f.epoch()==original.epoch();}
    public void cancel(){active=false;generation++;}
    public int expectedSlot(){return active?expectedSlot:-1;}
    public long deadline(){return deadline;}
}
