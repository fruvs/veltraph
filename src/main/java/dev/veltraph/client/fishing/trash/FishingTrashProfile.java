package dev.veltraph.client.fishing.trash;

import com.google.gson.Gson;
import java.util.*;

/** Only the source's native enchanted-book/player-head label pairs; salted labels have no defaults. */
public record FishingTrashProfile(List<Rule> rules,Long minClickIntervalMillis,Long requestTimeoutMillis,Long stageTimeoutMillis,Long checkIntervalMillis) {
    public record Rule(String itemId,String text) {
        public Rule {
            if(!Set.of("minecraft:enchanted_book","minecraft:player_head").contains(itemId)
                    ||text==null||text.isBlank()||text.length()>128)
                throw new IllegalArgumentException("Declare a source native book/head type and exact nonempty trash label");
        }
        public boolean matches(Stack value) {
            return value!=null&&value.count()>0&&itemId.equals(value.itemId())
                    &&(plain(value.name()).contains(text)||value.tooltip().stream().map(FishingTrashProfile::plain).anyMatch(line->line.contains(text)));
        }
    }
    public record Stack(String itemId,String name,List<String> tooltip,int count,Object identity) {
        public Stack {tooltip=List.copyOf(tooltip);Objects.requireNonNull(identity);if(count<0)throw new IllegalArgumentException();}
    }
    public FishingTrashProfile {
        if(rules==null||rules.isEmpty()||rules.size()>8||rules.stream().anyMatch(Objects::isNull)
                ||rules.stream().distinct().count()!=rules.size()
                ||rules.stream().filter(r->r.itemId().equals("minecraft:enchanted_book")).count()>7
                ||rules.stream().filter(r->r.itemId().equals("minecraft:player_head")).count()>1
                ||minClickIntervalMillis==null||minClickIntervalMillis<0||minClickIntervalMillis>10000
                ||requestTimeoutMillis==null||requestTimeoutMillis<250||requestTimeoutMillis>10000
                ||stageTimeoutMillis==null||stageTimeoutMillis<1000||stageTimeoutMillis>300000
                ||checkIntervalMillis==null||checkIntervalMillis<0||checkIntervalMillis>3600000)
            throw new IllegalArgumentException("Declare up to the source seven book/one head pairs and explicit request pacing/bounds");
        rules=List.copyOf(rules);
    }
    public boolean candidate(Stack value){return rules.stream().anyMatch(rule->rule.matches(value));}
    public static String plain(String value){return value.replaceAll("§.","");}
    public static FishingTrashProfile parse(String json) {
        try {if(json==null||json.length()>8192)throw new IllegalArgumentException();return Objects.requireNonNull(new Gson().fromJson(json,FishingTrashProfile.class));}
        catch(RuntimeException e){throw new IllegalArgumentException("Configure exact source Fishing trash book/head labels and request bounds",e);}
    }
}
