import android.content.Context;
import android.view.KeyEvent;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import java.awt.EventQueue;
import java.awt.KeyboardFocusManager;
import java.awt.KeyEventDispatcher;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Synthetic IME requests through the production bridge; no real keyboard/account is used. */
public final class TestImeCompositionReplay {
    private interface Sequence { void run(InputConnection input, StringBuilder report); }
    private static void require(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }
    public static String run(Context context) throws Exception {
        require(android.os.Looper.myLooper()==android.os.Looper.getMainLooper(),"Replay must run on Android's UI thread");
        KeyboardFocusManager manager=KeyboardFocusManager.getCurrentKeyboardFocusManager();
        java.awt.Component previous=manager.getFocusOwner();
        EventQueue.invokeAndWait(manager::clearGlobalFocusOwner);
        try { return runGameReplay(context); }
        finally { EventQueue.invokeAndWait(()->{if(previous!=null)previous.requestFocusInWindow();}); }
    }
    private static String runGameReplay(Context context) throws Exception {
        Class<?> type=Class.forName("net.runelite.mp.ui.bridge.KeyboardInputView");
        Object view=type.getConstructor(Context.class).newInstance(context);
        Field open=type.getDeclaredField("isSessionOpen");open.setAccessible(true);
        Method create=type.getMethod("onCreateInputConnection",EditorInfo.class);
        KeyboardFocusManager manager=KeyboardFocusManager.getCurrentKeyboardFocusManager();
        StringBuilder emitted=new StringBuilder(),report=new StringBuilder();
        KeyEventDispatcher recorder=event->{
            if(event.getID()==java.awt.event.KeyEvent.KEY_TYPED){
                char c=event.getKeyChar();
                if(c=='\b'){if(emitted.length()>0)emitted.setLength(emitted.length()-1);}
                else emitted.append(c);
            }
            return true; // Consume synthetic events inside this separate diagnostic process.
        };
        String[] names={"plain-commits","normal-composition","restore-80","restore-4597",
            "hardware-digits-restore","finish-then-restore","legitimate-repeat","empty-region",
            "untracked-prefix-delete","delete-and-recompose","middle-selection-replace","delete-after-cursor",
            "codepoint-delete","finish-preserves-context","stale-region-rejected"};
        String[] expected={"80p","80p","80p","4597k","80p","80p","8080","80p",
            "ol","8p","90p","8p","80","80","80"};
        Sequence[] sequences={
            (input,out)->{input.commitText("8",1);input.commitText("0",1);input.commitText("p",1);},
            (input,out)->{input.setComposingText("8",1);input.setComposingText("80",1);input.setComposingText("80p",1);input.commitText("80p",1);},
            (input,out)->{
                input.commitText("80",1);
                out.append("  afterCommit beforeCursor='").append(input.getTextBeforeCursor(20,0)).append("'\n");
                boolean accepted=input.setComposingRegion(0,2);
                BaseInputConnection base=(BaseInputConnection)input;
                out.append("  setComposingRegion(0,2) accepted=").append(accepted)
                    .append(" editable='").append(base.getEditable()).append("' span=")
                    .append(BaseInputConnection.getComposingSpanStart(base.getEditable())).append(":")
                    .append(BaseInputConnection.getComposingSpanEnd(base.getEditable())).append("\n");
                input.setComposingText("80p",1);input.commitText("80p",1);
            },
            (input,out)->{input.commitText("4597",1);input.setComposingRegion(0,4);input.setComposingText("4597k",1);input.commitText("4597k",1);},
            (input,out)->{
                for(int code:new int[]{KeyEvent.KEYCODE_8,KeyEvent.KEYCODE_0}){
                    input.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,code));
                    input.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,code));
                }
                input.setComposingRegion(0,2);input.setComposingText("80p",1);input.commitText("80p",1);
            },
            (input,out)->{input.setComposingText("80",1);input.finishComposingText();input.setComposingRegion(0,2);input.setComposingText("80p",1);input.commitText("80p",1);},
            (input,out)->{input.commitText("80",1);input.commitText("80",1);},
            (input,out)->{input.commitText("80",1);input.setComposingRegion(2,2);input.setComposingText("p",1);input.commitText("p",1);},
            (input,out)->{emitted.append("old");input.deleteSurroundingText(1,0);},
            (input,out)->{input.commitText("80",1);input.deleteSurroundingText(1,0);input.setComposingRegion(0,1);input.setComposingText("8p",1);input.commitText("8p",1);},
            (input,out)->{input.commitText("80p",1);input.setSelection(0,2);input.commitText("90",1);},
            (input,out)->{input.commitText("80p",1);input.setSelection(1,1);input.deleteSurroundingText(0,1);},
            (input,out)->{input.commitText("80p",1);input.deleteSurroundingTextInCodePoints(1,0);},
            (input,out)->{input.setComposingText("80",1);input.finishComposingText();require("80".contentEquals(input.getTextBeforeCursor(20,0)),"Finished composition lost text context");},
            (input,out)->{input.commitText("80",1);input.closeConnection();require(!input.setComposingRegion(0,2),"Closed connection accepted a composing region");require(!input.setSelection(0,2),"Closed connection accepted a selection");}
        };
        int failed=0;
        for(String digits:new String[]{"80","4597"}){
            String desired=digits+(digits.equals("80")?"p":"k");
            BaseInputConnection reference=new BaseInputConnection(new android.view.View(context),true);
            reference.commitText(digits,1);reference.setComposingRegion(0,digits.length());
            reference.setComposingText(desired,1);reference.commitText(desired,1);
            boolean pass=desired.contentEquals(reference.getEditable());
            if(!pass)failed++;
            report.append(pass?"PASS ":"FAIL ").append("Android-reference-").append(desired)
                .append(" expected='").append(desired).append("' actual='").append(reference.getEditable()).append("'\n");
            reference.closeConnection();
        }
        manager.addKeyEventDispatcher(recorder);
        try {
            for(int i=0;i<names.length;i++){
                emitted.setLength(0);open.setBoolean(view,true);
                InputConnection input=(InputConnection)create.invoke(view,new EditorInfo());
                StringBuilder details=new StringBuilder();sequences[i].run(input,details);
                EventQueue.invokeAndWait(()->{}); // Wait for the production bridge's queued AWT events.
                boolean pass=expected[i].contentEquals(emitted);
                if(!pass)failed++;
                report.append(pass?"PASS ":"FAIL ").append(names[i]).append(" expected='")
                    .append(expected[i]).append("' actual='").append(emitted).append("'\n").append(details);
                input.closeConnection();
            }
        } finally {
            manager.removeKeyEventDispatcher(recorder);
            type.getMethod("endSession").invoke(view);
        }
        report.append("RESULT passed=").append(names.length+2-failed).append(" failed=").append(failed).append("\n");
        report.append("Synthetic API replay; Samsung's actual call sequence has not been captured.\n");
        return report.toString();
    }
}
