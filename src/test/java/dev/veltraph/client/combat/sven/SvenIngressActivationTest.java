package dev.veltraph.client.combat.sven;

import dev.veltraph.client.core.*;
import dev.veltraph.client.game.*;
import dev.veltraph.client.testutil.FakeChatService;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real common activation guard/manager with the exact parent policy; no Minecraft surrogate gate. */
class SvenIngressActivationTest {
    static final String BINDING="""
        {"schema":1,"profile":"DARK_OAK","warpCommand":"private_sven_warp","travelToolLoreToken":"Private travel tool","operationTimeoutMs":10000,"modifyJumpBoost":false}
        """;
    private static class ParentContract extends AbstractModule {
        String binding=BINDING;SvenCaveProfile selected=SvenCaveProfile.DARK_OAK;
        final FixedGameContextService context;int checks,starts;boolean preparing=true;
        ParentContract(FixedGameContextService context){super("sven_test_parent","Sven source parent",ModuleCategory.COMBAT);this.context=context;}
        public Optional<GameArea> requiredArea(){return Optional.of(GameArea.THE_PARK);}
        public boolean requiresSkyBlock(){return true;}
        // No @Override so the red compiles against the unchanged pre-hook common interface.
        public Set<GameArea> ingressStartAreas(){return SvenIngressActivation.startAreas(binding,selected);}
        public Optional<String> preStartCheck(){checks++;return SvenIngressActivation.readiness(context.current(),binding,selected);}
        public void onEnable(){starts++;}
        public boolean hasTravelSetupPhase(){return preparing;}
    }
    private record Rig(FixedGameContextService context,ModuleManager manager,ModuleActivationGuard guard,ParentContract parent){}
    private static GameContext sky(GameArea area){return new GameContext(true,true,area);}
    private static Rig rig(GameContext context){
        var service=new FixedGameContextService(context);var manager=new ModuleManager();var parent=new ParentContract(service);manager.register(parent);
        return new Rig(service,manager,new ModuleActivationGuard(manager,service,new FakeChatService()),parent);
    }
    @Test void gardenAndIslandReachNativePreStartThroughTheActualGuard(){
        for(var area:List.of(GameArea.GARDEN,GameArea.ISLAND)){
            var rig=rig(sky(area));assertTrue(rig.guard.canStart(rig.parent));assertTrue(rig.guard.start(rig.parent));
            assertEquals(1,rig.parent.checks);assertEquals(1,rig.parent.starts);assertSame(rig.parent,rig.manager.inputOwner().orElseThrow());
            assertEquals(Optional.of(GameArea.THE_PARK),rig.parent.requiredArea());
        }
    }
    @Test void unknownLimboLobbyNonSkyBlockAndForeignServerNeverReachPreStart(){
        var contexts=new ArrayList<GameContext>();
        for(var area:GameArea.values())if(!area.isSkyBlock())contexts.add(sky(area));
        contexts.add(new GameContext(true,false,GameArea.GARDEN));contexts.add(new GameContext(false,true,GameArea.ISLAND));contexts.add(GameContext.NONE);
        for(var context:contexts){var rig=rig(context);assertFalse(rig.guard.canStart(rig.parent),context.toString());assertFalse(rig.guard.start(rig.parent));assertEquals(0,rig.parent.checks);assertEquals(0,rig.parent.starts);}
    }
    @Test void wrongSelectedProfileBlankMissingWarpAndMalformedConfigDoNotAdmitSourceArea(){
        for(var binding:List.of("", "{}",BINDING.replace("DARK_OAK","JUNGLE"),BINDING.replace("private_sven_warp",""),BINDING.replace("10000","NaN"))){
            var rig=rig(sky(GameArea.GARDEN));rig.parent.binding=binding;
            assertFalse(rig.guard.canStart(rig.parent));assertFalse(rig.guard.start(rig.parent));assertEquals(0,rig.parent.checks);assertEquals(0,rig.parent.starts);
        }
    }
    @Test void parkStartStillRequiresBoundSourceProfileBeforeEnable(){
        var rig=rig(sky(GameArea.THE_PARK));rig.parent.binding="";
        assertTrue(rig.guard.canStart(rig.parent));assertFalse(rig.guard.start(rig.parent));assertEquals(1,rig.parent.checks);assertEquals(0,rig.parent.starts);
    }
    @Test void sourceAdmissionIsDisabledStartOnlyAndCombatRetainsPark(){
        var rig=rig(sky(GameArea.GARDEN));assertTrue(rig.guard.start(rig.parent));
        assertTrue(ModuleActivationGuard.evaluate(rig.parent,rig.context.current()).isPresent());assertFalse(rig.guard.start(rig.parent));assertEquals(1,rig.parent.starts);
        rig.parent.preparing=false;rig.guard.enforceRunning();assertFalse(rig.parent.isEnabled());
        rig.context.set(sky(GameArea.THE_PARK));assertTrue(rig.guard.start(rig.parent));rig.guard.enforceRunning();assertTrue(rig.parent.isEnabled());
    }
    @Test void ordinaryParkCombatChildHasNoSourceAdmission(){
        var rig=rig(sky(GameArea.GARDEN));var child=new AbstractModule("sven_owned_child","Sven combat child",ModuleCategory.COMBAT){
            public Optional<GameArea> requiredArea(){return Optional.of(GameArea.THE_PARK);}public boolean requiresSkyBlock(){return true;}
        };rig.manager.register(child);assertFalse(rig.guard.canStart(child));assertFalse(rig.guard.start(child));assertFalse(child.isEnabled());
        rig.context.set(sky(GameArea.THE_PARK));assertTrue(rig.guard.start(child));rig.context.set(sky(GameArea.ISLAND));rig.guard.enforceRunning();assertFalse(child.isEnabled());
    }
    @Test void sameStartRechecksProfileAfterSharedReadinessChangesSettings(){
        var rig=rig(sky(GameArea.GARDEN));rig.guard.standardReadiness(module->{rig.parent.selected=SvenCaveProfile.JUNGLE;return Optional.empty();});
        assertFalse(rig.guard.start(rig.parent));assertEquals(1,rig.parent.checks);assertEquals(0,rig.parent.starts);
    }
    @Test void ordinaryOwnershipConflictAndExternalControlStillDeny(){
        var rig=rig(sky(GameArea.GARDEN));var other=new AbstractModule("other","Other controller",ModuleCategory.COMBAT){};rig.manager.register(other);other.setEnabled(true);
        assertFalse(rig.guard.start(rig.parent));assertEquals(0,rig.parent.checks);rig.manager.disable(other.id());
        rig.guard.externalControlBlocker(module->Optional.of("Existing safety owner"));assertFalse(rig.guard.start(rig.parent));assertEquals(0,rig.parent.checks);
    }
    @Test void admissionSetIsFiniteImmutableAndOnlyKnownSkyBlock(){
        var areas=SvenIngressActivation.startAreas(BINDING,SvenCaveProfile.DARK_OAK);
        assertFalse(areas.isEmpty());assertTrue(areas.stream().allMatch(GameArea::isSkyBlock));assertFalse(areas.contains(GameArea.UNKNOWN));assertFalse(areas.contains(GameArea.LIMBO));
        assertThrows(UnsupportedOperationException.class,()->areas.add(GameArea.LIMBO));
        assertTrue(SvenIngressActivation.startAreas(BINDING,SvenCaveProfile.JUNGLE).isEmpty());
    }
}
