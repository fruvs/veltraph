package dev.veltraph.client.gui;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MenuSlotComponentIdentityTest {
    private MenuSnapshot.Slot card(String identity){return new MenuSnapshot.Slot(2,"Reward",List.of("Hidden XP"),false,"minecraft:paper",1,false,identity);}
    @Test void legacyConstructorsKeepBlankIdentityAndEquality(){var old=new MenuSnapshot.Slot(2,"Reward",List.of("Hidden XP"),false,"minecraft:paper",1,false);assertEquals("",old.componentIdentity());assertEquals(old,card(""));assertEquals(new MenuSnapshot.Slot(1,"A",List.of(),false),new MenuSnapshot.Slot(1,"A",List.of(),false,"minecraft:paper",1,false,""));}
    @Test void componentOnlyMutationRevokesExactObservedSlot(){var old=card("{\"minecraft:custom_data\":{\"xp\":1}}");var changed=card("{\"minecraft:custom_data\":{\"xp\":2}}");assertEquals(old.name(),changed.name());assertEquals(old.lore(),changed.lore());assertNotEquals(old,changed);assertNotEquals(new MenuSnapshot(4,"Board",List.of(old),7),new MenuSnapshot(4,"Board",List.of(changed),7));}
    @Test void recursiveObjectOrderHasOneStableIdentity(){var a=JsonParser.parseString("{\"z\":{\"b\":2,\"a\":1},\"a\":[{\"y\":4,\"x\":3}]}");var b=JsonParser.parseString("{\"a\":[{\"x\":3,\"y\":4}],\"z\":{\"a\":1,\"b\":2}}");assertEquals(McMenuAccess.canonicalComponents(a),McMenuAccess.canonicalComponents(b));assertEquals("{\"a\":[{\"x\":3,\"y\":4}],\"z\":{\"a\":1,\"b\":2}}",McMenuAccess.canonicalComponents(a));}
    @Test void arrayOrderAndComponentTypesRemainDistinct(){assertNotEquals(McMenuAccess.canonicalComponents(JsonParser.parseString("{\"a\":[1,2]}")),McMenuAccess.canonicalComponents(JsonParser.parseString("{\"a\":[2,1]}")));assertNotEquals(McMenuAccess.canonicalComponents(JsonParser.parseString("{\"a\":1}")),McMenuAccess.canonicalComponents(JsonParser.parseString("{\"a\":\"1\"}")));}
    @Test void unknownIdentityCannotPretendExactBinding(){assertNotEquals(card(""),card("{}"));assertThrows(NullPointerException.class,()->card(null));}
}
