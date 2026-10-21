package dev.veltraph.client.service.mc;
/** Native primary identity survives any passive cleanup/suppression bookkeeping failure. */
final class SavedMaintenanceFailurePreservation {
    private SavedMaintenanceFailurePreservation() {}
    static void suppressWithoutReplacing(Throwable primary,Throwable observer){
        if(primary==null||observer==null||primary==observer)return;
        try{primary.addSuppressed(observer);}catch(Throwable unavailable){/* The real primary remains primary. */}
    }
}
