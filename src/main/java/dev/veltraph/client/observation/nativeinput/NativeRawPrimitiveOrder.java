package dev.veltraph.client.observation.nativeinput;
import java.util.ArrayList;
/** Internal data ordering used only by authenticated WireSource primitive hooks. No enrollment API. */
final class NativeRawPrimitiveOrder {
 static final int MAX_BYTES=8*1024*1024, MAX_LOCAL=4096;
 static final class Received {
  final Object target; final int start,count; private final byte[] expected;
  Received(Object target,int start,byte[] bytes){this.target=target;this.start=start;this.count=bytes.length;expected=bytes.clone();}
  byte[] bytes(){return expected.clone();}
 }
 static final class Read {
  final Object owner,target,primitive; final int start;
  private Object operation; private int count; private Received received;
  private boolean entered,returned,closed,refused,taken;
  Read(Object owner,Object target,Object primitive,int start){this.owner=owner;this.target=target;this.primitive=primitive;this.start=start;if(owner==null||target==null||primitive==null||start<0)refused=true;}
  Object enter(Object receiver,Object actualTarget){if(entered||receiver!=primitive||actualTarget!=target||refused){refused=true;return null;}entered=true;operation=new Object();return operation;}
  void returned(Object token,int bytes,byte[] expected,boolean normal){if(token==null||token!=operation||returned||!normal||bytes<=0||bytes>MAX_BYTES||expected==null||expected.length!=bytes){refused=true;return;}returned=true;count=bytes;received=new Received(target,start,expected);}
  void finish(int actualResult,boolean normal){if(closed||!normal||!entered||!returned||actualResult!=count)refused=true;closed=true;}
  void refuse(){refused=true;}
  Received take(Object actualOwner,Object actualTarget,int actualCount){if(taken||refused||!closed||!returned||owner!=actualOwner||target!=actualTarget||count!=actualCount)return null;taken=true;return received;}
 }
 static final class LocalWrite {
  final Object writer,peer,queue,outbound;
  private Object current; private boolean retained,inserted,closed,refused;
  private final ArrayList<LocalInsert> inserts=new ArrayList<>();
  LocalWrite(Object writer,Object peer,Object queue,Object outbound){this.writer=writer;this.peer=peer;this.queue=queue;this.outbound=outbound;if(writer==null||peer==null||queue==null||outbound==null||writer==peer)refused=true;}
  void current(Object originalOutbound,Object result){if(originalOutbound!=outbound||closed||current!=null&&!inserted)refused=true;current=result;retained=false;inserted=false;}
  void retained(Object original,Object result){if(closed||refused||current==null||original!=current||result!=current||retained){refused=true;return;}retained=true;}
  LocalInsert prepare(Object originalQueue,Object original,Object contents,int start,byte[] expected){
   if(closed||refused||!retained||inserted||queue!=originalQueue||original!=current||contents==null||start<0||expected==null||expected.length<=0||expected.length>MAX_BYTES||inserts.size()>=MAX_LOCAL){refused=true;return null;}
   inserted=true;var item=new LocalInsert(this,original,new Received(contents,start,expected));inserts.add(item);return item;
  }
  LocalInsert insert(Object originalQueue,Object original,Object contents,int start,byte[] expected,boolean added){
   if(!added){refused=true;return null;}
   var item=prepare(originalQueue,original,contents,start,expected);if(item!=null)item.additionReturned(true,true);return item;
  }
  void finish(boolean normal){if(closed||!normal||current!=null&&!inserted)refused=true;for(var item:inserts)if(!item.additionComplete())refused=true;closed=true;}
  void refuse(){refused=true;}
  boolean viable(){return !refused;}
  boolean complete(){return closed&&!refused;}
 }
 static final class LocalInsert {
  private final LocalWrite write; final Object wrapper; private final Received bytes; private boolean claimed; private volatile boolean additionReturned,added,refused;
  LocalInsert(LocalWrite write,Object wrapper,Received bytes){this.write=write;this.wrapper=wrapper;this.bytes=bytes;}
  synchronized Received poll(Object actualPeer,Object actualQueue,Object actualWrapper){if(claimed||refused||!write.viable()||write.peer!=actualPeer||write.queue!=actualQueue||wrapper!=actualWrapper)return null;claimed=true;return bytes;}
  synchronized void additionReturned(boolean actualAdded,boolean normal){if(additionReturned||!normal||!actualAdded){refused=true;write.refuse();}else added=true;additionReturned=true;}
  boolean additionComplete(){return additionReturned&&added&&!refused;}
  boolean viable(){return !refused&&write.viable();} boolean complete(){return additionComplete()&&write.complete();}
 }
}
