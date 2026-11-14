// host-contract
import java.awt.Dimension;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** A tree mutation and size query must not acquire the tree/layout monitors in opposite order. */
public final class TestLayoutLockContracts {
    public static void main(String[] args) {
        try {
            for (String mode : new String[]{"minimum", "preferred", "maximum"}) check(mode);
            System.out.println("PASS minimum/preferred/maximum layout queries respect the tree lock");
        } catch (Throwable error) { error.printStackTrace(System.out); System.exit(1); }
    }

    private static void check(String mode) throws Exception {
        JPanel panel = new JPanel();
        panel.setUI(null);
        BoxLayout layout = new BoxLayout(panel, BoxLayout.Y_AXIS);
        panel.setLayout(layout);
        panel.setSize(20, 20);
        CountDownLatch treeHeld = new CountDownLatch(1), queryStarted = new CountDownLatch(1), measuring = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        panel.add(new JComponent() {
            private Dimension measureLeaf() {
                measuring.countDown();
                synchronized (getTreeLock()) { return new Dimension(20, 20); }
            }
            @Override public Dimension getMinimumSize() { return measureLeaf(); }
            @Override public Dimension getPreferredSize() { return measureLeaf(); }
            @Override public Dimension getMaximumSize() { return measureLeaf(); }
        });
        Thread mutate = new Thread(() -> {
            try {
                synchronized (panel.getTreeLock()) {
                    treeHeld.countDown();
                    if (!queryStarted.await(3, TimeUnit.SECONDS)) throw new AssertionError("Size query never started");
                    // Desktop's query waits outside the layout monitor while the tree is locked.
                    // An inverted query enters the layout and then waits for this tree lock.
                    measuring.await(300, TimeUnit.MILLISECONDS);
                    layout.invalidateLayout(panel);
                }
            } catch (Throwable failure) { error.set(failure); }
        }, "layout-tree-owner");
        Thread query = new Thread(() -> {
            try {
                if (!treeHeld.await(3, TimeUnit.SECONDS)) throw new AssertionError("Tree owner never started");
                queryStarted.countDown();
                Dimension result = mode.equals("minimum") ? panel.getMinimumSize()
                    : mode.equals("preferred") ? panel.getPreferredSize() : panel.getMaximumSize();
                if (!result.equals(new Dimension(20, 20))) throw new AssertionError("Unexpected size: " + result);
            } catch (Throwable failure) { error.set(failure); }
        }, "layout-size-query");
        mutate.setDaemon(true); query.setDaemon(true);
        mutate.start(); query.start();
        mutate.join(3000); query.join(3000);
        if (mutate.isAlive() || query.isAlive()) throw new AssertionError(mode + " tree/layout lock inversion: "
            + mutate.getState() + "/" + query.getState());
        if (error.get() != null) throw new AssertionError(mode, error.get());
        System.out.println("PASS " + mode + " concurrent mutation/measurement");
    }
}
