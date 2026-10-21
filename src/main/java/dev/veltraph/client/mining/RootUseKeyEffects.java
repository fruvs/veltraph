package dev.veltraph.client.mining;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/** Exact hardware effects used by the native adapter; retirement is not reported as a release. */
public final class RootUseKeyEffects<K> {
    private final BiConsumer<K,Boolean> write;
    private final Predicate<K> down;
    private MiningUseHoldLease lease;
    private K key;
    private boolean retired;
    public RootUseKeyEffects(BiConsumer<K,Boolean> write,Predicate<K> down){this.write=Objects.requireNonNull(write);this.down=Objects.requireNonNull(down);}
    /** Caller has proved current Root acquisition authority. No release/repress pulse is emitted. */
    public boolean acquire(MiningUseHoldLease next,K nativeKey){
        Objects.requireNonNull(next);Objects.requireNonNull(nativeKey);
        if(retired && lease!=null && lease.owner()==next.owner() && lease.enableRun()==next.enableRun()){
            // Authority returning does not prove a still-down key belongs to this old activation.
            if(down.test(key))return false;
            clear(); // Actual up-state receipt permits a new owned write in the same activation.
        }
        lease=next;key=nativeKey;retired=false;
        if(!down.test(key))write.accept(key,true);
        return down.test(key);
    }
    /** Loss of fresh custody retires only the requesting old activation, without any hardware write. */
    public void retireOwned(Object caller,int activation){if(ownedBy(caller,activation))retired=true;}
    public boolean release(Object currentOwner,int currentEnableRun,Object player,Object world,Object gameMode,
            Object registeredOwner,String lockOwner){
        if(lease==null)return true;
        // An independent native/global release is an actual up-state receipt.
        if(!down.test(key)){clear();return true;}
        if(retired)return false;
        if(!lease.mayRelease(currentOwner,currentEnableRun,player,world,gameMode,registeredOwner,lockOwner)){
            retired=true;return false;
        }
        // Context replacement alone never suppresses the old activation's hardware cancellation.
        write.accept(key,false);
        if(!down.test(key)){clear();return true;}
        return false;
    }
    private void clear(){lease=null;key=null;retired=false;}
    public boolean ownedHeld(Object caller,int activation){return lease!=null&&!retired&&lease.owner()==caller&&lease.enableRun()==activation&&down.test(key);}
    public boolean ownedBy(Object caller,int activation){return lease!=null&&lease.owner()==caller&&lease.enableRun()==activation;}
    public boolean hasLease(){return lease!=null;}
    public boolean retired(){return retired;}
}
