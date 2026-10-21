package dev.veltraph.client.chocolate;
/** Deliberate non-queue action intent/result; no Coin conversion or guessed spending is stored. */
public record ChocolateActionJournal(int schema,String id,String scope,String action,String status,String phase,String evidence,String reason,String result) {
    public ChocolateActionJournal {if(schema!=1||id==null||id.isBlank()||scope==null||scope.isBlank()||!java.util.Set.of("TOWER","PRESTIGE","SELECTED_SHOP","SELECTED_HITMAN","SELECTED_MILESTONE").contains(action)||!java.util.Set.of("PENDING","UNKNOWN","ACKNOWLEDGED_UNPERSISTED","PREVIEW_READY","ACKNOWLEDGED","STOPPED","DENIED","RECONCILED").contains(status)||phase==null||evidence==null||reason==null||result==null)throw new IllegalArgumentException("Malformed consequential Factory journal");}
    public boolean pending(){return java.util.Set.of("PENDING","UNKNOWN","ACKNOWLEDGED_UNPERSISTED").contains(status);}
}
