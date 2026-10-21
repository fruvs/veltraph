package dev.veltraph.client.greenhouse;

import java.util.*;
import static dev.veltraph.client.greenhouse.GreenhouseModel.*;

/** Documented ingredients are distinct from observed server geometry and mutation probability. */
public final class GreenhouseCombinations {
    public enum Result { VALID_OBSERVED_PATTERN, MISSING_INGREDIENTS, NO_EMPTY_TARGET, UNKNOWN_RECIPE, UNKNOWN_PROXIMITY, STALE_OBSERVATIONS }
    public record Recipe(String crop,Map<String,Integer> ingredients,String source,boolean immediateGrowth,boolean needsWater,boolean current) {
        public Recipe { ingredients=Map.copyOf(ingredients); }
    }
    public record ObservedPattern(String crop,Cell emptyTarget,Set<Cell> ingredientCells,String guideEvidence) {
        public ObservedPattern { ingredientCells=Set.copyOf(ingredientCells); }
    }
    public record Validation(Result result,String reason) {}
    public static final Recipe DUSTGRAIN=new Recipe("Dustgrain",Map.of("Wheat",4),
        "https://hypixel.net/threads/hypixel-skyblock-0-24-the-greenhouse.6027542/",true,false,false);
    public List<Recipe> documentedRecipes(){return List.of(DUSTGRAIN);}
    public Validation validateObserved(GreenhouseGuide.Recipe recipe,Snapshot s,ObservedPattern pattern,long now,long maxAge){
        if(recipe==null)return new Validation(Result.UNKNOWN_RECIPE,"Current Guide counts have not been observed");
        if(s==null||s.knowledge(now,maxAge)!=Knowledge.KNOWN||!recipe.context().equals(s.context())||now<recipe.observedAt()||now-recipe.observedAt()>maxAge)
            return new Validation(Result.STALE_OBSERVATIONS,"Refresh Guide and plant observations for this profile/plot");
        if(pattern==null||pattern.emptyTarget()==null||!Objects.equals(pattern.crop(),recipe.crop())||recipe.offsets().isEmpty()||recipe.offsets().size()>100||pattern.ingredientCells().size()>100||recipe.source()==null||recipe.source().isBlank()||pattern.guideEvidence()==null||pattern.guideEvidence().isBlank())return new Validation(Result.UNKNOWN_PROXIMITY,"Guide proximity/offset evidence is missing");
        for(Cell offset:recipe.offsets())if(offset==null||Math.abs((long)offset.x())>16||Math.abs((long)offset.y())>16||Math.abs((long)offset.z())>16||offset.equals(new Cell(0,0,0)))return new Validation(Result.UNKNOWN_PROXIMITY,"Guide offset exceeds bounded observed geometry");
        Plant target=s.plants().get(pattern.emptyTarget());if(target==null||!target.identified()||target.state()!=PlantState.EMPTY)return new Validation(Result.NO_EMPTY_TARGET,"Guide target must be observed empty soil");
        var offsets=new HashSet<Cell>();var counts=new HashMap<String,Integer>();
        for(Cell cell:pattern.ingredientCells()){
            if(cell.equals(pattern.emptyTarget()))return new Validation(Result.NO_EMPTY_TARGET,"Target cannot also be an ingredient");
            try{offsets.add(new Cell(Math.subtractExact(cell.x(),pattern.emptyTarget().x()),Math.subtractExact(cell.y(),pattern.emptyTarget().y()),Math.subtractExact(cell.z(),pattern.emptyTarget().z())));}
            catch(ArithmeticException overflow){return new Validation(Result.UNKNOWN_PROXIMITY,"World coordinate difference overflow; refresh actual nearby cells");}
            Plant p=s.plants().get(cell);if(p==null||!p.identified()||(p.state()==PlantState.EMPTY||p.state()==PlantState.DEAD))return new Validation(Result.MISSING_INGREDIENTS,"Guide ingredient cell is unknown or empty");counts.merge(p.crop(),1,Integer::sum);
        }
        if(!offsets.equals(recipe.offsets()))return new Validation(Result.UNKNOWN_PROXIMITY,"Selected cells do not match the observed Guide offsets");
        if(!counts.equals(recipe.ingredients()))return new Validation(Result.MISSING_INGREDIENTS,"Selected crop counts do not match the current observed Guide");
        return new Validation(Result.VALID_OBSERVED_PATTERN,"Current observed Guide counts and offsets match; mutation outcome remains probabilistic");
    }
    public Validation validate(String desired,Snapshot s,ObservedPattern pattern,long now,long maxAge){
        var recipe=documentedRecipes().stream().filter(r->r.crop().equals(desired)).findFirst();
        if(recipe.isEmpty())return new Validation(Result.UNKNOWN_RECIPE,"No complete sourced ingredient recipe; inspect the Greenhouse guide");
        if(!recipe.get().current())return new Validation(Result.UNKNOWN_RECIPE,"Historical four-Wheat example; February recipes changed and current captured Guide is required");
        if(s==null||s.knowledge(now,maxAge)!=Knowledge.KNOWN)return new Validation(Result.STALE_OBSERVATIONS,"Refresh scoped Greenhouse cells");
        if(pattern==null||!desired.equals(pattern.crop())||pattern.guideEvidence()==null||pattern.guideEvidence().isBlank())
            return new Validation(Result.UNKNOWN_PROXIMITY,"Four nearby Wheat are documented; exact proximity requires observed guide evidence");
        Plant target=s.plants().get(pattern.emptyTarget());
        if(target==null||!target.identified()||target.state()!=PlantState.EMPTY)return new Validation(Result.NO_EMPTY_TARGET,"Mutation requires observed empty soil");
        if(pattern.ingredientCells().contains(pattern.emptyTarget()))return new Validation(Result.NO_EMPTY_TARGET,"Target cannot also be an ingredient");
        var actual=new HashMap<String,Integer>();
        for(Cell cell:pattern.ingredientCells()){Plant p=s.plants().get(cell);if(p==null||!p.identified()||(p.state()==PlantState.EMPTY||p.state()==PlantState.DEAD))
            return new Validation(Result.MISSING_INGREDIENTS,"Guide ingredient cell is unknown or empty");actual.merge(p.crop(),1,Integer::sum);}
        if(recipe.get().current()&&!actual.equals(recipe.get().ingredients()))return new Validation(Result.MISSING_INGREDIENTS,"Observed ingredient counts do not match documented combination");
        if(!recipe.get().current()&&(actual.isEmpty()||!actual.keySet().equals(recipe.get().ingredients().keySet())))return new Validation(Result.MISSING_INGREDIENTS,"Observed guide needs identified source crops; historical counts are not current requirements");
        return new Validation(Result.VALID_OBSERVED_PATTERN,"Observed guide pattern matches ingredients; mutation chance and growth-cycle timing remain probabilistic");
    }
}
