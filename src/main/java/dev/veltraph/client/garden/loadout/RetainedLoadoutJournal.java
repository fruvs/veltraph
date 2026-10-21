package dev.veltraph.client.garden.loadout;

import java.util.*;

/** Deterministic refusal accounting only. This public model cannot mint native loadout authority. */
public final class RetainedLoadoutJournal {
    public enum Effect { COMMAND, CLICK, CLOSE }
    private final List<String> before; private final String ancillary;
    private Effect inFlight; private int commands,clicks,closes; private boolean refused;
    private boolean opened,menuApplied,gearApplied,inventoryApplied,ancillaryApplied;
    private int menuId=-1,issuedRevision=-1; private long issuedSerial=-1;
    public RetainedLoadoutJournal(List<String> before,String ancillary){
        if(before==null||before.size()!=36||before.stream().anyMatch(Objects::isNull)||ancillary==null||ancillary.isBlank())throw new IllegalArgumentException("Exact full inventory and explicit native ancillary evidence required");
        this.before=List.copyOf(before);this.ancillary=ancillary;
    }
    public boolean begin(Effect effect,boolean nativeCurrent,int menu,int revision,long serial){
        if(refused||inFlight!=null||effect==null||!nativeCurrent){refuse();return false;}
        boolean allowed=switch(effect){case COMMAND->commands==0&&clicks==0&&closes==0&&menu==-1;
            case CLICK->commands==1&&opened&&clicks==0&&closes==0&&menu==menuId&&revision>=0&&serial>=0;
            case CLOSE->commands==1&&clicks==1&&closes==0&&completeApplied()&&menu==menuId;};
        if(!allowed){refuse();return false;}inFlight=effect;
        if(effect==Effect.CLICK){issuedRevision=revision;issuedSerial=serial;}return true;
    }
    public void submitted(Effect effect,boolean matching,boolean normal){
        if(refused||inFlight!=effect||!matching||!normal){refuse();return;}
        switch(effect){case COMMAND->commands++;case CLICK->clicks++;case CLOSE->closes++;}
    }
    public void returned(Effect effect,boolean normal,boolean nativeCurrent){
        if(refused||inFlight!=effect||!normal||!nativeCurrent){refuse();return;}
        int count=switch(effect){case COMMAND->commands;case CLICK->clicks;case CLOSE->closes;};
        if(count!=1){refuse();return;}inFlight=null;
    }
    public void opened(int menu,boolean normal,boolean current){
        if(refused||commands!=1||opened||!normal||!current||menu<0){refuse();return;}opened=true;menuId=menu;
    }
    public void appliedMenu(int menu,int revision,long serial,boolean matchingActiveRow,boolean emptyCursor,boolean normal,boolean current){
        if(refused||clicks!=1||menu!=menuId||revision<=issuedRevision||serial<=issuedSerial||!emptyCursor||!normal||!current){refuse();return;}
        if(matchingActiveRow)menuApplied=true;
    }
    public void appliedGear(long serial,boolean matching,boolean normal,boolean current){
        if(refused||clicks!=1||serial<=issuedSerial||!normal||!current){refuse();return;}if(matching)gearApplied=true;
    }
    public void appliedInventory(long serial,List<String> payload,List<String> actual,boolean emptyCursor,boolean normal,boolean current){
        if(refused||clicks!=1||serial<=issuedSerial||!normal||!current||!emptyCursor||!before.equals(payload)||!before.equals(actual)){refuse();return;}inventoryApplied=true;
    }
    public void appliedAncillary(long serial,String packetValue,boolean normal,boolean current){
        if(refused||clicks!=1||serial<=issuedSerial||!normal||!current||!ancillary.equals(packetValue)){refuse();return;}ancillaryApplied=true;
    }
    public boolean completeApplied(){return !refused&&menuApplied&&gearApplied&&inventoryApplied&&ancillaryApplied;}
    public boolean complete(boolean closed,boolean nativeCurrent){return !refused&&inFlight==null&&commands==1&&clicks==1&&closes==1&&completeApplied()&&closed&&nativeCurrent;}
    public void refuse(){refused=true;inFlight=null;}
    public boolean refused(){return refused;}
}
