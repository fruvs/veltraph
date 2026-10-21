package dev.veltraph.client.combat.slayer;

/** Quest quotes are denominated; a Motes price cannot borrow a Coins authorization. */
public enum QuestCurrency {
    COINS("Coins"),MOTES("Motes");
    private final String label;
    QuestCurrency(String label){this.label=label;}
    public String label(){return label;}
    public static QuestCurrency parse(String text){for(var value:values())if(value.label.equalsIgnoreCase(text))return value;return null;}
}
