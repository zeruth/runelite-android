use crate::fill::{src_over, src_atop};
use crate::{CLEAR, SRC, SRC_ATOP};
use jni::sys::{jfloat, jint};

/// Nearest-neighbour scaled blit with composite + global alpha. For dw==sw and dh==sh
/// this collapses to a straight copy. Coordinates are clipped against both buffers.
pub fn blit(
    src: &[i32],
    src_w: jint,
    src_h: jint,
    sx: jint,
    sy: jint,
    sw: jint,
    sh: jint,
    dst: &mut [i32],
    dst_w: jint,
    dst_h: jint,
    dx: jint,
    dy: jint,
    dw: jint,
    dh: jint,
    composite_rule: jint,
    alpha: jfloat,
) {
    if sw <= 0 || sh <= 0 || dw <= 0 || dh <= 0 {
        return;
    }
    let dst_x0 = dx.max(0).min(dst_w);
    let dst_y0 = dy.max(0).min(dst_h);
    let dst_x1 = (dx + dw).max(0).min(dst_w);
    let dst_y1 = (dy + dh).max(0).min(dst_h);
    if dst_x0 >= dst_x1 || dst_y0 >= dst_y1 {
        return;
    }

    let alpha = alpha.clamp(0.0, 1.0);
    let global_a = (alpha * 255.0 + 0.5) as u32;

    let dst_stride = dst_w as usize;
    let src_stride = src_w as usize;
    let one_to_one = sw == dw && sh == dh;

    for y in dst_y0..dst_y1 {
        let dyrow = y - dy;
        let sy_row = if one_to_one { sy + dyrow } else { sy + (dyrow * sh) / dh };
        if sy_row < 0 || sy_row >= src_h {
            continue;
        }
        let dst_off = y as usize * dst_stride;
        let src_off = sy_row as usize * src_stride;
        for x in dst_x0..dst_x1 {
            let dxrow = x - dx;
            let sx_col = if one_to_one { sx + dxrow } else { sx + (dxrow * sw) / dw };
            if sx_col < 0 || sx_col >= src_w {
                continue;
            }
            let mut sp = src[src_off + sx_col as usize] as u32;
            if global_a != 255 {
                let sa = (sp >> 24) & 0xFF;
                let new_a = (sa * global_a + 127) / 255;
                sp = (new_a << 24) | (sp & 0x00FFFFFF);
            }
            let dst_idx = dst_off + x as usize;
            match composite_rule {
                SRC => dst[dst_idx] = sp as i32,
                CLEAR => dst[dst_idx] = 0,
                SRC_ATOP => dst[dst_idx] = src_atop(sp, dst[dst_idx] as u32) as i32,
                _ => {
                    let dp = dst[dst_idx] as u32;
                    dst[dst_idx] = src_over(sp, dp) as i32;
                }
            }
        }
    }
}

/// Move a rectangle from (x, y) to (x+dx, y+dy) on the same buffer. Direction-aware so
/// overlapping regions don't smear.
pub fn copy_area(
    buf: &mut [i32],
    img_w: jint,
    img_h: jint,
    x: jint,
    y: jint,
    w: jint,
    h: jint,
    dst_x: jint,
    dst_y: jint,
) {
    if w <= 0 || h <= 0 {
        return;
    }
    let stride = img_w as usize;
    let going_up = dst_y < y;
    let going_left = dst_x < x;

    let (r_start, r_end, r_step): (i32, i32, i32) = if going_up {
        (0, h, 1)
    } else {
        (h - 1, -1, -1)
    };

    let mut r = r_start;
    while r != r_end {
        let src_y = y + r;
        let dst_y_r = dst_y + r;
        if src_y >= 0 && src_y < img_h && dst_y_r >= 0 && dst_y_r < img_h {
            let (c_start, c_end, c_step): (i32, i32, i32) = if going_left {
                (0, w, 1)
            } else {
                (w - 1, -1, -1)
            };
            let mut c = c_start;
            while c != c_end {
                let src_x = x + c;
                let dst_x_c = dst_x + c;
                if src_x >= 0 && src_x < img_w && dst_x_c >= 0 && dst_x_c < img_w {
                    buf[dst_y_r as usize * stride + dst_x_c as usize] =
                        buf[src_y as usize * stride + src_x as usize];
                }
                c += c_step;
            }
        }
        r += r_step;
    }
}

