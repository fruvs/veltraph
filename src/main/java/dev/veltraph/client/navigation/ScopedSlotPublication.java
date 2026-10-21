package dev.veltraph.client.navigation;
import java.util.Objects;
/** Ordering/accounting only; the native adapter privately mints the actual effect receipt. */
public final class ScopedSlotPublication {
 private ScopedSlotPublication(){}
 public enum Status { CONFIRMED,REFUSED,FAILED }
 public record Outcome(Status status,boolean localSelected,boolean packetSubmitted){public boolean confirmed(){return status==Status.CONFIRMED;}}
 public interface Effects {int slot();void observe();boolean beforeCurrent();void select(int next);boolean afterCurrent();void submit(int next);}
 public static Outcome apply(int expected,int next,Effects e){
  Objects.requireNonNull(e);boolean selected=false,submitted=false;
  try {
   if(expected<0||expected>8||next<0||next>8||e.slot()!=expected)return new Outcome(Status.REFUSED,false,false);
   e.observe();if(e.slot()!=expected||!e.beforeCurrent())return new Outcome(Status.REFUSED,false,false);
   e.select(next);selected=true;
   if(e.slot()!=next||!e.afterCurrent())return new Outcome(Status.REFUSED,selected,false);
   e.submit(next);submitted=true;
   return new Outcome(e.slot()==next&&e.afterCurrent()?Status.CONFIRMED:Status.REFUSED,selected,submitted);
  }catch(RuntimeException unavailable){return new Outcome(Status.FAILED,selected,submitted);}
 }
}
