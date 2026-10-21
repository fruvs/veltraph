package dev.veltraph.client.combat.grind;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
/** Ordering/identity only: private native producer must independently supply its actual receipt/history/source leaf. */
public final class WeaponSelectionParentBoundary {
 private WeaponSelectionParentBoundary(){}
 public static boolean active(Object captured,Supplier<?> declared,Supplier<?> actualOwner,BooleanSupplier nativeLeaf){
  return captured!=null&&declared!=null&&actualOwner!=null&&nativeLeaf!=null
   &&declared.get()==captured&&actualOwner.get()==captured&&declared.get()==captured&&nativeLeaf.getAsBoolean();
 }
 public static boolean afterFinalCallback(BooleanSupplier callback,BooleanSupplier exactNativeHistory,Object captured,
         Supplier<?> declared,Supplier<?> actualOwner,BooleanSupplier nativeLeaf){
  return callback!=null&&exactNativeHistory!=null&&callback.getAsBoolean()&&exactNativeHistory.getAsBoolean()
   &&active(captured,declared,actualOwner,nativeLeaf)&&exactNativeHistory.getAsBoolean();
 }
}
