package dev.veltraph.client.navigation;
/** Final observation only; it cannot issue keys, create native receipts or grant scope. */
public final class LegacyCleanupKeyLeafIdentity {
 private LegacyCleanupKeyLeafIdentity(){}
 public static boolean current(Object expectedOptions,Object actualOptions,Object[] expected,Object[] actual,boolean[] down){
  if(expectedOptions==null||expectedOptions!=actualOptions||expected==null||actual==null||down==null||expected.length!=9||actual.length!=9||down.length!=9)return false;
  for(int i=0;i<9;i++)if(expected[i]==null||expected[i]!=actual[i]||down[i])return false;
  return true;
 }
}
