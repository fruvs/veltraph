package dev.veltraph.client.movement.device;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DeviceTerminalNativeLeafTest {
 private final Object root=new Object(),p=new Object(),w=new Object(),c=new Object(),store=new Object(),options=new Object(),game=new Object(),menu=new Object(),camera=new Object();
 private DeviceTerminalNativeLeaf.Frame f(Object world,Object cfg,long gen,long pub,long ack,int slot,double x){return new DeviceTerminalNativeLeaf.Frame(root,p,world,c,cfg,options,game,menu,camera,9,gen,pub,ack,slot,x,70,.5,45,10);}
 private DeviceTerminalNativeLeaf.Frame initial(){return f(w,store,6,11,17,2,.5);}
 @Test void unchangedNativeLeafDoesNotCreateAuthorityButMatches(){assertTrue(DeviceTerminalNativeLeaf.same(initial(),initial()));}
 @Test void providerWorldReplacementAfterProofIsDenied(){assertFalse(DeviceTerminalNativeLeaf.same(initial(),f(new Object(),store,6,11,17,2,.5)));}
 @Test void providerStoreReplacementAfterProofIsDenied(){assertFalse(DeviceTerminalNativeLeaf.same(initial(),f(w,new Object(),6,11,17,2,.5)));}
 @Test void codecNewSourceRequestAfterEarlierProofIsDenied(){assertFalse(DeviceTerminalNativeLeaf.same(initial(),f(w,store,7,11,17,2,.5)));}
 @Test void providerRawSameEpochPublicationIsDenied(){assertFalse(DeviceTerminalNativeLeaf.same(initial(),f(w,store,6,12,17,2,.5)));}
 @Test void newerAckDuringFinalCameraCallbackIsDenied(){assertFalse(DeviceTerminalNativeLeaf.same(initial(),f(w,store,6,11,18,2,.5)));}
 @Test void slotChangedAfterCodecReadIsDenied(){assertFalse(DeviceTerminalNativeLeaf.same(initial(),f(w,store,6,11,17,0,.5)));}
 @Test void positionChangedDuringTerrainReadIsDenied(){assertFalse(DeviceTerminalNativeLeaf.same(initial(),f(w,store,6,11,17,2,.6)));}
 @Test void nonfiniteSnapshotDoesNotMatchItself(){var invalid=f(w,store,6,11,17,2,Double.NaN);assertFalse(DeviceTerminalNativeLeaf.same(invalid,invalid));}
}
