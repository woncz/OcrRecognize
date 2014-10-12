package test;

import java.io.File;

import net.sourceforge.tess4j.*;

public class AutoLoginTest {

    public static void main(String[] args) {
        File imageFile = new File("E:\\code\\data\\pmo.bmp");
        Tesseract instance = Tesseract.getInstance(); // JNA Interface Mapping
        // Tesseract1 instance = new Tesseract1(); // JNA Direct Mapping

        try {
        	String result = instance.doOCR(imageFile);
        	System.out.println(result);
        } catch (TesseractException e) {
            System.err.println(e.getMessage());
        }
    }
}

