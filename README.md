# Компьютерная графика — Лабораторные работы

---

## Лабораторная работа №1 — Анализ изображений

### Статистики

| Изображение | Среднее | Дисперсия | Энтропия (бит) | Энергия GLCM | PSNR (σ²=400) |
|---|---|---|---|---|---|
| checker | 127,50 | 16256,25 | 1,0000 | 0,47330 | 25,13 дБ |
| gradient | 127,25 | 5460,96 | 7,5938 | 0,00392 | 22,50 дБ |
| circles | 84,20 | 2605,92 | 2,1143 | 0,29811 | 22,40 дБ |
| dark | 19,20 | 134,42 | 4,8253 | 0,00140 | 23,52 дБ |
| noise | 127,15 | 2432,49 | 7,6288 | 0,00005 | 22,51 дБ |
| **sonoma_photo** | **138,59** | **1528,27** | **7,0726** | **0,00258** | **22,52 дБ** |
| **imac_blue_photo** | **165,72** | **649,22** | **6,6380** | **0,00100** | **22,45 дБ** |
| **baboon** | **128,64** | **1335,34** | **6,7434** | **0,00185** | **22,50 дБ** |

### GLCM

| checker | gradient | noise |
|---|---|---|
| ![](results/checker_glcm_horiz_0_1_.png) | ![](results/gradient_glcm_horiz_0_1_.png) | ![](results/noise_glcm_horiz_0_1_.png) |

| sonoma_photo | imac_blue_photo | baboon |
|---|---|---|
| ![](examples/sonoma_photo.jpg) | ![](examples/imac_blue_photo.jpg) | ![](examples/baboon.png) |
| ![](results/sonoma_photo_glcm_horiz_0_1_.png) | ![](results/imac_blue_photo_glcm_horiz_0_1_.png) | ![](results/baboon_glcm_horiz_0_1_.png) |

### PSNR(дисперсия шума)

| checker | sonoma_photo | baboon |
|---|---|---|
| ![](results/checker_psnr_plot.png) | ![](results/sonoma_photo_psnr_plot.png) | ![](results/baboon_psnr_plot.png) |

---

## Лабораторная работа №2 — Преобразование Фурье

### 1D сигналы

![](results2/signal1_fft.png)

![](results2/signal2_fft.png)

### 2D — синтетические изображения

![](results2/sin_horizontal_combined.png)

![](results2/sin_vertical_combined.png)

![](results2/sin_diagonal_combined.png)

![](results2/sin_mix_combined.png)

### Что вычисляет алгоритм вейвлет-разложения Хаара

Термин «спектр Хаара» — условный. Алгоритм реализует **дискретное вейвлет-преобразование Хаара (Haar DWT)**, которое принципиально отличается от преобразования Фурье.

На каждом уровне разложения для каждой пары соседних пикселей вычисляются:
- **сумма** `(a + b) / √2` — аппроксимирующий коэффициент (НЧ)
- **разность** `(a − b) / √2` — детализирующий коэффициент (ВЧ)

Для 2D-изображения преобразование применяется сначала по строкам, затем по столбцам. На каждом уровне разложения возникают **четыре субполосы**:

| Субполоса | Расположение | Описание |
|---|---|---|
| **LL** | верхний левый | Аппроксимация — уменьшенная копия изображения |
| **HL** | верхний правый | Детали вдоль X → чувствителен к вертикальным рёбрам |
| **LH** | нижний левый | Детали вдоль Y → чувствителен к горизонтальным рёбрам |
| **HH** | нижний правый | Диагональные детали |

Полное преобразование рекурсивно разлагает субполосу LL до одного пикселя (DC-компоненты). Вокруг неё — кольца субполос от крупного масштаба к мелкому.

**Ключевое отличие от FFT:**
- FFT — глобальное разложение по синусоидам, **нет пространственной локализации**
- Хаар — одновременно пространственная и масштабная локализация: коэффициент `d[i]` на уровне `k` = **насколько сильно меняется яркость в блоке размером `2^k` пикселей в конкретном месте изображения**

### 2D — реальные изображения

| Исходное | Спектр Фурье | IFFT | Вейвлет-разложение Хаара (с разметкой субполос) |
|---|---|---|---|
| ![](examples/checker.png) | ![](results2/checker_spectrum.png) | ![](results2/checker_ifft.png) | ![](results2/checker_haar.png) |
| ![](examples/gradient.png) | ![](results2/gradient_spectrum.png) | ![](results2/gradient_ifft.png) | ![](results2/gradient_haar.png) |
| ![](examples/circles.png) | ![](results2/circles_spectrum.png) | ![](results2/circles_ifft.png) | ![](results2/circles_haar.png) |
| ![](examples/baboon.png) | ![](results2/baboon_spectrum.png) | ![](results2/baboon_ifft.png) | ![](results2/baboon_haar.png) |
| ![](examples/sonoma_photo.jpg) | ![](results2/sonoma_photo_spectrum.png) | ![](results2/sonoma_photo_ifft.png) | ![](results2/sonoma_photo_haar.png) |

### Сравнение FFT и вейвлет-разложения Хаара

![](results2/baboon_spectra_comparison.png)

![](results2/sonoma_photo_spectra_comparison.png)

### Вейвлет-разложение Хаара — 1D сигналы

![](results2/signal1_haar.png)

![](results2/signal2_haar.png)

### Вейвлет-разложение Хаара — синтетические изображения

| sin_horizontal | sin_vertical | sin_diagonal | sin_mix |
|---|---|---|---|
| ![](results2/sin_horizontal_haar.png) | ![](results2/sin_vertical_haar.png) | ![](results2/sin_diagonal_haar.png) | ![](results2/sin_mix_haar.png) |

---

## Лабораторная работа №3 — Геометрические преобразования и интерполяция

### Поворот

**baboon:**

| 30° | 45° | 90° | 135° |
|---|---|---|---|
| ![](results3/baboon_rot030_comparison.png) | ![](results3/baboon_rot045_comparison.png) | ![](results3/baboon_rot090_comparison.png) | ![](results3/baboon_rot135_comparison.png) |

**checker:**

| 30° | 45° | 90° | 135° |
|---|---|---|---|
| ![](results3/checker_rot030_comparison.png) | ![](results3/checker_rot045_comparison.png) | ![](results3/checker_rot090_comparison.png) | ![](results3/checker_rot135_comparison.png) |

**circles:**

| 30° | 45° | 90° | 135° |
|---|---|---|---|
| ![](results3/circles_rot030_comparison.png) | ![](results3/circles_rot045_comparison.png) | ![](results3/circles_rot090_comparison.png) | ![](results3/circles_rot135_comparison.png) |

**gradient:**

| 30° | 45° | 90° | 135° |
|---|---|---|---|
| ![](results3/gradient_rot030_comparison.png) | ![](results3/gradient_rot045_comparison.png) | ![](results3/gradient_rot090_comparison.png) | ![](results3/gradient_rot135_comparison.png) |

**sonoma_photo:**

| 30° | 45° | 90° | 135° |
|---|---|---|---|
| ![](results3/sonoma_photo_rot030_comparison.png) | ![](results3/sonoma_photo_rot045_comparison.png) | ![](results3/sonoma_photo_rot090_comparison.png) | ![](results3/sonoma_photo_rot135_comparison.png) |

### PSNR туда-обратно (vs оригинал)

**Методика:** изображение поворачивается на `+θ`, затем обратно на `−θ` тем же методом; результат сравнивается с **оригиналом**. Это измеряет реальные потери качества при геометрическом преобразовании. При 90° потерь нет (точный поворот) → PSNR = 100 дБ.

| Изображение | Угол | NN | Билинейная | Бикубическая |
|---|---|---|---|---|
| baboon | 30° | 13.5 дБ | 13.5 дБ | **13.6 дБ** |
| baboon | 45° | 13.2 дБ | 13.2 дБ | **13.3 дБ** |
| baboon | 90° | 100.0 дБ | 100.0 дБ | 100.0 дБ |
| baboon | 135° | 13.2 дБ | 13.2 дБ | **13.3 дБ** |
| checker | 30° | 10.9 дБ | 11.0 дБ | **11.1 дБ** |
| checker | 45° | 10.5 дБ | 10.5 дБ | **10.6 дБ** |
| checker | 90° | 100.0 дБ | 100.0 дБ | 100.0 дБ |
| circles | 30° | 21.5 дБ | 21.6 дБ | **21.6 дБ** |
| circles | 45° | 20.7 дБ | 20.7 дБ | **20.7 дБ** |
| circles | 90° | 100.0 дБ | 100.0 дБ | 100.0 дБ |
| gradient | 30° | 11.9 дБ | 11.9 дБ | **11.9 дБ** |
| gradient | 45° | 11.5 дБ | 11.5 дБ | **11.5 дБ** |
| sonoma_photo | 30° | 12.8 дБ | 12.8 дБ | **12.8 дБ** |
| sonoma_photo | 45° | 12.7 дБ | 12.7 дБ | **12.7 дБ** |

### Скос (shear)

| baboon | checker | circles |
|---|---|---|
| ![](results3/baboon_shear_comparison.png) | ![](results3/checker_shear_comparison.png) | ![](results3/circles_shear_comparison.png) |

### Поворот + масштабирование (45°, ×1.5)

| baboon | checker | sonoma_photo |
|---|---|---|
| ![](results3/baboon_rot45_scale_comparison.png) | ![](results3/checker_rot45_scale_comparison.png) | ![](results3/sonoma_photo_rot45_scale_comparison.png) |

### Поворот in-situ (3 скоса, 45°)

| baboon | checker | circles |
|---|---|---|
| ![](results3/baboon_insitu_comparison.png) | ![](results3/checker_insitu_comparison.png) | ![](results3/circles_insitu_comparison.png) |

| gradient | sonoma_photo |
|---|---|
| ![](results3/gradient_insitu_comparison.png) | ![](results3/sonoma_photo_insitu_comparison.png) |

| Изображение | PSNR(in-situ vs билинейная, оба — прямой поворот 45°) |
|---|---|
| baboon | 19.25 дБ |
| checker | 17.36 дБ |
| circles | 31.22 дБ |
| gradient | 18.55 дБ |
| sonoma_photo | 19.27 дБ |

In-situ поворот через три скоса использует целочисленные сдвиги строк/столбцов — O(1) памяти, но точность ограничена ближайшим соседом. PSNR показывает отличие от билинейного метода (оба — прямой поворот на 45°).

---

## Лабораторная работа №4 — Свёртка

### ФНЧ

**baboon:**

![](results4/baboon_lpf_gaussian.png)

![](results4/baboon_lpf_impulse.png)

**circles:**

![](results4/circles_lpf_gaussian.png)

![](results4/circles_lpf_impulse.png)

**gradient:**

![](results4/gradient_lpf_gaussian.png)

![](results4/gradient_lpf_impulse.png)

**sonoma_photo:**

![](results4/sonoma_photo_lpf_gaussian.png)

![](results4/sonoma_photo_lpf_impulse.png)

### ФВЧ, zero-crossing, резкость

**baboon:**

![](results4/baboon_hpf.png)

**circles:**

![](results4/circles_hpf.png)

**gradient:**

![](results4/gradient_hpf.png)

**sonoma_photo:**

![](results4/sonoma_photo_hpf.png)

### PSNR фильтрации (дБ)

| Изображение | Шум | Box 3×3 | Box 7×7 | Gauss σ=2 | Порог | Bilateral |
|---|---|---|---|---|---|---|
| baboon | Гауссов σ²=400 | 24.76 | 23.05 | 23.68 | 24.33 | **25.99** |
| baboon | Импульс 10% | 22.31 | 22.47 | 22.97 | 15.52 | 17.63 |
| circles | Гауссов σ²=400 | 30.37 | 30.11 | 30.98 | 26.11 | **30.48** |
| circles | Импульс 10% | 23.53 | 26.42 | **26.70** | 15.09 | 15.84 |
| gradient | Гауссов σ²=400 | 31.56 | **38.78** | 38.51 | 26.26 | 31.13 |
| gradient | Импульс 10% | 23.39 | **27.91** | 27.79 | 14.77 | 15.43 |
| sonoma_photo | Гауссов σ²=400 | 31.25 | 34.31 | **34.95** | 26.08 | 30.42 |
| sonoma_photo | Импульс 10% | 24.72 | 29.45 | **29.55** | 15.63 | 17.32 |

### Билатеральный фильтр

**baboon:**

![](results4/baboon_bilateral.png)

**circles:**

![](results4/circles_bilateral.png)

**gradient:**

![](results4/gradient_bilateral.png)

**sonoma_photo:**

![](results4/sonoma_photo_bilateral.png)

### Морфологический градиент (нелинейный ФВЧ)

**baboon:**

![](results4/baboon_morph_hpf.png)

**circles:**

![](results4/circles_morph_hpf.png)

**gradient:**

![](results4/gradient_morph_hpf.png)

### Оптимальный σ гауссова фильтра

| Изображение | Гауссов шум | Импульсный шум |
|---|---|---|
| baboon | σ=0.75, PSNR=26.08 дБ | σ=1.25, PSNR=23.29 дБ |
| circles | σ=1.25, PSNR=31.73 дБ | σ=2.00, PSNR=26.72 дБ |
| gradient | σ=5.00, PSNR=45.39 дБ | σ=5.00, PSNR=29.73 дБ |
| sonoma_photo | σ=2.00, PSNR=34.95 дБ | σ=3.00, PSNR=30.21 дБ |

**baboon:**

![](results4/baboon_optimal_sigma.png)

**circles:**

![](results4/circles_optimal_sigma.png)

**gradient:**

![](results4/gradient_optimal_sigma.png)

**sonoma_photo:**

![](results4/sonoma_photo_optimal_sigma.png)

### Выводы

#### Фильтрация гауссового шума (σ²=400)

- **Box 3×3** хорошо работает на детальных изображениях (baboon: 24.76 дБ), но слабее на гладких.
- **Box 7×7** выигрывает на гладких изображениях (gradient: 38.78 дБ), но размывает мелкие структуры.
- **Гауссов σ=2** — лучший баланс среди линейных фильтров, рекомендован как стандартный НЧ-фильтр.
- **Билатеральный** — лучший на детальных изображениях (baboon: 25.99 дБ), сохраняет границы. На гладких уступает Box 7×7 (gradient: 31.13 vs 38.78 дБ), т.к. граничная функция там ничего не даёт.
- **Оптимальный σ** — даёт существенный выигрыш (gradient: 45.39 дБ при σ=5.0 vs 38.78 при σ=2). Параметр σ должен подбираться под изображение.

#### Фильтрация импульсного шума (10% соль/перец)

- **Box и Гауссов** частично подавляют шум (23–30 дБ), но не устраняют полностью.
- **Пороговый фильтр t=50: ~15 дБ — результат хуже исходного зашумлённого изображения.** Причина: если центральный пиксель сам является шумом (0 или 255), порог исключает корректных соседей и фильтр вырождается. Для импульсного шума нужен **медианный фильтр** (нелинейный).

#### Детектирование границ

- **Лапласиан** — быстрый, но реагирует на шум. Пригоден только на чистых изображениях.
- **LoG + zero-crossing** — гауссово размытие подавляет шум перед лапласианом; zero-crossing даёт **тонкие замкнутые контуры**. Рекомендован для точного детектирования.
- **Морфологический градиент** (дилатация − эрозия) — нелинейный, устойчив к шуму, но контуры шире. Хорош для выделения крупных объектов. Размер ядра управляет масштабом детектируемых структур.

#### Сводная таблица рекомендаций

| Задача | Рекомендуемый фильтр |
|---|---|
| Гауссов шум, детальное изображение | Билатеральный или Гауссов (подобранный σ) |
| Гауссов шум, гладкое изображение | Box 7×7 или Гауссов (большой σ) |
| Импульсный шум (соль/перец) | Медианный (нелинейный); Box как паллиатив |
| Детектирование границ | LoG + zero-crossing |
| Выделение крупных объектов | Морфологический градиент |
| Повышение резкости | Unsharp masking (на предварительно сглаженном) |
| Быстрая обработка больших изображений | Box через интегральное изображение — O(1) |

---

## Лабораторная работа №5 — Цветовые пространства

### Каналы RGB, HSV, YCbCr, CIELab

**baboon:**

![](results5/baboon_rgb_channels.png)

![](results5/baboon_hsv_channels.png)

![](results5/baboon_ycbcr_channels.png)

![](results5/baboon_lab_channels.png)

**sonoma_photo:**

![](results5/sonoma_photo_rgb_channels.png)

![](results5/sonoma_photo_hsv_channels.png)

![](results5/sonoma_photo_ycbcr_channels.png)

![](results5/sonoma_photo_lab_channels.png)

**circles:**

![](results5/circles_rgb_channels.png)

![](results5/circles_hsv_channels.png)

![](results5/circles_ycbcr_channels.png)

![](results5/circles_lab_channels.png)

**checker:**

![](results5/checker_rgb_channels.png)

![](results5/checker_hsv_channels.png)

![](results5/checker_ycbcr_channels.png)

![](results5/checker_lab_channels.png)

### Выравнивание гистограммы

**baboon:**

![](results5/baboon_histeq.png)

**sonoma_photo:**

![](results5/sonoma_photo_histeq.png)

**circles:**

![](results5/circles_histeq.png)

**checker:**

![](results5/checker_histeq.png)

### Баланс белого

| baboon | sonoma_photo | circles | checker |
|---|---|---|---|
| ![](results5/baboon_whitebalance.png) | ![](results5/sonoma_photo_whitebalance.png) | ![](results5/circles_whitebalance.png) | ![](results5/checker_whitebalance.png) |

### Цветовая квантизация (k-means)

**baboon:**

![](results5/baboon_quantize.png)

**sonoma_photo:**

![](results5/sonoma_photo_quantize.png)

**circles:**

![](results5/circles_quantize.png)

**checker:**

![](results5/checker_quantize.png)

### Сдвиг оттенка

**baboon:**

![](results5/baboon_hueshift.png)

**sonoma_photo:**

![](results5/sonoma_photo_hueshift.png)

**circles:**

![](results5/circles_hueshift.png)

**checker:**

![](results5/checker_hueshift.png)

### Хроматический ключ

![](results5/chromakey_demo.png)

**baboon:**

![](results5/baboon_chromakey.png)

**sonoma_photo:**

![](results5/sonoma_photo_chromakey.png)

**circles:**

![](results5/circles_chromakey.png)

**checker:**

![](results5/checker_chromakey.png)

---

### Выводы

#### Точность конвертации

RMSE = 0.0000 для всех пространств (RGB↔HSV, RGB↔YCbCr, RGB↔CIELab). Конвертации реализованы без потерь — можно переходить между пространствами для обработки и возвращаться в RGB без артефактов.

#### Эквализация гистограммы

| Метод | Результат |
|---|---|
| RGB (все каналы независимо) | Нарушает баланс R/G/B → **искажает цветность**, результат неестественный |
| HSV (канал V) | Повышает контраст, сохраняет оттенок H и насыщенность S |
| **YCbCr (канал Y)** | **Лучший результат**: контраст улучшен, цвета визуально не изменены |

**Вывод:** для эквализации цветных изображений следует обрабатывать только яркостный канал — YCbCr(Y).

#### Выбор цветового пространства по задаче

| Задача | RGB | HSV | YCbCr |
|---|---|---|---|
| Эквализация гистограммы | плохо | удовлетворительно | **хорошо** |
| Сдвиг оттенка | сложно | **просто (канал H)** | сложно |
| Хромакей (удаление фона по цвету) | плохо | **хорошо (H + S)** | плохо |
| Раздельная обработка яркости/цвета | нельзя | можно | **лучше** |
| Баланс белого | напрямую | сложнее | напрямую |

- **HSV** незаменим для задач, работающих с оттенком: хромакей (green ≈ 120°, фильтрация по H и S) и сдвиг тона.
- **YCbCr** лучший для раздельной обработки яркости и цвета (эквализация, сжатие).
- **CIELab** перцептуально равномерен (ΔE ≈ видимое различие), но вычислительно дороже и не даёт преимуществ в простых операциях.

#### Цветовая квантизация (k-means)

- **k=4**: сильная постеризация, цвета грубо усреднены.
- **k=8**: заметные, но терпимые артефакты.
- **k=16**: визуально приемлемо для большинства изображений.

Кластеризация в RGB-пространстве субоптимальна из-за неравномерности восприятия; в практических задачах лучше использовать CIELab.
