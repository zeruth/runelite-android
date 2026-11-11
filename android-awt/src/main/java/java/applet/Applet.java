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

    public void resize(int width, int height) { setSize(width, height); }
    public void resize(java.awt.Dimension d) { setSize(d); }
    public String getAppletInfo() { return null; }
    public String[][] getParameterInfo() { return new String[0][]; }
    public void init() {}
    public void start() {}
    public void stop() {}
    public void destroy() {}
}
