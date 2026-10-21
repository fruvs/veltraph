package dev.veltraph.client.greenhouse;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import static dev.veltraph.client.greenhouse.GreenhouseEnvironmentCensus.*;
import static dev.veltraph.client.greenhouse.GreenhouseModel.*;

/** Finite native leaf. Reads loaded cells, support and nearby bodies twice; never loads chunks or issues actions. */
final class McGreenhouseEnvironmentCensus {
    private final Minecraft client;
    private Object player,world,connection;
    private GreenhouseNativeInventory protectedInventory;
    private Sample latest;private long lastRead=-1;
    McGreenhouseEnvironmentCensus(Minecraft client){this.client=Objects.requireNonNull(client);}
    private boolean actor(){return client.isSameThread()&&client.player!=null&&client.level!=null&&client.getConnection()!=null
        &&client.player.isAlive()&&client.player.level()==client.level&&client.getCameraEntity()==client.player&&!client.isPaused()
        &&client.player.containerMenu==client.player.inventoryMenu&&client.player.inventoryMenu.getCarried().isEmpty();}
    private GreenhouseNativeInventory inventory(){
        var slots=new ArrayList<ItemStack>();for(int i=0;i<client.player.getInventory().getContainerSize();i++)slots.add(client.player.getInventory().getItem(i));
        var gear=new EnumMap<EquipmentSlot,ItemStack>(EquipmentSlot.class);for(var e:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.OFFHAND))gear.put(e,client.player.getItemBySlot(e));
        return GreenhouseNativeInventory.capture(slots,gear,client.player.inventoryMenu.getCarried(),client.player.getInventory().getSelectedSlot(),client.level.registryAccess());
    }
    Sample capture(Request request,long observedAt){
        Objects.requireNonNull(request);if(!actor()||observedAt<0)throw new IllegalArgumentException("Loaded native actor/connection, closed container and empty cursor required");
        player=client.player;world=client.level;connection=client.getConnection();protectedInventory=inventory();
        String dimension=client.level.dimension().identifier().toString();var before=read(request);var after=read(request);
        boolean stable=scope()&&protectedInventory.equals(inventory())&&before.equals(after);
        latest=new Sample(request,dimension,observedAt,after,stable);lastRead=observedAt;return latest;
    }
    Optional<Sample> fresh(long now,long maxAge){
        if(latest==null)return Optional.empty();
        if(now<lastRead||now<latest.observedAt()||maxAge<1||now-latest.observedAt()>maxAge||!scope()||!protectedInventory.equals(inventory())||!latest.stable()
                ||!latest.cells().equals(read(latest.request()))){latest=null;return Optional.empty();}
        lastRead=now;return Optional.of(latest);
    }
    private boolean scope(){return actor()&&player==client.player&&world==client.level&&connection==client.getConnection();}
    private Map<Cell,NativeCell> read(Request request){
        var cells=new HashMap<Cell,NativeCell>();
        for(Cell c:request.cells()){
            Cell below;try{below=new Cell(c.x(),Math.subtractExact(c.y(),1),c.z());}catch(ArithmeticException height){below=null;}
            if(!McGreenhouseLoadedCell.readable(client.level,c)||!McGreenhouseLoadedCell.readable(client.level,below)){
                cells.put(c,new NativeCell(c,false,"","","","",List.of(),false));continue;
            }
            var position=new BlockPos(c.x(),c.y(),c.z());var block=client.level.getBlockState(position);var soil=client.level.getBlockState(position.below());
            // Local bodies intersecting this unit plant cell are observations, never automatically donors or blockers.
            var box=new AABB(position);var bodies=new ArrayList<Body>();boolean complete=true;
            for(var entity:client.level.getEntities(client.player,box)){
                if(entity.isRemoved())continue;
                if(bodies.size()==MAX_BODIES){complete=false;break;}
                var b=entity.getBoundingBox();bodies.add(new Body(entity.getId(),BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
                    new Position(entity.getX(),entity.getY(),entity.getZ()),b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ));
            }
            bodies.sort(Comparator.comparingInt(Body::localId));
            cells.put(c,new NativeCell(c,true,BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString(),block.toString(),
                BuiltInRegistries.BLOCK.getKey(soil.getBlock()).toString(),soil.toString(),bodies,complete));
        }
        return Map.copyOf(cells);
    }
}
