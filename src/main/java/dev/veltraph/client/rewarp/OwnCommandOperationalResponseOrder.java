package dev.veltraph.client.rewarp;
/** Sequencing observations only. This model has no receipt constructor, source grant, or native effect. */
public final class OwnCommandOperationalResponseOrder {
 private enum State { EMPTY,ENTERED,COMPLETE,CONSUMED,REFUSED }
 private final long baseline;private long origin=-1;private State state=State.EMPTY;
 public OwnCommandOperationalResponseOrder(long actualNativeSendIngressBaseline){baseline=actualNativeSendIngressBaseline;if(baseline<0||baseline==Long.MAX_VALUE)state=State.REFUSED;}
 public boolean begin(long nativeOrigin){if(state!=State.EMPTY||nativeOrigin<=baseline||nativeOrigin<0){state=State.REFUSED;return false;}origin=nativeOrigin;state=State.ENTERED;return true;}
 public boolean returned(long nativeOrigin,boolean normalFullHandler){if(state!=State.ENTERED||nativeOrigin!=origin||!normalFullHandler){state=State.REFUSED;return false;}state=State.COMPLETE;return true;}
 public boolean consume(long nativeOrigin){if(state!=State.COMPLETE||nativeOrigin!=origin){state=State.REFUSED;return false;}state=State.CONSUMED;return true;}
 public boolean complete(){return state==State.COMPLETE;}public boolean refused(){return state==State.REFUSED;}
 public void refuse(){state=State.REFUSED;}
}
