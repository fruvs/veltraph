package dev.veltraph.client.builder;

import dev.veltraph.client.world.BlockPos;
import java.util.*;

/** Ordered source subprocesses, including unresolved source contracts. No world scan fabricates geometry. */
public record GeneratedBuildPlan(String name, int width, int height, int depth, List<Process> processes) {
    public enum Operation { PLACE, BREAK, WATER, TILL, PLANT, LEVER, RESOURCE_SETUP, MENU_FLOW, RESTOCK, TOOL_USE, PURCHASE, CONTRACT }
    public record Process(String source, Operation operation, BlockPos first, BlockPos last,
                          String before, String after, String item, String contract, BlockPos direction, boolean requireSupport) {
        public Process(String source,Operation operation,BlockPos first,BlockPos last,String before,String after,String item,String contract){this(source,operation,first,last,before,after,item,contract,null,false);}
        public Process {
            Objects.requireNonNull(source); Objects.requireNonNull(operation);
            Objects.requireNonNull(first); Objects.requireNonNull(last);
            before=Objects.requireNonNull(before); after=Objects.requireNonNull(after);
            item=Objects.requireNonNull(item); contract=Objects.requireNonNull(contract);
            for(var id:List.of(before,after,item))if(!id.isEmpty()&&!id.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"))throw new IllegalArgumentException("Invalid material id");
            if(operation==Operation.PLACE&&after.equals("minecraft:air"))throw new IllegalArgumentException("Placement requires a non-air block");
            if(direction!=null&&Math.abs(direction.x())+Math.abs(direction.y())+Math.abs(direction.z())!=1)throw new IllegalArgumentException("Placement direction must be cardinal");
            if(operation==Operation.PURCHASE&&!first.equals(last))throw new IllegalArgumentException("Source95 purchase is one non-geometric128 acquisition intent");
            if(volume(first,last)>50000)throw new IllegalArgumentException("Subprocess exceeds local operation cap");
        }
        public List<BlockPos> cells() {
            var out=new ArrayList<BlockPos>();
            // Local inclusive endpoint expansion, preserving endpoint direction. Native ordering is opaque.
            for(int y=first.y();;y+=Integer.compare(last.y(),first.y())) {
                for(int z=first.z();;z+=Integer.compare(last.z(),first.z())) {
                    for(int x=first.x();;x+=Integer.compare(last.x(),first.x())) {out.add(new BlockPos(x,y,z));if(x==last.x())break;}
                    if(z==last.z())break;
                }
                if(y==last.y())break;
            }
            return List.copyOf(out);
        }
        public Optional<String> unresolved() {
            if(operation==Operation.PURCHASE){try{Source95AcquisitionBinding.decode(contract,Source95CropResource.crop(item));return Optional.empty();}catch(RuntimeException invalid){return Optional.of(invalid.getMessage());}}
            if(operation==Operation.TOOL_USE){try{Source95ToolBinding.decode(contract);return Optional.empty();}catch(RuntimeException invalid){return Optional.of(invalid.getMessage());}}
            if(operation==Operation.CONTRACT)return Optional.of(contract);
            if(operation==Operation.LEVER)return Optional.empty();
            if(operation==Operation.RESOURCE_SETUP)return item.isBlank()?Optional.of(contract):Optional.empty();
            if(operation==Operation.MENU_FLOW)return contract.isBlank()?Optional.empty():Optional.of(contract);
            if(operation==Operation.RESTOCK)return item.isBlank()?Optional.of(contract):Optional.empty();
            if(operation==Operation.BREAK&&before.isBlank())return Optional.of(contract);
            if(operation!=Operation.BREAK&&(after.isBlank()||item.isBlank()))return Optional.of(contract);
            if((operation==Operation.TILL||operation==Operation.PLANT)&&before.isBlank())return Optional.of(contract);
            return contract.isBlank()?Optional.empty():Optional.of(contract);
        }
    }
    private static long volume(BlockPos a,BlockPos b) {
        return (Math.abs((long)a.x()-b.x())+1)*(Math.abs((long)a.y()-b.y())+1)*(Math.abs((long)a.z()-b.z())+1);
    }
    public GeneratedBuildPlan {
        Objects.requireNonNull(name);processes=List.copyOf(processes);
        if(name.isBlank()||width<1||width>481||depth<1||depth>481||height<1||height>256||processes.isEmpty())throw new IllegalArgumentException("Invalid generated plan");
        long count=0;
        for(var p:processes) {
            count+=volume(p.first(),p.last());
            if(p.operation()==Operation.PURCHASE)count+=128;
            if(p.operation()==Operation.TOOL_USE&&!p.contract().isBlank())count+=Source95ToolBinding.decode(p.contract()).outputs().size();
            for(var point:List.of(p.first(),p.last()))if(Math.abs((long)point.x())>480||Math.abs((long)point.z())>480||Math.abs((long)point.y())>256)throw new IllegalArgumentException("Source coordinates exceed local bounds");
        }
        if(count>50000)throw new IllegalArgumentException("Generated plan exceeds local operation cap");
    }
    public GeneratedBuildPlan transformed(SourceBuilderTransform transform) {
        var result=new GeneratedBuildPlan(name,width,height,depth,processes.stream().map(p->{
            BlockPos direction=null;if(p.direction()!=null){var a=transform.apply(new BlockPos(0,0,0));var b=transform.apply(p.direction());direction=new BlockPos(b.x()-a.x(),b.y()-a.y(),b.z()-a.z());}
            return new Process(p.source(),p.operation(),transform.apply(p.first()),transform.apply(p.last()),p.before(),p.after(),p.item(),p.contract(),direction,p.requireSupport());
        }).toList());
        for(var p:result.processes()) {
            var points=new ArrayList<BlockPos>(List.of(p.first(),p.last()));
            if(p.operation()==Operation.TOOL_USE&&!p.contract().isBlank())points.addAll(Source95ToolBinding.decode(p.contract()).at(p.first(),p.direction()).stream().map(Source95ToolBinding.Output::offset).toList());
            for(var point:points)if(point.x()<-240||point.x()>239||point.z()<-240||point.z()>239||point.y()<0||point.y()>255)throw new IllegalArgumentException("Generated target outside Garden bounds: "+point);
        }
        return result;
    }
    public Map<String,Long> requiredMaterials() {
        var out=new LinkedHashMap<String,Long>();
        for(var p:processes)if(p.operation()==Operation.PLACE||p.operation()==Operation.PLANT||p.operation()==Operation.WATER)if(!p.item().isBlank())out.merge(p.item(),volume(p.first(),p.last()),Long::sum);
        return Collections.unmodifiableMap(out);
    }
    public List<String> unresolvedContracts() {return processes.stream().flatMap(p->p.unresolved().stream()).distinct().toList();}
}
