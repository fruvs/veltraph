package dev.veltraph.client.fishing.mc;

import dev.veltraph.client.combat.grind.CombatWeaponPolicy;
import dev.veltraph.client.combat.grind.BestiaryWeaponSelector;
import java.util.List;
import java.util.Optional;

/** Readable source branch. Automatic's unrecovered native weights stay empty in the existing common policy. */
public final class FishingAutoKillerWeaponPolicy {
    private FishingAutoKillerWeaponPolicy() {}
    public static CombatWeaponPolicy policy(FishingAutoKillerWeaponType mode) {
        if(mode==null)throw new IllegalArgumentException("Fishing auto-killer weapon mode unavailable");
        return mode==FishingAutoKillerWeaponType.SLOT_1?CombatWeaponPolicy.fixed():CombatWeaponPolicy.common();
    }
    public static int fixedSlot(FishingAutoKillerWeaponType mode) {
        if(mode==null)throw new IllegalArgumentException("Fishing auto-killer weapon mode unavailable");
        return 0;
    }
    public static Optional<BestiaryWeaponSelector.Choice> select(FishingAutoKillerWeaponType mode,List<BestiaryWeaponSelector.Candidate> observed,String preferred) {
        if(observed==null||observed.size()!=9)return Optional.empty();
        var slots=new boolean[9];for(var item:observed){if(item==null||item.slot()<0||item.slot()>8||slots[item.slot()])return Optional.empty();slots[item.slot()]=true;}
        return policy(mode).select(observed,fixedSlot(mode),preferred);
    }
}
