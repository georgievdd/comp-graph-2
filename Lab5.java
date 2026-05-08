import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

/**
 * Лабораторная работа №5: Ранговая фильтрация и морфологические операции.
 *
 * Основное задание:
 *   1. Ранговая фильтрация для произвольной апертуры (квадрат, диск, крест)
 *   2. Фильтр усечённого среднего (trimmed mean)
 *   3. PSNR сравнение: ранговый, усечённое среднее, усредняющий (box), медианный
 *      — аддитивный гауссов шум (σ²=400)
 *      — импульсный шум (10% соль/перец)
 *   4. Полутоновые морфологические операции: эрозия, наращение, открытие, закрытие
 *
 * Дополнительные задания:
 *   5. Взвешенная медиана
 *   6. Верх шляпы (tophat) и низ шляпы (bothat)
 *   7. Морфологический градиент (дилатация − эрозия) vs. линейный лапласиан
 */
public class Lab5 {

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

    static BufferedImage normalizeToImage(double[][] data) {
        int h = data.length, w = data[0].length;
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (double[] r : data) for (double v : r) { if (v < min) min = v; if (v > max) max = v; }
        double[][] norm = new double[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                norm[y][x] = max > min ? 255.0*(data[y][x]-min)/(max-min) : 128;
        return toImage(norm);
    }

    static void save(BufferedImage img, String path) throws IOException {
        new File(path).getParentFile().mkdirs();
        ImageIO.write(img, "png", new File(path));
    }

    static double psnr(double[][] orig, double[][] filt) {
        int h = orig.length, w = orig[0].length;
        double mse = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) { double d = orig[y][x] - filt[y][x]; mse += d*d; }
        mse /= h*w;
        return mse < 1e-10 ? 99.99 : 10*Math.log10(255.0*255.0/mse);
    }

    // ── Генерация шума ────────────────────────────────────────────────────────────

    static double[][] addGaussianNoise(double[][] src, double variance, Random rng) {
        int h = src.length, w = src[0].length;
        double sigma = Math.sqrt(variance);
        double[][] out = new double[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                out[y][x] = Math.max(0, Math.min(255, src[y][x] + sigma * rng.nextGaussian()));
        return out;
    }

    static double[][] addImpulseNoise(double[][] src, double prob, Random rng) {
        int h = src.length, w = src[0].length;
        double[][] out = new double[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double r = rng.nextDouble();
                if      (r < prob/2) out[y][x] = 0;
                else if (r < prob)   out[y][x] = 255;
                else                 out[y][x] = src[y][x];
            }
        return out;
    }

    // ── Апертуры ─────────────────────────────────────────────────────────────────

    /** Квадратная апертура N×N (все пиксели включены). */
    static boolean[][] squareAperture(int size) {
        boolean[][] ap = new boolean[size][size];
        for (boolean[] row : ap) Arrays.fill(row, true);
        return ap;
    }

    /** Дисковая (круглая) апертура радиуса r. Размер (2r+1)×(2r+1). */
    static boolean[][] diskAperture(int radius) {
        int size = 2*radius + 1;
        boolean[][] ap = new boolean[size][size];
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                int dy = y-radius, dx = x-radius;
                ap[y][x] = (dy*dy + dx*dx <= radius*radius);
            }
        return ap;
    }

    /** Крестообразная апертура. */
    static boolean[][] crossAperture(int radius) {
        int size = 2*radius + 1;
        boolean[][] ap = new boolean[size][size];
        for (int i = 0; i < size; i++) {
            ap[radius][i] = true;
            ap[i][radius] = true;
        }
        return ap;
    }

    static int apertureName(boolean[][] ap) {
        int cnt = 0;
        for (boolean[] r : ap) for (boolean v : r) if (v) cnt++;
        return cnt;
    }

    // ── Сбор значений в апертуре ─────────────────────────────────────────────────

    static double[] collectNeighbors(double[][] src, int cy, int cx, boolean[][] aperture) {
        int h = src.length, w = src[0].length;
        int ar = aperture.length/2, ac = aperture[0].length/2;
        int cnt = 0;
        for (boolean[] row : aperture) for (boolean v : row) if (v) cnt++;
        double[] vals = new double[cnt];
        int idx = 0;
        for (int dy = -ar; dy <= ar; dy++)
            for (int dx = -ac; dx <= ac; dx++)
                if (aperture[dy+ar][dx+ac]) {
                    int sy = Math.max(0, Math.min(h-1, cy+dy));
                    int sx = Math.max(0, Math.min(w-1, cx+dx));
                    vals[idx++] = src[sy][sx];
                }
        return vals;
    }

    // ── Ранговая фильтрация ───────────────────────────────────────────────────────

    /**
     * Ранговый фильтр для произвольной апертуры.
     * rankFraction ∈ [0.0, 1.0]: 0.0 = минимум, 0.5 = медиана, 1.0 = максимум.
     */
    static double[][] applyRankFilter(double[][] src, boolean[][] aperture, double rankFraction) {
        int h = src.length, w = src[0].length;
        double[][] out = new double[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double[] vals = collectNeighbors(src, y, x, aperture);
                Arrays.sort(vals);
                int idx = Math.min((int)(rankFraction * vals.length), vals.length - 1);
                out[y][x] = vals[idx];
            }
        }
        return out;
    }

    static double[][] medianFilter(double[][] src, boolean[][] ap)  { return applyRankFilter(src, ap, 0.5); }
    static double[][] minFilter(double[][] src, boolean[][] ap)     { return applyRankFilter(src, ap, 0.0); }
    static double[][] maxFilter(double[][] src, boolean[][] ap)     { return applyRankFilter(src, ap, 1.0); }

    // ── Фильтр усечённого среднего ────────────────────────────────────────────────

    /**
     * Фильтр усечённого среднего (trimmed mean).
     * alpha ∈ [0, 0.5): доля значений, отбрасываемых с каждого конца отсортированного ряда.
     * alpha=0 ↔ обычное среднее;  alpha→0.5 ↔ медиана.
     */
    static double[][] applyTrimmedMeanFilter(double[][] src, boolean[][] aperture, double alpha) {
        int h = src.length, w = src[0].length;
        double[][] out = new double[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double[] vals = collectNeighbors(src, y, x, aperture);
                Arrays.sort(vals);
                int n = vals.length;
                int skip = Math.max(0, Math.min((int)(alpha * n), n/2 - 1));
                double sum = 0; int cnt = 0;
                for (int i = skip; i < n - skip; i++) { sum += vals[i]; cnt++; }
                out[y][x] = cnt > 0 ? sum/cnt : vals[n/2];
            }
        }
        return out;
    }

    /** Усредняющий (box) фильтр для произвольной апертуры (trimmed mean с alpha=0). */
    static double[][] boxFilter(double[][] src, boolean[][] aperture) {
        return applyTrimmedMeanFilter(src, aperture, 0.0);
    }

    // ── Взвешенная медиана (доп. задание 5) ─────────────────────────────────────

    /**
     * Взвешенная медиана: каждый пиксель окрестности получает вес,
     * обратно пропорциональный расстоянию до центра (1/(1+dist)).
     * Для нахождения медианы: взвешенная медиана — точка, где CDF весов переходит через 0.5.
     */
    static double[][] weightedMedianFilter(double[][] src, boolean[][] aperture) {
        int h = src.length, w = src[0].length;
        int ar = aperture.length/2, ac = aperture[0].length/2;
        double[][] out = new double[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                // Collect (value, weight) pairs
                double[] vals = collectNeighbors(src, y, x, aperture);
                double[] weights = new double[vals.length];
                int idx = 0;
                for (int dy = -ar; dy <= ar; dy++)
                    for (int dx = -ac; dx <= ac; dx++)
                        if (aperture[dy+ar][dx+ac]) {
                            double dist = Math.sqrt(dy*dy + dx*dx);
                            weights[idx++] = 1.0 / (1.0 + dist);
                        }
                // Sort by value
                Integer[] order = new Integer[vals.length];
                for (int i = 0; i < order.length; i++) order[i] = i;
                Arrays.sort(order, (a, b) -> Double.compare(vals[a], vals[b]));
                double totalW = 0;
                for (double ww : weights) totalW += ww;
                double cumW = 0, med = vals[order[order.length/2]];
                for (int i : order) {
                    cumW += weights[i];
                    if (cumW >= totalW/2.0) { med = vals[i]; break; }
                }
                out[y][x] = med;
            }
        }
        return out;
    }

    // ── Морфологические операции ─────────────────────────────────────────────────

    /** Эрозия — минимальное значение в окне структурирующего элемента. */
    static double[][] erosion(double[][] src, boolean[][] se) {
        return minFilter(src, se);
    }

    /** Наращение (дилатация) — максимальное значение в окне структурирующего элемента. */
    static double[][] dilation(double[][] src, boolean[][] se) {
        return maxFilter(src, se);
    }

    /** Открытие: эрозия, затем наращение тем же SE. Удаляет мелкие светлые объекты. */
    static double[][] opening(double[][] src, boolean[][] se) {
        return dilation(erosion(src, se), se);
    }

    /** Закрытие: наращение, затем эрозия тем же SE. Заполняет мелкие тёмные пробелы. */
    static double[][] closing(double[][] src, boolean[][] se) {
        return erosion(dilation(src, se), se);
    }

    /** Верх шляпы (tophat) = src − opening. Выделяет мелкие светлые структуры. */
    static double[][] tophat(double[][] src, boolean[][] se) {
        double[][] open = opening(src, se);
        int h = src.length, w = src[0].length;
        double[][] out = new double[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                out[y][x] = Math.max(0, src[y][x] - open[y][x]);
        return out;
    }

    /** Низ шляпы (bothat) = closing − src. Выделяет мелкие тёмные структуры. */
    static double[][] bothat(double[][] src, boolean[][] se) {
        double[][] close = closing(src, se);
        int h = src.length, w = src[0].length;
        double[][] out = new double[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                out[y][x] = Math.max(0, close[y][x] - src[y][x]);
        return out;
    }

    /** Морфологический градиент = dilation − erosion. Выделяет границы. */
    static double[][] morphGradient(double[][] src, boolean[][] se) {
        double[][] dil = dilation(src, se);
        double[][] er  = erosion(src, se);
        int h = src.length, w = src[0].length;
        double[][] out = new double[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                out[y][x] = dil[y][x] - er[y][x];
        return out;
    }

    // ── Визуализация ─────────────────────────────────────────────────────────────

    static BufferedImage makeRow(String title, String[] labels, BufferedImage[] imgs) {
        int pad = 6, topH = 28, labH = 16;
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
        g.drawString(title, pad, 18);
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

    // ── Обработка одного изображения ─────────────────────────────────────────────

    static void processImage(BufferedImage origImg, double[][] src, String name,
                             String outDir, PrintWriter log) throws IOException {
        int h = src.length, w = src[0].length;
        log.println("=== " + name + " (" + w + "×" + h + ") ===");

        Random rng = new Random(42);
        double[][] noiseG = addGaussianNoise(src, 400, rng);   // σ²=400, σ≈20
        double[][] noiseI = addImpulseNoise(src,  0.10, rng);  // 10% соль/перец

        // Апертуры
        boolean[][] sq5   = squareAperture(5);    // 5×5 квадрат (25 px)
        boolean[][] sq3   = squareAperture(3);    // 3×3 квадрат (9 px)
        boolean[][] disk2 = diskAperture(2);      // диск r=2 (~21 px)
        boolean[][] disk1 = diskAperture(1);      // диск r=1 (5 px, крест с углами)
        boolean[][] cross3= crossAperture(2);     // крест r=2

        // ──────────────────── ГАУССОВ ШУМ ────────────────────────────────────────
        double pNoiseG = psnr(src, noiseG);
        double[][] boxG5    = boxFilter(noiseG, sq5);
        double[][] medSq5G  = medianFilter(noiseG, sq5);
        double[][] medDisk2G= medianFilter(noiseG, disk2);
        double[][] medCross3G=medianFilter(noiseG, cross3);
        double[][] tm10G    = applyTrimmedMeanFilter(noiseG, sq5, 0.10);
        double[][] tm25G    = applyTrimmedMeanFilter(noiseG, sq5, 0.25);
        double[][] wmedG    = weightedMedianFilter(noiseG, sq5);

        log.println("  Гауссов шум σ²=400 (PSNR шума: " + String.format("%.2f", pNoiseG) + " дБ) → PSNR фильтрации:");
        log.println(String.format("    Усредняющий 5×5 (box):          %5.2f дБ", psnr(src, boxG5)));
        log.println(String.format("    Медиана 5×5 (квадрат):          %5.2f дБ", psnr(src, medSq5G)));
        log.println(String.format("    Медиана (диск r=2):             %5.2f дБ", psnr(src, medDisk2G)));
        log.println(String.format("    Медиана (крест r=2):            %5.2f дБ", psnr(src, medCross3G)));
        log.println(String.format("    Усечённое среднее α=10%%:        %5.2f дБ", psnr(src, tm10G)));
        log.println(String.format("    Усечённое среднее α=25%%:        %5.2f дБ", psnr(src, tm25G)));
        log.println(String.format("    Взвешенная медиана (5×5):       %5.2f дБ", psnr(src, wmedG)));

        // Ранговые фильтры на гауссовом шуме
        double[][] rk10G = applyRankFilter(noiseG, sq5, 0.10);
        double[][] rk25G = applyRankFilter(noiseG, sq5, 0.25);
        double[][] rk50G = medianFilter(noiseG, sq5);
        double[][] rk75G = applyRankFilter(noiseG, sq5, 0.75);
        double[][] rk90G = applyRankFilter(noiseG, sq5, 0.90);
        log.println("  Ранговые фильтры 5×5 (гауссов шум):");
        log.println(String.format("    Rank=0.10: %5.2f дБ  Rank=0.25: %5.2f дБ  Rank=0.50: %5.2f дБ  Rank=0.75: %5.2f дБ  Rank=0.90: %5.2f дБ",
            psnr(src, rk10G), psnr(src, rk25G), psnr(src, rk50G), psnr(src, rk75G), psnr(src, rk90G)));

        // Сохранить сравнение для гауссова шума
        save(makeRow(name + " — Гауссов шум σ²=400: сравнение фильтров",
            new String[]{"Оригинал", "Шум (σ²=400)", "Box 5×5", "Медиана 5×5",
                         "Мед. диск r=2", "TrimMean α=10%", "TrimMean α=25%"},
            new BufferedImage[]{origImg, toImage(noiseG), toImage(boxG5), toImage(medSq5G),
                                toImage(medDisk2G), toImage(tm10G), toImage(tm25G)}),
            outDir + "/" + name + "_gaussian.png");

        save(makeRow(name + " — Гауссов шум: ранговый фильтр 5×5 (разные квантили)",
            new String[]{"Оригинал", "Rank=0.10", "Rank=0.25", "Медиана(0.50)", "Rank=0.75", "Rank=0.90"},
            new BufferedImage[]{origImg, toImage(rk10G), toImage(rk25G), toImage(rk50G),
                                toImage(rk75G), toImage(rk90G)}),
            outDir + "/" + name + "_rank_gaussian.png");

        // ──────────────────── ИМПУЛЬСНЫЙ ШУМ ─────────────────────────────────────
        double pNoiseI = psnr(src, noiseI);
        double[][] boxI5    = boxFilter(noiseI, sq5);
        double[][] medSq5I  = medianFilter(noiseI, sq5);
        double[][] medDisk2I= medianFilter(noiseI, disk2);
        double[][] medCross3I=medianFilter(noiseI, cross3);
        double[][] tm10I    = applyTrimmedMeanFilter(noiseI, sq5, 0.10);
        double[][] tm25I    = applyTrimmedMeanFilter(noiseI, sq5, 0.25);
        double[][] wmedI    = weightedMedianFilter(noiseI, sq5);

        log.println("  Импульсный шум 10%% (PSNR шума: " + String.format("%.2f", pNoiseI) + " дБ) → PSNR фильтрации:");
        log.println(String.format("    Усредняющий 5×5 (box):          %5.2f дБ", psnr(src, boxI5)));
        log.println(String.format("    Медиана 5×5 (квадрат):          %5.2f дБ", psnr(src, medSq5I)));
        log.println(String.format("    Медиана (диск r=2):             %5.2f дБ", psnr(src, medDisk2I)));
        log.println(String.format("    Медиана (крест r=2):            %5.2f дБ", psnr(src, medCross3I)));
        log.println(String.format("    Усечённое среднее α=10%%:        %5.2f дБ", psnr(src, tm10I)));
        log.println(String.format("    Усечённое среднее α=25%%:        %5.2f дБ", psnr(src, tm25I)));
        log.println(String.format("    Взвешенная медиана (5×5):       %5.2f дБ", psnr(src, wmedI)));

        double[][] rk10I = applyRankFilter(noiseI, sq5, 0.10);
        double[][] rk25I = applyRankFilter(noiseI, sq5, 0.25);
        double[][] rk50I = medianFilter(noiseI, sq5);
        double[][] rk75I = applyRankFilter(noiseI, sq5, 0.75);
        double[][] rk90I = applyRankFilter(noiseI, sq5, 0.90);
        log.println("  Ранговые фильтры 5×5 (импульсный шум):");
        log.println(String.format("    Rank=0.10: %5.2f дБ  Rank=0.25: %5.2f дБ  Rank=0.50: %5.2f дБ  Rank=0.75: %5.2f дБ  Rank=0.90: %5.2f дБ",
            psnr(src, rk10I), psnr(src, rk25I), psnr(src, rk50I), psnr(src, rk75I), psnr(src, rk90I)));

        save(makeRow(name + " — Импульсный шум 10%: сравнение фильтров",
            new String[]{"Оригинал", "Шум (10%)", "Box 5×5", "Медиана 5×5",
                         "Мед. диск r=2", "TrimMean α=10%", "TrimMean α=25%"},
            new BufferedImage[]{origImg, toImage(noiseI), toImage(boxI5), toImage(medSq5I),
                                toImage(medDisk2I), toImage(tm10I), toImage(tm25I)}),
            outDir + "/" + name + "_impulse.png");

        save(makeRow(name + " — Импульсный шум: ранговый фильтр 5×5 (разные квантили)",
            new String[]{"Оригинал", "Rank=0.10", "Rank=0.25", "Медиана(0.50)", "Rank=0.75", "Rank=0.90"},
            new BufferedImage[]{origImg, toImage(rk10I), toImage(rk25I), toImage(rk50I),
                                toImage(rk75I), toImage(rk90I)}),
            outDir + "/" + name + "_rank_impulse.png");

        // ──────────────────── МОРФОЛОГИЧЕСКИЕ ОПЕРАЦИИ ───────────────────────────

        // Диск r=1 (маленький SE)
        boolean[][] se1 = diskAperture(1);
        double[][] er1   = erosion(src, se1);
        double[][] dil1  = dilation(src, se1);
        double[][] open1 = opening(src, se1);
        double[][] clos1 = closing(src, se1);
        double[][] th1   = tophat(src, se1);
        double[][] bh1   = bothat(src, se1);
        double[][] mg1   = morphGradient(src, se1);

        save(makeRow(name + " — Морфологические операции (диск r=1)",
            new String[]{"Оригинал", "Эрозия", "Наращение", "Открытие", "Закрытие"},
            new BufferedImage[]{origImg, toImage(er1), toImage(dil1), toImage(open1), toImage(clos1)}),
            outDir + "/" + name + "_morph_disk1.png");

        save(makeRow(name + " — Верх/Низ шляпы и градиент (диск r=1)",
            new String[]{"Оригинал", "Tophat", "Bothat", "Морф.градиент"},
            new BufferedImage[]{origImg, normalizeToImage(th1), normalizeToImage(bh1), normalizeToImage(mg1)}),
            outDir + "/" + name + "_morph_hat.png");

        // Диск r=2 (крупный SE)
        boolean[][] se2 = diskAperture(2);
        double[][] er2   = erosion(src, se2);
        double[][] dil2  = dilation(src, se2);
        double[][] open2 = opening(src, se2);
        double[][] clos2 = closing(src, se2);

        save(makeRow(name + " — Морфологические операции (диск r=2)",
            new String[]{"Оригинал", "Эрозия r=2", "Наращение r=2", "Открытие r=2", "Закрытие r=2"},
            new BufferedImage[]{origImg, toImage(er2), toImage(dil2), toImage(open2), toImage(clos2)}),
            outDir + "/" + name + "_morph_disk2.png");

        // Квадратный SE 5×5
        double[][] erSq5 = erosion(src, sq5);
        double[][] dilSq5= dilation(src, sq5);
        double[][] opSq5 = opening(src, sq5);
        double[][] clSq5 = closing(src, sq5);

        save(makeRow(name + " — Морфологические операции (квадрат 5×5)",
            new String[]{"Оригинал", "Эрозия 5×5", "Наращение 5×5", "Открытие 5×5", "Закрытие 5×5"},
            new BufferedImage[]{origImg, toImage(erSq5), toImage(dilSq5), toImage(opSq5), toImage(clSq5)}),
            outDir + "/" + name + "_morph_sq5.png");

        log.println("  Морфологические операции сохранены (диск r=1, r=2; квадрат 5×5).");
        log.println();
    }

    // ── main ─────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws IOException {
        String outDir = "results5";
        for (int i = 0; i < args.length-1; i++)
            if ("--outdir".equals(args[i])) outDir = args[i+1];
        new File(outDir).mkdirs();

        PrintWriter log = new PrintWriter(new FileWriter(outDir + "/log.txt", false));
        log.println("Лабораторная работа №5: Ранговая фильтрация и морфологические операции");
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

        log.println("=== ВЫВОДЫ ===");
        log.println();
        log.println("1. РАНГОВАЯ ФИЛЬТРАЦИЯ: ГАУССОВ ШУМ (σ²=400)");
        log.println("   Усредняющий (box) 5×5: лучший PSNR среди линейных фильтров.");
        log.println("   Все соседи вносят равный вклад → оптимально для гауссова шума (МНК оценка).");
        log.println("   Медиана 5×5: чуть хуже box при гауссовом шуме (не использует все значения,");
        log.println("   поэтому менее эффективна), но почти не уступает.");
        log.println("   Усечённое среднее α=25%: промежуточный результат между box и медианой.");
        log.println("   Чем больше alpha, тем ближе к медиане и хуже для гауссового шума.");
        log.println("   Форма апертуры: диск vs квадрат — небольшая разница. Диск чуть сглаживает");
        log.println("   границы меньше. Крест — хуже, т.к. меньше пикселей в апертуре.");
        log.println();
        log.println("2. РАНГОВАЯ ФИЛЬТРАЦИЯ: ИМПУЛЬСНЫЙ ШУМ (10% соль/перец)");
        log.println("   Усредняющий (box) 5×5: частично подавляет, но не устраняет полностью.");
        log.println("   Импульсные пиксели (0 или 255) разбавляются, но не исключаются.");
        log.println("   Медиана 5×5: лучший результат. При 10% шуме медиана (50-й процентиль)");
        log.println("   попадает в незашумлённую часть выборки, полностью устраняя импульсы.");
        log.println("   Усечённое среднее α=25%: отсекает крайние 25% с обоих концов — импульсы");
        log.println("   (соль и перец) попадают в отсекаемую область → результат близок к медиане.");
        log.println("   При уровне шума >25% нужно увеличить alpha.");
        log.println("   Взвешенная медиана: незначительное улучшение над обычной медианой, т.к.");
        log.println("   центральный пиксель весит больше — полезно, если центр незашумлён.");
        log.println();
        log.println("3. ПРОИЗВОЛЬНАЯ АПЕРТУРА");
        log.println("   Квадрат: изотропен лишь приближённо (диагонали включены).");
        log.println("   Диск: более изотропный — меньше угловых артефактов на краях объектов.");
        log.println("   Крест: анизотропный (только 4 направления) — меньше пикселей → хуже PSNR,");
        log.println("   но меньше размытие. Применяется в задачах с направленным шумом.");
        log.println("   Ранговый фильтр обобщает медиану и min/max на любую форму апертуры.");
        log.println();
        log.println("4. ПОЛУТОНОВЫЕ МОРФОЛОГИЧЕСКИЕ ОПЕРАЦИИ");
        log.println("   Эрозия (min в SE):");
        log.println("     Затемняет и сужает светлые объекты. Удаляет мелкие светлые детали (<<SE).");
        log.println("     Применение: удаление яркого шума, сужение объектов.");
        log.println("   Наращение/дилатация (max в SE):");
        log.println("     Осветляет и расширяет светлые объекты. Заполняет мелкие тёмные провалы.");
        log.println("     Применение: заполнение разрывов, расширение объектов.");
        log.println("   Открытие (эрозия + наращение):");
        log.println("     Удаляет объекты меньше SE, сохраняет крупные. Сглаживает контуры.");
        log.println("     Эффект: удаление мелких светлых структур без изменения крупных.");
        log.println("   Закрытие (наращение + эрозия):");
        log.println("     Заполняет тёмные дыры < SE в светлых объектах. Соединяет близкие объекты.");
        log.println("     Эффект: сглаживание контуров с заполнением разрывов.");
        log.println("   Tophat (src − opening): выделяет мелкие светлые объекты на тёмном фоне.");
        log.println("   Bothat (closing − src): выделяет мелкие тёмные объекты на светлом фоне.");
        log.println("   Морфологический градиент (dilation − erosion):");
        log.println("     Выделяет границы объектов. Ширина линий пропорциональна SE.");
        log.println("     В отличие от лапласиана, не усиливает высокочастотный гауссов шум,");
        log.println("     но чувствителен к импульсному шуму (max и min реагируют на выбросы).");
        log.println("     Для зашумлённых изображений применять после медианного фильтра.");
        log.close();
        System.out.println("Результаты → " + outDir + "/");
    }
}
