package dev.veltraph.client.mining.vein;
import dev.veltraph.client.world.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class VeinBreakSessionTest {
 final Object p=new Object(),w=new Object(),c=new Object(),owner=new Object(),camera=new Object();
 final BlockPos cell=new BlockPos(1,71,1);
 VeinBreakSession.Scope scope(){return new VeinBreakSession.Scope(p,w,c,owner,camera,"config-a","pickaxe-components-a",2,1);}
 VeinBreakSession.Frame frame(VeinBreakSession.Scope s,String id,boolean ray,boolean receipt,long serial,long at){return new VeinBreakSession.Frame(s,cell,id,true,true,ray,true,true,true,true,true,receipt,serial,at);}
 VeinBreakSession active(){var session=new VeinBreakSession();assertTrue(session.begin(frame(scope(),"minecraft:gray_wool",false,false,5,1000),1000,12000));assertEquals(VeinBreakSession.State.BREAKING,session.tick(frame(scope(),"minecraft:gray_wool",true,false,5,1050),1050));return session;}
 @Test void aimingNeverRequestsAttack(){var s=new VeinBreakSession();assertTrue(s.begin(frame(scope(),"minecraft:gray_wool",false,false,5,1000),1000,12000));assertEquals(VeinBreakSession.State.AIMING,s.tick(frame(scope(),"minecraft:gray_wool",false,false,5,1050),1050));assertFalse(s.attackRequested());}
 @Test void laterServerAirConfirmsRemoval(){var s=active();assertEquals(VeinBreakSession.State.REMOVED,s.tick(frame(scope(),"minecraft:air",false,true,6,1100),1100));assertFalse(s.attackRequested());}
 @Test void clientPredictedAirDoesNotCreditRemoval(){var s=active();assertEquals(VeinBreakSession.State.WAITING_RECEIPT,s.tick(frame(scope(),"minecraft:air",false,false,6,1100),1100));assertFalse(s.attackRequested());}
 @Test void unrelatedReplacementIsNotCredited(){assertEquals(VeinBreakSession.State.REPLACED,active().tick(frame(scope(),"minecraft:polished_diorite",true,true,6,1100),1100));}
 @Test void oldReceiptDoesNotCreditRemoval(){assertEquals(VeinBreakSession.State.STOPPED,active().tick(frame(scope(),"minecraft:air",false,true,5,1100),1100));}
 @Test void changedWorldStops(){var foreign=new VeinBreakSession.Scope(p,new Object(),c,owner,camera,"config-a","pickaxe-components-a",2,1);var s=active();assertEquals(VeinBreakSession.State.STOPPED,s.tick(frame(foreign,"minecraft:gray_wool",true,false,5,1100),1100));assertFalse(s.attackRequested());}
 @Test void toolComponentsChangedInSameSlotStops(){var changed=new VeinBreakSession.Scope(p,w,c,owner,camera,"config-a","pickaxe-components-b",2,1);assertEquals(VeinBreakSession.State.STOPPED,active().tick(frame(changed,"minecraft:gray_wool",true,false,5,1100),1100));}
 @Test void configRevisionChangeStops(){var changed=new VeinBreakSession.Scope(p,w,c,owner,camera,"config-b","pickaxe-components-a",2,1);assertEquals(VeinBreakSession.State.STOPPED,active().tick(frame(changed,"minecraft:gray_wool",true,false,5,1100),1100));}
 @Test void slotChangeStops(){var changed=new VeinBreakSession.Scope(p,w,c,owner,camera,"config-a","pickaxe-components-a",3,1);assertEquals(VeinBreakSession.State.STOPPED,active().tick(frame(changed,"minecraft:gray_wool",true,false,5,1100),1100));}
 @Test void cameraLoanStops(){var changed=new VeinBreakSession.Scope(p,w,c,owner,new Object(),"config-a","pickaxe-components-a",2,1);assertEquals(VeinBreakSession.State.STOPPED,active().tick(frame(changed,"minecraft:gray_wool",true,false,5,1100),1100));}
 @Test void clockRollbackStops(){assertEquals(VeinBreakSession.State.STOPPED,active().tick(frame(scope(),"minecraft:gray_wool",true,false,5,1000),1000));}
 @Test void staleFrameStops(){assertEquals(VeinBreakSession.State.STOPPED,active().tick(frame(scope(),"minecraft:gray_wool",true,false,5,1050),1400));}
 @Test void timeoutDoesNotRestartOrCredit(){var s=active();assertEquals(VeinBreakSession.State.TIMED_OUT,s.tick(frame(scope(),"minecraft:gray_wool",true,false,5,13000),13000));assertFalse(s.attackRequested());}
 @Test void lateAirAtDeadlineCannotCredit(){assertEquals(VeinBreakSession.State.TIMED_OUT,active().tick(frame(scope(),"minecraft:air",false,true,6,13000),13000));}
 @Test void lateAirAfterDeadlineCannotCredit(){assertEquals(VeinBreakSession.State.TIMED_OUT,active().tick(frame(scope(),"minecraft:air",false,true,6,13001),13001));}
 @Test void predictedAirWaitsThenQualifiedReceiptRemoves(){var s=active();assertEquals(VeinBreakSession.State.WAITING_RECEIPT,s.tick(frame(scope(),"minecraft:air",false,false,6,1100),1100));assertEquals(VeinBreakSession.State.REMOVED,s.tick(frame(scope(),"minecraft:air",false,true,7,1150),1150));}
 @Test void simultaneousAirAndForeignToolCannotCredit(){var foreign=new VeinBreakSession.Scope(p,w,c,owner,camera,"config-a","foreign-components",2,1);assertEquals(VeinBreakSession.State.STOPPED,active().tick(frame(foreign,"minecraft:air",false,true,6,1100),1100));}
 @Test void predictedAirCannotResetDeadline(){var s=active();assertEquals(VeinBreakSession.State.WAITING_RECEIPT,s.tick(frame(scope(),"minecraft:air",false,false,6,12000),12000));assertEquals(VeinBreakSession.State.TIMED_OUT,s.tick(frame(scope(),"minecraft:air",false,false,6,13000),13000));}
 @Test void interactionGuardRequiresExactCurrentCell(){var s=active();var f=frame(scope(),"minecraft:gray_wool",true,false,5,1100);assertTrue(s.permits(cell,f,1100));assertFalse(s.permits(cell.add(1,0,0),f,1100));}
 @Test void brokenRayReleasesHeldAttack(){var s=active();assertEquals(VeinBreakSession.State.AIMING,s.tick(frame(scope(),"minecraft:gray_wool",false,false,5,1100),1100));assertFalse(s.attackRequested());}
}
