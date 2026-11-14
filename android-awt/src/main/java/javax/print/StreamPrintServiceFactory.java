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

import java.io.OutputStream;

/**
 * A <code>StreamPrintServiceFactory</code> is the factory for
 * {@link StreamPrintService} instances,
 * which can print to an output stream in a particular
 * document format described as a mime type.
 * A typical output document format may be Postscript(TM).
 * <p>
 * This class is implemented by a service and located by the
 * implementation using the
 * <a href="../../../technotes/guides/jar/jar.html#Service%20Provider">
 * SPI JAR File specification</a>.
 */

public abstract class StreamPrintServiceFactory {

    /**
     * Locates factories for print services that can be used with
     * a print job to output a stream of data in the
     * format specified by {@code outputMimeType}.
     *
     * @param flavor of the input document type - null means match all
     * types.
     * @param outputMimeType representing the required output format, used to
     * identify suitable stream printer factories. A value of null means
     * match all formats.
     * @return - matching factories for stream print service instance,
     *           empty if no suitable factories could be located.
     */
     public static StreamPrintServiceFactory[]
         lookupStreamPrintServiceFactories(DocFlavor flavor,
                                           String outputMimeType) {
         // 8u collects the META-INF/services providers per AppContext; the desktop ones
         // (sun.print.PSStreamPrinterFactory) are not shadowed and Android ships none, so the
         // lookup is always the spec's empty answer.
         return new StreamPrintServiceFactory[0];
     }


    /** Queries the factory for the document format that is emitted
     * by printers obtained from this factory.
     *
     * @return the output format described as a mime type.
     */
    public abstract String getOutputFormat();

    /**
     * Queries the factory for the document flavors that can be accepted
     * by printers obtained from this factory.
     * @return array of supported doc flavors.
     */
    public abstract DocFlavor[] getSupportedDocFlavors();

    /**
     * Returns a <code>StreamPrintService</code> that can print to
     * the specified output stream.
     * The output stream is created and managed by the application.
     * It is the application's responsibility to close the stream and
     * to ensure that this Printer is not reused.
     *
     * @param out destination stream for generated output.
     * @return a PrintService which will generate the format specified by the
     * DocFlavor supported by this Factory.
     */
    public abstract StreamPrintService getPrintService(OutputStream out);

}
