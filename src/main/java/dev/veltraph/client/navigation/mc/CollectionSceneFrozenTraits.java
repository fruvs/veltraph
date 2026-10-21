package dev.veltraph.client.navigation.mc;

import java.util.List;

/** Immutable native traits. Final checks use primitive records and live body coordinates only. */
final class CollectionSceneFrozenTraits {
    record Scalars(int friction,int speed,int jump,int bounce,int fall) {}
    record Cell(int x,int y,int z,boolean hazard,boolean climbable,boolean historicalSupportSpecial,Scalars scalars) {}
    private CollectionSceneFrozenTraits() {}
    /** Exact fixed movement offset: native ifgt at 44 skips FENCES, retaining WALLS/FenceGate. */
    static int movementSupportY(double liveY,int historicalY,boolean wallsOrFenceGate) {
        return wallsOrFenceGate ? historicalY : (int)Math.floor(liveY-(double).500001F);
    }
    static Cell at(List<Cell> cells,int x,int y,int z) {
        for (Cell cell : cells) if (cell.x==x && cell.y==y && cell.z==z) return cell;
        return null;
    }
    static boolean clear(CollectionSceneFrozenBoxes.Box body,double footY,List<Cell> cells,
                         Scalars bodyBaseline,Scalars supportBaseline) {
        if (!CollectionSceneFrozenBoxes.finite(body) || !Double.isFinite(footY)
            || cells == null || cells.size()>4096 || bodyBaseline == null || supportBaseline == null
            || body.minX()<Integer.MIN_VALUE || body.maxX()>=Integer.MAX_VALUE
            || body.minY()<Integer.MIN_VALUE+2D || body.maxY()>=Integer.MAX_VALUE
            || body.minZ()<Integer.MIN_VALUE || body.maxZ()>=Integer.MAX_VALUE) return false;
        int low=(int)Math.floor(footY-1D),high=(int)Math.floor(footY),plane=(int)Math.floor(footY-.001D);
        int covered=0;
        int minX=(int)Math.floor(body.minX()),maxX=(int)Math.floor(Math.nextDown(body.maxX()));
        int minZ=(int)Math.floor(body.minZ()),maxZ=(int)Math.floor(Math.nextDown(body.maxZ()));
        int minY=Math.min(low,(int)Math.floor(body.minY())),maxY=(int)Math.floor(Math.nextDown(body.maxY()));
        double required=(maxX-minX+1D)*(maxY-minY+1D)*(maxZ-minZ+1D);
        if (!Double.isFinite(required) || required<1 || required>4096) return false;
        for (Cell cell : cells) {
            boolean horizontal=cell.x+1D>body.minX() && cell.x<body.maxX()
                && cell.z+1D>body.minZ() && cell.z<body.maxZ();
            boolean bodyHit=horizontal && cell.y+1D>body.minY() && cell.y<body.maxY();
            boolean bandHit=horizontal && cell.y>=low && cell.y<=high;
            if (!bodyHit && !bandHit) continue;
            covered++;
            if (cell.hazard || cell.climbable || cell.scalars==null) return false;
            // Ground damping uses the physical support plane. Body/upper-band block traits use
            // the actual captured native body cell, preserving distinct AIR and ice semantics.
            Scalars expected=bandHit && cell.y==plane ? supportBaseline : bodyBaseline;
            if (!cell.scalars.equals(expected)) return false;
        }
        // The parent's complete query halo must contain every live body/support cell.
        return covered == (int)required;
    }
}
