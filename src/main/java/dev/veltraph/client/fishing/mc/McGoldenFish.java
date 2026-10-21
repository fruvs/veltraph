package dev.veltraph.client.fishing.mc;

import com.google.gson.JsonParser;
import dev.veltraph.client.fishing.*;
import dev.veltraph.client.service.RotationService;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Local profile matching and actual rod actions. Never resolves profiles or downloads textures. */
public final class McGoldenFish {
    public static final String TEXTURE="120cf3c0a40fc67e0e5fe0c46b0ae409ac71030a7656da17b11ed001645888fe";
    private final Minecraft client;private final RotationService rotation;
    private final GoldenFishController controller=new GoldenFishController();
    private boolean rotationOwned;
    private ArmorStand target;private Vec3 origin;private final ArrayDeque<Float> yaws=new ArrayDeque<>(),pitches=new ArrayDeque<>();
    public McGoldenFish(Minecraft client,RotationService rotation){this.client=client;this.rotation=rotation;}
    public void message(String text){
        boolean was=controller.active();controller.message(text,System.currentTimeMillis());
        if(!was&&controller.active()&&client.player!=null){origin=client.player.position();target=null;yaws.clear();pitches.clear();}
    }
    public boolean active(){return controller.active();}
    public String failure(){return controller.failure();}
    public String status(){return controller.state().name();}
    public void stop(){releaseRotation();controller.reset();target=null;origin=null;yaws.clear();pitches.clear();}
    public boolean tick(Runnable useRod){
        if(!controller.active())return false;
        if(client.player==null||client.level==null||client.gui.screen()!=null){stop();return false;}
        if(target==null&&controller.state()==GoldenFishController.State.SEARCH) {
            // The source scans within ten blocks of the fishing origin. Keep identity
            // once acquired so another player's matching fish cannot silently replace it.
            List<ArmorStand> matches=client.level.getEntitiesOfClass(ArmorStand.class,
                    client.player.getBoundingBox().inflate(10),e->e.isAlive()&&origin!=null&&e.position().distanceTo(origin)<=10&&matches(e));
            if(matches.size()==1)target=matches.getFirst();
        }
        boolean present=target!=null&&target.isAlive()&&!target.isRemoved();
        boolean visible=present&&client.player.hasLineOfSight(target);
        boolean aligned=false;
        if(visible){
            Vec3 point=target.position().add(0,target.getBbHeight()*.9,0);
            Vec3 d=point.subtract(client.player.getEyePosition());
            push(yaws,(float)(Math.toDegrees(Math.atan2(d.z,d.x))-90),5);
            push(pitches,(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))),20);
            double sin=0,cos=0;for(float value:yaws){sin+=Math.sin(Math.toRadians(value));cos+=Math.cos(Math.toRadians(value));}
            float yaw=(float)Math.toDegrees(Math.atan2(sin,cos));float pitch=0;for(float value:pitches)pitch+=value;pitch/=pitches.size();
            rotation.track(yaw,pitch);rotationOwned=true;
            aligned=Math.hypot(net.minecraft.util.Mth.wrapDegrees(yaw-client.player.getYRot()),pitch-client.player.getXRot())<=5;
        }
        var hook=client.player.fishing;
        var action=controller.tick(new GoldenFishController.Input(present,visible,hook!=null,
                hook!=null&&hook.getHookedIn()!=null,hook!=null&&present&&hook.position().distanceTo(target.getEyePosition())<.15,
                hook!=null&&(hook.onGround()||hook.isInWater()||hook.isInLava()),aligned,System.currentTimeMillis()));
        if(action!=FishingAction.NONE)useRod.run();
        if(!controller.active())releaseRotation();
        return true;
    }
    private void releaseRotation(){if(rotationOwned)rotation.cancelRotation();rotationOwned=false;}
    private static void push(ArrayDeque<Float> values,float value,int max){values.addLast(value);if(values.size()>max)values.removeFirst();}
    public static boolean matches(ArmorStand entity){
        var profile=entity.getItemBySlot(EquipmentSlot.HEAD).get(DataComponents.PROFILE);if(profile==null)return false;
        for(var property:profile.partialProfile().properties().get("textures")) {
            if(property.value().length()>8192)continue;
            try {
                var json=JsonParser.parseString(new String(Base64.getDecoder().decode(property.value()),StandardCharsets.UTF_8)).getAsJsonObject();
                String url=json.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
                if(url.equals("http://textures.minecraft.net/texture/"+TEXTURE)||url.equals("https://textures.minecraft.net/texture/"+TEXTURE))return true;
            }catch(RuntimeException ignored){ /* incomplete or invalid local profile */ }
        }
        return false;
    }
    boolean rebindIdle(){if(active()||rotationOwned||!failure().isEmpty())return false;target=null;origin=null;yaws.clear();pitches.clear();return true;}
}
