package dev.veltraph.client.navigation;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.*;
import java.util.*;

/** Explicit local provider data. Native-backed original destinations/costs are never guessed. */
public record TravelAtlas(String identity,List<Shortcut> shortcuts) {
    public enum Kind { ETHERWARP, FAST_WARP }
    public record Shortcut(String id,Kind kind,GameArea area,String dimension,Vec3 entrance,Vec3 arrival,
            BlockPos target,Direction face,String warpDestination,String permissionKey,String grantReceipt,String rejectionReceipt,
            double transitCost,int manaCost) {
        public Shortcut {
            if(id==null||!id.matches("[a-z0-9_-]{1,48}")||kind==null||area==null||!area.isSkyBlock()
                    ||dimension==null||!dimension.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")||!finite(entrance)||!finite(arrival)
                    ||entrance.distanceTo(arrival)<1.5||!Double.isFinite(transitCost)||transitCost<=0||transitCost>10000
                    ||manaCost<0||manaCost>100000)throw new IllegalArgumentException("Invalid scoped shortcut data");
            warpDestination=warpDestination==null?"":warpDestination;permissionKey=permissionKey==null?"":permissionKey;
            grantReceipt=grantReceipt==null?"":grantReceipt;rejectionReceipt=rejectionReceipt==null?"":rejectionReceipt;
            if(kind==Kind.ETHERWARP&&(target==null||face==null||manaCost<1||!warpDestination.isEmpty()))throw new IllegalArgumentException("Ether shortcut needs exact target/face and known positive mana cost");
            if(kind==Kind.FAST_WARP&&(target!=null||face!=null||!warpDestination.matches("[a-z0-9_]{1,40}")||permissionKey.isBlank()
                    ||grantReceipt.isBlank()||rejectionReceipt.isBlank()||manaCost!=0))throw new IllegalArgumentException("Ranked warp needs explicit destination and observed access/rejection receipts");
            if(grantReceipt.length()>160||rejectionReceipt.length()>160)throw new IllegalArgumentException("Receipt too long");
        }
    }
    public TravelAtlas {
        if(identity==null||identity.isBlank()||identity.length()>80||shortcuts==null||shortcuts.size()>24)throw new IllegalArgumentException("Bounded atlas identity required");
        shortcuts=List.copyOf(shortcuts);var ids=new HashSet<String>();for(var s:shortcuts)if(!ids.add(s.id()))throw new IllegalArgumentException("Duplicate shortcut id");
    }
    static boolean finite(Vec3 p) {return p!=null&&Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z())
            &&Math.abs(p.x())<=30_000_000&&Math.abs(p.z())<=30_000_000&&Math.abs(p.y())<=2048;}
}
