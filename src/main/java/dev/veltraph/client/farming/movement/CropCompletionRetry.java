package dev.veltraph.client.farming.movement;
import dev.veltraph.client.world.BlockPos;
/** Bounded two-second retry exclusion; backwards clock revokes the exclusion. */
public final class CropCompletionRetry {
 private BlockPos cell;private long after;
 public void exclude(BlockPos cell,long now){this.cell=cell;after=now;}
 public BlockPos avoided(long now){if(cell==null)return null;if(now<after||now-after>=2000){clear();return null;}return cell;}
 public void clear(){cell=null;after=0;}
 public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState(){return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,"cell",cell,"after",after);}
}
