package dev.veltraph.client.service.mc;

import dev.veltraph.client.rotation.DirectRotationPublicationIdentity.Scope;
import java.lang.reflect.Modifier;

/** Authored only. Root must compile and run with the selected actual native service. */
public final class SourceAimCleanupEvidenceTest {
    private static final Object ROOT=new Object(),PARENT=new Object(),PLAYER=new Object(),WORLD=new Object(),CONNECTION=new Object(),CONFIG=new Object(),SERVICE=new Object();
    private static Scope scope(Object root,Object parent,Object player,Object world,Object connection,Object config,Object service,long generation,long epoch,long serial,long revision){
        return new Scope(root,parent,parent,parent,player,world,connection,config,service,generation,epoch,serial,revision);
    }
    private static Scope own(long generation,long epoch,long serial){return scope(ROOT,PARENT,PLAYER,WORLD,CONNECTION,CONFIG,SERVICE,generation,epoch,serial,4);}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){
        Scope before=own(7,11,19),after=own(8,11,19);
        require(McRotationService.metadataAimTransition(before,after),"own metadata-only +1g must be recognizable");
        require(McRotationService.metadataAimTransition(own(0,0,0),own(1,0,0)),"first aim metadata may have no native publication ticket");
        require(!McRotationService.metadataAimTransition(before,before),"zero generation delta must fail");
        require(!McRotationService.metadataAimTransition(before,own(9,11,19)),"two generation changes must fail");
        require(!McRotationService.metadataAimTransition(before,own(8,12,19)),"native cancellation is not metadata-only");
        require(!McRotationService.metadataAimTransition(before,own(8,11,20)),"new/foreign native publication must fail");
        require(!McRotationService.metadataAimTransition(own(Long.MAX_VALUE,11,19),own(Long.MIN_VALUE,11,19)),"overflow must fail");
        require(!McRotationService.metadataAimTransition(null,after),"missing before must fail");
        Object[][] scenes={
            {new Object(),PARENT,PLAYER,WORLD,CONNECTION,CONFIG,SERVICE},
            {ROOT,new Object(),PLAYER,WORLD,CONNECTION,CONFIG,SERVICE},
            {ROOT,PARENT,new Object(),WORLD,CONNECTION,CONFIG,SERVICE},
            {ROOT,PARENT,PLAYER,new Object(),CONNECTION,CONFIG,SERVICE},
            {ROOT,PARENT,PLAYER,WORLD,new Object(),CONFIG,SERVICE},
            {ROOT,PARENT,PLAYER,WORLD,CONNECTION,new Object(),SERVICE},
            {ROOT,PARENT,PLAYER,WORLD,CONNECTION,CONFIG,new Object()}
        };
        for(Object[] scene:scenes)require(!McRotationService.metadataAimTransition(before,scope(scene[0],scene[1],scene[2],scene[3],scene[4],scene[5],scene[6],8,11,19,4)),"foreign scene must fail");
        require(!McRotationService.metadataAimTransition(before,scope(ROOT,PARENT,PLAYER,WORLD,CONNECTION,CONFIG,SERVICE,8,11,19,5)),"config revision must fail");
        require(!McRotationService.metadataAimTransition(before,new Scope(ROOT,PARENT,new Object(),PARENT,PLAYER,WORLD,CONNECTION,CONFIG,SERVICE,8,11,19,4)),"foreign input owner must fail");
        require(!McRotationService.metadataAimTransition(before,new Scope(ROOT,PARENT,PARENT,new Object(),PLAYER,WORLD,CONNECTION,CONFIG,SERVICE,8,11,19,4)),"foreign camera owner must fail");
        // Evidence can be copied; custody constructors must remain inaccessible.
        require(McRotationService.metadataAimTransition(before,own(8,11,19)),"copied evidence remains an observation");
        for(var ctor:McRotationService.SourceAimOutcome.class.getDeclaredConstructors())require(Modifier.isPrivate(ctor.getModifiers()),"outcome constructor must be private");
        for(var ctor:McRotationService.SourceAimCancellation.class.getDeclaredConstructors())require(Modifier.isPrivate(ctor.getModifiers()),"cancellation constructor must be private");
    }
}
