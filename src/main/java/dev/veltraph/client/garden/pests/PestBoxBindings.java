package dev.veltraph.client.garden.pests;
import java.util.*;

/** User-observed assigned grid/spawns and explicit bounds for unresolved source box timings. */
public record PestBoxBindings(List<List<PestBoxGeometry.Entry>> rows,List<PestBoxGeometry.Plot> plots,int delayTicks,long spawnWaitMs) {
    public PestBoxBindings {rows=rows.stream().map(row->List.copyOf(row)).toList();plots=List.copyOf(plots);
        if(rows.isEmpty()||rows.size()>5||rows.stream().anyMatch(r->r.size()>5)||delayTicks<2||delayTicks>30||spawnWaitMs<100||spawnWaitMs>30_000
            ||PestBoxGeometry.assigned(rows,plots).isEmpty())throw new IllegalArgumentException("Unique first-enabled assigned spawn and bounded explicit box delay/wait required");}
    public PestBoxGeometry.Plot selected(){return PestBoxGeometry.assigned(rows,plots).orElseThrow();}
}
