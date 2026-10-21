package dev.veltraph.client.hunting;

import dev.veltraph.client.navigation.*;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.world.*;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.FluidTags;

/** Read-only loaded, swept body-clear route cost for source target ranking; sends no movement. */
final class HuntingPathCost implements AmphibiousPathfinder.Terrain {
    private final Minecraft client;private final McNavigator geometry;
    HuntingPathCost(Minecraft client,McNavigator geometry){this.client=client;this.geometry=geometry;}
    public Vec3 position(BlockPos node){var p=new Vec3(node.x()+.5,node.y(),node.z()+.5);var floor=geometry.surfacePosition(p);return geometry.standingClear(floor,true)?floor:p;}
    public boolean water(Vec3 p){
        if(!geometry.bodyClear(p,true))return false;
        for(double dx:new double[]{-.299,.299})for(double dz:new double[]{-.299,.299}){
            boolean wet=false;for(double y=0;y<=1.8;y+=.5){var b=net.minecraft.core.BlockPos.containing(p.x()+dx,p.y()+.001+y,p.z()+dz);
                if(!client.level.getChunkSource().hasChunk(b.getX()>>4,b.getZ()>>4))return false;
                var fluid=client.level.getFluidState(b);
                if(fluid.is(FluidTags.WATER)&&b.getY()+fluid.getHeight(client.level,b)>p.y()+.001+y)wet=true;
            }if(!wet)return false;
        }return true;
    }
    public boolean occupiable(Vec3 p){return geometry.bodyClear(p,true)&&(water(p)||geometry.standingClear(p,true));}
    public boolean startClear(Vec3 p){return occupiable(p);}
    public boolean connection(Vec3 a,Vec3 b,int maxDrop){
        if(geometry.walkClear(a,b,true)||geometry.stepClear(a,b))return true;
        if(!water(a)||!water(b))return false;
        int n=Math.max(1,(int)Math.ceil(a.distanceTo(b)*10));for(int i=0;i<=n;i++)if(!occupiable(TravelPath.lerp(a,b,(double)i/n)))return false;return true;
    }
    double cost(Vec3 from,Vec3 goal){
        var route=AmphibiousPathfinder.find(this,from,goal,32,2000,0);if(!route.found())return Double.POSITIVE_INFINITY;
        double total=0;var previous=from;for(var p:route.nodes()){total+=previous.distanceTo(p);previous=p;}return total;
    }
}
