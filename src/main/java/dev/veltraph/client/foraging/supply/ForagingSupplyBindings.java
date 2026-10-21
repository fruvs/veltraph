package dev.veltraph.client.foraging.supply;

import java.util.*;
import com.google.gson.*;
import dev.veltraph.client.skills.SkillContracts;
import dev.veltraph.client.world.BlockPos;

/** Observed local transaction metadata; source item predicates and acquisition labels are opaque. */
public record ForagingSupplyBindings(int schema, BlockPos chest, BlockPos stand, String chestTitle,
                                    Map<ForagingSupplyConfig.Type, Resource> resources, long timeoutMs) {
    public record Resource(String id, String name, String components, int quantity,
                           String openCommand, String offerTitle, String offerName,
                           String offerComponents, long maximumCost, String pursePrefix) {
        public Resource {
            SkillContracts.identity(id,name);
            if(components==null||components.isBlank()||quantity<1||quantity>64)throw new IllegalArgumentException("Exact components and quantity 1..64 required");
            if(openCommand==null||!openCommand.matches("[a-zA-Z0-9 _'-]{1,100}")||offerTitle==null||offerTitle.isBlank()||offerName==null||offerName.isBlank()||offerComponents==null||offerComponents.isBlank()||maximumCost<1||pursePrefix==null||pursePrefix.isBlank())throw new IllegalArgumentException("Observed acquisition command, offer identity, quote cap and purse prefix required");
        }
    }
    public ForagingSupplyBindings {
        resources=Map.copyOf(resources);
        if(schema!=1||chest==null||stand==null||chestTitle==null||chestTitle.isBlank()||timeoutMs<1000||timeoutMs>120000||resources.size()!=2||!resources.keySet().equals(Set.of(ForagingSupplyConfig.Type.BONEMEAL,ForagingSupplyConfig.Type.SAPLING)))throw new IllegalArgumentException("Explicit chest and both resource bindings required");
    }
    public static ForagingSupplyBindings parse(String json) {
        try {
            var root=JsonParser.parseString(json).getAsJsonObject();
            if(!root.keySet().equals(Set.of("schema","chest","stand","chestTitle","resources","timeoutMs")))throw new IllegalArgumentException("Unexpected/missing binding fields");
            var resources=new EnumMap<ForagingSupplyConfig.Type,Resource>(ForagingSupplyConfig.Type.class);
            var rs=root.getAsJsonObject("resources");
            for(var type:ForagingSupplyConfig.Type.values()) {
                var r=rs.getAsJsonObject(type.name());
                if(r==null||!r.keySet().equals(Set.of("id","name","components","quantity","openCommand","offerTitle","offerName","offerComponents","maximumCost","pursePrefix")))throw new IllegalArgumentException("Resource fields incomplete");
                resources.put(type,new Resource(r.get("id").getAsString(),r.get("name").getAsString(),r.get("components").getAsString(),r.get("quantity").getAsInt(),r.get("openCommand").getAsString(),r.get("offerTitle").getAsString(),r.get("offerName").getAsString(),r.get("offerComponents").getAsString(),r.get("maximumCost").getAsLong(),r.get("pursePrefix").getAsString()));
            }
            if(rs.size()!=2)throw new IllegalArgumentException("Only source BONEMEAL/SAPLING modes supported");
            return new ForagingSupplyBindings(root.get("schema").getAsInt(),SkillContracts.position(root.get("chest").getAsString()),SkillContracts.position(root.get("stand").getAsString()),root.get("chestTitle").getAsString(),resources,root.get("timeoutMs").getAsLong());
        }catch(RuntimeException e){throw new IllegalArgumentException("Invalid explicit Foraging chest supply bindings: "+e.getMessage(),e);}
    }
}
