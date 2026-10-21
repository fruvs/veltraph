package dev.veltraph.client.mining.vein;

import dev.veltraph.client.world.BlockPos;
import java.util.Objects;

/** Ordinary attack lifecycle; only a later server-ingress AIR receipt can confirm removal. */
public final class VeinBreakSession {
    public enum State { IDLE, AIMING, BREAKING, WAITING_RECEIPT, REMOVED, REPLACED, STOPPED, TIMED_OUT }
    public record Scope(Object player, Object world, Object connection, Object owner, Object cameraOwner,
            Object configStamp, Object toolComponents, int slot, long activation) {
        public Scope {
            Objects.requireNonNull(player);Objects.requireNonNull(world);Objects.requireNonNull(connection);
            Objects.requireNonNull(owner);Objects.requireNonNull(cameraOwner);Objects.requireNonNull(configStamp);
            Objects.requireNonNull(toolComponents);
            if (slot < 0 || slot > 8 || activation < 0) throw new IllegalArgumentException("Invalid custody");
        }
        public boolean same(Scope other) {
            return other != null && player == other.player && world == other.world && connection == other.connection
                    && owner == other.owner && cameraOwner == other.cameraOwner && activation == other.activation
                    && slot == other.slot && configStamp.equals(other.configStamp)
                    && toolComponents.equals(other.toolComponents);
        }
    }
    public record Frame(Scope scope, BlockPos cell, String state, boolean loaded, boolean reachable,
            boolean exactRayHit, boolean harvestTool, boolean capacity, boolean safe, boolean screenClosed,
            boolean manualQuiet, boolean sourceReceipt, long receiptSerial, long observedAtMillis) {}
    private State state = State.IDLE;
    private Scope scope;
    private BlockPos cell;
    private String originalState;
    private long selectedAt, issuedSerial, issuedAt, lastNow, timeout,lastGeometryObserved;
    private boolean attack,observedAirPending;
    public boolean begin(Frame frame, long now, long timeoutMillis) {
        if (state == State.AIMING || state == State.BREAKING || state == State.WAITING_RECEIPT) return false;
        if (frame == null || frame.cell == null || MiningMaterial.classify(frame.state).isEmpty()
                || !valid(frame,now) || timeoutMillis <= 0 || !frame.reachable) return false;
        scope = frame.scope; cell = frame.cell; originalState = frame.state;
        selectedAt = lastNow = lastGeometryObserved = now; timeout = timeoutMillis; issuedSerial = frame.receiptSerial;
        issuedAt = -1; attack = false; observedAirPending=false; state = State.AIMING; return true;
    }
    /** Partial native geometry is unknown, not an unsafe world witness. No partial frame reaches tick. */
    public State tickGeometry(Frame frame,long now,boolean geometryComplete){
        if(now<lastGeometryObserved){attack=false;state=State.STOPPED;return state;}
        lastGeometryObserved=now;
        if(observedAirPending&&frame!=null&&frame.state()!=null&&!air(frame.state())){attack=false;state=State.STOPPED;return state;}
        if(geometryComplete)return tick(frame,now);
        attack=false;
        if(state!=State.AIMING&&state!=State.BREAKING&&state!=State.WAITING_RECEIPT)return state;
        if(now<lastNow||!validIndependent(frame,now)||!scope.same(frame.scope)||!cell.equals(frame.cell)){state=State.STOPPED;return state;}
        if(now-selectedAt>=timeout){state=State.TIMED_OUT;return state;}
        if(!originalState.equals(frame.state)){
            if(air(frame.state)&&issuedAt>=0){observedAirPending=true;state=State.WAITING_RECEIPT;return state;}
            state=State.STOPPED;return state;
        }
        // Do not reset selectedAt, lastNow, issuedAt, serial or deadline. Next complete frame remains fresh.
        return state;
    }
    public State tick(Frame frame, long now) {
        attack = false;
        if (state != State.AIMING && state != State.BREAKING && state != State.WAITING_RECEIPT) return state;
        if (now < lastNow || !valid(frame,now) || !scope.same(frame.scope) || !cell.equals(frame.cell)) {
            state = State.STOPPED; return state;
        }
        lastNow = now;
        if(observedAirPending&&!air(frame.state)){state=State.STOPPED;return state;}
        if (now - selectedAt >= timeout) { state = State.TIMED_OUT; return state; }
        if (!originalState.equals(frame.state)) {
            if (air(frame.state) && issuedAt >= 0 && !frame.sourceReceipt) {
                observedAirPending=true;state = State.WAITING_RECEIPT; return state;
            }
            if (issuedAt >= 0 && frame.sourceReceipt && frame.receiptSerial > issuedSerial
                    && frame.observedAtMillis >= issuedAt) {
                state = air(frame.state) ? State.REMOVED : State.REPLACED;
            } else state = State.STOPPED;
            return state;
        }
        if (!frame.reachable || !frame.exactRayHit) { state = State.AIMING; return state; }
        if (issuedAt < 0) { issuedAt = now; issuedSerial = frame.receiptSerial; }
        attack = true; state = State.BREAKING; return state;
    }
    public void stop() { attack = false; state = State.STOPPED; }
    public boolean attackRequested() { return attack; }
    public State state() { return state; }
    public boolean permits(BlockPos actual, Frame frame, long now) {
        return attack && state == State.BREAKING && cell.equals(actual) && valid(frame,now)
                && scope.same(frame.scope) && originalState.equals(frame.state) && cell.equals(frame.cell)
                && frame.reachable && frame.exactRayHit && now >= lastNow;
    }
    /** Known nongeometry observations never become unknown just because native ray/support probing yielded. */
    private static boolean validIndependent(Frame frame,long now){
        return frame!=null&&frame.scope!=null&&frame.state!=null&&frame.harvestTool&&frame.capacity
                &&frame.screenClosed&&frame.manualQuiet&&now>=0&&frame.observedAtMillis>=0
                &&frame.observedAtMillis<=now&&now-frame.observedAtMillis<=250&&frame.receiptSerial>=0;
    }
    private static boolean valid(Frame frame, long now) {
        return frame != null && frame.scope != null && frame.state != null && frame.loaded && frame.harvestTool
                && frame.capacity && frame.safe && frame.screenClosed && frame.manualQuiet && now >= 0
                && frame.observedAtMillis >= 0 && frame.observedAtMillis <= now
                && now - frame.observedAtMillis <= 250 && frame.receiptSerial >= 0;
    }
    private static boolean air(String state) {
        return "minecraft:air".equals(state) || "minecraft:cave_air".equals(state) || "minecraft:void_air".equals(state);
    }
}
