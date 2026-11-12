// host-contract
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import javax.swing.plaf.basic.*;

/** Shared desktop/Android contracts for Plugin Hub scrollbar and popup delegates. */
public class TestSwingSelectionControls {
    private static void check(boolean pass, String label) {
        if (!pass) throw new AssertionError(label);
    }
    static class BarUI extends BasicScrollBarUI {
        int tracks, thumbs;
        @Override protected JButton createIncreaseButton(int orientation) { return zero(); }
        @Override protected JButton createDecreaseButton(int orientation) { return zero(); }
        private JButton zero() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            return button;
        }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            tracks++;
            g.setColor(Color.BLUE); g.fillRect(r.x, r.y, r.width, r.height);
        }
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            thumbs++;
            g.setColor(Color.RED); g.fillRect(r.x, r.y, r.width, r.height);
        }
        Rectangle thumb() { return new Rectangle(getThumbBounds()); }
    }
    private static void paint(JComponent c) {
        Graphics g = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_ARGB).getGraphics();
        try { c.paint(g); } finally { g.dispose(); }
    }
    static class ComboUI extends BasicComboBoxUI {
        JList<?> rows;
        int paints;
        @Override protected ComboPopup createPopup() {
            ComboPopup result = new BasicComboPopup(comboBox) {
                @Override protected void configureList() {
                    super.configureList();
                    list.setSelectionBackground(Color.ORANGE);
                    list.setSelectionForeground(Color.BLUE);
                    list.setFixedCellHeight(30);
                }
                @Override protected JScrollPane createScroller() {
                    JScrollPane pane = super.createScroller();
                    pane.getVerticalScrollBar().setUI(new BarUI());
                    return pane;
                }
            };
            rows = result.getList();
            return result;
        }
        @Override public void paint(Graphics g, JComponent c) { paints++; super.paint(g, c); }
    }
    private static void verify() {
        JPanel content = new JPanel();
        content.setPreferredSize(new Dimension(100, 800));
        JScrollPane pane = new JScrollPane(content);
        JScrollBar bar = pane.getVerticalScrollBar();
        BarUI styled = new BarUI();
        bar.setPreferredSize(new Dimension(12, 0));
        bar.setUI(styled);
        pane.setSize(180, 120); pane.doLayout(); pane.getViewport().doLayout();
        check(bar.isVisible() && bar.getWidth() == 12, "custom bar occupies a visible slot");
        bar.doLayout(); paint(bar);
        Rectangle before = styled.thumb();
        bar.setValue(90); bar.doLayout(); paint(bar);
        check(styled.tracks > 0 && styled.thumbs > 0 && styled.thumb().y > before.y, "custom thumb follows model");
        check(pane.getViewport().getViewPosition().y == 90, "bar scrolls the viewport");
        Rectangle thumb = styled.thumb();
        int x = thumb.x + thumb.width / 2, y = thumb.y + thumb.height / 2;
        bar.dispatchEvent(new MouseEvent(bar, MouseEvent.MOUSE_PRESSED, 1, InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1));
        bar.dispatchEvent(new MouseEvent(bar, MouseEvent.MOUSE_DRAGGED, 2, InputEvent.BUTTON1_DOWN_MASK, x, y + 10, 0, false, MouseEvent.NOBUTTON));
        bar.dispatchEvent(new MouseEvent(bar, MouseEvent.MOUSE_RELEASED, 3, 0, x, y + 10, 1, false, MouseEvent.BUTTON1));
        check(bar.getValue() > 90 && !bar.getValueIsAdjusting() && pane.getViewport().getViewPosition().y == bar.getValue(), "dragging a thumb moves the viewport and releases the model");
        bar.setUI(new BarUI());
        check(bar.getComponentCount() == 2, "replacing delegate removes old arrow buttons");
        pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER); pane.doLayout();
        check(!bar.isVisible(), "never policy");

        JComboBox<String> combo = new JComboBox<>(new String[]{"one", "two"});
        ComboUI ui = new ComboUI();
        combo.setUI(ui); combo.setSize(160, 30); combo.doLayout(); paint(combo);
        check(ui.paints > 0 && ui.rows.getFixedCellHeight() == 30, "custom combo and popup hooks");
        DefaultListCellRenderer renderer = new DefaultListCellRenderer();
        Component selected = renderer.getListCellRendererComponent(ui.rows, "two", 1, true, false);
        check(Color.ORANGE.equals(selected.getBackground()) && Color.BLUE.equals(selected.getForeground()), "selection colours reach renderer");
        combo.setModel(new DefaultComboBoxModel<>(new String[]{"new", "replacement"}));
        check(ui.rows.getModel() == combo.getModel(), "new model reaches popup");
        combo.setSelectedIndex(1);
        check("replacement".equals(combo.getSelectedItem()), "selection uses current model");
        JList<String> sized = new JList<>(new String[]{"a", "b"});
        sized.setCellRenderer((l, v, i, s, f) -> {
            JLabel label = new JLabel(v);
            label.setPreferredSize(new Dimension(53, 19));
            return label;
        });
        sized.setPrototypeCellValue("representative");
        check(sized.getFixedCellWidth() == 53 && sized.getFixedCellHeight() == 19, "prototype determines cell size");
        combo.setLightWeightPopupEnabled(false);
        check(!combo.isLightWeightPopupEnabled(), "combo popup weight hint");
        JTextField search = new JTextField();
        search.getAccessibleContext().setAccessibleName("Locations");
        check("Locations".equals(search.getAccessibleContext().getAccessibleName()), "accessible name survives reads");
    }
    public static void main(String[] args) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> { try { verify(); } catch (Throwable error) { failure.set(error); } });
        if (failure.get() != null) throw new AssertionError(failure.get());
        System.out.println("PASS selection controls");
    }
}
