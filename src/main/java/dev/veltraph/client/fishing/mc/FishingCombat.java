package dev.veltraph.client.fishing.mc;
import dev.veltraph.client.combat.grind.MeleeGrindModule;
import dev.veltraph.client.config.*;
import dev.veltraph.client.navigation.WalkController;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.service.FarmServices;
import net.minecraft.client.Minecraft;

/** AUTO_KILLER -> TRAVERSE_BACK -> FISHING, sourced from MACRO_DECODE_fishing.md.
 * Explicit creature names, vanilla melee or the separately bound source offensive helper, and local ground return.
 */
public final class FishingCombat {
    private final Minecraft client;private final ConfigService config;private final FarmServices services;
    private final MeleeGrindModule killer;private final McFishingTravel navigator;
    private final McFishingOffense offense;
    private dev.veltraph.client.world.Vec3 anchor;private int rodSlot;private dev.veltraph.client.world.Vec3 normalReturnGoal;private float yaw,pitch;
    private boolean returning,active;private long began,nextCheck;
    private dev.veltraph.client.core.Module owner;
    private final dev.veltraph.client.fishing.offense.FishingOwnedHook meleeHook=new dev.veltraph.client.fishing.offense.FishingOwnedHook();
    private boolean preparing;private long completedAttacks;private int combatSlot=-1;private Object player,world,connection;private String settings;
    private java.util.List<net.minecraft.world.item.ItemStack> loadout=java.util.List.of(),gear=java.util.List.of();
    private static final java.util.List<net.minecraft.world.entity.EquipmentSlot> GEAR=java.util.List.of(net.minecraft.world.entity.EquipmentSlot.HEAD,net.minecraft.world.entity.EquipmentSlot.CHEST,net.minecraft.world.entity.EquipmentSlot.LEGS,net.minecraft.world.entity.EquipmentSlot.FEET,net.minecraft.world.entity.EquipmentSlot.OFFHAND);
    private String settings(){return new com.google.gson.Gson().toJson(java.util.List.of(config.get(FishingConfig.FISHING_AUTOKILLER_WEAPON_TYPE),config.get(FishingConfig.FISHING_AUTOKILLER_IGNORE_SQUID),config.get(FishingConfig.FISHING_KILL_TARGETS),config.get(CombatConfig.BESTIARY_PREFERRED_WEAPON),config.get(dev.veltraph.client.fishing.offense.FishingOffenseConfig.FISHING_USE_HYPERION)));}
    private boolean authority(){return owner!=null&&owner.isEnabled()&&dev.veltraph.client.service.mc.McOwnedRotation.authority(client)==owner;}
    private boolean nativeContext(){return client.isSameThread()&&client.player!=null&&client.level!=null&&client.getConnection()!=null&&client.gameMode!=null&&client.player.isAlive()&&client.player.level()==client.level&&!client.level.tickRateManager().isEntityFrozen(client.player)&&!client.isPaused()&&!client.player.isScoping()&&client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu&&client.player.inventoryMenu.getCarried().isEmpty()&&client.getCameraEntity()==client.player;}
    private dev.veltraph.client.fishing.offense.FishingOwnedHook.Scope scope(){return new dev.veltraph.client.fishing.offense.FishingOwnedHook.Scope(client.player,client.level,client.getConnection());}
    private dev.veltraph.client.fishing.offense.FishingOwnedHook.Hook hook(){var h=client.player.fishing;return h==null?null:new dev.veltraph.client.fishing.offense.FishingOwnedHook.Hook(h,h.getPlayerOwner(),h.level(),h.isAlive()&&client.level.getEntity(h.getId())==h?h.getId():-1,h.getUUID());}
    private static net.minecraft.world.item.ItemStack stableRod(net.minecraft.world.item.ItemStack value){var copy=value.copy();copy.remove(net.minecraft.core.component.DataComponents.DAMAGE);return copy;}
    private void capture(){player=client.player;world=client.level;connection=client.getConnection();settings=settings();var items=new java.util.ArrayList<net.minecraft.world.item.ItemStack>();for(int i=0;i<36;i++)items.add(i==rodSlot?stableRod(client.player.getInventory().getItem(i)):client.player.getInventory().getItem(i).copy());loadout=java.util.List.copyOf(items);gear=GEAR.stream().map(slot->client.player.getItemBySlot(slot).copy()).toList();}
    private boolean reservation(boolean beforeCommit){
        if(!nativeContext()||!authority()||client.player!=player||client.level!=world||client.getConnection()!=connection||!java.util.Objects.equals(settings,settings()))return false;
        if(beforeCommit&&services.inventory().selectedSlot()!=rodSlot)return false;
        if(returning&&services.inventory().selectedSlot()!=rodSlot&&!navigator.rodSelectionCurrent(rodSlot))return false;
        for(int i=0;i<loadout.size();i++){
            var observed=client.player.getInventory().getItem(i);var original=loadout.get(i);
            if(i==rodSlot)observed=stableRod(observed);
            if(i==combatSlot&&active&&!preparing){
                if(!dev.veltraph.client.combat.grind.BestiaryToolWear.allowed(original.getCount(),observed.getCount(),original.getDamageValue(),observed.getDamageValue(),(killer.isEnabled()?killer.issuedWeaponAttacks():completedAttacks),net.minecraft.world.item.ItemStack.matches(stableRod(original),stableRod(observed))))return false;
            }else if(!net.minecraft.world.item.ItemStack.matches(original,observed))return false;
        }
        for(int i=0;i<GEAR.size();i++)if(!net.minecraft.world.item.ItemStack.matches(gear.get(i),client.player.getItemBySlot(GEAR.get(i))))return false;
        return meleeHook.observe(scope(),hook());
    }

    private boolean childWeaponReservation(MeleeGrindModule child){
        if(!(owner instanceof dev.veltraph.client.core.AbstractModule actual))return false;
        var phase=child.fishingWeaponReservationPhase(rodSlot,actual);
        return phase!=dev.veltraph.client.combat.grind.FishingWeaponSelectionState.Phase.REFUSED
                &&reservation(phase==dev.veltraph.client.combat.grind.FishingWeaponSelectionState.Phase.ORIGINAL_ROD);
    }
    public FishingCombat(Minecraft client,ConfigService config,FarmServices services) {
        this.client=client;this.config=config;this.services=services;
        offense=new McFishingOffense(client,config,services);
        killer=new MeleeGrindModule("fishing_auto_killer","Fishing Auto-Killer",null,"",client,services,config,null,
                target->client.level!=null&&FishingAutoKillerTargets.accepts(client.level.getEntity(target.id()),config.get(FishingConfig.FISHING_AUTOKILLER_IGNORE_SQUID)),
                MeleeGrindModule.ParentWeaponDeclaration.FISHING_AUTO_KILLER,
                ()->FishingAutoKillerWeaponPolicy.policy(config.get(FishingConfig.FISHING_AUTOKILLER_WEAPON_TYPE)),
                ()->FishingAutoKillerWeaponPolicy.fixedSlot(config.get(FishingConfig.FISHING_AUTOKILLER_WEAPON_TYPE)),this::settings,
                child->(preparing||active)&&authority()&&childWeaponReservation(child)&&meleeHook.maySelectTool(scope(),hook()),
                ()->owner instanceof dev.veltraph.client.core.AbstractModule actual?actual:null);
        navigator=new McFishingTravel(client,config,services,this);
    }
    dev.veltraph.client.service.FarmServices movementServices(){return services;}
    public boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){return offense.active()?offense.consumeExpectedTeleport(position,now):active&&returning&&navigator.consumeExpectedTeleport(position,now);}
    public void bindOwner(dev.veltraph.client.core.Module owner){if(active||preparing)throw new IllegalStateException("Cannot rebind live Fishing combat owner");this.owner=java.util.Objects.requireNonNull(owner);offense.bindOwner(owner);}
    public void startSession(){if(active||preparing)throw new IllegalStateException("Cannot start a new live Fishing combat session");meleeHook.clear();combatSlot=-1;completedAttacks=0;offense.startSession();}
    public void mana(String line){offense.mana(line);}
    public void message(String line){offense.message(line);}
    public String offenseStatus(){return offense.status();}
    public java.util.Map<String,Object> offensiveFootingDetails(){return offense.footingDetails();}
    public String offensiveReturnMedium(){return offense.returnMediumName();}
    public boolean offensiveReturnFootingReady(){return offense.footingReadyForReturn();}
    public boolean offensiveGroundReturnStarted(){return offense.groundReturnStarted();}
    public String offensiveReturnStatus(){return offense.returnStatus();}
    public int offensiveAcknowledgedUses(){return offense.acknowledgedUses();}
    public boolean offensiveReelIssued(){return offense.hookReelIssued();}
    public boolean offensiveHookRemovalObserved(){return offense.hookRemovalObserved();}
    public boolean tick() {return tick(true);}
    public boolean tick(boolean allowStart) {
        if((active||preparing)&&!java.util.Objects.equals(settings,settings())){stop();throw new IllegalStateException("Fishing combat source options changed during an owned melee lease");}
        if(config.get(dev.veltraph.client.fishing.offense.FishingOffenseConfig.FISHING_USE_HYPERION)||offense.active())return offense.tick(allowStart);
        if(!nativeContext()||!authority()) { stop();return false; }
        if(!config.get(FishingConfig.FISHING_AUTO_KILLER)) { stop();return false; }
        long now=System.currentTimeMillis();
        if(preparing){
            if(!reservation(true)||now-began>=4000){stop();throw new IllegalStateException("Fishing melee acquisition lost its original rod/hook reservation");}
            if(!meleeHook.maySelectTool(scope(),hook()))return true;
            try{killer.setEnabled(true);}catch(RuntimeException|Error failure){stop();throw failure;}active=killer.isEnabled();if(active)combatSlot=killer.committedWeaponSlot();
            if(!active){stop();throw new IllegalStateException("Fishing melee weapon proposal changed before owned commit");}
            preparing=false;
            return true;
        }
        if(!active) {
            if(!allowStart)return false;
            if(now<nextCheck)return false;nextCheck=now+1000;
            if(config.get(FishingConfig.FISHING_KILL_TARGETS).isBlank()||!killer.hasMatchingTargets())return false;
            if(!client.player.getMainHandItem().is(net.minecraft.world.item.Items.FISHING_ROD))return false;
            rodSlot=services.inventory().selectedSlot();
            yaw=client.player.getYRot();pitch=client.player.getXRot();anchor=new dev.veltraph.client.world.Vec3(client.player.getX(),client.player.getY(),client.player.getZ());
            // Readiness creates only a protected proposal. The original rod remains selected.
            if(killer.preStartCheck().isPresent())return false;
            capture();if(!meleeHook.begin(scope(),hook()))return false;
            completedAttacks=0;preparing=true;began=now;
            if(hook()!=null){if(!meleeHook.issueReel(scope(),hook())){stop();return false;}client.gameMode.useItem(client.player,net.minecraft.world.InteractionHand.MAIN_HAND);}
            return true;
        }
        if(!nativeContext()||client.player!=player||client.level!=world||client.getConnection()!=connection||!reservation(false)){stop();throw new IllegalStateException("Fishing melee parent context, options or protected loadout changed");}
        if(!returning) {
            if(killer.isEnabled()&&killer.hasMatchingTargets()&&now-began<30_000) { killer.onTick();return true; }
            if(services.inventory().selectedSlot()!=combatSlot){stop();throw new IllegalStateException("Fishing melee tool slot changed before return");}
            completedAttacks=killer.issuedWeaponAttacks();killer.setEnabled(false);
            if(!reservation(true)){stop();throw new IllegalStateException("Fishing melee original rod/loadout changed before return");}
            returning=true;normalReturnGoal=anchor;
            if(!navigator.start(normalReturnGoal,dev.veltraph.client.fishing.FishingTravelIntent.COMBAT_RETURN)) {
                stop();throw new IllegalStateException("Cannot return to fishing spot; fishing stopped");
            }
        }
        var state=navigator.tick();
        if(state==WalkController.State.BLOCKED) { stop();throw new IllegalStateException("Fishing return path blocked"); }
        if(state==WalkController.State.ARRIVED) {
            float dyaw=WalkController.wrap(yaw-client.player.getYRot()),dpitch=pitch-client.player.getXRot();
            services.rotation().track(yaw,pitch);
            if(client.player.position().distanceTo(new net.minecraft.world.phys.Vec3(anchor.x(),anchor.y(),anchor.z()))<.2&&Math.abs(dyaw)<1&&Math.abs(dpitch)<1) { stop();nextCheck=now+3000; }
        }
        return true;
    }
    public void stop() {
        offense.stop();
        if(!active&&!preparing)return;
        var history=owner instanceof dev.veltraph.client.core.AbstractModule actual?killer.fishingWeaponReturnHistory(rodSlot,actual):null;
        boolean restore=nativeContext()&&client.player==player&&client.level==world&&client.getConnection()==connection&&authority()&&(services.inventory().selectedSlot()==rodSlot||services.inventory().selectedSlot()==combatSlot)
                &&(services.inventory().selectedSlot()==rodSlot||history!=null&&history.current());
        killer.setEnabled(false);navigator.stop();if(authority())services.rotation().cancelRotation();
        if(restore&&history!=null&&history.current()&&nativeContext()&&client.player==player&&client.level==world&&client.getConnection()==connection&&authority()&&rodSlot>=0&&rodSlot<9&&loadout.size()>rodSlot&&net.minecraft.world.item.ItemStack.matches(loadout.get(rodSlot),stableRod(client.player.getInventory().getItem(rodSlot)))&&services.inventory().selectedSlot()!=rodSlot)
            services.inventory().selectSlot(rodSlot);
        meleeHook.cancel();active=false;preparing=false;returning=false;combatSlot=-1;

    }
    boolean idleForPause(){return !active&&!preparing&&!returning&&!killer.isEnabled()&&offense.idleForPause();}
    boolean rebindIdle(){if(!idleForPause())return false;anchor=null;return offense.rebindIdle();}
    McFishingTravel.NativeCleanupSource captureNativeCleanupSource(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg exact){var own=navigator.captureNativeCleanupSource(exact);return own==null?offense.captureNativeCleanupSource(exact):own;}
    McFishingTravel.NativeReturnSource captureNativeReturnSource(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg exact){var own=navigator.captureNativeReturnSource(exact);return own==null?offense.captureNativeReturnSource(exact):own;}
    Object nativeReturnProfileIdentity(){return settings;}
    Object nativeReturnRuntimeIdentity(){return killer;}
    int nativeReturnRodSlot(){return rodSlot;}
    Object nativeReturnCustodyIdentity(){return loadout;}
    Object nativeReturnAnchorIdentity(){return anchor;}
    boolean nativeReturnSourceCurrent(McFishingTravel exact,dev.veltraph.client.fishing.FishingTravelIntent intent,dev.veltraph.client.world.Vec3 goal,boolean starting){
        if(client.player==null||client.level==null||loadout.size()!=36||gear.size()!=GEAR.size())return false;
        for(int i=0;i<36;i++){
            var observed=client.player.getInventory().getItem(i);var original=loadout.get(i);if(i==rodSlot)observed=stableRod(observed);
            if(i==combatSlot){if(!dev.veltraph.client.combat.grind.BestiaryToolWear.allowed(original.getCount(),observed.getCount(),original.getDamageValue(),observed.getDamageValue(),completedAttacks,net.minecraft.world.item.ItemStack.matches(stableRod(original),stableRod(observed))))return false;}
            else if(!net.minecraft.world.item.ItemStack.matches(original,observed))return false;
        }
        for(int i=0;i<GEAR.size();i++)if(!net.minecraft.world.item.ItemStack.matches(gear.get(i),client.player.getItemBySlot(GEAR.get(i))))return false;
        return nativeReturnSourceRawCurrent(exact,intent,goal,starting);
    }
    /** Stored private phase/source fields only; no stack comparison or codec visitor. */
    boolean nativeReturnSourceRawCurrent(McFishingTravel exact,dev.veltraph.client.fishing.FishingTravelIntent intent,dev.veltraph.client.world.Vec3 goal,boolean starting){
        return exact==navigator&&intent==dev.veltraph.client.fishing.FishingTravelIntent.COMBAT_RETURN&&goal==normalReturnGoal&&goal==anchor
            &&active&&returning&&!preparing&&!killer.isEnabled()&&!offense.active()&&owner!=null&&player==client.player&&world==client.level
            &&connection==client.getConnection()&&settings!=null&&rodSlot>=0&&rodSlot<9&&loadout.size()==36&&gear.size()==GEAR.size()
            &&loadout.get(rodSlot).is(net.minecraft.world.item.Items.FISHING_ROD);
    }
}
