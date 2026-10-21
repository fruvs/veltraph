package dev.veltraph.client.navigation;

import dev.veltraph.client.config.*;
import java.util.Objects;
import java.util.Optional;

/** Exact store identity, values and revisions; changed-and-reverted travel policy revokes the journey. */
public record FrozenAJDynamicRouteConfigStamp(ConfigService store,long rotationRevision,double rotation,
        long approachRevision,ApproachMode approach) {
    public static Optional<FrozenAJDynamicRouteConfigStamp> capture(ConfigService config){
        if(config==null)return Optional.empty();
        var rr=config.revision(RotationConfig.ROTATION_SPEED_MULTIPLIER);var ar=config.revision(TravelConfig.TRAVEL_APPROACH_MODE);
        if(rr.isEmpty()||ar.isEmpty())return Optional.empty();
        Double rotation=config.get(RotationConfig.ROTATION_SPEED_MULTIPLIER);var approach=config.get(TravelConfig.TRAVEL_APPROACH_MODE);
        if(rotation==null||!Double.isFinite(rotation)||approach==null)return Optional.empty();
        var value=new FrozenAJDynamicRouteConfigStamp(config,rr.getAsLong(),rotation,ar.getAsLong(),approach);
        return value.current(config)?Optional.of(value):Optional.empty();
    }
    public boolean current(ConfigService config){
        if(config!=store)return false;var rr=config.revision(RotationConfig.ROTATION_SPEED_MULTIPLIER);var ar=config.revision(TravelConfig.TRAVEL_APPROACH_MODE);
        return rr.isPresent()&&ar.isPresent()&&rr.getAsLong()==rotationRevision&&ar.getAsLong()==approachRevision
                &&Objects.equals(config.get(RotationConfig.ROTATION_SPEED_MULTIPLIER),rotation)
                &&config.get(TravelConfig.TRAVEL_APPROACH_MODE)==approach
                &&config.revision(RotationConfig.ROTATION_SPEED_MULTIPLIER).orElse(-1)==rotationRevision
                &&config.revision(TravelConfig.TRAVEL_APPROACH_MODE).orElse(-1)==approachRevision;
    }
}
