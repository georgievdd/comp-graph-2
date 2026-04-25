import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

public class Lab2 {

    static class Complex {
        double re, im;
        Complex(double re, double im) { this.re = re; this.im = im; }
        Complex add(Complex o) { return new Complex(re + o.re, im + o.im); }
        Complex sub(Complex o) { return new Complex(re - o.re, im - o.im); }
        Complex mul(Complex o) { return new Complex(re*o.re - im*o.im, re*o.im + im*o.re); }
        double abs() { return Math.sqrt(re*re + im*im); }
    }

    static void fft(Complex[] a, boolean inverse) {
        int n = a.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) { Complex t = a[i]; a[i] = a[j]; a[j] = t; }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = 2 * Math.PI / len * (inverse ? -1 : 1);
            Complex wlen = new Complex(Math.cos(ang), Math.sin(ang));
            for (int i = 0; i < n; i += len) {
                Complex w = new Complex(1, 0);
                for (int j = 0; j < len / 2; j++) {
                    Complex u = a[i + j];
                    Complex v = a[i + j + len / 2].mul(w);
                    a[i + j]           = u.add(v);
                    a[i + j + len / 2] = u.sub(v);
                    w = w.mul(wlen);
                }
            }
        }
        if (inverse) for (Complex c : a) { c.re /= n; c.im /= n; }
    }

    static int nextPow2(int n) {
        int p = 1; while (p < n) p <<= 1; return p;
    }

    static Complex[] fft1d(double[] signal, boolean inverse) {
        int n = nextPow2(signal.length);
        Complex[] a = new Complex[n];
        for (int i = 0; i < n; i++)
            a[i] = new Complex(i < signal.length ? signal[i] : 0, 0);
        fft(a, inverse);
        return a;
    }

    static Complex[] ifft1d(Complex[] spectrum) {
        Complex[] a = Arrays.copyOf(spectrum, spectrum.length);
        fft(a, true);
        return a;
    }

    static Complex[][] fft2d(double[][] data, boolean inverse) {
        int h = data.length, w = data[0].length;
        int H = nextPow2(h), W = nextPow2(w);
        Complex[][] c = new Complex[H][W];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                c[y][x] = new Complex(y < h && x < w ? data[y][x] : 0, 0);
        for (int y = 0; y < H; y++) fft(c[y], inverse);
        Complex[] col = new Complex[H];
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) col[y] = c[y][x];
            fft(col, inverse);
            for (int y = 0; y < H; y++) c[y][x] = col[y];
        }
        return c;
    }

    static Complex[][] ifft2d(Complex[][] spectrum) {
        int H = spectrum.length, W = spectrum[0].length;
        Complex[][] a = new Complex[H][W];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                a[y][x] = new Complex(spectrum[y][x].re, -spectrum[y][x].im);
        for (int y = 0; y < H; y++) fft(a[y], false);
        Complex[] col = new Complex[H];
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) col[y] = a[y][x];
            fft(col, false);
            for (int y = 0; y < H; y++) a[y][x] = col[y];
        }
        double scale = 1.0 / (H * W);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                a[y][x] = new Complex(a[y][x].re * scale, -a[y][x].im * scale);
        return a;
    }

    static double[][] toDouble(int[][] arr) {
        int h = arr.length, w = arr[0].length;
        double[][] d = new double[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                d[y][x] = arr[y][x];
        return d;
    }

    static Complex[][] fftShift2d(Complex[][] data) {
        int H = data.length, W = data[0].length;
        Complex[][] s = new Complex[H][W];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                s[y][x] = data[(y + H / 2) % H][(x + W / 2) % W];
        return s;
    }

    static Complex[] fftShift1d(Complex[] data) {
        int n = data.length;
        Complex[] s = new Complex[n];
        for (int i = 0; i < n; i++) s[i] = data[(i + n / 2) % n];
        return s;
    }

    // Вейвлет-разложение Хаара не является периодическим, поэтому циклический
    // сдвиг (как fftShift для FFT) здесь не применяется. Коэффициенты отображаются
    // в их естественном порядке: DC и низкие частоты — в левом верхнем углу,
    // высокочастотные детали — в остальных квадрантах пирамиды.

    static double[] haar1d(double[] signal, boolean inverse) {
        int n = nextPow2(signal.length);
        double[] a = new double[n];
        for (int i = 0; i < signal.length; i++) a[i] = signal[i];
        if (!inverse) {
            for (int len = n; len >= 2; len /= 2) {
                double[] tmp = new double[len];
                for (int i = 0; i < len / 2; i++) {
                    tmp[i]           = (a[2*i] + a[2*i+1]) / Math.sqrt(2);
                    tmp[i + len / 2] = (a[2*i] - a[2*i+1]) / Math.sqrt(2);
                }
                System.arraycopy(tmp, 0, a, 0, len);
            }
        } else {
            for (int len = 2; len <= n; len *= 2) {
                double[] tmp = new double[len];
                for (int i = 0; i < len / 2; i++) {
                    tmp[2*i]   = (a[i] + a[i + len/2]) / Math.sqrt(2);
                    tmp[2*i+1] = (a[i] - a[i + len/2]) / Math.sqrt(2);
                }
                System.arraycopy(tmp, 0, a, 0, len);
            }
        }
        return a;
    }

    static double[][] haar2d(double[][] data, boolean inverse) {
        int h = data.length, w = data[0].length;
        int H = nextPow2(h), W = nextPow2(w);
        double[][] a = new double[H][W];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) a[y][x] = data[y][x];
        if (!inverse) {
            for (int y = 0; y < H; y++) a[y] = haar1d(a[y], false);
            for (int x = 0; x < W; x++) {
                double[] col = new double[H];
                for (int y = 0; y < H; y++) col[y] = a[y][x];
                col = haar1d(col, false);
                for (int y = 0; y < H; y++) a[y][x] = col[y];
            }
        } else {
            for (int x = 0; x < W; x++) {
                double[] col = new double[H];
                for (int y = 0; y < H; y++) col[y] = a[y][x];
                col = haar1d(col, true);
                for (int y = 0; y < H; y++) a[y][x] = col[y];
            }
            for (int y = 0; y < H; y++) a[y] = haar1d(a[y], true);
        }
        return a;
    }

    static double rmse1d(double[] a, Complex[] rec, int len) {
        double sum = 0;
        for (int i = 0; i < len; i++) { double d = a[i] - rec[i].re; sum += d*d; }
        return Math.sqrt(sum / len);
    }

    static double rmse2d(double[][] orig, Complex[][] rec) {
        int h = orig.length, w = orig[0].length;
        double sum = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) { double d = orig[y][x] - rec[y][x].re; sum += d*d; }
        return Math.sqrt(sum / (h * w));
    }

    static int[][] getBrightness(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        int[][] br = new int[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF, gg = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                br[y][x] = (int)(0.299*r + 0.587*gg + 0.114*b);
            }
        return br;
    }

    static void saveDoubleImage(double[][] data, String path) throws IOException {
        int H = data.length, W = data[0].length;
        double minV = Double.MAX_VALUE, maxV = -Double.MAX_VALUE;
        for (double[] row : data) for (double v : row) { if (v < minV) minV = v; if (v > maxV) maxV = v; }
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int v = (maxV > minV) ? (int)(255 * (data[y][x] - minV) / (maxV - minV)) : 128;
                img.setRGB(x, y, Math.max(0, Math.min(255, v)) * 0x10101);
            }
        new File(path).getParentFile().mkdirs();
        ImageIO.write(img, "png", new File(path));
    }

    static void saveSpectrum2d(Complex[][] spectrum, String path) throws IOException {
        int H = spectrum.length, W = spectrum[0].length;
        double maxLog = 0;
        double[][] logAmp = new double[H][W];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                logAmp[y][x] = Math.log(1 + spectrum[y][x].abs());
                if (logAmp[y][x] > maxLog) maxLog = logAmp[y][x];
            }
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int v = maxLog > 0 ? (int)(255 * logAmp[y][x] / maxLog) : 0;
                img.setRGB(x, y, v * 0x10101);
            }
        new File(path).getParentFile().mkdirs();
        ImageIO.write(img, "png", new File(path));
    }

    static void saveHaarSpectrum2d(double[][] haarCoeffs, String path) throws IOException {
        int H = haarCoeffs.length, W = haarCoeffs[0].length;
        double maxLog = 0;
        double[][] logAbs = new double[H][W];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                logAbs[y][x] = Math.log(1 + Math.abs(haarCoeffs[y][x]));
                if (logAbs[y][x] > maxLog) maxLog = logAbs[y][x];
            }
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int v = maxLog > 0 ? (int)(255 * logAbs[y][x] / maxLog) : 0;
                img.setRGB(x, y, v * 0x10101);
            }
        new File(path).getParentFile().mkdirs();
        ImageIO.write(img, "png", new File(path));
    }

    /**
     * Сохраняет вейвлет-разложение Хаара с разметкой субполос.
     *
     * Что вычисляет алгоритм (т.н. «спектр Хаара»):
     *   Это НЕ спектр в смысле Фурье. Дискретное вейвлет-преобразование Хаара
     *   разлагает изображение по базисным функциям-«ступенькам» (не синусоидам)
     *   на разных масштабах (уровнях разложения).
     *
     *   На каждом уровне для каждой пары соседних значений вычисляются:
     *     аппроксимирующий коэффициент = (a + b) / √2  (локальное среднее, НЧ)
     *     детализирующий коэффициент   = (a − b) / √2  (локальная разность, ВЧ)
     *
     *   Применяя это сначала по строкам, затем по столбцам, получаем четыре субполосы:
     *     LL  — аппроксимация (уменьшенная копия изображения)
     *     HL  — горизонтально-вариативные детали (реагирует на вертикальные рёбра)
     *     LH  — вертикально-вариативные детали (реагирует на горизонтальные рёбра)
     *     HH  — диагональные детали
     *
     *   Отличие от FFT:
     *     FFT: глобальное частотное разложение без пространственной локализации.
     *     Хаар: многоуровневое разложение с ОДНОВРЕМЕННОЙ локализацией по пространству
     *           и по масштабу. Коэффициенты = разности яркостей в блоках разного размера,
     *           а не амплитуды синусоид.
     *
     *   Структура пирамиды (дерево Маллата):
     *     Верхний левый угол → LL (аппроксимация, самый грубый уровень — один пиксель).
     *     Вокруг него кольцами → субполосы HL/LH/HH от грубого к мелкому масштабу.
     *     Самое внешнее кольцо (HL1, LH1, HH1) → самый мелкий масштаб (самые высокие «частоты»).
     */
    static void saveHaarWaveletDiagram(double[][] haarCoeffs, String path, String name) throws IOException {
        int H = haarCoeffs.length, W = haarCoeffs[0].length;
        double maxLog = 0;
        double[][] logAbs = new double[H][W];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                logAbs[y][x] = Math.log(1 + Math.abs(haarCoeffs[y][x]));
                if (logAbs[y][x] > maxLog) maxLog = logAbs[y][x];
            }
        int titleH = 22;
        BufferedImage img = new BufferedImage(W, H + titleH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, W, H + titleH);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int v = maxLog > 0 ? (int)(255 * logAbs[y][x] / maxLog) : 0;
                img.setRGB(x, y + titleH, v * 0x10101);
            }
        // Рисуем границы субполос и подписи (от мелкого масштаба к крупному)
        g.setStroke(new BasicStroke(1f));
        int lH = H, lW = W, lv = 1;
        while (lH >= 4 && lW >= 4) {
            int hh = lH / 2, hw = lW / 2;
            g.setColor(new Color(200, 50, 50));
            g.drawLine(0, titleH + hh, lW, titleH + hh);
            g.drawLine(hw, titleH, hw, titleH + lH);
            if (hh >= 24 && hw >= 24) {
                int fs = Math.max(8, Math.min(11, Math.min(hh, hw) / 6));
                g.setFont(new Font("Monospaced", Font.BOLD, fs));
                // HL: top-right (горизонтально-вариативные детали → вертикальные рёбра)
                g.setColor(new Color(255, 200, 80));
                g.drawString("HL" + lv, hw + 3, titleH + fs + 3);
                // LH: bottom-left (вертикально-вариативные детали → горизонтальные рёбра)
                g.setColor(new Color(80, 220, 80));
                g.drawString("LH" + lv, 3, titleH + hh + fs + 3);
                // HH: bottom-right (диагональные детали)
                g.setColor(new Color(80, 160, 255));
                g.drawString("HH" + lv, hw + 3, titleH + hh + fs + 3);
            }
            lH = hh; lW = hw; lv++;
        }
        if (lH >= 4) {
            g.setColor(Color.CYAN);
            g.setFont(new Font("Monospaced", Font.BOLD, 8));
            g.drawString("LL", 2, titleH + 9);
        }
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.drawString(name + " — вейвлет-коэффициенты Хаара (пирамида субполос)", 3, titleH - 5);
        g.dispose();
        new File(path).getParentFile().mkdirs();
        ImageIO.write(img, "png", new File(path));
    }

    private static void drawPanel(Graphics2D g, double[] values,
                                  int px, int py, int pw, int ph, int lpad,
                                  String label, Color color) {
        int plotX = px + lpad, plotY = py, plotW = pw - lpad, plotH = ph;
        double minV = Double.MAX_VALUE, maxV = -Double.MAX_VALUE;
        for (double v : values) { if (v < minV) minV = v; if (v > maxV) maxV = v; }
        if (maxV == minV) maxV = minV + 1;
        g.setColor(new Color(248, 248, 248)); g.fillRect(plotX, plotY, plotW, plotH);
        g.setColor(new Color(220, 220, 220));
        for (int i = 0; i <= 4; i++) {
            int gy = plotY + plotH - (int)(i * plotH / 4.0);
            g.drawLine(plotX, gy, plotX + plotW, gy);
        }
        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font("SansSerif", Font.PLAIN, 9));
        for (int i = 0; i <= 4; i++) {
            double val = minV + i * (maxV - minV) / 4;
            int gy = plotY + plotH - (int)(i * plotH / 4.0);
            g.drawString(String.format("%.0f", val), px + 2, gy + 4);
        }
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.setColor(Color.BLACK);
        g.drawString(label, plotX + 5, plotY - 5);
        g.setColor(Color.GRAY);
        g.setStroke(new BasicStroke(1f));
        g.drawRect(plotX, plotY, plotW, plotH);
        g.setColor(color);
        g.setStroke(new BasicStroke(1.5f));
        int n = values.length, prev_gx = -1, prev_gy = -1;
        for (int i = 0; i < n; i++) {
            int gx = plotX + (int)((double)i / (n - 1) * plotW);
            int gy = plotY + plotH - (int)((values[i] - minV) / (maxV - minV) * plotH);
            gy = Math.max(plotY, Math.min(plotY + plotH, gy));
            if (prev_gx >= 0) g.drawLine(prev_gx, prev_gy, gx, gy);
            prev_gx = gx; prev_gy = gy;
        }
    }

    static void saveSignalAndSpectrum1d(double[] signal, Complex[] spectrum,
                                        String path, String title) throws IOException {
        int W = 900, H = 420, panW = W / 2 - 10, panH = H - 70;
        int leftPad = 55, topPad = 45, botPad = 30;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE); g.fillRect(0, 0, W, H);
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.setColor(Color.BLACK);
        g.drawString(title, 10, 20);
        drawPanel(g, signal, 5, topPad, panW, panH - botPad, leftPad,
                  "Сигнал", new Color(30, 100, 200));
        Complex[] shifted = fftShift1d(spectrum);
        double[] logAmp = new double[shifted.length];
        for (int i = 0; i < shifted.length; i++)
            logAmp[i] = Math.log(1 + shifted[i].abs());
        drawPanel(g, logAmp, W / 2 + 5, topPad, panW, panH - botPad, leftPad,
                  "Лог-амплитудный спектр (центр = 0 Гц)", new Color(200, 50, 50));
        g.dispose();
        new File(path).getParentFile().mkdirs();
        ImageIO.write(img, "png", new File(path));
    }

    static void saveHaarSpectrum1d(double[] haarCoeffs, double[] original,
                                   String path, String title) throws IOException {
        int W = 900, H = 420, panW = W / 2 - 10, panH = H - 70;
        int leftPad = 55, topPad = 45;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE); g.fillRect(0, 0, W, H);
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.setColor(Color.BLACK);
        g.drawString(title, 10, 20);
        double[] logAmp = new double[haarCoeffs.length];
        for (int i = 0; i < haarCoeffs.length; i++)
            logAmp[i] = Math.log(1 + Math.abs(haarCoeffs[i]));
        drawPanel(g, original, 5, topPad, panW, panH - 30, leftPad,
                  "Сигнал", new Color(30, 100, 200));
        drawPanel(g, logAmp, W / 2 + 5, topPad, panW, panH - 30, leftPad,
                  "Лог-амплитудный спектр Хаара (DC слева, детали справа)", new Color(150, 50, 200));
        g.dispose();
        new File(path).getParentFile().mkdirs();
        ImageIO.write(img, "png", new File(path));
    }

    static void saveImageAndSpectrum2d(BufferedImage original, Complex[][] spectrum,
                                       String path, String title) throws IOException {
        Complex[][] shifted = fftShift2d(spectrum);
        int sH = shifted.length, sW = shifted[0].length;
        double maxLog = 0;
        double[][] logAmp = new double[sH][sW];
        for (int y = 0; y < sH; y++)
            for (int x = 0; x < sW; x++) {
                logAmp[y][x] = Math.log(1 + shifted[y][x].abs());
                if (logAmp[y][x] > maxLog) maxLog = logAmp[y][x];
            }
        int srcW = original.getWidth(), srcH = original.getHeight();
        BufferedImage specImg = new BufferedImage(sW, sH, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < sH; y++)
            for (int x = 0; x < sW; x++) {
                int v = maxLog > 0 ? (int)(255 * logAmp[y][x] / maxLog) : 0;
                specImg.setRGB(x, y, v * 0x10101);
            }
        int pad = 10, titleH = 30, labelH = 20;
        BufferedImage out = new BufferedImage(srcW + pad + sW, titleH + labelH + Math.max(srcH, sH),
                                             BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE); g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.setColor(Color.BLACK);
        g.drawString(title, 5, 18);
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.drawString("Исходное изображение", 5, titleH + labelH - 3);
        g.drawString("Лог-амплитудный спектр (0 Гц в центре)", srcW + pad + 5, titleH + labelH - 3);
        g.drawImage(original, 0, titleH + labelH, null);
        g.drawImage(specImg, srcW + pad, titleH + labelH, null);
        g.dispose();
        new File(path).getParentFile().mkdirs();
        ImageIO.write(out, "png", new File(path));
    }

    static void saveSpectraComparison(BufferedImage original, Complex[][] fftSpec,
                                      double[][] haarCoeffs, String path,
                                      String name) throws IOException {
        int srcW = original.getWidth(), srcH = original.getHeight();
        Complex[][] fftShifted = fftShift2d(fftSpec);
        int fH = fftShifted.length, fW = fftShifted[0].length;
        double maxFft = 0;
        double[][] fftLog = new double[fH][fW];
        for (int y = 0; y < fH; y++)
            for (int x = 0; x < fW; x++) {
                fftLog[y][x] = Math.log(1 + fftShifted[y][x].abs());
                if (fftLog[y][x] > maxFft) maxFft = fftLog[y][x];
            }
        BufferedImage fftImg = new BufferedImage(fW, fH, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < fH; y++)
            for (int x = 0; x < fW; x++) {
                int v = maxFft > 0 ? (int)(255 * fftLog[y][x] / maxFft) : 0;
                fftImg.setRGB(x, y, v * 0x10101);
            }
        int hH = haarCoeffs.length, hW = haarCoeffs[0].length;
        double maxHaar = 0;
        double[][] haarLog = new double[hH][hW];
        for (int y = 0; y < hH; y++)
            for (int x = 0; x < hW; x++) {
                haarLog[y][x] = Math.log(1 + Math.abs(haarCoeffs[y][x]));
                if (haarLog[y][x] > maxHaar) maxHaar = haarLog[y][x];
            }
        BufferedImage haarImg = new BufferedImage(hW, hH, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < hH; y++)
            for (int x = 0; x < hW; x++) {
                int v = maxHaar > 0 ? (int)(255 * haarLog[y][x] / maxHaar) : 0;
                haarImg.setRGB(x, y, v * 0x10101);
            }
        int pad = 10, titleH = 30, labelH = 20;
        int maxH = Math.max(srcH, Math.max(fH, hH));
        BufferedImage out = new BufferedImage(srcW + pad + fW + pad + hW, titleH + labelH + maxH,
                                             BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE); g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.setColor(Color.BLACK);
        g.drawString(name + " — сравнение спектров (FFT и Хаара)", 5, 18);
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.drawString("Оригинал", 5, titleH + labelH - 3);
        g.drawString("Лог-амплитуда FFT (DC в центре)", srcW + pad + 5, titleH + labelH - 3);
        g.drawString("Вейвлет-коэфф. Хаара (LL↖  HL↗ / LH↙  HH↘)", srcW + pad + fW + pad + 5, titleH + labelH - 3);
        g.drawImage(original, 0, titleH + labelH, null);
        g.drawImage(fftImg, srcW + pad, titleH + labelH, null);
        g.drawImage(haarImg, srcW + pad + fW + pad, titleH + labelH, null);
        g.dispose();
        new File(path).getParentFile().mkdirs();
        ImageIO.write(out, "png", new File(path));
    }

    static void processSynthImage(double[][] data, String outDir,
                                  String name, String title, PrintWriter log) throws IOException {
        saveDoubleImage(data, outDir + "/" + name + "_original.png");
        Complex[][] spec = fft2d(data, false);
        saveSpectrum2d(fftShift2d(spec), outDir + "/" + name + "_spectrum.png");
        BufferedImage orig = ImageIO.read(new File(outDir + "/" + name + "_original.png"));
        BufferedImage specImg = ImageIO.read(new File(outDir + "/" + name + "_spectrum.png"));
        int pad = 10, labelH = 20, titleH = 30;
        int outW = orig.getWidth() + pad + specImg.getWidth();
        int outH = titleH + labelH + Math.max(orig.getHeight(), specImg.getHeight());
        BufferedImage combined = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = combined.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0, 0, outW, outH);
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.setColor(Color.BLACK);
        g.drawString(title, 5, 18);
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.drawString("Изображение", 5, titleH + labelH - 3);
        g.drawString("Лог-амплитудный спектр (0 Гц в центре)", orig.getWidth() + pad + 5, titleH + labelH - 3);
        g.drawImage(orig, 0, titleH + labelH, null);
        g.drawImage(specImg, orig.getWidth() + pad, titleH + labelH, null);
        g.dispose();
        ImageIO.write(combined, "png", new File(outDir + "/" + name + "_combined.png"));
        double err = rmse2d(data, ifft2d(spec));
        String m = String.format("  %s: RMSE(IFFT) = %.2e", name, err);
        System.out.println(m); log.println(m);
        double[][] haar = haar2d(data, false);
        saveHaarSpectrum2d(haar, outDir + "/" + name + "_haar.png");
    }

    public static void main(String[] args) throws IOException {
        String outDir = "results2";
        for (int i = 0; i < args.length - 1; i++)
            if ("--outdir".equals(args[i])) outDir = args[i + 1];
        new File(outDir).mkdirs();

        PrintWriter log = new PrintWriter(new FileWriter(outDir + "/log.txt", false));
        log.println("Лабораторная работа №2: Преобразование Фурье и вейвлет-разложение Хаара");
        log.println("Дата: " + new java.util.Date());
        log.println();
        log.println("=== ЧТО ВЫЧИСЛЯЕТ АЛГОРИТМ «СПЕКТРА ХААРА» ===");
        log.println();
        log.println("Термин «спектр Хаара» — условный. На самом деле реализовано дискретное");
        log.println("вейвлет-преобразование Хаара (Haar Discrete Wavelet Transform, DWT).");
        log.println();
        log.println("Фурье-спектр показывает амплитуду каждой ЧАСТОТЫ (глобально по всему сигналу).");
        log.println("Вейвлет-преобразование показывает локальные изменения яркости на разных МАСШТАБАХ.");
        log.println();
        log.println("Алгоритм (1D, один уровень):");
        log.println("  Для каждой пары соседних отсчётов a[2i], a[2i+1]:");
        log.println("    аппроксим. коэфф.  s[i] = (a[2i] + a[2i+1]) / √2   (НЧ, «сумма»)");
        log.println("    детализир. коэфф.  d[i] = (a[2i] - a[2i+1]) / √2   (ВЧ, «разность»)");
        log.println("  Затем алгоритм рекурсивно применяется к массиву s[i] → многоуровневое разложение.");
        log.println();
        log.println("Для 2D-изображения преобразование применяется строчно, затем столбцово.");
        log.println("На каждом уровне разложения возникают 4 субполосы (квадранты):");
        log.println("  LL  (верхний левый)  — аппроксимация: уменьшенная версия изображения");
        log.println("  HL  (верхний правый) — детали вдоль оси X → чувствителен к вертикальным рёбрам");
        log.println("  LH  (нижний левый)   — детали вдоль оси Y → чувствителен к горизонтальным рёбрам");
        log.println("  HH  (нижний правый)  — диагональные детали");
        log.println();
        log.println("Структура пирамиды (дерево Маллата):");
        log.println("  Полное преобразование рекурсивно разлагает субполосу LL.");
        log.println("  В итоге LL сжимается до 1 пикселя (DC-компонента = среднее всего изображения).");
        log.println("  Вокруг него — кольца субполос: от крупного масштаба (соседние с DC)");
        log.println("                                  до мелкого (самые внешние HL1/LH1/HH1).");
        log.println();
        log.println("Ключевое отличие от FFT:");
        log.println("  FFT    — глобально, нет пространственной локализации (не знает, ГДЕ в изображении");
        log.println("           находится та или иная частота).");
        log.println("  Хаар   — одновременно пространственная И масштабная локализация.");
        log.println("           Коэффициент d[i] на уровне k = насколько сильно меняется яркость");
        log.println("           в блоке размером 2^k пикселей, расположенном В КОНКРЕТНОМ МЕСТЕ изображения.");
        log.println("=================================================");
        log.println();

        // 1D FFT
        int N = 512;
        double fs = 1000.0;
        double[] t = new double[N];
        for (int i = 0; i < N; i++) t[i] = i / fs;

        double[] sig1 = new double[N];
        for (int i = 0; i < N; i++)
            sig1[i] = Math.sin(2*Math.PI*50*t[i]) + 0.5*Math.sin(2*Math.PI*120*t[i]) + 0.25*Math.sin(2*Math.PI*300*t[i]);
        Complex[] spec1 = fft1d(sig1, false);
        saveSignalAndSpectrum1d(sig1, spec1, outDir + "/signal1_fft.png",
            "Сигнал 1: sin(50Гц) + 0.5·sin(120Гц) + 0.25·sin(300Гц)");
        log.println(String.format("  Сигнал 1: RMSE(IFFT) = %.2e", rmse1d(sig1, ifft1d(spec1), N)));

        double[] sig2 = new double[N];
        double[] freqs2 = {10, 80, 150, 250, 400};
        double[] amps2  = {2.0, 1.0, 0.7, 0.4, 0.2};
        Random rng = new Random(42);
        for (int i = 0; i < N; i++) {
            for (int k = 0; k < freqs2.length; k++)
                sig2[i] += amps2[k] * Math.sin(2 * Math.PI * freqs2[k] * t[i]);
            sig2[i] += 0.1 * rng.nextGaussian();
        }
        Complex[] spec2 = fft1d(sig2, false);
        saveSignalAndSpectrum1d(sig2, spec2, outDir + "/signal2_fft.png",
            "Сигнал 2: 5 синусоид (10, 80, 150, 250, 400 Гц) + гауссов шум σ=0.1");
        log.println(String.format("  Сигнал 2: RMSE(IFFT) = %.2e", rmse1d(sig2, ifft1d(spec2), N)));

        // 2D FFT — синтетические изображения
        int S = 256;
        double[][] sinH = new double[S][S], sinV = new double[S][S],
                   sinD = new double[S][S], sinMix = new double[S][S];
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                sinH[y][x]   = 127 + 127 * Math.sin(2 * Math.PI * 8 * x / S);
                sinV[y][x]   = 127 + 127 * Math.sin(2 * Math.PI * 8 * y / S);
                sinD[y][x]   = 127 + 127 * Math.sin(2 * Math.PI * 6 * (x + y) / S);
                sinMix[y][x] = 127 + 60 * Math.sin(2*Math.PI*4*x/S)
                                   + 40 * Math.sin(2*Math.PI*12*y/S)
                                   + 27 * Math.sin(2*Math.PI*20*(x+y)/S);
            }
        processSynthImage(sinH,   outDir, "sin_horizontal", "Горизонтальная синусоида (f=8)", log);
        processSynthImage(sinV,   outDir, "sin_vertical",   "Вертикальная синусоида (f=8)",   log);
        processSynthImage(sinD,   outDir, "sin_diagonal",   "Диагональная синусоида (f=6)",   log);
        processSynthImage(sinMix, outDir, "sin_mix",        "Смесь: sin(4,0) + sin(0,12) + sin(20,20)", log);

        // 2D FFT + Haar — реальные изображения
        String[] photoPaths = {"examples/checker.png","examples/gradient.png","examples/circles.png",
                               "examples/baboon.png","examples/sonoma_photo.jpg"};
        String[] photoNames = {"checker","gradient","circles","baboon","sonoma_photo"};

        for (int i = 0; i < photoPaths.length; i++) {
            File f = new File(photoPaths[i]);
            if (!f.exists()) { log.println("Не найден: " + photoPaths[i]); continue; }
            BufferedImage img = ImageIO.read(f);
            if (img == null) { log.println("Не читается: " + photoPaths[i]); continue; }

            double[][] dbl = toDouble(getBrightness(img));
            Complex[][] spec = fft2d(dbl, false);
            saveImageAndSpectrum2d(img, spec, outDir + "/" + photoNames[i] + "_spectrum.png",
                photoNames[i] + " — лог-амплитудный спектр Фурье");

            Complex[][] recImg = ifft2d(fft2d(dbl, false));
            log.println(String.format("  %s: %dx%d, RMSE(IFFT)=%.2e",
                photoNames[i], img.getWidth(), img.getHeight(), rmse2d(dbl, recImg)));

            double[][] recReal = new double[dbl.length][dbl[0].length];
            for (int y = 0; y < dbl.length; y++)
                for (int x = 0; x < dbl[0].length; x++)
                    recReal[y][x] = recImg[y][x].re;
            saveDoubleImage(recReal, outDir + "/" + photoNames[i] + "_ifft.png");

            double[][] haar = haar2d(dbl, false);
            saveHaarWaveletDiagram(haar, outDir + "/" + photoNames[i] + "_haar.png", photoNames[i]);
            saveSpectraComparison(img, spec, haar,
                outDir + "/" + photoNames[i] + "_spectra_comparison.png", photoNames[i]);
        }

        // Haar 1D
        double[] haarCoeffs1 = haar1d(sig1, false);
        saveHaarSpectrum1d(haarCoeffs1, sig1, outDir + "/signal1_haar.png",
            "Спектр Хаара — Сигнал 1 (sin(50)+0.5·sin(120)+0.25·sin(300))");
        double[] recHaar1 = haar1d(haarCoeffs1, true);
        double errH = 0;
        for (int i = 0; i < N; i++) { double d = sig1[i] - recHaar1[i]; errH += d*d; }
        log.println(String.format("  Сигнал 1: RMSE(обратный Хаар) = %.2e", Math.sqrt(errH / N)));

        saveHaarSpectrum1d(haar1d(sig2, false), sig2, outDir + "/signal2_haar.png",
            "Спектр Хаара — Сигнал 2 (5 синусоид + шум)");

        log.println("\nВсё сохранено в " + outDir + "/");
        log.close();
        System.out.println("Все результаты → " + outDir + "/");
    }
}
