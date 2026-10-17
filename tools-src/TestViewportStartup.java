import java.awt.*;
import java.awt.image.BufferedImage;
public class TestViewportStartup {
 public static void main(String[] args) throws Exception {
  net.runelite.awt.AwtCompat.ensureReady();
  Frame f=new Frame(); f.setLayout(null); f.setBounds(2373,400,80,60); f.setVisible(true);
  Canvas canvas=new Canvas(); canvas.setBounds(7,9,20,15); f.add(canvas);
  Graphics g=canvas.getGraphics(); g.setColor(Color.RED); g.fillRect(0,0,20,15); g.dispose();
  BufferedImage result=f.renderToBackbuffer();
  if(result.getRGB(10,12)!=0xffff0000) throw new AssertionError("Canvas hidden by desktop window origin");
  if(!canvas.getBoundsInWindow().equals(new Rectangle(7,9,20,15))) throw new AssertionError("Canvas coordinate mismatch");
  f.setLocation(-1200,-300);
  if(f.renderToBackbuffer().getRGB(10,12)!=0xffff0000) throw new AssertionError("Negative desktop origin hides canvas");
  Canvas.setRenderedByGles(true);
  if((f.renderToBackbuffer().getRGB(10,12) >>> 24)!=0) throw new AssertionError("GPU canvas covered by opaque chrome");
  Canvas.setRenderedByGles(false);
  Class<?> input=Class.forName("net.runelite.mp.AppAndroidKt");
  java.lang.reflect.Method hit=input.getDeclaredMethod("hitTest",Window.class,int.class,int.class); hit.setAccessible(true);
  java.lang.reflect.Method exact=input.getDeclaredMethod("hitTestExact",Window.class,Component.class); exact.setAccessible(true);
  java.lang.reflect.Method local=input.getDeclaredMethod("localCoords",Component.class,int.class,int.class); local.setAccessible(true);
  for(int origin:new int[]{2373,-1200,0}) {
   f.setLocation(origin,400);
   Object h=hit.invoke(null,f,10,12);
   if(h==null) throw new AssertionError("Hit missed at window origin "+origin);
   java.lang.reflect.Field comp=h.getClass().getDeclaredField("comp"); comp.setAccessible(true);
   if(comp.get(h)!=canvas) throw new AssertionError("Wrong hit target");
   Object e=exact.invoke(null,f,canvas);
   for(String axis:new String[]{"absX","absY"}) { java.lang.reflect.Field pos=e.getClass().getDeclaredField(axis);pos.setAccessible(true);if(pos.getInt(e)!=(axis.equals("absX")?7:9))throw new AssertionError("Wrong component origin"); }
   kotlin.Pair<?,?> xy=(kotlin.Pair<?,?>)local.invoke(null,canvas,10,12);
   if(!xy.getFirst().equals(3)||!xy.getSecond().equals(3))throw new AssertionError("Wrong local click coordinates");
  }
  System.out.println("PASS: desktop origins, canvas placement, hit testing, local click coordinates");
  System.exit(0);
 }
}
