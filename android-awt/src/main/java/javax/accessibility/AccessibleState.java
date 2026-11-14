package javax.accessibility;

/**
 * The strongly typed enumeration of an accessible object's states, compared by identity.
 */
public class AccessibleState extends AccessibleBundle {

    public static final AccessibleState ACTIVE = new AccessibleState("active");
    public static final AccessibleState PRESSED = new AccessibleState("pressed");
    public static final AccessibleState ARMED = new AccessibleState("armed");
    public static final AccessibleState BUSY = new AccessibleState("busy");
    public static final AccessibleState CHECKED = new AccessibleState("checked");
    public static final AccessibleState EDITABLE = new AccessibleState("editable");
    public static final AccessibleState EXPANDABLE = new AccessibleState("expandable");
    public static final AccessibleState COLLAPSED = new AccessibleState("collapsed");
    public static final AccessibleState EXPANDED = new AccessibleState("expanded");
    public static final AccessibleState ENABLED = new AccessibleState("enabled");
    public static final AccessibleState FOCUSABLE = new AccessibleState("focusable");
    public static final AccessibleState FOCUSED = new AccessibleState("focused");
    public static final AccessibleState ICONIFIED = new AccessibleState("iconified");
    public static final AccessibleState MODAL = new AccessibleState("modal");
    public static final AccessibleState OPAQUE = new AccessibleState("opaque");
    public static final AccessibleState RESIZABLE = new AccessibleState("resizable");
    public static final AccessibleState MULTISELECTABLE = new AccessibleState("multiselectable");
    public static final AccessibleState SELECTABLE = new AccessibleState("selectable");
    public static final AccessibleState SELECTED = new AccessibleState("selected");
    public static final AccessibleState SHOWING = new AccessibleState("showing");
    public static final AccessibleState VISIBLE = new AccessibleState("visible");
    public static final AccessibleState VERTICAL = new AccessibleState("vertical");
    public static final AccessibleState HORIZONTAL = new AccessibleState("horizontal");
    public static final AccessibleState SINGLE_LINE = new AccessibleState("singleline");
    public static final AccessibleState MULTI_LINE = new AccessibleState("multiline");
    public static final AccessibleState TRANSIENT = new AccessibleState("transient");
    public static final AccessibleState MANAGES_DESCENDANTS = new AccessibleState("managesDescendants");
    public static final AccessibleState INDETERMINATE = new AccessibleState("indeterminate");
    public static final AccessibleState TRUNCATED = new AccessibleState("truncated");

    /**
     * Creates a new AccessibleState using the given locale independent key. Use only the
     * predefined constants or a subclass's own.
     */
    protected AccessibleState(String key) {
        this.key = key;
    }
}
