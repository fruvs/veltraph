package dev.veltraph.client.inventory.selling;

import com.google.gson.*;
import dev.veltraph.client.config.AutoSellMode;
import java.math.BigDecimal;
import java.util.*;

/** Frozen finite approvals. A native UUID and complete item identity are independent checks. */
public record GeorgeBatchPlan(int maxPets, BigDecimal maxProceeds, int timeoutTicks, String uuidPath, List<Pet> pets) {
    public record Pet(int inventoryIndex, UUID uuid) {
        public Pet { if(inventoryIndex<9||inventoryIndex>35||uuid==null)throw new IllegalArgumentException("Exact main-slot pet UUID required"); }
    }
    public record Entry(SellingRule rule, UUID uuid) {}
    public GeorgeBatchPlan {
        pets=List.copyOf(pets);
        if(maxPets<1||maxPets>20||pets.size()>maxPets||maxProceeds==null||maxProceeds.signum()<=0||maxProceeds.scale()>2
                ||timeoutTicks<1||timeoutTicks>24000||uuidPath==null||!uuidPath.matches("[A-Za-z0-9_:-]+(?:\\.[A-Za-z0-9_:-]+){0,4}"))throw new IllegalArgumentException("Explicit finite George batch limits and native UUID path required");
        if(pets.stream().map(Pet::inventoryIndex).distinct().count()!=pets.size()||pets.stream().map(Pet::uuid).distinct().count()!=pets.size())throw new IllegalArgumentException("Duplicate approved pet slot or UUID");
    }
    public static GeorgeBatchPlan parse(String json) {
        var o=JsonParser.parseString(json).getAsJsonObject();if(!o.keySet().equals(Set.of("maxPets","maxProceeds","timeoutTicks","uuidPath","pets")))throw new IllegalArgumentException("Exact George batch fields required");
        var pets=new ArrayList<Pet>();for(var value:o.getAsJsonArray("pets")){var p=value.getAsJsonObject();if(!p.keySet().equals(Set.of("inventoryIndex","uuid")))throw new IllegalArgumentException("Exact pet slot and UUID required");pets.add(new Pet(integer(p,"inventoryIndex"),canonical(string(p,"uuid"))));}
        return new GeorgeBatchPlan(integer(o,"maxPets"),new BigDecimal(numeric(o,"maxProceeds")),integer(o,"timeoutTicks"),string(o,"uuidPath"),pets);
    }
    public List<Entry> freeze(List<SellingRule> rules,SellingObservation initial,SellingRuntime.Settings settings) {
        if(initial.menu()!=null||!initial.cursorEmpty()||initial.coins()==null||settings.channel()!=AutoSellMode.GEORGE_PETS)throw new IllegalArgumentException("Closed native healthy purse/cursor required");
        var queue=new ArrayList<Entry>();BigDecimal total=BigDecimal.ZERO;
        var george=rules.stream().filter(r->r.channel()==AutoSellMode.GEORGE_PETS).toList();
        var approvals=pets;if(pets.isEmpty()){approvals=george.stream().filter(r->r.matches(initial.inventoryStack(r.inventoryIndex()).orElse(null))).map(r->{var id=nativeUuid(initial.inventoryStack(r.inventoryIndex()).orElseThrow().customData().get(uuidPath));if(id==null)throw new IllegalArgumentException("Eligible configured pet requires canonical native UUID");return new Pet(r.inventoryIndex(),id);}).toList();}
        if(approvals.isEmpty()||approvals.size()>maxPets)throw new IllegalArgumentException("Frozen eligible queue exceeds explicit pet limit or is empty");
        final var approved=approvals;
        if(!pets.isEmpty()&&george.size()!=pets.size())throw new IllegalArgumentException("Every configured George rule must be explicitly approved in this batch");
        var seen=new HashSet<UUID>();for(var stack:initial.inventory()){String raw=stack.customData().get(uuidPath);if(raw==null||stack.empty())continue;UUID id=nativeUuid(raw);if(approved.stream().anyMatch(p->p.uuid().equals(id))&&!seen.add(id))throw new IllegalArgumentException("Approved UUID duplicated in native inventory");}
        for(var pet:approved){var matches=george.stream().filter(r->r.inventoryIndex()==pet.inventoryIndex()).toList();if(matches.size()!=1)throw new IllegalArgumentException("One exact rule per approved UUID required");var rule=matches.getFirst();var item=initial.inventoryStack(pet.inventoryIndex()).orElse(null);
            if(!rule.matches(item)||!pet.uuid().equals(nativeUuid(item.customData().get(uuidPath)))||rule.total().signum()<=0||rule.total().compareTo(settings.minProceeds())<0||rule.total().compareTo(settings.maxProceeds())>0)throw new IllegalArgumentException("Approved native UUID, full identity or exact quote bound missing");
            queue.add(new Entry(rule,pet.uuid()));total=total.add(rule.total());}
        if(queue.size()>settings.maxQuantity()||total.compareTo(maxProceeds)>0||total.compareTo(settings.maxProceeds())>0)throw new IllegalArgumentException("Cumulative George quantity/proceeds limit exceeded");
        return List.copyOf(queue);
    }
    public static UUID nativeUuid(String raw){if(raw==null)return null;try {var value=JsonParser.parseString(raw);return value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString()?canonical(value.getAsString()):null;}catch(RuntimeException invalid){return null;}}
    private static UUID canonical(String raw){var id=UUID.fromString(raw);if(!id.toString().equals(raw))throw new IllegalArgumentException("Canonical native pet UUID required");return id;}
    private static String string(JsonObject o,String key){var v=o.get(key);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Required literal "+key);return v.getAsString();}
    private static String numeric(JsonObject o,String key){var v=o.get(key);if(v==null||!v.isJsonPrimitive()||v.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Required number "+key);return v.getAsString();}
    private static int integer(JsonObject o,String key){return new BigDecimal(numeric(o,key)).intValueExact();}
}
