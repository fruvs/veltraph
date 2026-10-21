package dev.veltraph.client.navigation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AttributedCollectionLegBoundaryTest {
 @Test void nonCollectionSourcePreservesOnlyAdditionalVetoNotPermission(){var parent=new Object();var leg=new Object();var actual=dev.veltraph.client.mining.vein.VeinMiningModule.nativeCollectionTravelSnapshot(null,parent,leg);assertNotNull(actual);assertTrue(dev.veltraph.client.mining.vein.VeinMiningModule.nativeCollectionTravelSnapshotCurrent(null,parent,leg,actual,0));assertFalse(dev.veltraph.client.mining.vein.VeinMiningModule.nativeCollectionTravelSnapshotCurrent(null,parent,leg,new Object(),0));}
 @Test void foreignSameParentNewRequestRevokesPublicationIdentity(){Object leg=new Object(),id=new Object(),root=new Object(),p=new Object(),w=new Object(),connection=new Object(),cfg=new Object();var before=new LegacyPublicationSnapshotIdentity.State(leg,id,root,p,w,connection,cfg,1,5,8,9);var after=new LegacyPublicationSnapshotIdentity.State(leg,id,root,p,w,connection,cfg,1,6,8,9);assertFalse(LegacyPublicationSnapshotIdentity.same(before,after));}
 @Test void noEffectNewLegAbaCannotReuseOldPublication(){Object leg=new Object(),id=new Object(),root=new Object(),p=new Object(),w=new Object(),connection=new Object(),cfg=new Object();var before=new LegacyPublicationSnapshotIdentity.State(leg,id,root,p,w,connection,cfg,1,5,8,9);var after=new LegacyPublicationSnapshotIdentity.State(leg,id,root,p,w,connection,cfg,3,5,8,9);assertFalse(LegacyPublicationSnapshotIdentity.same(before,after));}
 @Test void realUnscopedSameValueAttemptStillRevokesHistoricalOutput(){var v=new dev.veltraph.client.rotation.NormalIdleInputVersion();var actual=v.snapshot();v.begin(null,true);v.end(false);assertFalse(v.current(actual));}
}
