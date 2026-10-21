package dev.veltraph.client.mining.recorded;

import dev.veltraph.client.inventory.NativeStackIdentity;
import dev.veltraph.client.mining.MiningServices;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Exact, local vanilla block-use binding for the source RIGHT_CLICK key. No inferred menu/ability. */
public final class McRecordedEchoPort implements RecordedEchoPort {
    private final Minecraft client;private final MiningServices mining;
    private RecordedEchoProfile.Placement owned;
    private NativeStackIdentity.Snapshot stack;
    private net.minecraft.client.player.LocalPlayer player;
    private net.minecraft.client.multiplayer.ClientLevel world;
    private net.minecraft.world.level.block.state.BlockState support;
    private int original=-1,slot=-1,count;private boolean submitted;
    public McRecordedEchoPort(Minecraft client,MiningServices mining){this.client=client;this.mining=mining;}
    public MiningServices mining(){return mining;}
    private net.minecraft.core.BlockPos mc(dev.veltraph.client.world.BlockPos p){return new net.minecraft.core.BlockPos(p.x(),p.y(),p.z());}
    private boolean loaded(dev.veltraph.client.world.BlockPos p){return client.level!=null&&client.level.isInWorldBounds(mc(p))&&client.level.getChunkSource().hasChunk(p.x()>>4,p.z()>>4);}
    private String id(net.minecraft.world.level.block.state.BlockState s){return BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();}
    private Vec3 point(RecordedEchoProfile.Placement p){return Vec3.atCenterOf(mc(p.support())).add(p.face().dx()*.499,p.face().dy()*.499,p.face().dz()*.499);}
    private boolean safe(RecordedEchoProfile.Placement p) {
        if(!mining.available()||!loaded(p.support())||!loaded(p.result())||client.player.containerMenu!=client.player.inventoryMenu||!client.player.inventoryMenu.getCarried().isEmpty())return false;
        var base=client.level.getBlockState(mc(p.support()));
        if(base.isAir()||base.hasBlockEntity()||!base.getFluidState().isEmpty()||!client.level.getBlockState(mc(p.result())).isAir())return false;
        // Plain structural supports cannot open an unrelated vanilla menu or toggle an interactive block.
        if(!java.util.Set.of("minecraft:stone","minecraft:cobblestone","minecraft:deepslate","minecraft:cobbled_deepslate","minecraft:andesite","minecraft:diorite","minecraft:granite","minecraft:dirt","minecraft:grass_block","minecraft:obsidian").contains(id(base)))return false;
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++) {
            var pos=p.result().add(dx,dy,dz);if(!loaded(pos))return false;
            var state=client.level.getBlockState(mc(pos));if(!state.getFluidState().isEmpty())return false;
        }
        var box=new AABB(mc(p.result()));if(box.intersects(client.player.getBoundingBox())||!client.level.getEntities(client.player,box).isEmpty())return false;
        var target=point(p);if(client.player.getEyePosition().distanceTo(target)>client.player.blockInteractionRange())return false;
        var trace=client.level.clip(new ClipContext(client.player.getEyePosition(),target,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,client.player));
        return trace.getType()==HitResult.Type.BLOCK&&trace.getBlockPos().equals(mc(p.support()))&&trace.getDirection().name().equals(p.face().name());
    }
    private boolean sameMaterial(int expectedCount) {
        if(slot<0||player!=client.player||world!=client.level||client.player.getInventory().getSelectedSlot()!=slot)return false;
        var current=client.player.getInventory().getItem(slot);
        if(current.getCount()!=expectedCount)return false;
        if(expectedCount==0)return current.isEmpty();
        var originalCount=current.copy();originalCount.setCount(count);return stack.matches(originalCount,client.level.registryAccess());
    }
    public String placementProblem(RecordedEchoProfile.Placement p) {
        if(owned!=null&&!owned.equals(p))return "Another recorded placement owns the material";
        if(submitted) {
            if(player!=client.player||world!=client.level||!loaded(p.result())||!loaded(p.support())||!support.equals(client.level.getBlockState(mc(p.support()))))return "Recorded placement context/support changed";
            if(!sameMaterial(count)&&!sameMaterial(count-1))return "Recorded placement full component material changed";
            var result=client.level.getBlockState(mc(p.result()));if(!result.isAir()&&!id(result).equals(p.block()))return "Recorded result contains an unexpected block";
            return "";
        }
        if(!safe(p))return "Recorded placement lacks loaded support, reach, LOS or free destination";
        if(owned!=null)return sameMaterial(count)&&support.equals(client.level.getBlockState(mc(p.support())))?"":"Recorded material or support changed";
        int found=-1;
        for(int i=0;i<9;i++) {
            var item=client.player.getInventory().getItem(i);
            if(!item.isEmpty()&&item.getItem() instanceof BlockItem block&&BuiltInRegistries.ITEM.getKey(item.getItem()).toString().equals(p.item())&&BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString().equals(p.block())&&!block.getBlock().defaultBlockState().hasBlockEntity()){found=i;break;}
        }
        if(found<0)return "Explicit recorded placement material is absent from the hotbar";
        player=client.player;world=client.level;original=player.getInventory().getSelectedSlot();slot=found;
        var item=player.getInventory().getItem(slot);count=item.getCount();
        try{stack=NativeStackIdentity.capture(item,world.registryAccess());}catch(RuntimeException bad){releasePlacement();return "Recorded material has unsupported components";}
        support=world.getBlockState(mc(p.support()));owned=p;mining.farm().inventory().selectSlot(slot);return "";
    }
    public boolean placementComplete(RecordedEchoProfile.Placement p){return submitted&&p.equals(owned)&&mining.available()&&loaded(p.result())&&loaded(p.support())&&player==client.player&&world==client.level&&support.equals(world.getBlockState(mc(p.support())))&&id(world.getBlockState(mc(p.result()))).equals(p.block())&&sameMaterial(count-1);}
    public boolean place(RecordedEchoProfile.Placement p) {
        if(submitted||!p.equals(owned)||!placementProblem(p).isEmpty()||!safe(p))return false;
        if(!(client.hitResult instanceof BlockHitResult hit)||hit.getType()!=HitResult.Type.BLOCK||!hit.getBlockPos().equals(mc(p.support()))||!hit.getDirection().name().equals(p.face().name()))return false;
        client.gameMode.useItemOn(client.player,InteractionHand.MAIN_HAND,hit);submitted=true;return true;
    }
    public void releasePlacement(){
        if(original>=0&&player==client.player&&world==client.level&&client.player.getInventory().getSelectedSlot()==slot)mining.farm().inventory().selectSlot(original);
        owned=null;stack=null;player=null;world=null;support=null;original=slot=-1;count=0;submitted=false;
    }
}
