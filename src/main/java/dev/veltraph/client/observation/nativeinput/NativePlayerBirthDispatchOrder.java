package dev.veltraph.client.observation.nativeinput;
/** Actual outer processor source closure and first initialization identity; no pose/Root grant. */
final class NativePlayerBirthDispatchOrder {
 private final Object packet;private boolean returned,normal,refused;private Object firstPosition;
 NativePlayerBirthDispatchOrder(Object packet){this.packet=packet;}
 synchronized void returned(Object exact,boolean normalReturn){if(returned||exact!=packet||!normalReturn)refused=true;returned=true;normal=normalReturn;}
 synchronized boolean complete(){return !refused&&returned&&normal;}
 synchronized boolean firstPosition(Object incoming){if(incoming==null||firstPosition!=null){refused=true;return false;}firstPosition=incoming;return true;}
 synchronized void refuse(){refused=true;}
}
