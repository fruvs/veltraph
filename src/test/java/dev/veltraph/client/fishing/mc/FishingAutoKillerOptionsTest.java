package dev.veltraph.client.fishing.mc;
import dev.veltraph.client.config.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FishingAutoKillerOptionsTest {
    private static java.util.List<dev.veltraph.client.combat.grind.BestiaryWeaponSelector.Candidate> hotbar(){
        var items=new java.util.ArrayList<dev.veltraph.client.combat.grind.BestiaryWeaponSelector.Candidate>();
        for(int i=0;i<9;i++)items.add(new dev.veltraph.client.combat.grind.BestiaryWeaponSelector.Candidate(i,i!=0&&i!=2,i==0||i==2?dev.veltraph.client.combat.grind.BestiaryWeaponSelector.Kind.SWORD:dev.veltraph.client.combat.grind.BestiaryWeaponSelector.Kind.OTHER,i==2?"Private automatic weapon":null));return items;
    }
    @Test void sourceFixedOneMeansNativeZeroAndAutomaticDelegatesExistingCommonPolicy(){
        assertEquals(0,FishingAutoKillerWeaponPolicy.select(FishingAutoKillerWeaponType.SLOT_1,hotbar(),"Private automatic weapon").orElseThrow().slot());
        assertEquals(2,FishingAutoKillerWeaponPolicy.select(FishingAutoKillerWeaponType.AUTOMATIC,hotbar(),"Private automatic weapon").orElseThrow().slot());
        assertThrows(IllegalArgumentException.class,()->FishingAutoKillerWeaponPolicy.policy(null));
    }
    @Test void partialDuplicateAndMissingFixedToolCannotAuthorizeSelection(){
        var items=hotbar();assertTrue(FishingAutoKillerWeaponPolicy.select(FishingAutoKillerWeaponType.AUTOMATIC,items.subList(0,8),"Private automatic weapon").isEmpty());
        var duplicate=new java.util.ArrayList<>(items);duplicate.set(8,items.get(2));assertTrue(FishingAutoKillerWeaponPolicy.select(FishingAutoKillerWeaponType.AUTOMATIC,duplicate,"Private automatic weapon").isEmpty());
        var empty=new java.util.ArrayList<>(items);empty.set(0,new dev.veltraph.client.combat.grind.BestiaryWeaponSelector.Candidate(0,true,dev.veltraph.client.combat.grind.BestiaryWeaponSelector.Kind.OTHER,null));assertTrue(FishingAutoKillerWeaponPolicy.select(FishingAutoKillerWeaponType.SLOT_1,empty,"Private automatic weapon").isEmpty());
    }

    @Test void explicitModesRoundTripWithoutChangingSharedCombatSlot(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) {
        var before=new JsonConfigService(PersistableKeys.all());
        before.set(FishingConfig.FISHING_AUTOKILLER_IGNORE_SQUID,false);
        before.set(FishingConfig.FISHING_AUTOKILLER_WEAPON_TYPE,FishingAutoKillerWeaponType.SLOT_1);
        before.set(CombatConfig.COMBAT_WEAPON_SLOT,7);
        var path=directory.resolve("options.json");before.save(path,null);
        var after=new JsonConfigService(PersistableKeys.all());after.load(path,null);
        assertFalse(after.get(FishingConfig.FISHING_AUTOKILLER_IGNORE_SQUID));
        assertEquals(FishingAutoKillerWeaponType.SLOT_1,after.get(FishingConfig.FISHING_AUTOKILLER_WEAPON_TYPE));
        assertEquals(7,after.get(CombatConfig.COMBAT_WEAPON_SLOT));
    }

    @Test void sourceDefaultsArePresentInPersistedFishingOptions() {
        var keys=PersistableKeys.collect(java.util.List.of(FishingConfig.class));
        var squid=keys.stream().filter(k->k.name().equals("FISHING_AUTOKILLER_IGNORE_SQUID")).findFirst();
        assertTrue(squid.isPresent(),"Readable source default true squid exclusion is missing");
        assertEquals(true,squid.orElseThrow().defaultValue());
    }
    @Test void sourceWeaponModesArePresentAndAutomaticByDefault() {
        var keys=PersistableKeys.collect(java.util.List.of(FishingConfig.class));
        var mode=keys.stream().filter(k->k.name().equals("FISHING_AUTOKILLER_WEAPON_TYPE")).findFirst();
        assertTrue(mode.isPresent(),"Readable source AUTOMATIC/SLOT_1 selector is missing");
        assertEquals("AUTOMATIC",mode.orElseThrow().defaultValue().toString());
        assertArrayEquals(new String[]{"AUTOMATIC","SLOT_1"},java.util.Arrays.stream(mode.orElseThrow().defaultValue().getClass().getEnumConstants()).map(Object::toString).toArray(String[]::new));
    }
}
