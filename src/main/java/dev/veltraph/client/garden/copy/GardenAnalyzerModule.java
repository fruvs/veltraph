package dev.veltraph.client.garden.copy;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.game.*;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import java.io.IOException;
import java.util.*;

/** Explicit loaded-region counterpart to source plot checks; no desk/fly/menu/input/world mutation. */
public final class GardenAnalyzerModule extends AbstractModule {
    public static final String ID="garden_analyzer";private final Minecraft client;private final ConfigService config;private final GardenBlueprintStore store;private String name;private GardenRegion region;private GardenScan scan;private GardenBlueprint blueprint;
    public GardenAnalyzerModule(Minecraft c,ConfigService cfg,GardenBlueprintStore store){super(ID,"Garden analyzer",ModuleCategory.INTERMEDIARY);client=c;config=cfg;this.store=store;}
    @Override public boolean isPassiveObserver(){return true;}@Override public boolean ownsGameplayInput(){return false;}@Override public boolean requiresSkyBlock(){return true;}@Override public Optional<GameArea> requiredArea(){return Optional.of(GameArea.GARDEN);}
    public void prepare(String name,GardenRegion region){if(isEnabled())throw new IllegalStateException("Stop active scan before replacing bounds");new GardenScan(name,region,config.get(GardenCopyConfig.GARDEN_SCAN_MAX_CELLS),reader());this.name=name;this.region=region;blueprint=null;}
    private GardenScan.Reader reader(){return new GardenScan.Reader(){public Object identity(){return client.level;}public GardenScan.Cell read(dev.veltraph.client.world.BlockPos p){if(client.level==null||!client.level.getChunkSource().hasChunk(p.x()>>4,p.z()>>4))return new GardenScan.Cell(false,null,false);var state=client.level.getBlockState(new net.minecraft.core.BlockPos(p.x(),p.y(),p.z()));return new GardenScan.Cell(true,BlockStateParser.serialize(state),state.hasBlockEntity());}};}
    @Override public Optional<String> preStartCheck(){return region==null||client.level==null?Optional.of("Prepare explicit loaded Garden bounds with /vgarden scan"):Optional.empty();}
    @Override public void onEnable(){blueprint=null;scan=new GardenScan(name,region,config.get(GardenCopyConfig.GARDEN_SCAN_MAX_CELLS),reader());}
    @Override public void onTick(){if(!isEnabled())return;scan.tick(reader(),config.get(GardenCopyConfig.GARDEN_SCAN_BATCH));if(scan.phase()==GardenScan.Phase.COMPLETE){blueprint=scan.result().orElseThrow();setEnabled(false);}else if(scan.phase()==GardenScan.Phase.FAILED)setEnabled(false);}
    @Override public void onDisable(){if(scan!=null)scan.cancel();}
    public Optional<GardenBlueprint> result(){return Optional.ofNullable(blueprint);}
    public void save()throws IOException{store.save(result().orElseThrow(()->new IllegalStateException("No completed coherent scan")));}
    public GardenBlueprint load(String name)throws IOException{return store.load(name);}
    @Override public String diagnostics(){return scan==null?"No explicit scan":scan.status()+(blueprint==null?"":" | cells="+blueprint.states().size()+" palette="+blueprint.histogram().size()+" | blockEntities="+blueprint.blockEntities().size()+" (payload excluded); complete state histogram in explicit saved blueprint");}
}
