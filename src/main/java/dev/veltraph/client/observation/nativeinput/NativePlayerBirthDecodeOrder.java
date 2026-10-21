package dev.veltraph.client.observation.nativeinput;
/** Exact actual decode result/list/body chain; no current channel/read stack grants packet origin. */
final class NativePlayerBirthDecodeOrder {
 final Object decoder,buffer,list;Object packet;boolean decoded,added,normal,refused,consumed;
 NativePlayerBirthDecodeOrder(Object d,Object b,Object l){decoder=d;buffer=b;list=l;}
 synchronized boolean decoded(Object d,Object b,Object p,boolean actualOpcode,boolean returned){if(decoded||d!=decoder||b!=buffer||p==null||!actualOpcode||!returned)refused=true;if(!refused){decoded=true;packet=p;}return !refused;}
 synchronized boolean added(Object l,Object p,boolean returned){if(!decoded||added||l!=list||p!=packet||!returned)refused=true;if(!refused)added=true;return !refused;}
 synchronized void finish(boolean returned){if(!decoded||!added||!returned)refused=true;else normal=true;}
 synchronized boolean take(Object p){if(refused||!normal||consumed||p!=packet)return false;consumed=true;return true;}
 synchronized boolean current(){return !refused&&normal;}
}
