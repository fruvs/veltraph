package dev.veltraph.client.combat.sven;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Each callback acknowledges its actual operation; issuing an action alone is not completion. */
public interface SvenIngressPort {
    enum Operation { PREPARE, PARENT_WARP, BUFF, TRAVEL_PREREQUISITE, WARP_ROUTE_START, PROFILE_ROUTE, WALK_ENTRANCE,
        OPEN_BAZAAR, CLICK_SIGN, ENTER_INPUT, CLICK_TNT, CLICK_BUY_INSTANTLY, CLICK_BUY_ONE,
        CLOSE_BAZAAR, OPEN_INVENTORY, FREE_SLOT, MOVE_TNT_INTO_HOTBAR, CLOSE_INVENTORY,
        ROTATE, USE_RESOURCE, PREPARE_INSIDE_TOOL, WALK_INSIDE }
    enum Outcome { COMPLETE, FAILED, BLOCKED }
    enum Continuation { DEFAULT, TRAVEL, PARENT_WARP, COMBAT, BUFF, COMBAT_AFTER_BUFF }
    record Result(Outcome outcome,Continuation continuation,String reason) {
        public static Result complete(){return new Result(Outcome.COMPLETE,Continuation.DEFAULT,"");}
        public static Result complete(Continuation next){return new Result(Outcome.COMPLETE,next,"");}
        public static Result failed(String reason){return new Result(Outcome.FAILED,Continuation.DEFAULT,reason);}
        public static Result blocked(String reason){return new Result(Outcome.BLOCKED,Continuation.DEFAULT,reason);}
    }
    record Ticket(long run,long serial) {}
    record Request(Operation operation,Ticket ticket,SvenCaveProfile profile,BlockPos gateCandidate,Vec3 aimTarget,int resourceSlot) {}
    /** Empty means observation is unavailable, never equivalent to closed or open. */
    Optional<Boolean> gatePassable(BlockPos gate);
    Optional<Integer> resourceHotbarSlot();
    default Optional<String> resourceReadiness(){return Optional.empty();}
    Optional<Boolean> hasEmptyHotbarSlot();
    List<BlockPos> crackedGateCandidates(BlockPos gate);
    Vec3 eyePosition();
    int selectedSlot();
    void selectSlot(int slot);
    void begin(Request request,Consumer<Result> completed);
    void tick();
    /** Cancel all callbacks, navigation, rotation, menus/actions owned by this run; release inputs. */
    void cancel();
}
