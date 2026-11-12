import javax.sound.sampled.*;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.awt.impl.AndroidAudioBridge;

/**
 * Standalone Android probe. The playback cases use silent PCM; the capture case records two seconds
 * from the microphone and plays them back. Does not change application settings.
 * Argument {@code capture} runs the capture case alone.
 */
public final class TestAudioLineContracts {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void capture()throws Exception{
        AudioFormat mic=new AudioFormat(16000f,16,1,true,false);
        check(AudioSystem.isLineSupported(new DataLine.Info(TargetDataLine.class,mic)),"mixer reports a target line for "+mic);
        check(AudioSystem.getTargetLineInfo(new Line.Info(TargetDataLine.class)).length>0,"target Line.Info listed");
        TargetDataLine in=AudioSystem.getTargetDataLine(mic);
        in.open(mic);
        check(in.isOpen()&&!in.isRunning(),"opened, not yet capturing");
        check(in.isControlSupported(FloatControl.Type.MASTER_GAIN)&&in.isControlSupported(BooleanControl.Type.MUTE),"capture controls");
        check(in.getBufferSize()==8000*mic.getFrameSize(),"default buffer is half a second: "+in.getBufferSize());
        check(in.available()==0,"nothing captured before start");
        in.start();
        byte[] recorded=new byte[2*16000*mic.getFrameSize()];
        int got=0;float peak=0f;long deadline=System.nanoTime()+6_000_000_000L;
        while(got<recorded.length&&System.nanoTime()<deadline){
            int ready=Math.min(in.available(),recorded.length-got);
            if(ready==0){Thread.sleep(10);continue;}
            got+=in.read(recorded,got,ready);peak=Math.max(peak,in.getLevel());
        }
        check(got==recorded.length,"captured "+got+" of "+recorded.length+" bytes in time");
        check(in.getLongFramePosition()>=got/mic.getFrameSize(),"frame position counts captured frames: "+in.getLongFramePosition());
        in.stop();
        check(!in.isRunning()&&in.getLevel()==0f,"stop ends capture");
        int left=in.available();
        check(in.read(new byte[left],0,left)==left,"data captured before stop stays readable");
        check(in.read(new byte[mic.getFrameSize()],0,mic.getFrameSize())==0,"a stopped, empty line does not block");
        in.flush();check(in.available()==0,"flush empties the buffer");
        in.close();check(!in.isOpen(),"closed");
        System.out.println("CAPTURE bytes="+got+" peak="+peak+" frames="+in.getLongFramePosition()+" (playing it back)");
        SourceDataLine out=AndroidAudioBridge.openSourceDataLine(mic,8192);
        out.open();out.start();out.write(recorded,0,got);out.drain();out.close();
        System.out.println("PASS: capture two seconds over AudioRecord and play them back");
    }
    static Object track(SourceDataLine line)throws Exception{
        Class<?> type=line.getClass();
        while(type!=null) { try { type.getDeclaredField("slot"); break; } catch(NoSuchFieldException ignored) { type=type.getSuperclass(); } }
        Field slot=type.getDeclaredField("slot");slot.setAccessible(true);Object state=slot.get(line);
        Field track=state.getClass().getDeclaredField("track");track.setAccessible(true);return track.get(state);
    }
    static String state(SourceDataLine line)throws Exception{
        android.media.AudioTrack nativeTrack=(android.media.AudioTrack)track(line);
        return "logical="+line.getLongFramePosition()+" nativeHead="+nativeTrack.getPlaybackHeadPosition()
            +" playState="+nativeTrack.getPlayState()+" buffer="+nativeTrack.getBufferSizeInFrames()
            +" threshold="+(android.os.Build.VERSION.SDK_INT>=31?nativeTrack.getStartThresholdInFrames():"unavailable");
    }
    public static void main(String[]args)throws Exception{
        if(args.length>0&&args[0].equals("capture")){capture();return;}
        AudioFormat format=new AudioFormat(22050,16,1,true,false);
        SourceDataLine one=AndroidAudioBridge.openSourceDataLine(format,8192);
        SourceDataLine two=AndroidAudioBridge.openSourceDataLine(format,8192);
        one.open();two.open();check(track(one)!=track(two),"active lines need independent tracks");
        System.out.println("AUDIO sdk="+android.os.Build.VERSION.SDK_INT+" requested=8192 "+state(one));
        one.start();two.start();one.write(new byte[4096],0,4096);two.write(new byte[4096],0,4096);
        long deadline=System.nanoTime()+3_000_000_000L;
        while(one.getLongFramePosition()<2048&&System.nanoTime()<deadline)Thread.sleep(10);
        System.out.println("AUDIO after short write bytes=4096 "+state(one));
        check(one.getLongFramePosition()==2048,"played frame counter: "+state(one));one.drain();
        two.stop();long frozen=two.getLongFramePosition();Thread.sleep(30);check(two.getLongFramePosition()==frozen,"pause freezes playback");
        two.flush();check(two.available()>0,"flush frees this line");two.start();two.close();
        Object previous=track(one);one.write(new byte[4096],0,4096);one.close();
        SourceDataLine reopened=AndroidAudioBridge.openSourceDataLine(format,8192);reopened.open();
        check(track(reopened)==previous,"rapid reopen preserves buffered native track");
        check(reopened.getLongFramePosition()==0,"previous line audio excluded from new frame count");reopened.start();
        Object reuse=track(reopened);reopened.close();Thread.sleep(1200);reopened.open();reopened.close();Thread.sleep(1000);reopened.open();
        check(track(reopened)==reuse,"stale idle timer cannot retire a newly returned slot");reopened.start();
        for(int i=0;i<20;i++){reopened.write(new byte[1024],0,1024);reopened.close();reopened.open();reopened.start();}
        AtomicReference<Throwable> failure=new AtomicReference<>();
        AtomicBoolean flushing=new AtomicBoolean(true);
        Thread flusher=new Thread(()->{try{while(flushing.get()){reopened.flush();Thread.sleep(1);}}catch(Throwable t){failure.set(t);}},"Audio-flush-race");
        flusher.start();
        for(int i=0;i<20;i++){reopened.close();reopened.open();reopened.start();reopened.write(new byte[256],0,256);}
        flushing.set(false);flusher.join(2000);
        check(!flusher.isAlive()&&failure.get()==null,"flush/reopen race: "+failure.get());
        Thread writer=new Thread(()->{try{reopened.write(new byte[1_000_000],0,1_000_000);}catch(Throwable t){failure.set(t);}},"Audio-close-race");
        writer.start();Thread.sleep(20);reopened.close();writer.join(2000);
        check(!writer.isAlive(),"close releases writer");check(failure.get()==null,"close/write race: "+failure.get());
        Thread.sleep(2200);
        System.out.println("PASS: independent lines, playback/pause/drain/flush, rapid reopen, concurrent close/write and deferred retirement");
        capture();
    }
}
