package dev.veltraph.client.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Differentially exercises the real manager against its original allocating expression. No native proof. */
class ModuleManagerInputOwnerFallbackTest {
    private static final class Probe implements Module {
        final String name;final List<String> trace;
        boolean enabled,passive,owns;boolean logging;
        Runnable enabledHook,passiveHook,ownsHook,idHook;
        Probe(String name,boolean enabled,boolean passive,boolean owns,List<String> trace){this.name=name;this.enabled=enabled;this.passive=passive;this.owns=owns;this.trace=trace;}
        public String id(){if(logging)trace.add("id:"+name);if(idHook!=null)idHook.run();return name;}
        public String displayName(){return name;}public ModuleCategory category(){return ModuleCategory.INTERNAL;}
        public boolean isEnabled(){trace.add("enabled:"+name);if(enabledHook!=null)enabledHook.run();return enabled;}
        public boolean isPassiveObserver(){trace.add("passive:"+name);if(passiveHook!=null)passiveHook.run();return passive;}
        public boolean ownsGameplayInput(){trace.add("owns:"+name);if(ownsHook!=null)ownsHook.run();return owns;}
        public void setEnabled(boolean value){enabled=value;}public void onEnable(){}public void onDisable(){}public void onTick(){}
        public MacroState state(){return enabled?MacroState.RUNNING:MacroState.STOPPED;}
    }
    private static final class Fixture {
        final ModuleManager manager=new ModuleManager();final List<String> trace=new ArrayList<>();final boolean optimized;
        Supplier<Module> lease=()->null;
        Fixture(boolean optimized){this.optimized=optimized;manager.leasedInputOwner(this::leased);}
        Module leased(){trace.add("leased");return lease.get();}
        Probe add(String name,boolean enabled,boolean passive,boolean owns){var p=new Probe(name,enabled,passive,owns,trace);manager.register(p);p.logging=true;return p;}
        Optional<Module> read(){
            if(optimized)return manager.inputOwner();
            Module leased=leased();
            if(leased!=null&&manager.get(leased.id()).orElse(null)==leased&&leased.isEnabled()&&!leased.isPassiveObserver()&&leased.ownsGameplayInput())return Optional.of(leased);
            return manager.enabledModules().stream().filter(module->!module.isPassiveObserver()&&module.ownsGameplayInput()).findFirst();
        }
        String name(){return read().map(module->((Probe)module).name).orElse("NONE");}
    }
    private interface Scenario {String run(Fixture f);}
    private record Outcome(String result,Class<?> failure,List<String> trace){}
    private static Outcome execute(boolean optimized,Scenario s){var f=new Fixture(optimized);try{return new Outcome(s.run(f),null,List.copyOf(f.trace));}catch(Throwable t){return new Outcome(null,t.getClass(),List.copyOf(f.trace));}}
    private static Outcome compare(Scenario s){var original=execute(false,s);var actual=execute(true,s);assertEquals(original,actual);return actual;}

    @Test void emptyFallbackStillReturnsEmpty(){var o=compare(Fixture::name);assertEquals("NONE",o.result());assertEquals(List.of("leased"),o.trace());}
    @Test void completesAllEnabledReadsThenStopsRolesAtFirstOwner(){var o=compare(f->{f.add("a",true,false,true);var b=f.add("b",true,false,true);b.passiveHook=()->{throw new AssertionError("Role after first owner");};f.add("c",false,false,true);return f.name();});assertEquals("a",o.result());assertEquals(List.of("leased","enabled:a","enabled:b","enabled:c","passive:a","owns:a"),o.trace());}
    @Test void disabledAndPassiveShortCircuitOwnsExactly(){var o=compare(f->{var d=f.add("disabled",false,false,true);d.passiveHook=()->{throw new AssertionError();};var p=f.add("passive",true,true,true);p.ownsHook=()->{throw new AssertionError();};f.add("idle",true,false,false);return f.name();});assertEquals("NONE",o.result());assertEquals(List.of("leased","enabled:disabled","enabled:passive","enabled:idle","passive:passive","passive:idle","owns:idle"),o.trace());}
    @Test void genuineLeasedFastPathNeverBorrowsOrEnumeratesFallback(){var o=compare(f->{f.add("other",true,false,true).enabledHook=()->{throw new AssertionError();};var p=f.add("lease",true,false,true);f.lease=()->p;return f.name();});assertEquals("lease",o.result());assertEquals(List.of("leased","id:lease","enabled:lease","passive:lease","owns:lease"),o.trace());}
    @Test void unregisteredLeaseReadsOnlyIdBeforeFallback(){var o=compare(f->{var foreign=new Probe("foreign",true,false,true,f.trace);foreign.logging=true;foreign.enabledHook=()->{throw new AssertionError();};f.lease=()->foreign;f.add("owner",true,false,true);return f.name();});assertEquals("owner",o.result());assertEquals(List.of("leased","id:foreign","enabled:owner","passive:owner","owns:owner"),o.trace());}
    @Test void rejectedLeaseRetainsItsGetterPrefixThenFreshFallback(){var o=compare(f->{var p=f.add("lease",true,true,true);p.ownsHook=()->{throw new AssertionError();};f.add("owner",true,false,true);f.lease=()->p;return f.name();});assertEquals("owner",o.result());assertEquals(List.of("leased","id:lease","enabled:lease","passive:lease","enabled:lease","enabled:owner","passive:lease","passive:owner","owns:owner"),o.trace());}
    @Test void disabledLeasedGetterIsReadAgainByCompleteFallback(){var o=compare(f->{var p=f.add("lease",false,false,true);f.lease=()->p;f.add("owner",true,false,true);return f.name();});assertEquals("owner",o.result());assertEquals(List.of("leased","id:lease","enabled:lease","enabled:lease","enabled:owner","passive:owner","owns:owner"),o.trace());}
    @Test void enabledGetterReentryGetsIndependentScratchDepth(){var o=compare(f->{var a=f.add("a",true,false,true);f.add("b",true,false,true);var entered=new AtomicBoolean();a.enabledHook=()->{if(entered.compareAndSet(false,true))f.trace.add("nested:"+f.name());};return f.name();});assertEquals("a",o.result());assertTrue(o.trace().contains("nested:a"));}
    @Test void roleReentryCannotReplaceOuterCollectedReferences(){var o=compare(f->{var a=f.add("a",true,true,false);var b=f.add("b",true,false,true);var entered=new AtomicBoolean();a.passiveHook=()->{if(entered.compareAndSet(false,true)){a.enabled=false;f.trace.add("nested:"+f.name());b.enabled=false;}};return f.name()+"/"+f.name();});assertEquals("b/NONE",o.result());assertTrue(o.trace().contains("nested:b"));}
    @Test void supplierReentryCompletesBeforeOuterBorrow(){var o=compare(f->{f.add("a",true,false,true);var entered=new AtomicBoolean();f.lease=()->{if(entered.compareAndSet(false,true))f.trace.add("nested:"+f.name());return null;};return f.name();});assertEquals("a",o.result());assertTrue(o.trace().contains("nested:a"));}
    @Test void enabledThrowReleasesScratchAndPreservesThrowPrefix(){var o=compare(f->{f.add("a",true,false,true);var b=f.add("b",true,false,true);b.enabledHook=()->{throw new IllegalStateException("enabled");};try{f.name();fail("Expected failure");}catch(IllegalStateException expected){f.trace.add("caught");}b.enabledHook=null;return f.name();});assertEquals("a",o.result());assertEquals(List.of("leased","enabled:a","enabled:b","caught","leased","enabled:a","enabled:b","passive:a","owns:a"),o.trace());}
    @Test void roleThrowReleasesScratchBeforeColdAndHotRetry(){var o=compare(f->{var a=f.add("a",true,false,true);f.add("b",true,false,true);a.ownsHook=()->{throw new IllegalArgumentException("owns");};try{f.name();fail("Expected failure");}catch(IllegalArgumentException expected){f.trace.add("caught");}a.ownsHook=null;return f.name()+"/"+f.name();});assertEquals("a/a",o.result());assertTrue(o.trace().contains("caught"));}
    @Test void registerDuringEnabledEnumerationPreservesConcurrentModification(){var o=compare(f->{var a=f.add("a",true,false,true);f.add("b",true,false,true);a.enabledHook=()->f.add("late",true,false,true);return f.name();});assertEquals(java.util.ConcurrentModificationException.class,o.failure());}
    @Test void registerDuringRolePhaseDoesNotEnterCollectedListUntilNextRead(){var o=compare(f->{var a=f.add("a",true,true,false);f.add("b",true,false,false);var entered=new AtomicBoolean();a.passiveHook=()->{if(entered.compareAndSet(false,true))f.add("late",true,false,true);};return f.name()+"/"+f.name();});assertEquals("NONE/late",o.result());}
    @Test void mutableEnabledRoleAndOwnershipAreFreshOnEveryCall(){var o=compare(f->{var a=f.add("a",true,false,true);var b=f.add("b",true,false,true);String cold=f.name();a.enabled=false;String disabled=f.name();a.enabled=true;a.passive=true;String passive=f.name();a.passive=false;a.owns=false;String idle=f.name();a.owns=true;b.enabled=false;return cold+"/"+disabled+"/"+passive+"/"+idle+"/"+f.name();});assertEquals("a/b/b/b/a",o.result());}
    @Test void leasedSupplierExceptionHasNoFallbackGetters(){var o=compare(f->{f.add("a",true,false,true);f.lease=()->{throw new IllegalStateException("lease");};return f.name();});assertEquals(IllegalStateException.class,o.failure());assertEquals(List.of("leased"),o.trace());}
}
