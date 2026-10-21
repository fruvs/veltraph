package dev.veltraph.client.greenhouse;

import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.greenhouse.GreenhouseModel.*;
import static dev.veltraph.client.greenhouse.GreenhouseEnvironmentEvidence.*;
import static dev.veltraph.client.greenhouse.GreenhouseCombinations.*;

/** Explicitly authored private evidence ports; these tests do not bind original Hypixel captures. */
class GreenhouseEnvironmentEvidenceTest {
    static final Cell T=new Cell(0,70,0),L=new Cell(-1,70,0),R=new Cell(1,70,0),N=new Cell(0,70,-1),S=new Cell(0,70,1);
    static final Set<Cell> OFFSETS=Set.of(new Cell(-1,0,0),new Cell(1,0,0),new Cell(0,0,-1),new Cell(0,0,1));
    static final Context CONTEXT=new Context("p","plot","dimension");
    @BeforeAll static void nativeInventoryRegistry(){GreenhouseDiagnosticAssociationTest.nativeRegistry();}
    static final class Port implements Authority {
        boolean live=true,plant=true,diagnostic=true,environment=true,absence=true,geometry=true,effect=true;
        boolean revokeOnDiagnostic,revokeOnEnvironment,throwOnPlant;
        Frame expected;
        public boolean current(Frame f){return live&&f==expected;}
        public boolean geometryAssociated(Frame f,Footprint p,GreenhouseGuide.Recipe g){return geometry;}
        public boolean effectGeometryAssociated(Frame f,Footprint p){return geometry;}
        public boolean plantAssociated(Frame f,CellEvidence c){if(throwOnPlant)throw new IllegalStateException("lost native scope");return plant;}
        public boolean diagnosticAssociated(Frame f,CellEvidence c){if(revokeOnDiagnostic)live=false;return diagnostic;}
        public boolean environmentAssociated(Frame f,CellEvidence c){if(revokeOnEnvironment)live=false;return environment;}
        public boolean absenceAssociated(Frame f,CellEvidence c){return absence;}
        public boolean effectAssociated(Frame f,EffectLink l){return effect;}
    }
    record Case(GreenhouseCapturedMenus.Mutation recipe,GreenhouseGuide.Recipe guide,ObservedPattern selected,Footprint footprint,Frame frame,Port port) {
        Validation run(){return validate(recipe,guide,selected,footprint,frame,port,21,50);}
        Case cells(Map<Cell,CellEvidence> cells){var f=new Frame(frame.custody(),frame.observedAt(),frame.revision(),cells);port.expected=f;return new Case(recipe,guide,selected,footprint,f,port);}
        Case footprint(Footprint p){return new Case(recipe,guide,selected,p,frame,port);}
    }
    static Plant plant(String crop,PlantState state){return new Plant(crop,state,WaterState.UNKNOWN,1,Clock.unknown(),Clock.unknown(),Clock.unknown(),"exact-native-block","Authored private associated receipt");}
    static GreenhouseCapturedMenus.Diagnostics diag(String name,GreenhouseCapturedMenus.Status status,List<GreenhouseCapturedMenus.Effect> applies,List<GreenhouseCapturedMenus.Effect> receives){
        var source=new GreenhouseCapturedMenus.Source("Crop Diagnostics",3,4,11,"Authored exact private receipt; not live association");
        var def=new GreenhouseCapturedMenus.Definition(name,"minecraft:paper",GreenhouseCapturedMenus.PlantKind.BASE,"1x1",List.of("Farmland"),2,List.of(),source,4);
        return new GreenhouseCapturedMenus.Diagnostics(def,status,status==GreenhouseCapturedMenus.Status.DEAD?"DEAD":"Growing","",Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),OptionalInt.empty(),Optional.empty(),false,false,applies,receives,false,source);
    }
    static CellEvidence empty(Cell c,String surface){return new CellEvidence(c,Occupancy.EMPTY_SOIL,"minecraft:air","exact-native-block",surface,11,true,true,plant("",PlantState.EMPTY),null,Role.SELECTED_TARGET);}
    static CellEvidence live(Cell c,String crop){return new CellEvidence(c,Occupancy.LIVE_PLANT,"minecraft:wheat","exact-native-block","Farmland",11,true,true,plant(crop,PlantState.GROWING),null,Role.MUTATION_DONOR);}
    static CellEvidence dead(Cell c){return new CellEvidence(c,Occupancy.DEAD_PLANT,"minecraft:dead_bush","exact-native-block","Soul Sand",11,true,true,null,diag("Wheat",GreenhouseCapturedMenus.Status.DEAD,List.of(),List.of()),Role.MUTATION_DONOR);}
    static CellEvidence fire(Cell c){return new CellEvidence(c,Occupancy.FIRE,"minecraft:fire","native-fire-state","Soul Sand",11,true,true,null,null,Role.MUTATION_DONOR);}
    static Case scenario(String name){
        var recipe=new GreenhouseMenuLedger().mutation(name).orElseThrow();
        var cells=new HashMap<Cell,CellEvidence>();cells.put(T,empty(T,recipe.definition().surfaces().getFirst()));
        for(Cell c:List.of(L,R,N,S))cells.put(c,empty(c,"Farmland"));
        Set<Cell> donors=switch(name){case "Ashwreath","Witherbloom"->Set.of(L,R,N,S);case "Dustgrain"->Set.of(L,R);default->Set.of();};
        if(name.equals("Ashwreath")){cells.put(L,live(L,"Nether Wart"));cells.put(R,live(R,"Nether Wart"));cells.put(N,fire(N));cells.put(S,fire(S));}
        if(name.equals("Witherbloom"))for(var c:donors)cells.put(c,dead(c));
        if(name.equals("Dustgrain"))for(var c:donors)cells.put(c,live(c,"Wheat"));
        var offset=new HashSet<Cell>();for(var c:donors)offset.add(new Cell(c.x()-T.x(),0,c.z()-T.z()));
        var guide=new GreenhouseGuide.Recipe(name,recipe.requirements(),offset,CONTEXT,19,"Authored exact private world offsets");
        var pattern=new ObservedPattern(name,T,donors,"Explicit private selection");
        var footprint=new Footprint(CONTEXT,T,OFFSETS,19,"Explicit private complete world neighborhood; no default adjacency",true);
        var frame=new Frame(GreenhouseDiagnosticAssociationTest.custody(T),20,5,cells);var p=new Port();p.expected=frame;
        return new Case(recipe,guide,pattern,footprint,frame,p);
    }
    static CellEvidence edit(CellEvidence c,Occupancy occupancy,String id,String surface,long at,boolean loaded,boolean height,Plant p,GreenhouseCapturedMenus.Diagnostics d,Role role){
        return new CellEvidence(c.cell(),occupancy,id,c.fingerprint(),surface,at,loaded,height,p,d,role);
    }
    static Case replace(Case c,Cell at,CellEvidence value){var cells=new HashMap<>(c.frame.cells());cells.put(at,value);return c.cells(cells);}
    @Test void exactFireAndWartConditionsValidateWithoutCropAliases(){assertEquals(Result.VALID_OBSERVED_PATTERN,scenario("Ashwreath").run().result());}
    @Test void explicitDeadDonorsValidateWithoutWaterOrStageInference(){assertEquals(Result.VALID_OBSERVED_PATTERN,scenario("Witherbloom").run().result());}
    @Test void completeAssociatedAbsenceValidatesZeroAdjacent(){assertEquals(Result.VALID_OBSERVED_PATTERN,scenario("Lonelily").run().result());}
    @Test void ordinaryCapturedCountsStillValidateUnderTypedEvidence(){assertEquals(Result.VALID_OBSERVED_PATTERN,scenario("Dustgrain").run().result());}
    @Test void completeNeighborhoodCannotOmitOneCell(){var c=scenario("Lonelily");var cells=new HashMap<>(c.frame.cells());cells.remove(N);assertEquals(Result.UNKNOWN_PROXIMITY,c.cells(cells).run().result());}
    @Test void extraObservedCellCannotExpandTheSpecifiedFootprint(){var c=scenario("Lonelily");var cells=new HashMap<>(c.frame.cells());cells.put(new Cell(2,70,0),empty(new Cell(2,70,0),"Farmland"));assertEquals(Result.UNKNOWN_PROXIMITY,c.cells(cells).run().result());}
    @Test void unknownLoadedStateNeverBecomesAbsence(){var c=scenario("Lonelily");var v=c.frame.cells().get(N);assertEquals(Result.UNKNOWN_PROXIMITY,replace(c,N,edit(v,Occupancy.UNKNOWN,v.nativeBlockId(),v.surface(),11,true,true,null,null,v.role())).run().result());}
    @Test void unloadedOrOutOfBuildHeightFailsClosed(){for(int i=0;i<2;i++){var c=scenario("Lonelily");var v=c.frame.cells().get(N);assertEquals(Result.UNKNOWN_PROXIMITY,replace(c,N,edit(v,v.occupancy(),v.nativeBlockId(),v.surface(),11,i!=0,i!=1,v.plant(),null,v.role())).run().result());}}
    @Test void airOrKnownNoncropIsNotAutomaticAbsence(){var c=scenario("Lonelily");c.port.absence=false;assertEquals(Result.UNKNOWN_PROXIMITY,c.run().result());}
    @Test void explicitNoncropAbsenceMayBeKnownWithoutPretendingItIsSoil(){var c=scenario("Lonelily");var v=c.frame.cells().get(N);assertEquals(Result.VALID_OBSERVED_PATTERN,replace(c,N,edit(v,Occupancy.OTHER,"minecraft:stone","",11,true,true,null,null,Role.UNKNOWN)).run().result());}
    @Test void liveOrDeadUnselectedCropRefutesZeroAdjacent(){for(boolean dead:List.of(false,true)){var c=scenario("Lonelily");assertEquals(Result.UNKNOWN_PROXIMITY,replace(c,N,dead?dead(N):live(N,"Wheat")).run().result());}}
    @Test void fireNeedsExactNativeBlockAndIndependentEnvironmentAssociation(){var c=scenario("Ashwreath");var v=c.frame.cells().get(N);assertEquals(Result.UNKNOWN_PROXIMITY,replace(c,N,edit(v,v.occupancy(),"minecraft:soul_fire",v.surface(),11,true,true,null,null,v.role())).run().result());c=scenario("Ashwreath");c.port.environment=false;assertEquals(Result.UNKNOWN_PROXIMITY,c.run().result());}
    @Test void deadPlantLabelDoesNotProveDeath(){var c=scenario("Witherbloom");var v=c.frame.cells().get(N);assertEquals(Result.UNKNOWN_PROXIMITY,replace(c,N,edit(v,v.occupancy(),v.nativeBlockId(),v.surface(),11,true,true,null,diag("Wheat",GreenhouseCapturedMenus.Status.GROWING,List.of(),List.of()),v.role())).run().result());}
    @Test void deadDiagnosticsNeedAuthenticAssociation(){var c=scenario("Witherbloom");c.port.diagnostic=false;assertEquals(Result.UNKNOWN_PROXIMITY,c.run().result());}
    @Test void cropNamedFireIsNotEnvironmentalFire(){var c=scenario("Ashwreath");assertEquals(Result.MISSING_INGREDIENTS,replace(c,N,live(N,"Fire alias")).run().result());}
    @Test void actualTargetSurfaceIsRequired(){var c=scenario("Ashwreath");var v=c.frame.cells().get(T);assertEquals(Result.UNKNOWN_PROXIMITY,replace(c,T,edit(v,v.occupancy(),v.nativeBlockId(),"Farmland",11,true,true,v.plant(),null,v.role())).run().result());}
    @Test void donorRoleCannotBeHarvestTarget(){var c=scenario("Witherbloom");var v=c.frame.cells().get(N);assertEquals(Result.UNKNOWN_PROXIMITY,replace(c,N,edit(v,v.occupancy(),v.nativeBlockId(),v.surface(),11,true,true,null,v.diagnostic(),Role.SELECTED_TARGET)).run().result());}
    @Test void incompleteOrUnassociatedGeometryStaysUnknown(){var c=scenario("Lonelily");var p=c.footprint;assertEquals(Result.UNKNOWN_PROXIMITY,c.footprint(new Footprint(p.context(),p.target(),p.offsets(),p.observedAt(),p.source(),false)).run().result());c=scenario("Lonelily");c.port.geometry=false;assertEquals(Result.UNKNOWN_PROXIMITY,c.run().result());}
    @Test void noUniversalAdjacentRadiusOrMenuSpacingIsInstalled(){var c=scenario("Lonelily");var p=c.footprint;assertEquals(Result.UNKNOWN_PROXIMITY,c.footprint(new Footprint(p.context(),p.target(),Set.of(),19,"No geometry",true)).run().result());}
    @Test void foreignContextAndCoordinateOverflowStayUnknown(){var c=scenario("Lonelily");var p=c.footprint;assertEquals(Result.UNKNOWN_PROXIMITY,c.footprint(new Footprint(new Context("other","plot","dimension"),T,OFFSETS,19,"Private",true)).run().result());assertEquals(Result.UNKNOWN_PROXIMITY,c.footprint(new Footprint(CONTEXT,T,Set.of(new Cell(Integer.MIN_VALUE,0,0)),19,"Private",true)).run().result());}
    @Test void staleFutureAndSnapshotFutureCellsFailClosed(){for(long at:List.of(0L,21L,22L)){var c=scenario("Lonelily");var v=c.frame.cells().get(N);var r=replace(c,N,edit(v,v.occupancy(),v.nativeBlockId(),v.surface(),at,true,true,v.plant(),null,v.role()));if(at==0)assertEquals(Result.UNKNOWN_PROXIMITY,validate(r.recipe,r.guide,r.selected,r.footprint,r.frame,r.port,21,10).result());else assertEquals(Result.UNKNOWN_PROXIMITY,r.run().result());}}
    @Test void reservedEnvironmentalNamesCannotBeLivePlantIngredients(){var c=scenario("Ashwreath");assertEquals(Result.UNKNOWN_PROXIMITY,replace(c,N,live(N,"Fire")).run().result());}
    @Test void custodyRevocationAndCallbackExceptionNeverReturnValid(){var c=scenario("Ashwreath");c.port.revokeOnEnvironment=true;assertEquals(Result.UNKNOWN_PROXIMITY,c.run().result());c=scenario("Witherbloom");c.port.revokeOnDiagnostic=true;assertEquals(Result.UNKNOWN_PROXIMITY,c.run().result());c=scenario("Lonelily");c.port.throwOnPlant=true;assertEquals(Result.NO_EMPTY_TARGET,c.run().result());}
    @Test void originalLedgerOverloadStillCannotInferEnvironmentalEvidence(){var c=scenario("Lonelily");assertEquals(Result.UNKNOWN_PROXIMITY,new GreenhouseMenuLedger().validateAssociatedPattern(c.guide,null,c.selected,21,50).result());assertEquals(Result.VALID_OBSERVED_PATTERN,new GreenhouseMenuLedger().validateAssociatedEnvironment(c.guide,c.selected,c.footprint,c.frame,c.port,21,50).result());}
    static final GreenhouseCapturedMenus.Effect BOOST=new GreenhouseCapturedMenus.Effect("Harvest Boost",List.of("Authored observed +20% yield"));
    static Case effectCase(){var c=scenario("Lonelily");var cells=new HashMap<>(c.frame.cells());cells.put(L,new CellEvidence(L,Occupancy.LIVE_PLANT,"minecraft:wheat","exact-native-block","Farmland",11,true,true,plant("Wheat",PlantState.GROWING),diag("Wheat",GreenhouseCapturedMenus.Status.GROWING,List.of(BOOST),List.of()),Role.EFFECT_DONOR));cells.put(T,new CellEvidence(T,Occupancy.LIVE_PLANT,"minecraft:carrots","exact-native-block","Farmland",11,true,true,plant("Carrot",PlantState.GROWING),diag("Carrot",GreenhouseCapturedMenus.Status.GROWING,List.of(),List.of(BOOST)),Role.EFFECT_RECIPIENT));return c.cells(cells);}
    static EffectLink link(){return new EffectLink(L,T,BOOST,19,"Authored independently observed directed effect",true);}
    static EffectValidation effects(Case c){return validateEffect(link(),c.footprint,c.frame,c.port,21,50);}
    @Test void exactObservedEffectPreservesItsDonorWithoutHarvestPermission(){var c=effectCase();var r=effects(c);assertEquals(GreenhouseEnvironmentEvidence.Knowledge.OBSERVED,r.knowledge());assertEquals(Set.of(L),r.preservedDonors());assertTrue(r.reason().contains("no harvest permission"));}
    @Test void matchingEffectNamesAloneNeverInventDirectedRelationship(){var c=effectCase();c.port.effect=false;assertEquals(GreenhouseEnvironmentEvidence.Knowledge.UNKNOWN,effects(c).knowledge());}
    @Test void sameNameDifferentDescriptionAndDuplicateEffectsDoNotStack(){for(boolean duplicate:List.of(false,true)){var c=effectCase();var v=c.frame.cells().get(T);var received=duplicate?List.of(BOOST,BOOST):List.of(new GreenhouseCapturedMenus.Effect(BOOST.name(),List.of("Different observed value")));c=replace(c,T,edit(v,v.occupancy(),v.nativeBlockId(),v.surface(),11,true,true,v.plant(),diag("Carrot",GreenhouseCapturedMenus.Status.GROWING,List.of(),received),v.role()));assertEquals(GreenhouseEnvironmentEvidence.Knowledge.MISMATCH,effects(c).knowledge());assertTrue(effects(c).preservedDonors().isEmpty());}}
    @Test void missingRoleDeadEndpointAndRevocationRefuseEffectGraph(){var c=effectCase();var v=c.frame.cells().get(L);assertEquals(GreenhouseEnvironmentEvidence.Knowledge.UNKNOWN,effects(replace(c,L,edit(v,v.occupancy(),v.nativeBlockId(),v.surface(),11,true,true,v.plant(),v.diagnostic(),Role.SELECTED_TARGET))).knowledge());c=effectCase();assertEquals(GreenhouseEnvironmentEvidence.Knowledge.UNKNOWN,effects(replace(c,L,dead(L))).knowledge());c=effectCase();c.port.revokeOnDiagnostic=true;assertEquals(GreenhouseEnvironmentEvidence.Knowledge.UNKNOWN,effects(c).knowledge());}
}
