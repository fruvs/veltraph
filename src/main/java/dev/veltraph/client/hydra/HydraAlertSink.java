package dev.veltraph.client.hydra;

/**
 * Local-only presentation hooks for an anti-staff reaction: an on-screen title and a single sound ping.
 * Implemented by the MC adapter ({@code hydra/mc/McHydraAlertSink}); a no-op default is used in tests
 * and when Minecraft UI/audio is unavailable. Chat is handled separately via the existing
 * {@code ChatService} (so it shares the local-chat plumbing).
 *
 * <p>Every method is local client-side only — nothing is transmitted to the server or any network.
 */
public interface HydraAlertSink {

    /** Show a local title + subtitle (best-effort; must never crash — implementations degrade). */
    void showTitle(String title, String subtitle);

    /** Play a single local sound ping (best-effort; must never crash). */
    void playPing();

    /** A sink that does nothing (tests / headless / missing UI). */
    HydraAlertSink NONE = new HydraAlertSink() {
        @Override
        public void showTitle(String title, String subtitle) {
        }

        @Override
        public void playPing() {
        }
    };
}
