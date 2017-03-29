package net.corda.demobench.getinput;

import java.awt.event.KeyEvent;
import java.io.*;

public class GetInput {

    public static void main(String[] args) throws IOException {
        System.out.println("READING ('ESC,ENTER' to Quit):");
        try (InputStream input = new FileInputStream(FileDescriptor.in)) {
            for (;;) {
                int c = input.read();
                if ((c == -1) || (c == KeyEvent.VK_ESCAPE)) {
                    break;
                }
                if (c != KeyEvent.VK_ENTER) {
                    System.out.printf("> 0x%04x (%c)\n", c, c);
                }
            }
        }
    }

}

