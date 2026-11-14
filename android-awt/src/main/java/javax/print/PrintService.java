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

import javax.print.attribute.Attribute;
import javax.print.attribute.AttributeSet;
import javax.print.attribute.PrintServiceAttribute;
import javax.print.attribute.PrintServiceAttributeSet;
import javax.print.event.PrintServiceAttributeListener;


/**
 * Interface PrintService is the factory for a DocPrintJob. A PrintService
 * describes the capabilities of a Printer and can be queried regarding
 * a printer's supported attributes.
 */
public interface PrintService {

    /** Returns a String name for this print service which may be used
      * by applications to request a particular print service.
      * @return name of the service.
      */
    public String getName();

    /**
     * Creates and returns a PrintJob capable of handling data from
     * any of the supported document flavors.
     * @return  a DocPrintJob object
     */
    public DocPrintJob createPrintJob();

    /**
     * Registers a listener for events on this PrintService.
     * @param listener  a PrintServiceAttributeListener, which
     *        monitors the status of a print service
     * @see #removePrintServiceAttributeListener
     */
    public void addPrintServiceAttributeListener(
                                       PrintServiceAttributeListener listener);

    /**
     * Removes the print-service listener from this print service.
     * @param listener  a PrintServiceAttributeListener object
     * @see #addPrintServiceAttributeListener
     */
    public void removePrintServiceAttributeListener(
                                       PrintServiceAttributeListener listener);

    /**
     * Obtains this print service's set of printer description attributes
     * giving this Print Service's status.
     *
     * @return  Unmodifiable snapshot of this Print Service's attribute set.
     *          May be empty, but not null.
     */
    public PrintServiceAttributeSet getAttributes();

    /**
     * Gets the value of the single specified service attribute.
     *
     * @param <T> the type of the specified service attribute
     * @param category the category of a PrintServiceAttribute supported
     * by this service - may not be null.
     * @return the value of the supported attribute or null if the
     * attribute is not supported by this service.
     * @exception NullPointerException if the category is null.
     * @exception  IllegalArgumentException
     *     (unchecked exception) if <CODE>category</CODE> is not a
     *     <code>Class</code> that implements interface
     *{@link javax.print.attribute.PrintServiceAttribute PrintServiceAttribute}.
     */
    public <T extends PrintServiceAttribute>
        T getAttribute(Class<T> category);

    /**
     * Determines the print data formats a client can specify when setting
     * up a job for this <code>PrintService</code>.
     *
     * @return Array of supported doc flavors, should have at least
     *         one element.
     */
    public DocFlavor[] getSupportedDocFlavors();

    /**
     * Determines if this print service supports a specific
     * <code>DocFlavor</code>.
     *
     * @param flavor the <code>DocFlavor</code>to query for support.
     * @return  <code>true</code> if this print service supports the
     * specified <code>DocFlavor</code>; <code>false</code> otherwise.
     * @exception  NullPointerException
     *     (unchecked exception) Thrown if <CODE>flavor</CODE> is null.
     */
    public boolean isDocFlavorSupported(DocFlavor flavor);


    /**
     * Determines the printing attribute categories a client can specify
     * when setting up a job for this print service.
     *
     * @return  Array of printing attribute categories that the client can
     *          specify as a doc-level or job-level attribute in a Print
     *          Request. Each element in the array is a {@link java.lang.Class
     *          Class} that implements interface {@link
     *          javax.print.attribute.Attribute Attribute}.
     *          The array is empty if no categories are supported.
     */
    public Class<?>[] getSupportedAttributeCategories();

    /**
     * Determines whether a client can specify the given printing
     * attribute category when setting up a job for this print service.
     *
     * @param  category  Printing attribute category to test. It must be a
     *                   {@link java.lang.Class Class} that implements
     *                   interface
     *                {@link javax.print.attribute.Attribute Attribute}.
     *
     * @return  <code>true</code> if the client can specify a doc-level or
     *          job-level attribute in <CODE>category</CODE> in a Print
     *          Request, <code>false</code> if it can't.
     */
    public boolean
        isAttributeCategorySupported(Class<? extends Attribute> category);

    /**
     * Determines this print service's default printing attribute value in
     * the given category.
     *
     * @param  category  Printing attribute category for which the default
     *                   attribute value is requested. It must be a {@link
     *                   java.lang.Class Class} that implements interface
     *                   {@link javax.print.attribute.Attribute
     *                   Attribute}.
     *
     * @return  Default attribute value for <CODE>category</CODE>, or null
     *       if this Print Service does not support specifying a doc-level or
     *          job-level attribute in <CODE>category</CODE> in a Print
     *          Request.
     */
    public Object
        getDefaultAttributeValue(Class<? extends Attribute> category);

    /**
     * Determines the printing attribute values a client can specify in
     * the given category when setting up a job for this print service.
     *
     * @param  category    Printing attribute category
     *                     about which to inquire.
     * @param  flavor      Doc flavor of a candidate doc. If null, then the
     *                     doc flavor is not taken into consideration.
     * @param  attributes  Set of printing attributes for a candidate job.
     *                     If null, then the job attributes are not taken
     *                     into consideration.
     *
     * @return  If this print service supports specifying a doc-level or
     *          job-level attribute in <CODE>category</CODE> in a Print
     *          Request, the return value is an object describing the
     *          supported values; otherwise null.
     */
    public Object
        getSupportedAttributeValues(Class<? extends Attribute> category,
                                    DocFlavor flavor,
                                    AttributeSet attributes);

    /**
     * Determines whether a client can specify the given printing
     * attribute
     * value when setting up a job for this Print Service.
     *
     * @param  attrval       Printing attribute value to test.
     * @param  flavor      Doc flavor of a candidate doc. If null, then the
     *                     doc flavor is not taken into consideration.
     * @param  attributes  Set of printing attributes for a candidate job.
     *                     If null, then the job attributes are not taken
     *                     into consideration.
     *
     * @return  <code>true</code> if this Print Service supports specifying
     *        <CODE>attrval</CODE> as a doc-level or job-level attribute in a
     *          Print Request, <code>false</code> if it doesn't.
     */
    public boolean isAttributeValueSupported(Attribute attrval,
                                             DocFlavor flavor,
                                             AttributeSet attributes);


    /**
     * Identifies the attributes that are unsupported for a print request
     * in the context of a particular DocFlavor.
     *
     * @param flavor      Doc flavor to test, or null
     * @param attributes  Set of printing attributes for a candidate job.
     *
     * @return  null if this Print Service supports the print request
     * specification, else the unsupported attributes.
     */
    public AttributeSet getUnsupportedAttributes(DocFlavor flavor,
                                           AttributeSet attributes);

    /**
     * Returns a factory for UI components which allow users to interact
     * with the service in various roles.
     *
     * @return null or a factory for browser UI components.
     */
    public ServiceUIFactory getServiceUIFactory();

    /**
     * Determines if two services are referring to the same underlying
     * service.
     *
     * @param obj  reference object with which to compare.
     * @return  <code>true</code> if this service is the same as the obj
     *          argument, <code>false</code> otherwise.
     */
    public boolean equals(Object obj);

    /**
     * This method should be implemented consistently with
     * <code>equals(Object)</code>.
     * @return hash code of this object.
     */
    public int hashCode();

}
