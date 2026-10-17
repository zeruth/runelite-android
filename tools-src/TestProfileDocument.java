import javax.swing.text.*;
public class TestProfileDocument {
 public static void main(String[] args) throws Exception {
  AbstractDocument doc = new PlainDocument();
  final int[] calls = {0};
  doc.setDocumentFilter(new DocumentFilter() {
   public void insertString(FilterBypass fb, int offset, String text, AttributeSet attrs) throws BadLocationException {
    calls[0]++; super.insertString(fb, offset, text.replace("/", ""), attrs);
   }
   public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
    calls[0]++; super.replace(fb, offset, length, text.replace("/", ""), attrs);
   }
  });
  doc.insertString(0, "Test/Profile", null);
  if (!doc.getText(0,doc.getLength()).equals("TestProfile")) throw new AssertionError("insert filter");
  doc.replace(0, doc.getLength(), "New/Name", null);
  if (!doc.getText(0,doc.getLength()).equals("NewName") || calls[0] != 2) throw new AssertionError("replace bypass");
  doc.remove(0, doc.getLength());
  if (doc.getLength() != 0) throw new AssertionError("remove bypass");
  System.out.println("Profile document inheritance and filter tests passed");
 }
}
