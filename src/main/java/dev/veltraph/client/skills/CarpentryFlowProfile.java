package dev.veltraph.client.skills;

import com.google.gson.*;
import dev.veltraph.client.world.BlockPos;
import java.util.*;
import java.math.BigDecimal;
import java.util.regex.Pattern;

/** Explicit source-unrecovered recipe, opener, price and outcome contracts. */
public record CarpentryFlowProfile(Target craft,Target shop,List<Material> materials,Item output,
        String recipeLore,String recipeComponents,String costLore,long maxCraftCost,int craftsPerBatch,int batches,
        boolean cursorResults,boolean buyMissing,int outputPerCraft,int sink,long spendBudget,long valueBudget,
        String currencyPrefix,long timeout,long runTimeout,long delay,boolean returnToStart,Sale sale) {
    public record Item(String id,String name,String components,long value){
        public Item{SkillContracts.identity(id,name);if(components==null||components.isBlank()||components.length()>12000||value<1)throw new IllegalArgumentException("Exact components and positive values required");}
        public String key(){return id+"|"+name+"|"+components;}
    }
    public record Target(String title,BlockPos position,String block,String command){
        public Target{if(title==null||title.isBlank()||command==null||!command.isEmpty()&&!command.matches("[a-zA-Z0-9_:-]{1,64}")||command.isEmpty()&&(position==null||block==null||!block.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")))throw new IllegalArgumentException("Provide exact menu title and one block or single-token gameplay command opener");}
    }
    public record Offer(String label,int amount,long maxCost,String receipt){
        public Offer{if(label.isBlank()||amount<1||amount>64||maxCost<1||receipt.length()>512||!receipt.startsWith("^")||!receipt.endsWith("$")||!receipt.contains("(?<quantity>")||!receipt.contains("(?<cost>"))throw new IllegalArgumentException("Exact bounded resource offer/receipt required");Pattern.compile(receipt);}
    }
    public record Material(Item item,int perCraft,Offer offer){public Material{if(perCraft<1||perCraft>64)throw new IllegalArgumentException("Bounded per-craft materials required");}}
    public record Sale(Target target,BigDecimal unitProceeds,BigDecimal maxProceeds,String quoteLabel,String receipt,CarpentrySaleProtocol protocol){
        public Sale{if(unitProceeds.signum()<=0||maxProceeds.signum()<=0||unitProceeds.scale()>2||quoteLabel.isBlank()||!receipt.contains("{quantity}")||!receipt.contains("{item}")||!receipt.contains("{coins}"))throw new IllegalArgumentException("Exact output sale values/quote/receipt required");}
    }
    public CarpentryFlowProfile{
        materials=List.copyOf(materials);
        if(materials.isEmpty()||materials.size()>8||new HashSet<>(materials.stream().map(m->m.item().key()).toList()).size()!=materials.size())throw new IllegalArgumentException("Configure distinct exact material requirements");
        if(recipeLore.isBlank()||recipeComponents.isBlank()||costLore.length()>256||!costLore.startsWith("^")||!costLore.endsWith("$")||!costLore.contains("(?<cost>")||maxCraftCost<0)throw new IllegalArgumentException("Exact recipe lore/components and fee observation required");Pattern.compile(costLore);
        if(craftsPerBatch<1||craftsPerBatch>16||batches<1||batches>16||outputPerCraft<1||outputPerCraft>64||Math.multiplyExact(craftsPerBatch,outputPerCraft)>64||sink<9||sink>35||spendBudget<1||valueBudget<1||currencyPrefix.isBlank()||timeout<250||timeout>30000||runTimeout<1000||runTimeout>600000||delay<0||delay>2000)throw new IllegalArgumentException("Invalid finite crafting, spend, sink or timer bounds");
        if(buyMissing&&(shop==null||materials.stream().anyMatch(m->m.offer()==null)))throw new IllegalArgumentException("Supply all exact material acquisition contracts");
        long value=0;for(var m:materials){if(m.item().key().equals(output.key()))throw new IllegalArgumentException("Material/result identities must differ");value=Math.addExact(value,Math.multiplyExact(m.item().value(),Math.multiplyExact(m.perCraft(),craftsPerBatch)));}if(value>valueBudget)throw new IllegalArgumentException("One batch exceeds resource value budget");
        if(sale!=null&&sale.protocol()!=null&&sale.target().title().equals(sale.protocol().confirmationTitle()))throw new IllegalArgumentException("Bazaar initial and confirmation titles must differ");
        if(sale!=null&&sale.protocol()==null)throw new IllegalArgumentException("Bazaar sale protocol required");
        if(sale!=null&&sale.protocol().fallback()!=null&&sale.protocol().fallback().unitProceeds().multiply(BigDecimal.valueOf((long)craftsPerBatch*outputPerCraft)).compareTo(sale.maxProceeds())>0)throw new IllegalArgumentException("Fallback stack exceeds proceeds cap");
        if(sale!=null&&sale.unitProceeds().multiply(BigDecimal.valueOf((long)craftsPerBatch*outputPerCraft)).compareTo(sale.maxProceeds())>0)throw new IllegalArgumentException("One result stack exceeds proceeds cap");
    }
    public static CarpentryFlowProfile parse(String json){
        if(json==null||json.isBlank()||json.length()>131072)throw new IllegalArgumentException("Provide a bounded carpentry flow profile");
        try{var p=JsonParser.parseString(json).getAsJsonObject();var materials=new ArrayList<Material>();for(var row:p.getAsJsonArray("materials")){var m=row.getAsJsonObject();Offer offer=null;if(m.has("offer")){var o=m.getAsJsonObject("offer");offer=new Offer(text(o,"label"),integer(o,"amount"),number(o,"maxCost"),text(o,"receipt"));}materials.add(new Material(item(m.getAsJsonObject("item")),integer(m,"perCraft"),offer));}
            Sale sale=null;if(p.has("sale")&&!p.get("sale").isJsonNull()){var s=p.getAsJsonObject("sale");sale=new Sale(target(s.getAsJsonObject("target")),new BigDecimal(text(s,"unitProceeds")),new BigDecimal(text(s,"maxProceeds")),text(s,"quoteLabel"),text(s,"receipt"),CarpentrySaleProtocol.parse(s));}
            boolean buy=p.get("buyMissing").getAsBoolean();return new CarpentryFlowProfile(target(p.getAsJsonObject("craft")),buy?target(p.getAsJsonObject("shop")):null,materials,item(p.getAsJsonObject("output")),text(p,"recipeLore"),p.get("recipeComponents").getAsString(),text(p,"costLore"),number(p,"maxCraftCost"),integer(p,"craftsPerBatch"),integer(p,"batches"),p.get("cursorResults").getAsBoolean(),buy,integer(p,"outputPerCraft"),integer(p,"sink"),number(p,"spendBudget"),number(p,"valueBudget"),text(p,"currencyPrefix"),number(p,"timeout"),number(p,"runTimeout"),number(p,"delay"),p.get("returnToStart").getAsBoolean(),sale);
        }catch(RuntimeException error){throw new IllegalArgumentException("Invalid carpentry profile: "+error.getMessage(),error);}
    }
    private static long number(JsonObject p,String key){return new BigDecimal(p.get(key).getAsString()).longValueExact();}
    private static int integer(JsonObject p,String key){return Math.toIntExact(number(p,key));}
    private static String text(JsonObject p,String key){String value=p.get(key).getAsString();if(value.isBlank())throw new IllegalArgumentException("Blank "+key);return value;}
    private static Item item(JsonObject p){return new Item(text(p,"id"),text(p,"name"),text(p,"components"),number(p,"value"));}
    private static Target target(JsonObject p){String command=p.has("command")?p.get("command").getAsString():"";return new Target(text(p,"title"),command.isEmpty()?SkillContracts.position(text(p,"position")):null,command.isEmpty()?text(p,"block"):"",command);}
}
