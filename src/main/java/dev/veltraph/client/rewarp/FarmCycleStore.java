package dev.veltraph.client.rewarp;

import com.google.gson.*;
import com.google.gson.stream.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.Vec3;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Separate, local, versioned normal-cycle plans. Old return markers are never reinterpreted. */
public final class FarmCycleStore {
    private final Path path;
    private final Map<String,FarmCyclePlan> plans=new LinkedHashMap<>();
    private boolean unreadable;
    public FarmCycleStore(Path path){this.path=Objects.requireNonNull(path);}
    public Optional<FarmCyclePlan> get(String profile){return Optional.ofNullable(plans.get(profile));}
    public List<FarmCyclePlan> list(){return List.copyOf(plans.values());}
    private void mutable(){if(unreadable)throw new IllegalStateException("Repair the unreadable cycle plan file before editing");}
    public void updateAndSave(Runnable edit)throws IOException{
        var before=new LinkedHashMap<>(plans);
        try{edit.run();save();}catch(IOException|RuntimeException failure){plans.clear();plans.putAll(before);throw failure;}
    }
    public void setStart(String profile,FarmReturnPoint pose){
        mutable();
        var old=plans.get(profile);
        if(old==null&&plans.size()>=64)throw new IllegalArgumentException("Cycle plan count limit");
        boolean same=old!=null&&old.start().area()==pose.area()&&old.start().dimension().equals(pose.dimension());
        plans.put(profile,new FarmCyclePlan(profile,pose,same?old.ends():List.of()));
    }
    public void addEnd(String profile,GameArea area,String dimension,Vec3 position){
        mutable();
        var plan=plans.get(profile);
        if(plan==null)throw new IllegalStateException("Capture the cycle start first");
        if(plan.start().area()!=area||!plan.start().dimension().equals(dimension))throw new IllegalArgumentException("End marker is outside the cycle start area/dimension");
        var ends=new ArrayList<>(plan.ends());ends.add(position);
        plans.put(profile,new FarmCyclePlan(profile,plan.start(),ends));
    }
    public void clear(String profile){mutable();plans.remove(profile);}
    public void load() throws IOException {
        if(!Files.exists(path)){plans.clear();unreadable=false;return;}
        try {
            if(Files.isSymbolicLink(path)||Files.size(path)>262_144)throw new IOException("Invalid cycle plan file");
            var decoded=decode(Files.readString(path));plans.clear();for(var plan:decoded)plans.put(plan.profileId(),plan);unreadable=false;
        } catch(IOException|RuntimeException e){plans.clear();unreadable=true;throw new IOException("Cannot read cycle plans; original file preserved",e);}
    }
    public void save() throws IOException {
        if(unreadable)throw new IOException("Refusing to overwrite unreadable cycle plan file");
        if(Files.isSymbolicLink(path))throw new IOException("Cycle plan path is a symlink");
        Path parent=path.toAbsolutePath().getParent();Files.createDirectories(parent);
        Path tmp=Files.createTempFile(parent,"farm-cycles-",".tmp");
        try {
            String data=encode(list());if(data.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>262_144)throw new IOException("Cycle plan file limit");
            Files.writeString(tmp,data);
            try {Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(tmp);}
    }
    public static String encode(List<FarmCyclePlan> plans){
        if(plans.size()>64)throw new IllegalArgumentException("Cycle plan count limit");
        Set<String> ids=new HashSet<>();
        JsonObject root=new JsonObject();root.addProperty("version",1);JsonArray all=new JsonArray();
        for(var plan:plans){if(!ids.add(plan.profileId()))throw new IllegalArgumentException("Duplicate profile plan");
            JsonObject p=new JsonObject();p.addProperty("profile",plan.profileId());
            p.addProperty("dimension",plan.start().dimension());p.addProperty("area",plan.start().area().name());
            JsonObject start=position(plan.start().position());start.addProperty("yaw",plan.start().yaw());start.addProperty("pitch",plan.start().pitch());p.add("start",start);
            JsonArray ends=new JsonArray();plan.ends().forEach(end->ends.add(position(end)));p.add("ends",ends);all.add(p);}
        root.add("plans",all);return new GsonBuilder().setPrettyPrinting().create().toJson(root)+"\n";
    }
    public static List<FarmCyclePlan> decode(String text){
        if(text==null||text.length()>262_144)throw new IllegalArgumentException("Cycle plan file limit");
        try {
            JsonReader r=new JsonReader(new StringReader(text));r.setStrictness(Strictness.STRICT);
            JsonObject root=readValue(r,0).getAsJsonObject();
            var version=root.get("version");
            if(r.peek()!=JsonToken.END_DOCUMENT||version==null||!version.isJsonPrimitive()
                    ||!version.getAsJsonPrimitive().isNumber()||version.getAsBigDecimal().intValueExact()!=1)
                throw new IllegalArgumentException("Unsupported cycle plan version/trailing data");
            var result=new ArrayList<FarmCyclePlan>();Set<String> ids=new HashSet<>();
            for(var element:root.getAsJsonArray("plans")) {
                if(result.size()>=64)throw new IllegalArgumentException("Cycle plan count limit");
                var p=element.getAsJsonObject();String id=string(p,"profile");if(!ids.add(id))throw new IllegalArgumentException("Duplicate profile plan");
                var s=p.getAsJsonObject("start");var pose=new FarmReturnPoint(string(p,"dimension"),GameArea.valueOf(string(p,"area")),vector(s),(float)number(s,"yaw"),(float)number(s,"pitch"));
                List<Vec3> ends=new ArrayList<>();for(var e:p.getAsJsonArray("ends")){if(ends.size()>=128)throw new IllegalArgumentException("End marker limit");ends.add(vector(e.getAsJsonObject()));}
                result.add(new FarmCyclePlan(id,pose,ends));
            }
            return List.copyOf(result);
        } catch(IOException|RuntimeException e){throw new IllegalArgumentException("Invalid cycle plans",e);}
    }
    private static JsonObject position(Vec3 v){var p=new JsonObject();p.addProperty("x",v.x());p.addProperty("y",v.y());p.addProperty("z",v.z());return p;}
    // Gson's tree parser accepts duplicate fields. Reject ambiguity before constructing an armed plan.
    private static JsonElement readValue(JsonReader reader,int depth)throws IOException {
        if(depth>8)throw new IllegalArgumentException("Cycle plan nesting limit");
        return switch(reader.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject result=new JsonObject();reader.beginObject();
                while(reader.hasNext()) {
                    String key=reader.nextName();
                    if(result.has(key))throw new IllegalArgumentException("Duplicate cycle plan field: "+key);
                    result.add(key,readValue(reader,depth+1));
                }
                reader.endObject();yield result;
            }
            case BEGIN_ARRAY -> {
                JsonArray result=new JsonArray();reader.beginArray();
                while(reader.hasNext())result.add(readValue(reader,depth+1));
                reader.endArray();yield result;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new java.math.BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {reader.nextNull();yield JsonNull.INSTANCE;}
            default -> throw new IllegalArgumentException("Invalid cycle plan JSON value");
        };
    }
    private static String string(JsonObject p,String key){var value=p.get(key);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected string "+key);return value.getAsString();}
    private static Vec3 vector(JsonObject p){return new Vec3(number(p,"x"),number(p,"y"),number(p,"z"));}
    private static double number(JsonObject p,String key){var value=p.get(key);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected numeric "+key);double d=value.getAsDouble();if(!Double.isFinite(d))throw new IllegalArgumentException("Invalid "+key);return d;}
}
