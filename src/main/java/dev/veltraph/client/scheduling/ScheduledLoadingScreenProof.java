package dev.veltraph.client.scheduling;

/** Finite issued remote load authority; a screen class name alone is never provenance. */
public final class ScheduledLoadingScreenProof {
    private ScheduledLoadingScreenProof(){}
    public static boolean permits(boolean issued,boolean preSavedReturn,boolean sameTarget,boolean remoteLoadingScreen,
                                  Object originalNativeConnection,Object issuedNativeConnection,Object currentNativeConnection,
                                  Object listenerTracker,Object screenTracker){
        return issued&&preSavedReturn&&sameTarget&&remoteLoadingScreen
                &&issuedNativeConnection!=null&&currentNativeConnection==issuedNativeConnection
                &&currentNativeConnection!=originalNativeConnection
                &&listenerTracker!=null&&screenTracker==listenerTracker;
    }
}
