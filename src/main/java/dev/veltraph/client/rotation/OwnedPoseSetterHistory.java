package dev.veltraph.client.rotation;
/** Actual outer setter attempt history; private caller envelopes are observation evidence only. */
public final class OwnedPoseSetterHistory {
 private final NormalIdleInputVersion yaw=new NormalIdleInputVersion(),pitch=new NormalIdleInputVersion();
 public static final class Snapshot {
  private final OwnedPoseSetterHistory owner;private final NormalIdleInputVersion.Snapshot yaw,pitch;
  private Snapshot(OwnedPoseSetterHistory owner,NormalIdleInputVersion.Snapshot yaw,NormalIdleInputVersion.Snapshot pitch){this.owner=owner;this.yaw=yaw;this.pitch=pitch;}
 }
 public void begin(boolean yawAxis,Object actual,boolean nativeThread){(yawAxis?yaw:pitch).begin(actual,nativeThread);}
 public void returned(boolean yawAxis){(yawAxis?yaw:pitch).end(false);}
 public void invalidate(){yaw.invalidate();pitch.invalidate();}
 public Snapshot snapshot(){var y=yaw.snapshot();var x=pitch.snapshot();return y==null||x==null?null:new Snapshot(this,y,x);}
 public boolean current(Object exact){return exact instanceof Snapshot s&&s.owner==this&&yaw.current(s.yaw)&&pitch.current(s.pitch);}
 public boolean advanced(Object before,Object after,java.util.List<?> actualYaw,java.util.List<?> actualPitch){return before instanceof Snapshot a&&after instanceof Snapshot b
  &&a.owner==this&&b.owner==this&&current(b)&&yaw.advancedOnly(a.yaw,b.yaw,actualYaw)&&pitch.advancedOnly(a.pitch,b.pitch,actualPitch);}
}
