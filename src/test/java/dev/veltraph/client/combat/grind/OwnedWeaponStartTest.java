package dev.veltraph.client.combat.grind;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OwnedWeaponStartTest {
    private final Object p=new Object(),w=new Object(),c=new Object();
    private final List<String> items=List.of("fixed sword","named axe|count1|loreA","empty","empty","empty","empty","empty","empty","protected stick");
    private BestiaryWeaponStart.Frame frame(){return new BestiaryWeaponStart.Frame(p,w,c,8,items,"SVEN_CAVE|profileA","global",true);}
    static final class Held implements dev.veltraph.client.service.InventoryService {
        int slot=8,writes;public int selectedSlot(){return slot;}public void selectSlot(int s){slot=s;writes++;}
        public List<String> hotbarItemNames(){return Collections.nCopies(9,"");}
    }
    @Test void deniedParentOrDisabledChildCannotSelectEvenWithExactNativeProposal(){
        var held=new Held();var parent=new AtomicBoolean(false);var child=new AtomicBoolean(true);
        var start=new OwnedWeaponStart(held,()->parent.get()&&child.get());
        start.prepare(frame(),new BestiaryWeaponSelector.Choice(1,503));start.prepare(frame(),new BestiaryWeaponSelector.Choice(1,503));
        assertEquals(0,held.writes);assertFalse(start.commit(frame()));assertEquals(0,held.writes);
        parent.set(true);child.set(false);start.prepare(frame(),new BestiaryWeaponSelector.Choice(1,503));
        assertFalse(start.commit(frame()));assertEquals(0,held.writes);assertNull(start.proposal());
    }
    @Test void removedBoundChildCannotSpendOldProposal(){
        var held=new Held();Object expected=new Object();Object[] bound={expected};
        var start=new OwnedWeaponStart(held,()->bound[0]==expected);
        start.prepare(frame(),new BestiaryWeaponSelector.Choice(1,503));bound[0]=new Object();
        assertFalse(start.commit(frame()));assertEquals(0,held.writes);
    }
    @Test void authorizedCommitIsOneUseAndRequiresExactComponentsConfigAndContext(){
        var held=new Held();var start=new OwnedWeaponStart(held,()->true);
        var f=frame();
        for(var changed:List.of(
                new BestiaryWeaponStart.Frame(new Object(),w,c,8,items,f.settings(),f.preferred(),true),
                new BestiaryWeaponStart.Frame(p,new Object(),c,8,items,f.settings(),f.preferred(),true),
                new BestiaryWeaponStart.Frame(p,w,new Object(),8,items,f.settings(),f.preferred(),true),
                new BestiaryWeaponStart.Frame(p,w,c,8,List.of("count2","named axe|count1|loreB","empty","empty","empty","empty","empty","empty","protected stick"),f.settings(),f.preferred(),true),
                new BestiaryWeaponStart.Frame(p,w,c,8,items,"SVEN_CAVE|profileB",f.preferred(),true),
                new BestiaryWeaponStart.Frame(p,w,c,8,items,f.settings(),"changed global",true),
                new BestiaryWeaponStart.Frame(p,w,c,7,items,f.settings(),f.preferred(),true),
                new BestiaryWeaponStart.Frame(p,w,c,8,items,f.settings(),f.preferred(),false))){
            start.prepare(f,new BestiaryWeaponSelector.Choice(1,503));assertFalse(start.commit(changed));assertEquals(0,held.writes);
        }
        start.prepare(f,new BestiaryWeaponSelector.Choice(1,503));assertTrue(start.commit(f));
        assertEquals(1,held.slot);assertEquals(1,held.writes);assertFalse(start.commit(f));assertEquals(1,held.writes);
    }
    @Test void fullInventoryArmorAndOffhandDriftCannotCommitAHotbarExactProposal(){
        var held=new Held();var start=new OwnedWeaponStart(held,()->true);
        var loadout=java.util.List.of("hotbar exact","escrow count64","helmet loreA","offhand map");
        for(int index=1;index<loadout.size();index++){
            var changed=new java.util.ArrayList<>(loadout);changed.set(index,"foreign components");
            start.prepare(frame(),new BestiaryWeaponSelector.Choice(1,503),loadout);
            assertFalse(start.commit(frame(),changed));assertEquals(0,held.writes);
        }
        start.prepare(frame(),new BestiaryWeaponSelector.Choice(1,503),loadout);assertTrue(start.commit(frame(),loadout));assertEquals(1,held.writes);
    }
    @Test void actualTypedPolicyChoosesDeclaredContextNameWithoutInventoryMoves(){
        String profile="{\"schema\":1,\"families\":{\"SVEN_CAVE\":{\"preferredNames\":[\"Axe\"]}}}";
        var policy=CombatWeaponPolicy.parse(profile,CombatWeaponPolicy.Family.SVEN_CAVE).policy();
        var choice=policy.select(List.of(new BestiaryWeaponSelector.Candidate(0,false,BestiaryWeaponSelector.Kind.SWORD,"global"),
                new BestiaryWeaponSelector.Candidate(1,false,BestiaryWeaponSelector.Kind.AXE,"Axe")),0,"").orElseThrow();
        assertEquals(1,choice.slot());var held=new Held();var start=new OwnedWeaponStart(held,()->true);
        start.prepare(frame(),choice);assertEquals(8,held.slot);assertEquals(0,held.writes);
        assertTrue(start.commit(frame()));assertEquals(1,held.slot);
    }
}
