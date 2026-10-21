package dev.veltraph.client.combat.slayer;

import dev.veltraph.client.gui.MenuSnapshot;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

/** Server owner/name metadata must identify one physical UUID; distance or birth alone is insufficient. */
public final class SlayerEntityOwnership {
    public record Owner(UUID body,UUID plate,String player) {}
    private SlayerEntityOwnership(){}
    public static List<Entity> visible(Minecraft client){var all=new ArrayList<Entity>();if(client.level!=null)for(var entity:client.level.entitiesForRendering())all.add(entity);return all;}
    private static boolean physical(Minecraft client,Entity entity){return entity instanceof LivingEntity&&!(entity instanceof ArmorStand)
            &&entity!=client.player&&entity.isAlive()&&(!(entity instanceof Player)||client.getConnection()!=null&&client.getConnection().getPlayerInfo(entity.getUUID())==null);}
    private static boolean attached(Entity plate,Entity body){return plate.getVehicle()!=null?plate.getVehicle()==body:plate.getBoundingBox().inflate(.6,2,.6).intersects(body.getBoundingBox());}
    public static boolean unambiguous(Minecraft client,Entity plate,LivingEntity body,List<Entity> entities){
        return attached(plate,body)&&entities.stream().filter(entity->physical(client,entity)&&attached(plate,entity)).count()==1;
    }
    public static Optional<Owner> own(Minecraft client,LivingEntity body,List<Entity> entities){
        if(client.player==null||client.level==null||client.getConnection()==null||!physical(client,body)
                ||client.level.getEntity(body.getId())!=body||!client.level.isLoaded(body.blockPosition()))return Optional.empty();
        var owners=entities.stream().filter(entity->entity instanceof ArmorStand&&entity.isAlive()&&entity.hasCustomName()
                &&attached(entity,body)&&MenuSnapshot.clean(entity.getName().getString()).startsWith("Spawned by:")).toList();
        if(owners.size()!=1)return Optional.empty();
        var plate=owners.getFirst();String expected="Spawned by: "+client.player.getName().getString();
        if(!MenuSnapshot.clean(plate.getName().getString()).equals(expected)||!client.level.isLoaded(plate.blockPosition())
                ||!unambiguous(client,plate,body,entities))return Optional.empty();
        return Optional.of(new Owner(body.getUUID(),plate.getUUID(),client.player.getName().getString()));
    }
    public static boolean hasOwnerLabel(LivingEntity body,List<Entity> entities){return entities.stream().anyMatch(entity->entity instanceof ArmorStand&&entity.isAlive()
            &&entity.hasCustomName()&&attached(entity,body)&&MenuSnapshot.clean(entity.getName().getString()).startsWith("Spawned by:"));}
    public static Optional<String> name(Minecraft client,LivingEntity body,List<Entity> entities,Predicate<String> allowed){
        String nativeName=body.getName().getString();if(allowed.test(nativeName))return Optional.of(nativeName);
        var labels=entities.stream().filter(entity->entity instanceof ArmorStand&&entity.isAlive()&&entity.hasCustomName()
                &&allowed.test(entity.getName().getString())&&attached(entity,body)).toList();
        return labels.size()==1&&unambiguous(client,labels.getFirst(),body,entities)
                ?Optional.of(labels.getFirst().getName().getString()):Optional.empty();
    }
}
