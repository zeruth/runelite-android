package java.awt;

import java.io.IOException;
import java.net.URI;

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
        throw new UnsupportedOperationException("Desktop.open not implemented in headless shadow");
    }

    public void edit(java.io.File file) throws IOException {
        throw new UnsupportedOperationException("Desktop.edit not implemented in headless shadow");
    }

    public void mail(URI uri) throws IOException {
        throw new UnsupportedOperationException("Desktop.mail not implemented in headless shadow");
    }
}
