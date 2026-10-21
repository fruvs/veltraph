package dev.veltraph.client.navigation;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GroundNoGravityMutationTest {
    private final Object player=new Object(),data=new Object(),item=new Object(),accessor=new Object();
    @Test void everyNoGravitySourcePreservesActualDetailAndInvalidatesEvenFalseFalse(){
        for(var source:GroundNoGravityMutation.Source.values()){
            var before=GroundPhysicsNativeRevisions.watch(player);
            var actual=GroundNoGravityMutation.record(player,source,data,item,accessor,Boolean.FALSE);
            assertSame(data,actual.actualData());assertSame(item,actual.actualItem());assertSame(accessor,actual.attemptedAccessor());
            assertSame(Boolean.FALSE,actual.attemptedValue());assertEquals(source,actual.source());assertFalse(before.current());
            GroundPhysicsNativeRevisions.mutation(player,GroundPhysicsNativeRevisions.Kind.SPEED_FIELD,.4F);
            assertFalse(before.followedExactlyBy(GroundPhysicsNativeRevisions.watch(player),List.of(
                new GroundPhysicsNativeRevisions.Change(GroundPhysicsNativeRevisions.Kind.SPEED_FIELD,.4F))));
        }
    }
    @Test void trueFalseRestorationCannotReviveOriginalRevision(){var before=GroundPhysicsNativeRevisions.watch(player);
        GroundNoGravityMutation.record(player,GroundNoGravityMutation.Source.RAW_SET2,data,item,accessor,Boolean.TRUE);
        GroundNoGravityMutation.record(player,GroundNoGravityMutation.Source.RAW_SET2,data,item,accessor,Boolean.FALSE);
        assertTrue(GroundNativeGravityTraits.consistent(false,Boolean.FALSE,.08D,.08D));assertFalse(before.current());
        assertFalse(before.followedExactlyBy(GroundPhysicsNativeRevisions.watch(player),List.of()));}
    @Test void forcedFalseAndDirectItemFalseCannotBecomeOwnedRefresh(){var before=GroundPhysicsNativeRevisions.watch(player);
        GroundNoGravityMutation.record(player,GroundNoGravityMutation.Source.RAW_SET3_FORCED,data,item,accessor,Boolean.FALSE);
        GroundNoGravityMutation.record(player,GroundNoGravityMutation.Source.RAW_ITEM,data,item,accessor,Boolean.FALSE);
        GroundPhysicsNativeRevisions.mutation(player,GroundPhysicsNativeRevisions.Kind.SPEED_FIELD,.36F);
        assertFalse(before.followedExactlyBy(GroundPhysicsNativeRevisions.watch(player),List.of(
            new GroundPhysicsNativeRevisions.Change(GroundPhysicsNativeRevisions.Kind.SPEED_FIELD,.36F))));}
    private record Packet(int id,String serializer,Object value) {}
    @Test void packetWrongIdSerializerAndSameValueKeepTheirActualAttemptDetail(){var payload=new Packet(91,"wrong:boolean",Boolean.FALSE);
        var before=GroundPhysicsNativeRevisions.watch(player);
        var attempt=GroundNoGravityMutation.record(player,GroundNoGravityMutation.Source.PACKET_ASSIGN,data,item,accessor,payload);
        assertSame(payload,attempt.attemptedValue());assertSame(item,attempt.actualItem());assertFalse(before.current());}
    @Test void noGravityEventCannotHideInsideExactNativeSprintSequence(){var before=GroundPhysicsNativeRevisions.watch(player);
        var first=new GroundPhysicsNativeRevisions.Change(GroundPhysicsNativeRevisions.Kind.SPRINT_DATA_SET2,(byte)8);
        var last=new GroundPhysicsNativeRevisions.Change(GroundPhysicsNativeRevisions.Kind.SPRINT_DATA_SET3,(byte)8);
        GroundPhysicsNativeRevisions.mutation(player,first.kind(),first.detail());
        GroundNoGravityMutation.record(player,GroundNoGravityMutation.Source.RAW_ITEM,data,item,accessor,Boolean.FALSE);
        GroundPhysicsNativeRevisions.mutation(player,last.kind(),last.detail());
        assertFalse(before.followedExactlyBy(GroundPhysicsNativeRevisions.watch(player),List.of(first,last)));}
    @Test void equalLookingAccessorAndInvalidValueRemainRefusalOnly(){String original=new String("NoGravity"),forged=new String("NoGravity");
        var before=GroundPhysicsNativeRevisions.watch(player);
        var attempt=GroundNoGravityMutation.record(player,GroundNoGravityMutation.Source.RAW_SET3,data,item,forged,"false");
        assertSame(forged,attempt.attemptedAccessor());assertNotSame(original,attempt.attemptedAccessor());assertFalse(before.current());}
    @Test void boundedHistoryLossCannotRecoverNoGravityAba(){var before=GroundPhysicsNativeRevisions.watch(player);
        for(int i=0;i<33;i++)GroundNoGravityMutation.record(player,GroundNoGravityMutation.Source.RAW_ITEM,data,item,accessor,(i&1)==0);
        assertFalse(before.current());assertFalse(before.followedExactlyBy(GroundPhysicsNativeRevisions.watch(player),List.of()));}
}
