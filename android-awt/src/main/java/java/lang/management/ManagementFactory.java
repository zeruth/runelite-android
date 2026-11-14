/*
 * Copyright (c) 2026, Tyler Bochard
 * All rights reserved.
 *
 * Part of android-awt, the Android shadow of the desktop JDK surface used by
 * runelite-android. Original work of this project, not derived from OpenJDK;
 * licensed under the BSD 2-Clause License (see the LICENSE file at the
 * repository root). Files ported from OpenJDK carry the OpenJDK header instead;
 * see android-awt/LICENSE.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package java.lang.management;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;

/**
 * The platform MXBeans of the running ART process (see {@code ArtManagement} for where each
 * value comes from) and the in-process platform MBean server they are registered in. Android
 * has no remote JMX connector, so the {@code MBeanServerConnection} overloads and proxies are
 * absent.
 */
public class ManagementFactory {
    public static final String CLASS_LOADING_MXBEAN_NAME = "java.lang:type=ClassLoading";
    public static final String COMPILATION_MXBEAN_NAME = "java.lang:type=Compilation";
    public static final String MEMORY_MXBEAN_NAME = "java.lang:type=Memory";
    public static final String OPERATING_SYSTEM_MXBEAN_NAME = "java.lang:type=OperatingSystem";
    public static final String RUNTIME_MXBEAN_NAME = "java.lang:type=Runtime";
    public static final String THREAD_MXBEAN_NAME = "java.lang:type=Threading";
    public static final String GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE = "java.lang:type=GarbageCollector";
    public static final String MEMORY_MANAGER_MXBEAN_DOMAIN_TYPE = "java.lang:type=MemoryManager";
    public static final String MEMORY_POOL_MXBEAN_DOMAIN_TYPE = "java.lang:type=MemoryPool";

    private static final List<Class<? extends PlatformManagedObject>> SINGLETONS = Collections.unmodifiableList(
            Arrays.<Class<? extends PlatformManagedObject>>asList(ClassLoadingMXBean.class, CompilationMXBean.class,
                    MemoryMXBean.class, OperatingSystemMXBean.class, com.sun.management.OperatingSystemMXBean.class,
                    RuntimeMXBean.class, ThreadMXBean.class));
    private static final List<Class<? extends PlatformManagedObject>> MULTIPLE = Collections.unmodifiableList(
            Arrays.<Class<? extends PlatformManagedObject>>asList(GarbageCollectorMXBean.class,
                    MemoryManagerMXBean.class, MemoryPoolMXBean.class));
    private static MBeanServer platformServer;

    private ManagementFactory() {}

    public static ClassLoadingMXBean getClassLoadingMXBean() {
        return ArtManagement.CLASS_LOADING;
    }

    public static MemoryMXBean getMemoryMXBean() {
        return ArtManagement.MEMORY;
    }

    public static ThreadMXBean getThreadMXBean() {
        return ArtManagement.THREADS;
    }

    public static RuntimeMXBean getRuntimeMXBean() {
        return ArtManagement.RUNTIME;
    }

    public static CompilationMXBean getCompilationMXBean() {
        return ArtManagement.COMPILATION;
    }

    /** Also a {@code com.sun.management.OperatingSystemMXBean}, as on the JDK. */
    public static OperatingSystemMXBean getOperatingSystemMXBean() {
        return ArtManagement.OPERATING_SYSTEM;
    }

    /** The ART (Java) heap and the native heap. */
    public static List<MemoryPoolMXBean> getMemoryPoolMXBeans() {
        return ArtManagement.POOLS;
    }

    public static List<MemoryManagerMXBean> getMemoryManagerMXBeans() {
        return Collections.<MemoryManagerMXBean>unmodifiableList(ArtManagement.COLLECTORS);
    }

    public static List<GarbageCollectorMXBean> getGarbageCollectorMXBeans() {
        return ArtManagement.COLLECTORS;
    }

    /** The in-process server holding every platform MXBean under its {@code getObjectName()}. */
    public static synchronized MBeanServer getPlatformMBeanServer() {
        if (platformServer == null) {
            MBeanServer server = MBeanServerFactory.createMBeanServer();
            for (PlatformManagedObject bean : ArtManagement.all()) {
                try {
                    server.registerMBean(bean, bean.getObjectName());
                } catch (Exception impossible) {
                    // A fresh server and distinct platform names: registration cannot fail.
                    throw new IllegalStateException("registering " + bean.getObjectName(), impossible);
                }
            }
            platformServer = server;
        }
        return platformServer;
    }

    /**
     * The platform MXBean implementing {@code mxbeanInterface}, which must be a singleton
     * platform interface (getting a GarbageCollectorMXBean this way is an error, as on the JDK).
     */
    public static <T extends PlatformManagedObject> T getPlatformMXBean(Class<T> mxbeanInterface) {
        if (!SINGLETONS.contains(mxbeanInterface)) {
            throw new IllegalArgumentException(mxbeanInterface.getName()
                    + (MULTIPLE.contains(mxbeanInterface) ? " can have more than one instance"
                    : " is not a platform management interface"));
        }
        for (PlatformManagedObject bean : ArtManagement.all()) {
            if (mxbeanInterface.isInstance(bean)) return mxbeanInterface.cast(bean);
        }
        return null;
    }

    public static <T extends PlatformManagedObject> List<T> getPlatformMXBeans(Class<T> mxbeanInterface) {
        if (!SINGLETONS.contains(mxbeanInterface) && !MULTIPLE.contains(mxbeanInterface)) {
            throw new IllegalArgumentException(mxbeanInterface.getName() + " is not a platform management interface");
        }
        List<T> beans = new ArrayList<>();
        for (PlatformManagedObject bean : ArtManagement.all()) {
            if (mxbeanInterface.isInstance(bean)) beans.add(mxbeanInterface.cast(bean));
        }
        return Collections.unmodifiableList(beans);
    }

    public static Set<Class<? extends PlatformManagedObject>> getPlatformManagementInterfaces() {
        Set<Class<? extends PlatformManagedObject>> interfaces = new HashSet<>(SINGLETONS);
        interfaces.addAll(MULTIPLE);
        return Collections.unmodifiableSet(interfaces);
    }
}
