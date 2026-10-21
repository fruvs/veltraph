package dev.veltraph.client.combat.heal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HealLookDownPolicyTest {
 @Test void exactLatestEnumNamesAndIronSword(){for(var n:new String[]{"Astraea","Hyperion","Scylla","Valkyrie"}){assertTrue(HealLookDownPolicy.required(true,"Heroic "+n,true));assertFalse(HealLookDownPolicy.required(false,n,true));}}
 @Test void instancePermissionAndUnclassifiedToolsDoNotTurn(){assertFalse(HealLookDownPolicy.required(true,"Hyperion",false));for(var n:new String[]{"hyperion","Florid Zombie Sword","Wand of Atonement","",null})assertFalse(HealLookDownPolicy.required(true,n,true));}
}
