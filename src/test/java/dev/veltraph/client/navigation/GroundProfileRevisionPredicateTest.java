package dev.veltraph.client.navigation;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GroundProfileRevisionPredicateTest {
    private static boolean originalAll(List<GroundPhysicsNativeRevisions.Token> values){
        return values.stream().allMatch(r->r!=null&&r.current());
    }
    private static boolean originalChanged(List<GroundPhysicsNativeRevisions.Token> values){
        return values.stream().anyMatch(r->r==null||!r.current());
    }
    private static void parity(List<GroundPhysicsNativeRevisions.Token> values){
        boolean actual=GroundPhysicsNativeRevisions.capturedTokensCurrent(values);
        assertEquals(originalAll(values),actual);
        assertEquals(originalChanged(values),!actual);
    }
    @Test void immutableEmptyAndSingleDomainsMatchBothOriginalPredicates(){
        var object=new Object();var token=GroundPhysicsNativeRevisions.watch(object);assertNotNull(token);
        parity(List.of());parity(List.of(token));assertTrue(GroundPhysicsNativeRevisions.capturedTokensCurrent(List.of(token)));
        GroundPhysicsNativeRevisions.mutation(object);parity(List.of(token));
        assertFalse(GroundPhysicsNativeRevisions.capturedTokensCurrent(List.of(token)));
    }
    @Test void originalUnmodifiableCaptureListRefusesEveryNullPosition(){
        var objects=new ArrayList<Object>();var tokens=new ArrayList<GroundPhysicsNativeRevisions.Token>();
        for(int i=0;i<14;i++){var object=new Object();objects.add(object);tokens.add(GroundPhysicsNativeRevisions.watch(object));assertNotNull(tokens.getLast());}
        parity(Collections.unmodifiableList(tokens));
        for(int i=0;i<tokens.size();i++){var copy=new ArrayList<>(tokens);copy.set(i,null);parity(Collections.unmodifiableList(copy));assertFalse(GroundPhysicsNativeRevisions.capturedTokensCurrent(Collections.unmodifiableList(copy)));}
        assertEquals(14,objects.size());
    }
    @Test void sameValueAndABARetireAtEveryActualNativeRevisionPosition(){
        var objects=new ArrayList<Object>();var tokens=new ArrayList<GroundPhysicsNativeRevisions.Token>();
        for(int i=0;i<7;i++){var object=new Object();objects.add(object);tokens.add(GroundPhysicsNativeRevisions.watch(object));assertNotNull(tokens.getLast());}
        for(int i=0;i<7;i++){
            var captured=Collections.unmodifiableList(new ArrayList<>(tokens));
            GroundPhysicsNativeRevisions.mutation(objects.get(i));GroundPhysicsNativeRevisions.mutation(objects.get(i));
            parity(captured);assertFalse(GroundPhysicsNativeRevisions.capturedTokensCurrent(captured));
            tokens.set(i,GroundPhysicsNativeRevisions.watch(objects.get(i)));parity(Collections.unmodifiableList(tokens));
            assertTrue(GroundPhysicsNativeRevisions.capturedTokensCurrent(Collections.unmodifiableList(tokens)));
            assertFalse(GroundPhysicsNativeRevisions.capturedTokensCurrent(captured));
        }
    }
    @Test void foreignMutationDoesNotChangeCapturedTokensOrRefreshThem(){
        var object=new Object();var foreign=new Object();var token=GroundPhysicsNativeRevisions.watch(object);assertNotNull(token);
        var captured=List.of(token);GroundPhysicsNativeRevisions.mutation(foreign);parity(captured);
        assertTrue(GroundPhysicsNativeRevisions.capturedTokensCurrent(captured));
        GroundPhysicsNativeRevisions.mutation(object,GroundPhysicsNativeRevisions.Kind.SPEED_FIELD,0.1F);
        parity(captured);assertFalse(GroundPhysicsNativeRevisions.capturedTokensCurrent(captured));
        assertNotNull(GroundPhysicsNativeRevisions.watch(object));
        assertFalse(GroundPhysicsNativeRevisions.capturedTokensCurrent(captured));
    }
    @Test void expiredHistoryCannotMakeOldComparisonCurrent(){
        var object=new Object();var original=GroundPhysicsNativeRevisions.watch(object);assertNotNull(original);
        for(int i=0;i<40;i++)GroundPhysicsNativeRevisions.mutation(object);
        parity(List.of(original));assertFalse(GroundPhysicsNativeRevisions.capturedTokensCurrent(List.of(original)));
        var fresh=GroundPhysicsNativeRevisions.watch(object);assertNotNull(fresh);parity(List.of(fresh));
        assertTrue(GroundPhysicsNativeRevisions.capturedTokensCurrent(List.of(fresh)));
    }
    @Test void nullTokenRefusesBeforeAnyLaterListOperand(){
        var accessed=new ArrayList<Integer>();
        List<GroundPhysicsNativeRevisions.Token> values=new AbstractList<>(){
            public int size(){return 2;}
            public GroundPhysicsNativeRevisions.Token get(int index){accessed.add(index);if(index==1)throw new AssertionError("late operand");return null;}
        };
        assertFalse(originalAll(values));assertEquals(List.of(0),accessed);accessed.clear();
        assertTrue(originalChanged(values));assertEquals(List.of(0),accessed);accessed.clear();
        assertFalse(GroundPhysicsNativeRevisions.capturedTokensCurrent(values));assertEquals(List.of(0),accessed);
    }
    @Test void exactErrorAndVisitedOrderMatchOriginalAfterCurrentToken(){
        var object=new Object();var token=GroundPhysicsNativeRevisions.watch(object);assertNotNull(token);
        var failure=new AssertionError("exact operand error");var accessed=new ArrayList<Integer>();
        List<GroundPhysicsNativeRevisions.Token> values=new AbstractList<>(){
            public int size(){return 3;}
            public GroundPhysicsNativeRevisions.Token get(int index){accessed.add(index);if(index==1)throw failure;return token;}
        };
        assertSame(failure,assertThrows(AssertionError.class,()->originalAll(values)));
        assertEquals(List.of(0,1),accessed);accessed.clear();
        assertSame(failure,assertThrows(AssertionError.class,()->originalChanged(values)));
        assertEquals(List.of(0,1),accessed);accessed.clear();
        assertSame(failure,assertThrows(AssertionError.class,()->GroundPhysicsNativeRevisions.capturedTokensCurrent(values)));
        assertEquals(List.of(0,1),accessed);assertTrue(token.current());
    }
    @Test void nullReceiverRetainsOriginalFailureRatherThanBecomingPermission(){
        assertThrows(NullPointerException.class,()->originalAll(null));
        assertThrows(NullPointerException.class,()->GroundPhysicsNativeRevisions.capturedTokensCurrent(null));
    }
}
