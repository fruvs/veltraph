package dev.veltraph.client.garden.visitor.mc;
import dev.veltraph.client.garden.visitor.*;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Reads visitor rarity from the named NPC's styled name without applying reward-item colors. */
final class McVisitorIdentity {
    private McVisitorIdentity(){}
    static VisitorRarity rarity(String name,List<String> lore,Component namedNpc){
        var explicit=VisitorLoreReader.parseRarity(name,lore);if(explicit!=VisitorRarity.UNKNOWN)return explicit;
        if(namedNpc==null)return VisitorRarity.UNKNOWN;var colors=new HashSet<Integer>();
        namedNpc.visit((style,text)->{if(!text.isBlank()&&style.getColor()!=null)colors.add(style.getColor().getValue());return Optional.empty();},net.minecraft.network.chat.Style.EMPTY);
        return colors.size()==1?VisitorRarity.fromRgb(colors.iterator().next()):VisitorRarity.UNKNOWN;
    }
}
