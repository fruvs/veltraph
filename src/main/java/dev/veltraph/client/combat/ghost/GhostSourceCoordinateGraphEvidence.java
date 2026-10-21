package dev.veltraph.client.combat.ghost;

/** Cached actual coordinate worker result only. This observation grants no native ownership or movement permission. */
public record GhostSourceCoordinateGraphEvidence(int cap,GhostSourceWalkWorker.Status graphStatus,int visitedCount,
        GhostSourceWalkFrontier.Cell start,GhostSourceWalkFrontier.Cell endpoint) {
    public static GhostSourceCoordinateGraphEvidence awaiting(int actualCap,GhostSourceWalkFrontier.Cell actualStart,GhostSourceWalkFrontier.Cell actualEnd){
        return new GhostSourceCoordinateGraphEvidence(actualCap,null,-1,actualStart,actualEnd);
    }
    public static GhostSourceCoordinateGraphEvidence completed(int actualCap,GhostSourceWalkFrontier.Cell actualStart,
            GhostSourceWalkFrontier.Cell actualEnd,GhostSourceWalkWorker.Result actualResult){
        java.util.Objects.requireNonNull(actualResult);
        return new GhostSourceCoordinateGraphEvidence(actualCap,actualResult.status(),actualResult.visitedCount(),actualStart,actualEnd);
    }
}
