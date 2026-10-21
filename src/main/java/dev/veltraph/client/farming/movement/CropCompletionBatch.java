package dev.veltraph.client.farming.movement;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.*;
/** One bounded owned look-back window; completed targets do not cause repeated row-view restoration. */
public final class CropCompletionBatch {
 private final Set<BlockPos> visited=new HashSet<>();private long started;private boolean active,restoring;private int restored;private Vec3 lastEnd;private long ended;private RowEvidence previousRow;private double horizontalProgress;
 public record RowScope(Object owner,Object player,Object world,Object connection,long epoch){boolean same(RowScope other){return other!=null&&owner==other.owner&&player==other.player&&world==other.world&&connection==other.connection&&epoch==other.epoch;}}
 public record RowEvidence(RowScope scope,long tick,Vec3 feet,double dirX,double dirZ,boolean ownedFarmingSideways) {}
 public boolean canStart(Vec3 feet,long now){if(lastEnd==null)return true;if(now<ended)return false;return now-ended>=1000&&horizontalProgress>=1&&previousRow!=null&&Math.abs(feet.y()-previousRow.feet.y())<=.02&&Math.hypot(feet.x()-previousRow.feet.x(),feet.z()-previousRow.feet.z())<=.5;}
 public boolean canStart(Vec3 feet,long now,RowScope current){return lastEnd==null||current!=null&&previousRow!=null&&previousRow.scope.same(current)&&canStart(feet,now);}
 public void observePrimary(RowEvidence evidence){
  if(active||lastEnd==null)return;
  if(evidence==null||!evidence.ownedFarmingSideways()||evidence.scope()==null){previousRow=null;horizontalProgress=0;return;}
  if(previousRow!=null){
   double dx=evidence.feet.x()-previousRow.feet.x(),dy=evidence.feet.y()-previousRow.feet.y(),dz=evidence.feet.z()-previousRow.feet.z(),length=Math.hypot(dx,dz),dot=dx*evidence.dirX+dz*evidence.dirZ;
   if(!previousRow.scope.same(evidence.scope)||Math.abs(previousRow.dirX-evidence.dirX)>1e-9||Math.abs(previousRow.dirZ-evidence.dirZ)>1e-9||evidence.tick!=previousRow.tick+1||Math.abs(dy)>.02||length>.5||dot<0||Math.abs(dx*evidence.dirZ-dz*evidence.dirX)>.05){horizontalProgress=0;previousRow=evidence;return;}
   horizontalProgress+=Math.max(0,dot);
  }
  previousRow=evidence;
 }
 public void start(long now){visited.clear();started=now;active=true;restoring=false;restored=0;}
 public void visited(BlockPos cell){visited.add(cell);}public Set<BlockPos> visited(){return Set.copyOf(visited);}
 public boolean expired(long now){return !active||now<started||now-started>=8000||visited.size()>=12;}
 public void restore(){restoring=true;}public boolean restoring(){return restoring;}
 public void finish(Vec3 feet,long now){if(active&&restoring)restored++;active=false;restoring=false;lastEnd=feet;ended=now;previousRow=null;horizontalProgress=0;}
 public void cancel(){active=false;restoring=false;visited.clear();lastEnd=null;previousRow=null;horizontalProgress=0;}
 public boolean active(){return active;}public int restoreCount(){return restored;}
 public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState(){return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,"visited",visited,"started",started,"active",active,"restoring",restoring,"restored",restored,"lastEnd",lastEnd,"ended",ended,"previousRow",previousRow,"horizontalProgress",horizontalProgress);}
}
