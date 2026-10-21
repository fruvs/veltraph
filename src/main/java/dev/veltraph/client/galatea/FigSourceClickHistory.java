package dev.veltraph.client.galatea;

import java.util.ArrayDeque;

/** Readable per-input shared history gate. A recorded attempt is not a queued key event. */
public final class FigSourceClickHistory {
    private final int maximum;
    private final ArrayDeque<Long> history=new ArrayDeque<>();
    public FigSourceClickHistory(int maximum) {
        if(maximum<10 || maximum>14)throw new IllegalArgumentException("Source initialized max count10..14 required");
        this.maximum=maximum;
    }
    public boolean admit(long now,boolean environmentReady,boolean record) {
        if(!environmentReady)return false;
        history.removeIf(t->t<now-1000);
        if(history.size()>=maximum)return false;
        long last=history.isEmpty()?0:history.getLast();
        if(now-last<50)return false;
        if(record)history.addLast(now);
        return true;
    }
    public int recorded(){return history.size();}
}
