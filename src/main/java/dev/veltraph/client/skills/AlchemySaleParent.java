package dev.veltraph.client.skills;

import dev.veltraph.client.inventory.selling.*;
import java.util.*;

/** One Alchemy owner retains newly collected potions in FIFO order until every sale is acknowledged. */
public final class AlchemySaleParent {
    public enum State {
        IDLE, OPEN_SB_MENU, WAIT_SB_MENU, OPEN_COOKIE, WAIT_COOKIE, SELL_ITEM, CLOSE_SB_MENU, WAIT_CLOSE,
        COMPLETE, FAILED, CANCELLED
    }

    public record Scope(Object player, Object world, Object connection) {
        public boolean same(Scope other) {
            return other != null && player == other.player && world == other.world
                    && connection == other.connection && player != null && world != null && connection != null;
        }
    }

    public record Observation(Scope scope, Object nativeMenu, SellingObservation sale,
                              boolean gameplay, boolean cookieActive, int selectedSlot,
                              boolean ownedCookiePrediction) {
        public Observation(Scope scope, Object nativeMenu, SellingObservation sale,
                           boolean gameplay, boolean cookieActive, int selectedSlot) {
            this(scope, nativeMenu, sale, gameplay, cookieActive, selectedSlot, false);
        }
    }

    public interface Port {
        Observation read(AlchemySaleProfile profile);
        boolean openSkyblock(Observation expected, AlchemySaleProfile profile);
        boolean openCookie(Observation expected, AlchemySaleProfile profile);
        boolean act(SellingRuntime.Action action, SellingRule rule, AlchemySaleProfile profile);
        void closeOwned(Object nativeMenu, AlchemySaleProfile profile);
        void restoreSelectedSlot(int previous, int issued);
    }

    private final Port port;
    private final AlchemyPotionSaleCustody seller = new AlchemyPotionSaleCustody();
    private final List<SellingRuntime.SaleProof> proofs = new ArrayList<>();
    private State state = State.IDLE;
    private Scope scope;
    private AlchemySaleProfile profile;
    private List<SellingRule> fifo = List.of();
    private Map<Integer, String> ledger = Map.of();
    private int index, originalSlot, requestRevision;
    private Object ownedMenu, previousMenu;
    private long due, next;
    private boolean openerIssued;
    private String reason = "Idle";

    public AlchemySaleParent(Port port) { this.port = Objects.requireNonNull(port); }
    public State state() { return state; }
    public String reason() { return reason; }
    public int acknowledgedCount() { return proofs.size(); }
    public List<SellingRuntime.SaleProof> proofs() { return List.copyOf(proofs); }
    public boolean active() {
        return state != State.IDLE && state != State.COMPLETE && state != State.FAILED && state != State.CANCELLED;
    }
    public boolean allowsScreen(String title) {
        return active() && profile != null && (title.equals(profile.skyblockTitle()) || title.equals(profile.saleTitle()));
    }

    /** A collection receipt must add exactly one potion to an empty main slot. */
    public static SellingObservation.Stack collected(SellingObservation before, SellingObservation after) {
        var added = after.inventory().stream()
                .filter(s -> !s.empty() && !Objects.equals(before.ledger().get(s.inventoryIndex()), after.ledger().get(s.inventoryIndex())))
                .toList();
        if (added.size() != 1) throw new IllegalStateException("Collection changed multiple or no inventory slots");
        var stack = added.getFirst();
        var expected = new HashMap<>(before.ledger());
        if (expected.containsKey(stack.inventoryIndex()) || stack.inventoryIndex() < 9 || stack.inventoryIndex() > 35
                || stack.count() != 1 || !stack.registryId().equals("minecraft:potion") || stack.protectedItem() || stack.wearable()) {
            throw new IllegalStateException("New potion requires one previously empty main inventory slot");
        }
        expected.put(stack.inventoryIndex(), after.ledger().get(stack.inventoryIndex()));
        if (!expected.equals(after.ledger())) throw new IllegalStateException("Unrelated inventory changed during potion collection");
        return stack;
    }

    public void start(AlchemySaleProfile profile, List<SellingObservation.Stack> collected, long tick) {
        if (active()) throw new IllegalStateException("Alchemy sale parent already active");
        this.profile = profile;
        var observed = port.read(profile);
        scope = observed.scope();
        originalSlot = observed.selectedSlot();
        openerIssued = false;
        ownedMenu = null;
        previousMenu = observed.nativeMenu();
        index = 0;
        proofs.clear();


        var indices = new HashSet<Integer>();
        var rules = new ArrayList<SellingRule>();
        for (var stack : collected) {
            if (!stack.registryId().equals("minecraft:potion") || stack.count() != 1 || !indices.add(stack.inventoryIndex())) {
                throw new IllegalArgumentException("Exact distinct newly collected potion FIFO required");
            }
            rules.add(profile.rule(stack));
        }
        if (rules.isEmpty() || rules.size() > 3) throw new IllegalArgumentException("One batch of collected potions required");
        fifo = List.copyOf(rules);
        ledger = observed.sale().ledger();
        if (!scope.same(observed.scope()) || !observed.gameplay() || !observed.sale().cursorEmpty() || !observed.cookieActive()
                || rules.stream().anyMatch(r -> !r.matches(observed.sale().inventoryStack(r.inventoryIndex()).orElse(null)))) {
            fail("Alchemy sale start has no exact native scope, cookie or potion custody");
            return;
        }
        phase(State.OPEN_SB_MENU, tick);
        reason = "Collected potion FIFO retained by Alchemy owner";
    }

    private void phase(State next, long tick) {
        state = next;
        due = tick + profile.timeoutTicks();
        this.next = tick + profile.delayTicks();
    }

    public void tick(long tick) {
        if (!active()) return;
        var observed = port.read(profile);
        if (!scope.same(observed.scope())) { fail("Alchemy sale player/world/connection replaced"); return; }
        if (tick > due) { fail("Alchemy sale receipt/menu timed out; no retry"); return; }
        if (!observed.cookieActive()) { fail("Fresh native cookie readiness lost"); return; }
        if (openerIssued && observed.selectedSlot() != profile.openerSlot()) {
            fail("Alchemy opener selected slot changed externally");
            return;
        }
        if (state == State.SELL_ITEM) { sell(observed, tick); return; }
        if (state == State.CLOSE_SB_MENU || state == State.WAIT_CLOSE) { close(observed, tick); return; }

        boolean ownedPrediction = state == State.WAIT_COOKIE && observed.ownedCookiePrediction();
        if ((!observed.sale().cursorEmpty() && !ownedPrediction) || !observed.sale().ledger().equals(ledger)) {
            fail("Alchemy ingress cursor or protected inventory changed");
            return;
        }
        if (tick < next) return;
        switch (state) {
            case OPEN_SB_MENU -> openSkyblock(observed, tick);
            case WAIT_SB_MENU -> waitSkyblock(observed, tick);
            case OPEN_COOKIE -> openCookie(observed, tick);
            case WAIT_COOKIE -> waitCookie(observed, tick);
            default -> fail("Invalid Alchemy sale parent state");
        }
    }

    private void openSkyblock(Observation observed, long tick) {
        if (!observed.gameplay()) { fail("Close owned stand before Alchemy sale ingress"); return; }
        previousMenu = observed.nativeMenu();
        if (!port.openSkyblock(observed, profile)) { fail("Exact native SkyBlock opener changed"); return; }
        openerIssued = true;
        phase(State.WAIT_SB_MENU, tick);
    }

    private void waitSkyblock(Observation observed, long tick) {
        var menu = observed.sale().menu();
        if (menu == null) {
            if (!observed.gameplay()) fail("Unexpected Alchemy ingress screen");
            return;
        }
        if (observed.nativeMenu() == previousMenu || !menu.title().equals(profile.skyblockTitle())) {
            fail("Unissued or wrong SkyBlock menu");
            return;
        }
        if (menu.revision() < 1) return;
        ownedMenu = observed.nativeMenu();
        phase(State.OPEN_COOKIE, tick);
    }

    private void openCookie(Observation observed, long tick) {
        var menu = observed.sale().menu();
        if (observed.nativeMenu() != ownedMenu || menu == null || !menu.title().equals(profile.skyblockTitle())) {
            fail("SkyBlock menu object changed");
            return;
        }
        previousMenu = ownedMenu;
        requestRevision = menu.revision();
        if (!port.openCookie(observed, profile)) { fail("Exact cookie slot/icon/lore/revision changed"); return; }
        phase(State.WAIT_COOKIE, tick);
    }

    private void waitCookie(Observation observed, long tick) {
        var menu = observed.sale().menu();
        if (menu == null) { fail("Cookie request menu closed"); return; }
        if (menu.title().equals(profile.skyblockTitle()) && observed.nativeMenu() == previousMenu) return;
        if (!menu.title().equals(profile.saleTitle())
                || observed.nativeMenu() == previousMenu && menu.revision() == requestRevision) {
            fail("Unacknowledged or foreign cookie sale menu");
            return;
        }
        if (menu.revision() < 1) return;
        if (!observed.sale().cursorEmpty()) { fail("Cookie sale menu has unacknowledged cursor"); return; }
        ownedMenu = observed.nativeMenu();
        seller.start(observed.sale(), fifo.get(index), profile, tick);
        if (seller.state() == AlchemyPotionSaleCustody.State.FAILED) { fail("Exact potion sale quote: " + seller.reason()); return; }
        phase(State.SELL_ITEM, tick);
    }

    private void sell(Observation observed, long tick) {
        if (observed.sale().menu() != null && observed.nativeMenu() != ownedMenu) {
            fail("Native Alchemy sale container replaced");
            return;
        }
        var action = seller.tick(observed.sale(), tick);
        if (action.isPresent()) {
            var request = action.get();
            var fresh = port.read(profile);
            boolean accepted = scope.same(fresh.scope()) && fresh.cookieActive() && fresh.nativeMenu() == ownedMenu
                    && seller.valid(request, fresh.sale()) && port.act(request, fifo.get(index), profile);
            seller.applied(request, accepted, tick);
        }
        if (seller.state() == AlchemyPotionSaleCustody.State.ACKNOWLEDGED) {
            proofs.add(seller.proof());
            var after = new HashMap<>(ledger);
            after.remove(fifo.get(index).inventoryIndex());
            ledger = Map.copyOf(after);
            index++;
            if (index == fifo.size()) {
                phase(State.CLOSE_SB_MENU, tick);
                reason = "Potion FIFO drained; close the same owned cookie menu";
            } else {
                seller.start(observed.sale(), fifo.get(index), profile, tick);
                due = tick + profile.timeoutTicks();
                reason = "Next exact FIFO potion in the same cookie menu";
                if (seller.state() == AlchemyPotionSaleCustody.State.FAILED) fail(seller.reason());
            }
        } else if (seller.state() == AlchemyPotionSaleCustody.State.FAILED) {
            fail("Potion sale: " + seller.reason());
        }
    }

    private void close(Observation observed, long tick) {
        if (!observed.sale().cursorEmpty() || !observed.sale().ledger().equals(ledger)
                || observed.sale().coins() == null || observed.sale().coins().compareTo(proofs.getLast().purseAfter()) != 0) {
            fail("Cookie close cursor, purse or protected inventory changed");
            return;
        }
        if (state == State.WAIT_CLOSE && observed.gameplay() && observed.sale().menu() == null) {
            state = State.COMPLETE;
            ownedMenu = null;
            reason = "FIFO potion sales and one cookie-menu close acknowledged; same Alchemy owner may open next stand";
            restore();
            return;
        }
        if (observed.nativeMenu() != ownedMenu || observed.sale().menu() == null
                || !observed.sale().menu().title().equals(profile.saleTitle())) {
            fail("Cookie close lost native menu ownership");
            return;
        }
        if (state == State.WAIT_CLOSE || tick < next) return;
        var fresh = port.read(profile);
        if (!scope.same(fresh.scope()) || fresh.nativeMenu() != ownedMenu || !fresh.cookieActive()
                || !fresh.sale().equals(observed.sale())) {
            fail("Cookie close observation changed before dispatch");
            return;
        }
        port.closeOwned(ownedMenu, profile);
        phase(State.WAIT_CLOSE, tick);
    }

    public void onMessage(String text) { if (active()) seller.onMessage(text); }
    private void restore() { if (openerIssued) port.restoreSelectedSlot(originalSlot, profile.openerSlot()); }
    private void fail(String why) { state = State.FAILED; reason = why; cleanup(); }
    public void cancel() {
        if (!active()) return;
        state = State.CANCELLED;
        reason = "Alchemy owner stopped; issued sale is never retried or credited";
        cleanup();
    }
    private void cleanup() {
        if (profile == null) return;
        var current = port.read(profile);
        if (scope != null && scope.same(current.scope()) && current.nativeMenu() == ownedMenu && current.sale().cursorEmpty()) {
            port.closeOwned(ownedMenu, profile);
        }
        if (scope != null && scope.same(current.scope())) restore();
    }
}
