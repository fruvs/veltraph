package dev.veltraph.client.rewarp.mc;
/** Actual ACK submission body order; neither an ACK received by server nor landing authority. */
final class IssuedFarmAckBodyOrder {
 private final Object packet;private boolean entered,write,body,outer,refused,scheduled;
 IssuedFarmAckBodyOrder(Object packet){this.packet=packet;if(packet==null)refused=true;}
 boolean enter(Object value,boolean schedule){if(entered||value!=packet)refused=true;entered=true;scheduled=schedule;return !refused;}
 boolean written(Object value,boolean normal){if(!entered||scheduled||write||value!=packet||!normal)refused=true;if(!refused)write=true;return !refused;}
 boolean returned(Object value,boolean normal){if(!entered||body||value!=packet||!normal||(!scheduled&&!write))refused=true;if(!refused)body=true;return !refused;}
 boolean outer(Object value,boolean normal){if(!body||outer||value!=packet||!normal)refused=true;if(!refused)outer=true;return !refused;}
 boolean complete(){return !refused&&entered&&body&&outer;}void refuse(){refused=true;}
}
