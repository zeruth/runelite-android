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

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleAWTDialog();
        }
        return accessibleContext;
    }

    /** Accessibility support for dialogs: role DIALOG, plus MODAL and RESIZABLE. */
    protected class AccessibleAWTDialog extends AccessibleAWTWindow {
        private static final long serialVersionUID = 4837230331833941201L;

        public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.DIALOG;
        }

        public javax.accessibility.AccessibleStateSet getAccessibleStateSet() {
            javax.accessibility.AccessibleStateSet states = super.getAccessibleStateSet();
            if (isModal()) {
                states.add(javax.accessibility.AccessibleState.MODAL);
            }
            if (isResizable()) {
                states.add(javax.accessibility.AccessibleState.RESIZABLE);
            }
            return states;
        }
    }

    public enum ModalityType {
        MODELESS, DOCUMENT_MODAL, APPLICATION_MODAL, TOOLKIT_MODAL
    }

    public enum ModalExclusionType {
        NO_EXCLUDE, APPLICATION_EXCLUDE, TOOLKIT_EXCLUDE
    }

    private String title = "";
    private ModalityType modality = ModalityType.MODELESS;
    private boolean resizable = true, undecorated;
    private final Object modalLock = new Object();
    private volatile SecondaryLoop modalLoop;

    public Dialog(Frame owner) { this((Window) owner); }
    public Dialog(Frame owner, String title) { this((Window) owner, title); }
    public Dialog(Frame owner, boolean modal) { this(owner, "", modal); }
    public Dialog(Frame owner, String title, boolean modal) { this((Window) owner, title, modal ? ModalityType.APPLICATION_MODAL : ModalityType.MODELESS); }
    public Dialog(Frame owner, String title, boolean modal, GraphicsConfiguration configuration) { this((Window) owner, title, modal ? ModalityType.APPLICATION_MODAL : ModalityType.MODELESS, configuration); }
    public Dialog(Dialog owner) { this((Window) owner); }
    public Dialog(Dialog owner, String title) { this((Window) owner, title); }
    public Dialog(Dialog owner, String title, boolean modal) { this((Window) owner, title, modal ? ModalityType.APPLICATION_MODAL : ModalityType.MODELESS); }
    public Dialog(Dialog owner, String title, boolean modal, GraphicsConfiguration configuration) { this((Window) owner, title, modal ? ModalityType.APPLICATION_MODAL : ModalityType.MODELESS, configuration); }
    public Dialog(Window owner) { this(owner, ""); }
    public Dialog(Window owner, String title) { this(owner, title, ModalityType.MODELESS); }
    public Dialog(Window owner, ModalityType modality) { this(owner, "", modality); }
    public Dialog(Window owner, String title, ModalityType modality) { this(owner, title, modality, null); }
    public Dialog(Window owner, String title, ModalityType modality, GraphicsConfiguration configuration) {
        super(owner, configuration); setTitle(title); setModalityType(modality);
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title == null ? "" : title; }
    public ModalityType getModalityType() { return modality; }
    public void setModalityType(ModalityType type) { this.modality = type == null ? ModalityType.MODELESS : type; }
    public boolean isModal() { return modality != ModalityType.MODELESS; }
    public void setModal(boolean modal) { this.modality = modal ? ModalityType.APPLICATION_MODAL : ModalityType.MODELESS; }
    public boolean isResizable() { return resizable; }
    public void setResizable(boolean resizable) { this.resizable = resizable; }
    public boolean isUndecorated() { return undecorated; }
    public void setUndecorated(boolean undecorated) { this.undecorated = undecorated; }
    @Override public void setVisible(boolean visible) {
        boolean block = visible && isModal() && !isVisible();
        SecondaryLoop loop = block && EventQueue.isDispatchThread() ? new EventQueue().createSecondaryLoop() : null;
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
