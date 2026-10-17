import java.lang.reflect.*;
public class TestSailingLambdaAccess {
 public static void main(String[] args) throws Exception {
  Class<?> unsafeType=Class.forName("sun.misc.Unsafe");
  Field field=unsafeType.getDeclaredField("theUnsafe"); field.setAccessible(true);
  Object unsafe=field.get(null);
  Object node=unsafeType.getMethod("allocateInstance",Class.class).invoke(unsafe,Class.forName("se"));
  Object lambda=Class.forName("runelite.desugar.Lambda6").getConstructor().newInstance();
  try {
   Object result=lambda.getClass().getMethod("apply",Object.class).invoke(lambda,node);
   if(result!=null) throw new AssertionError("Expected the node's null world-view field");
  } catch(InvocationTargetException e) { throw new AssertionError("World-view iterator lambda failed",e.getCause()); }
  System.out.println("PASS: real client world-view lambda executes across package boundaries");
 }
}
