import java.awt.*;
import javax.swing.*;

public class TestProfileLayout {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static JButton button(String name, int w, int h) {
        JButton b = new JButton(name); b.setPreferredSize(new Dimension(w,h));
        b.setMinimumSize(new Dimension(w,h)); b.setMaximumSize(new Dimension(w,h)); return b;
    }
    public static void main(String[] args) {
        JPanel panel = new JPanel(); panel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        JButton create = button("New",90,24), imp = button("Import",90,24);
        JPanel profiles = new JPanel(); profiles.setPreferredSize(new Dimension(188,30));
        JLabel info = new JLabel("Info"); info.setPreferredSize(new Dimension(188,30));
        GroupLayout g = new GroupLayout(panel); panel.setLayout(g);
        g.setHorizontalGroup(g.createParallelGroup().addComponent(profiles)
            .addGroup(g.createSequentialGroup().addComponent(create).addGap(8).addComponent(imp)).addComponent(info));
        g.setVerticalGroup(g.createSequentialGroup().addComponent(profiles,30,30,30).addGap(8)
            .addGroup(g.createParallelGroup().addComponent(create).addComponent(imp)).addGap(8).addComponent(info,30,30,30));
        panel.setSize(208,120); panel.doLayout();
        check(create.getY() == imp.getY(), "buttons must share a row");
        check(imp.getX() == create.getX()+create.getWidth()+8, "button gap");
        check(profiles.getX()==10 && profiles.getY()==10, "panel insets");
        check(info.getY()==80, "nested vertical groups");
        JPanel card = new JPanel(); GroupLayout c = new GroupLayout(card); card.setLayout(c);
        JTextField name = new JTextField(); name.setPreferredSize(new Dimension(160,24)); name.setMinimumSize(new Dimension(0,24));
        JPanel actions = new JPanel(); actions.setPreferredSize(new Dimension(160,24));
        JButton arrow = button(">",20,24);
        c.setHorizontalGroup(c.createSequentialGroup().addGap(4).addGroup(c.createParallelGroup().addComponent(name).addComponent(actions)).addComponent(arrow));
        c.setVerticalGroup(c.createParallelGroup().addGroup(c.createSequentialGroup().addComponent(name,24,24,24).addComponent(actions,24,24,24))
            .addComponent(arrow,GroupLayout.DEFAULT_SIZE,GroupLayout.DEFAULT_SIZE,Short.MAX_VALUE));
        actions.setVisible(false); card.setSize(200,24); card.doLayout();
        check(arrow.getX()==180 && name.getWidth()==176, "right arrow placement");
        check(card.getPreferredSize().height==24, "collapsed actions take no space");
        actions.setVisible(true); card.setSize(200,48); card.doLayout();
        check(actions.getY()==24 && arrow.getHeight()==48, "expanded row geometry");
        System.out.println("Profile group layout tests passed");
    }
}
