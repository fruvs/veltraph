package dev.veltraph.client.combat.grind;
import dev.veltraph.client.fishing.offense.FishingOwnedHook;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FishingParentWeaponSeamTest {
    private static final MeleeGrindModule.ParentWeaponDeclaration FISHING=MeleeGrindModule.ParentWeaponDeclaration.FISHING_AUTO_KILLER;
    private static List<BestiaryWeaponSelector.Candidate> items(){var result=new ArrayList<BestiaryWeaponSelector.Candidate>();for(int i=0;i<9;i++)result.add(new BestiaryWeaponSelector.Candidate(i,i!=0&&i!=4, i==0?BestiaryWeaponSelector.Kind.SWORD:i==4?BestiaryWeaponSelector.Kind.AXE:BestiaryWeaponSelector.Kind.OTHER,i==4?"Global Preferred Fishing Axe":null));return result;}
    @Test void slotOneForcesNativeZeroWhileAutomaticUsesReadableGlobalBonus(){
        assertEquals(0,OwnedWeaponStart.selectParentPolicy(FISHING,CombatWeaponPolicy.fixed(),0,items(),"Global Preferred").orElseThrow().slot());
        var automatic=OwnedWeaponStart.selectParentPolicy(FISHING,CombatWeaponPolicy.common(),0,items(),"Global Preferred").orElseThrow();assertEquals(4,automatic.slot());assertEquals(502,automatic.score());
        assertTrue(OwnedWeaponStart.selectParentPolicy(FISHING,CombatWeaponPolicy.fixed(),7,items(),"Global Preferred").isEmpty());
    }
    @Test void fishingDeclarationDoesNotIntroduceContextThirteenOrInferredSourceWeights(){
        var ordinary=OwnedWeaponStart.selectParentPolicy(FISHING,CombatWeaponPolicy.common(),0,items(),"").orElseThrow();assertEquals(0,ordinary.slot());assertEquals(3,ordinary.score());
        var contextual=CombatWeaponPolicy.parse("{\"schema\":1,\"families\":{\"SVEN_CAVE\":{\"preferredNames\":[\"Fishing Axe\"]}}}",CombatWeaponPolicy.Family.SVEN_CAVE).policy();
        assertTrue(OwnedWeaponStart.selectParentPolicy(FISHING,contextual,0,items(),"").isEmpty());
        assertThrows(IllegalArgumentException.class,()->OwnedWeaponStart.parentPolicySignature(FISHING,contextual,0,"same caller","") );
        assertTrue(OwnedWeaponStart.selectParentPolicy(null,CombatWeaponPolicy.common(),0,items(),"").isEmpty());
    }
    private static final class Held implements dev.veltraph.client.service.InventoryService {
        int slot=5,writes;public int selectedSlot(){return slot;}public void selectSlot(int s){slot=s;writes++;}public List<String> hotbarItemNames(){return Collections.nCopies(9,"");}
    }
    private static BestiaryWeaponStart.Frame frame(Object p,Object w,Object c,String signature){return new BestiaryWeaponStart.Frame(p,w,c,5,List.of("sword","empty","empty","empty","axe","original rod normalized damage","empty","empty","escrow"),signature,"Global Preferred",true);}
    @Test void actualOwnedHookReceiptKeepsRodHeldUntilRemovalThenBoundParentCommitsExactlyOnce(){
        Object p=new Object(),w=new Object(),c=new Object(),entity=new Object();var scope=new FishingOwnedHook.Scope(p,w,c);var nativeHook=new FishingOwnedHook.Hook(entity,p,w,42,UUID.randomUUID());var hook=new FishingOwnedHook();assertTrue(hook.begin(scope,nativeHook));assertTrue(hook.issueReel(scope,nativeHook));
        var held=new Held();FishingOwnedHook.Hook[] observed={nativeHook};boolean[] enabled={true},bound={true};
        var start=new OwnedWeaponStart(held,()->enabled[0]&&bound[0]&&hook.maySelectTool(scope,observed[0]));
        var choice=OwnedWeaponStart.selectParentPolicy(FISHING,CombatWeaponPolicy.common(),0,items(),"Global Preferred").orElseThrow();var f=frame(p,w,c,"frozen Fishing options");
        start.prepare(f,choice);assertEquals(5,held.slot);assertEquals(0,held.writes);assertFalse(start.commit(f));assertEquals(0,held.writes);
        observed[0]=null;assertTrue(hook.observe(scope,null));assertTrue(hook.removalObserved());start.prepare(f,choice);assertTrue(start.commit(f));assertEquals(4,held.slot);assertEquals(1,held.writes);assertFalse(start.commit(f));assertEquals(1,held.writes);
    }
    @Test void acknowledgedRemovalCannotCommitForDisabledReplacedOrForeignParent(){
        Object p=new Object(),w=new Object(),c=new Object();var scope=new FishingOwnedHook.Scope(p,w,c);var hook=new FishingOwnedHook();var h=new FishingOwnedHook.Hook(new Object(),p,w,1,UUID.randomUUID());assertTrue(hook.begin(scope,h));assertTrue(hook.issueReel(scope,h));assertTrue(hook.observe(scope,null));
        var held=new Held();boolean[] enabled={false},bound={true};FishingOwnedHook.Scope[] current={scope};var start=new OwnedWeaponStart(held,()->enabled[0]&&bound[0]&&hook.maySelectTool(current[0],null));var f=frame(p,w,c,"frozen");var choice=new BestiaryWeaponSelector.Choice(0,0);
        start.prepare(f,choice);assertFalse(start.commit(f));enabled[0]=true;bound[0]=false;start.prepare(f,choice);assertFalse(start.commit(f));bound[0]=true;current[0]=new FishingOwnedHook.Scope(p,new Object(),c);start.prepare(f,choice);assertFalse(start.commit(f));assertEquals(0,held.writes);assertEquals(5,held.slot);
    }
    @Test void policyModeRawOptionsAndGlobalPreferenceAreIndependentlyFrozenAtCommit(){
        var auto=OwnedWeaponStart.parentPolicySignature(FISHING,CombatWeaponPolicy.common(),0,"mode automatic squid true","Global Preferred");
        var fixed=OwnedWeaponStart.parentPolicySignature(FISHING,CombatWeaponPolicy.fixed(),0,"mode automatic squid true","Global Preferred");
        var raw=OwnedWeaponStart.parentPolicySignature(FISHING,CombatWeaponPolicy.common(),0,"mode automatic squid false","Global Preferred");
        var global=OwnedWeaponStart.parentPolicySignature(FISHING,CombatWeaponPolicy.common(),0,"mode automatic squid true","Different Preferred");
        var fixedSupplierDrift=OwnedWeaponStart.parentPolicySignature(FISHING,CombatWeaponPolicy.common(),7,"mode automatic squid true","Global Preferred");assertNotEquals(auto,fixedSupplierDrift);assertNotEquals(auto,fixed);assertNotEquals(auto,raw);assertNotEquals(auto,global);
        Object p=new Object(),w=new Object(),c=new Object();var held=new Held();var start=new OwnedWeaponStart(held,()->true);var f=frame(p,w,c,auto);
        for(String changed:List.of(fixed,raw,global,fixedSupplierDrift)){start.prepare(f,new BestiaryWeaponSelector.Choice(4,502));assertFalse(start.commit(frame(p,w,c,changed)));assertEquals(0,held.writes);}
    }
}
