package javax.swing;

import javax.swing.event.ChangeListener;

public class JProgressBar extends JComponent implements SwingConstants {
    private static final long serialVersionUID = 1L;
    protected BoundedRangeModel model = new DefaultBoundedRangeModel();
    protected int orientation = HORIZONTAL;
    protected boolean stringPainted;
    protected boolean indeterminate;
    protected String progressString;
    private boolean borderPainted = true;

    private final javax.swing.event.EventListenerList listeners=new javax.swing.event.EventListenerList();
    private final ChangeListener modelListener=e->{javax.swing.event.ChangeEvent event=new javax.swing.event.ChangeEvent(this);for(ChangeListener l:getChangeListeners())l.stateChanged(event);repaint();};
    public JProgressBar() { model.addChangeListener(modelListener); }
    public JProgressBar(int orient) { this();setOrientation(orient); }
    public JProgressBar(int min, int max) { this(); setMinimum(min); setMaximum(max); }
    public JProgressBar(int orient, int min, int max) { this(orient); setMinimum(min); setMaximum(max); }
    public JProgressBar(BoundedRangeModel newModel) { this();setModel(newModel); }

    public int getOrientation() { return orientation; }
    public void setOrientation(int o) { if(o!=HORIZONTAL&&o!=VERTICAL)throw new IllegalArgumentException("orientation");int old=orientation;orientation=o;firePropertyChange("orientation",old,o);revalidate();repaint(); }
    public int getValue() { return model.getValue(); }
    public void setValue(int n) { model.setValue(n); }
    public int getMinimum() { return model.getMinimum(); }
    public void setMinimum(int n) { model.setMinimum(n); }
    public int getMaximum() { return model.getMaximum(); }
    public void setMaximum(int n) { model.setMaximum(n); }
    public boolean isStringPainted() { return stringPainted; }
    public void setStringPainted(boolean b) { stringPainted = b; }
    public boolean isBorderPainted() { return borderPainted; }
    public void setBorderPainted(boolean b) { borderPainted = b; }
    public String getString() { return progressString; }
    public void setString(String s) { progressString = s; }
    public boolean isIndeterminate() { return indeterminate; }
    public void setIndeterminate(boolean b) { indeterminate = b; }
    public BoundedRangeModel getModel() { return model; }
    public void setModel(BoundedRangeModel m) { java.util.Objects.requireNonNull(m);BoundedRangeModel old=model;old.removeChangeListener(modelListener);model=m;m.addChangeListener(modelListener);firePropertyChange("model",old,m);repaint(); }
    public double getPercentComplete() {
        double range = model.getMaximum() - model.getMinimum();
        return range == 0 ? 0 : (model.getValue() - model.getMinimum()) / range;
    }
    public void addChangeListener(ChangeListener l) { listeners.add(ChangeListener.class,l); }
    public void removeChangeListener(ChangeListener l) { listeners.remove(ChangeListener.class,l); }
    public ChangeListener[] getChangeListeners() { return listeners.getListeners(ChangeListener.class); }
    public void setUI(javax.swing.plaf.ProgressBarUI ui) { super.setUI(ui); }
    public javax.swing.plaf.ProgressBarUI getUI() { return (javax.swing.plaf.ProgressBarUI) super.getUI(); }

    /**
     * Draw the bar ourselves — real Swing delegates to ProgressBarUI but we don't ship
     * a working UI delegate, so without this the splash's bar stays invisible (just an
     * empty rect between the action and subAction labels). Horizontal fill only; this
     * is what the SplashScreen + a couple of plugin status overlays need.
     */
    @Override
    protected void paintComponent(java.awt.Graphics g) {
        if(paintDelegate(g))return;
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        java.awt.Color bg = getBackground();
        if (bg != null) {
            g.setColor(bg);
            g.fillRect(0, 0, w, h);
        }
        double pct = getPercentComplete();
        if (pct > 0) {
            int fillW = (int) Math.round(w * Math.max(0, Math.min(1, pct)));
            java.awt.Color fg = getForeground();
            if (fg != null) {
                g.setColor(fg);
                if(orientation==HORIZONTAL)g.fillRect(0,0,fillW,h);else{int fillH=(int)Math.round(h*Math.max(0,Math.min(1,pct)));g.fillRect(0,h-fillH,w,fillH);}
            }
        }
        if (stringPainted) {
            String s = progressString != null
                ? progressString
                : ((int) (getPercentComplete() * 100)) + "%";
            java.awt.Font f = getFont();
            if (f != null) g.setFont(f);
            java.awt.FontMetrics fm = g.getFontMetrics();
            int tx = (w - fm.stringWidth(s)) / 2;
            int ty = (h + fm.getAscent() - fm.getDescent()) / 2;
            g.setColor(java.awt.Color.BLACK);
            g.drawString(s, tx, ty);
        }
    }
}
