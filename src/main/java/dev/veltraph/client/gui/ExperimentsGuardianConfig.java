package dev.veltraph.client.gui;

import dev.veltraph.client.config.*;

/** Source-backed Guardian preflight opt-in. No guessed live menu/command/tab/lore bindings. */
public final class ExperimentsGuardianConfig {
    private ExperimentsGuardianConfig() {}
    public static final ConfigKey<Boolean> EXPERIMENTS_GUARDIAN_ENABLED=ConfigKey.ofBool("EXPERIMENTS_GUARDIAN_ENABLED",false);
    public static final ConfigKey<String> EXPERIMENTS_GUARDIAN_TAB_PREFIX=new ConfigKey<>("EXPERIMENTS_GUARDIAN_TAB_PREFIX","");
    public static final ConfigKey<String> EXPERIMENTS_GUARDIAN_TAB_SUFFIX=new ConfigKey<>("EXPERIMENTS_GUARDIAN_TAB_SUFFIX","");
    public static final ConfigKey<String> EXPERIMENTS_GUARDIAN_MENU_TITLE=new ConfigKey<>("EXPERIMENTS_GUARDIAN_MENU_TITLE","");
    public static final ConfigKey<String> EXPERIMENTS_GUARDIAN_COMMAND=new ConfigKey<>("EXPERIMENTS_GUARDIAN_COMMAND","");
    public static final ConfigKey<String> EXPERIMENTS_GUARDIAN_PET_NAME=new ConfigKey<>("EXPERIMENTS_GUARDIAN_PET_NAME","");
    public static final ConfigKey<String> EXPERIMENTS_GUARDIAN_INACTIVE_LORE=new ConfigKey<>("EXPERIMENTS_GUARDIAN_INACTIVE_LORE","");
    public static final ConfigKey<String> EXPERIMENTS_GUARDIAN_ACTIVE_LORE=new ConfigKey<>("EXPERIMENTS_GUARDIAN_ACTIVE_LORE","");
    public static final ConfigKey<String> EXPERIMENTS_GUARDIAN_INACTIVE_ITEM_ID=new ConfigKey<>("EXPERIMENTS_GUARDIAN_INACTIVE_ITEM_ID","");
    public static final ConfigKey<String> EXPERIMENTS_GUARDIAN_ACTIVE_ITEM_ID=new ConfigKey<>("EXPERIMENTS_GUARDIAN_ACTIVE_ITEM_ID","");
    public static final ConfigKey<String> EXPERIMENTS_GUARDIAN_NEXT_PAGE_NAME=new ConfigKey<>("EXPERIMENTS_GUARDIAN_NEXT_PAGE_NAME","");
    public static final ConfigKey<String> EXPERIMENTS_GUARDIAN_NEXT_PAGE_ITEM_ID=new ConfigKey<>("EXPERIMENTS_GUARDIAN_NEXT_PAGE_ITEM_ID","");
    public static final ConfigKey<Integer> EXPERIMENTS_GUARDIAN_DELAY_TICKS=ConfigKey.ofInt("EXPERIMENTS_GUARDIAN_DELAY_TICKS",6);
    public static final ConfigKey<Integer> EXPERIMENTS_GUARDIAN_TIMEOUT_TICKS=ConfigKey.ofInt("EXPERIMENTS_GUARDIAN_TIMEOUT_TICKS",100);
    public static final ConfigKey<Integer> EXPERIMENTS_GUARDIAN_MAX_PAGES=ConfigKey.ofInt("EXPERIMENTS_GUARDIAN_MAX_PAGES",10);
    public static ExperimentsGuardianSettings settings(ConfigService c) {
        return new ExperimentsGuardianSettings(c.get(EXPERIMENTS_GUARDIAN_ENABLED),c.get(EXPERIMENTS_GUARDIAN_TAB_PREFIX),c.get(EXPERIMENTS_GUARDIAN_TAB_SUFFIX),
                c.get(EXPERIMENTS_GUARDIAN_MENU_TITLE),c.get(EXPERIMENTS_GUARDIAN_COMMAND),c.get(EXPERIMENTS_GUARDIAN_PET_NAME),
                c.get(EXPERIMENTS_GUARDIAN_INACTIVE_LORE),c.get(EXPERIMENTS_GUARDIAN_ACTIVE_LORE),c.get(EXPERIMENTS_GUARDIAN_INACTIVE_ITEM_ID),
                c.get(EXPERIMENTS_GUARDIAN_ACTIVE_ITEM_ID),c.get(EXPERIMENTS_GUARDIAN_NEXT_PAGE_NAME),c.get(EXPERIMENTS_GUARDIAN_NEXT_PAGE_ITEM_ID),
                c.get(EXPERIMENTS_GUARDIAN_DELAY_TICKS),c.get(EXPERIMENTS_GUARDIAN_TIMEOUT_TICKS),c.get(EXPERIMENTS_GUARDIAN_MAX_PAGES));
    }
}
