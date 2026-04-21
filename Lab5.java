import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;

/**
 * Лабораторная работа №5: Цветовые пространства и обработка цветных изображений.
 *
 * Основное задание:
 *   1. Конвертация RGB↔HSV, RGB↔YCbCr(BT.601), RGB↔CIELab(D65) + RMSE верификация
 *   2. Визуализация каналов (отдельно H,S,V / Y,Cb,Cr / L,a,b)
 *   3. Сравнение эквализации гистограммы: RGB, HSV(V), YCbCr(Y)
 *
 * Дополнительные задания:
 *   4. Коррекция баланса белого (метод серого мира)
 *   5. Цветовая квантизация (k-means, k=4,8,16)
 *   6. Сдвиг тона (в HSV) на +60°, +120°, +180°
 *   7. Хромакей (удаление зелёного фона)
 */
public class Lab5 {

    // ── Вспомогательные функции ──────────────────────────────────────────────────

    static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    static int clampRound(double v) {
        return clamp((int) Math.round(v));
    }

    static void save(BufferedImage img, String path) throws IOException {
        new File(path).getParentFile().mkdirs();
        ImageIO.write(img, "png", new File(path));
    }

    /** Читает изображение и конвертирует в TYPE_INT_RGB. */
    static BufferedImage loadRGB(String path) throws IOException {
        BufferedImage src = ImageIO.read(new File(path));
        if (src == null) throw new IOException("Cannot read: " + path);
        if (src.getType() == BufferedImage.TYPE_INT_RGB) return src;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    /**
     * Строит горизонтальную полосу сравнения из нескольких изображений.
     * Все изображения масштабируются до одной высоты (первого изображения).
     */
    static BufferedImage makeRow(String title, String[] labels, BufferedImage[] imgs) {
        int H = imgs[0].getHeight();
        int W = imgs[0].getWidth();
        int labelH = 20;
        int titleH = 24;
        int n = imgs.length;
        int totalW = W * n;
        int totalH = H + labelH + titleH;

        BufferedImage row = new BufferedImage(totalW, totalH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = row.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, totalW, totalH);

        // Заголовок
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(title);
        g.drawString(title, (totalW - tw) / 2, titleH - 5);

        // Изображения и подписи
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        fm = g.getFontMetrics();
        for (int i = 0; i < n; i++) {
            // Масштабируем если нужно
            BufferedImage img = imgs[i];
            if (img.getWidth() != W || img.getHeight() != H) {
                BufferedImage scaled = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
                Graphics2D gs = scaled.createGraphics();
                gs.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                gs.drawImage(img, 0, 0, W, H, null);
                gs.dispose();
                img = scaled;
            }
            g.drawImage(img, i * W, titleH, null);
            // Подпись под изображением
            if (labels != null && i < labels.length) {
                String lbl = labels[i];
                int lw = fm.stringWidth(lbl);
                g.setColor(Color.BLACK);
                g.drawString(lbl, i * W + (W - lw) / 2, titleH + H + labelH - 4);
            }
        }
        g.dispose();
        return row;
    }

    // ── Серое изображение из массива double[] (нормализация в [0,255]) ───────────

    static BufferedImage grayFromDoubleNorm(double[] data, int w, int h, double lo, double hi) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        double range = (hi - lo) < 1e-12 ? 1.0 : (hi - lo);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int v = clamp((int) Math.round((data[y * w + x] - lo) / range * 255.0));
                img.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        return img;
    }

    // ── RGB ↔ HSV ────────────────────────────────────────────────────────────────

    /** Возвращает {H [0,360), S [0,1], V [0,1]} */
    static double[] rgbToHSV(int r, int g, int b) {
        double rv = r / 255.0, gv = g / 255.0, bv = b / 255.0;
        double M = Math.max(rv, Math.max(gv, bv));
        double m = Math.min(rv, Math.min(gv, bv));
        double C = M - m;
        double V = M;
        double S = (M > 0) ? C / M : 0.0;
        double H;
        if (C < 1e-10) {
            H = 0;
        } else if (M == rv) {
            H = 60.0 * (((gv - bv) / C) % 6.0);
        } else if (M == gv) {
            H = 60.0 * ((bv - rv) / C + 2.0);
        } else {
            H = 60.0 * ((rv - gv) / C + 4.0);
        }
        if (H < 0) H += 360.0;
        return new double[]{H, S, V};
    }

    static int[] hsvToRGB(double H, double S, double V) {
        double Cv = V * S;
        double Hmod = H / 60.0;
        double X = Cv * (1.0 - Math.abs(Hmod % 2.0 - 1.0));
        double m = V - Cv;
        double rp, gp, bp;
        int sec = (int) Hmod % 6;
        // Handle H==360 edge
        if (H >= 360.0) sec = 0;
        switch (sec) {
            case 0: rp = Cv; gp = X;  bp = 0;  break;
            case 1: rp = X;  gp = Cv; bp = 0;  break;
            case 2: rp = 0;  gp = Cv; bp = X;  break;
            case 3: rp = 0;  gp = X;  bp = Cv; break;
            case 4: rp = X;  gp = 0;  bp = Cv; break;
            default:rp = Cv; gp = 0;  bp = X;  break;
        }
        return new int[]{
            clampRound((rp + m) * 255.0),
            clampRound((gp + m) * 255.0),
            clampRound((bp + m) * 255.0)
        };
    }

    // ── RGB ↔ YCbCr (BT.601) ────────────────────────────────────────────────────

    /** Returns {Y, Cb, Cr} as doubles (Y in [0,255], Cb/Cr in [0,255] with 128 offset) */
    static double[] rgbToYCbCr(int r, int g, int b) {
        double Y  =  0.299    * r + 0.587    * g + 0.114    * b;
        double Cb = -0.168736 * r - 0.331264 * g + 0.500    * b + 128.0;
        double Cr =  0.500    * r - 0.418688 * g - 0.081312 * b + 128.0;
        return new double[]{Y, Cb, Cr};
    }

    static int[] ycbcrToRGB(double Y, double Cb, double Cr) {
        double R = Y + 1.402    * (Cr - 128.0);
        double G = Y - 0.344136 * (Cb - 128.0) - 0.714136 * (Cr - 128.0);
        double B = Y + 1.772    * (Cb - 128.0);
        return new int[]{clampRound(R), clampRound(G), clampRound(B)};
    }

    // ── RGB ↔ CIELab (D65) ──────────────────────────────────────────────────────

    static final double Xn = 0.95047, Yn = 1.0, Zn = 1.08883;

    static double srgbLinearize(double v) {
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    static double srgbGamma(double v) {
        if (v <= 0.0) return 0.0;
        return v <= 0.0031308 ? 12.92 * v : 1.055 * Math.pow(v, 1.0 / 2.4) - 0.055;
    }

    static double labF(double t) {
        return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16.0 / 116.0;
    }

    static double labInvF(double t) {
        return t > 0.20689655 ? t * t * t : (t - 16.0 / 116.0) / 7.787;
    }

    /** Returns {L, a, b} */
    static double[] rgbToLab(int r, int g, int b) {
        double rl = srgbLinearize(r / 255.0);
        double gl = srgbLinearize(g / 255.0);
        double bl = srgbLinearize(b / 255.0);
        double X = 0.4124564 * rl + 0.3575761 * gl + 0.1804375 * bl;
        double Y = 0.2126729 * rl + 0.7151522 * gl + 0.0721750 * bl;
        double Z = 0.0193339 * rl + 0.1191920 * gl + 0.9503041 * bl;
        double fx = labF(X / Xn), fy = labF(Y / Yn), fz = labF(Z / Zn);
        double L = 116.0 * fy - 16.0;
        double a = 500.0 * (fx - fy);
        double bv = 200.0 * (fy - fz);
        return new double[]{L, a, bv};
    }

    static int[] labToRGB(double L, double a, double bv) {
        double fy = (L + 16.0) / 116.0;
        double fx = a / 500.0 + fy;
        double fz = fy - bv / 200.0;
        double X = labInvF(fx) * Xn;
        double Y = labInvF(fy) * Yn;
        double Z = labInvF(fz) * Zn;
        double rl =  3.2404542 * X - 1.5371385 * Y - 0.4985314 * Z;
        double gl = -0.9692660 * X + 1.8760108 * Y + 0.0415560 * Z;
        double bl =  0.0556434 * X - 0.2040259 * Y + 1.0572252 * Z;
        // Clamp linear before gamma to handle slight out-of-gamut
        rl = Math.max(0.0, rl);
        gl = Math.max(0.0, gl);
        bl = Math.max(0.0, bl);
        return new int[]{
            clampRound(srgbGamma(rl) * 255.0),
            clampRound(srgbGamma(gl) * 255.0),
            clampRound(srgbGamma(bl) * 255.0)
        };
    }

    // ── RMSE ────────────────────────────────────────────────────────────────────

    static double rmse(BufferedImage a, BufferedImage b) {
        int w = a.getWidth(), h = a.getHeight();
        double sum = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int ca = a.getRGB(x, y), cb = b.getRGB(x, y);
                int dr = ((ca >> 16) & 0xFF) - ((cb >> 16) & 0xFF);
                int dg = ((ca >> 8)  & 0xFF) - ((cb >> 8)  & 0xFF);
                int db = (ca & 0xFF) - (cb & 0xFF);
                sum += dr * dr + dg * dg + db * db;
            }
        return Math.sqrt(sum / (3.0 * w * h));
    }

    // ── Канальные визуализации ───────────────────────────────────────────────────

    /** Создаёт серое изображение из одного канала RGB (0=R,1=G,2=B). */
    static BufferedImage extractRGBChannel(BufferedImage img, int ch) {
        int w = img.getWidth(), h = img.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int v = (ch == 0) ? (rgb >> 16) & 0xFF : (ch == 1) ? (rgb >> 8) & 0xFF : rgb & 0xFF;
                out.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        return out;
    }

    static BufferedImage[] hsvChannelImages(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        double[] H = new double[w * h], S = new double[w * h], V = new double[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                double[] hsv = rgbToHSV((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
                H[y * w + x] = hsv[0];
                S[y * w + x] = hsv[1];
                V[y * w + x] = hsv[2];
            }
        return new BufferedImage[]{
            grayFromDoubleNorm(H, w, h, 0, 360),
            grayFromDoubleNorm(S, w, h, 0, 1),
            grayFromDoubleNorm(V, w, h, 0, 1)
        };
    }

    static BufferedImage[] ycbcrChannelImages(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        double[] Y = new double[w * h], Cb = new double[w * h], Cr = new double[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                double[] ycbcr = rgbToYCbCr((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
                Y[y * w + x]  = ycbcr[0];
                Cb[y * w + x] = ycbcr[1];
                Cr[y * w + x] = ycbcr[2];
            }
        return new BufferedImage[]{
            grayFromDoubleNorm(Y,  w, h, 0, 255),
            grayFromDoubleNorm(Cb, w, h, 0, 255),
            grayFromDoubleNorm(Cr, w, h, 0, 255)
        };
    }

    static BufferedImage[] labChannelImages(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        double[] L = new double[w * h], A = new double[w * h], B = new double[w * h];
        double Lmin = Double.MAX_VALUE, Lmax = -Double.MAX_VALUE;
        double Amin = Double.MAX_VALUE, Amax = -Double.MAX_VALUE;
        double Bmin = Double.MAX_VALUE, Bmax = -Double.MAX_VALUE;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                double[] lab = rgbToLab((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
                L[y * w + x] = lab[0]; A[y * w + x] = lab[1]; B[y * w + x] = lab[2];
                if (lab[0] < Lmin) Lmin = lab[0]; if (lab[0] > Lmax) Lmax = lab[0];
                if (lab[1] < Amin) Amin = lab[1]; if (lab[1] > Amax) Amax = lab[1];
                if (lab[2] < Bmin) Bmin = lab[2]; if (lab[2] > Bmax) Bmax = lab[2];
            }
        return new BufferedImage[]{
            grayFromDoubleNorm(L, w, h, Lmin, Lmax),
            grayFromDoubleNorm(A, w, h, Amin, Amax),
            grayFromDoubleNorm(B, w, h, Bmin, Bmax)
        };
    }

    // ── Эквализация гистограммы ──────────────────────────────────────────────────

    /** Эквализация одного канала (значения 0..255). */
    static int[] equalizeChannel(int[] vals) {
        int N = vals.length;
        int[] hist = new int[256];
        for (int v : vals) hist[v]++;
        int[] cdf = new int[256];
        cdf[0] = hist[0];
        for (int i = 1; i < 256; i++) cdf[i] = cdf[i - 1] + hist[i];
        int cdfMin = 0;
        for (int i = 0; i < 256; i++) { if (cdf[i] > 0) { cdfMin = cdf[i]; break; } }
        int[] lut = new int[256];
        for (int i = 0; i < 256; i++) {
            lut[i] = (N == cdfMin) ? 0 : clamp((int) Math.round((double)(cdf[i] - cdfMin) / (N - cdfMin) * 255.0));
        }
        int[] out = new int[N];
        for (int i = 0; i < N; i++) out[i] = lut[vals[i]];
        return out;
    }

    /** Эквализация RGB — каждый канал независимо. */
    static BufferedImage histEqRGB(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int N = w * h;
        int[] R = new int[N], G = new int[N], B = new int[N];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                R[y * w + x] = (rgb >> 16) & 0xFF;
                G[y * w + x] = (rgb >> 8)  & 0xFF;
                B[y * w + x] = rgb & 0xFF;
            }
        R = equalizeChannel(R); G = equalizeChannel(G); B = equalizeChannel(B);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                out.setRGB(x, y, (R[y*w+x] << 16) | (G[y*w+x] << 8) | B[y*w+x]);
        return out;
    }

    /** Эквализация только канала V в HSV. */
    static BufferedImage histEqHSV_V(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int N = w * h;
        double[][] hsvData = new double[N][3];
        int[] Vints = new int[N];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                double[] hsv = rgbToHSV((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
                hsvData[y * w + x] = hsv;
                Vints[y * w + x] = clamp((int) Math.round(hsv[2] * 255.0));
            }
        int[] Veq = equalizeChannel(Vints);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double[] hsv = hsvData[y * w + x];
                double Veqd = Veq[y * w + x] / 255.0;
                int[] rgb = hsvToRGB(hsv[0], hsv[1], Veqd);
                out.setRGB(x, y, (rgb[0] << 16) | (rgb[1] << 8) | rgb[2]);
            }
        return out;
    }

    /** Эквализация только канала Y в YCbCr. */
    static BufferedImage histEqYCbCr_Y(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int N = w * h;
        double[][] ycbcrData = new double[N][3];
        int[] Yints = new int[N];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                double[] ycbcr = rgbToYCbCr((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
                ycbcrData[y * w + x] = ycbcr;
                Yints[y * w + x] = clamp((int) Math.round(ycbcr[0]));
            }
        int[] Yeq = equalizeChannel(Yints);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double[] ycbcr = ycbcrData[y * w + x];
                int[] rgb = ycbcrToRGB((double) Yeq[y * w + x], ycbcr[1], ycbcr[2]);
                out.setRGB(x, y, (rgb[0] << 16) | (rgb[1] << 8) | rgb[2]);
            }
        return out;
    }

    // ── Баланс белого (метод серого мира) ────────────────────────────────────────

    static BufferedImage grayWorldWhiteBalance(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        long sumR = 0, sumG = 0, sumB = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                sumR += (rgb >> 16) & 0xFF;
                sumG += (rgb >> 8)  & 0xFF;
                sumB += rgb & 0xFF;
            }
        int N = w * h;
        double meanR = sumR / (double) N;
        double meanG = sumG / (double) N;
        double meanB = sumB / (double) N;
        double meanAll = (meanR + meanG + meanB) / 3.0;
        double kR = (meanR > 0) ? meanAll / meanR : 1.0;
        double kG = (meanG > 0) ? meanAll / meanG : 1.0;
        double kB = (meanB > 0) ? meanAll / meanB : 1.0;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int R = clampRound(((rgb >> 16) & 0xFF) * kR);
                int G = clampRound(((rgb >> 8)  & 0xFF) * kG);
                int B = clampRound((rgb & 0xFF)          * kB);
                out.setRGB(x, y, (R << 16) | (G << 8) | B);
            }
        return out;
    }

    // ── Цветовая квантизация (k-means) ───────────────────────────────────────────

    static BufferedImage kmeansQuantize(BufferedImage img, int k, int maxIter, long seed) {
        int w = img.getWidth(), h = img.getHeight();
        int N = w * h;
        int[] pixels = new int[N];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                pixels[y * w + x] = img.getRGB(x, y);

        // Инициализация k центров случайными пикселями
        Random rng = new Random(seed);
        double[][] centers = new double[k][3];
        Set<Integer> chosen = new HashSet<>();
        int ci = 0;
        while (ci < k) {
            int idx = rng.nextInt(N);
            if (chosen.add(idx)) {
                int rgb = pixels[idx];
                centers[ci][0] = (rgb >> 16) & 0xFF;
                centers[ci][1] = (rgb >> 8)  & 0xFF;
                centers[ci][2] = rgb & 0xFF;
                ci++;
            }
        }

        int[] assign = new int[N];
        Arrays.fill(assign, -1);

        for (int iter = 0; iter < maxIter; iter++) {
            boolean changed = false;
            // Назначение
            for (int i = 0; i < N; i++) {
                int rgb = pixels[i];
                int R = (rgb >> 16) & 0xFF;
                int G = (rgb >> 8)  & 0xFF;
                int B = rgb & 0xFF;
                int best = 0;
                double bestDist = Double.MAX_VALUE;
                for (int j = 0; j < k; j++) {
                    double dR = R - centers[j][0];
                    double dG = G - centers[j][1];
                    double dB = B - centers[j][2];
                    double dist = dR * dR + dG * dG + dB * dB;
                    if (dist < bestDist) { bestDist = dist; best = j; }
                }
                if (assign[i] != best) { assign[i] = best; changed = true; }
            }
            if (!changed) break;
            // Обновление центров
            double[][] newC = new double[k][3];
            int[] cnt = new int[k];
            for (int i = 0; i < N; i++) {
                int j = assign[i];
                int rgb = pixels[i];
                newC[j][0] += (rgb >> 16) & 0xFF;
                newC[j][1] += (rgb >> 8)  & 0xFF;
                newC[j][2] += rgb & 0xFF;
                cnt[j]++;
            }
            for (int j = 0; j < k; j++) {
                if (cnt[j] > 0) {
                    centers[j][0] = newC[j][0] / cnt[j];
                    centers[j][1] = newC[j][1] / cnt[j];
                    centers[j][2] = newC[j][2] / cnt[j];
                }
            }
        }

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < N; i++) {
            int j = assign[i];
            int R = clampRound(centers[j][0]);
            int G = clampRound(centers[j][1]);
            int B = clampRound(centers[j][2]);
            out.setRGB(i % w, i / w, (R << 16) | (G << 8) | B);
        }
        return out;
    }

    // ── Сдвиг тона (HSV) ────────────────────────────────────────────────────────

    static BufferedImage hueShift(BufferedImage img, double deltaDeg) {
        int w = img.getWidth(), h = img.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                double[] hsv = rgbToHSV((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
                double H = (hsv[0] + deltaDeg) % 360.0;
                if (H < 0) H += 360.0;
                int[] nrgb = hsvToRGB(H, hsv[1], hsv[2]);
                out.setRGB(x, y, (nrgb[0] << 16) | (nrgb[1] << 8) | nrgb[2]);
            }
        return out;
    }

    // ── Хромакей ─────────────────────────────────────────────────────────────────

    /**
     * Удаляет пиксели с оттенком около targetHue ± hueTol (в градусах),
     * насыщенностью > satThresh, заменяет на replaceRGB.
     */
    static BufferedImage chromaKey(BufferedImage img, double targetHue, double hueTol,
                                   double satThresh, int replaceRGB) {
        int w = img.getWidth(), h = img.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                double[] hsv = rgbToHSV((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
                double H = hsv[0], S = hsv[1];
                double diff = Math.abs(H - targetHue);
                if (diff > 180.0) diff = 360.0 - diff;
                if (diff <= hueTol && S > satThresh) {
                    out.setRGB(x, y, replaceRGB);
                } else {
                    out.setRGB(x, y, rgb);
                }
            }
        return out;
    }

    /** Создаёт синтетическое изображение: зелёный фон + белый круг. */
    static BufferedImage syntheticGreenScreen(int w, int h, int circleR) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int green = (0 << 16) | (200 << 8) | 0;
        int white = 0xFFFFFF;
        int cx = w / 2, cy = h / 2;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int dx = x - cx, dy = y - cy;
                img.setRGB(x, y, (dx * dx + dy * dy <= circleR * circleR) ? white : green);
            }
        return img;
    }

    // ── Конвертация изображения туда-обратно (для RMSE) ─────────────────────────

    static BufferedImage reconstructHSV(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                double[] hsv = rgbToHSV((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
                int[] nrgb = hsvToRGB(hsv[0], hsv[1], hsv[2]);
                out.setRGB(x, y, (nrgb[0] << 16) | (nrgb[1] << 8) | nrgb[2]);
            }
        return out;
    }

    static BufferedImage reconstructYCbCr(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                double[] ycbcr = rgbToYCbCr((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
                int[] nrgb = ycbcrToRGB(ycbcr[0], ycbcr[1], ycbcr[2]);
                out.setRGB(x, y, (nrgb[0] << 16) | (nrgb[1] << 8) | nrgb[2]);
            }
        return out;
    }

    static BufferedImage reconstructLab(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                double[] lab = rgbToLab((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
                int[] nrgb = labToRGB(lab[0], lab[1], lab[2]);
                out.setRGB(x, y, (nrgb[0] << 16) | (nrgb[1] << 8) | nrgb[2]);
            }
        return out;
    }

    // ── main ─────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws IOException {
        String outdir = "results5";
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--outdir")) outdir = args[i + 1];
        }
        new File(outdir).mkdirs();

        PrintWriter log = new PrintWriter(new FileWriter(outdir + "/log.txt"));

        String[] inputs = {
            "examples/baboon.png",
            "examples/sonoma_photo.jpg",
            "examples/circles.png",
            "examples/checker.png"
        };

        log.println("=== Lab5: Цветовые пространства и обработка цветных изображений ===");
        log.println();

        for (String path : inputs) {
            File f = new File(path);
            if (!f.exists()) {
                log.println("SKIP (not found): " + path);
                System.out.println("Skipping (not found): " + path);
                continue;
            }
            BufferedImage img = loadRGB(path);
            String name = f.getName().replaceFirst("\\.[^.]+$", "");
            int w = img.getWidth(), h = img.getHeight();
            log.println("--- " + name + " (" + w + "x" + h + ") ---");
            System.out.println("Processing: " + name);

            // ── 1. RMSE верификация конвертаций ─────────────────────────────────
            BufferedImage recHSV   = reconstructHSV(img);
            BufferedImage recYCbCr = reconstructYCbCr(img);
            BufferedImage recLab   = reconstructLab(img);

            double rmseHSV   = rmse(img, recHSV);
            double rmseYCbCr = rmse(img, recYCbCr);
            double rmseLab   = rmse(img, recLab);

            log.printf("  RMSE RGB->HSV->RGB:    %.4f%n", rmseHSV);
            log.printf("  RMSE RGB->YCbCr->RGB:  %.4f%n", rmseYCbCr);
            log.printf("  RMSE RGB->CIELab->RGB: %.4f%n", rmseLab);

            // ── 2. Визуализация каналов ──────────────────────────────────────────

            // RGB каналы
            BufferedImage rCh = extractRGBChannel(img, 0);
            BufferedImage gCh = extractRGBChannel(img, 1);
            BufferedImage bCh = extractRGBChannel(img, 2);
            save(makeRow(name + " — RGB channels", new String[]{"Original", "R", "G", "B"},
                new BufferedImage[]{img, rCh, gCh, bCh}),
                outdir + "/" + name + "_rgb_channels.png");

            // HSV каналы
            BufferedImage[] hsvChs = hsvChannelImages(img);
            save(makeRow(name + " — HSV channels", new String[]{"Original", "H", "S", "V"},
                new BufferedImage[]{img, hsvChs[0], hsvChs[1], hsvChs[2]}),
                outdir + "/" + name + "_hsv_channels.png");

            // YCbCr каналы
            BufferedImage[] ycbcrChs = ycbcrChannelImages(img);
            save(makeRow(name + " — YCbCr channels", new String[]{"Original", "Y", "Cb", "Cr"},
                new BufferedImage[]{img, ycbcrChs[0], ycbcrChs[1], ycbcrChs[2]}),
                outdir + "/" + name + "_ycbcr_channels.png");

            // CIELab каналы
            BufferedImage[] labChs = labChannelImages(img);
            save(makeRow(name + " — CIELab channels", new String[]{"Original", "L", "a", "b"},
                new BufferedImage[]{img, labChs[0], labChs[1], labChs[2]}),
                outdir + "/" + name + "_lab_channels.png");

            // ── 3. Эквализация гистограммы ───────────────────────────────────────
            BufferedImage eqRGB   = histEqRGB(img);
            BufferedImage eqHSV   = histEqHSV_V(img);
            BufferedImage eqYCbCr = histEqYCbCr_Y(img);
            save(makeRow(name + " — Histogram Equalization",
                new String[]{"Original", "RGB (all ch)", "HSV (V ch)", "YCbCr (Y ch)"},
                new BufferedImage[]{img, eqRGB, eqHSV, eqYCbCr}),
                outdir + "/" + name + "_histeq.png");
            log.println("  Saved histogram equalization: " + name + "_histeq.png");

            // ── 4. Баланс белого (серый мир) ────────────────────────────────────
            BufferedImage wb = grayWorldWhiteBalance(img);
            save(makeRow(name + " — White Balance (Gray World)",
                new String[]{"Original", "Corrected"},
                new BufferedImage[]{img, wb}),
                outdir + "/" + name + "_whitebalance.png");
            log.println("  Saved white balance: " + name + "_whitebalance.png");

            // ── 5. Квантизация k-means ───────────────────────────────────────────
            BufferedImage q4  = kmeansQuantize(img,  4, 20, 42L);
            BufferedImage q8  = kmeansQuantize(img,  8, 20, 42L);
            BufferedImage q16 = kmeansQuantize(img, 16, 20, 42L);
            save(makeRow(name + " — K-Means Quantization",
                new String[]{"Original", "k=4", "k=8", "k=16"},
                new BufferedImage[]{img, q4, q8, q16}),
                outdir + "/" + name + "_quantize.png");
            log.println("  Saved quantization: " + name + "_quantize.png");

            // ── 6. Сдвиг тона ────────────────────────────────────────────────────
            BufferedImage hs60  = hueShift(img,  60.0);
            BufferedImage hs120 = hueShift(img, 120.0);
            BufferedImage hs180 = hueShift(img, 180.0);
            save(makeRow(name + " — Hue Shift",
                new String[]{"Original", "+60°", "+120°", "+180°"},
                new BufferedImage[]{img, hs60, hs120, hs180}),
                outdir + "/" + name + "_hueshift.png");
            log.println("  Saved hue shift: " + name + "_hueshift.png");

            // ── 7. Хромакей ──────────────────────────────────────────────────────
            // hue ~ 120° (green), tol=40°, saturation > 0.25, replace with white
            int white = 0xFFFFFF;
            BufferedImage keyed = chromaKey(img, 120.0, 40.0, 0.25, white);
            save(makeRow(name + " — Chroma Key (green removal)",
                new String[]{"Original", "Chroma-Keyed"},
                new BufferedImage[]{img, keyed}),
                outdir + "/" + name + "_chromakey.png");
            log.println("  Saved chroma key: " + name + "_chromakey.png");

            log.println();
        }

        // ── Синтетический хромакей ────────────────────────────────────────────────
        log.println("--- Synthetic Chroma Key Demo ---");
        BufferedImage synth = syntheticGreenScreen(200, 200, 60);
        BufferedImage synthKeyed = chromaKey(synth, 120.0, 40.0, 0.25, 0xFFFFFF);
        save(makeRow("Chroma Key Demo (synthetic)",
            new String[]{"Synthetic (green+circle)", "Chroma-Keyed"},
            new BufferedImage[]{synth, synthKeyed}),
            outdir + "/chromakey_demo.png");
        log.println("  Saved chromakey_demo.png");
        log.println();
        log.println("=== Done ===");
        log.close();
        System.out.println("Done. Results in: " + outdir);
    }
}
