package dev.veltraph.client.fishing.support;
import com.google.gson.Gson;
import java.util.*;

/** Exact ordered tool contracts; source name/duration associations are still salted. */
public record OrbProfile(String itemName,String itemId,Integer durationSeconds,String effectId,Integer amplifier,
                         Integer manaCost,Integer itemConsumption,Boolean needs235Mana,Long minUseIntervalMillis) {
    public OrbProfile {
        for(String s:List.of(itemName,itemId,effectId))if(s.isBlank()||s.length()>128)throw new IllegalArgumentException("Orb identity/effect is required");
        for(Object value:List.of(durationSeconds,amplifier,manaCost,itemConsumption,needs235Mana,minUseIntervalMillis))Objects.requireNonNull(value);
        if(!Set.of(30,60,180,300).contains(durationSeconds)||amplifier<0||amplifier>255||manaCost<0||itemConsumption<0||itemConsumption>1||manaCost+itemConsumption==0||minUseIntervalMillis<0)
            throw new IllegalArgumentException("Declare a recovered orb duration and explicit consumption/cooldown contract");
    }
    public static List<OrbProfile> parse(String json){try{if(json.length()>16384)throw new IllegalArgumentException();var rows=new Gson().fromJson(json,OrbProfile[].class);if(rows==null||rows.length<1||rows.length>6)throw new IllegalArgumentException();var result=List.of(rows);if(result.stream().map(OrbProfile::itemName).distinct().count()!=result.size())throw new IllegalArgumentException();return result;}catch(RuntimeException e){throw new IllegalArgumentException("Configure exact ordered orb profiles, durations, effects and consumption",e);}}
    public boolean ready(int current,int maximum){return maximum>0&&maximum!=Short.MAX_VALUE&&current/(double)maximum>=.55&&current>=manaCost&&(!needs235Mana||current>=235);}
}
