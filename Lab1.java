import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

/**
 * Лабораторная работа №1: Анализ изображений.
 *
 * Запуск:
 *   java Lab1 <изображение1> [изображение2 ...] [--outdir <папка_результатов>]
 *
 * Если изображения не переданы — обрабатываются все PNG из папки examples/.
 * По умолчанию результаты пишутся в папку results/.
 *
 * Для каждого изображения:
 *   - вычисляются все статистики и записываются в results/statistics.txt
 *   - GLCM сохраняется как PNG (логарифмическая шкала яркости)
 *   - строится график PSNR(дисперсия) и сохраняется как PNG
 *   - сохраняется зашумлённая версия (дисперсия 400)
 */
public class Lab1 {

    // ─────────────────────────────────────────────────────────────────
    //  Яркость (luminance BT.601)
    // ─────────────────────────────────────────────────────────────────
    public static int[][] getBrightness(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        int[][] brightness = new int[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                brightness[y][x] = (int) (0.299 * r + 0.587 * g + 0.114 * b);
            }
        return brightness;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Гистограмма
    // ─────────────────────────────────────────────────────────────────
    public static int[] histogram(int[][] brightness) {
        int[] hist = new int[256];
        for (int[] row : brightness)
            for (int v : row)
                hist[v]++;
        return hist;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Среднее
    // ─────────────────────────────────────────────────────────────────
    public static double mean(int[][] brightness) {
        long sum = 0;
        int count = 0;
        for (int[] row : brightness)
            for (int v : row) { sum += v; count++; }
        return (double) sum / count;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Дисперсия
    // ─────────────────────────────────────────────────────────────────
    public static double variance(int[][] brightness) {
        double m = mean(brightness);
        double sum = 0;
        int count = 0;
        for (int[] row : brightness)
            for (int v : row) { double d = v - m; sum += d * d; count++; }
        return sum / count;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Квартили
    // ─────────────────────────────────────────────────────────────────
    public static double[] quartiles(int[][] brightness) {
        int[] sorted = flatten(brightness);
        Arrays.sort(sorted);
        return new double[]{
            percentile(sorted, 0.25),
            percentile(sorted, 0.50),
            percentile(sorted, 0.75)
        };
    }

    private static double percentile(int[] sorted, double p) {
        double idx = p * (sorted.length - 1);
        int lo = (int) Math.floor(idx), hi = (int) Math.ceil(idx);
        if (lo == hi) return sorted[lo];
        return sorted[lo] + (idx - lo) * (sorted[hi] - sorted[lo]);
    }

    private static int[] flatten(int[][] arr) {
        int total = 0;
        for (int[] row : arr) total += row.length;
        int[] flat = new int[total];
        int idx = 0;
        for (int[] row : arr) for (int v : row) flat[idx++] = v;
        return flat;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Энтропия
    // ─────────────────────────────────────────────────────────────────
    public static double entropy(int[][] brightness) {
        int[] hist = histogram(brightness);
        int total = 0;
        for (int c : hist) total += c;
        double ent = 0;
        for (int c : hist) {
            if (c == 0) continue;
            double p = (double) c / total;
            ent -= p * (Math.log(p) / Math.log(2));
        }
        return ent;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Энергия гистограммы
    // ─────────────────────────────────────────────────────────────────
    public static double energy(int[][] brightness) {
        int[] hist = histogram(brightness);
        int total = 0;
        for (int c : hist) total += c;
        double e = 0;
        for (int c : hist) { double p = (double) c / total; e += p * p; }
        return e;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Асимметрия (skewness)
    // ─────────────────────────────────────────────────────────────────
    public static double skewness(int[][] brightness) {
        double m = mean(brightness);
        double sigma = Math.sqrt(variance(brightness));
        if (sigma == 0) return 0;
        double sum = 0; int count = 0;
        for (int[] row : brightness)
            for (int v : row) { double d = (v - m) / sigma; sum += d * d * d; count++; }
        return sum / count;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Эксцесс (excess kurtosis)
    // ─────────────────────────────────────────────────────────────────
    public static double kurtosis(int[][] brightness) {
        double m = mean(brightness);
        double sigma = Math.sqrt(variance(brightness));
        if (sigma == 0) return 0;
        double sum = 0; int count = 0;
        for (int[] row : brightness)
            for (int v : row) { double d = (v - m) / sigma; sum += d * d * d * d; count++; }
        return sum / count - 3.0;
    }

    // ─────────────────────────────────────────────────────────────────
    //  GLCM (матрица совместной встречаемости)
    // ─────────────────────────────────────────────────────────────────
    public static double[][] glcm(int[][] brightness, int dr, int dc) {
        int levels = 256;
        double[][] matrix = new double[levels][levels];
        int count = 0;
        int h = brightness.length, w = brightness[0].length;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int ny = y + dr, nx = x + dc;
                if (ny >= 0 && ny < h && nx >= 0 && nx < w) {
                    matrix[brightness[y][x]][brightness[ny][nx]]++;
                    count++;
                }
            }
        if (count > 0)
            for (int i = 0; i < levels; i++)
                for (int j = 0; j < levels; j++)
                    matrix[i][j] /= count;
        return matrix;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Энергия GLCM
    // ─────────────────────────────────────────────────────────────────
    public static double glcmEnergy(double[][] glcm) {
        double e = 0;
        for (double[] row : glcm) for (double v : row) e += v * v;
        return e;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Сохранение GLCM как изображение (логарифмическая шкала яркости)
    //
    //  Идея: значения вблизи диагонали намного больше остальных,
    //  поэтому линейная шкала показала бы только диагональ. Логарифм
    //  log(1 + k*p) «растягивает» малые значения, делая видимой
    //  структуру матрицы (пики вдоль диагонали для однородных текстур,
    //  размытие для зашумлённых, широкие полосы для градиентных).
    // ─────────────────────────────────────────────────────────────────
    public static void saveGlcmImage(double[][] glcm, String path) throws IOException {
        int levels = glcm.length;
        final double K = 1e4; // масштаб перед логарифмом
        // найдём максимум для нормировки
        double maxLog = 0;
        for (double[] row : glcm)
            for (double v : row) {
                double lv = Math.log(1 + K * v);
                if (lv > maxLog) maxLog = lv;
            }
        if (maxLog == 0) maxLog = 1;

        BufferedImage img = new BufferedImage(levels, levels, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < levels; i++)
            for (int j = 0; j < levels; j++) {
                int bright = (int) (255 * Math.log(1 + K * glcm[i][j]) / maxLog);
                bright = Math.max(0, Math.min(255, bright));
                img.setRGB(j, i, (bright << 16) | (bright << 8) | bright);
            }
        new File(path).getParentFile().mkdirs();
        ImageIO.write(img, "png", new File(path));
    }

    // ─────────────────────────────────────────────────────────────────
    //  Добавление AWGN
    // ─────────────────────────────────────────────────────────────────
    public static BufferedImage addGaussianNoise(BufferedImage image, double noiseVariance) {
        Random rng = new Random(42);
        double sigma = Math.sqrt(noiseVariance);
        int w = image.getWidth(), h = image.getHeight();
        BufferedImage noisy = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int rgb = image.getRGB(x, y);
                int r = clamp((int) (((rgb >> 16) & 0xFF) + rng.nextGaussian() * sigma));
                int g = clamp((int) (((rgb >> 8) & 0xFF) + rng.nextGaussian() * sigma));
                int b = clamp((int) ((rgb & 0xFF) + rng.nextGaussian() * sigma));
                noisy.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        return noisy;
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    // ─────────────────────────────────────────────────────────────────
    //  PSNR
    // ─────────────────────────────────────────────────────────────────
    public static double psnr(BufferedImage original, BufferedImage noisy) {
        int w = original.getWidth(), h = original.getHeight();
        double mse = 0; int count = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int rgb1 = original.getRGB(x, y), rgb2 = noisy.getRGB(x, y);
                for (int shift = 0; shift <= 16; shift += 8) {
                    int c1 = (rgb1 >> shift) & 0xFF, c2 = (rgb2 >> shift) & 0xFF;
                    mse += (c1 - c2) * (c1 - c2); count++;
                }
            }
        mse /= count;
        if (mse == 0) return Double.POSITIVE_INFINITY;
        return 10.0 * Math.log10(255.0 * 255.0 / mse);
    }

    // ─────────────────────────────────────────────────────────────────
    //  График PSNR(дисперсия шума) — сохраняется как PNG
    //
    //  По теории: PSNR ≈ 10·log10(255²/σ²) + const,
    //  т.е. линейно убывает (в дБ) при росте σ² (в дБ).
    //  На практике из-за клиппирования наклон немного меньше −10 dB/dec.
    // ─────────────────────────────────────────────────────────────────
    public static void savePsnrPlot(BufferedImage original, String path) throws IOException {
        // диапазон дисперсий: от 1 до 4000, 40 точек
        int nPoints = 40;
        double[] variances = new double[nPoints];
        double[] psnrValues = new double[nPoints];
        double vMin = 1, vMax = 4000;
        for (int i = 0; i < nPoints; i++) {
            // логарифмическая шкала по оси X для равномерного покрытия
            variances[i] = Math.exp(Math.log(vMin) + i * (Math.log(vMax) - Math.log(vMin)) / (nPoints - 1));
            psnrValues[i] = psnr(original, addGaussianNoise(original, variances[i]));
        }

        // Размеры холста
        int W = 800, H = 500;
        int left = 80, right = 40, top = 50, bottom = 70;
        int plotW = W - left - right, plotH = H - top - bottom;

        BufferedImage chart = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = chart.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Фон
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);

        // Определяем диапазон Y
        double psnrMin = Double.MAX_VALUE, psnrMax = -Double.MAX_VALUE;
        for (double p : psnrValues) {
            if (Double.isFinite(p)) { psnrMin = Math.min(psnrMin, p); psnrMax = Math.max(psnrMax, p); }
        }
        psnrMin = Math.floor(psnrMin / 5) * 5;
        psnrMax = Math.ceil(psnrMax / 5) * 5 + 5;

        // Сетка и подписи Y
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.setColor(new Color(220, 220, 220));
        int ySteps = (int) ((psnrMax - psnrMin) / 5);
        for (int i = 0; i <= ySteps; i++) {
            double pVal = psnrMin + i * 5;
            int py = top + plotH - (int) ((pVal - psnrMin) / (psnrMax - psnrMin) * plotH);
            g.drawLine(left, py, left + plotW, py);
            g.setColor(Color.DARK_GRAY);
            g.drawString(String.format("%.0f", pVal), left - 38, py + 4);
            g.setColor(new Color(220, 220, 220));
        }

        // Подписи X (логарифмическая шкала)
        double[] xTicks = {1, 5, 10, 50, 100, 500, 1000, 2000, 4000};
        g.setColor(Color.DARK_GRAY);
        for (double xt : xTicks) {
            int px = left + (int) ((Math.log(xt) - Math.log(vMin)) / (Math.log(vMax) - Math.log(vMin)) * plotW);
            if (px < left || px > left + plotW) continue;
            g.setColor(new Color(220, 220, 220));
            g.drawLine(px, top, px, top + plotH);
            g.setColor(Color.DARK_GRAY);
            g.drawString(String.valueOf((int) xt), px - 10, top + plotH + 18);
        }

        // Оси
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRect(left, top, plotW, plotH);

        // Кривая
        g.setColor(new Color(30, 100, 200));
        g.setStroke(new BasicStroke(2.0f));
        int[] xs = new int[nPoints], ys = new int[nPoints];
        for (int i = 0; i < nPoints; i++) {
            xs[i] = left + (int) ((Math.log(variances[i]) - Math.log(vMin)) / (Math.log(vMax) - Math.log(vMin)) * plotW);
            double p = Double.isFinite(psnrValues[i]) ? psnrValues[i] : psnrMax;
            ys[i] = top + plotH - (int) ((p - psnrMin) / (psnrMax - psnrMin) * plotH);
        }
        for (int i = 0; i < nPoints - 1; i++)
            g.drawLine(xs[i], ys[i], xs[i + 1], ys[i + 1]);

        // Точки
        g.setColor(new Color(200, 50, 50));
        for (int i = 0; i < nPoints; i++)
            g.fillOval(xs[i] - 3, ys[i] - 3, 7, 7);

        // Подписи осей
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.drawString("PSNR (дБ)", 8, top + plotH / 2 + 5);
        g.drawString("Дисперсия шума σ²", left + plotW / 2 - 70, H - 15);

        // Заголовок
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.drawString("Зависимость PSNR от дисперсии шума (логарифмическая ось X)", left, top - 15);

        g.dispose();
        new File(path).getParentFile().mkdirs();
        ImageIO.write(chart, "png", new File(path));
    }

    // ─────────────────────────────────────────────────────────────────
    //  Печать гистограммы в поток (текстовая)
    // ─────────────────────────────────────────────────────────────────
    private static void printHistogram(int[] hist, PrintWriter out) {
        int maxVal = 0;
        for (int c : hist) maxVal = Math.max(maxVal, c);
        int barWidth = 50;
        out.println("Гистограмма яркостей:");
        for (int i = 0; i < 256; i += 8) {
            int sum = 0;
            for (int j = i; j < Math.min(i + 8, 256); j++) sum += hist[j];
            int bar = maxVal > 0 ? (int) ((long) sum * barWidth / maxVal / 8) : 0;
            out.printf("[%3d-%3d] %s %d%n", i, Math.min(i + 7, 255), "#".repeat(Math.max(bar, 0)), sum);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  Полный анализ одного изображения
    // ─────────────────────────────────────────────────────────────────
    public static void analyzeImage(BufferedImage image, String imageName,
                                    PrintWriter statsWriter, String outputDir) throws IOException {
        new File(outputDir).mkdirs();
        int[][] brightness = getBrightness(image);

        String sep = "=".repeat(60);
        String header = String.format("%n%s%nИзображение: %s (%dx%d)%n%s",
                sep, imageName, image.getWidth(), image.getHeight(), sep);
        System.out.println(header);
        statsWriter.println(header);

        // ── Гистограмма ──────────────────────────────────────────────
        int[] hist = histogram(brightness);
        PrintWriter both = new PrintWriter(new OutputStream() {
            @Override public void write(int b) {
                System.out.print((char) b);
                statsWriter.print((char) b);
            }
            @Override public void write(byte[] b, int off, int len) {
                String s = new String(b, off, len);
                System.out.print(s);
                statsWriter.print(s);
            }
        });
        printHistogram(hist, both);

        // ── Основные статистики ───────────────────────────────────────
        double m = mean(brightness);
        double v = variance(brightness);
        double[] q = quartiles(brightness);
        double ent = entropy(brightness);
        double eng = energy(brightness);
        double skew = skewness(brightness);
        double kurt = kurtosis(brightness);

        String stats = String.format(
            "%nОсновные статистики яркости:%n" +
            "  Среднее:                    %.4f%n" +
            "  Дисперсия:                  %.4f%n" +
            "  СКО:                        %.4f%n" +
            "  Q1 / Медиана / Q3:          %.1f / %.1f / %.1f%n" +
            "  Энтропия:                   %.4f бит%n" +
            "  Энергия гист.:              %.6f%n" +
            "  Коэф. асимметрии (skew):    %.4f%n" +
            "  Эксцесс (excess kurtosis):  %.4f",
            m, v, Math.sqrt(v), q[0], q[1], q[2], ent, eng, skew, kurt);
        System.out.println(stats);
        statsWriter.println(stats);

        // ── GLCM ─────────────────────────────────────────────────────
        int[][] directions = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        String[] dirNames  = {"horiz(0,1)", "vert(1,0)", "diag(1,1)", "anti(1,-1)"};

        StringBuilder glcmSb = new StringBuilder("\nМатрица совместной встречаемости (GLCM):\n");
        for (int d = 0; d < directions.length; d++) {
            int dr = directions[d][0], dc = directions[d][1];
            double[][] glcmMatrix = glcm(brightness, dr, dc);
            double ge = glcmEnergy(glcmMatrix);
            glcmSb.append(String.format("  [%s]  Энергия GLCM = %.8f%n", dirNames[d], ge));

            // сохраняем как изображение
            String glcmPath = outputDir + "/" + imageName + "_glcm_" + dirNames[d].replaceAll("[^a-zA-Z0-9]", "_") + ".png";
            saveGlcmImage(glcmMatrix, glcmPath);
            glcmSb.append(String.format("         Сохранена: %s%n", glcmPath));
        }
        System.out.print(glcmSb);
        statsWriter.print(glcmSb);

        // ── PSNR при дисперсии 400 ────────────────────────────────────
        double noiseVar = 400.0;
        BufferedImage noisy = addGaussianNoise(image, noiseVar);
        double psnrVal = psnr(image, noisy);
        String noisyPath = outputDir + "/" + imageName + "_noisy.png";
        ImageIO.write(noisy, "png", new File(noisyPath));

        int[][] noisyBr = getBrightness(noisy);
        String noiseStats = String.format(
            "%nЗашумление AWGN (дисперсия=%.1f, σ=%.2f):%n" +
            "  PSNR:                       %.2f дБ%n" +
            "  Среднее (шумное):           %.4f%n" +
            "  Дисперсия (шумное):         %.4f%n" +
            "  Энтропия (шумное):          %.4f бит%n" +
            "  Энергия (шумное):           %.6f%n" +
            "  Зашумлённое изображение: %s",
            noiseVar, Math.sqrt(noiseVar), psnrVal,
            mean(noisyBr), variance(noisyBr), entropy(noisyBr), energy(noisyBr),
            noisyPath);
        System.out.println(noiseStats);
        statsWriter.println(noiseStats);

        // ── График PSNR(дисперсия) ────────────────────────────────────
        String psnrPlotPath = outputDir + "/" + imageName + "_psnr_plot.png";
        System.out.println("\nГенерация графика PSNR(дисперсия)...");
        savePsnrPlot(image, psnrPlotPath);
        String plotMsg = "  График сохранён: " + psnrPlotPath;
        System.out.println(plotMsg);
        statsWriter.println(plotMsg);

        statsWriter.flush();
    }

    // ─────────────────────────────────────────────────────────────────
    //  main
    // ─────────────────────────────────────────────────────────────────
    public static void main(String[] args) throws IOException {
        // разбор аргументов
        java.util.List<String> imagePaths = new ArrayList<>();
        String outputDir = "results";

        for (int i = 0; i < args.length; i++) {
            if ("--outdir".equals(args[i]) && i + 1 < args.length) {
                outputDir = args[++i];
            } else {
                imagePaths.add(args[i]);
            }
        }

        // если пути не переданы — берём все PNG из examples/
        if (imagePaths.isEmpty()) {
            File exDir = new File("examples");
            if (exDir.exists() && exDir.isDirectory()) {
                for (File f : Objects.requireNonNull(exDir.listFiles())) {
                    if (f.getName().toLowerCase().endsWith(".png")
                            && !f.getName().contains("_noisy")) {
                        imagePaths.add(f.getPath());
                    }
                }
                Collections.sort(imagePaths);
            }
        }

        if (imagePaths.isEmpty()) {
            System.out.println("Использование: java Lab1 <изображение1> [изображение2 ...] [--outdir <папка>]");
            System.out.println("  Если изображения не указаны — обрабатываются все PNG из examples/");
            return;
        }

        new File(outputDir).mkdirs();
        String statsFilePath = outputDir + "/statistics.txt";
        PrintWriter statsWriter = new PrintWriter(new FileWriter(statsFilePath, false));

        statsWriter.println("Лабораторная работа №1: Анализ изображений");
        statsWriter.println("Дата: " + new java.util.Date());
        statsWriter.println();

        System.out.println("Результаты будут сохранены в: " + outputDir);
        System.out.println("Статистики: " + statsFilePath);
        System.out.println("Обрабатываем " + imagePaths.size() + " изображение(й)...");

        for (String path : imagePaths) {
            File f = new File(path);
            if (!f.exists()) {
                System.out.println("ОШИБКА: файл не найден — " + path);
                statsWriter.println("ОШИБКА: файл не найден — " + path);
                continue;
            }
            BufferedImage image = ImageIO.read(f);
            if (image == null) {
                System.out.println("ОШИБКА: не удалось прочитать — " + path);
                statsWriter.println("ОШИБКА: не удалось прочитать — " + path);
                continue;
            }
            // имя файла без расширения — используем как идентификатор
            String name = f.getName().replaceAll("\\.[^.]+$", "");
            analyzeImage(image, name, statsWriter, outputDir);
        }

        statsWriter.println("\n" + "=".repeat(60));
        statsWriter.println("Анализ завершён.");
        statsWriter.close();

        System.out.println("\nВсе результаты сохранены в папке: " + outputDir);
        System.out.println("Сводная статистика: " + statsFilePath);

        // ── Выводы о GLCM ────────────────────────────────────────────
        System.out.println("""

=== ВЫВОДЫ О МАТРИЦЕ СОВМЕСТНОЙ ВСТРЕЧАЕМОСТИ ===
1. Однородные (однотонные) изображения (dark.png):
   Энергия GLCM максимальна, матрица сосредоточена в одной точке на диагонали.

2. Текстуры с регулярной структурой (checker.png):
   GLCM имеет несколько ярких пятен вблизи диагонали, симметрично расположенных.
   Узор повторяемости хорошо виден по симметричным пикам.

3. Градиентные изображения (gradient.png):
   GLCM размыта вдоль диагонали — значения яркости соседних пикселей
   близки, но постепенно меняются => широкая диагональная полоса.

4. Зашумлённые изображения (noise.png, *_noisy.png):
   GLCM заметно рассеяна, пятна далеко от диагонали — соседние пиксели
   имеют слабо коррелированные яркости => низкая энергия GLCM.

5. Фотографии реальных сцен:
   GLCM, как правило, имеет основное пятно вдоль диагонали (смежные пиксели
   похожи), окружённое диффузным фоном (наличие границ и текстур).
""");

        // ── Выводы о PSNR(дисперсия) ─────────────────────────────────
        System.out.println("""
=== ВЫВОДЫ О ЗАВИСИМОСТИ PSNR ОТ ДИСПЕРСИИ ШУМА ===
Теоретически для аддитивного гауссового шума:
  PSNR ≈ 10·log10(255²/σ²) = 10·log10(65025) - 10·log10(σ²)
Это означает, что при увеличении дисперсии σ² в 10 раз (на 10 дБ)
PSNR уменьшается ровно на 10 дБ (наклон −10 дБ/дек по оси дисперсии).

На практике (см. график):
  - При малых дисперсиях (σ²<10) PSNR очень высокий (>40 дБ) — шум почти незаметен.
  - При σ²=400 (σ=20) PSNR ≈ 22-24 дБ — шум явно виден.
  - При σ²>2000 PSNR < 15 дБ — изображение сильно испорчено.
  - Из-за клиппирования [0,255] реальный наклон чуть меньше −10 дБ/дек.
""");
    }
}
