package dev.veltraph.client.skills;

import com.google.gson.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.BlockPos;
import java.util.*;
import java.nio.file.*;
import java.io.IOException;

/** Explicit local area -> [[x,y,z]] catalog and acknowledged claims; never downloads a soul database. */
public final class FairySoulCatalog {
    public record Soul(GameArea area,BlockPos position) {}
    public record Claim(String dimension,Soul soul) {}
    public record Descriptor(Soul soul,BlockPos start,FairySoulRouteCompiler.Plan route) {}
    public static List<Descriptor> descriptors(String text){
        var result=new ArrayList<Descriptor>();var identities=new HashSet<Soul>();var root=JsonParser.parseString(text).getAsJsonObject();
        for(var entry:root.entrySet()){
            var area=GameArea.valueOf(entry.getKey());if(area==GameArea.UNKNOWN)throw new IllegalArgumentException("Soul catalog requires a known area");
            for(var row:entry.getValue().getAsJsonArray()){
                BlockPos position,start=null;FairySoulRouteCompiler.Plan route=null;
                if(row.isJsonArray())position=coordinate(row);
                else {var object=row.getAsJsonObject();position=coordinate(object.get("position"));start=coordinate(object.get("start"));
                    route=FairySoulRouteCompiler.compile(start,steps(object.get("outbound")),steps(object.get("return")));
                }
                var soul=new Soul(area,position);if(!identities.add(soul))throw new IllegalArgumentException("Duplicate soul coordinate");
                result.add(new Descriptor(soul,start,route));if(result.size()>512)throw new IllegalArgumentException("Local soul catalog exceeds512 entries");
            }
        }if(result.isEmpty())throw new IllegalArgumentException("Local soul catalog is empty");return List.copyOf(result);
    }
    private static BlockPos coordinate(JsonElement raw){
        var p=raw.getAsJsonArray();if(p.size()!=3)throw new IllegalArgumentException("Soul requires XYZ triplet");
        return SkillContracts.position(p.get(0).getAsString()+","+p.get(1).getAsString()+","+p.get(2).getAsString());
    }
    private static List<FairySoulRouteCompiler.Step> steps(JsonElement raw){
        var result=new ArrayList<FairySoulRouteCompiler.Step>();for(var value:raw.getAsJsonArray()){
            var row=value.getAsJsonObject();var kind=FairySoulRouteCompiler.Kind.valueOf(row.get("kind").getAsString());
            result.add(new FairySoulRouteCompiler.Step(kind,coordinate(row.get("target")),row.has("face")?dev.veltraph.client.world.Direction.valueOf(row.get("face").getAsString()):null));
        }return List.copyOf(result);
    }
    private FairySoulCatalog() {}
    public static List<Soul> parse(String text){
        return descriptors(text).stream().map(Descriptor::soul).toList();
    }
    public static Path file(Path directory,String profile){if(profile==null||!profile.matches("[a-zA-Z0-9_-]{1,64}"))throw new IllegalArgumentException("Configure a local profile identifier (letters/digits/underscore/hyphen)");return directory.resolve(profile+".json");}
    public static Set<Claim> load(Path path)throws IOException{
        if(!Files.exists(path))return Set.of();if(Files.size(path)>262144)throw new IOException("Claimed-soul file too large");
        try{var result=new HashSet<Claim>();for(var raw:JsonParser.parseString(Files.readString(path)).getAsJsonArray()){
            var row=raw.getAsJsonObject();var area=GameArea.valueOf(row.get("area").getAsString());var pos=SkillContracts.position(row.get("x")+","+row.get("y")+","+row.get("z"));
            result.add(new Claim(row.get("dimension").getAsString(),new Soul(area,pos)));if(result.size()>4096)throw new IOException("Claimed-soul limit exceeded");}return Set.copyOf(result);
        }catch(RuntimeException invalid){throw new IOException("Invalid claimed-soul JSON",invalid);}
    }
    public static void save(Path path,Set<Claim> claims)throws IOException{
        var rows=new JsonArray();claims.stream().sorted(Comparator.comparing(Claim::dimension).thenComparing(c->c.soul().area().name()).thenComparingInt(c->c.soul().position().x()).thenComparingInt(c->c.soul().position().y()).thenComparingInt(c->c.soul().position().z())).forEach(c->{
            var row=new JsonObject();row.addProperty("dimension",c.dimension());row.addProperty("area",c.soul().area().name());row.addProperty("x",c.soul().position().x());row.addProperty("y",c.soul().position().y());row.addProperty("z",c.soul().position().z());rows.add(row);});
        Files.createDirectories(path.getParent());var temporary=Files.createTempFile(path.getParent(),"claims-",".tmp");
        try{Files.writeString(temporary,new GsonBuilder().setPrettyPrinting().create().toJson(rows));try{Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temporary);}
    }
}
