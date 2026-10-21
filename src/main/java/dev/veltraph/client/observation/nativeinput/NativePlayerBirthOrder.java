package dev.veltraph.client.observation.nativeinput;
/** Actual birth opcode bookkeeping, not a Root permission or current-pose enrollment. */
final class NativePlayerBirthOrder {
 private final Object packet,listener,connection;private Object player;private boolean refused,dispatch,created,installed,added,returned,position,finished,consumed;
 NativePlayerBirthOrder(Object packet,Object listener,Object connection){this.packet=packet;this.listener=listener;this.connection=connection;if(packet==null||listener==null||connection==null)refused=true;}
 boolean dispatch(Object p,Object l,Object c,boolean actualIngress){if(dispatch||p!=packet||l!=listener||c!=connection||!actualIngress)refused=true;if(!refused)dispatch=true;return !refused;}
 boolean created(Object p,boolean normal){if(!dispatch||created||p==null||!normal)refused=true;if(!refused){created=true;player=p;}return !refused;}
 boolean installed(Object p,boolean normal){if(!created||installed||p!=player||!normal)refused=true;if(!refused)installed=true;return !refused;}
 boolean preAddInstalled(Object exactPlayer){return !refused&&dispatch&&created&&installed&&!added&&!returned&&!position&&!finished&&!consumed&&player==exactPlayer;}
 boolean added(Object p,boolean normal){if(!installed||added||p!=player||!normal)refused=true;if(!refused)added=true;return !refused;}
 boolean returned(boolean normal){if(!added||returned||!normal)refused=true;if(!refused)returned=true;return !refused;}
 boolean positionInitialized(Object p,boolean actualIngress,boolean normal){if(!returned||position||p!=player||!actualIngress||!normal)refused=true;if(!refused)position=true;return !refused;}
 boolean finish(boolean historyCurrent){if(!position||finished||!historyCurrent)refused=true;if(!refused)finished=true;return !refused;}
 boolean consume(){if(refused||!finished||consumed){refused=true;return false;}consumed=true;return true;}boolean complete(){return !refused&&finished&&!consumed;}void refuse(){refused=true;}
}
