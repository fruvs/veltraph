package dev.veltraph.client.movement.device;
import dev.veltraph.client.world.Vec3;
/** Correlates one actual ordinary send-return and a later native applied position within its lease. */
public final class DeviceSubmissionReceipt {
 private final DeviceUseReceipt.Issue issue;private boolean window,submitted,returned,revoked;private long submittedAt;private DeviceUseReceipt.Applied applied;
 public DeviceSubmissionReceipt(DeviceUseReceipt.Issue issue){this.issue=java.util.Objects.requireNonNull(issue);}
 public void open(){if(!submitted&&!returned&&!revoked)window=true;}
 public void close(){window=false;}
 public boolean windowOpen(){return window&&!submitted&&!revoked;}
 public boolean submitted(DeviceUseReceipt.Context actual,long now){
  if(!window||submitted||revoked||!issue.context().same(actual)||now<issue.issuedAt()||now>=issue.deadline()){revoked=true;return false;}
  submitted=true;submittedAt=now;return true;
 }
 public void returnedNormally(){if(submitted&&!revoked)returned=true;else revoked=true;}
 public boolean dispatched(){return submitted&&returned&&!revoked;}
 public void observe(DeviceUseReceipt.Context actual,long serial,long receivedAt,Vec3 position){
  if(!dispatched()||!issue.context().same(actual)||serial<=issue.baseline()||receivedAt<submittedAt||receivedAt>=issue.deadline()
      ||position==null||!Double.isFinite(position.x())||!Double.isFinite(position.y())||!Double.isFinite(position.z())
      ||position.distanceTo(issue.goal())>issue.tolerance()){revoked=true;return;}
  if(applied==null)applied=new DeviceUseReceipt.Applied(issue.context(),issue.domain(),issue.nonce(),serial,receivedAt,position);
 }
 public void revoke(){revoked=true;window=false;}
 public DeviceUseReceipt.Applied applied(){return !revoked&&dispatched()?applied:null;}
 public boolean revoked(){return revoked;}
}
