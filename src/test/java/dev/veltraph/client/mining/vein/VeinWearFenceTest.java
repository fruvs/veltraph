package dev.veltraph.client.mining.vein;
import dev.veltraph.client.galatea.FigHarvestPickupLedger.Scope;
import dev.veltraph.client.galatea.FigToolWearLedger.*;
import dev.veltraph.client.world.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class VeinWearFenceTest {
 final Object owner=new Object(),player=new Object(),world=new Object(),connection=new Object(),tree=new Object(),guard=new Object(),nativeTool=new Object();
 final Scope scope=new Scope(owner,player,world,connection,1,tree,1);
 Cell cell(int damage,Object other){return new Cell(2,1,damage,"full"+damage+other,"withoutDamage"+other,nativeTool,1);}
 Receipt receipt(Cell before,Cell after,long ackAt,long appliedAt){var action=new Action(scope,1,1,new BlockPos(1,71,1),"gray_wool",guard,2,before,3,"settings",2000,1000,10,2,11);return new Receipt(Evidence.CORRELATED_NATIVE_ACTION,action,new Ack(3,ackAt,-1,true),4,appliedAt,before,after);}
 List<Object> inventory(Cell tool){var cells=new ArrayList<Object>(Collections.nCopies(36,"unchanged"));cells.set(2,tool.full());return cells;}
 VeinWearFence fence(Cell before){return new VeinWearFence(scope,1,3,"settings",2000,2,inventory(before));}
 @Test void oneActualWearProjectionConsumesPermitOnce(){var b=cell(0,"same");var a=cell(1,"same");var f=fence(b);assertTrue(f.stage(receipt(b,a,1100,1150)));assertTrue(f.accept(scope,inventory(a),1200,3,"settings",2000));assertFalse(f.accept(scope,inventory(a),1200,3,"settings",2000));}
 @Test void inventoryBeforeAirCanJoinWithinOriginalDeadline(){var b=cell(0,"same");var a=cell(1,"same");var f=fence(b);assertTrue(f.stage(receipt(b,a,1200,1100)));assertTrue(f.accept(scope,inventory(a),1250,3,"settings",2000));}
 @Test void missingAirCannotCreatePermit(){var b=cell(0,"same");var a=cell(1,"same");var f=fence(b);assertFalse(f.accept(scope,inventory(a),1200,3,"settings",2000));}
 @Test void unrelatedComponentChangeIsDenied(){var b=cell(0,"same");var a=cell(1,"different");assertFalse(fence(b).stage(receipt(b,a,1100,1150)));}
 @Test void excessiveDamageCannotProject(){var b=cell(0,"same");var a=cell(2,"same");assertFalse(fence(b).stage(receipt(b,a,1100,1150)));}
 @Test void foreignScopeDenied(){var b=cell(0,"same");var a=cell(1,"same");var f=fence(b);assertTrue(f.stage(receipt(b,a,1100,1150)));assertFalse(f.accept(new Scope(owner,player,new Object(),connection,1,tree,1),inventory(a),1200,3,"settings",2000));}
 @Test void lateReceiptDenied(){var b=cell(0,"same");var a=cell(1,"same");assertFalse(fence(b).stage(receipt(b,a,2001,1150)));}
 @Test void otherInventoryCellChangeNeedsSeparateProof(){var b=cell(0,"same");var a=cell(1,"same");var f=fence(b);assertTrue(f.stage(receipt(b,a,1100,1150)));var changed=inventory(a);changed.set(3,"foreign");assertFalse(f.accept(scope,changed,1200,3,"settings",2000));}
 @Test void retiredFenceCannotAccept(){var b=cell(0,"same");var a=cell(1,"same");var f=fence(b);assertTrue(f.stage(receipt(b,a,1100,1150)));f.retire();assertFalse(f.accept(scope,inventory(a),1200,3,"settings",2000));}
 @Test void cameraEpochChangeDenied(){var b=cell(0,"same");var a=cell(1,"same");var f=fence(b);assertTrue(f.stage(receipt(b,a,1100,1150)));assertFalse(f.accept(scope,inventory(a),1200,4,"settings",2000));}
 @Test void countChangesDenied(){var b=cell(0,"same");var a=new Cell(2,2,1,"full1same","withoutDamagesame",nativeTool,1);assertFalse(fence(b).stage(receipt(b,a,1100,1150)));}

 @Test void inventoryAppliedBeforeNativeStartCannotQualifyWear(){var b=cell(0,"same");var a=cell(1,"same");assertFalse(fence(b).stage(receipt(b,a,1100,999)));}
 @Test void authoritativeAirBeforeNativeStartCannotQualifyWear(){var b=cell(0,"same");var a=cell(1,"same");assertFalse(fence(b).stage(receipt(b,a,999,1100)));}
 @Test void stopSequenceBeforeStartCannotQualifyWear(){var b=cell(0,"same");var a=cell(1,"same");var original=receipt(b,a,1100,1150);var start=original.action();var staleStop=new Action(scope,1,1,start.target(),start.originalLogState(),guard,2,b,3,"settings",2000,1000,10,2,9);assertFalse(fence(b).stage(new Receipt(Evidence.CORRELATED_NATIVE_ACTION,staleStop,original.ack(),4,1150,b,a)));}
 @Test void duplicateNativeDeliveryCannotBeSpentTwice(){var b=cell(0,"same");var a=cell(1,"same");var f=fence(b);var r=receipt(b,a,1100,1150);assertTrue(f.stage(r));assertFalse(f.stage(r));assertTrue(f.accept(scope,inventory(a),1200,3,"settings",2000));assertFalse(f.stage(r));assertFalse(f.accept(scope,inventory(a),1200,3,"settings",2000));}
}
