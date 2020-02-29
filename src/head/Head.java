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

    private int[][][] cthead;
    private float[][][] pixels;
    private float[] equaliseMap;

    private float scaleFactor;
    private int currentSlice;
    private int maxSlice;
    private View currentView;
    private boolean mip;
    private boolean equalised;
    private boolean bilinear;

    private int max;
    private int min;
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
        bilinear = false;
        setZoom(scaleFactor);
        setView(View.TOP);
        updateImageDimensions();
        refresh();
    }

    private int[][][] load(String fname) {
        try {
            File file = new File(fname);
            DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
            cthead = new int[Z_LEN][Y_LEN][X_LEN];
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

    private void loadThumbnails() {

    }

    private float[] equalise(int[][][] cthead) {
        int len = max - min + 1;
        int[] hist = new int[len];
        for(int z = 0; z < Z_LEN; z++) {
            for (int y = 0; y < Y_LEN; y++) {
                for (int x = 0; x < X_LEN; x++) {
                    int index = cthead[z][y][x] - min;
                    hist[index]++;
                }
            }
        }
        float[] cumSum = new float[len];
        int t = hist[0];
        cumSum[0] = (float) t / (Z_LEN * Y_LEN * X_LEN);
        for (int i = 1; i < len; i++) {
            t += hist[i];
            cumSum[i] = (float) t / (Z_LEN * Y_LEN * X_LEN);
        }
        return cumSum;
    }

    private float[][][] mapPixels(boolean useEqualisation) {
        float[][][] pixels = new float[Z_LEN][Y_LEN][X_LEN];
        for (int z = 0; z < Z_LEN; z++){
            for (int y = 0; y < Y_LEN; y++) {
                for (int x = 0; x < X_LEN; x++) {
                    int datum = cthead[z][y][x];
                    if (useEqualisation) {
                        pixels[z][y][x] = equaliseMap[cthead[z][y][x] - min];
                    } else {
                        pixels[z][y][x] = mapToPixelRange(datum);
                    }
                }
            }
        }
        return pixels;
    }

    private float mapToPixelRange(int val) {
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

    private void drawAt(int x, int y, float val) {
        drawAt(x, y, val, image);
    }

    private void drawAt(int x, int y, float val, WritableImage img){
        Color c = new Color(val, val, val, 1);
        img.getPixelWriter().setColor(x, y, c);
    }

    private float pixelAt(int b, int a, int i, View view){
        if(mip) {
            return mipPixelAt(b, a, view);
        }

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

    private float resizedPixelAt(int b, int a, int i, View view) {
        return resizedPixelAt(b, a, i, view, scaleFactor);
    }

    private float resizedPixelAt(int b, int a, int i, View view, float sf){
        if (bilinear) {
            return resizedPixelAtBL(b, a, i, view, sf);
        } else {
            return resizedPixelAtNN(b, a, i, view, sf);
        }
    }

    private float resizedPixelAtNN(int b, int a, int i, View view, float sf) {
        int newB = Math.round(b/sf);
        int newA = Math.round(a/sf);
        return pixelAt(newB, newA, i, view);
    }

    private float resizedPixelAtBL(int b, int a, int i, View view, float sf) {
        float newB = b / sf;
        float  newA = a / sf;

        if(b == newB && a == newA) {
            return resizedPixelAtNN(b, a, i, view, sf);
        }

        int minB = (int) Math.floor(newB);
        int minA = (int) Math.floor(newA);
        int maxB = minB + 1;
        int maxA = minA + 1;

        double topVal = lerp(pixelAt(minB, minA, i, view),
                pixelAt(minB, maxA, i, view),
                minA, newA, maxA);
        double botVal = lerp(pixelAt(maxB, minA, i, view),
                pixelAt(maxB, maxA, i, view),
                minA, newA, maxA);
        double finVal = lerp(topVal, botVal, minB, newB, maxB);

        return (float) finVal;
    }

    public double lerp(double v1, double v2, int p1, double p, int p2) {
        double ratio = (p - p1) / (p2 - p1);
        return v1 + (v2 - v1) * ratio;
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
        slice(currentSlice);
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

    public void toggleBilinear() {
        bilinear = !bilinear;
    }

    public Image getImage() {
        return image;
    }

    public Image getThumbnailImage() {
        final float THUMBNAIL_SF = 0.5f;
        final int GAP = 5; //how many slices between thumbnails;
        final int N = 3; // how many thumbnails
        int thumbXLen = -1;
        int thumbYLen = -1;

        switch (currentView) {
            case TOP:
                thumbXLen = (int) (X_LEN * THUMBNAIL_SF);
                thumbYLen = (int) (Y_LEN * THUMBNAIL_SF);
                break;
            case FRONT:
                thumbXLen = (int) (X_LEN * THUMBNAIL_SF);
                thumbYLen = (int) (Z_LEN * THUMBNAIL_SF);
                break;
            case SIDE:
                thumbXLen = (int) (Y_LEN * THUMBNAIL_SF);
                thumbYLen = (int) (Z_LEN * THUMBNAIL_SF);
                break;
        }

        int imageX = thumbXLen * ((N * 2) + 1);
        int imageY = thumbYLen;
        WritableImage carousel = new WritableImage(imageX, imageY);
        int startSlice = Math.max(0, currentSlice - (N * GAP));
        int endSlice = Math.min(maxSlice, currentSlice + (N * GAP));
        for(int slice = startSlice; slice <= endSlice; slice+=GAP) {
            for (int y = 0; y < thumbYLen; y++){
                for (int x = 0; x < thumbXLen; x++){
                    float pix = resizedPixelAt(y, x, slice, currentView, THUMBNAIL_SF);
                    int offset = ((slice - startSlice) / GAP) * thumbXLen;
                    drawAt(x + offset, y, pix, carousel);
                }
            }
        }
        return carousel;
    }

    public int getMaxSlice() {
        return maxSlice;
    }

}
