import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

/**
 * Лабораторная работа №3: Геометрические преобразования и интерполяция.
 *
 * Основное задание:
 *   Поворот изображения на произвольный угол: ближайший сосед, билинейная,
 *   бикубическая интерполяция. Сравнение по качеству (PSNR) и времени.
 *
 * Дополнительные задания:
 *   1. Скос (shear) изображения.
 *   2. Поворот + масштабирование.
 *   3. Поворот in-situ через разложение на три скоса (без доп. буфера памяти).
 */
public class Lab3 {

    // ── Вспомогательные функции ──────────────────────────────────────────────────

    static int[][] getBrightness(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int[][] b = new int[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, bv = rgb & 0xFF;
                b[y][x] = (int)(0.299*r + 0.587*g + 0.114*bv);
            }
        return b;
    }

    static BufferedImage toBufferedImage(double[][] pix) {
        int h = pix.length, w = pix[0].length;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int v = Math.max(0, Math.min(255, (int)Math.round(pix[y][x])));
                img.setRGB(x, y, v * 0x10101);
            }
        return img;
    }

    static BufferedImage toBufferedImage(int[][] pix) {
        int h = pix.length, w = pix[0].length;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int v = Math.max(0, Math.min(255, pix[y][x]));
                img.setRGB(x, y, v * 0x10101);
            }
        return img;
    }

    static double[][] toDouble(int[][] arr) {
        int h = arr.length, w = arr[0].length;
        double[][] d = new double[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) d[y][x] = arr[y][x];
        return d;
    }

    /** PSNR между двумя double-массивами одинакового размера. */
    static double psnr(double[][] a, double[][] b) {
        int h = a.length, w = a[0].length;
        double mse = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) { double d = a[y][x] - b[y][x]; mse += d*d; }
        mse /= (h * w);
        return mse < 1e-10 ? 99.99 : 10 * Math.log10(255.0*255.0 / mse);
    }

    static double[][] cropCenter(double[][] arr, int newH, int newW) {
        int h = arr.length, w = arr[0].length;
        int y0 = Math.max(0, (h - newH) / 2);
        int x0 = Math.max(0, (w - newW) / 2);
        double[][] out = new double[newH][newW];
        for (int y = 0; y < newH; y++)
            for (int x = 0; x < newW; x++)
                out[y][x] = arr[Math.min(y0+y, h-1)][Math.min(x0+x, w-1)];
        return out;
    }

    static void saveImage(BufferedImage img, String path) throws IOException {
        new File(path).getParentFile().mkdirs();
        String ext = path.endsWith(".jpg") ? "jpg" : "png";
        ImageIO.write(img, ext, new File(path));
    }

    // ── Методы интерполяции ──────────────────────────────────────────────────────

    enum Method { NN, BILINEAR, BICUBIC }

    /** Ближайший сосед. */
    static double interpNN(double[][] src, double sx, double sy) {
        int h = src.length, w = src[0].length;
        int ix = (int)Math.round(sx), iy = (int)Math.round(sy);
        if (ix < 0 || ix >= w || iy < 0 || iy >= h) return 0;
        return src[iy][ix];
    }

    /** Билинейная интерполяция. */
    static double interpBilinear(double[][] src, double sx, double sy) {
        int h = src.length, w = src[0].length;
        int x0 = (int)Math.floor(sx), y0 = (int)Math.floor(sy);
        double fx = sx - x0, fy = sy - y0;
        double v00 = safe(src, y0,   x0,   h, w);
        double v10 = safe(src, y0,   x0+1, h, w);
        double v01 = safe(src, y0+1, x0,   h, w);
        double v11 = safe(src, y0+1, x0+1, h, w);
        return v00*(1-fx)*(1-fy) + v10*fx*(1-fy) + v01*(1-fx)*fy + v11*fx*fy;
    }

    /** Бикубическая интерполяция (ядро Keys с a=-0.5). */
    static double interpBicubic(double[][] src, double sx, double sy) {
        int h = src.length, w = src[0].length;
        int x0 = (int)Math.floor(sx), y0 = (int)Math.floor(sy);
        double sum = 0;
        for (int dy = -1; dy <= 2; dy++) {
            double wy = cubicKernel(sy - (y0 + dy));
            for (int dx = -1; dx <= 2; dx++) {
                double wx = cubicKernel(sx - (x0 + dx));
                sum += wx * wy * safe(src, y0+dy, x0+dx, h, w);
            }
        }
        return sum;
    }

    static double cubicKernel(double t) {
        final double a = -0.5;
        t = Math.abs(t);
        if (t <= 1) return (a+2)*t*t*t - (a+3)*t*t + 1;
        if (t <  2) return a*t*t*t - 5*a*t*t + 8*a*t - 4*a;
        return 0;
    }

    static double safe(double[][] src, int y, int x, int h, int w) {
        if (x < 0 || x >= w || y < 0 || y >= h) return 0;
        return src[y][x];
    }

    static double interp(double[][] src, double sx, double sy, Method m) {
        switch (m) {
            case NN:       return interpNN(src, sx, sy);
            case BILINEAR: return interpBilinear(src, sx, sy);
            case BICUBIC:  return interpBicubic(src, sx, sy);
        }
        return 0;
    }

    // ── Поворот (основное задание) ────────────────────────────────────────────────

    /**
     * Поворот на angleDeg градусов (обратное проецирование).
     * Если expand=true — выходное изображение охватывает весь повёрнутый контент.
     */
    static double[][] rotate(double[][] src, double angleDeg, Method m, boolean expand) {
        int h = src.length, w = src[0].length;
        double rad = Math.toRadians(angleDeg);
        double cos = Math.cos(rad), sin = Math.sin(rad);
        double cx = (w-1)/2.0, cy = (h-1)/2.0;

        int dstW, dstH;
        double dstCx, dstCy;
        if (expand) {
            double[] cornersX = {0, w-1, 0,   w-1};
            double[] cornersY = {0, 0,   h-1, h-1};
            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
            double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (int i = 0; i < 4; i++) {
                double rx = (cornersX[i]-cx)*cos - (cornersY[i]-cy)*sin;
                double ry = (cornersX[i]-cx)*sin + (cornersY[i]-cy)*cos;
                if (rx < minX) minX = rx; if (rx > maxX) maxX = rx;
                if (ry < minY) minY = ry; if (ry > maxY) maxY = ry;
            }
            dstW = (int)Math.ceil(maxX - minX) + 1;
            dstH = (int)Math.ceil(maxY - minY) + 1;
        } else {
            dstW = w; dstH = h;
        }
        dstCx = (dstW-1)/2.0; dstCy = (dstH-1)/2.0;

        double[][] dst = new double[dstH][dstW];
        for (int oy = 0; oy < dstH; oy++) {
            double dy = oy - dstCy;
            for (int ox = 0; ox < dstW; ox++) {
                double dx = ox - dstCx;
                // Обратное вращение
                double sx = cx + dx*cos + dy*sin;
                double sy = cy - dx*sin + dy*cos;
                dst[oy][ox] = interp(src, sx, sy, m);
            }
        }
        return dst;
    }

    // ── Скос (дополнительное задание 1) ──────────────────────────────────────────

    /** Горизонтальный скос: x' = x + shear*y. */
    static double[][] shearX(double[][] src, double shear, Method m) {
        int h = src.length, w = src[0].length;
        int newW = (int)(w + Math.abs(shear) * h + 1);
        double cx = (w-1)/2.0, ncx = (newW-1)/2.0;
        double cy = (h-1)/2.0;
        double[][] dst = new double[h][newW];
        for (int oy = 0; oy < h; oy++) {
            double dy = oy - cy;
            for (int ox = 0; ox < newW; ox++) {
                double sx = (ox - ncx) - shear * dy + cx;
                dst[oy][ox] = interp(src, sx, oy, m);
            }
        }
        return dst;
    }

    /** Вертикальный скос: y' = y + shear*x. */
    static double[][] shearY(double[][] src, double shear, Method m) {
        int h = src.length, w = src[0].length;
        int newH = (int)(h + Math.abs(shear) * w + 1);
        double cy = (h-1)/2.0, ncy = (newH-1)/2.0;
        double cx = (w-1)/2.0;
        double[][] dst = new double[newH][w];
        for (int oy = 0; oy < newH; oy++) {
            for (int ox = 0; ox < w; ox++) {
                double dx = ox - cx;
                double sy = (oy - ncy) - shear * dx + cy;
                dst[oy][ox] = interp(src, ox, sy, m);
            }
        }
        return dst;
    }

    // ── Поворот + масштабирование (дополнительное задание 2) ─────────────────────

    /** Поворот на angleDeg + масштабирование (scale > 1 = увеличение). */
    static double[][] rotateScale(double[][] src, double angleDeg, double scale, Method m) {
        int h = src.length, w = src[0].length;
        double rad = Math.toRadians(angleDeg);
        double cos = Math.cos(rad), sin = Math.sin(rad);
        double cx = (w-1)/2.0, cy = (h-1)/2.0;
        double[][] dst = new double[h][w];
        for (int oy = 0; oy < h; oy++) {
            double dy = oy - cy;
            for (int ox = 0; ox < w; ox++) {
                double dx = ox - cx;
                double sx = cx + (dx*cos + dy*sin) / scale;
                double sy = cy + (-dx*sin + dy*cos) / scale;
                dst[oy][ox] = interp(src, sx, sy, m);
            }
        }
        return dst;
    }

    // ── Поворот in-situ через 3 скоса (дополнительное задание 3) ─────────────────
    //
    // Разложение: R(θ) = Sx(-tan(θ/2)) · Sy(sin(θ)) · Sx(-tan(θ/2))
    // Каждый скос выполняется сдвигом строк/столбцов (целочисленный, ближайший сосед),
    // что не требует дополнительного буфера памяти (in-place cyclic shift на строке).

    static void shiftRowInPlace(int[] row, int shift) {
        int w = row.length;
        if (shift == 0) return;
        if (shift > 0) {
            shift = Math.min(shift, w);
            for (int x = w-1; x >= shift; x--) row[x] = row[x-shift];
            Arrays.fill(row, 0, shift, 0);
        } else {
            shift = Math.min(-shift, w);
            for (int x = 0; x < w-shift; x++) row[x] = row[x+shift];
            Arrays.fill(row, w-shift, w, 0);
        }
    }

    static void shiftColInPlace(int[][] img, int col, int shift) {
        int h = img.length;
        if (shift == 0) return;
        if (shift > 0) {
            shift = Math.min(shift, h);
            for (int y = h-1; y >= shift; y--) img[y][col] = img[y-shift][col];
            for (int y = 0; y < shift; y++) img[y][col] = 0;
        } else {
            shift = Math.min(-shift, h);
            for (int y = 0; y < h-shift; y++) img[y][col] = img[y+shift][col];
            for (int y = h-shift; y < h; y++) img[y][col] = 0;
        }
    }

    /**
     * Поворот in-situ через три последовательных скоса.
     * Используется целочисленное смещение строк/столбцов — никакого доп. буфера.
     * Точность — ближайший сосед, зато O(1) памяти.
     */
    static void rotateInSitu(int[][] img, double angleDeg) {
        int h = img.length, w = img[0].length;
        double rad = Math.toRadians(angleDeg);
        double tanHalf = Math.tan(rad / 2);
        double sinA    = Math.sin(rad);
        double cx = (w-1)/2.0, cy = (h-1)/2.0;

        // Шаг 1: горизонтальный скос x' = x - tan(θ/2)·(y - cy)
        for (int y = 0; y < h; y++)
            shiftRowInPlace(img[y], (int)Math.round(-tanHalf * (y - cy)));

        // Шаг 2: вертикальный скос y' = y + sin(θ)·(x - cx)
        for (int x = 0; x < w; x++)
            shiftColInPlace(img, x, (int)Math.round(sinA * (x - cx)));

        // Шаг 3: горизонтальный скос снова
        for (int y = 0; y < h; y++)
            shiftRowInPlace(img[y], (int)Math.round(-tanHalf * (y - cy)));
    }

    // ── Сохранение результатов ────────────────────────────────────────────────────

    static BufferedImage makeComparisonRow(String title, String[] labels,
                                           BufferedImage[] imgs) {
        int pad = 8, topH = 30, labH = 18;
        int maxH = 0, totalW = pad;
        for (BufferedImage img : imgs) {
            if (img.getHeight() > maxH) maxH = img.getHeight();
            totalW += img.getWidth() + pad;
        }
        int outH = topH + labH + maxH + pad;
        BufferedImage out = new BufferedImage(totalW, outH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE); g.fillRect(0, 0, totalW, outH);
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.drawString(title, pad, 20);
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        int px = pad;
        for (int i = 0; i < imgs.length; i++) {
            g.drawString(labels[i], px, topH + labH - 3);
            g.drawImage(imgs[i], px, topH + labH, null);
            px += imgs[i].getWidth() + pad;
        }
        g.dispose();
        return out;
    }

    // ── Обработка одного изображения ─────────────────────────────────────────────

    static void processImage(BufferedImage origImg, double[][] src,
                             String name, String outDir,
                             PrintWriter log) throws IOException {
        int h = src.length, w = src[0].length;
        log.println("=== " + name + " (" + w + "×" + h + ") ===");

        double[] angles = {30, 45, 90, 135};

        // ── Поворот: сравнение методов ────────────────────────────────────────────
        log.println("  Поворот:");
        for (double angle : angles) {
            long t0, t1;

            t0 = System.nanoTime();
            double[][] rotNN = rotate(src, angle, Method.NN, true);
            t1 = System.nanoTime(); long msNN = (t1-t0)/1_000_000;

            t0 = System.nanoTime();
            double[][] rotBL = rotate(src, angle, Method.BILINEAR, true);
            t1 = System.nanoTime(); long msBL = (t1-t0)/1_000_000;

            t0 = System.nanoTime();
            double[][] rotBC = rotate(src, angle, Method.BICUBIC, true);
            t1 = System.nanoTime(); long msBC = (t1-t0)/1_000_000;

            // PSNR туда-обратно: rotate(+angle) → rotate(-angle) → сравниваем с оригиналом.
            // Это показывает, насколько метод сохраняет изображение при геометрическом преобразовании.
            // Используем expand=false чтобы размеры совпадали с src.
            double pNN = psnr(src, rotate(rotate(src, angle, Method.NN,       false), -angle, Method.NN,       false));
            double pBL = psnr(src, rotate(rotate(src, angle, Method.BILINEAR, false), -angle, Method.BILINEAR, false));
            double pBC = psnr(src, rotate(rotate(src, angle, Method.BICUBIC,  false), -angle, Method.BICUBIC,  false));

            log.println(String.format(
                "    %5.0f°  NN: %4dms PSNR(туда-обр)=%5.1fdB | BL: %4dms PSNR=%5.1fdB | BC: %4dms PSNR=%5.1fdB",
                angle, msNN, pNN, msBL, pBL, msBC, pBC));

            // Сохранить сравнение
            String angleStr = String.format("%03.0f", angle);
            BufferedImage cmp = makeComparisonRow(
                name + " — поворот на " + (int)angle + "°",
                new String[]{"Оригинал", "Ближайший сосед (" + msNN + "ms)",
                             "Билинейная (" + msBL + "ms)", "Бикубическая (" + msBC + "ms)"},
                new BufferedImage[]{
                    origImg,
                    toBufferedImage(rotNN),
                    toBufferedImage(rotBL),
                    toBufferedImage(rotBC)
                });
            saveImage(cmp, outDir + "/" + name + "_rot" + angleStr + "_comparison.png");
        }

        // ── Доп. задание 1: Скос ──────────────────────────────────────────────────
        log.println("  Скос (shear X = 0.5):");
        double[][] shNN = shearX(src, 0.5, Method.NN);
        double[][] shBL = shearX(src, 0.5, Method.BILINEAR);
        double[][] shBC = shearX(src, 0.5, Method.BICUBIC);
        BufferedImage shCmp = makeComparisonRow(
            name + " — горизонтальный скос (0.5)",
            new String[]{"Оригинал", "Ближайший сосед", "Билинейная", "Бикубическая"},
            new BufferedImage[]{origImg, toBufferedImage(shNN), toBufferedImage(shBL), toBufferedImage(shBC)});
        saveImage(shCmp, outDir + "/" + name + "_shear_comparison.png");
        log.println("    Сохранено.");

        // ── Доп. задание 2: Поворот + масштаб ────────────────────────────────────
        log.println("  Поворот 45° + масштаб ×1.5:");
        double[][] rsNN = rotateScale(src, 45, 1.5, Method.NN);
        double[][] rsBL = rotateScale(src, 45, 1.5, Method.BILINEAR);
        double[][] rsBC = rotateScale(src, 45, 1.5, Method.BICUBIC);
        BufferedImage rsCmp = makeComparisonRow(
            name + " — поворот 45° + масштаб ×1.5",
            new String[]{"Оригинал", "Ближайший сосед", "Билинейная", "Бикубическая"},
            new BufferedImage[]{origImg, toBufferedImage(rsNN), toBufferedImage(rsBL), toBufferedImage(rsBC)});
        saveImage(rsCmp, outDir + "/" + name + "_rot45_scale_comparison.png");
        log.println("    Сохранено.");

        // ── Доп. задание 3: Поворот in-situ через 3 скоса ────────────────────────
        log.println("  Поворот in-situ (3 скоса, 45°):");
        int[][] inSitu = new int[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) inSitu[y][x] = (int)src[y][x];
        rotateInSitu(inSitu, 45);

        // Сравнение in-situ с обычным bilienar
        double[][] rotBL45 = rotate(src, 45, Method.BILINEAR, false);
        double[] inSituD = new double[h*w], rotBL45D = new double[h*w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            inSituD[y*w+x] = inSitu[y][x];
            rotBL45D[y*w+x] = rotBL45[y][x];
        }
        // Build double[][] for PSNR
        double[][] inSitu2D = new double[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) inSitu2D[y][x] = inSitu[y][x];
        log.println(String.format("    PSNR(in-situ vs билинейная, оба — прямой поворот 45°): %.2f dB",
                psnr(inSitu2D, rotBL45)));

        BufferedImage inSituImg = toBufferedImage(inSitu);
        BufferedImage inSituCmp = makeComparisonRow(
            name + " — поворот in-situ (3 скоса) vs Билинейная, 45°",
            new String[]{"Оригинал", "In-situ (3 скоса)", "Билинейная (обычный)"},
            new BufferedImage[]{origImg, inSituImg, toBufferedImage(rotBL45)});
        saveImage(inSituCmp, outDir + "/" + name + "_insitu_comparison.png");
        log.println("    Сохранено.");
        log.println();
    }

    // ── main ─────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws IOException {
        String outDir = "results3";
        for (int i = 0; i < args.length - 1; i++)
            if ("--outdir".equals(args[i])) outDir = args[i+1];
        new File(outDir).mkdirs();

        PrintWriter log = new PrintWriter(new FileWriter(outDir + "/log.txt", false));
        log.println("Лабораторная работа №3: Геометрические преобразования и интерполяция");
        log.println("Дата: " + new java.util.Date());
        log.println();
        log.println("=== ЧТО ПОКАЗЫВАЕТ PSNR В ДАННОЙ РАБОТЕ ===");
        log.println();
        log.println("PSNR (Peak Signal-to-Noise Ratio) — метрика качества восстановления изображения.");
        log.println("Формула: PSNR = 10 · log10(255² / MSE), где MSE — среднеквадратическая погрешность.");
        log.println("Чем выше PSNR (дБ), тем ближе результат к эталону. Ниже 25 дБ — заметные артефакты.");
        log.println();
        log.println("Методика измерения в данной работе:");
        log.println("  1. Берём оригинальное изображение.");
        log.println("  2. Поворачиваем на угол +θ методом интерполяции M.");
        log.println("  3. Поворачиваем результат обратно на −θ тем же методом M.");
        log.println("  4. Вычисляем PSNR между восстановленным изображением и оригиналом.");
        log.println();
        log.println("Что это показывает:");
        log.println("  Двойной поворот (туда-обратно) в идеале должен давать исходное изображение.");
        log.println("  Реально каждая интерполяция вносит погрешность (размытие, артефакты ступенек).");
        log.println("  PSNR измеряет эту суммарную погрешность и позволяет сравнить методы:");
        log.println("    Ближайший сосед (NN)  — быстрый, но ступенчатые артефакты → низкий PSNR");
        log.println("    Билинейная (BL)        — гладкий результат, некоторое размытие → средний PSNR");
        log.println("    Бикубическая (BC)      — лучшее сохранение деталей → высокий PSNR");
        log.println("=================================================");
        log.println();

        String[] paths = {
            "examples/baboon.png",
            "examples/checker.png",
            "examples/circles.png",
            "examples/gradient.png",
            "examples/sonoma_photo.jpg"
        };
        String[] names = {"baboon", "checker", "circles", "gradient", "sonoma_photo"};

        for (int i = 0; i < paths.length; i++) {
            File f = new File(paths[i]);
            if (!f.exists()) { log.println("Не найден: " + paths[i]); continue; }
            BufferedImage img = ImageIO.read(f);
            if (img == null) { log.println("Не читается: " + paths[i]); continue; }

            double[][] src = toDouble(getBrightness(img));
            processImage(img, src, names[i], outDir, log);
        }

        log.println("Все результаты сохранены в " + outDir + "/");
        log.close();
        System.out.println("Все результаты → " + outDir + "/");
    }
}
