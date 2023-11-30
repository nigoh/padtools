package padtools.editor;


import javax.swing.*;
import java.io.File;

public class Editor {
    public static void openEditor(final File file) {
        System.setProperty("apple.laf.useScreenMenuBar", "true");

        try{
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch(UnsupportedLookAndFeelException ex){}
        catch(ClassNotFoundException ex){}
        catch(InstantiationException ex){}
        catch(IllegalAccessException ex){}

        java.awt.EventQueue.invokeLater(new Runnable() {

            public void run() {
                JFrame.setDefaultLookAndFeelDecorated(true);
                MainFrame frame = new MainFrame(file);

                frame.setSize(800, 600);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            }
        });
    }


}
