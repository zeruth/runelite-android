package javax.accessibility;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Base class for the strongly typed enumerations of roles, states and relations. The key is
 * the locale-independent name; the display string comes from a resource bundle and falls back
 * to the key, as on the JDK.
 */
public abstract class AccessibleBundle {

    /** The locale independent name of the state. */
    protected String key = null;

    public AccessibleBundle() {
    }

    /**
     * Obtains the key as a localized string. When the named bundle cannot be found or lacks the
     * key, the key itself is returned.
     */
    protected String toDisplayString(String resourceBundleName, Locale locale) {
        try {
            ResourceBundle bundle = ResourceBundle.getBundle(resourceBundleName, locale);
            Object o = bundle.getObject(key);
            if (o instanceof String) {
                return (String) o;
            }
        } catch (MissingResourceException e) {
            // fall through to the built-in table, then the key
        }
        return AccessibleResourceBundle.displayString(key);
    }

    /**
     * Obtains the key as a localized string. The built-in table is English only, which is what
     * the JDK ships for every locale without its own accessibility bundle.
     */
    public String toDisplayString(Locale locale) {
        return AccessibleResourceBundle.displayString(key);
    }

    public String toDisplayString() {
        return toDisplayString(Locale.getDefault());
    }

    public String toString() {
        return toDisplayString();
    }
}
