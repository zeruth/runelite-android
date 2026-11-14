/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package javax.swing;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.Vector;
import javax.swing.border.Border;
import javax.swing.plaf.ComponentUI;

/**
 * {@code UIManager} manages the current look and feel, the set of
 * available look and feels, {@code PropertyChangeListeners} that
 * are notified when the look and feel changes, look and feel defaults, and
 * convenience methods for obtaining various default values.
 *
 * <h3>Specifying the look and feel</h3>
 *
 * The look and feel can be specified in two distinct ways: by
 * specifying the fully qualified name of the class for the look and
 * feel, or by creating an instance of {@code LookAndFeel} and passing
 * it to {@code setLookAndFeel}. If no look and feel has been set when
 * the defaults are first needed, the class named by the
 * {@code swing.defaultlaf} system property is installed, or the
 * cross-platform look and feel when that property is not set.
 *
 * <h3>Default values</h3>
 *
 * The {@code UIDefaults} returned by {@code getDefaults} resolve a key
 * through three tables: values set with {@code put} (the developer
 * defaults), then the look and feel defaults, then the system defaults.
 * Setting a look and feel replaces only the second table, so developer
 * values survive a change of look and feel.
 *
 * <p>Android has no {@code swing.properties} file under
 * {@code java.home}; the {@code swing.*} keys are read from the system
 * properties only, which is where the JDK lets them override the file.
 *
 * @author Thomas Ball
 * @author Hans Muller
 */
@SuppressWarnings("serial")
public class UIManager implements Serializable
{
    /**
     * This class defines the state managed by the <code>UIManager</code>.
     * The JDK keeps one per AppContext; Android has a single application
     * context, so there is one.
     */
    private static class LAFState
    {
        private UIDefaults[] tables = new UIDefaults[2];

        boolean initialized = false;
        MultiUIDefaults multiUIDefaults = new MultiUIDefaults(tables);
        LookAndFeel lookAndFeel;
        Vector<LookAndFeel> auxLookAndFeels = null;
        PropertyChangeSupport changeSupport;

        LookAndFeelInfo[] installedLAFs;

        UIDefaults getLookAndFeelDefaults() { return tables[0]; }
        void setLookAndFeelDefaults(UIDefaults x) { tables[0] = x; }

        UIDefaults getSystemDefaults() { return tables[1]; }
        void setSystemDefaults(UIDefaults x) { tables[1] = x; }

        public synchronized PropertyChangeSupport
                                 getPropertyChangeSupport(boolean create) {
            if (create && changeSupport == null) {
                changeSupport = new PropertyChangeSupport(UIManager.class);
            }
            return changeSupport;
        }
    }

    /* Lock object used in place of class object for synchronization. */
    private static final Object classLock = new Object();

    private static final LAFState lafState = new LAFState();

    private static LAFState getLAFState() {
        return lafState;
    }

    /* Keys used for the properties file in <java.home>/lib/swing.properties
     * on the JDK; read from the system properties here.
     */
    private static final String defaultLAFKey = "swing.defaultlaf";
    private static final String auxiliaryLAFsKey = "swing.auxiliarylaf";
    private static final String installedLAFsKey = "swing.installedlafs";

    /**
     * Returns the swing.installedlaf.&lt;id&gt;.&lt;attr&gt; key.
     */
    private static String makeInstalledLAFKey(String laf, String attr) {
        return "swing.installedlaf." + laf + "." + attr;
    }

    /**
     * Provides a little information about an installed
     * <code>LookAndFeel</code> for the sake of configuring a menu or
     * for initial application set up.
     *
     * @see UIManager#getInstalledLookAndFeels
     * @see LookAndFeel
     */
    public static class LookAndFeelInfo {
        private String name;
        private String className;

        /**
         * Constructs a <code>UIManager</code>s
         * <code>LookAndFeelInfo</code> object.
         *
         * @param name      a <code>String</code> specifying the name of
         *                      the look and feel
         * @param className a <code>String</code> specifying the name of
         *                      the class that implements the look and feel
         */
        public LookAndFeelInfo(String name, String className) {
            this.name = name;
            this.className = className;
        }

        /**
         * Returns the name of the look and feel in a form suitable
         * for a menu or other presentation
         * @return a <code>String</code> containing the name
         * @see LookAndFeel#getName
         */
        public String getName() {
            return name;
        }

        /**
         * Returns the name of the class that implements this look and feel.
         * @return the name of the class that implements this
         *              <code>LookAndFeel</code>
         * @see LookAndFeel
         */
        public String getClassName() {
            return className;
        }

        /**
         * Returns a string that displays and identifies this
         * object's properties.
         *
         * @return a <code>String</code> representation of this object
         */
        public String toString() {
            return getClass().getName() + "[" + getName() + " " + getClassName() + "]";
        }
    }

    /**
     * The default value of <code>installedLAFS</code> is used when no
     * <code>swing.installedlafs</code> property is set. The JDK also lists
     * Nimbus and CDE/Motif; neither is part of the shadow, and an entry
     * whose class cannot load would fail whoever picks it from a menu.
     */
    private static LookAndFeelInfo[] installedLAFs;

    static {
        ArrayList<LookAndFeelInfo> iLAFs = new ArrayList<LookAndFeelInfo>(4);
        iLAFs.add(new LookAndFeelInfo(
                      "Metal", "javax.swing.plaf.metal.MetalLookAndFeel"));
        installedLAFs = iLAFs.toArray(new LookAndFeelInfo[iLAFs.size()]);
    }


    /**
     * Returns an array of {@code LookAndFeelInfo}s representing the
     * {@code LookAndFeel} implementations currently available. The
     * <code>LookAndFeelInfo</code> objects can be used by an
     * application to construct a menu of look and feel options for
     * the user, or to determine which look and feel to set at startup
     * time.
     *
     * @return an array of <code>LookAndFeelInfo</code> objects
     * @see #setLookAndFeel
     */
    public static LookAndFeelInfo[] getInstalledLookAndFeels() {
        maybeInitialize();
        LookAndFeelInfo[] ilafs = getLAFState().installedLAFs;
        if (ilafs == null) {
            ilafs = installedLAFs;
        }
        LookAndFeelInfo[] rv = new LookAndFeelInfo[ilafs.length];
        System.arraycopy(ilafs, 0, rv, 0, ilafs.length);
        return rv;
    }


    /**
     * Sets the set of available look and feels. While this method does
     * not check to ensure all of the {@code LookAndFeelInfos} are
     * {@code non-null}, it is strongly recommended that only {@code non-null}
     * values are supplied in the {@code infos} array.
     *
     * @param infos set of <code>LookAndFeelInfo</code> objects specifying
     *        the available look and feels
     *
     * @see #getInstalledLookAndFeels
     * @throws NullPointerException if {@code infos} is {@code null}
     */
    public static void setInstalledLookAndFeels(LookAndFeelInfo[] infos)
        throws SecurityException
    {
        maybeInitialize();
        LookAndFeelInfo[] newInfos = new LookAndFeelInfo[infos.length];
        System.arraycopy(infos, 0, newInfos, 0, infos.length);
        getLAFState().installedLAFs = newInfos;
    }


    /**
     * Adds the specified look and feel to the set of available look
     * and feels. While this method allows a {@code null} {@code info},
     * it is strongly recommended that a {@code non-null} value be used.
     *
     * @param info a <code>LookAndFeelInfo</code> object that names the
     *          look and feel and identifies the class that implements it
     * @see #setInstalledLookAndFeels
     */
    public static void installLookAndFeel(LookAndFeelInfo info) {
        LookAndFeelInfo[] infos = getInstalledLookAndFeels();
        LookAndFeelInfo[] newInfos = new LookAndFeelInfo[infos.length + 1];
        System.arraycopy(infos, 0, newInfos, 0, infos.length);
        newInfos[infos.length] = info;
        setInstalledLookAndFeels(newInfos);
    }


    /**
     * Adds the specified look and feel to the set of available look
     * and feels. While this method does not check the
     * arguments in any way, it is strongly recommended that {@code
     * non-null} values be supplied.
     *
     * @param name descriptive name of the look and feel
     * @param className name of the class that implements the look and feel
     * @see #setInstalledLookAndFeels
     */
    public static void installLookAndFeel(String name, String className) {
        installLookAndFeel(new LookAndFeelInfo(name, className));
    }


    /**
     * Returns the current look and feel or <code>null</code>.
     *
     * @return current look and feel, or <code>null</code>
     * @see #setLookAndFeel
     */
    public static LookAndFeel getLookAndFeel() {
        maybeInitialize();
        return getLAFState().lookAndFeel;
    }

    // JDK 9+ (9): jdk17u src/java.desktop/share/classes/javax/swing/UIManager.java
    /**
     * Creates a supported built-in Java {@code LookAndFeel} specified
     * by the given {@code L&F name} name.
     *
     * @param name a {@code String} specifying the name of the built-in
     *             look and feel
     * @return the built-in {@code LookAndFeel} object
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws UnsupportedLookAndFeelException if the built-in Java {@code L&F}
     *         is not found for the given name or it is not supported by the
     *         underlying platform
     *
     * @see LookAndFeel#getName
     * @see LookAndFeel#isSupportedLookAndFeel
     *
     * @since 9
     */
    @SuppressWarnings("deprecation")
    public static LookAndFeel createLookAndFeel(String name)
            throws UnsupportedLookAndFeelException {
        if (name == null) {
            throw new NullPointerException("name");
        }
        // "Windows Classic" is for compatibility
        if ("Windows Classic".equals(name)) {
            name = "Windows";
        }
        for (LookAndFeelInfo info : getInstalledLookAndFeels()) {
            if (info.getName().equals(name)) {
                try {
                    LookAndFeel laf = (LookAndFeel) loadSystemClass(info.getClassName()).newInstance();
                    if (!laf.isSupportedLookAndFeel()) {
                        break;
                    }
                    return laf;
                } catch (ReflectiveOperationException ignore) {
                } catch (IllegalAccessError ignore) {
                }
            }
        }
        throw new UnsupportedLookAndFeelException(name);
    }

    /**
     * Sets the current look and feel to {@code newLookAndFeel}.
     * If the current look and feel is {@code non-null} {@code
     * uninitialize} is invoked on it. If {@code newLookAndFeel} is
     * {@code non-null}, {@code initialize} is invoked on it followed
     * by {@code getDefaults}. The defaults returned from {@code
     * newLookAndFeel.getDefaults()} replace those of the defaults
     * from the previous look and feel. If the {@code newLookAndFeel} is
     * {@code null}, the look and feel defaults are set to {@code null}.
     * <p>
     * A value of {@code null} can be used to set the look and feel
     * to {@code null}. As the {@code LookAndFeel} is required for
     * most of Swing to function, setting the {@code LookAndFeel} to
     * {@code null} is strongly discouraged.
     * <p>
     * This is a JavaBeans bound property.
     *
     * @param newLookAndFeel {@code LookAndFeel} to install
     * @throws UnsupportedLookAndFeelException if
     *          {@code newLookAndFeel} is {@code non-null} and
     *          {@code newLookAndFeel.isSupportedLookAndFeel()} returns
     *          {@code false}
     * @see #getLookAndFeel
     */
    public static void setLookAndFeel(LookAndFeel newLookAndFeel)
        throws UnsupportedLookAndFeelException
    {
        if ((newLookAndFeel != null) && !newLookAndFeel.isSupportedLookAndFeel()) {
            String s = newLookAndFeel.toString() + " not supported on this platform";
            throw new UnsupportedLookAndFeelException(s);
        }

        LAFState lafState = getLAFState();
        LookAndFeel oldLookAndFeel = lafState.lookAndFeel;
        if (oldLookAndFeel != null) {
            oldLookAndFeel.uninitialize();
        }

        lafState.lookAndFeel = newLookAndFeel;
        if (newLookAndFeel != null) {
            newLookAndFeel.initialize();
            lafState.setLookAndFeelDefaults(newLookAndFeel.getDefaults());
        }
        else {
            lafState.setLookAndFeelDefaults(null);
        }

        PropertyChangeSupport changeSupport = lafState.
                                         getPropertyChangeSupport(false);
        if (changeSupport != null) {
            changeSupport.firePropertyChange("lookAndFeel", oldLookAndFeel,
                                             newLookAndFeel);
        }
    }


    /**
     * Loads the {@code LookAndFeel} specified by the given class
     * name, using the current thread's context class loader, and
     * passes it to {@code setLookAndFeel(LookAndFeel)}.
     *
     * @param className  a string specifying the name of the class that implements
     *        the look and feel
     * @exception ClassNotFoundException if the <code>LookAndFeel</code>
     *           class could not be found
     * @exception InstantiationException if a new instance of the class
     *          couldn't be created
     * @exception IllegalAccessException if the class or initializer isn't accessible
     * @exception UnsupportedLookAndFeelException if
     *          <code>lnf.isSupportedLookAndFeel()</code> is false
     * @throws ClassCastException if {@code className} does not identify
     *         a class that extends {@code LookAndFeel}
     */
    @SuppressWarnings("deprecation")
    public static void setLookAndFeel(String className)
        throws ClassNotFoundException,
               InstantiationException,
               IllegalAccessException,
               UnsupportedLookAndFeelException
    {
        Class<?> lnfClass = loadSystemClass(className);
        setLookAndFeel((LookAndFeel)(lnfClass.newInstance()));
    }

    /**
     * What SwingUtilities.loadSystemClass does on the JDK: the context class
     * loader, then the loader of the shadow itself, which on Android is the
     * application loader that also holds FlatLaf and the client.
     */
    private static Class<?> loadSystemClass(String className) throws ClassNotFoundException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader != null) {
            try {
                return Class.forName(className, true, loader);
            } catch (ClassNotFoundException e) {
                // fall through to the shadow's own loader
            }
        }
        return Class.forName(className, true, UIManager.class.getClassLoader());
    }

    /**
     * Returns the name of the <code>LookAndFeel</code> class that implements
     * the native system look and feel if there is one, otherwise
     * the name of the default cross platform <code>LookAndFeel</code>
     * class. This value can be overriden by setting the
     * <code>swing.systemlaf</code> system property.
     *
     * @return the <code>String</code> of the <code>LookAndFeel</code>
     *          class
     *
     * @see #setLookAndFeel
     * @see #getCrossPlatformLookAndFeelClassName
     */
    public static String getSystemLookAndFeelClassName() {
        String systemLAF = System.getProperty("swing.systemlaf");
        if (systemLAF != null) {
            return systemLAF;
        }
        // Android has no native Swing look and feel.
        return getCrossPlatformLookAndFeelClassName();
    }


    /**
     * Returns the name of the <code>LookAndFeel</code> class that implements
     * the default cross platform look and feel -- the Java
     * Look and Feel (JLF).  This value can be overriden by setting the
     * <code>swing.crossplatformlaf</code> system property.
     *
     * @return  a string with the JLF implementation-class
     * @see #setLookAndFeel
     * @see #getSystemLookAndFeelClassName
     */
    public static String getCrossPlatformLookAndFeelClassName() {
        String laf = System.getProperty("swing.crossplatformlaf");
        if (laf != null) {
            return laf;
        }
        return "javax.swing.plaf.metal.MetalLookAndFeel";
    }


    /**
     * Returns the defaults. The returned defaults resolve using the
     * logic specified in the class documentation.
     *
     * @return a <code>UIDefaults</code> object containing the default values
     */
    public static UIDefaults getDefaults() {
        maybeInitialize();
        return getLAFState().multiUIDefaults;
    }

    /**
     * Returns a font from the defaults. If the value for {@code key} is
     * not a {@code Font}, {@code null} is returned.
     *
     * @param key  an <code>Object</code> specifying the font
     * @return the <code>Font</code> object
     * @throws NullPointerException if {@code key} is {@code null}
     */
    public static Font getFont(Object key) {
        return getDefaults().getFont(key);
    }

    /**
     * Returns a font from the defaults that is appropriate
     * for the given locale. If the value for {@code key} is
     * not a {@code Font}, {@code null} is returned.
     *
     * @param key  an <code>Object</code> specifying the font
     * @param l the <code>Locale</code> for which the font is desired; refer
     *        to {@code UIDefaults} for details on how a {@code null}
     *        {@code Locale} is handled
     * @return the <code>Font</code> object
     * @throws NullPointerException if {@code key} is {@code null}
     * @since 1.4
     */
    public static Font getFont(Object key, Locale l) {
        return getDefaults().getFont(key,l);
    }

    /**
     * Returns a color from the defaults. If the value for {@code key} is
     * not a {@code Color}, {@code null} is returned.
     *
     * @param key  an <code>Object</code> specifying the color
     * @return the <code>Color</code> object
     * @throws NullPointerException if {@code key} is {@code null}
     */
    public static Color getColor(Object key) {
        return getDefaults().getColor(key);
    }

    /**
     * Returns a color from the defaults that is appropriate
     * for the given locale. If the value for {@code key} is
     * not a {@code Color}, {@code null} is returned.
     *
     * @param key  an <code>Object</code> specifying the color
     * @param l the <code>Locale</code> for which the color is desired; refer
     *        to {@code UIDefaults} for details on how a {@code null}
     *        {@code Locale} is handled
     * @return the <code>Color</code> object
     * @throws NullPointerException if {@code key} is {@code null}
     * @since 1.4
     */
    public static Color getColor(Object key, Locale l) {
        return getDefaults().getColor(key,l);
    }

    /**
     * Returns an <code>Icon</code> from the defaults. If the value for
     * {@code key} is not an {@code Icon}, {@code null} is returned.
     *
     * @param key  an <code>Object</code> specifying the icon
     * @return the <code>Icon</code> object
     * @throws NullPointerException if {@code key} is {@code null}
     */
    public static Icon getIcon(Object key) {
        return getDefaults().getIcon(key);
    }

    /**
     * Returns an <code>Icon</code> from the defaults that is appropriate
     * for the given locale. If the value for
     * {@code key} is not an {@code Icon}, {@code null} is returned.
     *
     * @param key  an <code>Object</code> specifying the icon
     * @param l the <code>Locale</code> for which the icon is desired; refer
     *        to {@code UIDefaults} for details on how a {@code null}
     *        {@code Locale} is handled
     * @return the <code>Icon</code> object
     * @throws NullPointerException if {@code key} is {@code null}
     * @since 1.4
     */
    public static Icon getIcon(Object key, Locale l) {
        return getDefaults().getIcon(key,l);
    }

    /**
     * Returns a border from the defaults. If the value for
     * {@code key} is not a {@code Border}, {@code null} is returned.
     *
     * @param key  an <code>Object</code> specifying the border
     * @return the <code>Border</code> object
     * @throws NullPointerException if {@code key} is {@code null}
     */
    public static Border getBorder(Object key) {
        return getDefaults().getBorder(key);
    }

    /**
     * Returns a border from the defaults that is appropriate
     * for the given locale.  If the value for
     * {@code key} is not a {@code Border}, {@code null} is returned.
     *
     * @param key  an <code>Object</code> specifying the border
     * @param l the <code>Locale</code> for which the border is desired; refer
     *        to {@code UIDefaults} for details on how a {@code null}
     *        {@code Locale} is handled
     * @return the <code>Border</code> object
     * @throws NullPointerException if {@code key} is {@code null}
     * @since 1.4
     */
    public static Border getBorder(Object key, Locale l) {
        return getDefaults().getBorder(key,l);
    }

    /**
     * Returns a string from the defaults. If the value for
     * {@code key} is not a {@code String}, {@code null} is returned.
     *
     * @param key  an <code>Object</code> specifying the string
     * @return the <code>String</code>
     * @throws NullPointerException if {@code key} is {@code null}
     */
    public static String getString(Object key) {
        return getDefaults().getString(key);
    }

    /**
     * Returns a string from the defaults that is appropriate for the
     * given locale.  If the value for
     * {@code key} is not a {@code String}, {@code null} is returned.
     *
     * @param key  an <code>Object</code> specifying the string
     * @param l the <code>Locale</code> for which the string is desired; refer
     *        to {@code UIDefaults} for details on how a {@code null}
     *        {@code Locale} is handled
     * @return the <code>String</code>
     * @since 1.4
     * @throws NullPointerException if {@code key} is {@code null}
     */
    public static String getString(Object key, Locale l) {
        return getDefaults().getString(key,l);
    }

    /**
     * Returns an integer from the defaults. If the value for
     * {@code key} is not an {@code Integer}, or does not exist,
     * {@code 0} is returned.
     *
     * @param key  an <code>Object</code> specifying the int
     * @return the int
     * @throws NullPointerException if {@code key} is {@code null}
     * @since 1.4
     */
    public static int getInt(Object key) {
        return getDefaults().getInt(key);
    }

    /**
     * Returns an integer from the defaults that is appropriate
     * for the given locale. If the value for
     * {@code key} is not an {@code Integer}, or does not exist,
     * {@code 0} is returned.
     *
     * @param key  an <code>Object</code> specifying the int
     * @param l the <code>Locale</code> for which the int is desired; refer
     *        to {@code UIDefaults} for details on how a {@code null}
     *        {@code Locale} is handled
     * @return the int
     * @throws NullPointerException if {@code key} is {@code null}
     * @since 1.4
     */
    public static int getInt(Object key, Locale l) {
        return getDefaults().getInt(key,l);
    }

    /**
     * Returns a boolean from the defaults which is associated with
     * the key value. If the key is not found or the key doesn't represent
     * a boolean value then {@code false} is returned.
     *
     * @param key  an <code>Object</code> specifying the key for the desired boolean value
     * @return the boolean value corresponding to the key
     * @throws NullPointerException if {@code key} is {@code null}
     * @since 1.4
     */
    public static boolean getBoolean(Object key) {
        return getDefaults().getBoolean(key);
    }

    /**
     * Returns a boolean from the defaults which is associated with
     * the key value and the given <code>Locale</code>. If the key is not
     * found or the key doesn't represent
     * a boolean value then {@code false} will be returned.
     *
     * @param key  an <code>Object</code> specifying the key for the desired
     *             boolean value
     * @param l the <code>Locale</code> for which the boolean is desired; refer
     *        to {@code UIDefaults} for details on how a {@code null}
     *        {@code Locale} is handled
     * @return the boolean value corresponding to the key
     * @throws NullPointerException if {@code key} is {@code null}
     * @since 1.4
     */
    public static boolean getBoolean(Object key, Locale l) {
        return getDefaults().getBoolean(key,l);
    }

    /**
     * Returns an <code>Insets</code> object from the defaults. If the value
     * for {@code key} is not an {@code Insets}, {@code null} is returned.
     *
     * @param key  an <code>Object</code> specifying the <code>Insets</code> object
     * @return the <code>Insets</code> object
     * @throws NullPointerException if {@code key} is {@code null}
     */
    public static Insets getInsets(Object key) {
        return getDefaults().getInsets(key);
    }

    /**
     * Returns an <code>Insets</code> object from the defaults that is
     * appropriate for the given locale. If the value
     * for {@code key} is not an {@code Insets}, {@code null} is returned.
     *
     * @param key  an <code>Object</code> specifying the <code>Insets</code> object
     * @param l the <code>Locale</code> for which the object is desired; refer
     *        to {@code UIDefaults} for details on how a {@code null}
     *        {@code Locale} is handled
     * @return the <code>Insets</code> object
     * @throws NullPointerException if {@code key} is {@code null}
     * @since 1.4
     */
    public static Insets getInsets(Object key, Locale l) {
        return getDefaults().getInsets(key,l);
    }

    /**
     * Returns a dimension from the defaults. If the value
     * for {@code key} is not a {@code Dimension}, {@code null} is returned.
     *
     * @param key  an <code>Object</code> specifying the dimension object
     * @return the <code>Dimension</code> object
     * @throws NullPointerException if {@code key} is {@code null}
     */
    public static Dimension getDimension(Object key) {
        return getDefaults().getDimension(key);
    }

    /**
     * Returns a dimension from the defaults that is appropriate
     * for the given locale. If the value
     * for {@code key} is not a {@code Dimension}, {@code null} is returned.
     *
     * @param key  an <code>Object</code> specifying the dimension object
     * @param l the <code>Locale</code> for which the object is desired; refer
     *        to {@code UIDefaults} for details on how a {@code null}
     *        {@code Locale} is handled
     * @return the <code>Dimension</code> object
     * @throws NullPointerException if {@code key} is {@code null}
     * @since 1.4
     */
    public static Dimension getDimension(Object key, Locale l) {
        return getDefaults().getDimension(key,l);
    }

    /**
     * Returns an object from the defaults.
     *
     * @param key  an <code>Object</code> specifying the desired object
     * @return the <code>Object</code>
     * @throws NullPointerException if {@code key} is {@code null}
     */
    public static Object get(Object key) {
        return getDefaults().get(key);
    }

    /**
     * Returns an object from the defaults that is appropriate for
     * the given locale.
     *
     * @param key  an <code>Object</code> specifying the desired object
     * @param l the <code>Locale</code> for which the object is desired; refer
     *        to {@code UIDefaults} for details on how a {@code null}
     *        {@code Locale} is handled
     * @return the <code>Object</code>
     * @throws NullPointerException if {@code key} is {@code null}
     * @since 1.4
     */
    public static Object get(Object key, Locale l) {
        return getDefaults().get(key,l);
    }

    /**
     * Stores an object in the developer defaults. This is a cover method
     * for {@code getDefaults().put(key, value)}. This only effects the
     * developer defaults, not the system or look and feel defaults.
     *
     * @param key    an <code>Object</code> specifying the retrieval key
     * @param value  the <code>Object</code> to store; refer to
     *               {@code UIDefaults} for details on how {@code null} is
     *               handled
     * @return the <code>Object</code> returned by {@link UIDefaults#put}
     * @throws NullPointerException if {@code key} is {@code null}
     * @see UIDefaults#put
     */
    public static Object put(Object key, Object value) {
        return getDefaults().put(key, value);
    }

    /**
     * Returns the appropriate {@code ComponentUI} implementation for
     * {@code target}. Typically, this is a cover for
     * {@code getDefaults().getUI(target)}.
     * <p>
     * The JDK multiplexes the auxiliary look and feels through
     * {@code javax.swing.plaf.multi.MultiLookAndFeel}, a class the shadow
     * does not have; as on a JDK where that class fails to load, the
     * delegate comes from the primary look and feel alone.
     *
     * @param target the <code>JComponent</code> to return the
     *        {@code ComponentUI} for
     * @return the <code>ComponentUI</code> object for {@code target}
     * @throws NullPointerException if {@code target} is {@code null}
     * @see UIDefaults#getUI
     */
    public static ComponentUI getUI(JComponent target) {
        maybeInitialize();
        return getDefaults().getUI(target);
    }


    /**
     * Returns the {@code UIDefaults} from the current look and feel,
     * that were obtained at the time the look and feel was installed.
     * <p>
     * In general, developers should use the {@code UIDefaults} returned from
     * {@code getDefaults()}. As the current look and feel may expect
     * certain values to exist, altering the {@code UIDefaults} returned
     * from this method could have unexpected results.
     *
     * @return <code>UIDefaults</code> from the current look and feel
     * @see #getDefaults
     * @see #setLookAndFeel(LookAndFeel)
     * @see LookAndFeel#getDefaults
     */
    public static UIDefaults getLookAndFeelDefaults() {
        maybeInitialize();
        return getLAFState().getLookAndFeelDefaults();
    }

    /**
     * Adds a <code>LookAndFeel</code> to the list of auxiliary look and feels.
     * The auxiliary look and feels tell the multiplexing look and feel what
     * other <code>LookAndFeel</code> classes for a component instance are to be used
     * in addition to the default <code>LookAndFeel</code> class when creating a
     * multiplexing UI.  The change will only take effect when a new
     * UI class is created or when the default look and feel is changed
     * on a component instance.
     * <p>Note these are not the same as the installed look and feels.
     *
     * @param laf the <code>LookAndFeel</code> object
     * @see #removeAuxiliaryLookAndFeel
     * @see #setLookAndFeel
     * @see #getAuxiliaryLookAndFeels
     * @see #getInstalledLookAndFeels
     */
    public static void addAuxiliaryLookAndFeel(LookAndFeel laf) {
        maybeInitialize();

        if (!laf.isSupportedLookAndFeel()) {
            // Ideally we would throw an exception here, but it's too late
            // for that.
            return;
        }
        Vector<LookAndFeel> v = getLAFState().auxLookAndFeels;
        if (v == null) {
            v = new Vector<LookAndFeel>();
        }

        if (!v.contains(laf)) {
            v.addElement(laf);
            laf.initialize();
            getLAFState().auxLookAndFeels = v;
        }
    }


    /**
     * Removes a <code>LookAndFeel</code> from the list of auxiliary look and feels.
     * The auxiliary look and feels tell the multiplexing look and feel what
     * other <code>LookAndFeel</code> classes for a component instance are to be used
     * in addition to the default <code>LookAndFeel</code> class when creating a
     * multiplexing UI.  The change will only take effect when a new
     * UI class is created or when the default look and feel is changed
     * on a component instance.
     * <p>Note these are not the same as the installed look and feels.
     *
     * @param laf the {@code LookAndFeel} to be removed
     * @return true if the <code>LookAndFeel</code> was removed from the list
     * @see #removeAuxiliaryLookAndFeel
     * @see #getAuxiliaryLookAndFeels
     * @see #setLookAndFeel
     * @see #getInstalledLookAndFeels
     */
    public static boolean removeAuxiliaryLookAndFeel(LookAndFeel laf) {
        maybeInitialize();

        boolean result;

        Vector<LookAndFeel> v = getLAFState().auxLookAndFeels;
        if ((v == null) || (v.size() == 0)) {
            return false;
        }

        result = v.removeElement(laf);
        if (result) {
            if (v.size() == 0) {
                getLAFState().auxLookAndFeels = null;
            } else {
                getLAFState().auxLookAndFeels = v;
            }
        }
        laf.uninitialize();

        return result;
    }


    /**
     * Returns the list of auxiliary look and feels (can be <code>null</code>).
     * The auxiliary look and feels tell the multiplexing look and feel what
     * other <code>LookAndFeel</code> classes for a component instance are
     * to be used in addition to the default LookAndFeel class when creating a
     * multiplexing UI.
     * <p>Note these are not the same as the installed look and feels.
     *
     * @return list of auxiliary <code>LookAndFeel</code>s or <code>null</code>
     * @see #addAuxiliaryLookAndFeel
     * @see #removeAuxiliaryLookAndFeel
     * @see #setLookAndFeel
     * @see #getInstalledLookAndFeels
     */
    public static LookAndFeel[] getAuxiliaryLookAndFeels() {
        maybeInitialize();

        Vector<LookAndFeel> v = getLAFState().auxLookAndFeels;
        if ((v == null) || (v.size() == 0)) {
            return null;
        }
        else {
            LookAndFeel[] rv = new LookAndFeel[v.size()];
            for (int i = 0; i < rv.length; i++) {
                rv[i] = v.elementAt(i);
            }
            return rv;
        }
    }


    /**
     * Adds a <code>PropertyChangeListener</code> to the listener list.
     * The listener is registered for all properties.
     *
     * @param listener  the <code>PropertyChangeListener</code> to be added
     * @see java.beans.PropertyChangeSupport
     */
    public static void addPropertyChangeListener(PropertyChangeListener listener)
    {
        synchronized (classLock) {
            getLAFState().getPropertyChangeSupport(true).
                             addPropertyChangeListener(listener);
        }
    }


    /**
     * Removes a <code>PropertyChangeListener</code> from the listener list.
     * This removes a <code>PropertyChangeListener</code> that was registered
     * for all properties.
     *
     * @param listener  the <code>PropertyChangeListener</code> to be removed
     * @see java.beans.PropertyChangeSupport
     */
    public static void removePropertyChangeListener(PropertyChangeListener listener)
    {
        synchronized (classLock) {
            getLAFState().getPropertyChangeSupport(true).
                          removePropertyChangeListener(listener);
        }
    }


    /**
     * Returns an array of all the <code>PropertyChangeListener</code>s added
     * to this UIManager with addPropertyChangeListener().
     *
     * @return all of the <code>PropertyChangeListener</code>s added or an empty
     *         array if no listeners have been added
     * @since 1.4
     */
    public static PropertyChangeListener[] getPropertyChangeListeners() {
        synchronized(classLock) {
            return getLAFState().getPropertyChangeSupport(true).
                      getPropertyChangeListeners();
        }
    }

    /**
     * If a <code>swing.installedlafs</code> property with a comma
     * separated list of look and feel ids was specified, then initialize
     * the <code>installedLAFs</code> from the
     * <code>swing.installedlaf.&lt;id&gt;.name</code> and
     * <code>.class</code> properties.
     */
    private static void initializeInstalledLAFs() {
        String ilafsString = System.getProperty(installedLAFsKey);
        if (ilafsString == null) {
            return;
        }

        /* Create a vector that contains the value of the swing.installedlafs
         * property.  For example given "swing.installedlafs=motif,windows"
         * lafs = {"motif", "windows"}.
         */
        List<String> lafs = new ArrayList<String>();
        StringTokenizer st = new StringTokenizer(ilafsString, ",", false);
        while (st.hasMoreTokens()) {
            lafs.add(st.nextToken());
        }

        /* Look up the name and class for each name in the "swing.installedlafs"
         * list.  If they both exist then add a LookAndFeelInfo to
         * the installedLafs array.
         */
        List<LookAndFeelInfo> ilafs = new ArrayList<LookAndFeelInfo>(lafs.size());
        for (String laf : lafs) {
            String name = System.getProperty(makeInstalledLAFKey(laf, "name"), laf);
            String cls = System.getProperty(makeInstalledLAFKey(laf, "class"));
            if (cls != null) {
                ilafs.add(new LookAndFeelInfo(name, cls));
            }
        }

        LookAndFeelInfo[] installedLAFs = new LookAndFeelInfo[ilafs.size()];
        for (int i = 0; i < ilafs.size(); i++) {
            installedLAFs[i] = ilafs.get(i);
        }
        getLAFState().installedLAFs = installedLAFs;
    }


    /**
     * If the user has specified a default look and feel, use that.
     * Otherwise use the look and feel that's native to this platform.
     * If this code is called after the application has explicitly
     * set it's look and feel, do nothing.
     */
    private static void initializeDefaultLAF() {
        if (getLAFState().lookAndFeel != null) {
            return;
        }

        String lafName = System.getProperty(defaultLAFKey,
                                            getCrossPlatformLookAndFeelClassName());
        try {
            setLookAndFeel(lafName);
        } catch (Exception e) {
            throw new Error("Cannot load " + lafName);
        }
    }


    @SuppressWarnings("deprecation")
    private static void initializeAuxiliaryLAFs() {
        String auxLookAndFeelNames = System.getProperty(auxiliaryLAFsKey);
        if (auxLookAndFeelNames == null) {
            return;
        }

        Vector<LookAndFeel> auxLookAndFeels = new Vector<LookAndFeel>();

        StringTokenizer p = new StringTokenizer(auxLookAndFeelNames,",");

        // Try to load each LookAndFeel subclass in the list.

        while (p.hasMoreTokens()) {
            String className = p.nextToken();
            try {
                Class<?> lnfClass = loadSystemClass(className);
                LookAndFeel newLAF = (LookAndFeel)lnfClass.newInstance();
                newLAF.initialize();
                auxLookAndFeels.addElement(newLAF);
            }
            catch (Exception e) {
                System.err.println("UIManager: failed loading auxiliary look and feel " + className);
            }
        }

        /* If there were problems and no auxiliary look and feels were
         * loaded, make sure we reset auxLookAndFeels to null.
         */
        if (auxLookAndFeels.size() == 0) {
            auxLookAndFeels = null;
        }

        getLAFState().auxLookAndFeels = auxLookAndFeels;
    }


    private static void initializeSystemDefaults() {
        Object[] defaults = {
            "FocusManagerClassName", "javax.swing.DefaultFocusManager"
        };
        getLAFState().setSystemDefaults(new UIDefaults(defaults));
    }


    /*
     * This method is called before any code that depends on the
     * <code>AppContext</code> specific LAFState object runs.
     */
    private static void maybeInitialize() {
        synchronized (classLock) {
            if (!getLAFState().initialized) {
                getLAFState().initialized = true;
                initialize();
            }
        }
    }


    /*
     * Only called by maybeInitialize().
     */
    private static void initialize() {
        initializeSystemDefaults();
        initializeDefaultLAF();
        initializeAuxiliaryLAFs();
        initializeInstalledLAFs();
    }
}
