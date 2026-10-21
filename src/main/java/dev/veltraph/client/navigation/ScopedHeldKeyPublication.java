package dev.veltraph.client.navigation;
import java.util.Objects;
/** Callback ordering only. The native leaf supplies the actual mapping and an independent refusal fence. */
public final class ScopedHeldKeyPublication {
 private ScopedHeldKeyPublication(){}
 public static boolean afterRefusal(java.util.function.BooleanSupplier refusal,java.util.function.BooleanSupplier callbackFreeLeaf){
  return refusal.getAsBoolean()&&callbackFreeLeaf.getAsBoolean();
 }
 public interface Effects {void observe();boolean current();boolean down();void setDown(boolean held);boolean toggle();void clearRestore();}
 public static boolean apply(boolean held,Effects effects){
  Objects.requireNonNull(effects);effects.observe();
  if(effects.down()!=held){
   if(!effects.current())return false;effects.setDown(held);
   if(effects.down()!=held){if(!effects.current())return false;effects.setDown(true);}
   if(effects.down()!=held){if(!effects.current())return false;throw new IllegalStateException("Key mapping refused absolute held state");}
  }
  if(!held&&effects.toggle()){if(!effects.current())return false;effects.clearRestore();}
  return effects.current();
 }
}
