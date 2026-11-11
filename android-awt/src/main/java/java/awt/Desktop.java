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

import java.io.IOException;
import java.net.URI;
import net.runelite.awt.AwtUnsupported;

public class Desktop {
    public enum Action {
        OPEN, EDIT, PRINT, MAIL, BROWSE, APP_EVENT_FOREGROUND, APP_EVENT_HIDDEN,
        APP_EVENT_REOPENED, APP_EVENT_SCREEN_SLEEP, APP_EVENT_SYSTEM_SLEEP,
        APP_EVENT_USER_SESSION, APP_ABOUT, APP_PREFERENCES, APP_OPEN_FILE, APP_PRINT_FILE,
        APP_OPEN_URI, APP_QUIT_HANDLER, APP_QUIT_STRATEGY, APP_SUDDEN_TERMINATION,
        APP_REQUEST_FOREGROUND, APP_HELP_VIEWER, APP_MENU_BAR, BROWSE_FILE_DIR, MOVE_TO_TRASH
    }

    private static final Desktop INSTANCE = new Desktop();
    public interface BrowseHandler { void browse(URI uri) throws IOException; }
    private static volatile BrowseHandler browseHandler;
    public static void setBrowseHandler(BrowseHandler handler) { browseHandler = handler; }

    public static Desktop getDesktop() {
        return INSTANCE;
    }

    public static boolean isDesktopSupported() {
        return browseHandler != null;
    }

    public boolean isSupported(Action action) {
        return action == Action.BROWSE && browseHandler != null;
    }

    public void browse(URI uri) throws IOException {
        BrowseHandler handler = browseHandler;
        if (handler == null) throw new IOException("No browser bridge installed");
        if (uri == null || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())))
            throw new IOException("Only HTTP(S) browser links are supported");
        handler.browse(uri);
    }

    public void open(java.io.File file) throws IOException {
        throw AwtUnsupported.fail("java.awt.Desktop.open(File)");
    }

    public void edit(java.io.File file) throws IOException {
        throw AwtUnsupported.fail("java.awt.Desktop.edit(File)");
    }

    public void mail(URI uri) throws IOException {
        throw AwtUnsupported.fail("java.awt.Desktop.mail(URI)");
    }
}
