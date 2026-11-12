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
package com.sun.imageio.plugins.jpeg;

import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataFormatImpl;
import javax.imageio.metadata.IIOMetadataNode;

import org.w3c.dom.Node;

/**
 * The image metadata of a JPEG read by the platform-backed reader. The JDK's class
 * models every marker segment for its native format; the platform decoder exposes none
 * of them, so this keeps what the frame header says (size, component count, baseline or
 * progressive, the number of scans) and offers it through the standard
 * {@code javax_imageio_1.0} tree only. It is read-only.
 */
public class JPEGMetadata extends IIOMetadata implements Cloneable {

    private final int width;
    private final int height;
    private final int numComponents;
    private final boolean progressive;
    private final int numScans;

    JPEGMetadata(int width, int height, int numComponents, boolean progressive, int numScans) {
        super(true, null, null, null, null);
        this.width = width;
        this.height = height;
        this.numComponents = numComponents;
        this.progressive = progressive;
        this.numScans = numScans;
    }

    /**
     * Walks the marker segments up to the first frame header and counts the scans,
     * returning null when the bytes are not a JPEG with a frame header.
     */
    static JPEGMetadata parse(byte[] d) {
        int n = d.length;
        if (n < 4 || (d[0] & 0xff) != 0xff || (d[1] & 0xff) != JPEG.SOI) {
            return null;
        }
        int width = -1;
        int height = -1;
        int components = 0;
        boolean progressive = false;
        int scans = 0;
        int i = 2;
        while (i + 1 < n) {
            if ((d[i] & 0xff) != 0xff) {
                i++;
                continue;
            }
            int marker = d[i + 1] & 0xff;
            if (marker == 0xff) {
                i++;
                continue;
            }
            if (marker == JPEG.EOI) {
                break;
            }
            if (marker == 0 || marker == JPEG.TEM
                    || (marker >= JPEG.RST0 && marker <= JPEG.RST7)) {
                i += 2;
                continue;
            }
            if (i + 3 >= n) {
                break;
            }
            int length = ((d[i + 2] & 0xff) << 8) | (d[i + 3] & 0xff);
            boolean sof = marker >= JPEG.SOF0 && marker <= JPEG.SOF15
                && marker != JPEG.DHT && marker != JPEG.JPG && marker != JPEG.DAC;
            if (sof && width < 0 && i + 9 < n) {
                height = ((d[i + 5] & 0xff) << 8) | (d[i + 6] & 0xff);
                width = ((d[i + 7] & 0xff) << 8) | (d[i + 8] & 0xff);
                components = d[i + 9] & 0xff;
                progressive = marker == JPEG.SOF2 || marker == JPEG.SOF6
                    || marker == JPEG.SOF10 || marker == JPEG.SOF14;
            }
            i += 2 + length;
            if (marker == JPEG.SOS) {
                scans++;
                // Entropy-coded data: skip to the next marker that is not a stuffed
                // 0xFF00 or a restart marker.
                while (i + 1 < n) {
                    if ((d[i] & 0xff) == 0xff) {
                        int next = d[i + 1] & 0xff;
                        if (next != 0 && !(next >= JPEG.RST0 && next <= JPEG.RST7)) {
                            break;
                        }
                    }
                    i++;
                }
            }
        }
        if (width <= 0 || height <= 0 || components <= 0) {
            return null;
        }
        return new JPEGMetadata(width, height, components, progressive, scans);
    }

    int getWidth() {
        return width;
    }

    int getHeight() {
        return height;
    }

    int getNumComponents() {
        return numComponents;
    }

    /** Whether the frame header is a progressive (SOF2/6/10/14) one. */
    public boolean isProgressive() {
        return progressive;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public Node getAsTree(String formatName) {
        if (formatName == null) {
            throw new IllegalArgumentException("null formatName!");
        }
        if (!formatName.equals(IIOMetadataFormatImpl.standardMetadataFormatName)) {
            throw new IllegalArgumentException("Unsupported format name: " + formatName);
        }
        return getStandardTree();
    }

    @Override
    public void mergeTree(String formatName, Node root) {
        throw new IllegalStateException("Metadata is read-only!");
    }

    /**
     * Deliberately nothing: the values come from the stream and are read-only, so
     * there is no settable state to restore.
     */
    @Override
    public void reset() {
    }

    @Override
    protected IIOMetadataNode getStandardChromaNode() {
        IIOMetadataNode chroma = new IIOMetadataNode("Chroma");
        IIOMetadataNode type = new IIOMetadataNode("ColorSpaceType");
        String space;
        switch (numComponents) {
        case 1:
            space = "GRAY";
            break;
        case 4:
            space = "CMYK";
            break;
        default:
            space = "YCbCr";
        }
        type.setAttribute("name", space);
        chroma.appendChild(type);
        IIOMetadataNode channels = new IIOMetadataNode("NumChannels");
        channels.setAttribute("value", Integer.toString(numComponents));
        chroma.appendChild(channels);
        return chroma;
    }

    @Override
    protected IIOMetadataNode getStandardCompressionNode() {
        IIOMetadataNode compression = new IIOMetadataNode("Compression");
        IIOMetadataNode name = new IIOMetadataNode("CompressionTypeName");
        name.setAttribute("value", "JPEG");
        compression.appendChild(name);
        IIOMetadataNode lossless = new IIOMetadataNode("Lossless");
        lossless.setAttribute("value", "FALSE");
        compression.appendChild(lossless);
        if (progressive) {
            IIOMetadataNode passes = new IIOMetadataNode("NumProgressiveScans");
            passes.setAttribute("value", Integer.toString(numScans));
            compression.appendChild(passes);
        }
        return compression;
    }

    @Override
    protected IIOMetadataNode getStandardDimensionNode() {
        IIOMetadataNode dimension = new IIOMetadataNode("Dimension");
        IIOMetadataNode orientation = new IIOMetadataNode("ImageOrientation");
        orientation.setAttribute("value", "normal");
        dimension.appendChild(orientation);
        return dimension;
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e.getMessage());
        }
    }
}
