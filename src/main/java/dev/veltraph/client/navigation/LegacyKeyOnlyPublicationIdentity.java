package dev.veltraph.client.navigation;
/** Keys-only initial reservation evidence; it never supplies a camera control ticket or gameplay permission. */
public final class LegacyKeyOnlyPublicationIdentity {
 private LegacyKeyOnlyPublicationIdentity(){}
 public record Evidence(LegacyPublicationSnapshotIdentity.State issued,LegacyPublicationSnapshotIdentity.State live,
                        boolean actualScopedKeyPublication,boolean cameraAttemptedOrQueued,boolean authenticSourceKeysRole,boolean finalLifecycleCurrent){}
 public static boolean mayRelease(Evidence e){return e!=null&&e.actualScopedKeyPublication()&&!e.cameraAttemptedOrQueued()
  &&e.authenticSourceKeysRole()&&e.finalLifecycleCurrent()&&LegacyPublicationSnapshotIdentity.same(e.issued(),e.live());}
}
