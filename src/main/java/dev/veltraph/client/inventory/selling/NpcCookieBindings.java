package dev.veltraph.client.inventory.selling;

import com.google.gson.*;
import dev.veltraph.client.gui.MenuSnapshot;
import java.math.BigDecimal;
import java.util.*;

/** Observed native contracts, not decrypted/invented server command or cookie labels. */
public record NpcCookieBindings(String command,int menuItemSlot,Item menuItem,String skyBlockTitle,Item cookieButton,
                                String cookieTitle,Item sellerButton,String sellerTitle,Item sellerMarker) {
    public record Item(String registryId,String name,int count,String components){
        public Item {if(registryId==null||!registryId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||name==null||name.isBlank()||count<1||count>64||components==null||components.isBlank())throw new IllegalArgumentException("Full observed item identity required");}
    }
    public NpcCookieBindings {
        if(command==null||!command.matches("[A-Za-z][A-Za-z0-9_:-]*(?: [A-Za-z0-9_:-]+){0,8}")||command.length()>128||menuItemSlot<0||menuItemSlot>8
                ||skyBlockTitle==null||cookieTitle==null||sellerTitle==null||List.of(skyBlockTitle,cookieTitle,sellerTitle).stream().anyMatch(String::isBlank)
                ||new HashSet<>(List.of(skyBlockTitle,cookieTitle,sellerTitle)).size()!=3||menuItem==null||cookieButton==null||sellerButton==null||sellerMarker==null)
            throw new IllegalArgumentException("Distinct exact menu titles, hotbar opener and observed command required");
    }
    public boolean allows(String title){return List.of(skyBlockTitle,cookieTitle,sellerTitle).contains(MenuSnapshot.clean(title));}
    public static NpcCookieBindings parse(String json){
        try{var o=JsonParser.parseString(json).getAsJsonObject();exact(o,Set.of("schema","command","menuItemSlot","menuItem","skyBlockTitle","cookieButton","cookieTitle","sellerButton","sellerTitle","sellerMarker"));
            if(integer(o,"schema")!=1)throw new IllegalArgumentException("NPC cookie schema 1 required");
            return new NpcCookieBindings(text(o,"command"),integer(o,"menuItemSlot"),item(o.getAsJsonObject("menuItem")),text(o,"skyBlockTitle"),item(o.getAsJsonObject("cookieButton")),text(o,"cookieTitle"),item(o.getAsJsonObject("sellerButton")),text(o,"sellerTitle"),item(o.getAsJsonObject("sellerMarker")));
        }catch(IllegalStateException|NullPointerException|ArithmeticException e){throw new IllegalArgumentException("Invalid observed NPC cookie bindings",e);}
    }
    private static Item item(JsonObject o){exact(o,Set.of("registryId","name","count","components"));return new Item(text(o,"registryId"),text(o,"name"),integer(o,"count"),text(o,"components"));}
    private static void exact(JsonObject o,Set<String> fields){if(!o.keySet().equals(fields))throw new IllegalArgumentException("Exact observed binding fields required");}
    private static String text(JsonObject o,String key){var v=o.get(key);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Required string "+key);return v.getAsString();}
    private static int integer(JsonObject o,String key){var v=o.get(key);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Required integer "+key);return new BigDecimal(v.getAsString()).intValueExact();}
}
