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
package java.applet;

import java.awt.Panel;

public class Applet extends Panel {
    private static final long serialVersionUID = -5836846270535785031L;

    public AppletStub stub;

    public Applet() {}
    public void setStub(AppletStub stub) { this.stub = stub; }
    public AppletStub getStub() { return stub; }

    // The patched OSRS client reads its server endpoint and login parameters
    // (worldid, jav_config keys, etc.) through these methods. RuneLite wires its
    // RSAppletStub via setStub before init(), so we must delegate everything to it
    // — returning null forces the client onto stale defaults and the live server
    // returns "RuneScape has been updated" at login.
    public boolean isActive()         { return stub != null && stub.isActive(); }
    public java.net.URL getDocumentBase() { return stub == null ? null : stub.getDocumentBase(); }
    public java.net.URL getCodeBase()     { return stub == null ? null : stub.getCodeBase(); }
    public String getParameter(String name) { return stub == null ? null : stub.getParameter(name); }
    public AppletContext getAppletContext() { return stub == null ? null : stub.getAppletContext(); }

    /** Resizes the applet and tells the stub, as the JDK does; RuneLite's stub ignores it. */
    public void resize(int width, int height) {
        if (getWidth() != width || getHeight() != height) {
            setSize(width, height);
            if (stub != null) stub.appletResize(width, height);
        }
    }
    public void resize(java.awt.Dimension d) { resize(d.width, d.height); }

    /** An applet is a validate root: its revalidation stops here. */
    public boolean isValidateRoot() { return true; }

    public void showStatus(String msg) {
        AppletContext context = getAppletContext();
        if (context != null) context.showStatus(msg);
    }

    public java.awt.Image getImage(java.net.URL url) {
        AppletContext context = getAppletContext();
        return context == null ? null : context.getImage(url);
    }

    public java.awt.Image getImage(java.net.URL url, String name) {
        try {
            return getImage(new java.net.URL(url, name));
        } catch (java.net.MalformedURLException e) {
            return null;
        }
    }

    /** A clip over {@code javax.sound.sampled}, decoded on first play like the JDK's. */
    public static final AudioClip newAudioClip(java.net.URL url) {
        return new net.runelite.awt.impl.SampledAudioClip(url);
    }

    public AudioClip getAudioClip(java.net.URL url) {
        AppletContext context = getAppletContext();
        return context == null ? null : context.getAudioClip(url);
    }

    public AudioClip getAudioClip(java.net.URL url, String name) {
        try {
            return getAudioClip(new java.net.URL(url, name));
        } catch (java.net.MalformedURLException e) {
            return null;
        }
    }

    public String getAppletInfo() { return null; }

    public java.util.Locale getLocale() {
        java.util.Locale locale = super.getLocale();
        return locale == null ? java.util.Locale.getDefault() : locale;
    }

    public String[][] getParameterInfo() { return null; }

    public void play(java.net.URL url) {
        AudioClip clip = getAudioClip(url);
        if (clip != null) clip.play();
    }

    public void play(java.net.URL url, String name) {
        AudioClip clip = getAudioClip(url, name);
        if (clip != null) clip.play();
    }

    public void init() {}
    public void start() {}
    public void stop() {}
    public void destroy() {}

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleApplet();
        }
        return accessibleContext;
    }

    /** Accessibility support for applets: role FRAME, always ACTIVE. */
    protected class AccessibleApplet extends AccessibleAWTPanel {
        private static final long serialVersionUID = 8127374778187708896L;

        public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.FRAME;
        }

        public javax.accessibility.AccessibleStateSet getAccessibleStateSet() {
            javax.accessibility.AccessibleStateSet states = super.getAccessibleStateSet();
            states.add(javax.accessibility.AccessibleState.ACTIVE);
            return states;
        }
    }
}
