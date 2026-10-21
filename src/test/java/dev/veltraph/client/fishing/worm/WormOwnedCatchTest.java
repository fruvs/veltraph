package dev.veltraph.client.fishing.worm;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class WormOwnedCatchTest {
 final Object owner=new Object(),player=new Object(),world=new Object(),connection=new Object();final WormOwnedCatch.Scope scope=new WormOwnedCatch.Scope(owner,player,world,connection,1);final UUID item=UUID.randomUUID();
 List<WormOwnedCatch.Cell> cells(){var c=new ArrayList<WormOwnedCatch.Cell>();for(int i=0;i<36;i++)c.add(new WormOwnedCatch.Cell("air",0,"air"));return c;}
 WormOwnedCatch.Spawn spawn(){return new WormOwnedCatch.Spawn(item,21,"cod",1,101,true,true,true,false);}WormOwnedCatch.Take take(){return new WormOwnedCatch.Take(item,21,"cod",1,103,true,true);}
 WormOwnedCatch.Batch batch(int slot){var before=cells();var after=cells();after.set(slot,new WormOwnedCatch.Cell("cod",1,"cod1"));return new WormOwnedCatch.Batch(scope,before,after,104,true);}
 @Test void removalAndInventoryPacketsBeforeFirstRootReservationJoinOnFreshSingleObservation(){
    var nativePlayer=new Object();var nativeWorld=new Object();var nativeConnection=new Object();
    var hooks=new dev.veltraph.client.fishing.offense.FishingOwnedHook();var hscope=new dev.veltraph.client.fishing.offense.FishingOwnedHook.Scope(nativePlayer,nativeWorld,nativeConnection);
    var hook=new dev.veltraph.client.fishing.offense.FishingOwnedHook.Hook(new Object(),nativePlayer,nativeWorld,5,UUID.randomUUID());
    assertTrue(hooks.begin(hscope,hook));assertTrue(hooks.issueReel(hscope,hook));assertFalse(hooks.removalObserved());
    var c=new WormOwnedCatch(scope,100,200,0,2);assertTrue(c.correlate(scope,cells(),spawn(),take(),batch(1),hooks.removalObserved()).isEmpty());
    // Native removal and authoritative catch inventory have already applied before this ordinary Root reservation.
    boolean current=hooks.observe(hscope,null);assertTrue(current);assertTrue(hooks.removalObserved());
    assertTrue(c.correlate(scope,cells(),spawn(),take(),batch(1),hooks.removalObserved()).isPresent());
 }
 @Test void foreignNativeHookScopeCannotSupplyFreshRemovalReceipt(){
    var hooks=new dev.veltraph.client.fishing.offense.FishingOwnedHook();var hscope=new dev.veltraph.client.fishing.offense.FishingOwnedHook.Scope(player,world,connection);
    var nativeHook=new dev.veltraph.client.fishing.offense.FishingOwnedHook.Hook(new Object(),player,world,5,UUID.randomUUID());
    assertTrue(hooks.begin(hscope,nativeHook));assertTrue(hooks.issueReel(hscope,nativeHook));
    assertFalse(hooks.observe(new dev.veltraph.client.fishing.offense.FishingOwnedHook.Scope(player,new Object(),connection),null));assertFalse(hooks.removalObserved());
 }
 @Test void exactNativeCatchCanUpdateOnlyOneReservedCellOnce(){var c=new WormOwnedCatch(scope,100,200,0,2);var proof=c.correlate(scope,cells(),spawn(),take(),batch(1),true).orElseThrow();assertEquals(1,proof.slot());assertTrue(c.consume(scope,proof));assertFalse(c.consume(scope,proof));assertTrue(c.correlate(scope,cells(),spawn(),take(),batch(1),true).isEmpty());}
 @Test void ambiguousForeignSameItemOriginCannotAuthorize(){var c=new WormOwnedCatch(scope,100,200,0,2);assertTrue(c.correlate(scope,cells(),new WormOwnedCatch.Spawn(item,21,"cod",1,101,true,true,false,false),take(),batch(1),true).isEmpty());}
 @Test void knownForeignOriginRejectsEvenAnOtherwiseExactChain(){var c=new WormOwnedCatch(scope,100,200,0,2);assertTrue(c.correlate(scope,cells(),new WormOwnedCatch.Spawn(item,21,"cod",1,101,true,true,true,true),take(),batch(1),true).isEmpty());}
 @Test void loneUnknownForeignDropIsProtocolIndistinguishableAndOnlyQualifiedCorrelation(){var c=new WormOwnedCatch(scope,100,200,0,2);assertTrue(c.correlate(scope,cells(),spawn(),take(),batch(1),true).isPresent());/* This proves the observable chain, not exclusive causal source of the spawn. */}
 @Test void authoritativeInventoryPacketMayArriveBeforeTakeCallback(){var c=new WormOwnedCatch(scope,100,200,0,2);var b=batch(1);assertTrue(c.correlate(scope,cells(),spawn(),new WormOwnedCatch.Take(item,21,"cod",1,105,true,true),b,true).isPresent());}
 @Test void rodOrToolCellCannotBecomeCatchAllowance(){var c=new WormOwnedCatch(scope,100,200,0,2);for(int slot:new int[]{0,2})assertTrue(c.correlate(scope,cells(),spawn(),take(),batch(slot),true).isEmpty());}
 @Test void unrelatedManualDeltaCannotBeResnapshotted(){var c=new WormOwnedCatch(scope,100,200,0,2);var b=batch(1);var after=new ArrayList<>(b.after());after.set(3,new WormOwnedCatch.Cell("diamond",1,"diamond1"));assertTrue(c.correlate(scope,cells(),spawn(),take(),new WormOwnedCatch.Batch(scope,b.before(),after,104,true),true).isEmpty());assertTrue(c.correlate(scope,cells(),spawn(),take(),new WormOwnedCatch.Batch(scope,b.before(),b.after(),104,false),true).isEmpty());}
 @Test void foreignOwnerPlayerWorldConnectionOrLeaseCannotAdopt(){var c=new WormOwnedCatch(scope,100,200,0,2);for(var foreign:new WormOwnedCatch.Scope[]{new WormOwnedCatch.Scope(new Object(),player,world,connection,1),new WormOwnedCatch.Scope(owner,new Object(),world,connection,1),new WormOwnedCatch.Scope(owner,player,new Object(),connection,1),new WormOwnedCatch.Scope(owner,player,world,new Object(),1),new WormOwnedCatch.Scope(owner,player,world,connection,2)})assertTrue(c.correlate(foreign,cells(),spawn(),take(),batch(1),true).isEmpty());}
 @Test void noHookReceiptOldItemForeignTakeWrongIdentityAndTimeoutDeny(){var c=new WormOwnedCatch(scope,100,200,0,2);assertTrue(c.correlate(scope,cells(),spawn(),take(),batch(1),false).isEmpty());assertTrue(c.correlate(scope,cells(),new WormOwnedCatch.Spawn(item,21,"cod",1,101,false,true,true,false),take(),batch(1),true).isEmpty());assertTrue(c.correlate(scope,cells(),spawn(),new WormOwnedCatch.Take(item,21,"cod",1,103,false,true),batch(1),true).isEmpty());assertTrue(c.correlate(scope,cells(),spawn(),new WormOwnedCatch.Take(item,21,"salmon",1,103,true,true),batch(1),true).isEmpty());assertTrue(c.correlate(scope,cells(),spawn(),take(),new WormOwnedCatch.Batch(scope,batch(1).before(),batch(1).after(),201,true),true).isEmpty());}
}
