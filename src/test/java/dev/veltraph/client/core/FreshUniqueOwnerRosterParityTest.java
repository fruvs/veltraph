package dev.veltraph.client.core;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independent old Root loop over public all() snapshots. Pure module callbacks only; this
 * supplies no Minecraft/Root provider, receipt, world/current or native authority evidence. */
class FreshUniqueOwnerRosterParityTest {
    private static final class Probe implements Module {
        final String id;final List<String> trace;boolean enabled=true,passive,owns=true,warp;int kind;
        Runnable enabledHook,passiveHook,ownsHook,warpHook;
        Probe(String id,List<String> trace){this.id=id;this.trace=trace;}
        public String id(){return id;}public String displayName(){return id;}public ModuleCategory category(){return ModuleCategory.values()[0];}
        public boolean isEnabled(){trace.add(id+":enabled");if(enabledHook!=null)enabledHook.run();return enabled;}
        public boolean isPassiveObserver(){trace.add(id+":passive");if(passiveHook!=null)passiveHook.run();return passive;}
        public boolean ownsGameplayInput(){trace.add(id+":owns");if(ownsHook!=null)ownsHook.run();return owns;}
        boolean warp(){trace.add(id+":warp:"+kind);if(warpHook!=null)warpHook.run();return warp;}
        public void setEnabled(boolean value){enabled=value;}public void onEnable(){}public void onDisable(){}public void onTick(){}public MacroState state(){return enabled?MacroState.RUNNING:MacroState.STOPPED;}
    }
    private record Before(ModuleManager.UniqueOwnerStatus status,Module owner) {}
    /** Exact old per-module role/second-owner/warp ordering; all() is unchanged production API. */
    private static Before original(ModuleManager manager,Predicate<Module> warp){
        Module owner=null;
        for(var module:manager.all()){
            if(!module.isEnabled()||module.isPassiveObserver()||!module.ownsGameplayInput())continue;
            if(owner!=null)return new Before(ModuleManager.UniqueOwnerStatus.AMBIGUOUS,null);
            if(warp.test(module))return new Before(ModuleManager.UniqueOwnerStatus.WARP_REFUSED,null);
            owner=module;
        }
        return new Before(owner==null?ModuleManager.UniqueOwnerStatus.NONE:ModuleManager.UniqueOwnerStatus.OWNER,owner);
    }
    private static Predicate<Module> warp(){return module->((Probe)module).warp();}
    private static String owner(Module module){return module==null?null:module.id();}
    private static void result(Before a,ModuleManager.UniqueOwnerScan b){assertEquals(a.status,b.status());assertEquals(owner(a.owner),owner(b.owner()));}
    private static void rootObserver(Before a,List<String> trace){if(a.status==ModuleManager.UniqueOwnerStatus.NONE)trace.add("noOwner");}
    private static void rootObserver(ModuleManager.UniqueOwnerScan a,List<String> trace){if(a.status()==ModuleManager.UniqueOwnerStatus.NONE)trace.add("noOwner");}
    @Test void allFalseShortCircuitRoleCombinationsKeepOriginalCallbacksAndStatus(){
        for(int mask=0;mask<64;mask++){
            var ta=new ArrayList<String>();var tb=new ArrayList<String>();var a=new ModuleManager();var b=new ModuleManager();
            for(int i=0;i<2;i++){var x=new Probe("m"+i,ta);var z=new Probe("m"+i,tb);x.enabled=z.enabled=(mask&(1<<(i*3)))!=0;x.passive=z.passive=(mask&(1<<(i*3+1)))!=0;x.owns=z.owns=(mask&(1<<(i*3+2)))!=0;a.register(x);b.register(z);}
            var old=original(a,warp());var now=b.scanUniqueGameplayOwner(warp());result(old,now);rootObserver(old,ta);rootObserver(now,tb);assertEquals(ta,tb);
        }
    }
    @Test void noneOwnerAmbiguousAndEachWarpRefusalInvokeNoOwnerOnlyOriginalNonePath(){
        for(int mode=0;mode<6;mode++){
            var ta=new ArrayList<String>();var tb=new ArrayList<String>();var a=new ModuleManager();var b=new ModuleManager();
            for(int i=0;i<2;i++){var x=new Probe("m"+i,ta);var z=new Probe("m"+i,tb);x.enabled=z.enabled=mode!=0&&(i==0||mode==2);x.kind=z.kind=Math.max(0,mode-3);x.warp=z.warp=mode>=3;a.register(x);b.register(z);}
            var old=original(a,warp());var now=b.scanUniqueGameplayOwner(warp());result(old,now);rootObserver(old,ta);rootObserver(now,tb);assertEquals(ta,tb);assertEquals(mode==0,ta.contains("noOwner"));
        }
    }
    @Test void registrationDuringGetterIsNotAddedToOriginalCapturedMembership(){
        var ta=new ArrayList<String>();var tb=new ArrayList<String>();var a=new ModuleManager();var b=new ModuleManager();var x=new Probe("first",ta);var z=new Probe("first",tb);
        x.enabledHook=()->{ta.add("register:new");a.register(new Probe("new",ta));x.enabledHook=null;};z.enabledHook=()->{tb.add("register:new");b.register(new Probe("new",tb));z.enabledHook=null;};a.register(x);b.register(z);
        result(original(a,warp()),b.scanUniqueGameplayOwner(warp()));assertEquals(ta,tb);assertFalse(ta.contains("new:enabled"));
        ta.clear();tb.clear();result(original(a,warp()),b.scanUniqueGameplayOwner(warp()));assertEquals(ta,tb);assertTrue(ta.contains("new:enabled"));
    }
    @Test void roleMutationOfLaterAndEarlierReferencesRetainsOriginalSnapshotAndGetterOrder(){
        var ta=new ArrayList<String>();var tb=new ArrayList<String>();var a=new ModuleManager();var b=new ModuleManager();var x=new Probe("first",ta);var z=new Probe("first",tb);var xa=new Probe("second",ta);var za=new Probe("second",tb);xa.enabled=za.enabled=false;
        x.ownsHook=()->{ta.add("mutate");x.enabled=false;xa.enabled=true;};z.ownsHook=()->{tb.add("mutate");z.enabled=false;za.enabled=true;};a.register(x);a.register(xa);b.register(z);b.register(za);
        result(original(a,warp()),b.scanUniqueGameplayOwner(warp()));assertEquals(ta,tb);
    }
    @Test void nestedScanOwnsCompleteFreshRosterAndDoesNotOverwriteOuterSnapshot(){
        var ta=new ArrayList<String>();var tb=new ArrayList<String>();var a=new ModuleManager();var b=new ModuleManager();var x=new Probe("outer",ta);var z=new Probe("outer",tb);int[] da={0},db={0};a.register(x);b.register(z);
        x.ownsHook=()->{if(da[0]++==0){ta.add("nestedBegin");a.register(new Probe("later",ta));var nested=original(a,warp());ta.add("nested:"+nested.status);ta.add("nestedEnd");}};
        z.ownsHook=()->{if(db[0]++==0){tb.add("nestedBegin");b.register(new Probe("later",tb));var nested=b.scanUniqueGameplayOwner(warp());tb.add("nested:"+nested.status());tb.add("nestedEnd");}};
        result(original(a,warp()),b.scanUniqueGameplayOwner(warp()));assertEquals(ta,tb);assertEquals(ModuleManager.UniqueOwnerStatus.WARP_REFUSED,b.scanUniqueGameplayOwner(module->true).status());assertScratchClear(b);
    }
    @Test void everyGetterAndWarpErrorRetainsExactErrorAndNoLaterCallbacks(){
        for(int field=0;field<4;field++){
            var ta=new ArrayList<String>();var tb=new ArrayList<String>();var a=new ModuleManager();var b=new ModuleManager();var x=new Probe("m",ta);var z=new Probe("m",tb);Error error=new AssertionError("callback "+field);Runnable bad=()->{throw error;};
            switch(field){case 0-> {x.enabledHook=z.enabledHook=bad;}case 1-> {x.passiveHook=z.passiveHook=bad;}case 2-> {x.ownsHook=z.ownsHook=bad;}case 3-> {x.warpHook=z.warpHook=bad;}}
            a.register(x);b.register(z);assertSame(error,assertThrows(Error.class,()->original(a,warp())));assertSame(error,assertThrows(Error.class,()->b.scanUniqueGameplayOwner(warp())));assertEquals(ta,tb);assertScratchClear(b);
        }
    }
    @SuppressWarnings("unchecked") private static Map<String,Module> registry(ModuleManager m){try{var f=ModuleManager.class.getDeclaredField("byId");f.setAccessible(true);return (Map<String,Module>)f.get(m);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    @Test void nullAtAnySnapshotPositionRejectsBeforeEveryModuleGetter(){
        for(int pos=0;pos<3;pos++){var ta=new ArrayList<String>();var tb=new ArrayList<String>();var a=new ModuleManager();var b=new ModuleManager();
            for(int i=0;i<3;i++){if(i==pos){registry(a).put("bad",null);registry(b).put("bad",null);}else{a.register(new Probe("m"+i,ta));b.register(new Probe("m"+i,tb));}}
            assertThrows(NullPointerException.class,()->original(a,warp()));assertThrows(NullPointerException.class,()->b.scanUniqueGameplayOwner(warp()));assertTrue(ta.isEmpty());assertTrue(tb.isEmpty());assertScratchClear(b);
        }
    }
    @SuppressWarnings("unchecked") private static Object scratch(ModuleManager m){try{var f=ModuleManager.class.getDeclaredField("controllerScratch");f.setAccessible(true);return ((ThreadLocal<?>)f.get(m)).get();}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    @SuppressWarnings("unchecked") private static ArrayList<ArrayList<Module>> lists(Object scratch){try{var f=scratch.getClass().getDeclaredField("lists");f.setAccessible(true);return (ArrayList<ArrayList<Module>>)f.get(scratch);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static void assertScratchClear(ModuleManager m){try{var scratch=scratch(m);var f=scratch.getClass().getDeclaredField("depth");f.setAccessible(true);assertEquals(0,f.getInt(scratch));for(var list:lists(scratch))assertTrue(list.isEmpty());}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static final class FailAdd extends ArrayList<Module>{int count;final int failed;final Error error;FailAdd(int failed,Error error){this.failed=failed;this.error=error;}@Override public boolean add(Module m){if(++count==failed)throw error;return super.add(m);}}
    @Test void partialCopyFailureClearsBorrowedReferencesBeforePropagatingAndNextScanIsFresh(){
        var trace=new ArrayList<String>();var m=new ModuleManager();for(int i=0;i<3;i++)m.register(new Probe("m"+i,trace));m.scanUniqueGameplayOwner(warp());trace.clear();Object scratch=scratch(m);
        for(int fail=1;fail<=3;fail++){Error error=new AssertionError("copy "+fail);lists(scratch).set(0,new FailAdd(fail,error));assertSame(error,assertThrows(Error.class,()->m.scanUniqueGameplayOwner(warp())));assertTrue(trace.isEmpty());assertScratchClear(m);}
        lists(scratch).set(0,new ArrayList<>());var result=m.scanUniqueGameplayOwner(warp());assertEquals(ModuleManager.UniqueOwnerStatus.AMBIGUOUS,result.status());assertScratchClear(m);
    }
    @Test void publicAllRemainsImmutableHistoricSnapshotAndInputOwnerKeepsOriginalCalls(){
        var trace=new ArrayList<String>();var m=new ModuleManager();var first=new Probe("first",trace);m.register(first);var snapshot=m.all();m.register(new Probe("second",trace));assertEquals(1,snapshot.size());assertSame(first,snapshot.iterator().next());assertThrows(UnsupportedOperationException.class,snapshot::clear);
        m.leasedInputOwner(()->first);assertSame(first,m.inputOwner().orElseThrow());assertScratchClear(m);
    }
    @Test void observerAfterNoneCanRegisterAndNextFreshScanSeesItWithoutCachedResult(){
        var trace=new ArrayList<String>();var m=new ModuleManager();var none=m.scanUniqueGameplayOwner(warp());assertEquals(ModuleManager.UniqueOwnerStatus.NONE,none.status());assertScratchClear(m);
        trace.add("noOwner");var next=new Probe("new",trace);m.register(next);var now=m.scanUniqueGameplayOwner(warp());assertEquals(ModuleManager.UniqueOwnerStatus.OWNER,now.status());assertSame(next,now.owner());assertScratchClear(m);
    }
    @Test void warpCallbackRegistrationAndNestedFailureKeepOriginalOuterRefusalAndClearAllDepths(){
        var trace=new ArrayList<String>();var m=new ModuleManager();var first=new Probe("first",trace);m.register(first);Error error=new AssertionError("nested");
        first.warpHook=()->{m.register(new Probe("added",trace));first.warpHook=null;throw error;};assertSame(error,assertThrows(Error.class,()->m.scanUniqueGameplayOwner(warp())));assertScratchClear(m);
        first.warp=true;var result=m.scanUniqueGameplayOwner(warp());assertEquals(ModuleManager.UniqueOwnerStatus.WARP_REFUSED,result.status());assertNull(result.owner());assertScratchClear(m);
    }
    @Test void retainedResultDoesNotRetainScratchSnapshotOrBecomeALaterCurrentPermit(){
        var trace=new ArrayList<String>();var m=new ModuleManager();var first=new Probe("first",trace);m.register(first);var old=m.scanUniqueGameplayOwner(warp());assertSame(first,old.owner());assertScratchClear(m);
        first.enabled=false;var now=m.scanUniqueGameplayOwner(warp());assertEquals(ModuleManager.UniqueOwnerStatus.NONE,now.status());assertNull(now.owner());assertSame(first,old.owner());assertScratchClear(m);
    }
}
