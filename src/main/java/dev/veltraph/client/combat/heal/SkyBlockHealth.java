package dev.veltraph.client.combat.heal;
import java.util.OptionalDouble;
import java.util.regex.Pattern;

/** Action-bar health is authoritative while fresh; unrelated counters and player chat are ignored. */
public final class SkyBlockHealth {
    private static final Pattern HEALTH=Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*/\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*[❤♥]");
    private double percent;private double rawHealth;private long at=Long.MIN_VALUE/2;
    public void accept(String actionBar,long now) {
        if(actionBar==null) return;
        var match=HEALTH.matcher(actionBar.replaceAll("§.",""));
        if(!match.find()) return;
        try {
            double hp=Double.parseDouble(match.group(1).replace(",","")),max=Double.parseDouble(match.group(2).replace(",",""));
            if(Double.isFinite(hp)&&Double.isFinite(max)&&max>0&&hp>=0) { rawHealth=hp;percent=Math.min(100,100*hp/max);at=now; }
        } catch(NumberFormatException ignored) {}
    }
    public OptionalDouble percent(long now) { return now>=at&&now-at<=2000?OptionalDouble.of(percent):OptionalDouble.empty(); }
    /** Source readiness uses absolute action-bar health; percentage consumers retain their existing view. */
    public OptionalDouble rawHealth(long now) { return now>=at&&now-at<=2000?OptionalDouble.of(rawHealth):OptionalDouble.empty(); }
    public void reset() { at=Long.MIN_VALUE/2; }
}
