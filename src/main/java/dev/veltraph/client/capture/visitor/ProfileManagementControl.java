package dev.veltraph.client.capture.visitor;

import java.util.*;
import java.util.regex.Pattern;

/** Actual captured UI control only. Its profile count is not an active profile identity or click capability. */
public final class ProfileManagementControl {
    private ProfileManagementControl() {}
    public record Row(int nativeSlot,String itemId,String visibleName,List<String> lore,boolean playerInventory){public Row{lore=List.copyOf(lore);
        if(nativeSlot<0||nativeSlot>255||itemId==null||itemId.length()>128||visibleName==null||visibleName.length()>512||lore.size()>32||lore.stream().anyMatch(s->s==null||s.length()>512))throw new IllegalArgumentException("Unbounded profile control row");}}
    public record Observation(int nativeSlot,int profileCount,int capacity,String command,String identityStatus) {}
    private static final Pattern COUNT=Pattern.compile("Profiles: ([0-9]{1,2})/([0-9]{1,2})");
    public static Optional<Observation> read(String actualTitle,String actualMenuType,List<Row> rows){
        if(!"SkyBlock Menu".equals(actualTitle)||!"minecraft:generic_9x6".equals(actualMenuType)||rows==null||rows.size()>256)return Optional.empty();
        Observation match=null;
        for(var row:rows){
            if(row.playerInventory()||row.nativeSlot()<0||!"minecraft:name_tag".equals(row.itemId())||!"Profile Management".equals(row.visibleName()))continue;
            if(!row.lore().contains("Also accessible via /profiles"))return Optional.empty();
            Integer count=null,capacity=null;
            for(String line:row.lore()){var m=COUNT.matcher(line);if(!m.matches())continue;if(count!=null)return Optional.empty();count=Integer.parseInt(m.group(1));capacity=Integer.parseInt(m.group(2));}
            if(count==null||count<1||capacity<count||capacity>64||match!=null)return Optional.empty();
            match=new Observation(row.nativeSlot(),count,capacity,"profiles","UNKNOWN: active immutable SkyBlock profile/Garden identity not supplied by this control");
        }
        return Optional.ofNullable(match);
    }
}
