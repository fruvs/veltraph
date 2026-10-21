package dev.veltraph.client.galatea;

import com.google.gson.JsonParser;
import dev.veltraph.client.world.BlockPos;
import java.util.*;

/** Optional captured source base/set observations; ordinary loaded trees need no manual catalog. */
public final class FigObservedDescriptors {
    public record Descriptor(BlockPos componentRoot,BlockPos base,List<BlockPos> blocks,String provenance) {
        public Descriptor {
            blocks=List.copyOf(blocks);
            if(componentRoot==null||base==null||blocks.isEmpty()||new HashSet<>(blocks).size()!=blocks.size()
                    ||!blocks.contains(componentRoot)||provenance==null||provenance.isBlank())
                throw new IllegalArgumentException("Observed descriptor root, base, ordered blocks and provenance required");
        }
    }
    private final Map<BlockPos,Descriptor> entries;
    public FigObservedDescriptors(List<Descriptor> descriptors) {
        Map<BlockPos,Descriptor> map=new LinkedHashMap<>();
        for(Descriptor d:descriptors)if(map.put(d.componentRoot(),d)!=null)throw new IllegalArgumentException("Duplicate observed Fig root");
        entries=Map.copyOf(map);
    }
    public Optional<Descriptor> find(GalateaPlanner.Tree tree) {
        Descriptor d=entries.get(tree.root());if(d==null)return Optional.empty();
        return new HashSet<>(d.blocks()).equals(new HashSet<>(tree.logs()))?Optional.of(d):Optional.empty();
    }
    public static FigObservedDescriptors parse(String json) {
        if(json==null||json.isBlank())return new FigObservedDescriptors(List.of());
        var root=JsonParser.parseString(json).getAsJsonObject();
        if(!root.keySet().equals(Set.of("schema","descriptors"))||!root.get("schema").isJsonPrimitive()
                ||!root.getAsJsonPrimitive("schema").isNumber()||!root.get("schema").getAsString().equals("1")
                ||!root.get("descriptors").isJsonArray())throw new IllegalArgumentException("Observed Fig descriptor schema1 required");
        List<Descriptor> descriptors=new ArrayList<>();
        for(var row:root.getAsJsonArray("descriptors")) {
            var d=row.getAsJsonObject();
            if(!d.keySet().equals(Set.of("root","base","blocks","provenance"))||!d.get("blocks").isJsonArray()
                    ||!d.get("provenance").isJsonPrimitive()||!d.getAsJsonPrimitive("provenance").isString())throw new IllegalArgumentException("Observed Fig descriptor fields required");
            List<BlockPos> blocks=new ArrayList<>();for(var p:d.getAsJsonArray("blocks"))blocks.add(position(p));
            descriptors.add(new Descriptor(position(d.get("root")),position(d.get("base")),blocks,d.get("provenance").getAsString()));
        }
        return new FigObservedDescriptors(descriptors);
    }
    private static BlockPos position(com.google.gson.JsonElement value) {
        var a=value.getAsJsonArray();if(a.size()!=3)throw new IllegalArgumentException("Three integer coordinates required");
        int[] v=new int[3];for(int i=0;i<3;i++) {
            if(!a.get(i).isJsonPrimitive()||!a.get(i).getAsJsonPrimitive().isNumber()||!a.get(i).getAsString().matches("-?[0-9]+"))
                throw new IllegalArgumentException("Integer descriptor coordinates required");
            v[i]=Integer.parseInt(a.get(i).getAsString());
        }
        return new BlockPos(v[0],v[1],v[2]);
    }
}
