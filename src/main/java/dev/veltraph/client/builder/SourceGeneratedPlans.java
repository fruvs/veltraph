package dev.veltraph.client.builder;

import dev.veltraph.client.config.*;
import dev.veltraph.client.world.BlockPos;
import java.util.*;
import static dev.veltraph.client.builder.GeneratedBuildPlan.Operation;

/** Readable source graphs; server tools and native helper semantics remain explicit contracts. */
public final class SourceGeneratedPlans {
    private SourceGeneratedPlans() {}
    public static final String SOURCE95="lIIIllIlllIIlIlIllllIIllllIIlIIIIlIllllIIlIIIIlIlIIlIllIllllIIIl";
    public static final String SOURCE92="IIlIllIlIlIIlllIIlIIlllllIIIlIIllIlllllIlIllIllIllIlllIlIIllllII";
    private static GeneratedBuildPlan.Process p(String source,Operation op,BlockPos a,BlockPos b,String before,String after,String item,String contract){return new GeneratedBuildPlan.Process(source,op,a,b,before,after,item,contract);}
    public static GeneratedBuildPlan source92(ConfigService config) {
        var out=new ArrayList<GeneratedBuildPlan.Process>();
        String building=config.get(BuilderConfig.BUILDER_BUILDING_BLOCK),temporary=config.get(GeneratedBuilderConfig.TEMPORARY_BLOCK);
        var palette=GeneratedResourceBinding.palette(config.get(GeneratedBuilderConfig.SOURCE92_PALETTE));
        for(var row:Source92Geometry.ROWS) {
            String block=switch(row.material()){case "building"->building;case "temporary"->temporary;case "water"->"minecraft:water";default->palette.getOrDefault(row.material(),"");};
            String contract=row.operation()==Operation.CONTRACT?row.contract():"";
            if(block.isBlank()&&row.operation()==Operation.PLACE)contract="Bind "+row.material()+" in BUILDER_GENERATED_SOURCE92_PALETTE (source92 entry "+row.index()+")";
            if(row.material().equals("temporary")&&block.isBlank())contract="Bind BUILDER_GENERATED_TEMPORARY_BLOCK for source92 entry "+row.index();
            BlockPos direction=null;boolean support=row.index()==41||row.index()>=43&&row.index()<=56;
            if(row.index()==15)direction=new BlockPos(-1,0,0);
            if(row.index()==29||row.index()>=57&&row.index()<=63||support)direction=new BlockPos(0,0,-1);
            if(row.index()==25||row.index()>=64&&row.index()<=70)direction=new BlockPos(0,0,1);
            if(row.index()==91)direction=new BlockPos(1,0,0);
            boolean lever=row.operation()==Operation.LEVER;
            out.add(new GeneratedBuildPlan.Process(SOURCE92+"["+row.index()+"]",row.operation(),row.first(),row.last(),lever?"minecraft:lever":row.operation()==Operation.BREAK?block:"minecraft:air",lever?"minecraft:lever":row.operation()==Operation.BREAK?"minecraft:air":block,row.operation()==Operation.BREAK||lever?"":row.operation()==Operation.WATER?"minecraft:water_bucket":block,contract,direction,support));
        }
        return new GeneratedBuildPlan("Veltraph generated 31x7x7 (92 ordered processes; local adapters)",31,7,7,out);
    }
    public static GeneratedBuildPlan source95(ConfigService config) {
        var out=new ArrayList<GeneratedBuildPlan.Process>();
        String clear=config.get(GeneratedBuilderConfig.CLEAR_BLOCK),building=config.get(BuilderConfig.BUILDER_BUILDING_BLOCK),toolBlock=config.get(GeneratedBuilderConfig.SOURCE95_TOOL_BLOCK);
        boolean prepare=config.get(GeneratedBuilderConfig.PREPARATION),ironman=config.get(GeneratedBuilderConfig.IRONMAN_MODE);
        // AIR is the rejecting branch of the source tool completion predicate. The readable local
        // counterpart places the explicitly bound output at this anchor; native tool row expansion is not inferred.
        for(int[] window:new int[][]{{0,36,3456},{36,71,3456},{71,96,2432}}) {
            if(window[0]==0||!prepare)out.add(resource(config,"source95 group1 resource "+window[2],new BlockPos(window[0],0,192),GeneratedBuilderConfig.SOURCE95_RESOURCE_BINDING));
            for(int x=window[0];x<window[1];x++)if((x-4)%5!=0)out.add(p("source95 group1 tool placement "+x,Operation.PLACE,new BlockPos(x,0,192),new BlockPos(x,0,192),"minecraft:air",toolBlock,toolBlock,toolBlock.isBlank()?"Bind BUILDER_GENERATED_SOURCE95_TOOL_BLOCK; source tool non-AIR output is a local single-cell counterpart":""));
        }
        if(prepare) {
            int markerX=config.get(GeneratedBuilderConfig.SOURCE95_MARKER_X);if(markerX<2||markerX>3)throw new IllegalArgumentException("Source95 marker x must be 2 or3");
            String marker=config.get(GeneratedBuilderConfig.SOURCE95_MARKER_BLOCK);var markerPos=new BlockPos(markerX,0,191);
            out.add(p("source95 group2 random marker",Operation.PLACE,markerPos,markerPos,"minecraft:air",marker,marker,marker.isBlank()?"Bind source95 marker tool output block; original row/resource expansion remains a local single-cell counterpart":""));
            for(int x=4;x<96;x+=5)out.add(p("source95 group2 irrigation "+x,Operation.WATER,new BlockPos(x,0,192),new BlockPos(x,0,192),"minecraft:air","minecraft:water","minecraft:water_bucket",""));
            for(int x=0;x<95;x++)if((x-4)%5!=0)out.add(p("source95 group3 till "+x,Operation.TILL,new BlockPos(x,0,192),new BlockPos(x,0,192),"minecraft:dirt","minecraft:farmland",config.get(GeneratedBuilderConfig.TILL_ITEM),config.get(GeneratedBuilderConfig.TILL_ITEM).isBlank()?"Bind BUILDER_GENERATED_TILL_ITEM for local hoe counterpart":""));
        }
        for(int[] window:new int[][]{{0,36,3456},{36,71,3456},{71,95,2432}}) {
            if(!ironman)out.add(resource(config,"source95 group4 resource "+window[2],new BlockPos(window[0],0,192),GeneratedBuilderConfig.SOURCE95_PLANT_RESOURCE_BINDING));
            for(int x=window[0];x<window[1];x++)if((x-4)%5!=0)out.add(p("source95 group4 plant "+x,Operation.PLANT,new BlockPos(x,1,192),new BlockPos(x,1,192),"minecraft:air",config.get(GeneratedBuilderConfig.PLANT_BLOCK),config.get(GeneratedBuilderConfig.PLANT_ITEM),config.get(GeneratedBuilderConfig.PLANT_BLOCK).isBlank()||config.get(GeneratedBuilderConfig.PLANT_ITEM).isBlank()?"Bind local plant item and observed block; salted source crop/tool expansion unresolved":""));
        }
        if(config.get(BuilderConfig.BUILDER_BUY_RESOURCES)&&!ironman)out.add(p("source95 group5 configured resource128",Operation.PURCHASE,new BlockPos(0,0,0),new BlockPos(0,0,0),"","",Source95CropResource.item(config.get(BuilderConfig.BUILDER_CROP_TYPE)),config.get(GeneratedBuilderConfig.SOURCE95_CROP_ACQUISITION_BINDING)));
        out.add(p("source95 group6 chunk scan",Operation.CONTRACT,new BlockPos(0,0,0),new BlockPos(0,0,0),"","","","Source95 chunk-scan IIllIIllIIIllllIIlIlIlllllIllIlIlllllIIllllIlllIIIllllllIllIlllI native lllllllIlIIlIIIllIIIIllIlllIllIIllIIllIIIlIllllIIIllIlIlIIIIllII / IllllIlIIlIIlIllIllIllIlIllIlIIllIIlIllllIlIlIlIllIIIIlIIlIIIlIl lines233/236; no native geometry fabricated"));
        if(config.get(BuilderConfig.BUILDER_BUY_RESOURCES)&&!ironman)out.add(p("source95 group7 conditional sell",Operation.MENU_FLOW,new BlockPos(0,0,0),new BlockPos(0,0,0),"","","",config.get(GeneratedBuilderConfig.SOURCE95_SELL_FLOW).isBlank()?"Bind BUILDER_GENERATED_SOURCE95_SELL_FLOW; readable conditional Bazaar SELL_INVENTORY with exact item-count/menu receipts":""));
        for(int z:new int[]{0,287}) {
            for(int x=4;x<96;x+=5)out.add(p("source95 endcap "+x+","+z,Operation.PLACE,new BlockPos(x,0,z),new BlockPos(x,0,z),"minecraft:air",building,building,""));
            if(prepare) {
                var setup=BuildSetupPlan.recovered95Irrigation(clear.isBlank()?building:clear);
                var range=z==0?setup.steps().subList(0,99):setup.steps().subList(99,198);
                for(var step:range)out.add(p("source95 irrigation "+step.stage(),step.operation()==BuildSetupPlan.Operation.CLEAR?Operation.BREAK:Operation.WATER,step.pos(),step.pos(),step.expectedBlock(),step.operation()==BuildSetupPlan.Operation.CLEAR?"minecraft:air":"minecraft:water",step.operation()==BuildSetupPlan.Operation.WATER?"minecraft:water_bucket":"",""));
            }else {
                for(int x=z==0?93:88;x>= (z==0?13:8);x-=10) {
                    var anchor=new BlockPos(x,1,z==0?1:286);
                    out.add(p("source95 preparation-off clear",Operation.BREAK,anchor,anchor.add(2,1,0),clear,"minecraft:air","",clear.isBlank()?"Bind expected clearance material":""));
                    out.add(new GeneratedBuildPlan.Process("source95 preparation-off tool",Operation.TOOL_USE,anchor.add(0,-1,0),anchor.add(0,-1,0),"","","",config.get(GeneratedBuilderConfig.SOURCE95_PREPARATION_OFF_TOOL_BINDING),new BlockPos(-1,0,0),false));
                }
            }
        }
        return new GeneratedBuildPlan("Veltraph generated 95x287 (ordered generated graph; local adapters)",95,1,287,out);
    }
    private static GeneratedBuildPlan.Process resource(ConfigService config,String source,BlockPos pos,ConfigKey<String> key) {
        String item="";try{item=GeneratedResourceBinding.decode(config.get(key)).toolItem();}catch(RuntimeException blankOrInvalid){}
        return p(source,Operation.RESOURCE_SETUP,pos,pos,"","",item,"Bind "+key.name()+" tool/resource/menu/gauge contract; source helper body is readable");
    }
}
