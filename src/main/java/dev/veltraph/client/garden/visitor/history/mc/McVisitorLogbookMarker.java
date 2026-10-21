package dev.veltraph.client.garden.visitor.history.mc;

import dev.veltraph.client.garden.visitor.history.VisitorLogbookMarker;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Items;

/** Actual loaded entity observation only. Does not look, move, interact or open a guessed menu. */
public final class McVisitorLogbookMarker {
    private McVisitorLogbookMarker() {}
    /** Readable source measures its <=3 preflight from getEyePosition(), never player feet. */
    public static Optional<VisitorLogbookMarker.Anchor> preflight(Minecraft client){
        if(client==null||client.player==null||client.level==null)return Optional.empty();
        var player=client.player;var world=client.level;var connection=client.getConnection();
        var anchor=read(client);var eye=player.getEyePosition();
        if(player!=client.player||world!=client.level||connection!=client.getConnection())return Optional.empty();
        return anchor.filter(a->a.withinThreeOfEye(eye.x,eye.y,eye.z));
    }
    public static Optional<VisitorLogbookMarker.Anchor> read(Minecraft client) {
        if(client==null||client.level==null||client.player==null)return Optional.empty();
        var world=client.level;var player=client.player;var connection=client.getConnection();
        var stands=new ArrayList<VisitorLogbookMarker.Stand>();int inspected=0;
        try{
            for(var entity:world.entitiesForRendering()){
                if(++inspected>4096)return Optional.empty();
                if(entity instanceof ArmorStand stand&&!stand.isRemoved())
                    stands.add(new VisitorLogbookMarker.Stand(stand.getUUID(),true,
                            world.hasChunkAt(stand.blockPosition()),stand.getMainHandItem().is(Items.MUSIC_DISC_STAL),
                            stand.getX(),stand.getY(),stand.getZ()));
            }
            if(world!=client.level||player!=client.player||connection!=client.getConnection())return Optional.empty();
            return VisitorLogbookMarker.find(stands);
        }catch(RuntimeException unknown){return Optional.empty();}
    }
}
