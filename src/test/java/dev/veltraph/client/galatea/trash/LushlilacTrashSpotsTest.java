package dev.veltraph.client.galatea.trash;

import dev.veltraph.client.world.Vec3;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LushlilacTrashSpotsTest {
    private final String one="[{\"dimension\":\"minecraft:overworld\",\"x\":0.5,\"y\":70,\"z\":0.5,\"yaw\":90,\"pitch\":70}]";
    @Test void unknownMetadataFailsClosed(){assertThrows(RuntimeException.class,()->LushlilacTrashSpots.parse(""));assertThrows(IllegalArgumentException.class,()->LushlilacTrashSpots.parse("[]"));assertThrows(IllegalArgumentException.class,()->LushlilacTrashSpots.parse(one.replace("\"pitch\":70","\"pitch\":81")));}
    @Test void exactSchemaRejectsForeignActionsAndDuplicates(){assertThrows(IllegalArgumentException.class,()->LushlilacTrashSpots.parse(one.replace("\"pitch\":70","\"pitch\":70,\"command\":\"warp\"")));var object=one.substring(1,one.length()-1);assertThrows(IllegalArgumentException.class,()->LushlilacTrashSpots.parse("["+object+","+object+"]"));}
    @Test void unloadedSpotsCannotWinAndAllUnknownReturnsNoGoal(){var spots=LushlilacTrashSpots.parse(one);assertTrue(LushlilacTrashSpots.choose(spots,new Vec3(0,70,0),p->false,new Random(0)).isEmpty());}
    @Test void recoveredSquaredDistanceNoiseCannotSelectFarSpot(){var near=new LushlilacTrashSpots.Spot("minecraft:overworld",new Vec3(1,70,0),0,0);var far=new LushlilacTrashSpots.Spot("minecraft:overworld",new Vec3(20,70,0),0,0);var rng=new Random(41);
        for(int i=0;i<100;i++){var chosen=LushlilacTrashSpots.choose(List.of(far,near),new Vec3(0,70,0),p->true,rng).orElseThrow();assertEquals(near.position(),chosen.position());assertTrue(chosen.yaw()>=-10&&chosen.yaw()<10);assertTrue(chosen.pitch()>=-10&&chosen.pitch()<10);}}
    @Test void configuredPitchHeadroomHandlesBothExtremeRandomOffsets(){var spots=LushlilacTrashSpots.parse(one.replace("\"pitch\":70","\"pitch\":80"));var rng=new Random(29);for(int i=0;i<50;i++){var chosen=LushlilacTrashSpots.choose(spots,new Vec3(0,70,0),p->true,rng).orElseThrow();assertTrue(chosen.pitch()>=70&&chosen.pitch()<90);}}
}
