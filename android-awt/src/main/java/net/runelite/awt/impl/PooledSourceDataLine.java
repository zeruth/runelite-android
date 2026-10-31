package net.runelite.awt.impl;

import android.media.AudioAttributes;
import android.media.AudioTrack;
import java.util.*;
import java.util.concurrent.*;
import javax.sound.sampled.*;

/** Independent active lines; short-lived idle tracks preserve rapid reopen continuity. */
class PooledSourceDataLine implements SourceDataLine {
    private static final Object POOL_LOCK = new Object();
    private static final List<Slot> IDLE = new ArrayList<>();
    private static final ScheduledExecutorService REAPER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "AudioTrack-retire"); thread.setDaemon(true); return thread;
    });
    static final class Slot {
        final AudioTrack track;
        final AudioFormat format;
        final int bufferSize;
        Object legacyStartup;
        volatile boolean unusable;
        volatile long written;
        long wrap, lastHead;
        int calls;
        long idleGeneration;
        boolean idle, retired, stopped;
        Slot(AudioTrack track, AudioFormat format, int bufferSize) { this.track=track; this.format=format; this.bufferSize=bufferSize; }
        synchronized long played() {
            long head = Integer.toUnsignedLong(track.getPlaybackHeadPosition());
            if (head < lastHead) wrap += 1L << 32;
            lastHead = head;
            return wrap + head;
        }
    }
    private AudioFormat format;
    private int requestedBuffer;
    private Slot slot;
    private long startFrame, written, closedPosition;
    private boolean running;
    private final List<LineListener> listeners = new CopyOnWriteArrayList<>();

    PooledSourceDataLine(AudioFormat format, int bufferSize) { this.format=format; requestedBuffer=bufferSize; }

    private static boolean sameFormat(AudioFormat a, AudioFormat b) {
        return a.getEncoding().equals(b.getEncoding()) && a.getSampleRate()==b.getSampleRate()
            && a.getSampleSizeInBits()==b.getSampleSizeInBits() && a.getChannels()==b.getChannels()
            && a.getFrameSize()==b.getFrameSize() && a.isBigEndian()==b.isBigEndian();
    }
    private static Slot acquire(AudioFormat format, int requested) throws LineUnavailableException {
        if (format==null || format.getSampleRate()<=0 || format.getFrameSize()<=0 || format.isBigEndian())
            throw new LineUnavailableException("A concrete little-endian PCM format is required");
        int channels = format.getChannels()==1 ? android.media.AudioFormat.CHANNEL_OUT_MONO
            : format.getChannels()==2 ? android.media.AudioFormat.CHANNEL_OUT_STEREO : 0;
        if (channels==0) throw new LineUnavailableException("Only mono and stereo PCM are supported");
        int bits=format.getSampleSizeInBits(), encoding;
        if (format.getFrameSize() != format.getChannels() * ((bits + 7) / 8) || format.getFrameRate() != format.getSampleRate())
            throw new LineUnavailableException("PCM frames must match channel count and sample rate");
        if(format.getEncoding().equals(AudioFormat.Encoding.PCM_FLOAT) && bits==32) encoding=android.media.AudioFormat.ENCODING_PCM_FLOAT;
        else if(format.getEncoding().equals(AudioFormat.Encoding.PCM_UNSIGNED) && bits==8) encoding=android.media.AudioFormat.ENCODING_PCM_8BIT;
        else if(format.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED) && bits==16) encoding=android.media.AudioFormat.ENCODING_PCM_16BIT;
        else if(format.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED) && bits==24 && android.os.Build.VERSION.SDK_INT>=31) encoding=android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED;
        else if(format.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED) && bits==32 && android.os.Build.VERSION.SDK_INT>=31) encoding=android.media.AudioFormat.ENCODING_PCM_32BIT;
        else throw new LineUnavailableException("Unsupported PCM encoding");
        synchronized(POOL_LOCK) {
            for(ListIterator<Slot> it=IDLE.listIterator(IDLE.size());it.hasPrevious();) {
                Slot candidate=it.previous();
                if(sameFormat(candidate.format,format) && candidate.calls==0 && !candidate.retired) {
                    it.remove();candidate.idle=false;candidate.idleGeneration++;return candidate;
                }
            }
        }
        int minimum=AudioTrack.getMinBufferSize((int)format.getSampleRate(),channels,encoding);
        if(minimum<=0) throw new LineUnavailableException("AudioTrack does not support this format");
        int size=Math.max(minimum*2,Math.max(requested,8192));size-=size%format.getFrameSize();
        try {
            AudioTrack track=new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(new android.media.AudioFormat.Builder().setSampleRate((int)format.getSampleRate()).setChannelMask(channels).setEncoding(encoding).build())
                .setBufferSizeInBytes(size).setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY).build();
            try {
                // SourceDataLine.start() must play a short write without waiting for
                // the entire native ring buffer to fill. Keep its capacity for pacing.
                if (android.os.Build.VERSION.SDK_INT >= 31) track.setStartThresholdInFrames(1);
                return new Slot(track,format,track.getBufferSizeInFrames()*format.getFrameSize());
            } catch (RuntimeException failure) { track.release(); throw failure; }
        } catch(RuntimeException failure) { throw new LineUnavailableException("AudioTrack initialization failed: "+failure.getMessage()); }
    }
    private static void retire(Slot candidate, long generation) {
        synchronized(POOL_LOCK) {
            if(!candidate.idle || candidate.retired || candidate.idleGeneration != generation)return;
            candidate.retired=true;candidate.idle=false;IDLE.remove(candidate);
        }
        try { candidate.track.stop(); } catch(IllegalStateException ignored) {}
        synchronized(POOL_LOCK) {
            candidate.stopped=true;
            if(candidate.calls==0)candidate.track.release();
        }
    }
    private static void returnSlot(Slot candidate) {
        if (candidate.unusable) {
            final long generation;
            synchronized (POOL_LOCK) { candidate.idle = true; generation = ++candidate.idleGeneration; }
            retire(candidate, generation);
            return;
        }
        final long generation;
        synchronized(POOL_LOCK) { candidate.idle=true; generation=++candidate.idleGeneration; IDLE.add(candidate); }
        REAPER.schedule(()->retire(candidate,generation),2,TimeUnit.SECONDS);
        Slot excess=null; long excessGeneration=0;
        synchronized(POOL_LOCK) { if(IDLE.size()>4) { excess=IDLE.get(0); excessGeneration=excess.idleGeneration; } }
        if(excess!=null)retire(excess,excessGeneration);
    }
    private Slot borrow() {
        synchronized(this) {
            if(slot==null)return null;
            synchronized(POOL_LOCK) { slot.calls++; }
            return slot;
        }
    }
    private static void release(Slot candidate) {
        synchronized(POOL_LOCK) {
            candidate.calls--;
            if(candidate.calls==0 && candidate.retired && candidate.stopped)candidate.track.release();
        }
    }
    final Slot borrowLegacy() { return borrow(); }
    static void releaseLegacy(Slot candidate) { release(candidate); }
    @Override public void open() throws LineUnavailableException { open(format,requestedBuffer); }
    @Override public void open(AudioFormat format) throws LineUnavailableException { open(format,requestedBuffer); }
    @Override public void open(AudioFormat format,int bufferSize) throws LineUnavailableException {
        synchronized(this) {
            if(slot!=null) { if(!sameFormat(this.format,format))throw new IllegalStateException("Line already open with a different format"); return; }
            if(bufferSize<=0 || format==null || format.getFrameSize()<=0 || bufferSize%format.getFrameSize()!=0)throw new IllegalArgumentException("Whole-frame buffer required");
            Slot acquired=acquire(format,bufferSize);this.format=format;requestedBuffer=bufferSize;
            slot=acquired;startFrame=acquired.written;written=0;closedPosition=0;running=false;
        }
        fire(LineEvent.Type.OPEN);
    }
    @Override public int write(byte[] data,int offset,int length) {
        Objects.requireNonNull(data);
        if(offset<0||length<0||offset>data.length-length)throw new IndexOutOfBoundsException();
        if(length%format.getFrameSize()!=0)throw new IllegalArgumentException("Whole-frame write required");
        Slot current=borrow();if(current==null)return 0;
        try {
            int total=0;
            while(total<length) {
                synchronized(this) { if(slot!=current)break; }
                int count=current.track.write(data,offset+total,length-total,AudioTrack.WRITE_NON_BLOCKING);
                if(count<0)throw new IllegalStateException("AudioTrack write failed: "+count);
                if(count==0) { try { Thread.sleep(2); } catch(InterruptedException e) { Thread.currentThread().interrupt();break; } continue; }
                total+=count;
                synchronized(this) { long frames=count/current.format.getFrameSize();current.written+=frames;if(slot==current)written+=frames; }
            }
            return total;
        } finally { release(current); }
    }
    @Override public void start() {
        synchronized(this) { if(running)return; }
        Slot current=borrow();if(current==null)return;
        try { current.track.play(); synchronized(this){if(slot==current)running=true;} }finally{release(current);}
        fire(LineEvent.Type.START);
    }
    @Override public void stop() {
        synchronized(this) { if(!running)return; }
        Slot current=borrow();if(current==null)return;
        try { current.track.pause(); synchronized(this){if(slot==current)running=false;} }finally{release(current);}
        fire(LineEvent.Type.STOP);
    }
    @Override public void drain() {
        while(isOpen()) {
            synchronized(this){if(getLongFramePosition()>=written)return;}
            try{Thread.sleep(2);}catch(InterruptedException e){Thread.currentThread().interrupt();return;}
        }
    }
    @Override public void flush() {
        Slot current=borrow();if(current==null)return;
        try {
            synchronized(this) {
                if (slot != current) return;
                long position=getLongFramePosition();current.track.pause();current.track.flush();
                synchronized(current) { current.lastHead=0;current.written=position;current.wrap=position; }
                startFrame=0;written=position;
                if(running)current.track.play();
            }
        }finally{release(current);}
    }
    @Override public synchronized boolean isOpen(){return slot!=null;}
    @Override public synchronized boolean isRunning(){return running;}
    @Override public synchronized boolean isActive(){return isRunning()&&getLongFramePosition()<written;}
    @Override public synchronized AudioFormat getFormat(){return format;}
    @Override public synchronized int getBufferSize(){return slot==null?requestedBuffer:slot.bufferSize;}
    @Override public int available(){Slot current=borrow();if(current==null)return 0;try{return (int)Math.max(0,current.bufferSize-Math.max(0,current.written-current.played())*current.format.getFrameSize());}finally{release(current);}}
    @Override public int getFramePosition(){return (int)getLongFramePosition();}
    @Override public synchronized long getLongFramePosition(){Slot current=borrow();if(current==null)return closedPosition;try{return Math.max(0,Math.min(written,current.played()-startFrame));}finally{release(current);}}
    @Override public synchronized long getMicrosecondPosition(){return (long)(getLongFramePosition()*1_000_000d/format.getFrameRate());}
    @Override public float getLevel(){return AudioSystem.NOT_SPECIFIED;}
    @Override public Line.Info getLineInfo(){return new DataLine.Info(SourceDataLine.class,format,getBufferSize());}
    @Override public void close() {
        Slot previous;
        synchronized(this){if(slot==null)return;closedPosition=getLongFramePosition();previous=slot;slot=null;running=false;}
        returnSlot(previous);fire(LineEvent.Type.CLOSE);
    }
    @Override public Control[] getControls(){return new Control[0];}
    @Override public boolean isControlSupported(Control.Type type){return false;}
    @Override public Control getControl(Control.Type type){throw new IllegalArgumentException("Unsupported control: "+type);}
    @Override public void addLineListener(LineListener listener){if(listener!=null)listeners.add(listener);}
    @Override public void removeLineListener(LineListener listener){listeners.remove(listener);}
    private void fire(LineEvent.Type type){LineEvent event=new LineEvent(this,type,getLongFramePosition());for(LineListener listener:listeners)listener.update(event);}
}
