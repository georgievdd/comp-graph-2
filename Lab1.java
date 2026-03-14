import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

public class Lab1 {

    //  Извлечение яркости (luminance по формуле BT.601) 
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

    //  Гистограмма яркостей 
    public static int[] histogram(int[][] brightness) {
        int[] hist = new int[256];
        for (int[] row : brightness)
            for (int v : row)
                hist[v]++;
        return hist;
    }

    //  Среднее 
    public static double mean(int[][] brightness) {
        long sum = 0;
        int count = 0;
        for (int[] row : brightness)
            for (int v : row) {
                sum += v;
                count++;
            }
        return (double) sum / count;
    }

    //  Дисперсия 
    public static double variance(int[][] brightness) {
        double m = mean(brightness);
        double sum = 0;
        int count = 0;
        for (int[] row : brightness)
            for (int v : row) {
                double d = v - m;
                sum += d * d;
                count++;
            }
        return sum / count;
    }

    //  Квартили (Q1, медиана Q2, Q3) 
    public static double[] quartiles(int[][] brightness) {
        int[] sorted = flatten(brightness);
        Arrays.sort(sorted);
        int n = sorted.length;
        double q1 = percentile(sorted, 0.25);
        double q2 = percentile(sorted, 0.50);
        double q3 = percentile(sorted, 0.75);
        return new double[]{q1, q2, q3};
    }

    private static double percentile(int[] sorted, double p) {
        double idx = p * (sorted.length - 1);
        int lo = (int) Math.floor(idx);
        int hi = (int) Math.ceil(idx);
        if (lo == hi) return sorted[lo];
        return sorted[lo] + (idx - lo) * (sorted[hi] - sorted[lo]);
    }

    private static int[] flatten(int[][] arr) {
        int total = 0;
        for (int[] row : arr) total += row.length;
        int[] flat = new int[total];
        int idx = 0;
        for (int[] row : arr)
            for (int v : row)
                flat[idx++] = v;
        return flat;
    }

    //  Энтропия 
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

    //  Энергия (сумма квадратов вероятностей) 
    public static double energy(int[][] brightness) {
        int[] hist = histogram(brightness);
        int total = 0;
        for (int c : hist) total += c;
        double e = 0;
        for (int c : hist) {
            double p = (double) c / total;
            e += p * p;
        }
        return e;
    }

    //  Коэффициент асимметрии (skewness) 
    public static double skewness(int[][] brightness) {
        double m = mean(brightness);
        double var = variance(brightness);
        double sigma = Math.sqrt(var);
        if (sigma == 0) return 0;
        double sum = 0;
        int count = 0;
        for (int[] row : brightness)
            for (int v : row) {
                double d = (v - m) / sigma;
                sum += d * d * d;
                count++;
            }
        return sum / count;
    }

    //  Эксцесс (kurtosis) 
    public static double kurtosis(int[][] brightness) {
        double m = mean(brightness);
        double var = variance(brightness);
        double sigma = Math.sqrt(var);
        if (sigma == 0) return 0;
        double sum = 0;
        int count = 0;
        for (int[] row : brightness)
            for (int v : row) {
                double d = (v - m) / sigma;
                sum += d * d * d * d;
                count++;
            }
        return sum / count - 3.0; // excess kurtosis
    }

    //  Матрица совместной встречаемости (GLCM) 
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
        // нормализация
        if (count > 0)
            for (int i = 0; i < levels; i++)
                for (int j = 0; j < levels; j++)
                    matrix[i][j] /= count;
        return matrix;
    }

    //  Энергия матрицы совместной встречаемости 
    public static double glcmEnergy(double[][] glcm) {
        double e = 0;
        for (double[] row : glcm)
            for (double v : row)
                e += v * v;
        return e;
    }

    //  Добавление аддитивного белого гауссового шума (AWGN) 
    public static BufferedImage addGaussianNoise(BufferedImage image, double variance) {
        Random rng = new Random();
        double sigma = Math.sqrt(variance);
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

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    //  PSNR 
    public static double psnr(BufferedImage original, BufferedImage noisy) {
        int w = original.getWidth(), h = original.getHeight();
        double mse = 0;
        int count = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int rgb1 = original.getRGB(x, y);
                int rgb2 = noisy.getRGB(x, y);
                for (int shift = 0; shift <= 16; shift += 8) {
                    int c1 = (rgb1 >> shift) & 0xFF;
                    int c2 = (rgb2 >> shift) & 0xFF;
                    mse += (c1 - c2) * (c1 - c2);
                    count++;
                }
            }
        mse /= count;
        if (mse == 0) return Double.POSITIVE_INFINITY;
        return 10.0 * Math.log10(255.0 * 255.0 / mse);
    }

    //  Печать гистограммы (текстовая) 
    private static void printHistogram(int[] hist) {
        int maxVal = 0;
        for (int c : hist) maxVal = Math.max(maxVal, c);
        int barWidth = 60;
        System.out.println("Гистограмма яркостей:");
        // печатаем с шагом 8 для компактности
        for (int i = 0; i < 256; i += 8) {
            int sum = 0;
            for (int j = i; j < Math.min(i + 8, 256); j++) sum += hist[j];
            int bar = maxVal > 0 ? (int) ((long) sum * barWidth / maxVal / 8) : 0;
            System.out.printf("[%3d-%3d] %s %d%n", i, Math.min(i + 7, 255),
                    "#".repeat(Math.max(bar, 0)), sum);
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Использование: java ImageAnalysis <путь_к_изображению> [дисперсия_шума]");
            System.out.println("  дисперсия_шума — дисперсия для AWGN (по умолчанию 400)");
            return;
        }

        String path = args[0];
        double noiseVariance = args.length >= 2 ? Double.parseDouble(args[1]) : 400.0;

        BufferedImage image = ImageIO.read(new File(path));
        if (image == null) {
            System.out.println("Не удалось загрузить изображение: " + path);
            return;
        }
        System.out.printf("Изображение: %s (%dx%d)%n%n", path, image.getWidth(), image.getHeight());

        int[][] brightness = getBrightness(image);

        // Гистограмма
        int[] hist = histogram(brightness);
        printHistogram(hist);

        // Среднее и дисперсия
        double m = mean(brightness);
        double v = variance(brightness);
        System.out.printf("%nСреднее яркости: %.2f%n", m);
        System.out.printf("Дисперсия яркости: %.2f%n", v);
        System.out.printf("СКО яркости: %.2f%n", Math.sqrt(v));

        // Квартили
        double[] q = quartiles(brightness);
        System.out.printf("%nКвартили: Q1=%.1f, Q2 (медиана)=%.1f, Q3=%.1f%n", q[0], q[1], q[2]);

        // Энтропия и энергия
        System.out.printf("%nЭнтропия: %.4f бит%n", entropy(brightness));
        System.out.printf("Энергия: %.6f%n", energy(brightness));

        // Асимметрия и эксцесс
        System.out.printf("%nКоэффициент асимметрии (skewness): %.4f%n", skewness(brightness));
        System.out.printf("Эксцесс (excess kurtosis): %.4f%n", kurtosis(brightness));

        // GLCM
        int dr = 0, dc = 1; // соседние по горизонтали
        double[][] glcmMatrix = glcm(brightness, dr, dc);
        double glcmE = glcmEnergy(glcmMatrix);
        System.out.printf("%nМатрица совместной встречаемости (dr=%d, dc=%d):%n", dr, dc);
        System.out.printf("  Энергия GLCM: %.6f%n", glcmE);

        // Дополнительно: GLCM для диагонального смещения
        dr = 1; dc = 1;
        glcmMatrix = glcm(brightness, dr, dc);
        glcmE = glcmEnergy(glcmMatrix);
        System.out.printf("Матрица совместной встречаемости (dr=%d, dc=%d):%n", dr, dc);
        System.out.printf("  Энергия GLCM: %.6f%n", glcmE);

        // Добавление шума и PSNR
        System.out.printf("%nДобавление AWGN с дисперсией %.1f (σ=%.2f)...%n",
                noiseVariance, Math.sqrt(noiseVariance));
        BufferedImage noisy = addGaussianNoise(image, noiseVariance);

        double psnrValue = psnr(image, noisy);
        System.out.printf("PSNR: %.2f дБ%n", psnrValue);

        // Сохранение зашумлённого изображения
        String noisyPath = path.replaceAll("\\.[^.]+$", "_noisy.png");
        ImageIO.write(noisy, "png", new File(noisyPath));
        System.out.printf("Зашумлённое изображение сохранено: %s%n", noisyPath);

        // Статистика зашумлённого изображения
        int[][] noisyBrightness = getBrightness(noisy);
        System.out.printf("%nСтатистика зашумлённого изображения:%n");
        System.out.printf("  Среднее: %.2f%n", mean(noisyBrightness));
        System.out.printf("  Дисперсия: %.2f%n", variance(noisyBrightness));
        System.out.printf("  Энтропия: %.4f бит%n", entropy(noisyBrightness));
        System.out.printf("  Энергия: %.6f%n", energy(noisyBrightness));
    }
}
