package dev.veltraph.client.garden.pests;

import dev.veltraph.client.gui.MenuSnapshot;
import java.util.*;

/** One issued native PICKUP prediction, scoped to its exact button and unchanged custody. */
public final class PestSpraySelectionCustody {
    private PestSpraySelectionCustody() {}
    /** Identities supplied by the native adapter include count and ALL persistent components. */
    public record Scope(Object player,Object world,Object connection,Object nativeMenu,int menuId,int revision,int selectedSlot,long clientTick,
            List<?> menuSlots,List<?> inventory,Object gear,Object cursor,boolean cursorEmpty,Object emptyIdentity) {
        public Scope {menuSlots=List.copyOf(menuSlots);inventory=List.copyOf(inventory);Objects.requireNonNull(gear);Objects.requireNonNull(cursor);Objects.requireNonNull(emptyIdentity);
            if(menuId<0||revision<0||selectedSlot<0||selectedSlot>8||clientTick<0||menuSlots.isEmpty()||inventory.size()!=36)throw new IllegalArgumentException("Unknown native selection scope bounds");}
    }
    public static final class Intent {
        private final Scope original;private final MenuSnapshot request;private final int slot;private final Object button;
        private Intent(Scope original,MenuSnapshot request,int slot){this.original=original;this.request=request;this.slot=slot;button=original.menuSlots().get(slot);}
        public boolean pendingMatches(Scope now,MenuSnapshot issued,int issuedSlot){
            if(now==null||issued!=request||issuedSlot!=slot||now.clientTick()<original.clientTick()||!sameOwner(original,now)
                    ||now.cursorEmpty()||!button.equals(now.cursor())||!now.menuSlots().get(slot).equals(now.emptyIdentity())
                    ||!original.inventory().equals(now.inventory())||!original.gear().equals(now.gear()))return false;
            for(int i=0;i<original.menuSlots().size();i++)if(i!=slot&&!original.menuSlots().get(i).equals(now.menuSlots().get(i)))return false;
            return true;
        }
    }
    /** The only origin is a successful request with its exact prediction observed in that same tick. */
    public static Optional<Intent> capture(Scope before,MenuSnapshot request,int slot,boolean successfullyIssued,Scope immediatelyAfter){
        if(!successfullyIssued||before==null||request==null||immediatelyAfter==null||!before.cursorEmpty()||!before.cursor().equals(before.emptyIdentity())
                ||before.player()==null||before.world()==null||before.connection()==null||before.nativeMenu()==null
                ||before.menuId()!=request.id()||before.revision()!=request.revision()||slot<0||slot>=before.menuSlots().size()
                ||before.menuSlots().get(slot).equals(before.emptyIdentity())||before.clientTick()!=immediatelyAfter.clientTick())return Optional.empty();
        var match=request.slots().stream().filter(s->s.id()==slot&&!s.playerInventory()&&!s.locked()&&s.count()>0).toList();if(match.size()!=1)return Optional.empty();
        var intent=new Intent(before,request,slot);return intent.pendingMatches(immediatelyAfter,request,slot)?Optional.of(intent):Optional.empty();
    }
    private static boolean sameOwner(Scope a,Scope b){return a.player()==b.player()&&a.world()==b.world()&&a.connection()==b.connection()
        &&a.nativeMenu()==b.nativeMenu()&&a.menuId()==b.menuId()&&a.revision()==b.revision()&&a.selectedSlot()==b.selectedSlot()
        &&a.menuSlots().size()==b.menuSlots().size()&&a.inventory().size()==b.inventory().size();}
}
