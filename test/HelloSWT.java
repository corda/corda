import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Label;

public class HelloSWT {
  public static void main (String[] args) {
    Display display = new Display();
    final Shell shell = new Shell(display);
    RowLayout layout = new RowLayout();
    layout.justify = true;
    layout.pack = true;
    shell.setLayout(layout);
    shell.setText("Hello, World!");
    Label label = new Label(shell, SWT.CENTER);
    label.setText("Hello, world!");
    shell.pack();
    shell.open();
    while (!shell.isDisposed()) {
      if (!display.readAndDispatch()) display.sleep();
    }
    display.dispose();
  }
}
