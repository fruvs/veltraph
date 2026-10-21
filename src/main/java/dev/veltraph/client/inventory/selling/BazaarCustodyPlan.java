package dev.veltraph.client.inventory.selling;

import com.google.gson.*;
import java.math.BigDecimal;
import java.util.*;

/** Explicit local inventory/sack custody contracts; unrecovered prices, commands and labels have no defaults. */
public record BazaarCustodyPlan(String command,String menuTitle,String confirmTitle,String inventoryButton,String inventoryConfirm,
                               String sacksButton,String sacksConfirm,String receiptTemplate,String currencyPrefix,
                               SellingAcknowledgment.Denomination denomination,List<InventoryItem> inventory,List<Sack> sacks,
                               int maxQuantity,BigDecimal maxProceeds,int delayTicks,int timeoutTicks) {
    public record InventoryItem(int index,String registryId,String name,String components,int quantity,BigDecimal unitValue) {
        public InventoryItem {if(index<9||index>35||quantity<1||quantity>64)throw new IllegalArgumentException("Exact main stack quantity/index required");BazaarCustodyPlan.identity(registryId,name,components,unitValue);}
        public String identity(){return registryId+"\n"+name+"\n"+components;}
    }
    /** A native server container counter marker, with one exact full-component {quantity} substitution. */
    public record Sack(String name,int quantity,BigDecimal unitValue,int counterSlot,String markerRegistryId,String markerName,String markerComponentsTemplate) {
        public Sack {
            if(name==null||name.isBlank()||quantity<1||quantity>1000000||counterSlot<0||counterSlot>53
                    ||markerComponentsTemplate==null||occurrences(markerComponentsTemplate,"{quantity}")!=1)throw new IllegalArgumentException("Explicit sack counter custody contract required");
            BazaarCustodyPlan.identity(markerRegistryId,markerName,markerComponentsTemplate,unitValue);
        }
        public String components(int quantity){return markerComponentsTemplate.replace("{quantity}",Integer.toString(quantity));}
    }
    private static void identity(String registry,String name,String components,BigDecimal value){
        if(registry==null||!registry.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||name==null||name.isBlank()||components==null||components.isBlank()
                ||value==null||value.signum()<0||value.scale()>2||value.precision()>18)throw new IllegalArgumentException("Exact identity and observed unit value required");
    }
    private static int occurrences(String body,String token){return (body.length()-body.replace(token,"").length())/token.length();}
    public BazaarCustodyPlan {
        inventory=List.copyOf(inventory);sacks=List.copyOf(sacks);
        if(command==null||command.isBlank()||command.startsWith("/")||command.contains("\n")||command.contains("\r")||command.length()>256
                ||menuTitle==null||menuTitle.isBlank()||confirmTitle==null||confirmTitle.isBlank()||menuTitle.equals(confirmTitle)
                ||currencyPrefix==null||currencyPrefix.isBlank()||denomination==null||inventory.isEmpty()&&sacks.isEmpty()||inventory.size()>27||sacks.size()>16
                ||maxQuantity<1||maxQuantity>1000000||maxProceeds==null||maxProceeds.signum()<=0||delayTicks<1||timeoutTicks<delayTicks
                ||receiptTemplate==null||occurrences(receiptTemplate,"{quantity}")!=1||occurrences(receiptTemplate,"{coins}")!=1
                ||!inventory.isEmpty()&&(inventoryButton==null||inventoryButton.isBlank()||inventoryConfirm==null||inventoryConfirm.isBlank())
                ||!sacks.isEmpty()&&(sacksButton==null||sacksButton.isBlank()||sacksConfirm==null||sacksConfirm.isBlank()))throw new IllegalArgumentException("Complete bounded Bazaar contract required");
        var indices=new HashSet<Integer>();for(var item:inventory)if(!indices.add(item.index))throw new IllegalArgumentException("Duplicate Bazaar main index");
        var slots=new HashSet<Integer>();var names=new HashSet<String>();for(var sack:sacks)if(!slots.add(sack.counterSlot)||!names.add(sack.name))throw new IllegalArgumentException("Duplicate sack marker/name");
        int total=0;BigDecimal proceeds=BigDecimal.ZERO;
        for(var item:inventory){total=Math.addExact(total,item.quantity);proceeds=proceeds.add(item.unitValue.multiply(BigDecimal.valueOf(item.quantity)));}
        for(var sack:sacks){total=Math.addExact(total,sack.quantity);proceeds=proceeds.add(sack.unitValue.multiply(BigDecimal.valueOf(sack.quantity)));}
        if(total>maxQuantity||proceeds.compareTo(maxProceeds)>0)throw new IllegalArgumentException("Combined Bazaar quantity/value budget exceeded");
    }
    public static BazaarCustodyPlan parse(String json){
        var o=JsonParser.parseString(json).getAsJsonObject();fields(o,Set.of("command","menuTitle","confirmTitle","inventoryButton","inventoryConfirm","sacksButton","sacksConfirm","receiptTemplate","currencyPrefix","denomination","inventory","sacks","maxQuantity","maxProceeds","delayTicks","timeoutTicks"));
        var items=new ArrayList<InventoryItem>();for(var value:o.getAsJsonArray("inventory")){var item=value.getAsJsonObject();fields(item,Set.of("index","registryId","name","components","quantity","unitValue"));items.add(new InventoryItem(integer(item,"index"),string(item,"registryId"),string(item,"name"),string(item,"components"),integer(item,"quantity"),decimal(item,"unitValue")));}
        var sacks=new ArrayList<Sack>();for(var value:o.getAsJsonArray("sacks")){var sack=value.getAsJsonObject();fields(sack,Set.of("name","quantity","unitValue","counterSlot","markerRegistryId","markerName","markerComponentsTemplate"));sacks.add(new Sack(string(sack,"name"),integer(sack,"quantity"),decimal(sack,"unitValue"),integer(sack,"counterSlot"),string(sack,"markerRegistryId"),string(sack,"markerName"),string(sack,"markerComponentsTemplate")));}
        return new BazaarCustodyPlan(string(o,"command"),string(o,"menuTitle"),string(o,"confirmTitle"),string(o,"inventoryButton"),string(o,"inventoryConfirm"),string(o,"sacksButton"),string(o,"sacksConfirm"),string(o,"receiptTemplate"),string(o,"currencyPrefix"),SellingAcknowledgment.Denomination.valueOf(string(o,"denomination")),items,sacks,integer(o,"maxQuantity"),decimal(o,"maxProceeds"),integer(o,"delayTicks"),integer(o,"timeoutTicks"));
    }
    private static void fields(JsonObject o,Set<String> names){if(!o.keySet().equals(names))throw new IllegalArgumentException("Exact required Bazaar profile fields required");}
    private static String string(JsonObject o,String key){var v=o.get(key);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Required literal "+key);return v.getAsString();}
    private static BigDecimal decimal(JsonObject o,String key){var v=o.get(key);if(v==null||!v.isJsonPrimitive()||v.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Required numeric "+key);return new BigDecimal(v.getAsString());}
    private static int integer(JsonObject o,String key){return decimal(o,key).intValueExact();}
}
