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
    private final Map<String,String> rules=new LinkedHashMap<>();
    public void addRule(String source){if(source==null)return;Matcher blocks=Pattern.compile("([^{}]+)\\{([^{}]*)\\}").matcher(source);while(blocks.find())for(String name:blocks.group(1).split(",")){String selector=name.trim().toLowerCase(Locale.ROOT);String css=blocks.group(2).trim();rules.merge(selector,css,(old,value)->old+";"+value);Style style=addStyle(selector,null);for(String declaration:css.split(";")){String[] pair=declaration.split(":",2);if(pair.length==2)style.addAttribute(pair[0].trim(),pair[1].trim());}}}
    public void importStyleSheet(URL url){try(InputStream stream=url.openStream()){loadRules(new InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8),url);}catch(IOException error){throw new java.io.UncheckedIOException(error);}}
    public void loadRules(Reader reader,URL base)throws IOException{StringBuilder css=new StringBuilder();char[] buffer=new char[2048];int count;while((count=reader.read(buffer))!=-1)css.append(buffer,0,count);addRule(css.toString());}
    public Style getRule(String selector){return getStyle(selector);}
    public AttributeSet getViewAttributes(View view){return view==null||view.getElement()==null?SimpleAttributeSet.EMPTY:view.getElement().getAttributes();}
    public String applyRules(String html){
        Matcher tags=Pattern.compile("<([A-Za-z][A-Za-z0-9]*)([^>]*)>").matcher(html);StringBuffer result=new StringBuffer();
        while(tags.find()){String css=rules.get(tags.group(1).toLowerCase(Locale.ROOT));String attributes=tags.group(2);Matcher classes=Pattern.compile("(?i)class\\s*=\\s*[\"']([^\"']*)").matcher(attributes);if(classes.find())for(String name:classes.group(1).split("\\s+")){String extra=rules.get("."+name);if(extra!=null)css=(css==null?"":css+";")+extra;}
            if(css==null){tags.appendReplacement(result,Matcher.quoteReplacement(tags.group()));continue;}
            Matcher inline=Pattern.compile("(?i)style\\s*=\\s*([\"'])(.*?)\\1").matcher(attributes);String merged;
            if(inline.find())merged=inline.replaceFirst(Matcher.quoteReplacement("style=\""+css+";"+inline.group(2)+"\""));else merged=attributes+" style=\""+css.replace("\"","&quot;")+"\"";
            tags.appendReplacement(result,Matcher.quoteReplacement("<"+tags.group(1)+merged+">"));
        }tags.appendTail(result);return result.toString();
    }
}
