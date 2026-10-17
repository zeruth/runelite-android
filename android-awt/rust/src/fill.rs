use crate::{CLEAR, SRC, SRC_ATOP};
use jni::sys::jint;

/// Fill an axis-aligned rectangle on an ARGB int[] buffer. The buffer's row stride is
/// img_w; coordinates are clipped to [0, img_w) × [0, img_h).
pub fn fill_rect(
    buf: &mut [i32],
    img_w: jint,
    img_h: jint,
    x: jint,
    y: jint,
    w: jint,
    h: jint,
    argb: jint,
    composite_rule: jint,
) {
    let (x0, y0, x1, y1) = clamp_rect(img_w, img_h, x, y, w, h);
    if x0 >= x1 || y0 >= y1 {
        return;
    }
    let stride = img_w as usize;
    match composite_rule {
        SRC => {
            for row in y0..y1 {
                let off = row as usize * stride;
                for col in x0..x1 {
                    buf[off + col as usize] = argb;
                }
            }
        }
        CLEAR => {
            for row in y0..y1 {
                let off = row as usize * stride;
                for col in x0..x1 {
                    buf[off + col as usize] = 0;
                }
            }
        }
        _ => {
            for row in y0..y1 {
                let off = row as usize * stride;
                for col in x0..x1 {
                    let dst = buf[off + col as usize] as u32;
                    buf[off + col as usize] = if composite_rule == SRC_ATOP { src_atop(argb as u32, dst) } else { src_over(argb as u32, dst) } as i32;
                }
            }
        }
    }
}

#[inline]
pub fn plot_span(
    buf: &mut [i32],
    stride: usize,
    row: i32,
    x0: i32,
    x1: i32,
    img_w: i32,
    img_h: i32,
    argb: jint,
    composite_rule: jint,
) {
    if row < 0 || row >= img_h {
        return;
    }
    let c0 = x0.max(0).min(img_w);
    let c1 = x1.max(0).min(img_w);
    if c0 >= c1 {
        return;
    }
    let off = row as usize * stride;
    match composite_rule {
        SRC => {
            for col in c0..c1 {
                buf[off + col as usize] = argb;
            }
        }
        CLEAR => {
            for col in c0..c1 {
                buf[off + col as usize] = 0;
            }
        }
        _ => {
            for col in c0..c1 {
                let dst = buf[off + col as usize] as u32;
                buf[off + col as usize] = if composite_rule == SRC_ATOP { src_atop(argb as u32, dst) } else { src_over(argb as u32, dst) } as i32;
            }
        }
    }
}

#[inline]
pub fn plot_pixel(buf: &mut [i32], stride: usize, x: i32, y: i32, img_w: i32, img_h: i32, argb: jint) {
    if x >= 0 && x < img_w && y >= 0 && y < img_h {
        let idx = y as usize * stride + x as usize;
        buf[idx] = src_over(argb as u32, buf[idx] as u32) as i32;
    }
}

pub fn fill_oval(
    buf: &mut [i32],
    img_w: jint,
    img_h: jint,
    x: jint,
    y: jint,
    w: jint,
    h: jint,
    argb: jint,
    composite_rule: jint,
) {
    if w <= 0 || h <= 0 {
        return;
    }
    let stride = img_w as usize;
    let rx = (w as f64) * 0.5;
    let ry = (h as f64) * 0.5;
    let cx = (x as f64) + rx;
    let cy = (y as f64) + ry;

    let y0 = y.max(0).min(img_h);
    let y1 = (y + h).max(0).min(img_h);

    for row in y0..y1 {
        let dy = ((row as f64) + 0.5) - cy;
        let norm_y = dy / ry;
        let rem_sq = 1.0 - norm_y * norm_y;
        if rem_sq >= 0.0 {
            let dx = rx * rem_sq.sqrt();
            let x0 = (cx - dx).round() as i32;
            let x1 = (cx + dx).round() as i32;
            plot_span(buf, stride, row, x0, x1, img_w, img_h, argb, composite_rule);
        }
    }
}

pub fn draw_oval(
    buf: &mut [i32],
    img_w: jint,
    img_h: jint,
    x: jint,
    y: jint,
    w: jint,
    h: jint,
    argb: jint,
) {
    if w <= 0 || h <= 0 {
        return;
    }
    let stride = img_w as usize;
    let a = w / 2;
    let b = h / 2;
    let xc = x + a;
    let yc = y + b;
    let mut xi = 0;
    let mut yi = b;
    let a2 = (a as i64) * (a as i64);
    let b2 = (b as i64) * (b as i64);
    let mut d1 = b2 - a2 * (b as i64) + (a2 / 4);
    let mut dx = 2 * b2 * (xi as i64);
    let mut dy = 2 * a2 * (yi as i64);

    while dx < dy {
        plot_pixel(buf, stride, xc + xi, yc + yi, img_w, img_h, argb);
        plot_pixel(buf, stride, xc - xi, yc + yi, img_w, img_h, argb);
        plot_pixel(buf, stride, xc + xi, yc - yi, img_w, img_h, argb);
        plot_pixel(buf, stride, xc - xi, yc - yi, img_w, img_h, argb);

        if d1 < 0 {
            xi += 1;
            dx += 2 * b2;
            d1 += dx + b2;
        } else {
            xi += 1;
            yi -= 1;
            dx += 2 * b2;
            dy -= 2 * a2;
            d1 += dx - dy + b2;
        }
    }

    let mut d2 = (b2 * ((xi as i64 * 2 + 1) * (xi as i64 * 2 + 1)) + 4 * a2 * ((yi as i64 - 1) * (yi as i64 - 1)) - 4 * a2 * b2) / 4;
    while yi >= 0 {
        plot_pixel(buf, stride, xc + xi, yc + yi, img_w, img_h, argb);
        plot_pixel(buf, stride, xc - xi, yc + yi, img_w, img_h, argb);
        plot_pixel(buf, stride, xc + xi, yc - yi, img_w, img_h, argb);
        plot_pixel(buf, stride, xc - xi, yc - yi, img_w, img_h, argb);

        if d2 > 0 {
            yi -= 1;
            dy -= 2 * a2;
            d2 += a2 - dy;
        } else {
            xi += 1;
            yi -= 1;
            dx += 2 * b2;
            dy -= 2 * a2;
            d2 += dx - dy + a2;
        }
    }
}

pub fn fill_round_rect(
    buf: &mut [i32],
    img_w: jint,
    img_h: jint,
    x: jint,
    y: jint,
    w: jint,
    h: jint,
    arc_w: jint,
    arc_h: jint,
    argb: jint,
    composite_rule: jint,
) {
    if w <= 0 || h <= 0 {
        return;
    }
    let rx = ((arc_w as f64) * 0.5).min((w as f64) * 0.5);
    let ry = ((arc_h as f64) * 0.5).min((h as f64) * 0.5);
    if rx <= 0.0 || ry <= 0.0 {
        fill_rect(buf, img_w, img_h, x, y, w, h, argb, composite_rule);
        return;
    }

    let stride = img_w as usize;
    let top_curve_y = (y as f64) + ry;
    let bot_curve_y = ((y + h) as f64) - ry;
    let left_cx = (x as f64) + rx;
    let right_cx = ((x + w) as f64) - rx;

    let y0 = y.max(0).min(img_h);
    let y1 = (y + h).max(0).min(img_h);

    for row in y0..y1 {
        let cy_sample = (row as f64) + 0.5;
        let (x0, x1) = if cy_sample < top_curve_y {
            let dy = (cy_sample - top_curve_y) / ry;
            let rem_sq = (1.0 - dy * dy).max(0.0);
            let dx = rx * rem_sq.sqrt();
            ((left_cx - dx).round() as i32, (right_cx + dx).round() as i32)
        } else if cy_sample > bot_curve_y {
            let dy = (cy_sample - bot_curve_y) / ry;
            let rem_sq = (1.0 - dy * dy).max(0.0);
            let dx = rx * rem_sq.sqrt();
            ((left_cx - dx).round() as i32, (right_cx + dx).round() as i32)
        } else {
            (x, x + w)
        };
        plot_span(buf, stride, row, x0, x1, img_w, img_h, argb, composite_rule);
    }
}

#[inline]
pub fn src_over(src: u32, dst: u32) -> u32 {
    let sa = (src >> 24) & 0xFF;
    if sa == 0xFF {
        return src;
    }
    if sa == 0 {
        return dst;
    }
    let sr = (src >> 16) & 0xFF;
    let sg = (src >> 8) & 0xFF;
    let sb = src & 0xFF;
    let da = (dst >> 24) & 0xFF;
    let dr = (dst >> 16) & 0xFF;
    let dg = (dst >> 8) & 0xFF;
    let db = dst & 0xFF;

    let inv = 255 - sa;
    let out_a = sa + (da * inv + 127) / 255;
    let out_r = (sr * sa + dr * inv + 127) / 255;
    let out_g = (sg * sa + dg * inv + 127) / 255;
    let out_b = (sb * sa + db * inv + 127) / 255;
    (out_a << 24) | (out_r << 16) | (out_g << 8) | out_b
}

#[inline]
pub fn clamp_rect(img_w: jint, img_h: jint, x: jint, y: jint, w: jint, h: jint) -> (jint, jint, jint, jint) {
    let x0 = x.max(0).min(img_w);
    let y0 = y.max(0).min(img_h);
    let x1 = (x + w).max(0).min(img_w);
    let y1 = (y + h).max(0).min(img_h);
    (x0, y0, x1, y1)
}

/// Source-atop on straight ARGB pixels: preserve destination coverage, including
/// transparent corners and antialiased edges when tinting Swing icons.
#[inline]
pub fn src_atop(src: u32, dst: u32) -> u32 {
    let da = dst >> 24;
    if da == 0 { return 0; }
    let sa = src >> 24;
    let inv = 255 - sa;
    let channel = |shift: u32| -> u32 {
        ((((src >> shift) & 255) * sa + ((dst >> shift) & 255) * inv + 127) / 255) << shift
    };
    (da << 24) | channel(16) | channel(8) | channel(0)
}

#[cfg(test)]
mod tint_tests {
    use super::*;
    #[test]
    fn recolor_preserves_transparent_and_antialiased_pixels() {
        let mut pixels = [0, 0x40ffffffu32 as i32, 0xffffffffu32 as i32];
        fill_rect(&mut pixels, 3, 1, 0, 0, 3, 1, 0xffff8800u32 as i32, SRC_ATOP);
        assert_eq!(pixels.map(|p| p as u32), [0, 0x40ff8800, 0xffff8800]);
    }
    #[test]
    fn translucent_tint_blends_color_without_changing_coverage() {
        assert_eq!(src_atop(0x80ff0000, 0x400000ff), 0x4080007f);
        assert_eq!(src_atop(0x00ff0000, 0x400000ff), 0x400000ff);
    }
    #[test]
    fn spans_and_image_blits_preserve_coverage() {
        let mut pixels = [0, 0x80ffffffu32 as i32];
        plot_span(&mut pixels, 2, 0, 0, 2, 2, 1, 0xffff8800u32 as i32, SRC_ATOP);
        assert_eq!(pixels.map(|p| p as u32), [0, 0x80ff8800]);
        crate::blit::blit(&[0xff0000ffu32 as i32; 2], 2, 1, 0, 0, 2, 1,
            &mut pixels, 2, 1, 0, 0, 2, 1, SRC_ATOP, 1.0);
        assert_eq!(pixels.map(|p| p as u32), [0, 0x800000ff]);
    }
}