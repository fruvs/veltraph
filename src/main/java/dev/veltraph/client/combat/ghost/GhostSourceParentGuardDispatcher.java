package dev.veltraph.client.combat.ghost;

import java.util.Objects;

/** Typed ordinary guard transaction only; no default-unhandled port and no autonomous action/selection. */
public final class GhostSourceParentGuardDispatcher {
    public enum Role { AUTO_SELL, FARM_GARDEN_PAUSE, FARM_PEST_SPRAY, PESTS, VISITORS, MARKER_WORK, PEST_TRAPS, MOONGLADE, SCHEDULED_REST }
    public enum Result { CLAIMED, UNHANDLED, UNKNOWN }
    public interface OwnedPort {
        boolean current();
        Object parentIdentity();
        Object actualSearchChildIdentity();
        long parentGeneration();
        /** Actual handler/observed applicability result; missing mapping must return UNKNOWN. */
        Result dispatch(Role role);
    }
    private GhostSourceParentGuardDispatcher() {}
    public static Result beforeSearch(Object actualParent,Object actualChild,long generation,OwnedPort port){
        Objects.requireNonNull(port);
        if(actualParent==null||actualChild==null||!same(actualParent,actualChild,generation,port))return Result.UNKNOWN;
        for(var role:Role.values()){
            // Source Ghost constructor explicitly sets COMBAT+DWARVEN; these two FARMING+GARDEN branches cannot apply.
            if(role==Role.FARM_GARDEN_PAUSE||role==Role.FARM_PEST_SPRAY)continue;
            if(!same(actualParent,actualChild,generation,port))return Result.UNKNOWN;
            var result=port.dispatch(role);
            if(!same(actualParent,actualChild,generation,port))return Result.UNKNOWN;
            if(result==null||result==Result.UNKNOWN)return Result.UNKNOWN;
            if(result==Result.CLAIMED)return Result.CLAIMED;
        }
        return Result.UNHANDLED;
    }
    private static boolean same(Object parent,Object child,long generation,OwnedPort p){return p.current()&&p.parentIdentity()==parent
            &&p.actualSearchChildIdentity()==child&&p.parentGeneration()==generation;}
}
