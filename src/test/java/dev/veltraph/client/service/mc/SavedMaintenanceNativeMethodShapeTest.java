package dev.veltraph.client.service.mc;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Shape data are synthetic; no test mints private native instructions or publication authority. */
class SavedMaintenanceNativeMethodShapeTest {
 private final Object input=new Object(),key=new Object();
 private boolean outer(boolean noParent,boolean notEntered,Object installed,Object receiver,boolean request,boolean restore,boolean base,boolean plain){
  return SavedMaintenanceNativeMethodShape.outer(true,noParent,notEntered,input,installed,key,receiver,true,request,false,restore,base,plain,true);
 }
 @Test void exactPendingPlainOperationMatches(){assertTrue(outer(true,true,input,key,true,false,true,true));}
 @Test void sameKeyForeignNestedEntryIsOrdinary(){assertFalse(outer(false,true,input,key,true,false,true,true));}
 @Test void secondMethodEntryCannotBorrowPrivatePending(){assertFalse(outer(true,false,input,key,true,false,true,true));}
 @Test void differentActualInvocationCannotSelect(){assertFalse(outer(true,true,new Object(),key,true,false,true,true));}
 @Test void receiverAliasCannotSelect(){assertFalse(outer(true,true,input,new Object(),true,false,true,true));}
 @Test void falseRequestCannotBorrowTrueInstruction(){assertFalse(outer(true,true,input,key,false,false,true,true));}
 @Test void restoreCannotBorrowSetterInstruction(){assertFalse(outer(true,true,input,key,true,true,false,false));}
 @Test void toggleBaseCannotClaimOuterPlainEntry(){assertFalse(outer(true,true,input,key,true,false,true,false));}
 @Test void noPendingContextIsOrdinary(){assertFalse(SavedMaintenanceNativeMethodShape.outer(true,true,true,null,null,key,key,true,true,false,false,true,true,true));}
 @Test void actualCookieMustMatchExactParentAndBaseArgument(){Object parent=new Object(),context=new Object();assertTrue(SavedMaintenanceNativeMethodShape.child(true,true,true,true,parent,parent,context,context,input,input,input,key,key,false,false,key,false,false));assertFalse(SavedMaintenanceNativeMethodShape.child(true,true,true,true,parent,parent,context,context,input,input,input,key,key,false,true,key,false,false));}
 @Test void consumedCookieCannotBeReused(){Object parent=new Object(),context=new Object();assertFalse(SavedMaintenanceNativeMethodShape.child(true,true,true,false,parent,parent,context,context,input,input,input,key,key,true,true,key,false,false));}
 @Test void foreignParentCannotBorrowSuperCookie(){Object parent=new Object(),context=new Object();assertFalse(SavedMaintenanceNativeMethodShape.child(true,true,true,true,new Object(),parent,context,context,input,input,input,key,key,true,true,key,false,false));}
 @Test void nullParentStillHasActiveOrdinaryFallbackDepth(){int depth=SavedMaintenanceNativeMethodShape.enterOrdinaryFallback(0);assertEquals(1,depth);assertEquals(0,SavedMaintenanceNativeMethodShape.leaveOrdinaryFallback(depth));}
 @Test void nestedFallbackCloseRetainsOuterOrdinaryClassification(){int depth=SavedMaintenanceNativeMethodShape.enterOrdinaryFallback(0);depth=SavedMaintenanceNativeMethodShape.enterOrdinaryFallback(depth);assertEquals(1,SavedMaintenanceNativeMethodShape.leaveOrdinaryFallback(depth));}
 @Test void fallbackOverflowRefusesWithoutErasingAncestor(){assertThrows(IllegalStateException.class,()->SavedMaintenanceNativeMethodShape.enterOrdinaryFallback(Integer.MAX_VALUE));assertThrows(IllegalStateException.class,()->SavedMaintenanceNativeMethodShape.leaveOrdinaryFallback(0));}
}
