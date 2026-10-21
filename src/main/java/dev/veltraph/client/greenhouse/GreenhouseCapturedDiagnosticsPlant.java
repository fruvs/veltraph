package dev.veltraph.client.greenhouse;

import dev.veltraph.client.gui.MenuSnapshot;
import java.util.*;
import static dev.veltraph.client.greenhouse.GreenhouseModel.*;

/** Parses received full diagnostics only; this helper alone never supplies request/target association authority. */
public final class GreenhouseCapturedDiagnosticsPlant {
    private GreenhouseCapturedDiagnosticsPlant() {}
    public record Observed(Plant plant,GreenhouseCapturedMenus.Diagnostics diagnostic) {}
    public static Optional<Observed> read(GreenhouseDiagnosticAssociation.Selection selection,MenuSnapshot menu,
            Map<Cell,String> actualWorldStates,long receivedAt){
        if(selection==null||!selection.fullCapturedDiagnostics()||menu==null||receivedAt<0||actualWorldStates==null)return Optional.empty();
        var b=selection.bindings();var c=selection.binding();String fingerprint=actualWorldStates.get(selection.cell());
        if(fingerprint==null||fingerprint.isBlank()||!menu.title().equals(b.menuTitle()))return Optional.empty();
        var rows=menu.slots().stream().filter(r->!r.playerInventory()&&!r.locked()&&r.count()>0&&r.id()==4
            &&r.itemId().equals(selection.receiptItemId())&&r.name().equals(c.slotName())&&r.itemId().equals(c.itemId())).toList();
        if(rows.size()!=1)return Optional.empty();var row=rows.getFirst();
        if(!selection.isolatedNativeMenu()&&(Collections.frequency(row.lore(),b.profileLine())!=1||Collections.frequency(row.lore(),b.plotLine())!=1
                ||Collections.frequency(row.lore(),c.cellLine())!=1))return Optional.empty();
        String source=(selection.isolatedNativeMenu()?"Isolated native transaction; server target echo absent. ":"")+"Received full native diagnostics container="+menu.id()+" revision="+menu.revision()+"; association requires submitted request and complete native row custody";
        var observation=GreenhouseCapturedMenus.read(menu,receivedAt,source);if(observation.isEmpty()||observation.get().diagnostics().isEmpty())return Optional.empty();
        var d=observation.get().diagnostics().get();
        if(!d.definition().name().equals(row.name())||!d.definition().nativeMenuItem().equals(row.itemId()))return Optional.empty();
        PlantState state=switch(d.status()){case DEAD->PlantState.DEAD;case GROWING->PlantState.GROWING;case UNKNOWN->PlantState.UNKNOWN;};
        if(state==PlantState.UNKNOWN)return Optional.empty();
        // A displayed stage maximum is not an explicit HARVEST_READY observation. Relative water/growth values do not authorize care.
        int stage=d.stage().map(GreenhouseCapturedMenus.Stage::current).orElse(-1);
        return Optional.of(new Observed(new Plant(d.definition().name(),state,WaterState.UNKNOWN,stage,
            Clock.unknown(),Clock.unknown(),Clock.unknown(),fingerprint,source),d));
    }
}
