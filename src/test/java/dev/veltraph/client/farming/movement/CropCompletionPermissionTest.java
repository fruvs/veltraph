package dev.veltraph.client.farming.movement;
import dev.veltraph.client.world.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class CropCompletionPermissionTest {
 CropCompletionPermission all(){return new CropCompletionPermission(true,true,true,true,true,true,true,true,true,true,true,true,true);}
 @Test void everyNativePermissionDimensionIsRequired(){for(int i=0;i<13;i++){boolean[] b=new boolean[13];java.util.Arrays.fill(b,true);b[i]=false;assertFalse(new CropCompletionPermission(b[0],b[1],b[2],b[3],b[4],b[5],b[6],b[7],b[8],b[9],b[10],b[11],b[12]).allows(),"dimension="+i);}assertTrue(all().allows());}
 @Test void lateToolGuardOrOwnerRevocationBlocksAttackAfterGeometry(){assertFalse(CropCompletionCommit.mayAttack(true,true,new CropCompletionPermission(true,true,true,true,true,true,true,true,true,true,true,false,true),true));assertFalse(CropCompletionCommit.mayAttack(true,true,all(),false));assertFalse(CropCompletionCommit.mayAttack(true,false,all(),true));assertTrue(CropCompletionCommit.mayAttack(true,true,all(),true));}
 @Test void geometryCallbackStealingOwnerPreventsRotation(){var owned=new java.util.concurrent.atomic.AtomicBoolean(true);var rotations=new java.util.concurrent.atomic.AtomicInteger();assertFalse(CropCompletionCommit.rotateAfterObservation(()->{owned.set(false);return true;},owned::get,rotations::incrementAndGet));assertEquals(0,rotations.get());}
 @Test void geometryOcclusionPreventsLateRotationAndPermissionReads(){var permission=new java.util.concurrent.atomic.AtomicInteger();var rotations=new java.util.concurrent.atomic.AtomicInteger();assertFalse(CropCompletionCommit.rotateAfterObservation(()->false,()->{permission.incrementAndGet();return true;},rotations::incrementAndGet));assertEquals(0,permission.get());assertEquals(0,rotations.get());}
 @Test void retryExpiresAndClockRollbackRevokesPermanentExclusion(){var retry=new CropCompletionRetry();var cell=new BlockPos(56,71,0);retry.exclude(cell,100);assertEquals(cell,retry.avoided(2099));assertNull(retry.avoided(2100));retry.exclude(cell,100);assertNull(retry.avoided(99));}
}
