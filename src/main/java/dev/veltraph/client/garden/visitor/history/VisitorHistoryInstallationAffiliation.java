package dev.veltraph.client.garden.visitor.history;

/** Refusal predicate only. Native install still requires Minecraft's real isSameThread on ordinary ticks. */
public final class VisitorHistoryInstallationAffiliation {
    private VisitorHistoryInstallationAffiliation(){}
    public static boolean current(Object expectedClient,Object expectedLocation,Object expectedGeneration,
            Object currentClient,Object currentLocation,Object currentGeneration,boolean actualClientThread,boolean stopped){
        return actualClientThread&&!stopped&&expectedClient!=null&&expectedLocation!=null&&expectedGeneration!=null
                &&expectedClient==currentClient&&expectedLocation==currentLocation&&expectedGeneration==currentGeneration;
    }
}
