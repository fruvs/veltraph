package dev.veltraph.client.combat.slayer;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.*;

/** Correlates observed flying glyphs with newly appeared blocks; static beacons are never objectives. */
public final class GlyphTracker {
    private record Flight(Vec3 position,long seen) {}
    private final Map<Integer,Flight> flights=new HashMap<>();
    private final Map<Integer,Long> consumed=new HashMap<>();
    private final Map<BlockPos,Long> landed=new HashMap<>();
    private Set<BlockPos> previous=Set.of();
    private boolean initialized;
    public Set<BlockPos> observe(Map<Integer,Vec3> flying,Set<BlockPos> beacons,long now) {
        consumed.replaceAll((id,time)->flying.containsKey(id)?now:time);
        consumed.entrySet().removeIf(e->now-e.getValue()>7000);
        flying.forEach((id,p)->{if(!consumed.containsKey(id))flights.put(id,new Flight(p,now));});
        flights.entrySet().removeIf(e->now-e.getValue().seen()>1500);
        landed.entrySet().removeIf(e->!beacons.contains(e.getKey())||now-e.getValue()>7000);
        if(initialized) {
            var appeared=new ArrayList<>(beacons);appeared.removeAll(previous);
            appeared.sort(Comparator.comparingDouble(this::nearestFlight).thenComparingInt(BlockPos::x).thenComparingInt(BlockPos::y).thenComparingInt(BlockPos::z));
            for(BlockPos block:appeared) {
                Vec3 center=new Vec3(block.x()+.5,block.y()+.5,block.z()+.5);
                var match=flights.entrySet().stream().filter(e->e.getValue().position().distanceTo(center)<3)
                        .min(Comparator.comparingDouble(e->e.getValue().position().squaredDistanceTo(center)));
                if(match.isPresent()) {
                    int id=match.get().getKey();landed.put(block,now);flights.remove(id);consumed.put(id,now);
                }
            }
        }
        initialized=true;previous=Set.copyOf(beacons);return Set.copyOf(landed.keySet());
    }
    private double nearestFlight(BlockPos block) {
        Vec3 center=new Vec3(block.x()+.5,block.y()+.5,block.z()+.5);
        return flights.values().stream().mapToDouble(f->f.position().squaredDistanceTo(center)).min().orElse(Double.POSITIVE_INFINITY);
    }
    public boolean tracking(int entityId) { return flights.containsKey(entityId)||consumed.containsKey(entityId); }
    public void reset() { flights.clear();consumed.clear();landed.clear();previous=Set.of();initialized=false; }
}
