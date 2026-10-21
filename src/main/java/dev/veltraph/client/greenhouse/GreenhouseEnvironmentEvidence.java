package dev.veltraph.client.greenhouse;

import java.util.*;
import static dev.veltraph.client.greenhouse.GreenhouseModel.*;
import static dev.veltraph.client.greenhouse.GreenhouseCombinations.*;

/** Read-only, finite evidence validation. A successful result never prepares care, harvesting or spending. */
public final class GreenhouseEnvironmentEvidence {
    private GreenhouseEnvironmentEvidence() {}
    public static final int MAX_CELLS=100;
    public enum Occupancy { EMPTY_SOIL, LIVE_PLANT, DEAD_PLANT, FIRE, OTHER, UNKNOWN }
    public enum Role { MUTATION_DONOR, EFFECT_DONOR, EFFECT_RECIPIENT, SELECTED_TARGET, UNKNOWN }
    public enum Knowledge { OBSERVED, UNKNOWN, STALE, MISMATCH }
    /** Geometry is explicit observed WORLD offsets, never a menu grid or assumed radius. */
    public record Footprint(Context context,Cell target,Set<Cell> offsets,long observedAt,String source,boolean complete) {
        public Footprint { offsets=Set.copyOf(offsets); }
    }
    /** Native block/soil identity and loaded build-height checks must be independently supplied. */
    public record CellEvidence(Cell cell,Occupancy occupancy,String nativeBlockId,String fingerprint,
                               String surface,long observedAt,boolean loaded,boolean withinBuildHeight,
                               Plant plant,GreenhouseCapturedMenus.Diagnostics diagnostic,Role role) {}
    /** The exact protected native inventory/tool/cursor and PWC/owner epoch are included in custody. */
    public record Frame(GreenhouseDiagnosticAssociation.Custody custody,long observedAt,long revision,
                        Map<Cell,CellEvidence> cells) {
        public Frame { cells=Map.copyOf(cells); }
    }
    /** No default implementation: a typed value or archived provenance cannot establish native association. */
    public interface Authority {
        boolean current(Frame exactFrame);
        boolean geometryAssociated(Frame exactFrame,Footprint exactFootprint,GreenhouseGuide.Recipe exactGuide);
        boolean effectGeometryAssociated(Frame exactFrame,Footprint exactFootprint);
        boolean plantAssociated(Frame exactFrame,CellEvidence exactCell);
        boolean diagnosticAssociated(Frame exactFrame,CellEvidence exactCell);
        boolean environmentAssociated(Frame exactFrame,CellEvidence exactCell);
        boolean absenceAssociated(Frame exactFrame,CellEvidence exactCell);
        boolean effectAssociated(Frame exactFrame,EffectLink exactLink);
    }
    public record EffectLink(Cell donor,Cell recipient,GreenhouseCapturedMenus.Effect effect,
                             long observedAt,String source,boolean complete) {}
    public record EffectValidation(Knowledge knowledge,Set<Cell> preservedDonors,String reason) {
        public EffectValidation { preservedDonors=Set.copyOf(preservedDonors); }
    }
    private static Validation unknown(String reason){return new Validation(Result.UNKNOWN_PROXIMITY,reason);}
    private static boolean text(String s){return s!=null&&!s.isBlank();}
    private static boolean fresh(long at,long now,long age){return at>=0&&age>0&&now>=at&&now-at<=age;}
    private static boolean guarded(Authority authority,Frame frame){
        try{return authority!=null&&frame!=null&&frame.custody()!=null&&frame.custody().complete()&&authority.current(frame);}
        catch(RuntimeException lost){return false;}
    }
    private static boolean associated(Authority authority,Frame frame,CellEvidence cell,boolean diagnostic){
        if(!guarded(authority,frame))return false;
        try{boolean accepted=diagnostic?authority.diagnosticAssociated(frame,cell):authority.plantAssociated(frame,cell);
            return accepted&&guarded(authority,frame);
        }catch(RuntimeException lost){return false;}
    }
    private static boolean environmental(Authority a,Frame f,CellEvidence c,boolean absence){
        if(!guarded(a,f))return false;
        try{return (absence?a.absenceAssociated(f,c):a.environmentAssociated(f,c))&&guarded(a,f);}
        catch(RuntimeException lost){return false;}
    }
    private static boolean basic(Frame f,CellEvidence c,long now,long age){
        return c!=null&&c.cell()!=null&&c.occupancy()!=null&&c.role()!=null&&c.loaded()&&c.withinBuildHeight()
            &&text(c.nativeBlockId())&&text(c.fingerprint())&&fresh(c.observedAt(),now,age)&&c.observedAt()<=f.observedAt();
    }
    private static Set<Cell> absolute(Footprint p){
        var cells=new HashSet<Cell>();
        for(var o:p.offsets()){
            if(o==null||Math.abs((long)o.x())>16||Math.abs((long)o.y())>16||Math.abs((long)o.z())>16||o.equals(new Cell(0,0,0)))return Set.of();
            try{cells.add(new Cell(Math.addExact(p.target().x(),o.x()),Math.addExact(p.target().y(),o.y()),Math.addExact(p.target().z(),o.z())));}
            catch(ArithmeticException outside){return Set.of();}
        }
        return Set.copyOf(cells);
    }
    private static boolean alivePlant(CellEvidence c){
        return c.plant()!=null&&c.plant().identified()&&c.fingerprint().equals(c.plant().fingerprint())
            &&(c.plant().state()==PlantState.GROWING||c.plant().state()==PlantState.HARVEST_READY);
    }
    private static boolean diagnostic(CellEvidence c,Frame f,long now,long age){
        var d=c.diagnostic();
        return d!=null&&d.source()!=null&&d.definition()!=null&&d.source().containerId()>=0&&d.source().revision()>=0
            &&text(d.source().provenance())&&fresh(d.source().observedAtMillis(),now,age)
            &&d.source().observedAtMillis()<=f.observedAt()&&text(d.definition().name());
    }
    private static boolean empty(Authority a,Frame f,CellEvidence c){
        return c.occupancy()==Occupancy.EMPTY_SOIL&&c.plant()!=null&&c.plant().identified()
            &&c.plant().state()==PlantState.EMPTY&&c.plant().crop().isBlank()
            &&c.fingerprint().equals(c.plant().fingerprint())&&associated(a,f,c,false);
    }
    /** Exact captured requirements + observed complete neighborhood; no extra crop may be silently omitted. */
    public static Validation validate(GreenhouseCapturedMenus.Mutation recipe,GreenhouseGuide.Recipe guide,
            ObservedPattern selected,Footprint footprint,Frame frame,Authority authority,long now,long maxAge){
        if(recipe==null||!recipe.countsKnown())return new Validation(Result.UNKNOWN_RECIPE,"Recipe remains hidden or unobserved");
        if(selected==null||guide==null||footprint==null||frame==null||!guarded(authority,frame))return unknown("Fresh native custody, association and complete observed footprint required");
        if(recipe.definition()==null||selected.crop()==null||selected.emptyTarget()==null||footprint.target()==null||footprint.context()==null||guide.context()==null)
            return unknown("Required recipe, target and scope fields are missing");
        if(!fresh(frame.observedAt(),now,maxAge)||!fresh(guide.observedAt(),now,maxAge)||!fresh(footprint.observedAt(),now,maxAge))
            return new Validation(Result.STALE_OBSERVATIONS,"Refresh native cells and exact world geometry");
        if(frame.revision()<0||!footprint.complete()||!text(footprint.source())||!text(guide.source())
                ||!text(selected.guideEvidence())
                ||!frame.custody().context().equals(guide.context())||!guide.context().equals(footprint.context())
                ||!selected.crop().equals(recipe.definition().name())||!selected.crop().equals(guide.crop())
                ||!guide.ingredients().equals(recipe.requirements())||!selected.emptyTarget().equals(footprint.target())
                ||footprint.offsets().isEmpty()||footprint.offsets().size()>MAX_CELLS||frame.cells().size()>MAX_CELLS+1)return unknown("Counts, scope, target or complete bounded geometry disagree");
        try{if(!authority.geometryAssociated(frame,footprint,guide)||!guarded(authority,frame))return unknown("Exact world geometry is not authentically associated");}
        catch(RuntimeException lost){return unknown("World geometry association changed");}
        var neighborhood=absolute(footprint);
        if(neighborhood.size()!=footprint.offsets().size()||!neighborhood.containsAll(selected.ingredientCells())||selected.ingredientCells().contains(footprint.target()))return unknown("Ingredient cells lie outside the exact observed neighborhood");
        var requiredCells=new HashSet<>(neighborhood);requiredCells.add(footprint.target());
        if(!frame.cells().keySet().equals(requiredCells))return unknown("Every cell of the complete footprint and target must be independently observed");
        for(var entry:frame.cells().entrySet())if(!entry.getKey().equals(entry.getValue().cell())||!basic(frame,entry.getValue(),now,maxAge))return unknown("Unloaded, out-of-height, stale or mismatched native cell");
        var target=frame.cells().get(footprint.target());
        if(!empty(authority,frame,target))return new Validation(Result.NO_EMPTY_TARGET,"Target is not independently associated empty soil");
        if(!recipe.definition().surfaces().contains(target.surface()))return unknown("Actual target growth surface does not match the captured requirement");
        boolean zero=recipe.disclosure()==GreenhouseCapturedMenus.Disclosure.ZERO_ADJACENT_CROPS;
        if(zero&&(!recipe.requirements().isEmpty()||!selected.ingredientCells().isEmpty()||!guide.offsets().isEmpty()))return unknown("Zero-adjacent target cannot also select donors");
        if(!zero){
            var offsets=new HashSet<Cell>();for(var cell:selected.ingredientCells())offsets.add(new Cell(cell.x()-footprint.target().x(),cell.y()-footprint.target().y(),cell.z()-footprint.target().z()));
            if(!offsets.equals(guide.offsets()))return unknown("Donors do not match the exact observed Guide offsets");
        }
        var counts=new HashMap<String,Integer>();
        for(var cell:neighborhood){
            var c=frame.cells().get(cell);boolean donor=selected.ingredientCells().contains(cell);
            if(!donor){
                if(c.occupancy()==Occupancy.LIVE_PLANT||c.occupancy()==Occupancy.DEAD_PLANT||c.occupancy()==Occupancy.UNKNOWN
                        ||!environmental(authority,frame,c,true))return unknown("Complete absence proof includes a crop or unidentified cell");
                if(c.occupancy()==Occupancy.EMPTY_SOIL&&!empty(authority,frame,c))return unknown("Empty soil is not independently associated");
                continue;
            }
            if(c.role()!=Role.MUTATION_DONOR&&c.role()!=Role.EFFECT_DONOR)return unknown("Selected donor lacks an explicit preserved role");
            String ingredient;
            switch(c.occupancy()){
                case FIRE -> {
                    if(!c.nativeBlockId().equals("minecraft:fire")||c.plant()!=null||c.diagnostic()!=null||!environmental(authority,frame,c,false))return unknown("Fire needs exact loaded native fire evidence, not a crop alias");
                    ingredient="Fire";
                }
                case DEAD_PLANT -> {
                    if(!diagnostic(c,frame,now,maxAge)||c.diagnostic().status()!=GreenhouseCapturedMenus.Status.DEAD
                            ||!c.diagnostic().rawStatus().equals("DEAD")||!associated(authority,frame,c,true))return unknown("Dead donor needs explicit freshly associated DEAD diagnostics");
                    if(c.plant()!=null&&(!c.plant().identified()||c.plant().state()!=PlantState.DEAD
                            ||!c.plant().fingerprint().equals(c.fingerprint())||!c.plant().crop().equals(c.diagnostic().definition().name())
                            ||!associated(authority,frame,c,false)))return unknown("Associated dead plant and exact DEAD diagnostics disagree");
                    ingredient="Dead Plant";
                }
                case LIVE_PLANT -> {
                    if(!alivePlant(c)||!associated(authority,frame,c,false))return unknown("Live donor is not an exact fresh associated plant");
                    if(c.diagnostic()!=null&&(!diagnostic(c,frame,now,maxAge)||c.diagnostic().status()!=GreenhouseCapturedMenus.Status.GROWING
                            ||!c.diagnostic().definition().name().equals(c.plant().crop())||!associated(authority,frame,c,true)))return unknown("Live plant and diagnostic state disagree");
                    ingredient=c.plant().crop();
                    if(ingredient.equals("Fire")||ingredient.equals("Dead Plant"))return unknown("Environmental/dead requirements cannot be satisfied by a live crop alias");
                }
                default -> {return new Validation(Result.MISSING_INGREDIENTS,"Selected donor is empty, unknown or an unrelated environment block");}
            }
            counts.merge(ingredient,1,Integer::sum);
        }
        if(!counts.equals(recipe.requirements()))return new Validation(Result.MISSING_INGREDIENTS,"Typed donor counts do not match current captured requirements");
        if(!guarded(authority,frame))return unknown("Custody changed during validation");
        return new Validation(Result.VALID_OBSERVED_PATTERN,"Exact complete neighborhood, surface and typed donors match observed requirements; donors remain protected, mutation is probabilistic, and no care/spend action is authorized");
    }
    /** Observe an explicit directed effect only. Matching effect names never invent adjacency, stacking or numeric formulas. */
    public static EffectValidation validateEffect(EffectLink link,Footprint footprint,Frame frame,Authority authority,long now,long maxAge){
        var noDonors=Set.<Cell>of();
        if(link==null||footprint==null||frame==null||!guarded(authority,frame))return new EffectValidation(Knowledge.UNKNOWN,noDonors,"Fresh native scope and actual effect relationship required");
        if(link.donor()==null||link.recipient()==null||footprint.target()==null||footprint.context()==null)
            return new EffectValidation(Knowledge.UNKNOWN,noDonors,"Missing effect endpoints or scope");
        if(!fresh(link.observedAt(),now,maxAge)||!fresh(frame.observedAt(),now,maxAge)||!fresh(footprint.observedAt(),now,maxAge))return new EffectValidation(Knowledge.STALE,noDonors,"Refresh effect/geometry/native observations");
        if(!link.complete()||!text(link.source())||link.effect()==null||!text(link.effect().name())||link.effect().description().isEmpty()
                ||!footprint.complete()||!text(footprint.source())||footprint.offsets().isEmpty()||footprint.offsets().size()>MAX_CELLS
                ||!frame.custody().context().equals(footprint.context())||!link.recipient().equals(footprint.target())||link.donor().equals(link.recipient()))return new EffectValidation(Knowledge.UNKNOWN,noDonors,"Incomplete actual directed effect/geometry evidence");
        try{if(!authority.effectGeometryAssociated(frame,footprint)||!guarded(authority,frame)
                ||!authority.effectAssociated(frame,link)||!guarded(authority,frame))return new EffectValidation(Knowledge.UNKNOWN,noDonors,"No authentic observed directed effect relationship and exact world geometry");}
        catch(RuntimeException lost){return new EffectValidation(Knowledge.UNKNOWN,noDonors,"Effect association changed");}
        var cells=absolute(footprint);var expected=new HashSet<>(cells);expected.add(footprint.target());
        if(cells.size()!=footprint.offsets().size()||!cells.contains(link.donor())||!frame.cells().keySet().equals(expected)||frame.revision()<0)return new EffectValidation(Knowledge.UNKNOWN,noDonors,"Exact complete effect footprint is not observed");
        for(var entry:frame.cells().entrySet())if(!entry.getKey().equals(entry.getValue().cell())||!basic(frame,entry.getValue(),now,maxAge))return new EffectValidation(Knowledge.UNKNOWN,noDonors,"Effect footprint includes an unknown or mismatched native cell");
        var donor=frame.cells().get(link.donor());var recipient=frame.cells().get(link.recipient());
        if(donor.role()!=Role.EFFECT_DONOR||recipient.role()!=Role.EFFECT_RECIPIENT||donor.occupancy()!=Occupancy.LIVE_PLANT||recipient.occupancy()!=Occupancy.LIVE_PLANT
                ||!alivePlant(donor)||!alivePlant(recipient)||!diagnostic(donor,frame,now,maxAge)||!diagnostic(recipient,frame,now,maxAge)
                ||donor.diagnostic().status()!=GreenhouseCapturedMenus.Status.GROWING||recipient.diagnostic().status()!=GreenhouseCapturedMenus.Status.GROWING
                ||!donor.diagnostic().definition().name().equals(donor.plant().crop())||!recipient.diagnostic().definition().name().equals(recipient.plant().crop())
                ||!associated(authority,frame,donor,false)||!associated(authority,frame,recipient,false)
                ||!associated(authority,frame,donor,true)||!associated(authority,frame,recipient,true))return new EffectValidation(Knowledge.UNKNOWN,noDonors,"Both effect endpoints need fresh associated live plant and diagnostic identities");
        if(Collections.frequency(donor.diagnostic().applies(),link.effect())!=1||Collections.frequency(recipient.diagnostic().receives(),link.effect())!=1)
            return new EffectValidation(Knowledge.MISMATCH,noDonors,"Exact applies/receives effect is missing or ambiguous; no stacking inferred");
        if(!guarded(authority,frame))return new EffectValidation(Knowledge.UNKNOWN,noDonors,"Native custody changed during effect validation");
        return new EffectValidation(Knowledge.OBSERVED,Set.of(link.donor()),"Exact observed directed effect; preserve donor, no harvest permission or universal effect formula");
    }
}
