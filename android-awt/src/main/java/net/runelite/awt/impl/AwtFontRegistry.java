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
package net.runelite.awt.impl;

import android.graphics.Typeface;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Process-wide registry mapping AWT {@link java.awt.Font#getFamily() font family names}
 * to Android {@link Typeface} handles. Populated when shadow {@code Font.createFont(int,
 * InputStream)} is called — RuneLite loads runescape.ttf / runescape_bold.ttf this way,
 * then references the loaded fonts later via {@code new Font(family, style, size)}.
 *
 * Without this, the shadow {@code Font.createFont} drained the stream and returned a
 * default Font, so every "runescape" lookup later in the rendering path fell back to
 * Android's {@code Typeface.DEFAULT} (i.e. Roboto) — meaning none of the in-game UI
 * fonts looked right.
 *
 * Family name is parsed from the TTF/OTF 'name' table (Windows Unicode BMP record,
 * nameID=1). If parsing fails we fall back to a synthetic "rl-font-N" name; that
 * still works for lookup as long as the caller holds onto {@code font.getFamily()}.
 *
 * <h3>Family mapping</h3>
 * {@link #lookup(String, int)} resolves a {@code Font} name to a Typeface in this order:
 * <ol>
 * <li>Logical families, case-insensitive, never overridden by a registered face (as on
 *     desktop): {@code Dialog} and {@code SansSerif} → {@code sans-serif} (Roboto),
 *     {@code Serif} → {@code serif} (Noto Serif), {@code Monospaced} and
 *     {@code DialogInput} → {@code monospace} (Droid Sans Mono). Styles come from the
 *     system family's own bold/italic members via {@code Typeface.create(tf, style)}.
 *     The table itself is {@link FontFamilies#androidFamilyFor(String)}.</li>
 * <li>Families registered by {@code Font.createFont}: exact, then case-insensitive.</li>
 * <li>Android system families listed in {@code /system/etc/fonts.xml}
 *     ({@code sans-serif-condensed}, {@code serif-monospace}, {@code casual}, ...), via
 *     {@code Typeface.create(name, NORMAL)}.</li>
 * <li>A registered family that starts with the name or that the name starts with
 *     ({@code "RuneScape"} vs {@code "RuneScape Plain"}).</li>
 * <li>Any other name (the gamepack's {@code "Helvetica"}, a plugin's {@code "Arial"})
 *     renders with the primary RuneScape face. This is a deliberate deviation: desktop
 *     would draw it in Dialog, but on a phone the OSRS face is what those strings were
 *     drawn in before logical families existed here, and {@code Font.getFamily()} still
 *     reports {@code Dialog} for such names, as desktop does.</li>
 * </ol>
 * The names those families report ({@code getFamily}, {@code getFontName},
 * {@code GraphicsEnvironment.getAvailableFontFamilyNames}) come from {@link FontFamilies},
 * which this class feeds on every registration.
 */
public final class AwtFontRegistry
{
	private static final String TAG = "AwtFontRegistry";

	/** Family → faces indexed by java.awt.Font style bits (PLAIN, BOLD, ITALIC, BOLD|ITALIC).
	 *  Arrays are replaced, never mutated, once published. */
	private static final ConcurrentHashMap<String, Typeface[]> FAMILIES = new ConcurrentHashMap<>();
	/** java.awt.Font style bits the registered face itself already carries (BOLD from
	 *  OS/2 usWeightClass >= 600 / fsSelection / subfamily, ITALIC likewise). Decides
	 *  whether a styled Font needs synthesis — RuneLite's FontManager asks for the bold
	 *  RuneScape face with Font.BOLD and it must not be emboldened a second time. */
	private static final ConcurrentHashMap<Typeface, Integer> FACE_STYLES = new ConcurrentHashMap<>();

	/** Style bits {@code tf} already renders with; 0 for faces not registered here. */
	public static int faceStyle(Typeface tf)
	{
		Integer s = tf != null ? FACE_STYLES.get(tf) : null;
		return s != null ? s : 0;
	}

	/** Whether {@code tf} was loaded by {@link #register(InputStream)}. */
	public static boolean isRegistered(Typeface tf)
	{
		return tf != null && FACE_STYLES.containsKey(tf);
	}

	/** 'maxp' numGlyphs of registered faces. */
	private static final ConcurrentHashMap<Typeface, Integer> GLYPH_COUNTS = new ConcurrentHashMap<>();
	/** Inverse caret slope from the 'post' italicAngle of registered italic faces. */
	private static final ConcurrentHashMap<Typeface, Float> ITALIC_SLOPES = new ConcurrentHashMap<>();
	/** System family name → its NORMAL Typeface, and system font file → its glyph count. */
	private static final ConcurrentHashMap<String, Typeface> SYSTEM = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<String, Integer> SYSTEM_GLYPH_COUNTS = new ConcurrentHashMap<>();

	/** Run-over-rise slope of a registered italic face, NaN when its 'post' table has none. */
	public static float italicSlope(Typeface tf)
	{
		Float s = tf != null ? ITALIC_SLOPES.get(tf) : null;
		return s != null ? s : Float.NaN;
	}

	/**
	 * Glyph count of the face {@code family}/{@code style} renders with, from 'maxp':
	 * registered faces from their bytes, logical and system families from their regular
	 * file under /system/fonts. -1 when unknown.
	 */
	public static int numGlyphs(String family, int style)
	{
		String android = FontFamilies.androidFamilyFor(family);
		String system = android != null ? android : FontFamilies.registered(family) == null ? FontFamilies.system(family) : null;
		if (system != null)
		{
			return systemGlyphCount(FontFamilies.systemFontFile(system));
		}
		Typeface tf = lookup(family, style);
		Integer n = tf != null ? GLYPH_COUNTS.get(tf) : null;
		return n != null ? n : -1;
	}

	private static Typeface systemTypeface(String family)
	{
		Typeface tf = SYSTEM.get(family);
		if (tf == null)
		{
			tf = Typeface.create(family, Typeface.NORMAL);
			Typeface prev = SYSTEM.putIfAbsent(family, tf);
			if (prev != null) tf = prev;
		}
		return tf;
	}

	private static int systemGlyphCount(String file)
	{
		if (file == null) return -1;
		Integer cached = SYSTEM_GLYPH_COUNTS.get(file);
		if (cached != null) return cached;
		int n = -1;
		try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(new File("/system/fonts", file), "r"))
		{
			long base = 0;
			if (raf.readInt() == 0x74746366 /* 'ttcf' */)
			{
				raf.seek(12);
				base = raf.readInt() & 0xFFFFFFFFL;
			}
			raf.seek(base + 4);
			int numTables = raf.readUnsignedShort();
			for (int i = 0; i < numTables; i++)
			{
				raf.seek(base + 12 + i * 16L);
				if (raf.readInt() == 0x6D617870 /* 'maxp' */)
				{
					raf.readInt(); // checksum
					raf.seek((raf.readInt() & 0xFFFFFFFFL) + 4);
					n = raf.readUnsignedShort();
					break;
				}
			}
		}
		catch (Throwable t)
		{
			Log.w(TAG, "font: maxp read failed for " + file, t);
		}
		SYSTEM_GLYPH_COUNTS.put(file, n);
		return n;
	}
	private static final AtomicInteger SYNTHETIC_COUNTER = new AtomicInteger();

	private AwtFontRegistry() {}

	/** Read {@code in} fully, write to a temp file, load as Typeface, and register
	 *  under whatever family name the TTF declares (or a synthetic fallback).
	 *  Returns the registered family name; null on failure. */
	public static String register(InputStream in)
	{
		if (in == null) {
			Log.w(TAG, "font: register called with null stream — getResourceAsStream missed in classpath?");
			return null;
		}
		byte[] bytes;
		try
		{
			bytes = readAll(in);
		}
		catch (IOException ex)
		{
			Log.w(TAG, "font: failed to read stream", ex);
			return null;
		}
		if (bytes.length < 12) {
			Log.w(TAG, "font: stream produced only " + bytes.length + " bytes — not a valid TTF");
			return null;
		}
		Log.i(TAG, "font: read " + bytes.length + " bytes from stream");

		String family = parseFamilyName(bytes);
		String subfamily = parseSubfamilyName(bytes);
		if (family == null) family = "rl-font-" + SYNTHETIC_COUNTER.incrementAndGet();
		int style = parseFaceStyle(bytes, subfamily);
		// RIBBI faces (regular / bold / italic / bold italic) are members of their family,
		// as on desktop: runescape_bold.ttf declares Family "RuneScape", Subfamily
		// "Regular", and is bold only by OS/2 weight 700 + fsSelection, so a family-name
		// key alone dropped it as a duplicate and every RuneScape BOLD request rendered
		// the regular face. Other subfamilies ("Light", "Condensed") are distinct faces,
		// so they keep a suffixed family name.
		if (subfamily != null && !subfamily.isEmpty() && !isRibbi(subfamily))
		{
			// Avoid double-suffix when Family already ends in the same word ("RuneScape Small").
			if (!family.toLowerCase().endsWith(" " + subfamily.toLowerCase()))
			{
				family = family + " " + subfamily;
			}
		}

		// Re-registering the same face is a no-op — RuneLite's FontManager runs
		// createFont in a static block, so each process start hits this path once
		// per font. Reuse the existing Typeface rather than rewriting the temp file.
		Typeface[] slots = FAMILIES.get(family);
		if (slots != null && slots[style] != null) return family;

		Typeface tf = loadTypeface(bytes, family);
		if (tf == null) return null;
		FACE_STYLES.put(tf, style);
		int glyphs = parseGlyphCount(bytes);
		if (glyphs > 0) GLYPH_COUNTS.put(tf, glyphs);
		float slope = parseItalicSlope(bytes);
		if (!Float.isNaN(slope)) ITALIC_SLOPES.put(tf, slope);
		String fullName = parseName(bytes, 4);
		FontFamilies.registerFace(family, style, fullName != null ? fullName : family, parseName(bytes, 6));
		synchronized (FAMILIES)
		{
			slots = FAMILIES.get(family);
			Typeface[] next = slots != null ? slots.clone() : new Typeface[4];
			if (next[style] == null) next[style] = tf;
			FAMILIES.put(family, next);
		}
		Log.i(TAG, "font: registered \"" + family + "\" style=" + style + " (" + bytes.length + " bytes)");
		return family;
	}

	private static boolean isRibbi(String subfamily)
	{
		String s = subfamily.toLowerCase().replace("-", " ").trim();
		return s.equals("regular") || s.equals("plain") || s.equals("book") || s.equals("normal")
			|| s.equals("roman") || s.equals("bold") || s.equals("italic") || s.equals("oblique")
			|| s.equals("bold italic") || s.equals("bolditalic") || s.equals("bold oblique");
	}

	/** Best registered face of {@code family} for java.awt.Font style bits: the exact
	 *  member if registered, else the closest one (the caller synthesizes the rest, see
	 *  {@link #faceStyle}). Logical and system families return their NORMAL system
	 *  Typeface, from which {@code Typeface.create(tf, style)} picks the family's real
	 *  styled member. The order is the family mapping in the class comment. */
	public static Typeface lookup(String family, int style)
	{
		String android = FontFamilies.androidFamilyFor(family);
		if (android != null) return systemTypeface(android);
		if (family != null && FontFamilies.registered(family) == null)
		{
			String system = FontFamilies.system(family);
			if (system != null) return systemTypeface(system);
		}
		Typeface[] slots = slotsFor(family);
		if (slots == null) return null;
		style &= 3;
		if (slots[style] != null) return slots[style];
		if (slots[style & java.awt.Font.BOLD] != null) return slots[style & java.awt.Font.BOLD];
		if (slots[style & java.awt.Font.ITALIC] != null) return slots[style & java.awt.Font.ITALIC];
		for (Typeface t : slots) if (t != null) return t;
		return null;
	}

	/** Per-face code point → glyph code and its inverse, so a GlyphVector built from
	 *  {@code int[]} glyph codes (usually codes read back from another vector) can find
	 *  the characters to outline and measure. */
	private static final ConcurrentHashMap<Typeface, GlyphMap> GLYPH_MAPS = new ConcurrentHashMap<>();

	private static final class GlyphMap
	{
		final ConcurrentHashMap<Integer, Integer> codes = new ConcurrentHashMap<>();
		final ConcurrentHashMap<Integer, Integer> codePoints = new ConcurrentHashMap<>();
		volatile boolean seeded;
	}

	private static GlyphMap glyphMap(Typeface tf)
	{
		GlyphMap map = GLYPH_MAPS.get(tf);
		if (map == null)
		{
			GlyphMap fresh = new GlyphMap();
			map = GLYPH_MAPS.putIfAbsent(tf, fresh);
			if (map == null) map = fresh;
		}
		return map;
	}

	/**
	 * Glyph code of {@code codePoint} in {@code tf}. On API 31+ this is the font's real glyph
	 * id, read from {@link android.graphics.text.TextRunShaper} shaping the code point alone
	 * (a code point the face lacks gets the id of the fallback font Minikin picks, so ids of
	 * different fonts can share a vector). Below API 31 Android has no public glyph-id API and
	 * the code is the Unicode code point itself, which is stable and round-trips but does not
	 * match the font's cmap the way desktop codes do.
	 */
	public static int glyphCode(Typeface tf, int codePoint)
	{
		if (android.os.Build.VERSION.SDK_INT < 31) return codePoint;
		GlyphMap map = glyphMap(tf);
		Integer known = map.codes.get(codePoint);
		if (known != null) return known;
		int code = Shaper31.shape(tf, codePoint);
		map.codes.put(codePoint, code);
		map.codePoints.putIfAbsent(code, codePoint);
		return code;
	}

	/**
	 * Inverse of {@link #glyphCode}: the code point a glyph code stands for, or -1 when it is
	 * unknown. On API 31+ only codes this process has produced for {@code tf} are known, plus
	 * U+0020..U+024F (Basic Latin through Latin Extended-B), which are shaped once per face on
	 * the first miss; Android cannot enumerate a font's glyphs.
	 */
	public static int codePointForGlyph(Typeface tf, int glyphCode)
	{
		if (android.os.Build.VERSION.SDK_INT < 31)
		{
			return glyphCode > 0 && Character.isValidCodePoint(glyphCode) ? glyphCode : -1;
		}
		GlyphMap map = glyphMap(tf);
		Integer cp = map.codePoints.get(glyphCode);
		if (cp == null && !map.seeded)
		{
			for (int c = 0x20; c <= 0x24F; c++) glyphCode(tf, c);
			map.seeded = true;
			cp = map.codePoints.get(glyphCode);
		}
		return cp != null ? cp : -1;
	}

	@android.annotation.TargetApi(31)
	private static final class Shaper31
	{
		private static final ThreadLocal<android.graphics.Paint> PAINT = new ThreadLocal<android.graphics.Paint>()
		{
			@Override
			protected android.graphics.Paint initialValue()
			{
				return new android.graphics.Paint();
			}
		};

		static int shape(Typeface tf, int codePoint)
		{
			android.graphics.Paint paint = PAINT.get();
			paint.setTypeface(tf);
			char[] chars = Character.toChars(codePoint);
			android.graphics.text.PositionedGlyphs glyphs = android.graphics.text.TextRunShaper.shapeTextRun(
				chars, 0, chars.length, 0, chars.length, 0f, 0f, false, paint);
			return glyphs.glyphCount() > 0 ? glyphs.getGlyphId(0) : 0;
		}
	}

	private static final java.util.Set<String> WARNED = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

	/** PLAIN lookup. Registered families try an exact match first, then
	 *  case-insensitive, then a startswith probe (e.g. RL may pass "RuneScape" while
	 *  the TTF registered itself as "RuneScape Plain"). A name that is neither logical,
	 *  registered nor a system family falls back to the primary "RuneScape"
	 *  registration so unknown physical names (Helvetica, Arial, …) still render in the
	 *  OSRS face; the miss is logged once per family so it's easy to spot in logcat. */
	public static Typeface lookup(String family)
	{
		return lookup(family, java.awt.Font.PLAIN);
	}

	private static Typeface[] slotsFor(String family)
	{
		if (family == null) return null;
		Typeface[] t = FAMILIES.get(family);
		if (t != null) return t;
		// case-insensitive
		for (java.util.Map.Entry<String, Typeface[]> e : FAMILIES.entrySet())
		{
			if (e.getKey().equalsIgnoreCase(family)) return e.getValue();
		}
		// substring / prefix
		String fl = family.toLowerCase();
		for (java.util.Map.Entry<String, Typeface[]> e : FAMILIES.entrySet())
		{
			String kl = e.getKey().toLowerCase();
			if (kl.startsWith(fl) || fl.startsWith(kl)) return e.getValue();
		}
		if (WARNED.add(family))
		{
			Log.w(TAG, "font: lookup miss for \"" + family + "\" (registered=" + FAMILIES.keySet() + "), falling back to primary");
		}
		// Last-resort: hand back whichever primary RS face is registered (prefer
		// the unstyled "RuneScape" if available; otherwise any registered TTF).
		// This trades strict fidelity for "the screen looks like OSRS" on Android.
		Typeface[] primary = FAMILIES.get("RuneScape");
		if (primary != null) return primary;
		java.util.Iterator<Typeface[]> it = FAMILIES.values().iterator();
		return it.hasNext() ? it.next() : null;
	}

	private static byte[] readAll(InputStream in) throws IOException
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
		byte[] buf = new byte[8192];
		int n;
		while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
		return out.toByteArray();
	}

	private static Typeface loadTypeface(byte[] bytes, String family)
	{
		File f = null;
		try
		{
			f = File.createTempFile("rl-font-", ".ttf");
			f.deleteOnExit();
			try (FileOutputStream os = new FileOutputStream(f))
			{
				os.write(bytes);
			}
			return Typeface.createFromFile(f);
		}
		catch (Throwable t)
		{
			Log.w(TAG, "font: typeface load failed for \"" + family + "\"", t);
			if (f != null) try { f.delete(); } catch (Throwable ignored) {}
			return null;
		}
	}

	// ---------------------------------------------------------------------------
	// TTF/OTF 'name' table parser — extracts the family name. Spec:
	//   https://learn.microsoft.com/en-us/typography/opentype/spec/name
	// ---------------------------------------------------------------------------

	private static int parseFaceStyle(byte[] bytes, String subfamily)
	{
		int style = 0;
		String sub = subfamily != null ? subfamily.toLowerCase() : "";
		if (sub.contains("bold") || sub.contains("black") || sub.contains("heavy")) style |= java.awt.Font.BOLD;
		if (sub.contains("italic") || sub.contains("oblique")) style |= java.awt.Font.ITALIC;
		try
		{
			int os2 = findTable(bytes, 0x4F532F32 /* 'OS/2' */);
			if (os2 >= 0 && os2 + 64 <= bytes.length)
			{
				ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
				int weight = bb.getShort(os2 + 4) & 0xFFFF;
				int fsSelection = bb.getShort(os2 + 62) & 0xFFFF;
				if (weight >= 600 || (fsSelection & 0x20) != 0) style |= java.awt.Font.BOLD;
				if ((fsSelection & 0x01) != 0) style |= java.awt.Font.ITALIC;
			}
		}
		catch (Throwable t)
		{
			Log.w(TAG, "font: OS/2 parse failed", t);
		}
		return style;
	}

	private static int parseGlyphCount(byte[] bytes)
	{
		try
		{
			int maxp = findTable(bytes, 0x6D617870 /* 'maxp' */);
			if (maxp < 0 || maxp + 6 > bytes.length) return -1;
			return ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).getShort(maxp + 4) & 0xFFFF;
		}
		catch (Throwable t)
		{
			return -1;
		}
	}

	/** 'post' italicAngle (16.16 degrees, negative leaning right) as run over rise; NaN for
	 *  an upright or unreadable face. */
	private static float parseItalicSlope(byte[] bytes)
	{
		try
		{
			int post = findTable(bytes, 0x706F7374 /* 'post' */);
			if (post < 0 || post + 8 > bytes.length) return Float.NaN;
			double degrees = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).getInt(post + 4) / 65536.0;
			return degrees == 0 ? Float.NaN : (float) Math.tan(Math.toRadians(-degrees));
		}
		catch (Throwable t)
		{
			return Float.NaN;
		}
	}

	private static int findTable(byte[] bytes, int wantTag)
	{
		ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
		int numTables = bb.getShort(4) & 0xFFFF;
		for (int i = 0; i < numTables; i++)
		{
			int rec = 12 + i * 16;
			if (rec + 16 > bytes.length) break;
			if (bb.getInt(rec) == wantTag) return bb.getInt(rec + 8);
		}
		return -1;
	}

	private static String parseFamilyName(byte[] bytes)    { return parseName(bytes, 1); }
	private static String parseSubfamilyName(byte[] bytes) { return parseName(bytes, 2); }

	private static String parseName(byte[] bytes, int wantNameId)
	{
		ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
		// Offset Table: sfntVersion(u32), numTables(u16), searchRange(u16),
		// entrySelector(u16), rangeShift(u16).
		try
		{
			bb.position(0);
			bb.getInt(); // sfntVersion
			int numTables = bb.getShort() & 0xFFFF;
			bb.position(bb.position() + 6); // skip 3 × u16

			int nameOffset = -1;
			for (int i = 0; i < numTables; i++)
			{
				int tag = bb.getInt();
				bb.getInt(); // checksum
				int offset = bb.getInt();
				bb.getInt(); // length
				if (tag == 0x6E616D65 /* 'name' */)
				{
					nameOffset = offset;
					break;
				}
			}
			if (nameOffset < 0) return null;

			// 'name' table header: format(u16), count(u16), stringOffset(u16)
			bb.position(nameOffset);
			bb.getShort(); // format
			int count = bb.getShort() & 0xFFFF;
			int stringOffset = bb.getShort() & 0xFFFF;
			int storageBase = nameOffset + stringOffset;

			// Pass 1: Windows + Unicode BMP record (platformID=3 encodingID=1) — the
			// canonical Windows-side name. Pass 2: Macintosh Roman fallback.
			for (int pass = 0; pass < 2; pass++)
			{
				int wantPlatform = (pass == 0) ? 3 : 1;
				int wantEncoding = (pass == 0) ? 1 : 0;
				for (int i = 0; i < count; i++)
				{
					int recOff = nameOffset + 6 + i * 12;
					bb.position(recOff);
					int platformID = bb.getShort() & 0xFFFF;
					int encodingID = bb.getShort() & 0xFFFF;
					bb.getShort(); // languageID
					int nameID = bb.getShort() & 0xFFFF;
					int length = bb.getShort() & 0xFFFF;
					int sOff = bb.getShort() & 0xFFFF;
					if (nameID != wantNameId) continue;
					if (platformID != wantPlatform || encodingID != wantEncoding) continue;
					int start = storageBase + sOff;
					if (start + length > bytes.length) continue;
					String name = (pass == 0)
						? new String(bytes, start, length, java.nio.charset.StandardCharsets.UTF_16BE)
						: new String(bytes, start, length, java.nio.charset.StandardCharsets.ISO_8859_1);
					if (!name.isEmpty()) return name;
				}
			}
		}
		catch (Throwable t)
		{
			Log.w(TAG, "font: name-table parse failed", t);
		}
		return null;
	}
}
