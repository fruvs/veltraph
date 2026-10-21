package dev.veltraph.client.fishing.mc;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.fishing.trash.*;
import dev.veltraph.client.fishing.trash.mc.*;
import dev.veltraph.client.inventory.NativeStackIdentity;
import dev.veltraph.client.navigation.*;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.service.RotationService;
import dev.veltraph.client.service.mc.McInputService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Source automatic cliff interruption under the Fishing owner, with physical native trip/return and strict drop receipts. */
final class McFishingTrash implements FishingTrashWorkflow.Port {
    private final Minecraft client;private final ConfigService config;private final RotationService rotation;private final Runnable useRod;
    private final McFishingTravel travel;private final McNavigator navigator;private final McFishingTrashCliffFacts facts;private final McFishingTrashDiscard discard;
    private final FishingTrashWorkflow workflow=new FishingTrashWorkflow();private final Random random=new Random();
    private FishingTrashProfile profile;private String contract;private Object player,world,connection;
    private Object sessionPlayer,sessionWorld;private final Vec3 sessionAnchor;private final float sessionYaw,sessionPitch;
    private Vec3 anchor,cliff;private float yaw,pitch,cliffYaw,cliffPitch;private int selected;
    private NativeStackIdentity.Snapshot rod;private List<NativeStackIdentity.Snapshot> original=List.of(),gear=List.of();
    private FishingTrashCliffSelector.Scan scan;private FishingTrashCliffSelector.Candidate candidate;
    private InventoryScreen ownedScreen;private boolean navigating,rotating;private long lastCheck;private dev.veltraph.client.world.Vec3 normalReturnGoal;
    McFishingTrash(Minecraft client,ConfigService config,RotationService rotation,Runnable useRod,dev.veltraph.client.service.FarmServices services) {
        this.client=client;this.config=config;this.rotation=rotation;this.useRod=useRod;
        sessionPlayer=client.player;sessionWorld=client.level;sessionAnchor=client.player==null?null:client.player.position();sessionYaw=client.player==null?0:client.player.getYRot();sessionPitch=client.player==null?0:client.player.getXRot();
        travel=new McFishingTravel(client,config,services,this);navigator=new McNavigator(client,new McInputService(client),config);facts=new McFishingTrashCliffFacts(client);discard=new McFishingTrashDiscard(client,config);
    }
    static String problem(ConfigService config) {
        if(!config.get(FishingTrashConfig.FISHING_THROW_TRASH))return "";
        if(!config.get(FishingTrashConfig.FISHING_USE_AUTOMATIC_TRASH_LOCATIONS))return "Enable recovered automatic trash locations; native recorded cliff presets are not decoded";
        return McFishingTrashDiscard.configurationError(config);
    }
    private static ItemStack withoutDamage(ItemStack value){var copy=value.copy();if(copy.is(Items.FISHING_ROD))copy.remove(DataComponents.DAMAGE);return copy;}
    private NativeStackIdentity.Snapshot identity(ItemStack item){return NativeStackIdentity.capture(withoutDamage(item),client.level.registryAccess());}
    private List<NativeStackIdentity.Snapshot> main(){var rows=new ArrayList<NativeStackIdentity.Snapshot>();for(int i=0;i<36;i++)rows.add(identity(client.player.getInventory().getItem(i)));return List.copyOf(rows);}
    private List<NativeStackIdentity.Snapshot> protectedGear(){return List.of(net.minecraft.world.entity.EquipmentSlot.HEAD,net.minecraft.world.entity.EquipmentSlot.CHEST,net.minecraft.world.entity.EquipmentSlot.LEGS,net.minecraft.world.entity.EquipmentSlot.FEET,net.minecraft.world.entity.EquipmentSlot.OFFHAND).stream().map(slot->identity(client.player.getItemBySlot(slot))).toList();}
    boolean tick(boolean allow) {
        if(!failure().isEmpty())return true;
        if(!active()) {
            if(!allow||!config.get(FishingTrashConfig.FISHING_THROW_TRASH)||!config.get(FishingTrashConfig.FISHING_USE_AUTOMATIC_TRASH_LOCATIONS)
                    ||client.player==null||client.level==null||client.getConnection()==null||client.gui.screen()!=null
                    ||client.player.containerMenu!=client.player.inventoryMenu||!client.player.containerMenu.getCarried().isEmpty()
                    ||client.player!=sessionPlayer||client.level!=sessionWorld||sessionAnchor==null
                    ||client.player.isUnderWater()||!client.player.onGround()||!client.player.getMainHandItem().is(Items.FISHING_ROD))return false;
            try {
                profile=FishingTrashProfile.parse(config.get(FishingTrashConfig.FISHING_TRASH_PROFILE));long now=System.currentTimeMillis();
                if(now-lastCheck<profile.checkIntervalMillis())return false;
                lastCheck=now;long count=client.player.getInventory().getNonEquipmentItems().stream().map(value->FishingTrashInventoryReceipts.describe(client,value)).filter(profile::candidate).count();
                if(count<5)return false;
                player=client.player;world=client.level;connection=client.getConnection();contract=config.get(FishingTrashConfig.FISHING_TRASH_PROFILE);
                anchor=sessionAnchor;yaw=sessionYaw;pitch=sessionPitch;selected=client.player.getInventory().getSelectedSlot();
                if(!navigator.standingClear(new dev.veltraph.client.world.Vec3(anchor.x,anchor.y,anchor.z)))return false;
                rod=identity(client.player.getMainHandItem());original=main();gear=protectedGear();ownedScreen=null;candidate=null;scan=null;
                if(!workflow.start(profile,now,this))return false;
            }catch(RuntimeException invalid){workflow.fail("Fishing trash contract/identity refused: "+invalid.getMessage(),this);return true;}
            return true;
        }
        workflow.tick(System.currentTimeMillis(),this);return true;
    }
    boolean active(){return workflow.active();}String failure(){return workflow.failure();}String status(){return workflow.state()+" "+discard.state();}
    boolean screen(String title){return active()&&ownedScreen!=null&&client.gui.screen()==ownedScreen&&ownedScreen.getTitle().getString().equals(title);}
    @Override public boolean valid() {
        if(client.player!=player||client.level!=world||client.getConnection()!=connection||client.player==null||!client.player.isAlive()
                ||!config.get(FishingTrashConfig.FISHING_THROW_TRASH)||!config.get(FishingTrashConfig.FISHING_USE_AUTOMATIC_TRASH_LOCATIONS)
                ||!Objects.equals(contract,config.get(FishingTrashConfig.FISHING_TRASH_PROFILE))
                ||(client.player.getInventory().getSelectedSlot()!=selected&&!(workflow.state()==FishingTrashWorkflow.State.RETURN&&travel.rodSelectionCurrent(selected)))||client.player.containerMenu!=client.player.inventoryMenu
                ||!client.player.containerMenu.getCarried().isEmpty()||client.gui.screen()!=null&&client.gui.screen()!=ownedScreen)return false;
        try {
            if(!rod.equals(identity(client.player.getInventory().getItem(selected)))||!gear.equals(protectedGear()))return false;
            if(workflow.state().ordinal()<FishingTrashWorkflow.State.REMOVE_TRASH.ordinal()&&!original.equals(main()))return false;
            return true;
        }catch(RuntimeException missingIdentity){return false;}
    }
    @Override public boolean bobber(){return client.player.fishing!=null;}
    @Override public void reel(){useRod.run();}
    @Override public int selectCliff() {
        if(scan==null){var p=client.player.blockPosition();scan=new FishingTrashCliffSelector.Scan(new FishingTrashCliffSelector.Pos(p.getX(),p.getY(),p.getZ()),random);}
        scan.advance(facts,2048);if(!scan.complete())return 0;
        for(var row:scan.ranked()) {
            if(!facts.stillEligible(row))continue;var p=row.position();var node=new dev.veltraph.client.world.BlockPos(p.x(),p.y(),p.z());var position=navigator.position(node);
            if(!navigator.standingClear(position)||!travel.start(position,dev.veltraph.client.fishing.FishingTravelIntent.TRASH_CLIFF))continue;
            candidate=row;cliff=new Vec3(position.x(),position.y(),position.z());navigating=true;
            int dx=0,dz=0;for(var d:row.throwDirections()){dx+=switch(d){case EAST->1;case WEST->-1;default->0;};dz+=switch(d){case SOUTH->1;case NORTH->-1;default->0;};}
            cliffYaw=(float)(Math.toDegrees(Math.atan2(-dx,dz))+random.nextDouble(-6,6));cliffPitch=(float)random.nextDouble(25,40);return 1;
        }
        return -1;
    }
    @Override public int walkCliff(){if(!facts.stillEligible(candidate))return -1;var state=travel.tick();if(state==WalkController.State.ARRIVED){travel.stop();navigating=false;return 1;}return state==WalkController.State.BLOCKED?-1:0;}
    private boolean atCliff(){return candidate!=null&&facts.stillEligible(candidate)&&cliff!=null&&client.player.position().distanceTo(cliff)<.5&&client.player.onGround()
            &&navigator.standingClear(new dev.veltraph.client.world.Vec3(client.player.getX(),client.player.getY(),client.player.getZ()));}
    @Override public boolean aimCliff(){if(!atCliff()){workflow.fail("Fishing trash cliff lost physical support",this);return false;}rotation.track(cliffYaw,cliffPitch);rotating=true;return Math.hypot(net.minecraft.util.Mth.wrapDegrees(cliffYaw-client.player.getYRot()),cliffPitch-client.player.getXRot())<5;}
    @Override public boolean openInventory(){if(!atCliff()||client.gui.screen()!=null)return false;ownedScreen=new InventoryScreen(client.player);client.gui.setScreen(ownedScreen);return discard.start(ownedScreen);}
    @Override public void discardTick(){if(!atCliff()||Math.hypot(net.minecraft.util.Mth.wrapDegrees(cliffYaw-client.player.getYRot()),cliffPitch-client.player.getXRot())>=5){workflow.fail("Fishing trash aim or cliff changed before discard",this);return;}discard.tick();}
    @Override public boolean discarded(){return discard.state()==FishingTrashDiscard.State.COMPLETE;}
    @Override public String discardFailure(){return discard.failure();}
    @Override public boolean closeInventory(){if(client.gui.screen()!=ownedScreen||ownedScreen==null||!client.player.containerMenu.getCarried().isEmpty())return false;client.player.closeContainer();client.gui.setScreen(null);ownedScreen=null;return true;}
    @Override public boolean startReturn(){normalReturnGoal=new dev.veltraph.client.world.Vec3(anchor.x,anchor.y,anchor.z);navigating=true;if(!travel.start(normalReturnGoal,dev.veltraph.client.fishing.FishingTravelIntent.TRASH_RETURN)){navigating=false;return false;}return true;}
    @Override public int returnTick(){var state=travel.tick();if(state==WalkController.State.ARRIVED){travel.stop();navigating=false;return 1;}return state==WalkController.State.BLOCKED?-1:0;}
    @Override public boolean restoreAim(){rotation.track(yaw,pitch);rotating=true;return client.player.position().distanceTo(anchor)<.5&&Math.hypot(net.minecraft.util.Mth.wrapDegrees(yaw-client.player.getYRot()),pitch-client.player.getXRot())<1;}
    @Override public void release(){
        if(navigating){travel.stop();navigating=false;}if(rotating){rotation.cancelRotation();rotating=false;}
        if(client.player==player&&client.level==world&&ownedScreen!=null&&client.gui.screen()==ownedScreen&&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty()){client.player.closeContainer();client.gui.setScreen(null);}
        ownedScreen=null;
    }
    void stop(){workflow.stop(this);discard.stop();scan=null;candidate=null;player=world=connection=null;original=gear=List.of();}
    boolean rebindIdle(){if(active()||navigating||rotating||!failure().isEmpty())return false;scan=null;candidate=null;discard.stop();player=world=connection=null;sessionPlayer=client.player;sessionWorld=client.level;original=gear=List.of();return true;}
    boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){return active()&&travel.consumeExpectedTeleport(position,now);}
    McFishingTravel.NativeCleanupSource captureNativeCleanupSource(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg exact){return travel.captureNativeCleanupSource(exact);}
    McFishingTravel.NativeReturnSource captureNativeReturnSource(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg exact){return travel.captureNativeReturnSource(exact);}
    Object nativeReturnProfileIdentity(){return profile;}
    Object nativeReturnRuntimeIdentity(){return workflow;}
    int nativeReturnRodSlot(){return selected;}
    Object nativeReturnCustodyIdentity(){return rod;}
    Object nativeReturnAnchorIdentity(){return anchor;}
    boolean nativeReturnSourceCurrent(McFishingTravel exact,dev.veltraph.client.fishing.FishingTravelIntent intent,dev.veltraph.client.world.Vec3 goal,boolean starting){
        if(client.player==null||client.level==null||selected<0||selected>8||rod==null)return false;
        boolean semantic=rod.equals(identity(client.player.getInventory().getItem(selected)))&&gear.equals(protectedGear());
        return semantic&&nativeReturnSourceRawCurrent(exact,intent,goal,starting);
    }
    /** Stored private phase/source fields only; no stack comparison or codec visitor. */
    boolean nativeReturnSourceRawCurrent(McFishingTravel exact,dev.veltraph.client.fishing.FishingTravelIntent intent,dev.veltraph.client.world.Vec3 goal,boolean starting){
        return exact==travel&&intent==dev.veltraph.client.fishing.FishingTravelIntent.TRASH_RETURN&&goal==normalReturnGoal&&normalReturnGoal!=null
            &&navigating&&(workflow.state()==FishingTrashWorkflow.State.RETURN||starting&&workflow.state()==FishingTrashWorkflow.State.CLOSE_INVENTORY)
            &&ownedScreen==null&&workflow.failure().isEmpty()&&profile!=null&&contract!=null&&discard.state()==FishingTrashDiscard.State.COMPLETE
            &&client.player==player&&client.level==world&&client.getConnection()==connection&&player==sessionPlayer&&world==sessionWorld
            &&anchor==sessionAnchor&&selected>=0&&selected<9&&rod!=null;
    }
}
