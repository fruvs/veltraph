package dev.veltraph.client.navigation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ScopedSlotPublicationTest {
 static final class F implements ScopedSlotPublication.Effects {
  int slot=2,setters,packets,observers;long generation=1,admission=7,serial=4;Object player=new Object(),captured=player,menu=new Object(),capturedMenu=menu;boolean cursorEmpty=true;
  Runnable observer=()->{},selectCallback=()->{},beforeCallback=()->{},afterCallback=()->{},packetCallback=()->{};
  public int slot(){return slot;}public void observe(){observers++;observer.run();}
  boolean nativeLeaf(){return generation==1&&admission==7&&serial==4&&player==captured&&menu==capturedMenu&&cursorEmpty;}
  public boolean beforeCurrent(){return ScopedHeldKeyPublication.afterRefusal(()->{beforeCallback.run();return true;},()->nativeLeaf()&&slot==2);}
  public boolean afterCurrent(){return ScopedHeldKeyPublication.afterRefusal(()->{afterCallback.run();return true;},()->nativeLeaf()&&slot==5);}
  public void select(int next){setters++;slot=next;selectCallback.run();}public void submit(int next){packets++;packetCallback.run();}
 }
 @Test void observerReplacesRootPwcAndNoSelectionOrPacketOccurs(){var f=new F();f.observer=()->f.player=new Object();var r=ScopedSlotPublication.apply(2,5,f);assertFalse(r.confirmed());assertEquals(0,f.setters);assertEquals(0,f.packets);assertEquals(2,f.slot);}
 @Test void observerSameParentRequestRefusesBeforeSetter(){var f=new F();f.observer=()->f.generation++;var r=ScopedSlotPublication.apply(2,5,f);assertFalse(r.localSelected());assertFalse(r.packetSubmitted());assertEquals(0,f.setters);}
 @Test void beforeFenceReturnsTrueAfterReentrantAdmissionButNativeLeafRefuses(){var f=new F();f.beforeCallback=()->f.admission+=2;var r=ScopedSlotPublication.apply(2,5,f);assertEquals(0,f.setters);assertEquals(0,f.packets);assertFalse(r.confirmed());}
 @Test void beforeFenceReturnsTrueAfterForeignPlanButLeafSerialRefuses(){var f=new F();f.beforeCallback=()->f.serial++;var r=ScopedSlotPublication.apply(2,5,f);assertEquals(0,f.setters);assertEquals(0,f.packets);}
 @Test void actualSetterThenForeignSupersessionCannotSubmitPacket(){var f=new F();f.selectCallback=()->f.generation++;var r=ScopedSlotPublication.apply(2,5,f);assertTrue(r.localSelected());assertFalse(r.packetSubmitted());assertFalse(r.confirmed());assertEquals(1,f.setters);assertEquals(0,f.packets);}
 @Test void afterFenceReturnsTrueForNewSlotButCallbackChangesPwcAndPacketRefuses(){var f=new F();f.afterCallback=()->f.player=new Object();var r=ScopedSlotPublication.apply(2,5,f);assertTrue(r.localSelected());assertFalse(r.packetSubmitted());assertEquals(0,f.packets);}
 @Test void ownedSelectionUsesOldSlotBeforeAndNewSlotBeforePacket(){var f=new F();var r=ScopedSlotPublication.apply(2,5,f);assertTrue(r.confirmed());assertTrue(r.localSelected());assertTrue(r.packetSubmitted());assertEquals(1,f.observers);assertEquals(1,f.setters);assertEquals(1,f.packets);assertEquals(5,f.slot);}
 @Test void packetSubmissionThenSupersessionIsNotConfirmedOrServerAck(){var f=new F();f.packetCallback=()->f.serial++;var r=ScopedSlotPublication.apply(2,5,f);assertTrue(r.localSelected());assertTrue(r.packetSubmitted());assertFalse(r.confirmed());}
 @Test void beforeSupplierReturnsTrueAfterMenuReplacementButFinalNativeContextRefuses(){var f=new F();f.beforeCallback=()->f.menu=new Object();var r=ScopedSlotPublication.apply(2,5,f);assertFalse(r.localSelected());assertEquals(0,f.setters);assertEquals(0,f.packets);}
 @Test void beforeSupplierReturnsTrueAfterCursorMutationButNoSelectionOccurs(){var f=new F();f.beforeCallback=()->f.cursorEmpty=false;var r=ScopedSlotPublication.apply(2,5,f);assertFalse(r.confirmed());assertEquals(0,f.setters);assertEquals(0,f.packets);}
 @Test void afterSupplierReturnsTrueAfterNewSlotReplacementButNoPacketOccurs(){var f=new F();f.afterCallback=()->f.slot=6;var r=ScopedSlotPublication.apply(2,5,f);assertTrue(r.localSelected());assertFalse(r.packetSubmitted());assertEquals(0,f.packets);}
 @Test void fullDeviceBeforeFenceReentryRevokesReservationBeforeNativeSetter(){var f=new F();var identity=new Object();var root=new Object();var world=new Object();var connection=new Object();var store=new Object();var leg=new Object();var old=new LegacyPublicationSnapshotIdentity.State(leg,identity,root,f.player,world,connection,store,7,1,1,4);f.beforeCallback=()->{f.admission+=2;var actual=new LegacyPublicationSnapshotIdentity.State(leg,identity,root,f.player,world,connection,store,f.admission,1,1,4);assertFalse(LegacyPublicationSnapshotIdentity.same(old,actual));};var result=ScopedSlotPublication.apply(2,5,f);assertFalse(result.localSelected());assertEquals(0,f.setters);assertEquals(0,f.packets);}
 @Test void fullDeviceAfterFenceReentryPreservesLocalResultButRefusesPacket(){var f=new F();f.afterCallback=()->f.admission+=2;var result=ScopedSlotPublication.apply(2,5,f);assertTrue(result.localSelected());assertFalse(result.packetSubmitted());assertFalse(result.confirmed());assertEquals(0,f.packets);}
}
