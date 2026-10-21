package dev.veltraph.client.rewarp;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.Vec3;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Explicit normal-cycle START pose and END markers; independent of manual RETURN anchors. */
public record FarmCyclePlan(String profileId,FarmReturnPoint start,List<Vec3> ends) {
    public FarmCyclePlan {
        if(profileId==null||!profileId.matches("[a-z0-9][a-z0-9_-]{0,95}"))throw new IllegalArgumentException("Invalid farm profile key");
        Objects.requireNonNull(start,"start");Objects.requireNonNull(ends,"ends");
        if(start.area()!=GameArea.GARDEN||start.dimension().isBlank())throw new IllegalArgumentException("Cycle plans require a known Garden dimension");
        if(ends.size()>128)throw new IllegalArgumentException("Too many cycle end markers");
        for(Vec3 p:ends)if(p==null||!Double.isFinite(p.x())||!Double.isFinite(p.y())||!Double.isFinite(p.z()))throw new IllegalArgumentException("Invalid cycle end marker");
        ends=List.copyOf(ends);
    }
    /** Source nearest-3D selection followed by strict horizontal-radius and vertical1.1 tests. */
    public Optional<Vec3> matchingEnd(GameArea area,String dimension,Vec3 position,double radius) {
        if(area!=start.area()||!start.dimension().equals(dimension)||position==null||!Double.isFinite(radius)||radius<=0)return Optional.empty();
        var nearest=ends.stream().min(java.util.Comparator.comparingDouble(position::squaredDistanceTo));
        return nearest.filter(p->p.horizontalDistanceTo(position)<radius&&Math.abs(p.y()-position.y())<1.1);
    }
}
