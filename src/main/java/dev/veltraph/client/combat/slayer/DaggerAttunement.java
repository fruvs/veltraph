package dev.veltraph.client.combat.slayer;

import java.util.*;
import java.util.regex.Pattern;

/** Right-click switches attunement; attacks wait for observed item-state acknowledgment. */
public final class DaggerAttunement {
    public enum Aspect { ASHEN,AURIC,SPIRIT,CRYSTAL }
    public enum Action { READY,SWITCH,WAIT }
    public record Item(int slot,String name,List<String> lore) { public Item { lore=List.copyOf(lore); } }
    public record Decision(Action action,int slot,String status) {}
    public record Receipt(long serial,Aspect aspect) {}
    private record Confirmed(String name,Aspect original,Aspect current) {}
    private final Map<Integer,Confirmed> confirmed=new HashMap<>();
    private long pendingSerial;
    private int pendingSlot=-1;private String pendingName;private Aspect expected,pendingRaw;private long sent;
    private static final Pattern ASPECT=Pattern.compile("(?<![A-Z])(ASHEN|AURIC|SPIRIT|CRYSTAL)(?![A-Z])");
    public static Aspect shield(String name) {
        var matcher=ASPECT.matcher(clean(name).toUpperCase(Locale.ROOT));Aspect found=null;
        while(matcher.find()) { Aspect next=Aspect.valueOf(matcher.group());if(found!=null&&found!=next)return null;found=next; }
        return found;
    }
    public static Aspect attuned(Item item) {
        Aspect found=null;
        for(String line:item.lore()) {
            String value=clean(line).toUpperCase(Locale.ROOT);
            if(!value.startsWith("ATTUNED: "))continue;
            try { Aspect next=Aspect.valueOf(value.substring(9).strip());if(found!=null&&found!=next)return null;found=next; }
            catch(IllegalArgumentException ignored) { return null; }
        }
        return found;
    }
    private static boolean supports(Item item,Aspect aspect) {
        String name=clean(item.name()).toLowerCase(Locale.ROOT);
        boolean twilight=name.contains("twilight dagger")||name.contains("mawdredge dagger")||name.contains("deathripper dagger");
        boolean firedust=name.contains("firedust dagger")||name.contains("kindlebane dagger")||name.contains("pyrochaos dagger");
        return switch(aspect) {case SPIRIT,CRYSTAL->twilight;case ASHEN,AURIC->firedust;};
    }
    private Aspect effective(Item item) {
        Aspect raw=attuned(item);var state=confirmed.get(item.slot());
        if(state==null)return raw;
        if(!state.name().equals(item.name())||raw!=state.original()) { confirmed.remove(item.slot());return raw; }
        return state.current();
    }
    public Decision tick(String label,List<Item> items,long now) { return tick(label,items,now,new Receipt(0,null)); }
    public Decision tick(String label,List<Item> items,long now,Receipt receipt) {
        Aspect desired=shield(label);
        if(desired==null)return new Decision(Action.WAIT,-1,"Unrecognized or ambiguous Hellion shield");
        if(pendingSlot>=0) {
            Item pending=items.stream().filter(i->i.slot()==pendingSlot&&i.name().equals(pendingName)).findFirst().orElse(null);
            if(pending!=null&&(attuned(pending)==expected&&attuned(pending)!=pendingRaw||receipt.serial()>pendingSerial&&receipt.aspect()==expected)) {
                confirmed.remove(pending.slot());
                if(attuned(pending)!=expected)confirmed.put(pending.slot(),new Confirmed(pending.name(),attuned(pending),expected));
                pendingSlot=-1;pendingName=null;expected=null;
            }
            else return new Decision(Action.WAIT,pendingSlot,now-sent>2000?"Dagger switch not acknowledged; holding attacks":"Waiting for dagger attunement");
        }
        for(Item item:items) if(supports(item,desired)&&effective(item)==desired)
            return new Decision(Action.READY,item.slot(),"Confirmed "+desired+" dagger");
        for(Item item:items) {
            Aspect current=effective(item);
            if(supports(item,desired)&&current!=null&&supports(item,current)) {
                pendingSlot=item.slot();pendingName=item.name();expected=desired;pendingRaw=attuned(item);sent=now;pendingSerial=receipt.serial();
                return new Decision(Action.SWITCH,item.slot(),"Switching dagger to "+desired);
            }
        }
        return new Decision(Action.WAIT,-1,"Need compatible dagger with readable Attuned lore for "+desired);
    }
    public void reset() { confirmed.clear();pendingSerial=0;pendingSlot=-1;pendingName=null;expected=null;sent=0; }
    private static String clean(String text) {return text==null?"":text.replaceAll("§.","").strip();}
}
