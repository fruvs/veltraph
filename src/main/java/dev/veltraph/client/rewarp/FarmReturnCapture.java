package dev.veltraph.client.rewarp;

import java.util.List;

/** Ephemeral interrupted macro pose. Persistent manual markers never participate. */
public final class FarmReturnCapture<T> {
    private Object healthyWorld,pinnedWorld;
    private FarmReturnPoint healthyPoint,pinnedPoint;
    private List<T> healthyOwners=List.of(),pinnedOwners=List.of();
    public void record(Object world,FarmReturnPoint point,List<T> owners) {
        if(world==null||point==null||owners.isEmpty()){clear();return;}
        healthyWorld=world;healthyPoint=point;healthyOwners=List.copyOf(owners);
    }
    public boolean pin(Object world,List<T> owners) {
        clearPending();
        if(healthyPoint==null||world!=healthyWorld||!healthyOwners.equals(owners)||owners.isEmpty())return false;
        pinnedWorld=world;pinnedPoint=healthyPoint;pinnedOwners=healthyOwners;return true;
    }
    public boolean matches(Object world,List<T> owners) {
        return pinnedPoint!=null&&world==pinnedWorld&&pinnedOwners.equals(owners);
    }
    public FarmReturnPoint point(){return pinnedPoint;}
    public List<T> owners(){return pinnedOwners;}
    public void clearPending(){pinnedWorld=null;pinnedPoint=null;pinnedOwners=List.of();}
    public void clear(){clearPending();healthyWorld=null;healthyPoint=null;healthyOwners=List.of();}
}
