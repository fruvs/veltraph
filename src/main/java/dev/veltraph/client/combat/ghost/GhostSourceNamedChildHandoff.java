package dev.veltraph.client.combat.ghost;
/** One named child transition, latching the observed destination identity. Never changes camera or parent scope. */
public final class GhostSourceNamedChildHandoff {
    private final Object original;private Object named;private boolean observed;
    public GhostSourceNamedChildHandoff(Object original){this.original=java.util.Objects.requireNonNull(original);}
    public boolean authorize(Object actualOriginal,Object actualNamed){if(named!=null||actualOriginal!=original||actualNamed==null||actualNamed==original)return false;named=actualNamed;return true;}
    public boolean current(Object actual){if(named==null)return actual==original;if(actual==named){observed=true;return true;}return !observed&&actual==original;}
}
