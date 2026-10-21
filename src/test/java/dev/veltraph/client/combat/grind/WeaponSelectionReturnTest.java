package dev.veltraph.client.combat.grind;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Pure ordering/source-refusal regression; fake histories cannot grant the native adapter any permission. */
class WeaponSelectionReturnTest {
 static final class Frame implements WeaponSelectionReturn.Effects {
  final Object originalPlayer=new Object(),originalWorld=new Object(),originalConnection=new Object(),originalRoot=new Object();
  Object player=originalPlayer,world=originalWorld,connection=originalConnection,root=originalRoot;
  final Object originalDeclaredParent=new Object();Object declaredParent=originalDeclaredParent,actualInputOwner=originalDeclaredParent;
  long originalHistory=12,history=12,configEpoch=3;int selected=0,writes,packets;boolean producer=true,normalReturn=true,realStop=true,items=true,inFlight,menu;
  boolean mutateAtNativeFence,throwAtNativeFence;final ArrayList<String> calls=new ArrayList<>();
  public boolean current(){calls.add("source");return producer&&normalReturn&&realStop&&!inFlight&&!menu&&items&&selected==0&&history==originalHistory&&configEpoch==3
    &&player==originalPlayer&&world==originalWorld&&connection==originalConnection&&root==originalRoot&&declaredParent==originalDeclaredParent&&actualInputOwner==originalDeclaredParent;}
  public boolean submit(){calls.add("native-before");if(mutateAtNativeFence){history+=2;selected=0;}if(throwAtNativeFence)throw new IllegalStateException("native failure");
    if(!current())return false;writes++;selected=4;history+=2;packets++;calls.add("native-return");return true;}
  void set(int value){selected=value;history+=2;}
 }
 @Test void genuineUntouchedOwnedWeaponReturnsOriginalOnce(){var f=new Frame();var custody=new WeaponSelectionReturn();assertTrue(custody.restore(f));assertEquals(4,f.selected);assertEquals(1,f.writes);assertEquals(1,f.packets);assertFalse(custody.restore(f));assertEquals(1,f.writes);}
 @Test void observedManualSameSlotSetterRevokesValueEqualReturn(){var f=new Frame();f.set(0);assertFalse(new WeaponSelectionReturn().restore(f));assertEquals(0,f.selected);assertEquals(0,f.writes);assertEquals(0,f.packets);}
 @Test void manualSlotAbaCannotRecoverOldHistory(){var f=new Frame();f.set(6);f.set(0);assertFalse(new WeaponSelectionReturn().restore(f));assertEquals(0,f.writes);}
 @Test void laterFinishingThenManualPrimaryCannotLaunderBestiarySource(){var f=new Frame();f.set(7);f.set(0);assertFalse(new WeaponSelectionReturn().restore(f));assertEquals(0,f.writes);}
 @Test void nativeChangedSlotCannotBeOverwritten(){var f=new Frame();f.set(6);assertFalse(new WeaponSelectionReturn().restore(f));assertEquals(6,f.selected);assertEquals(0,f.writes);}
 @Test void originalContextAndRootAllRequireIdentity(){for(int i=0;i<4;i++){var f=new Frame();switch(i){case 0->f.player=new Object();case 1->f.world=new Object();case 2->f.connection=new Object();default->f.root=new Object();}assertFalse(new WeaponSelectionReturn().restore(f));assertEquals(0,f.writes);}}
 @Test void unknownForeignOrFailedProducerCannotCreateReturn(){for(int i=0;i<3;i++){var f=new Frame();if(i==0)f.producer=false;else if(i==1)f.normalReturn=false;else f.inFlight=true;assertFalse(new WeaponSelectionReturn().restore(f));assertEquals(0,f.writes);}}
 @Test void configAbaEpochAndChangedOriginalItemMenuOrFakeStopRefuse(){for(int i=0;i<4;i++){var f=new Frame();switch(i){case 0->f.configEpoch+=2;case 1->f.items=false;case 2->f.menu=true;default->f.realStop=false;}assertFalse(new WeaponSelectionReturn().restore(f));assertEquals(0,f.writes);}}
 @Test void lateNativeFenceObservesNewSameSlotHistory(){var f=new Frame();f.mutateAtNativeFence=true;assertFalse(new WeaponSelectionReturn().restore(f));assertEquals(0,f.writes);assertEquals(0,f.packets);}
 @Test void failedNativeReturnKeepsPrimaryExceptionAndCannotRetry(){var f=new Frame();f.throwAtNativeFence=true;var custody=new WeaponSelectionReturn();var failure=assertThrows(IllegalStateException.class,()->custody.restore(f));assertEquals("native failure",failure.getMessage());f.throwAtNativeFence=false;assertFalse(custody.restore(f));assertEquals(0,f.writes);}
 @Test void unrelatedActualRootOwnerCannotLaunderDeclaredParent(){var f=new Frame();f.actualInputOwner=new Object();assertFalse(new WeaponSelectionReturn().restore(f));assertEquals(0,f.writes);}
 @Test void declaredParentReplacementCannotRefreshOldFrame(){var f=new Frame();f.declaredParent=new Object();f.actualInputOwner=f.declaredParent;assertFalse(new WeaponSelectionReturn().restore(f));assertEquals(0,f.writes);}
 @Test void reentrantRestoreCannotSpendCapturedReturnTwice(){var custody=new WeaponSelectionReturn();var f=new Frame();assertTrue(custody.restore(new WeaponSelectionReturn.Effects(){public boolean current(){assertFalse(custody.restore(f));return f.current();}public boolean submit(){return f.submit();}}));assertEquals(1,f.writes);}
}
