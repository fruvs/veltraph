package dev.veltraph.client.gui;

import com.google.gson.*;
import java.util.*;

/** Explicit observed reward categories; the reference XP classifier remains unavailable. */
public record SuperpairsRewardBindings(Set<Card> xpCards) {
    public record Card(String itemId,int count,String name,List<String> lore,boolean foil,String components) {
        public Card {
            Objects.requireNonNull(itemId);Objects.requireNonNull(name);Objects.requireNonNull(components);
            lore=List.copyOf(lore);
            if(!itemId.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")||count<1||name.isBlank()||components.isBlank())
                throw new IllegalArgumentException("Full observed reward item/count/name/lore/foil/components required");
            if(!JsonParser.parseString(components).isJsonObject())throw new IllegalArgumentException("Canonical native component object required");
        }
        public static Card from(MenuSnapshot.Slot slot) {
            if(slot.componentIdentity().isBlank())return null;
            return new Card(slot.itemId(),slot.count(),slot.name(),slot.lore(),slot.foil(),slot.componentIdentity());
        }
    }
    public SuperpairsRewardBindings { xpCards=Set.copyOf(xpCards); }
    public static SuperpairsRewardBindings empty(){return new SuperpairsRewardBindings(Set.of());}
    public boolean isXp(Card card){return card!=null&&xpCards.contains(card);}
    public static SuperpairsRewardBindings parse(String json) {
        if(json==null||json.isBlank())return empty();
        try {
            var root=JsonParser.parseString(json).getAsJsonObject();
            if(!root.keySet().equals(Set.of("schema","xpCards"))||!root.get("schema").toString().equals("1")||!root.get("xpCards").isJsonArray())throw new IllegalArgumentException("Exact schema 1 and xpCards required");
            Set<Card> cards=new HashSet<>();
            for(var element:root.getAsJsonArray("xpCards")) {
                var item=element.getAsJsonObject();
                if(!item.keySet().equals(Set.of("itemId","count","name","lore","foil","components")))throw new IllegalArgumentException("All exact card fields required");
                for(String key:List.of("itemId","name","components"))if(!item.get(key).isJsonPrimitive()||!item.getAsJsonPrimitive(key).isString())throw new IllegalArgumentException("Card strings required");
                if(!item.get("count").toString().matches("[1-9][0-9]*")||!item.get("foil").isJsonPrimitive()||!item.getAsJsonPrimitive("foil").isBoolean())throw new IllegalArgumentException("Exact count and foil required");
                List<String> lore=new ArrayList<>();for(var line:item.getAsJsonArray("lore")){if(!line.isJsonPrimitive()||!line.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Exact lore strings required");lore.add(line.getAsString());}
                if(!cards.add(new Card(item.get("itemId").getAsString(),item.get("count").getAsInt(),item.get("name").getAsString(),lore,item.get("foil").getAsBoolean(),item.get("components").getAsString())))throw new IllegalArgumentException("Duplicate XP card binding");
            }
            return new SuperpairsRewardBindings(cards);
        }catch(RuntimeException invalid){throw new IllegalArgumentException("Invalid explicit Superpairs reward binding",invalid);}
    }
}
