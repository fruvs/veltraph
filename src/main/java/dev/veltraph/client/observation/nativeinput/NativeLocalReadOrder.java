package dev.veltraph.client.observation.nativeinput;

/** Pure lifecycle data only. No native factory, bytes, Node, source publication or permission is produced by this class. */
final class NativeLocalReadOrder {
    private final Object owner,parent,parentBody;
    private Object childBody,child;
    private boolean attempted,bound,returned,refused;
    NativeLocalReadOrder(Object owner,Object parent,Object parentBody){this.owner=owner;this.parent=parent;this.parentBody=parentBody;}
    boolean claim(Object owner,Object active,Object body,Object childBody){
        if(attempted){refused=true;return false;}attempted=true;
        if(refused||this.owner==null||parent==null||parentBody==null||this.owner!=owner||parent!=active||parentBody!=body||childBody==null||childBody==body){refused=true;return false;}
        this.childBody=childBody;return true;
    }
    boolean bind(Object child){
        if(refused||!attempted||bound||child==null||child==parent){refused=true;return false;}bound=true;this.child=child;return true;
    }
    boolean returned(Object child,Object restoredParent,Object restoredBody,boolean normal){
        if(refused||!bound||returned||this.child!=child||parent!=restoredParent||parentBody!=restoredBody||!normal){refused=true;return false;}
        returned=true;return true;
    }
    boolean activeChild(Object actualChild,Object actualChildBody){
        return bound&&!refused&&!returned&&child==actualChild&&childBody==actualChildBody;
    }
    void refuse(){refused=true;}
}
