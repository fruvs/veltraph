package dev.veltraph.client.fishing.offense;

import com.google.gson.Gson;
import java.util.List;
import java.util.Objects;

/** Explicit local contract; never guesses salted source item names, costs, pacing or ability outcomes. */
public record FishingOffenseProfile(String itemId,String itemName,List<String> itemLore,String targetType,
        String abilityReceiptTemplate,Integer manaCost,Long pacingMinMillis,Long pacingMaxMillis,
        Long timeoutMillis,Integer maxUses,boolean supportedWaterReturn) {
    public FishingOffenseProfile {
        for(String value:new String[]{itemId,itemName,targetType,abilityReceiptTemplate})
            if(value==null||value.isBlank()||value.length()>256)throw new IllegalArgumentException("Declare exact offensive tool, target and native ability receipt");
        if(!itemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||!targetType.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                ||targetType.equals("minecraft:blaze")||targetType.equals("minecraft:armor_stand")
                ||!abilityReceiptTemplate.contains("{target}"))throw new IllegalArgumentException("Declare supported living target and receipt containing {target}; opaque Blaze branch is unavailable");
        if(itemLore==null||itemLore.isEmpty()||itemLore.size()>32||itemLore.stream().anyMatch(s->s==null||s.isBlank()||s.length()>256))
            throw new IllegalArgumentException("Declare exact offensive tool lore");
        itemLore=List.copyOf(itemLore);
        if(manaCost==null||manaCost<1||manaCost>100000||pacingMinMillis==null||pacingMaxMillis==null
                ||pacingMinMillis<50||pacingMaxMillis<pacingMinMillis||pacingMaxMillis>10000
                ||timeoutMillis==null||timeoutMillis<250||timeoutMillis>30000||timeoutMillis<=pacingMaxMillis
                ||maxUses==null||maxUses<1||maxUses>20)throw new IllegalArgumentException("Declare positive mana cost, finite pacing, deadline and use limit");
    }
    /** Existing dry profiles remain dry. Water return requires the explicit additional contract field. */
    public FishingOffenseProfile(String itemId,String itemName,List<String> itemLore,String targetType,String abilityReceiptTemplate,Integer manaCost,Long pacingMinMillis,Long pacingMaxMillis,Long timeoutMillis,Integer maxUses){this(itemId,itemName,itemLore,targetType,abilityReceiptTemplate,manaCost,pacingMinMillis,pacingMaxMillis,timeoutMillis,maxUses,false);}
    public String receipt(java.util.UUID target){return abilityReceiptTemplate.replace("{target}",target.toString());}
    public static FishingOffenseProfile parse(String json) {
        try {if(json==null||json.length()>8192)throw new IllegalArgumentException();return Objects.requireNonNull(new Gson().fromJson(json,FishingOffenseProfile.class));}
        catch(RuntimeException failure){throw new IllegalArgumentException("Configure exact offensive Fishing tool, lore, target, mana, pacing and ability receipt profile",failure);}
    }
}
