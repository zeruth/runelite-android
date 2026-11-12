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

public class Dialog extends Window {
    private static final long serialVersionUID = 5036553639616394035L;

    /**
     * Modality of a dialog: what a visible modal dialog blocks.
     *
     * @since 1.6
     */
    public static enum ModalityType {
        MODELESS, DOCUMENT_MODAL, APPLICATION_MODAL, TOOLKIT_MODAL
    }

    /**
     * The modality type used by the boolean constructors and {@link #setModal(boolean)}.
     *
     * @since 1.6
     */
    public final static ModalityType DEFAULT_MODALITY_TYPE = ModalityType.APPLICATION_MODAL;

    /**
     * Which modal dialogs a top-level window ignores.
     *
     * @since 1.6
     */
    public static enum ModalExclusionType {
        NO_EXCLUDE, APPLICATION_EXCLUDE, TOOLKIT_EXCLUDE
    }

    private String title = "";
    private ModalityType modalityType = ModalityType.MODELESS;
    boolean modal;
    private boolean resizable = true, undecorated;
    private final Object modalLock = new Object();
    private volatile SecondaryLoop modalLoop;

    public Dialog(Frame owner) { this(owner, "", false); }
    public Dialog(Frame owner, boolean modal) { this(owner, "", modal); }
    public Dialog(Frame owner, String title) { this(owner, title, false); }
    public Dialog(Frame owner, String title, boolean modal) { this((Window) owner, title, modal ? DEFAULT_MODALITY_TYPE : ModalityType.MODELESS); }
    public Dialog(Frame owner, String title, boolean modal, GraphicsConfiguration gc) { this((Window) owner, title, modal ? DEFAULT_MODALITY_TYPE : ModalityType.MODELESS, gc); }
    public Dialog(Dialog owner) { this(owner, "", false); }
    public Dialog(Dialog owner, String title) { this(owner, title, false); }
    public Dialog(Dialog owner, String title, boolean modal) { this((Window) owner, title, modal ? DEFAULT_MODALITY_TYPE : ModalityType.MODELESS); }
    public Dialog(Dialog owner, String title, boolean modal, GraphicsConfiguration gc) { this((Window) owner, title, modal ? DEFAULT_MODALITY_TYPE : ModalityType.MODELESS, gc); }
    /** @since 1.6 */
    public Dialog(Window owner) { this(owner, "", ModalityType.MODELESS); }
    /** @since 1.6 */
    public Dialog(Window owner, String title) { this(owner, title, ModalityType.MODELESS); }
    /** @since 1.6 */
    public Dialog(Window owner, ModalityType modalityType) { this(owner, "", modalityType); }
    /** @since 1.6 */
    public Dialog(Window owner, String title, ModalityType modalityType) { this(owner, title, modalityType, null); }
    /**
     * The JDK accepts only a Frame or a Dialog as the owner of a dialog.
     *
     * @since 1.6
     */
    public Dialog(Window owner, String title, ModalityType modalityType, GraphicsConfiguration gc) {
        super(checkOwner(owner), gc);
        this.title = title == null ? "" : title;
        setModalityType(modalityType);
    }

    private static Window checkOwner(Window owner) {
        if (owner != null && !(owner instanceof Dialog) && !(owner instanceof Frame)) {
            throw new IllegalArgumentException("Wrong parent window");
        }
        return owner;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) {
        String oldTitle = this.title;
        this.title = title == null ? "" : title;
        firePropertyChange("title", oldTitle, this.title);
    }

    /** @since 1.6 */
    public ModalityType getModalityType() { return modalityType; }

    /**
     * Sets the modality; {@code null} and a type the toolkit does not support both mean
     * MODELESS, as on the JDK (the shadow Toolkit supports every non-null type). Like the
     * JDK, a change applies from the next time the dialog is shown.
     *
     * @since 1.6
     */
    public void setModalityType(ModalityType type) {
        if (type == null) type = ModalityType.MODELESS;
        if (!Toolkit.getDefaultToolkit().isModalityTypeSupported(type)) type = ModalityType.MODELESS;
        modalityType = type;
        modal = modalityType != ModalityType.MODELESS;
    }

    public boolean isModal() { return modalityType != ModalityType.MODELESS; }
    public void setModal(boolean modal) {
        this.modal = modal;
        setModalityType(modal ? DEFAULT_MODALITY_TYPE : ModalityType.MODELESS);
    }

    public boolean isResizable() { return resizable; }
    public void setResizable(boolean resizable) { this.resizable = resizable; }
    public boolean isUndecorated() { return undecorated; }

    /** Decorations can only change while the dialog is not displayable, as on the JDK. */
    public void setUndecorated(boolean undecorated) {
        synchronized (getTreeLock()) {
            if (hasHostPeer()) {
                throw new IllegalComponentStateException("The dialog is displayable.");
            }
            if (!undecorated) {
                if (getOpacity() < 1.0f) throw new IllegalComponentStateException("The dialog is not opaque");
                if (getShape() != null) throw new IllegalComponentStateException("The dialog does not have a default shape");
                Color bg = getBackground();
                if (bg != null && bg.getAlpha() < 255) throw new IllegalComponentStateException("The dialog background color is not opaque");
            }
            this.undecorated = undecorated;
        }
    }

    @Override
    public void setOpacity(float opacity) {
        synchronized (getTreeLock()) {
            if (opacity < 1.0f && !isUndecorated()) throw new IllegalComponentStateException("The dialog is decorated");
            super.setOpacity(opacity);
        }
    }

    @Override
    public void setShape(Shape shape) {
        synchronized (getTreeLock()) {
            if (shape != null && !isUndecorated()) throw new IllegalComponentStateException("The dialog is decorated");
            super.setShape(shape);
        }
    }

    @Override
    public void setBackground(Color bgColor) {
        synchronized (getTreeLock()) {
            if (bgColor != null && bgColor.getAlpha() < 255 && !isUndecorated()) {
                throw new IllegalComponentStateException("The dialog is decorated");
            }
            super.setBackground(bgColor);
        }
    }

    /**
     * Shows or hides the dialog. Showing a modal dialog returns only once it is hidden or
     * disposed: on the event dispatch thread through a {@link SecondaryLoop} of the system
     * event queue, so posted events and {@code invokeLater} tasks (the host's close request
     * among them) keep running; on any other thread by waiting.
     */
    @Override public void setVisible(boolean visible) {
        boolean block = visible && isModal() && !isVisible();
        SecondaryLoop loop = block && EventQueue.isDispatchThread()
            ? Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop() : null;
        if (loop != null) modalLoop = loop;
        super.setVisible(visible);
        if (!visible) {
            synchronized (modalLock) { modalLock.notifyAll(); }
            SecondaryLoop current = modalLoop;
            if (current != null) {
                current.exit();
                // Also wake a loop whose enter races this hide operation.
                EventQueue.invokeLater(current::exit);
            }
        } else if (block) {
            if (loop != null) {
                try { if (isVisible()) loop.enter(); } finally { if (modalLoop == loop) modalLoop = null; }
            } else {
                synchronized (modalLock) {
                    while (isVisible()) try { modalLock.wait(); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                }
            }
        }
    }
}
