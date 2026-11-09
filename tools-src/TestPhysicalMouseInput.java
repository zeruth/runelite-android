package net.runelite.mp.mouseprobe;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.awt.Canvas;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Instrumentation for the isolated mouse-test APK. Exercises Android window dispatch, both UI receivers and their AWT listeners. */
public class TestPhysicalMouseInput extends Instrumentation {
    private final List<String> events = Collections.synchronizedList(new ArrayList<>());
    private final MouseAdapter listener = new MouseAdapter() {
        public void mousePressed(MouseEvent e) { events.add("+" + e.getButton()); }
        public void mouseReleased(MouseEvent e) { events.add("-" + e.getButton()); }
        public void mouseClicked(MouseEvent e) { events.add("c" + e.getButton()); }
        public void mouseDragged(MouseEvent e) { events.add("d" + e.getModifiersEx()); }
    };
    private Activity activity;
    private View receiver;
    private long down;
    private Method setActionButton;
    private final StringBuilder report = new StringBuilder();
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context ctx = getTargetContext();
            activity = startActivitySync(new Intent().setClassName(ctx,"net.runelite.mp.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Class<?> surface = ctx.getClassLoader().loadClass("net.runelite.mp.input.PhysicalMouseView");
            for (int i=0; i<100 && receiver==null; i++) {
                runOnMainSync(() -> receiver = find(activity.getWindow().getDecorView(),surface));
                if (receiver==null) Thread.sleep(100);
            }
            if (receiver==null) throw new AssertionError("Game mouse receiver unavailable");
            Frame frame = new Frame();
            Canvas canvas = new Canvas();
            EventQueue.invokeAndWait(() -> {
                frame.setSize(765,503); frame.add(canvas); canvas.setSize(765,503);
                canvas.addMouseListener(listener); canvas.addMouseMotionListener(listener);
                canvas.addMouseWheelListener(e -> events.add("wheel"+e.getWheelRotation()));
                frame.setVisible(true);
            });
            setActionButton=MotionEvent.class.getDeclaredMethod("setActionButton",int.class);
            setActionButton.setAccessible(true);
            verifySurface("game");
            event(MotionEvent.ACTION_SCROLL,0,0,0);
            expect("game wheel","wheel-1");
            JPanel panel = new JPanel();
            panel.addMouseListener(listener); panel.addMouseMotionListener(listener);
            Class<?> host = ctx.getClassLoader().loadClass("net.runelite.mp.ui.panels.SwingPanelView");
            View sidebar=(View)host.getConstructor(Context.class,String.class,JComponent.class).newInstance(activity,"mouse fixture",panel);
            runOnMainSync(() -> activity.addContentView(sidebar,new FrameLayout.LayoutParams(-1,-1)));
            receiver=sidebar;
            Thread.sleep(450);
            verifySurface("plugin panel");
            result.putString("stream",report.toString());
            finish(-1,result);
        } catch(Throwable t) {
            result.putString("stream",report + android.util.Log.getStackTraceString(t)); finish(0,result);
        }
    }
    private static View find(View v,Class<?> type) {
        if(type.isInstance(v) && v.getWidth()>0) return v;
        if(v instanceof ViewGroup) for(int i=0;i<((ViewGroup)v).getChildCount();i++){
            View child=find(((ViewGroup)v).getChildAt(i),type); if(child!=null)return child;
        }
        return null;
    }
    private void event(int action,int state,int changed,int dx) {
        event(action,state,changed,dx,InputDevice.SOURCE_MOUSE);
    }
    private void event(int action,int state,int changed,int dx,int source) {
        runOnMainSync(() -> {
            int[] pos=new int[2]; receiver.getLocationInWindow(pos);
            MotionEvent.PointerProperties prop=new MotionEvent.PointerProperties(); prop.id=0;
            prop.toolType=source==InputDevice.SOURCE_MOUSE ? MotionEvent.TOOL_TYPE_MOUSE : MotionEvent.TOOL_TYPE_FINGER;
            MotionEvent.PointerCoords coords=new MotionEvent.PointerCoords();
            coords.x=pos[0]+receiver.getWidth()/2f+dx; coords.y=pos[1]+receiver.getHeight()/2f; coords.pressure=1;
            if(action==MotionEvent.ACTION_SCROLL) coords.setAxisValue(MotionEvent.AXIS_VSCROLL,1);
            long now=SystemClock.uptimeMillis();
            if(action==MotionEvent.ACTION_DOWN || down==0) down=now;
            MotionEvent e=MotionEvent.obtain(down,now,action,1,new MotionEvent.PointerProperties[]{prop},new MotionEvent.PointerCoords[]{coords},0,state,1,1,7,0,source,0);
            try {
                if(changed!=0)setActionButton.invoke(e,changed);
                if(action==11 || action==12 || action==MotionEvent.ACTION_SCROLL) activity.dispatchGenericMotionEvent(e);
                else activity.dispatchTouchEvent(e);
            } catch(Exception ex) { throw new RuntimeException(ex); }
            finally { e.recycle(); }
        });
    }
    private void expect(String label,String... expected) throws Exception {
        EventQueue.invokeAndWait(()->{});
        synchronized(events){
            if(!events.equals(Arrays.asList(expected)))throw new AssertionError(label+" expected "+Arrays.toString(expected)+" got "+events);
            events.clear();
        }
        report.append("PASS ").append(label).append('\n');
    }
    private void verifySurface(String name) throws Exception {
        event(0,1,0,0); event(1,0,0,0);
        expect(name+" ordinary left","+1","-1","c1");
        event(0,2,0,0); event(11,2,2,0); event(12,0,2,0); event(1,0,0,0);
        expect(name+" duplicate right","+3","-3","c3");
        event(0,0,0,0); event(11,0,2,0); event(12,0,2,0); event(1,0,0,0);
        expect(name+" separately identified right","+3","-3","c3");
        event(11,0,4,0); event(12,0,4,0);
        expect(name+" generic-only middle","+2","-2","c2");
        event(0,4,0,0); event(11,4,4,0); event(2,0,0,64); event(12,0,4,64); event(1,0,0,64);
        expect(name+" held middle drag","+2","d2048","-2");
        event(0,4,0,0); event(3,0,0,0);
        expect(name+" cancel","+2","-2");
        event(0,0,0,0); event(1,0,0,0);
        expect(name+" unidentified left tap","+1","-1","c1");
        event(0,4,0,0); event(11,4,4,0); event(11,6,2,0);
        event(12,4,2,0); event(12,0,4,0); event(1,0,0,0);
        expect(name+" right while holding middle","+2","+3","-3","c3","-2","c2");
        event(0,0,0,0,InputDevice.SOURCE_TOUCHSCREEN); event(1,0,0,0,InputDevice.SOURCE_TOUCHSCREEN);
        expect(name+" finger tap","+1","-1","c1");
    }
}
