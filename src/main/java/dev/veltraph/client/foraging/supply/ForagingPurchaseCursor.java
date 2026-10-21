package dev.veltraph.client.foraging.supply;
import java.util.*;
/** A single issued PICKUP's exact predicted cursor; permits waiting only, never clicking/crediting/closing. */
final class ForagingPurchaseCursor {
    private final ForagingSupplyRuntime.Scope scope;private final Object menu;private final int menuId;private final ForagingSupplyRuntime.Stack predicted;private final List<ForagingSupplyRuntime.Stack> before;private final long purse;
    ForagingPurchaseCursor(ForagingSupplyRuntime.Scope scope,Object menu,int menuId,ForagingSupplyRuntime.Stack predicted,List<ForagingSupplyRuntime.Stack> before,long purse){this.scope=Objects.requireNonNull(scope);this.menu=Objects.requireNonNull(menu);this.menuId=menuId;this.predicted=Objects.requireNonNull(predicted);this.before=List.copyOf(before);this.purse=purse;}
    boolean permits(ForagingSupplyRuntime.Observation o,Object actualMenu,int actualMenuId,ForagingSupplyRuntime.Stack cursor){return scope.same(o.scope())&&o.menu()==menu&&actualMenu==menu&&actualMenuId==menuId&&o.alive()&&o.chestValid()&&!o.cursorEmpty()&&o.inventory().equals(before)&&o.purse()==purse&&cursor.equals(predicted)&&predicted.count()>0&&!predicted.protectedItem();}
}
