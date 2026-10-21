package dev.veltraph.client.greenhouse;

import java.util.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GreenhouseParentResumeOperandsTest {
    @Test void everyOriginalTruthRowHasSameOutcomeOrderAndSkippedTail(){
        for(int mask=0;mask<32;mask++){final int row=mask;var calls=new ArrayList<Integer>();BooleanSupplier[] values=new BooleanSupplier[5];
            for(int i=0;i<5;i++){final int index=i;values[i]=()->{calls.add(index);return(row&(1<<index))!=0;};}
            var result=GreenhouseParentResumeOperands.evaluate(values[0],values[1],values[2],values[3],values[4]);assertEquals(mask!=0,result.failed());
            int evaluated=mask==0?5:Integer.numberOfTrailingZeros(mask)+1;assertEquals(java.util.stream.IntStream.range(0,evaluated).boxed().toList(),calls);
            for(int i=0;i<5;i++)if(i<evaluated)assertEquals((mask&(1<<i))!=0,result.values().get(i));else assertNull(result.values().get(i));
        }
    }
    @Test void firstFalseOperandIsQueriedOnlyOnceAndFailureStopsLaterCallbacks(){var calls=new int[5];var result=GreenhouseParentResumeOperands.evaluate(()->{calls[0]++;return false;},()->{calls[1]++;return true;},()->{calls[2]++;throw new AssertionError("Skipped");},()->false,()->false);assertEquals(List.of(1,1,0,0,0),Arrays.stream(calls).boxed().toList());assertEquals(Arrays.asList(false,true,null,null,null),result.values());}
}
