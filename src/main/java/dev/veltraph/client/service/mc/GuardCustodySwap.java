package dev.veltraph.client.service.mc;

import java.util.function.BooleanSupplier;


final class GuardCustodySwap<T> {
    private final GuardCustodyStore<T> current;private final T original,helper;private final long before;
    private long published=-1;private boolean attempted;
    GuardCustodySwap(GuardCustodyStore<T> current,T original,T helper,long originalGeneration){
        this.current=current;this.original=original;this.helper=helper;before=originalGeneration;
    }
    boolean installed(){return published>=0;}
    boolean install(BooleanSupplier source,BooleanSupplier nativeIdle){
        if(installed()||attempted||original==null||helper==null||original==helper||current.get()!=original)return false;
        try {if(!source.getAsBoolean()||!nativeIdle.getAsBoolean()||!source.getAsBoolean()||!nativeIdle.getAsBoolean())return false;
            published=current.replaceExact(original,helper,before);return published>=0;
        }catch(Throwable unavailable){return false;}
    }
    boolean restore(BooleanSupplier source,BooleanSupplier nativeIdle){
        if(!installed()||attempted)return false;attempted=true;
        try {return current.get()==helper&&source.getAsBoolean()&&nativeIdle.getAsBoolean()
            &&source.getAsBoolean()&&nativeIdle.getAsBoolean()&&current.replaceExact(helper,original,published)>=0;
        }catch(Throwable unavailable){return false;}
    }
}
