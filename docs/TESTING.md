# TESTING.md — Elle Test Listesi

Her faz sonunda **önce** aşağıdaki sabit liste çalıştırılır, **sonra** Claude
Code'un o faz için söylediği ek adımlar.

Her maddenin yanındaki faz, o davranışın hangi fazda kazanıldığını gösterir.
Bir madde bozulursa önce oraya bakılır — hangi kodun bu davranışı ürettiği
oradan bulunur.

---

## Sabit Regresyon Listesi

**Önce otomatik testler:**

```bash
./gradlew :app:testDebugUnitTest
```

Geçmeden elle teste başlanmaz — kırmızı bir birim testi varken cihazda gözlem
yapmak zaman kaybıdır.

**Yeni API kullanan değişikliklerden sonra lint şart**

```bash
./gradlew :app:lintDebug
```

`assembleDebug` yeni API kullanımını **yakalamaz**: `compileSdk` sınıf yolunda
her şey vardır, derleme sessizce geçer. Sorun ancak `minSdk`'ye yakın bir
cihazda çalışma anında `NoClassDefFoundError` olarak çıkar — yani test
edilmeyen bir API aralığında, yani muhtemelen kullanıcıda.

Faz 10a'da tam olarak bu oldu: `java.time.LocalDate.now()` `assembleDebug`'dan
geçti, `lintDebug` ise `Call requires API level 26, or core library
desugaring (current min is 24) [NewApi]` diye hata verip derlemeyi durdurdu.

Kural: `java.time`, yeni Compose/AndroidX API'si veya platform çağrısı ekleyen
her değişiklikten sonra lint koşturulur. Yalnızca birim testi ve
`assembleDebug` yeterli değildir.

Sonra aşağıdaki liste, sırayla ve tek oturumda çalıştırılır. Bir madde kalırsa sonrakilere devam
etmeden bildirin; sonraki maddeler zaten bozuk bir durumun üstüne binebilir.

| # | Test | Beklenen | Kazanıldığı faz |
|---|---|---|---|
| 1 | Uygulamayı aç | Açılıyor, çökmüyor | 0 · Hilt grafı 4 |
| 2 | FAB'a bas | Sheet açılıyor | 0 |
| 3 | Ad + fiyat gir, Kaydet | Sheet kapanıyor, öğe listeye giriyor | 0 · repository üzerinden 5a |
| 4 | FAB'a tekrar bas | Alanlar **boş** geliyor | 0 |
| 5 | Sheet'i scrim'e dokunarak / geri tuşuyla kapat | Kapanıyor | 0 |
| 6 | Bir kartı hafifçe kaydır (~1/5) bırak | **Silmiyor**, temiz geri yaslanıyor | Hotfix serisi |
| 7 | Aynı satırda 8-10 kez ardışık hafif kaydır | Hiçbiri silmiyor, **birikme yok** | Hotfix serisi |
| 8 | Hızlı kısa fiske | **Silmiyor** (mesafe şartı) | Hotfix serisi |
| 9 | Tam kaydır | **Siliyor** | Hotfix serisi |
| 10 | Ters yöne sürükle | Kart **kıpırdamıyor** | Hotfix serisi |
| 11 | 159,99 ve 59,90 ekle | Toplam tam olarak **219,89**, kuruş hatası yok | 5a |
| 12 | Uygulamayı tamamen kapat, yeniden aç | Liste **duruyor** (Room zinciri) | 5a |
| 13 | Ekran döndür | Liste duruyor, **titremiyor** | 5a |
| 14 | Sheet açık ve metin yazılıyken ekran döndür | Sheet **açık kalıyor**, metin duruyor | 5b |
| 15 | Sistem temasını koyuya al | Tüm metinler okunabilir, kartlar arka plandan ayrışıyor | 1b · kontrast 1c |
| 16 | Cihaz dilini İngilizceye al | Metinler çevrilmiş geliyor | 1b |
| 17 | Boş ad veya geçersiz fiyatla Kaydet'e bas | Sheet **açık kalıyor**, hata ilgili alanın **altında** | 6 |
| 18 | Bir satırı sil, Snackbar'a dokunma | Birkaç saniyede **kendiliğinden** kayboluyor | 6 |
| 19 | Sil, sonra "Geri al"a bas | Öğe **eski sırasına** dönüyor | 6 |
| 20 | Ayarlar ikonuna bas, geri oku ve sistem geri tuşuyla dön | Ayarlar açılıyor, iki yol da ana ekrana dönüyor, geri yığınında **birikme yok** | 9b |
| 21 | Ayarlarda başka bir para birimi seç, uygulamayı tamamen kapat, yeniden aç | Seçim **duruyor**, toplam o para biriminde | 9b |
| 22 | Ayarlar → Döviz Kurları, USD kurunu değiştir, kaydet, ana ekrana dön | Toplam **yeni kurla** hesaplanıyor | 9b-2 |
| 23 | Kur ekranında `0`, `-5`, `1,23456`, `1000,0001` gir ve kaydet | Dördü de **alan altında** hata veriyor, hiçbiri kaydedilmiyor, **çökme yok** | 9b-2 |
| 24 | Kur değiştir, uygulamayı tamamen kapat, yeniden aç | Kur **duruyor**, "Son düzenleme" tarihi görünüyor | 9b-2 |
| 25 | Kur ekranında "Varsayılana dön" → Sıfırla | Alanlar 42,85 / 46,2 / 53,9'a dönüyor, tarih yerine **"hiç düzenlenmedi"** yazısı geliyor | 9b-2 |
| 26 | Kur alanına yazarken klavye açıkken Kaydet ve "Varsayılana dön"e ulaş | Pencere klavye kadar küçülüyor, **tek fiskede** ikisine de ulaşılıyor | 9b-2 · `adjustResize` hotfix |

**Klavye açıkken buton erişilebilirliği — her fazda kontrol edilecek**

Metin alanı olan **her** ekranda, klavye açıkken ekranın alt kısmındaki
eylemlerin erişilebilir olup olmadığı ölçülür. Bugün bu ekranlar:

| Ekran | Kontrol edilecek | Bugünkü durum |
|---|---|---|
| Ekleme sheet'i | Kaydet tam görünür, kaydırınca sabit | **Geçiyor** — `ModalBottomSheet` kendi `imePadding()`'ini uyguluyor (Faz 9b-1 hotfix) |
| Kur ekranı | Kaydet ve "Varsayılana dön" | **Geçiyor** — `adjustResize` ile pencere küçülüyor, butonlara tek fiskede ulaşılıyor |
| Ayarlar ekranı | — | Metin alanı yok, konu dışı |
| Faz 10 tarih seçici, Faz 15 düzenleme ekranı | eklenince buraya yazılacak | henüz yok |

Ölçüm `show_ime_with_hard_keyboard 1` ile yapılır (aşağıdaki bölüm), yoksa
emülatörde klavye hiç çizilmez ve test sessizce yanlış sonuç verir. Klavyenin
üst kenarı, kaydırma düğümünün (`android.widget.ScrollView`) alt sınırından
okunur — pencere `adjustResize` ile küçüldüğü için ikisi aynı çizgidir.

**Sheet için ayrıca çift uygulama kontrolü:** `ModalBottomSheet` kendi
`imePadding()`'ini uyguluyor, pencere de küçülüyor. Bunlar üst üste binerse
sheet gereğinden çok kısalır. Kontrol: klavye kapalıyken sheet koordinatları
önceki ölçümle aynı mı, klavye açıkken Kaydet'in alt kenarı ile sheet'in alt
kenarı arasındaki boşluk `SheetBottomPadding` (40dp = 80px @320dpi) mu.

**Faz 16 uyarısı:** `adjustResize` geçici bir çözümdür ve uygulama
edge-to-edge'e geçtiğinde sistem tarafından yok sayılır. `enableEdgeToEdge`
eklendiği gün bu tablodaki her satır yeniden ölçülmelidir. Gerekçe
`ARCHITECTURE.md` §16'da.

**Not — beklenen davranışlar, hata değil:**
- İlk kurulumda liste **boş** başlar. Seed veri yok; boş durum ekranı Faz 8'de.
- Ardışık silmede **yalnızca son işlem** geri alınabilir; tek undo izleniyor.

---

## Emülatör Testleri

Fiziksel cihaz (OPPO A15s, CPH2179, Android 10) iki şeyi ölçemiyor: ekranı
tek boyutta ve **423dp** genişlikte (720x1600 @ 272dpi override), ve TalkBack
açılınca donuyor. Dar ekran ve farklı Android sürümü testleri emülatörde
yapılır.

### AVD'ler

| AVD | Çözünürlük | Yoğunluk | Efektif genişlik | API | Ne için |
|---|---|---|---|---|---|
| `subtrack_narrow_api29` | 720x1280 | 320 dpi | **360dp** | 29 | Dar ekran, sığma/sarma testleri. Test cihazıyla aynı Android sürümü. |
| `subtrack_wide_api34` | 1080x2400 | 420 dpi | 411dp | 34 | Güncel Android davranışları, koyu tema, dynamic color |

360dp keyfi değil: Compose bileşenlerinin sığıp sığmadığı bu eşiğe göre
hesaplanıyor, ve yaygın bütçe telefonlarının genişliği bu. Fiziksel cihaz
423dp olduğu için dar durumu hiç göstermiyor.

Oluşturma (yalnızca bir kez gerekir):

```bash
avdmanager create avd -n subtrack_narrow_api29 -k "system-images;android-29;google_apis_playstore;x86_64" --abi x86_64
avdmanager create avd -n subtrack_wide_api34 -k "system-images;android-34;google_apis_playstore;x86_64" -d pixel_6 --abi x86_64
```

`avdmanager` dar profili varsayılan 320x640 @ 160dpi ile kurar; sonra
`~/.android/avd/subtrack_narrow_api29.avd/config.ini` içinde
`hw.lcd.width=720`, `hw.lcd.height=1280`, `hw.lcd.density=320` yapılır.
İki AVD'de de `hw.keyboard=yes` — donanım klavyesi olmadan `adb shell input
text` yazarken soft klavye açılıp düzeni kaydırıyor ve dokunma koordinatları
şaşıyor.

Başlatma (ikisi aynı anda çalışabilir, farklı port):

```bash
emulator -avd subtrack_narrow_api29 -no-snapshot-save -no-boot-anim -gpu swiftshader_indirect
```

### Soft klavye — iki yönlü, ikisi de gerekli

Her iki AVD'de `hw.keyboard=yes`. Bunun iki sonucu var ve **ikisi de tuzak**.

**Normal otomatik testte kapalı olmalı.** Açık kalırsa `adb shell input text`
sırasında klavye açılıp düzeni kaydırır, dokunma koordinatları şaşar.

```bash
adb shell settings put secure show_ime_with_hard_keyboard 0
```

**Klavyeyle ilgili bir şey ölçülecekse açılmalı.** Donanım klavyesi tanımlı
olduğu için yazılım klavyesi **hiç çizilmez**: `dumpsys input_method`
`mInputShown=true` der, IME penceresi de vardır, ama içerik inset'i sıfırdır
ve ekranda yer kaplamaz. Bu yüzden klavye testi **sessizce yanlış sonuç
verir** — hata vermez, sadece ölçtüğün şey gerçek telefonda olan şey değildir.
Faz 9b-1 kapanışında tam olarak bu oldu ve "sheet klavyeyle kaymıyor" diye
yanlış bir bulgu raporlandı.

```bash
adb shell settings put secure show_ime_with_hard_keyboard 1   # ölçümden önce
adb shell settings put secure show_ime_with_hard_keyboard 0   # ölçümden sonra
```

Klavyenin üst kenarı, uygulama penceresi `adjustResize` ile küçüldüğü için
en dıştaki kaydırma düğümünün (`android.widget.ScrollView`) alt sınırından
okunabilir; ayrı bir piksel taramasına gerek yok.

### Yazı tipi ölçeği

```bash
adb shell settings put system font_scale 2.0
adb shell settings put system font_scale 1.0
```

Değiştirdikten sonra Activity yeniden başlatılır. **Test bitince 1.0'a geri
alın** — unutulursa sonraki tüm ölçümler yanlış çıkar.

### Koyu tema

```bash
adb shell cmd uimode night yes
adb shell cmd uimode night no
```

**API 29'da çalışmıyor** — komut "Night mode: no" döndürüp değeri yazmıyor,
`settings put secure ui_night_mode 2` de tutmuyor. Koyu tema testleri
`subtrack_wide_api34` üzerinde yapılır.

### TalkBack

**Şu an yapılamıyor.** Ne API 29 ne API 34 `google_apis_playstore` imajında
Android Accessibility Suite kurulu değil (`pm list packages -u` ve
`/system/priv-app` taramasında iz yok). Kurmak için ya emülatörde bir Google
hesabıyla Play Store'a girilmeli ya da imaj değiştirilmeli. Erişilebilirlik
davranışı bu yüzden yalnızca **erişilebilirlik ağacından** doğrulanabiliyor:

```bash
adb shell uiautomator dump /sdcard/u.xml
adb exec-out cat /sdcard/u.xml
```

Bu ağaç ekran okuyucunun okuduğu şeyin ta kendisi; `contentDescription` ve
düğüm birleşmesi buradan görülür. Görmediği tek şey **özel eylemler**
(`CustomAccessibilityAction`) — dump biçimi eylem listesini içermiyor, o
yüzden "Sil" eyleminin varlığı bu yolla ne doğrulanabilir ne çürütülebilir.

### Ölçüm

`uiautomator dump` bottom sheet açıkken çoğu zaman alttaki pencereyi
döndürüyor, sheet içindeki bileşenleri göremiyor. Piksel ölçümü için ham
ekran görüntüsü kullanılır:

```bash
adb exec-out screencap > ekran.raw
```

Baştaki 12 veya 16 baytlık başlıktan sonra RGBA gelir; `dosya_boyutu -
genişlik*yükseklik*4` başlığın hangisi olduğunu verir. Bu makinede görüntü
kütüphanesi yok ve kurulmuyor.

### Otomatik testler

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
```

`connectedDebugAndroidTest` bitince uygulamayı **kaldırıyor**. Ekran
görüntüsü veya elle test yapılacaksa testten sonra yeniden kurun.

### Açılış süresi

```bash
adb shell am force-stop com.elinacn.subtrack
adb shell am start -W -n com.elinacn.subtrack/.MainActivity
```

Beş tekrarın medyanı alınır. Debug build ölçümüdür, release değil —
karşılaştırma yalnızca kendi içinde anlamlıdır.

---

## Faza Özel Testler

Sabit liste geçtikten sonra çalıştırılır. Claude Code her faz sonunda bu
bölümü doldurur; test bitince buradaki maddeler silinir, kalıcı hale gelen
davranışlar yukarıdaki sabit listeye taşınır.

**Format:**

```markdown
### [Faz N] — YYYY-AA-GG

a) Ne yapılacak → ne beklenmeli
b) ...

Kritik adım: (hangisi bu fazın asıl sınavı ve neden)
Bilinen eksik: (bu fazda kasıtlı olarak yapılmayan, hata sanılmaması gereken şey)
```

**Bir maddenin sabit listeye taşınma ölçütü:** davranış artık o fazın değil,
uygulamanın kalıcı bir özelliği ise ve sonraki fazlarda bozulabilecekse.

### (şu an boş — bir sonraki faz sonunda doldurulacak)
