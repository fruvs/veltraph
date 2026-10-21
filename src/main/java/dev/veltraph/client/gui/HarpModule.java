package dev.veltraph.client.gui;
import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import java.util.ArrayList;
import java.util.OptionalLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Opt-in helper. The player opens Melody and chooses a song; only the harp grid is automated. */
public final class HarpModule extends AbstractModule {
    public static final String ID="auto_harp";
    private final Minecraft client;private final ConfigService config;
    private final HarpRuntime runtime=new HarpRuntime();
    private final HarpRuntime.TimingLimits timingLimits;
    private AbstractContainerMenu boundMenu;
    private Object boundConnection,boundLevel;
    private boolean automatic;
    private String timingStatus="waiting for native harp menu";
    private boolean nativeInputObserverAttached;
    private long nativeInputObserverReceipts,lastNativeInputObserverMillis=Long.MIN_VALUE;
    private long tick;private int clicks;
    public HarpModule(Minecraft client,ConfigService config) { this(client,config,HarpRuntime.TimingLimits.recoveredSource()); }
    /** Explicit private fixture/recovered numeric contract; production never invents these constants. */
    HarpModule(Minecraft client,ConfigService config,HarpRuntime.TimingLimits limits) {
        super(ID,"Auto-Harp",ModuleCategory.MISC);this.client=client;this.config=config;this.timingLimits=limits;
    }
    @Override public boolean requiresSkyBlock() { return true; }
    @Override public boolean ownsGameplayInput() { return false; }
    @Override public boolean allowsScreen(String title) { return title.startsWith("Harp - ")||title.equals("Melody"); }
    @Override public java.util.Optional<String> preStartCheck() {
        if(config.get(HarpConfig.AUTOMATIC_HARP_TICK_RATE_DETECTION)&&!timingLimits.complete())
            return java.util.Optional.of("Automatic Harp timing unsupported: source sample retention and shared click cooldown remain unreadable. Manual timing is available.");
        if(timingLimits.complete()&&!nativeInputObserverAttached)
            return java.util.Optional.of("Harp source timing requires the installed common native container-input observer.");
        return java.util.Optional.empty();
    }
    @Override public void onEnable() { clearBinding();tick=0;clicks=0; }
    @Override public void onDisable() { clearBinding(); }
    private void clearBinding() { runtime.reset();boundMenu=null;boundConnection=null;boundLevel=null; }
    /** Root integration callback from the actual container-input handler, including other menus. */
    public void onNativeContainerInput() {
        long now=System.currentTimeMillis();runtime.onNativeContainerInput(now);
        nativeInputObserverReceipts++;lastNativeInputObserverMillis=now;
    }
    /** Root calls once after registering this instance with the actual common native input hook. */
    public void attachNativeInputObserver() { nativeInputObserverAttached=true; }
    public record NativeInputObserverReceipt(boolean attached,long receipts,OptionalLong wallClockMillis) {}
    public NativeInputObserverReceipt nativeInputObserverReceipt() {
        return new NativeInputObserverReceipt(nativeInputObserverAttached,nativeInputObserverReceipts,
                lastNativeInputObserverMillis==Long.MIN_VALUE?OptionalLong.empty():OptionalLong.of(lastNativeInputObserverMillis));
    }
    public HarpRuntime.NativeInputObservation nativeInputObservation() { return runtime.nativeInputObservation(); }
    /** Read-only query with an explicit diagnostic/fixture interval; never configures source timing. */
    public long nativeInputCooldownRemainingMillis(long diagnosticIntervalMillis) {
        return runtime.nativeInputCooldownRemainingMillis(System.currentTimeMillis(),diagnosticIntervalMillis);
    }
    public HarpRuntime.TimingLimits sourceTimingLimits() { return timingLimits; }
    public java.util.Optional<HarpRuntime.SampleObservation> latestSourceSampleObservation() { return runtime.latestSampleObservation(); }
    public record SourceTimingObservation(HarpRuntime.TimingState state,int retainedSamples,int queuedNotes,
            java.util.OptionalDouble averageTicks,java.util.OptionalDouble sumTicks,java.util.OptionalInt maxWholeTicks) {}
    /** Observation of the actual native clock and source retention policy; never fills missing limits. */
    public SourceTimingObservation sourceTimingObservation() {
        if(timingLimits.sampleRetentionMillis().isEmpty())return new SourceTimingObservation(runtime.timingState(),runtime.sampleCount(),runtime.queuedSourceNotes(),java.util.OptionalDouble.empty(),java.util.OptionalDouble.empty(),java.util.OptionalInt.empty());
        long now=System.currentTimeMillis(),retention=timingLimits.sampleRetentionMillis().getAsLong();
        double average=runtime.averageTicks(now,retention),sum=runtime.sumTicks(now,retention);int max=runtime.maxWholeTicks(now,retention);
        return new SourceTimingObservation(runtime.timingState(),runtime.sampleCount(),runtime.queuedSourceNotes(),java.util.OptionalDouble.of(average),java.util.OptionalDouble.of(sum),java.util.OptionalInt.of(max));
    }
    @Override public void onTick() {
        tick++;
        if(!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)||!screen.getTitle().getString().startsWith("Harp - ")) { clearBinding();timingStatus="waiting for native harp menu";return; }
        if(client.player==null||client.gameMode==null) { setEnabled(false);return; }
        var handler=screen.getMenu();var slots=new ArrayList<String>();
        boolean currentAutomatic=config.get(HarpConfig.AUTOMATIC_HARP_TICK_RATE_DETECTION);
        if(boundMenu!=handler||boundConnection!=client.getConnection()||boundLevel!=client.level||automatic!=currentAutomatic) {
            runtime.reset();boundMenu=handler;boundConnection=client.getConnection();boundLevel=client.level;automatic=currentAutomatic;
        }
        // Never include the player's own inventory in note detection or clicks.
        for(var slot:handler.slots) {
            if(slot.container==client.player.getInventory()) break;
            if(slot.index!=slots.size()) { setEnabled(false);return; }
            slots.add(BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).toString());
        }
        if(client.player.containerMenu!=handler||!handler.getCarried().isEmpty()) { runtime.reset();timingStatus="native menu/cursor changed";return; }
        // Missing latency is distinct from a real native zero, including private integrated servers.
        var info=client.getConnection()==null?null:client.getConnection().getPlayerInfo(client.getUser().getProfileId());
        var latency=info==null?OptionalLong.empty():OptionalLong.of(info.getLatency());
        var click=automatic||timingLimits.complete()
                ?runtime.nativeTick(handler.containerId,slots,System.currentTimeMillis(),latency,automatic,
                        config.get(MiscConfig.AUTO_HARP_TICK_DELAY),timingLimits)
                :runtime.tick(handler.containerId,slots,tick,config.get(MiscConfig.AUTO_HARP_TICK_DELAY));
        timingStatus=automatic||timingLimits.complete()?runtime.timingState().name():"manual local timing; source shared cooldown unresolved";
        if(click.isPresent()&&click.getAsInt()<slots.size()&&client.player.containerMenu==handler&&client.gui.screen()==screen) {
            // Source note helper uses THROW/button 0, not ordinary pickup.
            // Standalone component fallback has no installed common hook. Registered Root input
            // observes the real GameMode call once through its hook, without a second pre-notify.
            if(!nativeInputObserverAttached)runtime.onNativeContainerInput(System.currentTimeMillis());
            client.gameMode.handleContainerInput(handler.containerId,click.getAsInt(),0,ContainerInput.THROW,client.player);clicks++;
        }
    }
    @Override public String diagnostics() { return "Harp helper: "+clicks+" clicks; "+timingStatus; }
}
