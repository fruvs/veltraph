package dev.veltraph.client.movement.device;
import java.util.function.BooleanSupplier;
/** Pure one-attempt refusal/commit state. It cannot construct a native owned origin or grant source permission. */
public final class OwnedUseCommitState {
 private enum Phase { READY,CLAIMED,LOCAL_APPROVED,RESERVED,REFUSED,CLOSED }
 private Phase phase=Phase.READY;
 public boolean ready(){return phase==Phase.READY;}public boolean refusedOrClosed(){return phase==Phase.REFUSED||phase==Phase.CLOSED;}
 public boolean claim(){if(phase!=Phase.READY)return false;phase=Phase.CLAIMED;return true;}
 public boolean localResult(boolean allowed){if(phase!=Phase.CLAIMED||!allowed){phase=Phase.REFUSED;return false;}phase=Phase.LOCAL_APPROVED;return true;}
 /** Recheck state after the last source/native leaf, which may reenter; only one actual caller can reserve. */
 public boolean reserve(BooleanSupplier finalLeaf){
  if(phase!=Phase.LOCAL_APPROVED)return false;
  try{if(!finalLeaf.getAsBoolean()){if(phase==Phase.LOCAL_APPROVED)phase=Phase.REFUSED;return false;}}
  catch(RuntimeException|Error failure){if(phase==Phase.LOCAL_APPROVED)phase=Phase.REFUSED;throw failure;}
  if(phase!=Phase.LOCAL_APPROVED)return false;phase=Phase.RESERVED;return true;
 }
 public boolean refuse(){if(phase!=Phase.RESERVED)phase=Phase.REFUSED;return false;}
 public void close(){phase=Phase.CLOSED;}
}
