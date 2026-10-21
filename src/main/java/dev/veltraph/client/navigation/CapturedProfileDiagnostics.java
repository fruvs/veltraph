package dev.veltraph.client.navigation;

/** Cached observation counters only. This performs no custody, geometry or native gameplay read. */
public record CapturedProfileDiagnostics(String phase,int acceptedPhysicsCount,int acceptedJumpCount,
        int acceptedContactCount,String retirementCause) {
    public static CapturedProfileDiagnostics inactive(){return new CapturedProfileDiagnostics("INACTIVE",0,0,0,"");}
}
