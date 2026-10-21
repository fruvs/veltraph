package dev.veltraph.client.greenhouse;

import java.util.*;
import static dev.veltraph.client.greenhouse.GreenhouseCapturedMenus.*;

/** Exact observed effect lore, without donor inference, stacking arithmetic or care permission. */
public final class GreenhouseEffectDisplay {
    private GreenhouseEffectDisplay() {}
    public static String summary(Diagnostics d,long now,long maxAge,boolean associated){
        if(d==null||d.source()==null||d.definition()==null)return "Inspect a plant with the Diagnostics Tool to view its effects.";
        long at=d.source().observedAtMillis();boolean fresh=at>=0&&maxAge>0&&now>=at&&now-at<=maxAge;
        String scope=associated&&fresh?"Selected plant":"Unlinked diagnostic";
        return scope+": "+d.definition().name()+" — "+d.rawStatus()+"\nApplies to nearby crops: "+effects(d.applies())
            +"\nReceived effects: "+effects(d.receives())
            +(d.receivedEffectsLockedExplicit()?"\nReceived effects were fixed when this crop first became harvestable.":"")
            +(!fresh?"\nRefresh Diagnostics; these are the last observed effects.":!associated?"\nInspect and link the selected plant before preparing care.":"");
    }
    private static String effects(List<Effect> observed){
        if(observed.isEmpty())return "None listed";
        var lines=new ArrayList<String>();for(var e:observed)lines.add(e.name()+(e.description().isEmpty()?"":": "+String.join(" ",e.description())));
        return String.join("; ",lines);
    }
}
