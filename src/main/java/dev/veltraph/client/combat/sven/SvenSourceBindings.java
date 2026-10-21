package dev.veltraph.client.combat.sven;

import java.util.function.Consumer;

/**
 * Explicit source-helper boundary. Implementations must own their action acknowledgments and
 * cancellation, preserve the Request ticket, and use the selected profile's actual route.
 * A string config value identifies a recovered binding; it does not implement one.
 */
public interface SvenSourceBindings {
    void begin(SvenIngressPort.Request request,Consumer<SvenIngressPort.Result> completed);
    default java.util.Optional<SvenPreparationPolicy.Observation> preparationObservation(){return java.util.Optional.empty();}
    default SvenIngressPort.Result preparationContinuation(SvenIngressPort.Result result){return result;}
    default boolean permitsNativeRebind(){return false;}
    default java.util.Optional<SvenWarpPermit> pendingWarpPermit(){return java.util.Optional.empty();}
    default boolean commitNativeWarp(SvenWarpPermit permit){return false;}
    default boolean awaitingNativeArrival(){return false;}
    default void tick(){}
    default void cancel(){}
    default boolean allowsScreen(String title){return false;}
    default boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){return false;}
    static SvenSourceBindings unbound(){return (request,completed)->completed.accept(SvenIngressPort.Result.blocked(switch(request.operation()){
        case PREPARE -> "shared preparation prerequisite/area/path helper and strict anchor distance <15.0 continuation (SVEN_SOURCE_BINDING_PROFILE) unresolved";
        case PARENT_WARP, WARP_ROUTE_START -> "current salted warp/helper acknowledgment (binding profile warpCommand) unresolved";
        case BUFF, TRAVEL_PREREQUISITE -> "shared buff gate and travel continuation (binding profile buff signals/menu) unresolved";
        case PROFILE_ROUTE -> "actual "+request.profile()+" TreeType route output / movement semantics (binding profile + source literal routes) unresolved";
        case ENTER_INPUT -> "current Bazaar search/sign query and acknowledgment (binding profile bazaar.searchInput) unresolved";
        case FREE_SLOT, MOVE_TNT_INTO_HOTBAR -> "exact tool/sword predicate, free-slot selection, held-item policy and inventory move receipts (binding profile inventory) unresolved";
        default -> "current source menu/helper identity, slot selector and action acknowledgment (binding profile bazaar) unresolved for "+request.operation();
    }));}
}
