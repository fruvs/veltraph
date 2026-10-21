package dev.veltraph.client.greenhouse;

import java.time.Instant;
import java.util.*;
import static dev.veltraph.client.greenhouse.GreenhouseCapturedMenus.*;

/** Recorded plant facts for a player to inspect. No recipe, location, discovery or action authority is synthesized. */
public final class GreenhouseRecipeBrowser {
    private GreenhouseRecipeBrowser() {}
    public record View(String text,List<String> names){public View{names=List.copyOf(names);}public boolean authorizesAction(){return false;}}
    private record Entry(Definition definition,Optional<Mutation> mutation) {}
    private static List<Entry> entries(GreenhouseMenuLedger ledger){var result=new ArrayList<Entry>();for(var d:ledger.crops())result.add(new Entry(d,Optional.empty()));for(var m:ledger.mutations())result.add(new Entry(m.definition(),Optional.of(m)));result.sort(Comparator.comparing(e->e.definition().name()));return List.copyOf(result);}
    private static String key(String value){return value==null?"":value.strip().toLowerCase(Locale.ROOT);}
    private static List<GreenhouseIdentityCatalog.Entry> identities(GreenhouseIdentityCatalog catalog,String query){return catalog.entries().stream().filter(e->"MUTATION".equals(e.category())&&(key(e.id()).equals(key(query))||key(e.name()).equals(key(query)))).toList();}
    public static View list(GreenhouseMenuLedger ledger,GreenhouseIdentityCatalog catalog,String requested){
        String filter=key(requested);if(filter.isEmpty())filter="all";
        if(!Set.of("all","base","disclosed","hidden","secret","uncaptured").contains(filter))return new View("Choose all, base, disclosed, hidden, secret or uncaptured.",List.of());
        var entries=entries(ledger);var names=new ArrayList<String>();
        if(filter.equals("uncaptured")){
            var recorded=entries.stream().map(e->key(e.definition().name())).collect(java.util.stream.Collectors.toSet());
            for(var id:catalog.entries())if("MUTATION".equals(id.category())&&!recorded.contains(key(id.name())))names.add(id.name());Collections.sort(names);
            return new View("Identity-only mutations without a recorded Guide row: "+names+"\nRecipes, growth metadata and discovery remain unobserved. Catalog captured "+catalog.capturedAt()+"; lastUpdated="+catalog.lastUpdated()+"; source="+catalog.source()+".",names);
        }
        var selectedSources=new LinkedHashSet<Source>();
        for(var e:entries){boolean include=switch(filter){case "all"->true;case "base"->e.definition().kind()==PlantKind.BASE;case "disclosed"->e.mutation().map(Mutation::countsKnown).orElse(false);case "hidden"->e.mutation().map(m->m.disclosure()==Disclosure.HIDDEN).orElse(false);case "secret"->e.mutation().map(m->m.disclosure()==Disclosure.SECRET).orElse(false);default->false;};if(include){names.add(e.definition().name());selectedSources.add(e.definition().source());}}
        return new View("Recorded Greenhouse entries ("+filter+"): "+names+"\nUse /vgreenhouse recipe <recorded name or mutation catalog ID>. Records are dated evidence; refresh the actual Guide. No current profile unlock, physical layout or care action is inferred.\n"
            +String.join("\n",selectedSources.stream().limit(8).map(s->source("Recorded list source",s)).toList())
            +(selectedSources.size()>8?"\nAdditional source records: "+(selectedSources.size()-8):""),names);
    }
    public static View describe(GreenhouseMenuLedger ledger,GreenhouseIdentityCatalog catalog,String query){
        if(query==null||query.length()>128||key(query).isEmpty())return new View("Use /vgreenhouse recipe <recorded name or mutation catalog ID>.",List.of());
        var all=entries(ledger);var matches=all.stream().filter(e->key(e.definition().name()).equals(key(query))).toList();
        var ids=identities(catalog,query);
        if(matches.isEmpty()&&ids.size()==1)matches=all.stream().filter(e->key(e.definition().name()).equals(key(ids.getFirst().name()))).toList();
        String recordedName=matches.size()==1?key(matches.getFirst().definition().name()):"";
        if(matches.size()==1&&ids.stream().anyMatch(id->!key(id.name()).equals(recordedName)))return new View("Recorded name and catalog identity conflict; inspect the actual Guide rather than choosing another record.",List.of());
        if(matches.size()>1||(matches.isEmpty()&&ids.size()>1))return new View("Recorded name or identity is ambiguous; inspect the actual Guide rather than selecting a record.",List.of());
        if(matches.isEmpty()){
            if(ids.size()==1){var id=ids.getFirst();return new View(id.name()+" — identity only: "+id.id()+"\nNo recorded Guide row. Requirements, surfaces, stages, effects, layout and discovery are unobserved.\nCatalog captured "+catalog.capturedAt()+"; lastUpdated="+catalog.lastUpdated()+"; source="+catalog.source(),List.of(id.name()));}
            return new View("No recorded plant or known mutation identity matches. Use /vgreenhouse recipes all.",List.of());
        }
        var e=matches.getFirst();var d=e.definition();var lines=new ArrayList<String>();
        lines.add(d.name()+" — recorded "+(d.kind()==PlantKind.BASE?"base/group row":"mutation row"));
        lines.add("Recorded size: "+d.size()+" | growth surfaces: "+String.join(", ",d.surfaces())+" | growth stages: "+d.growthStages());
        lines.add("Stage totals are metadata, not current maturity, harvest eligibility or a growth interval.");
        e.mutation().ifPresent(m->{
            lines.add("Recorded conditions: "+switch(m.disclosure()){case VISIBLE_COUNTS->String.join("; ",m.conditionLines());case ZERO_ADJACENT_CROPS->"0 adjacent crops (shown condition; physical neighborhood remains unbound)";case HIDDEN->"Hidden (???) — ingredient counts are unknown";case SECRET->"Secret — ingredient counts are unknown";case ABSENT->"Unobserved";});
            lines.add("Recorded discovery: "+(m.discoveryKnown()?(m.discovered()?"DISCOVERED":"UNKNOWN"):"unobserved")+"; this does not establish the current profile's unlocks.");
        });
        if(!d.effects().isEmpty())for(var effect:d.effects())lines.add("Recorded effect: "+effect.name()+" — "+String.join(" ",effect.description()));
        lines.add(source("Definition/conditions",d.source()));
        if(e.mutation().isPresent())ledger.layout(d.name()).ifPresentOrElse(layout->{
            lines.add("Recorded preview uses menu-grid positions only:");
            for(var c:layout.cells())lines.add("slot "+c.slot()+", row "+(c.row()+1)+", column "+(c.column()+1)+": "+c.crop()+(c.target()?" [spread target]":"")+(c.anyOrderExplicit()?" [any order explicitly shown]":""));
            lines.add(source("Layout preview",layout.source()));
            lines.add("These positions are not world offsets, a path or a universal adjacency rule.");
        },()->lines.add("Layout preview not recorded; no physical arrangement inferred."));
        var catalogMatches=catalog.entries().stream().filter(id->"MUTATION".equals(id.category())&&key(id.name()).equals(key(d.name()))).toList();
        if(catalogMatches.size()==1)lines.add("Catalog identity only: "+catalogMatches.getFirst().id()+"; captured "+catalog.capturedAt()+"; lastUpdated="+catalog.lastUpdated()+"; source="+catalog.source()+" (separate from the menu observation).");
        lines.add("Care frequency requires the selected plant's refreshed Diagnostics durations and conditions. Counts and previews do not promise mutation results or authorize placement, harvest or watering.");
        return new View(String.join("\n",lines),List.of(d.name()));
    }
    private static String source(String kind,Source source){return kind+" recorded at "+Instant.ofEpochMilli(source.observedAtMillis())+" from "+source.title()+" (container="+source.containerId()+", revision="+source.revision()+"): "+source.provenance();}
}
