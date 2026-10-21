package dev.veltraph.client.combat.slayer;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Strict boss-associated countdown observation. The quiet interval is a local safety default. */
public final class TwinclawsTiming {
    public record Signal(int entityId,String label) {}
    public record Hazard(boolean active,long castSerial,long remainingMs,boolean timed,String detail) {
        public static Hazard none() {return new Hazard(false,0,0,false,"");}
    }
    private static final Pattern TIMER=Pattern.compile("^TWINCLAWS\\s+(\\d{1,2}(?:[.,]\\d{1,3})?)s$",Pattern.CASE_INSENSITIVE);
    private int boss=-1,plate=-1;
    private long serial,impact,lastSeen,quietUntil;
    private double previous=Double.NaN;
    public static double seconds(String label) {
        if(label==null)return Double.NaN;
        var m=TIMER.matcher(label.replaceAll("§.","").strip());if(!m.matches())return Double.NaN;
        try {double value=Double.parseDouble(m.group(1).replace(',','.'));return value>=0&&value<=10?value:Double.NaN;}
        catch(NumberFormatException ignored){return Double.NaN;}
    }
    public Hazard observe(int bossId,List<Signal> signals,long now) {
        if(boss!=bossId){reset();boss=bossId;}
        var valid=signals.stream().filter(s->Double.isFinite(seconds(s.label()))).toList();
        if(valid.size()==1) {
            var signal=valid.getFirst();double seconds=seconds(signal.label());
            boolean newCast=signal.entityId()!=plate||now-lastSeen>1000
                    ||Double.isFinite(previous)&&seconds>previous+.3;
            if(newCast) {serial++;impact=now+Math.round(seconds*1000);}
            else if(seconds<previous)impact=now+Math.round(seconds*1000);
            plate=signal.entityId();previous=seconds;lastSeen=now;quietUntil=now+750;
            // Only the actual declared counter authorizes an ability window. Predicting
            // it from elapsed client time can click early when server updates stall.
            return new Hazard(true,serial,Math.round(seconds*1000),true,"Twinclaws: retreat and hold through countdown");
        }
        if(!valid.isEmpty()) {
            lastSeen=now;quietUntil=now+750;
            return new Hazard(true,serial,0,false,"Ambiguous Twinclaws: retreat, no timed ability");
        }
        if(now<quietUntil)return new Hazard(true,serial,Math.max(0,impact-now),false,"Twinclaws: hold through quiet interval");
        return Hazard.none();
    }
    public void reset(){boss=plate=-1;serial=impact=lastSeen=quietUntil=0;previous=Double.NaN;}
}
