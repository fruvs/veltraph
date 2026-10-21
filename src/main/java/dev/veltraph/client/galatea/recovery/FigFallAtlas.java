package dev.veltraph.client.galatea.recovery;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.List;
import java.util.Optional;

/** Ordinary Java constants from stable 4.0.30; independent of the native tree atlas. */
public final class FigFallAtlas {
    public record Area(BlockPos first,BlockPos second,List<BlockPos> goals) {
        public Area { goals=List.copyOf(goals); }
        /** Source AABB helper takes min and max + 1, with exclusive upper faces. */
        public boolean contains(Vec3 p) {
            return p!=null&&p.x()>=Math.min(first.x(),second.x())&&p.x()<Math.max(first.x(),second.x())+1
                    &&p.y()>=Math.min(first.y(),second.y())&&p.y()<Math.max(first.y(),second.y())+1
                    &&p.z()>=Math.min(first.z(),second.z())&&p.z()<Math.max(first.z(),second.z())+1;
        }
    }
    public static final List<Area> AREAS=List.of(
            new Area(new BlockPos(-733,114,-39),new BlockPos(-726,109,-32),List.of(
                    new BlockPos(-732,115,-37),new BlockPos(-733,115,-35),new BlockPos(-733,119,-28),
                    new BlockPos(-729,120,-23),new BlockPos(-703,121,19))),
            new Area(new BlockPos(-735,105,-15),new BlockPos(-719,115,-31),List.of(
                    new BlockPos(-729,120,-23),new BlockPos(-727,120,-19),new BlockPos(-734,116,-35),new BlockPos(-703,121,-19))));
    private FigFallAtlas() {}
    public static Optional<Area> containing(Vec3 position) {return AREAS.stream().filter(a->a.contains(position)).findFirst();}
}
