package dev.veltraph.client.garden.pests;

import dev.veltraph.client.rewarp.AssignedPlotTravelPort;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import com.google.gson.*;

/** Source-opaque labels and assigned landings are supplied explicitly, never inferred. */
public record PestSprayonatorBindings(PestBindings.Item tool,PestBindings.Item material,
        String selectionPrefix,String selectedLore,String menuTitle,String availableLore,String selectedMenuLore,
        boolean sneakToOpen,Escape escape,List<Plot> plots) {
    /** Source radius and candidate cube are readable; opaque candidate ranking is explicitly bound. */
    public record Escape(PestBindings.Entity trap,Vec3 landing,double arrivalRadius) {
        public Escape {Objects.requireNonNull(trap);if(landing==null||!Double.isFinite(landing.x())||!Double.isFinite(landing.y())||!Double.isFinite(landing.z())||landing.y()<66
                ||!Double.isFinite(arrivalRadius)||arrivalRadius<=0||arrivalRadius>1)throw new IllegalArgumentException("Exact verified source escape landing and bounded arrival radius required");}
        public boolean sourceCandidate(Vec3 origin){return origin!=null&&Math.abs(landing.x()-origin.x())<=10&&Math.abs(landing.y()-origin.y())<=10&&Math.abs(landing.z()-origin.z())<=10;}
        public boolean outsideTrap(Vec3 point,Vec3 trapPosition){return point!=null&&trapPosition!=null&&point.distanceTo(trapPosition)>=7.5;}
    }
    public record Plot(int id,boolean enabled,boolean excluded,Vec3 spawn,float yaw,float pitch,String successAck) {
        public Plot {
            if(id<1||id>24||spawn==null||!Double.isFinite(spawn.x())||!Double.isFinite(spawn.y())||!Double.isFinite(spawn.z())
                    ||!GardenPlotBounds.contains(id,spawn.x(),spawn.z())||!GardenPlotBounds.sourceSprayContains(id,spawn.x(),spawn.y(),spawn.z())
                    ||!Float.isFinite(yaw)||!Float.isFinite(pitch)||Math.abs(pitch)>90)throw new IllegalArgumentException("Exact loaded selected-plot landing required");
            literal(successAck);
            if(!successAck.startsWith("SPRAYONATOR! You sprayed "))throw new IllegalArgumentException("Observed source spray success line required");
        }
        public AssignedPlotTravelPort.Target target(){return new AssignedPlotTravelPort.Target(id,spawn,yaw,pitch);}
    }
    public PestSprayonatorBindings {
        Objects.requireNonNull(tool);Objects.requireNonNull(material);Objects.requireNonNull(escape);
        literal(selectionPrefix);literal(selectedLore);literal(menuTitle);literal(availableLore);literal(selectedMenuLore);
        if(tool.equals(material)||!selectedLore.startsWith(selectionPrefix)
                ||!selectedLore.substring(selectionPrefix.length()).strip().equals(material.name())||availableLore.equals(selectedMenuLore))
            throw new IllegalArgumentException("Exact Sprayonator material and distinct selection lore required");
        plots=List.copyOf(Objects.requireNonNull(plots));
        if(plots.isEmpty()||plots.size()>24||plots.stream().map(Plot::id).distinct().count()!=plots.size()
                ||plots.stream().map(Plot::successAck).distinct().count()!=plots.size()||plots.stream().noneMatch(p->p.enabled()&&!p.excluded()))
            throw new IllegalArgumentException("Unique explicit source-enabled, non-excluded plots required");
    }
    public List<Plot> selected(){return plots.stream().filter(p->p.enabled()&&!p.excluded()).toList();}
    /** Missing gesture/filter values cannot silently default to an invented source contract. */
    public static PestSprayonatorBindings read(String raw){
        if(raw==null||raw.isBlank())throw new IllegalArgumentException("Configure exact selected-plot spray bindings");
        var json=JsonParser.parseString(raw).getAsJsonObject();
        if(!json.keySet().equals(Set.of("tool","material","selectionPrefix","selectedLore","menuTitle","availableLore","selectedMenuLore","sneakToOpen","escape","plots")))
            throw new IllegalArgumentException("Exact spray binding fields required");
        if(!json.get("sneakToOpen").isJsonPrimitive()||!json.get("sneakToOpen").getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Explicit opening gesture required");
        for(String name:List.of("tool","material"))if(!json.getAsJsonObject(name).keySet().equals(Set.of("registryId","name","skyblockId")))throw new IllegalArgumentException("Exact tool and material item identity fields required");
        var escape=json.getAsJsonObject("escape");if(!escape.keySet().equals(Set.of("trap","landing","arrivalRadius"))||!escape.getAsJsonObject("trap").keySet().equals(Set.of("registryId","name","helmetIdentity"))
                ||!escape.getAsJsonObject("landing").keySet().equals(Set.of("x","y","z")))throw new IllegalArgumentException("Explicit trap identity and verified escape landing contract required");
        for(var element:json.getAsJsonArray("plots")){
            var plot=element.getAsJsonObject();if(!plot.keySet().equals(Set.of("id","enabled","excluded","spawn","yaw","pitch","successAck")))throw new IllegalArgumentException("Explicit selected/excluded plot and landing fields required");
            for(String name:List.of("enabled","excluded"))if(!plot.get(name).isJsonPrimitive()||!plot.get(name).getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Explicit source plot filter required");
            if(!plot.getAsJsonObject("spawn").keySet().equals(Set.of("x","y","z")))throw new IllegalArgumentException("Explicit native landing coordinates required");
        }
        return new Gson().fromJson(json,PestSprayonatorBindings.class);
    }
    private static void literal(String s){if(s==null||s.isBlank()||s.indexOf('\n')>=0||s.indexOf('\r')>=0)throw new IllegalArgumentException("Exact observed single-line spray binding required");}
}
