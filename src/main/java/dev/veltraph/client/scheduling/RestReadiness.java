package dev.veltraph.client.scheduling;

/** Verified source gate order. Hidden numeric guards are explicit policy data, never invented salts. */
public record RestReadiness(boolean capabilityEnabled,boolean boundaryCapable,boolean restOnlyAtRewarp,
                            boolean atRewarpBoundary,boolean higherPriorityOwned,boolean contestActive,
                            long contestRemainingSeconds,long minimumContestRemainingSeconds,
                            long sinceRewarpMillis,long recentRewarpGuardMillis){
    public boolean permits(){
        if(!capabilityEnabled||higherPriorityOwned)return false;
        if(boundaryCapable&&restOnlyAtRewarp&&!atRewarpBoundary)return false;
        if(minimumContestRemainingSeconds<0||recentRewarpGuardMillis<0)return false;
        if(contestActive&&contestRemainingSeconds<minimumContestRemainingSeconds)return false;
        return sinceRewarpMillis>=recentRewarpGuardMillis;
    }
}
