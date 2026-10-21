package dev.veltraph.client.skills;

import com.google.gson.*;
import dev.veltraph.client.world.BlockPos;
import java.util.*;
import java.util.regex.Pattern;

/** Required, bounded local contracts. No enchanted IDs, recipes, prices or receipts are inferred. */
public record AlchemyFlowProfile(List<BlockPos> stands,String standTitle,Resource input,Resource fuel,
                                 List<Step> steps,int bottles,int batches,boolean acquireMissing,
                                 BlockPos shop,String shopBlock,String shopTitle,long coinBudget,long valueBudget,
                                 long actionTimeout,long brewTimeout,long runTimeout,long clickDelay,Layout layout,AlchemySaleProfile terminalSale) {
    public AlchemyFlowProfile(List<BlockPos> stands,String standTitle,Resource input,Resource fuel,List<Step> steps,int bottles,int batches,boolean acquireMissing,BlockPos shop,String shopBlock,String shopTitle,long coinBudget,long valueBudget,long actionTimeout,long brewTimeout,long runTimeout,long clickDelay,Layout layout){
        this(stands,standTitle,input,fuel,steps,bottles,batches,acquireMissing,shop,shopBlock,shopTitle,coinBudget,valueBudget,actionTimeout,brewTimeout,runTimeout,clickDelay,layout,null);
    }
    public record Layout(String mode,int rows,List<Integer> bottleSlots,int ingredientSlot,int fuelSlot,int fuelMarkerSlot,String fuelLore) {
        public Layout {
            bottleSlots=List.copyOf(bottleSlots);
            if(!mode.equals("native")&&!mode.equals("source_13"))throw new IllegalArgumentException("Unknown alchemy menu protocol");
            if(mode.equals("source_13")){
                var all=new ArrayList<Integer>(bottleSlots);all.add(ingredientSlot);all.add(fuelSlot);all.add(fuelMarkerSlot);
                if(rows<2||rows>6||ingredientSlot!=13||bottleSlots.isEmpty()||bottleSlots.size()>3||new HashSet<>(all).size()!=all.size()||all.stream().anyMatch(s->s<0||s>=rows*9))throw new IllegalArgumentException("Configure distinct source-layout container slots with ingredient13");
                if(fuelLore==null||fuelLore.length()>256||!fuelLore.startsWith("^")||!fuelLore.endsWith("$")||!fuelLore.contains("(?<fuel>"))throw new IllegalArgumentException("Source layout needs exact named fuel lore observation");
                Pattern.compile(fuelLore);
            }
        }
        public static Layout nativeMenu(){return new Layout("native",0,List.of(0,1,2),3,4,-1,"");}
    }
    public record Item(String id,String name,String components,long value) {
        public Item {
            SkillContracts.identity(id,name);
            if(components==null||components.isBlank()||components.length()>12000||value<1) throw new IllegalArgumentException("Exact components and positive resource values are required");
        }
        public String key(){return id+"|"+name+"|"+components;}
    }
    public record Offer(String label,int amount,long maxCost,String receipt) {
        public Offer {
            if(label==null||label.isBlank()||amount<1||amount>64||maxCost<1||receipt==null||receipt.length()>512||!receipt.startsWith("^")||!receipt.endsWith("$")) throw new IllegalArgumentException("Configure bounded exact offer/receipt contract");
            if(!receipt.contains("(?<quantity>")||!receipt.contains("(?<cost>")) throw new IllegalArgumentException("Receipt requires named quantity and cost groups");
            Pattern.compile(receipt);
        }
    }
    public record Resource(Item item,Offer offer) {}
    public record Step(Resource reagent,Item output) {}
    public AlchemyFlowProfile {
        if(layout==null)layout=Layout.nativeMenu();
        stands=List.copyOf(stands);steps=List.copyOf(steps);
        if(stands.isEmpty()||stands.size()>16||new HashSet<>(stands).size()!=stands.size()||steps.isEmpty()||steps.size()>8) throw new IllegalArgumentException("Configure1..16 distinct stands and1..8 ordered steps");
        if(standTitle.isBlank()||bottles<1||bottles>3||batches<1||batches>16||coinBudget<0||valueBudget<1) throw new IllegalArgumentException("Invalid alchemy quantities or budgets");
        if(layout.bottleSlots().size()<bottles)throw new IllegalArgumentException("Layout has fewer potion slots than declared bottles");
        if(actionTimeout<250||actionTimeout>30000||brewTimeout<1000||brewTimeout>120000||runTimeout<1000||runTimeout>600000||clickDelay<0||clickDelay>2000) throw new IllegalArgumentException("Invalid alchemy time bounds");
        if(acquireMissing&&(shop==null||shopTitle.isBlank()||shopBlock.isBlank()||coinBudget<1)) throw new IllegalArgumentException("Acquisition needs an exact shop position/block/title and coin cap");
        var resources=new ArrayList<Resource>();resources.add(input);resources.add(fuel);resources.addAll(steps.stream().map(Step::reagent).toList());
        if(acquireMissing&&resources.stream().anyMatch(r->r.offer()==null)) throw new IllegalArgumentException("Every acquired resource requires an exact offer/receipt");
        if(terminalSale!=null&&!steps.getLast().output().id().equals("minecraft:potion"))throw new IllegalArgumentException("Terminal Alchemy sale only accepts declared POTION output");
        long value=Math.addExact(Math.multiplyExact(input.item().value(),bottles),fuel.item().value());
        Item previous=input.item();
        for(var step:steps) { if(step.output().key().equals(previous.key())) throw new IllegalArgumentException("Each declared brew output must differ from its input");previous=step.output();value=Math.addExact(value,step.reagent().item().value()); }
        if(value>valueBudget) throw new IllegalArgumentException("One full batch exceeds the resource value budget");
    }
    public static AlchemyFlowProfile parse(String json) {
        if(json==null||json.isBlank()||json.length()>131072) throw new IllegalArgumentException("Configure a bounded alchemy flow profile");
        try {
            var p=JsonParser.parseString(json).getAsJsonObject();
            var stands=new ArrayList<BlockPos>();for(var point:p.getAsJsonArray("stands")) stands.add(SkillContracts.position(point.getAsString()));
            var steps=new ArrayList<Step>();for(var row:p.getAsJsonArray("steps")) {var s=row.getAsJsonObject();steps.add(new Step(resource(s.getAsJsonObject("reagent")),item(s.getAsJsonObject("output"))));}
            boolean acquire=p.get("acquireMissing").getAsBoolean();
            return new AlchemyFlowProfile(stands,string(p,"standTitle"),resource(p.getAsJsonObject("input")),resource(p.getAsJsonObject("fuel")),steps,
                    p.get("bottles").getAsInt(),p.get("batches").getAsInt(),acquire,acquire?SkillContracts.position(string(p,"shop")):null,
                    acquire?string(p,"shopBlock"):"",acquire?string(p,"shopTitle"):"",p.get("coinBudget").getAsLong(),p.get("valueBudget").getAsLong(),
                    p.get("actionTimeout").getAsLong(),p.get("brewTimeout").getAsLong(),p.get("runTimeout").getAsLong(),p.get("clickDelay").getAsLong(),layout(p),p.has("terminalSale")&&!p.get("terminalSale").isJsonNull()?AlchemySaleProfile.parse(p.getAsJsonObject("terminalSale")):null);
        } catch(RuntimeException invalid) {throw new IllegalArgumentException("Invalid alchemy flow profile: "+invalid.getMessage(),invalid);}
    }
    private static String string(JsonObject value,String key){String text=value.get(key).getAsString().replaceAll("§.","").strip();if(text.isBlank())throw new IllegalArgumentException("Blank "+key);return text;}
    private static Item item(JsonObject i){return new Item(string(i,"id"),string(i,"name"),i.get("components").getAsString(),i.get("value").getAsLong());}
    private static Resource resource(JsonObject r){Offer offer=null;if(r.has("offer")&&!r.get("offer").isJsonNull()){var o=r.getAsJsonObject("offer");offer=new Offer(string(o,"label"),o.get("amount").getAsInt(),o.get("maxCost").getAsLong(),string(o,"receipt"));}return new Resource(item(r.getAsJsonObject("item")),offer);}
    private static Layout layout(JsonObject p){if(!p.has("layout"))return Layout.nativeMenu();var l=p.getAsJsonObject("layout");String mode=string(l,"mode");if(mode.equals("native"))return Layout.nativeMenu();var slots=new ArrayList<Integer>();for(var s:l.getAsJsonArray("bottleSlots"))slots.add(s.getAsInt());return new Layout(mode,l.get("rows").getAsInt(),slots,13,l.get("fuelSlot").getAsInt(),l.get("fuelMarkerSlot").getAsInt(),string(l,"fuelLore"));}
}
