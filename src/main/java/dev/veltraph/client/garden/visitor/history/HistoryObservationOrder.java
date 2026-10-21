package dev.veltraph.client.garden.visitor.history;

/** A later-returning old invocation cannot overwrite a newer observed native event. Ordering is not authority. */
public final class HistoryObservationOrder {
    private HistoryObservationOrder() {}
    public static boolean newer(long previous,long incoming){return incoming>0&&(previous<0||incoming>previous);}
}
