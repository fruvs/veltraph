package dev.veltraph.client.core;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual public Module API callbacks and source-pinned original loops; no SDK/Root permit modeled. */
final class ControllerScratchIndexedParityTest {
    enum Query { OWNER,COUNT,FIRST,UNIQUE }
    private static final class Probe implements Module {
        final String id;final List<String> trace;boolean enabled=true,passive,owns=true,warp;Runnable enabledHook,passiveHook,ownsHook,warpHook;
        String expectedRoleCaller;
        Probe(String id,List<String> trace){this.id=id;this.trace=trace;}
        public String id(){return id;}public String displayName(){return id;}public ModuleCategory category(){return ModuleCategory.INTERNAL;}
        public boolean isEnabled(){trace.add(id+":enabled");if(enabledHook!=null)enabledHook.run();return enabled;}
        public boolean isPassiveObserver(){trace.add(id+":passive");checkCaller();if(passiveHook!=null)passiveHook.run();return passive;}
        public boolean ownsGameplayInput(){trace.add(id+":owns");checkCaller();if(ownsHook!=null)ownsHook.run();return owns;}
        boolean warp(){trace.add(id+":warp");if(warpHook!=null)warpHook.run();return warp;}
        private void checkCaller(){if(expectedRoleCaller==null)return;var caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(s->s.skip(2).findFirst().orElseThrow());assertEquals(ModuleManager.class,caller.getDeclaringClass());assertEquals(expectedRoleCaller,caller.getMethodName());}
        public void setEnabled(boolean value){trace.add(id+":set:"+value);enabled=value;}public void onEnable(){}public void onDisable(){}public void onTick(){}public MacroState state(){return enabled?MacroState.RUNNING:MacroState.STOPPED;}
    }
    private static final class Fixture {
        final boolean actual;final ModuleManager manager=new ModuleManager();final ArrayList<String> trace=new ArrayList<>();
        Fixture(boolean actual){this.actual=actual;}
        Probe add(String id){var p=new Probe(id,trace);manager.register(p);return p;}
        Object read(Query q){
            if(actual)return switch(q){case OWNER->manager.inputOwner().orElse(null);case COUNT->manager.enabledControllerCount();case FIRST->manager.firstEnabledController();case UNIQUE->manager.scanUniqueGameplayOwner(m->((Probe)m).warp());};
            return switch(q){case OWNER->FrozenASControllerLoops.owner(new ArrayList<>(manager.enabledModules())).orElse(null);case COUNT->FrozenASControllerLoops.count(new ArrayList<>(manager.enabledModules()));case FIRST->FrozenASControllerLoops.first(new ArrayList<>(manager.enabledModules()));case UNIQUE->FrozenASControllerLoops.unique(new ArrayList<>(manager.all()),m->((Probe)m).warp());};
        }
    }
    private static Object normalized(Object value){if(value instanceof Module m)return m.id();if(value instanceof ModuleManager.UniqueOwnerScan s)return List.of(s.status().name(),s.owner()==null?"none":s.owner().id());return value;}
    private interface Scenario {Object apply(Fixture f,Query q);}
    private record Outcome(Object value,Class<?> error,List<String> trace){}
    private static Outcome outcome(boolean actual,Query q,Scenario scenario){var f=new Fixture(actual);try{return new Outcome(normalized(scenario.apply(f,q)),null,List.copyOf(f.trace));}catch(Throwable error){return new Outcome(null,error.getClass(),List.copyOf(f.trace));}}
    private static void compare(Query q,Scenario scenario){assertEquals(outcome(false,q,scenario),outcome(true,q,scenario));}
    @Test void allRoleCombinationsKeepOriginalResultsShortCircuitsAndDoubleReadOrder(){
        for(Query q:Query.values())for(int mask=0;mask<64;mask++){final int bits=mask;compare(q,(f,query)->{for(int i=0;i<2;i++){var p=f.add("p"+i);p.enabled=(bits&(1<<(i*3)))!=0;p.passive=(bits&(1<<(i*3+1)))!=0;p.owns=(bits&(1<<(i*3+2)))!=0;}return f.read(query);});}
    }
    @Test void originalCompleteEnabledCapturePrecedesAnyPassiveRoleGetter(){
        for(Query q:List.of(Query.OWNER,Query.COUNT,Query.FIRST)){var f=new Fixture(true);var a=f.add("a");var b=f.add("b");a.passive=true;f.read(q);assertEquals(List.of("a:enabled","b:enabled"),f.trace.subList(0,2));}
    }
    @Test void nestedQueriesOwnDifferentDepthAndOuterSnapshotSurvives(){
        for(Query q:Query.values())compare(q,(f,query)->{var first=f.add("first");var second=f.add("second");second.owns=false;int[] depth={0};first.passiveHook=()->{if(depth[0]++==0){f.trace.add("nestedBegin");f.manager.register(new Probe("added",f.trace));f.trace.add("nested:"+normalized(f.read(query)));f.trace.add("nestedEnd");}};return f.read(query);});
    }
    @Test void roleCallbackRegistrationIsOutsideOriginalCapturedMembership(){
        for(Query q:Query.values())compare(q,(f,query)->{var first=f.add("first");first.passiveHook=()->{first.passiveHook=null;f.trace.add("register");f.add("new");};var result=f.read(query);f.trace.add("secondRead");f.read(query);return result;});
    }
    @Test void registryMutationDuringEnabledCaptureRetainsMapFailFastAndClearsScratch(){
        for(Query q:List.of(Query.OWNER,Query.COUNT,Query.FIRST))compare(q,(f,query)->{var a=f.add("a");f.add("b");a.enabledHook=()->{a.enabledHook=null;f.trace.add("register");f.add("new");};return f.read(query);});
        var f=new Fixture(true);var a=f.add("a");f.add("b");a.enabledHook=()->{a.enabledHook=null;f.add("new");};assertThrows(ConcurrentModificationException.class,()->f.read(Query.COUNT));assertScratchClear(f.manager);assertEquals(3,f.read(Query.COUNT));assertScratchClear(f.manager);
    }
    @Test void stopDuringRoleCallbackPreservesLaterReadsAndOriginalResult(){
        for(Query q:Query.values())compare(q,(f,query)->{var a=f.add("a");f.add("b");a.passiveHook=()->{a.passiveHook=null;f.trace.add("stopBegin");f.manager.stopAll();f.trace.add("stopEnd");};return f.read(query);});
    }
    @Test void eachUncheckedCallbackFaultRetainsExactObjectAndFinallyDepth(){
        for(Query q:Query.values())for(String stage:List.of("enabled","passive","owns","warp"))for(Throwable error:List.of(new IllegalStateException(stage),new AssertionError(stage))){
            List<String> before=null;for(boolean actual:new boolean[]{false,true}){var f=new Fixture(actual);var p=f.add("p");Runnable fail=()->{if(error instanceof RuntimeException e)throw e;throw (Error)error;};
                if(stage.equals("enabled"))p.enabledHook=fail;if(stage.equals("passive"))p.passiveHook=fail;if(stage.equals("owns"))p.ownsHook=fail;if(stage.equals("warp"))p.warpHook=fail;
                boolean reached=stage.equals("enabled")||stage.equals("passive")||stage.equals("owns")&&(q==Query.OWNER||q==Query.UNIQUE)||stage.equals("warp")&&q==Query.UNIQUE;
                if(reached)assertSame(error,assertThrows(Throwable.class,()->f.read(q)));else f.read(q);
                if(before==null)before=List.copyOf(f.trace);else assertEquals(before,f.trace);if(actual)assertScratchClear(f.manager);}}
    }
    @Test void actualRoleCallbacksRetainOriginalModuleManagerCallerMethods(){
        for(Query q:Query.values()){var f=new Fixture(true);var p=f.add("p");p.expectedRoleCaller=switch(q){case OWNER->"inputOwner";case COUNT->"enabledControllerCount";case FIRST->"firstEnabledController";case UNIQUE->"scanUniqueGameplayOwner";};f.read(q);assertScratchClear(f.manager);}
    }
    @Test void leasedSupplierNestedQueryRunsBeforeOuterBorrowAndKeepsFreshRoleReads(){
        var f=new Fixture(true);var p=f.add("p");f.manager.leasedInputOwner(()->{f.trace.add("leaseBegin");assertEquals(1,f.manager.enabledControllerCount());f.trace.add("leaseEnd");return null;});assertSame(p,f.manager.inputOwner().orElseThrow());assertEquals(List.of("leaseBegin","p:enabled","p:passive","leaseEnd","p:enabled","p:passive","p:owns"),f.trace);assertScratchClear(f.manager);
    }
    @Test void originalFullNullValidationStillPrecedesEveryUniqueRoleCallback(){
        var f=new Fixture(true);f.add("before");registry(f.manager).put("bad",null);f.add("after");assertThrows(NullPointerException.class,()->f.manager.scanUniqueGameplayOwner(m->false));assertTrue(f.trace.isEmpty());assertScratchClear(f.manager);
    }
    @Test void iteratorFailFastRemainsReachableOnlyOnRegistryOrOutOfDomainScratchMutation(){
        var modules=new ArrayList<Module>();var f=new Fixture(true);var p=f.add("p");modules.add(p);p.passiveHook=()->modules.clear();assertThrows(ConcurrentModificationException.class,()->FrozenASControllerLoops.count(modules));
        // This control demonstrates the excluded mutable-list domain; production never exposes that list.
        assertScratchClear(f.manager);
    }
    @SuppressWarnings("unchecked") private static Map<String,Module> registry(ModuleManager m){try{Field field=ModuleManager.class.getDeclaredField("byId");field.setAccessible(true);return (Map<String,Module>)field.get(m);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static void assertScratchClear(ModuleManager m){try{Field field=ModuleManager.class.getDeclaredField("controllerScratch");field.setAccessible(true);Object scratch=((ThreadLocal<?>)field.get(m)).get();Field depth=scratch.getClass().getDeclaredField("depth");depth.setAccessible(true);assertEquals(0,depth.getInt(scratch));Field lists=scratch.getClass().getDeclaredField("lists");lists.setAccessible(true);for(Object list:(List<?>)lists.get(scratch))assertTrue(((List<?>)list).isEmpty());}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
}
