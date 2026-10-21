package dev.veltraph.client.navigation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GroundSharedFlagsWriteSequenceTest {
    private final Object data=new Object(),item=new Object(),accessor=new Object();
    private GroundSharedFlagsWriteSequence sprint(byte before,byte after,boolean enabled){return new GroundSharedFlagsWriteSequence(data,item,accessor,before,after,3,enabled);}
    @Test void exactOriginalSprintNestedWriteCompletes(){var sequence=sprint((byte)2,(byte)10,true);
        for(int stage=1;stage<=3;stage++)assertEquals(GroundSharedFlagsWriteSequence.Result.NATIVE_SPRINT,sequence.observe(data,item,accessor,(byte)10,stage));
        assertTrue(sequence.complete((byte)10));}
    @Test void sameValueNativeAttemptRequiresBothSettersButNoItemWrite(){var sequence=sprint((byte)8,(byte)8,true);
        assertEquals(GroundSharedFlagsWriteSequence.Result.NATIVE_SPRINT,sequence.observe(data,item,accessor,(byte)8,1));
        assertFalse(sequence.complete((byte)8));
        assertEquals(GroundSharedFlagsWriteSequence.Result.NATIVE_SPRINT,sequence.observe(data,item,accessor,(byte)8,2));
        assertTrue(sequence.complete((byte)8));
        assertEquals(GroundSharedFlagsWriteSequence.Result.FOREIGN,sequence.observe(data,item,accessor,(byte)8,3));
        assertFalse(sequence.complete((byte)8));}
    @Test void rawForcedSetterOrRawItemCannotStartOriginalChain(){for(int stage:new int[]{2,3,4}){
        var sequence=sprint((byte)0,(byte)8,true);
        assertEquals(GroundSharedFlagsWriteSequence.Result.FOREIGN,sequence.observe(data,item,accessor,(byte)8,stage));assertFalse(sequence.complete((byte)8));}}
    @Test void duplicateRawSameValueAttemptIsForeignAndSticky(){var sequence=sprint((byte)0,(byte)8,true);
        sequence.observe(data,item,accessor,(byte)8,1);
        assertEquals(GroundSharedFlagsWriteSequence.Result.FOREIGN,sequence.observe(data,item,accessor,(byte)8,1));
        assertEquals(GroundSharedFlagsWriteSequence.Result.FOREIGN,sequence.observe(data,item,accessor,(byte)8,2));assertFalse(sequence.complete((byte)8));}
    @Test void anotherActualDataItemAccessorOrPlayerScopeCannotSubstitute(){for(int identity=0;identity<3;identity++){
        var sequence=sprint((byte)0,(byte)8,true);
        assertEquals(GroundSharedFlagsWriteSequence.Result.FOREIGN,sequence.observe(identity==0?new Object():data,identity==1?new Object():item,
            identity==2?new Object():accessor,(byte)8,1));}}
    @Test void originalOtherFlagCallPreservesSprintBitWithExactNestedSequence(){var sequence=new GroundSharedFlagsWriteSequence(data,item,accessor,(byte)8,(byte)10,1,true);
        for(int stage=1;stage<=3;stage++)assertEquals(GroundSharedFlagsWriteSequence.Result.NATIVE_OTHER,sequence.observe(data,item,accessor,(byte)10,stage));
        assertTrue(sequence.complete((byte)10));
        assertEquals(GroundSharedFlagsWriteSequence.Result.FOREIGN,sequence.observe(data,item,accessor,(byte)10,1));}
    @Test void anotherFlagCannotSmuggleSprintBitChange(){var sequence=new GroundSharedFlagsWriteSequence(data,item,accessor,(byte)0,(byte)10,1,true);
        assertEquals(GroundSharedFlagsWriteSequence.Result.FOREIGN,sequence.observe(data,item,accessor,(byte)10,1));}
    @Test void wrongValueTypeRoundingOrRestoredByteNeverQualifies(){var sequence=sprint((byte)0,(byte)8,true);
        assertEquals(GroundSharedFlagsWriteSequence.Result.FOREIGN,sequence.observe(data,item,accessor,8,1));
        assertFalse(sequence.complete((byte)8));
        var changed=sprint((byte)0,(byte)8,true);for(int stage=1;stage<=3;stage++)changed.observe(data,item,accessor,(byte)8,stage);
        assertFalse(changed.complete((byte)0));}
    @Test void packetOrRawDataAbaCannotHideInExactSprintJournal(){Object player=new Object();var before=GroundPhysicsNativeRevisions.watch(player);
        GroundPhysicsNativeRevisions.mutation(player,GroundPhysicsNativeRevisions.Kind.SPRINT_DATA_SET2,(byte)8);
        GroundPhysicsNativeRevisions.mutation(player,GroundPhysicsNativeRevisions.Kind.FOREIGN_FLAGS_PACKET,item);
        GroundPhysicsNativeRevisions.mutation(player,GroundPhysicsNativeRevisions.Kind.SPRINT_DATA_SET3,(byte)8);
        GroundPhysicsNativeRevisions.mutation(player,GroundPhysicsNativeRevisions.Kind.SPRINT_DATA_ITEM,(byte)8);
        var expected=java.util.List.of(new GroundPhysicsNativeRevisions.Change(GroundPhysicsNativeRevisions.Kind.SPRINT_DATA_SET2,(byte)8),
            new GroundPhysicsNativeRevisions.Change(GroundPhysicsNativeRevisions.Kind.SPRINT_DATA_SET3,(byte)8),
            new GroundPhysicsNativeRevisions.Change(GroundPhysicsNativeRevisions.Kind.SPRINT_DATA_ITEM,(byte)8));
        assertFalse(before.followedExactlyBy(GroundPhysicsNativeRevisions.watch(player),expected));}
    @Test void foreignPacketSameValueCannotBecomeNormalRefresh(){Object player=new Object();var before=GroundPhysicsNativeRevisions.watch(player);
        GroundPhysicsNativeRevisions.mutation(player,GroundPhysicsNativeRevisions.Kind.FOREIGN_FLAGS_PACKET,item);
        GroundPhysicsNativeRevisions.mutation(player,GroundPhysicsNativeRevisions.Kind.SPEED_FIELD,.4F);
        assertFalse(before.followedExactlyBy(GroundPhysicsNativeRevisions.watch(player),java.util.List.of(
            new GroundPhysicsNativeRevisions.Change(GroundPhysicsNativeRevisions.Kind.SPEED_FIELD,.4F))));}
}
