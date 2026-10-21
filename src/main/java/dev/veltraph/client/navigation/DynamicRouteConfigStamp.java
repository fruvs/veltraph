package dev.veltraph.client.navigation;

import dev.veltraph.client.config.*;
import java.util.Objects;
import java.util.Optional;

/** Exact store identity, values and revisions; changed-and-reverted travel policy revokes the journey. */
public record DynamicRouteConfigStamp(ConfigService store,long rotationRevision,double rotation,
        long approachRevision,ApproachMode approach) {
    public static Optional<DynamicRouteConfigStamp> capture(ConfigService config){
        if(config==null)return Optional.empty();
        var rr=config.revision(RotationConfig.ROTATION_SPEED_MULTIPLIER);var ar=config.revision(TravelConfig.TRAVEL_APPROACH_MODE);
        if(rr.isEmpty()||ar.isEmpty())return Optional.empty();
        Double rotation=config.get(RotationConfig.ROTATION_SPEED_MULTIPLIER);var approach=config.get(TravelConfig.TRAVEL_APPROACH_MODE);
        if(rotation==null||!Double.isFinite(rotation)||approach==null)return Optional.empty();
        var value=new DynamicRouteConfigStamp(config,rr.getAsLong(),rotation,ar.getAsLong(),approach);
        return value.current(config)?Optional.of(value):Optional.empty();
    }
    public boolean current(ConfigService config){
        if(config!=store)return false;
        boolean storedEpochs=config instanceof MapConfigService||config instanceof JsonConfigService;
        if(storedEpochs){long rr=(config instanceof MapConfigService map?map.storedRevision(RotationConfig.ROTATION_SPEED_MULTIPLIER):((JsonConfigService)config).storedRevision(RotationConfig.ROTATION_SPEED_MULTIPLIER));long ar=(config instanceof MapConfigService map?map.storedRevision(TravelConfig.TRAVEL_APPROACH_MODE):((JsonConfigService)config).storedRevision(TravelConfig.TRAVEL_APPROACH_MODE));
            if(!(rr==rotationRevision&&ar==approachRevision))return false;
        }else{var rr=config.revision(RotationConfig.ROTATION_SPEED_MULTIPLIER);var ar=config.revision(TravelConfig.TRAVEL_APPROACH_MODE);
            if(!(rr.isPresent()&&ar.isPresent()&&rr.getAsLong()==rotationRevision&&ar.getAsLong()==approachRevision))return false;}
        // Only the final JDK Double receiver has a closed, pure canonical-bit equality.
        // Foreign/null values retain their original Objects.equals callback and fresh expected box.
        Object sampledObject=config.get(RotationConfig.ROTATION_SPEED_MULTIPLIER);
        boolean sameRotation;
        if(sampledObject instanceof Double sampledDouble)
            sameRotation=Double.doubleToLongBits(rotation)==Double.doubleToLongBits(sampledDouble.doubleValue());
        else sameRotation=Objects.equals(sampledObject,rotation);
        return sameRotation&&config.get(TravelConfig.TRAVEL_APPROACH_MODE)==approach
                &&(storedEpochs?(config instanceof MapConfigService map?map.storedRevision(RotationConfig.ROTATION_SPEED_MULTIPLIER):((JsonConfigService)config).storedRevision(RotationConfig.ROTATION_SPEED_MULTIPLIER)):config.revision(RotationConfig.ROTATION_SPEED_MULTIPLIER).orElse(-1))==rotationRevision
                &&(storedEpochs?(config instanceof MapConfigService map?map.storedRevision(TravelConfig.TRAVEL_APPROACH_MODE):((JsonConfigService)config).storedRevision(TravelConfig.TRAVEL_APPROACH_MODE)):config.revision(TravelConfig.TRAVEL_APPROACH_MODE).orElse(-1))==approachRevision;
    }
}
