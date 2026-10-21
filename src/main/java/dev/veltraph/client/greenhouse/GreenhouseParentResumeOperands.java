package dev.veltraph.client.greenhouse;

import java.util.*;
import java.util.function.BooleanSupplier;

/** Records only the original ordered OR operands; null means the original expression skipped it. */
public final class GreenhouseParentResumeOperands {
    private GreenhouseParentResumeOperands(){}
    public record Result(boolean failed,List<Boolean> values){public Result{values=Collections.unmodifiableList(new ArrayList<>(values));}}
    public static Result evaluate(BooleanSupplier a,BooleanSupplier b,BooleanSupplier c,BooleanSupplier d,BooleanSupplier e){
        var values=new ArrayList<Boolean>(Collections.nCopies(5,null));
        BooleanSupplier[] original={a,b,c,d,e};
        for(int i=0;i<5;i++){boolean value=original[i].getAsBoolean();values.set(i,value);if(value)return new Result(true,values);}
        return new Result(false,values);
    }
}
