package dev.veltraph.client.fishing.support;
import com.google.gson.Gson;
import dev.veltraph.client.fishing.bait.BaitProfile;
import dev.veltraph.client.gui.MenuSnapshot;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/** Salted Vanessa vocabulary and explicitly bounded purchase contract. No guessed production labels. */
public record RainProfile(String npcName,String npcType,String npcUuid,Double anchorX,Double anchorY,Double anchorZ,
        String menuTitle,String bucketName,String bucketId,String remainingPrefix,String costPrefix,String costSuffix,
        String currencyPrefix,String receiptTemplate,Integer targetMinutes,Long minimumCheckIntervalMillis,BigDecimal maximumCost) {
    public RainProfile {
        for(String s:List.of(npcName,npcType,npcUuid,menuTitle,bucketName,bucketId,remainingPrefix,costPrefix,costSuffix,currencyPrefix,receiptTemplate))if(s.isBlank()||s.length()>256)throw new IllegalArgumentException("Exact rain identity/quote/receipt is required");
        UUID.fromString(npcUuid);for(Object v:List.of(anchorX,anchorY,anchorZ,targetMinutes,minimumCheckIntervalMillis,maximumCost))Objects.requireNonNull(v);
        if(!Double.isFinite(anchorX)||!Double.isFinite(anchorY)||!Double.isFinite(anchorZ)||targetMinutes<1||targetMinutes>10||minimumCheckIntervalMillis<0||maximumCost.signum()<=0)throw new IllegalArgumentException("Invalid bounded rain contract");
        pattern(receiptTemplate);
    }
    public static RainProfile parse(String json){try{if(json.length()>16384)throw new IllegalArgumentException();return Objects.requireNonNull(new Gson().fromJson(json,RainProfile.class));}catch(RuntimeException e){throw new IllegalArgumentException("Configure exact Vanessa/menu/rain quote and bounds",e);}}
    private static String only(List<String> lore,String prefix){var lines=lore.stream().map(MenuSnapshot::clean).filter(v->v.startsWith(prefix)).toList();if(lines.size()!=1)throw new IllegalArgumentException("Ambiguous/missing rain lore");return lines.getFirst().substring(prefix.length()).strip();}
    public int seconds(MenuSnapshot.Slot slot){String[] parts=only(slot.lore(),remainingPrefix).split(":");if(parts.length!=2||!parts[0].matches("[0-9]{1,4}")||!parts[1].matches("[0-9]{2}"))throw new IllegalArgumentException("Invalid rain remaining mm:ss");int seconds=Integer.parseInt(parts[1]);if(seconds>=60)throw new IllegalArgumentException("Invalid rain seconds");return Integer.parseInt(parts[0])*60+seconds;}
    public BigDecimal cost(MenuSnapshot.Slot slot){String value=only(slot.lore(),costPrefix);if(!value.endsWith(costSuffix))throw new IllegalArgumentException("Unknown total rain price");BigDecimal cost=BaitProfile.money(value.substring(0,value.length()-costSuffix.length()));if(cost.signum()<=0)throw new IllegalArgumentException("Rain cost must be positive");return cost;}
    private static Pattern pattern(String template){int unit=template.indexOf("{minutes}"),cost=template.indexOf("{cost}");if(unit<0||cost<=unit||template.indexOf("{minutes}",unit+1)>=0||template.indexOf("{cost}",cost+1)>=0)throw new IllegalArgumentException("Receipt needs unique minutes/cost tokens in order");return Pattern.compile("^"+Pattern.quote(template.substring(0,unit))+"(?<minutes>[0-9]{1,2})"+Pattern.quote(template.substring(unit+9,cost))+"(?<cost>[0-9,.]{1,32})"+Pattern.quote(template.substring(cost+6))+"$");}
    public boolean receipt(String text,BigDecimal wanted){var match=pattern(receiptTemplate).matcher(MenuSnapshot.clean(text));if(!match.matches())return false;try{return Integer.parseInt(match.group("minutes"))==1&&BaitProfile.money(match.group("cost")).compareTo(wanted)==0;}catch(RuntimeException e){return false;}}
}
