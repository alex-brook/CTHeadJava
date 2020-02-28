package head;

import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.io.*;
import java.nio.ByteBuffer;

public class Head {
    public enum View {
        TOP("Transverse"),
        FRONT("Coronal"),
        SIDE("Sagittal");

        private String label;

        View(String label) {
            this.label = label;
        }
        public String getLabel() {
            return label;
        }
        public static View valueOfLabel(String label) {
            for (View e: values()) {
                if (e.getLabel().equals(label)) {
                    return e;
                }
            }
            return null;
        }
    }

    public static final float MAX_SF = 4.0f;
    public static final float MIN_SF = 0.5f;

    private static final int Z_LEN = 113;
    private static final int Y_LEN = 256;
    private static final int X_LEN = 256;

    private short[][][] cthead;
    private float[][][] pixels;
    private short[] equaliseMap;

    private float scaleFactor;
    private int currentSlice;
    private int maxSlice;
    private View currentView;
    private boolean mip;
    private boolean equalised;

    private short max;
    private short min;
    private WritableImage image;
    private int imageXLen;
    private int imageYLen;

    public Head(String fname, float scaleFactor) {
        this.scaleFactor = scaleFactor;
        this.currentSlice = 0;
        mip = false;

        cthead = load(fname);
        equaliseMap = equalise(cthead);
        equalised = false;
        pixels = mapPixels(equalised);
        setZoom(scaleFactor);
        setView(View.TOP);
        updateImageDimensions();
        refresh();
    }

    public short[][][] load(String fname) {
        try {
            File file = new File(fname);
            DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
            cthead = new short[Z_LEN][Y_LEN][X_LEN];
            min = Short.MAX_VALUE;
            max = Short.MIN_VALUE;

            for (int z = 0; z < Z_LEN; z++) {
                for (int y = 0; y < Y_LEN; y++) {
                    for (int x = 0; x < X_LEN; x++) {
                        byte b1 = in.readByte();
                        byte b2 = in.readByte();
                        short s = ByteBuffer.wrap(new byte[]{b2, b1}).getShort();
                        cthead[z][y][x] = s;
                        if (s < min) {
                            min = s;
                        }
                        if (s > max) {
                            max = s;
                        }
                    }
                }
            }
            return cthead;
        } catch (IOException e) {
            System.err.println("Could not load cthead data");
            System.err.println(e);
            return null;
        }
    }

    private short[] equalise(short[][][] cthead) {
        int len = max - min + 1;
        short[] hist = new short[len];
        for(int z = 0; z < Z_LEN; z++) {
            for (int y = 0; y < Y_LEN; y++) {
                for (int x = 0; x < X_LEN; x++) {
                    hist[cthead[z][y][x] - min]++;
                }
            }
        }

        short[] cumDist = new short[len];
        short t = hist[0];
        cumDist[0] = t;
        for (int i = 1; i < len; i++) {
            t += hist[i];
        }
        return cumDist;
    }

    private float[][][] mapPixels(boolean useEqualisation) {
        float[][][] pixels = new float[Z_LEN][Y_LEN][X_LEN];
        for (int z = 0; z < Z_LEN; z++){
            for (int y = 0; y < Y_LEN; y++) {
                for (int x = 0; x < X_LEN; x++) {
                    short datum = cthead[z][y][x];
                    if (useEqualisation) {
                        datum -= min;
                    }
                    pixels[z][y][x] = mapToPixelRange(datum);
                }
            }
        }
        return pixels;
    }

    private float mapToPixelRange(short val) {
        float res = ((float)val-(float)min)/((float)(max-min));
        return Math.min(res, 1);
    }

    public void slice(int i){
        currentSlice = i;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                drawAt(x, y, resizedPixelAt(y, x, i, currentView));
            }
        }
    }

    public void drawMip() {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                drawAt(x, y, resizedMipPixelAt(y, x, currentView));
            }
        }
    }


    private void drawAt(int x, int y, float val) {
        Color c = new Color(val, val, val, 1);
        image.getPixelWriter().setColor(x, y, c);
    }

    private float pixelAt(int b, int a, int i, View view){
        switch (view) {
            case TOP:
                b = Math.min(b, Y_LEN - 1);
                a = Math.min(a, X_LEN - 1);
                return pixels[i][b][a];
            case FRONT:
                b = Math.min(b, Z_LEN - 1);
                a = Math.min(a, X_LEN - 1);
                return pixels[b][i][a];
            case SIDE:
                b = Math.min(b, Z_LEN - 1);
                a = Math.min(a, Y_LEN - 1);
                return pixels[b][a][i];
        }
        return -1;
    }


    private float resizedPixelAt(int b, int a, int i, View view){
        return resizedPixelAtNN(b, a, i, view);
    }

    private float resizedPixelAtNN(int b, int a, int i, View view) {
        int newB = Math.round(b/scaleFactor);
        int newA = Math.round(a/scaleFactor);
        return pixelAt(newB, newA, i, view);
    }

    private float resizedPixelAtBL(int b, int a, int i, View view) {
        return 0;
    }

    private float lerp(int v1, int v2, int p1, int p, int p2) {
        return 0;
    }

    private  float resizedMipPixelAt(int b, int a, View view) {
        int newB = Math.round(b/scaleFactor);
        int newA = Math.round(a/scaleFactor);
        return mipPixelAt(newB, newA, view);
    }

    private float mipPixelAt(int b, int a, View view) {
        float mipMax = Float.MIN_VALUE;
        switch (view) {
            case TOP:
                b = Math.min(b, Y_LEN - 1);
                a = Math.min(a, X_LEN - 1);
                for(int i = 0; i < Z_LEN; i++) {
                    if (pixels[i][b][a] > mipMax) {
                        mipMax = pixels[i][b][a];
                    }
                }
                break;
            case FRONT:
                b = Math.min(b, Z_LEN - 1);
                a = Math.min(a, X_LEN - 1);
                for (int i = 0; i < Y_LEN; i++) {
                    if (pixels[b][i][a] > mipMax) {
                        mipMax = pixels[b][i][a];
                    }
                }
                break;
            case SIDE:
                b = Math.min(b, Z_LEN - 1);
                a = Math.min(a, Y_LEN - 1);
                for (int i = 0; i < X_LEN; i++) {
                    if (pixels[b][a][i] > mipMax) {
                        mipMax = pixels[b][a][i];
                    }
                }
        }
        return mipMax;
    }

    public void refresh() {
        if (mip) {
            drawMip();
        } else {
            slice(currentSlice);
        }
    }

    public void updateImageDimensions() {
        switch (currentView) {
            case TOP:
                imageXLen = (int) (X_LEN * scaleFactor);
                imageYLen = (int) (Y_LEN * scaleFactor);
                break;
            case FRONT:
                imageXLen = (int) (X_LEN * scaleFactor);
                imageYLen = (int) (Z_LEN * scaleFactor);
                break;
            case SIDE:
                imageXLen = (int) (Y_LEN * scaleFactor);
                imageYLen = (int) (Z_LEN * scaleFactor);
                break;
        }
        image = new WritableImage(imageXLen, imageYLen);
    }

    public void setView(View view){
        currentView = view;
        switch (view){
            case TOP:
                maxSlice = Z_LEN - 1;
                break;
            case FRONT:
                maxSlice = Y_LEN - 1;
                break;
            case SIDE:
                maxSlice = X_LEN - 1;
                break;
        }
        currentSlice = Math.min(currentSlice, maxSlice);
    }

    public void setZoom(float sf) {
        scaleFactor = Math.max(MIN_SF, Math.min(sf, MAX_SF));
    }

    public void toggleMip() {
        mip = !mip;
    }

    public void toggleEqualisation() {
        equalised = !equalised;
        pixels = mapPixels(equalised);
    }

    public Image getImage() {
        return image;
    }
    public int getMaxSlice() {
        return maxSlice;
    }

}
