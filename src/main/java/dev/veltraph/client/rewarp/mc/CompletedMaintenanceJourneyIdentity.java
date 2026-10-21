package dev.veltraph.client.rewarp.mc;

/** Data equality only, never a completed resume, Root transition, or native permission. */
final class CompletedMaintenanceJourneyIdentity {
    private CompletedMaintenanceJourneyIdentity(){}
    /** Data-only proposed metadata replacement; caller still owns every actual source/native check. */
    static <T> Detachment<T> detach(T retained,T selected,long retainedSerial,long currentGeneration,boolean exhausted){
        return matches(retained,selected,retainedSerial,currentGeneration,exhausted)?new Detachment<>(retained,retainedSerial):null;
    }
    static final class Detachment<T> {
        private final T retained;private final long serial;
        private Detachment(T retained,long serial){this.retained=retained;this.serial=serial;}
        boolean rawCurrent(T selected,long generation,boolean exhausted){return matches(retained,selected,serial,generation,exhausted);}
        T remaining(){return null;}
    }
    static boolean matches(Object retained,Object selected,long retainedSerial,long currentGeneration,boolean exhausted){
        return retained!=null&&retained==selected&&!exhausted&&retainedSerial>0&&retainedSerial==currentGeneration;
    }
}
