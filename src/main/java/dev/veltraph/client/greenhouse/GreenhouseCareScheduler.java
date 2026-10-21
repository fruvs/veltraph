package dev.veltraph.client.greenhouse;

import java.util.*;
import static dev.veltraph.client.greenhouse.GreenhouseModel.*;

/** Advisory tasks require refreshed plant state; offline elapsed time is never harvest permission. */
public final class GreenhouseCareScheduler {
    public enum Care { HARVEST, WATER, INSPECT, WAIT }
    public record Recommendation(Cell cell,Care care,String reason,OptionalLong serverDeadline) {}
    public List<Recommendation> evaluate(Snapshot snapshot,long now,long freshnessMs,OptionalLong serverNow){
        if(snapshot==null)return List.of(new Recommendation(null,Care.INSPECT,"No Greenhouse observations",OptionalLong.empty()));
        var result=new ArrayList<Recommendation>();
        for(var e:new TreeMap<>(snapshot.plants()).entrySet()){
            Plant p=e.getValue();Care care;String why;
            if(snapshot.knowledge(now,freshnessMs)!=Knowledge.KNOWN||!p.identified()){care=Care.INSPECT;why="Refresh unknown/stale plant diagnostics";}
            else if(p.state()==PlantState.DEAD){care=Care.WAIT;why="Observed DEAD donor is preserved; refresh its independent decay timer and species-group minimum counter; no watering, harvest or replant permission";}
            else if(p.state()==PlantState.HARVEST_READY){care=Care.HARVEST;why="Refreshed server observation marks ready";}
            else if(p.state()==PlantState.EMPTY){care=Care.WAIT;why="Empty soil is reserved for the selected combination";}
            else if(p.water()==WaterState.DRY){care=Care.WATER;why="Refreshed server observation marks dry; drought pauses growth/effects/spreading, and actual watering resources still require a receipt";}
            else if(expired(p.waterExpiry(),serverNow)){care=Care.INSPECT;why="Observed water deadline passed; refresh before watering";}
            else if(expired(p.decayExpiry(),serverNow)){care=Care.INSPECT;why="Observed decay deadline passed; refresh the species-group minimum counter and actual plant state";}
            else if(expired(p.nextGrowth(),serverNow)){care=Care.INSPECT;why="Observed growth deadline passed; refresh actual growth stage";}
            else if(p.water()==WaterState.UNKNOWN){care=Care.INSPECT;why="Care frequency unknown; inspect watering diagnostics";}
            else {care=Care.WAIT;why=p.nextGrowth().known()?"Observed growth clock retained; readiness needs refresh":"Growth interval unknown; no countdown inferred";}
            OptionalLong deadline=p.state()==PlantState.DEAD?OptionalLong.empty():java.util.stream.Stream.of(p.nextGrowth(),p.waterExpiry(),p.decayExpiry())
                .filter(c->c.known()&&c.kind()==ClockKind.SERVER_EPOCH).mapToLong(Clock::value).min();
            result.add(new Recommendation(e.getKey(),care,why,deadline));
        }
        if(result.isEmpty())result.add(new Recommendation(null,Care.INSPECT,"No plant cells observed; inspect guide and diagnostics",OptionalLong.empty()));
        return List.copyOf(result);
    }
    private boolean expired(Clock clock,OptionalLong serverNow){return clock.known()&&clock.kind()==ClockKind.SERVER_EPOCH&&serverNow.isPresent()&&serverNow.getAsLong()>=clock.value();}
}
