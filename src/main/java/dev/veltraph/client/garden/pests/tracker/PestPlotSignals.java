package dev.veltraph.client.garden.pests.tracker;

import dev.veltraph.client.garden.pests.*;
import dev.veltraph.client.gui.MenuSnapshot;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import java.util.regex.Pattern;

/** Stable source tab integer plot IDs and native global count; no unverified desk survey adaptation. */
public final class PestPlotSignals {
    private PestPlotSignals(){}
    public record Census(List<PestBoxGeometry.Plot> plots,int total){public Census {plots=List.copyOf(plots);}
        public Optional<PestBoxGeometry.Plot> next(Vec3 position){return plots.stream().min(Comparator.comparingDouble(p->{var b=GardenPlotBounds.ALL.stream().filter(x->x.id()==p.plotId()).findFirst().orElseThrow();return new Vec3((b.minX()+b.maxX())/2.0,92,(b.minZ()+b.maxZ())/2.0).squaredDistanceTo(position);}));}}
    public static Optional<Census> parse(PestTrackedBindings.Signals bindings,List<String> tab,List<String> sidebar,int maximum){
        var cleanTab=tab.stream().map(MenuSnapshot::clean).toList();var total=PestBindings.count((bindings.counterSource()==PestTrackedBindings.CounterSource.TAB?cleanTab:sidebar.stream().map(MenuSnapshot::clean).toList()),bindings.totalPrefix());
        if(total.isEmpty()||total.getAsInt()>maximum)return Optional.empty();if(total.getAsInt()==0)return Optional.of(new Census(List.of(),0));
        var lines=cleanTab.stream().filter(s->s.startsWith(bindings.plotsPrefix())).toList();if(lines.size()!=1)return Optional.empty();String body=lines.getFirst().substring(bindings.plotsPrefix().length());if(body.isBlank())return Optional.empty();
        var ids=new LinkedHashSet<Integer>();for(String token:body.split(Pattern.quote(bindings.separator()),-1)){if(!token.matches("[0-9]{1,2}"))return Optional.empty();int id=Integer.parseInt(token);if(id<1||id>24||!ids.add(id))return Optional.empty();}
        if(ids.size()>total.getAsInt())return Optional.empty();var plots=new ArrayList<PestBoxGeometry.Plot>();for(int id:ids){var matches=bindings.plots().stream().filter(p->p.plotId()==id).toList();if(matches.size()!=1)return Optional.empty();plots.add(matches.getFirst());}
        return Optional.of(new Census(plots,total.getAsInt()));
    }
}
