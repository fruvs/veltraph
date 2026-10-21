package dev.veltraph.client.garden.jacob;

import dev.veltraph.client.config.*;
import java.util.*;

/** Genuine stored config revision data. This object alone grants no native effect or journal write. */
public final class JacobRewardJournalConfigFence {
    private final ConfigService origin;private final MapConfigService.StoredStateSnapshot stored;
    private JacobRewardJournalConfigFence(ConfigService origin,MapConfigService.StoredStateSnapshot stored){this.origin=origin;this.stored=stored;}
    public static Optional<JacobRewardJournalConfigFence> capture(ConfigService source){
        var snapshot=source instanceof MapConfigService map?map.storedStateSnapshot():source instanceof JsonConfigService json?json.storedStateSnapshot():null;
        return snapshot==null?Optional.empty():Optional.of(new JacobRewardJournalConfigFence(source,snapshot));
    }
    public boolean current(ConfigService exactSource){return exactSource==origin&&stored.current();}
}
