package dev.veltraph.client.galatea;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** STAGED uncompiled/unrun; values model actual correlated receipts, never fabricate native proof. */
class FigHarvestReceiptAllocationTest {
    private record Take(String id,long amount,long takeAt) {}
    private Optional<List<Take>> unique(long amount,Take... rows){return FigHarvestReceiptAllocation.unique(List.of(rows),amount,Take::amount);}
    @Test void unmatchedHistoricalOversupplyDoesNotPoisonLaterExactReceipt(){var old=new Take("old",4,10);var exact=new Take("new",2,20);assertEquals(List.of(exact),unique(2,old,exact).orElseThrow());}
    @Test void oversupplyAloneNeverClosesSmallerInventoryDelta(){assertTrue(unique(2,new Take("oversupply",3,10)).isEmpty());}
    @Test void multipleFeasibleReceiptSetsAreAmbiguous(){assertTrue(unique(2,new Take("a",1,10),new Take("b",1,11),new Take("c",2,12)).isEmpty());}
    @Test void twoIndistinguishableOneItemReceiptsCannotBeChosenArbitrarily(){assertTrue(unique(1,new Take("a",1,10),new Take("b",1,11)).isEmpty());}
    @Test void aggregateExactDistinctItemsCanShareOneDelivery(){var a=new Take("a",1,10);var b=new Take("b",2,11);assertEquals(List.of(a,b),unique(3,a,b).orElseThrow());}
    @Test void afterDeliveryTakeIsNotDiscardedByAssumedTransportOrder(){long actualDeliveryAt=100;var take=new Take("later-native-take",1,120);assertTrue(take.takeAt()>actualDeliveryAt);assertEquals(List.of(take),unique(1,take).orElseThrow());}
    @Test void nonPositiveUnprovenAmountsDoNotContribute(){var exact=new Take("verified",1,10);assertEquals(List.of(exact),unique(1,new Take("zero",0,1),new Take("negative",-1,2),exact).orElseThrow());}
    @Test void irrelevantHistoricalCandidatesDoNotSpendEligibleCapacity(){var rows=new ArrayList<Take>();for(int i=0;i<100;i++)rows.add(new Take("old"+i,99,i));var exact=new Take("new",1,101);rows.add(exact);assertEquals(List.of(exact),FigHarvestReceiptAllocation.unique(rows,1,Take::amount).orElseThrow());}
    @Test void eligibleCandidateCapacityDeniesInsteadOfExponentialWork(){var rows=IntStream.range(0,65).mapToObj(i->new Take("i"+i,1,i)).toList();assertTrue(FigHarvestReceiptAllocation.unique(rows,65,Take::amount).isEmpty());}
    @Test void subtotalCapacityDeniesEvenAMathematicallyUniqueLargeClosure(){var rows=IntStream.range(0,11).mapToObj(i->new Take("i"+i,1L<<i,i)).toList();assertTrue(FigHarvestReceiptAllocation.unique(rows,2047,Take::amount).isEmpty());}
    @Test void longAdditionCannotWrapIntoFalseExactMatch(){var huge=new Take("huge",Long.MAX_VALUE,1);assertEquals(List.of(huge),unique(Long.MAX_VALUE,huge,new Take("one",1,2)).orElseThrow());}
    @Test void returnedClosureIsImmutable(){var exact=new Take("a",1,1);var result=unique(1,exact).orElseThrow();assertThrows(UnsupportedOperationException.class,()->result.add(exact));}
}
