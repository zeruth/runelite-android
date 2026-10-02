package javax.swing;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal HTML renderer for shadow JLabel/JButton — RuneLite labels frequently
 * use HTML markup like {@code "<html><span style='color:#ff0000'>Foo</span> bar</html>"}
 * to color-mix multiple words in a single label, or {@code "<br/>"} to wrap onto
 * a new line. The full {@code javax.swing.text.html} pipeline is huge; this
 * implementation supports only what RL actually emits:
 *
 *  - {@code <html>}, {@code </html>}: outer wrapper, stripped.
 *  - {@code <body style='...'>}, {@code </body>}: stripped; outer style honored.
 *  - {@code <br>} / {@code <br/>} / {@code <br />}: line break.
 *  - {@code <span style='color:#RRGGBB'>...</span>}: foreground swap.
 *  - {@code <b>}, {@code </b>}: bold toggle (visual only — we don't carry it
 *    forward to FontMetrics since glyph metrics already approximate it well
 *    enough at the small sizes RL uses).
 *  - Any other tag: stripped, its inner text kept.
 *  - HTML entities {@code &amp;}, {@code &lt;}, {@code &gt;}, {@code &nbsp;}.
 *
 * Output: a list of {@link Line}, each with one or more {@link Run}s. The
 * caller paints them and uses {@link #measure} to size the label.
 */
final class HtmlTextRenderer
{
	static final class Run
	{
		final String text;
		final Color color;       // null = use foreground
		final boolean bold;
		Run(String text, Color color, boolean bold) { this.text = text; this.color = color; this.bold = bold; }
	}

	static final class Line
	{
		final List<Run> runs = new ArrayList<>(2);
	}

	static final class Layout
	{
		final List<Line> lines;
		final int width;
		final int height;
		Layout(List<Line> lines, int w, int h) { this.lines = lines; this.width = w; this.height = h; }
	}

	static boolean isHtml(String s)
	{
		if (s == null || s.length() < 6) return false;
		// Skip leading whitespace before <html>
		int i = 0;
		while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
		return i + 6 <= s.length() && s.regionMatches(true, i, "<html>", 0, 6);
	}

	/** Parse + measure. Returns a Layout that can be painted with {@link #paint}. */
	static Layout measure(String html, FontMetrics fm)
	{
		List<Line> lines = parse(html);
		int width = 0;
		for (Line line : lines)
		{
			int w = 0;
			for (Run r : line.runs) w += fm.stringWidth(r.text);
			if (w > width) width = w;
		}
		int height = lines.size() * fm.getHeight();
		return new Layout(lines, width, height);
	}

	/** Paint a previously-measured layout. {@code defaultColor} is used for runs
	 *  that didn't override it via {@code <span style='color:...'>}. */
	static void paint(Graphics g, Layout layout, FontMetrics fm, int x, int y, Color defaultColor)
	{
		int lineHeight = fm.getHeight();
		int ascent = fm.getAscent();
		for (int i = 0; i < layout.lines.size(); i++)
		{
			Line line = layout.lines.get(i);
			int cx = x;
			int baseline = y + i * lineHeight + ascent;
			for (Run r : line.runs)
			{
				Color c = r.color != null ? r.color : defaultColor;
				if (c != null) g.setColor(c);
				g.drawString(r.text, cx, baseline);
				cx += fm.stringWidth(r.text);
			}
		}
	}

	// -------------------------------------------------------------------------

	private static List<Line> parse(String html)
	{
		List<Line> lines = new ArrayList<>();
		Line current = new Line();
		lines.add(current);

		// null means inherit the label foreground; ArrayDeque rejects null entries.
		java.util.Deque<Color> colorStack = new java.util.LinkedList<>();
		java.util.Deque<Boolean> boldStack = new java.util.ArrayDeque<>();

		Color color = null;
		boolean bold = false;

		StringBuilder buf = new StringBuilder();
		int i = 0;
		int n = html.length();
		while (i < n)
		{
			char c = html.charAt(i);
			if (c == '<')
			{
				// Flush accumulated text
				if (buf.length() > 0)
				{
					current.runs.add(new Run(buf.toString(), color, bold));
					buf.setLength(0);
				}
				// Find matching '>'
				int end = html.indexOf('>', i + 1);
				if (end < 0) break;
				String tag = html.substring(i + 1, end).trim();
				i = end + 1;

				String tagLower = tag.toLowerCase();
				boolean closing = tagLower.startsWith("/");
				if (closing) tagLower = tagLower.substring(1).trim();

				// Match against the tag NAME (first word before any attr).
				int sp = tagLower.indexOf(' ');
				String tagName = sp < 0 ? tagLower : tagLower.substring(0, sp);
				// <br/> with self-close slash
				if (tagName.endsWith("/")) tagName = tagName.substring(0, tagName.length() - 1).trim();

				switch (tagName)
				{
					case "br":
						current = new Line();
						lines.add(current);
						break;
					case "span":
					case "body":
					case "font":
						if (closing)
						{
							if (!colorStack.isEmpty()) color = colorStack.pop();
						}
						else
						{
							colorStack.push(color);
							Color nc = parseColorAttr(tag);
							if (nc != null) color = nc;
						}
						break;
					case "b":
					case "strong":
						if (closing) { if (!boldStack.isEmpty()) bold = boldStack.pop(); }
						else { boldStack.push(bold); bold = true; }
						break;
					default:
						// strip
						break;
				}
				continue;
			}
			if (c == '&')
			{
				int semi = html.indexOf(';', i + 1);
				if (semi > 0 && semi - i <= 7)
				{
					String ent = html.substring(i + 1, semi).toLowerCase();
					switch (ent)
					{
						case "amp":  buf.append('&'); i = semi + 1; continue;
						case "lt":   buf.append('<'); i = semi + 1; continue;
						case "gt":   buf.append('>'); i = semi + 1; continue;
						case "nbsp": buf.append(' '); i = semi + 1; continue;
						case "quot": buf.append('"'); i = semi + 1; continue;
						case "apos": buf.append('\''); i = semi + 1; continue;
						default:
							// numeric &#NN; entities, optional
							if (ent.startsWith("#"))
							{
								try
								{
									int code = ent.startsWith("#x")
										? Integer.parseInt(ent.substring(2), 16)
										: Integer.parseInt(ent.substring(1), 10);
									buf.append((char) code);
									i = semi + 1;
									continue;
								}
								catch (NumberFormatException ignored) {}
							}
							break;
					}
				}
				buf.append(c);
				i++;
				continue;
			}
			buf.append(c);
			i++;
		}
		if (buf.length() > 0)
		{
			current.runs.add(new Run(buf.toString(), color, bold));
		}
		// Drop trailing empty trailing line caused by closing </html>
		if (lines.size() > 1 && lines.get(lines.size() - 1).runs.isEmpty())
		{
			lines.remove(lines.size() - 1);
		}
		return lines;
	}

	/** Pull a `color:#RRGGBB` or `color:name` from a tag's style attribute, or
	 *  a direct `color="..."` attribute on a {@code <font>}. */
	private static Color parseColorAttr(String tagBody)
	{
		String lower = tagBody.toLowerCase();
		// style='...color:#xxx...'
		int s = lower.indexOf("color");
		if (s < 0) return null;
		// Walk past 'color' and any whitespace/quote/:/=
		int j = s + 5;
		while (j < lower.length() && (lower.charAt(j) == ':' || lower.charAt(j) == '=' || Character.isWhitespace(lower.charAt(j))
			|| lower.charAt(j) == '\'' || lower.charAt(j) == '"')) j++;
		// Read the value up to ; or ' or " or whitespace or >
		int k = j;
		while (k < lower.length())
		{
			char ch = lower.charAt(k);
			if (ch == ';' || ch == '\'' || ch == '"' || ch == ' ' || ch == '>' || ch == ')') break;
			k++;
		}
		if (k <= j) return null;
		String val = lower.substring(j, k).trim();
		if (val.isEmpty()) return null;
		try
		{
			if (val.startsWith("#"))
			{
				if (val.length() == 7) return new Color(Integer.parseInt(val.substring(1), 16));
				if (val.length() == 4)
				{
					// #RGB shorthand
					int r = Integer.parseInt(val.substring(1, 2), 16) * 17;
					int gg = Integer.parseInt(val.substring(2, 3), 16) * 17;
					int b = Integer.parseInt(val.substring(3, 4), 16) * 17;
					return new Color(r, gg, b);
				}
			}
			switch (val)
			{
				case "black":   return Color.BLACK;
				case "white":   return Color.WHITE;
				case "red":     return Color.RED;
				case "green":   return Color.GREEN;
				case "blue":    return Color.BLUE;
				case "yellow":  return Color.YELLOW;
				case "cyan":    return Color.CYAN;
				case "magenta": return Color.MAGENTA;
				case "gray":
				case "grey":    return Color.GRAY;
				case "orange":  return Color.ORANGE;
				case "pink":    return Color.PINK;
				default:        return null;
			}
		}
		catch (Throwable ignored)
		{
			return null;
		}
	}

	private HtmlTextRenderer() {}
}
