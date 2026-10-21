package dev.veltraph.client.navigation.mc;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.veltraph.client.core.ModuleActivationGuard;
import dev.veltraph.client.game.*;
import dev.veltraph.client.navigation.*;
import dev.veltraph.client.world.Vec3;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Local route setup, inspired by FishingLocationSetup and GhostRouteLoader. */
public final class RouteCommands {
    private final List<LocalRoute.Node> recording=new ArrayList<>();
    private GameArea area;private String dimension;
    private final LocalRouteStore store;private final RouteModule module;
    private final GameContextService context;private final ModuleActivationGuard guard;
    public RouteCommands(LocalRouteStore store,RouteModule module,GameContextService context,ModuleActivationGuard guard) {
        this.store=store;this.module=module;this.context=context;this.guard=guard;
    }
    public void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->dispatcher.register(ClientCommands.literal("vroute")
                .executes(c->run("help","",c.getSource()))
                .then(ClientCommands.argument("action",StringArgumentType.word()).executes(c->run(StringArgumentType.getString(c,"action"),"",c.getSource()))
                        .then(ClientCommands.argument("name",StringArgumentType.word()).executes(c->run(StringArgumentType.getString(c,"action"),StringArgumentType.getString(c,"name"),c.getSource()))))));
    }
    private int run(String action,String name,FabricClientCommandSource source) {
        try {
            var client=Minecraft.getInstance();
            if(module.isEnabled()&&Set.of("add","clear","save","save-loop","load").contains(action))
                throw new IllegalArgumentException("Stop the route before changing its recording or files");
            switch(action) {
                case "add" -> {
                    if(module.isEnabled()) throw new IllegalArgumentException("Stop the route before recording");
                    if(client.player==null||client.level==null||!context.current().inSkyBlock()||context.current().area()==GameArea.UNKNOWN)
                        throw new IllegalArgumentException("A known SkyBlock location is required");
                    if(recording.size()>=4096) throw new IllegalArgumentException("4096 checkpoint limit reached");
                    var here=context.current().area();String dim=client.level.dimension().identifier().toString();
                    if(!recording.isEmpty()&&(here!=area||!dim.equals(dimension))) throw new IllegalArgumentException("Clear the recording before changing area or dimension");
                    area=here;dimension=dim;
                    LocalRoute.Kind kind=name.isBlank()?LocalRoute.Kind.WALK:LocalRoute.Kind.valueOf(name.toUpperCase(Locale.ROOT));
                    LocalRoute.Node node;
                    if(kind==LocalRoute.Kind.ETHERWARP) {
                        var hit=client.player.pick(100,1,true);
                        if(!(hit instanceof net.minecraft.world.phys.BlockHitResult block)||hit.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK)
                            throw new IllegalArgumentException("Look at the destination block to record an etherwarp");
                        var pos=block.getBlockPos();
                        node=new LocalRoute.Node(kind,new Vec3(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5),client.player.getYRot(),client.player.getXRot(),
                                new dev.veltraph.client.world.BlockPos(pos.getX(),pos.getY(),pos.getZ()),dev.veltraph.client.world.Direction.valueOf(block.getDirection().name()));
                    } else node=new LocalRoute.Node(kind,new Vec3(client.player.getX(),client.player.getY(),client.player.getZ()),client.player.getYRot(),client.player.getXRot());
                    recording.add(node);
                    module.setRoute(new LocalRoute(area,dimension,false,recording));
                    source.sendFeedback(Component.literal("Recorded "+kind.name().toLowerCase(Locale.ROOT)+" checkpoint "+recording.size()));
                }
                case "clear" -> { if(module.isEnabled()) throw new IllegalArgumentException("Stop the route first");recording.clear();source.sendFeedback(Component.literal("Recording cleared; loaded route unchanged")); }
                case "save","save-loop" -> {
                    LocalRoute route=new LocalRoute(area,dimension,action.equals("save-loop"),recording);
                    store.save(name,route);module.setRoute(route);source.sendFeedback(Component.literal("Saved "+recording.size()+" checkpoints to "+store.path(name)));
                }
                case "load" -> { LocalRoute route=store.load(name);module.setRoute(route);recording.clear();recording.addAll(route.nodes());area=route.area();dimension=route.dimension();source.sendFeedback(Component.literal(module.diagnostics())); }
                case "start" -> guard.start(module);
                case "stop" -> module.setEnabled(false);
                case "status" -> source.sendFeedback(Component.literal(module.diagnostics()+" | recording="+recording.size()));
                default -> source.sendFeedback(Component.literal("/vroute add [walk|fly|etherwarp] | clear | save <name> | save-loop <name> | load <name> | start | stop | status"));
            }
            return 1;
        } catch(Exception e) { source.sendError(Component.literal("Route: "+e.getMessage()));return 0; }
    }
}
