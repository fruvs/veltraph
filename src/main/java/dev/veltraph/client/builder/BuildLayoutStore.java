package dev.veltraph.client.builder;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import dev.veltraph.client.world.BlockPos;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class BuildLayoutStore {
    private final Path root;
    public BuildLayoutStore(Path root) { this.root=root; }
    public BuildLayout load(String name) throws IOException {
        if(!name.matches("[a-zA-Z0-9_-]{1,64}")) throw new IOException("Invalid layout name");
        Path path=root.resolve(name+".json");
        if(Files.isSymbolicLink(path)||Files.size(path)>8_000_000) throw new IOException("Layout is a symlink or larger than 8 MB");
        return decode(Files.readString(path));
    }
    public static BuildLayout decode(String json) {
        if(json==null||json.length()>8_000_000) throw new IllegalArgumentException("Layout exceeds 8 MB");
        try {
            JsonReader reader=new JsonReader(new StringReader(json));reader.setStrictness(Strictness.STRICT);
            JsonObject obj=JsonParser.parseReader(reader).getAsJsonObject();
            if(reader.peek()!=com.google.gson.stream.JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing layout data");
            if(obj.get("version").getAsInt()!=1) throw new IllegalArgumentException("Unsupported layout version");
            List<BuildLayout.Entry> entries=new ArrayList<>();
            for(var e:obj.getAsJsonArray("entries")) {
                if(entries.size()>=50000) throw new IllegalArgumentException("Too many layout entries");
                var row=e.getAsJsonObject();var p=row.getAsJsonObject("pos");
                entries.add(new BuildLayout.Entry(BuildLayout.Operation.valueOf(row.get("type").getAsString().toUpperCase(Locale.ROOT)),
                        new BlockPos(integer(p,"x"),integer(p,"y"),integer(p,"z")),row.get("block").getAsString()));
            }
            return new BuildLayout(obj.get("name").getAsString(),entries);
        } catch(IOException|RuntimeException e) { throw new IllegalArgumentException("Invalid layout: "+e.getMessage(),e); }
    }
    private static int integer(JsonObject object,String key) { return object.get(key).getAsBigDecimal().intValueExact(); }
}
