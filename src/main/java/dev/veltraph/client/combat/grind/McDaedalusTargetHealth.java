package dev.veltraph.client.combat.grind;

import dev.veltraph.client.combat.slayer.SlayerEntityOwnership;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import java.util.Optional;

/** Native target-associated HP producer. No unrelated nameplate or vanilla twenty-HP approximation. */
final class McDaedalusTargetHealth {
    private final Minecraft client;
    private Object world;private LivingEntity target;private ArmorStand plate;private float reference;
    McDaedalusTargetHealth(Minecraft client){this.client=client;}
    record Observed(LivingEntity target,ArmorStand plate,String rawName,float current,float reference) {}
    Optional<Observed> observe(LivingEntity body) {
        if(body==null||client.level==null||body.level()!=client.level||!body.isAlive()
                ||client.level.getEntity(body.getId())!=body||!client.level.isLoaded(body.blockPosition()))return Optional.empty();
        var all=SlayerEntityOwnership.visible(client);
        var plates=all.stream().filter(entity->entity instanceof ArmorStand&&entity.isAlive()&&entity.hasCustomName()
                &&client.level.isLoaded(entity.blockPosition())&&SlayerEntityOwnership.unambiguous(client,entity,body,all)
                &&ReadableDaedalusHealth.parseAssociatedPlate(entity.getCustomName().getString()).isPresent()).toList();
        if(plates.isEmpty())return Optional.empty();
        ArmorStand actual;
        if(world==client.level&&target==body&&plate!=null) {
            if(!plates.contains(plate))return Optional.empty();
            actual=plate; // source keeps the initially associated native plate
        } else {
            var closest=plates.stream().min(java.util.Comparator.comparingDouble(entity->entity.distanceTo(body))).orElseThrow();
            float distance=closest.distanceTo(body);
            if(plates.stream().filter(entity->Float.compare(entity.distanceTo(body),distance)==0).count()!=1)return Optional.empty();
            actual=(ArmorStand)closest;
        }
        String name=actual.getCustomName().getString();
        var hp=ReadableDaedalusHealth.parseAssociatedPlate(name);if(hp.isEmpty())return Optional.empty();
        if((world!=client.level||target!=body)&&hp.get()<=0)return Optional.empty();
        if(world!=client.level||target!=body){world=client.level;target=body;plate=actual;reference=hp.get();}
        return Optional.of(new Observed(body,actual,name,hp.get(),reference));
    }
    /** Callback-free native observation leaf: no role, predicate, config or private-cache mutation. */
    Optional<Observed> leaf(Observed pinned,boolean exactValue){
        if(pinned==null||world!=client.level||target!=pinned.target||plate!=pinned.plate
                ||Float.compare(reference,pinned.reference)!=0||client.level==null||target==null||!target.isAlive()
                ||target.level()!=client.level||client.level.getEntity(target.getId())!=target||!client.level.isLoaded(target.blockPosition())
                ||plate==null||!plate.isAlive()||!plate.hasCustomName()||client.level.getEntity(plate.getId())!=plate
                ||!client.level.isLoaded(plate.blockPosition())
                ||!SlayerEntityOwnership.unambiguous(client,plate,target,SlayerEntityOwnership.visible(client)))return Optional.empty();
        String raw=plate.getCustomName().getString();var current=ReadableDaedalusHealth.parseAssociatedPlate(raw);
        if(current.isEmpty()||exactValue&&(!raw.equals(pinned.rawName)||Float.compare(current.get(),pinned.current)!=0))return Optional.empty();
        return Optional.of(new Observed(target,plate,raw,current.get(),reference));
    }
    void reset(){world=null;target=null;plate=null;reference=0;}
}
