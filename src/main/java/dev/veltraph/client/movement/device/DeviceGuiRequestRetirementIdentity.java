package dev.veltraph.client.movement.device;
/** Refusal-only shape checks after the real private native and same-Leg GUI receipts. Never authority. */
public final class DeviceGuiRequestRetirementIdentity {
 private DeviceGuiRequestRetirementIdentity(){}
 public record Input(Object expectedSource,Object actualSource,long heldGeneration,long beforeGeneration,long afterGeneration,
   long heldEpoch,long originalGuiEpoch,long beforeRetirementEpoch,long afterRetirementEpoch,long originalGuiAfterEpoch,long currentGuiEpoch,long retiredTicket,long currentTicket){}
 public static boolean valid(Input i){return i!=null&&i.expectedSource()!=null&&i.expectedSource()==i.actualSource()
   &&i.heldGeneration()>=0&&i.heldGeneration()<Long.MAX_VALUE&&i.beforeGeneration()==i.heldGeneration()&&i.afterGeneration()==i.heldGeneration()+1
   &&i.heldEpoch()>=0&&i.heldEpoch()<Long.MAX_VALUE&&i.originalGuiEpoch()==i.heldEpoch()&&i.originalGuiAfterEpoch()>i.heldEpoch()
   &&i.beforeRetirementEpoch()==i.afterRetirementEpoch()&&i.afterRetirementEpoch()==i.originalGuiAfterEpoch()&&i.currentGuiEpoch()>=i.originalGuiAfterEpoch()
   &&i.retiredTicket()>0&&i.currentTicket()==i.retiredTicket();}
}
