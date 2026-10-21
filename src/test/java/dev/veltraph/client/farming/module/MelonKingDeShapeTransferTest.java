package dev.veltraph.client.farming.module;

import dev.veltraph.client.config.*;
import dev.veltraph.client.farming.domain.FarmLayoutRegistry;
import dev.veltraph.client.farming.movement.planner.MelonKingDeWaterPlanner;
import dev.veltraph.client.service.*;
import dev.veltraph.client.service.stub.*;
import dev.veltraph.client.testutil.*;
import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Source-backed narrow join: an open north panel remains behind the forward body probe. */
class MelonKingDeShapeTransferTest {
    private final Map<BlockPos,String> cells=new HashMap<>();
    private final Set<BlockPos> unloaded=new HashSet<>();
    private final FakePlayerService actor=new FakePlayerService();
    private final FakeRotationService rotation=new FakeRotationService(0,-59);
    private final RecordingInputService input=new RecordingInputService();
    private final MapConfigService values=new MapConfigService();
    private final ConfigService config=new CallbackConfig();
    private Runnable afterLoaded,afterBody,afterSupport,afterConfig,afterPosition;
    private Vec3 lastProbe;private int bodyCalls;private boolean support=true;
    private final PumpkinMelonFarmModule module;
    private final class CallbackConfig implements ConfigService {
        public <T>T get(ConfigKey<T> key){T result=values.get(key);if(key==FarmingConfig.PUMPKIN_FARM_DESIGN&&afterConfig!=null){var call=afterConfig;afterConfig=null;call.run();}return result;}
        public <T>void set(ConfigKey<T> key,T value){values.set(key,value);}
        public boolean isSet(ConfigKey<?> key){return values.isSet(key);}public void reset(ConfigKey<?> key){values.reset(key);}
    }
    private final class CallbackPlayer implements PlayerService {
        public boolean isAvailable(){return actor.isAvailable();}
        public Vec3 position(){Vec3 result=actor.position();if(afterPosition!=null){var call=afterPosition;afterPosition=null;call.run();}return result;}
        public BlockPos blockPos(){return actor.blockPos();}public float yaw(){return actor.yaw();}public float pitch(){return actor.pitch();}
        public boolean onGround(){return actor.onGround();}public double horizontalSpeed(){return actor.horizontalSpeed();}
        public Direction facing(){return actor.facing();}
    }
    private final class ShapeWorld implements WorldService {
        public boolean isLoaded(BlockPos p){boolean result=!unloaded.contains(p);if(afterLoaded!=null){var call=afterLoaded;afterLoaded=null;call.run();}return result;}
        public String blockId(BlockPos p){return cells.getOrDefault(p,"minecraft:air");}
        public boolean isPassable(BlockPos p){return Set.of("minecraft:air","minecraft:water","minecraft:oak_wall_sign").contains(blockId(p));}
        public boolean isStandable(BlockPos p){return !isPassable(p);}
        public boolean bodyClear(Vec3 feet,double width,double height){
            bodyCalls++;lastProbe=feet;boolean clear=true;
            for(var entry:cells.entrySet()) {
                var p=entry.getKey();String id=entry.getValue();
                if(Set.of("minecraft:air","minecraft:water","minecraft:oak_wall_sign").contains(id))continue;
                double top=id.equals("minecraft:snow")||id.equals("minecraft:soul_sand")?p.y()+.875:p.y()+1;
                // The retained source south-facing open trapdoor hinges at its north face.
                double maxZ=id.equals("minecraft:oak_trapdoor")?p.z()+.1875:p.z()+1;
                if(feet.x()+width>p.x()+1e-7&&feet.x()-width<p.x()+1-1e-7
                        &&feet.z()+width>p.z()+1e-7&&feet.z()-width<maxZ-1e-7
                        &&feet.y()+height>p.y()&&feet.y()+.001<top)clear=false;
            }
            if(afterBody!=null){var call=afterBody;afterBody=null;call.run();}return clear;
        }
        public boolean hasGroundSupport(Vec3 feet,double width){
            boolean contact=false;
            for(int x=(int)Math.floor(feet.x()-width);x<=(int)Math.floor(feet.x()+width-1e-7);x++)
                for(int z=(int)Math.floor(feet.z()-width);z<=(int)Math.floor(feet.z()+width-1e-7);z++) {
                    var p=new BlockPos(x,69,z);String id=blockId(p);
                    contact|=!unloaded.contains(p)&&(id.equals("minecraft:snow")||id.equals("minecraft:soul_sand"))&&Math.abs(feet.y()-69.875)<.025;
                }
            if(afterSupport!=null){var call=afterSupport;afterSupport=null;call.run();}return support&&contact;
        }
    }
    MelonKingDeShapeTransferTest(){
        actor.position=new Vec3(94.69999998807907,69.875,7.699999988079071);
        for(int z=7;z<=10;z++)cells.put(new BlockPos(94,69,z),"minecraft:snow");
        cells.put(new BlockPos(94,70,7),"minecraft:oak_wall_sign");
        cells.put(new BlockPos(94,71,7),"minecraft:oak_trapdoor");
        config.set(FarmingConfig.REWARP_REQUIRED,false);config.set(FarmingConfig.ROTATION_USE_MOUSE_GCD,false);
        config.set(FarmingConfig.PUMPKIN_FARM_DESIGN,PumpkinFarmDesign.MELONKINGDE_WATER);
        module=new PumpkinMelonFarmModule(new FarmServices(new FakeClock(),new CallbackPlayer(),new ShapeWorld(),input,rotation,
                new NoOpBlockBreakingService(),new NoOpInventoryService()),config,FarmLayoutRegistry.createDefault());
        module.setEnabled(true);
    }
    private MelonKingDeWaterPlanner.World course() throws Exception {
        var f=PumpkinMelonFarmModule.class.getDeclaredField("waterPlanner");f.setAccessible(true);var planner=f.get(module);
        var world=MelonKingDeWaterPlanner.class.getDeclaredField("world");world.setAccessible(true);
        return (MelonKingDeWaterPlanner.World)world.get(planner);
    }
    @Test void retainedNonemptyPanelBehindBodyDoesNotBlockTheActualSupportedTransfer() throws Exception {
        assertTrue(course().transferClear());assertEquals(actor.position.z()+.35,lastProbe.z(),1e-9);
        assertEquals(actor.position.y(),lastProbe.y());assertEquals(0,rotation.yaw);assertEquals(-59,rotation.pitch);
        assertFalse(input.forward||input.back||input.left||input.right||input.jump||input.sneak||input.sprint,"Read-only geometry cannot emit keys");
    }
    @Test void aPanelInsideTheActualForwardBodyAndAFullCubeBothRefuse() throws Exception {
        cells.put(new BlockPos(94,71,8),"minecraft:oak_trapdoor");assertFalse(course().transferClear());
        cells.remove(new BlockPos(94,71,8));cells.put(new BlockPos(94,70,8),"minecraft:dirt");assertFalse(course().transferClear());
    }
    @Test void unloadedFullBoundsAndMissingPhysicalSupportRefuse() throws Exception {
        unloaded.add(new BlockPos(94,71,7));assertFalse(course().transferClear());assertEquals(0,bodyCalls);
        unloaded.clear();support=false;assertFalse(course().transferClear());
        support=true;cells.remove(new BlockPos(94,69,7));cells.remove(new BlockPos(94,69,8));assertFalse(course().transferClear());
    }
    @Test void sourceMovementDuringEachProviderPhaseRefuses() throws Exception {
        for(int phase=0;phase<3;phase++) {
            actor.position=new Vec3(94.69999998807907,69.875,7.699999988079071);
            Runnable change=()->actor.position=new Vec3(actor.position.x(),actor.position.y(),actor.position.z()+.01);
            if(phase==0)afterLoaded=change;else if(phase==1)afterBody=change;else afterSupport=change;
            assertFalse(course().transferClear(),"A result assembled across different actor positions is not current");
        }
    }
    @Test void changedDesignOrDisabledModuleDuringBodyReadRefuses() throws Exception {
        afterBody=()->config.set(FarmingConfig.PUMPKIN_FARM_DESIGN,PumpkinFarmDesign.CLASSICAL);assertFalse(course().transferClear());
        config.set(FarmingConfig.PUMPKIN_FARM_DESIGN,PumpkinFarmDesign.MELONKINGDE_WATER);
        afterBody=()->module.setEnabled(false);assertFalse(course().transferClear());
        module.setEnabled(true);
        Runnable replacementActivation=()->{module.setEnabled(false);module.setEnabled(true);};
        afterBody=replacementActivation;assertFalse(course().transferClear(),"A body callback cannot adopt another activation at the same pose");
        assertTrue(module.isEnabled());assertTrue(course().transferClear(),"A later fresh query may sample the legitimate new activation");
        afterConfig=replacementActivation;assertFalse(course().transferClear(),"Config callback after an earlier aim check must not rearm the probe");
        assertTrue(course().transferClear());
        afterPosition=replacementActivation;assertFalse(course().transferClear(),"Per-enable witness must precede the first position provider");
        assertTrue(course().transferClear());
    }
    @Test void heldCardinalDeterminesTheActualProbeWithoutChangingTheView() throws Exception {
        cells.clear();for(int x=92;x<=97;x++)for(int z=5;z<=10;z++)cells.put(new BlockPos(x,69,z),"minecraft:snow");
        for(float yaw:new float[]{0,90,180,-90}) {
            module.setEnabled(false);rotation.yaw=yaw;rotation.pitch=-59;module.setEnabled(true);
            Direction h=module.heading();assertTrue(course().transferClear());
            assertEquals(actor.position.x()+h.dx()*.35,lastProbe.x(),1e-9);assertEquals(actor.position.z()+h.dz()*.35,lastProbe.z(),1e-9);
            assertEquals(yaw,rotation.yaw);assertEquals(-59,rotation.pitch);
        }
    }
    @Test void saigoKeepsItsExistingWholeVoxelTransferInterpretation() throws Exception {
        module.setEnabled(false);config.set(FarmingConfig.PUMPKIN_FARM_DESIGN,PumpkinFarmDesign.SAIGO_CURRENT);module.setEnabled(true);
        assertFalse(course().transferClear());assertEquals(0,bodyCalls,"MKD shape adaptation must not reinterpret SAIGO corners");
    }
    @Test void invalidOrUnboundedPositionNeverRunsTheBodyProvider() throws Exception {
        for(Vec3 invalid:new Vec3[]{null,new Vec3(Double.NaN,69.875,7.7),new Vec3(94.7,Double.POSITIVE_INFINITY,7.7),
                new Vec3(94.7,69.875,Double.NEGATIVE_INFINITY),new Vec3(30_000_001,69.875,7.7)}) {
            actor.position=invalid;assertFalse(course().transferClear());assertEquals(0,bodyCalls);
        }
    }
}
