package javax.accessibility;

import java.util.HashMap;
import java.util.ListResourceBundle;
import java.util.Map;

/**
 * The English display strings of the accessibility roles, states, relations and actions.
 *
 * @deprecated This class is deprecated as of version 1.3 of the Java Platform.
 */
@Deprecated
public class AccessibleResourceBundle extends ListResourceBundle {

    private static final Object[][] CONTENTS = {
        // roles
        { "alert", "alert" },
        { "awtcomponent", "AWT component" },
        { "checkbox", "check box" },
        { "colorchooser", "color chooser" },
        { "columnheader", "column header" },
        { "combobox", "combo box" },
        { "canvas", "canvas" },
        { "desktopicon", "desktop icon" },
        { "desktoppane", "desktop pane" },
        { "dialog", "dialog" },
        { "directorypane", "directory pane" },
        { "glasspane", "glass pane" },
        { "filechooser", "file chooser" },
        { "filler", "filler" },
        { "frame", "frame" },
        { "internalframe", "internal frame" },
        { "label", "label" },
        { "layeredpane", "layered pane" },
        { "list", "list" },
        { "listitem", "list item" },
        { "menubar", "menu bar" },
        { "menu", "menu" },
        { "menuitem", "menu item" },
        { "optionpane", "option pane" },
        { "pagetab", "page tab" },
        { "pagetablist", "page tab list" },
        { "panel", "panel" },
        { "passwordtext", "password text" },
        { "popupmenu", "popup menu" },
        { "progressbar", "progress bar" },
        { "pushbutton", "push button" },
        { "radiobutton", "radio button" },
        { "rootpane", "root pane" },
        { "rowheader", "row header" },
        { "scrollbar", "scroll bar" },
        { "scrollpane", "scroll pane" },
        { "separator", "separator" },
        { "slider", "slider" },
        { "splitpane", "split pane" },
        { "swingcomponent", "swing component" },
        { "table", "table" },
        { "text", "text" },
        { "tree", "tree" },
        { "togglebutton", "toggle button" },
        { "toolbar", "tool bar" },
        { "tooltip", "tool tip" },
        { "unknown", "unknown" },
        { "viewport", "viewport" },
        { "window", "window" },
        { "hyperlink", "hyperlink" },
        { "icon", "icon" },
        { "htmlcontainer", "HTML container" },
        { "statusbar", "status bar" },
        { "dateeditor", "date editor" },
        { "spinbox", "spin box" },
        { "fontchooser", "font chooser" },
        { "groupbox", "group box" },
        { "header", "header" },
        { "footer", "footer" },
        { "paragraph", "paragraph" },
        { "ruler", "ruler" },
        { "editbar", "editbar" },
        { "progressMonitor", "progress monitor" },
        // relations
        { "labelFor", "label for" },
        { "labeledBy", "labeled by" },
        { "memberOf", "member of" },
        { "controlledBy", "controlledBy" },
        { "controllerFor", "controllerFor" },
        { "flowsTo", "flows to" },
        { "flowsFrom", "flows from" },
        { "subwindowOf", "subwindow of" },
        { "parentWindowOf", "parent window of" },
        { "embeds", "embeds" },
        { "embeddedBy", "embedded by" },
        { "childNodeOf", "child node of" },
        // states
        { "active", "active" },
        { "armed", "armed" },
        { "busy", "busy" },
        { "checked", "checked" },
        { "collapsed", "collapsed" },
        { "editable", "editable" },
        { "expandable", "expandable" },
        { "expanded", "expanded" },
        { "enabled", "enabled" },
        { "focusable", "focusable" },
        { "focused", "focused" },
        { "iconified", "iconified" },
        { "modal", "modal" },
        { "multiline", "multiple line" },
        { "multiselectable", "multiselectable" },
        { "opaque", "opaque" },
        { "pressed", "pressed" },
        { "resizable", "resizable" },
        { "selectable", "selectable" },
        { "selected", "selected" },
        { "showing", "showing" },
        { "singleline", "single line" },
        { "transient", "transient" },
        { "visible", "visible" },
        { "vertical", "vertical" },
        { "horizontal", "horizontal" },
        { "managesDescendants", "manages descendants" },
        { "indeterminate", "indeterminate" },
        { "truncated", "truncated" },
        // actions
        { "toggleexpand", "toggle expand" },
    };

    private static final Map<String, String> TABLE = new HashMap<String, String>();
    static {
        for (Object[] entry : CONTENTS) {
            TABLE.put((String) entry[0], (String) entry[1]);
        }
    }

    /** The display string of {@code key}, or the key itself when the table has none. */
    static String displayString(String key) {
        String s = key == null ? null : TABLE.get(key);
        return s != null ? s : key;
    }

    public Object[][] getContents() {
        return CONTENTS;
    }
}
