package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.navigation.GroundGeometryCellJournal;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Ordered immutable copies of already-read shape data. No world, native, clock, scope,
 * cache or permission is retained. The native adapter supplies only its original Box mapper.
 * Native shape caps remain at the original callers, before this pure conversion.
 * Internal contract: mapper returns a non-null Box; all three native mappers construct new Box.
 * This helper does not promise generic nullable-stream parity. */
final class GroundGeometryShapeSnapshot {
    private GroundGeometryShapeSnapshot() {}
    static <T> List<GroundGeometryCellJournal.Box> mapNonNull(List<T> source,
            Function<? super T,GroundGeometryCellJournal.Box> mapper) {
        Objects.requireNonNull(source);Objects.requireNonNull(mapper);
        int size=source.size();
        if(size==0)return List.of();
        if(size==1)return List.of(mapper.apply(source.get(0)));
        var boxes=new GroundGeometryCellJournal.Box[size];
        for(int i=0;i<size;i++)boxes[i]=mapper.apply(source.get(i));
        return List.of(boxes);
    }
}
