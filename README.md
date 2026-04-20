# Компьютерная графика — Лабораторные работы

---

## Лабораторная работа №1 — Анализ изображений

Статистический анализ яркости (BT.601): гистограмма, среднее, дисперсия, квартили, энтропия, энергия, асимметрия, эксцесс, GLCM, PSNR.

### Запуск

```bash
javac Lab1.java

# Указать файлы явно (поддерживаются jpg, png и любые другие)
java Lab1 examples/checker.png photo.jpg --outdir results

# Без аргументов — все PNG из examples/
java Lab1
```

Результаты сохраняются в `results/`: статистики → `statistics.txt`, GLCM → `<name>_glcm_*.png`, график PSNR → `<name>_psnr_plot.png`, зашумлённое → `<name>_noisy.png`.

### Результаты

#### Статистики (`results/statistics.txt`)

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

#### GLCM (горизонтальное смещение, логарифмическая шкала)

| checker | gradient | noise |
|---|---|---|
| ![](results/checker_glcm_horiz_0_1_.png) | ![](results/gradient_glcm_horiz_0_1_.png) | ![](results/noise_glcm_horiz_0_1_.png) |

**Фотографии:**

| sonoma_photo | imac_blue_photo | baboon |
|---|---|---|
| ![](examples/sonoma_photo.jpg) | ![](examples/imac_blue_photo.jpg) | ![](examples/baboon.png) |
| ![](results/sonoma_photo_glcm_horiz_0_1_.png) | ![](results/imac_blue_photo_glcm_horiz_0_1_.png) | ![](results/baboon_glcm_horiz_0_1_.png) |

#### График PSNR(дисперсия шума)

| checker | sonoma_photo | baboon |
|---|---|---|
| ![](results/checker_psnr_plot.png) | ![](results/sonoma_photo_psnr_plot.png) | ![](results/baboon_psnr_plot.png) |

---

## Лабораторная работа №2 — Преобразование Фурье

Прямое и обратное быстрое преобразование Фурье (FFT/IFFT) для 1D сигналов и 2D изображений произвольного размера. Нулевые частоты в центре спектра. Спектр Хаара.

### Запуск

```bash
javac Lab2.java
java Lab2 [--outdir results2]
```

Результаты → `results2/`: спектры, восстановленные изображения, лог `log.txt`.

### 1D — смесь синусоид

**Сигнал 1:** sin(50 Гц) + 0.5·sin(120 Гц) + 0.25·sin(300 Гц), N=512, fs=1000 Гц

![](results2/signal1_fft.png)

**Сигнал 2:** 5 синусоид (10, 80, 150, 250, 400 Гц) + гауссов шум σ=0.1

![](results2/signal2_fft.png)

RMSE после IFFT ≈ 1e-14 — машинная точность.

---

### 2D — синтетические изображения из синусоид

**Горизонтальная синусоида** (f=8):

![](results2/sin_horizontal_combined.png)

**Вертикальная синусоида** (f=8):

![](results2/sin_vertical_combined.png)

**Диагональная синусоида** (f=6):

![](results2/sin_diagonal_combined.png)

**Смесь трёх синусоид** (4,0 + 0,12 + 20,20):

![](results2/sin_mix_combined.png)

---

### 2D — реальные изображения

**checker:**

| Исходное | Спектр Фурье | IFFT | Спектр Хаара |
|---|---|---|---|
| ![](examples/checker.png) | ![](results2/checker_spectrum.png) | ![](results2/checker_ifft.png) | ![](results2/checker_haar.png) |

**gradient:**

| Исходное | Спектр Фурье | IFFT | Спектр Хаара |
|---|---|---|---|
| ![](examples/gradient.png) | ![](results2/gradient_spectrum.png) | ![](results2/gradient_ifft.png) | ![](results2/gradient_haar.png) |

**circles:**

| Исходное | Спектр Фурье | IFFT | Спектр Хаара |
|---|---|---|---|
| ![](examples/circles.png) | ![](results2/circles_spectrum.png) | ![](results2/circles_ifft.png) | ![](results2/circles_haar.png) |

**baboon (фото):**

| Исходное | Спектр Фурье | IFFT | Спектр Хаара |
|---|---|---|---|
| ![](examples/baboon.png) | ![](results2/baboon_spectrum.png) | ![](results2/baboon_ifft.png) | ![](results2/baboon_haar.png) |

**sonoma_photo (фото):**

| Исходное | Спектр Фурье | IFFT | Спектр Хаара |
|---|---|---|---|
| ![](examples/sonoma_photo.jpg) | ![](results2/sonoma_photo_spectrum.png) | ![](results2/sonoma_photo_ifft.png) | ![](results2/sonoma_photo_haar.png) |

RMSE(IFFT) ≈ 1e-13 для всех изображений.

---

### Сравнение спектров Фурье и Хаара

Оба спектра отображаются с DC-компонентой в центре. Для фотографий оба показывают концентрацию энергии в центре (низкие частоты) и спад к краям (высокие частоты).

![](results2/baboon_spectra_comparison.png)

![](results2/sonoma_photo_spectra_comparison.png)

---

### Спектр Хаара — 1D сигналы

![](results2/signal1_haar.png)

![](results2/signal2_haar.png)

### Спектр Хаара — синтетические изображения

| sin_horizontal | sin_vertical | sin_diagonal | sin_mix |
|---|---|---|---|
| ![](results2/sin_horizontal_haar.png) | ![](results2/sin_vertical_haar.png) | ![](results2/sin_diagonal_haar.png) | ![](results2/sin_mix_haar.png) |

---

## Лабораторная работа №3 — Геометрические преобразования и интерполяция

Поворот изображения на произвольный угол с тремя методами интерполяции: ближайший сосед (NN), билинейная, бикубическая (ядро Keys, a=−0.5). Сравнение по качеству (PSNR) и времени.

Дополнительные задания: скос (shear), поворот + масштабирование, поворот in-situ через разложение на три скоса.

### Запуск

```bash
javac Lab3.java
java Lab3 [--outdir results3]
```

Результаты → `results3/`: сравнительные PNG по каждому изображению и углу, лог `log.txt`.

---

### Поворот — сравнение методов интерполяции

**baboon:**

| 30° | 45° | 90° |
|---|---|---|
| ![](results3/baboon_rot030_comparison.png) | ![](results3/baboon_rot045_comparison.png) | ![](results3/baboon_rot090_comparison.png) |

**checker:**

| 30° | 45° | 90° |
|---|---|---|
| ![](results3/checker_rot030_comparison.png) | ![](results3/checker_rot045_comparison.png) | ![](results3/checker_rot090_comparison.png) |

**sonoma_photo:**

| 30° | 45° | 90° |
|---|---|---|
| ![](results3/sonoma_photo_rot030_comparison.png) | ![](results3/sonoma_photo_rot045_comparison.png) | ![](results3/sonoma_photo_rot090_comparison.png) |

---

### Качество и скорость (baboon 200×200)

| Угол | Метод | Время | PSNR vs Bilinear |
|---|---|---|---|
| 30° | Ближайший сосед | ~4 мс | 31.5 дБ |
| 30° | Билинейная | ~3 мс | — (эталон) |
| 30° | Бикубическая | ~12 мс | 41.8 дБ |
| 45° | Ближайший сосед | ~1 мс | 32.4 дБ |
| 45° | Билинейная | ~1 мс | — (эталон) |
| 45° | Бикубическая | ~2 мс | 42.0 дБ |

Бикубическая интерполяция даёт прирост ~10 дБ по PSNR относительно билинейной при увеличении времени в ~3–4 раза. Ближайший сосед быстрее всего, но уступает ~7 дБ.

---

### Дополнительное задание 1 — Скос (shear)

| baboon | checker | circles |
|---|---|---|
| ![](results3/baboon_shear_comparison.png) | ![](results3/checker_shear_comparison.png) | ![](results3/circles_shear_comparison.png) |

---

### Дополнительное задание 2 — Поворот + масштабирование (45°, ×1.5)

| baboon | checker | sonoma_photo |
|---|---|---|
| ![](results3/baboon_rot45_scale_comparison.png) | ![](results3/checker_rot45_scale_comparison.png) | ![](results3/sonoma_photo_rot45_scale_comparison.png) |

---

### Дополнительное задание 3 — Поворот in-situ (3 скоса, 45°)

Разложение R(θ) = Sx(−tan θ/2) · Sy(sin θ) · Sx(−tan θ/2). Каждый скос — целочисленный сдвиг строк/столбцов in-place, дополнительный буфер не используется.

| baboon | checker | sonoma_photo |
|---|---|---|
| ![](results3/baboon_insitu_comparison.png) | ![](results3/checker_insitu_comparison.png) | ![](results3/sonoma_photo_insitu_comparison.png) |

PSNR(in-situ vs bilinear) ≈ 17–31 дБ — потери из-за целочисленного округления сдвигов.

---

## Лабораторная работа №4 — Свёртка

2D свёртка для произвольного ядра. ФНЧ (box, гаусс, порог). ФВЧ (лапласиан, LoG). Детектирование границ через переходы нулевого уровня. Повышение резкости (unsharp masking).

Дополнительные задания: box-фильтр через интегральное изображение, билатеральный фильтр, морфологический градиент (нелинейный ФВЧ), подбор оптимального σ по PSNR.

### Запуск

```bash
javac Lab4.java
java Lab4 [--outdir results4]
```

---

### ФНЧ — сравнение на гауссовом и импульсном шуме

**baboon:**

![](results4/baboon_lpf_gaussian.png)

![](results4/baboon_lpf_impulse.png)

**sonoma_photo:**

![](results4/sonoma_photo_lpf_gaussian.png)

![](results4/sonoma_photo_lpf_impulse.png)

---

### ФВЧ, zero-crossing, резкость

**baboon:**

![](results4/baboon_hpf.png)

**circles:**

![](results4/circles_hpf.png)

**sonoma_photo:**

![](results4/sonoma_photo_hpf.png)

---

### PSNR фильтрации (baboon 200×200)

| Шум | Box 3×3 | Box 7×7 | Gauss σ=2 | Порог t=30/50 | Bilateral |
|---|---|---|---|---|---|
| Гауссов σ²=400 | 24.76 дБ | 23.05 дБ | 23.68 дБ | 24.33 дБ | **25.99 дБ** |
| Импульс 10% | 22.31 дБ | 22.47 дБ | 22.97 дБ | 15.52 дБ | 17.63 дБ |

---

### Дополнительное задание 1 — Box-фильтр через интегральное изображение

PSNR(интегральный vs свёртка) > 50 дБ — результаты идентичны, разница только в округлении с плавающей точкой.

---

### Дополнительное задание 2 — Билатеральный фильтр

![](results4/baboon_bilateral.png)

![](results4/sonoma_photo_bilateral.png)

---

### Дополнительное задание 3 — Нелинейный ФВЧ (морфологический градиент)

![](results4/baboon_morph_hpf.png)

![](results4/circles_morph_hpf.png)

---

### Дополнительное задание 4 — Оптимальный σ гауссова фильтра по PSNR

| Изображение | Гауссов шум | Импульсный шум |
|---|---|---|
| baboon | σ=0.75, PSNR=26.08 дБ | σ=1.25, PSNR=23.29 дБ |
| circles | σ=1.25, PSNR=31.73 дБ | σ=2.00, PSNR=26.72 дБ |
| gradient | σ=5.00, PSNR=45.39 дБ | σ=5.00, PSNR=29.73 дБ |
| sonoma_photo | σ=2.00, PSNR=34.95 дБ | σ=3.00, PSNR=30.21 дБ |

![](results4/baboon_optimal_sigma.png)

![](results4/sonoma_photo_optimal_sigma.png)
