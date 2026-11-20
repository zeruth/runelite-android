// host-contract
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.View;

/** Word-wrapping labels must not push sibling controls outside narrow panels. */
public class TestHtmlLabelLayoutContracts {
    private static int checks;
    private static void check(boolean ok,String name) { if(!ok)throw new AssertionError(name);checks++; }
    private static void layout(Container c) { c.doLayout();for(Component child:c.getComponents())if(child instanceof Container)layout((Container)child); }
    private static final String HELP="<html>Profiles are separate sets of plugins and settings that you can switch between at any time.";
    public static void main(String[] args) {
        JButton marginButton=new JButton("New Profile");
        check(marginButton.getInsets().equals(marginButton.getBorder().getBorderInsets(marginButton)),"button border supplies insets once");
        marginButton.setMargin(new Insets(2,14,2,14));int initialWidth=marginButton.getPreferredSize().width;
        marginButton.setMargin(new Insets(2,24,2,24));
        check(marginButton.getPreferredSize().width-initialWidth==20,"button margin contributes once");
        marginButton.setBorder(new EmptyBorder(1,3,2,4));
        check(marginButton.getInsets().equals(new Insets(1,3,2,4)),"custom button border owns insets");
        JLabel html=new JLabel(HELP);
        check(html.getMinimumSize().width<html.getPreferredSize().width,"HTML minimum allows word wrapping");
        int small=html.getMinimumSize().width;
        html.setBorder(new EmptyBorder(0,8,0,9));
        check(html.getMinimumSize().width>=small+17,"HTML minimum includes insets");
        html.setMinimumSize(new Dimension(111,33));
        check(html.getMinimumSize().equals(new Dimension(111,33)),"explicit minimum wins");
        JLabel plain=new JLabel("Plain text stays plain");
        check(plain.getMinimumSize().equals(plain.getPreferredSize()),"plain label sizing unchanged");
        html=new JLabel();html.putClientProperty("html.disable",Boolean.TRUE);html.setText(HELP);
        check(html.getMinimumSize().equals(html.getPreferredSize()),"disabled HTML stays plain");
        JLabel styled=new JLabel("<html>un<span style='color:red'>break</span>able short");
        check(styled.getMinimumSize().width<styled.getPreferredSize().width,"styled HTML minimum wraps");
        JLabel icon=new JLabel(HELP,new ImageIcon(new BufferedImage(70,20,BufferedImage.TYPE_INT_ARGB)),SwingConstants.LEFT);
        check(icon.getMinimumSize().width>=70,"HTML icon preserved in minimum");
        icon.setVerticalTextPosition(SwingConstants.BOTTOM);icon.setHorizontalTextPosition(SwingConstants.CENTER);
        check(icon.getMinimumSize().height>=20,"stacked HTML icon height preserved");
        JEditorPane editor=new JEditorPane("text/html",HELP);
        View v=editor.getUI().getRootView(editor).getView(0);
        check(v.getMinimumSpan(View.X_AXIS)<v.getPreferredSpan(View.X_AXIS),"HTML View minimum wraps");
        for(int width:new int[]{300,360}) {
            JPanel panel=new JPanel();panel.setBorder(new EmptyBorder(10,10,10,10));
            GroupLayout group=new GroupLayout(panel);panel.setLayout(group);
            JPanel list=new JPanel(new GridLayout(0,1));
            for(int i=0;i<4;i++){JPanel card=new JPanel();card.setPreferredSize(new Dimension(227,30));list.add(card);}
            JButton add=new JButton("New Profile"),imp=new JButton("Import Profile");JLabel info=new JLabel(HELP);
            group.setVerticalGroup(group.createSequentialGroup().addComponent(list).addGap(8)
                .addGroup(group.createParallelGroup().addComponent(add).addComponent(imp)).addGap(8).addComponent(info));
            group.setHorizontalGroup(group.createParallelGroup().addComponent(list)
                .addGroup(group.createSequentialGroup().addComponent(add).addGap(8).addComponent(imp)).addComponent(info));
            panel.setSize(width,260);layout(panel);
            check(list.getX()+list.getWidth()<=width-10,"profile list stays inside panel "+width);
            check(imp.getX()+imp.getWidth()<=width-10,"profile buttons stay inside panel "+width);
            check(info.getX()+info.getWidth()<=width-10,"HTML help stays inside panel "+width);
            BufferedImage image=new BufferedImage(width,260,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();panel.paint(g);g.dispose();
            list.getComponent(0).setPreferredSize(new Dimension(227,60));panel.invalidate();layout(panel);
            check(list.getX()+list.getWidth()<=width-10,"expanded profile stays inside panel "+width);
        }
        System.out.println("PASS HTML label layout: "+checks+" checks");
    }
}
