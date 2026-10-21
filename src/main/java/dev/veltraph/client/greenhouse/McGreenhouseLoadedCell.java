package dev.veltraph.client.greenhouse;

import net.minecraft.client.multiplayer.ClientLevel;
import static dev.veltraph.client.greenhouse.GreenhouseModel.*;

/** A loaded X/Z chunk alone does not establish a readable plant or soil cell. Never loads chunks. */
final class McGreenhouseLoadedCell {
    private McGreenhouseLoadedCell() {}
    static boolean withinHeight(Cell c,int actualMinY,int actualMaxY){return c!=null&&actualMinY<actualMaxY&&c.y()>=actualMinY&&c.y()<actualMaxY;}
    static boolean readable(ClientLevel actualLevel,Cell c){
        return actualLevel!=null&&withinHeight(c,actualLevel.getMinY(),actualLevel.getMaxY())
            &&actualLevel.getChunkSource().hasChunk(c.x()>>4,c.z()>>4);
    }
}
