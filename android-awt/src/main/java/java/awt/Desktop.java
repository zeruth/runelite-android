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

import java.awt.desktop.AboutHandler;
import java.awt.desktop.OpenFilesHandler;
import java.awt.desktop.OpenURIHandler;
import java.awt.desktop.PreferencesHandler;
import java.awt.desktop.PrintFilesHandler;
import java.awt.desktop.QuitHandler;
import java.awt.desktop.QuitStrategy;
import java.awt.desktop.SystemEventListener;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import javax.swing.JMenuBar;
import net.runelite.awt.AwtUnsupported;

/**
 * The {@code Desktop} class allows a Java application to launch associated
 * applications registered on the native desktop to handle a {@link URI} or a
 * file.
 *
 * <p>On Android the desktop is the host app: it installs a {@link Bridge}
 * that turns each action into an intent. Until one is installed
 * {@link #isDesktopSupported()} is {@code false} and {@link #getDesktop()}
 * throws, exactly like a JDK without a desktop peer. With a bridge the
 * supported actions are:
 *
 * <table border="1">
 * <caption>Desktop actions on Android</caption>
 * <tr><th>Action</th><th>Supported</th><th>Android counterpart</th></tr>
 * <tr><td>{@code BROWSE}</td><td>yes</td><td>Custom Tab for http(s), {@code ACTION_VIEW} otherwise</td></tr>
 * <tr><td>{@code OPEN}, {@code PRINT}</td><td>yes</td><td>{@code ACTION_VIEW} by MIME type through a content URI</td></tr>
 * <tr><td>{@code EDIT}</td><td>yes</td><td>{@code ACTION_EDIT} by MIME type with a write grant</td></tr>
 * <tr><td>{@code MAIL}</td><td>yes</td><td>{@code ACTION_SENDTO} with the {@code mailto:} URI</td></tr>
 * <tr><td>{@code APP_*}</td><td>no</td><td>Android has no application menu, about/preferences/quit
 *     hooks, file-open events or help viewer; the activity lifecycle replaces them</td></tr>
 * <tr><td>{@code BROWSE_FILE_DIR}</td><td>no</td><td>no file manager can be asked to reveal a file
 *     in app-private storage</td></tr>
 * <tr><td>{@code MOVE_TO_TRASH}</td><td>no</td><td>app-private storage has no trash</td></tr>
 * </table>
 *
 * <p>Unsupported actions throw {@code UnsupportedOperationException} with the
 * JDK message, as on a desktop whose peer lacks them.
 *
 * @since 1.6
 */
public class Desktop {

    /**
     * Represents an action type. Each platform supports a different set of
     * actions; {@link Desktop#isSupported(Action)} tells which.
     */
    public static enum Action {
        OPEN,
        EDIT,
        PRINT,
        MAIL,
        BROWSE,
        /** @since 9 */ APP_EVENT_FOREGROUND,
        /** @since 9 */ APP_EVENT_HIDDEN,
        /** @since 9 */ APP_EVENT_REOPENED,
        /** @since 9 */ APP_EVENT_SCREEN_SLEEP,
        /** @since 9 */ APP_EVENT_SYSTEM_SLEEP,
        /** @since 9 */ APP_EVENT_USER_SESSION,
        /** @since 9 */ APP_ABOUT,
        /** @since 9 */ APP_PREFERENCES,
        /** @since 9 */ APP_OPEN_FILE,
        /** @since 9 */ APP_PRINT_FILE,
        /** @since 9 */ APP_OPEN_URI,
        /** @since 9 */ APP_QUIT_HANDLER,
        /** @since 9 */ APP_QUIT_STRATEGY,
        /** @since 9 */ APP_SUDDEN_TERMINATION,
        /** @since 9 */ APP_REQUEST_FOREGROUND,
        /** @since 9 */ APP_HELP_VIEWER,
        /** @since 9 */ APP_MENU_BAR,
        /** @since 9 */ BROWSE_FILE_DIR,
        /** @since 9 */ MOVE_TO_TRASH
    }

    /**
     * The host side of the desktop. Arguments are already validated the JDK way
     * (existing files, non-null URIs, {@code mailto} scheme); an implementation
     * reports a missing handler app as {@code IOException}.
     */
    public interface Bridge {
        void browse(URI uri) throws IOException;

        void open(File file) throws IOException;

        void edit(File file) throws IOException;

        void print(File file) throws IOException;

        void mail(URI mailtoURI) throws IOException;
    }

    /**
     * The browse-only bridge of earlier builds.
     *
     * @deprecated install a {@link Bridge} with {@link #setBridge(Bridge)}.
     */
    @Deprecated
    public interface BrowseHandler {
        void browse(URI uri) throws IOException;
    }

    private static final Desktop INSTANCE = new Desktop();
    private static volatile Bridge bridge;

    private Desktop() {
    }

    /** Installs the host bridge; {@code null} uninstalls it. */
    public static void setBridge(Bridge replacement) {
        bridge = replacement;
    }

    /** Uninstalls the bridge only when it is still {@code expected}. */
    public static synchronized void clearBridge(Bridge expected) {
        if (bridge == expected) {
            bridge = null;
        }
    }

    /**
     * Installs a bridge that supports only {@code BROWSE}, limited to http(s).
     *
     * @deprecated install a {@link Bridge} with {@link #setBridge(Bridge)}.
     */
    @Deprecated
    public static void setBrowseHandler(BrowseHandler handler) {
        bridge = handler == null ? null : new BrowseOnly(handler);
    }

    /**
     * Returns the {@code Desktop} instance of the current context.
     *
     * @throws UnsupportedOperationException if this platform does not support
     *         the {@code Desktop} class (no host bridge is installed)
     */
    public static synchronized Desktop getDesktop() {
        // No HeadlessException: the shadow GraphicsEnvironment is always headless
        // and the bridge alone decides whether a desktop exists.
        if (!isDesktopSupported()) {
            throw new UnsupportedOperationException("Desktop API is not supported on the current platform");
        }
        return INSTANCE;
    }

    /** Tests whether this class is supported on the current platform. */
    public static boolean isDesktopSupported() {
        return bridge != null;
    }

    /**
     * Tests whether an action is supported on the current platform; see the
     * class description for the table.
     */
    public boolean isSupported(Action action) {
        Bridge current = bridge;
        if (current == null) {
            return false;
        }
        if (current instanceof BrowseOnly) {
            return action == Action.BROWSE;
        }
        switch (action) {
            case OPEN:
            case EDIT:
            case PRINT:
            case MAIL:
            case BROWSE:
                return true;
            default:
                return false;
        }
    }

    private void checkActionSupport(Action actionType) {
        if (!isSupported(actionType)) {
            throw new UnsupportedOperationException("The " + actionType.name()
                    + " action is not supported on the current platform!");
        }
    }

    private static void checkFileValidation(File file) {
        if (!file.exists()) {
            throw new IllegalArgumentException("The file: " + file.getPath() + " doesn't exist.");
        }
    }

    private Bridge bridge() throws IOException {
        Bridge current = bridge;
        if (current == null) {
            throw new IOException("The desktop bridge was uninstalled");
        }
        return current;
    }

    /**
     * Launches the associated application to open the file.
     *
     * @throws NullPointerException if {@code file} is {@code null}
     * @throws IllegalArgumentException if the specified file doesn't exist
     * @throws UnsupportedOperationException if the {@code OPEN} action is unsupported
     * @throws IOException if no application can open the file
     */
    public void open(File file) throws IOException {
        file = new File(file.getPath());
        checkActionSupport(Action.OPEN);
        checkFileValidation(file);
        bridge().open(file);
    }

    /**
     * Launches the associated editor application and opens a file for editing.
     *
     * @throws IOException if the file is a directory or no editor is installed
     */
    public void edit(File file) throws IOException {
        file = new File(file.getPath());
        checkActionSupport(Action.EDIT);
        checkFileValidation(file);
        if (file.isDirectory()) {
            throw new IOException(file.getPath() + " is a directory");
        }
        bridge().edit(file);
    }

    /**
     * Prints a file with the native desktop printing facility. On Android this
     * opens the file in a viewer, from which the system print service is reached.
     *
     * @throws IOException if the file is a directory or no viewer is installed
     */
    public void print(File file) throws IOException {
        file = new File(file.getPath());
        checkActionSupport(Action.PRINT);
        checkFileValidation(file);
        if (file.isDirectory()) {
            throw new IOException(file.getPath() + " is a directory");
        }
        bridge().print(file);
    }

    /**
     * Launches the default browser to display a {@code URI}.
     *
     * @throws NullPointerException if {@code uri} is {@code null}
     * @throws IOException if no application can handle the URI
     */
    public void browse(URI uri) throws IOException {
        checkActionSupport(Action.BROWSE);
        if (uri == null) {
            throw new NullPointerException();
        }
        bridge().browse(uri);
    }

    /** Launches the mail composing window of the user default mail client. */
    public void mail() throws IOException {
        checkActionSupport(Action.MAIL);
        // The JDK's own empty mailto: "mailto:" alone has no scheme-specific part and does not parse.
        mail(URI.create("mailto:?"));
    }

    /**
     * Launches the mail composing window of the user default mail client,
     * filling the message fields specified by a {@code mailto:} URI.
     *
     * @throws NullPointerException if {@code mailtoURI} is {@code null}
     * @throws IllegalArgumentException if the URI scheme is not {@code "mailto"}
     */
    public void mail(URI mailtoURI) throws IOException {
        checkActionSupport(Action.MAIL);
        if (mailtoURI == null) {
            throw new NullPointerException();
        }
        if (!"mailto".equalsIgnoreCase(mailtoURI.getScheme())) {
            throw new IllegalArgumentException("URI scheme is not \"mailto\"");
        }
        bridge().mail(mailtoURI);
    }

    /**
     * Adds sub-types of {@link SystemEventListener} to listen for notifications
     * from the native system. Android delivers none of the {@code APP_EVENT_*}
     * notifications, and the JDK specifies that a sub-type the platform does not
     * support has no effect, so this is a documented no-op.
     *
     * @since 9
     */
    // JDK 9+ (9)
    public void addAppEventListener(final SystemEventListener listener) {
        AwtUnsupported.noop("java.awt.Desktop.addAppEventListener(SystemEventListener)",
                "Android delivers no APP_EVENT_* notifications to AWT");
    }

    /**
     * Removes a listener added with {@link #addAppEventListener}; a no-op for
     * the same reason.
     *
     * @since 9
     */
    // JDK 9+ (9)
    public void removeAppEventListener(final SystemEventListener listener) {
        AwtUnsupported.noop("java.awt.Desktop.removeAppEventListener(SystemEventListener)",
                "Android delivers no APP_EVENT_* notifications to AWT");
    }

    /** @throws UnsupportedOperationException always: {@code APP_ABOUT} is unsupported. @since 9 */
    // JDK 9+ (9)
    public void setAboutHandler(final AboutHandler aboutHandler) {
        checkActionSupport(Action.APP_ABOUT);
    }

    /** @throws UnsupportedOperationException always: {@code APP_PREFERENCES} is unsupported. @since 9 */
    // JDK 9+ (9)
    public void setPreferencesHandler(final PreferencesHandler preferencesHandler) {
        checkActionSupport(Action.APP_PREFERENCES);
    }

    /** @throws UnsupportedOperationException always: {@code APP_OPEN_FILE} is unsupported. @since 9 */
    // JDK 9+ (9)
    public void setOpenFileHandler(final OpenFilesHandler openFileHandler) {
        checkActionSupport(Action.APP_OPEN_FILE);
    }

    /** @throws UnsupportedOperationException always: {@code APP_PRINT_FILE} is unsupported. @since 9 */
    // JDK 9+ (9)
    public void setPrintFileHandler(final PrintFilesHandler printFileHandler) {
        checkActionSupport(Action.APP_PRINT_FILE);
    }

    /** @throws UnsupportedOperationException always: {@code APP_OPEN_URI} is unsupported. @since 9 */
    // JDK 9+ (9)
    public void setOpenURIHandler(final OpenURIHandler openURIHandler) {
        checkActionSupport(Action.APP_OPEN_URI);
    }

    /** @throws UnsupportedOperationException always: {@code APP_QUIT_HANDLER} is unsupported. @since 9 */
    // JDK 9+ (9)
    public void setQuitHandler(final QuitHandler quitHandler) {
        checkActionSupport(Action.APP_QUIT_HANDLER);
    }

    /** @throws UnsupportedOperationException always: {@code APP_QUIT_STRATEGY} is unsupported. @since 9 */
    // JDK 9+ (9)
    public void setQuitStrategy(final QuitStrategy strategy) {
        checkActionSupport(Action.APP_QUIT_STRATEGY);
    }

    /** @throws UnsupportedOperationException always: {@code APP_SUDDEN_TERMINATION} is unsupported. @since 9 */
    // JDK 9+ (9)
    public void enableSuddenTermination() {
        checkActionSupport(Action.APP_SUDDEN_TERMINATION);
    }

    /** @throws UnsupportedOperationException always: {@code APP_SUDDEN_TERMINATION} is unsupported. @since 9 */
    // JDK 9+ (9)
    public void disableSuddenTermination() {
        checkActionSupport(Action.APP_SUDDEN_TERMINATION);
    }

    /** @throws UnsupportedOperationException always: {@code APP_REQUEST_FOREGROUND} is unsupported. @since 9 */
    // JDK 9+ (9)
    public void requestForeground(final boolean allWindows) {
        checkActionSupport(Action.APP_REQUEST_FOREGROUND);
    }

    /** @throws UnsupportedOperationException always: {@code APP_HELP_VIEWER} is unsupported. @since 9 */
    // JDK 9+ (9)
    public void openHelpViewer() {
        checkActionSupport(Action.APP_HELP_VIEWER);
    }

    /** @throws UnsupportedOperationException always: {@code APP_MENU_BAR} is unsupported. @since 9 */
    // JDK 9+ (9)
    public void setDefaultMenuBar(final JMenuBar menuBar) {
        checkActionSupport(Action.APP_MENU_BAR);
    }

    /** @throws UnsupportedOperationException always: {@code BROWSE_FILE_DIR} is unsupported. @since 9 */
    // JDK 9+ (9)
    public void browseFileDirectory(File file) {
        file = new File(file.getPath());
        checkActionSupport(Action.BROWSE_FILE_DIR);
    }

    /** @throws UnsupportedOperationException always: {@code MOVE_TO_TRASH} is unsupported. @since 9 */
    // JDK 9+ (9)
    public boolean moveToTrash(File file) {
        file = new File(file.getPath());
        checkActionSupport(Action.MOVE_TO_TRASH);
        return false;
    }

    private static final class BrowseOnly implements Bridge {
        private final BrowseHandler handler;

        BrowseOnly(BrowseHandler handler) {
            this.handler = handler;
        }

        @Override
        public void browse(URI uri) throws IOException {
            String scheme = uri.getScheme();
            if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
                throw new IOException("Only HTTP(S) browser links are supported");
            }
            handler.browse(uri);
        }

        @Override
        public void open(File file) {
            throw AwtUnsupported.fail("java.awt.Desktop.open(File)");
        }

        @Override
        public void edit(File file) {
            throw AwtUnsupported.fail("java.awt.Desktop.edit(File)");
        }

        @Override
        public void print(File file) {
            throw AwtUnsupported.fail("java.awt.Desktop.print(File)");
        }

        @Override
        public void mail(URI mailtoURI) {
            throw AwtUnsupported.fail("java.awt.Desktop.mail(URI)");
        }
    }
}
