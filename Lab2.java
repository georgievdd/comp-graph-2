import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

/**
 * Лабораторная работа №2: Преобразование Фурье.
 *
 * Реализовано:
 *   - Прямое/обратное быстрое 1D FFT (Cooley–Tukey, radix-2)
 *   - Прямое/обратное быстрое 2D FFT (построчно + постолбцово)
 *   - Поддержка произвольного размера (дополнение нулями до степени 2)
 *   - fftshift: нулевая частота в центре спектра
 *   - Сохранение log-амплитудного спектра как PNG
 *   - Примеры: 1D-смесь синусоид, 2D-изображение из синусоид, реальные фото
 *   - Обратное преобразование с проверкой round-trip
 *   - Спектр Хаара (1D и 2D)
 *
 * Запуск:
 *   java Lab2 [--outdir <папка>]   (по умолчанию results2/)
 */
public class Lab2 {

    // ─────────────────────────────────────────────────────────────────
    //  Комплексное число
    // ─────────────────────────────────────────────────────────────────
    static class Complex {
        double re, im;
        Complex(double re, double im) { this.re = re; this.im = im; }
        Complex add(Complex o) { return new Complex(re + o.re, im + o.im); }
        Complex sub(Complex o) { return new Complex(re - o.re, im - o.im); }
        Complex mul(Complex o) { return new Complex(re*o.re - im*o.im, re*o.im + im*o.re); }
        double abs() { return Math.sqrt(re*re + im*im); }
        @Override public String toString() { return String.format("(%.3f, %.3f)", re, im); }
    }

    // ─────────────────────────────────────────────────────────────────
    //  FFT / IFFT (Cooley–Tukey radix-2, in-place)
    //  inverse=false → прямое (X[k] = Σ x[n]·e^{-2πikn/N})
    //  inverse=true  → обратное (x[n] = (1/N) Σ X[k]·e^{+2πikn/N})
    // ─────────────────────────────────────────────────────────────────
    static void fft(Complex[] a, boolean inverse) {
        int n = a.length;
        // бит-реверсная перестановка
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) { Complex t = a[i]; a[i] = a[j]; a[j] = t; }
        }
        // "бабочки"
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

    // 1D FFT произвольной длины (дополняем нулями)
    static Complex[] fft1d(double[] signal, boolean inverse) {
        int n = nextPow2(signal.length);
        Complex[] a = new Complex[n];
        for (int i = 0; i < n; i++)
            a[i] = new Complex(i < signal.length ? signal[i] : 0, 0);
        fft(a, inverse);
        return a;
    }

    // 1D IFFT из комплексного массива
    static Complex[] ifft1d(Complex[] spectrum) {
        Complex[] a = Arrays.copyOf(spectrum, spectrum.length);
        fft(a, true);
        return a;
    }

    // ─────────────────────────────────────────────────────────────────
    //  2D FFT / IFFT
    // ─────────────────────────────────────────────────────────────────
    static Complex[][] fft2d(double[][] data, boolean inverse) {
        int h = data.length, w = data[0].length;
        int H = nextPow2(h), W = nextPow2(w);
        Complex[][] c = new Complex[H][W];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                c[y][x] = new Complex(y < h && x < w ? data[y][x] : 0, 0);

        // по строкам
        for (int y = 0; y < H; y++) fft(c[y], inverse);

        // по столбцам
        Complex[] col = new Complex[H];
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) col[y] = c[y][x];
            fft(col, inverse);
            for (int y = 0; y < H; y++) c[y][x] = col[y];
        }
        if (inverse) {
            // fft() уже делит на N для каждого измерения, но мы делаем два
            // прохода с inverse=true — итого делим на H*W, что правильно для 2D IFFT
        }
        return c;
    }

    // Из int[][] (яркость) → double[][]
    static double[][] toDouble(int[][] arr) {
        int h = arr.length, w = arr[0].length;
        double[][] d = new double[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                d[y][x] = arr[y][x];
        return d;
    }

    // ─────────────────────────────────────────────────────────────────
    //  fftshift: нулевая частота в центр
    // ─────────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────────
    //  Сохранение лог-амплитудного спектра 2D как PNG
    // ─────────────────────────────────────────────────────────────────
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
                img.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        new File(path).getParentFile().mkdirs();
        ImageIO.write(img, "png", new File(path));
    }

    // ─────────────────────────────────────────────────────────────────
    //  Сохранение 1D сигнала + его спектра как PNG (два графика рядом)
    // ─────────────────────────────────────────────────────────────────
    static void saveSignalAndSpectrum1d(double[] signal, Complex[] spectrum,
                                        String path, String title) throws IOException {
        int W = 900, H = 420;
        int panW = W / 2 - 10, panH = H - 70;
        int leftPad = 55, topPad = 45, botPad = 30;

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE); g.fillRect(0, 0, W, H);

        // Заголовок
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.setColor(Color.BLACK);
        g.drawString(title, 10, 20);

        // ── левая панель: сигнал ─────────────────────────────────────
        drawPanel(g, signal, 5, topPad, panW, panH - botPad, leftPad,
                  "Сигнал", new Color(30, 100, 200), false, false);

        // ── правая панель: лог-амплитудный спектр (со сдвигом) ───────
        Complex[] shifted = fftShift1d(spectrum);
        double[] logAmp = new double[shifted.length];
        for (int i = 0; i < shifted.length; i++)
            logAmp[i] = Math.log(1 + shifted[i].abs());
        drawPanel(g, logAmp, W / 2 + 5, topPad, panW, panH - botPad, leftPad,
                  "Лог-амплитудный спектр (центр = 0 Гц)", new Color(200, 50, 50), false, false);

        g.dispose();
        new File(path).getParentFile().mkdirs();
        ImageIO.write(img, "png", new File(path));
    }

    private static void drawPanel(Graphics2D g, double[] values,
                                  int px, int py, int pw, int ph, int lpad,
                                  String label, Color color,
                                  boolean showZeroLine, boolean dots) {
        int plotX = px + lpad, plotY = py, plotW = pw - lpad, plotH = ph;

        double minV = Double.MAX_VALUE, maxV = -Double.MAX_VALUE;
        for (double v : values) { if (v < minV) minV = v; if (v > maxV) maxV = v; }
        if (maxV == minV) { maxV = minV + 1; }

        // фон панели
        g.setColor(new Color(248, 248, 248));
        g.fillRect(plotX, plotY, plotW, plotH);

        // сетка
        g.setColor(new Color(220, 220, 220));
        for (int i = 0; i <= 4; i++) {
            int gy = plotY + plotH - (int)(i * plotH / 4.0);
            g.drawLine(plotX, gy, plotX + plotW, gy);
        }

        // ось Y
        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font("SansSerif", Font.PLAIN, 9));
        for (int i = 0; i <= 4; i++) {
            double val = minV + i * (maxV - minV) / 4;
            int gy = plotY + plotH - (int)(i * plotH / 4.0);
            g.drawString(String.format("%.0f", val), px + 2, gy + 4);
        }

        // подпись
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.setColor(Color.BLACK);
        g.drawString(label, plotX + 5, plotY - 5);

        // рамка
        g.setColor(Color.GRAY);
        g.setStroke(new BasicStroke(1f));
        g.drawRect(plotX, plotY, plotW, plotH);

        // кривая
        g.setColor(color);
        g.setStroke(new BasicStroke(1.5f));
        int n = values.length;
        int prev_gx = -1, prev_gy = -1;
        for (int i = 0; i < n; i++) {
            int gx = plotX + (int)((double)i / (n - 1) * plotW);
            int gy = plotY + plotH - (int)((values[i] - minV) / (maxV - minV) * plotH);
            gy = Math.max(plotY, Math.min(plotY + plotH, gy));
            if (prev_gx >= 0) g.drawLine(prev_gx, prev_gy, gx, gy);
            prev_gx = gx; prev_gy = gy;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  Сохранение 2D изображения рядом со спектром
    // ─────────────────────────────────────────────────────────────────
    static void saveImageAndSpectrum2d(BufferedImage original, Complex[][] spectrum,
                                       String path, String title) throws IOException {
        Complex[][] shifted = fftShift2d(spectrum);
        int sH = shifted.length, sW = shifted[0].length;

        // лог-амплитудный спектр → нормировка в [0,255]
        double maxLog = 0;
        double[][] logAmp = new double[sH][sW];
        for (int y = 0; y < sH; y++)
            for (int x = 0; x < sW; x++) {
                logAmp[y][x] = Math.log(1 + shifted[y][x].abs());
                if (logAmp[y][x] > maxLog) maxLog = logAmp[y][x];
            }

        int srcW = original.getWidth(), srcH = original.getHeight();
        int size = Math.max(Math.max(srcW, srcH), Math.max(sW, sH));
        size = Math.min(size, 512); // ограничиваем для удобства

        BufferedImage specImg = new BufferedImage(sW, sH, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < sH; y++)
            for (int x = 0; x < sW; x++) {
                int v = maxLog > 0 ? (int)(255 * logAmp[y][x] / maxLog) : 0;
                specImg.setRGB(x, y, (v << 16) | (v << 8) | v);
            }

        int pad = 10, titleH = 30, labelH = 20;
        int W = srcW + pad + sW;
        int H = titleH + labelH + Math.max(srcH, sH);

        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE); g.fillRect(0, 0, W, H);

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

    // ─────────────────────────────────────────────────────────────────
    //  Проверка IFFT: восстановление сигнала из спектра
    // ─────────────────────────────────────────────────────────────────
    static double rmse1d(double[] a, Complex[] reconstructed, int origLen) {
        double sum = 0;
        for (int i = 0; i < origLen; i++) {
            double d = a[i] - reconstructed[i].re;
            sum += d * d;
        }
        return Math.sqrt(sum / origLen);
    }

    static double rmse2d(double[][] orig, Complex[][] rec) {
        int h = orig.length, w = orig[0].length;
        double sum = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double d = orig[y][x] - rec[y][x].re;
                sum += d * d;
            }
        return Math.sqrt(sum / (h * w));
    }

    // ─────────────────────────────────────────────────────────────────
    //  Преобразование Хаара (1D, in-place, для длины-степени-2)
    //  forward: анализ (разложение)
    //  inverse: синтез (восстановление)
    // ─────────────────────────────────────────────────────────────────
    static double[] haar1d(double[] signal, boolean inverse) {
        int n = nextPow2(signal.length);
        double[] a = new double[n];
        for (int i = 0; i < signal.length; i++) a[i] = signal[i];

        if (!inverse) {
            // прямое: от полного масштаба к мельчайшему
            for (int len = n; len >= 2; len /= 2) {
                double[] tmp = new double[len];
                for (int i = 0; i < len / 2; i++) {
                    tmp[i]           = (a[2*i] + a[2*i+1]) / Math.sqrt(2);
                    tmp[i + len / 2] = (a[2*i] - a[2*i+1]) / Math.sqrt(2);
                }
                System.arraycopy(tmp, 0, a, 0, len);
            }
        } else {
            // обратное: от мельчайшего масштаба к полному
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

    // 2D преобразование Хаара (построчно, затем постолбцово)
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

    // Сохранение спектра Хаара как PNG (лог-масштаб)
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
                img.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        new File(path).getParentFile().mkdirs();
        ImageIO.write(img, "png", new File(path));
    }

    // Сохранение спектра Хаара 1D как PNG
    static void saveHaarSpectrum1d(double[] haarCoeffs, double[] original,
                                   String path, String title) throws IOException {
        int W = 900, H = 420;
        int panW = W / 2 - 10, panH = H - 70;
        int leftPad = 55, topPad = 45;

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE); g.fillRect(0, 0, W, H);

        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.setColor(Color.BLACK);
        g.drawString(title, 10, 20);

        drawPanel(g, original, 5, topPad, panW, panH - 30, leftPad,
                  "Сигнал", new Color(30, 100, 200), false, false);
        drawPanel(g, haarCoeffs, W / 2 + 5, topPad, panW, panH - 30, leftPad,
                  "Коэффициенты Хаара", new Color(150, 50, 200), false, false);

        g.dispose();
        new File(path).getParentFile().mkdirs();
        ImageIO.write(img, "png", new File(path));
    }

    // ─────────────────────────────────────────────────────────────────
    //  Извлечение яркости из BufferedImage
    // ─────────────────────────────────────────────────────────────────
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

    // Сохранение double[][] как изображения (нормировка в [0,255])
    static void saveDoubleImage(double[][] data, String path) throws IOException {
        int H = data.length, W = data[0].length;
        double minV = Double.MAX_VALUE, maxV = -Double.MAX_VALUE;
        for (double[] row : data) for (double v : row) { if (v < minV) minV = v; if (v > maxV) maxV = v; }
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int v = (maxV > minV) ? (int)(255 * (data[y][x] - minV) / (maxV - minV)) : 128;
                v = Math.max(0, Math.min(255, v));
                img.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        new File(path).getParentFile().mkdirs();
        ImageIO.write(img, "png", new File(path));
    }

    // ─────────────────────────────────────────────────────────────────
    //  main
    // ─────────────────────────────────────────────────────────────────
    public static void main(String[] args) throws IOException {
        String outDir = "results2";
        for (int i = 0; i < args.length - 1; i++)
            if ("--outdir".equals(args[i])) outDir = args[i + 1];
        new File(outDir).mkdirs();

        PrintWriter log = new PrintWriter(new FileWriter(outDir + "/log.txt", false));
        log.println("Лабораторная работа №2: Преобразование Фурье");
        log.println("Дата: " + new java.util.Date());
        log.println();

        // ═══════════════════════════════════════════════════════════════
        //  1. ОДНОМЕРНЫЕ СИГНАЛЫ — смесь синусоид
        // ═══════════════════════════════════════════════════════════════
        System.out.println("=== 1D FFT: смесь синусоид ===");
        log.println("=== 1D FFT: смесь синусоид ===");

        int N = 512;
        double fs = 1000.0; // частота дискретизации, Гц
        double[] t = new double[N];
        for (int i = 0; i < N; i++) t[i] = i / fs;

        // Сигнал 1: три синусоиды
        double[] sig1 = new double[N];
        double[] freqs1 = {50, 120, 300};
        double[] amps1  = {1.0, 0.5, 0.25};
        for (int i = 0; i < N; i++)
            for (int k = 0; k < freqs1.length; k++)
                sig1[i] += amps1[k] * Math.sin(2 * Math.PI * freqs1[k] * t[i]);

        Complex[] spec1 = fft1d(sig1, false);
        saveSignalAndSpectrum1d(sig1, spec1,
            outDir + "/signal1_fft.png",
            "Сигнал 1: sin(50Гц) + 0.5·sin(120Гц) + 0.25·sin(300Гц)");

        // IFFT — проверка round-trip
        Complex[] rec1 = ifft1d(spec1);
        double err1 = rmse1d(sig1, rec1, N);
        String msg1 = String.format("  Сигнал 1: RMSE после IFFT = %.2e (должно быть ≈ 0)", err1);
        System.out.println(msg1); log.println(msg1);

        // Сигнал 2: пять синусоид + шум
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
        saveSignalAndSpectrum1d(sig2, spec2,
            outDir + "/signal2_fft.png",
            "Сигнал 2: 5 синусоид (10, 80, 150, 250, 400 Гц) + гауссов шум σ=0.1");

        Complex[] rec2 = ifft1d(spec2);
        double err2 = rmse1d(sig2, rec2, N);
        String msg2 = String.format("  Сигнал 2: RMSE после IFFT = %.2e", err2);
        System.out.println(msg2); log.println(msg2);

        // ═══════════════════════════════════════════════════════════════
        //  2. ДВУМЕРНЫЕ ИЗОБРАЖЕНИЯ из синусоид
        // ═══════════════════════════════════════════════════════════════
        System.out.println("\n=== 2D FFT: синтетические изображения из синусоид ===");
        log.println("\n=== 2D FFT: синтетические изображения из синусоид ===");

        int S = 256;

        // Изображение A: горизонтальная синусоида
        double[][] sinH = new double[S][S];
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++)
                sinH[y][x] = 127 + 127 * Math.sin(2 * Math.PI * 8 * x / S);
        processSynthImage(sinH, outDir, "sin_horizontal", "Горизонтальная синусоида (f=8)", log);

        // Изображение B: вертикальная синусоида
        double[][] sinV = new double[S][S];
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++)
                sinV[y][x] = 127 + 127 * Math.sin(2 * Math.PI * 8 * y / S);
        processSynthImage(sinV, outDir, "sin_vertical", "Вертикальная синусоида (f=8)", log);

        // Изображение C: диагональная синусоида
        double[][] sinD = new double[S][S];
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++)
                sinD[y][x] = 127 + 127 * Math.sin(2 * Math.PI * 6 * (x + y) / S);
        processSynthImage(sinD, outDir, "sin_diagonal", "Диагональная синусоида (f=6)", log);

        // Изображение D: смесь трёх синусоид
        double[][] sinMix = new double[S][S];
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++)
                sinMix[y][x] = 127
                    + 60 * Math.sin(2 * Math.PI * 4 * x / S)
                    + 40 * Math.sin(2 * Math.PI * 12 * y / S)
                    + 27 * Math.sin(2 * Math.PI * 20 * (x + y) / S);
        processSynthImage(sinMix, outDir, "sin_mix",
            "Смесь: sin(4,0) + sin(0,12) + sin(20,20)", log);

        // ═══════════════════════════════════════════════════════════════
        //  3. РЕАЛЬНЫЕ ИЗОБРАЖЕНИЯ
        // ═══════════════════════════════════════════════════════════════
        System.out.println("\n=== 2D FFT: реальные изображения ===");
        log.println("\n=== 2D FFT: реальные изображения ===");

        String[] photoPaths = {
            "examples/checker.png",
            "examples/gradient.png",
            "examples/circles.png",
            "examples/baboon.png",
            "examples/sonoma_photo.jpg"
        };
        String[] photoNames = {"checker", "gradient", "circles", "baboon", "sonoma_photo"};

        for (int i = 0; i < photoPaths.length; i++) {
            File f = new File(photoPaths[i]);
            if (!f.exists()) { log.println("Не найден: " + photoPaths[i]); continue; }
            BufferedImage img = ImageIO.read(f);
            if (img == null) { log.println("Не читается: " + photoPaths[i]); continue; }

            int[][] br = getBrightness(img);
            double[][] dbl = toDouble(br);
            Complex[][] spec = fft2d(dbl, false);
            Complex[][] shifted = fftShift2d(spec);

            String specPath = outDir + "/" + photoNames[i] + "_spectrum.png";
            saveImageAndSpectrum2d(img, spec, specPath,
                photoNames[i] + " — лог-амплитудный спектр Фурье");

            // IFFT round-trip
            Complex[][] rec = fft2d(dbl, true); // используем inverse на оригинале для проверки
            // Правильный round-trip: forward → inverse
            Complex[][] specCopy = fft2d(dbl, false);
            // inverse 2D FFT через transpose trick:
            Complex[][] recImg = ifft2d(specCopy);
            double err = rmse2d(dbl, recImg);
            String m = String.format("  %s: размер=%dx%d, RMSE(IFFT)=%.2e",
                photoNames[i], img.getWidth(), img.getHeight(), err);
            System.out.println(m); log.println(m);

            // Сохранить восстановленное изображение
            double[][] recReal = new double[br.length][br[0].length];
            for (int y = 0; y < br.length; y++)
                for (int x = 0; x < br[0].length; x++)
                    recReal[y][x] = recImg[y][x].re;
            saveDoubleImage(recReal, outDir + "/" + photoNames[i] + "_ifft.png");

            // Спектр Хаара
            double[][] haar = haar2d(dbl, false);
            saveHaarSpectrum2d(haar, outDir + "/" + photoNames[i] + "_haar.png");
            log.println("    Спектр Хаара сохранён: " + outDir + "/" + photoNames[i] + "_haar.png");
        }

        // ═══════════════════════════════════════════════════════════════
        //  4. СПЕКТР ХААРА для 1D сигналов
        // ═══════════════════════════════════════════════════════════════
        System.out.println("\n=== Спектр Хаара (1D) ===");
        log.println("\n=== Спектр Хаара (1D) ===");

        double[] haarCoeffs1 = haar1d(sig1, false);
        saveHaarSpectrum1d(haarCoeffs1, sig1,
            outDir + "/signal1_haar.png",
            "Спектр Хаара — Сигнал 1 (sin(50)+0.5·sin(120)+0.25·sin(300))");

        double[] recHaar1 = haar1d(haarCoeffs1, true);
        double errHaar1 = 0;
        for (int i = 0; i < N; i++) { double d = sig1[i] - recHaar1[i]; errHaar1 += d*d; }
        errHaar1 = Math.sqrt(errHaar1 / N);
        String mh1 = String.format("  Сигнал 1: RMSE после обратного Хаара = %.2e", errHaar1);
        System.out.println(mh1); log.println(mh1);

        double[] haarCoeffs2 = haar1d(sig2, false);
        saveHaarSpectrum1d(haarCoeffs2, sig2,
            outDir + "/signal2_haar.png",
            "Спектр Хаара — Сигнал 2 (5 синусоид + шум)");

        log.println("\nВсё сохранено в " + outDir + "/");
        log.close();

        System.out.println("\nВсе результаты → " + outDir + "/");
    }

    // ─────────────────────────────────────────────────────────────────
    //  Вспомогательный: обработка синтетического double[][] изображения
    // ─────────────────────────────────────────────────────────────────
    static void processSynthImage(double[][] data, String outDir,
                                  String name, String title, PrintWriter log) throws IOException {
        int H = data.length, W = data[0].length;

        // Сохранить исходное
        saveDoubleImage(data, outDir + "/" + name + "_original.png");

        // FFT
        Complex[][] spec = fft2d(data, false);
        Complex[][] shifted = fftShift2d(spec);
        saveSpectrum2d(shifted, outDir + "/" + name + "_spectrum.png");

        // Собрать в одну картинку: оригинал | спектр
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
        g.drawString("Лог-амплитудный спектр (0 Гц в центре)",
            orig.getWidth() + pad + 5, titleH + labelH - 3);
        g.drawImage(orig, 0, titleH + labelH, null);
        g.drawImage(specImg, orig.getWidth() + pad, titleH + labelH, null);
        g.dispose();
        ImageIO.write(combined, "png", new File(outDir + "/" + name + "_combined.png"));

        // IFFT round-trip
        Complex[][] rec = ifft2d(spec);
        double err = rmse2d(data, rec);
        String m = String.format("  %s: RMSE(IFFT) = %.2e", name, err);
        System.out.println(m); log.println(m);

        // Спектр Хаара
        double[][] haar = haar2d(data, false);
        saveHaarSpectrum2d(haar, outDir + "/" + name + "_haar.png");
    }

    // 2D IFFT
    static Complex[][] ifft2d(Complex[][] spectrum) {
        int H = spectrum.length, W = spectrum[0].length;
        // сопрягаем → прямое FFT → сопрягаем → делим на N
        Complex[][] a = new Complex[H][W];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                a[y][x] = new Complex(spectrum[y][x].re, -spectrum[y][x].im);

        // применяем прямое 2D FFT
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
}
