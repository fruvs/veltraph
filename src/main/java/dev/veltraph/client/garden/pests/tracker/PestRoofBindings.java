package dev.veltraph.client.garden.pests.tracker;

import dev.veltraph.client.garden.pests.GardenPlotBounds;
import dev.veltraph.client.world.BlockPos;
import java.util.*;

/** Opaque Garden roof geometry is supplied from an observed world, never an invented default map. */
public record PestRoofBindings(List<Support> supports) {
    public record Support(int plotId,BlockPos block,String registryId) {
        public Support {
            if(block==null||block.y()<60||block.y()>=77||!GardenPlotBounds.contains(plotId,block.x()+.5,block.z()+.5)
                    ||registryId==null||!registryId.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"))
                throw new IllegalArgumentException("Exact observed source-range roof support and Garden plot required");
        }
    }
    public PestRoofBindings {
        supports=List.copyOf(supports);
        if(supports.isEmpty()||supports.size()>4096)throw new IllegalArgumentException("Bounded observed roof supports required");
        var unique=new HashSet<BlockPos>();for(var support:supports)if(!unique.add(support.block()))throw new IllegalArgumentException("Duplicate roof support");
    }
    public Optional<Support> at(int plot,BlockPos block){return supports.stream().filter(s->s.plotId()==plot&&s.block().equals(block)).findFirst();}
}
