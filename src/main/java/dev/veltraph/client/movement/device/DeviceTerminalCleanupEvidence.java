package dev.veltraph.client.movement.device;
/** Evidence predicate only; successful checks cannot construct the private native terminal receipt. */
public final class DeviceTerminalCleanupEvidence {
 private DeviceTerminalCleanupEvidence(){}
 public record Input(boolean arrived,boolean dispatched,boolean qualifiedApplied,boolean legCurrent,boolean inventoryPermission,boolean exactInventory,
                     long beforeEpoch,long afterEpoch,long beforeGeneration,long afterGeneration,long ownTicket,long actualPublicationSerial,
                     long acceptedSerial,long latestSerial,int originalSlot,int currentSlot){}
 public static boolean valid(Input i){return i!=null&&i.arrived()&&i.dispatched()&&i.qualifiedApplied()&&i.legCurrent()&&i.inventoryPermission()&&i.exactInventory()
  &&i.beforeEpoch()>=0&&i.afterEpoch()>i.beforeEpoch()&&i.afterEpoch()-i.beforeEpoch()==1
  &&i.beforeGeneration()>=0&&i.afterGeneration()>i.beforeGeneration()&&i.afterGeneration()-i.beforeGeneration()==1
  &&i.ownTicket()>0&&i.actualPublicationSerial()==i.ownTicket()&&i.acceptedSerial()>0&&i.acceptedSerial()==i.latestSerial()
  &&i.originalSlot()>=0&&i.originalSlot()<9&&i.currentSlot()==i.originalSlot();}
}
