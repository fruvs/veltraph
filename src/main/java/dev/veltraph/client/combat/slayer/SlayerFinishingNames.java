package dev.veltraph.client.combat.slayer;

import dev.veltraph.client.gui.MenuSnapshot;
import dev.veltraph.client.combat.grind.ReadableDaedalusHealth;
import java.util.List;

/** Explicit modern declaration matching only. This pure name result grants no actor, tier, slot or attack. */
public final class SlayerFinishingNames {
    private SlayerFinishingNames(){}
    public static boolean matches(String nativeMenuDeclaration,String observedName){
        if(nativeMenuDeclaration==null||observedName==null)return false;
        List<String> names=switch(nativeMenuDeclaration){
            case "Revenant Horror" -> List.of("Revenant Horror","Atoned Horror");
            case "Tarantula Broodfather" -> List.of("Tarantula Broodfather");
            case "Sven Packmaster" -> List.of("Sven Packmaster");
            case "Voidgloom Seraph" -> List.of("Voidgloom Seraph");
            default -> List.of();
        };
        String clean=MenuSnapshot.clean(observedName).strip();
        if(clean.startsWith("☠ "))clean=clean.substring(2).strip();
        clean=clean.replaceFirst("^\\[Lv[0-9]+\\]\\s+","");
        for(String exact:names){
            if(clean.equals(exact))return true;
            if(clean.startsWith(exact+" ")&&ReadableDaedalusHealth.parseAssociatedPlate(clean).isPresent()
                    &&clean.substring(exact.length()+1).indexOf(' ')<0)return true;
        }
        return false;
    }
}
