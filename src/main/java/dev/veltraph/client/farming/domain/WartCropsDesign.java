package dev.veltraph.client.farming.domain;

/** Explicit design choice; the private modified capture remains on the classical row engine. */
public enum WartCropsDesign {
    CLASSICAL("Classical rows"),
    ACE_TWO_KEY_LOOP("Ace two-key wheat loop");
    private final String label;
    WartCropsDesign(String label){this.label=label;}
    @Override public String toString(){return label;}
}
