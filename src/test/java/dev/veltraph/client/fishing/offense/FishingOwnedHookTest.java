package dev.veltraph.client.fishing.offense;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
final class FishingOwnedHookTest {
    final Object player=new Object(),world=new Object(),connection=new Object(),entity=new Object();final UUID uuid=UUID.randomUUID();
    final FishingOwnedHook.Scope scope=new FishingOwnedHook.Scope(player,world,connection);
    FishingOwnedHook.Hook hook(){return new FishingOwnedHook.Hook(entity,player,world,3,uuid);}
    @Test void lateCurrentOwnedHookClaimsOneReelAndOnlyActualRemovalPermitsSelection(){var g=new FishingOwnedHook();assertTrue(g.begin(scope,null));assertTrue(g.observe(scope,hook()));assertFalse(g.maySelectTool(scope,hook()));assertTrue(g.issueReel(scope,hook()));assertFalse(g.issueReel(scope,hook()));assertFalse(g.maySelectTool(scope,hook()));assertFalse(g.removalObserved());assertTrue(g.maySelectTool(scope,null));assertTrue(g.removalObserved());assertFalse(g.observe(scope,hook()));}
    @Test void foreignPlayerAndWorldCannotBecomeOwnedHook(){var g=new FishingOwnedHook();assertTrue(g.begin(scope,null));assertFalse(g.issueReel(scope,new FishingOwnedHook.Hook(entity,new Object(),world,3,uuid)));assertFalse(g.issueReel(scope,new FishingOwnedHook.Hook(entity,player,new Object(),3,uuid)));assertFalse(g.reelIssued());}
    @Test void replacedNativeObjectIdOrUuidCannotReuseCapturedHook(){var g=new FishingOwnedHook();assertTrue(g.begin(scope,hook()));assertFalse(g.observe(scope,new FishingOwnedHook.Hook(new Object(),player,world,3,uuid)));assertFalse(g.observe(scope,new FishingOwnedHook.Hook(entity,player,world,4,uuid)));assertFalse(g.observe(scope,new FishingOwnedHook.Hook(entity,player,world,3,UUID.randomUUID())));assertTrue(g.issueReel(scope,hook()));}
    @Test void changedPlayerWorldConnectionAndStopCannotIssueReelOrSelect(){var g=new FishingOwnedHook();g.begin(scope,hook());for(var changed:new FishingOwnedHook.Scope[]{new FishingOwnedHook.Scope(new Object(),world,connection),new FishingOwnedHook.Scope(player,new Object(),connection),new FishingOwnedHook.Scope(player,world,new Object())}){assertFalse(g.issueReel(changed,hook()));assertFalse(g.maySelectTool(changed,null));}g.cancel();assertFalse(g.issueReel(scope,hook()));assertFalse(g.maySelectTool(scope,null));}
    @Test void absentHookCannotTurnOrdinaryUseIntoNewCast(){var g=new FishingOwnedHook();g.begin(scope,null);assertFalse(g.issueReel(scope,null));assertFalse(g.reelIssued());assertTrue(g.maySelectTool(scope,null));}
    @Test void unsupportedNativeIdentityCannotBeAccepted(){var g=new FishingOwnedHook();assertFalse(g.begin(scope,new FishingOwnedHook.Hook(entity,player,world,-1,uuid)));assertFalse(g.begin(scope,new FishingOwnedHook.Hook(entity,player,world,3,null)));}
}
