package dev.veltraph.client.combat.slayer;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Request, not assumed success. Item duration/cooldown must be readable from local ability lore. */
public final class HolyIceController {
    public record Item(int slot,String name,List<String> lore,boolean ice) {public Item{lore=List.copyOf(lore);}}
    public record Decision(boolean use,int slot,String detail) {}
    private static final Pattern DURATION=Pattern.compile("\\bfor\\s+(\\d+(?:\\.\\d+)?)\\s*(?:s|secs?|seconds?)\\b",Pattern.CASE_INSENSITIVE);
    private static final Pattern COOLDOWN=Pattern.compile("\\bcooldown:\\s*(\\d+(?:\\.\\d+)?)\\s*(?:s|secs?|seconds?)\\b",Pattern.CASE_INSENSITIVE);
    private long lastCast=-1,nextUse;
    private int boss=-1;
    public static long duration(Item item) {return timing(item,DURATION);}
    private static long timing(Item item,Pattern pattern) {
        String name=clean(item.name()).toLowerCase(Locale.ROOT);
        if(!item.ice()||!name.equals("holy ice"))return -1;
        String lore=clean(String.join(" ",item.lore()));
        if(!lore.toLowerCase(Locale.ROOT).contains("ability:")||!lore.toLowerCase(Locale.ROOT).contains("damage"))return -1;
        var matcher=pattern.matcher(lore);if(!matcher.find())return -1;
        double seconds=Double.parseDouble(matcher.group(1));
        return Double.isFinite(seconds)&&seconds>0&&seconds<=60?Math.round(seconds*1000):-1;
    }
    public Decision tick(int bossId,TwinclawsTiming.Hazard hazard,List<Item> items,long now) {
        if(boss!=bossId){boss=bossId;lastCast=-1;}
        if(!hazard.active()||!hazard.timed())return new Decision(false,-1,"");
        if(lastCast==hazard.castSerial())return new Decision(false,-1,"Holy Ice requested; server effect unconfirmed");
        var candidates=items.stream().filter(i->duration(i)>0&&timing(i,COOLDOWN)>0).toList();
        if(candidates.size()!=1)return new Decision(false,-1,"Need one Holy Ice with readable duration/cooldown lore");
        var item=candidates.getFirst();
        if(now<nextUse)return new Decision(false,-1,"Holy Ice lore cooldown: holding attacks");
        // Use inside the final half of the client-visible protection duration.
        
        if(hazard.remainingMs()>duration(item)/2)return new Decision(false,-1,"Twinclaws: waiting for Holy Ice request window");
        lastCast=hazard.castSerial();nextUse=now+timing(item,COOLDOWN);
        return new Decision(true,item.slot(),"Holy Ice requested; server effect unconfirmed");
    }
    public void reset(){boss=-1;lastCast=-1;nextUse=0;}
    private static String clean(String value){return value==null?"":value.replaceAll("§.","").strip();}
}
