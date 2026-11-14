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
package javax.swing.text.html;
import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.Map; // the JDK's package-private javax.swing.text.html.Map would win over the wildcard on a host compile
import java.util.regex.*;
import javax.swing.text.*;
/** Rule storage for the lightweight text renderer; no scripts or browser execution. */
public class StyleSheet extends StyleContext {
    private static final long serialVersionUID=1L;
    private final List<StyleSheet> styleSheets = new ArrayList<>();
    private final Map<String,String> rules=new LinkedHashMap<>();
    public void addStyleSheet(StyleSheet sheet) {
        if (sheet == null || sheet == this || sheet.containsSheet(this)) return;
        styleSheets.remove(sheet);
        styleSheets.add(0, sheet);
    }
    public void removeStyleSheet(StyleSheet sheet) { styleSheets.remove(sheet); }
    public StyleSheet[] getStyleSheets() { return styleSheets.isEmpty() ? null : styleSheets.toArray(new StyleSheet[0]); }
    private boolean containsSheet(StyleSheet target) {
        if (styleSheets.contains(target)) return true;
        for (StyleSheet sheet : styleSheets) if (sheet.containsSheet(target)) return true;
        return false;
    }
    private Map<String,String> resolvedRules() {
        Map<String,String> result = new LinkedHashMap<>();
        for (int i = styleSheets.size() - 1; i >= 0; i--)
            styleSheets.get(i).resolvedRules().forEach((selector, css) -> result.merge(selector, css, (a, b) -> a + ";" + b));
        rules.forEach((selector, css) -> result.merge(selector, css, (a, b) -> a + ";" + b));
        return result;
    }
    public void addRule(String source){if(source==null)return;Matcher blocks=Pattern.compile("([^{}]+)\\{([^{}]*)\\}").matcher(source);while(blocks.find())for(String name:blocks.group(1).split(",")){String selector=name.trim().toLowerCase(Locale.ROOT);String css=blocks.group(2).trim();rules.merge(selector,css,(old,value)->old+";"+value);Style style=addStyle(selector,null);for(String declaration:css.split(";")){String[] pair=declaration.split(":",2);if(pair.length==2)style.addAttribute(pair[0].trim(),pair[1].trim());}}}
    public void importStyleSheet(URL url){try(InputStream stream=url.openStream()){loadRules(new InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8),url);}catch(IOException error){throw new java.io.UncheckedIOException(error);}}
    public void loadRules(Reader reader,URL base)throws IOException{StringBuilder css=new StringBuilder();char[] buffer=new char[2048];int count;while((count=reader.read(buffer))!=-1)css.append(buffer,0,count);addRule(css.toString());}
    public Style getRule(String selector) {
        Style resolved = null;
        for (int i = styleSheets.size() - 1; i >= 0; i--) {
            Style inherited = styleSheets.get(i).getRule(selector);
            if (inherited != null) {
                if (resolved == null) resolved = addStyle(null, null);
                resolved.addAttributes(inherited);
            }
        }
        Style own = getStyle(selector);
        if (resolved == null) return own;
        if (own != null) resolved.addAttributes(own);
        return resolved;
    }
    public AttributeSet getViewAttributes(View view){return view==null||view.getElement()==null?SimpleAttributeSet.EMPTY:view.getElement().getAttributes();}
    public String applyRules(String html){
        Map<String,String> effectiveRules = resolvedRules();
        Matcher tags=Pattern.compile("<([A-Za-z][A-Za-z0-9]*)([^>]*)>").matcher(html);StringBuffer result=new StringBuffer();
        while(tags.find()){String css=effectiveRules.get(tags.group(1).toLowerCase(Locale.ROOT));String attributes=tags.group(2);Matcher classes=Pattern.compile("(?i)class\\s*=\\s*[\"']([^\"']*)").matcher(attributes);if(classes.find())for(String name:classes.group(1).split("\\s+")){String extra=effectiveRules.get("."+name);if(extra!=null)css=(css==null?"":css+";")+extra;}
            if(css==null){tags.appendReplacement(result,Matcher.quoteReplacement(tags.group()));continue;}
            Matcher inline=Pattern.compile("(?i)style\\s*=\\s*([\"'])(.*?)\\1").matcher(attributes);String merged;
            if(inline.find())merged=inline.replaceFirst(Matcher.quoteReplacement("style=\""+css+";"+inline.group(2)+"\""));else merged=attributes+" style=\""+css.replace("\"","&quot;")+"\"";
            tags.appendReplacement(result,Matcher.quoteReplacement("<"+tags.group(1)+merged+">"));
        }tags.appendTail(result);return result.toString();
    }
}
