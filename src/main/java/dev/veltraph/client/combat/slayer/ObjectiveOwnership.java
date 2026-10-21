package dev.veltraph.client.combat.slayer;

import dev.veltraph.client.world.Vec3;
import java.util.*;

/** Sticky, fail-closed spatial association. A nearest boss is never ownership proof. */
public final class ObjectiveOwnership {
    public record Boss(UUID identity,SlayerSignals.Family family,Vec3 position) {}
    private final Map<UUID,UUID> owners=new HashMap<>();
    private final Set<UUID> rejected=new HashSet<>();
    private boolean saturated;
    public Optional<UUID> associate(UUID objective,SlayerSignals.Family family,Vec3 point,double radius,List<Boss> bosses) {
        return associateWithMargin(objective,family,point,radius,0,bosses);
    }
    public Optional<UUID> associateWithMargin(UUID objective,SlayerSignals.Family family,Vec3 point,double radius,double uncertainty,List<Boss> bosses) {
        if(owners.containsKey(objective))return Optional.of(owners.get(objective));
        if(rejected.contains(objective))return Optional.empty();
        if(saturated||owners.size()+rejected.size()>=512) {saturated=true;return Optional.empty();}
        var candidates=bosses.stream().filter(b->b.family()==family&&b.position().distanceTo(point)<=radius+uncertainty).toList();
        if(candidates.size()!=1||candidates.getFirst().position().distanceTo(point)>radius) {rejected.add(objective);return Optional.empty();}
        UUID owner=candidates.getFirst().identity();owners.put(objective,owner);return Optional.of(owner);
    }
    public Optional<UUID> owner(UUID objective) {return Optional.ofNullable(owners.get(objective));}
    public void reject(UUID objective) {if(!owners.containsKey(objective)&&owners.size()+rejected.size()<512)rejected.add(objective);}
    public void retain(Set<UUID> visible) {owners.keySet().retainAll(visible);rejected.retainAll(visible);}
    public void reset() {owners.clear();rejected.clear();saturated=false;}
}
