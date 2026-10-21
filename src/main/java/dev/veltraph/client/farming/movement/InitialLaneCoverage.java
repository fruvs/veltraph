package dev.veltraph.client.farming.movement;
import dev.veltraph.client.world.Direction;
import dev.veltraph.client.world.Vec3;
/** Modern opt-in coverage of the initial partially entered lane, without inventing crop coordinates. */
public final class InitialLaneCoverage {
 public enum Decision { REVERSE, EXIT, DENY }
 private boolean enabled;private Direction heading;private TurnDirection initial;private Vec3 firstEnd,otherEnd;private int phase;
 public void start(boolean enabled,Direction heading,TurnDirection initial){this.enabled=enabled;this.heading=heading;this.initial=initial;firstEnd=null;otherEnd=null;phase=0;}
 public boolean requiresForwardExitProof(){return phase!=1;}
 public boolean pending(){return enabled&&phase<3;}
 public Decision end(TurnDirection side,Vec3 pose,boolean provedEnd,boolean returnOpen){
  if(!pending())return Decision.EXIT;
  if(!provedEnd||!returnOpen||pose==null||!Double.isFinite(pose.x())||!Double.isFinite(pose.y())||!Double.isFinite(pose.z()))return Decision.DENY;
  if(firstEnd!=null&&Math.abs(pose.y()-firstEnd.y())>.025)return Decision.DENY;
  if(phase==0){if(side!=initial)return Decision.DENY;firstEnd=pose;phase=1;return Decision.REVERSE;}
  if(phase==1){if(side!=initial.opposite()||Math.abs((pose.x()-firstEnd.x())*heading.dx()+(pose.z()-firstEnd.z())*heading.dz())>.35||pose.horizontalDistanceTo(firstEnd)<1)return Decision.DENY;otherEnd=pose;phase=2;return Decision.REVERSE;}
  if(side!=initial||pose.horizontalDistanceTo(firstEnd)>.35)return Decision.DENY;
  phase=3;return Decision.EXIT;
 }
 public void cancel(){enabled=false;phase=3;}
 public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState(){return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,"enabled",enabled,"heading",heading,"initial",initial,"firstEnd",firstEnd,"otherEnd",otherEnd,"phase",phase);}
}
