// host-contract
import java.awt.Point;
import java.beans.BeanInfo;
import java.beans.EventSetDescriptor;
import java.beans.ExceptionListener;
import java.beans.Expression;
import java.beans.IndexedPropertyDescriptor;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.beans.PropertyDescriptor;
import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.beans.PropertyVetoException;
import java.beans.Statement;
import java.beans.VetoableChangeListener;
import java.beans.VetoableChangeSupport;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JButton;

/**
 * java.beans contracts: Introspector analysis of a sample bean, descriptors, vetoable changes,
 * the built-in property editors, Statement/Expression, and XMLEncoder/XMLDecoder round trips
 * (a Point, a JButton's text, a list, a bean) plus decoding a hand-written JDK-format archive.
 */
public final class TestBeansContracts {
    private static int failures;
    private static int total;

    private static void check(String name, boolean actual) {
        total++;
        if (!actual) failures++;
        System.out.println((actual ? "PASS " : "FAIL ") + name);
    }

    /** A bean with bound, constrained, boolean, read-only and indexed properties. */
    public static class Sample {
        private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
        private String name;
        private int count;
        private boolean active;
        private int[] values = new int[0];

        public Sample() {
        }

        public String getName() { return name; }
        public void setName(String name) { String old = this.name; this.name = name; pcs.firePropertyChange("name", old, name); }
        public int getCount() { return count; }
        public void setCount(int count) throws PropertyVetoException { this.count = count; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        public String getReadOnly() { return "r"; }
        public int[] getValues() { return values.clone(); }
        public void setValues(int[] values) { this.values = values.clone(); }
        public int getValues(int i) { return values[i]; }
        public void setValues(int i, int v) { values[i] = v; }
        public void addPropertyChangeListener(PropertyChangeListener l) { pcs.addPropertyChangeListener(l); }
        public void removePropertyChangeListener(PropertyChangeListener l) { pcs.removePropertyChangeListener(l); }
    }

    private static PropertyDescriptor property(BeanInfo info, String name) {
        for (PropertyDescriptor pd : info.getPropertyDescriptors()) {
            if (pd.getName().equals(name)) return pd;
        }
        return null;
    }

    private static final List<Exception> encoderErrors = new ArrayList<>();
    private static final ExceptionListener QUIET = new ExceptionListener() {
        public void exceptionThrown(Exception e) {
            encoderErrors.add(e);
        }
    };

    private static byte[] encode(Object... objects) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        XMLEncoder encoder = new XMLEncoder(bytes);
        encoder.setExceptionListener(QUIET);
        for (Object o : objects) encoder.writeObject(o);
        encoder.close();
        return bytes.toByteArray();
    }

    private static List<Object> decode(byte[] xml, int count) {
        XMLDecoder decoder = new XMLDecoder(new ByteArrayInputStream(xml), null, QUIET);
        List<Object> result = new ArrayList<>();
        for (int i = 0; i < count; i++) result.add(decoder.readObject());
        decoder.close();
        return result;
    }

    private static void introspector() throws Exception {
        BeanInfo info = Introspector.getBeanInfo(Sample.class, Object.class);
        List<String> names = new ArrayList<>();
        for (PropertyDescriptor pd : info.getPropertyDescriptors()) names.add(pd.getName());
        check("properties sorted by name below Object", names.equals(Arrays.asList("active", "count", "name", "readOnly", "values")));

        PropertyDescriptor name = property(info, "name");
        check("getter/setter pair", name != null && name.getReadMethod().getName().equals("getName")
            && name.getWriteMethod().getName().equals("setName") && name.getPropertyType() == String.class);
        check("bound when addPropertyChangeListener exists", name != null && name.isBound());
        PropertyDescriptor count = property(info, "count");
        check("constrained when setter throws PropertyVetoException", count != null && count.isConstrained()
            && count.getPropertyType() == int.class);
        PropertyDescriptor active = property(info, "active");
        check("is-getter for boolean", active != null && active.getReadMethod().getName().equals("isActive"));
        PropertyDescriptor readOnly = property(info, "readOnly");
        check("read-only property", readOnly != null && readOnly.getWriteMethod() == null);
        PropertyDescriptor values = property(info, "values");
        check("indexed property", values instanceof IndexedPropertyDescriptor
            && ((IndexedPropertyDescriptor) values).getIndexedPropertyType() == int.class
            && ((IndexedPropertyDescriptor) values).getIndexedReadMethod() != null
            && values.getPropertyType() == int[].class);

        boolean propertyChangeEvent = false;
        for (EventSetDescriptor esd : info.getEventSetDescriptors()) {
            if (esd.getName().equals("propertyChange") && esd.getListenerType() == PropertyChangeListener.class
                    && esd.getAddListenerMethod().getName().equals("addPropertyChangeListener")) {
                propertyChangeEvent = true;
            }
        }
        check("propertyChange event set", propertyChangeEvent);
        check("class property above the stop class", property(Introspector.getBeanInfo(Sample.class), "class") != null
            && property(info, "class") == null);
        check("bean descriptor names the class", info.getBeanDescriptor().getBeanClass() == Sample.class
            && info.getBeanDescriptor().getName().equals("TestBeansContracts$Sample"));

        boolean threw = false;
        try {
            Introspector.getBeanInfo(Object.class, Sample.class);
        } catch (IntrospectionException e) {
            threw = true;
        }
        check("stop class must be a superclass", threw);

        check("decapitalize", Introspector.decapitalize("FooBah").equals("fooBah")
            && Introspector.decapitalize("URL").equals("URL") && Introspector.decapitalize("X").equals("x")
            && Introspector.decapitalize("").equals("") && Introspector.decapitalize(null) == null);

        PropertyDescriptor byName = new PropertyDescriptor("name", Sample.class);
        check("PropertyDescriptor(name, class)", byName.getReadMethod().getName().equals("getName")
            && byName.getWriteMethod().getName().equals("setName"));
        check("PropertyDescriptor finds is-getter", new PropertyDescriptor("active", Sample.class)
            .getReadMethod().getName().equals("isActive"));
        threw = false;
        try {
            new PropertyDescriptor("missing", Sample.class);
        } catch (IntrospectionException e) {
            threw = true;
        }
        check("missing property throws IntrospectionException", threw);
        check("descriptor equality", byName.equals(new PropertyDescriptor("name", Sample.class)));

        byName.setValue("transient", Boolean.TRUE);
        check("feature attributes", Boolean.TRUE.equals(byName.getValue("transient"))
            && byName.attributeNames().hasMoreElements() && byName.getDisplayName().equals("name")
            && byName.getShortDescription().equals("name"));
    }

    private static void vetoes() {
        final Object source = new Object();
        VetoableChangeSupport support = new VetoableChangeSupport(source);
        final List<PropertyChangeEvent> seen = new ArrayList<>();
        support.addVetoableChangeListener(new VetoableChangeListener() {
            public void vetoableChange(PropertyChangeEvent evt) {
                seen.add(evt);
            }
        });
        support.addVetoableChangeListener("size", new VetoableChangeListener() {
            public void vetoableChange(PropertyChangeEvent evt) throws PropertyVetoException {
                if (Integer.valueOf(13).equals(evt.getNewValue())) throw new PropertyVetoException("no", evt);
            }
        });
        boolean vetoed = false;
        try {
            support.fireVetoableChange("size", 1, 13);
        } catch (PropertyVetoException e) {
            vetoed = e.getPropertyChangeEvent().getNewValue().equals(13);
        }
        check("veto is rethrown", vetoed);
        check("veto reverts the listeners already notified", seen.size() == 2
            && seen.get(1).getOldValue().equals(13) && seen.get(1).getNewValue().equals(1)
            && seen.get(1).getSource() == source);
        seen.clear();
        try {
            support.fireVetoableChange("size", 2, 2);
            support.fireVetoableChange("other", 1, 13);
        } catch (PropertyVetoException e) {
            seen.add(null);
        }
        check("equal values are not fired, named listeners only see their property", seen.size() == 1
            && seen.get(0) != null);
        check("listener lists", support.getVetoableChangeListeners().length == 2
            && support.getVetoableChangeListeners("size").length == 1 && support.hasListeners("x"));
    }

    private static void editors() {
        PropertyEditor ints = PropertyEditorManager.findEditor(int.class);
        check("int editor", ints != null);
        if (ints != null) {
            ints.setAsText("42");
            check("int editor parses", Integer.valueOf(42).equals(ints.getValue())
                && "42".equals(ints.getAsText()) && "42".equals(ints.getJavaInitializationString()));
        }
        PropertyEditor bools = PropertyEditorManager.findEditor(boolean.class);
        check("boolean editor tags", bools != null && Arrays.asList(bools.getTags()).equals(Arrays.asList("True", "False")));
        check("string editor", PropertyEditorManager.findEditor(String.class) != null);
        check("no editor for an arbitrary bean", PropertyEditorManager.findEditor(Sample.class) == null);
    }

    private static void statements() throws Exception {
        check("expression invokes the getter", Double.valueOf(1.0).equals(
            new Expression(new Point(1, 2), "getX", null).getValue()));
        int[] array = new int[2];
        new Statement(array, "set", new Object[] {1, 5}).execute();
        check("array set statement", array[1] == 5
            && Integer.valueOf(5).equals(new Expression(array, "get", new Object[] {1}).getValue()));
        check("constructor expression", new Point(3, 4).equals(
            new Expression(Point.class, "new", new Object[] {3, 4}).getValue()));
        check("static method expression", "5".equals(
            new Expression(String.class, "valueOf", new Object[] {5}).getValue()));
        Expression bound = new Expression("v", null, "ignored", null);
        check("expression with a preset value", "v".equals(bound.getValue()));
    }

    private static void xml() throws Exception {
        byte[] point = encode(new Point(3, 4));
        String text = new String(point, StandardCharsets.UTF_8);
        check("encoded archive names the class", text.contains("java.awt.Point") && text.contains("<java "));
        check("Point round-trips", new Point(3, 4).equals(decode(point, 1).get(0)));

        JButton button = new JButton();
        button.setText("Hi & <there>");
        Object decoded = decode(encode(button), 1).get(0);
        check("JButton text round-trips", decoded instanceof JButton
            && "Hi & <there>".equals(((JButton) decoded).getText()));

        List<Object> list = new ArrayList<>(Arrays.<Object>asList("a", 7, null));
        check("list round-trips", list.equals(decode(encode(list), 1).get(0)));

        Sample sample = new Sample();
        sample.setName("bean");
        sample.setCount(3);
        sample.setActive(true);
        List<Object> two = decode(encode(sample, "second"), 2);
        Object first = two.get(0);
        check("bean round-trips", first instanceof Sample && "bean".equals(((Sample) first).getName())
            && ((Sample) first).getCount() == 3 && ((Sample) first).isActive());
        check("objects are read back in order", "second".equals(two.get(1)));

        String archive = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<java version=\"1.8.0\" class=\"java.beans.XMLDecoder\">\n"
            + " <object class=\"java.util.ArrayList\">\n"
            + "  <void method=\"add\"><string>a</string></void>\n"
            + "  <void method=\"add\"><int>7</int></void>\n"
            + " </object>\n"
            + " <array class=\"int\" length=\"2\"><void index=\"1\"><int>9</int></void></array>\n"
            + " <object class=\"java.awt.Point\" id=\"p\"><int>1</int><int>2</int></object>\n"
            + " <object idref=\"p\"/>\n"
            + " <object class=\"java.lang.Enum\" method=\"valueOf\">"
            + "<class>java.lang.Thread$State</class><string>NEW</string></object>\n"
            + "</java>\n";
        List<Object> read = decode(archive.getBytes(StandardCharsets.UTF_8), 5);
        check("decodes method statements", Arrays.asList("a", 7).equals(read.get(0)));
        check("decodes arrays with index statements", read.get(1) instanceof int[]
            && Arrays.equals((int[]) read.get(1), new int[] {0, 9}));
        check("decodes constructors and idrefs", new Point(1, 2).equals(read.get(2)) && read.get(2) == read.get(3));
        check("decodes static methods", read.get(4) == Thread.State.NEW);

        XMLDecoder empty = new XMLDecoder(new ByteArrayInputStream(encode()), null, QUIET);
        boolean exhausted = false;
        try {
            empty.readObject();
        } catch (ArrayIndexOutOfBoundsException e) {
            exhausted = true;
        }
        empty.close();
        check("reading past the end throws ArrayIndexOutOfBoundsException", exhausted);
    }

    public static void main(String[] args) throws Exception {
        introspector();
        vetoes();
        editors();
        statements();
        xml();
        System.out.println(total - failures + "/" + total + " passed");
        if (failures != 0) {
            System.exit(1);
        }
    }
}
