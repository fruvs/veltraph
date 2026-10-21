package dev.veltraph.client.greenhouse;

import java.util.*;
import static dev.veltraph.client.greenhouse.GreenhouseCareScheduler.*;
import static dev.veltraph.client.greenhouse.GreenhouseLifecyclePolicy.*;

/** Combines existing inspection estimates and current condition dependencies; never starts care. */
public final class GreenhouseCareSchedule {
    private GreenhouseCareSchedule() {}
    public static Recommendation associated(GreenhouseModel.Cell selected,GreenhouseInspectionEstimates.View view,
            Advice lifecycle,long now) {
        if(selected==null||view==null||lifecycle==null||view.scope().target().isEmpty()||!selected.equals(view.scope().target().get()))
            return new Recommendation(selected,Care.INSPECT,"Refresh the selected physical plant inspection",OptionalLong.empty());
        // Missing current drought/group/charge dependencies require inspection even if an older timer is pending.
        boolean needsInspection=lifecycle.needs().contains(Need.INSPECT)||lifecycle.needs().contains(Need.WATERING_INSPECTION)
            ||lifecycle.needs().contains(Need.DISCHARGE_INSPECTION);
        boolean pending=view.state()==GreenhouseInspectionEstimates.State.FRESH&&view.earliestInspectionAtLocalMillis().isPresent()
            &&view.earliestInspectionAtLocalMillis().getAsLong()>now;
        String reason=view.summary()+" | current lifecycle: "+lifecycle.reason();
        return new Recommendation(selected,pending&&!needsInspection?Care.WAIT:Care.INSPECT,reason,OptionalLong.empty());
    }
}
