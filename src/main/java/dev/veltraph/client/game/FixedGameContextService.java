package dev.veltraph.client.game;

/**
 * A {@link GameContextService} holding a fixed/settable {@link GameContext}. Pure — used as a safe
 * default (reports "not in SkyBlock") and in unit tests.
 */
public final class FixedGameContextService implements GameContextService {

    private volatile GameContext context;

    public FixedGameContextService() {
        this(GameContext.NONE);
    }

    public FixedGameContextService(GameContext context) {
        this.context = context;
    }

    public void set(GameContext context) {
        this.context = context;
    }

    @Override
    public GameContext current() {
        return context;
    }
}
