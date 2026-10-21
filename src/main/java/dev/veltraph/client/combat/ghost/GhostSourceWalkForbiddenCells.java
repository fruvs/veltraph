package dev.veltraph.client.combat.ghost;
import java.util.Set;
import java.util.HashSet;
import com.google.gson.JsonParser;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.Cell;

/** F23 native source getter remains opaque. Explicit modern policy is never labeled original empty. */
public final class GhostSourceWalkForbiddenCells {
    public enum Provenance { SOURCE_UNRECOVERED, MODERN_CONFIGURED_OBSERVED }
    public record Observation(Provenance provenance,Set<Cell> cells){public Observation {cells=Set.copyOf(cells);}
        public boolean available(){return provenance==Provenance.MODERN_CONFIGURED_OBSERVED;}
        public Set<Cell> requireObserved(){if(!available())throw new IllegalStateException("Original forbidden-cell getter unrecovered; explicit modern policy required");return cells;}}
    private GhostSourceWalkForbiddenCells() {}
    /** Blank preserves unavailable source input; explicit [] is a user-configured empty policy. Coordinates are exact integers. */
    public static Observation fromActualConfiguredJson(String json){
        if(json==null||json.isBlank())return new Observation(Provenance.SOURCE_UNRECOVERED,Set.of());
        var parsed=JsonParser.parseString(json);if(!parsed.isJsonArray())throw new IllegalArgumentException("Forbidden policy must be an array of [x,y,z]");
        var values=new HashSet<Cell>();for(var element:parsed.getAsJsonArray()){
            if(!element.isJsonArray()||element.getAsJsonArray().size()!=3)throw new IllegalArgumentException("Exact forbidden [x,y,z] cell required");
            var xyz=element.getAsJsonArray();int[] n=new int[3];for(int i=0;i<3;i++){
                var v=xyz.get(i);if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Forbidden coordinates must be numeric integers");
                n[i]=v.getAsBigDecimal().intValueExact();
            }values.add(new Cell(n[0],n[1],n[2]));
        }return new Observation(Provenance.MODERN_CONFIGURED_OBSERVED,values);
    }
}
