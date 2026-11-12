package javax.accessibility;

import java.awt.datatransfer.DataFlavor;
import java.io.InputStream;

/**
 * Content of an accessible object exposed as streams of the given MIME types.
 */
public interface AccessibleStreamable {

    DataFlavor[] getMimeTypes();

    InputStream getStream(DataFlavor flavor);
}
