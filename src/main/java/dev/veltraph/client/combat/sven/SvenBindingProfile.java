package dev.veltraph.client.combat.sven;

import com.google.gson.*;
import java.util.*;

/** Explicit salted bindings plus local bounds. Parsing never manufactures original identities. */
public record SvenBindingProfile(int schema,SvenCaveProfile profile,String warpCommand,String travelToolLoreToken,long operationTimeoutMs,
        boolean modifyJumpBoost,long buffCooldownMs,Bazaar bazaar,Inventory inventory,Buff buff) {
    public record Bazaar(String command,String searchMenu,String searchButton,String searchInput,String resultsMenu,
                         String productName,String productMenu,String buyInstantly,String buyMenu,String buyOne,
                         String priceLorePrefix,long maxCoins){}
    public record Inventory(String protectedToolTag,String protectedRegistryPathSubstring,String resourceMoveName){}
    public record Buff(String skyblockItemId,String skyblockItemName,String skyblockMenu,String cookieButton,String cookieMenu,
                       String potionButton,String potionMenuPrefix,List<String> effectNames,String disabledLoreMarker,String nextPageButton,String openCommand,
                       String footerPattern,String footerStart,String footerEnd,String footerInactive,String rowLabel,String rowNeighborMarker,String nextRowInactivePrefix){
        public Buff{effectNames=List.copyOf(effectNames);}
    }
    public record Parsed(SvenBindingProfile profile,String error){public boolean bound(){return profile!=null&&error.isEmpty();}}
    public static Parsed parse(String json,SvenCaveProfile selected){
        if(json==null||json.isBlank())return new Parsed(null,"SVEN_SOURCE_BINDING_PROFILE is blank; recovered native contract is unbound");
        if(json.length()>32768)return new Parsed(null,"Binding profile exceeds 32768 characters");
        try{
            var root=JsonParser.parseString(json).getAsJsonObject();requireKeys(root,Set.of("schema","profile","warpCommand","travelToolLoreToken","operationTimeoutMs","modifyJumpBoost","buffCooldownMs","bazaar","inventory","buff"));
            int schema=Math.toIntExact(number(root,"schema"));if(schema!=1)throw new IllegalArgumentException("Expected source binding schema 1");
            var profile=SvenCaveProfile.valueOf(text(root,"profile"));if(profile!=selected)throw new IllegalArgumentException("Binding profile does not match selected SVEN_CAVE_TYPE");
            long timeout=number(root,"operationTimeoutMs");if(timeout<500||timeout>60000)throw new IllegalArgumentException("operationTimeoutMs must be 500..60000 (explicit local bound)");
            if(!root.has("modifyJumpBoost")||!root.get("modifyJumpBoost").isJsonPrimitive()||!root.get("modifyJumpBoost").getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Explicit MODIFY_JUMP_BOOST control must be a JSON boolean");
            boolean modify=root.get("modifyJumpBoost").getAsBoolean();
            long cooldown=root.has("buffCooldownMs")?number(root,"buffCooldownMs"):-1;
            if(modify&&(cooldown<0||cooldown>3600000))throw new IllegalArgumentException("Jump Boost modification requires explicit recovered buffCooldownMs");
            Bazaar bazaar=null;Inventory inventory=null;Buff buff=null;
            if(root.has("bazaar")){
                var b=root.getAsJsonObject("bazaar");requireKeys(b,Set.of("command","searchMenu","searchButton","searchInput","resultsMenu","productName","productMenu","buyInstantly","buyMenu","buyOne","priceLorePrefix","maxCoins"));
                long max=number(b,"maxCoins");if(max<1||max>1_000_000_000)throw new IllegalArgumentException("Explicit purchase cap maxCoins must be 1..1000000000");
                bazaar=new Bazaar(command(text(b,"command")),text(b,"searchMenu"),text(b,"searchButton"),text(b,"searchInput"),text(b,"resultsMenu"),text(b,"productName"),text(b,"productMenu"),text(b,"buyInstantly"),text(b,"buyMenu"),text(b,"buyOne"),text(b,"priceLorePrefix"),max);
                if(bazaar.searchInput().length()>100)throw new IllegalArgumentException("Search input exceeds 100 characters");
            }
            if(root.has("inventory")){
                var i=root.getAsJsonObject("inventory");requireKeys(i,Set.of("protectedToolTag","protectedRegistryPathSubstring","resourceMoveName"));String tag=text(i,"protectedToolTag");
                if(!tag.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"))throw new IllegalArgumentException("protectedToolTag must be an explicit namespace:path");
                inventory=new Inventory(tag,text(i,"protectedRegistryPathSubstring"),i.has("resourceMoveName")?text(i,"resourceMoveName"):"");
            }
            if(root.has("buff")){
                var b=root.getAsJsonObject("buff");requireKeys(b,Set.of("skyblockItemId","skyblockItemName","skyblockMenu","cookieButton","cookieMenu","potionButton","potionMenuPrefix","effectNames","disabledLoreMarker","nextPageButton","openCommand","footerPattern","footerStart","footerEnd","footerInactive","rowLabel","rowNeighborMarker","nextRowInactivePrefix"));
                String id=text(b,"skyblockItemId");if(!id.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"))throw new IllegalArgumentException("SkyBlock item ID must be an explicit namespace:path");
                String regex=text(b,"footerPattern");java.util.regex.Pattern.compile(regex);
                var names=new ArrayList<String>();for(var element:b.getAsJsonArray("effectNames")){if(!element.isJsonPrimitive()||!element.getAsJsonPrimitive().isString()||element.getAsString().isBlank()||element.getAsString().length()>100)throw new IllegalArgumentException("Invalid source effect name");names.add(element.getAsString());}
                if(names.isEmpty()||names.size()>32||new HashSet<>(names).size()!=names.size())throw new IllegalArgumentException("Source effectNames must contain 1..32 distinct actual labels");
                buff=new Buff(id,text(b,"skyblockItemName"),text(b,"skyblockMenu"),text(b,"cookieButton"),text(b,"cookieMenu"),text(b,"potionButton"),text(b,"potionMenuPrefix"),names,text(b,"disabledLoreMarker"),text(b,"nextPageButton"),b.has("openCommand")?command(text(b,"openCommand")):"",regex,text(b,"footerStart"),text(b,"footerEnd"),text(b,"footerInactive"),text(b,"rowLabel"),text(b,"rowNeighborMarker"),text(b,"nextRowInactivePrefix"));
            }
            if(modify&&buff==null)throw new IllegalArgumentException("Jump Boost modification requires current footer/ordered-row and seven-state SkyBlock/Cookie/Potion menu bindings");
            return new Parsed(new SvenBindingProfile(schema,profile,command(text(root,"warpCommand")),text(root,"travelToolLoreToken"),timeout,modify,cooldown,bazaar,inventory,buff),"");
        }catch(RuntimeException invalid){return new Parsed(null,"Invalid Sven source binding profile: "+invalid.getMessage());}
    }
    private static String text(JsonObject o,String name){
        if(!o.has(name)||!o.get(name).isJsonPrimitive()||!o.get(name).getAsJsonPrimitive().isString())throw new IllegalArgumentException("Missing string binding: "+name);
        String value=o.get(name).getAsString();if(value.isBlank()||value.length()>256||value.codePoints().anyMatch(c->Character.isISOControl(c)||c==0xA7))throw new IllegalArgumentException("Invalid or blank binding: "+name);return value;
    }
    private static long number(JsonObject o,String name){if(!o.has(name)||!o.get(name).isJsonPrimitive()||!o.get(name).getAsJsonPrimitive().isNumber()||!o.get(name).getAsString().matches("-?[0-9]+"))throw new IllegalArgumentException("Missing integer binding: "+name);return Long.parseLong(o.get(name).getAsString());}
    private static String command(String value){if(!value.matches("[a-z][a-z0-9_-]*(?: [a-zA-Z0-9 _-]+)?"))throw new IllegalArgumentException("Command must be a single explicit slashless command");return value;}
    private static void requireKeys(JsonObject o,Set<String> keys){if(!keys.containsAll(o.keySet()))throw new IllegalArgumentException("Unknown binding fields: "+o.keySet().stream().filter(k->!keys.contains(k)).toList());}
}
