package dev.veltraph.client.garden.visitor.history;

/** Duck surface implemented only by actual transformed Connection/ClientPacketListener native wrappers.
 * Its token exists only during that target's exact original invocation; it is not a public evidence record. */
public interface VisitorHistoryNativeCallCarrier {
    Object veltraph$claimHistoryNativeCall(Object packet,Object argument1,Object argument2,int kind);
    boolean veltraph$historyNativeCallCurrent(Object token,Object packet,Object argument1,Object argument2,int kind,boolean normalReturned);
}
