package dev.veltraph.client.hunting;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ShardSelectionCustodyTest {
    @Test void onlyOwnedSelectionRestoresAcrossACompletedBatch(){
        assertTrue(ShardSelectionCustody.restore(true,true,0,1,1));
        assertTrue(ShardSelectionCustody.restore(true,true,0,2,2));
        assertFalse(ShardSelectionCustody.restore(true,true,0,2,7));
    }
    @Test void NewNativeContextOrReplacedProtectedToolCannotBeOverwritten(){
        assertFalse(ShardSelectionCustody.restore(false,true,0,1,1));
        assertFalse(ShardSelectionCustody.restore(true,false,0,1,1));
        assertFalse(ShardSelectionCustody.restore(true,true,-1,1,1));
        assertFalse(ShardSelectionCustody.restore(true,true,0,-1,0));
    }
}
