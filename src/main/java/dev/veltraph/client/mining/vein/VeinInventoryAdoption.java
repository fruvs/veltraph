package dev.veltraph.client.mining.vein;
import java.util.*;
import java.util.function.BooleanSupplier;
/** Joint cell projection. Each permission is independently scoped; no skipped native inventory cells. */
public final class VeinInventoryAdoption {
 public interface Permission {
  boolean fresh();
  List<?> before();
  List<?> after();
  boolean commit(BooleanSupplier exactCas);
 }
 private VeinInventoryAdoption(){}
 public static boolean adopt(List<?> original,List<?> actual,int toolSlot,Permission wear,Permission pickup,BooleanSupplier scope,BooleanSupplier cas){
  if(original==null||actual==null||original.size()!=36||actual.size()!=36||toolSlot<0||toolSlot>8||scope==null||cas==null||!scope.getAsBoolean())return false;
  var projected=new ArrayList<Object>(original);
  if(wear!=null){if(!wear.fresh()||!wear.before().equals(original)||wear.after().size()!=36)return false;for(int i=0;i<36;i++)if(i!=toolSlot&&!Objects.equals(original.get(i),wear.after().get(i)))return false;projected.set(toolSlot,wear.after().get(toolSlot));}
  if(pickup!=null){if(!pickup.fresh()||!pickup.before().equals(projected)||pickup.after().size()!=36||!Objects.equals(projected.get(toolSlot),pickup.after().get(toolSlot)))return false;projected=new ArrayList<>(pickup.after());}
  if(!projected.equals(actual)||!scope.getAsBoolean())return false;
  var finalProjected=List.copyOf(projected);
  BooleanSupplier mutation=()->scope.getAsBoolean()&&finalProjected.equals(actual)&&cas.getAsBoolean();
  if(wear!=null&&pickup!=null)return wear.commit(()->pickup.fresh()&&pickup.commit(mutation));
  if(wear!=null)return wear.commit(mutation);if(pickup!=null)return pickup.commit(mutation);return original.equals(actual)&&mutation.getAsBoolean();
 }
}
