package dev.veltraph.client.galatea;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FigScaffoldAttemptFenceTest {
    final Object p=new Object(),w=new Object(),c=new Object(),owner=new Object();
    final List<String> inventory=Collections.nCopies(36,"exact component/count"),gear=Collections.nCopies(5,"protected gear");
    FigScaffoldAttemptFence.Frame frame(Object p,Object w,Object c,Object owner,long epoch,int slot,List<?> inventory,List<?> gear,String config,boolean ready){return new FigScaffoldAttemptFence.Frame(p,w,c,owner,epoch,slot,inventory,gear,config,ready);}
    FigScaffoldAttemptFence.Frame original(){return frame(p,w,c,owner,4,1,inventory,gear,"source settings",true);}
    @Test void exactNativeAttemptHasOriginalDeadlineAndCannotAcceptLateOrOlderCallbacks(){
        var fence=new FigScaffoldAttemptFence();assertTrue(fence.begin(10,100,1200,original()));assertTrue(fence.valid(10,1200,original()));assertFalse(fence.valid(10,1201,original()));assertFalse(fence.valid(9,200,original()));assertFalse(fence.valid(10,99,original()));assertEquals(1200,fence.deadline());
    }
    @Test void foreignPlayerWorldConnectionOwnerOrEpochCannotUseAnOriginalAttempt(){
        var fence=new FigScaffoldAttemptFence();assertTrue(fence.begin(10,100,1200,original()));
        for(var f:List.of(frame(new Object(),w,c,owner,4,1,inventory,gear,"source settings",true),frame(p,new Object(),c,owner,4,1,inventory,gear,"source settings",true),frame(p,w,new Object(),owner,4,1,inventory,gear,"source settings",true),frame(p,w,c,new Object(),4,1,inventory,gear,"source settings",true),frame(p,w,c,owner,5,1,inventory,gear,"source settings",true))){assertFalse(fence.valid(10,200,f));assertFalse(fence.sameNativeScope(f));}
    }
    @Test void unexpectedItemCountComponentsGearSettingsOrMenuNeverBecomeAResourceReceipt(){
        var fence=new FigScaffoldAttemptFence();assertTrue(fence.begin(10,100,1200,original()));var items=new ArrayList<>(inventory);items.set(2,"count decremented or foreign component");var armor=new ArrayList<>(gear);armor.set(0,"changed helmet");
        for(var f:List.of(frame(p,w,c,owner,4,1,items,gear,"source settings",true),frame(p,w,c,owner,4,1,inventory,armor,"source settings",true),frame(p,w,c,owner,4,1,inventory,gear,"changed",true),frame(p,w,c,owner,4,1,inventory,gear,"source settings",false)))assertFalse(fence.valid(10,200,f));
    }
    @Test void ownedSelectedSlotNeedsObservedReceiptWhileManualSlotDriftStops(){
        var fence=new FigScaffoldAttemptFence();assertTrue(fence.begin(10,100,1200,original()));var selected=frame(p,w,c,owner,4,2,inventory,gear,"source settings",true);assertFalse(fence.valid(10,200,selected));assertFalse(fence.selected(10,200,3,selected));assertTrue(fence.selected(10,200,2,selected));assertTrue(fence.valid(10,200,selected));assertFalse(fence.valid(10,200,original()));
    }
    @Test void cancellationInvalidatesBothNativeSelectionAndGeneration(){
        var fence=new FigScaffoldAttemptFence();assertTrue(fence.begin(10,100,1200,original()));fence.cancel();assertFalse(fence.valid(10,200,original()));assertFalse(fence.selected(10,200,1,original()));assertEquals(-1,fence.expectedSlot());
    }
    @Test void unboundOrIncompleteNativeProofDoesNotStart(){
        var fence=new FigScaffoldAttemptFence();assertFalse(fence.begin(10,100,1200,frame(p,w,c,null,4,1,inventory,gear,"settings",true)));assertFalse(fence.begin(10,100,1200,frame(p,w,c,owner,4,1,List.of(),gear,"settings",true)));assertFalse(fence.begin(10,100,1200,frame(p,w,c,owner,4,1,inventory,gear,"settings",false)));
    }
}
