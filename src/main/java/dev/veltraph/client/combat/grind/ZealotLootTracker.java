package dev.veltraph.client.combat.grind;

import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

/** A disappeared drop is not a pickup acknowledgment: count only a matching inventory increase. */
public final class ZealotLootTracker {
    private long specialUntil;
    private int baseline,confirmed;
    private boolean pending;
    private final Map<Integer,Long> ignored=new HashMap<>();
    public void reset() {specialUntil=0;baseline=confirmed=0;pending=false;ignored.clear();}
    public void onChat(String text,long now) {
        if(text!=null&&text.replaceAll("§.","").contains("A special Zealot has spawned nearby!"))specialUntil=now+15000;
    }
    public boolean special(long now) {return now<specialUntil;}
    public void begin(int inventoryCount) {if(!pending){pending=true;baseline=inventoryCount;}}
    public boolean observe(int inventoryCount) {
        if(pending&&inventoryCount>baseline) {confirmed+=inventoryCount-baseline;pending=false;specialUntil=0;return true;}
        return false;
    }
    public void abandon() {pending=false;}
    /** Drop IDs/receipts from the old native world expire; collected-eye totals remain. */
    public void clearNativeContext(){pending=false;specialUntil=0;ignored.clear();}
    /** Source DropFinder's short seen-cache window keeps an unreachable eye from monopolizing patrol. */
    public void abandon(int id,long now) {pending=false;ignored.put(id,now+60000);}
    public boolean eligible(int id,long now) {ignored.entrySet().removeIf(e->now>=e.getValue());return !ignored.containsKey(id);}
    public int confirmedEyes() {return confirmed;}
    public static boolean eye(String name) {return name!=null&&name.replaceAll("§.","").toLowerCase(Locale.ROOT).equals("summoning eye");}
}
