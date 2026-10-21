package dev.veltraph.client.capture.visitor;

import com.google.gson.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.veltraph.client.game.LocationText;
import dev.veltraph.client.mixin.PlayerListHudAccessor;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.*;
import net.minecraft.world.scores.*;
import java.nio.file.Path;
import java.util.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;

/** Explicit local readonly context capture paired by user label with the existing GUI recorder. */
public final class CaptureVisitorContextTools {
    public static final Path OUTPUT=Path.of("config/veltraph/captures/visitor-context").toAbsolutePath().normalize();
    private static boolean registered;
    private final Path output;private VisitorContextCaptureSession session;private long nextSample;
    private Object world,player,connection;private long worldEpoch,playerEpoch,connectionEpoch;
    private CaptureVisitorContextTools(Path output){this.output=output;}
    public static synchronized void register(){register(OUTPUT);}
    public static synchronized void register(Path output){
        if(registered)return;
        if(output==null||!output.isAbsolute()||!output.equals(output.normalize()))throw new IllegalArgumentException("Normalized absolute capture output required");
        var tool=new CaptureVisitorContextTools(output);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,registry)->dispatcher.register(literal("vcapture")
            .then(literal("visitor")
                .then(literal("start").then(argument("label",StringArgumentType.word()).executes(c->tool.start(c.getSource(),StringArgumentType.getString(c,"label")))))
                .then(literal("status").executes(c->tool.status(c.getSource())))
                .then(literal("sample").executes(c->tool.sample(c.getSource(),false)))
                .then(literal("tool").executes(c->tool.sample(c.getSource(),true)))
                .then(literal("stop").executes(c->tool.stop(c.getSource()))))));
        ClientTickEvents.END_CLIENT_TICK.register(tool::tick);registered=true;
    }
    private int start(FabricClientCommandSource source,String label){
        if(session!=null&&session.active()){source.sendError(Component.literal("Visitor context already recording; /vcapture visitor stop first"));return 0;}
        try{session=new VisitorContextCaptureSession(output,label,System.currentTimeMillis());world=player=connection=null;worldEpoch=playerEpoch=connectionEpoch=0;nextSample=0;
            source.sendFeedback(Component.literal("Readonly visitor context recording. Pair /vcapture gui start "+label+". /vcapture visitor tool records only your selected main-hand tool on demand. No actions are sent."));return 1;
        }catch(IllegalArgumentException|java.io.IOException failure){source.sendError(Component.literal("Visitor capture cannot start: label or SSD path unavailable/unsafe"));return 0;}
    }
    private int status(FabricClientCommandSource source){source.sendFeedback(Component.literal(session==null?"Visitor context idle":(session.active()?"Visitor context recording":"Visitor context stopped: "+session.stopReason())+" | "+session.frames()+" frames | "+session.directory()));return 1;}
    private int stop(FabricClientCommandSource source){if(session==null||!session.active())return status(source);try{session.stop("user_stop",System.currentTimeMillis());return status(source);}catch(java.io.IOException e){source.sendError(Component.literal("Capture stopped; existing context frames retained. Manifest update failed."));return 0;}finally{clearReferences();}}
    private int sample(FabricClientCommandSource source,boolean tool){if(session==null||!session.active()){source.sendError(Component.literal("Start /vcapture visitor start <label> first"));return 0;}try{record(Minecraft.getInstance(),tool);return 1;}catch(RuntimeException|java.io.IOException e){fail(source);return 0;}}
    private void tick(Minecraft client){if(session==null||!session.active()){clearReferences();return;}long nano=System.nanoTime();if(nano<nextSample)return;nextSample=nano+250_000_000L;try{record(client,false);}catch(RuntimeException|java.io.IOException e){try{session.stop("capture_error",System.currentTimeMillis());}catch(java.io.IOException ignored){}clearReferences();client.gui.hud.getChat().addClientSystemMessage(Component.literal("Visitor context capture stopped after an observation/write error; existing files retained."));}}
    private void clearReferences(){world=player=connection=null;}
    private void fail(FabricClientCommandSource source){clearReferences();try{session.stop("capture_error",System.currentTimeMillis());}catch(java.io.IOException ignored){}source.sendError(Component.literal("Visitor context observation/write failed; existing files retained."));}
    private void record(Minecraft c,boolean includeTool)throws java.io.IOException{
        if(!c.isSameThread())throw new IllegalStateException("Client-thread readonly capture required");
        var p=c.player;var w=c.level;var conn=c.getConnection();
        if(world!=w){world=w;worldEpoch++;}if(player!=p){player=p;playerEpoch++;}if(connection!=conn){connection=conn;connectionEpoch++;}
        var result=new JsonObject();result.addProperty("schemaVersion",1);result.addProperty("textBounds","Visible row/name text capped512 chars; tab header/footer4096 chars; exceeding text is truncated and must not be treated as complete binding evidence");result.addProperty("worldEpoch",worldEpoch);result.addProperty("playerEpoch",playerEpoch);result.addProperty("connectionEpoch",connectionEpoch);
        result.addProperty("profileIdentityStatus","UNKNOWN: observation only; no immutable active profile parser");result.addProperty("gardenIdentityStatus","UNKNOWN: area/owner text is not an independently confirmed Garden ID");
        if(p==null||w==null||conn==null){result.addProperty("nativeSession","unavailable");session.record(result,System.currentTimeMillis());return;}
        result.addProperty("nativeSession","present");result.addProperty("dimension",w.dimension().identifier().toString());
        result.addProperty("localPlayerMatchesNativeUserUUID",p.getGameProfile().id().equals(c.getUser().getProfileId()));
        var pose=new JsonObject();pose.add("feet",vector(p.position()));pose.add("eye",vector(p.getEyePosition()));pose.addProperty("yaw",p.getYRot());pose.addProperty("pitch",p.getXRot());result.add("pose",pose);
        var tab=new JsonObject();var overlay=c.gui.hud.getTabList();
        if(overlay instanceof PlayerListHudAccessor access){tab.addProperty("header",text(access.getHeader(),4096));tab.addProperty("footer",text(access.getFooter(),4096));var rows=new JsonArray();int count=0;for(var info:access.veltraph$getPlayerInfos()){if(++count>80)break;var display=info.getTabListDisplayName();rows.add(display==null?bounded(info.getProfile()==null?"":info.getProfile().name(),512):text(display,512));}tab.add("displayRows",rows);tab.addProperty("status","actual_native_accessor");}
        else tab.addProperty("status","unavailable_accessor");result.add("tab",tab);
        var sidebar=new JsonObject();var scoreboard=w.getScoreboard();var objective=scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if(objective!=null){sidebar.addProperty("title",text(objective.getDisplayName(),512));var rows=new JsonArray();int count=0;for(var entry:scoreboard.listPlayerScores(objective)){if(entry.isHidden())continue;if(++count>40)break;var team=scoreboard.getPlayersTeam(entry.owner());String line=team==null?entry.owner():team.getPlayerPrefix().getString()+entry.owner()+team.getPlayerSuffix().getString();rows.add(bounded(line,512));}sidebar.add("displayRows",rows);}else sidebar.addProperty("status","no_sidebar_objective");result.add("sidebar",sidebar);
        var ray=new JsonObject();if(c.hitResult!=null){ray.addProperty("type",c.hitResult.getType().name());ray.add("hit",vector(c.hitResult.getLocation()));if(c.hitResult instanceof BlockHitResult block){ray.addProperty("blockX",block.getBlockPos().getX());ray.addProperty("blockY",block.getBlockPos().getY());ray.addProperty("blockZ",block.getBlockPos().getZ());ray.addProperty("face",block.getDirection().name());if(w.hasChunkAt(block.getBlockPos()))ray.addProperty("blockId",BuiltInRegistries.BLOCK.getKey(w.getBlockState(block.getBlockPos()).getBlock()).toString());}
            else if(c.hitResult instanceof EntityHitResult entity&&!(entity.getEntity() instanceof net.minecraft.world.entity.player.Player)){ray.addProperty("entityRuntimeId",entity.getEntity().getId());ray.addProperty("entityType",BuiltInRegistries.ENTITY_TYPE.getKey(entity.getEntity().getType()).toString());ray.addProperty("visibleName",text(entity.getEntity().getName(),512));}}result.add("ray",ray);
        var stands=new JsonArray();int inspected=0,kept=0;for(var entity:w.entitiesForRendering()){if(++inspected>4096)break;if(!(entity instanceof ArmorStand stand)||stand.isRemoved()||stand.distanceToSqr(p)>256)continue;if(++kept>128)break;var row=new JsonObject();row.addProperty("runtimeId",stand.getId());row.add("position",vector(stand.position()));row.addProperty("visibleName",stand.hasCustomName()?text(stand.getCustomName(),512):"");row.addProperty("nameVisible",stand.isCustomNameVisible());row.addProperty("loaded",w.hasChunkAt(stand.blockPosition()));row.addProperty("mainHandItemId",BuiltInRegistries.ITEM.getKey(stand.getMainHandItem().getItem()).toString());row.addProperty("mainHandIsStal",stand.getMainHandItem().is(Items.MUSIC_DISC_STAL));stands.add(row);}result.add("nearbyNonPlayerArmorStands",stands);result.addProperty("entityScanBoundReached",inspected>4096||kept>128);
        var screen=c.gui.screen();if(screen instanceof AbstractContainerScreen<?> nativeScreen&&nativeScreen.getMenu()==p.containerMenu){result.addProperty("menuTitle",text(nativeScreen.getTitle(),512));result.addProperty("menuId",nativeScreen.getMenu().containerId);result.addProperty("menuRevision",nativeScreen.getMenu().getStateId());}
        var profileControl=McProfileManagementControl.read(c);if(profileControl.isPresent()){var control=profileControl.get();var row=new JsonObject();row.addProperty("nativeSlot",control.nativeSlot());row.addProperty("profileCount",control.profileCount());row.addProperty("capacity",control.capacity());row.addProperty("observedCommand",control.command());row.addProperty("identityStatus",control.identityStatus());result.add("actualProfileManagementControl",row);}
        if(includeTool){var stack=p.getMainHandItem();result.add("explicitSelectedMainHand",selectedTool(c,stack));result.addProperty("selectedToolMeaning","Explicit readonly sample, not tool capability or effect proof");}
        if(c.player!=p||c.level!=w||c.getConnection()!=conn)throw new IllegalStateException("Native context changed while observing");
        session.record(result,System.currentTimeMillis());
    }
    private static JsonObject selectedTool(Minecraft c,ItemStack stack){
        var row=new JsonObject();row.addProperty("selectedHotbarSlot",c.player.getInventory().getSelectedSlot());row.addProperty("itemId",BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());row.addProperty("count",stack.getCount());row.addProperty("visibleName",text(stack.getHoverName(),512));
        var lore=new JsonArray();var nativeLore=stack.get(DataComponents.LORE);if(nativeLore!=null){int count=0;for(var line:nativeLore.lines()){if(++count>80)break;lore.add(text(line,512));}}row.add("lore",lore);
        var ids=new JsonObject();var data=stack.get(DataComponents.CUSTOM_DATA);
        if(data!=null){var tag=data.copyTag();tag.getString("id").ifPresent(id->ids.addProperty("id",bounded(id,128)));var extra=tag.getCompoundOrEmpty("ExtraAttributes");extra.getString("id").ifPresent(id->ids.addProperty("ExtraAttributes.id",bounded(id,128)));}
        row.add("whitelistedCustomDataIds",ids);row.addProperty("componentPolicy","Only selected-hand visible fields and whitelisted custom-data id/ExtraAttributes.id. No arbitrary tags, UUIDs, skins or inventory enumeration.");return row;
    }
    private static String text(Component value,int max){return bounded(value==null?"":value.getString(),max);}
    private static String bounded(String s,int max){if(s==null)return "";String out=LocationText.stripColorCodes(s);return out.length()>max?out.substring(0,max):out;}
    private static JsonObject vector(Vec3 v){var o=new JsonObject();o.addProperty("x",v.x);o.addProperty("y",v.y);o.addProperty("z",v.z);return o;}
}
