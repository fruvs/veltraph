package dev.veltraph.client.mining.vein;
/** Both authoritative observations must follow this issued operation; packet order is irrelevant. */
public final class VeinCompletionFence {
 public enum Result { WAIT, READY, EXPIRED, REJECTED }
 private final long issued,deadline;private long airAt=-1,inventoryAt=-1;private boolean rejected,spent;
 public VeinCompletionFence(long issued,long deadline){if(issued<0||deadline<=issued||deadline==Long.MAX_VALUE)throw new IllegalArgumentException("Invalid deadline");this.issued=issued;this.deadline=deadline;}
 public void air(long at){if(at<issued||at>=deadline)rejected=true;else if(airAt<0)airAt=at;}
 public void inventory(long at,boolean qualifiedTool){if(at<issued||at>=deadline||!qualifiedTool)rejected=true;else if(inventoryAt<0)inventoryAt=at;}
 public boolean unchangedInventory(long at,boolean coversTool,boolean knownZeroCost){
  if(!coversTool||at<issued||at>=deadline)return false;
  if(!knownZeroCost&&(airAt<0||at<=airAt))return false;
  inventory(at,true);return true;
 }
 public Result result(long now,boolean liveScope){if(spent||rejected||!liveScope||now<issued)return Result.REJECTED;if(now>=deadline)return Result.EXPIRED;return airAt>=0&&inventoryAt>=0?Result.READY:Result.WAIT;}
 public boolean consume(long now,boolean liveScope){if(result(now,liveScope)!=Result.READY)return false;spent=true;return true;}
}
