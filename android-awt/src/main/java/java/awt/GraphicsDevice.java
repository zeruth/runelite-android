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
package java.awt;

public abstract class GraphicsDevice {
    public static final int TYPE_RASTER_SCREEN = 0;
    public static final int TYPE_PRINTER = 1;
    public static final int TYPE_IMAGE_BUFFER = 2;

    protected GraphicsDevice() {}

    public abstract int getType();
    public abstract String getIDstring();
    public abstract GraphicsConfiguration[] getConfigurations();
    public abstract GraphicsConfiguration getDefaultConfiguration();

    private Window fullScreenWindow;

    public Window getFullScreenWindow() { return fullScreenWindow; }
    public void setFullScreenWindow(Window w) { this.fullScreenWindow = w; }
    public boolean isFullScreenSupported() { return false; }
    public boolean isDisplayChangeSupported() { return false; }
    public DisplayMode getDisplayMode() { return null; }
    public DisplayMode[] getDisplayModes() { return new DisplayMode[0]; }
    public void setDisplayMode(DisplayMode dm) {}
    public int getAvailableAcceleratedMemory() { return -1; }

    /**
     * Kinds of translucency supported by the underlying system.
     *
     * @see #isWindowTranslucencySupported
     * @since 1.7
     */
    public static enum WindowTranslucency {
        /** Each pixel is either fully opaque or fully transparent (window shapes). */
        PERPIXEL_TRANSPARENT,
        /** All pixels share one alpha value (window opacity). */
        TRANSLUCENT,
        /** Each pixel has its own alpha (non-opaque window backgrounds). */
        PERPIXEL_TRANSLUCENT;
    }

    /**
     * Returns whether the given level of translucency is supported by this graphics device.
     *
     * <p>Every top-level window here is an ARGB backbuffer that the Android host composites,
     * so a raster screen supports shaping and opacity the way the desktop toolkits do;
     * per-pixel translucency additionally needs a translucency-capable configuration, as on
     * the JDK.
     *
     * @since 1.7
     */
    public boolean isWindowTranslucencySupported(WindowTranslucency translucencyKind) {
        switch (translucencyKind) {
            case PERPIXEL_TRANSPARENT:
            case TRANSLUCENT:
                return getType() == TYPE_RASTER_SCREEN;
            case PERPIXEL_TRANSLUCENT:
                return getType() == TYPE_RASTER_SCREEN && getTranslucencyCapableGC() != null;
        }
        return false;
    }

    GraphicsConfiguration getTranslucencyCapableGC() {
        GraphicsConfiguration defaultGC = getDefaultConfiguration();
        if (defaultGC != null && defaultGC.isTranslucencyCapable()) {
            return defaultGC;
        }
        GraphicsConfiguration[] configs = getConfigurations();
        if (configs != null) {
            for (GraphicsConfiguration config : configs) {
                if (config != null && config.isTranslucencyCapable()) {
                    return config;
                }
            }
        }
        return null;
    }
}
