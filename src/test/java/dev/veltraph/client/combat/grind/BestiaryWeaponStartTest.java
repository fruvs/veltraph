package dev.veltraph.client.combat.grind;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BestiaryWeaponStartTest {
    private final Object player=new Object(),world=new Object(),connection=new Object();
    private final List<String> hotbar=List.of("sword0","empty","preferred:count1:damage0:loreA","empty","empty","tie","empty","stick","apple4");
    private BestiaryWeaponStart.Frame frame(Object p,Object w,Object c,int slot,List<?> tools,String settings,String preferred,boolean idle){return new BestiaryWeaponStart.Frame(p,w,c,slot,tools,settings,preferred,idle);}
    private BestiaryWeaponStart.Frame original(){return frame(player,world,connection,8,hotbar,"bestiary|quarry|radius16","preferred",true);}
    private static final class Held implements dev.veltraph.client.service.InventoryService {
        int slot=8,writes;boolean ignore;
        public int selectedSlot(){return slot;}public void selectSlot(int s){writes++;if(!ignore)slot=s;}
        public List<String> hotbarItemNames(){return Collections.nCopies(9,"");}
    }
    @Test void repeatedReadinessAndDeniedStartNeverSelectOrRestoreAnything() {
        var inv=new Held();var start=new BestiaryWeaponStart(inv);
        start.prepare(original(),new BestiaryWeaponSelector.Choice(2,502));
        start.prepare(original(),new BestiaryWeaponSelector.Choice(2,502));
        assertEquals(8,inv.slot);assertEquals(0,inv.writes);
        start.cancel();assertEquals(8,inv.slot);assertEquals(0,inv.writes);assertFalse(start.commit(original()));
    }
    @Test void changedProposalCannotWriteHeldSlotThenFreshAuthorizedCommitSelectsOnce() {
        var inv=new Held();var start=new BestiaryWeaponStart(inv);
        start.prepare(original(),new BestiaryWeaponSelector.Choice(2,502));
        assertFalse(start.commit(frame(player,world,connection,8,hotbar,"changed settings","preferred",true)));
        assertEquals(8,inv.slot);assertEquals(0,inv.writes);assertNull(start.proposal());
        start.prepare(original(),new BestiaryWeaponSelector.Choice(2,502));assertTrue(start.commit(original()));
        assertEquals(2,inv.slot);assertEquals(1,inv.writes);assertFalse(start.commit(original()));assertEquals(1,inv.writes);
    }
    @Test void absentNativeSelectionObservationCannotReportCommitSuccess() {
        var inv=new Held();inv.ignore=true;var start=new BestiaryWeaponStart(inv);
        start.prepare(original(),new BestiaryWeaponSelector.Choice(2,502));assertFalse(start.commit(original()));
        assertEquals(8,inv.slot);assertEquals(1,inv.writes);
    }
    @Test void exactPassiveProposalCanCommitWithoutChangingIt() {
        var p=original();assertTrue(BestiaryWeaponStart.mayCommit(p,original()));assertEquals(8,p.heldSlot());assertEquals(hotbar,p.hotbar());
    }
    @Test void configurationAndPreferenceDriftCannotCommit() {
        assertFalse(BestiaryWeaponStart.mayCommit(original(),frame(player,world,connection,8,hotbar,"bestiary|other|radius16","preferred",true)));
        assertFalse(BestiaryWeaponStart.mayCommit(original(),frame(player,world,connection,8,hotbar,"bestiary|quarry|radius16","changed",true)));
    }
    @Test void AnyHotbarCountDamageLoreOrCandidateDriftCannotCommit() {
        for(String value:List.of("preferred:count2:damage0:loreA","preferred:count1:damage1:loreA","preferred:count1:damage0:loreB","higher-scored sword")){
            var changed=new ArrayList<>(hotbar);changed.set(2,value);assertFalse(BestiaryWeaponStart.mayCommit(original(),frame(player,world,connection,8,changed,"bestiary|quarry|radius16","preferred",true)));
        }
    }
    @Test void manualSelectionForeignContextAndMenusCannotCommit() {
        assertFalse(BestiaryWeaponStart.mayCommit(original(),frame(player,world,connection,7,hotbar,"bestiary|quarry|radius16","preferred",true)));
        assertFalse(BestiaryWeaponStart.mayCommit(original(),frame(new Object(),world,connection,8,hotbar,"bestiary|quarry|radius16","preferred",true)));
        assertFalse(BestiaryWeaponStart.mayCommit(original(),frame(player,new Object(),connection,8,hotbar,"bestiary|quarry|radius16","preferred",true)));
        assertFalse(BestiaryWeaponStart.mayCommit(original(),frame(player,world,new Object(),8,hotbar,"bestiary|quarry|radius16","preferred",true)));
        assertFalse(BestiaryWeaponStart.mayCommit(original(),frame(player,world,connection,8,hotbar,"bestiary|quarry|radius16","preferred",false)));
    }
    @Test void missingOrPartialProposalNeverAuthorizesSelection() {
        assertFalse(BestiaryWeaponStart.mayCommit(null,original()));assertFalse(BestiaryWeaponStart.mayCommit(original(),null));
        assertFalse(BestiaryWeaponStart.mayCommit(frame(player,world,connection,8,List.of(),"settings","preferred",true),original()));
    }
}
