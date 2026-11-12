/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.awt.print;

import java.awt.HeadlessException;

import javax.print.DocFlavor;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.StreamPrintServiceFactory;
import javax.print.attribute.PrintRequestAttributeSet;

/**
 * The <code>PrinterJob</code> class is the principal class that controls
 * printing. An application calls methods in this class to set up a job,
 * optionally to invoke a print dialog with the user, and then to print
 * the pages of the job.
 */
public abstract class PrinterJob {

 /* Public Class Methods */

    /**
     * Creates and returns a <code>PrinterJob</code> which is initially
     * associated with the default printer.
     * If no printers are available on the system, a PrinterJob will still
     * be returned from this method, but <code>getPrintService()</code>
     * will return <code>null</code>, and calling
     * {@link #print() print} with this <code>PrinterJob</code> might
     * generate an exception.  Applications that need to determine if
     * there are suitable printers before creating a <code>PrinterJob</code>
     * should ensure that the array returned from
     * {@link #lookupPrintServices() lookupPrintServices} is not empty.
     * @return a new <code>PrinterJob</code>.
     */
    public static PrinterJob getPrinterJob() {
        // 8u instantiates the class named by the java.awt.printerjob property (sun.awt.windows.WPrinterJob
        // and friends). Android has no print service the shadow can reach, so the job is always the
        // printer-less one the spec describes above.
        return new net.runelite.awt.impl.HeadlessPrinterJob();
    }

    /**
     * A convenience method which looks up 2D print services.
     * Services returned from this method may be installed on
     * <code>PrinterJob</code>s which support print services.
     * Calling this method is equivalent to calling
     * {@link javax.print.PrintServiceLookup#lookupPrintServices(
     * DocFlavor, javax.print.attribute.AttributeSet)
     * PrintServiceLookup.lookupPrintServices()}
     * and specifying a Pageable DocFlavor.
     * @return a possibly empty array of 2D print services.
     * @since     1.4
     */
    public static PrintService[] lookupPrintServices() {
        return PrintServiceLookup.
            lookupPrintServices(DocFlavor.SERVICE_FORMATTED.PAGEABLE, null);
    }


    /**
     * A convenience method which locates factories for stream print
     * services which can image 2D graphics.
     * @param mimeType the required output format, or null to mean any format.
     * @return a possibly empty array of 2D stream print service factories.
     * @since     1.4
     */
    public static StreamPrintServiceFactory[]
        lookupStreamPrintServices(String mimeType) {
        return StreamPrintServiceFactory.lookupStreamPrintServiceFactories(
                                       DocFlavor.SERVICE_FORMATTED.PAGEABLE,
                                       mimeType);
    }


 /* Public Methods */

    /**
     * A <code>PrinterJob</code> object should be created using the
     * static {@link #getPrinterJob() getPrinterJob} method.
     */
    public PrinterJob() {
    }

    /**
     * Returns the service (printer) for this printer job.
     * Implementations of this class which do not support print services
     * may return null.  null will also be returned if no printers are
     * available.
     * @return the service for this printer job.
     * @see #setPrintService(PrintService)
     * @see #getPrinterJob()
     * @since     1.4
     */
    public PrintService getPrintService() {
        return null;
    }

    /**
     * Associate this PrinterJob with a new PrintService.
     * This method is overridden by subclasses which support
     * specifying a Print Service.
     *
     * Throws <code>PrinterException</code> if the specified service
     * cannot support the <code>Pageable</code> and
     * <code>Printable</code> interfaces necessary to support 2D printing.
     * @param service a print service that supports 2D printing
     * @exception PrinterException if the specified service does not support
     * 2D printing, or this PrinterJob class does not support
     * setting a 2D print service, or the specified service is
     * otherwise not a valid print service.
     * @see #getPrintService
     * @since     1.4
     */
    public void setPrintService(PrintService service)
        throws PrinterException {
            throw new PrinterException(
                         "Setting a service is not supported on this class");
    }

    /**
     * Calls <code>painter</code> to render the pages.  The pages in the
     * document to be printed by this
     * <code>PrinterJob</code> are rendered by the {@link Printable}
     * object, <code>painter</code>.  The {@link PageFormat} for each page
     * is the default page format.
     * @param painter the <code>Printable</code> that renders each page of
     * the document.
     */
    public abstract void setPrintable(Printable painter);

    /**
     * Calls <code>painter</code> to render the pages in the specified
     * <code>format</code>.
     * @param painter the <code>Printable</code> called to render
     *          each page of the document
     * @param format the size and orientation of each page to
     *                   be printed
     */
    public abstract void setPrintable(Printable painter, PageFormat format);

    /**
     * Queries <code>document</code> for the number of pages and
     * the <code>PageFormat</code> and <code>Printable</code> for each
     * page held in the <code>Pageable</code> instance,
     * <code>document</code>.
     * @param document the pages to be printed. It can not be
     * <code>null</code>.
     * @exception NullPointerException the <code>Pageable</code> passed in
     * was <code>null</code>.
     * @see PageFormat
     * @see Printable
     */
    public abstract void setPageable(Pageable document)
        throws NullPointerException;

    /**
     * Presents a dialog to the user for changing the properties of
     * the print job.
     * @return <code>true</code> if the user does not cancel the dialog;
     * <code>false</code> otherwise.
     * @exception HeadlessException if GraphicsEnvironment.isHeadless()
     * returns true.
     * @see java.awt.GraphicsEnvironment#isHeadless
     */
    public abstract boolean printDialog() throws HeadlessException;

    /**
     * A convenience method which displays a cross-platform print dialog
     * for all services which are capable of printing 2D graphics using the
     * <code>Pageable</code> interface.
     * @param attributes on input is application supplied attributes,
     * on output the contents are updated to reflect user choices.
     * This parameter may not be null.
     * @return <code>true</code> if the user does not cancel the dialog;
     * <code>false</code> otherwise.
     * @exception HeadlessException if GraphicsEnvironment.isHeadless()
     * returns true.
     * @exception NullPointerException if <code>attributes</code> parameter
     * is null.
     * @see java.awt.GraphicsEnvironment#isHeadless
     * @since     1.4
     */
    public boolean printDialog(PrintRequestAttributeSet attributes)
        throws HeadlessException {

        if (attributes == null) {
            throw new NullPointerException("attributes");
        }
        return printDialog();
    }

    /**
     * Displays a dialog that allows modification of a
     * <code>PageFormat</code> instance.
     * @param page the default <code>PageFormat</code> presented to the
     *                  user for modification
     * @return    the original <code>page</code> object if the dialog
     *            is cancelled; a new <code>PageFormat</code> object
     *            containing the format indicated by the user if the
     *            dialog is acknowledged.
     * @exception HeadlessException if GraphicsEnvironment.isHeadless()
     * returns true.
     * @see java.awt.GraphicsEnvironment#isHeadless
     * @since     1.2
     */
    public abstract PageFormat pageDialog(PageFormat page)
        throws HeadlessException;

    /**
     * A convenience method which displays a cross-platform page setup dialog.
     * @param attributes on input is application supplied attributes,
     * on output the contents are updated to reflect user choices.
     * This parameter may not be null.
     * @return a page format if the user does not cancel the dialog;
     * <code>null</code> otherwise.
     * @exception HeadlessException if GraphicsEnvironment.isHeadless()
     * returns true.
     * @exception NullPointerException if <code>attributes</code> parameter
     * is null.
     * @see java.awt.GraphicsEnvironment#isHeadless
     * @since     1.4
     */
    public PageFormat pageDialog(PrintRequestAttributeSet attributes)
        throws HeadlessException {

        if (attributes == null) {
            throw new NullPointerException("attributes");
        }
        return pageDialog(defaultPage());
    }

    /**
     * Clones the <code>PageFormat</code> argument and alters the
     * clone to describe a default page size and orientation.
     * @param page the <code>PageFormat</code> to be cloned and altered
     * @return clone of <code>page</code>, altered to describe a default
     *                      <code>PageFormat</code>.
     */
    public abstract PageFormat defaultPage(PageFormat page);

    /**
     * Creates a new <code>PageFormat</code> instance and
     * sets it to a default size and orientation.
     * @return a <code>PageFormat</code> set to a default size and
     *          orientation.
     */
    public PageFormat defaultPage() {
        return defaultPage(new PageFormat());
    }

    /**
     * Calculates a <code>PageFormat</code> with values consistent with those
     * supported by the current <code>PrintService</code> for this job
     * (ie the value returned by <code>getPrintService()</code>) and media,
     * printable area and orientation contained in <code>attributes</code>.
     * <p>
     * Calling this method does not update the job.
     * It is useful for clients that have a set of attributes obtained from
     * <code>printDialog(PrintRequestAttributeSet attributes)</code>
     * and need a PageFormat to print a Pageable object.
     * @param attributes a set of printing attributes, for example obtained
     * from calling printDialog. If <code>attributes</code> is null a default
     * PageFormat is returned.
     * @return a <code>PageFormat</code> whose settings conform with
     * those of the current service and the specified attributes.
     * @since 1.6
     */
    public PageFormat getPageFormat(PrintRequestAttributeSet attributes) {
        // 8u maps the Media, MediaPrintableArea and OrientationRequested attributes of the current
        // service onto the page. Without a print service (getPrintService() is null here) 8u takes
        // the same early return, so the default page is the faithful answer, and the
        // javax.print.attribute.standard tree that the mapping needs is not shadowed.
        return defaultPage();
    }

    /**
     * Returns the clone of <code>page</code> with its settings
     * adjusted to be compatible with the current printer of this
     * <code>PrinterJob</code>.
     * @param page the current settings of the print job
     * @return a <code>PageFormat</code> that is cloned from
     *          <code>page</code> and whose settings are changed
     *          to conform with this <code>PrinterJob</code>.
     */
    public abstract PageFormat validatePage(PageFormat page);

    /**
     * Prints a set of pages.
     * @exception PrinterException an error in the print system
     *            caused the job to be aborted.
     * @see Book
     * @see Pageable
     * @see Printable
     */
    public abstract void print() throws PrinterException;

   /**
     * Prints a set of pages using the settings in the attribute
     * set. The default implementation ignores the attribute set.
     * @param attributes a set of attributes for the job
     * @exception PrinterException an error in the print system
     *            caused the job to be aborted.
     * @see Book
     * @see Pageable
     * @see Printable
     * @since 1.4
     */
    public void print(PrintRequestAttributeSet attributes)
        throws PrinterException {
        print();
    }

    /**
     * Sets the number of copies to be printed.
     * @param copies the number of copies to be printed
     * @see #getCopies
     */
    public abstract void setCopies(int copies);

    /**
     * Gets the number of copies to be printed.
     * @return the number of copies to be printed.
     * @see #setCopies
     */
    public abstract int getCopies();

    /**
     * Gets the name of the printing user.
     * @return the name of the printing user
     */
    public abstract String getUserName();

    /**
     * Sets the name of the document to be printed.
     * The document name can not be <code>null</code>.
     * @param jobName the name of the document to be printed
     * @see #getJobName
     */
    public abstract void setJobName(String jobName);

    /**
     * Gets the name of the document to be printed.
     * @return the name of the document to be printed.
     * @see #setJobName
     */
    public abstract String getJobName();

    /**
     * Cancels a print job that is in progress.  If
     * {@link #print() print} has been called but has not
     * returned then this method signals
     * that the job should be cancelled at the next
     * chance. If there is no print job in progress then
     * this call does nothing.
     */
    public abstract void cancel();

    /**
     * Returns <code>true</code> if a print job is
     * in progress, but is going to be cancelled
     * at the next opportunity; otherwise returns
     * <code>false</code>.
     * @return <code>true</code> if the job in progress
     * is going to be cancelled; <code>false</code> otherwise.
     */
    public abstract boolean isCancelled();

}
