package dev.veltraph.client.combat.heal;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.regex.Pattern;

/** Exact received 26.3 captured HUD fields. A reading is never an issued-use or heal-success receipt. */
public final class CapturedHudVitals {
    public static final long FRESHNESS_MILLIS=2000;
    private static final String NUMBER="(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)";
    private static final Pattern FRAME=Pattern.compile("^§([c6])("+NUMBER+")/("+NUMBER+")\uE010     (.+)     §b("+NUMBER+")/("+NUMBER+")\uE003 Mana     $");
    private static final Pattern DEFENSE=Pattern.compile("§a"+NUMBER+"§a\uE008 Defense");
    private static final Pattern TRUE_DEFENSE=Pattern.compile("§f"+NUMBER+"§f\uE027 True Defense");
    private static final Set<String> LOCATION_CENTERS=Set.of(
            "\u00a77\ue067 \u00a78The Mist",
            "\u00a77\ue067 \u00a7bAristocrat Passage",
            "\u00a77\ue067 \u00a7bBarracks of Heroes",
            "\u00a77\ue067 \u00a7bCliffside Veins",
            "\u00a77\ue067 \u00a7bDivan's Gateway",
            "\u00a77\ue067 \u00a7bDwarven Village",
            "\u00a77\ue067 \u00a7bFar Reserve",
            "\u00a77\ue067 \u00a7bForge Basin",
            "\u00a77\ue067 \u00a7bGoblin Burrows",
            "\u00a77\ue067 \u00a7bGrand Library",
            "\u00a77\ue067 \u00a7bGreat Ice Wall",
            "\u00a77\ue067 \u00a7bPalace Bridge",
            "\u00a77\ue067 \u00a7bRampart's Quarry",
            "\u00a77\ue067 \u00a7bRoyal Mines",
            "\u00a77\ue067 \u00a7bRoyal Palace",
            "\u00a77\ue067 \u00a7bRoyal Quarters",
            "\u00a77\ue067 \u00a7bThe Forge",
            "\u00a77\ue067 \u00a7bUpper Mines");
    public record Fields(int currentHealth,int maximumHealth,int currentMana,int maximumMana,char healthColor,String observedCenter) {
        public Fields {if(currentHealth<0||maximumHealth<1||currentMana<0||maximumMana<1||(healthColor!='c'&&healthColor!='6'))throw new IllegalArgumentException("Invalid observed vitals");}
        public double percent(){return 100.0*currentHealth/maximumHealth;}
        public boolean aboveMaximum(){return currentHealth>maximumHealth;}
    }
    public record Scope(WeakReference<Object> player,WeakReference<Object> world,WeakReference<Object> connection,long epoch) {
        public static Scope of(Object player,Object world,Object connection,long epoch){return new Scope(new WeakReference<>(player),new WeakReference<>(world),new WeakReference<>(connection),epoch);}
        public boolean valid(){return epoch>0&&player.get()!=null&&world.get()!=null&&connection.get()!=null;}
        public boolean same(Scope other){Object p=player.get(),w=world.get(),c=connection.get();return other!=null&&epoch>0&&epoch==other.epoch&&p!=null&&w!=null&&c!=null&&p==other.player.get()&&w==other.world.get()&&c==other.connection.get();}
    }
    public record Reading(Fields fields,Scope scope,long serial,long receivedAt) {}
    private Scope bound;
    private Reading reading;
    private long lastSerial=-1,lastReceived=-1,lastRead=-1;
    public static Optional<Fields> parse(String text){
        if(text==null||text.length()>512)return Optional.empty();var m=FRAME.matcher(text);
        if(!m.matches()||(!DEFENSE.matcher(m.group(4)).matches()&&!TRUE_DEFENSE.matcher(m.group(4)).matches()&&!LOCATION_CENTERS.contains(m.group(4))))return Optional.empty();
        try{int hp=number(m.group(2)),max=number(m.group(3)),mana=number(m.group(5)),maxMana=number(m.group(6));
            if(max<1||maxMana<1)return Optional.empty();return Optional.of(new Fields(hp,max,mana,maxMana,m.group(1).charAt(0),m.group(4)));
        }catch(NumberFormatException invalid){return Optional.empty();}
    }
    private static int number(String text){if(text.length()>16||text.startsWith("0")&&text.length()>1)throw new NumberFormatException();return Integer.parseInt(text.replace(",",""));}
    public void bind(Scope scope){if(bound==null||!bound.same(scope)){reading=null;bound=scope;}}
    public void invalidate(){reading=null;}
    /** Native adapter verifies origin, literal/default style, current PWC and game context before this call. */
    public boolean receive(Scope scope,Optional<Fields> fields,long serial,long at,boolean nativeAlive){
        boolean newer=serial>lastSerial,timing=at>=0&&at>=lastReceived&&at>=lastRead;
        lastSerial=Math.max(lastSerial,serial);lastReceived=Math.max(lastReceived,at);
        if(bound==null||!bound.same(scope)||!nativeAlive||fields.isEmpty()||!newer||!timing){reading=null;return false;}
        reading=new Reading(fields.get(),scope,serial,at);return true;
    }
    public Optional<Reading> read(Scope scope,long now,boolean nativeAlive){
        if(!nativeAlive||bound==null||!bound.same(scope)||now<lastRead){reading=null;lastRead=Math.max(lastRead,now);return Optional.empty();}
        lastRead=now;
        if(reading==null||!reading.scope().same(scope)||now<reading.receivedAt()||now-reading.receivedAt()>FRESHNESS_MILLIS){reading=null;return Optional.empty();}
        return Optional.of(reading);
    }
    /** An observed frame adds its explicit mana fence; absent data never opens the strict policy. */
    public static boolean mayEmitHeal(Optional<Reading> captured,boolean required,HealItemType type){
        if(type==null)return false;
        return captured.isPresent()?captured.get().fields().currentMana()>=type.manaCost():!required;
    }
    /** Default prefers the complete current frame; required policy never substitutes legacy/native health. */
    public static OptionalDouble preferredPercent(Optional<Reading> captured,boolean required,OptionalDouble legacy,double nativePercent){
        if(captured.isPresent())return OptionalDouble.of(captured.get().fields().percent());
        if(required)return OptionalDouble.empty();return legacy.isPresent()?legacy:OptionalDouble.of(nativePercent);
    }
}
