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
package java.awt.image;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.util.Hashtable;

import net.runelite.awt.impl.BufferedImageGraphics2D;

/**
 * Pixel-buffer-backed image. ARGB pixels live in an {@code int[]} (one int per pixel)
 * inside a {@link DataBufferInt}, so JNI hot paths can pin the array via
 * {@code GetPrimitiveArrayCritical} for zero-copy access from Rust.
 */
public class BufferedImage extends Image implements RenderedImage {
    public static final int TYPE_CUSTOM        = 0;
    public static final int TYPE_INT_RGB       = 1;
    public static final int TYPE_INT_ARGB      = 2;
    public static final int TYPE_INT_ARGB_PRE  = 3;
    public static final int TYPE_INT_BGR       = 4;
    public static final int TYPE_3BYTE_BGR     = 5;
    public static final int TYPE_4BYTE_ABGR    = 6;
    public static final int TYPE_4BYTE_ABGR_PRE = 7;
    public static final int TYPE_USHORT_565_RGB = 8;
    public static final int TYPE_USHORT_555_RGB = 9;
    public static final int TYPE_BYTE_GRAY     = 10;
    public static final int TYPE_USHORT_GRAY   = 11;
    public static final int TYPE_BYTE_BINARY   = 12;
    public static final int TYPE_BYTE_INDEXED  = 13;

    /** Raster origin. createWritableRaster only reads x/y off this, it never keeps the
     *  reference, so one shared instance does for every image. */
    private static final java.awt.Point ORIGIN = new java.awt.Point(0, 0);

    private int imageType;
    private ColorModel colorModel;
    private final WritableRaster raster;
    private final Hashtable<?, ?> properties;
    private final int[] pixels;
    /** Native raster kernels require a full contiguous canonical RGB/ARGB buffer. */
    public boolean isCanonicalBuffer() {
        if (pixels == null || !(colorModel instanceof DirectColorModel) || colorModel.isAlphaPremultiplied()
            || !colorModel.getColorSpace().isCS_sRGB() || !(raster.getSampleModel() instanceof SinglePixelPackedSampleModel)
            || raster.getParent() != null || raster.getMinX() != 0 || raster.getMinY() != 0
            || raster.getSampleModelTranslateX() != 0 || raster.getSampleModelTranslateY() != 0
            || raster.getDataBuffer().getOffset() != 0) return false;
        DirectColorModel direct = (DirectColorModel)colorModel;
        return direct.getRedMask() == 0xff0000 && direct.getGreenMask() == 0xff00 && direct.getBlueMask() == 0xff
            && (direct.getAlphaMask() == 0 || direct.getAlphaMask() == 0xff000000)
            && ((SinglePixelPackedSampleModel)raster.getSampleModel()).getScanlineStride() == getWidth();
    }
    private int pixelIndex(int x,int y){
        if(x<0||y<0||x>=getWidth()||y>=getHeight())throw new ArrayIndexOutOfBoundsException("pixel");
        return (y-raster.getSampleModelTranslateY())*((SinglePixelPackedSampleModel)raster.getSampleModel()).getScanlineStride()+x-raster.getSampleModelTranslateX()+raster.getDataBuffer().getOffset();
    }

    public BufferedImage(int width, int height, int imageType) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("BufferedImage dimensions must be > 0");
        }
        this.imageType = imageType;
        this.colorModel = colorModelFor(imageType);
        SampleModel sm;
        if (imageType == TYPE_BYTE_BINARY) sm = new MultiPixelPackedSampleModel(DataBuffer.TYPE_BYTE, width, height, 1);
        else if (imageType == TYPE_3BYTE_BGR) sm = new PixelInterleavedSampleModel(DataBuffer.TYPE_BYTE, width, height, 3, width * 3, new int[]{2, 1, 0});
        else if (imageType == TYPE_4BYTE_ABGR || imageType == TYPE_4BYTE_ABGR_PRE) sm = new PixelInterleavedSampleModel(DataBuffer.TYPE_BYTE, width, height, 4, width * 4, new int[]{3, 2, 1, 0});
        else sm = colorModel.createCompatibleSampleModel(width, height);
        this.raster = Raster.createWritableRaster(sm, sm.createDataBuffer(), ORIGIN);
        DataBuffer data = raster.getDataBuffer();
        this.pixels = data instanceof DataBufferInt ? ((DataBufferInt)data).getData() : null;
        this.properties = null;
    }

    public BufferedImage(int width, int height, int imageType, IndexColorModel model) {
        if (imageType != TYPE_BYTE_BINARY && imageType != TYPE_BYTE_INDEXED) throw new IllegalArgumentException("Indexed image type required");
        this.imageType = imageType; colorModel = java.util.Objects.requireNonNull(model); properties = null; pixels = null;
        int bits = model.getMapSize() <= 2 ? 1 : model.getMapSize() <= 4 ? 2 : 4;
        if (imageType == TYPE_BYTE_BINARY && model.getMapSize() > 16) throw new IllegalArgumentException("Binary image palette is too large");
        SampleModel sm = imageType == TYPE_BYTE_BINARY ? new MultiPixelPackedSampleModel(DataBuffer.TYPE_BYTE, width, height, bits) : model.createCompatibleSampleModel(width, height);
        raster = Raster.createWritableRaster(sm, sm.createDataBuffer(), ORIGIN);
    }

    public BufferedImage(ColorModel cm, WritableRaster raster, boolean isRasterPremultiplied, Hashtable<?, ?> properties) {
        java.util.Objects.requireNonNull(cm); java.util.Objects.requireNonNull(raster);
        if(raster.getMinX()!=0||raster.getMinY()!=0)throw new IllegalArgumentException("Image raster origin must be zero");
        if(!cm.isCompatibleRaster(raster))throw new IllegalArgumentException("Incompatible color model and raster");
        this.colorModel = cm.coerceData(raster,isRasterPremultiplied);
        this.raster = raster;
        this.properties = properties;
        this.imageType = TYPE_CUSTOM;
        DataBuffer db = raster.getDataBuffer();
        if (db instanceof DataBufferInt) {
            this.pixels = ((DataBufferInt) db).getData();
        } else {
            this.pixels = null;
        }
    }

    public int getType()           { return imageType; }
    public ColorModel getColorModel() { return colorModel; }
    public WritableRaster getRaster() { return raster; }
    public int getWidth()          { return raster.getWidth(); }
    public int getHeight()         { return raster.getHeight(); }

    @Override public int getWidth(ImageObserver observer)  { return getWidth(); }
    @Override public int getHeight(ImageObserver observer) { return getHeight(); }

    /** Direct pixel buffer (TYPE_INT_* images only). Mutating it mutates the image. */
    public int[] backingArray() {
        return pixels;
    }

    // 1 = packed RGB, 2 = packed ARGB. Offsets and child strides are still honored.
    private int directRgbKind() {
        if (pixels == null || !(colorModel instanceof DirectColorModel) || colorModel.isAlphaPremultiplied()
            || !colorModel.getColorSpace().isCS_sRGB() || !(raster.getSampleModel() instanceof SinglePixelPackedSampleModel)) return 0;
        DirectColorModel cm = (DirectColorModel)colorModel;
        if (cm.getRedMask() != 0xff0000 || cm.getGreenMask() != 0xff00 || cm.getBlueMask() != 0xff) return 0;
        return cm.getAlphaMask() == 0 ? 1 : cm.getAlphaMask() == 0xff000000 ? 2 : 0;
    }
    public int getRGB(int x, int y) {
        int kind = directRgbKind();
        if (kind != 0) { int value = pixels[pixelIndex(x, y)]; return kind == 1 ? value | 0xff000000 : value; }
        if (pixels != null && raster.getSampleModel() instanceof SinglePixelPackedSampleModel) return colorModel.getRGB(pixels[pixelIndex(x,y)]);
        return colorModel.getRGB(raster.getDataElements(x, y, null));
    }
    public void setRGB(int x, int y, int rgb) {
        int kind = directRgbKind();
        if (kind != 0) pixels[pixelIndex(x, y)] = kind == 1 ? rgb & 0xffffff : rgb;
        else if (pixels != null && raster.getSampleModel() instanceof SinglePixelPackedSampleModel) pixels[pixelIndex(x,y)] = ((int[])colorModel.getDataElements(rgb,null))[0];
        else raster.setDataElements(x, y, colorModel.getDataElements(rgb, null));
    }
    private void checkRegion(int x, int y, int w, int h) {
        if (w < 0 || h < 0 || x < 0 || y < 0 || x > getWidth() - w || y > getHeight() - h)
            throw new ArrayIndexOutOfBoundsException("Image region");
    }
    public int[] getRGB(int startX,int startY,int w,int h,int[] rgbArray,int offset,int scansize){
        checkRegion(startX, startY, w, h);
        int[] out=rgbArray==null?new int[offset+(h==0?0:(h-1)*scansize+w)]:rgbArray;
        int kind = directRgbKind();
        if (kind != 0 && w != 0 && h != 0) {
            int source = pixelIndex(startX, startY), stride = ((SinglePixelPackedSampleModel)raster.getSampleModel()).getScanlineStride();
            for (int row = 0; row < h; row++, source += stride) {
                int target = offset + row * scansize;
                if (kind == 2) System.arraycopy(pixels, source, out, target, w);
                else for (int col = 0; col < w; col++) out[target + col] = pixels[source + col] | 0xff000000;
            }
        } else for(int row=0;row<h;row++)for(int col=0;col<w;col++)out[offset+row*scansize+col]=getRGB(startX+col,startY+row);
        return out;
    }
    public void setRGB(int startX,int startY,int w,int h,int[] values,int offset,int scansize){
        checkRegion(startX, startY, w, h);
        int kind = directRgbKind();
        if (kind != 0 && w != 0 && h != 0) {
            int target = pixelIndex(startX, startY), stride = ((SinglePixelPackedSampleModel)raster.getSampleModel()).getScanlineStride();
            for (int row = 0; row < h; row++, target += stride) {
                int source = offset + row * scansize;
                if (kind == 2) System.arraycopy(values, source, pixels, target, w);
                else for (int col = 0; col < w; col++) pixels[target + col] = values[source + col] & 0xffffff;
            }
        } else for(int row=0;row<h;row++)for(int col=0;col<w;col++)setRGB(startX+col,startY+row,values[offset+row*scansize+col]);
    }

    @Override
    public Graphics getGraphics() {
        return createGraphics();
    }

    public BufferedImage getSubimage(int x,int y,int w,int h){
        BufferedImage child = new BufferedImage(colorModel,raster.createWritableChild(x,y,w,h,0,0,null),colorModel.isAlphaPremultiplied(),properties);
        child.imageType=imageType;return child;
    }

    public Graphics2D createGraphics() {
        return new BufferedImageGraphics2D(this);
    }

    public Object getProperty(String name){java.util.Objects.requireNonNull(name);return properties!=null&&properties.containsKey(name)?properties.get(name):UndefinedProperty;}
    @Override public Object getProperty(String name,ImageObserver observer){return getProperty(name);}
    public String[] getPropertyNames(){if(properties==null)return null;return properties.keySet().stream().map(Object::toString).toArray(String[]::new);}
    public void coerceData(boolean premultiplied){colorModel=colorModel.coerceData(raster,premultiplied);}
    public boolean isAlphaPremultiplied(){return colorModel.isAlphaPremultiplied();}
    public int getTransparency(){return colorModel.getTransparency();}
    public int getMinX(){return 0;}public int getMinY(){return 0;}
    public int getNumXTiles(){return 1;}public int getNumYTiles(){return 1;}
    public int getMinTileX(){return 0;}public int getMinTileY(){return 0;}
    public int getTileWidth(){return getWidth();}public int getTileHeight(){return getHeight();}
    public int getTileGridXOffset(){return 0;}public int getTileGridYOffset(){return 0;}
    public SampleModel getSampleModel(){return raster.getSampleModel();}
    public java.util.Vector<RenderedImage> getSources(){return null;}
    public Raster getTile(int x,int y){if(x!=0||y!=0)throw new ArrayIndexOutOfBoundsException("tile");return raster;}
    public Raster getData(){return copyData(null);}
    public Raster getData(java.awt.Rectangle rect){WritableRaster copy=raster.createCompatibleWritableRaster(rect.width,rect.height);for(int y=0;y<rect.height;y++)for(int x=0;x<rect.width;x++)copy.setDataElements(x,y,raster.getDataElements(rect.x+x,rect.y+y,null));return copy.createWritableTranslatedChild(rect.x,rect.y);}
    public WritableRaster copyData(WritableRaster out){if(out==null)out=raster.createCompatibleWritableRaster();int minX=Math.max(0,out.getMinX()),minY=Math.max(0,out.getMinY()),maxX=Math.min(getWidth(),out.getMinX()+out.getWidth()),maxY=Math.min(getHeight(),out.getMinY()+out.getHeight());for(int y=minY;y<maxY;y++)for(int x=minX;x<maxX;x++)out.setDataElements(x,y,raster.getDataElements(x,y,null));return out;}
    /** As the JDK: an {@link sun.awt.image.OffScreenImageSource} over this image and its properties. */
    @Override public ImageProducer getSource(){
        return new sun.awt.image.OffScreenImageSource(this, properties);
    }

    // ColorModel is immutable and depends only on the image type, so one instance per
    // type is enough. Building a fresh one per image cost a DirectColorModel, its
    // component-size int[], a ColorSpace lookup and the mask int[] on top of the pixel
    // buffer — around 130 bytes of metadata on top of, say, a 1 KB item icon. RuneLite's
    // sprite and icon caches hold thousands of those.
    //
    // masksFor() stays a fresh array only in the sense that SinglePixelPackedSampleModel
    // clones what it is handed, so sharing these is safe.
    private static final int[] MASKS_RGB  = { 0x00FF0000, 0x0000FF00, 0x000000FF };
    private static final int[] MASKS_ARGB = { 0x00FF0000, 0x0000FF00, 0x000000FF, 0xFF000000 };
    private static final int[] MASKS_BGR  = { 0x000000FF, 0x0000FF00, 0x00FF0000 };

    private static final ColorModel CM_RGB =
        new DirectColorModel(24, 0x00FF0000, 0x0000FF00, 0x000000FF);
    private static final ColorModel CM_ARGB =
        new DirectColorModel(32, 0x00FF0000, 0x0000FF00, 0x000000FF, 0xFF000000);
    private static final ColorModel CM_BGR =
        new DirectColorModel(24, 0x000000FF, 0x0000FF00, 0x00FF0000);

    private static ColorModel colorModelFor(int type) {
        switch (type) {
            case TYPE_INT_RGB:  return CM_RGB;
            case TYPE_INT_BGR:  return CM_BGR;
            case TYPE_INT_ARGB_PRE: return new DirectColorModel(java.awt.color.ColorSpace.getInstance(java.awt.color.ColorSpace.CS_sRGB),32,0xff0000,0xff00,0xff,0xff000000,true,DataBuffer.TYPE_INT);
            case TYPE_INT_ARGB: return CM_ARGB;
            case TYPE_USHORT_565_RGB: return new DirectColorModel(java.awt.color.ColorSpace.getInstance(java.awt.color.ColorSpace.CS_sRGB), 16, 0xf800, 0x7e0, 0x1f, 0, false, DataBuffer.TYPE_USHORT);
            case TYPE_USHORT_555_RGB: return new DirectColorModel(java.awt.color.ColorSpace.getInstance(java.awt.color.ColorSpace.CS_sRGB), 15, 0x7c00, 0x3e0, 0x1f, 0, false, DataBuffer.TYPE_USHORT);
            case TYPE_3BYTE_BGR: return new ComponentColorModel(java.awt.color.ColorSpace.getInstance(java.awt.color.ColorSpace.CS_sRGB), false, false, java.awt.Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
            case TYPE_4BYTE_ABGR: case TYPE_4BYTE_ABGR_PRE: return new ComponentColorModel(java.awt.color.ColorSpace.getInstance(java.awt.color.ColorSpace.CS_sRGB), true, type == TYPE_4BYTE_ABGR_PRE, java.awt.Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE);
            case TYPE_BYTE_GRAY: case TYPE_USHORT_GRAY: return new ComponentColorModel(java.awt.color.ColorSpace.getInstance(java.awt.color.ColorSpace.CS_GRAY), false, false, java.awt.Transparency.OPAQUE, type == TYPE_BYTE_GRAY ? DataBuffer.TYPE_BYTE : DataBuffer.TYPE_USHORT);
            case TYPE_BYTE_BINARY: return new IndexColorModel(1, 2, new byte[]{0, -1}, new byte[]{0, -1}, new byte[]{0, -1});
            case TYPE_BYTE_INDEXED:
                int[] palette = new int[256]; int index = 0;
                for (int r = 0; r < 256; r += 51) for (int g = 0; g < 256; g += 51) for (int b = 0; b < 256; b += 51) palette[index++] = 0xff000000 | r << 16 | g << 8 | b;
                for (int gray = 18; index < 256; gray += 6) palette[index++] = 0xff000000 | gray << 16 | gray << 8 | gray;
                return new IndexColorModel(8, 256, palette, 0, false, -1, DataBuffer.TYPE_BYTE);
            default: throw new IllegalArgumentException("Unknown image type: " + type);
        }
    }

    private static int[] masksFor(int type) {
        switch (type) {
            case TYPE_INT_RGB:  return MASKS_RGB;
            case TYPE_INT_BGR:  return MASKS_BGR;
            case TYPE_INT_ARGB:
            case TYPE_INT_ARGB_PRE:
            default:            return MASKS_ARGB;
        }
    }
}
