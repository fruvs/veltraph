package dev.veltraph.client.navigation;

/** Caller workflow equality only. A token cannot grant native Root or camera permission. */
public final class RouteWorkflowEpoch {
    private long epoch;private boolean exhausted;
    public RouteWorkflowEpoch(){}
    RouteWorkflowEpoch(long initial){if(initial<0)throw new IllegalArgumentException("Negative epoch");epoch=initial;}
    public void advance(){if(epoch==Long.MAX_VALUE)exhausted=true;else epoch++;}
    public Snapshot snapshot(){return new Snapshot(this,epoch);}
    public boolean current(Snapshot exact){return !exhausted&&exact!=null&&exact.owner==this&&exact.epoch==epoch;}
    public static final class Snapshot {
        private final RouteWorkflowEpoch owner;private final long epoch;
        private Snapshot(RouteWorkflowEpoch owner,long epoch){this.owner=owner;this.epoch=epoch;}
    }
}
