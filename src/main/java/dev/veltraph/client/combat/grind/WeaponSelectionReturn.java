package dev.veltraph.client.combat.grind;
/** One-use ordering only. Actual native adapter supplies its privately produced receipt/history/context; this class grants no native authority. */
public final class WeaponSelectionReturn {
    public interface Effects {boolean current();boolean submit();}
    private boolean consumed;
    public boolean restore(Effects effects){
        if(consumed||effects==null)return false;consumed=true;
        return effects.current()&&effects.submit();
    }
}
