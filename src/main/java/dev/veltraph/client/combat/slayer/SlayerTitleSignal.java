package dev.veltraph.client.combat.slayer;

/** Last exact attunement title, scoped to its client world. No chat command execution or logging. */
public final class SlayerTitleSignal {
    private static java.lang.ref.WeakReference<Object> world=new java.lang.ref.WeakReference<>(null);
    private static long serial;
    private static DaggerAttunement.Aspect aspect;
    private SlayerTitleSignal() {}
    public static void receive(Object owner,String title) {
        if(owner==null)return;
        String cleaned=title.replaceAll("§.","").strip().toUpperCase(java.util.Locale.ROOT);
        try { var next=DaggerAttunement.Aspect.valueOf(cleaned);world=new java.lang.ref.WeakReference<>(owner);aspect=next;serial++; }
        catch(IllegalArgumentException ignored) { /* unrelated titles do not acknowledge a dagger switch */ }
    }
    public static DaggerAttunement.Receipt read(Object owner) {
        return new DaggerAttunement.Receipt(serial,owner!=null&&owner==world.get()?aspect:null);
    }
}
