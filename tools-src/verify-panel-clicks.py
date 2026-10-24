"""Replay click sequences independently of Android and rendering speed."""
from pathlib import Path
import os, subprocess
root = Path('build/verify-panel-clicks')
root.mkdir(parents=True, exist_ok=True)
source = root / 'VerifyPanelClicks.java'
source.write_text(r'''import net.runelite.mp.input.PanelClickSequence;
public class VerifyPanelClicks {
 static final Object A=new Object(), B=new Object();
 static PanelClickSequence c=new PanelClickSequence();
 static void click(int expected,Object target,long press,long release,double x,int device,int tool) {
  int actual=c.click(target,press,release,x,0,device,tool,300,8);
  if(actual!=expected)throw new AssertionError("at "+press+": expected "+expected+" got "+actual);
 }
 public static void main(String[] args) {
  click(1,A,100,120,0,1,3); click(2,A,210,230,3,1,3);
  click(1,A,260,280,3,1,3); // triple click cannot hop again
  c.reset(); click(1,A,100,120,0,1,3); click(1,B,200,220,0,1,3);
  c.reset(); click(1,A,100,120,0,1,3); click(1,A,200,220,9,1,3);
  c.reset(); click(1,A,100,120,0,1,3); click(1,A,421,441,0,1,3);
  c.reset(); click(1,A,100,120,0,1,3); click(2,A,420,440,8,1,3);
  c.reset(); click(1,A,100,120,0,1,3); click(1,A,200,220,0,2,3);
  c.reset(); click(1,A,100,120,0,1,3); click(1,A,200,220,0,1,1);
  c.reset(); click(1,A,100,120,0,1,3); c.reset(); click(1,A,200,220,0,1,3);
  c.reset(); click(1,A,100,800,0,1,3); click(1,A,900,920,0,1,3);
  c.reset(); click(1,A,100,120,0,1,3); click(1,A,200,800,0,1,3);
  c.reset(); click(1,A,100,120,0,1,3); click(1,A,90,110,0,1,3);
  System.out.println("PASS: pairing, triple clicks, targets, distance, timing boundaries, devices, tools, cancellation and holds");
 }
}''',encoding='utf-8')
java=Path(os.environ['JAVA_HOME'])/'bin'
subprocess.run([str(java/'javac.exe'),'-d',str(root),'android/src/androidMain/java/net/runelite/mp/input/PanelClickSequence.java',str(source)],check=True)
subprocess.run([str(java/'java.exe'),'-cp',str(root),'VerifyPanelClicks'],check=True)
