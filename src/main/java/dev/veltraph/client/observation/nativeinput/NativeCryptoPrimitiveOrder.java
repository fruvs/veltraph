package dev.veltraph.client.observation.nativeinput;
import java.util.Arrays;
/** Data receipts used by authenticated WireSource crypto hooks, never a public output enrollment API. */
final class NativeCryptoPrimitiveOrder {
 static final int MAX=8388608;
 static byte[] slice(byte[] array,int start,int count){return array==null||start<0||count<0||count>MAX||start>array.length-count?null:Arrays.copyOfRange(array,start,start+count);}
 static final class CipherUpdate {
  final Object owner,receiver;final byte[] input,output;final int inputOffset,inputCount,outputOffset;private final byte[] expected;
  private byte[] produced;private boolean refused,returned,taken;
  CipherUpdate(Object owner,Object receiver,byte[] expected,byte[] input,int offset,int count,byte[] output,int outputOffset){this.owner=owner;this.receiver=receiver;this.input=input;this.inputOffset=offset;this.inputCount=count;this.output=output;this.outputOffset=outputOffset;this.expected=expected==null?null:expected.clone();if(owner==null||receiver==null||expected==null||expected.length<=0||expected.length>MAX||input==output||count!=expected.length||!Arrays.equals(expected,slice(input,offset,count))||outputOffset<0||output==null||outputOffset>output.length-count)refused=true;}
  boolean viable(){return !refused&&!returned;}
  void returned(int actualCount,boolean normal){if(returned||!normal||refused||actualCount!=inputCount||!Arrays.equals(expected,slice(input,inputOffset,inputCount))){refused=true;return;}produced=slice(output,outputOffset,actualCount);if(produced==null){refused=true;return;}returned=true;}
  void refuse(){refused=true;}
  byte[] finish(Object originalOwner,boolean normal){if(taken||refused||!normal||!returned||owner!=originalOwner||!Arrays.equals(expected,slice(input,inputOffset,inputCount))){refused=true;return null;}taken=true;return produced.clone();}
 }
 static final class Compressed {
  final int decodedSize;private final byte[] payload;
  Compressed(int n,byte[] p){decodedSize=n;payload=p.clone();}byte[] payload(){return payload.clone();}
 }
 static Compressed compressed(byte[] frame){if(frame==null||frame.length<=1||frame.length>MAX)return null;int value=0;for(int i=0;i<5&&i<frame.length;i++){int b=frame[i]&255;value|=(b&127)<<(7*i);if((b&128)==0){if(value<=0||value>MAX||i+1>=frame.length)return null;return new Compressed(value,Arrays.copyOfRange(frame,i+1,frame.length));}}return null;}
 static final class Inflate {
  final Object owner,receiver,input;final int inputStart;final Compressed expected;
  private boolean setStarted,setNormal,inflating,returned,refused,taken;private Object output;private int outputStart;private byte[] produced;
  Inflate(Object owner,Object receiver,Object input,int inputStart,Compressed expected){this.owner=owner;this.receiver=receiver;this.input=input;this.inputStart=inputStart;this.expected=expected;if(owner==null||receiver==null||input==null||inputStart<0||expected==null)refused=true;}
  boolean beforeSet(Object originalReceiver,Object originalInput,int position,byte[] observed){if(setStarted||refused||receiver!=originalReceiver||input!=originalInput||position!=inputStart||!Arrays.equals(expected.payload,observed)){refused=true;return false;}setStarted=true;return true;}
  void afterSet(Object originalReceiver,Object originalInput,int position,byte[] observed,boolean normal){if(!setStarted||setNormal||!normal||receiver!=originalReceiver||input!=originalInput||position!=inputStart||!Arrays.equals(expected.payload,observed)){refused=true;return;}setNormal=true;}
  boolean beforeInflate(Object originalOwner,Object originalReceiver,Object actualOutput,int position,int remaining,byte[] observedInput){if(refused||!setNormal||inflating||originalOwner!=owner||receiver!=originalReceiver||actualOutput==null||actualOutput==input||position<0||remaining!=expected.decodedSize||!Arrays.equals(expected.payload,observedInput)){refused=true;return false;}inflating=true;output=actualOutput;outputStart=position;return true;}
  void afterInflate(Object originalOutput,int position,int count,byte[] observedInput,byte[] observedOutput,boolean normal){if(refused||!inflating||returned||!normal||output!=originalOutput||count!=expected.decodedSize||position!=outputStart+count||!Arrays.equals(expected.payload,observedInput)||observedOutput==null||observedOutput.length!=count){refused=true;return;}produced=observedOutput.clone();returned=true;}
  void refuse(){refused=true;}
  byte[] finish(Object originalOwner,boolean normal,byte[] observedInput){if(taken||refused||!returned||!normal||owner!=originalOwner||!Arrays.equals(expected.payload,observedInput)){refused=true;return null;}taken=true;return produced.clone();}
 }
}
