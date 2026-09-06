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
| 27 | Ekleme formunda tarih seç, kaydet | Kartta doğru gün sayısı: cihaz tarihi ile seçilen tarih arasındaki **takvim günü** farkı | 10a |
| 28 | Tarih **seçmeden** kaydet | Kayıt oluşuyor, kartta gösterge **yok**, yer tutucu da yok, çökme yok | 10a |
| 29 | Geçmiş bir tarih seç | Kart "gecikmiş" diyor, tarih **ilerletilmiyor** | 10a |
| 30 | Bugünün tarihini seç | Kart "Bugün ödenecek" diyor | 10a |
| 31 | Seçicinin metin girişinden 10 yıldan uzak bir tarih gir, kaydet | Alan altında hata, **kaydedilmiyor**, sheet açık kalıyor, çökme yok | 10a |
| 32 | Tarih seçili haldeyken sheet açıkken döndür | Tarih **korunuyor** | 10a |

| 33 | Ayarlarda "Ödeme hatırlatmaları" satırı | Üç halden birini söylüyor: **Açık** / **Kapalı — açmak için dokunun** / **Kapalı — sistem ayarlarından açılmalı** | 10c-1 |
| 34 | (API 33+) Temiz kurulumda satıra dokun | **Doğrudan** sistem izin diyaloğu; araya uygulamanın diyaloğu **girmiyor** | 10c-1 |
| 35 | İzni verip satıra bak | **Açık** | 10c-1 |
| 36 | Bir kez reddettikten sonra satıra dokun | Önce **uygulamanın açıklama diyaloğu**, sonra sistem diyaloğu | 10c-1 |
| 37 | Kalıcı reddedildikten sonra satıra dokun | **Sistem bildirim ayarları** açılıyor, izin diyaloğu çıkmıyor | 10c-1 |
| 38 | Sistem ayarlarından bildirimleri aç, geri dön | Satır **uygulama yeniden başlatılmadan** güncelleniyor | 10c-1 |
| 39 | Sistem ayarlarından **yalnızca kanalı** kapat, geri dön | Satır **kapalı** diyor (uygulama izni hâlâ verili olsa bile) | 10c-1 |
| 40 | (API < 33) Satıra dokun | Sistem bildirim ayarları açılıyor, izin diyaloğu **hiç** çıkmıyor | 10c-1 |

| 41 | Temiz kurulumda **tarihli** ilk aboneliği kaydet | Sheet kapandıktan **sonra** sistem izin diyaloğu çıkıyor; ikisi üst üste binmiyor | 10c-2 |
| 42 | Temiz kurulumda **tarihsiz** abonelik kaydet | Diyalog **çıkmıyor** | 10c-2 |
| 43 | Reddettikten sonra ikinci tarihli aboneliği kaydet | Diyalog **çıkmıyor** (tek sefer kuralı) | 10c-2 |
| 44 | İzin verilmişken tarihli abonelik kaydet | Diyalog **çıkmıyor** | 10c-2 |
| 45 | Diyalog açıkken ekranı döndür | Diyalog **tek** kalıyor, kapatılınca yeniden çıkmıyor | 10c-2 |
| 46 | (API < 33) Tarihli ilk aboneliği kaydet | İzin diyaloğu **hiç** çıkmıyor, uygulama normal | 10c-2 |

| 47 | Temiz kurulumda uygulamayı aç | Listede **boş durum** görünüyor: ikon + "Henüz abonelik yok" + "Eklemek için + düğmesine dokun". Dashboard kartı (0,00) ve FAB yerinde | 8b |
| 48 | Bir abonelik ekle | Boş durum **kayboluyor**, liste geliyor | 8b |
| 49 | Tek aboneliği sil | Boş durum **geri geliyor**, undo Snackbar'ı ile çakışmıyor | 8b |
| 50 | Veri varken uygulamayı aç | Boş durum **hiç görünmüyor** (yükleme sırasında da) | 8b |

| 51 | Ekleme formunda kategori seçici | Dört chip: Eğlence / Üretkenlik / Sağlık / Diğer. **Diğer** seçili gelir (ağaçta `checked="true"`) | 11a |
| 52 | Kategori seçip kaydet | Kartta kategori adı görünüyor; satır tek odak durağı olarak "Ad, tutar, (geri sayım,) kategori" diye okunuyor | 11a |
| 53 | Kategoriye dokunmadan kaydet | Kayıt oluşuyor, kartta kategori satırı **yok** (Diğer yazılmaz) | 11a |
| 54 | Kaydettikten sonra FAB'a tekrar bas | Kategori **Diğer**'e dönmüş | 11a |
| 55 | Kategori seçili haldeyken sheet açıkken döndür | Seçim **korunuyor** | 11a |

| 56 | Liste üstündeki filtre çubuğu | "Tümü" + dört kategori, beş chip; açılışta **Tümü** seçili | 11b |
| 57 | Bir kategori seç | Liste yalnızca o kategoriyi gösteriyor, **dashboard toplamı da** görünen satırların toplamı | 11b |
| 58 | Hiçbir aboneliği olmayan bir kategoriyi seç | "Bu kategoride abonelik yok / Başka bir kategori seç" — 8b'deki ilk boş durumdan **farklı metin** | 11b |
| 59 | Filtre açıkken bir satırı sil, sonra "Geri al" | Satır dönüyor, filtre **korunuyor**, "Tümü"de sıra da eski hâlinde | 11b |
| 60 | Filtre seçiliyken döndür, sonra uygulamayı tamamen kapat ve aç | Döndürmede **korunuyor**, yeniden açılışta **Tümü**'ye dönüyor (filtre kalıcı değil) | 11b |
| 61 | Filtre çubuğunu yatay kaydır | Ekrandan taşan chip'e ulaşılıyor; kaydırma ekranın **en sağ kenarından başlatılmaz** (aşağıdaki API 34 tuzağı) | 11b |

| 62 | Ekleme formunda periyot seçici | Üç chip: Aylık / Yıllık / Haftalık. **Aylık** seçili gelir; 360dp'de **tek satıra sığar** | 12-1 |
| 63 | Aylık 100,00 + yıllık 1.200,00 + haftalık 10,00 kaydet | Kartlarda periyot yazıyor; toplam **243,33** (100,00 + 100,00 + 43,33) | 12-1 |
| 64 | Dashboard'ın altındaki **Yıllık** chip'ine bas | Başlık "Yıllık Toplam", değer **2.920,00** — aylık figürün 12 katı (2.919,96) **değil**; fark tek yuvarlamadan | 12-1 |
| 65 | Bir kategori filtresi seçip iki görünüme de bak | Yıllık toplam yalnızca **görünen** satırları kapsıyor | 12-1 |
| 66 | Periyot seçip kaydettikten sonra FAB'a tekrar bas | Periyot **Aylık**'a dönmüş | 12-1 |
| 67 | Periyot seçili haldeyken sheet açıkken döndür | Seçim **korunuyor** | 12-1 |
| 68 | Yıllık görünümdeyken uygulamayı tamamen kapat ve aç | **Aylık**'a dönüyor (görünüm kalıcı değil) | 12-1 |

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
- İlk kurulumda liste **boş** başlar. Seed veri yok; bu durumda **boş durum ekranı**
  görünür (Faz 8b), dashboard kartı ve FAB yerinde kalır.
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

### Kaydırma testleri ekranın kenarından başlatılmaz — API 34 tuzağı

Jest tabanlı gezinmede ekranın sol ve sağ kenarındaki dar şerit **sistem geri
jestine** ayrılmıştır. Oradan başlayan bir `adb shell input swipe` uygulamanın
kaydırma bileşenine hiç ulaşmaz; uygulama geri gider ve ölçüm **sessizce
yanlış** çıkar — hata verilmez, sadece test ettiğin şey test edilmez.

Faz 10b doğrulamasında tam olarak bu oldu: geniş emülatörde (1080 px)
`input swipe 1040 ...` uygulamadan çıktı ve `topResumedActivity` launcher'a
döndü. Aynı kaydırma `input swipe 950 ...` ile beklendiği gibi çalıştı, satır
silindi.

| Başlangıç x (1080 px ekran) | Kenardan uzaklık | Sonuç |
|---|---|---|
| 1040 | 40 px | **Sistem geri jesti**, uygulamadan çıkış |
| 950 | 130 px | Uygulamaya ulaştı, satır silindi |

**Eşik ikili aramayla daraltılmadı**; yalnızca bu iki nokta ölçüldü. Android'in
varsayılan geri jesti şeridi kenar başına **20dp**, 420 dpi'da ≈ 52 px — yani
1040 şeridin içinde, 950 dışında kalıyor. Kural: kaydırmayı kenardan
**100 px'den fazla** içeriden başlat.

**İki AVD neden farklı davranıyor, ölçüldü:**

```bash
adb shell cmd overlay list android | grep navbar
```

| AVD | Etkin overlay | Sonuç |
|---|---|---|
| `subtrack_narrow_api29` | hiçbiri (üç tuşlu gezinme) | Geri jesti şeridi **yok**; x=690 (kenardan 30 px) sorunsuz çalıştı |
| `subtrack_wide_api34` | `[x] com.android.internal.systemui.navbar.gestural` | Şerit **var**; kenardan başlayan kaydırma uygulamadan çıkarıyor |

Yani bu, API sürümünün değil **gezinme modunun** sonucudur. Bir AVD'nin modu
değişirse davranış da değişir; şüphede kalınca yukarıdaki komutla bakılır.

### Kaydırarak silme: `input swipe` süresi 700 ms olmalı

`adb shell input swipe x1 y cx2 y 400` bazı koşullarda satırı silmiyor: aynı
mesafe, aynı başlangıç noktası, ama jest fiske (fling) sayılıp mesafe şartına
takılıyor. 700 ms'lik aynı kaydırma her seferinde sildi. Faz 11b
doğrulamasında bir kaydırma 400 ms ile çalıştı, emülatör yeniden başlatıldıktan
sonra aynı komut üç kez üst üste hiçbir şey yapmadı.

Kural: **silme** kaydırmalarında 700 ms kullan. 400 ms ve altı yalnızca
"silmemeli" maddelerinde (liste #8, hızlı kısa fiske) anlamlıdır — orada zaten
silmemesi beklenir, yani sessizce yanlış geçmez.

### Tarih seçicinin onay düğmesi de "Save" diyor

`DatePickerDialog`'un confirm düğmesi ile sheet'in Kaydet düğmesi **aynı
metni** taşıyor. Diyalog kapanırken alınan bir dump'ta iki düğüm de "Save"
diye görünür; ilkine dokunmak boşa gider ve test, kaydetmediğini fark etmeden
devam eder. Faz 12-1 doğrulamasında bu oldu: 29. madde "kart yok" dedi, sebep
üründe değil ölçümdeydi.

Kural: diyalogda Kaydet'e bastıktan sonra **"Select date" başlığının
kaybolmasını bekle**, sonra yeniden dump al.

### 360dp'de form artık kaydırma istiyor

Periyot sırası (Faz 12-1) formu bir sıra uzattı. 360dp'lik ekranda **kategori
chip'leri ve tarih alanı açılışta ekranın altında kalıyor**; ikisine de
ulaşmak için form kaydırılmalı. Ürün açısından sorun değil — form zaten
kaydırılabilir ve iki alan da opsiyonel — ama otomatik testte "düğüm yok"
hatası olarak çıkar. Ölçüm yaparken önce kaydır.

### Bildirim izni durumunu adb ile kurma ve okuma

Ayarlar'daki "Ödeme hatırlatmaları" satırı üç hal gösterir ve üçü de elle
kurulabilir. Okumak için:

```bash
adb shell dumpsys package com.elinacn.subtrack | grep POST_NOTIFICATIONS
```

`granted=false` + bayraklarda `USER_FIXED` yoksa "hiç sorulmamış veya bir kez
reddedilmiş"; `USER_FIXED` varsa **kalıcı ret**.

Kurmak için:

```bash
adb shell pm clear com.elinacn.subtrack        # hiç sorulmamış hale döner
adb shell pm grant  com.elinacn.subtrack android.permission.POST_NOTIFICATIONS
adb shell pm revoke com.elinacn.subtrack android.permission.POST_NOTIFICATIONS
```

**`pm revoke` uygulama sürecini öldürür.** Ölçüldü: pid `pm revoke`'tan sonra
kayboluyor. Bu yüzden `ON_RESUME` tazelemesi bu komutla gösterilemez —
gösterilecekse bildirimler **sistem ayarları arayüzünden** kapatılıp geri
dönülür, o yol süreci öldürmüyor (aynı pid kalıyor).

Kanal durumunu okumak için:

```bash
adb shell dumpsys notification --noredact | grep -A3 payment_reminders
```

`mImportance=0` kanalın susturulduğu anlamına gelir. **Kanal ancak ilk bildirim
gönderildikten sonra vardır**; oluşturmak için hatırlatma testini bir kez
koşturmak yeterli.

Sistem izin diyaloğunun düğümleri `com.android.permissioncontroller` paketinde
olur; ilk soruda `permission_allow_button` / `permission_deny_button`, ikinci
soruda `permission_deny_and_dont_ask_again_button` çıkar.

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

Bugün **11 enstrümantasyon testi** var: 8 DAO + 1 şablon + 2 hatırlatma worker'ı.

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
```

`connectedDebugAndroidTest` bitince uygulamayı **kaldırıyor**. Ekran
görüntüsü veya elle test yapılacaksa testten sonra yeniden kurun.

**Bildirim gözlemlenecekse bu görevi kullanmayın** — uygulama kaldırıldığı için
bildirim de ekrandan gider. Onun yerine iki APK'yı kurup testi doğrudan
çalıştırın:

```bash
adb shell pm clear com.elinacn.subtrack
./gradlew :app:installDebug :app:installDebugAndroidTest
adb shell pm list instrumentation                      # tam adı buradan al
adb shell am instrument -w -e class com.elinacn.subtrack.reminder.PaymentReminderWorkerTest   com.elinacn.subtrack.test/androidx.test.runner.AndroidJUnitRunner
adb logcat -d -s ReminderWorkerTest:V
```

API 33+ cihazda önce izin verilmeli, yoksa bildirim hiç gönderilmez:

```bash
adb shell pm grant com.elinacn.subtrack android.permission.POST_NOTIFICATIONS
```

**`pm clear` şart.** Worker günde en fazla bir bildirim gönderir ve
**gönderebildiği** günü kaydeder; aynı gün ikinci koşu tasarım gereği hiçbir
şey yapmaz. Metodların sırası önemsizdir ve sabitlenmemiştir: gün yalnızca
bildirim gerçekten gösterildiğinde işaretlendiği için "bildirimler kapalı"
durumu kaydı kirletmez (ARCHITECTURE §18).

### Periyodik WorkManager işi — gövdesi gecikmesiz bir `OneTimeWorkRequest` ile koşturulur

**Kuyruğa girip girmediği** ve **hangi ayarlarla** girdiği iki yoldan okunur.

Diagnostics yayını iş adını, sınıfını ve durumunu logcat'e yazar:

```bash
adb logcat -c
adb shell am broadcast -a "androidx.work.diagnostics.REQUEST_DIAGNOSTICS" -p com.elinacn.subtrack
adb logcat -d -s WM-DiagnosticsWrkr:V
```

Periyot, ilk gecikme ve constraint'ler bu çıktıda **yok**. Onlar WorkManager'ın
kendi veritabanındadır — `databases/` altında değil, `no_backup/` altında:

```bash
adb exec-out run-as com.elinacn.subtrack cat no_backup/androidx.work.workdb > work.db
adb exec-out run-as com.elinacn.subtrack cat no_backup/androidx.work.workdb-wal > work.db-wal
```

`WorkSpec` tablosunda `interval_duration`, `initial_delay`, `state`,
`period_count` ve `requires_*` sütunları; `WorkName` tablosunda unique ad
karşılığı vardır. **`-wal` dosyası da çekilmeli** — tablolar çoğu zaman ana
dosyaya henüz yazılmamıştır ve yalnız `.db` "file is not a database" der.

JobScheduler tarafı:

```bash
adb shell dumpsys jobscheduler | grep -A25 "JOB #u0a<UID>/0:"
```

`Requires:` satırı constraint'leri, `Run time: earliest=` ilk gecikmeyi gösterir.

**Erken çalıştırmak bu imajlarda mümkün değil.** Denenen ve tükenen yollar:

| Yol | Sonuç |
|---|---|
| `adb shell cmd jobscheduler run -f com.elinacn.subtrack <id>` | Komut çalışır (`Running job [FORCED]`) ama **worker gövdesi koşmaz.** WorkManager kendi denetimini yapar: `WM-WorkerWrapper: Delaying execution for … because it is being executed before schedule. Status … is ENQUEUED; not doing any work and rescheduling for later execution` |
| `adb root` | `adbd cannot run as root in production builds` |
| `adb shell date <MMDDhhmmYYYY>` | `date: cannot set date: Operation not permitted` |
| `adb shell setprop persist.sys.timezone …` | `setprop: failed to set property` |

Yani JobScheduler'ı zorlamak yetmiyor; WorkManager `lastEnqueueTime +
initial_delay` geçmeden çalışmıyor ve o eşiği aşmanın tek yolu duvar saatini
ileri almak — o da root istiyor. `google_apis_playstore` imajlarında root yok.

**Sonuç: ilk gecikmesi olan periyodik bir işin gövdesi, adb'den erken
çalıştırılamaz.** Yukarıdaki iki yol yalnızca işin kuyruğa doğru girdiğini
kanıtlar.

### Çalışan yöntem: gecikmesiz `OneTimeWorkRequest`

WorkManager'ın "zamanından önce çalıştırma" denetimi **yalnızca gecikmeli veya
geri çekilmiş** işler içindir. Bekleyecek hiçbir şeyi olmayan tek seferlik bir
iş anında koşar. Enstrümantasyon testi bunu kullanır:

```kotlin
WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<PaymentReminderWorker>().build())
```

Enstrümantasyon uygulamayla **aynı süreçte** koştuğu için buradaki
`WorkManager`, uygulamanın kendi `Configuration.Provider`'ıyla kurulmuş
örneğidir; worker'ı gerçek `HiltWorkerFactory` üretir. Sahte fabrika,
`work-testing` bağımlılığı veya `TestListenableWorkerBuilder` gerekmez ve
kullanılmaz — koşan sınıf üretimde koşanın aynısıdır.

Periyodik işin **zamanlaması** hâlâ bu yolla doğrulanamaz; o `WorkSpec`
tablosundan ve `dumpsys jobscheduler`'dan okunur.

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
