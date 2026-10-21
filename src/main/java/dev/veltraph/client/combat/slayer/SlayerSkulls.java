package dev.veltraph.client.combat.slayer;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;

/** Texture identifiers from the pinned SkyHanni resource inventory; no downloads or profile lookups. */
public final class SlayerSkulls {
    public enum Kind { NONE, NUKEKUBI, BLOOD_ICHOR, KILLER_SPRING }
    public static Kind identify(LivingEntity entity) {
        var profile=entity.getItemBySlot(EquipmentSlot.HEAD).get(DataComponents.PROFILE);
        if(profile==null)return Kind.NONE;
        Kind identified=Kind.NONE;
        for(var property:profile.partialProfile().properties().get("textures")) {
            Kind next=switch(SkullTexture.hash(property.value())) {
                case "eb07594e2df273921a77c101d0bfdfa1115abed5b9b2029eb496ceba9bdbb4b3" -> Kind.NUKEKUBI;
                case "c0340923a6de4825a176813d133503eff186db0896e32b6704928c2a2bf68422" -> Kind.BLOOD_ICHOR;
                case "77f7a7bc8ac86f23ca7bf98afeb76960227e1832fe209a3026f6ceb8bde74f54" -> Kind.KILLER_SPRING;
                default -> Kind.NONE;
            };
            if(next!=Kind.NONE) {if(identified!=Kind.NONE&&identified!=next)return Kind.NONE;identified=next;}
        }
        return identified;
    }
}
