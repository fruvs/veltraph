package dev.veltraph.client.combat.slayer;
import java.util.UUID;
/** Withhold binding until independently owned body/plate data survives one native update interval. */
public final class CocoonBodyCoherence {
    private UUID body,plate;private long since=-1,last=-1;
    public void clear(){body=plate=null;since=last=-1;}
    public boolean observe(UUID nextBody,UUID nextPlate,long tick,int cadence){
        if(nextBody==null||nextPlate==null||cadence<1||cadence>20){clear();return false;}
        if(tick<last||!nextBody.equals(body)||!nextPlate.equals(plate)){body=nextBody;plate=nextPlate;since=tick;}
        last=tick;return tick-since>=cadence;
    }
}
