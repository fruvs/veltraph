package dev.veltraph.client.observation.nativeinput;
/** Native upstream cause identity, never a public handler/current-channel proof. */
final class NativePlayerBirthPipelineOrder {
 final Object handler,context,packet;private boolean source,entered,stamped,refused,normal;
 NativePlayerBirthPipelineOrder(Object h,Object c,Object p){handler=h;context=c;packet=p;}
 synchronized boolean nativeEnter(Object h,Object c,Object p,boolean exactWovenEntry,boolean actualTransportRead,boolean actualPipelineOpcode,boolean eventLoop){if(entered||h!=handler||c!=context||p!=packet||!exactWovenEntry||!actualTransportRead||!actualPipelineOpcode||!eventLoop)refused=true;if(!refused){source=true;entered=true;}return !refused;}
 synchronized boolean stamp(Object h,Object c,Object p){if(!source||!entered||stamped||h!=handler||c!=context||p!=packet)refused=true;if(!refused)stamped=true;return !refused;}
 synchronized void refuse(){refused=true;}
 synchronized void returned(boolean value){if(!entered||!value)refused=true;else normal=true;}
 synchronized boolean usableQueued(){return !refused&&stamped;}
 synchronized boolean complete(){return !refused&&stamped&&normal;}
}
