package dev.veltraph.client.observation.nativeinput;
/** Exact captured native scheduled task causality; never a current-pose/Root enrollment. */
final class NativePlayerBirthTaskOrder {
 final Object packet,connection,loop,task;boolean entered,write,returned,refused;
 NativePlayerBirthTaskOrder(Object p,Object c,Object l,Object t){packet=p;connection=c;loop=l;task=t;if(p==null||c==null||l==null||t==null)refused=true;}
 boolean enter(Object p,Object c,Object l,Object t,boolean actualEventLoop){if(entered||p!=packet||c!=connection||l!=loop||t!=task||!actualEventLoop)refused=true;if(!refused)entered=true;return !refused;}
 boolean written(Object p,Object c,Object l,boolean normal){if(!entered||write||p!=packet||c!=connection||l!=loop||!normal)refused=true;if(!refused)write=true;return !refused;}
 boolean returned(boolean normal){if(!entered||!write||returned||!normal)refused=true;if(!refused)returned=true;return !refused;}
 boolean complete(){return !refused&&entered&&write&&returned;}
}
