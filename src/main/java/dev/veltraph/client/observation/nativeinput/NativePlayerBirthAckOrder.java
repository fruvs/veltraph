package dev.veltraph.client.observation.nativeinput;
/** Actual initialization apply/ACK native body evidence; normal wrapper alone never proves submission. */
final class NativePlayerBirthAckOrder {
 private final Object packet;private Object ack;private boolean position,body,write,scheduled,bodyReturned,outer,refused;
 NativePlayerBirthAckOrder(Object packet){this.packet=packet;if(packet==null)refused=true;}
 boolean position(Object p,boolean normal){if(position||p!=packet||!normal)refused=true;if(!refused)position=true;return !refused;}
 boolean ack(Object a,boolean exact){if(!position||ack!=null||a==null||!exact)refused=true;if(!refused)ack=a;return !refused;}
 boolean body(Object a,boolean schedule){if(ack!=a||body)refused=true;if(!refused){body=true;scheduled=schedule;}return !refused;}
 boolean write(Object a,boolean normal){if(ack!=a||!body||scheduled||write||!normal)refused=true;if(!refused)write=true;return !refused;}
 boolean bodyReturned(Object a,boolean normal){if(ack!=a||!body||bodyReturned||!normal||(!scheduled&&!write))refused=true;if(!refused)bodyReturned=true;return !refused;}
 boolean returned(Object a,boolean normal){if(ack!=a||!bodyReturned||outer||!normal)refused=true;if(!refused)outer=true;return !refused;}
 boolean complete(){return !refused&&position&&ack!=null&&bodyReturned&&outer;}void refuse(){refused=true;}
}
