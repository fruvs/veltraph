package dev.veltraph.client.navigation;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GroundOwnedSprintRevisionTest {
    @Test void exactNativeNestedAttributeSequenceQualifiesOnlyItsExactIdentity(){Object attribute=new Object(),id=new Object(),modifier=new Object();
        var before=GroundPhysicsNativeRevisions.watch(attribute);
        GroundPhysicsNativeRevisions.mutation(attribute,GroundPhysicsNativeRevisions.Kind.REMOVE_ID,id);
        GroundPhysicsNativeRevisions.mutation(attribute,GroundPhysicsNativeRevisions.Kind.ADD_TRANSIENT,modifier);
        GroundPhysicsNativeRevisions.mutation(attribute,GroundPhysicsNativeRevisions.Kind.ADD_MODIFIER,modifier);
        var next=GroundPhysicsNativeRevisions.watch(attribute);var expected=List.of(
            new GroundPhysicsNativeRevisions.Change(GroundPhysicsNativeRevisions.Kind.REMOVE_ID,id),
            new GroundPhysicsNativeRevisions.Change(GroundPhysicsNativeRevisions.Kind.ADD_TRANSIENT,modifier),
            new GroundPhysicsNativeRevisions.Change(GroundPhysicsNativeRevisions.Kind.ADD_MODIFIER,modifier));
        assertFalse(before.current());assertTrue(before.followedExactlyBy(next,expected));
        assertFalse(before.followedExactlyBy(next,List.of(expected.getFirst(),expected.getLast())));}
    @Test void unrelatedOrSameValueAbaCannotHideInsideAuthorizedSequence(){Object player=new Object();var before=GroundPhysicsNativeRevisions.watch(player);
        GroundPhysicsNativeRevisions.mutation(player,GroundPhysicsNativeRevisions.Kind.SPRINT_METHOD,true);
        GroundPhysicsNativeRevisions.mutation(player); // Effect refresh, foreign setter, base mutation, or ABA attempt.
        GroundPhysicsNativeRevisions.mutation(player,GroundPhysicsNativeRevisions.Kind.SPRINT_FLAG,true);
        var next=GroundPhysicsNativeRevisions.watch(player);
        assertFalse(before.followedExactlyBy(next,List.of(new GroundPhysicsNativeRevisions.Change(GroundPhysicsNativeRevisions.Kind.SPRINT_METHOD,true),
            new GroundPhysicsNativeRevisions.Change(GroundPhysicsNativeRevisions.Kind.SPRINT_FLAG,true))));}
    @Test void equalLookingModifierSubstituteDenied(){Object a=new Object();String actual=new String("minecraft:sprinting"),copy=new String("minecraft:sprinting");
        var before=GroundPhysicsNativeRevisions.watch(a);GroundPhysicsNativeRevisions.mutation(a,GroundPhysicsNativeRevisions.Kind.REMOVE_ID,actual);
        assertFalse(before.followedExactlyBy(GroundPhysicsNativeRevisions.watch(a),List.of(new GroundPhysicsNativeRevisions.Change(GroundPhysicsNativeRevisions.Kind.REMOVE_ID,copy))));}
    @Test void anyMutationAfterReceiptMakesItStale(){Object a=new Object();var before=GroundPhysicsNativeRevisions.watch(a);
        GroundPhysicsNativeRevisions.mutation(a,GroundPhysicsNativeRevisions.Kind.SPEED_FIELD,.36F);var receipt=GroundPhysicsNativeRevisions.watch(a);
        GroundPhysicsNativeRevisions.mutation(a);assertFalse(before.followedExactlyBy(receipt,List.of(new GroundPhysicsNativeRevisions.Change(GroundPhysicsNativeRevisions.Kind.SPEED_FIELD,.36F))));}
    @Test void directSpeedAbaAndOverflowedHistoryCannotQualify(){Object a=new Object();var before=GroundPhysicsNativeRevisions.watch(a);
        for(int i=0;i<33;i++)GroundPhysicsNativeRevisions.mutation(a,GroundPhysicsNativeRevisions.Kind.SPEED_FIELD,.4F);
        assertFalse(before.followedExactlyBy(GroundPhysicsNativeRevisions.watch(a),java.util.Collections.nCopies(33,new GroundPhysicsNativeRevisions.Change(GroundPhysicsNativeRevisions.Kind.SPEED_FIELD,.4F))));}
    @Test void tokensFromDifferentNativeObjectsCannotJoin(){Object a=new Object(),b=new Object();var x=GroundPhysicsNativeRevisions.watch(a);var y=GroundPhysicsNativeRevisions.watch(b);
        assertFalse(x.sameRevision(y));assertFalse(x.followedExactlyBy(y,List.of()));}
}
