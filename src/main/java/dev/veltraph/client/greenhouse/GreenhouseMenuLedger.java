package dev.veltraph.client.greenhouse;

import com.google.gson.*;
import dev.veltraph.client.gui.MenuSnapshot;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static dev.veltraph.client.greenhouse.GreenhouseCapturedMenus.*;

/** Bounded factual menu ledger. Its unbound diagnostics never become actionable cell snapshots. */
public final class GreenhouseMenuLedger {
    public record Entry(MenuSnapshot menu,long observedAtMillis,String provenance) {}
    private final Map<String,Definition> crops=new TreeMap<>();
    private final Map<String,Mutation> mutations=new TreeMap<>();
    private final Map<String,Layout> layouts=new TreeMap<>();
    private final ArrayDeque<Entry> recent=new ArrayDeque<>();
    private Diagnostics liveDiagnostic;private Observation latest;private Carpenter carpenter;
    private final GreenhouseInspectionEstimates inspectionEstimates=new GreenhouseInspectionEstimates();
    private String error="";
    public GreenhouseMenuLedger(){loadBundledCatalogue();}
    public Optional<Observation> observe(MenuSnapshot menu,long at,String source){
        MenuSnapshot stripped=strip(menu);var parsed=GreenhouseCapturedMenus.read(stripped,at,source);if(parsed.isEmpty()){liveDiagnostic=null;latest=null;inspectionEstimates.invalidate("Visible menu was replaced by an unknown profile");return Optional.empty();}var o=parsed.get();
        boolean already=recent.stream().anyMatch(e->e.menu().equals(stripped)&&e.observedAtMillis()==at&&e.provenance().equals(source));
        if(!already){recent.addLast(new Entry(stripped,at,source));while(recent.size()>64)recent.removeFirst();}
        apply(o,true);return parsed;
    }
    private void apply(Observation o,boolean current){
        for(var c:o.crops())crops.put(c.name(),c);for(var m:o.mutations())mutations.put(m.definition().name(),m);
        o.layout().ifPresent(l->layouts.put(l.mutation(),l));o.carpenter().ifPresent(c->carpenter=c);
        if(current){latest=o;liveDiagnostic=o.diagnostics().orElse(null);inspectionEstimates.observe(liveDiagnostic,GreenhouseInspectionEstimates.Scope.unbound());}
    }
    public Collection<Definition> crops(){return List.copyOf(crops.values());}
    public Collection<Mutation> mutations(){return List.copyOf(mutations.values());}
    public Optional<Mutation> mutation(String name){return Optional.ofNullable(mutations.get(name));}
    public Optional<Diagnostics> diagnostic(){return Optional.ofNullable(liveDiagnostic);}
    public Optional<Observation> latest(){return Optional.ofNullable(latest);}
    public void invalidateCurrent(){liveDiagnostic=null;latest=null;inspectionEstimates.invalidate("Native context changed; diagnostic scope revoked");}
    public GreenhouseInspectionEstimates.View inspectionEstimates(long now,long maxAge){return inspectionEstimates.view(now,maxAge);}
    public Optional<Layout> layout(String name){return Optional.ofNullable(layouts.get(name));}
    public String export(){return new Gson().toJson(List.copyOf(recent));}
    /** Restored menu observations remain archival. They cannot create a current diagnostic. */
    public void restore(String json){
        if(json==null||json.isBlank())return;
        if(json.length()>2_000_000){error="Saved menu ledger exceeds bounded size";return;}
        try{var entries=new Gson().fromJson(json,Entry[].class);if(entries==null||entries.length>64)throw new IllegalArgumentException("Too many archived menus");
            for(var e:entries){if(e==null||e.menu()==null||e.provenance()==null)throw new IllegalArgumentException("Invalid archived menu");var parsed=GreenhouseCapturedMenus.read(e.menu(),e.observedAtMillis(),e.provenance());if(parsed.isPresent()){recent.addLast(new Entry(strip(e.menu()),e.observedAtMillis(),e.provenance()));apply(parsed.get(),false);}}
        }catch(RuntimeException invalid){error="Saved menu ledger is invalid";}
    }
    public String catalogSummary(){long visible=mutations.values().stream().filter(Mutation::countsKnown).count(),hidden=mutations.values().stream().filter(m->m.disclosure()==Disclosure.HIDDEN).count(),secret=mutations.values().stream().filter(m->m.disclosure()==Disclosure.SECRET).count();
        return crops.size()+" captured base/group entries, "+mutations.size()+" mutation rows; "+visible+" disclosed conditions, "+hidden+" hidden, "+secret+" secret; API identity catalogue has40 mutations, Jerryflower uncaptured";}
    public String diagnosticSummary(long now,long maxAge){
        if(liveDiagnostic==null)return "Crop Diagnostics not refreshed; captured archive does not identify current plant state";
        long at=liveDiagnostic.source().observedAtMillis();String knowledge=now<at||maxAge<1||now-at>maxAge?"STALE":"KNOWN unbound";
        return knowledge+": "+liveDiagnostic.summary()+" | original remaining times copied unchanged from menu; "+inspectionEstimates(now,maxAge).summary();
    }
    public String careSummary(long now,long maxAge){
        if(liveDiagnostic==null)return "Open Crop Diagnostics to inspect current care; no JSON binding needed for read-only data";
        var d=liveDiagnostic;boolean stale=now<d.source().observedAtMillis()||now-d.source().observedAtMillis()>maxAge;
        if(stale)return "Refresh Crop Diagnostics before care; original remaining times are stale and unchanged | "+inspectionEstimates(now,maxAge).summary();
        if(d.status()==Status.DEAD)return "Observed DEAD"+(d.causeOfDeath().isBlank()?"":" from "+d.causeOfDeath())+"; watering/harvesting is not authorized by water level or zero growth-stage metadata";
        return d.summary()+" | enough water to reach full growth="+(d.enoughWaterExplicit()?"explicitly observed":"unknown")+" | "+inspectionEstimates(now,maxAge).summary();
    }
    public GreenhouseCombinations.Validation validateCounts(String mutation,Map<String,Integer> selected){
        var recipe=mutations.get(mutation);if(recipe==null||!recipe.countsKnown())return new GreenhouseCombinations.Validation(GreenhouseCombinations.Result.UNKNOWN_RECIPE,"No visible captured requirement counts for "+mutation);
        if(selected==null||selected.entrySet().stream().anyMatch(e->e.getKey()==null||e.getValue()==null||e.getValue()<1)||!selected.equals(recipe.requirements()))return new GreenhouseCombinations.Validation(GreenhouseCombinations.Result.MISSING_INGREDIENTS,"Captured requirement counts: "+recipe.conditionLines());
        return new GreenhouseCombinations.Validation(GreenhouseCombinations.Result.UNKNOWN_PROXIMITY,"Captured counts match"+(recipe.disclosure()==Disclosure.ZERO_ADJACENT_CROPS?" zero-adjacent condition":"")+"; "+(layouts.containsKey(mutation)?"an actual menu layout preview is available":"layout preview not captured")+"; world target, empty soil and adjacency remain unbound");
    }
    /** Only exact current Guide offsets plus freshly associated world cells can close the advisory proximity gap. */
    public GreenhouseCombinations.Validation validateAssociatedPattern(GreenhouseGuide.Recipe guide,dev.veltraph.client.greenhouse.GreenhouseModel.Snapshot observed,
            GreenhouseCombinations.ObservedPattern pattern,long now,long freshnessMs){
        if(pattern==null)return new GreenhouseCombinations.Validation(GreenhouseCombinations.Result.UNKNOWN_PROXIMITY,"Select observed target and ingredient cells");
        var recipe=mutations.get(pattern.crop());
        if(recipe==null||!recipe.countsKnown())return new GreenhouseCombinations.Validation(GreenhouseCombinations.Result.UNKNOWN_RECIPE,"Hidden or unobserved recipe is not actionable");
        if(recipe.disclosure()==Disclosure.ZERO_ADJACENT_CROPS||recipe.requirements().containsKey("Fire")||recipe.requirements().containsKey("Dead Plant"))return new GreenhouseCombinations.Validation(GreenhouseCombinations.Result.UNKNOWN_PROXIMITY,"Typed environmental Fire/dead-donor/absence evidence is not supplied; no recipe geometry inferred");
        if(guide==null||!guide.crop().equals(pattern.crop())||!guide.ingredients().equals(recipe.requirements()))return new GreenhouseCombinations.Validation(GreenhouseCombinations.Result.UNKNOWN_PROXIMITY,"Refresh exact Guide counts and explicit world offsets; menu-grid spacing is not world geometry");
        var result=new GreenhouseCombinations().validateObserved(guide,observed,pattern,now,freshnessMs);
        return result.result()==GreenhouseCombinations.Result.VALID_OBSERVED_PATTERN?new GreenhouseCombinations.Validation(result.result(),"Fresh associated cells match exact observed Guide counts/offsets only; current surface and effect conditions are not inferred, and no mutation action is prepared"):result;
    }
    /** Typed environmental validation is advisory; no producer, native binding or mutation action is synthesized. */
    public GreenhouseCombinations.Validation validateAssociatedEnvironment(GreenhouseGuide.Recipe guide,
            GreenhouseCombinations.ObservedPattern pattern,GreenhouseEnvironmentEvidence.Footprint footprint,
            GreenhouseEnvironmentEvidence.Frame frame,GreenhouseEnvironmentEvidence.Authority authority,long now,long freshnessMs){
        var recipe=pattern==null?null:mutations.get(pattern.crop());
        return GreenhouseEnvironmentEvidence.validate(recipe,guide,pattern,footprint,frame,authority,now,freshnessMs);
    }
    public String recipeSummary(){return mutations.values().stream().filter(Mutation::countsKnown).map(m->m.definition().name()+": "+String.join(", ",m.conditionLines())).toList()+" | "+catalogSummary()+" | recorded requirements are advisory until fresh Guide and scoped plant observations";}
    public String error(){return error;}
    private static MenuSnapshot strip(MenuSnapshot m){return m==null?null:new MenuSnapshot(m.id(),m.title(),m.slots().stream().filter(r->!r.playerInventory()).toList(),m.revision());}
    private void loadBundledCatalogue(){
        try(var stream=GreenhouseMenuLedger.class.getResourceAsStream("/greenhouse/captured-mechanics-2026-10-01.json")){
            if(stream==null)return;var data=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            if(data.get("schemaVersion").getAsInt()!=1||data.getAsJsonArray("plants").size()>100)throw new IllegalArgumentException("Invalid captured catalogue");
            for(var value:data.getAsJsonArray("plants")){var row=value.getAsJsonObject();var evidence=row.getAsJsonObject("evidence");
                String title=evidence.get("title").getAsString(),provenance="Bundled menu catalogue (2026-10-01)";
                var source=new Source(title,-1,-1,Instant.parse(evidence.get("observedAtUtc").getAsString()).toEpochMilli(),provenance);
                List<String> lore=evidence.getAsJsonArray("lore").asList().stream().map(l->l.getAsJsonObject().get("text").getAsString()).toList();
                // Main evidence resource intentionally excludes icon components; no missing wire/icon ID is synthesized.
                var def=new Definition(row.get("name").getAsString(),"",row.get("kind").getAsString().equals("Base Crop")?PlantKind.BASE:PlantKind.MUTATION,row.get("size").getAsString(),Arrays.asList(row.get("growthSurface").getAsString().split(", ")),row.get("growthStages").getAsInt(),readEffects(lore),source,evidence.get("slot").getAsInt());
                if(def.kind()==PlantKind.BASE)crops.put(def.name(),def);
                else{Disclosure disclosure=switch(row.get("recipeDisclosure").getAsString()){case "visible"->row.get("zeroAdjacentCrops").getAsBoolean()?Disclosure.ZERO_ADJACENT_CROPS:Disclosure.VISIBLE_COUNTS;case "hidden"->Disclosure.HIDDEN;case "secret"->Disclosure.SECRET;default->Disclosure.ABSENT;};
                    var counts=new TreeMap<String,Integer>();if(!row.get("requirements").isJsonNull())for(var ingredient:row.getAsJsonArray("requirements")){var r=ingredient.getAsJsonObject();counts.put(r.get("name").getAsString(),r.get("count").getAsInt());}
                    mutations.put(def.name(),new Mutation(def,disclosure,counts,lore.contains("DISCOVERED"),lore.contains("DISCOVERED")||lore.contains("UNKNOWN"),row.getAsJsonArray("spreadingConditionText").asList().stream().map(JsonElement::getAsString).toList()));
                }
            }
            for(var value:data.getAsJsonArray("layoutPreviews")){var row=value.getAsJsonObject();var cells=new ArrayList<LayoutCell>();Source source=null;String mutation="";
                for(var valueCell:row.getAsJsonArray("cells")){var c=valueCell.getAsJsonObject();int slot=c.get("slot").getAsInt();var lore=c.getAsJsonArray("lore").asList().stream().map(JsonElement::getAsString).toList();boolean target=lore.equals(List.of("Will spread here!"));String name=c.get("name").getAsString();if(target)mutation=name;cells.add(new LayoutCell(slot,slot/9,slot%9,name,target,lore.equals(List.of("These can be placed in any order!"))));var e=c.getAsJsonObject("evidence");source=new Source(e.get("title").getAsString(),-1,-1,Instant.parse(e.get("observedAtUtc").getAsString()).toEpochMilli(),"Bundled menu catalogue (2026-10-01)");}
                if(!mutation.isBlank()&&source!=null)layouts.put(mutation,new Layout(mutation,cells,source));
            }
        }catch(IOException|RuntimeException invalid){crops.clear();mutations.clear();layouts.clear();error="Bundled captured catalogue unavailable or invalid";}
    }
    private static List<Effect> readEffects(List<String> lore){var result=new ArrayList<Effect>();String name=null;var text=new ArrayList<String>();for(String line:lore){if(line.startsWith("➤ ")){if(name!=null)result.add(new Effect(name,text));name=line.substring(2);text=new ArrayList<>();}else if(name!=null){if(line.isBlank()){result.add(new Effect(name,text));name=null;text=new ArrayList<>();}else text.add(line);}}if(name!=null)result.add(new Effect(name,text));return List.copyOf(result);}
}
