package dev.veltraph.client.metrics;

import com.google.gson.*;
import java.math.BigDecimal;
import java.util.*;

/** Unknown values stay absent. Display names alone never classify or value an inventory item. */
public record MetricContracts(Map<String,Item> items,List<Signal> signals) {
    public enum Channel { SYSTEM, ACTION_BAR, PLAYER_CHAT }
    public enum SignalKind { SKILL_XP_TOTAL, SLAYER_XP_TOTAL, HOTF_XP_TOTAL, WHISPERS_TOTAL, RNG_PERCENT, LUCK_RATIO_PERCENT }
    public enum Role { SORROW, SUMMONING_EYE, RARE_ITEM, LUSHLILAC }
    public record Item(String fingerprint,String label,Set<Role> roles,BigDecimal unitValue) {
        public Item {
            if(fingerprint==null||fingerprint.isBlank()||fingerprint.length()>65536||label==null||label.isBlank()||label.length()>128)throw new IllegalArgumentException("Exact full item fingerprint and local label required");
            roles=Set.copyOf(roles);
            if(unitValue!=null&&(unitValue.signum()<0||unitValue.scale()>8||unitValue.compareTo(new BigDecimal("1000000000000000"))>0))throw new IllegalArgumentException("Invalid declared unit value");
        }
    }
    public record Signal(SignalKind kind,String skill,Channel channel,String prefix,String suffix) {
        public Signal {
            Objects.requireNonNull(kind);Objects.requireNonNull(channel);Objects.requireNonNull(prefix);Objects.requireNonNull(suffix);
            if(channel==Channel.PLAYER_CHAT||prefix.isBlank()||prefix.length()>128||suffix.length()>128)throw new IllegalArgumentException("Exact trusted-channel cumulative signal required");
            if(kind==SignalKind.SKILL_XP_TOTAL&&!Set.of("FARMING","MINING","COMBAT","FORAGING","FISHING","ENCHANTING","ALCHEMY","CARPENTRY","RUNECRAFTING","SOCIAL","TAMING","HUNTING").contains(skill))throw new IllegalArgumentException("Explicit source skill required");
            if(kind!=SignalKind.SKILL_XP_TOTAL&&skill!=null&&!skill.isEmpty())throw new IllegalArgumentException("Unexpected skill field");
        }
        public Optional<BigDecimal> parse(String line) {
            if(!line.startsWith(prefix)||!line.endsWith(suffix)||line.length()<prefix.length()+suffix.length()+1)return Optional.empty();
            String value=line.substring(prefix.length(),line.length()-suffix.length());
            if(!value.matches("(?:[0-9]{1,15}|[0-9]{1,3}(?:,[0-9]{3}){1,4})(?:\\.[0-9]{1,8})?"))return Optional.empty();
            try{var n=new BigDecimal(value.replace(",",""));if(kind==SignalKind.RNG_PERCENT&&n.compareTo(BigDecimal.valueOf(100))>0)return Optional.empty();return Optional.of(n);}catch(NumberFormatException bad){return Optional.empty();}
        }
        public String key(){return kind+(kind==SignalKind.SKILL_XP_TOTAL?":"+skill:"");}
    }
    public MetricContracts {items=Map.copyOf(items);signals=List.copyOf(signals);}
    public static MetricContracts empty(){return new MetricContracts(Map.of(),List.of());}
    public static MetricContracts parse(String itemJson,String signalJson) {
        if(itemJson.length()>524288||signalJson.length()>32768)throw new IllegalArgumentException("Oversized metric contract");
        var items=new LinkedHashMap<String,Item>();var signals=new ArrayList<Signal>();
        try{
            for(var value:JsonParser.parseString(itemJson).getAsJsonArray()){
                var o=value.getAsJsonObject();var roles=EnumSet.noneOf(Role.class);if(o.has("roles"))for(var r:o.getAsJsonArray("roles"))roles.add(Role.valueOf(r.getAsString()));
                BigDecimal price=!o.has("unitValue")||o.get("unitValue").isJsonNull()?null:o.get("unitValue").getAsBigDecimal();
                var item=new Item(o.get("fingerprint").getAsString(),o.get("label").getAsString(),roles,price);
                if(items.putIfAbsent(item.fingerprint(),item)!=null||items.size()>256)throw new IllegalArgumentException("Duplicate/too many metric items");
            }
            var unique=new HashSet<String>();
            for(var value:JsonParser.parseString(signalJson).getAsJsonArray()){
                var o=value.getAsJsonObject();var s=new Signal(SignalKind.valueOf(o.get("kind").getAsString()),o.has("skill")?o.get("skill").getAsString():"",Channel.valueOf(o.get("channel").getAsString()),o.get("prefix").getAsString(),o.has("suffix")?o.get("suffix").getAsString():"");
                if(!unique.add(s.key())||signals.size()>=32)throw new IllegalArgumentException("Duplicate/too many scalar signals");signals.add(s);
            }
            return new MetricContracts(items,signals);
        }catch(RuntimeException bad){throw new IllegalArgumentException("Invalid explicit local metrics contract",bad);}
    }
}
