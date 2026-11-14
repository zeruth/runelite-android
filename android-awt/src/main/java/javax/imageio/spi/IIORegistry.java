/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

package javax.imageio.spi;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Vector;
import com.sun.imageio.spi.FileImageInputStreamSpi;
import com.sun.imageio.spi.FileImageOutputStreamSpi;
import com.sun.imageio.spi.InputStreamImageInputStreamSpi;
import com.sun.imageio.spi.OutputStreamImageOutputStreamSpi;
import com.sun.imageio.spi.RAFImageInputStreamSpi;
import com.sun.imageio.spi.RAFImageOutputStreamSpi;

/**
 * A registry for service provider instances.  Service provider
 * classes may be detected at run time by means of meta-information in
 * the JAR files containing them.  The intent is that it be relatively
 * inexpensive to load and inspect all available service provider
 * classes.  These classes may them be used to locate and instantiate
 * more heavyweight classes that will perform actual work, in this
 * case instances of <code>ImageReader</code>,
 * <code>ImageWriter</code>, <code>ImageTranscoder</code>,
 * <code>ImageInputStream</code>, and <code>ImageOutputStream</code>.
 *
 * <p> Service providers found on the system classpath (typically
 * the <code>lib/ext</code> directory in the Java
 * installation directory) are automatically loaded as soon as this class is
 * instantiated.
 *
 * <p> When the <code>registerApplicationClasspathSpis</code> method
 * is called, service provider instances declared in the
 * meta-information section of JAR files on the application class path
 * are loaded.  To declare a service provider, a <code>services</code>
 * subdirectory is placed within the <code>META-INF</code> directory
 * that is present in every JAR file.  This directory contains a file
 * for each service provider interface that has one or more
 * implementation classes present in the JAR file.  For example, if
 * the JAR file contained a class named
 * <code>com.mycompany.imageio.MyFormatReaderSpi</code> which
 * implements the <code>ImageReaderSpi</code> interface, the JAR file
 * would contain a file named:
 *
 * <pre>
 * META-INF/services/javax.imageio.spi.ImageReaderSpi
 * </pre>
 *
 * containing the line:
 *
 * <pre>
 * com.mycompany.imageio.MyFormatReaderSpi
 * </pre>
 *
 * <p> The service provider classes are intended to be lightweight
 * and quick to load.  Implementations of these interfaces
 * should avoid complex dependencies on other classes and on
 * native code.
 *
 * <p> It is also possible to manually add service providers not found
 * automatically, as well as to remove those that are using the
 * interfaces of the <code>ServiceRegistry</code> class.  Thus
 * the application may customize the contents of the registry as it
 * sees fit.
 *
 * <p> For more details on declaring service providers, and the JAR
 * format in general, see the <a
 * href="{@docRoot}/../technotes/guides/jar/jar.html">
 * JAR File Specification</a>.
 *
 */
public final class IIORegistry extends ServiceRegistry {

    /**
     * A <code>Vector</code> containing the valid IIO registry
     * categories (superinterfaces) to be used in the constructor.
     */
    private static final Vector initialCategories = new Vector(5);

    static {
        initialCategories.add(ImageReaderSpi.class);
        initialCategories.add(ImageWriterSpi.class);
        initialCategories.add(ImageTranscoderSpi.class);
        initialCategories.add(ImageInputStreamSpi.class);
        initialCategories.add(ImageOutputStreamSpi.class);
    }

    /**
     * Set up the valid service provider categories and automatically
     * register all available service providers.
     *
     * <p> The constructor is private in order to prevent creation of
     * additional instances.
     */
    private IIORegistry() {
        super(initialCategories.iterator());
        registerStandardSpis();
        registerApplicationClasspathSpis();
    }

    /**
     * Returns the default <code>IIORegistry</code> instance used by
     * the Image I/O API.  This instance should be used for all
     * registry functions.
     *
     * <p> Each <code>ThreadGroup</code> will receive its own
     * instance; this allows different <code>Applet</code>s in the
     * same browser (for example) to each have their own registry.
     *
     * @return the default registry for the current
     * <code>ThreadGroup</code>.
     */
    public static IIORegistry getDefaultInstance() {
        // There is no AppContext on Android: one process, one registry.
        synchronized (IIORegistry.class) {
            if (defaultInstance == null) {
                defaultInstance = new IIORegistry();
            }
            return defaultInstance;
        }
    }

    private static IIORegistry defaultInstance;

    /**
     * The reader/writer plugins of the shadow tree, by class name so the registry
     * compiles and runs before (or without) each plugin: a name whose class is absent
     * is skipped. This list replaces the JDK's {@code ServiceLoader.loadInstalled}
     * scan of {@code lib/ext}, which has no counterpart in an APK.
     */
    private static final String[] BUILT_IN_PLUGINS = {
        "com.sun.imageio.plugins.gif.GIFImageReaderSpi",
        "com.sun.imageio.plugins.gif.GIFImageWriterSpi",
        "com.sun.imageio.plugins.bmp.BMPImageReaderSpi",
        "com.sun.imageio.plugins.bmp.BMPImageWriterSpi",
        "com.sun.imageio.plugins.wbmp.WBMPImageReaderSpi",
        "com.sun.imageio.plugins.wbmp.WBMPImageWriterSpi",
        "com.sun.imageio.plugins.png.PNGImageReaderSpi",
        "com.sun.imageio.plugins.png.PNGImageWriterSpi",
        "com.sun.imageio.plugins.jpeg.JPEGImageReaderSpi",
        "com.sun.imageio.plugins.jpeg.JPEGImageWriterSpi",
        // Android extra: the platform codec reads and writes WebP.
        "com.sun.imageio.plugins.webp.WebPImageReaderSpi",
        "com.sun.imageio.plugins.webp.WebPImageWriterSpi",
    };

    private void registerStandardSpis() {
        registerServiceProvider(new FileImageInputStreamSpi());
        registerServiceProvider(new FileImageOutputStreamSpi());
        registerServiceProvider(new InputStreamImageInputStreamSpi());
        registerServiceProvider(new OutputStreamImageOutputStreamSpi());
        registerServiceProvider(new RAFImageInputStreamSpi());
        registerServiceProvider(new RAFImageOutputStreamSpi());
    }

    private void registerBuiltInPlugins() {
        for (String name : BUILT_IN_PLUGINS) {
            Object provider;
            try {
                Class<?> c = Class.forName(name, true,
                                           IIORegistry.class.getClassLoader());
                provider = c.newInstance();
            } catch (ReflectiveOperationException | LinkageError e) {
                continue;
            }
            // Re-registering an already present provider class is a no-op apart
            // from replacing the instance, so a repeated scanForPlugins is harmless.
            if (getServiceProviderByClass(provider.getClass()) == null) {
                registerServiceProvider(provider);
            }
        }
    }

    /**
     * Registers all available service providers found on the
     * application class path, using the default
     * <code>ClassLoader</code>.  This method is typically invoked by
     * the <code>ImageIO.scanForPlugins</code> method.
     *
     * <p> On Android the application class path is the APK, whose plugins are
     * the built-in list; a provider of a downloaded plugin registers itself
     * with {@link #registerServiceProvider(Object)}. The JDK's
     * {@code META-INF/services} scan is not run: dexed hub jars do not carry
     * their service files, and a malformed one would fail every ImageIO call.
     *
     * @see javax.imageio.ImageIO#scanForPlugins
     * @see ClassLoader#getResources
     */
    public void registerApplicationClasspathSpis() {
        registerBuiltInPlugins();
    }
}
