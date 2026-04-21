import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

/**
 * Лабораторная работа №4: Свёртка.
 *
 * Основное задание:
 *   - 2D свёртка для произвольного ядра
 *   - ФНЧ: усредняющий (box) и гауссовый фильтры
 *   - Усредняющий фильтр с порогом
 *   - ФВЧ: лапласиан и LoG
 *   - Детектирование границ через переходы нулевого уровня (LoG)
 *   - Фильтр повышения резкости (unsharp masking)
 *
 * Дополнительные задания:
 *   1. Усредняющий фильтр через интегральное изображение
 *   2. Билатеральный фильтр
 *   3. Нелинейный ФВЧ (морфологический градиент)
 *   4. Подбор оптимального sigma гауссова фильтра по PSNR
 */
public class Lab4 {

    // ── Вспомогательные функции ──────────────────────────────────────────────────

    static int[][] getBrightness(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int[][] b = new int[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                b[y][x] = (int)(0.299*((rgb>>16)&0xFF) + 0.587*((rgb>>8)&0xFF) + 0.114*(rgb&0xFF));
            }
        return b;
    }

    static double[][] toDouble(int[][] a) {
        int h = a.length, w = a[0].length;
        double[][] d = new double[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) d[y][x] = a[y][x];
        return d;
    }

    static BufferedImage toImage(double[][] p) {
        int h = p.length, w = p[0].length;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int v = Math.max(0, Math.min(255, (int)Math.round(p[y][x])));
                img.setRGB(x, y, v * 0x10101);
            }
        return img;
    }

    static void save(BufferedImage img, String path) throws IOException {
        new File(path).getParentFile().mkdirs();
        ImageIO.write(img, "png", new File(path));
    }

    static double psnr(double[][] orig, double[][] filt) {
        int h = orig.length, w = orig[0].length;
        double mse = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) { double d = orig[y][x]-filt[y][x]; mse += d*d; }
        mse /= h*w;
        return mse < 1e-10 ? 99.99 : 10*Math.log10(255.0*255.0/mse);
    }

    // ── Шум ──────────────────────────────────────────────────────────────────────

    /** Аддитивный гауссов шум с заданной дисперсией. */
    static double[][] addGaussianNoise(double[][] src, double variance, Random rng) {
        int h = src.length, w = src[0].length;
        double sigma = Math.sqrt(variance);
        double[][] out = new double[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                out[y][x] = src[y][x] + sigma * rng.nextGaussian();
        return out;
    }

    /** Импульсный шум (соль и перец). prob — вероятность засветки/затемнения каждого пикселя. */
    static double[][] addImpulseNoise(double[][] src, double prob, Random rng) {
        int h = src.length, w = src[0].length;
        double[][] out = new double[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double r = rng.nextDouble();
                if (r < prob/2)       out[y][x] = 0;
                else if (r < prob)    out[y][x] = 255;
                else                  out[y][x] = src[y][x];
            }
        return out;
    }

    // ── 2D свёртка (основная операция) ───────────────────────────────────────────

    /**
     * 2D свёртка с зеркальным дополнением границ.
     * kernel[ky][kx], ядро произвольного нечётного размера.
     */
    static double[][] convolve(double[][] src, double[][] kernel) {
        int h = src.length, w = src[0].length;
        int kh = kernel.length, kw = kernel[0].length;
        int ry = kh/2, rx = kw/2;
        double[][] out = new double[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double sum = 0;
                for (int ky = 0; ky < kh; ky++)
                    for (int kx = 0; kx < kw; kx++) {
                        int sy = Math.abs(y - ry + ky);
                        int sx = Math.abs(x - rx + kx);
                        if (sy >= h) sy = 2*h - sy - 2;
                        if (sx >= w) sx = 2*w - sx - 2;
                        sy = Math.max(0, Math.min(h-1, sy));
                        sx = Math.max(0, Math.min(w-1, sx));
                        sum += kernel[ky][kx] * src[sy][sx];
                    }
                out[y][x] = sum;
            }
        return out;
    }

    // ── Ядра фильтров ─────────────────────────────────────────────────────────────

    static double[][] boxKernel(int size) {
        double[][] k = new double[size][size];
        double v = 1.0/(size*size);
        for (double[] row : k) Arrays.fill(row, v);
        return k;
    }

    static double[][] gaussianKernel(int size, double sigma) {
        double[][] k = new double[size][size];
        int r = size/2;
        double sum = 0;
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                double dy = y-r, dx = x-r;
                k[y][x] = Math.exp(-(dx*dx+dy*dy)/(2*sigma*sigma));
                sum += k[y][x];
            }
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) k[y][x] /= sum;
        return k;
    }

    static double[][] laplacianKernel() {
        return new double[][]{{0,1,0},{1,-4,1},{0,1,0}};
    }

    static double[][] laplacian8Kernel() {
        return new double[][]{{1,1,1},{1,-8,1},{1,1,1}};
    }

    static double[][] logKernel(int size, double sigma) {
        double[][] k = new double[size][size];
        int r = size/2;
        double s2 = sigma*sigma;
        double sum = 0;
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                double dy = y-r, dx = x-r;
                double r2 = dx*dx+dy*dy;
                k[y][x] = -(1 - r2/(2*s2)) * Math.exp(-r2/(2*s2));
                sum += k[y][x];
            }
        // нормализуем, чтобы сумма равнялась 0 (DC-free)
        double mean = sum/(size*size);
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) k[y][x] -= mean;
        return k;
    }

    // ── ФНЧ ──────────────────────────────────────────────────────────────────────

    static double[][] boxFilter(double[][] src, int size) {
        return convolve(src, boxKernel(size));
    }

    static double[][] gaussianFilter(double[][] src, int size, double sigma) {
        return convolve(src, gaussianKernel(size, sigma));
    }

    /** Усредняющий фильтр с порогом: пиксель включается в среднее только если |разность| < threshold. */
    static double[][] thresholdAverageFilter(double[][] src, int size, double threshold) {
        int h = src.length, w = src[0].length;
        int r = size/2;
        double[][] out = new double[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double center = src[y][x];
                double sum = 0; int cnt = 0;
                for (int dy = -r; dy <= r; dy++)
                    for (int dx = -r; dx <= r; dx++) {
                        int sy = Math.max(0, Math.min(h-1, y+dy));
                        int sx = Math.max(0, Math.min(w-1, x+dx));
                        double v = src[sy][sx];
                        if (Math.abs(v - center) < threshold) { sum += v; cnt++; }
                    }
                out[y][x] = cnt > 0 ? sum/cnt : center;
            }
        return out;
    }

    // ── ФВЧ: лапласиан и LoG ─────────────────────────────────────────────────────

    static double[][] laplacianFilter(double[][] src) {
        return convolve(src, laplacian8Kernel());
    }

    static double[][] logFilter(double[][] src, int size, double sigma) {
        return convolve(src, logKernel(size, sigma));
    }

    /** Детектирование границ через переходы нулевого уровня (zero-crossing) LoG. */
    static double[][] zeroCrossing(double[][] log) {
        int h = log.length, w = log[0].length;
        double[][] edges = new double[h][w];
        for (int y = 1; y < h-1; y++)
            for (int x = 1; x < w-1; x++) {
                double v = log[y][x];
                boolean zc =
                    (v * log[y][x-1] < 0) || (v * log[y][x+1] < 0) ||
                    (v * log[y-1][x] < 0) || (v * log[y+1][x] < 0);
                edges[y][x] = zc ? 255 : 0;
            }
        return edges;
    }

    // ── Повышение резкости (unsharp masking) ─────────────────────────────────────

    /** Unsharp masking: sharp = src + amount*(src - blur). */
    static double[][] sharpen(double[][] src, double sigma, double amount) {
        double[][] blur = gaussianFilter(src, kernelSize(sigma), sigma);
        int h = src.length, w = src[0].length;
        double[][] out = new double[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                out[y][x] = src[y][x] + amount*(src[y][x] - blur[y][x]);
        return out;
    }

    static int kernelSize(double sigma) {
        int s = (int)Math.ceil(6*sigma) | 1; // нечётный
        return Math.max(3, s);
    }

    // ── Дополнительное задание 1: box-фильтр через интегральное изображение ──────

    static double[][] integralBoxFilter(double[][] src, int size) {
        int h = src.length, w = src[0].length;
        int r = size / 2;
        // Зеркальное дополнение — чтобы граничная обработка совпадала с convolve().
        int ph = h + 2*r, pw = w + 2*r;
        double[][] padded = new double[ph][pw];
        for (int y = 0; y < ph; y++)
            for (int x = 0; x < pw; x++) {
                int sy = Math.abs(y - r);
                int sx = Math.abs(x - r);
                if (sy >= h) sy = 2*h - sy - 2;
                if (sx >= w) sx = 2*w - sx - 2;
                sy = Math.max(0, Math.min(h-1, sy));
                sx = Math.max(0, Math.min(w-1, sx));
                padded[y][x] = src[sy][sx];
            }
        // Интегральное изображение по дополненному массиву.
        double[][] ii = new double[ph+1][pw+1];
        for (int y = 0; y < ph; y++)
            for (int x = 0; x < pw; x++)
                ii[y+1][x+1] = padded[y][x] + ii[y][x+1] + ii[y+1][x] - ii[y][x];
        double[][] out = new double[h][w];
        double area = size * size;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                // Окно [y..y+size-1][x..x+size-1] в padded-координатах.
                int y2 = y + size, x2 = x + size;
                double s = ii[y2][x2] - ii[y][x2] - ii[y2][x] + ii[y][x];
                out[y][x] = s / area;
            }
        return out;
    }

    // ── Дополнительное задание 2: билатеральный фильтр ───────────────────────────

    static double[][] bilateralFilter(double[][] src, int size, double sigmaS, double sigmaR) {
        int h = src.length, w = src[0].length;
        int r = size/2;
        double s2 = 2*sigmaS*sigmaS, r2 = 2*sigmaR*sigmaR;
        double[][] out = new double[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double sum = 0, wsum = 0;
                double center = src[y][x];
                for (int dy = -r; dy <= r; dy++)
                    for (int dx = -r; dx <= r; dx++) {
                        int sy = Math.max(0, Math.min(h-1, y+dy));
                        int sx = Math.max(0, Math.min(w-1, x+dx));
                        double v = src[sy][sx];
                        double ws = Math.exp(-(dx*dx+dy*dy)/s2);
                        double wr = Math.exp(-(v-center)*(v-center)/r2);
                        double wt = ws*wr;
                        sum += wt*v; wsum += wt;
                    }
                out[y][x] = sum/wsum;
            }
        return out;
    }

    // ── Дополнительное задание 3: нелинейный ФВЧ (морфологический градиент) ──────

    static double[][] morphGradient(double[][] src, int size) {
        int h = src.length, w = src[0].length;
        int r = size/2;
        double[][] dilate = new double[h][w];
        double[][] erode  = new double[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double maxV = -Double.MAX_VALUE, minV = Double.MAX_VALUE;
                for (int dy = -r; dy <= r; dy++)
                    for (int dx = -r; dx <= r; dx++) {
                        double v = src[Math.max(0,Math.min(h-1,y+dy))][Math.max(0,Math.min(w-1,x+dx))];
                        if (v > maxV) maxV = v;
                        if (v < minV) minV = v;
                    }
                dilate[y][x] = maxV;
                erode[y][x]  = minV;
            }
        double[][] out = new double[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) out[y][x] = dilate[y][x] - erode[y][x];
        return out;
    }

    // ── Дополнительное задание 4: подбор оптимального sigma по PSNR ──────────────

    static double findBestSigma(double[][] clean, double[][] noisy) {
        double bestPsnr = -1, bestSigma = 1.0;
        for (double sigma = 0.5; sigma <= 5.0; sigma += 0.25) {
            double[][] filtered = gaussianFilter(noisy, kernelSize(sigma), sigma);
            double p = psnr(clean, filtered);
            if (p > bestPsnr) { bestPsnr = p; bestSigma = sigma; }
        }
        return bestSigma;
    }

    // ── Сохранение сравнительных изображений ─────────────────────────────────────

    /** Строка с N панелями: заголовок + подписи + изображения. */
    static BufferedImage row(String title, String[] labels, BufferedImage[] imgs) {
        int pad = 6, topH = 26, labH = 16;
        int maxH = 0;
        for (BufferedImage img : imgs) if (img.getHeight() > maxH) maxH = img.getHeight();
        int totalW = pad;
        for (BufferedImage img : imgs) totalW += img.getWidth() + pad;
        BufferedImage out = new BufferedImage(totalW, topH+labH+maxH+pad, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE); g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.drawString(title, pad, 17);
        g.setFont(new Font("SansSerif", Font.PLAIN, 9));
        int px = pad;
        for (int i = 0; i < imgs.length; i++) {
            g.drawString(labels[i], px, topH+labH-3);
            g.drawImage(imgs[i], px, topH+labH, null);
            px += imgs[i].getWidth() + pad;
        }
        g.dispose();
        return out;
    }

    static BufferedImage normalizeToImage(double[][] data) {
        int h = data.length, w = data[0].length;
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (double[] r : data) for (double v : r) { if (v<min) min=v; if (v>max) max=v; }
        double[][] norm = new double[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                norm[y][x] = max > min ? 255*(data[y][x]-min)/(max-min) : 128;
        return toImage(norm);
    }

    // ── Обработка одного изображения ─────────────────────────────────────────────

    static void processImage(BufferedImage origImg, double[][] src, String name,
                             String outDir, PrintWriter log) throws IOException {
        int h = src.length, w = src[0].length;
        log.println("=== " + name + " (" + w + "×" + h + ") ===");

        Random rng = new Random(42);
        double[][] noiseG = addGaussianNoise(src, 400, rng);     // σ²=400, σ≈20
        double[][] noiseI = addImpulseNoise(src, 0.1, rng);      // 10% соль/перец

        // ── ФНЧ на гауссовом шуме ────────────────────────────────────────────────
        double[][] boxG3    = boxFilter(noiseG, 3);
        double[][] boxG7    = boxFilter(noiseG, 7);
        double[][] gaussG   = gaussianFilter(noiseG, 9, 2.0);
        double[][] thrG     = thresholdAverageFilter(noiseG, 5, 30);

        log.println("  Гауссов шум (σ²=400) → PSNR фильтрации:");
        log.println(String.format("    Box 3×3:       %.2f dB", psnr(src, boxG3)));
        log.println(String.format("    Box 7×7:       %.2f dB", psnr(src, boxG7)));
        log.println(String.format("    Gauss σ=2:     %.2f dB", psnr(src, gaussG)));
        log.println(String.format("    Threshold(t=30): %.2f dB", psnr(src, thrG)));

        save(row(name + " — ФНЧ, гауссов шум (σ²=400)",
            new String[]{"Оригинал", "Шум", "Box 3×3", "Box 7×7", "Gauss σ=2", "Порог t=30"},
            new BufferedImage[]{origImg, toImage(noiseG), toImage(boxG3), toImage(boxG7),
                                toImage(gaussG), toImage(thrG)}),
            outDir + "/" + name + "_lpf_gaussian.png");

        // ── ФНЧ на импульсном шуме ───────────────────────────────────────────────
        double[][] boxI3    = boxFilter(noiseI, 3);
        double[][] boxI7    = boxFilter(noiseI, 7);
        double[][] gaussI   = gaussianFilter(noiseI, 9, 2.0);
        double[][] thrI     = thresholdAverageFilter(noiseI, 5, 50);

        log.println("  Импульсный шум (10%) → PSNR фильтрации:");
        log.println(String.format("    Box 3×3:       %.2f dB", psnr(src, boxI3)));
        log.println(String.format("    Box 7×7:       %.2f dB", psnr(src, boxI7)));
        log.println(String.format("    Gauss σ=2:     %.2f dB", psnr(src, gaussI)));
        log.println(String.format("    Threshold(t=50): %.2f dB", psnr(src, thrI)));

        save(row(name + " — ФНЧ, импульсный шум (10%)",
            new String[]{"Оригинал", "Шум", "Box 3×3", "Box 7×7", "Gauss σ=2", "Порог t=50"},
            new BufferedImage[]{origImg, toImage(noiseI), toImage(boxI3), toImage(boxI7),
                                toImage(gaussI), toImage(thrI)}),
            outDir + "/" + name + "_lpf_impulse.png");

        // ── ФВЧ: лапласиан, LoG, zero-crossing, резкость ────────────────────────
        double[][] lap     = laplacianFilter(src);
        double[][] logImg  = logFilter(src, 11, 2.0);
        double[][] zeroCr  = zeroCrossing(logImg);
        double[][] sharp   = sharpen(src, 1.5, 1.5);

        save(row(name + " — ФВЧ и повышение резкости",
            new String[]{"Оригинал", "Лапласиан", "LoG σ=2", "Zero-crossing", "Резкость ×1.5"},
            new BufferedImage[]{origImg, normalizeToImage(lap), normalizeToImage(logImg),
                                toImage(zeroCr), toImage(sharp)}),
            outDir + "/" + name + "_hpf.png");

        log.println("  ФВЧ сохранён.");

        // ── Доп. 1: интегральное изображение ────────────────────────────────────
        double[][] intBox = integralBoxFilter(src, 7);
        double[][] convBox = boxFilter(src, 7);
        log.println(String.format("  Интегральный box 7×7: PSNR vs свёртка = %.1f dB", psnr(convBox, intBox)));

        // ── Доп. 2: билатеральный фильтр ─────────────────────────────────────────
        double[][] bilG = bilateralFilter(noiseG, 9, 3.0, 30.0);
        double[][] bilI = bilateralFilter(noiseI, 9, 3.0, 50.0);
        log.println(String.format("  Bilateral (гауссов шум):  PSNR=%.2f dB", psnr(src, bilG)));
        log.println(String.format("  Bilateral (импульс. шум): PSNR=%.2f dB", psnr(src, bilI)));

        save(row(name + " — Билатеральный фильтр",
            new String[]{"Оригинал", "Гауссов шум", "Bilateral", "Импульс. шум", "Bilateral"},
            new BufferedImage[]{origImg, toImage(noiseG), toImage(bilG), toImage(noiseI), toImage(bilI)}),
            outDir + "/" + name + "_bilateral.png");

        // ── Доп. 3: нелинейный ФВЧ (морфологический градиент) ────────────────────
        double[][] morphG3 = morphGradient(src, 3);
        double[][] morphG5 = morphGradient(src, 5);

        save(row(name + " — Нелинейный ФВЧ (морфологический градиент)",
            new String[]{"Оригинал", "Лапласиан (лин.)", "Морф. градиент 3×3", "Морф. градиент 5×5"},
            new BufferedImage[]{origImg, normalizeToImage(lap), normalizeToImage(morphG3), normalizeToImage(morphG5)}),
            outDir + "/" + name + "_morph_hpf.png");

        log.println("  Морф. градиент сохранён.");

        // ── Доп. 4: подбор sigma по PSNR ─────────────────────────────────────────
        double bestSigmaG = findBestSigma(src, noiseG);
        double bestSigmaI = findBestSigma(src, noiseI);
        double[][] bestFiltG = gaussianFilter(noiseG, kernelSize(bestSigmaG), bestSigmaG);
        double[][] bestFiltI = gaussianFilter(noiseI, kernelSize(bestSigmaI), bestSigmaI);
        log.println(String.format("  Оптимальный sigma (гауссов шум):  σ=%.2f  PSNR=%.2f dB", bestSigmaG, psnr(src, bestFiltG)));
        log.println(String.format("  Оптимальный sigma (импульс. шум): σ=%.2f  PSNR=%.2f dB", bestSigmaI, psnr(src, bestFiltI)));

        save(row(name + " — Оптимальный гауссов фильтр (по PSNR)",
            new String[]{"Оригинал",
                         "Гауссов шум → Gauss σ=" + String.format("%.2f", bestSigmaG),
                         "Импульс. шум → Gauss σ=" + String.format("%.2f", bestSigmaI)},
            new BufferedImage[]{origImg, toImage(bestFiltG), toImage(bestFiltI)}),
            outDir + "/" + name + "_optimal_sigma.png");

        log.println();
    }

    // ── main ─────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws IOException {
        String outDir = "results4";
        for (int i = 0; i < args.length-1; i++)
            if ("--outdir".equals(args[i])) outDir = args[i+1];
        new File(outDir).mkdirs();

        PrintWriter log = new PrintWriter(new FileWriter(outDir + "/log.txt", false));
        log.println("Лабораторная работа №4: Свёртка");
        log.println("Дата: " + new java.util.Date());
        log.println();

        String[] paths = {"examples/baboon.png", "examples/circles.png",
                          "examples/gradient.png", "examples/sonoma_photo.jpg"};
        String[] names = {"baboon", "circles", "gradient", "sonoma_photo"};

        for (int i = 0; i < paths.length; i++) {
            File f = new File(paths[i]);
            if (!f.exists()) { log.println("Не найден: " + paths[i]); continue; }
            BufferedImage img = ImageIO.read(f);
            if (img == null) { log.println("Не читается: " + paths[i]); continue; }
            processImage(img, toDouble(getBrightness(img)), names[i], outDir, log);
        }

        log.println("Все результаты сохранены в " + outDir + "/");
        log.close();
        System.out.println("Все результаты → " + outDir + "/");
    }
}
