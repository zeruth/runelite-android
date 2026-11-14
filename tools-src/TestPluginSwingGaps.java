// host-contract
import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import javax.swing.*;
import javax.swing.table.*;
import javax.swing.text.*;

public class TestPluginSwingGaps {
    static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    public static void main(String[] args) throws Exception {
        DefaultTableModel model=new DefaultTableModel(new Object[][]{{"c",3},{"a",1},{"b",2}},new Object[]{"name","n"});
        TableRowSorter<TableModel> sorter=new TableRowSorter<>(model);
        JTable table=new JTable(model);table.setRowSorter(sorter);
        sorter.toggleSortOrder(0);
        check("a".equals(table.getValueAt(0,0)),"sorted table read");
        check(table.convertRowIndexToModel(0)==1,"view to model");
        table.setValueAt("z",0,0);sorter.sort();
        check("z".equals(model.getValueAt(1,0)),"sorted table edit");
        sorter.setRowFilter(RowFilter.regexFilter("b",0));
        check(table.getRowCount()==1 && "b".equals(table.getValueAt(0,0)),"filtered row count and read");
        check(table.rowAtPoint(new Point(0,-100))==-1 && table.rowAtPoint(new Point(0,1000))==-1,"row hit bounds");
        Calendar cal=Calendar.getInstance();cal.clear();cal.set(2024,Calendar.JANUARY,31);
        SpinnerDateModel dates=new SpinnerDateModel(cal.getTime(),null,null,Calendar.MONTH);
        cal.setTime((Date)dates.getNextValue());check(cal.get(Calendar.DAY_OF_MONTH)==29,"calendar month rollover");
        dates.setEnd((Date)dates.getValue());check(dates.getNextValue()==null,"date bound");
        JSlider slider=new JSlider(0,100,50);int[] events={0};slider.addChangeListener(e->events[0]++);
        slider.setValue(200);check(slider.getValue()==100 && events[0]==1,"slider clamp and change");
        slider.setValue(100);check(events[0]==1,"no redundant event");
        slider.setValueIsAdjusting(true);check(slider.getValueIsAdjusting(),"adjusting state");
        DefaultStyledDocument doc=new DefaultStyledDocument();SimpleAttributeSet bold=new SimpleAttributeSet();StyleConstants.setBold(bold,true);
        doc.insertString(0,"hello",bold);doc.insertString(0,"x",null);doc.remove(0,1);
        check("hello".equals(doc.getText(0,5)),"styled document edits");
        bold.removeAttribute(StyleConstants.Bold);
        check(StyleConstants.isBold(doc.getCharacterElement(0).getAttributes()),"attribute snapshot");
        RoundRectangle2D shape=new RoundRectangle2D.Float(0,0,100,100,40,40);
        check(shape.contains(50,50) && !shape.contains(0,0),"rounded corners");
        PathIterator path=shape.getPathIterator(null,.1);int count=0;double[] p=new double[6];
        while(!path.isDone()){int type=path.currentSegment(p);check(type!=PathIterator.SEG_CUBICTO && type!=PathIterator.SEG_QUADTO,"flatten curves");count++;path.next();}
        check(count>10,"flatten resolution");
        System.out.println("PASS: sorting, filtering, dates, sliders, styled text, geometry");
    }
}
