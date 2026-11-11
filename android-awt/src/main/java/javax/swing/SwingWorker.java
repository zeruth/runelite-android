/*
 * Copyright (c) 2026, Tyler Bochard
 * All rights reserved.
 *
 * Part of android-awt, the Android shadow of the desktop JDK surface used by
 * runelite-android. Original work of this project, not derived from OpenJDK;
 * licensed under the BSD 2-Clause License (see the LICENSE file at the
 * repository root). Files ported from OpenJDK carry the OpenJDK header instead;
 * see android-awt/LICENSE.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package javax.swing;
import java.util.*;
import java.util.concurrent.*;
import java.beans.*;
/** Background work with completion, publication and state notifications on the EDT. */
public abstract class SwingWorker<T,V> implements RunnableFuture<T> {
    public enum StateValue { PENDING,STARTED,DONE }
    private static final ExecutorService EXECUTOR=Executors.newFixedThreadPool(10,r->{Thread thread=new Thread(r,"SwingWorker");thread.setDaemon(true);return thread;});
    private final PropertyChangeSupport changes=new PropertyChangeSupport(this);
    private volatile StateValue state=StateValue.PENDING;
    private volatile int progress;
    private final java.util.List<V> chunks=new ArrayList<>();
    private boolean publishPending;
    private final FutureTask<T> future=new FutureTask<T>(()->{setState(StateValue.STARTED);return doInBackground();}) {
        protected void done(){SwingUtilities.invokeLater(()->{try{SwingWorker.this.done();}finally{setState(StateValue.DONE);}});}
    };
    protected abstract T doInBackground() throws Exception;
    protected void process(java.util.List<V> values){}
    protected void done(){}
    protected final void publish(V... values){synchronized(chunks){Collections.addAll(chunks,values);if(publishPending)return;publishPending=true;}SwingUtilities.invokeLater(()->{java.util.List<V> valuesToProcess;synchronized(chunks){valuesToProcess=new ArrayList<>(chunks);chunks.clear();publishPending=false;}process(valuesToProcess);});}
    protected final void setProgress(int value){if(value<0||value>100)throw new IllegalArgumentException("progress");int old=progress;progress=value;firePropertyChange("progress",old,value);}
    public final int getProgress(){return progress;}
    public final StateValue getState(){return state;}
    private void setState(StateValue value){StateValue old=state;state=value;firePropertyChange("state",old,value);}
    public final void firePropertyChange(String name,Object old,Object value){if(SwingUtilities.isEventDispatchThread())changes.firePropertyChange(name,old,value);else SwingUtilities.invokeLater(()->changes.firePropertyChange(name,old,value));}
    public final void addPropertyChangeListener(PropertyChangeListener listener){changes.addPropertyChangeListener(listener);}
    public final void removePropertyChangeListener(PropertyChangeListener listener){changes.removePropertyChangeListener(listener);}
    public final PropertyChangeSupport getPropertyChangeSupport(){return changes;}
    public final void execute(){EXECUTOR.execute(this);}
    public final void run(){future.run();}
    public final boolean cancel(boolean interrupt){return future.cancel(interrupt);}
    public final boolean isCancelled(){return future.isCancelled();}
    public final boolean isDone(){return future.isDone();}
    public final T get()throws InterruptedException,ExecutionException{return future.get();}
    public final T get(long timeout,TimeUnit unit)throws InterruptedException,ExecutionException,TimeoutException{return future.get(timeout,unit);}
}
