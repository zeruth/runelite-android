/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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

package javax.print;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Class <code>DocFlavor</code> encapsulates an object that specifies the
 * format in which print data is supplied to a {@link DocPrintJob}.
 * "Doc" is a short, easy-to-pronounce term that means "a piece of print data."
 * The print data format, or "doc flavor", consists of two things:
 * <UL>
 * <LI>
 * <B>MIME type.</B> This is a Multipurpose Internet Mail Extensions (MIME)
 * media type (as defined in <A HREF="http://www.ietf.org/rfc/rfc2045.txt">RFC
 * 2045</A> and <A HREF="http://www.ietf.org/rfc/rfc2046.txt">RFC 2046</A>)
 * that specifies how the print data is to be interpreted.
 * <LI>
 * <B>Representation class name.</B> This specifies the fully-qualified name of
 * the class of the object from which the actual print data comes, as returned
 * by the {@link java.lang.Class#getName() Class.getName()} method.
 * </UL>
 */
public class DocFlavor implements Serializable, Cloneable {

    private static final long serialVersionUID = -4512080796965449721L;

    /**
     * A String representing the host operating system encoding.
     * This will follow the conventions documented in
     * <a href="http://www.ietf.org/rfc/rfc2278.txt">
     * <i>RFC&nbsp;2278:&nbsp;IANA Charset Registration Procedures</i></a>
     * except where historical names are returned for compatibility with
     * previous versions of the Java platform.
     * The value returned from method is valid only for the VM which
     * returns it, for use in a DocFlavor.
     * This is the charset for all the "HOST" pre-defined DocFlavors in
     * the executing VM.
     */
    public static final String hostEncoding;

    static {
        hostEncoding = java.nio.charset.Charset.defaultCharset().name();
    }

    /**
     * MIME type.
     */
    private transient MimeType myMimeType;

    /**
     * Representation class name.
     * @serial
     */
    private String myClassName;

    /**
     * String value for this doc flavor. Computed when needed and cached.
     */
    private transient String myStringValue = null;


    /**
     * Constructs a new doc flavor object from the given MIME type and
     * representation class name. The given MIME type is converted into
     * canonical form and stored internally.
     *
     * @param  mimeType   MIME media type string.
     * @param  className  Fully-qualified representation class name.
     *
     * @exception  NullPointerException
     *     (unchecked exception) Thrown if <CODE>mimeType</CODE> is null or
     *     <CODE>className</CODE> is null.
     * @exception  IllegalArgumentException
     *     (unchecked exception) Thrown if <CODE>mimeType</CODE> does not
     *     obey the syntax for a MIME media type string.
     */
    public DocFlavor(String mimeType, String className) {
        if (className == null) {
            throw new NullPointerException();
        }
        myMimeType = new MimeType (mimeType);
        myClassName = className;
    }

    /**
     * Returns this doc flavor object's MIME type string based on the
     * canonical form. Each parameter value is enclosed in quotes.
     * @return the mime type
     */
    public String getMimeType() {
        return myMimeType.getMimeType();
    }

    /**
     * Returns this doc flavor object's media type (from the MIME type).
     * @return the media type
     */
    public String getMediaType() {
        return myMimeType.getMediaType();
    }

    /**
     * Returns this doc flavor object's media subtype (from the MIME type).
     * @return the media sub-type
     */
    public String getMediaSubtype() {
        return myMimeType.getMediaSubtype();
    }

    /**
     * Returns a <code>String</code> representing a MIME
     * parameter.
     * Mime types may include parameters which are usually optional.
     * The charset for text types is a commonly useful example.
     * This convenience method will return the value of the specified
     * parameter if one was specified in the mime type for this flavor.
     *
     * @param paramName the name of the paramater. This name is internally
     * converted to the canonical lower case format before performing
     * the match.
     * @return String representing a mime parameter, or
     * null if that parameter is not in the mime type string.
     * @exception NullPointerException if paramName is null.
     */
    public String getParameter(String paramName) {
        return myMimeType.getParameterMap().get(paramName.toLowerCase());
    }

    /**
     * Returns the name of this doc flavor object's representation class.
     * @return the name of the representation class.
     */
    public String getRepresentationClassName() {
        return myClassName;
    }

    /**
     * Converts this <code>DocFlavor</code> to a string.
     *
     * @return  MIME type string based on the canonical form. Each parameter
     *          value is enclosed in quotes.
     *          A "class=" parameter is appended to the
     *          MIME type string to indicate the representation class name.
     */
    public String toString() {
        return getStringValue();
    }

    /**
     * Returns a hash code for this doc flavor object.
     */
    public int hashCode() {
        return getStringValue().hashCode();
    }

    /**
     * Determines if this doc flavor object is equal to the given object.
     * The two are equal if the given object is not null, is an instance
     * of <code>DocFlavor</code>, has a MIME type equivalent to this doc
     * flavor object's MIME type (that is, the MIME types have the same media
     * type, media subtype, and parameters), and has the same representation
     * class name as this doc flavor object. Thus, if two doc flavor objects'
     * MIME types are the same except for comments, they are considered equal.
     * However, two doc flavor objects with MIME types of "text/plain" and
     * "text/plain; charset=US-ASCII" are not considered equal, even though
     * they represent the same media type (because the default character
     * set for plain text is US-ASCII).
     *
     * @param  obj  Object to test.
     *
     * @return  True if this doc flavor object equals <CODE>obj</CODE>, false
     *          otherwise.
     */
    public boolean equals(Object obj) {
        return
            obj != null &&
            obj instanceof DocFlavor &&
            getStringValue().equals (((DocFlavor) obj).getStringValue());
    }

    /**
     * Returns this doc flavor object's string value.
     */
    private String getStringValue() {
        if (myStringValue == null) {
            myStringValue = myMimeType + "; class=\"" + myClassName + "\"";
        }
        return myStringValue;
    }

    /**
     * Write the instance to a stream (ie serialize the object).
     */
    private void writeObject(ObjectOutputStream s) throws IOException {

        s.defaultWriteObject();
        s.writeObject(myMimeType.getMimeType());
    }

    /**
     * Reconstitute an instance from a stream (that is, deserialize it).
     *
     * @serialData
     * The serialised form of a DocFlavor is the String naming the
     * representation class followed by the String representing the canonical
     * form of the mime type.
     */
    private void readObject(ObjectInputStream s)
        throws ClassNotFoundException, IOException {

        s.defaultReadObject();
        myMimeType = new MimeType((String)s.readObject());
    }

    /**
     * Class DocFlavor.BYTE_ARRAY provides predefined static constant
     * DocFlavor objects for example doc flavors using a byte array
     * (<CODE>byte[]</CODE>) as the print data representation class.
     */
    public static class BYTE_ARRAY extends DocFlavor {

        private static final long serialVersionUID = -9065578006593857475L;

        /**
         * Constructs a new doc flavor with the given MIME type and a print
         * data representation class name of <CODE>"[B"</CODE> (byte array).
         *
         * @param  mimeType   MIME media type string.
         *
         * @exception  NullPointerException
         *     (unchecked exception) Thrown if <CODE>mimeType</CODE> is null.
         * @exception  IllegalArgumentException
         *     (unchecked exception) Thrown if <CODE>mimeType</CODE> does not
         *     obey the syntax for a MIME media type string.
         */
        public BYTE_ARRAY (String mimeType) {
            super (mimeType, "[B");
        }

        /** "text/plain" with the host encoding, byte array. */
        public static final BYTE_ARRAY TEXT_PLAIN_HOST =
            new BYTE_ARRAY ("text/plain; charset="+hostEncoding);

        /** "text/plain; charset=utf-8", byte array. */
        public static final BYTE_ARRAY TEXT_PLAIN_UTF_8 =
            new BYTE_ARRAY ("text/plain; charset=utf-8");

        /** "text/plain; charset=utf-16", byte array. */
        public static final BYTE_ARRAY TEXT_PLAIN_UTF_16 =
            new BYTE_ARRAY ("text/plain; charset=utf-16");

        /** "text/plain; charset=utf-16be", byte array. */
        public static final BYTE_ARRAY TEXT_PLAIN_UTF_16BE =
            new BYTE_ARRAY ("text/plain; charset=utf-16be");

        /** "text/plain; charset=utf-16le", byte array. */
        public static final BYTE_ARRAY TEXT_PLAIN_UTF_16LE =
            new BYTE_ARRAY ("text/plain; charset=utf-16le");

        /** "text/plain; charset=us-ascii", byte array. */
        public static final BYTE_ARRAY TEXT_PLAIN_US_ASCII =
            new BYTE_ARRAY ("text/plain; charset=us-ascii");


        /** "text/html" with the host encoding, byte array. */
        public static final BYTE_ARRAY TEXT_HTML_HOST =
            new BYTE_ARRAY ("text/html; charset="+hostEncoding);

        /** "text/html; charset=utf-8", byte array. */
        public static final BYTE_ARRAY TEXT_HTML_UTF_8 =
            new BYTE_ARRAY ("text/html; charset=utf-8");

        /** "text/html; charset=utf-16", byte array. */
        public static final BYTE_ARRAY TEXT_HTML_UTF_16 =
            new BYTE_ARRAY ("text/html; charset=utf-16");

        /** "text/html; charset=utf-16be", byte array. */
        public static final BYTE_ARRAY TEXT_HTML_UTF_16BE =
            new BYTE_ARRAY ("text/html; charset=utf-16be");

        /** "text/html; charset=utf-16le", byte array. */
        public static final BYTE_ARRAY TEXT_HTML_UTF_16LE =
            new BYTE_ARRAY ("text/html; charset=utf-16le");

        /** "text/html; charset=us-ascii", byte array. */
        public static final BYTE_ARRAY TEXT_HTML_US_ASCII =
            new BYTE_ARRAY ("text/html; charset=us-ascii");


        /** "application/pdf", byte array. */
        public static final BYTE_ARRAY PDF = new BYTE_ARRAY ("application/pdf");

        /** "application/postscript", byte array. */
        public static final BYTE_ARRAY POSTSCRIPT =
            new BYTE_ARRAY ("application/postscript");

        /** "application/vnd.hp-PCL", byte array. */
        public static final BYTE_ARRAY PCL =
            new BYTE_ARRAY ("application/vnd.hp-PCL");

        /** "image/gif", byte array. */
        public static final BYTE_ARRAY GIF = new BYTE_ARRAY ("image/gif");

        /** "image/jpeg", byte array. */
        public static final BYTE_ARRAY JPEG = new BYTE_ARRAY ("image/jpeg");

        /** "image/png", byte array. */
        public static final BYTE_ARRAY PNG = new BYTE_ARRAY ("image/png");

        /** "application/octet-stream", byte array; the service autosenses the format. */
        public static final BYTE_ARRAY AUTOSENSE =
            new BYTE_ARRAY ("application/octet-stream");

    }

    /**
     * Class DocFlavor.INPUT_STREAM provides predefined static constant
     * DocFlavor objects for example doc flavors using a byte stream ({@link
     * java.io.InputStream java.io.InputStream}) as the print
     * data representation class.
     */
    public static class INPUT_STREAM extends DocFlavor {

        private static final long serialVersionUID = -7045842700749194127L;

        /**
         * Constructs a new doc flavor with the given MIME type and a print
         * data representation class name of
         * <CODE>"java.io.InputStream"</CODE> (byte stream).
         *
         * @param  mimeType   MIME media type string.
         */
        public INPUT_STREAM (String mimeType) {
            super (mimeType, "java.io.InputStream");
        }

        /** "text/plain" with the host encoding, input stream. */
        public static final INPUT_STREAM TEXT_PLAIN_HOST =
            new INPUT_STREAM ("text/plain; charset="+hostEncoding);

        /** "text/plain; charset=utf-8", input stream. */
        public static final INPUT_STREAM TEXT_PLAIN_UTF_8 =
            new INPUT_STREAM ("text/plain; charset=utf-8");

        /** "text/plain; charset=utf-16", input stream. */
        public static final INPUT_STREAM TEXT_PLAIN_UTF_16 =
            new INPUT_STREAM ("text/plain; charset=utf-16");

        /** "text/plain; charset=utf-16be", input stream. */
        public static final INPUT_STREAM TEXT_PLAIN_UTF_16BE =
            new INPUT_STREAM ("text/plain; charset=utf-16be");

        /** "text/plain; charset=utf-16le", input stream. */
        public static final INPUT_STREAM TEXT_PLAIN_UTF_16LE =
            new INPUT_STREAM ("text/plain; charset=utf-16le");

        /** "text/plain; charset=us-ascii", input stream. */
        public static final INPUT_STREAM TEXT_PLAIN_US_ASCII =
                new INPUT_STREAM ("text/plain; charset=us-ascii");

        /** "text/html" with the host encoding, input stream. */
        public static final INPUT_STREAM TEXT_HTML_HOST =
            new INPUT_STREAM ("text/html; charset="+hostEncoding);

        /** "text/html; charset=utf-8", input stream. */
        public static final INPUT_STREAM TEXT_HTML_UTF_8 =
            new INPUT_STREAM ("text/html; charset=utf-8");

        /** "text/html; charset=utf-16", input stream. */
        public static final INPUT_STREAM TEXT_HTML_UTF_16 =
            new INPUT_STREAM ("text/html; charset=utf-16");

        /** "text/html; charset=utf-16be", input stream. */
        public static final INPUT_STREAM TEXT_HTML_UTF_16BE =
            new INPUT_STREAM ("text/html; charset=utf-16be");

        /** "text/html; charset=utf-16le", input stream. */
        public static final INPUT_STREAM TEXT_HTML_UTF_16LE =
            new INPUT_STREAM ("text/html; charset=utf-16le");

        /** "text/html; charset=us-ascii", input stream. */
        public static final INPUT_STREAM TEXT_HTML_US_ASCII =
            new INPUT_STREAM ("text/html; charset=us-ascii");

        /** "application/pdf", input stream. */
        public static final INPUT_STREAM PDF = new INPUT_STREAM ("application/pdf");

        /** "application/postscript", input stream. */
        public static final INPUT_STREAM POSTSCRIPT =
            new INPUT_STREAM ("application/postscript");

        /** "application/vnd.hp-PCL", input stream. */
        public static final INPUT_STREAM PCL =
            new INPUT_STREAM ("application/vnd.hp-PCL");

        /** "image/gif", input stream. */
        public static final INPUT_STREAM GIF = new INPUT_STREAM ("image/gif");

        /** "image/jpeg", input stream. */
        public static final INPUT_STREAM JPEG = new INPUT_STREAM ("image/jpeg");

        /** "image/png", input stream. */
        public static final INPUT_STREAM PNG = new INPUT_STREAM ("image/png");

        /** "application/octet-stream", input stream; the service autosenses the format. */
        public static final INPUT_STREAM AUTOSENSE =
            new INPUT_STREAM ("application/octet-stream");

    }

    /**
     * Class DocFlavor.URL provides predefined static constant DocFlavor
     * objects.
     * For example doc flavors using a Uniform Resource Locator ({@link
     * java.net.URL java.net.URL}) as the print data
     * representation  class.
     */
    public static class URL extends DocFlavor {

        private static final long serialVersionUID = 2936725788144902062L;

        /**
         * Constructs a new doc flavor with the given MIME type and a print
         * data representation class name of <CODE>"java.net.URL"</CODE>.
         *
         * @param  mimeType   MIME media type string.
         */
        public URL (String mimeType) {
            super (mimeType, "java.net.URL");
        }

        /** "text/plain" with the host encoding, URL. */
        public static final URL TEXT_PLAIN_HOST =
            new URL ("text/plain; charset="+hostEncoding);

        /** "text/plain; charset=utf-8", URL. */
        public static final URL TEXT_PLAIN_UTF_8 =
            new URL ("text/plain; charset=utf-8");

        /** "text/plain; charset=utf-16", URL. */
        public static final URL TEXT_PLAIN_UTF_16 =
            new URL ("text/plain; charset=utf-16");

        /** "text/plain; charset=utf-16be", URL. */
        public static final URL TEXT_PLAIN_UTF_16BE =
            new URL ("text/plain; charset=utf-16be");

        /** "text/plain; charset=utf-16le", URL. */
        public static final URL TEXT_PLAIN_UTF_16LE =
            new URL ("text/plain; charset=utf-16le");

        /** "text/plain; charset=us-ascii", URL. */
        public static final URL TEXT_PLAIN_US_ASCII =
            new URL ("text/plain; charset=us-ascii");

        /** "text/html" with the host encoding, URL. */
        public static final URL TEXT_HTML_HOST =
            new URL ("text/html; charset="+hostEncoding);

        /** "text/html; charset=utf-8", URL. */
        public static final URL TEXT_HTML_UTF_8 =
            new URL ("text/html; charset=utf-8");

        /** "text/html; charset=utf-16", URL. */
        public static final URL TEXT_HTML_UTF_16 =
            new URL ("text/html; charset=utf-16");

        /** "text/html; charset=utf-16be", URL. */
        public static final URL TEXT_HTML_UTF_16BE =
            new URL ("text/html; charset=utf-16be");

        /** "text/html; charset=utf-16le", URL. */
        public static final URL TEXT_HTML_UTF_16LE =
            new URL ("text/html; charset=utf-16le");

        /** "text/html; charset=us-ascii", URL. */
        public static final URL TEXT_HTML_US_ASCII =
            new URL ("text/html; charset=us-ascii");

        /** "application/pdf", URL. */
        public static final URL PDF = new URL ("application/pdf");

        /** "application/postscript", URL. */
        public static final URL POSTSCRIPT = new URL ("application/postscript");

        /** "application/vnd.hp-PCL", URL. */
        public static final URL PCL = new URL ("application/vnd.hp-PCL");

        /** "image/gif", URL. */
        public static final URL GIF = new URL ("image/gif");

        /** "image/jpeg", URL. */
        public static final URL JPEG = new URL ("image/jpeg");

        /** "image/png", URL. */
        public static final URL PNG = new URL ("image/png");

        /** "application/octet-stream", URL; the service autosenses the format. */
        public static final URL AUTOSENSE = new URL ("application/octet-stream");

    }

    /**
     * Class DocFlavor.CHAR_ARRAY provides predefined static constant
     * DocFlavor objects for example doc flavors using a character array
     * (<CODE>char[]</CODE>) as the print data representation class. As such,
     * the character set is Unicode.
     */
    public static class CHAR_ARRAY extends DocFlavor {

        private static final long serialVersionUID = -8720590903724405128L;

        /**
         * Constructs a new doc flavor with the given MIME type and a print
         * data representation class name of
         * <CODE>"[C"</CODE> (character array).
         *
         * @param  mimeType  MIME media type string. If it is a text media
         *                      type, it is assumed to contain a
         *                      <CODE>"charset=utf-16"</CODE> parameter.
         */
        public CHAR_ARRAY (String mimeType) {
            super (mimeType, "[C");
        }

        /** "text/plain; charset=utf-16", character array. */
        public static final CHAR_ARRAY TEXT_PLAIN =
            new CHAR_ARRAY ("text/plain; charset=utf-16");

        /** "text/html; charset=utf-16", character array. */
        public static final CHAR_ARRAY TEXT_HTML =
            new CHAR_ARRAY ("text/html; charset=utf-16");

    }

    /**
     * Class DocFlavor.STRING provides predefined static constant DocFlavor
     * objects for example doc flavors using a string ({@link java.lang.String
     * java.lang.String}) as the print data representation class.
     * As such, the character set is Unicode.
     */
    public static class STRING extends DocFlavor {

        private static final long serialVersionUID = 4414407504887034035L;

        /**
         * Constructs a new doc flavor with the given MIME type and a print
         * data representation class name of <CODE>"java.lang.String"</CODE>.
         *
         * @param  mimeType  MIME media type string. If it is a text media
         *                      type, it is assumed to contain a
         *                      <CODE>"charset=utf-16"</CODE> parameter.
         */
        public STRING (String mimeType) {
            super (mimeType, "java.lang.String");
        }

        /** "text/plain; charset=utf-16", string. */
        public static final STRING TEXT_PLAIN =
            new STRING ("text/plain; charset=utf-16");

        /** "text/html; charset=utf-16", string. */
        public static final STRING TEXT_HTML =
            new STRING ("text/html; charset=utf-16");
    }

    /**
     * Class DocFlavor.READER provides predefined static constant DocFlavor
     * objects for example doc flavors using a character stream ({@link
     * java.io.Reader java.io.Reader}) as the print data
     * representation class. As such, the character set is Unicode.
     */
    public static class READER extends DocFlavor {

        private static final long serialVersionUID = 7100295812579351567L;

        /**
         * Constructs a new doc flavor with the given MIME type and a print
         * data representation class name of
         * <CODE>"java.io.Reader"</CODE> (character stream).
         *
         * @param  mimeType  MIME media type string. If it is a text media
         *                      type, it is assumed to contain a
         *                      <CODE>"charset=utf-16"</CODE> parameter.
         */
        public READER (String mimeType) {
            super (mimeType, "java.io.Reader");
        }

        /** "text/plain; charset=utf-16", reader. */
        public static final READER TEXT_PLAIN =
            new READER ("text/plain; charset=utf-16");

        /** "text/html; charset=utf-16", reader. */
        public static final READER TEXT_HTML =
            new READER ("text/html; charset=utf-16");

    }

    /**
     * Class DocFlavor.SERVICE_FORMATTED provides predefined static constant
     * DocFlavor objects for example doc flavors for service formatted print
     * data.
     */
    public static class SERVICE_FORMATTED extends DocFlavor {

        private static final long serialVersionUID = 6181337766266637256L;

        /**
         * Constructs a new doc flavor with a MIME type of
         * <CODE>"application/x-java-jvm-local-objectref"</CODE> indicating
         * service formatted print data and the given print data
         * representation class name.
         *
         * @param  className  Fully-qualified representation class name.
         *
         * @exception  NullPointerException
         *     (unchecked exception) Thrown if <CODE>className</CODE> is
         *     null.
         */
        public SERVICE_FORMATTED (String className) {
            super ("application/x-java-jvm-local-objectref", className);
        }

        /**
         * Service formatted print data doc flavor with print data
         * representation class name =
         * <CODE>"java.awt.image.renderable.RenderableImage"</CODE>
         * (renderable image object).
         */
        public static final SERVICE_FORMATTED RENDERABLE_IMAGE =
            new SERVICE_FORMATTED("java.awt.image.renderable.RenderableImage");

        /**
         * Service formatted print data doc flavor with print data
         * representation class name = <CODE>"java.awt.print.Printable"</CODE>
         * (printable object).
         */
        public static final SERVICE_FORMATTED PRINTABLE =
            new SERVICE_FORMATTED ("java.awt.print.Printable");

        /**
         * Service formatted print data doc flavor with print data
         * representation class name = <CODE>"java.awt.print.Pageable"</CODE>
         * (pageable object).
         */
        public static final SERVICE_FORMATTED PAGEABLE =
            new SERVICE_FORMATTED ("java.awt.print.Pageable");

        }

}
