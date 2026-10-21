package dev.veltraph.client.combat.sven;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.combat.sven.SvenIngressPort.*;
import static dev.veltraph.client.combat.sven.SvenIngressController.*;

/** Pure transition/receipt fixture. It makes no native server/ability physics claim. */
class SvenIngressControllerTest {
    static final class Port implements SvenIngressPort {
        final List<Request> issued=new ArrayList<>();
        final List<Integer> selections=new ArrayList<>();
        final List<Consumer<Result>> callbacks=new ArrayList<>();
        Optional<Boolean> open=Optional.of(false),empty=Optional.of(true);
        Optional<Integer> resource=Optional.empty();
        Optional<String> resourceBlock=Optional.empty();
        List<BlockPos> cracked=List.of();
        int selected=5,cancels;
        Vec3 eyes=new Vec3(-390.5,99,-62.5);
        public Optional<Boolean> gatePassable(BlockPos p){return open;}
        public Optional<Integer> resourceHotbarSlot(){return resource;}
        public Optional<String> resourceReadiness(){return resourceBlock;}
        public Optional<Boolean> hasEmptyHotbarSlot(){return empty;}
        public List<BlockPos> crackedGateCandidates(BlockPos p){return cracked;}
        public Vec3 eyePosition(){return eyes;}
        public int selectedSlot(){return selected;}
        public void selectSlot(int s){selected=s;selections.add(s);}
        public void begin(Request r,Consumer<Result> done){issued.add(r);callbacks.add(done);}
        public void tick(){}
        public void cancel(){cancels++;}
        Request last(){return issued.getLast();}
        void complete(){callbacks.getLast().accept(Result.complete());}
        void complete(Continuation c){callbacks.getLast().accept(Result.complete(c));}
        void fail(){callbacks.getLast().accept(Result.failed("fixture path failure"));}
        long count(Operation op){return issued.stream().filter(r->r.operation()==op).count();}
    }
    static final class Fixture {
        final Port port=new Port();int combat;
        final SvenIngressController flow=new SvenIngressController(port,new Random(7),()->combat++);
        Fixture(){start();}
        void start(){flow.start(SvenCaveProfile.DARK_OAK,false,"[verified receipt]","verified resource","verified denial",Set.of("verified throttle"));}
        void action(Operation expected){flow.tick();assertEquals(expected,port.last().operation());}
        void done(Operation expected){action(expected);port.complete();}
        void entrance(){action(Operation.PREPARE);port.complete(Continuation.TRAVEL);done(Operation.TRAVEL_PREREQUISITE);
            done(Operation.WARP_ROUTE_START);done(Operation.PROFILE_ROUTE);action(Operation.WALK_ENTRANCE);}
        void buy(){entrance();port.complete();done(Operation.OPEN_BAZAAR);done(Operation.CLICK_SIGN);done(Operation.ENTER_INPUT);
            done(Operation.CLICK_TNT);done(Operation.CLICK_BUY_INSTANTLY);action(Operation.CLICK_BUY_ONE);}
        void rotationAndUse(){action(Operation.ROTATE);port.complete();for(int i=0;i<6&&port.count(Operation.USE_RESOURCE)==0;i++)flow.tick();
            assertEquals(Operation.USE_RESOURCE,port.last().operation());port.complete();}
    }
    @Test void purchaseReceiptDoesNotProveGateOpeningOrInsideArrival(){
        var f=new Fixture();f.buy();f.port.complete();
        for(int i=0;i<20;i++)f.flow.tick();assertEquals(1,f.port.count(Operation.CLICK_BUY_ONE));assertEquals(State.CLICK_BUY_ONE,f.flow.state());
        f.flow.message("[verified receipt] Bought verified resource");assertEquals(State.CLOSE_BAZAAR,f.flow.state());assertEquals(0,f.combat);
        // Only a separately observed resource hotbar delivery permits placement.
        f.port.resource=Optional.of(2);f.done(Operation.CLOSE_BAZAAR);f.rotationAndUse();
        assertEquals(State.WAITING_FOR_EXPLOSION,f.flow.state());assertEquals(0,f.combat);
        for(int i=0;i<500;i++)f.flow.tick();assertEquals(State.WAITING_FOR_EXPLOSION,f.flow.state());assertEquals(0,f.port.count(Operation.WALK_INSIDE));
        f.port.open=Optional.of(true);f.flow.tick();assertEquals(State.WALK_INSIDE,f.flow.state());assertEquals(5,f.port.selected);
        f.action(Operation.WALK_INSIDE);assertEquals(0,f.combat);f.port.complete();assertEquals(1,f.combat);assertEquals(Child.COMBAT,f.flow.child());
    }
    @Test void openGateSkipsResourceInventoryAndPurchaseButRequiresInsideWalkArrival(){
        var f=new Fixture();f.port.open=Optional.of(true);f.port.resourceBlock=Optional.of("unbound resource");f.entrance();f.port.complete();
        assertEquals(State.WALK_INSIDE,f.flow.state());assertEquals(0,f.combat);f.action(Operation.WALK_INSIDE);f.port.complete();
        assertEquals(1,f.combat);assertEquals(0,f.port.count(Operation.OPEN_BAZAAR));assertEquals(0,f.port.count(Operation.USE_RESOURCE));
    }
    @Test void resourceHotbarSkipsPurchaseAndRotationDelaysThreeToSixTicks(){
        var f=new Fixture();f.port.resource=Optional.of(2);f.entrance();f.port.complete();assertEquals(State.ROTATE_TO_PLACEABLE_GROUND,f.flow.state());
        assertEquals(2,f.port.selected);f.action(Operation.ROTATE);f.port.complete();
        f.flow.tick();f.flow.tick();assertEquals(0,f.port.count(Operation.USE_RESOURCE));
        for(int i=0;i<4;i++)f.flow.tick();assertEquals(1,f.port.count(Operation.USE_RESOURCE));assertEquals(0,f.port.count(Operation.OPEN_BAZAAR));
    }
    @Test void receiptsAreStatePrefixItemAndIssuedActionGuarded(){
        var f=new Fixture();f.flow.message("[verified receipt] Bought verified resource");assertNull(f.flow.state());
        f.buy();f.flow.message("[verified receipt] Bought verified resource");assertEquals(State.CLICK_BUY_ONE,f.flow.state());
        f.port.complete();f.flow.message("wrong prefix verified resource");f.flow.message("[verified receipt] wrong item");assertEquals(State.CLICK_BUY_ONE,f.flow.state());
        f.flow.message("§a[verified receipt] Bought verified resource");assertEquals(State.CLOSE_BAZAAR,f.flow.state());
    }
    @Test void missingReceiptBindingBlocksAtPurchaseBeforeIssuingBuy(){
        var f=new Fixture();f.flow.start(SvenCaveProfile.DARK_OAK,false,"","","",Set.of());
        f.entrance();f.port.complete();f.done(Operation.OPEN_BAZAAR);f.done(Operation.CLICK_SIGN);f.done(Operation.ENTER_INPUT);f.done(Operation.CLICK_TNT);f.done(Operation.CLICK_BUY_INSTANTLY);f.flow.tick();
        assertEquals(State.CLICK_BUY_ONE,f.flow.state());assertTrue(f.flow.blocker().orElseThrow().contains("receipt"));assertEquals(0,f.port.count(Operation.CLICK_BUY_ONE));
    }
    @Test void fullHotbarUsesInventoryHelperThenResourceRecheck(){
        var f=new Fixture();f.port.empty=Optional.of(false);f.entrance();f.port.complete();assertEquals(State.OPEN_INVENTORY,f.flow.state());
        f.done(Operation.OPEN_INVENTORY);f.action(Operation.FREE_SLOT);f.port.resource=Optional.of(2);f.port.complete();
        assertEquals(State.ROTATE_TO_PLACEABLE_GROUND,f.flow.state());assertEquals(0,f.port.count(Operation.MOVE_TNT_INTO_HOTBAR));
    }
    @Test void freedSlotWithoutResourceReturnsToBazaarAndNeverInventsMoveEdge(){
        var f=new Fixture();f.port.empty=Optional.of(false);f.entrance();f.port.complete();f.done(Operation.OPEN_INVENTORY);f.done(Operation.FREE_SLOT);
        assertEquals(State.OPEN_BAZAAR,f.flow.state());assertEquals(0,f.port.count(Operation.MOVE_TNT_INTO_HOTBAR));
    }
    @Test void inventoryFailureStopsInsteadOfClaimingPurchaseOrGateSuccess(){
        var f=new Fixture();f.port.empty=Optional.of(false);f.entrance();f.port.complete();f.done(Operation.OPEN_INVENTORY);f.action(Operation.FREE_SLOT);f.port.fail();
        assertTrue(f.flow.failure().orElseThrow().startsWith("FREE_SLOT"));assertEquals(0,f.combat);int n=f.port.issued.size();f.flow.tick();assertEquals(n,f.port.issued.size());
    }
    @Test void entrancePathFailureStopsButInsidePathFailureRetriesParentWarp(){
        var entrance=new Fixture();entrance.entrance();entrance.port.fail();assertTrue(entrance.flow.failure().isPresent());assertEquals(Child.TRAVEL,entrance.flow.child());
        var inside=new Fixture();inside.port.open=Optional.of(true);inside.entrance();inside.port.complete();inside.action(Operation.WALK_INSIDE);inside.port.fail();
        assertEquals(Child.WARP,inside.flow.child());assertTrue(inside.flow.failure().isEmpty());assertEquals(0,inside.combat);inside.action(Operation.PARENT_WARP);
    }
    @Test void missingResourceAfterPurchaseReturnsToParentWarp(){
        var f=new Fixture();f.buy();f.port.complete();f.flow.message("[verified receipt] Bought verified resource");f.done(Operation.CLOSE_BAZAAR);
        assertEquals(Child.WARP,f.flow.child());assertEquals(0,f.combat);
    }
    @Test void cancelledRunAndOldOperationCallbacksCannotCompleteNewRun(){
        var f=new Fixture();f.entrance();var old=f.port.callbacks.getLast();f.flow.stop();assertEquals(5,f.port.selected);f.port.selected=7;f.start();old.accept(Result.complete());
        assertEquals(Child.PREPARATION,f.flow.child());assertEquals(7,f.flow.originalSlot());assertEquals(0,f.combat);
    }
    @Test void oldCallbackCannotCompleteNewStateWithinSameRun(){
        var f=new Fixture();f.buy();var stale=f.port.callbacks.getLast();f.port.complete();f.flow.message("[verified receipt] Bought verified resource");
        stale.accept(Result.failed("late"));assertEquals(State.CLOSE_BAZAAR,f.flow.state());assertTrue(f.flow.failure().isEmpty());
    }
    @Test void buffDetourReturnsToSameParentTravelPrerequisite(){
        var f=new Fixture();f.action(Operation.PREPARE);f.port.complete(Continuation.PARENT_WARP);f.action(Operation.PARENT_WARP);f.port.complete(Continuation.BUFF);
        assertEquals(Child.BUFF,f.flow.child());f.done(Operation.BUFF);assertEquals(Child.TRAVEL,f.flow.child());f.action(Operation.TRAVEL_PREREQUISITE);
        assertEquals(0,f.combat);
    }
    @Test void actualUsablePreparationContinuationOwnsCombatWithoutStartingIngress(){
        var f=new Fixture();f.action(Operation.PREPARE);f.port.complete(Continuation.COMBAT);
        assertEquals(Child.COMBAT,f.flow.child());assertEquals(1,f.combat);assertEquals(0,f.port.count(Operation.WALK_INSIDE));assertEquals(0,f.port.count(Operation.PROFILE_ROUTE));
    }
    @Test void usablePreparationBuffContinuationReturnsToOwnedCombat(){
        var f=new Fixture();f.action(Operation.PREPARE);f.port.complete(Continuation.COMBAT_AFTER_BUFF);
        assertEquals(Child.BUFF,f.flow.child());f.done(Operation.BUFF);assertEquals(Child.COMBAT,f.flow.child());assertEquals(1,f.combat);
        assertEquals(0,f.port.count(Operation.TRAVEL_PREREQUISITE));
    }
    @Test void missingPreparationContinuationIsExplicitBlocker(){
        var f=new Fixture();f.done(Operation.PREPARE);assertTrue(f.flow.blocker().orElseThrow().contains("<15.0"));assertEquals(Child.PREPARATION,f.flow.child());assertEquals(0,f.combat);
    }
    @Test void unavailableGateObservationCannotBeTreatedAsOpenOrClosed(){
        var f=new Fixture();f.port.open=Optional.empty();f.entrance();f.port.complete();assertTrue(f.flow.blocker().orElseThrow().contains("gate observation"));assertEquals(0,f.combat);
    }
    @Test void gateCandidateIsCrackedBlockAndBeforeAfterTargetIsSameLiteral(){
        var f=new Fixture();var candidate=SvenCaveProfile.DARK_OAK.gate().add(1,-1,-1);f.port.cracked=List.of(candidate);f.port.open=Optional.of(true);
        f.entrance();var entrance=f.port.last();assertEquals(candidate,entrance.gateCandidate());f.port.complete();f.action(Operation.WALK_INSIDE);
        assertEquals(entrance.profile().pathTarget(),f.port.last().profile().pathTarget());assertEquals(new BlockPos(-390,97,-63),entrance.profile().pathTarget());
    }
    @Test void noCrackedCandidatesFallsBackToExactProfileGate(){
        var f=new Fixture();f.entrance();assertEquals(SvenCaveProfile.DARK_OAK.gate(),f.port.last().gateCandidate());
    }
    @Test void cancellationRestoresSlotEvenWithAutomaticSwapPolicy(){
        var f=new Fixture();f.flow.start(SvenCaveProfile.DARK_OAK,true,"r","i","",Set.of());f.port.resource=Optional.of(2);f.entrance();f.port.complete();assertEquals(2,f.port.selected);
        f.flow.stop();assertEquals(5,f.port.selected);assertEquals(0,f.combat);
    }
    @Test void throttleInvalidatesPendingPurchaseAndReturnsParentWarp(){
        var f=new Fixture();f.buy();var old=f.port.callbacks.getLast();f.flow.message("verified throttle");assertEquals(Child.WARP,f.flow.child());old.accept(Result.complete());assertEquals(Child.WARP,f.flow.child());assertEquals(0,f.combat);
    }
    @Test void missingResourceIdentityBlocksClosedGateAtExactEntranceState(){
        var f=new Fixture();f.port.resourceBlock=Optional.of("salted resource unbound");f.entrance();f.port.complete();assertEquals(State.WALKING_TO_ENTRANCE,f.flow.state());assertTrue(f.flow.blocker().orElseThrow().contains("salted resource"));
    }
}
