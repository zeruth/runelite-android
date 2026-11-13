/*
 * Copyright (c) 1996, 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.awt.datatransfer;

import java.io.ByteArrayInputStream;
import java.io.CharArrayReader;
import java.io.Externalizable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OptionalDataException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A {@code DataFlavor} provides meta information about data. {@code DataFlavor}
 * is typically used to access data on the clipboard, or during
 * a drag and drop operation.
 * <p>
 * An instance of {@code DataFlavor} encapsulates a content type as
 * defined in <a href="http://www.ietf.org/rfc/rfc2045.txt">RFC 2045</a>
 * and <a href="http://www.ietf.org/rfc/rfc2046.txt">RFC 2046</a>.
 * A content type is typically referred to as a MIME type.
 * <p>
 * A content type consists of a media type (referred
 * to as the primary type), a subtype, and optional parameters. See
 * <a href="http://www.ietf.org/rfc/rfc2045.txt">RFC 2045</a>
 * for details on the syntax of a MIME type.
 * <p>
 * The JRE data transfer implementation interprets the parameter &quot;class&quot;
 * of a MIME type as <B>a representation class</b>.
 * The representation class reflects the class of the object being
 * transferred. In other words, the representation class is the type of
 * object returned by {@link Transferable#getTransferData}.
 * For example, the MIME type of {@link #imageFlavor} is
 * {@code "image/x-java-image;class=java.awt.Image"},
 * the primary type is {@code image}, the subtype is
 * {@code x-java-image}, and the representation class is
 * {@code java.awt.Image}. When {@code getTransferData} is invoked
 * with a {@code DataFlavor} of {@code imageFlavor}, an instance of
 * {@code java.awt.Image} is returned.
 * It's important to note that {@code DataFlavor} does no error checking
 * against the representation class. It is up to consumers of
 * {@code DataFlavor}, such as {@code Transferable}, to honor the representation
 * class.
 * <br>
 * Note, if you do not specify the representation class when
 * creating a {@code DataFlavor}, the default
 * representation class is used. See appropriate documentation for
 * {@code DataFlavor}'s constructors.
 * <p>
 * Also, {@code DataFlavor} instances with the &quot;text&quot; primary
 * MIME type may have a &quot;charset&quot; parameter. Refer to
 * <a href="http://www.ietf.org/rfc/rfc2046.txt">RFC 2046</a> and
 * {@link #selectBestTextFlavor} for details on &quot;text&quot; MIME types
 * and the &quot;charset&quot; parameter.
 * <p>
 * Equality of {@code DataFlavors} is determined by the primary type,
 * subtype, and representation class. Refer to {@link #equals(DataFlavor)} for
 * details. When determining equality, any optional parameters are ignored.
 * For example, the following produces two {@code DataFlavors} that
 * are considered identical:
 * <pre>
 *   DataFlavor flavor1 = new DataFlavor(Object.class, &quot;X-test/test; class=&lt;java.lang.Object&gt;; foo=bar&quot;);
 *   DataFlavor flavor2 = new DataFlavor(Object.class, &quot;X-test/test; class=&lt;java.lang.Object&gt;; x=y&quot;);
 *   // The following returns true.
 *   flavor1.equals(flavor2);
 * </pre>
 * As mentioned, {@code flavor1} and {@code flavor2} are considered identical.
 * As such, asking a {@code Transferable} for either {@code DataFlavor} returns
 * the same results.
 * <p>
 * The text-flavor rules (charset support per subtype, the preference order of
 * {@link #selectBestTextFlavor}) live in the nested {@code TextFlavors} class:
 * OpenJDK keeps them in {@code sun.datatransfer.DataFlavorUtil}, which this
 * module does not ship.
 *
 * @author      Blake Sullivan
 * @author      Laurence P. G. Cable
 * @author      Jeff Dunn
 */
public class DataFlavor implements Externalizable, Cloneable {

    private static final long serialVersionUID = 8367026044764648243L;
    private static final Class<InputStream> ioInputStreamClass = InputStream.class;

    /**
     * Tries to load a class from: the bootstrap loader, the system loader,
     * the context loader (if one is present) and finally the loader specified.
     *
     * @param className the name of the class to be loaded
     * @param fallback the fallback loader
     * @return the class loaded
     * @exception ClassNotFoundException if class is not found
     */
    protected final static Class<?> tryToLoadClass(String className,
                                                   ClassLoader fallback)
        throws ClassNotFoundException
    {
        try {
            ClassLoader loader = ClassLoader.getSystemClassLoader();
            try {
                // bootstrap class loader and system class loader if present
                return Class.forName(className, true, loader);
            }
            catch (ClassNotFoundException exception) {
                // thread context class loader if and only if present
                loader = Thread.currentThread().getContextClassLoader();
                if (loader != null) {
                    try {
                        return Class.forName(className, true, loader);
                    }
                    catch (ClassNotFoundException e) {
                        // fallback to user's class loader
                    }
                }
            }
        } catch (SecurityException exception) {
            // ignore secured class loaders
        }
        // On Android the system loader only sees the boot class path and the
        // context loader may be unset off the main thread: the shadow's own
        // loader (the APK's) is what finds java.awt.Image and plugin classes.
        if (fallback == null) {
            fallback = DataFlavor.class.getClassLoader();
        }
        return Class.forName(className, true, fallback);
    }

    /*
     * private initializer
     */
    static private DataFlavor createConstant(Class<?> rc, String prn) {
        try {
            return new DataFlavor(rc, prn);
        } catch (Exception e) {
            return null;
        }
    }

    /*
     * private initializer
     */
    static private DataFlavor createConstant(String mt, String prn) {
        try {
            return new DataFlavor(mt, prn);
        } catch (Exception e) {
            return null;
        }
    }

    /*
     * private initializer
     */
    static private DataFlavor initHtmlDataFlavor(String htmlFlavorType) {
        try {
            return new DataFlavor ("text/html; class=java.lang.String;document=" +
                                       htmlFlavorType + ";charset=Unicode");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The <code>DataFlavor</code> representing a Java Unicode String class,
     * where:
     * <pre>
     *     representationClass = java.lang.String
     *     mimeType           = "application/x-java-serialized-object"
     * </pre>
     */
    public static final DataFlavor stringFlavor = createConstant(java.lang.String.class, "Unicode String");

    /**
     * The <code>DataFlavor</code> representing a Java Image class,
     * where:
     * <pre>
     *     representationClass = java.awt.Image
     *     mimeType            = "image/x-java-image"
     * </pre>
     */
    public static final DataFlavor imageFlavor = createConstant("image/x-java-image; class=java.awt.Image", "Image");

    /**
     * The <code>DataFlavor</code> representing plain text with Unicode
     * encoding, where:
     * <pre>
     *     representationClass = InputStream
     *     mimeType            = "text/plain; charset=unicode"
     * </pre>
     *
     * @deprecated as of 1.3. Use <code>DataFlavor.getReaderForText(Transferable)</code>
     *             instead of <code>Transferable.getTransferData(DataFlavor.plainTextFlavor)</code>.
     */
    @Deprecated
    public static final DataFlavor plainTextFlavor = createConstant("text/plain; charset=unicode; class=java.io.InputStream", "Plain Text");

    /**
     * A MIME Content-Type of application/x-java-serialized-object represents
     * a graph of Java object(s) that have been made persistent.
     */
    public static final String javaSerializedObjectMimeType = "application/x-java-serialized-object";

    /**
     * To transfer a list of files to/from Java (and the underlying
     * platform) a <code>DataFlavor</code> of this type/subtype and
     * representation class of <code>java.util.List</code> is used.
     * Each element of the list is required/guaranteed to be of type
     * <code>java.io.File</code>.
     */
    public static final DataFlavor javaFileListFlavor = createConstant("application/x-java-file-list;class=java.util.List", null);

    /**
     * To transfer a reference to an arbitrary Java object reference that
     * has no associated MIME Content-type, across a <code>Transferable</code>
     * interface WITHIN THE SAME JVM, a <code>DataFlavor</code>
     * with this type/subtype is used, with a <code>representationClass</code>
     * equal to the type of the class/interface being passed across the
     * <code>Transferable</code>.
     */
    public static final String javaJVMLocalObjectMimeType = "application/x-java-jvm-local-objectref";

    /**
     * In order to pass a live link to a Remote object via a Drag and Drop
     * <code>ACTION_LINK</code> operation a Mime Content Type of
     * application/x-java-remote-object should be used,
     * where the representation class of the <code>DataFlavor</code>
     * represents the type of the <code>Remote</code> interface to be
     * transferred.
     */
    public static final String javaRemoteObjectMimeType = "application/x-java-remote-object";

    /**
     * Represents a piece of an HTML markup. The markup consists of the part
     * selected on the source side. Therefore some tags in the markup may be
     * unpaired. If the flavor is used to represent the data in
     * a {@link Transferable} instance, no additional changes will be made.
     * This DataFlavor instance represents the same HTML markup as DataFlavor
     * instances which content MIME type does not contain document parameter
     * and representation class is the String class.
     * <pre>
     *     representationClass = String
     *     mimeType           = "text/html"
     * </pre>
     *
     * @since 1.8
     */
    public static DataFlavor selectionHtmlFlavor = initHtmlDataFlavor("selection");

    /**
     * Represents a piece of an HTML markup. If possible, the markup received
     * from a native system is supplemented with pair tags to be
     * a well-formed HTML markup. If the flavor is used to represent the data in
     * a {@link Transferable} instance, no additional changes will be made.
     * <pre>
     *     representationClass = String
     *     mimeType           = "text/html"
     * </pre>
     *
     * @since 1.8
     */
    public static DataFlavor fragmentHtmlFlavor = initHtmlDataFlavor("fragment");

    /**
     * Represents a piece of an HTML markup. If possible, the markup
     * received from a native system is supplemented with additional
     * tags to make up a well-formed HTML document. If the flavor is used to
     * represent the data in a {@link Transferable} instance,
     * no additional changes will be made.
     * <pre>
     *     representationClass = String
     *     mimeType           = "text/html"
     * </pre>
     *
     * @since 1.8
     */
    public static  DataFlavor allHtmlFlavor = initHtmlDataFlavor("all");

    /**
     * Constructs a new <code>DataFlavor</code>.  This constructor is
     * provided only for the purpose of supporting the
     * <code>Externalizable</code> interface.  It is not
     * intended for public (client) use.
     *
     * @since 1.2
     */
    public DataFlavor() {
        super();
    }

    /**
     * Constructs a fully specified <code>DataFlavor</code>.
     *
     * @exception NullPointerException if either <code>primaryType</code>,
     *            <code>subType</code> or <code>representationClass</code> is null
     */
    private DataFlavor(String primaryType, String subType, MimeTypeParameterList params, Class<?> representationClass, String humanPresentableName) {
        super();
        if (primaryType == null) {
            throw new NullPointerException("primaryType");
        }
        if (subType == null) {
            throw new NullPointerException("subType");
        }
        if (representationClass == null) {
            throw new NullPointerException("representationClass");
        }

        if (params == null) params = new MimeTypeParameterList();

        params.set("class", representationClass.getName());

        if (humanPresentableName == null) {
            humanPresentableName = params.get("humanPresentableName");

            if (humanPresentableName == null)
                humanPresentableName = primaryType + "/" + subType;
        }

        try {
            mimeType = new MimeType(primaryType, subType, params);
        } catch (MimeTypeParseException mtpe) {
            throw new IllegalArgumentException("MimeType Parse Exception: " + mtpe.getMessage());
        }

        this.representationClass  = representationClass;
        this.humanPresentableName = humanPresentableName;

        mimeType.removeParameter("humanPresentableName");
    }

    /**
     * Constructs a <code>DataFlavor</code> that represents a Java class.
     * <p>
     * The returned <code>DataFlavor</code> will have the following
     * characteristics:
     * <pre>
     *    representationClass = representationClass
     *    mimeType            = application/x-java-serialized-object
     * </pre>
     * @param representationClass the class used to transfer data in this flavor
     * @param humanPresentableName the human-readable string used to identify
     *                 this flavor; if this parameter is <code>null</code>
     *                 then the value of the the MIME Content Type is used
     * @exception NullPointerException if <code>representationClass</code> is null
     */
    public DataFlavor(Class<?> representationClass, String humanPresentableName) {
        this("application", "x-java-serialized-object", null, representationClass, humanPresentableName);
        if (representationClass == null) {
            throw new NullPointerException("representationClass");
        }
    }

    /**
     * Constructs a <code>DataFlavor</code> that represents a
     * <code>MimeType</code>.
     * <p>
     * The returned <code>DataFlavor</code> will have the following
     * characteristics:
     * <p>
     * If the <code>mimeType</code> is
     * "application/x-java-serialized-object; class=&lt;representation class&gt;",
     * the result is the same as calling
     * <code>new DataFlavor(Class:forName(&lt;representation class&gt;)</code>.
     * <p>
     * Otherwise:
     * <pre>
     *     representationClass = InputStream
     *     mimeType            = mimeType
     * </pre>
     * @param mimeType the string used to identify the MIME type for this flavor;
     *                 if the the <code>mimeType</code> does not specify a
     *                 "class=" parameter, or if the class is not successfully
     *                 loaded, then an <code>IllegalArgumentException</code>
     *                 is thrown
     * @param humanPresentableName the human-readable string used to identify
     *                 this flavor; if this parameter is <code>null</code>
     *                 then the value of the the MIME Content Type is used
     * @exception IllegalArgumentException if <code>mimeType</code> is
     *                 invalid or if the class is not successfully loaded
     * @exception NullPointerException if <code>mimeType</code> is null
     */
    public DataFlavor(String mimeType, String humanPresentableName) {
        super();
        if (mimeType == null) {
            throw new NullPointerException("mimeType");
        }
        try {
            initialize(mimeType, humanPresentableName, this.getClass().getClassLoader());
        } catch (MimeTypeParseException mtpe) {
            throw new IllegalArgumentException("failed to parse:" + mimeType);
        } catch (ClassNotFoundException cnfe) {
            throw new IllegalArgumentException("can't find specified class: " + cnfe.getMessage());
        }
    }

    /**
     * Constructs a <code>DataFlavor</code> that represents a
     * <code>MimeType</code>.
     * <p>
     * The returned <code>DataFlavor</code> will have the following
     * characteristics:
     * <p>
     * If the mimeType is
     * "application/x-java-serialized-object; class=&lt;representation class&gt;",
     * the result is the same as calling
     * <code>new DataFlavor(Class:forName(&lt;representation class&gt;)</code>.
     * <p>
     * Otherwise:
     * <pre>
     *     representationClass = InputStream
     *     mimeType            = mimeType
     * </pre>
     * @param mimeType the string used to identify the MIME type for this flavor
     * @param humanPresentableName the human-readable string used to
     *          identify this flavor
     * @param classLoader the class loader to use
     * @exception ClassNotFoundException if the class is not loaded
     * @exception IllegalArgumentException if <code>mimeType</code> is
     *                 invalid
     * @exception NullPointerException if <code>mimeType</code> is null
     */
    public DataFlavor(String mimeType, String humanPresentableName, ClassLoader classLoader) throws ClassNotFoundException {
        super();
        if (mimeType == null) {
            throw new NullPointerException("mimeType");
        }
        try {
            initialize(mimeType, humanPresentableName, classLoader);
        } catch (MimeTypeParseException mtpe) {
            throw new IllegalArgumentException("failed to parse:" + mimeType);
        }
    }

    /**
     * Constructs a <code>DataFlavor</code> from a <code>mimeType</code> string.
     * The string can specify a "class=&lt;fully specified Java class name&gt;"
     * parameter to create a <code>DataFlavor</code> with the desired
     * representation class. If the string does not contain "class=" parameter,
     * <code>java.io.InputStream</code> is used as default.
     *
     * @param mimeType the string used to identify the MIME type for this flavor;
     *                 if the class specified by "class=" parameter is not
     *                 successfully loaded, then an
     *                 <code>ClassNotFoundException</code> is thrown
     * @exception ClassNotFoundException if the class is not loaded
     * @exception IllegalArgumentException if <code>mimeType</code> is
     *                 invalid
     * @exception NullPointerException if <code>mimeType</code> is null
     */
    public DataFlavor(String mimeType) throws ClassNotFoundException {
        super();
        if (mimeType == null) {
            throw new NullPointerException("mimeType");
        }
        try {
            initialize(mimeType, null, this.getClass().getClassLoader());
        } catch (MimeTypeParseException mtpe) {
            throw new IllegalArgumentException("failed to parse:" + mimeType);
        }
    }

   /**
    * Common initialization code called from various constructors.
    *
    * @exception MimeTypeParseException
    * @exception ClassNotFoundException
    * @exception  NullPointerException if <code>mimeType</code> is null
    */
    private void initialize(String mimeType, String humanPresentableName, ClassLoader classLoader) throws MimeTypeParseException, ClassNotFoundException {
        if (mimeType == null) {
            throw new NullPointerException("mimeType");
        }

        this.mimeType = new MimeType(mimeType); // throws

        String rcn = getParameter("class");

        if (rcn == null) {
            if ("application/x-java-serialized-object".equals(this.mimeType.getBaseType()))

                throw new IllegalArgumentException("no representation class specified for:" + mimeType);
            else
                representationClass = java.io.InputStream.class; // default
        } else { // got a class name
            representationClass = DataFlavor.tryToLoadClass(rcn, classLoader);
        }

        this.mimeType.setParameter("class", representationClass.getName());

        if (humanPresentableName == null) {
            humanPresentableName = this.mimeType.getParameter("humanPresentableName");
            if (humanPresentableName == null)
                humanPresentableName = this.mimeType.getPrimaryType() + "/" + this.mimeType.getSubType();
        }

        this.humanPresentableName = humanPresentableName; // set it.

        this.mimeType.removeParameter("humanPresentableName"); // just in case
    }

    /**
     * String representation of this <code>DataFlavor</code> and its
     * parameters. The resulting <code>String</code> contains the name of
     * the <code>DataFlavor</code> class, this flavor's MIME type, and its
     * representation class. If this flavor has a primary MIME type of "text",
     * supports the charset parameter, and has an encoded representation, the
     * flavor's charset is also included. See <code>selectBestTextFlavor</code>
     * for a list of text flavors which support the charset parameter.
     *
     * @return  string representation of this <code>DataFlavor</code>
     * @see #selectBestTextFlavor
     */
    public String toString() {
        String string = getClass().getName();
        string += "["+paramString()+"]";
        return string;
    }

    private String paramString() {
        String params = "";
        params += "mimetype=";
        if (mimeType == null) {
            params += "null";
        } else {
            params += mimeType.getBaseType();
        }
        params += ";representationclass=";
        if (representationClass == null) {
           params += "null";
        } else {
           params += representationClass.getName();
        }
        if (TextFlavors.isFlavorCharsetTextType(this) &&
            (isRepresentationClassInputStream() ||
             isRepresentationClassByteBuffer() ||
             byte[].class.equals(representationClass)))
        {
            params += ";charset=" + TextFlavors.getTextCharset(this);
        }
        return params;
    }

    /**
     * Returns a <code>DataFlavor</code> representing plain text with Unicode
     * encoding, where:
     * <pre>
     *     representationClass = java.io.InputStream
     *     mimeType            = "text/plain;
     *                            charset=&lt;platform default Unicode encoding&gt;"
     * </pre>
     * The platform default Unicode encoding of Android text is UTF-8.
     *
     * @return a <code>DataFlavor</code> representing plain text
     *    with Unicode encoding
     * @since 1.3
     */
    public static final DataFlavor getTextPlainUnicodeFlavor() {
        return new DataFlavor(
            "text/plain;charset=utf-8"
            +";class=java.io.InputStream", "Plain Text");
    }

    /**
     * Selects the best text <code>DataFlavor</code> from an array of <code>
     * DataFlavor</code>s. Only <code>DataFlavor.stringFlavor</code>, and
     * equivalent flavors, and flavors that have a primary MIME type of "text",
     * are considered for selection.
     * <p>
     * Flavors are first sorted by their MIME types in the following order:
     * <ul>
     * <li>"text/sgml"
     * <li>"text/xml"
     * <li>"text/html"
     * <li>"text/rtf"
     * <li>"text/enriched"
     * <li>"text/richtext"
     * <li>"text/uri-list"
     * <li>"text/tab-separated-values"
     * <li>"text/t140"
     * <li>"text/rfc822-headers"
     * <li>"text/parityfec"
     * <li>"text/directory"
     * <li>"text/css"
     * <li>"text/calendar"
     * <li>"application/x-java-serialized-object"
     * <li>"text/plain"
     * <li>"text/&lt;other&gt;"
     * </ul>
     * <p>For example, "text/sgml" will be selected over
     * "text/html", and <code>DataFlavor.stringFlavor</code> will be chosen
     * over <code>DataFlavor.plainTextFlavor</code>.
     * <p>
     * If two or more flavors share the best MIME type in the array, then that
     * MIME type will be checked to see if it supports the charset parameter.
     * <p>
     * The following MIME types support, or are treated as though they support,
     * the charset parameter:
     * <ul>
     * <li>"text/sgml"
     * <li>"text/xml"
     * <li>"text/html"
     * <li>"text/enriched"
     * <li>"text/richtext"
     * <li>"text/uri-list"
     * <li>"text/directory"
     * <li>"text/css"
     * <li>"text/calendar"
     * <li>"application/x-java-serialized-object"
     * <li>"text/plain"
     * </ul>
     * The following MIME types do not support, or are treated as though they
     * do not support, the charset parameter:
     * <ul>
     * <li>"text/rtf"
     * <li>"text/tab-separated-values"
     * <li>"text/t140"
     * <li>"text/rfc822-headers"
     * <li>"text/parityfec"
     * </ul>
     * For "text/&lt;other&gt;" MIME types, the first time the JRE needs to
     * determine whether the MIME type supports the charset parameter, it will
     * check whether the parameter is explicitly listed in an arbitrarily
     * chosen <code>DataFlavor</code> which uses that MIME type. If so, the JRE
     * will assume from that point on that the MIME type supports the charset
     * parameter and will not check again. If the parameter is not explicitly
     * listed, the JRE will assume from that point on that the MIME type does
     * not support the charset parameter and will not check again. Because
     * this check is performed on an arbitrarily chosen
     * <code>DataFlavor</code>, developers must ensure that all
     * <code>DataFlavor</code>s with a "text/&lt;other&gt;" MIME type specify
     * the charset parameter if it is supported by that MIME type. Developers
     * should never rely on the JRE to substitute the platform's default
     * charset for a "text/&lt;other&gt;" DataFlavor. Failure to adhere to this
     * restriction will lead to undefined behavior.
     * <p>
     * If the best MIME type in the array does not support the charset
     * parameter, the flavors which share that MIME type will then be sorted by
     * their representation classes in the following order:
     * <code>java.io.InputStream</code>, <code>java.nio.ByteBuffer</code>,
     * <code>[B</code>, &lt;all others&gt;.
     * <p>
     * If two or more flavors share the best representation class, or if no
     * flavor has one of the three specified representations, then one of those
     * flavors will be chosen non-deterministically.
     * <p>
     * If the best MIME type in the array does support the charset parameter,
     * the flavors which share that MIME type will then be sorted by their
     * representation classes in the following order:
     * <code>java.io.Reader</code>, <code>java.lang.String</code>,
     * <code>java.nio.CharBuffer</code>, <code>[C</code>, &lt;all others&gt;.
     * <p>
     * If two or more flavors share the best representation class, and that
     * representation is one of the four explicitly listed, then one of those
     * flavors will be chosen non-deterministically. If, however, no flavor has
     * one of the four specified representations, the flavors will then be
     * sorted by their charsets. Unicode charsets, such as "UTF-16", "UTF-8",
     * "UTF-16BE", "UTF-16LE", and their aliases, are considered best. After
     * them, the platform default charset and its aliases are selected.
     * "US-ASCII" and its aliases are worst. All other charsets are chosen in
     * alphabetical order, but only charsets supported by this implementation
     * of the Java platform will be considered.
     * <p>
     * If two or more flavors share the best charset, the flavors will then
     * again be sorted by their representation classes in the following order:
     * <code>java.io.InputStream</code>, <code>java.nio.ByteBuffer</code>,
     * <code>[B</code>, &lt;all others&gt;.
     * <p>
     * If two or more flavors share the best representation class, or if no
     * flavor has one of the three specified representations, then one of those
     * flavors will be chosen non-deterministically.
     *
     * @param availableFlavors an array of available <code>DataFlavor</code>s
     * @return the best (highest fidelity) flavor according to the rules
     *         specified above, or <code>null</code>,
     *         if <code>availableFlavors</code> is <code>null</code>,
     *         has zero length, or contains no text flavors
     * @since 1.3
     */
    public static final DataFlavor selectBestTextFlavor(
                                       DataFlavor[] availableFlavors) {
        if (availableFlavors == null || availableFlavors.length == 0) {
            return null;
        }

        DataFlavor bestFlavor = Collections.max(Arrays.asList(availableFlavors),
                                                TextFlavors.TEXT_FLAVOR_COMPARATOR);

        if (!bestFlavor.isFlavorTextType()) {
            return null;
        }

        return bestFlavor;
    }

    /**
     * Gets a Reader for a text flavor, decoded, if necessary, for the expected
     * charset (encoding). The supported representation classes are
     * <code>java.io.Reader</code>, <code>java.lang.String</code>,
     * <code>java.nio.CharBuffer</code>, <code>[C</code>,
     * <code>java.io.InputStream</code>, <code>java.nio.ByteBuffer</code>,
     * and <code>[B</code>.
     * <p>
     * Because text flavors which do not support the charset parameter are
     * encoded in a non-standard format, this method should not be called for
     * such flavors. However, in order to maintain backward-compatibility,
     * if this method is called for such a flavor, this method will treat the
     * flavor as though it supports the charset parameter and attempt to
     * decode it accordingly. See <code>selectBestTextFlavor</code> for a list
     * of text flavors which do not support the charset parameter.
     *
     * @param transferable the <code>Transferable</code> whose data will be
     *        requested in this flavor
     *
     * @return a <code>Reader</code> to read the <code>Transferable</code>'s
     *         data
     *
     * @exception IllegalArgumentException if the representation class
     *            is not one of the seven listed above
     * @exception IllegalArgumentException if the <code>Transferable</code>
     *            has <code>null</code> data
     * @exception NullPointerException if the <code>Transferable</code> is
     *            <code>null</code>
     * @exception java.io.UnsupportedEncodingException if this flavor's representation
     *            is <code>java.io.InputStream</code>,
     *            <code>java.nio.ByteBuffer</code>, or <code>[B</code> and
     *            this flavor's encoding is not supported by this
     *            implementation of the Java platform
     * @exception UnsupportedFlavorException if the <code>Transferable</code>
     *            does not support this flavor
     * @exception IOException if the data cannot be read because of an
     *            I/O error
     * @see #selectBestTextFlavor
     * @since 1.3
     */
    public Reader getReaderForText(Transferable transferable)
        throws UnsupportedFlavorException, IOException
    {
        Object transferObject = transferable.getTransferData(this);
        if (transferObject == null) {
            throw new IllegalArgumentException
                ("getTransferData() returned null");
        }

        if (transferObject instanceof Reader) {
            return (Reader)transferObject;
        } else if (transferObject instanceof String) {
            return new StringReader((String)transferObject);
        } else if (transferObject instanceof CharBuffer) {
            CharBuffer buffer = (CharBuffer)transferObject;
            int size = buffer.remaining();
            char[] chars = new char[size];
            buffer.get(chars, 0, size);
            return new CharArrayReader(chars);
        } else if (transferObject instanceof char[]) {
            return new CharArrayReader((char[])transferObject);
        }

        InputStream stream = null;

        if (transferObject instanceof InputStream) {
            stream = (InputStream)transferObject;
        } else if (transferObject instanceof ByteBuffer) {
            ByteBuffer buffer = (ByteBuffer)transferObject;
            int size = buffer.remaining();
            byte[] bytes = new byte[size];
            buffer.get(bytes, 0, size);
            stream = new ByteArrayInputStream(bytes);
        } else if (transferObject instanceof byte[]) {
            stream = new ByteArrayInputStream((byte[])transferObject);
        }

        if (stream == null) {
            throw new IllegalArgumentException("transfer data is not Reader, String, CharBuffer, char array, InputStream, ByteBuffer, or byte array");
        }

        String encoding = getParameter("charset");
        return (encoding == null)
            ? new InputStreamReader(stream)
            : new InputStreamReader(stream, encoding);
    }

    /**
     * Returns the MIME type string for this <code>DataFlavor</code>.
     * @return the MIME type string for this flavor
     */
    public String getMimeType() {
        return (mimeType != null) ? mimeType.toString() : null;
    }

    /**
     * Returns the <code>Class</code> which objects supporting this
     * <code>DataFlavor</code> will return when this <code>DataFlavor</code>
     * is requested.
     * @return the <code>Class</code> which objects supporting this
     * <code>DataFlavor</code> will return when this <code>DataFlavor</code>
     * is requested
     */
    public Class<?> getRepresentationClass() {
        return representationClass;
    }

    /**
     * Returns the human presentable name for the data format that this
     * <code>DataFlavor</code> represents.  This name would be localized
     * for different countries.
     * @return the human presentable name for the data format that this
     *    <code>DataFlavor</code> represents
     */
    public String getHumanPresentableName() {
        return humanPresentableName;
    }

    /**
     * Returns the primary MIME type for this <code>DataFlavor</code>.
     * @return the primary MIME type of this <code>DataFlavor</code>
     */
    public String getPrimaryType() {
        return (mimeType != null) ? mimeType.getPrimaryType() : null;
    }

    /**
     * Returns the sub MIME type of this <code>DataFlavor</code>.
     * @return the Sub MIME type of this <code>DataFlavor</code>
     */
    public String getSubType() {
        return (mimeType != null) ? mimeType.getSubType() : null;
    }

    /**
     * Returns the human presentable name for this <code>DataFlavor</code>
     * if <code>paramName</code> equals "humanPresentableName".  Otherwise
     * returns the MIME type value associated with <code>paramName</code>.
     *
     * @param paramName the parameter name requested
     * @return the value of the name parameter, or <code>null</code>
     *  if there is no associated value
     */
    public String getParameter(String paramName) {
        if (paramName.equals("humanPresentableName")) {
            return humanPresentableName;
        } else {
            return (mimeType != null)
                ? mimeType.getParameter(paramName) : null;
        }
    }

    /**
     * Sets the human presentable name for the data format that this
     * <code>DataFlavor</code> represents. This name would be localized
     * for different countries.
     * @param humanPresentableName the new human presentable name
     */
    public void setHumanPresentableName(String humanPresentableName) {
        this.humanPresentableName = humanPresentableName;
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     * <p>
     * The {@code equals} method implements an equivalence relation
     * on non-null object references:
     * <ul>
     * <li>It is <i>reflexive</i>: for any non-null reference value
     *     {@code x}, {@code x.equals(x)} should return {@code true}.
     * <li>It is <i>symmetric</i>: for any non-null reference values
     *     {@code x} and {@code y}, {@code x.equals(y)}
     *     should return {@code true} if and only if
     *     {@code y.equals(x)} returns {@code true}.
     * <li>It is <i>transitive</i>: for any non-null reference values
     *     {@code x}, {@code y}, and {@code z}, if
     *     {@code x.equals(y)} returns {@code true} and
     *     {@code y.equals(z)} returns {@code true},
     *     then {@code x.equals(z)} should return {@code true}.
     * <li>It is <i>consistent</i>: for any non-null reference values
     *     {@code x} and {@code y}, multiple invocations of
     *     {@code x.equals(y)} consistently return {@code true}
     *     or consistently return {@code false}, provided no
     *     information used in {@code equals} comparisons on the
     *     objects is modified.
     * <li>For any non-null reference value {@code x},
     *     {@code x.equals(null)} should return {@code false}.
     * </ul>
     * <p>
     * The equals comparison for the {@code DataFlavor} class is implemented
     * as follows: Two {@code DataFlavor}s are considered equal if and
     * only if their MIME primary type and subtype and representation class are
     * equal. Additionally, if the primary type is "text", the subtype denotes
     * a text flavor which supports the charset parameter, and the
     * representation class is not {@code java.io.Reader},
     * {@code java.lang.String}, {@code java.nio.CharBuffer}, or
     * {@code [C}, the {@code charset} parameter must also be equal.
     * If a charset is not explicitly specified for one or both
     * {@code DataFlavor}s, the platform default encoding is assumed. See
     * {@code selectBestTextFlavor} for a list of text flavors which support
     * the charset parameter.
     *
     * @param o the {@code Object} to compare with {@code this}
     * @return {@code true} if {@code that} is equivalent to this
     *         {@code DataFlavor}; {@code false} otherwise
     * @see #selectBestTextFlavor
     */
    public boolean equals(Object o) {
        return ((o instanceof DataFlavor) && equals((DataFlavor)o));
    }

    /**
     * This method has the same behavior as {@link #equals(Object)}.
     * The only difference being that it takes a {@code DataFlavor} instance
     * as a parameter.
     *
     * @param that the {@code DataFlavor} to compare with {@code this}
     * @return {@code true} if {@code that} is equivalent to this
     *         {@code DataFlavor}; {@code false} otherwise
     * @see #selectBestTextFlavor
     */
    public boolean equals(DataFlavor that) {
        if (that == null) {
            return false;
        }
        if (this == that) {
            return true;
        }

        if (!Objects.equals(this.getRepresentationClass(), that.getRepresentationClass())) {
            return false;
        }

        if (mimeType == null) {
            if (that.mimeType != null) {
                return false;
            }
        } else {
            if (!mimeType.match(that.mimeType)) {
                return false;
            }

            if ("text".equals(getPrimaryType())) {
                if (TextFlavors.doesSubtypeSupportCharset(this)
                        && representationClass != null
                        && !isStandardTextRepresentationClass()) {
                    String thisCharset =
                            TextFlavors.canonicalName(this.getParameter("charset"));
                    String thatCharset =
                            TextFlavors.canonicalName(that.getParameter("charset"));
                    if (!Objects.equals(thisCharset, thatCharset)) {
                        return false;
                    }
                }

                if ("html".equals(getSubType())) {
                    String thisDocument = this.getParameter("document");
                    String thatDocument = that.getParameter("document");
                    if (!Objects.equals(thisDocument, thatDocument)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    /**
     * Compares only the <code>mimeType</code> against the passed in
     * <code>String</code> and <code>representationClass</code> is
     * not considered in the comparison.
     *
     * If <code>representationClass</code> needs to be compared, then
     * <code>equals(new DataFlavor(s))</code> may be used.
     * @deprecated As inconsistent with <code>hashCode()</code> contract,
     *             use <code>isMimeTypeEqual(String)</code> instead.
     * @param s the {@code mimeType} to compare.
     * @return true if the String (MimeType) is equal; false otherwise or if
     *         {@code s} is {@code null}
     */
    @Deprecated
    public boolean equals(String s) {
        if (s == null || mimeType == null)
            return false;
        return isMimeTypeEqual(s);
    }

    /**
     * Returns hash code for this <code>DataFlavor</code>.
     * For two equal <code>DataFlavor</code>s, hash codes are equal.
     * For the <code>String</code>
     * that matches <code>DataFlavor.equals(String)</code>, it is not
     * guaranteed that <code>DataFlavor</code>'s hash code is equal
     * to the hash code of the <code>String</code>.
     *
     * @return a hash code for this <code>DataFlavor</code>
     */
    public int hashCode() {
        int total = 0;

        if (representationClass != null) {
            total += representationClass.hashCode();
        }

        if (mimeType != null) {
            String primaryType = mimeType.getPrimaryType();
            if (primaryType != null) {
                total += primaryType.hashCode();
            }

            // Do not add subType.hashCode() to the total. equals uses
            // MimeType.match which reports a match if one or both of the
            // subTypes is '*', regardless of the other subType.

            if ("text".equals(primaryType)) {
                if (TextFlavors.doesSubtypeSupportCharset(this)
                        && representationClass != null
                        && !isStandardTextRepresentationClass()) {
                    String charset = TextFlavors.canonicalName(getParameter("charset"));
                    if (charset != null) {
                        total += charset.hashCode();
                    }
                }

                if ("html".equals(getSubType())) {
                    String document = this.getParameter("document");
                    if (document != null) {
                        total += document.hashCode();
                    }
                }
            }
        }

        return total;
    }

    /**
     * Identical to {@link #equals(DataFlavor)}.
     *
     * @param that the <code>DataFlavor</code> to compare with
     *        <code>this</code>
     * @return <code>true</code> if <code>that</code> is equivalent to this
     *         <code>DataFlavor</code>; <code>false</code> otherwise
     * @see #selectBestTextFlavor
     * @since 1.3
     */
    public boolean match(DataFlavor that) {
        return equals(that);
    }

    /**
     * Returns whether the string representation of the MIME type passed in
     * is equivalent to the MIME type of this <code>DataFlavor</code>.
     * Parameters are not included in the comparison.
     *
     * @param mimeType the string representation of the MIME type
     * @return true if the string representation of the MIME type passed in is
     *         equivalent to the MIME type of this <code>DataFlavor</code>;
     *         false otherwise
     * @throws NullPointerException if mimeType is <code>null</code>
     */
    public boolean isMimeTypeEqual(String mimeType) {
        // JCK Test DataFlavor0117: if 'mimeType' is null, throw NPE
        if (mimeType == null) {
            throw new NullPointerException("mimeType");
        }
        if (this.mimeType == null) {
            return false;
        }
        try {
            return this.mimeType.match(new MimeType(mimeType));
        } catch (MimeTypeParseException mtpe) {
            return false;
        }
    }

    /**
     * Compares the <code>mimeType</code> of two <code>DataFlavor</code>
     * objects. No parameters are considered.
     *
     * @param dataFlavor the <code>DataFlavor</code> to be compared
     * @return true if the <code>MimeType</code>s are equal,
     *  otherwise false
     */

    public final boolean isMimeTypeEqual(DataFlavor dataFlavor) {
        return isMimeTypeEqual(dataFlavor.mimeType);
    }

    /**
     * Compares the <code>mimeType</code> of two <code>DataFlavor</code>
     * objects.  No parameters are considered.
     *
     * @return true if the <code>MimeType</code>s are equal,
     *  otherwise false
     */

    private boolean isMimeTypeEqual(MimeType mtype) {
        if (this.mimeType == null) {
            return (mtype == null);
        }
        return mimeType.match(mtype);
    }

    /**
     * Checks if the representation class is one of the standard text
     * representation classes.
     *
     * @return true if the representation class is one of the standard text
     *              representation classes, otherwise false
     */
    private boolean isStandardTextRepresentationClass() {
        return isRepresentationClassReader()
                || String.class.equals(representationClass)
                || isRepresentationClassCharBuffer()
                || char[].class.equals(representationClass);
    }

   /**
    * Does the <code>DataFlavor</code> represent a serialized object?
    * @return whether or not a serialized object is represented
    */
    public boolean isMimeTypeSerializedObject() {
        return isMimeTypeEqual(javaSerializedObjectMimeType);
    }

    /**
     * Returns the default representation class.
     * @return the default representation class
     */
    public final Class<?> getDefaultRepresentationClass() {
        return ioInputStreamClass;
    }

    /**
     * Returns the name of the default representation class.
     * @return the name of the default representation class
     */
    public final String getDefaultRepresentationClassAsString() {
        return getDefaultRepresentationClass().getName();
    }

   /**
    * Does the <code>DataFlavor</code> represent a
    * <code>java.io.InputStream</code>?
    * @return whether or not this {@code DataFlavor} represent a
    * {@code java.io.InputStream}
    */
    public boolean isRepresentationClassInputStream() {
        return ioInputStreamClass.isAssignableFrom(representationClass);
    }

    /**
     * Returns whether the representation class for this
     * <code>DataFlavor</code> is <code>java.io.Reader</code> or a subclass
     * thereof.
     * @return whether or not the representation class for this
     * {@code DataFlavor} is {@code java.io.Reader} or a subclass
     * thereof
     *
     * @since 1.4
     */
    public boolean isRepresentationClassReader() {
        return java.io.Reader.class.isAssignableFrom(representationClass);
    }

    /**
     * Returns whether the representation class for this
     * <code>DataFlavor</code> is <code>java.nio.CharBuffer</code> or a
     * subclass thereof.
     * @return whether or not the representation class for this
     * {@code DataFlavor} is {@code java.nio.CharBuffer} or a subclass
     * thereof
     *
     * @since 1.4
     */
    public boolean isRepresentationClassCharBuffer() {
        return java.nio.CharBuffer.class.isAssignableFrom(representationClass);
    }

    /**
     * Returns whether the representation class for this
     * <code>DataFlavor</code> is <code>java.nio.ByteBuffer</code> or a
     * subclass thereof.
     * @return whether or not the representation class for this
     * {@code DataFlavor} is {@code java.nio.ByteBuffer} or a subclass
     * thereof
     *
     * @since 1.4
     */
    public boolean isRepresentationClassByteBuffer() {
        return java.nio.ByteBuffer.class.isAssignableFrom(representationClass);
    }

   /**
    * Returns true if the representation class can be serialized.
    * @return true if the representation class can be serialized
    */

    public boolean isRepresentationClassSerializable() {
        return java.io.Serializable.class.isAssignableFrom(representationClass);
    }

   /**
    * Returns true if the representation class is <code>Remote</code>.
    * Android has no {@code java.rmi}: the answer is always false there.
    * @return true if the representation class is <code>Remote</code>
    */
    public boolean isRepresentationClassRemote() {
        Class<?> remote = TextFlavors.REMOTE_CLASS;
        return remote != null && remote.isAssignableFrom(representationClass);
    }

   /**
    * Returns true if the <code>DataFlavor</code> specified represents
    * a serialized object.
    * @return true if the <code>DataFlavor</code> specified represents
    *   a Serialized Object
    */

    public boolean isFlavorSerializedObjectType() {
        return isRepresentationClassSerializable() && isMimeTypeEqual(javaSerializedObjectMimeType);
    }

    /**
     * Returns true if the <code>DataFlavor</code> specified represents
     * a remote object.
     * @return true if the <code>DataFlavor</code> specified represents
     *  a Remote Object
     */

    public boolean isFlavorRemoteObjectType() {
        return isRepresentationClassRemote()
            && isRepresentationClassSerializable()
            && isMimeTypeEqual(javaRemoteObjectMimeType);
    }


   /**
    * Returns true if the <code>DataFlavor</code> specified represents
    * a list of file objects.
    * @return true if the <code>DataFlavor</code> specified represents
    *   a List of File objects
    */

   public boolean isFlavorJavaFileListType() {
        if (mimeType == null || representationClass == null)
            return false;
        return java.util.List.class.isAssignableFrom(representationClass) &&
               mimeType.match(javaFileListFlavor.mimeType);

   }

    /**
     * Returns whether this <code>DataFlavor</code> is a valid text flavor for
     * this implementation of the Java platform. Only flavors equivalent to
     * <code>DataFlavor.stringFlavor</code> and <code>DataFlavor</code>s with
     * a primary MIME type of "text" can be valid text flavors.
     * <p>
     * If this flavor supports the charset parameter, it must be equivalent to
     * <code>DataFlavor.stringFlavor</code>, or its representation must be
     * <code>java.io.Reader</code>, <code>java.lang.String</code>,
     * <code>java.nio.CharBuffer</code>, <code>[C</code>,
     * <code>java.io.InputStream</code>, <code>java.nio.ByteBuffer</code>, or
     * <code>[B</code>. If the representation is
     * <code>java.io.InputStream</code>, <code>java.nio.ByteBuffer</code>, or
     * <code>[B</code>, then this flavor's <code>charset</code> parameter must
     * be supported by this implementation of the Java platform. If a charset
     * is not specified, then the platform default charset, which is always
     * supported, is assumed.
     * <p>
     * If this flavor does not support the charset parameter, its
     * representation must be <code>java.io.InputStream</code>,
     * <code>java.nio.ByteBuffer</code>, or <code>[B</code>.
     * <p>
     * See <code>selectBestTextFlavor</code> for a list of text flavors which
     * support the charset parameter.
     *
     * @return <code>true</code> if this <code>DataFlavor</code> is a valid
     *         text flavor as described above; <code>false</code> otherwise
     * @see #selectBestTextFlavor
     * @since 1.4
     */
    public boolean isFlavorTextType() {
        return (TextFlavors.isFlavorCharsetTextType(this) ||
                TextFlavors.isFlavorNoncharsetTextType(this));
    }

   /**
    * Serializes this <code>DataFlavor</code>.
    */

   public synchronized void writeExternal(ObjectOutput os) throws IOException {
       if (mimeType != null) {
           mimeType.setParameter("humanPresentableName", humanPresentableName);
           os.writeObject(mimeType);
           mimeType.removeParameter("humanPresentableName");
       } else {
           os.writeObject(null);
       }

       os.writeObject(representationClass);
   }

   /**
    * Restores this <code>DataFlavor</code> from a Serialized state.
    */

   public synchronized void readExternal(ObjectInput is) throws IOException , ClassNotFoundException {
       String rcn = null;
        mimeType = (MimeType)is.readObject();

        if (mimeType != null) {
            humanPresentableName =
                mimeType.getParameter("humanPresentableName");
            mimeType.removeParameter("humanPresentableName");
            rcn = mimeType.getParameter("class");
            if (rcn == null) {
                throw new IOException("no class parameter specified in: " +
                                      mimeType);
            }
        }

        try {
            representationClass = (Class<?>)is.readObject();
        } catch (OptionalDataException ode) {
            if (!ode.eof || ode.length != 0) {
                throw ode;
            }
            // Ensure backward compatibility.
            // Old versions didn't write the representation class to the stream.
            if (rcn != null) {
                representationClass =
                    DataFlavor.tryToLoadClass(rcn, getClass().getClassLoader());
            }
        }
   }

   /**
    * Returns a clone of this <code>DataFlavor</code>.
    * @return a clone of this <code>DataFlavor</code>
    */

    public Object clone() throws CloneNotSupportedException {
        Object newObj = super.clone();
        if (mimeType != null) {
            ((DataFlavor)newObj).mimeType = (MimeType)mimeType.clone();
        }
        return newObj;
    } // clone()

   /**
    * Called on <code>DataFlavor</code> for every MIME Type parameter
    * to allow <code>DataFlavor</code> subclasses to handle special
    * parameters like the text/plain <code>charset</code>
    * parameters, whose values are case insensitive.  (MIME type parameter
    * values are supposed to be case sensitive.
    * <p>
    * This method is called for each parameter name/value pair and should
    * return the normalized representation of the <code>parameterValue</code>.
    *
    * This method is never invoked by this implementation from 1.1 onwards.
    *
    * @param parameterName the parameter name
    * @param parameterValue the parameter value
    * @return the parameter value
    * @deprecated
    */
    @Deprecated
    protected String normalizeMimeTypeParameter(String parameterName, String parameterValue) {
        return parameterValue;
    }

   /**
    * Called for each MIME type string to give <code>DataFlavor</code> subtypes
    * the opportunity to change how the normalization of MIME types is
    * accomplished.  One possible use would be to add default
    * parameter/value pairs in cases where none are present in the MIME
    * type string passed in.
    *
    * This method is never invoked by this implementation from 1.1 onwards.
    *
    * @param mimeType the mime type
    * @return the mime type
    * @deprecated
    */
    @Deprecated
    protected String normalizeMimeType(String mimeType) {
        return mimeType;
    }

    /*
     * fields
     */

    /* placeholder for caching any platform-specific data for flavor */

    transient int       atom;

    /* Mime Type of DataFlavor */

    MimeType            mimeType;

    private String      humanPresentableName;

    /** Java class of objects this DataFlavor represents **/

    private Class<?>       representationClass;

    /**
     * The text-flavor tables and comparators of OpenJDK's
     * {@code sun.datatransfer.DataFlavorUtil}: which subtypes take a charset,
     * and the preference order behind {@link #selectBestTextFlavor}.
     * Nested so the class initializes after the flavor constants it reads.
     */
    static final class TextFlavors {

        private TextFlavors() {
        }

        /** {@code java.rmi.Remote} where it exists; Android has none. */
        static final Class<?> REMOTE_CLASS = loadRemote();

        private static Class<?> loadRemote() {
            try {
                return Class.forName("java.rmi.Remote");
            } catch (ClassNotFoundException | LinkageError e) {
                return null;
            }
        }

        private static final Map<String, Boolean> textMIMESubtypeCharsetSupport;

        static {
            Map<String, Boolean> tempMap = new HashMap<>(17);
            tempMap.put("sgml", Boolean.TRUE);
            tempMap.put("xml", Boolean.TRUE);
            tempMap.put("html", Boolean.TRUE);
            tempMap.put("enriched", Boolean.TRUE);
            tempMap.put("richtext", Boolean.TRUE);
            tempMap.put("uri-list", Boolean.TRUE);
            tempMap.put("directory", Boolean.TRUE);
            tempMap.put("css", Boolean.TRUE);
            tempMap.put("calendar", Boolean.TRUE);
            tempMap.put("plain", Boolean.TRUE);
            tempMap.put("rtf", Boolean.FALSE);
            tempMap.put("tab-separated-values", Boolean.FALSE);
            tempMap.put("t140", Boolean.FALSE);
            tempMap.put("rfc822-headers", Boolean.FALSE);
            tempMap.put("parityfec", Boolean.FALSE);
            textMIMESubtypeCharsetSupport = Collections.synchronizedMap(tempMap);
        }

        static String canonicalName(String charset) {
            if (charset == null) {
                return null;
            }
            try {
                return Charset.forName(charset).name();
            } catch (IllegalCharsetNameException icne) {
                return charset;
            } catch (UnsupportedCharsetException uce) {
                return charset;
            }
        }

        static boolean isEncodingSupported(String encoding) {
            if (encoding == null) {
                return false;
            }
            try {
                return Charset.isSupported(encoding);
            } catch (IllegalCharsetNameException icne) {
                return false;
            }
        }

        static boolean doesSubtypeSupportCharset(DataFlavor flavor) {
            String subType = flavor.getSubType();
            if (subType == null) {
                return false;
            }
            Boolean support = textMIMESubtypeCharsetSupport.get(subType);
            if (support != null) {
                return support;
            }
            boolean ret_val = (flavor.getParameter("charset") != null);
            textMIMESubtypeCharsetSupport.put(subType, ret_val);
            return ret_val;
        }

        static String getTextCharset(DataFlavor flavor) {
            if (!isFlavorCharsetTextType(flavor)) {
                return null;
            }
            String encoding = flavor.getParameter("charset");
            return (encoding != null) ? encoding : Charset.defaultCharset().name();
        }

        static boolean isFlavorCharsetTextType(DataFlavor flavor) {
            // Although stringFlavor doesn't actually support the charset
            // parameter (because its primary MIME type is not "text"), it should
            // be treated as though it does. stringFlavor is semantically
            // equivalent to "text/plain" data.
            if (DataFlavor.stringFlavor.equals(flavor)) {
                return true;
            }

            if (!"text".equals(flavor.getPrimaryType()) ||
                !doesSubtypeSupportCharset(flavor))
            {
                return false;
            }

            Class<?> rep_class = flavor.getRepresentationClass();

            if (flavor.isRepresentationClassReader() ||
                String.class.equals(rep_class) ||
                flavor.isRepresentationClassCharBuffer() ||
                char[].class.equals(rep_class))
            {
                return true;
            }

            if (!(flavor.isRepresentationClassInputStream() ||
                  flavor.isRepresentationClassByteBuffer() ||
                  byte[].class.equals(rep_class))) {
                return false;
            }

            String charset = flavor.getParameter("charset");

            // null equals default encoding which is always supported
            return (charset == null) || isEncodingSupported(charset);
        }

        static boolean isFlavorNoncharsetTextType(DataFlavor flavor) {
            if (!"text".equals(flavor.getPrimaryType()) || doesSubtypeSupportCharset(flavor)) {
                return false;
            }

            return (flavor.isRepresentationClassInputStream() ||
                    flavor.isRepresentationClassByteBuffer() ||
                    byte[].class.equals(flavor.getRepresentationClass()));
        }

        static <T> int compareIndices(Map<T, Integer> indexMap,
                                      T obj1, T obj2,
                                      Integer fallbackIndex) {
            Integer index1 = indexMap.get(obj1);
            Integer index2 = indexMap.get(obj2);
            if (index1 == null) {
                index1 = fallbackIndex;
            }
            if (index2 == null) {
                index2 = fallbackIndex;
            }
            return index1.compareTo(index2);
        }

        /** Orders charset names worst to best, the way the JDK ranks text flavors. */
        static final class CharsetComparator {
            private static final Map<String, Integer> charsets;

            private static final Integer DEFAULT_CHARSET_INDEX = 2;
            private static final Integer OTHER_CHARSET_INDEX = 1;
            private static final Integer WORST_CHARSET_INDEX = 0;
            private static final Integer UNSUPPORTED_CHARSET_INDEX = Integer.MIN_VALUE;

            private static final String UNSUPPORTED_CHARSET = "UNSUPPORTED";

            static {
                Map<String, Integer> charsetsMap = new HashMap<>(8, 1.0f);

                // we prefer Unicode charsets
                charsetsMap.put(canonicalName("UTF-16LE"), 4);
                charsetsMap.put(canonicalName("UTF-16BE"), 5);
                charsetsMap.put(canonicalName("UTF-8"), 6);
                charsetsMap.put(canonicalName("UTF-16"), 7);

                // US-ASCII is the worst charset supported
                charsetsMap.put(canonicalName("US-ASCII"), WORST_CHARSET_INDEX);

                String defaultEncoding = Charset.defaultCharset().name();
                if (!charsetsMap.containsKey(defaultEncoding)) {
                    charsetsMap.put(defaultEncoding, DEFAULT_CHARSET_INDEX);
                }
                charsetsMap.put(UNSUPPORTED_CHARSET, UNSUPPORTED_CHARSET_INDEX);

                charsets = Collections.unmodifiableMap(charsetsMap);
            }

            static int compareCharsets(String charset1, String charset2) {
                charset1 = getEncoding(charset1);
                charset2 = getEncoding(charset2);

                int comp = compareIndices(charsets, charset1, charset2,
                                          OTHER_CHARSET_INDEX);

                if (comp == 0) {
                    return charset2.compareTo(charset1);
                }

                return comp;
            }

            static String getEncoding(String charset) {
                if (charset == null) {
                    return null;
                } else if (!isEncodingSupported(charset)) {
                    return UNSUPPORTED_CHARSET;
                } else {
                    // Only convert to canonical form if the charset is one
                    // of the charsets explicitly listed in the known charsets
                    // map. This will happen only for Unicode, ASCII, or default
                    // charsets.
                    String canonicalName = canonicalName(charset);
                    return (charsets.containsKey(canonicalName))
                        ? canonicalName
                        : charset;
                }
            }
        }

        /** OpenJDK's DataFlavorComparator in SELECT_BEST order: a greater flavor is a better one. */
        static class DataFlavorComparator implements Comparator<DataFlavor> {

            private static final Map<String, Integer> exactTypes;
            private static final Map<String, Integer> primaryTypes;
            private static final Map<Class<?>, Integer> nonTextRepresentations;
            private static final Map<String, Integer> textTypes;
            private static final Map<Class<?>, Integer> decodedTextRepresentations;
            private static final Map<Class<?>, Integer> encodedTextRepresentations;

            private static final Integer UNKNOWN_OBJECT_LOSES = Integer.MIN_VALUE;
            private static final Integer UNKNOWN_OBJECT_WINS = Integer.MAX_VALUE;

            static {
                {
                    Map<String, Integer> exactTypesMap = new HashMap<>(4, 1.0f);

                    // application/x-java-* MIME types
                    exactTypesMap.put("application/x-java-file-list", 0);
                    exactTypesMap.put("application/x-java-serialized-object", 1);
                    exactTypesMap.put("application/x-java-jvm-local-objectref", 2);
                    exactTypesMap.put("application/x-java-remote-object", 3);

                    exactTypes = Collections.unmodifiableMap(exactTypesMap);
                }

                {
                    Map<String, Integer> primaryTypesMap = new HashMap<>(1, 1.0f);

                    primaryTypesMap.put("application", 0);

                    primaryTypes = Collections.unmodifiableMap(primaryTypesMap);
                }

                {
                    Map<Class<?>, Integer> nonTextRepresentationsMap = new HashMap<>(3, 1.0f);

                    nonTextRepresentationsMap.put(java.io.InputStream.class, 0);
                    nonTextRepresentationsMap.put(java.io.Serializable.class, 1);

                    if (REMOTE_CLASS != null) {
                        nonTextRepresentationsMap.put(REMOTE_CLASS, 2);
                    }

                    nonTextRepresentations = Collections.unmodifiableMap(nonTextRepresentationsMap);
                }

                {
                    Map<String, Integer> textTypesMap = new HashMap<>(16, 1.0f);

                    // plain text
                    textTypesMap.put("text/plain", 0);

                    // stringFlavor
                    textTypesMap.put("application/x-java-serialized-object", 1);

                    // misc
                    textTypesMap.put("text/calendar", 2);
                    textTypesMap.put("text/css", 3);
                    textTypesMap.put("text/directory", 4);
                    textTypesMap.put("text/parityfec", 5);
                    textTypesMap.put("text/rfc822-headers", 6);
                    textTypesMap.put("text/t140", 7);
                    textTypesMap.put("text/tab-separated-values", 8);
                    textTypesMap.put("text/uri-list", 9);

                    // enriched
                    textTypesMap.put("text/richtext", 10);
                    textTypesMap.put("text/enriched", 11);
                    textTypesMap.put("text/rtf", 12);

                    // markup
                    textTypesMap.put("text/html", 13);
                    textTypesMap.put("text/xml", 14);
                    textTypesMap.put("text/sgml", 15);

                    textTypes = Collections.unmodifiableMap(textTypesMap);
                }

                {
                    Map<Class<?>, Integer> decodedTextRepresentationsMap = new HashMap<>(4, 1.0f);

                    decodedTextRepresentationsMap.put(char[].class, 0);
                    decodedTextRepresentationsMap.put(CharBuffer.class, 1);
                    decodedTextRepresentationsMap.put(String.class, 2);
                    decodedTextRepresentationsMap.put(Reader.class, 3);

                    decodedTextRepresentations =
                            Collections.unmodifiableMap(decodedTextRepresentationsMap);
                }

                {
                    Map<Class<?>, Integer> encodedTextRepresentationsMap = new HashMap<>(3, 1.0f);

                    encodedTextRepresentationsMap.put(byte[].class, 0);
                    encodedTextRepresentationsMap.put(ByteBuffer.class, 1);
                    encodedTextRepresentationsMap.put(InputStream.class, 2);

                    encodedTextRepresentations =
                            Collections.unmodifiableMap(encodedTextRepresentationsMap);
                }
            }

            public int compare(DataFlavor flavor1, DataFlavor flavor2) {
                if (flavor1.equals(flavor2)) {
                    return 0;
                }

                int comp;

                String primaryType1 = flavor1.getPrimaryType();
                String subType1 = flavor1.getSubType();
                String mimeType1 = primaryType1 + "/" + subType1;
                Class<?> class1 = flavor1.getRepresentationClass();

                String primaryType2 = flavor2.getPrimaryType();
                String subType2 = flavor2.getSubType();
                String mimeType2 = primaryType2 + "/" + subType2;
                Class<?> class2 = flavor2.getRepresentationClass();

                if (flavor1.isFlavorTextType() && flavor2.isFlavorTextType()) {
                    // First, compare MIME types
                    comp = compareIndices(textTypes, mimeType1, mimeType2,
                                          UNKNOWN_OBJECT_LOSES);
                    if (comp != 0) {
                        return comp;
                    }

                    // Only need to test one flavor because they both have the
                    // same MIME type. Also don't need to worry about accidentally
                    // passing stringFlavor because either
                    //   1. Both flavors are stringFlavor, in which case the
                    //      equality test at the top of the function succeeded.
                    //   2. Only one flavor is stringFlavor, in which case the MIME
                    //      type comparison returned a non-zero value.
                    if (doesSubtypeSupportCharset(flavor1)) {
                        // Next, prefer the decoded text representations of Reader,
                        // String, CharBuffer, and [C, in that order.
                        comp = compareIndices(decodedTextRepresentations, class1,
                                              class2, UNKNOWN_OBJECT_LOSES);
                        if (comp != 0) {
                            return comp;
                        }

                        // Next, compare charsets
                        comp = CharsetComparator.compareCharsets(getTextCharset(flavor1),
                                                                 getTextCharset(flavor2));
                        if (comp != 0) {
                            return comp;
                        }
                    }

                    // Finally, prefer the encoded text representations of
                    // InputStream, ByteBuffer, and [B, in that order.
                    comp = compareIndices(encodedTextRepresentations, class1,
                                          class2, UNKNOWN_OBJECT_LOSES);
                    if (comp != 0) {
                        return comp;
                    }
                } else {
                    // First, prefer text types
                    if (flavor1.isFlavorTextType()) {
                        return 1;
                    }

                    if (flavor2.isFlavorTextType()) {
                        return -1;
                    }

                    // Next, prefer application types.
                    comp = compareIndices(primaryTypes, primaryType1, primaryType2,
                                          UNKNOWN_OBJECT_LOSES);
                    if (comp != 0) {
                        return comp;
                    }

                    // Next, look for application/x-java-* types. Prefer unknown
                    // MIME types because if the user provides his own data flavor,
                    // it will likely be the most descriptive one.
                    comp = compareIndices(exactTypes, mimeType1, mimeType2,
                                          UNKNOWN_OBJECT_WINS);
                    if (comp != 0) {
                        return comp;
                    }

                    // Finally, prefer the representation classes of Remote,
                    // Serializable, and InputStream, in that order.
                    comp = compareIndices(nonTextRepresentations, class1, class2,
                                          UNKNOWN_OBJECT_LOSES);
                    if (comp != 0) {
                        return comp;
                    }
                }

                // The flavours are not equal but still not distinguishable.
                // Compare String representations in alphabetical order
                return flavor1.getMimeType().compareTo(flavor2.getMimeType());
            }
        }

        /** Text flavors beat everything else; among text flavors the DataFlavorComparator decides. */
        static final class TextFlavorComparator extends DataFlavorComparator {
            @Override
            public int compare(DataFlavor flavor1, DataFlavor flavor2) {
                if (flavor1.isFlavorTextType()) {
                    if (flavor2.isFlavorTextType()) {
                        return super.compare(flavor1, flavor2);
                    } else {
                        return 1;
                    }
                } else if (flavor2.isFlavorTextType()) {
                    return -1;
                } else {
                    return 0;
                }
            }
        }

        static final Comparator<DataFlavor> TEXT_FLAVOR_COMPARATOR = new TextFlavorComparator();
    }
}
