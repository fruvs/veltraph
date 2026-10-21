package dev.veltraph.client.combat.ghost;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class GhostSourceStampValuesTest {
    @Test void identicalFreshValuesReuseExactImmutableStampAndOptional(){
        var values=new GhostSourceStampValues();var parent=new Object();var player=new Object();var world=new Object();var connection=new Object();
        var first=values.observed(parent,2,7,player,world,connection);
        for(int i=0;i<10000;i++)assertSame(first,values.observed(parent,2,7,player,world,connection));
        assertTrue(first.orElseThrow().same(new GhostSourceOwnedParentArea.Stamp(parent,2,7,player,world,connection)));
    }
    @Test void eachParentGenerationAndSettingsChangeAllocatesItsActualNewValue(){
        var values=new GhostSourceStampValues();var parent=new Object();var player=new Object();var world=new Object();var connection=new Object();
        var first=values.observed(parent,2,7,player,world,connection);var generation=values.observed(parent,3,7,player,world,connection);
        assertNotSame(first,generation);assertEquals(3,generation.orElseThrow().generation());assertFalse(first.orElseThrow().same(generation.orElseThrow()));
        var settings=values.observed(parent,3,8,player,world,connection);assertNotSame(generation,settings);assertEquals(8,settings.orElseThrow().settingsRevision());
        assertNotSame(settings,values.observed(new Object(),3,8,player,world,connection));
    }
    @Test void everyPwcIdentityChangeProducesDistinctActualValue(){
        var values=new GhostSourceStampValues();var parent=new Object();var player=new Object();var world=new Object();var connection=new Object();
        var first=values.observed(parent,2,7,player,world,connection);
        assertNotSame(first,values.observed(parent,2,7,new Object(),world,connection));
        values.clear();first=values.observed(parent,2,7,player,world,connection);assertNotSame(first,values.observed(parent,2,7,player,new Object(),connection));
        values.clear();first=values.observed(parent,2,7,player,world,connection);assertNotSame(first,values.observed(parent,2,7,player,world,new Object()));
    }
    @Test void equalObjectsWithDifferentIdentityCannotReuseAStamp(){
        class Equal {public boolean equals(Object other){return other instanceof Equal;}public int hashCode(){return 1;}}
        var values=new GhostSourceStampValues();var parent=new Object();var player=new Object();var a=new Equal();var b=new Equal();var connection=new Object();
        assertEquals(a,b);var first=values.observed(parent,1,1,player,a,connection);var next=values.observed(parent,1,1,player,b,connection);
        assertNotSame(first,next);assertFalse(first.orElseThrow().same(next.orElseThrow()));
    }
    @Test void invalidInputsPreserveConstructorRefusalWithoutPublishingFailedValue(){
        var values=new GhostSourceStampValues();var parent=new Object();var player=new Object();var world=new Object();var connection=new Object();
        var before=values.observed(parent,0,0,player,world,connection);
        assertThrows(IllegalArgumentException.class,()->values.observed(null,0,0,player,world,connection));
        assertThrows(IllegalArgumentException.class,()->values.observed(parent,-1,0,player,world,connection));
        assertThrows(IllegalArgumentException.class,()->values.observed(parent,0,-1,player,world,connection));
        assertThrows(IllegalArgumentException.class,()->values.observed(parent,0,0,null,world,connection));
        assertThrows(IllegalArgumentException.class,()->values.observed(parent,0,0,player,null,connection));
        assertThrows(IllegalArgumentException.class,()->values.observed(parent,0,0,player,world,null));
        assertSame(before,values.observed(parent,0,0,player,world,connection));
    }
    @Test void clearingReferencesCannotReviveOldObjectWhenEqualContextReturns(){
        var values=new GhostSourceStampValues();var parent=new Object();var player=new Object();var world=new Object();var connection=new Object();
        var first=values.observed(parent,1,1,player,world,connection);values.clear();values.clear();
        var next=values.observed(parent,1,1,player,world,connection);assertNotSame(first,next);assertTrue(first.orElseThrow().same(next.orElseThrow()));
    }
    @Test void valueIdentityNeverSubstitutesForFreshScopeCallbacksOrFalseResults(){
        var values=new GhostSourceStampValues();var parent=new Object();var player=new Object();var world=new Object();var connection=new Object();
        var allowed=new AtomicBoolean(true);var order=new ArrayList<String>();
        java.util.function.Supplier<Optional<GhostSourceOwnedParentArea.Stamp>> guarded=()->{
            order.add("root");if(!allowed.get()){values.clear();return Optional.empty();}
            order.add("settings");order.add("jump");order.add("stamp");return values.observed(parent,1,1,player,world,connection);
        };
        var first=guarded.get();var second=guarded.get();assertSame(first,second);assertEquals(List.of("root","settings","jump","stamp","root","settings","jump","stamp"),order);
        order.clear();allowed.set(false);assertTrue(guarded.get().isEmpty());assertEquals(List.of("root"),order);allowed.set(true);assertNotSame(first,guarded.get());
    }
    @Test void callbackExceptionRemainsTheExactOriginalFailureAndNoValueIsPublished(){
        var values=new GhostSourceStampValues();var failure=new IllegalStateException("original fresh callback failure");var order=new ArrayList<String>();
        java.util.function.Supplier<Optional<GhostSourceOwnedParentArea.Stamp>> guarded=()->{order.add("root");throw failure;};
        assertSame(failure,assertThrows(IllegalStateException.class,guarded::get));assertEquals(List.of("root"),order);
        var first=values.observed(new Object(),1,1,new Object(),new Object(),new Object());assertTrue(first.isPresent());
    }
}
