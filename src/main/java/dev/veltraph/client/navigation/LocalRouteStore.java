package dev.veltraph.client.navigation;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.Vec3;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Strict, bounded local JSON. No code, network, or arbitrary path traversal in route files. */
public final class LocalRouteStore {
    private final Path root;
    public LocalRouteStore(Path root) { this.root=root.toAbsolutePath().normalize(); }
    public Path path(String name) {
        if(name==null||!name.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException("Use a route name containing only letters, numbers, - or _");
        return root.resolve(name+".json");
    }
    public LocalRoute load(String name) throws IOException {
        Path path=path(name);
        if(Files.isSymbolicLink(path)||Files.size(path)>2_000_000) throw new IOException("Route is a symlink or exceeds 2 MB");
        return decode(Files.readString(path));
    }
    public void save(String name,LocalRoute route) throws IOException {
        Files.createDirectories(root);Path dest=path(name);
        Path temp=Files.createTempFile(root,"route-",".tmp");
        try {
            Files.writeString(temp,encode(route));
            try { Files.move(temp,dest,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch(AtomicMoveNotSupportedException e) { Files.move(temp,dest,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    public static LocalRoute decode(String json) {
        if(json==null||json.length()>2_000_000) throw new IllegalArgumentException("Route exceeds 2 MB");
        try {
            JsonReader reader=new JsonReader(new StringReader(json)); reader.setStrictness(Strictness.STRICT);
            JsonObject obj=JsonParser.parseReader(reader).getAsJsonObject();
            if(reader.peek()!=com.google.gson.stream.JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing route data");
            int version=obj.get("version").getAsBigDecimal().intValueExact();
            if(version!=1 && version!=2) throw new IllegalArgumentException("Unsupported route version");
            List<LocalRoute.Node> points=new ArrayList<>();
            for(JsonElement e:obj.getAsJsonArray("nodes")) {
                if(points.size()>=4096) throw new IllegalArgumentException("Too many checkpoints");
                JsonObject node=e.getAsJsonObject();
                LocalRoute.Kind kind=LocalRoute.Kind.valueOf(node.get("type").getAsString().toUpperCase(Locale.ROOT));
                if(version==1 && kind!=LocalRoute.Kind.WALK) throw new IllegalArgumentException("Version 1 supports walk checkpoints only");
                dev.veltraph.client.world.BlockPos target=null;dev.veltraph.client.world.Direction face=null;
                if(kind==LocalRoute.Kind.ETHERWARP) {
                    JsonObject block=node.getAsJsonObject("target");
                    target=new dev.veltraph.client.world.BlockPos(block.get("x").getAsBigDecimal().intValueExact(),block.get("y").getAsBigDecimal().intValueExact(),block.get("z").getAsBigDecimal().intValueExact());
                    face=dev.veltraph.client.world.Direction.valueOf(node.get("face").getAsString().toUpperCase(Locale.ROOT));
                }
                points.add(new LocalRoute.Node(kind,new Vec3(node.get("x").getAsDouble(),node.get("y").getAsDouble(),node.get("z").getAsDouble()),
                        node.get("yaw").getAsFloat(),node.get("pitch").getAsFloat(),target,face));
            }
            return new LocalRoute(GameArea.valueOf(obj.get("area").getAsString()),obj.get("dimension").getAsString(),obj.get("loop").getAsBoolean(),points);
        } catch(IOException|RuntimeException e) { throw new IllegalArgumentException("Invalid route: "+e.getMessage(),e); }
    }
    public static String encode(LocalRoute route) {
        JsonObject obj=new JsonObject(); obj.addProperty("version",route.nodes().stream().allMatch(n->n.kind()==LocalRoute.Kind.WALK)?1:2);obj.addProperty("area",route.area().name());
        obj.addProperty("dimension",route.dimension());obj.addProperty("loop",route.loop());
        JsonArray nodes=new JsonArray();
        for(var n:route.nodes()) {
            JsonObject point=new JsonObject();point.addProperty("type",n.kind().name().toLowerCase(Locale.ROOT));
            if(n.kind()==LocalRoute.Kind.ETHERWARP) {
                JsonObject target=new JsonObject();target.addProperty("x",n.target().x());target.addProperty("y",n.target().y());target.addProperty("z",n.target().z());
                point.add("target",target);point.addProperty("face",n.face().name());
            }
            point.addProperty("x",n.position().x());point.addProperty("y",n.position().y());point.addProperty("z",n.position().z());
            point.addProperty("yaw",n.yaw());point.addProperty("pitch",n.pitch());nodes.add(point);
        }
        obj.add("nodes",nodes);return new GsonBuilder().setPrettyPrinting().create().toJson(obj)+"\n";
    }
}
