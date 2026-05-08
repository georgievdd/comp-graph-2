import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;

/**
 * Лабораторная работа №6: Сегментация и вычисление признаков.
 *
 * Основное задание:
 *   1. Бинаризация (порог Оцу): максимизация межклассовой дисперсии σ²b = ω₀ω₁(μ₀−μ₁)²
 *   2. Разметка 4-связных областей (двухпроходный алгоритм + Union-Find)
 *   3. Геом. моменты 0-го и 1-го порядков; центральные геом. моменты 2-го порядка
 *   4. Число областей с площадью > 30 пкс, похожих на круг (Kr2 < 1.3)
 *
 * Дополнительные задания:
 *   5. Разметка 8-связных областей методом заливки с затравкой (FloodFill)
 *   6. Фильтрация областей на изображении по признакам (площадь, форма)
 *   7. Распределение объектов по ориентации
 */
public class Lab6 {

    // ── Вспомогательные функции ──────────────────────────────────────────────────

    static double[][] getBrightness(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        double[][] b = new double[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                b[y][x] = 0.299*((rgb>>16)&0xFF) + 0.587*((rgb>>8)&0xFF) + 0.114*(rgb&0xFF);
            }
        return b;
    }

    static BufferedImage toGray(double[][] p) {
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

    // ── Алгоритм Оцу ─────────────────────────────────────────────────────────────
    //
    //   Максимизирует межклассовую дисперсию (лекция 9):
    //   σ²b(t) = ω₀(t)·ω₁(t)·(μ₀(t) − μ₁(t))²
    //   Эквивалентно минимизации внутриклассовой: σ²w = ω₀σ²₀ + ω₁σ²₁

    static int otsuThreshold(double[][] src) {
        int h = src.length, w = src[0].length;
        int total = h * w;
        int[] hist = new int[256];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                hist[Math.max(0, Math.min(255, (int)src[y][x]))]++;

        // Общая сумма яркостей
        double sum = 0;
        for (int i = 0; i < 256; i++) sum += (double)i * hist[i];

        double sumB = 0, wB = 0, varMax = 0;
        int threshold = 0;
        for (int t = 0; t < 256; t++) {
            wB += hist[t];
            if (wB == 0) continue;
            double wF = total - wB;
            if (wF == 0) break;
            sumB += (double)t * hist[t];
            double mB = sumB / wB;
            double mF = (sum - sumB) / wF;
            double varBetween = wB * wF * (mB - mF) * (mB - mF);
            if (varBetween > varMax) {
                varMax = varBetween;
                threshold = t;
            }
        }
        return threshold;
    }

    /** Бинаризация: true = передний план (пиксель ≥ порог). */
    static boolean[][] binarize(double[][] src, int threshold) {
        int h = src.length, w = src[0].length;
        boolean[][] bin = new boolean[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                bin[y][x] = (src[y][x] >= threshold);
        return bin;
    }

    static BufferedImage binaryToImage(boolean[][] bin) {
        int h = bin.length, w = bin[0].length;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                img.setRGB(x, y, bin[y][x] ? 0xFFFFFF : 0x000000);
        return img;
    }

    // ── Union-Find (для двухпроходного алгоритма CCL) ────────────────────────────

    static int[] ufParent;

    static void ufInit(int n) {
        ufParent = new int[n + 1];
        for (int i = 0; i <= n; i++) ufParent[i] = i;
    }

    // Поиск с сжатием пути
    static int ufFind(int x) {
        while (ufParent[x] != x) {
            ufParent[x] = ufParent[ufParent[x]]; // двухшаговое сжатие
            x = ufParent[x];
        }
        return x;
    }

    static void ufUnion(int a, int b) {
        int ra = ufFind(a), rb = ufFind(b);
        if (ra != rb) ufParent[ra] = rb;
    }

    // ── 4-связная разметка: двухпроходный алгоритм (лекция 9) ──────────────────
    //
    //   Первый проход: сканируем слева→направо, сверху→вниз.
    //     – Нет размеченных 4-соседей (выше, левее) → новая метка.
    //     – Один размеченный сосед → присвоить его метку.
    //     – Два разных соседа → присвоить меньший, внести в таблицу эквивалентности.
    //   Второй проход: заменить все метки на корневую (через Union-Find).

    static int lastNumLabels = 0; // число меток после последней разметки

    static int[][] labelComponents4(boolean[][] bin) {
        int h = bin.length, w = bin[0].length;
        int[][] labels = new int[h][w];
        ufInit(h * w + 2);
        int nextLabel = 1;

        // Первый проход
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!bin[y][x]) continue;
                int above = (y > 0) ? labels[y-1][x] : 0;
                int left  = (x > 0) ? labels[y][x-1] : 0;
                if (above == 0 && left == 0) {
                    // Нет размеченных соседей — новая метка
                    labels[y][x] = nextLabel++;
                } else if (above == 0) {
                    labels[y][x] = ufFind(left);
                } else if (left == 0) {
                    labels[y][x] = ufFind(above);
                } else {
                    // Два соседа: объединяем эквивалентные метки
                    int ra = ufFind(above), rl = ufFind(left);
                    if (ra != rl) ufUnion(ra, rl);
                    labels[y][x] = ufFind(ra);
                }
            }
        }

        // Второй проход: нормализуем метки (0, 1, 2, ...)
        Map<Integer,Integer> remap = new HashMap<>();
        int count = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (labels[y][x] == 0) continue;
                int root = ufFind(labels[y][x]);
                if (!remap.containsKey(root)) remap.put(root, ++count);
                labels[y][x] = remap.get(root);
            }
        }
        lastNumLabels = count;
        return labels;
    }

    // ── 8-связная разметка: заливка с затравкой (FloodFill, лекция 9) ───────────
    //
    //   Для каждого непомеченного пикселя переднего плана:
    //     1. Назначить новую метку, добавить в стек.
    //     2. Пока стек не пуст: извлечь пиксель, пометить всех 8-соседей той же меткой.

    static int[][] labelComponents8(boolean[][] bin) {
        int h = bin.length, w = bin[0].length;
        int[][] labels = new int[h][w];
        int label = 0;
        int[][] dirs8 = {{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}};
        Deque<int[]> stack = new ArrayDeque<>();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!bin[y][x] || labels[y][x] != 0) continue;
                label++;
                labels[y][x] = label;
                stack.push(new int[]{y, x});
                while (!stack.isEmpty()) {
                    int[] p = stack.pop();
                    for (int[] d : dirs8) {
                        int ny = p[0]+d[0], nx = p[1]+d[1];
                        if (ny >= 0 && ny < h && nx >= 0 && nx < w
                                && bin[ny][nx] && labels[ny][nx] == 0) {
                            labels[ny][nx] = label;
                            stack.push(new int[]{ny, nx});
                        }
                    }
                }
            }
        }
        lastNumLabels = label;
        return labels;
    }

    // ── Моменты и признаки формы (лекция 10) ─────────────────────────────────────

    static class Region {
        int   label;
        double m00;              // Геом. момент 0-го порядка: m₀₀ = ΣΣ p(x,y) = площадь
        double m10, m01;         // Геом. моменты 1-го порядка: m₁₀ = ΣΣ x·p, m₀₁ = ΣΣ y·p
        double xc, yc;           // Центроид: xc = m10/m00, yc = m01/m00
        double mu20, mu02, mu11; // Центральные геом. моменты 2-го порядка
        double kr2;              // Округлость: Kr2 = 2π(μ₂₀+μ₀₂)/S² ≈ 1 для круга
        double orientation;      // Угол большой оси (рад): ½·arctan(2μ₁₁/(μ₂₀−μ₀₂))
    }

    /**
     * Вычисляет геом. моменты 0-го, 1-го порядков и центральные 2-го порядка.
     * Все формулы — из лекции 10.
     */
    static Region[] computeRegions(int[][] labels, int numLabels) {
        if (numLabels == 0) return new Region[0];
        int h = labels.length, w = labels[0].length;
        double[] m00 = new double[numLabels+1];
        double[] m10 = new double[numLabels+1];
        double[] m01 = new double[numLabels+1];

        // Первый проход: моменты 0-го и 1-го порядков
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int lbl = labels[y][x];
                if (lbl <= 0) continue;
                m00[lbl]++;
                m10[lbl] += x;
                m01[lbl] += y;
            }

        double[] xcArr = new double[numLabels+1];
        double[] ycArr = new double[numLabels+1];
        for (int lbl = 1; lbl <= numLabels; lbl++)
            if (m00[lbl] > 0) {
                xcArr[lbl] = m10[lbl] / m00[lbl];
                ycArr[lbl] = m01[lbl] / m00[lbl];
            }

        // Второй проход: центральные моменты 2-го порядка
        // μ_pq = ΣΣ (x−xc)ᵖ(y−yc)ᵍ·p(x,y)
        double[] mu20 = new double[numLabels+1];
        double[] mu02 = new double[numLabels+1];
        double[] mu11 = new double[numLabels+1];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int lbl = labels[y][x];
                if (lbl <= 0) continue;
                double dx = x - xcArr[lbl], dy = y - ycArr[lbl];
                mu20[lbl] += dx * dx;
                mu02[lbl] += dy * dy;
                mu11[lbl] += dx * dy;
            }

        Region[] regions = new Region[numLabels];
        for (int i = 0; i < numLabels; i++) {
            int lbl = i + 1;
            Region r = new Region();
            r.label = lbl;
            r.m00 = m00[lbl]; r.m10 = m10[lbl]; r.m01 = m01[lbl];
            r.xc = xcArr[lbl]; r.yc = ycArr[lbl];
            r.mu20 = mu20[lbl]; r.mu02 = mu02[lbl]; r.mu11 = mu11[lbl];
            // Округлость Kr2 = 2π(μ₂₀+μ₀₂)/S² (лекция 10); для круга Kr2=1
            r.kr2 = (r.m00 > 0) ? 2*Math.PI*(r.mu20+r.mu02)/(r.m00*r.m00) : Double.MAX_VALUE;
            // Угол большой оси эллипса: Amajor = ½·arctan(2μ₁₁/(μ₂₀−μ₀₂)) (лекция 10)
            r.orientation = 0.5 * Math.atan2(2*r.mu11, r.mu20 - r.mu02);
            regions[i] = r;
        }
        return regions;
    }

    // ── Визуализация ─────────────────────────────────────────────────────────────

    /** Псевдоцветная карта меток (равномерно по тону HSV). */
    static BufferedImage colorLabels(int[][] labels, int numLabels) {
        int h = labels.length, w = labels[0].length;
        int[] palette = new int[Math.max(numLabels, 1) + 1];
        for (int i = 1; i <= numLabels; i++)
            palette[i] = Color.HSBtoRGB((float)i / numLabels, 0.85f, 0.90f);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int lbl = labels[y][x];
                img.setRGB(x, y, lbl > 0 ? palette[lbl] : 0x000000);
            }
        return img;
    }

    /**
     * Накладывает выбранные области поверх полутонового изображения.
     * showMask[lbl-1] = true → закрасить регион заданным цветом.
     */
    static BufferedImage overlayRegions(double[][] src, int[][] labels,
                                        boolean[] showMask, Color color) {
        int h = src.length, w = src[0].length;
        int cr = color.getRed(), cg = color.getGreen(), cb = color.getBlue();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int lbl = labels[y][x];
                if (lbl > 0 && lbl <= showMask.length && showMask[lbl-1]) {
                    img.setRGB(x, y, (cr<<16)|(cg<<8)|cb);
                } else {
                    int v = Math.max(0, Math.min(255, (int)Math.round(src[y][x])));
                    img.setRGB(x, y, v * 0x10101);
                }
            }
        return img;
    }

    /** Гистограмма ориентаций (рад → градусы, −90°…+90°). */
    static BufferedImage orientationHistogram(List<Region> regions) {
        int bins = 18; // шаг 10°
        int[] hist = new int[bins];
        for (Region r : regions) {
            double deg = Math.toDegrees(r.orientation); // −90..+90
            int bin = (int)((deg + 90.0) / 180.0 * bins);
            hist[Math.max(0, Math.min(bins-1, bin))]++;
        }
        int maxVal = 1;
        for (int v : hist) if (v > maxVal) maxVal = v;

        int bw = 30, chartH = 120, padX = 35, padTop = 20, labH = 16;
        int W = bins * bw + 2 * padX, H = chartH + padTop + labH;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE); g.fillRect(0, 0, W, H);
        g.setColor(new Color(70, 130, 180));
        for (int i = 0; i < bins; i++) {
            int barH = (int)((double)hist[i] / maxVal * chartH);
            g.fillRect(padX + i*bw + 1, padTop + chartH - barH, bw - 2, barH);
        }
        g.setColor(Color.DARK_GRAY);
        g.drawLine(padX, padTop, padX, padTop + chartH);
        g.drawLine(padX, padTop + chartH, W - padX, padTop + chartH);
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.PLAIN, 8));
        for (int i = 0; i <= bins; i += 3) {
            int deg = -90 + i * 10;
            String s = deg + "°";
            g.drawString(s, padX + i*bw - 6, padTop + chartH + labH - 3);
        }
        g.dispose();
        return img;
    }

    // ── Компоновка строк изображений ─────────────────────────────────────────────

    static BufferedImage makeRow(String title, String[] lbls, BufferedImage[] imgs) {
        int pad = 6, topH = 22, labH = 13;
        int maxH = 0, totalW = pad;
        for (BufferedImage im : imgs) {
            if (im == null) continue;
            if (im.getHeight() > maxH) maxH = im.getHeight();
            totalW += im.getWidth() + pad;
        }
        BufferedImage out = new BufferedImage(totalW, topH+labH+maxH+pad, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE); g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.drawString(title, pad, 14);
        g.setFont(new Font("SansSerif", Font.PLAIN, 8));
        int px = pad;
        for (int i = 0; i < imgs.length; i++) {
            if (imgs[i] == null) continue;
            if (i < lbls.length) g.drawString(lbls[i], px, topH+labH-3);
            g.drawImage(imgs[i], px, topH+labH, null);
            px += imgs[i].getWidth() + pad;
        }
        g.dispose();
        return out;
    }

    static BufferedImage vstack(BufferedImage... imgs) {
        int W = 0, H = 0;
        for (BufferedImage im : imgs) if (im != null) { W = Math.max(W, im.getWidth()); H += im.getHeight(); }
        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0, 0, W, H);
        int y = 0;
        for (BufferedImage im : imgs) if (im != null) { g.drawImage(im, 0, y, null); y += im.getHeight(); }
        g.dispose();
        return out;
    }

    // ── Обработка одного изображения ─────────────────────────────────────────────

    static void processImage(double[][] src, String name,
                             String outDir, PrintWriter log) throws IOException {
        int h = src.length, w = src[0].length;
        log.println("=== " + name + " (" + w + "×" + h + ") ===");

        // 1. Порог Оцу
        int T = otsuThreshold(src);
        log.println("  Порог Оцу: T = " + T);

        boolean[][] bin = binarize(src, T);

        // 2. 4-связная разметка (двухпроходный алгоритм, лекция 9)
        int[][] labels4 = labelComponents4(bin);
        int numLabels4 = lastNumLabels;
        log.println("  4-связных регионов: " + numLabels4);

        // 3. Моменты и признаки формы (лекция 10)
        Region[] regions4 = computeRegions(labels4, numLabels4);

        // Фильтрация: площадь > 30 пикселов
        final int MIN_AREA = 30;
        List<Region> large = new ArrayList<>();
        for (Region r : regions4) if (r.m00 > MIN_AREA) large.add(r);
        // Сортируем по убыванию площади
        large.sort((a, b) -> Double.compare(b.m00, a.m00));
        log.println("  Регионов с S > " + MIN_AREA + ": " + large.size());

        // Круглые: Kr2 < 1.3 (округлость по моментам инерции, лекция 10)
        final double KR2_THRESH = 1.3;
        List<Region> circles = new ArrayList<>();
        for (Region r : large) if (r.kr2 < KR2_THRESH) circles.add(r);
        log.println("  Из них похожих на круг (Kr2 < " + KR2_THRESH + "): " + circles.size());

        // Лог признаков крупных регионов (до 30 строк)
        log.printf("  %-6s %-8s %-7s %-7s %-9s %-9s %-9s %-6s %-7s%n",
                "Метка","m00","xc","yc","μ20","μ02","μ11","Kr2","∠°");
        int logCount = 0;
        for (Region r : large) {
            log.printf("  %-6d %-8.0f %-7.1f %-7.1f %-9.1f %-9.1f %-9.1f %-6.3f %-7.1f%n",
                    r.label, r.m00, r.xc, r.yc,
                    r.mu20, r.mu02, r.mu11,
                    r.kr2, Math.toDegrees(r.orientation));
            if (++logCount >= 30) { if (large.size() > 30) log.println("  ... (показаны первые 30)"); break; }
        }

        // Дополн. задание 1: 8-связная разметка (FloodFill, лекция 9)
        int[][] labels8 = labelComponents8(bin);
        int numLabels8 = lastNumLabels;
        log.println("  8-связных регионов: " + numLabels8);
        log.println();

        // ── Визуализация ─────────────────────────────────────────────────────────

        // Маски для наложения (доп. задание 2: оставить области с заданными характеристиками)
        boolean[] maskLarge  = new boolean[numLabels4];
        boolean[] maskCircle = new boolean[numLabels4];
        for (Region r : large)   maskLarge [r.label-1] = true;
        for (Region r : circles) maskCircle[r.label-1] = true;

        BufferedImage grayImg   = toGray(src);
        BufferedImage binImg    = binaryToImage(bin);
        BufferedImage lbl4Img   = colorLabels(labels4, numLabels4);
        BufferedImage lbl8Img   = colorLabels(labels8, numLabels8);
        BufferedImage largeImg  = overlayRegions(src, labels4, maskLarge,  new Color(60, 180, 60));
        BufferedImage circleImg = overlayRegions(src, labels4, maskCircle, new Color(210, 50, 50));
        BufferedImage orientImg = orientationHistogram(large);

        BufferedImage row1 = makeRow(
                name + " — бинаризация и разметка",
                new String[]{"Оригинал (серый)", "Бин. (Оцу T="+T+")",
                             "4-связн. (n="+numLabels4+")", "8-связн. (n="+numLabels8+")"},
                new BufferedImage[]{grayImg, binImg, lbl4Img, lbl8Img});

        BufferedImage row2 = makeRow(
                name + " — признаки и фильтрация (S>"+MIN_AREA+")",
                new String[]{"Регионы S>"+MIN_AREA+" (n="+large.size()+")",
                             "Круги Kr2<"+KR2_THRESH+" (n="+circles.size()+")"},
                new BufferedImage[]{largeImg, circleImg});

        BufferedImage row3 = makeRow(
                name + " — распределение по ориентации",
                new String[]{"Гистограмма ориентаций регионов S>"+MIN_AREA},
                new BufferedImage[]{orientImg});

        save(vstack(row1, row2, row3), outDir + "/" + name + "_seg.png");
    }

    // ── Точка входа ──────────────────────────────────────────────────────────────

    public static void main(String[] args) throws IOException {
        String outDir = "results6";
        for (int i = 0; i < args.length-1; i++)
            if ("--outdir".equals(args[i])) outDir = args[i+1];
        new File(outDir).mkdirs();

        PrintWriter log = new PrintWriter(new FileWriter(outDir + "/log.txt", false));
        log.println("Лабораторная работа №6: Сегментация и вычисление признаков");
        log.println("Дата: " + new java.util.Date());
        log.println();

        String[][] images = {
            {"examples/circles.png",       "circles"},
            {"examples/baboon.png",        "baboon"},
            {"examples/checker.png",       "checker"},
            {"examples/gradient.png",      "gradient"},
            {"examples/sonoma_photo.jpg",  "sonoma_photo"},
        };

        for (String[] entry : images) {
            File f = new File(entry[0]);
            if (!f.exists()) { log.println("Не найден: " + entry[0]); continue; }
            BufferedImage img = ImageIO.read(f);
            if (img == null) { log.println("Не читается: " + entry[0]); continue; }
            processImage(getBrightness(img), entry[1], outDir, log);
        }

        log.flush();
        log.close();
        System.out.println("Готово. Результаты: " + outDir + "/");
    }
}
