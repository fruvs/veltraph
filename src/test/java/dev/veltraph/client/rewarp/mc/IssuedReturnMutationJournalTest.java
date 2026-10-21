package dev.veltraph.client.rewarp.mc;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class IssuedReturnMutationJournalTest {
    @Test void attemptedSameValueNativeWriteRefusesEvenWithoutValueChange(){var scope=IssuedReturnMutationJournal.open();try{Object value=new Object();assertTrue(scope.watch(value));assertTrue(scope.current());IssuedReturnMutationJournal.mutation(value);assertFalse(scope.current());}finally{scope.close();}}
    @Test void abaSecondObservationCannotRearmInvalidatedScope(){var scope=IssuedReturnMutationJournal.open();try{Object value=new Object();assertTrue(scope.watch(value));IssuedReturnMutationJournal.mutation(value);IssuedReturnMutationJournal.mutation(value);assertFalse(scope.watch(value));assertFalse(scope.current());}finally{scope.close();}}
    @Test void foreignNativeMutationDoesNotInvalidateOtherInventory(){var scope=IssuedReturnMutationJournal.open();try{assertTrue(scope.watch(new Object()));IssuedReturnMutationJournal.mutation(new Object());assertTrue(scope.current());}finally{scope.close();}}
    @Test void sharedMutableNbtObjectInvalidatesAllItsOwningSlots(){var first=IssuedReturnMutationJournal.open();var second=IssuedReturnMutationJournal.open();try{Object shared=new Object();assertTrue(first.watch(shared));assertTrue(second.watch(shared));IssuedReturnMutationJournal.mutation(shared);assertFalse(first.current());assertFalse(second.current());}finally{first.close();second.close();}}
    @Test void twoIndependentSnapshotsHaveIndependentCustody(){var first=IssuedReturnMutationJournal.open();var second=IssuedReturnMutationJournal.open();try{Object a=new Object(),b=new Object();assertTrue(first.watch(a));assertTrue(second.watch(b));IssuedReturnMutationJournal.mutation(a);assertFalse(first.current());assertTrue(second.current());}finally{first.close();second.close();}}
    @Test void closedSnapshotCannotBeReusedAfterDisconnect(){var scope=IssuedReturnMutationJournal.open();scope.watch(new Object());scope.close();assertFalse(scope.current());assertFalse(scope.watch(new Object()));scope.close();}
    @Test void capacityRefusalDoesNotSilentlyOmitRemainingNbtObjects(){var scope=IssuedReturnMutationJournal.open();try{for(int i=0;i<16384;i++)assertTrue(scope.watch(new Object()));assertTrue(scope.current());assertFalse(scope.watch(new Object()));assertFalse(scope.current());}finally{scope.close();}}
    @Test void exhaustedLiveCapacityCanRecoverOnlyByClosingScope(){var opened=new ArrayList<IssuedReturnMutationJournal.Scope>();try{for(int i=0;i<32;i++){var scope=IssuedReturnMutationJournal.open();assertNotNull(scope);opened.add(scope);}assertNull(IssuedReturnMutationJournal.open());opened.get(0).close();var replacement=IssuedReturnMutationJournal.open();assertNotNull(replacement);opened.add(replacement);assertFalse(opened.get(0).current());}finally{opened.forEach(IssuedReturnMutationJournal.Scope::close);}}
    @Test void sameNativeObjectUsesOneCapacityCellByIdentity(){var scope=IssuedReturnMutationJournal.open();try{Object value=new Object();for(int i=0;i<20000;i++)assertTrue(scope.watch(value));assertTrue(scope.current());IssuedReturnMutationJournal.mutation(value);assertFalse(scope.current());}finally{scope.close();}}
}
