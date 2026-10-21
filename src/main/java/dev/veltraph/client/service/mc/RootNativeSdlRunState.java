package dev.veltraph.client.service.mc;

/** One original whole-run claim; model state alone grants no native source. */
final class RootNativeSdlRunState {
    private final Object client;private final Thread thread=Thread.currentThread();
    private Object whole;private boolean consumed,revoked;
    RootNativeSdlRunState(Object client){this.client=client;}
    boolean live(Object exact){return !revoked&&thread==Thread.currentThread()&&client==exact;}
    void revoke(){revoked=true;}
    boolean claim(Object exactClient,Object exactWhole) {
        if(!live(exactClient)||consumed||whole!=null||exactWhole==null){revoke();return false;}
        consumed=true;whole=exactWhole;return true;
    }
    boolean current(Object exactClient,Object exactWhole){return live(exactClient)&&consumed&&whole==exactWhole&&exactWhole!=null;}
    void close(Object exactWhole,boolean normal){if(!live(client)||whole!=exactWhole||exactWhole==null||!normal)revoke();if(whole==exactWhole)whole=null;}
}
