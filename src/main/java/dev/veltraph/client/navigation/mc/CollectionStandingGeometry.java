package dev.veltraph.client.navigation.mc;
import dev.veltraph.client.navigation.*;
import dev.veltraph.client.world.Vec3;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
/** Native proof before callbacks; final Core leaf reads only the resulting scene watch. */
public final class CollectionStandingGeometry {
    private CollectionStandingGeometry(){}
    public static final class Proof {
        private final Minecraft client;private final Object player,world,connection;private final AABB region;
        private final CollectionSceneHistory.Snapshot scene;
        private Proof(Minecraft c,AABB region,CollectionSceneHistory.Snapshot scene){client=c;player=c.player;world=c.level;connection=c.getConnection();this.region=region;this.scene=scene;}
        /** Stored scene/native fields only. No shape/collision/clock/provider method executes. */
        public boolean nativeCurrent(){return client.isSameThread()&&client.player==player&&client.level==world&&client.getConnection()==connection
            &&client.player!=null&&CollectionSceneHistory.scenePlayerClear(scene)&&contains(region,client.player.getBoundingBox());}
    }
    private static boolean contains(AABB a,AABB b){return b.minX>=a.minX&&b.maxX<=a.maxX&&b.minY>=a.minY&&b.maxY<=a.maxY&&b.minZ>=a.minZ&&b.maxZ<=a.maxZ;}
    public static Proof capture(Minecraft c,Vec3 from,Vec3 goal,ItemEntity ownedItem,DynamicRouteQueryFence fence){
        if(c==null||c.player==null||c.level==null||from==null||goal==null||ownedItem==null||fence==null
            ||!GroundLocomotionMotion.finite(from)||!GroundLocomotionMotion.finite(goal)||from.horizontalDistanceTo(goal)>2.5||Math.abs(from.y()-goal.y())>.12)return null;
        var originalPlayer=c.player;var originalWorld=c.level;var originalConnection=c.getConnection();var originalBox=c.player.getBoundingBox();
        var pass=new McGroundProfileGeometry.Pass(c);
        for(double dx:new double[]{-.05,0,.05})for(double dz:new double[]{-.05,0,.05}){
            var end=new Vec3(goal.x()+dx,goal.y(),goal.z()+dz);
            if(!fence.query(()->McGroundProfileGeometry.union(c,from,end,pass)))return null;
        }
        // Native dynamic-route windows retain their original .7 width; include full body and support halo.
        double half=c.player.getBbWidth()/2.0,height=c.player.getBbHeight(),extra=.7+.05;
        var region=new AABB(Math.min(from.x(),goal.x())-half-extra,Math.min(from.y(),goal.y())-1,
            Math.min(from.z(),goal.z())-half-extra,Math.max(from.x(),goal.x())+half+extra,
            Math.max(from.y(),goal.y())+height+.05,Math.max(from.z(),goal.z())+half+extra);
        var domain=FlightCollisionDomain.of(region.minX,region.minY,region.minZ,region.maxX,region.maxY,region.maxZ);
        if(domain==null||domain.cells()>pass.remaining[0])return null;
        var cells=new ArrayList<BlockPos>();for(int x=domain.minX();x<=domain.maxX();x++)for(int y=domain.minY();y<=domain.maxY();y++)for(int z=domain.minZ();z<=domain.maxZ();z++)cells.add(new BlockPos(x,y,z));
        // Charge every scene capture read inside this same original 4096-cell/50ms phase.
        pass.remaining[0]-=cells.size();
        if(!fence.available()||pass.remaining[0]<0)return null;
        var scene=CollectionSceneHistory.capture(c,cells,region,ownedItem);
        if(scene==null)return null;
        pass.remaining[0]-=Math.max(0,CollectionSceneHistory.capturedSampleCount(scene)-cells.size());
        if(pass.remaining[0]<0||!fence.available()||!pass.revalidate(fence)||!CollectionSceneHistory.scenePlayerClear(scene)
            ||c.player!=originalPlayer||c.level!=originalWorld||c.getConnection()!=originalConnection||!c.player.getBoundingBox().equals(originalBox))return null;
        var proof=new Proof(c,region,scene);return proof.nativeCurrent()?proof:null;
    }
}
