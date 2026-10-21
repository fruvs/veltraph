package dev.veltraph.client.builder;

import java.util.concurrent.ConcurrentHashMap;

/** Read-only timing witnesses. No packet contents are read/retained and no packet is sent or cancelled. */
public final class BuilderResourcePacketClock {
    public record Sample(long packet,long recoveredGap,long sequence) {}
    private record Observer(Object owner,Sample sample) {}
    private static final ConcurrentHashMap<Object,Observer> observers=new ConcurrentHashMap<>();
    private BuilderResourcePacketClock() {}
    static void attach(Object connection,Object owner){if(observers.putIfAbsent(connection,new Observer(owner,new Sample(-1,-1,0)))!=null)throw new IllegalStateException("Source packet clock already owned");}
    static void detach(Object connection,Object owner){observers.computeIfPresent(connection,(k,prior)->prior.owner()==owner?null:prior);}
    public static void received(Object connection,long now){observers.computeIfPresent(connection,(k,prior)->new Observer(prior.owner(),new Sample(now,prior.sample().packet()<0||now-prior.sample().packet()>500?now:prior.sample().recoveredGap(),prior.sample().sequence()+1)));}
    static Sample sample(Object connection,Object owner){var entry=observers.get(connection);return entry!=null&&entry.owner()==owner?entry.sample():null;}
    static boolean lag(Sample sample,long now){return sample==null||sample.packet()<0||now-sample.packet()>500;}
    static boolean clickBlocked(Sample sample,long now){return lag(sample,now)||sample.recoveredGap()>=0&&now-sample.recoveredGap()<100;}
}
