package dev.veltraph.client.mining.vein;
import dev.veltraph.client.galatea.FigHarvestPickupLedger.Scope;
import dev.veltraph.client.galatea.FigToolWearLedger;
import java.util.*;
/** Native bridge joins actual submissions/AFTER observations; this ledger issues no generic damage exemption. */
public final class VeinWearFence {
 private final FigToolWearLedger ledger=new FigToolWearLedger();
 private final Scope scope;private final long generation,epoch,deadline;private final String settings;private final int slot;
 private List<?> expected;private boolean retired;
 public VeinWearFence(Scope scope,long generation,long epoch,String settings,long deadline,int slot,List<?> expected){
  this.scope=Objects.requireNonNull(scope);this.generation=generation;this.epoch=epoch;this.settings=Objects.requireNonNull(settings);this.deadline=deadline;this.slot=slot;this.expected=List.copyOf(expected);
  if(slot<0||slot>=this.expected.size()||generation<0||epoch<0||deadline<=0||deadline==Long.MAX_VALUE)throw new IllegalArgumentException("Invalid wear scope");
 }
 public boolean stage(FigToolWearLedger.Receipt receipt){return !retired&&ledger.stage(receipt);}
 public java.util.Optional<dev.veltraph.client.galatea.FigToolWearLedger.Permit> prepare(Scope current,List<?> actual,long now,long actualEpoch,String actualSettings,long actualDeadline){
  if(retired||!scope.same(current)||actual==null||actual.size()!=expected.size()||epoch!=actualEpoch||!settings.equals(actualSettings)||deadline!=actualDeadline||now>deadline)return java.util.Optional.empty();
  return ledger.review(current,generation,slot,expected,actual,now,epoch,settings,deadline).filter(p->p.boundTo(generation,epoch,settings,deadline)&&p.matches(scope.owner(),scope.player(),scope.world(),scope.connection(),generation,expected,p.after()));
 }
 public boolean commitPrepared(dev.veltraph.client.galatea.FigToolWearLedger.Permit permit,Scope current,java.util.function.BooleanSupplier cas){
  var original=expected;if(retired||!scope.same(current)||permit==null||!permit.boundTo(generation,epoch,settings,deadline)||!permit.matches(scope.owner(),scope.player(),scope.world(),scope.connection(),generation,original,permit.after()))return false;
  return ledger.commit(permit,current,()->{if(retired||expected!=original||!cas.getAsBoolean())return false;expected=java.util.List.copyOf(permit.after());return true;});
 }
 public java.util.Optional<VeinInventoryAdoption.Permission> permission(Scope current,List<?> actual,long now,long actualEpoch,String actualSettings,long actualDeadline){
  if(retired||!scope.same(current)||actual==null||actual.size()!=expected.size()||epoch!=actualEpoch||!settings.equals(actualSettings)||deadline!=actualDeadline||now>deadline)return java.util.Optional.empty();
  var permit=ledger.review(current,generation,slot,expected,actual,now,epoch,settings,deadline).orElse(null);
  if(permit==null||!permit.boundTo(generation,epoch,settings,deadline)||!permit.matches(scope.owner(),scope.player(),scope.world(),scope.connection(),generation,expected,permit.after()))return java.util.Optional.empty();
  var original=expected;
  return java.util.Optional.of(new VeinInventoryAdoption.Permission(){
   public boolean fresh(){return !retired&&expected==original&&permit.boundTo(generation,epoch,settings,deadline)&&permit.matches(scope.owner(),scope.player(),scope.world(),scope.connection(),generation,original,permit.after());}
   public java.util.List<?> before(){return original;}public java.util.List<?> after(){return permit.after();}
   public boolean commit(java.util.function.BooleanSupplier cas){return fresh()&&ledger.commit(permit,current,()->{if(!fresh()||!cas.getAsBoolean())return false;expected=java.util.List.copyOf(permit.after());return true;});}
  });
 }
 public boolean accept(Scope current,List<?> actual,long now,long actualEpoch,String actualSettings,long actualDeadline){
  if(retired||!scope.same(current)||actual==null||actual.size()!=expected.size()||epoch!=actualEpoch||!settings.equals(actualSettings)||deadline!=actualDeadline||now>deadline)return false;
  var permit=ledger.review(current,generation,slot,expected,actual,now,epoch,settings,deadline).orElse(null);
  if(permit==null||!permit.boundTo(generation,epoch,settings,deadline)||!permit.matches(scope.owner(),scope.player(),scope.world(),scope.connection(),generation,expected,permit.after()))return false;
  // Review projects the tool only. Every other inventory cell must remain exact; pickups need a separate fence.
  if(!permit.after().equals(actual))return false;var before=expected;
  return ledger.commit(permit,current,()->{if(retired||expected!=before)return false;expected=List.copyOf(actual);return true;});
 }
 public List<?> expected(){return expected;}
 public void retire(){retired=true;ledger.clear();}
}
