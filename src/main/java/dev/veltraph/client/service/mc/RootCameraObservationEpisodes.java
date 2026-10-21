package dev.veltraph.client.service.mc;

/** Observation ordering only. No token from this class authorizes an effect or identifies Root. */
final class RootCameraObservationEpisodes {
    private Episode active;
    final class Episode implements AutoCloseable {
        private final Episode previous;
        private boolean ambiguous,closed;
        private Episode(Episode previous){this.previous=previous;}
        boolean current(){return !closed&&!ambiguous&&active==this;}
        boolean unambiguous(){return !ambiguous;}
        void revoke(){ambiguous=true;}
        @Override public void close(){
            if(closed)return;
            closed=true;
            if(active==this)active=previous;
            else {ambiguous=true;for(var at=active;at!=null;at=at.previous)at.ambiguous=true;}
        }
    }
    Episode enter(){
        var previous=active;var next=new Episode(previous);
        if(previous!=null){next.ambiguous=true;for(var at=previous;at!=null;at=at.previous)at.ambiguous=true;}
        active=next;return next;
    }
}
