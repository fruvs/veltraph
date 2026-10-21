package dev.veltraph.client.rotation;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Source contract on the actual projected Bootstrap initializer; no fake provider/registry constructor or native pumping. */
class NormalIdleBootstrapInitializationOrderTest {
 @Test void actualRegistryAssignmentPrecedesAuthenticProviderConstructionAndInstallation() throws Exception {
  var source=Files.readString(Path.of("src/main/java/dev/veltraph/client/VeltraphBootstrap.java"));
  int registry=source.indexOf("moduleManager = new ModuleManager();");
  int construct=source.indexOf("rootRotationProvider=new RootRotationAuthorityProvider(client,moduleManager,config);");
  int install=source.indexOf("McOwnedRotation.installRoot(client,rootRotationProvider);");
  assertTrue(registry>=0&&construct>registry&&install>construct,"The actual provider must capture the assigned registry, not startup null");
  assertTrue(source.substring(registry,construct).replace("moduleManager = new ModuleManager();","").isBlank(),"Provider must be created immediately after actual assignment");
 }
 @Test void servicesAndRewarpInitializationRemainBeforeActualRegistryAndFirstModuleRegistration() throws Exception {
  var s=Files.readString(Path.of("src/main/java/dev/veltraph/client/VeltraphBootstrap.java"));
  int service=s.indexOf("FarmServices services = createOriginalFarmServices(client, clock, config);");int store=s.indexOf("farmServices = services;");
  int capture=s.indexOf("RewarpCapture capture = new RewarpCapture(client, services.player(), gameContext);");
  int command=s.indexOf("rewarpCommand = new RewarpCommandHandler(");int registry=s.indexOf("moduleManager = new ModuleManager();");
  int install=s.indexOf("McOwnedRotation.installRoot(client,rootRotationProvider);");int first=s.indexOf("moduleManager.register(");
  assertTrue(service>=0&&service<store&&store<capture&&capture<command&&command<registry&&registry<install&&install<first);
  assertEquals(first,s.indexOf("moduleManager.register(savedAnchorRecovery);"),"The named original first module must also be the actual first registration");
  var wrapper=methodBody(s,"private static FarmServices createOriginalFarmServices(");
  assertEquals(1,occurrences(wrapper,"McFarmServices.create("),"The wrapper must invoke the original concrete factory exactly once");
  int began=wrapper.indexOf("beginOriginalStartupConstruction(true)");
  int pending=wrapper.indexOf("boolean normal=false;");
  int invoke=wrapper.indexOf("var services=McFarmServices.create(client,clock,store);");
  int normal=wrapper.indexOf("normal=true;",invoke);
  int result=wrapper.indexOf("return services;",normal);
  int finished=wrapper.indexOf("finishOriginalStartupConstruction(actual,normal)",result);
  assertTrue(began>=0&&began<pending&&pending<invoke&&invoke<normal&&normal<result&&result<finished,
      "The original constructed services must be returned only after normal factory completion; cleanup retains the original normal flag");
  assertTrue(wrapper.substring(pending,invoke).contains("try{"),"The real factory call stays in the original construction try body");
  assertTrue(wrapper.substring(result,finished).contains("finally{"),"Exceptional original construction still reaches the retained cleanup finally");
 }
 private static int occurrences(String source,String token){
  int count=0,start=0,index;while((index=source.indexOf(token,start))>=0){count++;start=index+token.length();}return count;
 }
 private static String methodBody(String source,String signature){
  int start=source.indexOf(signature);assertTrue(start>=0,"The genuine original service wrapper must be present");
  int opened=source.indexOf('{',start);assertTrue(opened>=0,"Wrapper body must be defined");
  int depth=1,end=opened+1;
  while(depth>0&&end<source.length()){char c=source.charAt(end++);if(c=='{')depth++;else if(c=='}')depth--;}
  assertEquals(0,depth,"Wrapper body must have its actual closed boundary");return source.substring(opened,end);
 }
}
