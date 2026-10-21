package dev.veltraph.client.navigation;

/** Identity/timing protocol only. Native source, real SDK receipts and tickets are independently validated by its private issuer. */
public final class IssuedReturnDeviceQueueState {
    private final Object owner;private final long beforeGeneration,beforeEpoch,beforeSerial,start,deadline;
    private Object receipt;private long generation,epoch,serial,ticket,lastNow=-1;private int refreshes;
    private boolean attached,observed,delivered,revoked;
    public IssuedReturnDeviceQueueState(Object owner,long generation,long epoch,long serial,long start,long deadline){
        this.owner=owner;beforeGeneration=generation;beforeEpoch=epoch;beforeSerial=serial;this.start=start;this.deadline=deadline;
    }
    private boolean live(Object exact,long now){return !revoked&&owner!=null&&exact==owner&&start>=0&&deadline>start&&now>=start&&now>=lastNow&&now<deadline;}
    public boolean attach(Object exact,Object actualReceipt,long actualGeneration,long actualEpoch,long actualSerial,long now){
        if(attached||actualReceipt==null||!live(exact,now)||beforeGeneration==Long.MAX_VALUE
            ||actualGeneration!=beforeGeneration+1||actualEpoch!=beforeEpoch||actualSerial!=beforeSerial)return false;
        receipt=actualReceipt;generation=actualGeneration;epoch=actualEpoch;serial=actualSerial;lastNow=now;attached=true;return true;
    }
    public boolean refresh(Object exact,Object expected,Object actual,long gen,long ep,long ser,long ownTicket,long now){
        if(!attached||actual==null||expected!=receipt||!live(exact,now)||gen!=generation)return false;
        boolean pending=ownTicket<=0&&ep==epoch&&ser==serial&&ticket==0;
        boolean same=ownTicket>0&&ownTicket==ticket&&ep==epoch&&ser==serial;
        boolean advanced=ownTicket>0&&ownTicket==ser&&serial<Long.MAX_VALUE&&ser==serial+1&&ep>=epoch&&ep-epoch<=1;
        if(!pending&&!same&&!advanced)return false;
        receipt=actual;epoch=ep;serial=ser;if(ownTicket>0)ticket=ownTicket;refreshes++;lastNow=now;return true;
    }
    public boolean observedCompletion(Object exact,long now){if(!attached||observed||delivered||!live(exact,now))return false;observed=true;lastNow=now;return true;}
    public boolean deliver(Object exact,Object actualReceipt,long actualTicket,long now){
        if(!attached||!observed||delivered||actualReceipt!=receipt||ticket<=0||actualTicket!=ticket||!live(exact,now))return false;
        delivered=true;lastNow=now;return true;
    }
    public void revoke(){revoked=true;}
    public Object receiptIdentity(){return receipt;}public long generation(){return generation;}public long ticket(){return ticket;}
    public int refreshCount(){return refreshes;}public boolean completionObserved(){return observed;}public boolean completionDelivered(){return delivered;}
}
