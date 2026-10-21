package dev.veltraph.client.garden.admission;

import dev.veltraph.client.config.*;
import java.util.*;

/** Store identity plus per-key revisions detect policy revocation and change-and-revert. */
public record GardenAdmissionPolicy(ConfigService store,List<Long> revisions,List<Object> values) {
    private static final List<ConfigKey<?>> KEYS=List.of(GardenAdmissionConfig.AUTOSTART_VISITOR_DURING_FARMING,
        GardenAdmissionConfig.AUTOSTART_PESTS_DURING_FARMING,GardenAdmissionConfig.ACTIVATE_VISITOR_THRESHOLD,
        GardenAdmissionConfig.ACTIVATE_PEST_THRESHOLD,GardenAdmissionConfig.ACTIVATE_VISITOR_INSTANTLY,
        GardenAdmissionConfig.ACTIVATE_PESTS_INSTANTLY,GardenAdmissionConfig.DO_VISITORS_IN_CONTEST_2,
        GardenAdmissionConfig.DO_PESTS_IN_CONTEST_2,GardenAdmissionConfig.GARDEN_ADMISSION_BINDINGS);
    public GardenAdmissionPolicy { revisions=List.copyOf(revisions);values=List.copyOf(values); }
    public static Optional<GardenAdmissionPolicy> capture(ConfigService store){
        if(store==null)return Optional.empty();var revisions=new ArrayList<Long>();var values=new ArrayList<Object>();
        for(var key:KEYS){var revision=store.revision(key);if(revision.isEmpty())return Optional.empty();revisions.add(revision.getAsLong());values.add(store.get(key));}
        var result=new GardenAdmissionPolicy(store,revisions,values);
        return result.current(store)?Optional.of(result):Optional.empty();
    }
    public boolean current(ConfigService current){
        if(current!=store)return false;
        for(int i=0;i<KEYS.size();i++){var key=KEYS.get(i);var revision=current.revision(key);if(revision.isEmpty()||revision.getAsLong()!=revisions.get(i)||!Objects.equals(current.get(key),values.get(i)))return false;}
        return true;
    }
}
