package dev.veltraph.client.inventory.selling;

import com.google.gson.*;
import dev.veltraph.client.config.AutoSellMode;
import dev.veltraph.client.gui.MenuSnapshot;
import java.math.BigDecimal;
import java.util.*;

/** Exact main-inventory slot allowlist; hotbar, armor, offhand and protected tools cannot match. */
public record SellingRule(AutoSellMode channel,int inventoryIndex,String registryId,String name,String components,
                          int quantity,BigDecimal unitValue,String quoteLabel,String petDataPath,String petDataValue) {
    public SellingRule(AutoSellMode channel,int inventoryIndex,String registryId,String name,String components,int quantity,BigDecimal unitValue,String quoteLabel){this(channel,inventoryIndex,registryId,name,components,quantity,unitValue,quoteLabel,"","");}
    public SellingRule {
        name=MenuSnapshot.clean(name);quoteLabel=MenuSnapshot.clean(quoteLabel);
        if(channel==null||channel==AutoSellMode.BAZAAR||inventoryIndex<9||inventoryIndex>35
                ||registryId==null||!registryId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||name.isBlank()
                ||components==null||components.isBlank()||quantity<1||quantity>64||unitValue==null
                ||unitValue.signum()<0||unitValue.scale()>2||unitValue.precision()>18
                ||channel==AutoSellMode.GEORGE_PETS&&(quantity!=1||!registryId.equals("minecraft:player_head")||petDataPath==null
                ||!petDataPath.matches("[A-Za-z0-9_:-]+(?:\\.[A-Za-z0-9_:-]+){0,4}")||petDataValue==null||petDataValue.isBlank())
                ||channel!=AutoSellMode.TRASH&&quoteLabel.isBlank())throw new IllegalArgumentException("Invalid exact selling rule");
    }
    public String identity(){return registryId+"\n"+name+"\n"+components;}
    public BigDecimal total(){return unitValue.multiply(BigDecimal.valueOf(quantity));}
    public boolean petMatches(SellingObservation.Stack stack){return channel==AutoSellMode.GEORGE_PETS&&stack.registryId().equals("minecraft:player_head")
            &&Objects.equals(stack.customData().get(petDataPath),petDataValue);}
    public boolean matches(SellingObservation.Stack stack){return stack!=null&&!stack.protectedItem()
            &&(!stack.wearable()||petMatches(stack))&&(channel!=AutoSellMode.GEORGE_PETS||petMatches(stack))
            &&stack.inventoryIndex()==inventoryIndex&&stack.identity().equals(identity())&&stack.count()==quantity;}
    public static List<SellingRule> parse(String json){
        try {
            var array=JsonParser.parseString(json).getAsJsonArray();
            if(array.isEmpty()||array.size()>16)throw new IllegalArgumentException("Configure 1..16 exact selling rules");
            List<SellingRule> rules=new ArrayList<>();Set<String> slots=new HashSet<>();
            Set<String> fields=Set.of("channel","inventoryIndex","registryId","name","components","quantity","unitValue","quoteLabel","petDataPath","petDataValue");
            for(var entry:array){
                var value=entry.getAsJsonObject();if(!fields.containsAll(value.keySet()))throw new IllegalArgumentException("Unknown selling rule field");
                var channel=AutoSellMode.valueOf(string(value,"channel"));
                var rule=new SellingRule(channel,integer(value,"inventoryIndex"),string(value,"registryId"),string(value,"name"),
                        string(value,"components"),integer(value,"quantity"),new BigDecimal(stringOrNumber(value,"unitValue")),
                        channel==AutoSellMode.TRASH?"":string(value,"quoteLabel"),channel==AutoSellMode.GEORGE_PETS?string(value,"petDataPath"):"",
                        channel==AutoSellMode.GEORGE_PETS?string(value,"petDataValue"):"");
                if(!slots.add(channel+":"+rule.inventoryIndex))throw new IllegalArgumentException("Duplicate channel/inventory index");rules.add(rule);
            }
            return List.copyOf(rules);
        } catch(IllegalStateException|NullPointerException|ArithmeticException e){throw new IllegalArgumentException("Invalid required selling-rule JSON",e);}
    }
    private static String string(JsonObject value,String key){
        var entry=value.get(key);if(entry==null||!entry.isJsonPrimitive()||!entry.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Required string: "+key);
        return entry.getAsString();
    }
    private static String stringOrNumber(JsonObject value,String key){
        var entry=value.get(key);if(entry==null||!entry.isJsonPrimitive()||entry.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Required numeric value: "+key);
        return entry.getAsString();
    }
    private static int integer(JsonObject value,String key){return new BigDecimal(stringOrNumber(value,key)).intValueExact();}
}
