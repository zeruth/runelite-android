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

import java.util.ArrayList;

import javax.print.attribute.AttributeSet;

/** Implementations of this class provide lookup services for
  * print services (typically equivalent to printers) of a particular type.
  * <p>
  * Multiple implementations may be installed concurrently.
  * All implementations must be able to describe the located printers
  * as instances of a PrintService.
  * Typically implementations of this service class are located
  * automatically in JAR files (see the SPI JAR file specification).
  * These classes must be instantiable using a default constructor.
  * Alternatively applications may explicitly register instances
  * at runtime.
  * <p>
  * Applications use only the static methods of this abstract class.
  * The instance methods are implemented by a service provider in a subclass
  * and the unification of the results from all installed lookup classes
  * are reported by the static methods of this class when called by
  * the application.
  */
public abstract class PrintServiceLookup {

    // 8u keeps these per AppContext and seeds the lookup list from META-INF/services
    // (sun.print.Win32PrintServiceLookup and friends). Android ships no provider, so only
    // what an application registers explicitly is ever found; one process-wide registry
    // is the AppContext the shadow has.
    private static final ArrayList<PrintServiceLookup> LOOKUP_SERVICES =
        new ArrayList<PrintServiceLookup>();
    private static final ArrayList<PrintService> REGISTERED_SERVICES =
        new ArrayList<PrintService>();

    /**
     * Locates print services capable of printing the specified
     * {@link DocFlavor}.
     *
     * @param flavor the flavor to print. If null, this constraint is not
     *        used.
     * @param attributes attributes that the print service must support.
     * If null this constraint is not used.
     *
     * @return array of matching <code>PrintService</code> objects
     * representing print services that support the specified flavor
     * attributes.  If no services match, the array is zero-length.
     */
    public static final PrintService[]
        lookupPrintServices(DocFlavor flavor,
                            AttributeSet attributes) {
        ArrayList<PrintService> list = getServices(flavor, attributes);
        return list.toArray(new PrintService[list.size()]);
    }


    /**
     * Locates MultiDoc print Services capable of printing MultiDocs
     * containing all the specified doc flavors.
     *
     * @param flavors the flavors to print. If null or empty this
     *        constraint is not used.
     * @param attributes attributes that the print service must
     * support.  If null this constraint is not used.
     *
     * @return array of matching {@link MultiDocPrintService} objects.
     * If no services match, the array is zero-length.
     *
     */
    public static final MultiDocPrintService[]
        lookupMultiDocPrintServices(DocFlavor[] flavors,
                                    AttributeSet attributes) {
        ArrayList<MultiDocPrintService> list = getMultiDocServices(flavors, attributes);
        return list.toArray(new MultiDocPrintService[list.size()]);
    }


    /**
     * Locates the default print service for this environment.
     * This may return null.
     * If multiple lookup services each specify a default, the
     * chosen service is not precisely defined, but a
     * platform native service, rather than an installed service,
     * is usually returned as the default.  If there is no clearly
     * identifiable
     * platform native default print service, the default is the first
     * to be located in an implementation-dependent manner.
     *
     * @return the default PrintService.
     */

    public static final PrintService lookupDefaultPrintService() {

        for (PrintServiceLookup lus : getAllLookupServices()) {
            PrintService service = lus.getDefaultPrintService();
            if (service != null) {
                return service;
            }
        }
        return null;
    }


    /**
     * Allows an application to explicitly register a class that
     * implements lookup services. The registration will not persist
     * across VM invocations.
     * This is useful if an application needs to make a new service
     * available that is not part of the installation.
     * If the lookup service is already registered, or cannot be registered,
     * the method returns false.
     *
     * @param sp an implementation of a lookup service.
     * @return <code>true</code> if the new lookup service is newly
     *         registered; <code>false</code> otherwise.
     */
    public static boolean registerServiceProvider(PrintServiceLookup sp) {
        synchronized (PrintServiceLookup.class) {
            if (LOOKUP_SERVICES.contains(sp)) {
                return false;
            }
            LOOKUP_SERVICES.add(sp);
            return true;
        }
    }


    /**
     * Allows an application to directly register an instance of a
     * class which implements a print service.
     * The lookup operations for this service will be
     * performed by the PrintServiceLookup class using the attribute
     * values and classes reported by the service.
     * This may be less efficient than a lookup
     * service tuned for that service.
     * Therefore registering a <code>PrintServiceLookup</code> instance
     * instead is recommended.
     * The method returns true if this service is not previously
     * registered and is now successfully registered.
     * This method should not be called with StreamPrintService instances.
     * They will always fail to register and the method will return false.
     * @param service an implementation of a print service.
     * @return <code>true</code> if the service is newly
     *         registered; <code>false</code> otherwise.
     */

    public static boolean registerService(PrintService service) {
        synchronized (PrintServiceLookup.class) {
            if (service instanceof StreamPrintService) {
                return false;
            }
            if (REGISTERED_SERVICES.contains(service)) {
                return false;
            }
            REGISTERED_SERVICES.add(service);
            return true;
        }
    }


   /**
    * Locates services that can be positively confirmed to support
    * the combination of attributes and DocFlavors specified.
    * This method is not called directly by applications.
    *
    * @param flavor of document required.  If null it is ignored.
    * @param attributes required to be supported. If null this
    * constraint is not used.
    * @return array of matching PrintServices. If no services match, the
    * array is zero-length.
    */
    public abstract PrintService[] getPrintServices(DocFlavor flavor,
                                                    AttributeSet attributes);

    /**
     * Not called directly by applications.
     * Implemented by a service provider, used by the static methods
     * of this class.
     * @return array of all PrintServices known to this look up service
     * class. If none are found, the array is zero-length.
     */
    public abstract PrintService[] getPrintServices() ;


   /**
    * Not called directly by applications.
    * <p>
    * Implemented by a service provider, used by the static methods
    * of this class.
    * <p>
    * Locates MultiDoc print services which can be positively confirmed
    * to support the combination of attributes and DocFlavors specified.
    *
    * @param flavors of documents required. If null or empty it is ignored.
    * @param attributes required to be supported. If null this
     * constraint is not used.
    * @return array of matching PrintServices. If no services match, the
    * array is zero-length.
    */
    public abstract MultiDocPrintService[]
        getMultiDocPrintServices(DocFlavor[] flavors,
                                 AttributeSet attributes);

    /**
     * Not called directly by applications.
     * Implemented by a service provider, and called by the print lookup
     * service
     * @return the default PrintService for this lookup service.
     * If there is no default, returns null.
     */
    public abstract PrintService getDefaultPrintService();

    private static ArrayList<PrintServiceLookup> getAllLookupServices() {
        synchronized (PrintServiceLookup.class) {
            return new ArrayList<PrintServiceLookup>(LOOKUP_SERVICES);
        }
    }

    private static ArrayList<PrintService> getRegisteredServices() {
        synchronized (PrintServiceLookup.class) {
            return new ArrayList<PrintService>(REGISTERED_SERVICES);
        }
    }

    private static ArrayList<PrintService> getServices(DocFlavor flavor,
                                                       AttributeSet attributes) {

        ArrayList<PrintService> listOfServices = new ArrayList<PrintService>();
        for (PrintServiceLookup lus : getAllLookupServices()) {
            try {
                PrintService[] services;
                if (flavor == null && attributes == null) {
                    services = lus.getPrintServices();
                } else {
                    services = lus.getPrintServices(flavor, attributes);
                }
                if (services == null) {
                    continue;
                }
                for (PrintService service : services) {
                    listOfServices.add(service);
                }
            } catch (Exception e) {
                // As 8u: a failing provider is skipped, not reported.
            }
        }
        /* add any directly registered services */
        for (PrintService service : getRegisteredServices()) {
            if (!listOfServices.contains(service)) {
                if (flavor == null && attributes == null) {
                    listOfServices.add(service);
                } else if (((flavor != null &&
                             service.isDocFlavorSupported(flavor)) ||
                            flavor == null) &&
                           null == service.getUnsupportedAttributes(
                                                      flavor, attributes)) {
                    listOfServices.add(service);
                }
            }
        }
        return listOfServices;
    }

    private static ArrayList<MultiDocPrintService>
        getMultiDocServices(DocFlavor[] flavors, AttributeSet attributes) {

        ArrayList<MultiDocPrintService> listOfServices =
            new ArrayList<MultiDocPrintService>();
        for (PrintServiceLookup lus : getAllLookupServices()) {
            try {
                MultiDocPrintService[] services =
                    lus.getMultiDocPrintServices(flavors, attributes);
                if (services == null) {
                    continue;
                }
                for (MultiDocPrintService service : services) {
                    listOfServices.add(service);
                }
            } catch (Exception e) {
                // As 8u: a failing provider is skipped, not reported.
            }
        }
        /* add any directly registered services */
        for (PrintService service : getRegisteredServices()) {
            if (service instanceof MultiDocPrintService &&
                !listOfServices.contains(service)) {
                if (flavors == null || flavors.length == 0) {
                    listOfServices.add((MultiDocPrintService) service);
                } else {
                    boolean supported = true;
                    for (DocFlavor flavor : flavors) {
                        if (service.isDocFlavorSupported(flavor)) {
                            if (service.getUnsupportedAttributes(
                                    flavor, attributes) != null) {
                                supported = false;
                                break;
                            }
                        } else {
                            supported = false;
                            break;
                        }
                    }
                    if (supported) {
                        listOfServices.add((MultiDocPrintService) service);
                    }
                }
            }
        }
        return listOfServices;
    }

}
