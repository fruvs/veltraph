package dev.veltraph.client.galatea;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/** Source outer history may record before native inner queue readiness refuses. */
public final class OwnedNativeUseAdmission {
    private OwnedNativeUseAdmission() {}
    public static boolean ready(OwnedNativeUseTicket.Frame current,long originalDeadlineMillis,
            BooleanSupplier sourceThrottle,IntSupplier pendingClicks,BooleanSupplier innerReadiness){
        if(!OwnedNativeUseTicket.eligible(current,originalDeadlineMillis)||sourceThrottle==null)return false;
        if(!sourceThrottle.getAsBoolean())return false;
        return pendingClicks.getAsInt()==0&&innerReadiness.getAsBoolean();
    }
}
