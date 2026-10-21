package dev.veltraph.client.mining.vein;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class VeinCollectionWindowTest {
 @Test void ExactOriginalOperationDeadlineRefuses(){assertTrue(VeinCollectionWindow.current(11999,0,12000));assertFalse(VeinCollectionWindow.current(12000,0,12000));assertFalse(VeinCollectionWindow.current(12001,0,12000));}
 @Test void OriginalFloorAndRollbackRemainBound(){assertTrue(VeinCollectionWindow.current(100,100,12000));assertFalse(VeinCollectionWindow.current(99,100,12000));}
 @Test void NoNewWindowAtExpiredDeadline(){assertFalse(VeinCollectionWindow.current(12000,12000,12000));assertFalse(VeinCollectionWindow.current(12001,12000,12000));}
}
