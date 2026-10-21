package dev.veltraph.client.rewarp.mc;
/** Exact causal native transport body order, never Root authority by itself. */
final class IssuedFarmTransportOrder {
 private final Object token,backend,rest;private boolean entered,returned,connected,incoming,player,entity,finished,refused;
 IssuedFarmTransportOrder(Object token,Object backend,Object rest){this.token=token;this.backend=backend;this.rest=rest;if(token==null||backend==null||rest==null)refused=true;}
 boolean disconnect(Object t,Object b,Object r,boolean normal,boolean absent){if(entered||t!=token||b!=backend||r!=rest||!normal||!absent)refused=true;if(!refused){entered=true;returned=true;}return !refused;}
 boolean connect(Object b,Object r,boolean normal,boolean genuineNewConnection){if(!returned||connected||b!=backend||r!=rest||!normal||!genuineNewConnection)refused=true;if(!refused)connected=true;return !refused;}
 boolean incoming(boolean ingress,boolean processor,boolean exactIssuedConnection){if(!connected||incoming||!ingress||!processor||!exactIssuedConnection)refused=true;if(!refused)incoming=true;return !refused;}
 boolean playerWritten(){if(!incoming||player)refused=true;if(!refused)player=true;return !refused;}
 boolean entityAdded(){if(!incoming||entity)refused=true;if(!refused)entity=true;return !refused;}
 boolean finish(boolean normal,boolean exactTracker){if(!incoming||!player||!entity||finished||!normal||!exactTracker)refused=true;if(!refused)finished=true;return !refused;}
 boolean complete(){return !refused&&returned&&connected&&incoming&&player&&entity&&finished;}
}
