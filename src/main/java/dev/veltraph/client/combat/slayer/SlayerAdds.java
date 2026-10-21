package dev.veltraph.client.combat.slayer;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;

/** Add role/nameplate observations never replace independent server owner metadata. */
public final class SlayerAdds {
    public record Result(LivingEntity target,String name,boolean withheld){}
    private Object world;private final Set<UUID> rejected=new HashSet<>();
    private final Map<UUID,UUID> ownerPlates=new HashMap<>();
    public void reset(){world=null;rejected.clear();ownerPlates.clear();}
    public Result observe(Minecraft client,LivingEntity selected,String label,List<Entity> entities){
        if(world!=client.level){reset();world=client.level;}
        var family=SlayerSignals.family(label);var bossOwner=SlayerEntityOwnership.own(client,selected,entities);
        if(family==SlayerSignals.Family.OTHER)return new Result(null,"",false);
        LivingEntity best=null;String bestName="";boolean unknown=false;
        for(var entity:entities)if(entity instanceof LivingEntity body&&!(body instanceof ArmorStand)&&body!=selected&&body!=client.player&&body.isAlive()){
            var name=SlayerEntityOwnership.name(client,body,entities,text->SlayerSignals.response(family,text)==SlayerSignals.Response.ADDS);
            if(name.isEmpty())continue;
            var owner=SlayerEntityOwnership.own(client,body,entities);
            if(rejected.size()+ownerPlates.size()>=512){unknown=true;continue;}
            if(owner.isEmpty()&&SlayerEntityOwnership.hasOwnerLabel(body,entities))rejected.add(body.getUUID());
            var established=ownerPlates.get(body.getUUID());
            if(bossOwner.isEmpty()||owner.isEmpty()||rejected.contains(body.getUUID())
                    ||established!=null&&!established.equals(owner.get().plate())){unknown=true;continue;}
            ownerPlates.putIfAbsent(body.getUUID(),owner.get().plate());
            if(best==null||body.distanceToSqr(client.player)<best.distanceToSqr(client.player)){best=body;bestName=name.get();}
        }
        // A role plate with ambiguous physical bodies cannot silently fall back to attacking the boss.
        for(var entity:entities)if(entity instanceof ArmorStand plate&&plate.isAlive()&&plate.hasCustomName()
                &&SlayerSignals.response(family,plate.getName().getString())==SlayerSignals.Response.ADDS){
            long bodies=entities.stream().filter(other->other instanceof LivingEntity&&!(other instanceof ArmorStand)&&other!=client.player&&other.isAlive()
                    &&(plate.getVehicle()!=null?plate.getVehicle()==other:plate.getBoundingBox().inflate(.6,2,.6).intersects(other.getBoundingBox()))).count();if(bodies!=1)unknown=true;
        }
        return new Result(best,bestName,best==null&&unknown);
    }
}
