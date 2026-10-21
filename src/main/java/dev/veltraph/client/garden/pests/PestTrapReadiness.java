package dev.veltraph.client.garden.pests;

import dev.veltraph.client.game.LocationText;
import java.util.List;

/** Source tab-row prefix followed by literal '#' count; no GUI pest count or guessed ready state. */
public final class PestTrapReadiness {
    private PestTrapReadiness() {}
    public record Observation(boolean known,String row,int readyTraps) {}
    public static Observation read(List<String> actualTabRows,String exactPrefix) {
        if(actualTabRows==null||exactPrefix==null||exactPrefix.isBlank()
                ||!exactPrefix.equals(LocationText.stripColorCodes(exactPrefix).trim()))return new Observation(false,"",0);
        String matched=null;
        for(String raw:actualTabRows){
            if(raw==null)continue;
            String row=LocationText.stripColorCodes(raw).trim();
            if(!row.startsWith(exactPrefix))continue;
            // Ambiguous native evidence cannot authorize a maintenance transaction.
            if(matched!=null)return new Observation(false,"",0);
            matched=row;
        }
        return matched==null?new Observation(false,"",0):new Observation(true,matched,(int)matched.chars().filter(c->c=='#').count());
    }
    public static int readyTraps(List<String> actualTabRows,String exactPrefix){return read(actualTabRows,exactPrefix).readyTraps();}
}
