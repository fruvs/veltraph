package dev.veltraph.client.garden.copy;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Bounded local schema. No original/native transform protocol, player UUID or block-entity payload. */
public final class GardenBlueprintStore {
    private final Path root;
    public GardenBlueprintStore(Path root){this.root=root.toAbsolutePath().normalize();}
    private Path path(String name)throws IOException{if(name==null||!name.matches("[A-Za-z0-9_-]{1,64}"))throw new IOException("Invalid blueprint name");if(Files.isSymbolicLink(root))throw new IOException("Blueprint directory is a symlink");var p=root.resolve(name+".json");if(Files.isSymbolicLink(p))throw new IOException("Blueprint is a symlink");return p;}
    public void save(GardenBlueprint b)throws IOException{var p=path(b.name());String json=encode(b);if(json.length()>16000000)throw new IOException("Blueprint exceeds16MB; nothing saved");Files.createDirectories(root);var tmp=Files.createTempFile(root,".garden-copy-",".tmp");try{Files.writeString(tmp,json);try{Files.move(tmp,p,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,p,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}}
    public GardenBlueprint load(String name)throws IOException{var p=path(name);if(Files.size(p)>16000000)throw new IOException("Blueprint exceeds16MB");var b=decode(Files.readString(p));if(!name.equals(b.name()))throw new IOException("Blueprint filename/name mismatch");return b;}
    public static String encode(GardenBlueprint b){var o=new JsonObject();o.addProperty("version",1);o.addProperty("name",b.name());var r=new JsonObject();r.addProperty("plot",b.source().plot());r.addProperty("minX",b.source().minX());r.addProperty("minY",b.source().minY());r.addProperty("minZ",b.source().minZ());r.addProperty("maxX",b.source().maxX());r.addProperty("maxY",b.source().maxY());r.addProperty("maxZ",b.source().maxZ());o.add("source",r);var palette=new ArrayList<String>();var indices=new HashMap<String,Integer>();var mask=new JsonArray();for(String state:b.states()){Integer i=indices.get(state);if(i==null){i=palette.size();palette.add(state);indices.put(state,i);}mask.add(i);}o.add("palette",new Gson().toJsonTree(palette));o.add("mask",mask);o.add("blockEntities",new Gson().toJsonTree(new TreeSet<>(b.blockEntities())));return o.toString();}
    private static int integer(JsonObject o,String key){return o.get(key).getAsBigDecimal().intValueExact();}
    public static GardenBlueprint decode(String json){
        if(json==null||json.length()>16000000)throw new IllegalArgumentException("Blueprint exceeds16MB");
        try{var reader=new JsonReader(new StringReader(json));reader.setStrictness(Strictness.STRICT);var o=JsonParser.parseReader(reader).getAsJsonObject();if(reader.peek()!=JsonToken.END_DOCUMENT||integer(o,"version")!=1)throw new IllegalArgumentException("Unsupported/trailing blueprint data");var r=o.getAsJsonObject("source");var region=new GardenRegion(integer(r,"plot"),integer(r,"minX"),integer(r,"minY"),integer(r,"minZ"),integer(r,"maxX"),integer(r,"maxY"),integer(r,"maxZ"));var palette=new ArrayList<String>();for(var p:o.getAsJsonArray("palette")){if(palette.size()>=4096)throw new IllegalArgumentException("Palette too large");palette.add(p.getAsString());}var states=new ArrayList<String>();for(var v:o.getAsJsonArray("mask")){if(states.size()>=region.cells())throw new IllegalArgumentException("Mask too large");states.add(palette.get(v.getAsBigDecimal().intValueExact()));}var entities=new HashSet<Integer>();for(var v:o.getAsJsonArray("blockEntities"))entities.add(v.getAsBigDecimal().intValueExact());return new GardenBlueprint(o.get("name").getAsString(),region,states,entities);}catch(IOException|RuntimeException invalid){throw new IllegalArgumentException("Invalid blueprint: "+invalid.getMessage(),invalid);}
    }
}
