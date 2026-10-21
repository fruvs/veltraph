package dev.veltraph.client.fishing.setup;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RodSelectorTest {
    static RodSelectionProfile profile() {
        return new RodSelectionProfile("excluded",bindings("water",50,30,30,20,10,5,1),bindings("lava",50,30,20,10,3,1));
    }
    private static List<RodSelectionProfile.Binding> bindings(String prefix,int... weights) {
        var entries=new ArrayList<RodSelectionProfile.Binding>();
        for(int i=0;i<weights.length;i++)entries.add(new RodSelectionProfile.Binding(prefix+i,weights[i]));
        return entries;
    }
    static List<RodSelector.Candidate> hotbar() {
        var entries=new ArrayList<RodSelector.Candidate>();
        for(int i=0;i<9;i++)entries.add(new RodSelector.Candidate(i,false,"empty",false));return entries;
    }
    @Test void bindsEveryRecoveredWeightWithoutGuessedLabels() {
        var p=profile();assertEquals(p,RodSelectionProfile.parse(new Gson().toJson(p)));
        assertEquals(SourceRodBindings.profile(),RodSelectionProfile.parse("{}"));
        assertThrows(IllegalArgumentException.class,()->RodSelectionProfile.parse("null"));
        assertThrows(IllegalArgumentException.class,()->new RodSelectionProfile("",p.water(),p.lava()));
        assertThrows(IllegalArgumentException.class,()->new RodSelectionProfile("excluded",p.water().subList(0,6),p.lava()));
        var changed=new ArrayList<>(p.lava());changed.set(0,new RodSelectionProfile.Binding("lava0",49));
        assertThrows(IllegalArgumentException.class,()->new RodSelectionProfile("excluded",p.water(),changed));
    }
    @Test void nativeItemIdentityIsRequiredAndExcludedRawNameOverridesFoil() {
        assertEquals(0,RodSelector.score(new RodSelector.Candidate(0,false,"water0",true),false,profile()));
        assertEquals(0,RodSelector.score(new RodSelector.Candidate(0,true,"excluded water0",true),false,profile()));
        assertEquals(60,RodSelector.score(new RodSelector.Candidate(0,true,"§bwater0",true),false,profile()));
        assertEquals(0,RodSelector.score(new RodSelector.Candidate(0,true,"Unbound",false),false,profile()));
        assertEquals(10,RodSelector.score(new RodSelector.Candidate(0,true,"Unbound",true),false,profile()));
    }
    @Test void environmentSelectsItsOwnTable() {
        var entries=hotbar();entries.set(1,new RodSelector.Candidate(1,true,"water0",false));
        entries.set(4,new RodSelector.Candidate(4,true,"lava0",false));
        assertEquals(1,RodSelector.select(entries,false,profile()));assertEquals(4,RodSelector.select(entries,true,profile()));
    }
    @Test void excludesReservedNinthSlotAndPreservesFirstPositiveMaximumRegardlessOfInputOrder() {
        var entries=hotbar();entries.set(2,new RodSelector.Candidate(2,true,"water1",false));
        entries.set(6,new RodSelector.Candidate(6,true,"water2",false));
        entries.set(8,new RodSelector.Candidate(8,true,"water0",true));
        Collections.reverse(entries);assertEquals(2,RodSelector.select(entries,false,profile()));
        assertEquals(-1,RodSelector.select(hotbar(),false,profile()));
    }
    @Test void overlapUsesActualHashMapIterationFirstMatchWithoutAddingAllWeights() {
        var p=profile();var name="water0 water1 water6";
        int expected=p.table(false).keySet().stream().filter(name::contains).map(key->p.table(false).get(key)).findFirst().orElseThrow();
        assertEquals(expected,RodSelector.score(new RodSelector.Candidate(0,true,name,false),false,p));
    }
    @Test void sceneUsesExactlyTheSourceOffsetsAndNativeWaterLavaBalance() {
        var calls=new AtomicInteger();
        assertFalse(RodSelector.lavaNearby((x,y,z)->{calls.incrementAndGet();assertTrue(x>=-5&&x<5&&y>=-5&&y<5&&z>=-5&&z<5);return RodSelector.Block.OTHER;}));
        assertEquals(1000,calls.get());
        assertTrue(RodSelector.lavaNearby((x,y,z)->x==-5&&y==-5&&z==-5?RodSelector.Block.LAVA:RodSelector.Block.OTHER));
        assertFalse(RodSelector.lavaNearby((x,y,z)->x==-5&&y==-5?(z==-5?RodSelector.Block.WATER:z==-4?RodSelector.Block.LAVA:RodSelector.Block.OTHER):RodSelector.Block.OTHER));
        assertThrows(IllegalArgumentException.class,()->RodSelector.lavaNearby((x,y,z)->RodSelector.Block.UNLOADED));
    }
    @Test void persistedNNativeObsidianSceneCorrectlyKeepsTheWaterTable() {
        // Saved N player center0,70,0: original162lava-arrangement area became161obsidian
        // plus the preserved stone support; the outer scan edge retained38native water blocks.
        var lava=RodSelector.lavaNearby((x,y,z)->(y==-2||y==-1)&&(x==-5||z==-5)
                ?RodSelector.Block.WATER:RodSelector.Block.OTHER);
        assertFalse(lava,"Native obsidian is OTHER; it must not imply lava capability");
        var entries=hotbar();entries.set(2,new RodSelector.Candidate(2,true,"water1",false));
        entries.set(5,new RodSelector.Candidate(5,true,"water2",false));
        entries.set(7,new RodSelector.Candidate(7,true,"lava0",false));
        entries.set(8,new RodSelector.Candidate(8,true,"water0",true));
        assertEquals(2,RodSelector.select(entries,lava,profile()));
    }
    @Test void declaredWaterFreeLowerLayerFixtureHasANegativeNativeBalance() {
        // Native fixture clears WATER while preserving0,69,0stone, then places81LAVA atY68.
        var samples=new AtomicInteger();
        boolean lava=RodSelector.lavaNearby((x,y,z)->{
            if(y==-2&&x>=-4&&x<=4&&z>=-4&&z<=4){samples.incrementAndGet();return RodSelector.Block.LAVA;}
            return RodSelector.Block.OTHER;
        });
        assertEquals(81,samples.get());assertTrue(lava);
        var entries=hotbar();entries.set(2,new RodSelector.Candidate(2,true,"water1",false));
        entries.set(7,new RodSelector.Candidate(7,true,"lava0",false));
        entries.set(8,new RodSelector.Candidate(8,true,"water0",true));
        assertEquals(7,RodSelector.select(entries,lava,profile()));
    }
    @Test void malformedHotbarCannotSelectAgainstPartialOrDuplicateInventory() {
        var entries=hotbar();entries.remove(3);
        assertThrows(IllegalArgumentException.class,()->RodSelector.select(entries,false,profile()));
        assertThrows(IllegalArgumentException.class,()->RodSelector.select(List.of(new RodSelector.Candidate(0,true,"water0",false),new RodSelector.Candidate(0,true,"water1",false)),false,profile()));
    }
}
