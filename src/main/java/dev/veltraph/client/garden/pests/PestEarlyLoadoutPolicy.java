package dev.veltraph.client.garden.pests;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/** Read-only source timer decision. A decision never grants named gear, camera or farm authority. */
public final class PestEarlyLoadoutPolicy {
    public enum Desired { UNKNOWN, MAIN, ALT }
    public record Bindings(String timerPrefix,String readyRow,String maximumRow) {
        public Bindings { exact(timerPrefix);exact(readyRow);exact(maximumRow);
            if(readyRow.equals(maximumRow)||readyRow.startsWith(timerPrefix)||maximumRow.startsWith(timerPrefix))
                throw new IllegalArgumentException("Independent observed timer/ready/maximum rows required"); }
        private static void exact(String s){if(s==null||s.isBlank()||s.length()>256||s.indexOf('\n')>=0||s.indexOf('\r')>=0||s.indexOf('§')>=0)
            throw new IllegalArgumentException("Exact bounded color-stripped current native rows required");}
    }
    /** Values are sampled once for an actual Root-owned policy generation, never on every read. */
    public record Thresholds(int normalSeconds,int earlySeconds) {
        public Thresholds {if(normalSeconds<5||normalSeconds>=15||earlySeconds< -2||earlySeconds>=2)
            throw new IllegalArgumentException("Recovered source threshold domains required");}
    }
    public record Observation(Integer pestSeconds,Integer equipmentSeconds,boolean readyOrMaximum) {}
    private PestEarlyLoadoutPolicy(){}
    /** Exact one-row native countdown; unknown or duplicate matches cannot borrow a ready row. */
    public static Observation read(Bindings b,List<String> tab,Integer ownedEquipmentSeconds){
        Objects.requireNonNull(b);if(tab==null||tab.size()>512)return new Observation(null,null,false);
        int matches=0;Integer countdown=null;boolean ready=false;
        for(String row:tab){if(row==null||row.length()>1024)return new Observation(null,null,false);
            if(row.startsWith(b.timerPrefix())){matches++;var parsed=seconds(row.substring(b.timerPrefix().length()));countdown=parsed.isPresent()?parsed.getAsInt():null;}
            else if(row.equals(b.readyRow())||row.equals(b.maximumRow())){matches++;ready=true;}}
        if(matches!=1||countdown==null&&!ready)return new Observation(null,null,false);
        return new Observation(countdown,ready?0:ownedEquipmentSeconds,ready);
    }
    /** Strict observed adapter supports seconds or minute+seconds tokens. Unlike source trim/signed Kotlin parser, it refuses ambiguous formatting, negative native timers and seconds >=60. */
    public static OptionalInt seconds(String text){
        if(text==null||text.length()>32)return OptionalInt.empty();
        try {
            if(text.matches("[0-9]{1,9}s"))return OptionalInt.of(Integer.parseInt(text.substring(0,text.length()-1)));
            if(text.matches("[0-9]{1,9}m [0-9]{1,2}s")){int split=text.indexOf(' ');int m=Integer.parseInt(text.substring(0,split-1));int s=Integer.parseInt(text.substring(split+1,text.length()-1));
                if(s>=60)return OptionalInt.empty();return OptionalInt.of(Math.addExact(Math.multiplyExact(m,60),s));}
        }catch(ArithmeticException|NumberFormatException unknown){return OptionalInt.empty();}
        return OptionalInt.empty();
    }
    /** Own cooldown remaining may be negative; a public timer/default alone cannot establish it. Disabled swap is an advisory refusal; source itself computes before its separate application toggle gate. */
    public static Desired decide(boolean loadoutSwap,boolean early,Thresholds thresholds,Observation observation){
        if(!loadoutSwap||thresholds==null||observation==null)return Desired.UNKNOWN;
        if(early&&observation.equipmentSeconds()!=null){int remaining=observation.equipmentSeconds();
            if(remaining<=thresholds.earlySeconds())return Desired.ALT;if(remaining>30)return Desired.MAIN;}
        if(observation.pestSeconds()!=null){int remaining=observation.pestSeconds();if(remaining<0)return Desired.UNKNOWN;
            if(remaining<=thresholds.normalSeconds())return Desired.ALT;if(remaining>30)return Desired.MAIN;}
        return Desired.UNKNOWN;
    }
}
