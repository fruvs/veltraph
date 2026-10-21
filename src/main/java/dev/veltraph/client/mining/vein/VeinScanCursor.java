package dev.veltraph.client.mining.vein;
import java.util.*;
/** Incremental bounded ranking; a completed cached candidate is advisory until a fresh commit proof. */
public final class VeinScanCursor<T> {
 public record Ranked<T>(T value,double cost,long ordinal){}
 private final int count;private final Object scope;private final long deadline;
 private int index;private Ranked<T> best;private boolean retired;
 public VeinScanCursor(int count,Object scope,long deadline){if(count<0||count>16384||scope==null||deadline<=0)throw new IllegalArgumentException("Invalid scan");this.count=count;this.scope=scope;this.deadline=deadline;}
 public boolean live(Object current,long now){return !retired&&Objects.equals(scope,current)&&now<deadline;}
 public int index(){return index;}
 public boolean complete(){return !retired&&index==count;}
 /** false means no successful probe; current cell must be retried, never silently skipped. */
 public boolean advance(Object current,long now,boolean probeCompleted,T value,double cost){
  if(!live(current,now)||index>=count){retired=true;return false;}
  if(!probeCompleted)return false;
  if(value!=null){if(!Double.isFinite(cost)||cost<0)throw new IllegalArgumentException("Invalid cost");var choice=new Ranked<>(value,cost,index);if(best==null||cost<best.cost)best=choice;}
  index++;return true;
 }
 public Optional<Ranked<T>> best(Object current,long now){return complete()&&live(current,now)?Optional.ofNullable(best):Optional.empty();}
 public void retire(){retired=true;best=null;}
}
