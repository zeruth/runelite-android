/*
 * Copyright (c) 2007, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.media.sound;

/**
 * Abstract resampler class.
 *
 * <p>Ported for the sample-rate conversion of {@link AudioFloatFormatConverter},
 * which only calls {@link #getPadding} and {@link #interpolate}. The JDK class
 * also implements {@code SoftResampler} with a wavetable streamer for the
 * software synthesizer; that part needs the {@code javax.sound.midi} model
 * classes and returns with the MIDI port.
 *
 * @author Karl Helgason
 */
public abstract class SoftAbstractResampler {

    public abstract int getPadding();

    public abstract void interpolate(float[] in, float[] in_offset,
            float in_end, float[] pitch, float pitchstep, float[] out,
            int[] out_offset, int out_end);
}
