package dev.veltraph.client.greenhouse;

import com.google.gson.Gson;
import dev.veltraph.client.gui.MenuSnapshot;
import java.util.*;
import static dev.veltraph.client.greenhouse.GreenhouseModel.*;

/** Explicit locally captured menu profile. No built-in server title, crop wire ID, or timer grammar. */
public record GreenhouseBindings(String menuTitle,String profileId,String plotId,String profileLine,String plotLine,String diagnosticToolIdentity,
                                int diagnosticToolSlot,List<CellBinding> cells) {
    public record CellBinding(Cell cell,String slotName,String itemId,String cellLine,String cropPrefix,
                              String emptyLine,String growingLine,String readyLine,String dryLine,String wateredLine,
                              String stagePrefix,String waterEpochPrefix,String growthCyclePrefix,String decayEpochPrefix,Cell diagnosticTargetCell,String deadLine) {
        /** Existing profiles remain death-unbound; no default native status marker is invented. */
        public CellBinding(Cell cell,String slotName,String itemId,String cellLine,String cropPrefix,
                String emptyLine,String growingLine,String readyLine,String dryLine,String wateredLine,
                String stagePrefix,String waterEpochPrefix,String growthCyclePrefix,String decayEpochPrefix,Cell diagnosticTargetCell){
            this(cell,slotName,itemId,cellLine,cropPrefix,emptyLine,growingLine,readyLine,dryLine,wateredLine,
                stagePrefix,waterEpochPrefix,growthCyclePrefix,decayEpochPrefix,diagnosticTargetCell,null);
        }
    }
    public GreenhouseBindings { cells=List.copyOf(cells); }
    public static GreenhouseBindings parse(String json){
        if(json==null||json.isBlank())throw new IllegalArgumentException("No observed Greenhouse Diagnostic Tool/menu bindings");
        var b=new Gson().fromJson(json,GreenhouseBindings.class);
        if(b==null||blank(b.menuTitle)||blank(b.profileId)||blank(b.plotId)||blank(b.profileLine)||blank(b.plotLine)||blank(b.diagnosticToolIdentity)||b.diagnosticToolSlot<0||b.diagnosticToolSlot>8||b.cells.isEmpty()||b.cells.size()>100)throw new IllegalArgumentException("Invalid scoped Diagnostic Tool bindings");
        Set<Cell> unique=new HashSet<>();
        for(var c:b.cells)if(c==null||c.cell==null||!unique.add(c.cell)||blank(c.slotName)||blank(c.itemId)||blank(c.cellLine)||blank(c.cropPrefix)||blank(c.emptyLine)||blank(c.growingLine)||blank(c.readyLine)||blank(c.dryLine)||blank(c.wateredLine))throw new IllegalArgumentException("Exact unique diagnostic cell markers required");
        for(var c:b.cells)if(!blank(c.deadLine)&&(blank(c.stagePrefix)||Set.of(c.emptyLine,c.growingLine,c.readyLine).contains(c.deadLine)))
            throw new IllegalArgumentException("Distinct exact observed DEAD status and stage grammar required");
        return b;
    }
    public Optional<Map<Cell,Plant>> read(MenuSnapshot menu,Map<Cell,String> worldStates){
        if(menu==null||!menu.title().equals(menuTitle))return Optional.empty();
        List<String> lore=menu.slots().stream().filter(s->!s.playerInventory()&&!s.locked()&&s.count()>0).flatMap(s->s.lore().stream()).toList();
        if(!lore.contains(profileLine)||!lore.contains(plotLine))return Optional.empty();
        var result=new HashMap<Cell,Plant>();
        for(var c:cells){
            var slots=menu.slots().stream().filter(s->!s.playerInventory()&&!s.locked()&&s.count()>0&&s.name().equals(c.slotName)&&s.itemId().equals(c.itemId)&&s.lore().contains(c.cellLine)).toList();
            if(slots.size()!=1||!worldStates.containsKey(c.cell))return Optional.empty();var lines=slots.getFirst().lore();
            var states=new ArrayList<PlantState>();if(lines.contains(c.emptyLine))states.add(PlantState.EMPTY);if(lines.contains(c.growingLine))states.add(PlantState.GROWING);if(lines.contains(c.readyLine))states.add(PlantState.HARVEST_READY);
            var stageValues=blank(c.stagePrefix)?List.<String>of():lines.stream().filter(l->l.startsWith(c.stagePrefix)).map(l->l.substring(c.stagePrefix.length()).trim()).toList();
            if(stageValues.size()>1)return Optional.empty();
            boolean dead=!blank(c.deadLine)&&lines.contains(c.deadLine);
            if(dead){
                if(Collections.frequency(lines,c.deadLine)!=1||!uniqueText(lines,c.stagePrefix).filter("DEAD"::equals).isPresent())return Optional.empty();
                states.add(PlantState.DEAD);
            }
            if(states.size()!=1)return Optional.empty();PlantState state=states.getFirst();
            // A literal death stage is not maturity, a numeric stage or missing-stage fallback.
            if(state!=PlantState.DEAD&&stageValues.stream().anyMatch("DEAD"::equals))return Optional.empty();
            boolean dry=lines.contains(c.dryLine),watered=lines.contains(c.wateredLine);if(dry&&watered)return Optional.empty();
            WaterState water=dry?WaterState.DRY:watered?WaterState.WATERED:WaterState.UNKNOWN;
            String crop=state==PlantState.EMPTY?"":uniqueText(lines,c.cropPrefix).orElse("");if(state!=PlantState.EMPTY&&crop.isBlank())return Optional.empty();
            long stage=state==PlantState.DEAD?-1:number(lines,c.stagePrefix).orElse(-1);if(stage>Integer.MAX_VALUE)return Optional.empty();
            result.put(c.cell,new Plant(crop,state,water,(int)stage,clock(lines,c.growthCyclePrefix,ClockKind.GROWTH_CYCLE),
                clock(lines,c.waterEpochPrefix,ClockKind.SERVER_EPOCH),clock(lines,c.decayEpochPrefix,ClockKind.SERVER_EPOCH),worldStates.get(c.cell),"Observed menu: "+menuTitle+" / "+c.cellLine));
        }
        return Optional.of(Map.copyOf(result));
    }
    private static Clock clock(List<String> lines,String prefix,ClockKind kind){var n=number(lines,prefix);return n.isPresent()?new Clock(kind,n.getAsLong(),"Observed diagnostic: "+prefix):Clock.unknown();}
    private static OptionalLong number(List<String> lines,String prefix){var text=uniqueText(lines,prefix);if(text.isEmpty())return OptionalLong.empty();try{long n=Long.parseLong(text.get());return n>=0?OptionalLong.of(n):OptionalLong.empty();}catch(NumberFormatException invalid){return OptionalLong.empty();}}
    private static Optional<String> uniqueText(List<String> lines,String prefix){if(blank(prefix))return Optional.empty();var values=lines.stream().filter(s->s.startsWith(prefix)).map(s->s.substring(prefix.length()).trim()).toList();return values.size()==1?Optional.of(values.getFirst()):Optional.empty();}
    private static boolean blank(String s){return s==null||s.isBlank();}
}
