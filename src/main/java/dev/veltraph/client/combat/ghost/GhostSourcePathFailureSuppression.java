package dev.veltraph.client.combat.ghost;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.lang.ref.WeakReference;

/** Original failed-path target UUID suppression; neither ordinary attacks nor protected-chat blacklist populate it. */
public final class GhostSourcePathFailureSuppression {
    public static final long SOURCE_WINDOW_MS=60000;
    public static final long SOURCE_EXPIRY_MS=600000;
    private static final GhostSourcePathFailureSuppression RUNTIME=new GhostSourcePathFailureSuppression();
    public record Scope(Object job,Object parent,Object child,long generation,Object player,Object world,Object connection){
        public Scope {if(job==null||parent==null||child==null||player==null||world==null||connection==null||generation<0)
            throw new IllegalArgumentException("Actual issued path job/handler/PWC required");}
        boolean same(Scope s){return s!=null&&job==s.job&&parent==s.parent&&child==s.child&&generation==s.generation
                &&player==s.player&&world==s.world&&connection==s.connection;}
    }
    public final class IssuedJob {
        private final Scope scope;private boolean consumed;
        private IssuedJob(Scope scope){this.scope=scope;}
        public boolean completeFailure(Scope actualCurrentJob,UUID actualTargetUuid,boolean pathPresent,long now){
            if(consumed)return false;consumed=true;
            if(!scope.same(actualCurrentJob))return false;
            if(pathPresent||actualTargetUuid==null)return false;
            entries.put(actualTargetUuid,now);changed(actualTargetUuid);return true;
        }
        public void revoke(){consumed=true;}
    }
    private static final class Revision {long value;}
    public final class Stamp {
        private final UUID uuid;private final Revision revision;private final long value;
        private Stamp(UUID uuid,Revision revision){this.uuid=uuid;this.revision=revision;value=revision.value;}
        public boolean current(){return revision.value==value;}
        public boolean suppressedAt(long now){if(!current())throw new IllegalStateException("Source path-suppression entry changed");return suppressed(uuid,now);}
    }
    private final Map<UUID,Long> entries=new HashMap<>();
    private final Map<UUID,WeakReference<Revision>> observedRevisions=new HashMap<>();
    public Stamp capture(UUID uuid){Objects.requireNonNull(uuid);var reference=observedRevisions.get(uuid);var revision=reference==null?null:reference.get();
        if(revision==null){revision=new Revision();observedRevisions.put(uuid,new WeakReference<>(revision));}return new Stamp(uuid,revision);}
    private void changed(UUID uuid){var reference=observedRevisions.get(uuid);var revision=reference==null?null:reference.get();if(revision!=null)revision.value++;}
    public static GhostSourcePathFailureSuppression runtime(){return RUNTIME;}
    public IssuedJob issue(Scope actualIssued){return new IssuedJob(Objects.requireNonNull(actualIssued));}
    public boolean suppressed(UUID nativeUuid,long now){Long stamp=entries.get(Objects.requireNonNull(nativeUuid));return stamp!=null&&now-stamp<SOURCE_WINDOW_MS;}
    /** Explicit source cleanup only; source global map persists across module/world changes until expiry/hook. */
    public void cleanupExpired(long now){entries.entrySet().removeIf(e->{if(now-e.getValue()>SOURCE_EXPIRY_MS){changed(e.getKey());return true;}return false;});
        observedRevisions.entrySet().removeIf(e->e.getValue().get()==null);}
}
