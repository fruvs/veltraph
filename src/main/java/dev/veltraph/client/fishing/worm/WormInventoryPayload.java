package dev.veltraph.client.fishing.worm;
import java.util.*;
/** Typed single-slot packet payload must equal the actual applied changed cell; timing alone is no receipt. */
public final class WormInventoryPayload {
    private WormInventoryPayload(){}
    public record Payload(int inventoryIndex,Object fullIdentity,boolean handledWindow){}
    public static int inventoryIndexForMenuSlot(int containerId,int menuSlot){if(containerId!=0)return -1;if(menuSlot>=9&&menuSlot<=35)return menuSlot;if(menuSlot>=36&&menuSlot<=44)return menuSlot-36;return -1;}
    public static boolean applied(Payload payload,List<WormOwnedCatch.Cell> before,List<WormOwnedCatch.Cell> after){
        if(payload==null||!payload.handledWindow||payload.inventoryIndex<0||payload.inventoryIndex>=36||payload.fullIdentity==null||before.size()!=36||after.size()!=36)return false;
        int changed=-1;for(int i=0;i<36;i++)if(!before.get(i).equals(after.get(i))){if(changed>=0)return false;changed=i;}
        return changed==payload.inventoryIndex&&Objects.equals(after.get(changed).full(),payload.fullIdentity);
    }
}
