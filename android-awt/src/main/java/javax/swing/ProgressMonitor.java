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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * A class to monitor the progress of some operation. If it looks like the
 * operation will take a while, a progress dialog will be popped up. When the
 * ProgressMonitor is created it is given a numeric range and a descriptive
 * string. As the operation progresses, call the setProgress method to
 * indicate how far along the [min,max] range the operation is. Initially,
 * there is no ProgressDialog. After the first millisToDecideToPopup
 * milliseconds (default 500) the progress monitor will predict how long the
 * operation will take. If it is longer than millisToPopup (default 2000,
 * 2 seconds) a ProgressDialog will be popped up.
 *
 * <p>The timing, the cancel semantics and every property match the JDK. The
 * dialog differs in construction only: the shadow {@code JOptionPane} has no
 * instance API ({@code createDialog}, {@code getValue}), so the monitor lays
 * out the message, the note, the bar and the Cancel button in a non-modal
 * {@code JDialog} itself, the same rows {@code ProgressOptionPane} shows.
 * {@code getAccessibleContext} is not provided.
 */
public class ProgressMonitor {
    private JDialog dialog;
    private JLabel noteLabel;
    private JProgressBar myBar;
    private Component parentComponent;
    private String note;
    private Object[] cancelOption = null;
    private Object message;
    private long T0;
    private int millisToDecideToPopup = 500;
    private int millisToPopup = 2000;
    private int min;
    private int max;
    private volatile boolean canceled;

    /**
     * Constructs a graphic object that shows progress, typically by filling in
     * a rectangular bar as the process nears completion.
     *
     * @param parentComponent the parent component for the dialog box
     * @param message a descriptive message that will be shown to the user to
     *        indicate what operation is being monitored. This does not change
     *        as the operation progresses.
     * @param note a short note describing the state of the operation. As the
     *        operation progresses, you can call setNote to change the note
     *        displayed. If note is null, the dialog has no note line and
     *        setNote will have no effect.
     * @param min the lower bound of the range
     * @param max the upper bound of the range
     */
    public ProgressMonitor(Component parentComponent, Object message,
                           String note, int min, int max) {
        this.min = min;
        this.max = max;
        this.parentComponent = parentComponent;

        cancelOption = new Object[1];
        cancelOption[0] = uiString("OptionPane.cancelButtonText", "Cancel");

        this.message = message;
        this.note = note;
        T0 = System.currentTimeMillis();
    }

    /**
     * Indicate the progress of the operation being monitored. If the specified
     * value is &gt;= the maximum, the progress monitor is closed.
     *
     * @param nv an int specifying the current value, between the maximum and
     *        minimum specified for this component
     */
    public void setProgress(int nv) {
        if (nv >= max) {
            close();
        } else {
            if (myBar != null) {
                myBar.setValue(nv);
            } else {
                long T = System.currentTimeMillis();
                long dT = (int) (T - T0);
                if (dT >= millisToDecideToPopup) {
                    int predictedCompletionTime;
                    if (nv > min) {
                        predictedCompletionTime = (int) (dT
                                * (max - min)
                                / (nv - min));
                    } else {
                        predictedCompletionTime = millisToPopup;
                    }
                    if (predictedCompletionTime >= millisToPopup) {
                        myBar = new JProgressBar();
                        myBar.setMinimum(min);
                        myBar.setMaximum(max);
                        myBar.setValue(nv);
                        if (note != null) {
                            noteLabel = new JLabel(note);
                        }
                        dialog = createDialog(uiString(
                                "ProgressMonitor.progressText", "Progress..."));
                        dialog.setVisible(true);
                    }
                }
            }
        }
    }

    /**
     * Indicate that the operation is complete. This happens automatically when
     * the value set by setProgress is &gt;= max, but it may be called earlier
     * if the operation ends early.
     */
    public void close() {
        if (dialog != null) {
            dialog.setVisible(false);
            dialog.dispose();
            dialog = null;
            myBar = null;
            // As in the JDK, dropping the pane forgets a cancel: a closed
            // monitor reports isCanceled() == false.
            canceled = false;
        }
    }

    /** Returns the minimum value -- the lower end of the progress value. */
    public int getMinimum() {
        return min;
    }

    /** Specifies the minimum value. */
    public void setMinimum(int m) {
        if (myBar != null) {
            myBar.setMinimum(m);
        }
        min = m;
    }

    /** Returns the maximum value -- the higher end of the progress value. */
    public int getMaximum() {
        return max;
    }

    /** Specifies the maximum value. */
    public void setMaximum(int m) {
        if (myBar != null) {
            myBar.setMaximum(m);
        }
        max = m;
    }

    /** Returns true if the user hit the Cancel button or closed the progress dialog. */
    public boolean isCanceled() {
        if (dialog == null) {
            return false;
        }
        return canceled;
    }

    /**
     * Specifies the amount of time to wait before deciding whether or not to
     * popup a progress monitor.
     */
    public void setMillisToDecideToPopup(int millisToDecideToPopup) {
        this.millisToDecideToPopup = millisToDecideToPopup;
    }

    /** Returns the amount of time this object waits before deciding whether or not to popup a progress monitor. */
    public int getMillisToDecideToPopup() {
        return millisToDecideToPopup;
    }

    /**
     * Specifies the amount of time it will take for the popup to appear. (If
     * the predicted time remaining is less than this time, the popup won't be
     * displayed.)
     */
    public void setMillisToPopup(int millisToPopup) {
        this.millisToPopup = millisToPopup;
    }

    /** Returns the amount of time it will take for the popup to appear. */
    public int getMillisToPopup() {
        return millisToPopup;
    }

    /**
     * Specifies the additional note that is displayed along with the progress
     * message. Used, for example, to show which file is currently being
     * copied during a multiple-file copy.
     */
    public void setNote(String note) {
        this.note = note;
        if (noteLabel != null) {
            noteLabel.setText(note);
        }
    }

    /** Specifies the additional note that is displayed along with the progress message. */
    public String getNote() {
        return note;
    }

    private static String uiString(String key, String fallback) {
        String value = UIManager.getString(key);
        return value != null ? value : fallback;
    }

    // ProgressOptionPane's rows: message, note, bar, then the single Cancel
    // option; closing the window counts as Cancel, as it does there.
    private JDialog createDialog(String title) {
        Window window = parentComponent == null ? null
                : parentComponent instanceof Window ? (Window) parentComponent
                : SwingUtilities.getWindowAncestor(parentComponent);
        final JDialog d = new JDialog(window, title, Dialog.ModalityType.MODELESS);

        JPanel rows = new JPanel();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        addMessage(rows, message);
        if (noteLabel != null) {
            rows.add(noteLabel);
        }
        rows.add(myBar);

        JButton cancel = new JButton(String.valueOf(cancelOption[0]));
        cancel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                canceled = true;
                d.setVisible(false);
            }
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttons.add(cancel);

        d.getContentPane().setLayout(new BorderLayout());
        d.getContentPane().add(rows, BorderLayout.CENTER);
        d.getContentPane().add(buttons, BorderLayout.SOUTH);
        d.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent we) {
                canceled = true;
            }
        });
        d.setResizable(true);
        d.pack();
        d.setLocationRelativeTo(parentComponent);
        return d;
    }

    // JOptionPane's message rendering: components as is, arrays row by row,
    // icons and everything else as labels; null adds nothing.
    private static void addMessage(JPanel rows, Object msg) {
        if (msg == null) {
            return;
        }
        if (msg instanceof Component) {
            rows.add((Component) msg);
        } else if (msg instanceof Object[]) {
            for (Object part : (Object[]) msg) {
                addMessage(rows, part);
            }
        } else if (msg instanceof Icon) {
            rows.add(new JLabel((Icon) msg));
        } else {
            rows.add(new JLabel(msg.toString()));
        }
    }
}
