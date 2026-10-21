package dev.veltraph.client.garden.pests.philip;

import dev.veltraph.client.garden.pests.PestBindings;
import dev.veltraph.client.inventory.abiphone.AbiphoneCall;
import dev.veltraph.client.world.Vec3;
import java.util.*;

/** Both source ingress routes remain available; encrypted metadata requires explicit observations. */
public record PestPhilipBindings(Route route,PestBindings.Item vacuum,String bagPrefix,PestBagExchange.Settings exchange,
                                 AbiphoneCall.Request call,Npc npc,Barn barn,SourceContactCommand.Settings commandCall) {
    public enum Route { ABIPHONE_COMMAND, ABIPHONE_PHONE_LEGACY, BARN_NPC }
    /** Source RemotePlayer NPC: profile UUID and exact observed display identity, never a generic model. */
    public record Npc(UUID uuid,String name) {public Npc {Objects.requireNonNull(uuid);if(name==null||name.isBlank())throw new IllegalArgumentException("Observed exact Philip profile/display identity required");}}
    public record Barn(String command,Vec3 spawn,int timeoutTicks) {public Barn {
        if(command==null||!Set.of("warp garden","tptoplot barn","tptoplot 0").contains(command)||spawn==null||!Double.isFinite(spawn.x())||!Double.isFinite(spawn.y())||!Double.isFinite(spawn.z())
            ||!dev.veltraph.client.garden.pests.GardenPlotBounds.contains(0,spawn.x(),spawn.z())||spawn.y()<60||spawn.y()>=92||timeoutTicks<20||timeoutTicks>1200)
            throw new IllegalArgumentException("Observed bounded barn command/spawn/native-arrival contract required");}}
    public PestPhilipBindings {Objects.requireNonNull(route);Objects.requireNonNull(vacuum);Objects.requireNonNull(exchange);if(bagPrefix==null||bagPrefix.isBlank())throw new IllegalArgumentException("Observed vacuum bag prefix required");
        if(route==Route.ABIPHONE_PHONE_LEGACY&&(call==null||!call.retainDialogue()||!call.dialogueTitle().equals(exchange.title())||!call.dialogueMarker().equals(exchange.marker())))throw new IllegalArgumentException("Exact retained Philip Abiphone dialogue handoff required");
        if(route==Route.ABIPHONE_COMMAND&&(commandCall==null||!commandCall.title().equals(exchange.title())||!commandCall.marker().equals(exchange.marker())))throw new IllegalArgumentException("Exact current source command/dialogue handoff required");
        if(route==Route.BARN_NPC&&(npc==null||barn==null))throw new IllegalArgumentException("Both exact Philip NPC and observed barn ingress required");}
}
