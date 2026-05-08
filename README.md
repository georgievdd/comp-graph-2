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

### 2D — реальные изображения

| Исходное | Спектр Фурье | IFFT | Вейвлет-коэф. Хаара |
|---|---|---|---|
| ![](examples/checker.png) | ![](results2/checker_spectrum.png) | ![](results2/checker_ifft.png) | ![](results2/checker_haar.png) |
| ![](examples/gradient.png) | ![](results2/gradient_spectrum.png) | ![](results2/gradient_ifft.png) | ![](results2/gradient_haar.png) |
| ![](examples/circles.png) | ![](results2/circles_spectrum.png) | ![](results2/circles_ifft.png) | ![](results2/circles_haar.png) |
| ![](examples/baboon.png) | ![](results2/baboon_spectrum.png) | ![](results2/baboon_ifft.png) | ![](results2/baboon_haar.png) |
| ![](examples/sonoma_photo.jpg) | ![](results2/sonoma_photo_spectrum.png) | ![](results2/sonoma_photo_ifft.png) | ![](results2/sonoma_photo_haar.png) |

### Энергетический спектр Хаара — 2D изображения

![](results2/baboon_haar_spectrum.png)

![](results2/sonoma_photo_haar_spectrum.png)

### Сравнение спектров Фурье и Хаара (пирамидальное представление)

![](results2/baboon_spectra_comparison.png)

![](results2/sonoma_photo_spectra_comparison.png)

### Вейвлет-коэффициенты Хаара и энергетический спектр — 1D сигналы

**Сигнал 1 (sin(50)+0.5·sin(120)+0.25·sin(300)):**

![](results2/signal1_haar.png)

![](results2/signal1_haar_spectrum.png)

**Сигнал 2 (5 синусоид + гауссов шум):**

![](results2/signal2_haar.png)

![](results2/signal2_haar_spectrum.png)

### Спектр Хаара — синтетические изображения

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

### Время поворота (мс)

| Изображение | Угол | NN | Билинейная | Бикубическая |
|---|---|---|---|---|
| baboon (200×200) | 30° | 7 | 7 | 18 |
| baboon (200×200) | 45° | 1 | 9 | 7 |
| checker (256×256) | 30° | 1 | 1 | 5 |
| checker (256×256) | 45° | 0 | 1 | 5 |
| sonoma_photo (256×256) | 30° | 0 | 1 | 4 |
| sonoma_photo (256×256) | 45° | 0 | 2 | 4 |

Бикубическая интерполяция медленнее (16 соседей vs 4 у билинейной и 1 у ближайшего соседа), зато визуально даёт наиболее гладкий результат без ступенчатости. Ближайший сосед — самый быстрый, но даёт блочные артефакты.

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

---

## Лабораторная работа №5 — Ранговая фильтрация и морфологические операции

### Сравнение фильтров: гауссов шум (σ²=400)

**baboon:**

![](results5/baboon_gaussian.png)

![](results5/baboon_rank_gaussian.png)

**circles:**

![](results5/circles_gaussian.png)

**gradient:**

![](results5/gradient_gaussian.png)

**sonoma_photo:**

![](results5/sonoma_photo_gaussian.png)

### Сравнение фильтров: импульсный шум (10% соль/перец)

**baboon:**

![](results5/baboon_impulse.png)

![](results5/baboon_rank_impulse.png)

**circles:**

![](results5/circles_impulse.png)

### Морфологические операции (полутоновые)

**baboon — диск r=1:**

![](results5/baboon_morph_disk1.png)

**baboon — верх/низ шляпы и градиент:**

![](results5/baboon_morph_hat.png)

**baboon — диск r=2:**

![](results5/baboon_morph_disk2.png)

**circles — диск r=1:**

![](results5/circles_morph_disk1.png)

**circles — диск r=2:**

![](results5/circles_morph_disk2.png)

### PSNR сравнение (дБ)

*Числовые значения — в results5/log.txt после запуска Lab5.java.*

---

## Лабораторная работа №6 — Сегментация и вычисление признаков

### circles

![](results6/circles_seg.png)

### baboon

![](results6/baboon_seg.png)

### checker

![](results6/checker_seg.png)

### gradient

![](results6/gradient_seg.png)

### sonoma_photo

![](results6/sonoma_photo_seg.png)

### Результаты (порог Оцу, разметка, признаки формы)

| Изображение | Порог Оцу | 4-св. регионов | 8-св. регионов | Регионов S>30 | Кругов (Kr2<1.3) |
|---|---|---|---|---|---|
| circles | 114 | 2 | 2 | 2 | **2** |
| baboon | 128 | 722 | 351 | 12 | 0 |
| checker | 0 | 1 | 1 | 1 | 1 |
| gradient | 127 | 1 | 1 | 1 | 0 |
| sonoma_photo | 124 | 26 | 7 | 2 | 0 |

*Моменты и полная таблица признаков — в results6/log.txt.*

---

## ДЗ — Улучшение видимости слабоконтрастного текста (9.tif)

**Задача:** изображение содержит одновременно слабоконтрастный светлый текст на тёмном фоне и тёмный текст на светлом фоне. Глобальные методы улучшают один тип контраста за счёт другого — нужен локальный адаптивный метод.

**Критерий качества:** среднее локальное значение контраста Михельсона по тайлам 32×32:
$$Q = \text{mean}_\text{tile}\!\left\{\frac{\max - \min}{\max + \min + 1}\right\} \in [0,1], \text{ выше} = \text{лучше}$$

### Сравнение методов

![](results_dz/comparison.png)

### Результаты критерия

| Метод | Q (↑ лучше) |
|---|---|
| Оригинал | 0.516 |
| Эквализация гистограммы | 0.185 |
| CLAHE (tile=64, clip=3) | **0.520** |
| CLAHE (tile=64, clip=6) | 0.516 |
| Unsharp Mask (σ=3, k=2) | 0.543 |
| **Unsharp Mask (σ=5, k=3)** | **0.561** |
| Retinex SSR (σ=40) | 0.218 |
| Сигмоида (tile=64, T=20) | 0.514 |
| Вычитание фона (σ=60) | 0.119 |

**Вывод:** нерезкое маскирование (σ=5, k=3) даёт наибольший Q=0.56: усиливает высокочастотные перепады (границы текст/фон). CLAHE (clip=3) — предпочтителен при наличии шума (clip ограничивает артефакты). Глобальные методы снижают Q — перераспределяют контраст глобально и «выравнивают» локальные различия.

*Полный анализ — results_dz/log.txt.*
