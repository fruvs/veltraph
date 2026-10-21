package dev.veltraph.client.fishing.trash;
import dev.veltraph.client.config.ConfigKey;
/** No source-salted junk label or guessed drop timing is enabled by default. */
public final class FishingTrashConfig {
    private FishingTrashConfig(){}
    public static final ConfigKey<Boolean> FISHING_THROW_TRASH=ConfigKey.ofBool("FISHING_THROW_TRASH",false);
    public static final ConfigKey<Boolean> FISHING_USE_AUTOMATIC_TRASH_LOCATIONS=ConfigKey.ofBool("FISHING_USE_AUTOMATIC_TRASH_LOCATIONS",false);
    public static final ConfigKey<String> FISHING_TRASH_PROFILE=new ConfigKey<>("FISHING_TRASH_PROFILE","{}");
}
