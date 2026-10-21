package dev.veltraph.client.inventory.selling;

import dev.veltraph.client.gui.MenuSnapshot;

/** Read-only rejection predicate, never an action permit or a menu-adoption mechanism. */
public final class PreparedBazaarStartBoundary {
    private PreparedBazaarStartBoundary() {}
    public static boolean current(boolean gameplay,Object preparedScreen,Object preparedContainer,MenuSnapshot preparedMenu,
                                  Object screen,Object container,Object inventoryContainer,MenuSnapshot menu){
        if(preparedContainer==null||container!=preparedContainer)return false;
        if(gameplay)return preparedScreen==null&&preparedMenu==null&&screen==null&&menu==null&&container==inventoryContainer;
        return preparedScreen!=null&&preparedMenu!=null&&screen==preparedScreen&&preparedMenu.equals(menu);
    }
}
