package dev.veltraph.client.movement.device;

import java.util.*;
import dev.veltraph.client.inventory.NativeStackIdentity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;

/** Defensive read-only discovery; all 36 slots are captured, only a real hotbar device is selectable. */
public final class McMovementDeviceInventory {
    public record Tool(int slot,String serverId,NativeStackIdentity.Snapshot identity,List<MovementDeviceEvidence.Capability> capabilities) {}
    public record Snapshot(List<NativeStackIdentity.Snapshot> inventory,List<Tool> tools,int selectedSlot) {}
    private McMovementDeviceInventory() {}
    public static Optional<Snapshot> read(Minecraft c){
        if(c==null||!c.isSameThread()||c.player==null||c.level==null||c.player.level()!=c.level)return Optional.empty();
        var values=new ArrayList<NativeStackIdentity.Snapshot>();var tools=new ArrayList<Tool>();
        try{for(int slot=0;slot<36;slot++){
            var stack=c.player.getInventory().getItem(slot);var id=NativeStackIdentity.capture(stack,c.level.registryAccess());values.add(id);
            if(stack.isEmpty()||slot>=9)continue;var data=stack.get(DataComponents.CUSTOM_DATA);var lore=stack.get(DataComponents.LORE);if(data==null||lore==null)continue;
            var tag=data.copyTag();String serverId=tag.getString("id").orElseGet(()->tag.getCompoundOrEmpty("ExtraAttributes").getStringOr("id",""));
            var capabilities=MovementDeviceEvidence.parse(serverId,lore.lines().stream().map(l->l.getString()).toList());
            if(!capabilities.isEmpty())tools.add(new Tool(slot,serverId,id,capabilities));
        }}catch(RuntimeException unsupported){return Optional.empty();}
        return Optional.of(new Snapshot(List.copyOf(values),List.copyOf(tools),c.player.getInventory().getSelectedSlot()));
    }
    public static boolean unchanged(Minecraft c,Snapshot saved){return saved!=null&&read(c).map(s->s.inventory().equals(saved.inventory())).orElse(false);}
}
