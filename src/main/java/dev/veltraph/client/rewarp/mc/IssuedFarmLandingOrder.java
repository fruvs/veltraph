package dev.veltraph.client.rewarp.mc;
/** Pure incoming application order; never permission or an authenticated packet by itself. */
final class IssuedFarmLandingOrder {
 private final Object packet,listener,player,world,command;private boolean refused,entered,applied,ack,returned,consumed;
 IssuedFarmLandingOrder(Object packet,Object listener,Object player,Object world,Object command){this.packet=packet;this.listener=listener;this.player=player;this.world=world;this.command=command;if(packet==null||listener==null||player==null||world==null||command==null)refused=true;}
 boolean enter(Object p,Object l,Object actor,Object level,Object token){if(entered||p!=packet||l!=listener||actor!=player||level!=world||token!=command)refused=true;entered=true;return !refused;}
 boolean applied(Object p,boolean normal,boolean changed){if(!entered||applied||p!=packet||!normal||!changed)refused=true;if(!refused)applied=true;return !refused;}
 boolean acknowledged(Object p,boolean normal,boolean exactAck){if(!applied||ack||p!=packet||!normal||!exactAck)refused=true;if(!refused)ack=true;return !refused;}
 boolean returned(Object p,boolean normal){if(!ack||returned||p!=packet||!normal)refused=true;if(!refused)returned=true;return !refused;}
 boolean consume(Object token){if(refused||!returned||consumed||token!=command){refused=true;return false;}consumed=true;return true;}
 void refuse(){refused=true;}boolean complete(){return !refused&&entered&&applied&&ack&&returned&&!consumed;}
}
