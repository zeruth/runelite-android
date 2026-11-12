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
package java.awt;

public class Frame extends Window {
    private static final long serialVersionUID = 2673458971256075116L;

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleAWTFrame();
        }
        return accessibleContext;
    }

    /** Accessibility support for frames: role FRAME, ACTIVE with the focus, RESIZABLE. */
    protected class AccessibleAWTFrame extends AccessibleAWTWindow {
        private static final long serialVersionUID = -6172960752956030250L;

        public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.FRAME;
        }

        public javax.accessibility.AccessibleStateSet getAccessibleStateSet() {
            javax.accessibility.AccessibleStateSet states = super.getAccessibleStateSet();
            if (isResizable()) {
                states.add(javax.accessibility.AccessibleState.RESIZABLE);
            }
            return states;
        }
    }

    public static final int NORMAL = 0;
    public static final int ICONIFIED = 1;
    public static final int MAXIMIZED_HORIZ = 2;
    public static final int MAXIMIZED_VERT = 4;
    public static final int MAXIMIZED_BOTH = MAXIMIZED_VERT | MAXIMIZED_HORIZ;

    /** @deprecated replaced by {@code Cursor.DEFAULT_CURSOR}. */
    @Deprecated public static final int DEFAULT_CURSOR = Cursor.DEFAULT_CURSOR;
    /** @deprecated replaced by {@code Cursor.CROSSHAIR_CURSOR}. */
    @Deprecated public static final int CROSSHAIR_CURSOR = Cursor.CROSSHAIR_CURSOR;
    /** @deprecated replaced by {@code Cursor.TEXT_CURSOR}. */
    @Deprecated public static final int TEXT_CURSOR = Cursor.TEXT_CURSOR;
    /** @deprecated replaced by {@code Cursor.WAIT_CURSOR}. */
    @Deprecated public static final int WAIT_CURSOR = Cursor.WAIT_CURSOR;
    /** @deprecated replaced by {@code Cursor.SW_RESIZE_CURSOR}. */
    @Deprecated public static final int SW_RESIZE_CURSOR = Cursor.SW_RESIZE_CURSOR;
    /** @deprecated replaced by {@code Cursor.SE_RESIZE_CURSOR}. */
    @Deprecated public static final int SE_RESIZE_CURSOR = Cursor.SE_RESIZE_CURSOR;
    /** @deprecated replaced by {@code Cursor.NW_RESIZE_CURSOR}. */
    @Deprecated public static final int NW_RESIZE_CURSOR = Cursor.NW_RESIZE_CURSOR;
    /** @deprecated replaced by {@code Cursor.NE_RESIZE_CURSOR}. */
    @Deprecated public static final int NE_RESIZE_CURSOR = Cursor.NE_RESIZE_CURSOR;
    /** @deprecated replaced by {@code Cursor.N_RESIZE_CURSOR}. */
    @Deprecated public static final int N_RESIZE_CURSOR = Cursor.N_RESIZE_CURSOR;
    /** @deprecated replaced by {@code Cursor.S_RESIZE_CURSOR}. */
    @Deprecated public static final int S_RESIZE_CURSOR = Cursor.S_RESIZE_CURSOR;
    /** @deprecated replaced by {@code Cursor.W_RESIZE_CURSOR}. */
    @Deprecated public static final int W_RESIZE_CURSOR = Cursor.W_RESIZE_CURSOR;
    /** @deprecated replaced by {@code Cursor.E_RESIZE_CURSOR}. */
    @Deprecated public static final int E_RESIZE_CURSOR = Cursor.E_RESIZE_CURSOR;
    /** @deprecated replaced by {@code Cursor.HAND_CURSOR}. */
    @Deprecated public static final int HAND_CURSOR = Cursor.HAND_CURSOR;
    /** @deprecated replaced by {@code Cursor.MOVE_CURSOR}. */
    @Deprecated public static final int MOVE_CURSOR = Cursor.MOVE_CURSOR;

    private String title = "";
    private boolean resizable = true;
    private boolean undecorated;
    private MenuBar menuBar;
    private Rectangle maximizedBounds;
    private int state = NORMAL;

    public Frame() {
        this("");
    }

    public Frame(GraphicsConfiguration gc) {
        this("", gc);
    }

    public Frame(String title) {
        this(title, null);
    }

    public Frame(String title, GraphicsConfiguration gc) {
        super((Window) null, gc);
        this.title = (title == null) ? "" : title;
    }

    /**
     * Returns every Frame this application created that has not been garbage collected,
     * disposed or not, exactly as {@link Window#getWindows} does on the JDK.
     */
    public static Frame[] getFrames() {
        java.util.List<Frame> frames = new java.util.ArrayList<>();
        for (Window window : getAllWindows()) {
            if (window instanceof Frame) frames.add((Frame) window);
        }
        return frames.toArray(new Frame[0]);
    }

    public String getTitle() { return title; }
    public void setTitle(String title) {
        String oldTitle = this.title;
        this.title = (title == null) ? "" : title;
        firePropertyChange("title", oldTitle, this.title);
    }

    public MenuBar getMenuBar() { return menuBar; }

    /**
     * Sets the AWT menu bar. The host has no native menu bar, so the bar is stored and
     * reachable through {@link #getMenuBar} but not drawn; Swing frames use
     * {@code JFrame.setJMenuBar}, which is part of the root pane and does render.
     */
    public void setMenuBar(MenuBar mb) {
        synchronized (getTreeLock()) {
            if (menuBar == mb) return;
            if (mb != null && mb.parent != null) mb.parent.remove(mb);
            if (menuBar != null) remove(menuBar);
            menuBar = mb;
            if (mb != null) mb.parent = this;
            invalidate();
        }
    }

    /** Removes the menu bar when it is the one given, otherwise a popup menu. */
    @Override
    public void remove(MenuComponent m) {
        if (m == null) return;
        synchronized (getTreeLock()) {
            if (m == menuBar) {
                menuBar = null;
                m.parent = null;
                invalidate();
                return;
            }
        }
        super.remove(m);
    }

    public boolean isResizable() { return resizable; }
    public void setResizable(boolean resizable) {
        boolean oldResizable = this.resizable;
        this.resizable = resizable;
        firePropertyChange("resizable", oldResizable, resizable);
    }

    public boolean isUndecorated() { return undecorated; }

    /**
     * As on the JDK, decorations can only change while the frame is not displayable, and a
     * frame that is translucent, shaped or has a non-opaque background must stay undecorated.
     */
    public void setUndecorated(boolean undecorated) {
        synchronized (getTreeLock()) {
            if (hasHostPeer()) {
                throw new IllegalComponentStateException("The frame is displayable.");
            }
            if (!undecorated) {
                if (getOpacity() < 1.0f) throw new IllegalComponentStateException("The frame is not opaque");
                if (getShape() != null) throw new IllegalComponentStateException("The frame does not have a default shape");
                Color bg = getBackground();
                if (bg != null && bg.getAlpha() < 255) throw new IllegalComponentStateException("The frame background color is not opaque");
            }
            this.undecorated = undecorated;
        }
    }

    @Override
    public void setOpacity(float opacity) {
        synchronized (getTreeLock()) {
            if (opacity < 1.0f && !isUndecorated()) throw new IllegalComponentStateException("The frame is decorated");
            super.setOpacity(opacity);
        }
    }

    @Override
    public void setShape(Shape shape) {
        synchronized (getTreeLock()) {
            if (shape != null && !isUndecorated()) throw new IllegalComponentStateException("The frame is decorated");
            super.setShape(shape);
        }
    }

    @Override
    public void setBackground(Color bgColor) {
        synchronized (getTreeLock()) {
            if (bgColor != null && bgColor.getAlpha() < 255 && !isUndecorated()) {
                throw new IllegalComponentStateException("The frame is decorated");
            }
            super.setBackground(bgColor);
        }
    }

    /** @deprecated As of JDK 1.4, replaced by {@link #getExtendedState}. */
    public synchronized int getState() {
        return (getExtendedState() & ICONIFIED) != 0 ? ICONIFIED : NORMAL;
    }

    /** Iconifies or restores the frame, keeping its maximized bits. */
    public synchronized void setState(int state) {
        int current = getExtendedState();
        if (state == ICONIFIED && (current & ICONIFIED) == 0) {
            setExtendedState(current | ICONIFIED);
        } else if (state == NORMAL && (current & ICONIFIED) != 0) {
            setExtendedState(current & ~ICONIFIED);
        }
    }

    public int getExtendedState() {
        synchronized (getTreeLock()) { return state; }
    }

    /**
     * Sets the extended state; a state the toolkit does not support is ignored, as on the
     * JDK. A displayable frame then receives what the desktop window manager would post:
     * {@code WINDOW_ICONIFIED} or {@code WINDOW_DEICONIFIED} when the iconified bit flips,
     * followed by {@code WINDOW_STATE_CHANGED}.
     *
     * @since 1.4
     */
    public void setExtendedState(int state) {
        if (!isFrameStateSupported(state)) return;
        int oldState;
        synchronized (getTreeLock()) {
            oldState = this.state;
            this.state = state;
        }
        if (oldState == state || !hasHostPeer()) return;
        if ((oldState & ICONIFIED) != (state & ICONIFIED)) {
            dispatchEvent(new java.awt.event.WindowEvent(this, (state & ICONIFIED) != 0
                ? java.awt.event.WindowEvent.WINDOW_ICONIFIED : java.awt.event.WindowEvent.WINDOW_DEICONIFIED));
        }
        dispatchEvent(new java.awt.event.WindowEvent(this, java.awt.event.WindowEvent.WINDOW_STATE_CHANGED, oldState, state));
    }

    private boolean isFrameStateSupported(int state) {
        Toolkit toolkit = getToolkit();
        if (!toolkit.isFrameStateSupported(state)) {
            // A compound state is supported when each part is; MAXIMIZED_BOTH is not compound.
            if ((state & ICONIFIED) != 0 && !toolkit.isFrameStateSupported(ICONIFIED)) return false;
            state &= ~ICONIFIED;
            return toolkit.isFrameStateSupported(state);
        }
        return true;
    }

    /** Stored for the JDK contract; the host sizes a maximized frame to the activity. @since 1.4 */
    public void setMaximizedBounds(Rectangle bounds) {
        synchronized (getTreeLock()) { this.maximizedBounds = bounds; }
    }
    /** @since 1.4 */
    public Rectangle getMaximizedBounds() {
        synchronized (getTreeLock()) { return maximizedBounds; }
    }

    /** @deprecated As of JDK 1.1, replaced by {@code Component.setCursor(Cursor)}. */
    @Deprecated
    public void setCursor(int cursorType) {
        if (cursorType < Cursor.DEFAULT_CURSOR || cursorType > Cursor.MOVE_CURSOR) {
            throw new IllegalArgumentException("illegal cursor type");
        }
        setCursor(Cursor.getPredefinedCursor(cursorType));
    }

    /** @deprecated As of JDK 1.1, replaced by {@code Component.getCursor()}. */
    @Deprecated
    public int getCursorType() {
        Cursor cursor = getCursor();
        return cursor == null ? Cursor.DEFAULT_CURSOR : cursor.getType();
    }

    @Override
    public Insets getInsets() { return new Insets(0, 0, 0, 0); }
}
