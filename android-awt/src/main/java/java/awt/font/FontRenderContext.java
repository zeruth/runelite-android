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
package java.awt.font;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
public class FontRenderContext {
    private final AffineTransform transform;
    private final Object antialias,fractional;
    protected FontRenderContext(){this(null,false,false);}
    public FontRenderContext(AffineTransform transform,boolean aa,boolean fractional){this(transform,aa?RenderingHints.VALUE_TEXT_ANTIALIAS_ON:RenderingHints.VALUE_TEXT_ANTIALIAS_OFF,fractional?RenderingHints.VALUE_FRACTIONALMETRICS_ON:RenderingHints.VALUE_FRACTIONALMETRICS_OFF);}
    public FontRenderContext(AffineTransform transform,Object aa,Object fractional){this.transform=transform==null?new AffineTransform():new AffineTransform(transform);this.antialias=aa;this.fractional=fractional;}
    public AffineTransform getTransform(){return new AffineTransform(transform);}
    public boolean isAntiAliased(){return antialias!=RenderingHints.VALUE_TEXT_ANTIALIAS_OFF && antialias!=RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT;}
    public boolean usesFractionalMetrics(){return fractional==RenderingHints.VALUE_FRACTIONALMETRICS_ON;}
    public boolean isTransformed(){return !transform.isIdentity();}
    public int getTransformType(){return transform.getType();}
    public Object getAntiAliasingHint(){return antialias;}
    public Object getFractionalMetricsHint(){return fractional;}
    public boolean equals(FontRenderContext other){return other!=null&&transform.equals(other.transform)&&antialias.equals(other.antialias)&&fractional.equals(other.fractional);}
    public boolean equals(Object other){return other instanceof FontRenderContext&&equals((FontRenderContext)other);}
    public int hashCode(){return transform.hashCode()^antialias.hashCode()^fractional.hashCode();}
}
