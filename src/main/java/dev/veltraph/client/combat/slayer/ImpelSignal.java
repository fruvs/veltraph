package dev.veltraph.client.combat.slayer;

/** World-scoped, bounded prompt receipt. Closely spaced identical packets share one response deadline. */
public final class ImpelSignal {
    private static java.lang.ref.WeakReference<Object> world=new java.lang.ref.WeakReference<>(null);
    private static long serial,received,lastPacket;
    private static ImpelController.Command command=ImpelController.Command.NONE;
    private ImpelSignal() {}
    public static void receive(Object owner,String text,long now) {
        if(owner==null)return;
        var next=ImpelController.parse(text);if(next==ImpelController.Command.NONE)return;
        if(owner==world.get()&&next==command&&now-lastPacket<500) {lastPacket=now;return;}
        world=new java.lang.ref.WeakReference<>(owner);command=next;received=now;lastPacket=now;serial++;
    }
    public static void clear(Object owner) {if(owner==world.get()){command=ImpelController.Command.NONE;serial++;}}
    public static ImpelController.Receipt read(Object owner) {return new ImpelController.Receipt(serial,owner!=null&&owner==world.get()?command:ImpelController.Command.NONE,received);}
}
