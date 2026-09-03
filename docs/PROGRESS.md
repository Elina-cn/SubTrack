# PROGRESS.md — İlerleme Günlüğü

Her faz sonunda **en üste** yeni kayıt eklenir. Eski kayıtlar silinmez.

### Kayıt formatı

```markdown
## [Faz N] Başlık — YYYY-AA-GG

**Durum:** Tamamlandı / Kısmen / Ertelendi

**Yapılanlar**
- ...

**Değişen dosyalar**
- `path/to/File.kt` — ne değişti

**Commit'ler**
- `abc1234` feat: ...

**Karşılaşılan sorunlar**
- ...

**Sonraki faz için not**
- ...
```

---

## [Faz 9b-2] Düzenlenebilir Döviz Kurları — 2026-09-02

**Durum:** Tamamlandı. Faz 9 kapandı.

**Yapılanlar — belgeler ve dosya bölme**
- `ARCHITECTURE.md`'ye **§15 Döviz Kurları** eklendi; kod ona göre yazıldı.
- `ROADMAP.md` girişi düzeltildi: tag'i **kullanıcı** atar, CC atmaz.
- `HomeScreen.kt` 301 satırdı, CLAUDE.md §4 sınırının bir satır üstünde. Beş
  `@Preview` fonksiyonu ve `previewSubscription` yardımcısı
  `ui/home/HomeScreenPreviews.kt`'ye taşındı: **301 → 201 + 103**. Taşımadan
  sonra `HomeScreen.kt`'de sekiz import ölü kaldı, onlar da silindi.
  (Prompt sekiz preview diyordu; dosyada beş tane vardı.)

**Üst sınır hesabı — `MAX_RATE`**
`CurrencyConverter.convert` içindeki en geniş ara değer
`grupToplamıKuruş × kaynakKuru`, `Long` içinde. Sınırlar:
- Abonelik başına tavan `MAX_PRICE` = 1.000.000 birim = **10⁸ kuruş**
- `Long.MAX_VALUE` = 9.223.372.036.854.775.807 ≈ **9,22 × 10¹⁸**

Bin abonelik aynı para biriminde: grup toplamı 10¹¹ kuruş. Seçilen
`MAX_RATE = 10.000.000` (ölçekli) = **1.000,0000 TRY / yabancı birim** ile
çarpım 10¹¹ × 10⁷ = **10¹⁸** — tavanın **9,2 katı** altında.

Tersinden bakınca daha okunaklı: `Long.MAX_VALUE / (10⁸ × 10⁷) = **9.223
abonelik**. Tek para biriminde, hepsi fiyat tavanında, kur da tavanda. Yoğun
bir kullanıcının listesi 50-100 kalem; **yaklaşık yüz kat pay** var. Bu sayı
yoruma bırakılmadı, `CurrencyConverterTest`'te aritmetik olarak sabitlendi —
`MAX_RATE` düşünmeden yükseltilirse test kırılır.

Kurun kendisi bölen olarak da kullanılıyor. En kötü hâl kaynak `MAX_RATE`,
hedef `MIN_RATE`: bölme çarpımı hiç küçültmüyor, sonuç 10¹⁸'de kalıyor, hâlâ
güvenli. Bu da ayrı bir testte.

**Sıfırlama neden anahtar siliyor**
`resetRates()` `remove()` çağırıyor, `Default`'u geri yazmıyor. Fark ileride
ortaya çıkar: bir sonraki sürüm daha iyi varsayılan kurlarla gelirse,
sıfırlamış kullanıcı **yeni** değerleri alır; bugünün sayılarını yazmış olsaydı
onlara sonsuza kadar takılı kalırdı. Emülatörde doğrulandı — sıfırlamadan sonra
`settings.preferences_pb` **0 bayta** düştü ve tarih göstergesi "hiç
düzenlenmedi"e geri döndü.

**Doğrulama**
- Ölçek altı kur ayrı bir kontrol değil: pozitiflik + en fazla dört ondalık
  kuralları birlikte zaten ≥ 1 ölçek birimi garantiliyor. Yine de dönüşümden
  sonra `scaled < MIN_RATE` kontrolü **duruyor** — koruduğu şey sıfıra bölme,
  yani üslup değil doğruluk meselesi; koda erişilemez olduğu yazıldı.
- Bir alan hatalıysa **hiçbiri yazılmıyor.** Kısmi kayıt, kullanıcıyı hangi
  kutunun kaydedildiğini bilemez hâlde bırakırdı.

**Testler**
- 58 → **84 birim testi**, hepsi geçti. Enstrümantasyon **9/9, iki emülatörde**.
- İki test yazarken `Save`'in aynı karede yapılan tuş vuruşunu görmediği
  ortaya çıktı: `save()` türetilmiş `uiState`'i okuyordu, o da `combine`
  yeniden yayınlayana kadar geride kalıyor. `screenState` önce okunacak şekilde
  düzeltildi — testin bulduğu gerçek bir zamanlama hatasıydı.

**Değişen dosyalar**
- `domain/model/ExchangeRateTable.kt` — `MIN_RATE`, `MAX_RATE` (Default değerleri değişmedi)
- `domain/repository/SettingsRepository.kt`, `data/repository/SettingsRepositoryImpl.kt`
- `ui/settings/rates/` — `ExchangeRatesUiState.kt`, `ExchangeRatesViewModel.kt`, `ExchangeRatesScreen.kt` (yeni)
- `ui/navigation/Destination.kt`, `SubTrackNavHost.kt` — üçüncü hedef
- `ui/settings/SettingsScreen.kt` — kur ekranına giden satır
- `ui/home/HomeViewModel.kt` — toplam artık saklanan tabloyla
- `ui/home/HomeScreen.kt`, `ui/home/HomeScreenPreviews.kt` — bölme
- `ui/theme/Dimens.kt` — `MinTouchTarget`
- `res/values/strings.xml`, `res/values-en/strings.xml`
- `docs/ARCHITECTURE.md`, `docs/ROADMAP.md`, `docs/TESTING.md`

**Commit'ler**
- `641c5e1` docs: add exchange rate decisions and clarify rounding note
- `6d7932a` refactor: move home previews to a separate file
- `ba07464` feat: store editable exchange rates in DataStore
- `2ef9027` feat: add a screen for editing exchange rates by hand
- `64c5d8a` feat: total with the stored rate table instead of the shipped one
- `7cbb2b8` test: cover rate validation, overflow bounds and reset
- `f41a400` fix: pass the anchor code into the exchange rate description
- `ee0d2db` docs: add exchange rate regression checks

**Karşılaşılan sorunlar**
- Açıklama metni ilk turda ekranda ham `%1$s` olarak çıktı: string biçim
  argümanı `stringResource` çağrısına geçilmemişti. Emülatörde görüldü,
  `f41a400` ile düzeltildi.
- API 34 emülatöründe IME servisini `am force-stop` ile yeniden başlatmak
  "System UI isn't responding" diyaloğunu tetikledi. Bir daha yapılmamalı;
  `show_ime_with_hard_keyboard` ayarını değiştirdikten sonra IME'yi
  kapatmadan beklemek yetiyor.

**Emülatör test sonuçları**

| # | Test | Dar (API 29, 360dp) | Geniş (API 34, 411dp) |
|---|---|---|---|
| 1 | Ayarlar → kur ekranı, beş tur | geçti — beş tur, tek geri tuşuyla launcher | geçti — satır `[42,685][1038,851]` = **63dp** |
| 2 | Kur değişince toplam | geçti — 10,00 USD @ 50,0000 → **500,00 TRY** | geçti — aynı |
| 3 | Kalıcılık | geçti — force-stop sonrası 500,00 | geçti |
| 4 | Sıfırlama | geçti — 42,85/46,2/53,9, dosya **0 bayt** | geçti |
| 5 | Doğrulama (0, -5, 1,23456, 1000,0001) | geçti — dördü de alan hatası, hiçbiri kaydedilmedi, **FATAL yok** | — |
| 6 | Klavye erişilebilirliği | **kısmen** — aşağıda | **kısmen** — aşağıda |
| 7 | Döndürme | geçti — "77.7" korundu | geçti — "88.88" korundu |
| 8 | `pm clear` sonrası | geçti — varsayılan kurlar, çökme yok | geçti |

**Test 2 elle hesap:** 1000 kuruş × 500.000 / 10.000, yuvarlama terimi bölenin
yarısı = 5.000 → (500.000.000 + 5.000) / 10.000 = 50.000 kuruş = **500,00 TRY**.
Cihazın gösterdiği değer.

**Bilinen kusur — klavye açıkken butonlar (test 6)**
Uygulama penceresi `windowSoftInputMode` tanımlamadığı için platform
`adjustPan` gibi davranıyor: pencere **küçülmüyor**, kayıyor. Kaydırma
görünümü de küçülmediği için içerik klavyenin altında kalabiliyor ve
kaydırarak kurtarılamıyor.

| Cihaz | fs | Klavye üstü | Kaydet | Varsayılana dön | Kaydırarak ulaşılıyor mu |
|---|---|---|---|---|---|
| Dar | 1.0 | y=**863** | `[32,1016][688,1112]` altta | `[32,1136][688,1232]` altta | **Hayır** — kaydırma payı yok |
| Dar | 2.0 | y=**1079** (pencere kaydı) | iki fiske sonra `[303,825][417,900]` | `[163,956][557,1031]` | **Evet** |
| Geniş | 1.0 | y=**1625** | `[42,1403][1038,1529]` **görünür** | `[42,1561][1038,1687]` kısmen altta | gerek yok |
| Geniş | 2.0 | y=**1625** | kaydırma sonu `[42,1983][1038,2123]` altta | `[42,2155][1038,2295]` altta | **Hayır** |

Her durumda geri tuşu klavyeyi kapatıyor ve ekrandan çıkmıyor; sonra butonlar
erişilebilir. Yani özellik kullanılabilir ama akış pürüzlü. **Düzeltilmedi:**
çözüm `Modifier.imePadding()` ya da manifest'te `adjustResize`, ikisi de bu
promptun DOKUNMA listesinde ve tek ekranın kararı değil. Ayrı prompt konusu.

**Chip erişilebilirlik ölçümü — 9b-1 bulgusu yanlıştı**
Ekleme sheet'i açıkken `uiautomator dump` ve `uiautomator dump --compressed`
alındı; ayarlar ekranının dump'ı ile karşılaştırıldı. **Üçü de birebir aynı
yapıyı veriyor:**

| Düğüm | sheet (normal) | sheet (--compressed) | ayarlar |
|---|---|---|---|
| Tıklanabilir sarmalayıcı `View` | `checkable=true checked=true` | aynı | aynı |
| `contentDescription` taşıyan çocuk | `checkable=false` | aynı | aynı |

Yani fark ne bileşenden ne dump aracından geliyor — **9b-1'de yanlış düğüme
bakılmıştı**: `contentDescription` taşıyan çocuk okunmuş, seçili durumu bildiren
tıklanabilir ebeveyn atlanmıştı. `CurrencySelector` seçili durumu her iki
ekranda da doğru bildiriyor. Ağaçta doğru olması duyurulduğu anlamına gelmez;
TalkBack doğrulaması Faz 16'da duruyor.

**Sabit regresyon listesi**
21 maddenin tamamı geniş emülatörde koşuldu, hepsi geçti. Toplam 219,89 tam
çıktı; USD'ye geçince $3,73 (= 15999 × 10.000 + 214.250, bölü 428.500).
Türkçe metinler `cmd locale set-app-locales tr-TR` ile ayrıca doğrulandı.

**Sonraki faz için not**
- **`ExchangeRateTable.Default` kurları (42,85 / 46,20 / 53,90) hâlâ
  doğrulanmadı.** Artık kullanıcı düzeltebiliyor ve ekran bunların tahmin
  olduğunu açıkça söylüyor, ama yayından önce elle kontrol maddesi duruyor.
- Klavye/inset kararı bir sonraki prompta bırakıldı; `imePadding` ilk kez
  girecekse bu tek ekranın değil projenin kararı.

---

### Hotfix — IME insets ölçüldü, çözülmedi (2026-09-03)

**Amaç:** 9b-2'de bulunan "klavye açıkken butonlar altta kalıyor" kusurunu
`Modifier.imePadding()` ile kapatmak, ve Faz 10 / Faz 15 öncesinde bir
strateji kurmak. **Ölçüm olumsuz çıktı, uygulama yapılmadı.**

**a. Kur ekranında kaydırma var mı:** var. `ExchangeRatesScreen.kt`'deki kök
`Column` `verticalScroll(rememberScrollState())` taşıyor. Eksik olan kaydırma
değil, kaydırma görünümünün klavye kadar küçülmemesi.

**b/c. `WindowInsets.ime` ölçümü.** Kur ekranına geçici bir probe konuldu:
`Scaffold`'un dışında, composable gövdesinin başında,
`WindowInsets.ime.getBottom(density)` ve `WindowInsets.navigationBars.getBottom(density)`
logcat'e yazıldı.

| Cihaz | Klavye | `ime` | `navigationBars` |
|---|---|---|---|
| API 29, 360dp | kapalı | 0 | 0 |
| API 29, 360dp | **açık** | **0** | 0 |
| API 34, 411dp | kapalı | 0 | 0 |
| API 34, 411dp | **açık** | **0** | 0 |

Klavye açıkken (`dumpsys input_method` → `mInputShown=true`) tuşa basılarak
**yeniden kompozisyon zorlandı** ve probe yeni satır yazdı — okunan değer
bayat değil, gerçekten sıfır.

**d. Sonuç: `imePadding()` iki API'de de işe yaramaz.** Beklenti API 29'un
sorunlu, API 34'ün sağlam olmasıydı; **ikisi de sıfır** verdi, yani sebep API
sürümü değil. Kanıt `navigationBars = 0`: iki emülatörde de gezinme çubuğu
var, insets uygulamaya ulaşsaydı orada sıfırdan başkası görünürdü. Uygulama
`setDecorFitsSystemWindows(false)` / `enableEdgeToEdge` çağırmadığı için
insets'i decor view tüketiyor, Compose'a hiç ulaşmıyor. Sıfır bir insets'e
padding uygulamak hiçbir şey yapmaz.

Promptun kuralı gereği **durdum**: alternatif (`enableEdgeToEdge` veya
manifestte `adjustResize`) ayrı bir karar ve ikisi de bu promptta yasaktı.
GÖREV 2 atlandı, üretim kodu değişmedi.

**Sheet'lerin neden sorunu yok:** `ModalBottomSheet` içeriğini
`Box(Modifier.fillMaxSize().imePadding())` içine koyuyor (material3 1.4.0,
`ModalBottomSheet.kt:186`) ve kendi dialog penceresinde çiziliyor — o pencere
insets alıyor. Bu bir tesadüf, uygulamanın kararı değil.

**Temizlik ve doğrulama**
- Probe `git checkout` ile birebir geri alındı; `git status` temiz, commit'e
  girmedi.
- `assembleDebug --rerun-tasks` geçti, yeni uyarı yok.
- `testDebugUnitTest --rerun-tasks`: **84 test, hepsi geçti.**
- Üretim kodu değişmediği için emülatör regresyon turu koşulmadı — doğrulanacak
  bir davranış değişikliği yok.
- Emülatör ayarları geri alındı (`show_ime_with_hard_keyboard 0`, `font_scale 1.0`).

**Belgeler**
- `ARCHITECTURE.md` **§16 IME (Klavye) Insets** — ölçüm tablosu, sebep,
  sheet'in neden ayrıcalıklı olduğu ve iki seçenekli açık karar.
- `TESTING.md` — metin alanı olan her ekranda klavye açıkken buton
  erişilebilirliğinin kontrol edileceği madde, ekran listesiyle.

**Not:** API 34 emülatöründe bu turda da "System UI isn't responding"
diyaloğu çıktı, bu sefer force-stop yapmadan, açılışta. Diyaloğu "Wait" ile
kapatmak yetiyor; ölçümü etkilemedi.

---

## [Faz 9b-1] Navigation, DataStore ve Ana Para Birimi Tercihi — 2026-08-30

**Durum:** Tamamlandı (9b-2 — kur düzenleme — ayrı prompt)

**Yapılanlar — belgeler ve bağımlılıklar**
- `ARCHITECTURE.md`'ye **§13 Navigation** ve **§14 Kullanıcı Tercihleri** eklendi;
  kod bunlara göre yazıldı, tersi değil.
- **Sürüm tavanı çıkmadı.** `navigation-compose 2.9.5` ve
  `datastore-preferences 1.1.7` ilk denemede geçti; `checkDebugAarMetadata`
  compileSdk/AGP şikayeti **vermedi**, Faz 5a'daki hilt-navigation-compose
  durumu tekrarlanmadı. AGP 9.0.1 ve compileSdk 36.1 olduğu gibi kaldı.

**Yapılanlar — kod**
- `SettingsRepository` (domain) / `SettingsRepositoryImpl` (data). Para birimi
  **ISO kodu** olarak saklanıyor, ordinal değil: ordinal, enum'a bir sabit
  eklendiği gün sessizce anlam değiştirir, dosya ise onu yazan sürümden uzun
  yaşar. Okuma `IOException`'da `emptyPreferences()`'a düşüyor, başka her hata
  yeniden fırlatılıyor.
- `DataStoreModule` `@Singleton`. `RepositoryModule`'e ikinci `@Binds`.
- `ui/navigation/`: `Destination` (düz String rota) + `SubTrackNavHost`.
  `hiltViewModel()` yalnızca `composable` bloklarında; ekranlar durumsuz.
  Ayarlara gidişte `launchSingleTop = true` — çift dokunuş iki kopya yığmasın.
- `MainActivity` yalnızca tema + `SubTrackNavHost`.
- `CurrencySelector` `ui/home/components/` → `ui/common/`, içeriği değişmeden.
- `SettingsViewModel` **iyimser kopya tutmuyor:** dokunuş yazar, ekran store
  yeniden yayınladığı için güncellenir. Yazma başarısız olursa chip'ler yerinde
  kalır — ekranda görünen, gerçekten kayıtlı olandır.
- `HomeViewModel` üç akışı `combine` ediyor; toplam seçili para biriminde.

**Testler**
- 46 → **58 birim testi**, hepsi geçti. Enstrümantasyon **9/9, iki emülatörde de**.
- `SettingsRepositoryImplTest` geçici dosya üzerinde **gerçek DataStore** ile
  çalışıyor, fake ile değil — asıl sınanan şey diskten geri okumak.
- **`multiple DataStores active` hatası testte kasıtlı üretildi.** Aynı dosya
  üzerinde ikinci store açmak `IllegalStateException` fırlatıyor; test ilk
  scope'u iptal ederek geçiyor. Bu, §14'teki "tekillik Hilt'in sorumluluğudur"
  cümlesinin çalışma zamanı kanıtı.

**Değişen dosyalar**
- `domain/repository/SettingsRepository.kt`, `data/repository/SettingsRepositoryImpl.kt` — yeni
- `di/DataStoreModule.kt` — yeni; `di/RepositoryModule.kt` — ikinci binding
- `ui/navigation/Destination.kt`, `ui/navigation/SubTrackNavHost.kt` — yeni
- `ui/settings/SettingsUiState.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt` — yeni
- `ui/common/CurrencySelector.kt` — `ui/home/components/` altından taşındı
- `MainActivity.kt`, `ui/home/HomeScreen.kt`, `HomeViewModel.kt`, `HomeUiState.kt`
- `ui/home/components/AddSubscriptionSheet.kt` — yalnızca import satırı
- `res/values/strings.xml`, `res/values-en/strings.xml`
- `gradle/libs.versions.toml`, `app/build.gradle.kts`
- `docs/ARCHITECTURE.md`, `docs/WORKFLOW.md`, `docs/TESTING.md`

**Commit'ler**
- `2d172e4` docs: add navigation and datastore architecture decisions
- `8de2106` build: add navigation-compose and datastore-preferences
- `b21171f` feat: store the main currency preference in DataStore
- `ae2d16f` feat: add a settings screen behind a navigation host
- `ce4b24b` feat: total subscriptions in the chosen main currency
- `aa8f39f` test: cover the settings repository, view model and currency switch
- `10ef2f4` docs: add navigation and preference regression checks

**Karşılaşılan sorunlar**
- **`b21171f` tek başına derlenmiyor.** `git mv` dosya taşımasını çoktan
  stage'lemişti ve o commit'e girdi; `package` satırının düzeltilmesi bir
  sonraki commit'te kaldı. **Geçmiş düzeltilmedi** — bilinçli karar: taşıma
  içerik taşımıyor, geçmişi yeniden yazmanın riski kazancından büyük.
- Emülatörler oturumlar arasında iki kez kendiliğinden kapandı, yeniden
  başlatıldı. Bu turda ikisi de **İngilizce locale + açık temada** açıldı;
  API 29 bu sefer koyu temada kilitli değildi.

**Emülatör test sonuçları**

| # | Test | Dar (API 29, 360dp) | Geniş (API 34, 411dp) |
|---|---|---|---|
| 1 | Ayarlara gidiş | geçti — ikon 96×96px = **48×48dp** | geçti — 126×126px = **48×48dp** |
| 2 | İki geri yolu + 5 gidiş-gel | geçti — tek geri tuşuyla launcher | geçti |
| 3 | Tercih toplamı değiştiriyor | geçti — TRY 630,91 → **$14,72** | geçti — aynı |
| 4 | Kalıcılık | geçti — `settings.preferences_pb` 24 byte | geçti |
| 5 | Tek instance | temiz — logcat'te `multiple DataStores` yok | temiz |
| 6 | Ayarlarda döndürme | geçti — ekran ve seçim korundu | geçti |
| 7 | Dar + font_scale 2.0 | geçti — chip 80,0/82,0/80,0/83,0dp, kırpılma yok | — |
| 8 | Temiz kurulum (`pm clear`) | geçti — TRY 0.00, çökme yok | geçti |

Sabit regresyon listesi (19 madde) geniş emülatörde bir kez koşuldu, hepsi geçti.
Türkçe metinler `cmd locale set-app-locales tr-TR` ile ayrıca doğrulandı.

Test 8'in kanıtı dikkate değer: `pm clear` sonrası `files/datastore/` **yok**,
ama uygulama TRY ile açılıyor. DataStore dosyayı ilk yazmada oluşturuyor,
okuma varsayılana düşüyor — §14'ün tarif ettiği davranış.

**Bulgu — chip seçim durumu ayarlar ekranında görünüyor**
Hotfix kaydındaki "dört chip de checked=false bildiriyor" maddesinin tersi
ölçüldü: ayarlar ekranında chip'ler `checkable="true"`, seçili olan
`checked="true"` veriyor (dar ve geniş emülatörde, dört ayrı dump). Ekleme
sheet'inin dump'ında ise `checkable="false"` çıkıyor. Yani sorun bileşende
değil, **sheet penceresinin dump'ında** — o pencerenin `uiautomator dump`
çıktısı zaten Faz 9a'dan beri güvenilmez. TalkBack olmadan kesin konuşulamaz;
madde Faz 16'da açık kaldı.

**Bulgu — koyu temada iki tanımsız rol**
`TopAppBar` **palet içinde**: açık temada `#FFFFFF`, koyu temada `#2D3436`,
ikisi de tanımlı `surface`. Ama `outline` ve `onSurfaceVariant` tanımsız
olduğu için Material baseline moru geliyor: chip kenarlığı **#49454F**,
seçilmemiş chip etiketi ve ayarlar açıklama metni **#CAC4D0** (koyu) /
**#49454F** (açık). Kontrast AA geçiyor (9.66:1 ve 6.85:1) — sorun
okunabilirlik değil, palet tutarlılığı. Faz 14'e eklendi, düzeltilmedi.

**IME ölçümü — önceki bulgu yanlıştı**
İlk raporda "sheet API 29'da klavyeyle yukarı kaymıyor" yazılmıştı. **Yanlış.**
Sebep AVD'nin donanım klavyesi (`hw.keyboard`, config `qwerty/v/v`): yazılım
klavyesi hiç çizilmiyordu. `mInputShown=true` görünüyor ama IME penceresinin
`mGivenContentInsets=[0,1232][0,0]` ve dokunma bölgesi boş — ekranda yer
kaplamıyor. `settings put secure show_ime_with_hard_keyboard 1` ile gerçek
telefon durumu üretildi ve yeniden ölçüldü:

| Cihaz | font_scale | Klavye üst kenarı | Save (klavye açıkken) | Erişilebilir mi |
|---|---|---|---|---|
| Dar API 29 | 1.0 | y=**782** | `[329,763][391,782]` — 40px'in 19'u görünür, **kırpık** | Evet — tek fiske, içerik 129px kaydı, Save `[329,634][391,674]`, klavyeden 108px yukarıda |
| Dar API 29 | 2.0 | y=**870** | ağaçta **yok**, viewport dışında | Evet — iki fiske, içerik 328px kaydı, Save `[303,699][417,774]`, klavyeden 96px yukarıda |
| Geniş API 34 | 1.0 | y=**1517** | `[500,1323][580,1376]` — **tam görünür** | Kaydırma gerekmedi |
| Geniş API 34 | 2.0 | y=**1517** | `[465,1293][616,1391]` — **tam görünür** | Kaydırma gerekmedi |

Klavye üst kenarı `ScrollView` düğümünün alt sınırından okundu; pencere
`adjustResize` ile küçüldüğü için ikisi aynı çizgi. Klavye açılınca sheet her
iki cihazda da tam yüksekliğe genişliyor ve `verticalScroll` gerçekten
çalışıyor — Hotfix'te eklenen kaydırma **klavye durumunda da işini görüyor**.
Ama dar ekranda Save **ilk anda kırpık (fs 1.0) veya hiç görünmez (fs 2.0)**
geliyor; kullanıcının kaydırması gerekiyor.

Manifest'te `windowSoftInputMode` **tanımlı değil** (platform varsayılanı;
ölçüm pencerenin pan değil resize ettiğini gösteriyor). Kod tabanında
`imePadding`, `WindowInsets`, `enableEdgeToEdge`, `setDecorFitsSystemWindows`
**hiç geçmiyor**. Düzeltme yapılmadı, ayrı prompt bekliyor.

**Düzeltme — kur hesabı formülü**
Faz 9b-1 raporunda TRY toplamı için yazılan `(1099×428500+214250)/10000`
formülü **yanlıştı**; 47113 verir. Koddaki yuvarlama terimi **bölenin** yarısı,
yani hedef kurun yarısı: `divideHalfUp` içinde `val half = denominator / 2` ve
çağrıda `denominator = rates.rateOf(to)`. TRY'ye çevirirken bölen 10.000,
terim 5.000: `(1099×428500+5000)/10000 = 47092` — cihazın gösterdiği değer.
**Kodda hata yok**, hatalı olan rapordaki elle hesaptı.

**Not — 214250 sayısı bu kayıtta iki kez geçiyor, ikisi de doğru bağlamda.**
Yuvarlama terimi **her zaman bölenin yarısı**, bölen de **hedef** kur:
TRY'ye çevirirken bölen 10.000, terim 5.000; USD'ye çevirirken bölen 428.500,
terim 214.250. Yukarıdaki hata, TRY hedefi için USD'nin terimini kullanmaktı.

**Sonraki faz için not**
- 9b-2: kur düzenleme. `ExchangeRateTable.of()` eksikleri varsayılandan
  dolduruyor, `CurrencyConverter` tabloyu zaten dışarıdan alıyor —
  `SettingsRepository`'ye ikinci bir anahtar grubu eklenecek.
- `ExchangeRateTable.Default` kurları (42,85 / 46,20 / 53,90) **hâlâ
  doğrulanmadı.** 9b-2 bunları düzenlenebilir yapınca tekrar gündeme gelecek.

---

### Hotfix — Kaydet butonu kaydırma alanının dışına alındı (2026-08-30)

**Bulunan:** 9b-1 kapanış ölçümünde, 360dp'de klavye açıkken Kaydet
font_scale 1.0'da 40px'in 19'u görünecek şekilde kırpıktı, font_scale 2.0'da
erişilebilirlik ağacında **hiç yoktu**. Kaydırarak ulaşılıyordu ama
kullanıcıya kaydırması gerektiğini söyleyen hiçbir işaret yoktu. Faz 10, 11 ve
12 bu sheet'e üç alan daha ekleyecek; taşma davranışının onlardan önce
düzeltilmesi gerekiyordu.

**Kaynaktan doğrulananlar** (material3 1.4.0, foundation-layout 1.9.5):
- `ModalBottomSheet.kt:186` — kütüphane içeriği zaten
  `Box(Modifier.fillMaxSize().imePadding())` içine koyuyor. **IME insets'i
  hâlihazırda halledilmiş**, projeye `imePadding` / `WindowInsets` /
  `enableEdgeToEdge` sokmaya gerek yok. Manifest'e de dokunulmadı.
- İçerik `Surface` içindeki bir `Column`'a `ColumnScope` alıcısıyla geçiyor.
  `Surface`'ın yükseklik kısıtı yok, `draggableAnchors` bloğu `sheetSize.height`
  ile `constraints.maxHeight`'ı karşılaştırıyor: içerik **wrap-content**
  ölçülüyor ama üst sınır olarak kullanılabilir yükseklik veriliyor. Dış `Box`
  `fillMaxSize` olduğu için bu sınır **sonlu** — yani `weight` çalışır.

**Seçilen yöntem: `Modifier.weight(1f, fill = false)`.**
`ColumnScope.weight` KDoc'u (`Column.kt:292`) ve `RowColumnMeasurePolicy.kt:198`
birlikte okununca: `mainAxisMin = if (parentData.fill) childMainAxisSize else 0`,
`mainAxisMax = childMainAxisSize`. Yani `fill = true` sheet'i **her zaman tam
ekran** yapardı ve Hotfix serisindeki "sheet içeriğe göre boyutlanıyor"
davranışını bozardı. `fill = false` ile kaydırma bölgesi
`minHeight = 0, maxHeight = kalan alan` kısıtıyla ölçülüyor: form kısaysa
küçülüyor, uzunsa kırpılıp kaydırılıyor. Kaydet weight'siz kardeş olduğu için
`fixedSpace`'e giriyor — yeri **form ölçülmeden önce** ayrılıyor.

Ekstra sarmalayıcıya gerek olmadı: sheet'in `content` lambda'sı zaten
`ColumnScope`, iki kardeş doğrudan oraya kondu. Alan sırası, doğrulama,
hata gösterimi, `CurrencySelector` ve string kaynakları **değişmedi**;
`skipPartiallyExpanded = true` korundu. Kaydet ile seçici arasındaki 24dp
boşluk `Spacer` olmaktan çıkıp butonun üst padding'i oldu — böylece sabit
kalıyor, kaydırılıp gitmiyor.

**Ölçüm** (`show_ime_with_hard_keyboard 1` ile, dört kombinasyonun hepsi):

| Cihaz | fs | Klavye üstü | Kaydet (klavye açık) | Dokunma alanı | Kaydırınca oynuyor mu |
|---|---|---|---|---|---|
| Dar API 29 | 1.0 | y=782 | `[48,606][672,702]` tam görünür | 96px = **48dp** | hayır — üç dump'ta da aynı |
| Dar API 29 | 2.0 | y=870 | `[48,683][672,790]` tam görünür | 107px = **53,5dp** | hayır |
| Geniş API 34 | 1.0 | y=1517 | `[63,1287][1017,1413]` tam görünür | 126px = **48dp** | hayır |
| Geniş API 34 | 2.0 | y=1517 | `[63,1272][1017,1412]` tam görünür | 140px = **53,3dp** | hayır |

Öncesiyle karşılaştırma: dar fs1.0'da Kaydet `[329,763][391,782]`'de kırpıktı,
dar fs2.0'da ağaçta yoktu. Şimdi dördünde de tam görünür ve kaydırmadan
dokunulabilir.

**Klavye kapalıyken sheet yüksekliği değişmedi — dört kombinasyonda da 0dp
fark.** Kaydet metni klavyesiz konumlarda birebir aynı yerde: dar fs1.0
`[329,1132][391,1172]`, dar fs2.0 `[303,1109][417,1184]`, geniş fs1.0
`[500,2143][580,2196]`, geniş fs2.0 `[465,2113][616,2211]`. `fill = false`
seçiminin koruduğu şey tam olarak buydu.

Yatayda (2400x1080) da kontrol edildi: kaydırma bölgesi `[360,190][2040,723]`,
Kaydet `[423,797][1977,902]` — ekranda ve sabit.

**Fonksiyonel:** iki emülatörde de abonelik eklendi, sheet kapandı, satır ve
toplam güncellendi. Dar emülatörde font_scale 2.0'da kaydırmadan kaydedildi —
düzeltmenin asıl kazancı bu.

**Testler:** 58 birim testi geçti, yeni derleme uyarısı yok.
`docs/TESTING.md` sabit regresyon listesi (21 madde) geniş emülatörde bir kez
koşuldu, hepsi geçti.

**Belge:** `docs/TESTING.md`'deki soft klavye bölümü iki yönlü hale getirildi —
ölçümden önce `show_ime_with_hard_keyboard 1`, sonra `0`. Unutulursa klavye
testi hata vermeden yanlış sonuç verir.

**Commit'ler**
- `63026ca` fix: keep the save button out of the sheet's scrolling region

---

## [Faz 9a] Para Birimi Seçimi ve Normalizasyon — 2026-08-30

**Durum:** Tamamlandı (9b — ayarlar ekranı — ayrı prompt)

**Yapılanlar — kur dönüşümü**
- **`Currency`, `ExchangeRateTable`, `CurrencyConverter`** domain'e eklendi.
  Kurlar `Long`, **10.000 ölçekli**, hepsi tek çıpaya (TRY) göre yazılı.
  Böylece **her çift tek adımda** çevriliyor — kaynak kuru bölü hedef kuru,
  ölçek sadeleşiyor — ve USD→EUR gibi bir çapraz dönüşüm iki kez değil
  **bir kez** yuvarlanıyor.
- **Yuvarlama HALF_UP.** Gerekçe: `parsePrice` da HALF_UP kullanıyor; aynı
  tutar girişte bir şey, gösterimde başka bir şey olamaz. HALF_EVEN'in
  koruduğu şey tekrarlı yuvarlamanın yukarı kayması, o da burada olmuyor.
- **`totalIn` önce her para birimi içinde topluyor, sonra çeviriyor.**
  Hata böylece abonelik sayısıyla değil **para birimi sayısıyla** (en fazla
  dört) sınırlı. Kanıt testte sabitlendi: 4 adet 0,01 USD → satır satır
  çevrilirse **1,72 TL**, grup halinde **1,71 TL** (doğrusu 1,714).
- Dönüşüm mantığı saf Kotlin; domain'de tek yabancı import Faz 3'ten kalma
  `kotlinx.coroutines.flow.Flow`.

**Yapılanlar — arayüz**
- **`CurrencySelector`, `FilterChip` sırası.** SegmentedButton kaynağa
  bakılarak elendi: `SegmentedButton.kt:425` her segmentte ikon çizilsin
  çizilmesin **18dp + 8dp** ayırıyor, dört segment ~320dp istiyor ve sheet
  padding'inden sonra 360dp ekrana sığmıyor.
- `MoneyFormatter` — `NumberFormat.getCurrencyInstance(locale)`, para birimi
  açıkça set ediliyor. Kart kendi para biriminde, dashboard normalize edilmiş
  toplamda; karışık listede altına çevrim notu çıkıyor.

**Uyarı — kurlar tahmindir**
`ExchangeRateTable.Default` içindeki 42,85 / 46,20 / 53,90 değerleri
**doğrulanmadı.** Yayından önce elle kontrol edilmeli; kodda tarih ve
"bunlar eskir" notu var.

**Testler**
- 29 → **46 birim testi**, hepsi geçti (`CurrencyConverterTest` 14 yeni).

**Değişen dosyalar**
- `domain/model/Currency.kt`, `domain/model/ExchangeRateTable.kt` — yeni
- `domain/usecase/CurrencyConverter.kt` — yeni
- `domain/model/Subscription.kt` — `currencyCode: String` → `currency: Currency`
- `data/mapper/SubscriptionMapper.kt` — enum dönüşümü, şema değişmedi
- `ui/common/MoneyFormatter.kt` — yeni
- `ui/home/components/CurrencySelector.kt` — yeni
- `ui/home/HomeViewModel.kt`, `HomeUiState.kt`, `HomeScreen.kt`,
  `AddSubscriptionSheet.kt`, `DashboardCard.kt`
- `res/values/strings.xml`, `res/values-en/strings.xml`

**Commit'ler**
- `a709af2` feat: add currency conversion to domain
- `6923adc` feat: add currency selector to add sheet
- `55c837d` feat: show per-subscription currency and normalized total
- `a97d8eb` test: add currency conversion tests

**Elle test sonucu (fiziksel cihaz, OPPO A15s)**
- a, b, c, d, e, g **geçti**.
- h'de chip'ler alt satıra **kaymadı** — beklenen, cihaz 423dp, dar değil.
- f'de bulgu (hata değil): İngilizce dilde toplam **"TRY1,785.45"** diye
  boşluksuz çıkıyordu. `NumberFormat` sembolü locale'e göre seçiyor; kartta
  `$10.99`, toplamda ISO kodu yazması tutarsız görünüyor ama teknik olarak
  doğru. Sembol/kod tutarlılığı kararı **Faz 14'e** madde olarak eklendi;
  okunabilirlik `cac1f63` ile düzeltildi.

**Tag**
- `phase-9a-done`

---

## [Emülatör Kurulumu] Dar Ekran ve Farklı Android Sürümü — 2026-08-30

**Durum:** Tamamlandı (TalkBack kısmı hariç)

**Kurulan AVD'ler**

| AVD | Çözünürlük | Yoğunluk | Efektif genişlik | API |
|---|---|---|---|---|
| `subtrack_narrow_api29` | 720x1280 | 320 dpi | **360dp** | 29 |
| `subtrack_wide_api34` | 1080x2400 (Pixel 6) | 420 dpi | 411dp | 34 |

**En önemli bulgu:** fiziksel cihaz ölçüldü — `init=720x1600 320dpi
base=720x1600 272dpi` → efektif genişlik **423dp**. Yani Faz 9a'da "360dp'ye
sığar mı" diye hesaplanan durum **hiç ekranda görülmemişti**.

**Ölçümler**
- **Chip genişlikleri 360dp'de:** TRY 57,5 · USD 59,0 · EUR 58,0 · GBP 60,0dp.
  Aralar 8,0dp, sol kenar 24,0dp, yükseklik 32,0dp. Dördü **tek satırda**,
  toplam 258,5dp, sağda **77,5dp boş**. Faz 9a'daki "~58dp" tahmini tuttu.
- **font_scale 2.0'da FlowRow sarıyor:** TRY/USD/EUR üst satır (80,0 / 82,0 /
  80,0dp), GBP alt satır (83,0dp). **Kırpılma yok.**
- **Enstrümantasyon testleri 9/9**, iki emülatörde de geçti.
- **Açılış medyanı (5 tekrar):** API 29 **7055 ms**, API 34 **3946 ms**,
  fiziksel cihaz ~8100 ms. Hepsi debug build.
- **Dynamic color paletimizi ezmiyor** — API 34 koyu temada dashboard kartı
  hâlâ PastelBlue. Faz 14 için: sorun Material You değil, tanımsız roller.
- **Uygulamadan tek bir FATAL veya StrictMode ihlali yok.** Logcat'teki
  `FATAL EXCEPTION` `com.google.android.sdksetup`'a ait (Olson timezone),
  StrictMode ihlalleri `android.process.acore` ve `com.google.android.gms`.

**TalkBack — YAPILAMADI**
Her iki `google_apis_playstore` imajında da Android Accessibility Suite
kurulu değil (`pm list packages -u` ve `/system/priv-app` taramasında iz
yok). Kurmanın iki yolu var, ikisi de reddedildi: emülatörde Google hesabına
giriş, ve üçüncü taraf APK indirme. **Faz 16'ya taşındı.**

**Değişen dosyalar**
- `docs/TESTING.md` — "Emülatör Testleri" bölümü eklendi

**Commit'ler**
- `23b2459` docs: document emulator testing setup

**Karşılaşılan sorunlar**
- `cmd uimode night yes` **API 29'da çalışmıyor**; koyu tema testleri API 34'te.
- `uiautomator dump` bottom sheet açıkken çoğunlukla alttaki pencereyi
  döndürüyor. Piksel ölçümü için ham `screencap` tamponu elle çözümlendi
  (makinede görüntü kütüphanesi yok, kurulmadı).
- `connectedDebugAndroidTest` bitince uygulamayı **kaldırıyor**.

---

## [Hotfix] Erişilebilirlik Birleştirme, Dar Ekran Sheet, Para Birimi Boşluğu — 2026-08-30

**Durum:** Tamamlandı

**1. Semantics birleştirme (`de60e47`)**

**Önemli bulgu — önceki teşhis yanlıştı.** `mergeDescendants` aslında
çalışıyordu. Erişilebilirlik ağacı **birleştirilmemiş** ağaçtan kuruluyor:
`SemanticsOwner.kt:157`'deki `getAllUncoveredSemanticsNodesToIntObjectMap`
`unmergedRootSemanticsNode`'dan başlıyor ve `replacedChildren` üzerinden
yürüyor. Yani birleşen bir düğümün çocukları **her zaman** ayrı düğüm olarak
kalıyor; dump birleşmeyi hiç gösteremiyor. `Surface`'ın kendi
`semantics(mergeDescendants = false)` çağrısı da suçlu değil —
`collapsePeer` bayrağı yalnızca OR'luyor, `false` `true`'yu kapatamıyor.

`clearAndSetSemantics`'e geçildi: `getChildren` temizleyen düğüm için boş
liste döndürüyor, alt ağaç gerçekten kayboluyor. Hem **ölçülebilir** hem de
cümlenin sırası bizim kontrolümüzde. Silme özel eylemi korunuyor —
`calculateSemanticsConfiguration` config'i sıfırlayıp sonra o düğümün kendi
bloğunu uyguluyor.

**2. Dar ekranda Kaydet butonu (`f4a6bd9`)**

360x640dp'de sheet yarı açık geliyordu ve Kaydet ekranın altında kalıyordu —
her iki font ölçeğinde. `skipPartiallyExpanded = true` + içeriğe
`verticalScroll`. İkisi birlikte: expand bildirilen durumu çözüyor, scroll
formun ekrandan uzun olduğu hâli garantiliyor.

**3. Para birimi boşluğu (`cac1f63`)**

ISO kodundan sonra **kırılmaz boşluk (U+00A0)**. Boşluk yalnızca sembol
harflerden oluşuyorsa giriyor; sembollü biçimler değişmedi.

**Doğrulama testi sonuçları (her iki emülatörde)**

| # | Test | Dar (API 29, 360dp) | Geniş (API 34, 411dp) |
|---|---|---|---|
| 1 | Kaydet, font_scale 1.0, sürükleme yok | **geçti** — y=1113..1190, ekran altına 89px | — |
| 2 | Kaydet, font_scale 2.0, sürükleme yok | **geçti** — y=1094..1198, ekran altına 81px | — |
| 3 | Sheet içi kaydırma | **geçti** | — |
| 4 | Tutamaçtan kapatma | **geçti** | — |
| 5 | Sheet boyu içeriğe göre, regresyon yok | — | **geçti** — sheet 457dp, Kaydet 178px içeride |
| 6 | Para birimi biçimi (byte seviyesi) | **geçti** | **geçti** |
| 7 | Kaydırarak silme regresyonu | **geçti** | **geçti** |
| 8 | Döndürmede state korunması | **geçti** | **geçti** |
| 9 | TalkBack "Sil" eylemi | **doğrulanamadı** | **doğrulanamadı** |

Ayrıntılar:
- **Kaydırma (3):** font_scale 2.0'da içerik zaten sığdığı için kaydıracak
  taşma yok, koordinatlar sabit kaldı. Taşmayı zorlamak için **font_scale
  3.0**'a çıkıldı; orada "Currency" ekran dışından y=978'e, sonra y=608'e
  geldi ve Kaydet `[267,1064][453,1184]`'te erişilebilir oldu. Yukarı
  kaydırma sheet'i kapatmıyor; içerik en üstteyken aşağı kaydırma kapatıyor
  (ModalBottomSheet'in standart sürükleyip-kapat davranışı).
- **Byte doğrulaması (6):** ISO kodlu tutarlarda `54 52 59 C2 A0 31` —
  yani "TRY" + U+00A0 + rakam. Sembollü tutarlarda sembol doğrudan rakama
  bitişik, boşluk **yok**.
- **Silme (7):** hafif kaydırma (%20) silmedi, **ardışık üç hafif kaydırma
  da silmedi** (Faz 1a'daki birikme düzeltmesi ayakta), tam kaydırma (%86)
  sildi. Toplam 1.785,45 → 630,91 (= 159,99 + 470,92), doğru.
- **Döndürme (8):** yatayda `TestAbonelik` ve `42.50` korundu, EUR chip'i
  piksel düzeyinde hâlâ seçili (PastelBlue dolgu).

**Commit'ler**
- `de60e47` fix: merge semantics on dashboard and subscription rows
- `f4a6bd9` fix: keep the save button reachable on narrow screens
- `cac1f63` fix: add spacing to currency codes

**Bilinen eksikler**
- **Chip seçim durumu erişilebilirlik ağacında görünmüyor.** EUR görsel
  olarak seçiliyken dört chip de `checked="false" selected="false"`
  bildiriyor. Ekran okuyucu kullanıcısına hangi para biriminin etkin olduğu
  söylenmiyor olabilir — TalkBack'siz doğrulanamıyor, Faz 16'ya madde.
- Silme özel eyleminin gerçekten duyurulup çalıştığı **doğrulanamadı**.
- Dar emülatör koyu temada kilitli kaldı (API 29 kısıtı); doğrulama testleri
  orada koyu temada yapıldı. Kaydet butonunun rengi iki temada aynı olduğu
  için ölçümler etkilenmedi.

**Sonraki faz için not**
- Faz 9b: ayarlar ekranı, Navigation, DataStore. `CurrencyConverter` tablosunu
  zaten dışarıdan alıyor; `ExchangeRateTable.of()` eksik kurları varsayılandan
  dolduruyor, yani yalnızca argüman değişecek.

---

## [Faz 8a] Yükleme, Hata ve Erişilebilirlik — 2026-08-29

**Durum:** Tamamlandı (8b — boş durum ekranı — ertelendi)

**Yapılanlar**
- **`isLoading` UI'a bağlandı.** Faz 5a'dan beri hesaplanıyor ama hiç
  okunmuyordu; açılışta "yükleniyor" ile "hiç abonelik yok" ayırt
  edilemiyordu. Yeni `DelayedLoadingIndicator` bileşeni göstergeyi
  **300 ms geciktiriyor**: yerel Room okuması onlarca milisaniye sürüyor ve
  o kadarlık bir spinner ilerleme değil arıza gibi görünür — kırpışma
  kullanıcıya "bir şeyler ters gitti" hissi verir.
- **Gösterge ekran okuyucuya "meşgul" diyor:** `contentDescription` +
  `liveRegion = Polite`. Sessiz bir spinner, görmeyen kullanıcıya hiçbir
  şey anlatmıyor.
- **Dashboard toplamı tek odak durağı oldu** (`semantics(mergeDescendants = true)`).
  Önce etiket ve tutar ayrı düğümdü; tutarın üstüne düşen okuyucu
  "219.89 TL" deyip neyin toplamı olduğunu söylemiyordu.
- **Dokunma alanı taraması:** `SubscriptionCard` 56dp, FAB 56dp, Kaydet
  butonu görsel olarak 40dp — ama Material3 `Surface.kt` içinde
  `minimumInteractiveComponentSize()` uyguluyor, dokunma alanı 48dp.
  Kaynaktan doğrulandı, **değişiklik gerekmedi**.
- **Kontrast ölçümü** (Faz 1c'den sonra eklenen her metin/zemin çifti):
  Snackbar metni 11.65:1, geri al eylemi 7.73:1, alan altı hata metni
  6.54:1, spinner 3.96:1 (spinner metin değil, eşiği 3:1). Açık ve koyu
  temada hepsi AA geçiyor. **Palete dokunulmadı.**
- **`UiText.Resource` argüman alıyor.** Argümanlar `vararg` değil `List`
  olarak tutuluyor: vararg dizi olurdu ve data class eşitliği referansa
  düşerdi — testler eşitliğe dayanıyor.
- **Fiyat üst sınırı mesajı sınırı söylüyor:** 1000000 metne gömülmek
  yerine argüman olarak geçiyor, tavan değişince mesaj ve kod ayrışamıyor.
- Yükleme ve hata durumları için `@Preview`'lar eklendi.

**Değişen dosyalar**
- `ui/common/DelayedLoadingIndicator.kt` — yeni, 300 ms gecikmeli gösterge
- `ui/common/UiText.kt` — `Resource` artık `args: List<Any>` taşıyor
- `ui/home/HomeScreen.kt` — gösterge bağlandı, yükleme/hata preview'ları
- `ui/home/components/DashboardCard.kt` — `mergeDescendants`
- `ui/home/components/AddSubscriptionSheet.kt` — hata durumu preview'ları
- `ui/home/HomeViewModel.kt` — sınır mesajına `MAX_PRICE` argümanı
- `res/values/strings.xml`, `res/values-en/strings.xml` — `loading`,
  argümanlı `error_price_too_large`
- `test/.../ui/home/HomeViewModelTest.kt` — argümanlı mesaj testi (28 → 29)
- `docs/ARCHITECTURE.md`, `docs/ROADMAP.md` — palet borcu kaydı

**Commit'ler**
- `6c73bbe` feat: show loading indicator while subscriptions load
- `88a8aa1` feat: improve accessibility of dashboard and controls
- `df71ec1` feat: add previews for loading and error states
- `06338ac` feat: support arguments in UiText and show the price limit
- `ee64802` docs: record palette debt for unmapped colour roles

**Tag**
- `phase-8a-done`

**Karşılaşılan sorunlar**
- **Yeniden dene (retry) eylemi eklenmedi — bilinçli karar.**
  `error_save_failed` gerçek bir veritabanı reddi demek; aynı veriyle tekrar
  denemek aynı sonucu verir. "Yeniden dene" düğmesi kullanıcıya olmayan bir
  çıkış yolu vaat eder.
- **Palet borcu:** `inversePrimary` ve `inverseSurface` tanımlı değil,
  Material'ın varsayılan moru devreye giriyor. Kontrast sorunlu değil ama
  kimlik yanlış. Tek tek yamamak yerine **Faz 14'te** (tema gözden geçirme)
  bütün olarak ele alınacak.

**Elle test sonucu**
- Yükleme göstergesi hızlı açılışta hiç görünmüyor (beklenen — 300 ms
  eşiğinin altında kalıyor), yapay gecikmede düzgün çıkıyor.
- Fiyat sınırı mesajı sınır değerini gösteriyor.
- **TalkBack testi yapılamadı:** cihazda TalkBack çok donuyor (OPPO A15s,
  düşük donanım + debug build). Erişilebilirlik değişiklikleri kod düzeyinde
  doğru ama **cihazda doğrulanmadı**. Faz 16 öncesi emülatörde bir kez
  düzgün test edilmeli — ROADMAP Faz 16'ya madde olarak eklendi.

**Sonraki faz için not**
- **Faz 8b (boş durum ekranı) ertelendi.** Tasarım kararı bekliyor: ekranda
  ne yazacağı, hangi görselin kullanılacağı ve kullanıcıyı nereye
  yönlendireceği kararlaştırılmadan kod yazmak boşa iş.
- Faz 14'te palet bütün olarak elden geçirilecek (`inversePrimary`,
  `inverseSurface`).

---

## [Faz 7] Test Altyapısı — 2026-08-29

**Durum:** Tamamlandı

**Yapılanlar**
- **Test bağımlılıkları:** JUnit, **Turbine 1.2.1**, `kotlinx-coroutines-test`.
  Sonuncusu uygulamanın gerçekten çözümlediği **coroutines 1.9.0** ile aynı
  sürüme sabitlendi; farklı sürüm dispatcher hatalarını test hatası gibi
  gösterirdi.
- **Birim testleri (28 test, hepsi geçti):**

  | Sınıf | Test |
  |---|---:|
  | `SubscriptionMapperTest` | 8 |
  | `HomeViewModelTest` | 19 |
  | `ExampleUnitTest` (şablon) | 1 |

- **Enstrümantasyon testleri (9 test, hepsi geçti):** `SubscriptionDaoTest` (8)
  + şablon. **Cihazda çalıştırıldı** (OPPO CPH2179, Android 10), in-memory Room
  üzerinde. Son test Faz 1'deki id çakışması hatasını Room tarafından kapatıyor:
  `AUTOINCREMENT` silinen id'yi asla geri vermiyor.
- **`FakeSubscriptionRepository` elle yazıldı**, mock kütüphanesi kullanılmadı
  (ARCHITECTURE §11). Gerçekten satır saklıyor, böylece testler "hangi metot
  çağrıldı"ya değil **ortaya çıkan listeye** bakıyor.
- **Üretim kodunda tek satır değişmedi.**

### Bulgu — `parsePrice` private

Fiyat doğrulaması saf fonksiyon olarak test edilemedi: `parsePrice` `private`
ve üretim kodunu değiştirmek yasaktı. Testler **public yüzeyden** yazıldı —
`onEvent(Save(...))` gönderilip `uiState.priceError` ve
`repository.inserted`'ın boş kaldığı kontrol ediliyor.

**Bu tercih edilen yol.** İmplementasyonu değil davranışı doğruluyor;
`parsePrice` yeniden adlandırılsa veya başka sınıfa taşınsa testler geçerli
kalır. **`internal` yapılmayacak.**

Tek etkisi sıralama: fiyat testleri fake gerektirdiği için önce mapper, sonra
fake, sonra ViewModel yazıldı.

### Testlerin gerçekten çalıştığının kanıtı

İki beklenti kasten bozuldu:
1. `Money(21989)` → `Money(21988)` (toplam aritmetiği)
2. `assertPriceRejected` içinde `isEmpty()` → `isNotEmpty()`

Sonuç: **`28 tests completed, 7 failed`** — biri aritmetik, **altısı** paylaşılan
yardımcıyı kullanan tüm fiyat reddi testlerinden. Geri alındı, tekrar yeşil.

### İki teknik not

- **`WhileSubscribed` testte tuzak.** `uiState` soğuk; kimse toplamadığı sürece
  `.value` başlangıç değerini döndürür. Testlerde
  `backgroundScope.launch { uiState.collect() }` ile bir toplayıcı açılıyor.
  Açılmasaydı her assert `isLoading = true` görürdü ve testler **hiçbir şey
  doğrulamadan yeşil geçerdi** — sessizce işe yaramaz bir test paketi.
- **`Dispatchers.setMain` zorunlu:** `viewModelScope` `Dispatchers.Main`
  kullanıyor, JVM testinde öyle bir şey yok.

### Faz 3'ün dördüncü hantal noktası ödenmedi

Faz 3'te *"testte sahte repository koymak üretim kodunu değiştirmeyi
gerektirir, Hilt'te `@TestInstallIn` ile modül değiştirilir"* demiştik.
**Gerçekleşmedi.** `HomeViewModel` repository'yi constructor'dan aldığı için
fake'i elle geçirmek yetti; Hilt'e hiç dokunulmadı.

`@TestInstallIn` gerçekten **Hilt'in kendi kurduğu grafı** test ederken
gerekecek: Compose UI testleri (`@HiltAndroidTest`) veya uçtan uca testler.
Faz 8'de boş durum ve yükleme ekranları için UI testi yazılırsa orada çıkar.

**Değişen dosyalar**
- `gradle/libs.versions.toml`, `app/build.gradle.kts` — test bağımlılıkları
- `app/src/test/java/.../data/mapper/SubscriptionMapperTest.kt` (yeni)
- `app/src/test/java/.../fake/FakeSubscriptionRepository.kt` (yeni)
- `app/src/test/java/.../ui/home/HomeViewModelTest.kt` (yeni)
- `app/src/androidTest/java/.../data/local/SubscriptionDaoTest.kt` (yeni)
- `.gitignore` — `/.idea/markdown.xml`

**Commit'ler**
- `a114982` build: add test dependencies
- `e1e0c22` test: add mapper tests
- `a322ae5` test: add home view model tests with fake repository
- `b087356` test: add dao instrumentation tests on an in-memory database
- `82dfaf7` chore: ignore markdown.xml

**Tag**
- `phase-7-done`

**Elle test sonucu**
- Üretim kodu değişmediği için faza özel adım yok; `docs/TESTING.md`'deki sabit
  liste yeterli.
- Uygulamanın hâlâ derlenip cihazda açıldığı doğrulandı.

**Bilinen borç**
- Şablon testler (`ExampleUnitTest`, `ExampleInstrumentedTest`) dolgu — sırasıyla
  `2+2=4` ve paket adı kontrolü. Kapsam dışı bırakıldı, Faz 16 temizliğinde
  kaldırılacak.

**Sonraki faz için not**
- Faz 8: boş durum, yükleme göstergesi, hata gösterimi, erişilebilirlik.
  `HomeUiState.isLoading` hâlâ hesaplanıyor ama UI'da okunmuyor — o fazın ilk
  maddesi.

---

## [Faz 6] Girdi Doğrulama, Hata Gösterimi ve Undo — 2026-08-29

**Durum:** Tamamlandı

**Yapılanlar**
- **`UiText` sarmalayıcısı `ui/common/` altına konuldu, `domain/`'e değil.**
  Çözümlemesi `stringResource` gerektiriyor; domain'e koymak
  `androidx.annotation.StringRes` import'u demekti ve **domain Faz 2'den beri
  sıfır import'la derleniyor.**
- **Girdi doğrulama:** boş ad; boş, geçersiz, negatif veya sıfır fiyat; üst
  sınır; ondalık basamak sayısı. Hatalar **alan bazlı** (`nameError`,
  `priceError`) — tepede "bir şeyler yanlış" diyen tek mesaj değil.
- **Sheet durumu ViewModel'a taşındı** (`HomeUiState.isAddSheetOpen` +
  `OpenAddSheet` / `DismissAddSheet`). Gerekçe: *"hatalı girdide sheet
  kapanmasın"* şartı, kapanma kararının **doğrulama sonucuna bağlı** olması
  demek; composable bunu bilemez. Yan kazanç: ViewModel yapılandırma
  değişikliğinden zaten sağ çıktığı için döndürmede sheet açık kalıyor.
- **Undo:** Snackbar ile geri alma. Silmeden önce `getById` ile okunuyor, geri
  alırken **aynı id** ile ekleniyor — öğe listenin sonuna atlamak yerine eski
  sırasına dönüyor.
- **`CancellationException` yeniden fırlatılıyor.** Yakalansaydı iptal edilen
  bir coroutine "veritabanı hatası" olarak raporlanırdı.
- **Faz 1a'daki uyarı kontrol edildi.** *"Undo sonrası kaydırma state'i
  `EndToStart`'ta takılabilir"* deniyordu; `b678e2d`'de `rememberSaveable` →
  düz `remember` değişikliğiyle sızıntı zaten kapanmış. Gereksiz bir `reset()`
  **eklenmedi**, gerekmediği doğrulandı.

### Mimari karar — `Result<T>` kullanılmıyor

Hatalarımızın çoğu veritabanı hatası değil, **girdi hatası**; ViewModel'da
Room'a hiç ulaşmadan yakalanıyor. `Result<T>` bunlara dokunmaz, yalnızca nadir
DB hataları için her çağrıya sarmalayıcı ekler. Doğrulama ViewModel'da, DB
hataları `try/catch` ile yakalanıp `UiText`'e çevriliyor. Sessiz `try/catch`
yasağı korunuyor — yakalanan her hata kullanıcıya ulaşıyor. (ARCHITECTURE §9
bu karara göre güncellendi.)

### Hotfix 1 — Snackbar kaybolmuyordu (`c72a2cc`)

material3'te `showSnackbar`'ın varsayılan süresi **`actionLabel` verilip
verilmediğine göre değişiyor**: etiket yoksa `Short`, varsa `Indefinite`.
"Geri al" butonu eklemek Snackbar'ı farkında olmadan süresiz yapmış.

İki mekanizma birlikte çalışıyordu: `Indefinite` bitmemesini, ViewModel'da
hayatta kalan `pendingUndo` state'i ise döndürmede geri gelmesini sağlıyordu.

Her iki `showSnackbar` çağrısına `duration` **açıkça** verildi.

**Döndürme kararı:** Snackbar kalsın ve sayaç sıfırlansın. Undo penceresi
kullanıcıya verilmiş bir fırsat; telefonu çevirmek ondan vazgeçme kararı değil.
Satır zaten veritabanından silinmiş durumda, `pendingUndo` yalnızca geri koymak
için gerekeni tutuyor.

**Bilinen sınır:** aynı anda **tek** undo izleniyor. Ardışık silmede yalnızca
son işlem geri alınabilir.

### Hotfix 2 — Fiyat üst sınırı (`aac3bd3`)

`999999999` kabul ediliyordu. Eski eşik `Long` taşmasına göre yazılmıştı; taşma
~92 katrilyon kuruşta olduğu için **pratikte hiç tetiklenmiyordu.**
`MAX_PRICE = 1000000` — bir ürün sınırı, en pahalı gerçek aboneliğin binlerce
katı ama kayan bir tuş vuruşunu yakalayacak kadar düşük.

Ayrıca **ikiden fazla ondalık artık sessizce yuvarlanmıyor**, reddediliyor.
`159,999` eskiden sessizce `160,00` oluyordu; para değerini kullanıcıya sormadan
değiştirmek bu kod tabanının `BigDecimal` duruşuyla çelişiyordu. Sondaki sıfırlar
sayılmıyor: `159,990` kabul, `159,999` red.

### Açılış süresi incelemesi — düzeltme yapılmadı

Faz 6 sonrası uygulamanın geç açıldığı bildirildi. **Ölçüldü**
(OPPO CPH2179, Android 10, `adb shell am start -W -S`, 5 tekrar):

| Build | Medyan cold start |
|---|---|
| Debug | **~8100 ms** |
| Release | **~856 ms** |

**Fark 9,5 kat.** Logcat kesin yeri gösterdi:

> `E ANR_LOG : Blocked msg = { what=110 obj=AppBindData{com.elinacn.subtrack} }, cost = 5709 ms`

`what=110` = `BIND_APPLICATION` — APK açma, sınıf yükleyici, dex doğrulama.
8 saniyenin **5,7'si bizim kodumuz çalışmadan önce** geçiyor. Debug build'in
`debuggable` bayrağı ART'ın optimizasyonunu engelliyor (`Late-enabling
-Xcheck:jni`).

**Şüphelerin hepsi çürütüldü:** sheet kapalıyken kompozisyona girmiyor,
`LaunchedEffect`'ler ilk satırda `null` kontrolüyle çıkıyor, Room ilk açılışta
yük getirmiyor (veri temizken 9236 ms, doluyken 8027 ms — fark gürültü içinde),
Hilt ve `Theme.kt` `BIND_APPLICATION`'dan sonra geliyor.

Faz 6 yavaşlamayı **tetikledi ama sebebi değil**: kod arttıkça debug dex
doğrulaması doğrusal büyüyor. Aynı kod release'de 856 ms'de açılıyor.

**Değişen dosyalar**
- `app/src/main/java/com/elinacn/subtrack/ui/common/UiText.kt` (yeni)
- `app/src/main/java/com/elinacn/subtrack/ui/home/HomeUiState.kt` — hata alanları, yeni event'ler
- `app/src/main/java/com/elinacn/subtrack/ui/home/HomeViewModel.kt` — doğrulama, undo, `try/catch`
- `app/src/main/java/com/elinacn/subtrack/ui/home/HomeScreen.kt` — Snackbar altyapısı
- `app/src/main/java/com/elinacn/subtrack/ui/home/components/AddSubscriptionSheet.kt` — `isError` / `supportingText`
- `app/src/main/res/values/strings.xml`, `values-en/strings.xml` — doğrulama ve undo metinleri
- `docs/ARCHITECTURE.md` §9

**Commit'ler**
- `368555f` feat: add UiText wrapper for view model messages
- `3f3dd34` feat: add input validation with inline field errors
- `224de58` feat: add undo for subscription deletion
- `7981262` docs: record error handling decision in architecture
- `c72a2cc` fix: give the undo snackbar a duration so it dismisses itself
- `aac3bd3` fix: cap the price at a product limit and reject extra decimals

**Tag**
- `phase-6-done`

**Elle test sonucu**
- Doğrulama, undo, hata gösterimi, İngilizce çeviriler ve fiyat sınırları —
  hepsi geçti.
- `docs/TESTING.md`'deki 16 maddelik sabit regresyon listesi geçti.
- **Not:** test cihazının varsayılanı **koyu tema**, testler ağırlıklı orada
  yapılıyor. Açık tema Faz 8'de ayrıca gözden geçirilmeli.

**Sonraki faz için not**
- Faz 7: test altyapısı. `HomeViewModel`'ın doğrulama mantığı artık saf ve
  bağımlılıksız test edilebilir durumda — `parsePrice` kuralları ilk yazılacak
  testler.
- Faz 16'ya iki performans maddesi eklendi (`material-icons-extended`, R8).

---

## [Faz 5b] MainActivity Parçalama ve Durumsuzlaştırma — 2026-08-21

**Durum:** Tamamlandı

**Yapılanlar**
- **`MainActivity` parçalandı: 380 satır → 31 satır.** Yalnızca `setContent`
  kaldı; ViewModel'ı çözüyor, state'i topluyor ve durumsuz ekrana veriyor.

  | Dosya | Satır |
  |---|---:|
  | `MainActivity.kt` | 31 |
  | `ui/home/HomeScreen.kt` | 159 |
  | `ui/home/HomeUiState.kt` | 26 |
  | `ui/home/HomeViewModel.kt` | 107 |
  | `ui/home/components/AddSubscriptionSheet.kt` | 113 |
  | `ui/home/components/SwipeToDeleteRow.kt` | 137 |
  | `ui/home/components/SubscriptionCard.kt` | 96 |
  | `ui/home/components/DashboardCard.kt` | 67 |

  Hiçbiri 300 satırı geçmiyor (CLAUDE.md §4).
- **`HomeScreen` durumsuz hale getirildi:** `HomeScreen(uiState, onEvent)`.
  `hiltViewModel()` çağrısı `MainActivity`'de kaldı, dolayısıyla altındaki
  hiçbir composable render olmak için Hilt grafına ihtiyaç duymuyor.
- **`showBottomSheet` `rememberSaveable`'a çevrildi** (skill denetimi #9).
  Form alanlarındaki metin de öyle. Artık sheet açıkken ekran döndürülünce
  sheet açık kalıyor ve yazılan metin korunuyor.
- **Dokunma alanı ölçüldü** (skill denetimi #6): `SubscriptionCard` satırı
  **56dp** — `CardPadding` 16dp × 2 + içerik 24dp (ikon ve satır yüksekliğinin
  büyüğü). Android eşiği 48dp, **8dp payla geçiyor.** `Dimens`'e dokunulmadı;
  ölçüm bileşenin KDoc'una yazıldı ki ileride padding değiştiren biri neyi
  bozduğunu görsün.
- **Altı preview eklendi**, `SubTrackPreview` kaldırıldı (skill denetimi #10):
  `HomeScreen` boş/dolu, `SubscriptionCard` bilinen/bilinmeyen servis,
  `DashboardCard` dolu/sıfır.
- `getIconForSubscription` → `iconFor`, **`@Composable` işareti kaldırıldı.**
  Fonksiyon composition'dan hiçbir şey okumuyordu; Faz 0 analizinde
  işaretlenmişti. Davranış aynı, `when` bloğu birebir.
- **Taşımada başka hiçbir mantık değişmedi.** Jest/animasyon kodu, renk, boyut
  ve metin kaynakları harfi harfine taşındı.

### Preview sorunu ve çözümü

Altı preview'ın hiçbiri render olmuyordu:

> `NoClassDefFoundError: Could not initialize class ui.theme.ThemeKt`

**Kodda hata yoktu.** `ThemeKt`'nin başlatma zinciri okundu: `Context` erişimi,
kaynak okuma veya `dynamicColorScheme` çağrısı yok; yapı Compose'un kendi
şablonuyla aynıydı.

**Mekanizma:** `Theme.kt`'deki iki top-level `val` (`DarkColorScheme`,
`LightColorScheme`) sınıf yüklenirken hemen çalışıyordu. JVM'de bir static
initializer bir kez patlarsa sınıf **kalıcı olarak** "hatalı" işaretlenir ve
sonraki her erişim aynı opak mesajı verir — asıl hata bir daha görünmez. Altı
preview'ın da aynı mesajla ölmesi ve gerçek sebebin görünmemesi buydu.

**Çözüm (`392499e`):** şema kurulumu `by lazy` ile class-init'ten çıkarıldı.
Sınıf yüklenirken artık patlayacak bir şey yok. Palet, roller ve koyu tema
davranışı birebir aynı; şemalar yine bir kez üretilip önbelleğe alınıyor.

**Ardından ikinci bir sorun çıktı:** Studio 56 sahte syntax hatası gösterdi ve
"No preview found" dedi. Gradle derlemesi tertemizdi — sorun Studio'nun
indeksiydi. **Build → Clean Project + Invalidate Caches / Restart** çözdü.

**Değişen dosyalar**
- `app/src/main/java/com/elinacn/subtrack/MainActivity.kt` — 380 → 31 satır
- `app/src/main/java/com/elinacn/subtrack/ui/home/HomeScreen.kt` (yeni)
- `app/src/main/java/com/elinacn/subtrack/ui/home/components/` — dört dosya (yeni)
- `app/src/main/java/com/elinacn/subtrack/ui/theme/Theme.kt` — `by lazy`

**Commit'ler**
- `2b7d175` refactor: extract components from MainActivity
- `1a0037f` fix: preserve bottom sheet state across rotation
- `bd71368` feat: add previews for home components
- `392499e` fix: build colour schemes lazily so previews can load the theme

**Tag**
- `phase-5-done`

**Elle test sonucu**
- Görsel regresyon yok — taşıma fazıydı, ekran birebir aynı.
- Sheet açıkken ekran döndürülünce **açık kalıyor**, yazılan metin duruyor.
- Ekleme, silme, toplam çalışıyor; kaydırma regresyonu yok; kapat–aç sağlam.
- **Altı preview render oluyor.**
- **Yapılamayan:** TalkBack testi. ColorOS'ta yerel eylemler menüsü açılmıyor,
  cihaz kısıtı. Kod ve kaynak tarafı doğru (`mergeDescendants` + custom action
  birebir taşındı), ama **cihazda doğrulanmadı** — Faz 5a'daki aynı kısıt.

**Sonraki faz için not**
- Faz 6: girdi doğrulama, hata mesajı ve undo. `HomeUiState`'e `errorMessage`
  alanı eklenecek; repository hata yönetimi kararı da bu fazda verilecek
  (Faz 3'ten ertelenmişti).

---

## [Faz 5a] ViewModel, UiState ve Room Bağlantısı — 2026-08-21

**Durum:** Tamamlandı

**Yapılanlar**
- **`HomeUiState` ve `HomeEvent` oluşturuldu.** Ekran tek bir state nesnesi
  çiziyor, tek bir `onEvent` kanalıyla geri konuşuyor (ARCHITECTURE §5).
  `errorMessage` alanı bilerek eklenmedi — onu üretebilecek doğrulama Faz 6'da
  geliyor.
- **`HomeViewModel` (`@HiltViewModel`).** `repository.observeAll()` Flow'u
  `stateIn` ile `StateFlow<HomeUiState>`'e çevriliyor (`viewModelScope`,
  `SharingStarted.WhileSubscribed(5_000)`). Aylık toplam burada hesaplanıyor,
  composable'da değil. Dışarıya yalnızca `uiState` ve `onEvent` açık; mutable
  hiçbir şey sızmıyor.
- **`MainActivity` ViewModel'a bağlandı**, `collectAsStateWithLifecycle` ile.
  Kaldırılanlar: `mutableStateListOf`, monoton id sayacı, toplam
  `derivedStateOf`'u, yerel `Subscription` data class'ı, `SubscriptionListSaver`
  ve koda gömülü Netflix/Spotify verisi. **`MainActivity.kt`: 29 satır eklendi,
  73 silindi.**
- **Silme mecburen bu faza geldi.** `mutableStateListOf` kalkınca
  `subscriptionList.remove(sub)` de kalkmak zorundaydı; `onDelete` artık
  `HomeEvent.Delete(id)` gönderiyor. ROADMAP Faz 6'daki "kaydırarak silme
  repository'yi tetiklesin" maddesi burada karşılandı.
- **Para hassasiyeti: `Double` hiç kullanılmadı.** Parse `BigDecimal` üzerinden
  (`"159,99"` → virgül noktaya → `movePointRight(2)` → `15999L`), gösterimde ters
  yön (`movePointLeft(2)`). Tam sayı aritmetiği, yuvarlama hatası yok.
- `SwipeToDeleteRow`'un jest/animasyon koduna dokunulmadı; yalnızca `onDelete`'in
  ne çağırdığı değişti.

**İki sorun, ikisi de çözüldü**
- **`hilt-navigation-compose:1.4.0` derlemeyi durdurdu.**
  `checkDebugAarMetadata` sekiz sorun buldu: sürüm **compileSdk 37 ve AGP
  9.1.0** istiyor, proje 36.1 / 9.0.1'de. **1.3.0**'a düşürüldü — AGP
  yükseltmek ayrı bir karar, veri akışı fazına sıkıştırılacak iş değil.
- **`hiltViewModel()` deprecated çıktı.** Import
  `androidx.hilt.navigation.compose`'dan
  `androidx.hilt.lifecycle.viewmodel.compose`'a taşındı, uyarı gitti.

**Bilinen eksik**
- **`SubTrackPreview` çalışmıyor.** `MainScreen()` varsayılan olarak
  `hiltViewModel()` çağırıyor, Compose preview'da Hilt grafı yok. Derlemeyi
  etkilemiyor. **5b**'de `HomeScreen(uiState, onEvent)` durumsuz hâle gelince
  kendiliğinden düzelecek; yarım düzeltme yapılmadı.

**Değişen dosyalar**
- `gradle/libs.versions.toml`, `app/build.gradle.kts` — `hilt-navigation-compose` 1.3.0
- `app/src/main/java/com/elinacn/subtrack/ui/home/HomeUiState.kt` (yeni)
- `app/src/main/java/com/elinacn/subtrack/ui/home/HomeViewModel.kt` (yeni)
- `app/src/main/java/com/elinacn/subtrack/MainActivity.kt` — ViewModel bağlantısı

**Commit'ler**
- `019765e` build: add hilt-navigation-compose
- `18c5570` feat: add home ui state and events
- `7db9968` feat: add home view model with room-backed flow
- `7e1b215` refactor: connect ui to view model and remove in-memory state

**Tag**
- `phase-5a-done`

**Elle test sonucu**
- Hepsi geçti. İlk açılışta liste boş — **beklenen**, seed data eklenmedi.
- Ekleme çalışıyor; **kapat–aç sonrası liste duruyor.** Bu, Room → DAO →
  mapper → repository → ViewModel → UI zincirinin **ilk kez uçtan uca
  doğrulanması** demek; `subtrack.db` de bu adımda yaratıldı.
- Silme kalıcı, ekran döndürmede liste titremiyor
  (`WhileSubscribed(5_000)` bunun için), kuruş hassasiyeti doğru
  (159,99 + 59,90 = 219,89), kaydırma regresyonu yok.

**Sonraki faz için not**
- 5b: `MainActivity` parçalanacak. Durumsuzlaştırma, `SubTrackPreview`'i ve
  döndürmede kapanan sheet sorununu birlikte çözecek.
- Faz 6'da kalan iş daraldı: girdi doğrulama, hata mesajı ve undo. Şu an
  geçersiz girdi sessizce reddediliyor, kodda bunun geçici olduğu yazılı.

---

## [Faz 4] Hilt — 2026-08-21

**Durum:** Tamamlandı

**Yapılanlar**
- **Hilt 2.60.1 seçildi.** Dagger 2.51'den beri KSP2 destekli ve
  `hilt-android-compiler` KSP işlemcisi olarak yayınlanıyor; mevcut
  Kotlin 2.2.10 / KSP `2.2.10-2.0.2` zincirine kapt'a hiç dokunmadan giriyor.
- `@HiltAndroidApp` (`SubTrackApplication`) ve `@AndroidEntryPoint`
  (`MainActivity`) eklendi. `MainActivity`'ye tek satır bile başka değişiklik
  yapılmadı.
- **`DatabaseModule`** (`@Provides`): veritabanı `@Singleton` ve
  `@ApplicationContext` ile sağlanıyor — instance her Activity'den uzun
  yaşadığı için Activity context'i sızıntı olurdu. DAO ise veritabanının
  üzerine bir görünüm, kendi scope'u yok.
- **`RepositoryModule`** (`@Binds`): arayüz → gerçekleme bağlaması.
  `SubscriptionRepositoryImpl`'e `@Inject constructor` eklendi.
- **`SubTrackApplication`'daki elle kurulum tamamen silindi.** `by lazy`
  property'ler, `Room.databaseBuilder` çağrısı ve elle repository oluşturma
  gitti; sınıf yalnızca `@HiltAndroidApp` taşıyan boş bir gövde.

**Karşılaşılan sorun — yeni bir hata sınıfı**

Plugin'i diğer ikisi gibi root'a `apply false` ile koyunca yapılandırma
patladı:

> `The KSP plugin was detected to be applied but its task class could not be
> found. This is an indicator that the Hilt Gradle Plugin is using a different
> class loader because it was declared at the root while KSP was declared in a
> sub-project.` (google/dagger#3965)

**Sebep:** Hilt'in Gradle plugin'i KSP'nin task sınıfını arıyor; ikisi farklı
scope'ta tanımlanınca farklı class loader'lara düşüyor ve arama boşa çıkıyor.

**Çözüm:** Hilt root'tan çıkarılıp KSP'nin durduğu yere — sadece `:app`'e —
alındı. Root'a, birinin "tutarlılık" adına geri eklememesi için gerekçe yorumu
bırakıldı.

**Faz 1a'daki AGP 9 sorunlarıyla aynı sınıftan ama farklı:** orada plugin
**sürümü** çakışıyordu, burada plugin'in **bildirim yeri**.

**Doğrulamalar**
- **KSP artık 13 dosya üretiyor** (Faz 2'de 2 idi): Room'un iki `_Impl`'i ve
  11 Hilt dosyası (`Hilt_MainActivity`, iki `_GeneratedInjector`,
  `SubscriptionRepositoryImpl_Factory`, iki `DatabaseModule_*Factory`,
  aggregated root ve `hilt_aggregated_deps`).
- **`@Binds` seçiminin somut kanıtı üretilen dosya listesinde:**
  `DatabaseModule`'ün iki `@Provides`'ı için ikişer factory üretilmiş, ama
  `RepositoryModule` için **hiçbir factory yok.** Dagger `@Binds`'ı bir cast'e
  indiriyor; `@Provides` olsaydı çağrılacak bir metot ve onu saran bir factory
  daha olurdu.
- **Domain katmanı hâlâ saf:** `dagger` / `javax.inject` / `Hilt` taraması
  **sıfır eşleşme**, import sayısı hâlâ iki
  (`domain.model.Subscription`, `kotlinx.coroutines.flow.Flow`).

**Değişen dosyalar**
- `gradle/libs.versions.toml` — Hilt 2.60.1, `hilt-android`, `hilt-compiler`
- `build.gradle.kts` — Hilt **bilerek eklenmedi**, gerekçe yorumu
- `app/build.gradle.kts` — plugin alias'ı ve bağımlılıklar
- `app/src/main/java/com/elinacn/subtrack/SubTrackApplication.kt` — boş gövde
- `app/src/main/java/com/elinacn/subtrack/MainActivity.kt` — sadece annotation
- `app/src/main/java/com/elinacn/subtrack/di/DatabaseModule.kt` (yeni)
- `app/src/main/java/com/elinacn/subtrack/di/RepositoryModule.kt` (yeni)
- `app/src/main/java/com/elinacn/subtrack/data/repository/SubscriptionRepositoryImpl.kt`
  — `@Inject constructor`

**Commit'ler**
- `07e9aaa` build: add Hilt plugin and dependencies
- `58a4cbe` feat: enable Hilt in application and activity
- `d99e906` feat: add database and repository Hilt modules
- `b8cfc52` refactor: remove manual dependency wiring

**Tag**
- `phase-4-done`

**Elle test sonucu**
- Uygulama açılıyor — bu fazın asıl sınavıydı, çünkü Hilt grafı çalışma
  zamanında kuruyor ve derlemenin geçmesi kanıt değil.
- UI değişmemiş; ekleme, silme, toplam, ekran döndürme ve kapat–aç doğru.
- Kaydırarak silmede regresyon yok.

### Faz 3'teki dört hantal noktanın durumu

**Çözüldü (2/4):**
1. **Cast kalktı.** `(context.applicationContext as SubTrackApplication)` diye
   bir şey yok. Bağımlılık derleme zamanında doğrulanıyor: bir modül eksik olsa
   Hilt **derlemeyi** durdurur, uygulama çalışma zamanında çökmez.
3. **Graf sırası elle yönetilmiyor.** `SubTrackApplication` boş gövde. DataStore
   (Faz 9) veya WorkManager (Faz 10) eklendiğinde yeni bir `@Provides` yazılacak,
   kurulum sırasını Dagger çözecek.

**Faz 5'te çözülecek (1/4):**
2. **ViewModel fabrikası.** `@HiltViewModel` + `@Inject constructor` altyapısı
   hazır ama henüz ViewModel yok. Kazanç `HomeViewModel` yazılınca somutlaşacak.

**Faz 7'de çözülecek (1/4):**
4. **Test edilebilirlik.** `@TestInstallIn` ile modül değiştirme altyapısı
   mevcut, bedeli fake'lerle test yazarken görülecek.

**Sonraki faz için not**
- Faz 5 `hiltViewModel()` fonksiyonunu kullanacak; bunun için
  `androidx.hilt:hilt-navigation-compose` bağımlılığı gerekiyor, ROADMAP'e
  madde olarak eklendi.
- Veritabanı dosyası hâlâ oluşmadı: Hilt `@Provides` metotlarını tembel çağırır,
  kimse repository istemediği için Room açılmadı. Faz 5'te ViewModel isteyince
  `subtrack.db` ilk kez yaratılacak.

---

## [Faz 3] Repository Katmanı (manuel DI) — 2026-08-20

**Durum:** Tamamlandı

**Yapılanlar**
- **`SubscriptionRepository` arayüzü domain'e**, `SubscriptionRepositoryImpl`
  gerçeklemesi data'ya eklendi. DAO'dan gelen `Flow<List<SubscriptionEntity>>`,
  `Flow.map` ile mapper'dan geçirilip `Flow<List<Subscription>>` olarak yukarı
  veriliyor. Akış bozulmuyor: tabloda bir değişiklik olduğunda liste
  kendiliğinden yeniden yayınlanıyor.
- **`SubTrackApplication` oluşturuldu.** Veritabanı ve repository `by lazy` ile
  elle kuruluyor, `AndroidManifest.xml`'e `android:name` ile kaydedildi.
- Hilt kullanılmadı; `@Inject`/`@Module`/`@Provides` yok. Bu kasıtlı — Faz 4'ün
  neyi çözdüğünü görebilmek için kurulum önce elle yapıldı.
- `withContext` eklenmedi: Room hem `suspend` hem `Flow` sorgularını zaten kendi
  arka planına alıyor (ARCHITECTURE §8).

**Doğrulamalar**
- **Domain katmanında `android`/`androidx` import'u yok.** Yeni eklenen tek
  dosyada iki import var, ikisi de meşru: `domain.model.Subscription` ve
  `kotlinx.coroutines.flow.Flow`.
- **Sınır kapalı.** `SubscriptionEntity`, `domain/` altında **sıfır** kez
  geçiyor; `data/` dışında hiçbir dosyada bahsi yok. Entity gerçekten
  `SubscriptionRepositoryImpl`'de duruyor.
- **Veritabanı dosyası bu fazda oluşmuyor.** `by lazy` sayesinde kimse
  repository'yi istemediği sürece Room hiç açılmıyor. Kasıtlı — şimdi açmak boş
  bir dosya yaratmaktan başka işe yaramazdı. Faz 5'te UI'a bağlanınca oluşacak.

**Değişen dosyalar**
- `app/src/main/java/com/elinacn/subtrack/domain/repository/SubscriptionRepository.kt` (yeni)
- `app/src/main/java/com/elinacn/subtrack/data/repository/SubscriptionRepositoryImpl.kt` (yeni)
- `app/src/main/java/com/elinacn/subtrack/SubTrackApplication.kt` (yeni)
- `app/src/main/AndroidManifest.xml` — `android:name=".SubTrackApplication"`

**Commit'ler**
- `16063f0` feat: add subscription repository interface and implementation
- `5c7c5fa` feat: wire dependencies manually in application class

**Tag**
- `phase-3-done`

**Elle test sonucu**
- Uygulama açılıyor, çökme yok (`Application` sınıfı eklenmesi açılış yolunu
  değiştirdiği için asıl risk buydu).
- UI değişmemiş; ekleme, silme, toplam hesabı ve ekran döndürme regresyonsuz.
- Kapat–aç sonrası liste Netflix/Spotify'a dönüyor — **beklenen davranış**,
  kalıcılık Faz 5'te geliyor.

### Manuel DI'ın hantal noktaları — Faz 4'ün gerekçesi

İki nesne için mevcut hali masum görünüyor; sorun bundan sonrasında başlıyor.

1. **Erişim cast gerektiriyor.**
   `(context.applicationContext as SubTrackApplication).subscriptionRepository`
   Bu cast derleme zamanında doğrulanmıyor. Manifest'teki `android:name` satırı
   silinse kod hâlâ derlenir, uygulama **çalışma zamanında**
   `ClassCastException` ile çöker. Hilt'te böyle bir cast yok; bağımlılık
   derleme zamanında doğrulanır.
2. **ViewModel'a bağımlılık geçirmek elle `ViewModelProvider.Factory` yazmayı
   gerektiriyor** — her ViewModel için ayrı, her yeni bağımlılıkta güncellenen
   ~15 satır boilerplate. Faz 5'te `HomeViewModel(repository)` yazarken
   doğrudan karşımıza çıkacak.
3. **Graf büyüdükçe kurulum sırası elle yönetiliyor.** Zincir şu an iki halkalı
   (`database → dao → repository`). DataStore (Faz 9) ve WorkManager (Faz 10)
   eklendikçe `SubTrackApplication` şişecek, `by lazy` zincirleri elle takip
   edilecek.
4. **Testte sahte repository koymak üretim kodunu değiştirmeyi gerektiriyor.**
   Hilt'te `@TestInstallIn` ile modül değiştirilir, üretim kodu ellenmez.
   Faz 7'de fake'lerle test yazarken bedeli görülecek.

**Özet:** Faz 4'ün kazancı "daha az kod" değil; **cast'in ve fabrika
boilerplate'inin kalkması, grafın derleme zamanında doğrulanması.** 1. ve 2.
maddeler Faz 5'e başlar başlamaz somut olarak canımızı yakacak.

### Açık karar — ARCHITECTURE §9 sapması

Repository imzaları `Result<T>` döndürmüyor; ARCHITECTURE §9 ise *"Repository,
`Result<T>` döndürür ya da özel bir `DataError` tipi kullanır"* diyor.

**Şu an bir ihlal doğurmuyor:** Room hata durumunda exception fırlatıyor, bu da
`viewModelScope` içinde yakalanıp `UiState.errorMessage`'a çevrilebilir — §9'un
asıl yasakladığı *sessiz* `try/catch` bu değil. Ama §9'un lafzına da uymuyor.

**Karar Faz 6'ya ertelendi** (girdi doğrulama fazı), çünkü hata yönetiminin
somut ihtiyacı orada ortaya çıkacak; şimdi soyut karar vermek erken olurdu.
ROADMAP Faz 6'ya madde olarak eklendi.

**Sonraki faz için not**
- Faz 4: Hilt. `SubTrackApplication`'ın gövdesi `@Module`/`@Provides`'a taşınacak,
  cast kalkacak.

---

## [Faz 2] Domain Modelleri + Room Şeması — 2026-08-20

**Durum:** Tamamlandı

**Yapılanlar**
- **Domain katmanı kuruldu.** `Money` (value class, `Long` kuruş), `BillingPeriod`,
  `SubscriptionCategory`, `Subscription`. Katmanda **tek bir `import` satırı
  yok** — `android.*` sızması yapısal olarak imkânsız, taramayla doğrulandı.
  `Money` içinde `plus`, `minus`, `times`, `compareTo` ve `ZERO`; `Double`/`Float`
  hiçbir yerde geçmiyor.
- **`SubscriptionEntity` dokuz alanla oluşturuldu:** `id`, `name`, `priceInCents`,
  `currencyCode`, `billingPeriod`, `nextPaymentDate`, `category`, `iconKey`,
  `createdAt`. v1.5'e kadarki tüm alanlar baştan dahil (PROJECT_SPEC §4 şema
  notu), böylece sonraki sürümler migration gerektirmeyecek. Entity yalnızca
  ilkel tiplerden oluşuyor — enum'lar isimle, para kuruş cinsinden — bu yüzden
  type converter gerekmedi.
- **`SubscriptionDao`:** `observeAll(): Flow<List<SubscriptionEntity>>` (suspend
  değil, akış), `getById`, `insert`, `update`, `deleteById` (hepsi `suspend`).
  ARCHITECTURE §8'e uygun: Room ikisini de kendi arka planına alıyor.
- **`SubTrackDatabase`** version 1, `exportSchema = true`. Instance kurulmadı —
  Faz 3 elle, Faz 4 Hilt ile kuracak.
- **`SubscriptionMapper`:** saf fonksiyonlar, Entity ↔ Domain. Bağımlılığı yok,
  doğrudan test edilebilir.
- **Room 2.8.4 katalogda yoktu, eklendi.** `room-runtime`, `room-ktx` ve
  `room-compiler`. Derleyici **`ksp()`** ile bağlandı, kapt kullanılmadı.

**Doğrulamalar**
- **KSP artık gerçekten çalışıyor.** Faz 1a'da `kspDebugKotlin SKIPPED` idi;
  şimdi görev koşuyor ve iki dosya üretiyor: `SubscriptionDao_Impl.kt`,
  `SubTrackDatabase_Impl.kt`. Üretilen DAO'da `observeAll`,
  `createFlow(__db, false, arrayOf("subscriptions"))` kullanıyor — tabloyu
  dinleyip değişince yeniden yayınlıyor.
- **Şema JSON'u okundu ve doğrulandı**
  (`app/schemas/com.elinacn.subtrack.data.local.SubTrackDatabase/1.json`):
  dokuz alanın hepsi yerinde, nullable'lık Kotlin tipleriyle birebir örtüşüyor
  (yalnızca `nextPaymentDate` ve `iconKey` `NOT NULL` almamış), ve
  **`AUTOINCREMENT` üretilmiş.** Bu son madde Faz 1'deki *"Room gelince id
  geri dönüşümü kendiliğinden çözülür"* öngörüsünü kanıtlıyor — SQLite
  AUTOINCREMENT silinen id'leri asla yeniden kullanmaz, dolayısıyla
  `MainActivity`'deki monoton sayaç Faz 5'te gereksiz kalacak.
- Domain katmanında `android`/`androidx` import taraması: **temiz.**

**Değişen dosyalar**
- `app/src/main/java/com/elinacn/subtrack/domain/model/` — `Money.kt`,
  `BillingPeriod.kt`, `SubscriptionCategory.kt`, `Subscription.kt` (yeni)
- `app/src/main/java/com/elinacn/subtrack/data/local/entity/SubscriptionEntity.kt` (yeni)
- `app/src/main/java/com/elinacn/subtrack/data/local/dao/SubscriptionDao.kt` (yeni)
- `app/src/main/java/com/elinacn/subtrack/data/local/SubTrackDatabase.kt` (yeni)
- `app/src/main/java/com/elinacn/subtrack/data/mapper/SubscriptionMapper.kt` (yeni)
- `app/schemas/.../1.json` — Room'un ürettiği şema, commit'e dahil
- `gradle/libs.versions.toml`, `app/build.gradle.kts` — Room 2.8.4 ve
  `room.schemaLocation` KSP argümanı

**Commit'ler**
- `72a9e78` feat: add domain models
- `f29a512` feat: add Room entity and dao
- `fd26d43` feat: add database and mapper

**Tag**
- `phase-2-done`

**Karşılaşılan sorunlar**
- Kayda değer bir sorun çıkmadı. Faz 1a'da kurulan KSP altyapısı ilk denemede
  çalıştı.

**Alınan kararlar**
- **`enumValueOf` kullanılmadı.** Tanımadığı bir isimde exception fırlatıyor;
  elle düzenlenmiş veya daha yeni bir sürümün yazdığı tek bir bozuk satır tüm
  listeyi çökertirdi. İsim eşleştirilip varsayılana düşülüyor — sessiz
  `try/catch` değil, açık bir fallback (ARCHITECTURE §9).
- **`exportSchema = true`.** Kapalı olsaydı Room, entity ilerledikten sonra
  sürüm 1'in nasıl göründüğünü bilemez ve migration'ı doğrulayamazdı.

**Elle test sonucu**
- Bu fazda elle test yok: kod hiçbir ekrana bağlı değil, uygulamanın davranışı
  değişmedi. Doğrulama KSP çıktısı, domain saflık taraması ve şema JSON'u
  üzerinden yapıldı.

**Sonraki faz için not**
- Faz 3: `SubscriptionRepository` arayüzü domain'e, gerçeklemesi data'ya.
  Nesneler `SubTrackApplication` içinde elle kurulacak.
- `SubscriptionMapper` saf fonksiyonlardan oluşuyor ve testi yok; Faz 7'de
  yazılacak, ama bağımlılığı olmadığı için daha erken de alınabilir.

---

## [Faz 1b + 1c] Tema, Metinler ve Kontrast — 2026-08-20

**Durum:** Tamamlandı

**Yapılanlar — Faz 1b (yedi commit)**
- **Kotlin sürüm tutarsızlığı giderildi.** `libs.versions.toml` 2.0.21 diyordu,
  AGP 9 gerçekte 2.2.10 kullanıyordu; Compose compiler plugin'i eklendiği
  derleyiciden bir minör sürüm geride kalıyordu. Alias gerçek sürüme
  çevrildi, **sorunsuz geçti** (riskli madde olarak işaretlenmişti).
- **`Theme.kt` devreye alındı.** Tüm hardcoded renkler
  `MaterialTheme.colorScheme` üzerinden okunuyor; tanımlı olduğu halde UI'a
  hiç ulaşmayan koyu şema nihayet uygulanıyor.
- **`Type.kt` bağlandı.** `MaterialTheme(typography = Typography)` geçildi —
  dosya proje şablonundan beri ölü koddu. Kullanılan stiller tanımlandı:
  `headlineMedium`, `titleLarge`, `titleMedium`, `bodyLarge`.
- **`Dimens.kt` oluşturuldu.** `MainActivity`'deki her `dp` sabiti anlamlı
  isimli bir alana taşındı (`ListBottomSpacing`, `SectionTitleStart`,
  `DashboardCorner` vb.).
- **Tüm kullanıcı metinleri `strings.xml`'e taşındı**, `values-en` karşılıkları
  girildi. Faz 1a'dan kalan `CustomAccessibilityAction("Sil")` borcu kapandı.
- **Kod içi Türkçe yorumlar İngilizceye çevrildi** (CLAUDE.md §2). Veri modeli
  yorumu ayrıca gerçeğe uyduruldu: `Double`'ın "hesaplamalar için kritik"
  olduğunu söylüyordu, tam tersi doğru.
- **`.gitignore` tamamlandı.** `local.properties` iki kez yazılıydı, tekilleşti;
  `.kotlin/`, `*.apk`, `*.aab`, `*.jks`, `*.keystore` eklendi.
- **Ölü kaynak temizliği.** `colors.xml` tamamen silindi — 7 şablon rengi, kodda
  ve XML'de **sıfır** referans. `welcome_message` string'i de hiç
  gösterilmiyordu, iki dilden kaldırıldı.
- **Koyu temaya `DarkBackground` (#1C2022) eklendi.** Eski şemada `background`
  ve `surface` ikisi de `DarkText`'ti; kartlar arka planla aynı renk olurdu,
  yani koyu tema fiilen kullanılamazdı.

**Yapılanlar — Faz 1c (tek commit)**
- Faz 1b elle testinde iki sorun çıktı: **fiyatlar silik görünüyordu** ve
  **kartlar arka plandan yeterince ayrışmıyordu.** Ölçüldü: fiyat metni
  (PastelBlue / beyaz) **1.78:1**, kart↔arka plan **1.05:1**.
- **Çözüm: palet değiştirilmedi, mavi iki role bölündü.**
  `primary` artık `DeepBlue` (#46707F) — metin ve ikon aksanı, beyaz üzerinde
  **5.41:1**. `primaryContainer` eski `PastelBlue` (#AEC6CF) olarak kaldı;
  dashboard kartı, FAB ve Kaydet butonu **birebir aynı** görünüyor.
- `background`: `PastelGray` → `SoftBlueGray` (#D5DEE2). Kart ayrışması
  **1.05:1 → 1.37:1**.
- Dashboard etiketindeki `0.7f` alfa kaldırıldı; kontrastı **3.64:1**'e
  düşürüyordu. Hiyerarşiyi 16sp normal ↔ 32sp ExtraBold farkı zaten taşıyor.
- Koyu şemaya `primaryContainer` / `onPrimaryContainer` rolleri eklendi,
  değerleri eskiden `primary` / `onPrimary` üzerinden kullandığıyla aynı —
  yani koyu tema **görsel olarak değişmedi**, sadece Material varsayılanlarına
  düşmesi engellendi.
- **`PastelBlue`, `PastelMint`, `PastelGray`, `DarkText` değerleri değişmedi.**
  Pastel kimlik korundu; `DeepBlue` de aynı hue ailesinin (~197°) koyu ucu.
- Açık ve koyu temada **tüm metin/zemin çiftleri WCAG AA eşiğini geçiyor.**

**Değişen dosyalar**
- `gradle/libs.versions.toml` — Kotlin 2.0.21 → 2.2.10
- `app/src/main/java/com/elinacn/subtrack/ui/theme/Color.kt` — `DarkBackground`,
  `DeepBlue`, `SoftBlueGray`
- `app/src/main/java/com/elinacn/subtrack/ui/theme/Theme.kt` — eksiksiz `on*`
  rolleri, container rolleri, typography bağlandı
- `app/src/main/java/com/elinacn/subtrack/ui/theme/Type.kt` — kullanılan stiller
- `app/src/main/java/com/elinacn/subtrack/ui/theme/Dimens.kt` — yeni
- `app/src/main/java/com/elinacn/subtrack/MainActivity.kt` — renk/tipografi/ölçü
  kaynakları, `stringResource`, İngilizce yorumlar
- `app/src/main/res/values/strings.xml`, `values-en/strings.xml` — yeni metinler
- `app/src/main/res/values/colors.xml` — silindi
- `.gitignore` — tamamlandı

**Commit'ler**
- `e273587` build: align Kotlin and Compose compiler versions
- `2b91379` refactor: use MaterialTheme colors and typography
- `37c1187` refactor: extract dimensions to Dimens
- `a585cd5` feat: move user-facing strings to resources
- `09e328f` refactor: translate code comments to English
- `41f89af` chore: complete gitignore
- `0db78df` chore: remove dead resources
- `d70dbdc` refactor: improve light theme contrast

**Tag**
- `phase-1-done`

**Elle test sonucu**
- Açık temada fiyatlar net okunuyor, kartlar arka plandan ayrışıyor.
- Koyu tema değişmemiş (regresyon yok).
- İngilizce dil doğru çalışıyor.
- Kaydırarak silme, ekleme ve toplam hesabı regresyonsuz.
- **Doğrulanamayan:** TalkBack yerel eylemler menüsü ColorOS'ta açılamadığı
  için "Delete" etiketi cihazda görülemedi. Kaynak (`values-en/strings.xml`)
  ve kod (`stringResource(R.string.delete)`) tarafı doğru.

**İki bulgu — uygulama hatası değil**
- TalkBack'in silme için "Sil" demesi **Gboard'un backspace tuşundan** geliyor,
  bizim custom action'ımızdan değil. Klavye ayrı bir uygulama ve kendi dil
  ayarını kullanıyor.
- Fiyat girerken sayıların karışık dilde okunması TTS'in otomatik dil
  algılaması. Muhtemel katkı: fiyatı `Locale.US` ile biçimlendiriyoruz
  (`159.99`), Türkçe locale virgül bekliyor. Faz 9'a madde olarak eklendi.

**Sonraki faz için not**
- Faz 2: domain modelleri ve Room şeması. `Money` value class'ı `Double`'ı
  devralacak.
- Yeni renk eklenirken `primary` (metin aksanı) ↔ `primaryContainer` (dolu
  yüzey) ayrımına uyulmalı; gerekçesi ARCHITECTURE §12'de.

---

## [Faz 1a] Build Altyapısı + Kaydırarak Silme Yeniden Yazımı — 2026-08-20

**Durum:** Tamamlandı

**Yapılanlar — build altyapısı**
- `material-icons-extended` version catalog'a taşındı; artık doğrudan string
  olarak yazılmış bağımlılık kalmadı.
- **Compose BOM'da gerçek çakışma vardı.** BOM 2024.09.00 Compose 1.7.0'ı
  sabitliyordu, ama `activity-compose` 1.12.4 ve `lifecycle` 2.10.0 geçişli
  olarak Compose 1.9.2 çekiyordu. `material3`'ü kimse yukarı çekmediği için
  1.3.0'da kalmıştı — yani Compose 1.7 için derlenmiş bir material3, 1.9.2
  runtime üzerinde çalışıyordu. BOM 2025.11.01'e alındı; her şey 1.9.5,
  material3 1.4.0. Grafikte BOM'un üstüne çıkan modül kalmadı.
- **KSP, parcelize'ın aksine AGP 9 ile gelmiyor.** Sürümsüz alias
  *"plugin dependency must include a version number"* hatası verdi. Açık
  sürüm gerekti: `2.2.10-2.0.2` (Kotlin 2.2.10 ile eşleşen tek kararlı sürüm).
- `gradle.properties`'e `android.disallowKotlinSourceSets=false` eklendi.
  KSP 2.0.2 ürettiği kaynakları `kotlin.sourceSets` ile kaydediyor, AGP 9'un
  yerleşik Kotlin'i bunu yasaklıyor. **Bu bir AGP geçiş anahtarı, kalıcı
  çözüm değil — teknik borç.**
- **Gerçek Kotlin derleyicisi 2.2.10** (AGP 9.0.1 getiriyor), ama
  `libs.versions.toml` `kotlin = "2.0.21"` diyor ve bu yalnızca Compose
  plugin alias'ını besliyor. Tutarsız, Faz 1b'de hizalanacak.
- `.gitignore`: `deploymentTargetSelector.xml`, `deviceManager.xml`,
  `appInsightsSettings.xml` takipten çıkarıldı, `emulatorDisplays.xml`
  ignore'a eklendi.

**Yapılanlar — kaydırarak silme hotfix serisi (yedi tur)**
- `1f358c6` — id üretimi `max+1`'den monoton sayaca çevrildi. En yüksek id'li
  öğe silinince maksimum düşüyor, bir sonraki ekleme aynı id'yi geri alıyordu;
  LazyColumn key'i benzersiz olmaktan çıkıyordu.
- `7572d92` — silme `confirmValueChange`'den `LaunchedEffect`'e taşındı.
  O callback bir onay sorusudur ve jest yerleşirken birden çok kez çağrılabilir;
  içinde liste değiştirmek satırı animasyon bitmeden kompozisyondan atıyordu.
  Deprecated API de böylece kalktı.
- `b5f39b2` — `currentValue` yerine `settledValue` izleniyor. Kaynaktan
  doğrulandı: `currentValue` kaydırma sürerken en yakın anchor'a atlıyor, yani
  silme parmak hâlâ ekrandayken tetikleniyordu. Ayrıca varsayılan
  `positionalThreshold`'un yüzde değil **sabit 56.dp** olduğu görüldü —
  `totalDistance` parametresini alıp kullanmıyor.
- `b678e2d` — state `rememberSaveable` yerine düz `remember`. Saver
  `currentValue`'yu kaydedip `initialValue` olarak geri yüklüyor;
  `LazySaveableStateHolder` ise state'i satır silinince değil **yalnızca
  `performSave()` sırasında** temizliyor. Bayat `EndToStart` değeri oturum
  içinde yaşayıp yeni satırda kırmızı şerit oluyordu. Uygulama kapanınca
  sorunun kaybolması tam olarak bunu doğruluyordu.
- `ce62db8` — **`SwipeToDismissBox` tamamen bırakıldı**, özel
  `SwipeToDeleteRow` yazıldı. Eşik altı kaydırmada `settledValue` hiç
  değişmiyor, kütüphanenin `enabled = settledValue == Settled` koruması devreye
  girmiyor; geri yaslanma animasyonu ile kullanıcı sürüklemesi aynı
  `MutatePriority.Default`'ta olduğu için yeni jest animasyonu ortada iptal
  ediyor ve offset birikiyor. Public API'de bunu engellemenin yolu olmadığı
  kaynak koddan kanıtlandı.
- `7c2de53` — **son kök sebep.** `Animatable`'ın `snapTo` ve `animateTo`
  çağrıları tek mutex paylaşıyor. Sürükleme deltaları `scope.launch` ile
  kuyruğa giriyordu; parmak kalkınca `animateTo` başlıyor, kuyrukta bekleyen
  bir `snapTo` mutex'i kapıp animasyonu iptal ediyor, `CancellationException`
  fırlıyor ve altındaki `onDelete()` hiç çalışmıyordu. Kaydırma çalışır
  görünüyordu çünkü hepsi `snapTo` yolundan geçiyordu. Sürükleme düz
  `mutableFloatStateOf`'a çevrildi, yerleşme için `animate()` kullanılıyor.
  Aynı commit'te `semantics`'e `mergeDescendants = true` eklendi; onsuz
  TalkBack odağı `Text`'lere veriyor, custom action odaklanılamayan ebeveynde
  kalıyordu.

**Değişen dosyalar**
- `gradle/libs.versions.toml` — `material-icons-extended`, BOM 2025.11.01, KSP sürümü
- `app/build.gradle.kts` — catalog alias'ları, KSP plugin'i
- `gradle.properties` — `android.disallowKotlinSourceSets=false`
- `.gitignore` — makineye özel `.idea` dosyaları
- `app/src/main/java/com/elinacn/subtrack/MainActivity.kt` — `SwipeToDeleteRow`,
  monoton id sayacı, silme mantığı

**Commit'ler**
- `f32fdbe` build: migrate dependencies to version catalog
- `f14c09b` build: align Compose BOM with resolved dependency versions
- `0419593` build: add KSP plugin
- `7c6c5fd` chore: ignore machine-specific IDE files
- `1f358c6` fix: prevent id reuse and restore swipe box settle behavior
- `7572d92` fix: move swipe deletion out of confirmValueChange
- `b5f39b2` fix: raise swipe dismissal threshold to prevent accidental deletion
- `b678e2d` fix: resolve stale swipe state and keep threshold lambda stable
- `ce62db8` feat: replace SwipeToDismissBox with a hand-written swipe row
- `7c2de53` fix: make swipe deletion and its accessibility action fire

**Tag**
- `phase-1a-done` = `7c2de53`

**Karşılaşılan sorunlar**
- Hotfix serisi yedi tur sürdü. İlk üç tur tahmine dayalıydı ve tutmadı;
  çözüm ancak material3 ve foundation kaynak jar'ları indirilip okunduktan
  sonra geldi. Ders: davranışı belgelenmemiş kütüphane iç mantığı için
  kaynağa bakmak tahminden hızlı.
- `positionalThreshold` bu bileşende büyük ölçüde dekoratif çıktı:
  `computeTarget`'ın üç dalından ikisi (`!isMoving` ve hız ≥ 125 dp/s) onu
  hiç okumuyor. Hızlı fiskenin ısrarla silmesinin sebebi buydu.

**Elle test sonucu**
- Tüm adımlar geçti: tek ve ardışık hafif kaydırma silmiyor, hızlı fiske
  silmiyor, tam kaydırma siliyor, sil-ekle döngüsünde şerit dönmüyor, ekran
  döndürme sorunsuz, ters yön kıpırdamıyor, TalkBack'te "Sil" eylemi görünüyor
  ve çalışıyor.

**Bilinen borç**
- `SwipeToDeleteRow`'daki `CustomAccessibilityAction("Sil")` hardcoded Türkçe
  metin — CLAUDE.md §4 ihlali. `res/` bu fazda DOKUNMA listesindeydi, kodda
  `TODO` var. Faz 1b'de `strings.xml`'e taşınacak.
- `android.disallowKotlinSourceSets=false` geçici; KSP `android.sourceSets`
  DSL'ine geçince kaldırılmalı.

**Sonraki faz için not**
- Faz 1b: tema, `Dimens`, string'ler, Türkçe yorumlar, Kotlin sürüm hizalaması.
- Faz 6'da kalan iş küçüldü: `SwipeToDeleteRow` hazır, yalnızca `onDelete`
  callback'ini `HomeEvent.Delete`'e bağlamak ve undo eklemek gerekiyor.

---

## [Faz 0] Acil Düzeltmeler — 2026-08-18

**Durum:** Tamamlandı

**Yapılanlar**
- `onDismissRequest` dolduruldu — scrim'e dokunma, geri tuşu ve aşağı
  sürükleme artık sheet'i kapatıyor
- Kaydet butonu kaydettikten sonra sheet'i kapatıyor. Material3'ün
  belgelenmiş kalıbı kullanıldı: `sheetState.hide()` tamamlanınca
  `showBottomSheet = false`. Doğrudan atama sheet'i animasyonsuz kaybettiriyordu.
- `remember` → `rememberSaveable`, özel `Saver` ile. Liste artık ekran
  döndürmede ve process death'te korunuyor.
- `// Resetle ve Kapat` yorumu kaldırıldı (kod artık gerçekten ikisini de yapıyor)
- `ANALYSIS_REPORT.md` → `docs/archive/ANALYSIS_REPORT_2026-08.md`
- Takip edilmeyen belgeler git'e alındı

**Değişen dosyalar**
- `app/src/main/java/com/elinacn/subtrack/MainActivity.kt` — sheet kapatma,
  `rememberSaveable` + `SubscriptionListSaver`, yeni import'lar
- `docs/archive/ANALYSIS_REPORT_2026-08.md` — arşive taşındı (`git mv`)
- `CLAUDE.md`, `docs/*.md` — git'e eklendi

**Commit'ler**
- `af011bf` fix: close bottom sheet and preserve state on rotation
- `998bc4a` docs: add project documentation and working rules

**Tag**
- `phase-0-done` = `ec4789d`

**Karşılaşılan sorunlar**
- `@Parcelize` denendi, AGP 9 ile çalışmadı. İki ayrı hata alındı:
  sürüm belirtilince *"plugin is already on the classpath with an unknown
  version"*, sürümsüz uygulanınca `kotlinx.parcelize.Parcelize`
  çözümlenemedi (runtime compile classpath'te yok). Çözüm: `Saver`
  her öğeyi `Int`/`String`/`Double` üçlüsüne düzleştiriyor — Bundle bu
  tipleri zaten tanıdığı için Parcelable gereksiz. **`build.gradle.kts`
  ve `libs.versions.toml` değiştirilmedi.**
- `SubTrack.md` repoda ve git geçmişinde hiç yok; ROADMAP maddesi silindi.
- Derleme sırasında `libandroidx.graphics.path.so` strip uyarısı görüldü.
  Ortam kaynaklı, koddan bağımsız — `assembleDebug --rerun-tasks` ilk kez
  `stripDebugDebugSymbols` görevini çalıştırdığı için ortaya çıktı.

**Elle test sonucu**
- Elle test: 10 adımın hepsi cihazda (OPPO A15s, Android 10) beklendiği
  gibi çalıştı. Ekran döndürme ve process death dahil, veri kaybı yok.

**Bilinen eksikler**
- Boş isimle Kaydet'e basınca sessizce başarısız oluyor, sheet açık kalıyor
  → Faz 6 (girdi doğrulama)
- `Subscription.price` hâlâ `Double` — CLAUDE.md §4 bunu yasaklıyor
  → Faz 2'de `Money` (kuruş cinsinden `Long`) devralacak

**Sonraki faz için not**
- AGP 9 bulgusu KSP eklenirken tekrar karşımıza çıkabilir: plugin'i
  sürüm belirtmeden uygulamayı dene.
- `MainActivity.kt` içindeki yorumlar Türkçe, CLAUDE.md §2 İngilizce
  istiyor — Faz 1'e madde olarak eklendi.

---

## [Faz 0] Proje Altyapısı ve Belgelendirme — 2026-08-18

**Durum:** Tamamlandı

**Yapılanlar**
- Claude Code ile tam kod tabanı analizi yapıldı (3 Kritik, 9 Orta, 7 Düşük bulgu)
- Proje belgeleri oluşturuldu: `CLAUDE.md`, `PROJECT_SPEC.md`,
  `ARCHITECTURE.md`, `ROADMAP.md`, `WORKFLOW.md`, `PROGRESS.md`
- Mimari kararlaştırıldı: Repository + Hilt + UiState
- Hedef netleşti: Play Store'da yayınlanacak ürün

**Kritik bulgular**
- Bottom sheet kapanmıyor → uygulama fiilen kullanılamaz durumda
- Room projede hiç yok (eski `SubTrack.md` yanlış bilgi veriyordu)
- State `remember` içinde, ekran döndürmede veri kayboluyor

**Sonraki faz için not**
- `SubTrack.md` ve `ANALYSIS_REPORT.md` → `docs/archive/` altına taşınacak
- Compose BOM ↔ activity-compose sürüm çakışması Faz 1'de doğrulanacak
