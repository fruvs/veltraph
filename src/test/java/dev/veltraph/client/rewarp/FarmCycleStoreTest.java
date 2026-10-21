package dev.veltraph.client.rewarp;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class FarmCycleStoreTest {
    @TempDir Path dir;
    private FarmReturnPoint pose(String dimension,double y){return new FarmReturnPoint(dimension,GameArea.GARDEN,new Vec3(-234.31,y,1.7),-166.4f,-79);}
    @Test void fractionalPoseAndEndRolesRoundTripSeparatelyFromOldMarkers()throws Exception{
        var file=dir.resolve("farm-cycles.json");var old=dir.resolve("config.json");Files.writeString(old,"old return markers");
        var s=new FarmCycleStore(file);s.setStart("cocoa_two_key_current",pose("minecraft:overworld",68.375));s.addEnd("cocoa_two_key_current",GameArea.GARDEN,"minecraft:overworld",new Vec3(4.79,68.375,-47.12));s.save();
        var loaded=new FarmCycleStore(file);loaded.load();assertEquals(s.list(),loaded.list());assertEquals("old return markers",Files.readString(old));
    }
    @Test void differentDimensionClearsEndsAndWrongScopeCannotAppend(){
        var s=new FarmCycleStore(dir.resolve("farm-cycles.json"));s.setStart("test",pose("first",69.9375));s.addEnd("test",GameArea.GARDEN,"first",new Vec3(20,69.9375,0));
        assertThrows(IllegalArgumentException.class,()->s.addEnd("test",GameArea.HUB,"first",new Vec3(20,69.9375,0)));
        s.setStart("test",pose("second",69.9375));assertTrue(s.get("test").orElseThrow().ends().isEmpty());
    }
    @Test void corruptFileIsPreservedAndCannotArmEditedPlans()throws Exception{
        Path p=dir.resolve("farm-cycles.json");Files.writeString(p,"broken saved data");var s=new FarmCycleStore(p);
        assertThrows(java.io.IOException.class,s::load);assertThrows(IllegalStateException.class,()->s.setStart("test",pose("first",69.9375)));assertThrows(java.io.IOException.class,s::save);assertEquals("broken saved data",Files.readString(p));
    }
    @Test void failedAtomicSaveRollsBackMemory()throws Exception{
        Path file=dir.resolve("plans.json");var s=new FarmCycleStore(file);s.setStart("test",pose("first",69.9375));s.save();Files.delete(file);Files.createDirectory(file);Files.writeString(file.resolve("keep"),"data");
        assertThrows(java.io.IOException.class,()->s.updateAndSave(()->s.setStart("test",pose("second",68.375))));assertEquals("first",s.get("test").orElseThrow().start().dimension());assertEquals("data",Files.readString(file.resolve("keep")));
    }
    @Test void strictMarkerBoundariesAndNearest3dSelection(){
        var start=pose("first",0);var plan=new FarmCyclePlan("test",start,List.of(new Vec3(0,0,0)));
        assertTrue(plan.matchingEnd(GameArea.GARDEN,"first",new Vec3(.99,1.09,0),1).isPresent());
        assertTrue(plan.matchingEnd(GameArea.GARDEN,"first",new Vec3(1,0,0),1).isEmpty());assertTrue(plan.matchingEnd(GameArea.GARDEN,"first",new Vec3(0,1.1,0),1).isEmpty());assertTrue(plan.matchingEnd(GameArea.GARDEN,"other",new Vec3(0,0,0),1).isEmpty());
        var nearestInvalid=new FarmCyclePlan("test",start,List.of(new Vec3(.99,1.09,0),new Vec3(1,0,0)));
        assertTrue(nearestInvalid.matchingEnd(GameArea.GARDEN,"first",new Vec3(0,0,0),1).isEmpty(),"Source checks nearest3D first rather than choosing a farther valid marker");
    }
    @Test void duplicateAndInvalidPlansAreRejected(){
        var p=new FarmCyclePlan("test",pose("first",69.9375),List.of());assertThrows(IllegalArgumentException.class,()->FarmCycleStore.encode(List.of(p,p)));
        var duplicate=com.google.gson.JsonParser.parseString(FarmCycleStore.encode(List.of(p))).getAsJsonObject();
        duplicate.getAsJsonArray("plans").add(duplicate.getAsJsonArray("plans").get(0).deepCopy());
        assertThrows(IllegalArgumentException.class,()->FarmCycleStore.decode(duplicate.toString()));
        assertThrows(IllegalArgumentException.class,()->FarmCycleStore.decode("{\"version\":2,\"plans\":[]}"));assertThrows(IllegalArgumentException.class,()->new FarmCyclePlan("../bad",pose("first",69.9375),List.of()));
    }
    @Test void ambiguousFieldsAndTypeCoercionCannotLoadAnArmedPlan(){
        String text=FarmCycleStore.encode(List.of(new FarmCyclePlan("test",pose("first",69.9375),List.of())));
        assertThrows(IllegalArgumentException.class,()->FarmCycleStore.decode(text.replace("\"version\": 1","\"version\": 1, \"version\": 1")));
        assertThrows(IllegalArgumentException.class,()->FarmCycleStore.decode(text.replace("\"version\": 1","\"version\": \"1\"")));
        assertThrows(IllegalArgumentException.class,()->FarmCycleStore.decode(text.replace("\"x\": -234.31","\"x\": 0, \"x\": -234.31")));
        assertThrows(IllegalArgumentException.class,()->FarmCycleStore.decode(text+" {}"));
    }
    @Test void inMemoryLimitsApplyBeforeMutatingTheStore(){
        var store=new FarmCycleStore(dir.resolve("plans.json"));
        for(int i=0;i<64;i++)store.setStart("p"+i,pose("first",69.9375));
        assertThrows(IllegalArgumentException.class,()->store.setStart("overflow",pose("first",69.9375)));
        assertEquals(64,store.list().size());
    }
}
