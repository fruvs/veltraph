package dev.veltraph.client.galatea.trash;

import com.google.gson.JsonParser;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/** Source chooses squared-distance + random [0,5) then offsets both aim axes by [-10,10). */
public final class LushlilacTrashSpots {
    public record Spot(String dimension,Vec3 position,float yaw,float pitch) {
        public Spot {
            if(dimension==null||!dimension.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")||position==null
                    ||!Double.isFinite(position.x())||!Double.isFinite(position.y())||!Double.isFinite(position.z())
                    ||Math.abs(position.x())>30000000||Math.abs(position.z())>30000000||Math.abs(position.y())>2048
                    ||!Float.isFinite(yaw)||!Float.isFinite(pitch)||Math.abs(pitch)>90)
                throw new IllegalArgumentException("Trash spot requires finite scoped pose");
        }
    }
    private LushlilacTrashSpots() {}
    public static List<Spot> parse(String json) {
        try {
            var array=JsonParser.parseString(json).getAsJsonArray();if(array.isEmpty()||array.size()>32)throw new IllegalArgumentException("Configure 1..32 exact trash spots");
            var result=new ArrayList<Spot>();var unique=new HashSet<String>();
            for(var entry:array){var value=entry.getAsJsonObject();
                if(!value.keySet().equals(Set.of("dimension","x","y","z","yaw","pitch")))throw new IllegalArgumentException("Trash spot fields: dimension,x,y,z,yaw,pitch");
                var spot=new Spot(value.get("dimension").getAsString(),new Vec3(value.get("x").getAsDouble(),value.get("y").getAsDouble(),value.get("z").getAsDouble()),value.get("yaw").getAsFloat(),value.get("pitch").getAsFloat());
                if(Math.abs(spot.pitch())>80)throw new IllegalArgumentException("Configured pitch needs ten degrees of source aim headroom");
                if(!unique.add(spot.dimension()+spot.position()))throw new IllegalArgumentException("Duplicate trash spot");result.add(spot);
            }return List.copyOf(result);
        }catch(IllegalStateException|NullPointerException e){throw new IllegalArgumentException("Invalid required trash spot JSON",e);}
    }
    public static Optional<Spot> choose(List<Spot> spots,Vec3 player,Predicate<Spot> loaded,RandomGenerator random) {
        Spot best=null;double score=Double.POSITIVE_INFINITY;
        for(var spot:spots)if(loaded.test(spot)){double distance=player.distanceTo(spot.position());double candidate=distance*distance+random.nextDouble(5);
            if(candidate<score){score=candidate;best=spot;}}
        if(best==null)return Optional.empty();return Optional.of(new Spot(best.dimension(),best.position(),best.yaw()+(float)random.nextDouble(-10,10),best.pitch()+(float)random.nextDouble(-10,10)));
    }
}
