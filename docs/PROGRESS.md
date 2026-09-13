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

## [Faz 12-2 hotfix] Bildirim ilerletilmiş tarihi okuyor, gecikme penceresi kalktı — 2026-09-13

**Durum:** Tamamlandı. 12-2'de ölçülüp sohbete bırakılan iki soru da kapandı:
pencere kaldırıldı, `TESTING.md` #29 yeniden yazıldı.

### Sorun

12-2 ölçümü: kart `NextPaymentDate.onOrAfter` sonucunu, `PaymentReminderSelection`
ise **çıpayı** okuyordu. Aynı aylık abonelik için bildirim "1 day overdue",
kart "29 days left" diyordu. İki doğru yoktu — yanlış olan çıpayı okuyan taraftı.

### Görev 1 — seçim mantığı

`PaymentReminderSelection.on()` artık aboneliğin **periyoduyla** ilerletilmiş
tarihi ölçüyor. `OVERDUE_WITHIN_DAYS` ve gecikme dalı kalktı;
`UPCOMING_WITHIN_DAYS = 1` aynen duruyor. Kural: **bugün ödenecek + 1 gün kalan.**

### Görev 3 — Overdue ulaşılabilir mi: kanıt, sonra kaldırma

`PaymentCountdown.between`'in üretimde **iki** çağıranı var ve Görev 1'den
sonra **ikisi de** ona `NextPaymentDate.onOrAfter` sonucunu veriyor:

| çağıran | verdiği tarih |
|---|---|
| `HomeViewModel` (kart) | `onOrAfter(today, anchor, period)` |
| `PaymentReminderSelection` (bildirim) | `onOrAfter(today, anchor, period)` |

O sonuç hiçbir zaman bugünden önce değil — `NextPaymentDateTest`'teki özellik
testiyle sabitli. Yani **gecikmiş dalı çalışamıyor; çalışan dal her zaman
`DueToday` ya da `Upcoming`.** Kaldırıldı:

- `PaymentCountdown.Overdue` tipi ve `between`'in negatif dalı
- `PaymentReminderNotifier`'daki `days_overdue` dalı
- `SubscriptionCard`'daki `error` renkli geri sayım dalı, `asText` dalı ve
  `SubscriptionCardOverduePreview`
- `SubscriptionRowDescription`'daki `asString` dalı
- `days_overdue` çoğulu — `values` ve `values-en`
- `OVERDUE_WITHIN_DAYS`
- "gecikmiş"ten söz eden kanal açıklaması (iki dilde de yenilendi; kullanıcıya
  görünen ve artık doğru olmayan tek metindi)

`between` geçmiş bir tarihi artık **reddediyor** (`require`), uydurma bir cevap
üretmiyor: çıpayı doğrudan veren bir çağıran `NextPaymentDate`'i atlamıştır.

**Kapsam notu:** promptun DOKUNMA listesinde `ui/home/` vardı ("kart davranışı
değişmiyor"). `SubscriptionCard.kt` ve `SubscriptionRowDescription.kt`
değiştirildi çünkü `when` dallarının kaldırılmasını Görev 3 istiyor ve derleyici
zorunlu kılıyor. Davranış birebir aynı: kaldırılan dallar zaten üretilemeyen bir
durumu çiziyordu.

`MaterialTheme.colorScheme.error` uygulamada **hâlâ kullanılıyor**
(`SwipeToDeleteRow`), yani Faz 14 renk borcu duruyor.

### Görev 2 — testler

176 → **202 birim testi** (5 yeni seçim testi, 2 sözleşme testi), 0 hata.
`lintDebug` **0 hata, 23 uyarı** — 23'ün 15'i `libs.versions.toml`'daki sürüm
tazeliği uyarısı (yeni sürümler yayınlandıkça artıyor); hiçbiri bu değişiklikten
gelmiyor.

Beklentisi değişen testler (hiçbiri silinmedi, her birinin yanında gerekçesi var):

| test | eskiden | şimdi |
|---|---|---|
| `on_theAnchorWasYesterday_...` | `Overdue(1)`, seçilirdi | seçilmiyor — sonraki ödeme 14 Nisan |
| `on_theAnchorWasThreeDaysAgo_...` | `Overdue(3)`, pencerenin kenarı | seçilmiyor — 12 Nisan |
| `on_theAnchorWasFourDaysAgo_...` | seçilmiyordu (pencere dışı) | seçilmiyor (pencere yok) |
| `on_mixedList_...` | OneLate + ThreeLate girerdi | girmiyor; yerine geçmiş çıpalı haftalık **giriyor** |
| `on_theWindowCrossesAMonthBoundary_...` | `Overdue(3)` | yalnızca 1 Nisan |
| `on_theWindowCrossesAYearBoundary_...` | `Overdue(3)` | 1 Ocak + Noel çıpalı haftalık |
| `between_theDateIsBehindToday_...` | `Overdue(1)` | `IllegalArgumentException` |
| `between_theDateIsAYearBehind_...` | `Overdue(365)` | `IllegalArgumentException` |
| `everyPeriod_countsTowards...` | "hiçbiri Overdue değil" | üç periyodun üç ayrı günü (16 / 322 / 4) |

Yeni: geçmiş çıpalı haftalık → yarın → **giriyor**; geçmiş çıpalı aylık → 20 gün
sonra → **girmiyor**; bugüne denk gelen geçmiş çıpa → `DueToday`; aynı çıpa üç
periyotla üç farklı sonuç; seçilen hatırlatma **çıpayı taşımaya devam ediyor**.

Enstrümantasyon fikstürü yeniden yazıldı: `OneDayLate`/`ThreeDaysLate` isimleri
yanıltıcıydı, artık satırlar **nereye vardıklarına** göre adlandırılıyor
(`PassedLandsTomorrow`, `PassedLandsToday`, `PassedLandsFarOff`,
`LongPassedYearly`) ve bildirim metninde "overdue"/"gecik" geçmediği ayrıca
doğrulanıyor.

### Doğrulama — iki emülatörde de birebir aynı

**(a) Ham bildirim metni** (API 29 ve API 34):

```
EXTRA_TITLE=[Payment reminder: 4 subscriptions]
EXTRA_TEXT =[DueToday — today, Tomorrow — tomorrow,
             PassedLandsTomorrow — tomorrow, PassedLandsToday — today]
```

"overdue" **yok**, "gecik" **yok**.

**(b)/(c) Kart ile bildirim, aynı veriden, yan yana:**

| satır | çıpa · periyot | kart | bildirim |
|---|---|---|---|
| DueToday | bugün · aylık | Due today | today |
| Tomorrow | +1 · aylık | 1 day left | tomorrow |
| TwoDaysOut | +2 · aylık | 2 days left | (yok) |
| **PassedLandsTomorrow** | **−6 · haftalık** | **1 day left** | **tomorrow** |
| **PassedLandsToday** | **−7 · haftalık** | **Due today** | **today** |
| **PassedLandsFarOff** | **−3 · aylık** | **27 days left** | **(yok)** |
| LongPassedYearly | −400 · yıllık | 330 days left | (yok) |
| NoDate | — | (gösterge yok) | (yok) |

Çelişki kalmadı. Geçmiş çıpalı iki satır bildirime **girdi** (vardıkları gün
yakın), uzağa varan iki satır **girmedi**.

### (e) 75 maddelik sabit regresyon — ikisinde de

Kayıtlı istisnalar önceki fazlarla aynı (**API 29**: #15 koyu tema ve #16 dil —
`cmd uimode`/`cmd locale` servisleri bu imajda yok; #34-#37, #41-#45 API 33+
maddeleri, karşılıkları #40 ve #46 koşuldu. **API 34**: #40 ve #46 API < 33
maddeleri).

**#29 yeni hâliyle koşuldu ve geçti**, üç iddiası da ölçüldü: çıpa 2026-09-08
(5 gün geçmiş), kart `Late, TRY 40.00, Monthly, 25 days left`, "gecikmiş" yok,
ve `run-as` ile okunan satırda tarih **değişmemiş** (`1788825600000 → 2026-09-08`).
Listenin altındaki 12-2 geçici notu kaldırıldı.

Üç madde ilk okumada "durum kalıcı olmuş" gibi göründü (#60 filtre, #68 yıllık
görünüm, #14 yazılmış metin) — üçü de **bayat ağaç**tı. Ekran görüntüsü
uygulamanın hâlâ açılış ekranında olduğunu gösterdi; uzun beklemeden sonra üçü
de doğru davranışı verdi (#60 → All, #68 → Total Monthly 243,33 + Monthly
seçili, #14 → "Half typed" korunuyor). TESTING.md'ye yazıldı.

### Değişen dosyalar

- `domain/usecase/PaymentReminderSelection.kt` — ilerletilmiş tarih, pencere yok
- `domain/usecase/PaymentCountdown.kt` — `Overdue` kalktı, `require` geldi
- `reminder/PaymentReminderNotifier.kt`, `reminder/PaymentReminderWorker.kt` — dal ve metin
- `ui/home/components/SubscriptionCard.kt`, `SubscriptionRowDescription.kt` — ölü dallar
- `res/values/strings.xml`, `res/values-en/strings.xml` — `days_overdue` silindi, kanal açıklaması
- `test/.../PaymentReminderSelectionTest.kt`, `PaymentCountdownTest.kt`, `HomeViewModelNextPaymentTest.kt`
- `androidTest/.../PaymentReminderWorkerTest.kt` — fikstür
- `docs/ARCHITECTURE.md` §17 ve §18, `docs/ROADMAP.md`, `docs/TESTING.md`

**Dokunulmayanlar:** `NextPaymentDate`, Room şeması, DataStore, Manifest,
`Theme.kt`/`Color.kt`, `ui/settings/`.

### Commit'ler

- `322d9e3` feat: remind about the payment that is next, not the one that has gone
- `cb5c013` refactor: drop the overdue state, which nothing can reach any more
- `4935f86` docs: record why the lateness window went, and what its going costs

### Karşılaşılan sorunlar

- **`pm clear` bildirim iznini de geri alıyor.** API 34'te ilk enstrümantasyon
  koşusu "no notification was posted" dedi; `pm grant`, clear'dan **sonra**
  gelmeli. TESTING.md'ye yazıldı.
- **`am force-stop` uygulamanın bildirimini siliyor**, yani bildirim metni
  uygulama açılmadan önce okunmalı. Yazıldı.
- **API 34 emülatörü "System UI isn't responding" veriyordu**; sürücü artık
  diyaloğu kendi kapatıyor, tekrarlarsa `adb reboot`. Ürünle ilgisi yok.

### Açık kalan

- Hiçbiri. Faz 12 kapalı; tag kullanıcıda.

---

## [Faz 12-2] Tarih İlerletme — 2026-09-13

**Durum:** Tamamlandı. **Faz 12 KAPANDI.** Gecikme penceresine bilerek
dokunulmadı — ölçüldü ve karar sohbete bırakıldı (aşağıda).

### İlerletme — okuma anında, veritabanına yazmadan

`domain/usecase/NextPaymentDate.onOrAfter(today, anchor, period)`: çıpayı,
bugüne ulaşana kadar tam periyotlarla ilerletir. Saklanan tarih **çıpa** olarak
kalıyor; hiçbir yere yazılmıyor. Gerekçe 10a'daki geri sayım kararının aynısı —
ilerletilmiş tarih aboneliğin değil, *abonelik + bugün* ikilisinin özelliği.

**Döngü yok, aritmetik var.** Üç periyot için de aynı şekil: kaç **tam** periyot
geçtiğini `ChronoUnit.WEEKS/MONTHS/YEARS.between` ile sor, çıpaya bir kerede
ekle, gerekirse bir periyot daha ekle. `between` tam birim saydığı için düzeltme
en fazla bir adım. On yıllık haftalık bir çıpa 558 adım değil, bir çıkarma ve
bir toplama.

**Ay sonu tuzağı — çıpadan sayılıyor.** 31 Ocak'tan ayda bir ilerlenirse
28 Şubat'a, oradan 28 Mart'a gidilir ve abonelik sessizce 28'ine taşınır. Kaç ay
geçtiğini sorup çıpaya bir kerede eklemek Mart'ta 31'i geri veriyor. Testle
sabitlendi (`monthly_theClampDoesNotStick_becauseCountingStartsAtTheAnchor`), ve
dört ay boyunca 28 Şubat → 31 Mart → 30 Nisan → 31 Mayıs dizisi de ayrıca.

`plusMonths`/`plusYears`'in takvim mantığı **bilerek** korundu: 31 Ocak + 1 ay =
28 Şubat, 29 Şubat 2028 + 1 yıl = 28 Şubat 2029. Gün sayısına çevrilmedi.

### Geri sayım ve kart

`PaymentCountdown`'a **dokunulmadı**; `HomeViewModel` ona artık ilerletilmiş
tarihi veriyor. Kartta ayrıca bir tarih metni yok — kartın gösterdiği şey geri
sayım, o da ilerletilmiş tarihe göre.

**"Gecikmiş" durumu ana ekranda artık oluşmuyor.** Sayılan tarih hiçbir zaman
geçmişte olmadığı için `Overdue` üretilemez; bugünden geriye kalan tek durum
"bugün ödenecek". Bu bir davranış **değişikliği değil, kararın sonucu** — 
promptun öngördüğü durum — o yüzden durup sorulmadı; `PaymentCountdown.Overdue`
kaldırılmadı, çünkü bildirim tarafı hâlâ çıpayı okuyor ve oradan gelebiliyor.

Çıpa hiçbir yerde kaybolmuyor: Faz 15'in düzenleme ekranı onu gösterecek
(ROADMAP'e madde olarak eklendi).

### Görev 3 — bildirim tarafı: okundu, ölçüldü, değiştirilmedi

**Kod:** `PaymentReminderSelection.on()` `subscription.nextPaymentDate`'i, yani
**çıpayı** okuyor. İlerletme yalnızca `HomeViewModel`'in geri sayım hesabında.

**Ölçüm (iki emülatörde de aynı):** enstrümantasyon paketi `am instrument` ile
koşuldu; yedi abonelikten dördü bildirime girdi ve **ikisi gecikme
penceresinden** geldi. Ham metin:

```
EXTRA_TITLE=[Payment reminder: 4 subscriptions]
EXTRA_TEXT =[DueToday — today, Tomorrow — tomorrow,
             OneDayLate — 1 day overdue, ThreeDaysLate — 3 days overdue]
```

Aynı satırlar için ekran başka bir şey diyor:

| satır | çıpa | bildirim | kart |
|---|---|---|---|
| DueToday | bugün | "today" | Due today |
| Tomorrow | +1 | "tomorrow" | 1 day left |
| TwoDaysOut | +2 | (yok) | 2 days left |
| **OneDayLate** | −1 | **"1 day overdue"** | **29 days left** |
| **ThreeDaysLate** | −3 | **"3 days overdue"** | **27 days left** |
| FourDaysLate | −4 | (yok) | 26 days left |
| NoDate | — | (yok) | (gösterge yok) |

**Sonuç: pencere ölü kod değil, hâlâ tetikleniyor** — ama gerekçesi düştü ve
artık ekranla aynı şeyi söylemiyor. Eşiklere dokunulmadı; seçenekler
`ARCHITECTURE.md` §18'e yazıldı, **karar sohbette**.

### Testler

- 176 → **197 birim testi** (21 yeni), 0 hata. `lintDebug` **0 hata, 20 uyarı**
  (`java.time` kullanıldığı için şart koşuldu).
- `NextPaymentDateTest` (13): gelecek/bugün dokunulmuyor · aylık ilerletme ·
  kısa ay kırpması · **kırpmanın kalıcı olmadığı** · dört aylık dizi · bugüne
  tam denk gelme · artık gün çıpası (2028-02-29) hem sıradan yılda kırpılıyor
  hem **sonraki artık yılda 29'a dönüyor** · on yıl geçmiş yıllık · haftalık ·
  **558 periyotluk** haftalık · ve hepsini kapsayan özellik testi (sonuç asla
  geçmişte değil, bir önceki periyot her zaman geçmişte).
- `HomeViewModelNextPaymentTest` (8): üç periyot, tarihsiz satır, gelecek,
  bugün, **saklanan tarihin değişmediği**, ve `Overdue`'nun artık üretilmediği.
- **Beklentisi değişen tek test:** `HomeViewModelTest.uiState_datedSubscriptions_carryTheirCountdown`
  — 4 gün geçmiş aylık satır için `Overdue(4)` diyordu, artık `Upcoming(27)`
  (11 Mart çıpası, 15 Mart'ta bakınca 11 Nisan). Fazın istediği davranış
  değişikliği; testin yanına gerekçesi yazıldı.

### Emülatör doğrulaması (cihaz tarihi 2026-09-13, iki AVD'de de aynı)

| | periyot | çıpa | beklenen | kart |
|---|---|---|---|---|
| (a) | Monthly | 2026-08-09 (−35g) | 2026-10-09, 26 gün | `26 days left` |
| (b) | Weekly | 2026-08-14 (−30g) | 2026-09-18, 5 gün | `5 days left` |
| (b) | Yearly | 2025-08-09 (−400g) | 2027-08-09, 330 gün | `330 days left` |
| (c) | Weekly | 2024-07-05 (−800g, **115 periyot**) | 2026-09-18, 5 gün | `5 days left` |
| (d) | Monthly | 2026-09-18 (gelecek) | dokunulmaz | `5 days left` |
| (e) | Monthly | bugün | dokunulmaz | `Due today` |

Hepsi elle hesapla birebir. **(c) donma yok:** kaydetten kartın görünmesine
kadar geçen süre 115 periyotluk satırda da 1 periyotluk satırdakiyle aynı
(~4,4 s, ölçümün tamamı uyku + dump); hesap zaten sabit sayıda işlem.

**Çıpa cihazda da yerinde duruyor.** 40 gün geçmiş bir çıpa kaydedildi, kart
`Anchored, TRY 12.00, Monthly, 21 days left` dedi; `run-as` ile okunan satır:

```
Anchored | MONTHLY | 1785801600000 -> 2026-08-04
```

### (g) Sabit regresyon listesi — 68 → 75 madde, ikisinde de koşuldu

12-2'nin yedi maddesi eklendi (69-75). 68 maddenin tamamı iki emülatörde
koşuldu. Kayıtlı istisnalar önceki fazlarla aynı (**API 29**: #15 koyu tema,
#16 dil; #34-#37 ve #41-#45 API 33+ maddeleri — karşılıkları #40 ve #46 koşuldu.
**API 34**: #40 ve #46 API < 33 maddeleri).

**#29 artık geçerli değil ve bilerek güncellenmedi.** "Geçmiş bir tarih seç →
kart 'gecikmiş' diyor, tarih ilerletilmiyor" maddesi iki emülatörde de yeni
davranışı gösterdi: 5 gün geçmiş aylık çıpa → **`Late, TRY 40.00, Monthly,
25 days left`**. Maddenin yerine ne yazılacağı §18'deki pencere kararıyla
birlikte verilmeli; TESTING.md'ye listenin altına bu ölçümü anlatan bir not
eklendi, satırın kendisine dokunulmadı.

### Değişen dosyalar

- `domain/usecase/NextPaymentDate.kt` — yeni
- `ui/home/HomeViewModel.kt` — geri sayım ilerletilmiş tarihe göre
- `test/.../NextPaymentDateTest.kt`, `test/.../HomeViewModelNextPaymentTest.kt` — yeni
- `test/.../HomeViewModelTest.kt` — bir beklenti (yukarıda)
- `docs/ARCHITECTURE.md` §17 ve §18, `docs/ROADMAP.md`, `docs/TESTING.md`

**Dokunulmayanlar:** `reminder/` (yalnızca okundu), `PaymentCountdown`,
`PaymentReminderSelection`, Room şeması, `ui/settings/`.

### Commit'ler

- `974fb50` feat: work out where a payment date has got to by today
- `e9a1135` feat: count towards the payment that is actually next

### Karşılaşılan sorunlar

- **Emülatör yavaşlığı ölçümü bozdu, ürün değil.** `am start` sonrası 6 saniye
  yetmediği için birkaç koşu "düğüm yok" diye düştü; `restart_app` artık saate
  değil, ekrandaki FAB'ın belirmesine bakıyor. Aynı sebepten #68 bir koşuda
  "yıllık görünüm kalmış" gibi göründü — 9 saniye beklenince doğru sonuç
  (`Total Monthly` ve `Monthly checked=true`, pid değişmiş) alındı.
- **uiautomator bayat ağaç döndürebiliyor.** Temizlik sonrası dump "Ayarlar
  ekranı" gösterdi; aynı anda alınan ekran görüntüsü ana ekranı gösteriyordu.
  TESTING.md'de kayıtlı tuzak, tekrar doğrulandı.
- **Tarih seçicideki "Save" ikilemi** (12-1'de yazılmıştı) yine çıktı; takvim
  hücresi aramak yerine **metin girişi** kullanan bir yardımcıya geçildi, artık
  tarihler cihazın kendi tarihinden hesaplanıyor (eski betikler sabit "10 Eylül"
  gibi günlere bakıyordu ve tarih ilerleyince bozuluyordu).

### Rapor edilen, karar bekleyen

> **Kapandı** — ikisi de yukarıdaki **12-2 hotfix** kaydında çözüldü.

- **Gecikme penceresi** (aşağıdaki ölçüm). Bildirim çıpayı, ekran ilerletilmiş
  tarihi okuyor; ikisi aynı satır için farklı şey söylüyor.
- **#29** maddesinin yeni metni.

### Sonraki faz için not

- Faz 15 düzenleme ekranı **çıpayı** göstermeli (ROADMAP'te madde var).
- `PaymentCountdown.Overdue` artık yalnızca bildirim yolundan gelebiliyor; §18
  kararı onu tamamen kaldırırsa kartlardaki `error` rengi kullanımı da gözden
  geçirilmeli (Faz 14 renk borcu). *(12-2 hotfix: kaldırıldı. `error` rolü
  `SwipeToDeleteRow`'da kullanılmaya devam ettiği için borç duruyor.)*

---

## [Faz 12-1 hotfix] Normalizasyon ara değeri BigInteger'a taşındı — 2026-09-06

**Durum:** Tamamlandı. Kullanıcıya dönük hiçbir şey değişmedi — ne bir sayı, ne
bir sınır, ne bir imza.

**Sorun (12-1 doğrulamasında ölçülmüştü)**
Haftalık normalizasyon payı 52 ile çarpıyor: `fiyat × paymentsPerYear × kur`
uygulamadaki en geniş değer. Long'da, fiyat tavanı (1.000.000 birim) ve kur
tavanı (1.000,0000) birlikte, satırların hepsi haftalıkken **177 satırdan**
sonra taşıyordu; normalizasyondan önceki sınır **9.223**'tü. Bugünkü hiçbir
liste oraya yaklaşmıyor, ama pay bu fazda daraldığı için bu fazda kapatıldı.

**Yapılan**
`CurrencyConverter.total(...)` zincirinin ara değeri `BigInteger`:

```
Σ(fiyat × paymentsPerYear) × kaynakKuru / (hedefKuru × parça)
```

Ağırlıklı toplam `BigInteger.ZERO` üzerinden katlanıyor, kur çarpımı ve bölme
de aynı tipte; sonuç tek noktada HALF_UP ile yuvarlanıp `toLong()` ile
`Money`'ye dönüyor. `divideHalfUp`'ın `BigInteger` sürümü Long sürümünün
yanına eklendi (`convert` tek bir tutara bakıyor, orada genişliğe ihtiyaç yok
ve iki `BigInteger` ayırmak boşuna olurdu). `BigInteger.TWO` API 31, minSdk 24
— sabit kendi companion'ımızda.

**Neden davranış değişmiyor:** `BigInteger` de tam sayı bölmesini **sıfıra
doğru kırpıyor**, tıpkı Long gibi. Bölmeden önce paydanın yarısını eklemek bu
yüzden iki tipte de aynı yuvarlamayı veriyor. Tek yuvarlama noktası kuralı
(ARCHITECTURE §6) aynen duruyor.

**Değişmeyenler (bilerek)**
- `MAX_PRICE` (1.000.000 birim) ve `ExchangeRateTable.MAX_RATE` (1.000,0000).
- `CurrencyConverter`'ın public imzaları; `Money` hâlâ `Long` kuruş (§6).
- UI'ın tamamı — bu bir hesap değişikliği.

**Yeni sınır — ara değerin tavanı kalmadı, cevabın tavanı kaldı**
`Money` bir `Long` olduğu için sınır artık **sonucun sığması**. Aynı tavanlarda,
çapaya çevirirken haftalık bir satır ayda 433.333.333.333 kuruş:

| | eski (Long ara değer) | yeni (BigInteger ara değer) |
|---|---|---|
| Aylık görünüm | **177 satır** | **21.284.704 satır** |
| Yıllık görünüm | 177 satır | **1.773.725 satır** |
| Normalizasyon öncesi referans | 9.223 satır | — |

Yani pratikte kalan sınır, listenin bellekte tutulup `LazyColumn` ile çizildiği
bir uygulamanın göremeyeceği yerde: 21 milyon satır. İki sayı da teste
sabitlendi.

**Testler**
- 174 → **176 birim testi** (2 yeni), 0 hata. `lintDebug` **0 hata, 20 uyarı**.
- **12-1'deki 14 normalizasyon testinin hiçbirinin beklentisi değişmedi.**
  Tek dokunulan yer `weeklyNormalisation_costsHeadroom_andWhatIsLeftIsPinned`
  testinin KDoc'u: 9.223 ve 177 assertion'ları aynen duruyor (ikisi de Long
  hakkında doğru olgular ve bu tercihin gerekçesi), ama artık dönüştürücünün
  sınırı olmadıkları yazıldı.
- Yeni: `farPastTheOldLongCeiling_theTotalIsStillExact` — 500 satır, hepsi
  tavan fiyatta, haftalık, kur tavanında. Önce çarpımın Long'a **sığmadığı**
  gösteriliyor (2,6 × 10¹⁹ > 9,22 × 10¹⁸), sonra sonucun kuruşu kuruşuna
  doğru olduğu: **216.666.666.666.667 kuruş**.
- Yeni: `whatIsLeftIsTheAnswerHavingToFitInMoney` — 21.284.704 ve 1.773.725.

**Emülatör (yalnızca (c) ve (d) ile 11. madde, iki AVD'de)**

| | API 29 | API 34 |
|---|---|---|
| (c) 100 aylık + 1.200 yıllık + 10 haftalık | `Total Monthly, TRY 243.33` | `Total Monthly, TRY 243.33` |
| (d) yıllık görünüm | `Total Yearly, TRY 2,920.00` | `Total Yearly, TRY 2,920.00` |
| 11. 159,99 + 59,90 | `Total Monthly, TRY 219.89` | `Total Monthly, TRY 219.89` |

Üç sayı da hotfix öncesiyle **birebir aynı**. Tam regresyon koşulmadı: UI
değişmedi, hesap testlerle kapalı.

**Değişen dosyalar**
- `domain/usecase/CurrencyConverter.kt` — ara değer `BigInteger`, ikinci
  `divideHalfUp`
- `domain/model/ExchangeRateTable.kt` — `MAX_RATE`'in KDoc'u artık doğru sınırı
  anlatıyor (eski 9.223 gerekçesi tarih olarak duruyor)
- `test/.../PeriodNormalisationTest.kt` — iki yeni test, bir KDoc
- `docs/ARCHITECTURE.md` §6

**Commit**
- `a5cc0a4` fix: give the totals room the ceilings cannot use up

---

## [Faz 12-1] Ödeme Periyodu: seçim, normalizasyon, toplam görünümü — 2026-09-06

**Durum:** Tamamlandı. **Faz 12 KAPANMADI** — tarih ilerletme ve hatırlatma
penceresi 12-2'nin işi, ROADMAP'teki o iki madde açık bırakıldı.

### Görev 1 — mevcut durumun tespiti (kod yazmadan önce)

**a) `BillingPeriod`** üç sabit taşıyordu, başka hiçbir şey yoktu:
`MONTHLY`, `YEARLY`, `WEEKLY`. UI'a hiç bağlanmamıştı;
`HomeViewModel.save()` her satıra koşulsuz `BillingPeriod.MONTHLY` yazıyordu.

**b) Veritabanındaki değer — iki emülatörde de `MONTHLY`.** Üç kayıt
oluşturulup `run-as` ile `subtrack.db` (+ `-wal`) çekildi ve okundu:

```
(1, 'Alpha',  10000, 'TRY', 'MONTHLY', 'OTHER')
(2, 'Beta',  120000, 'TRY', 'MONTHLY', 'HEALTH')
(3, 'Gamma',   1000, 'TRY', 'MONTHLY', 'OTHER')
group by billingPeriod -> [('MONTHLY', 3)]
```

Yani mevcut toplamların anlamı değişmiyor: bugüne kadar yazılmış her satır
zaten aylık. Durup sormayı gerektiren bir şey çıkmadı.

> Tuzak: `subtrack.db` tek başına çekilirse **boş** görünür, kayıtlar WAL
> dosyasındadır. `force-stop` checkpoint yapmıyor; `-wal` ve `-shm` de
> çekilmeli (ve `exec-out` ile, `shell` ikiliyi bozuyor).

**c) Taşma hesabı — taşmıyor, ama pay 52 kat daraldı.** Yeni paydaki en geniş
değer `fiyat × paymentsPerYear × kur`:

| | en geniş satır | Long'a sığan satır sayısı |
|---|---|---|
| Normalizasyon öncesi | 10^8 × 10^7 = 1,0 × 10^15 | **9.223** |
| Haftalık normalizasyonla | 10^8 × 52 × 10^7 = 5,2 × 10^16 | **177** |
| Aynısı, uygulamanın kendi kurlarıyla (≤ 53,90) | 2,8 × 10^15 | **3.290** |

`Long.MAX_VALUE = 9,223 × 10^18`. Payda `hedefKuru × 12 ≤ 1,2 × 10^8`, eklenen
yarım `6 × 10^7` — bu bandı değiştirmiyor. Yani **hiçbir tavan
değiştirilmedi**; sınır `PeriodNormalisationTest`'te sabitlendi (9.223 ve 177
birlikte).

177 satırın hepsinin aynı anda tavan fiyatta (1.000.000 birim), haftalık ve
kullanıcının 1.000,0000'e çektiği bir kurda olması gerekiyor. Gerçek bir
listede ulaşılmaz; yine de eski payın yüzde ikisi olduğu için buraya yazıldı.
Payı geri istemenin üç yolu var (fiyat tavanını indirmek, kur tavanını
indirmek, ara değeri `BigInteger`'a taşımak) ve üçü de ürün/mimari kararı —
kendi başıma almadım.

### Normalizasyon

- `BillingPeriod(paymentsPerYear)` — aylık 12, yıllık 1, haftalık 52.
  Fiyatı bununla **çarpmak** yıllık maliyeti verir ve çarpma tamdır.
- `TotalPeriod(partsOfAYear)` — görünüm için ayrı enum (MONTHLY 12, YEARLY 1).
  `BillingPeriod` kullanılmadı: haftalık bir *görünüm* yok, olmayan bir cevabı
  temsil eden sabit taşımak istemedim.
- `CurrencyConverter.totalIn(subs, target, period)` — **yeni aşırı yükleme**,
  mevcut iki parametreli imzaya dokunulmadı (prompt öyle istiyordu). İkisi de
  aynı özel gövdeyi çağırıyor: satırları ağırlıklandır, para birimine göre
  grupla, **tek bölmede** çevir ve böl.
- **52 hafta** kararı ve **tek yuvarlama noktası** kuralı gerekçeleriyle
  `ARCHITECTURE.md` §6'ya yazıldı.

**İki parametreli `totalIn` neden duruyor:** artık üründe çağıran yok. Silmek
imza değiştirmekten büyük bir adım olurdu ve `CurrencyConverterTest`'teki
gruplama/yuvarlama sözleşmesini (aynı özel gövdeyi koruyan testler) götürürdü.
KDoc'una "bu fiyatları toplar, maliyetleri değil; periyotlar karışıksa çıkan
sayının karşılığı yoktur" diye açıkça yazıldı.

### Form

Periyot sırası **para biriminin altına**, kategorinin üstüne kondu: para birimi
ve periyot ikisi de üstteki fiyatın ne demek olduğunu söylüyor (neyle, ne
sıklıkta), kategori ise formda hiçbir şeyi değiştirmiyor ve tarih diyalog açıp
sırayı bitiriyor.

`FlowRow` seçildi (kategorideki gibi), kaydırma değil: form zaten kaydırılıyor,
ikinci satırın kalıcı bir bedeli yok — filtre çubuğundaki gerekçe (liste üstünde
sonsuza kadar duran şerit) burada geçerli değil. **Sığdı, sarma gerekmedi:**

| | chip genişlikleri | toplam | kullanılabilir |
|---|---|---|---|
| API 29 (360dp) | 168 + 142 + 156 | 466 px + boşluklar | 624 px |
| API 34 (411dp) | 217 + 186 + 202 | 605 px + boşluklar | 984 px |

State sheet'in kendi `rememberSaveable`'ında (ARCHITECTURE §5, 11b'de
netleşti); `BillingPeriodSaver`, `CategorySaver`'ın deseni.

### Kartta gösterim

**Adın altında ayrı bir satır, her kartta.** Fiyatın yanına sonek olarak
yazılmadı: fiyat sağdaki sütunda, ad kalanı alıyor; fiyatı uzatmak adın
sütununu daraltırdı ve **fs 2.0'da ad kırpılması zaten bilinen sorun**
(Faz 14). Satır yükseklik harcıyor, kartın fazlası olan şey o.

Kategoriden farklı olarak **koşulsuz**: `OTHER` "cevap yok" demekti, ama
işaretsiz bir kart aylık mı yoksa işaretlenmemiş mi belli olmaz ve parada
örtük kural olmaz.

**Ölçüm — periyot satırı bir `bodySmall` satırı kadar yer alıyor, yatayda
hiçbir şey almıyor:**

| | 1 satır (12-1 öncesi) | 2 satır (ad + periyot) | 3 satır (+ kategori) |
|---|---|---|---|
| API 29 fs 1.0 | 144 px | **166 px** | **198 px** |
| API 29 fs 2.0 | 162 px | **226 px** | **290 px** |
| API 34 fs 1.0 | 189 px | **217 px** | **259 px** |
| API 34 fs 2.0 | 212 px | **296 px** | **380 px** |

fs 2.0'da her satır API 29'da 64 px, API 34'te 84 px ekliyor; ilk ek satır
fs 1.0'da daha ucuz (28/22 px) çünkü ikonun asgari yüksekliği bir kısmını
zaten içeriyordu. **Kırpılma yok, kart uzuyor ve liste kaydırılıyor.** Adın
yatay alanı değişmedi: fiyat metni aynı, `weight(1f)` dağılımı aynı.

Kart hâlâ **tek erişilebilirlik düğümü**; okunan cümle artık
`"Delta, TRY 55.00, Monthly, Health"`.

**Okunan cümle tek formata indi.** `subscription_row_description_dated`
silindi, `..._with_category` → `..._more` diye yeniden adlandırıldı: cümle
artık "bir bilgi daha ekle" formatıyla adım adım kuruluyor (ad+tutar → periyot
→ geri sayım → kategori). Dört kombinasyon dört çeviri isteyecekti.

### Dashboard

Kartın **altında** iki chip (Aylık / Yıllık). İçine konamaz: kart
`clearAndSetSemantics` ile tek odak durağı, çocukları ağaçtan düşüyor —
içerideki bir chip erişilemez olurdu.

Başlık görünümle değişiyor (`total_monthly` / `total_yearly`) ve tutarla
birlikte tek cümle olarak okunuyor: `"Total Yearly, TRY 2,920.00"` — 8a'daki
tek odak durağı bozulmadı.

Görünüm state'i ViewModel'da (sayıyı değiştiriyor, §5), **kalıcı değil**;
filtre kararıyla aynı çizgide.

### Testler

- 149 → **174 birim testi** (25 yeni), 0 hata. `lintDebug` **0 hata, 20 uyarı**
  — sayı değişmedi.
- `PeriodNormalisationTest` (14 test): yıllık ÷ 12 HALF_UP (1.199,00 → 99,92),
  haftalık × 52 ÷ 12 (10,00 → 43,33), karışık liste (243,33), yıllık görünümün
  tamlığı (292.000 kuruş), aylık × 12 ile yıllık arasındaki farkın sınırı
  (≤ 6 kuruş), **tek yuvarlama kanıtı** ve tavanlar.
- **Tek yuvarlama kanıtı:** haftalık 10,00 USD → TRY.
  Birlikte `1.000 × 52 × 428.500 / (10.000 × 12)` = **185.683** kuruş;
  ayrı ayrı `52.000/12 = 4.333` sonra `× 42,85` = **185.669**. Fark **14
  kuruş**, test ikisini yan yana gösteriyor.
- `HomeViewModelBillingPeriodTest` (4) ve `HomeViewModelTotalPeriodTest` (7):
  kaydedilen periyot, varsayılan, görünüm değişimi, filtreyle birlikte çalışma.
- Fake kullanıldı, mock yok (§11).

### Emülatör doğrulaması

**(b) Seçici** — yukarıdaki tabloda; iki AVD'de de **tek satır**.

**(c) Toplam — elle hesapla birebir aynı.** 100,00 aylık + 1.200,00 yıllık +
10,00 haftalık:

```
beklenen: 100,00 + 100,00 + 43,33 = 243,33
ekranda : "Total Monthly, TRY 243.33"   (API 29 ve API 34)
```

**(d) Yıllık görünüm:** `"Total Yearly, TRY 2,920.00"` (iki AVD'de de).
Aylık figürün 12 katı **2.919,96**; fark **4 kuruş**. Sebep tek yuvarlama:
gerçek yıllık maliyet 292.000 kuruş, aylık figür onun yuvarlanmış on ikide
biri (24.333). Ekranda gösterilen, yuvarlanmış sayının katı değil, bölünmemiş
ara değerin kendisi — yani doğru olanı.

**(e) Kartlar:** `"Alpha, TRY 100.00, Monthly"` · `"Beta, TRY 1,200.00, Yearly"`
· `"Gamma, TRY 10.00, Weekly"`.

**(f)** Yukarıdaki yükseklik tablosu.

**(g) Filtre + görünüm birlikte:** Sağlık seçiliyken aylık **55,00**, yıllık
**660,00** (= 55 × 12), "Tümü"ye dönünce aylık **298,33**. İki AVD'de de aynı.

**(h)** Haftalık seçilip kaydedildikten sonra form yeniden açıldığında
**Aylık** seçili geliyor; seçim yapılmışken döndürüldüğünde korunuyor
(API 29 ve API 34).

**(i) Erişilebilirlik:** dashboard tek düğüm
(`[42,338][1038,633] "Total Monthly, TRY 243.33"`), kart tek düğüm, toggle
chip'leri `checkable=true` ve seçili olan `checked=true`, her biri tek odak
durağı.

**(j) Sabit regresyon listesi — 61 → 68 madde, ikisinde de koşuldu.** 12-1'in
yedi maddesi eklendi (62-68). 61 maddenin tamamı iki emülatörde koşuldu ve
geçti; kayıtlı istisnalar 11b'dekilerle aynı (**API 29**: #15 koyu tema,
#16 dil — API 33 öncesi `cmd locale` yok; #34-#37 ve #41-#45 API 33+ maddeleri,
karşılıkları #40 ve #46 koşuldu. **API 34**: #40 ve #46 API < 33 maddeleri).
#39 için kanal yine `am instrument` ile yaratıldı.

**(k)** İki emülatör de silinip yeniden kuruldu, ayarlar geri alındı, ikisi de
temiz boş durumla açılıyor.

### Değişen dosyalar

- `domain/model/BillingPeriod.kt` — `paymentsPerYear`
- `domain/model/TotalPeriod.kt` — yeni
- `domain/usecase/CurrencyConverter.kt` — periyot alan `totalIn` aşırı yüklemesi
- `ui/common/BillingPeriodSelector.kt`, `ui/common/TotalPeriodToggle.kt` — yeni
- `ui/home/components/SubscriptionRowDescription.kt` — yeni (HomeScreen 300
  satırı geçmişti, okunan cümle oraya taşındı)
- `ui/home/HomeUiState.kt` (`monthlyTotal` → `total`, `totalPeriod`),
  `HomeViewModel.kt`, `HomeScreen.kt`, `HomeScreenPreviews.kt`
- `ui/home/components/AddSubscriptionSheet.kt`, `SubscriptionCard.kt`,
  `DashboardCard.kt`
- `res/values/strings.xml`, `res/values-en/strings.xml`
- `test/.../PeriodNormalisationTest.kt`, `HomeViewModelBillingPeriodTest.kt`,
  `HomeViewModelTotalPeriodTest.kt` — yeni; `HomeViewModelTest`,
  `HomeViewModelFilterTest` — alan adı güncellendi
- `docs/ARCHITECTURE.md` §6 ve §17, `docs/ROADMAP.md`, `docs/TESTING.md`

### Commit'ler

- `2cf0459` feat: total what subscriptions cost, not what their prices say
- `b1e1be7` feat: choose how often a subscription is billed
- `1626d45` feat: say on every card how often it is billed
- `94af319` feat: switch the total between a month and a year

### Karşılaşılan sorunlar

- **`HomeScreen.kt` 304 satıra çıktı** (sınır 300). Okunan satır cümlesi
  `SubscriptionRowDescription.kt`'ye taşındı, 269'a indi.
- **Tarih seçicinin onay düğmesi de "Save" diyor.** Diyalog kapanırken alınan
  dump'ta iki "Save" düğümü görünüyor ve yanlış olana dokunuluyor; 29. madde
  bu yüzden "kart yok" dedi. Üründe sorun yok, ölçümdeydi — TESTING.md'ye
  tuzak olarak yazıldı.
- **360dp'de form bir sıra uzadı**, kategori chip'leri ve tarih alanı açılışta
  ekranın altında kalıyor. Ürün açısından sorun değil (form kaydırılabilir,
  ikisi de opsiyonel) ama ölçümden önce kaydırmak gerekiyor; TESTING.md'ye
  yazıldı.

### Rapor edilen, düzeltilmedi

- **Taşma payı 9.223 → 177.** Yukarıda hesabıyla duruyor. Tavan
  değiştirilmedi, karar sohbetin.
- İki parametreli `totalIn` artık üründe çağrılmıyor (yukarıda gerekçesi).

### Sonraki faz için not

- **12-2:** tarih ilerletme + hatırlatma penceresi. §17'deki "kullanıcı
  seçmiyor" önkoşulu artık geçersiz; §18'deki 1-3 günlük pencerenin gerekçesi
  ilerletme gelince ortadan kalkıyor. İkisi birlikte ele alınmalı.
- Faz 14'te kart yeniden ele alınırken fs 2.0'da **üç satırlı** kart
  (ad + periyot + kategori) 290/380 px; tabloyu oradan al.

---

## [Faz 11b] Kategori Filtresi — 2026-09-06

**Durum:** Tamamlandı. **Faz 11 KAPANDI** (ROADMAP'teki dört madde de işaretli).

**Görev 0 — 11a'daki form state tutarsızlığı kapatıldı**
11a kategoriyi `HomeUiState.selectedCategory` + `HomeEvent.SelectCategory`
olarak tutuyordu; formun diğer dört alanı (ad, fiyat, para birimi, tarih)
sheet'in kendi `rememberSaveable`'ındaydı. Kategori sheet'e **indirildi**:

- `AddSubscriptionSheet` artık `rememberSaveable(stateSaver = CategorySaver)`
  ile kendi kategorisini tutuyor. `CategorySaver`, `CurrencySaver`'ın birebir
  deseni — enum adı saklanıyor, tanınmayan ad `OTHER`'a düşüyor.
- `onSave` beş parametreye çıktı; `HomeEvent.Save`'in `category` parametresi
  varsayılanı `OTHER` olarak kaldı, yani eski çağrılar bozulmadı.
- `HomeUiState.selectedCategory` ve `HomeEvent.SelectCategory` **kaldırıldı**.
- Kural `ARCHITECTURE.md` §5'e yazıldı: form alanı composable'da, listeyi veya
  toplamı değiştiren state ViewModel'da. Ayırt edici soru orada.

11a'nın kategori testleri silinmedi, **yeni yüzeye göre yazıldı**: artık
"seçim state'e yansıyor" değil, "kayıt doğru kategoriyle gidiyor" ölçülüyor
(4 test). Chip'in döndürmede korunması ve kayıttan sonra sıfırlanması sheet'in
`rememberSaveable`'ının işi ve cihazda ölçüldü (liste #54, #55).

**Filtre**
- `ui/common/CategoryFilterBar` — beş `FilterChip` ("Tümü" + dört kategori),
  `horizontalScroll`. **`FlowRow` değil:** dört chip 360dp'de zaten tek satıra
  sığmıyordu (11a: 788 px gerekiyor, 624 px var), beşincisi eklenince sarma her
  ekranda kalıcı olarak ikinci bir satır yerdi. Kaydırmanın bedeli yalnızca
  taşan chip'i isteyen kullanıcıya ait.
- Filtre `HomeUiState.categoryFilter`'da; `null` "seçim yok" demek, beşinci bir
  kategori değil. Kalıcı değil — DataStore'a yazılmıyor, süreç ölünce "Tümü".
- ViewModel'da liste **bir kez** süzülüyor, geri sayım ve toplam süzülmüş
  listeden türetiliyor. Yani filtre açıkken dashboard o kategoriyi gösteriyor;
  ayrı bir "kategori toplamı" göstergesi **eklenmedi** (gerekçe ROADMAP'te).
- `hasAnySubscriptions` eklendi: "hiç abonelik yok" ile "bu kategoride yok"
  ayrımını ekran bu alandan yapıyor.

**Boş durum varyantı**
`EmptyState`'in **imzası değişmedi**; `EmptyCategory()` sarmalayıcısı eklendi ve
`EmptySubscriptions()` ile aynı ikonu kullanıyor (`AutoMirrored.Filled.List`)
— iki durum aynı şeyin farklı pencereden görünüşü, ayıran şey metin. Faz 16'nın
daraltacağı ikon kümesi büyümedi.

**Testler**
- 143 → **149 birim testi** (yeni `HomeViewModelFilterTest` 9 test,
  `HomeViewModelCategoryTest` 7 → 4 teste yeniden yazıldı), 0 hata.
- `lintDebug` **0 hata, 20 uyarı** — sayı değişmedi.
- Enstrümantasyon: `PaymentReminderWorkerTest` iki emülatörde de `OK (2 tests)`
  (kanalı yaratmak için koşuldu, aşağıya bak).

**Sahte repository'de bulunan hata — üründe değil, testte**
Undo sıra testleri kırmızı geldi. Sebep: `FakeSubscriptionRepository.observeAll()`
eklenme sırasını döndürüyordu, DAO ise `ORDER BY createdAt DESC` ile ters
sırayı. Yani sahte, gerçek sözleşmeyi taklit etmiyordu. **Düzeltilen sahte
oldu, ürün değil** — fake artık `sortedByDescending { it.createdAt }` veriyor.

**Emülatör doğrulaması — iki AVD'de de tam**

| | API 29 (360dp) | API 34 (411dp) |
|---|---|---|
| Chip'ler tek satıra | sığmıyor, kaydırma gerekiyor | sığmıyor, "Diğer" `[1043,821][1080,947]`'de kırpık |
| Kaydırmadan sonra | beş chip de ulaşılabilir | "Diğer" tam görünür `[862,821][1038,947]` |
| Süzme | her kategori kendi satırını gösterdi | Diğer 10,00 · Sağlık 20,00 · Üretkenlik 30,00 · Eğlence 50,00 (Tümü 110,00) |

- **Boş kategori:** `"No subscriptions in this category, Try another category"`
  — ilk boş durum `"No subscriptions yet, Tap + to add one"`. İkisi de tek
  erişilebilirlik düğümü ve ikisi de `[84,1052][996,1417]` kutusunda.
- **Depo boşken** bir kategori seçilirse yine **ilk** boş durum çıkıyor
  ("...yet, Tap + to add one"), yani `hasAnySubscriptions` dalı cihazda da
  doğru — `uiState_storeIsEmpty_saysSoRegardlessOfTheFilter` testiyle aynı
  sonuç.
- **Sil + geri al (filtre açık):** Üretkenlik seçiliyken tek satır silindi →
  filtre boş durumu + Snackbar; "Geri al" satırı geri getirdi, filtre
  Üretkenlik'te kaldı, toplam 30,00'a döndü. "Tümü"de sıra da eski hâlinde.
- **Döndürme:** filtre korunuyor. Yazı tipi ölçeği değiştirilince (yapılandırma
  değişikliği) hem filtre hem **çubuğun kaydırma konumu** korunuyor.
- **Yeniden başlatma:** "Tümü"ye dönüyor (kalıcı değil, beklenen).
- **fs 2.0:** chip'ler sarmıyor, metin kırpılmıyor ("Entertainment"
  `[271,938][711,1036]`), çubuk sonuna kadar kayıyor ve "Diğer" seçilip
  süzebiliyor. Chip yüksekliği 126 px.
- **Koyu tema:** filtre çubuğu okunur, seçili chip `primaryContainer` zemininde,
  seçilmeyenler çerçeveyle ayrışıyor; ekran görüntüsü alındı.
- **Türkçe:** çubuk `Tümü / Eğlence / Üretkenlik / Sağlık / Diğer` diye geliyor
  (API 34, `cmd locale set-app-locales`).

**Erişilebilirlik — chip başına tek durak**
Filtre chip'i ağaçta bir `View` (odaklanabilir, `checkable=true`, seçiliyse
`checked=true`) ve içinde odaklanamayan iki çocuk: etiket `TextView` ve bir
`CheckBox`. Yani çubuk **beş** durak ekliyor, chip başına bir tane; fazladan
veya etiketsiz düğüm yok. Bu, formdaki kategori ve para birimi chip'lerinin
yapısıyla aynı (stok `FilterChip`).

**Sabit regresyon listesi — 55 → 61 madde, ikisinde de koşuldu**
11b'nin altı maddesi eklendi (56-61). Liste **iki emülatörde de baştan sona**
koşuldu ve geçti. Kayıtlı istisnalar:

- **API 34:** #40 ve #46 yalnızca API < 33 maddeleri.
- **API 29:** #15 ölçülemedi — `cmd uimode night yes` bu imajda "Night mode: no"
  dönüyor, tema değişmiyor. #16 ölçülemedi — `cmd locale` servisi API 33
  öncesinde **yok** ("Can't find service: locale"); cihaz dilini değiştirmek
  çerçeve yeniden başlatması istiyor. Filtre çubuğunun Türkçesi bu yüzden API
  34'te doğrulandı. #34-#37 ve #41-#45 API 33+ davranışları; karşılıkları #40
  ve #46 koşuldu ve geçti.
- #38 ve #39 **iki AVD'de de** koşuldu: sistem ayarından bildirim kapatılıp
  açıldığında satır aynı süreçte (pid değişmeden) güncelleniyor. #39 için kanal
  gerekiyor — kanal ilk bildirimle doğduğu için `PaymentReminderWorkerTest`
  `am instrument` ile koşuldu; kanal kapatılınca satır "Kapalı — sistem
  ayarlarından açılmalı" dedi, uygulama izni açık olsa bile.

**Değişen dosyalar**
- `ui/common/CategoryFilterBar.kt` — yeni
- `ui/common/EmptyState.kt` — `EmptyCategory()` eklendi, `EmptyState` imzası aynı
- `ui/home/HomeUiState.kt`, `HomeViewModel.kt`, `HomeScreen.kt`
- `ui/home/components/AddSubscriptionSheet.kt` — kategori + `CategorySaver`
- `res/values/strings.xml`, `res/values-en/strings.xml`
- `test/fake/FakeSubscriptionRepository.kt` — DAO gibi sıralıyor
- `test/ui/home/HomeViewModelFilterTest.kt` — yeni
- `test/ui/home/HomeViewModelCategoryTest.kt` — yeni yüzeye göre yazıldı
- `docs/ARCHITECTURE.md` §5, `docs/ROADMAP.md`, `docs/TESTING.md`

**Commit'ler**
- `133a373` refactor: let the add form own the category like its other fields
- `768ce32` feat: narrow the list to one category
- `5e7c173` feat: say when a category has nothing in it
- `6d61411` test: cover the category filter and make the fake sort like the DAO

**Karşılaşılan sorunlar**
- **Sahte repository sıralamayı taklit etmiyordu** (yukarıda). Ders: bir fake
  sözleşmeyi taklit ediyorsa, sözleşmenin sırası da sözleşmenin parçasıdır.
- **`input swipe ... 400` bazen silmiyor.** Aynı komut emülatör yeniden
  başlatıldıktan sonra üç kez üst üste hiçbir şey yapmadı, 700 ms ile her
  seferinde sildi. TESTING.md'ye tuzak olarak yazıldı.
- **`connectedDebugAndroidTest` uygulamayı siliyor.** Gradle koşum sonunda hem
  test hem uygulama APK'sını kaldırıyor; kanalı yaratmak için koşulan test,
  ölçülecek kurulumu da götürdü. Çözüm: APK'ları elle kurup `am instrument`
  çağırmak.
- **`pm revoke` kalıcı reddi taklit etmiyor.** Revoke sonrası satır yine
  "Kapalı — açmak için dokunun" diyor; **kalıcı ret** ancak gerçek sistem
  diyaloğunda iki kez reddedilerek üretilebiliyor. (10c-1'deki "pm revoke
  süreci öldürüyor" notunun yanına.)

**Rapor edilen, düzeltilmeyen**
- Depoda hiç abonelik yokken filtre çubuğu **görünmeye devam ediyor**. Promptta
  gizlenmesi istenmedi, kapsam dışı bırakıldı; davranış zararsız çünkü boş
  depoda hangi chip seçilirse seçilsin ekran "Henüz abonelik yok" diyor.
  Faz 14'te ekran yeniden ele alınırken karara bağlanabilir.

**Sonraki faz için not**
- Faz 12 ödeme periyodunu getirince toplam "aylık normalize" olacak; filtre
  süzmeyi ondan **önce** yapıyor, yani sıra değişmemeli ama `totalIn`
  değiştiğinde `HomeViewModelFilterTest`'teki toplam beklentileri gözden
  geçirilmeli.
- Filtre kalıcı yapılmak istenirse yeri `SettingsRepository` değil, ekranın
  kendi `SavedStateHandle`'ı olur — bugünkü karar "kalıcı değil".

---

## [Faz 11a] Kategori Seçimi ve Gösterimi — 2026-09-05

**Durum:** Tamamlandı. **Faz 11 KAPANMADI** — filtre ve kategori bazlı toplam
11b'nin işi, ROADMAP'teki o maddeler açık bırakıldı.

**Domain kontrolü — enum spec'le uyuşuyor, dokunulmadı**
`SubscriptionCategory` dört sabit taşıyor: `ENTERTAINMENT`, `PRODUCTIVITY`,
`HEALTH`, `OTHER`. PROJECT_SPEC §4'teki "Eğlence / Üretkenlik / Sağlık / Diğer"
ile birebir. Durup sormayı gerektiren bir fark yok. Mapper'daki `OTHER`
fallback'ine (ARCHITECTURE §12) dokunulmadı; Room şeması ve `app/schemas/`
değişmedi.

> Not: prompt "beş chip" diyordu, kategori **dört** tane.

**Yapılanlar**
- `ui/common/CategorySelector` — `CurrencySelector`'ın deseni: `FlowRow` +
  `FilterChip`, tek dokunuş. Para birimi chip'lerindeki `contentDescription`
  ezmesi burada **yok**, çünkü o ezme ISO kodunun ("TRY") kelime gibi
  okunmasını düzeltmek içindi; kategori chip'i zaten anlamı olan kelimeyi
  gösteriyor.
- `SubscriptionCategory.labelRes()` — exhaustive `when`, UI katmanında.
  Enum İngilizce kalıyor ve ekrana hiç çıkmıyor; domain'e string resource
  ID'si sokulmadı.
- `HomeUiState.selectedCategory` + `HomeEvent.SelectCategory`. Kayıtta
  repository'ye gidiyor; `clearedErrors` içinde `OTHER`'a dönüyor, yani
  açılış, vazgeçme ve başarılı kayıt üçü de formu sıfırlıyor.
- `SubscriptionCard`'da kategori satırı, **yalnızca `OTHER` değilse**.

**Formdaki yeri — para birimi chip'lerinin hemen altı**
Gerekçe: iki chip sırası formdaki tek "seçim" öğesi, bir arada okunmaları
dağınık durmalarından iyi. Para biriminden **sonra**, çünkü para birimi
üstündeki fiyatın anlamını değiştiriyor; kategori formda başka hiçbir şeyi
değiştirmiyor. Tarihten **önce**, çünkü tarih bir diyalog açıp sırayı bitiriyor.

**Kartta gösterim kararı**
Kart bugün ad, fiyat ve geri sayım taşıyor; dördüncü öğe sıkışıklık riski.
Seçilen çözüm: **adın altında ayrı bir satır, yalnızca kategori `OTHER`
değilken**. Üç gerekçe:

1. Dikey bir satır adın **yatay** alanından hiçbir şey almıyor — fs 2.0'daki
   bilinen ad kırpılması bu yüzden kötüleşemez.
2. `OTHER` "cevap yok" demek; her satıra yazmak listenin tamamında anlamsız
   bir kelime tekrarı olurdu.
3. Aynı kural geri sayımda zaten var: "tarihi olmayan satırlar eski
   yüksekliğini korur, olmayan bir şey için yer ayırmaz".

Renk `onSurface`. `onSurfaceVariant` **bilerek kullanılmadı** — tanımsız ve
Material baseline'ının mor-grisine düşüyor (ARCHITECTURE §12). Hiyerarşiyi
punto farkı taşıyor.

**Erişilebilirlik cümlesi**
Kategori okunan cümleye de eklendi, yoksa duyurulmazdı (8a dersi). Dört ayrı
string yerine iki adımda kuruluyor: ad/fiyat (+ varsa geri sayım) cümleyi
yapıyor, sonra kategori varsa `subscription_row_description_with_category` ile
ekleniyor. Dört kombinasyon dört çeviri gerektirirdi.

**Testler**
- 136 → **143 birim testi** (7 yeni), 0 hata.
- Kapsanan: seçim state'e yansıyor · kayıt doğru kategoriyle gidiyor ·
  seçilmezse `OTHER` · kayıttan sonra `OTHER` · vazgeçmeden sonra `OTHER` ·
  reddedilen kayıtta seçim korunuyor.
- Mapper'ın enum dönüşümü **tekrar edilmedi**: `SubscriptionMapperTest` iki
  yönü ve tanınmayan isim fallback'ini zaten kapsıyor (kontrol edildi).
- `lintDebug` **0 hata, 20 uyarı** — sayı değişmedi.

**Emülatör sonuçları**

**(a) Chip'ler ve yerleşim** — dört chip **tek satıra sığmıyor**, `FlowRow`
sardı. Yatay kaydırma gerekmedi, kırpılma yok.

| | API 29 (360dp) | API 34 (411dp) |
|---|---|---|
| 1. satır | Eğlence `[48,767][291,863]`, Üretkenlik `[307,767][523,863]` | Eğlence `[63,1507][379,1633]`, Üretkenlik `[400,…]`, Sağlık `[702,…][894,1633]` |
| 2. satır | Sağlık `[48,879][195,975]`, Diğer `[211,879][345,975]` | Diğer `[63,1654][239,1780]` |

Tek satır gerekseydi API 29'da 788 px lazımdı, kullanılabilir genişlik 624 px.

**(b)** Eğlence seçilip kaydedildi → kart: **`"Netflix, TRY 159.99, Entertainment"`**
**(c)** Dokunmadan kaydedildi → kart: **`"Plain, TRY 20.00"`** (kategori yok)
**(d)** Kayıttan sonra FAB → `Diğer = checked`, diğer üçü `false`, alanlar boş
**(e)** Seçiliyken döndürüldü → seçim korundu (API 29 Eğlence, API 34 Sağlık)

**(f) fs 2.0 karşılaştırması** — aynı dump içinde kategorisiz satır referans:

| | kategorisiz | kategorili |
|---|---|---|
| API 29 fs 1.0 | 144 px | 166 px |
| API 29 fs 2.0 | 162 px | 290 px |
| API 34 fs 1.0 | 189 px | 217 px |
| API 34 fs 2.0 | 212 px | 380 px |

Kategorili satır fs 2.0'da belirgin biçimde uzuyor. Bu **büyüme**, kırpılma
değil: kart uzuyor, liste kaydırılıyor, hiçbir şey kesilmiyor.
**Yatay kırpılma ölçülemedi** — kart tek birleşik erişilebilirlik düğümü
olduğu için içindeki ad kutusunun sınırları ağaçta yok. Kategorinin adın
yatay alanını almadığı **kod düzeyinde kesin** (aynı `Column` içinde ayrı bir
`Text`, `Row`'daki `weight(1f)` dağılımı değişmiyor), ama bu bir ölçüm değil.

**(g) Erişilebilirlik**
- Chip'ler `checkable="true"`; seçili olan `checked="true"`. Para birimi
  chip'leriyle aynı yapı (9b-2'de doğrulanmıştı).
- Kart hâlâ **tek düğüm** — 8a'daki tek odak durağı bozulmadı. API 29
  `[0,560][720,726]`, API 34 `[0,989][1080,1206]`.
- Dört parçalı cümle de doğrulandı: **`"Dated, TRY 30.00, 6 days left, Health"`**

**(h) Klavye açıkken Kaydet** — yeni alan Kaydet'i itmedi:

| | klavye açık | klavye kapalı |
|---|---|---|
| API 29 | `[329,634][391,674]` | buton kutusu `[48,1104][672,1200]` — **10a referansıyla aynı** |
| API 34 | `[500,1323][580,1376]` | `[500,2143][580,2196]` — 10c-2 ölçümüyle aynı |

**(i) Regresyon — 55 maddelik liste**
Sabit listeye 11a'nın beş maddesi eklendi (51-55), liste 50 → **55**.
50 maddenin tamamı iki emülatörde koşuldu ve geçti. Kayıtlı istisnalar:
**#15/#16** API 29'da ölçülemiyor (API 34'te geçti); **#34-#37, #41-#45**
API 33+ davranışları, API 29'daki karşılığı **#46** ve o geçti; **#40**
yalnızca API < 33 maddesi.

**Değişen dosyalar**
- `ui/common/CategorySelector.kt` — yeni
- `ui/home/HomeUiState.kt`, `HomeViewModel.kt`, `HomeScreen.kt`
- `ui/home/components/AddSubscriptionSheet.kt`, `SubscriptionCard.kt`
- `res/values/strings.xml`, `res/values-en/strings.xml`
- `test/ui/home/HomeViewModelCategoryTest.kt` — yeni
- `docs/ROADMAP.md`, `docs/TESTING.md`

**Bir tutarsızlık — raporlanıyor, düzeltilmedi**
Prompt kategoriyi `HomeUiState` + `HomeEvent` üzerinden istedi ve öyle yapıldı.
Ama formun diğer alanları (ad, fiyat, para birimi, tarih) sheet'in kendi
`rememberSaveable` state'inde duruyor — sheet'in KDoc'u bunu "geçici görsel
durum, ARCHITECTURE §3 izin veriyor" diye gerekçelendiriyor. Yani kategori
kardeşlerinden farklı bir yerde yaşıyor.

İhlal değil (ViewModel her zaman state tutabilir) ve pratikte iki avantajı
oldu: döndürmede seçim bedava korunuyor ve sıfırlama tek yerde. Ama beş form
alanından dördü bir yerde, biri başka yerde. Para birimi ve tarihi de
ViewModel'a taşımak mı, kategoriyi sheet'e indirmek mi — karar sohbetin.

**Sonraki faz için not**
- 11b: filtre, kategori bazlı toplam, filtre boşluğu (`EmptyState` varyantı).
- Faz 14'te kart yeniden ele alınırken fs 2.0'da kategorili satırın yüksekliği
  göz önünde bulundurulmalı; bugün kırpılma yok ama satır iki katına çıkıyor.

---

## [Faz 8b] Boş Durum Ekranı — 2026-09-05

**Durum:** Tamamlandı. **Faz 8'in tamamı kapandı.**

**Yapılanlar**
- `ui/common/EmptyState` — ikon, başlık ve alt satırı **parametre** alan bir
  composable. Faz 11'deki filtre boşluğu aynı bileşenin varyantı olacak;
  o varyant bu fazda yazılmadı.
- `ui/common/EmptySubscriptions` — abonelik listesi için hazır metinlerle
  sarmalayan ince bir katman.
- `HomeScreen`: `!isLoading && subscriptions.isEmpty()` olduğunda boş durum,
  değilse liste. Dashboard kartı ve FAB her iki durumda da yerinde.
- Düğme yok. FAB zaten sağ altta; ikinci bir giriş noktası aynı odaya iki kapı
  açmak olurdu.

**İkon seçimi — yeni ikon eklenmedi**
`Icons.AutoMirrored.Filled.List` seçildi. Gerekçe: bu ikon **zaten pakette**,
`SubscriptionCard` Spotify için kullanıyor. Faz 16'da
`material-icons-extended` daraltılacak ve o listeye yeni bir isim eklememek
önemliydi. Anlamı da doğru: boş bir listenin yerinde bir liste simgesi duruyor.

Mevcut ikon kümesi (9 isim): `ArrowBack`, `List`, `Add`, `Cloud`, `DateRange`,
`Delete`, `PlayArrow`, `Settings`, `Star`. Bu faz **sayıyı artırmadı.**

**Renk rolleri — `onSurfaceVariant` bilerek kullanılmadı**
ARCHITECTURE §12'ye göre o rol şemamızda **tanımsız** ve Material baseline'ının
mor-grisine düşüyor. Bunun yerine:

| Öğe | Rol | Neden |
|---|---|---|
| İkon | `primary` (DeepBlue) | §12: metin/ikon `primary` ailesinden, dolu yüzeyler `*Container`. `primaryContainer` ikon olarak beyaz üstünde 1.78:1 verirdi |
| Başlık | `onSurface` | Tanımlı |
| Alt satır | `onSurface` | Tanımlı. Alpha ile soluklaştırılmadı — DashboardCard'ın 8a'daki dersi: 0.7 alpha kontrastı 3.6:1'e düşürüyor, hiyerarşiyi boyut/ağırlık farkı zaten taşıyor |

Boyutlar `Dimens`'e eklendi: `EmptyStateIconSize = 72.dp`, `EmptyStatePadding = 32.dp`.

**Erişilebilirlik — `clearAndSetSemantics`, `mergeDescendants` değil**
Prompt "8a'daki `mergeDescendants` deseni" diyordu, ama `DashboardCard`'ın
kendi yorumu bunun **yetmediğini** kaydediyor: birleştirme çocukları ağaçta
bırakıyor (erişilebilirlik köprüsü birleştirilmemiş ağacı yürüyor), kart yine
üç durak veriyordu. Orada `clearAndSetSemantics` ile çözülmüştü; burada da o
kullanıldı. Ölçüm doğruladı: iki emülatörde de **tek düğüm**.

**Yükleme ile çakışma**
`isLoading` ve liste **aynı emisyonda** geliyor — `combine` bloğu ikisini
birlikte üretiyor. Yani "yükleniyor bitti ama liste henüz gelmedi" diye bir kare
yok; boş durum yalnızca listenin gerçekten boş olduğu bilindiğinde çiziliyor.
8a'daki 300 ms gecikmeli gösterge de yerinde duruyor. Ölçüldü: veri varken
açılışta boş durum **hiç** görünmüyor (iki dump'ta da 0 eşleşme).

**Testler**
- Birim testi yazılmadı; bu saf UI. **136 test** değişmedi, 0 hata.
- `lintDebug` **0 hata, 20 uyarı** — uyarı sayısı da değişmedi.
- `HomeScreenEmptyPreview` zaten `isLoading = false` ile duruyordu, artık yeni
  boş durumu render ediyor. Bileşenin kendi preview'ı da eklendi.

**Emülatör sonuçları**

| Ölçüm | API 29 (720x1280) | API 34 (1080x2400) |
|---|---|---|
| (a) boş durum düğümü | `[64,624][656,902]` | `[84,884][996,1249]` |
| (a) dashboard | `[32,208][688,433]` "Total Monthly, TRY 0.00" | `[42,338][1038,633]` |
| (a) FAB | `[608,1168][656,1216]` | `[933,2190][996,2253]` |
| (c) abonelik eklendi | Boş durum gitti, liste geldi | aynı |
| (d) silindi | Boş durum geri geldi | aynı |
| (d) Snackbar | `[56,1044][313,1084]` — boş durumun altı 902, **142 px boşluk** | `[74,2026][415,2079]` — üstü 1249, **777 px boşluk** |
| (e) fs 2.0 | `[64,703][656,1044]` — 278 → 341 px, kırpılma yok, FAB'la çakışma yok | `[84,987][996,1434]` — 365 → 447 px, aynı |
| (g) ağaçtaki hâli | **1 düğüm**, "No subscriptions yet, Tap + to add one" | aynı, aynı isim |

**(b) yükleme/boş durum çakışması:** veri varken uygulama kapatılıp açıldı ve
iki ayrı dump alındı; ikisinde de `No subscriptions yet` **0 kez** geçti,
ardından liste göründü.

**(f) koyu tema (API 34):** boş durum ağaçta, uygulama çökmedi
(`logcat -b crash` uygulama için 0 satır). Kullanılan roller `primary` ve
`onSurface`; ikisi de tanımlı ve daha önceki fazlarda ölçülmüş. **Renk
kontrastının kendisi dump'tan ölçülemez** — okunabilirlik iddiası bu ölçümle
kanıtlanmadı, yalnızca çizildiği ve çökmediği doğrulandı.

**Regresyon — 50 maddelik liste**

Sabit listeye 8b'nin dört maddesi eklendi (47-50), liste 46 → **50**.
Tamamı iki emülatörde koşuldu ve geçti. Kayıtlı istisnalar: **#15/#16** API
29'da ölçülemiyor (API 34'te geçti); **#34-#37 ve #41-#45** API 33+ davranışları,
API 29'da karşılığı **#46** ve o geçti; **#40** yalnızca API < 33 maddesi.

**Ölçüm sırasında çıkan engel**
Geniş emülatör taze açılışta iki kez sistem ANR diyaloğu gösterdi
("Process system isn't responding" / "System UI isn't responding"). İlki
"Wait" tuşuna basılmasına rağmen kapanmadı — `system_server` yanıt vermiyordu —
ve emülatör yeniden başlatıldı. Uygulamayla ilgisi yok: diyalog altındaki
`topResumedActivity` bizim `MainActivity`'mizdi ve crash logu boştu.

**Değişen dosyalar**
- `ui/common/EmptyState.kt` — yeni
- `ui/home/HomeScreen.kt` — bağlama
- `ui/theme/Dimens.kt` — iki yeni değer
- `res/values/strings.xml`, `res/values-en/strings.xml`
- `docs/ROADMAP.md`, `docs/TESTING.md`

**Sonraki faz için not**
- Faz 8 ve Faz 10 kapandı. Sırada Faz 11 (kategoriler); oradaki filtre boşluğu
  `EmptyState`'in varyantı olacak, ROADMAP'e yazıldı.
- fs 2.0'da uzun metin kırpılması Faz 14'ün konusu; bu bileşende kırpılma
  görülmedi ama ölçüm yalnızca blok sınırlarından yapıldı, tek tek satırlardan
  değil (`clearAndSetSemantics` çocukları ağaçtan kaldırıyor).

---

## [Faz 10c-2] Bağlamsal İzin İsteği — 2026-09-05

**Durum:** Tamamlandı. **Faz 10c ve Faz 10'un tamamı kapandı.**

**Yapılanlar**
- `HomeUiState`'e tek seferlik `shouldRequestNotificationPermission` bayrağı ve
  `HomeEvent.NotificationRequestHandled`. Yeni `Channel`/`SharedFlow` açılmadı
  (ARCHITECTURE §5).
- `HomeViewModel.armNotificationRequest` — kayıt başarılı olduktan sonra çalışan,
  altı koşullu bir kapı: tarih var mı · bildirimler zaten görünüyor mu · bu
  derlemede runtime izin gerekiyor mu · izin zaten verili mi · daha önce
  sorulmuş mu · listedeki tarihli abonelik sayısı 1 mi.
- Sayım, **mevcut** `repository.observeAll()` bir kez okunarak yapılıyor. Yeni
  DAO sorgusu, yeni repository fonksiyonu, yeni DataStore anahtarı açılmadı.
  `uiState`'ten okunmadı çünkü arkasındaki Flow yeni satır için henüz emit
  etmemiş olabilir.
- `HomeScreen`'e koşulsuz kayıtlı `RequestPermission` launcher'ı ve sıralamayı
  kuran efekt.

**Sıralama nasıl kuruldu**

Sheet'i ağaçta tutan koşulun tersi kullanıldı:

```kotlin
val isAddSheetGone = !uiState.isAddSheetOpen && !sheetState.isVisible
LaunchedEffect(uiState.shouldRequestNotificationPermission, isAddSheetGone) { … }
```

`isAddSheetOpen` durumun kapandığını, `sheetState.isVisible` gizlenme
animasyonunun bittiğini söyler; istek ancak ikisi de sağlandığında gönderiliyor.
Tetik gönderildiği anda `NotificationRequestHandled` ile temizleniyor.

**Bayrak yazımı — prompttan bilinçli sapma**

Prompt "bayrak yazma işi 10c-1'deki akışın sahibinde kalsın, iki yerden
yazılmasın" diyordu; aynı promptun ürün kuralı ise "reddedilirse **bayrak
yazıldığı için** bu yol bir daha çalışmaz" diyordu. İkisi birlikte tutmuyor:
`HomeViewModel` yazmazsa bayrak `false` kalır, Ayarlar satırı kullanıcıya az
önce sorulduğunu bilmez ve açıklama diyaloğunu atlar; tarihli aboneliklerin
hepsi silinip yenisi eklenirse istek tekrar çıkar.

**Aynı anahtar, iki çağrı yeri** seçildi: `HomeViewModel` de
`reminderState.setPermissionRequested()` çağırıyor. İkinci bir anahtar
açılmadı. Gerekçe ARCHITECTURE §18'e yazıldı.

**Testler**
- 127 → **136 birim testi** (9 yeni), 0 hata.
- Kapının her dalı `HomeViewModel`'ın public yüzeyinden test edildi: tarihli ilk
  kayıt · tarihsiz kayıt · tarihli ikinci kayıt · daha önce sorulmuş · zaten
  görünür · izin verili ama bildirim kapalı · API < 33 · tetiğin tüketilmesi ·
  doğrulamada takılan kayıt.
- `lintDebug` **0 hata, 19 → 20 uyarı**. Tek yeni uyarı `HomeScreen.kt:125`,
  `InlinedApi` (`POST_NOTIFICATIONS`, API 33 sabiti) — diğer üçüyle aynı sınıf,
  derleme anında satır içine alınan `String` sabiti.

**Emülatör turu — API 34**

| Adım | Kanıt |
|---|---|
| (a) tarihli ilk abonelik, Kaydet | Diyalog çıktığı anda alınan dump'ta **sheet ağaçta yok** (`New Subscription` sayısı 0). Pencere listesinde yalnızca `GrantPermissionsActivity` ve `MainActivity` var — sheet'in penceresi de yok |
| (b) Allow | `granted=true` · kayıt "Netflix, TRY 159.99, 6 days left" · Ayarlar satırı **"Payment reminders, On"** |
| (c) `pm clear` + **tarihsiz** abonelik | `permissioncontroller` düğüm sayısı **0**, izin hâlâ `granted=false`, kayıt oluştu ("NoDate, TRY 20.00") |
| (d) aynı turda tarihli abonelik | Diyalog **çıktı**, sheet yine ağaçta değil. "Don't allow" → `granted=false, USER_SET` |
| (e) ikinci tarihli abonelik | `permissioncontroller` **0** — tek sefer kuralı tuttu. Kayıt oluştu ("Second, TRY 40.00, 6 days left") |
| (f) döndürme | Diyalog çıktı (`grant_dialog` = 1) → yatay çevrildi → hâlâ **1** → kapatıldı → **0** → dikeye dönüldü → **0**. İkinci diyalog yok |
| — | İzin verilmişken (ENABLED) tarihli abonelik eklendi → `permissioncontroller` **0** |

**(f) nasıl kurgulandı:** tetik anını yakalamak yerine diyalog açıkken
döndürüldü. Kod tetiği gönderdiği anda temizlediği için asıl risk buradadır:
ekran yeniden kurulurken bayrak hâlâ duruyorsa istek ikinci kez gider.
Ölçümde gitmedi.

**Emülatör turu — API 29**

Tarihli ilk abonelik kaydedildi: `permissioncontroller` düğüm sayısı **0**,
kayıt normal oluştu ("Dated, TRY 30.00, 6 days left"), `logcat -b crash` boş.
Beklenen davranış: `CAN_REQUEST` dalı o platformda hiç oluşmuyor.

**Regresyon — 46 maddelik liste**

Sabit listeye 10c-2'nin altı maddesi eklendi (41-46), liste 40 → **46**.
Tamamı iki emülatörde koşuldu ve geçti. Kayıtlı istisnalar: **#15 (koyu tema)**
ve **#16 (dil)** API 29'da ölçülemiyor, API 34'te koşuldu ve geçti;
**#34-#37** yalnızca API 33+ maddeleri, API 29'da konu dışı.

Seçme kanıtlar: #11 toplam tam **219.89** · #19 undo satırı eski sırasına
döndü (`[0,560][720,704]` / `[0,800][1080,989]`) · #23 dört geçersiz kur da
alan altında hata verdi · #26 klavye açıkken `ScrollView` küçüldü ve iki butona
da ulaşıldı · #27 11 Eylül seçildi, cihaz tarihi 5 Eylül → **"6 days left"** ·
#31 31.12.2040 reddedildi, kaydedilmedi · #39 kanal `mImportance=0` iken
uygulama izni `granted=true` kaldı ve satır doğru şekilde kapalı dedi
(pid iki cihazda da değişmedi: 16610 ve 14825).

**Ölçüm sırasında öğrenilen**

`pm revoke` gibi, **sistem ayarlarından bildirimleri kapatmak da API 33+'ta
uygulama sürecini öldürüyor** (pid 14061 → 14350). Açmak öldürmüyor (10c-1'de
6715 → 6715 ölçülmüştü). Yani `ON_RESUME` tazelemesi yalnızca "açma" yönünde
süreç korunarak gösterilebiliyor; kapatma yönünde satır yine doğru güncelleniyor
ama süreç yeni.

**Değişen dosyalar**
- `ui/home/HomeUiState.kt` — tetik alanı + olay
- `ui/home/HomeViewModel.kt` — `armNotificationRequest`
- `ui/home/HomeScreen.kt` — launcher ve sıralama
- `test/ui/home/HomeViewModelReminderTriggerTest.kt` — yeni
- `test/ui/home/HomeViewModelTest.kt` — yeni fake'ler
- `docs/ARCHITECTURE.md` §18, `docs/TESTING.md`, `docs/ROADMAP.md`

**Sonraki faz için not**
- Faz 10 kapandı. Sırada ROADMAP'e göre Faz 11 (kategoriler); Faz 8b (boş durum
  ekranı) hâlâ tasarım kararı bekliyor.
- Faz 12 tarih ilerletmeyi getirdiğinde 1-3 günlük gecikme penceresi yeniden
  değerlendirilecek (ARCHITECTURE §18).
- Ayarlar satırlarının erişilebilirlik ağacında iki düğüm vermesi ROADMAP
  Faz 16'daki TalkBack maddesinde duruyor; bu fazda değişmedi.

---

## [Faz 10c-1] Bildirim Durumu ve İzin Akışı (Ayarlar) — 2026-09-05

**Durum:** Tamamlandı. **Faz 10c KAPANMADI** — bağlamsal tetikleyici (tarihli
abonelik kaydedilince sorma) 10c-2'nin işi, ROADMAP'teki 10c maddesi o bitene
kadar işaretlenmiyor.

**Yapılanlar**
- `ReminderNotificationStatus` — bildirimin fiilen görünüp görünmediği için tek
  kaynak. Kontrol iki aşamalı: uygulama anahtarı **ve** kanal importance'ı.
  Arayüz + `AndroidReminderNotificationStatus` gerçeklemesi, `@Binds` ile
  `di/ReminderModule`'de bağlandı. Arayüz olmasının sebebi test: durum
  makinesinin yedi dalı gerçek bir `NotificationManager` üzerinden
  koşturulamaz.
- `PaymentReminderNotifier` artık kararı bu sınıftan alıyor; kendi içindeki
  `areNotificationsEnabled` kontrolü kaldırıldı. Kanal id'si tek yerde
  (`ReminderNotificationStatus.CHANNEL_ID`).
- `ReminderStateRepository`'ye `wasPermissionRequested` / `setPermissionRequested`.
  Mevcut `DataStore<Preferences>` örneği; yeni repository veya ikinci instance
  yok (§14).
- `SettingsUiState` üç halli `ReminderPermissionState` ve iki eylemli
  `ReminderPermissionAction` kazandı. Karar tamamen ViewModel'da; composable
  yalnızca Activity gerektiren işi yapıyor ve sonucu `onEvent` ile bildiriyor.
  Yeni `Channel`/`SharedFlow` açılmadı.
- Ayarlar ekranına "Ödeme hatırlatmaları" satırı. Kur satırıyla aynı desen;
  ikisi de artık ortak `ui/common/SettingsRow` bileşeninden geliyor.
- `LocalActivity` kullanıldı (activity-compose **1.12.4**, sınıfın varlığı
  `unzip -l classes.jar` ile doğrulandı). `ContextWrapper` zinciri yürüyen
  yardımcı yazılmadı.
- `SettingsScreen.kt` bölünmeden 310 satır olmuştu (CLAUDE.md §4 sınırı 300);
  satır bileşeni `ui/common/SettingsRow.kt`'ye, Activity yardımcıları
  `ui/settings/ReminderPermissionActions.kt`'ye taşındı → 214 satır.

**Karar tablosu** ARCHITECTURE §18'de. Özet sıra: görünüyorsa `ENABLED`;
API < 33 ise `SETTINGS_ONLY`; izin var ama görünmüyorsa `SETTINGS_ONLY`;
hiç sorulmamışsa `CAN_REQUEST`; sorulmuş + rationale varsa `CAN_REQUEST`;
kalan `SETTINGS_ONLY`.

**Testler**
- 112 → **127 birim testi** (15 yeni), 0 hata. Yedi dalın tamamı ve dokunma
  davranışları ViewModel'ın public yüzeyinden test edildi.
- Yeni fake'ler: `FakeReminderStateRepository`, `FakeReminderNotificationStatus`.
  Mock kütüphanesi yok (§11).
- `lintDebug` **0 hata, 19 uyarı** (15 → 19). Dört yeni uyarının hepsi
  `InlinedApi`: `POST_NOTIFICATIONS` (×2, API 33 sabiti),
  `ACTION_APP_NOTIFICATION_SETTINGS` ve `EXTRA_APP_PACKAGE` (API 26 sabitleri).
  Üçü de derleme anında satır içine alınan `String` sabitleri; eski cihazda
  çalışma zamanı sorunu değil, zaten `ActivityNotFoundException` fallback'i ve
  API < 33 dalı bu yüzden var.
- Enstrümantasyon testi bu fazda yazılmadı (izin diyaloğu sistem UI'ı).

**Lint yüzünden kalan ikinci kontrol — kabul edilen sapma**
Prompt "notifier'da iki ayrı kontrol kalmasın" diyordu. Karar tek yerde
(`status.areRemindersVisible()`), ama `notify()` ile **aynı fonksiyonda** bir
`ContextCompat.checkSelfPermission` satırı bırakmak zorunda kaldım: lint'in
`MissingPermission` denetimi arayüzün arkasını görmüyor ve kaldırıldığında
derleme `Call requires permission which may be rejected by user` hatasıyla
duruyor. `@SuppressLint` yasak (CLAUDE.md §4), `@RequiresPermission` ile
yukarı taşımak worker'a yanlış bir söz yazmak olurdu. Satır, sebebi yazılarak
bırakıldı.

**Emülatör turu — API 34, tek tur, sırayla**

| Adım | Kanıt |
|---|---|
| (a) `pm clear` sonrası | `granted=false` · satır: **"Payment reminders, Off — tap to turn on"** |
| (b) satıra dokun | `com.android.permissioncontroller:id/grant_dialog` + `permission_allow_button` / `permission_deny_button`. Uygulamanın diyaloğu **yok** |
| (c) Allow | `granted=true` · satır: **"Payment reminders, On"** |
| (e) bir kez reddedip satıra dokun | **Uygulamanın diyaloğu**: "Allow reminders" / "You get at most one notification a day…" / Cancel · Ask for permission |
| (e devam) sistem diyaloğu | İkinci soruda düğme `permission_deny_and_dont_ask_again_button` |
| (f) kalıcı ret | `granted=false, flags=[USER_SET\|**USER_FIXED**\|…]` · satır: **"Off — turn on in system settings"** · dokununca `com.android.settings/.Settings$AppNotificationSettingsActivity` |
| (d) sistem ayarlarından aç, geri dön | pid **6715 → 6715** (yeniden başlatma yok) · satır: **"Payment reminders, On"** |
| (g) yalnızca kanalı kapat, geri dön | kanal `mImportance=0`, uygulama izni **`granted=true`**, pid **7175 → 7175** · satır: **"Off — turn on in system settings"** |
| (h) fs 2.0 | satır `[42,1214][1038,1560]`, durum metni `[42,1332][1038,1528]` — iki satıra sarıyor, kırpılma yok, üstteki kur satırıyla çakışma yok (`…1211` / `1214…`) |

**(d) hakkında bir düzeltme:** prompt `pm revoke` ile ON_RESUME tazelemesini
göstermeyi istiyordu, ama **`pm revoke` uygulama sürecini öldürüyor** (pid
6503 → yok). Bu yüzden tazeleme sistem ayarları üzerinden gösterildi; o yol
süreci öldürmüyor ve zaten gerçek kullanım senaryosu bu. Ölçüm TESTING.md'ye
yazıldı.

**Emülatör turu — API 29**

Prompt satırın `SETTINGS_ONLY` olmasını bekliyordu; **temiz kurulumda `ENABLED`
çıkıyor** ve bu doğru: API 29'da bildirimler varsayılan açık, durum makinesinin
ilk dalı kazanıyor. `SETTINGS_ONLY` yolu, bildirimler kapatılınca üretildi:

- (i) satıra dokun → `com.android.settings/.Settings$AppNotificationSettingsActivity`,
  ağaçta `permissioncontroller` düğümü sayısı **0** (izin diyaloğu hiç çıkmıyor).
- Sistem ayarlarından bildirimleri kapat, geri dön → pid **7293 → 7293**,
  satır: **"Payment reminders, Off — turn on in system settings"**.

**(j) Erişilebilirlik — bulundu, çözülemedi**

İki emülatörde de satır ağaçta **aynı sınırlarda iki düğüm** veriyor:

```
node class="android.view.View" content-desc=""                          clickable="true"  focusable="true"  bounds="[32,600][688,726]"
node class="android.view.View" content-desc="Payment reminders, Off — turn on in system settings"  clickable="false" focusable="false" bounds="[32,600][688,726]"
```

(API 34'te aynı yapı, `bounds="[42,854][1038,1020]"`.)

Denenenler: `semantics`'i `clickable`'dan **önce** koymak (değişmedi, hâlâ iki
düğüm) ve `Role.Button` eklemek (**üç** düğüme çıkardı). Bırakılan biçim,
adın odaklanabilir düğümün doğrudan altında olduğu iki-düğüm biçimi.

TalkBack'in üst düğüme odaklanıp alttakinin adını okuyup okumadığı **bu
imajlarda doğrulanamıyor** (Android Accessibility Suite yok, TESTING.md'de
kayıtlı). ROADMAP Faz 16'daki TalkBack maddesine eklendi.

**(k) Regresyon — 40 maddelik liste**

Sabit listeye 10c-1'in sekiz maddesi eklendi (33-40), liste 32 → **40**.
Tamamı iki emülatörde koşuldu ve geçti, iki bilinen istisnayla: **#15 (koyu
tema)** API 29'da ölçülemiyor, **#16 (dil)** API 29'da ayarlar arayüzü
gerektirdiği için koşulmadı; ikisi de API 34'te koşuldu ve geçti
("Aylık Toplam", "Aboneliklerim", "Ödeme hatırlatmaları, Kapalı — açmak için
dokunun", "4 gün gecikti", "6 gün kaldı").

Cihaz tarihi 5 Eylül 2026 olduğu için tarih maddeleri buna göre koşuldu:
11 Eylül → **"6 days left"**, 1 Eylül → **"4 days overdue"**, bugün →
**"Due today"**, 31.12.2040 → alan hatası ve **kaydedilmedi**.

Hiçbir metinde ham `%` görülmedi.

**Değişen dosyalar**
- `reminder/ReminderNotificationStatus.kt`, `reminder/AndroidReminderNotificationStatus.kt` — yeni
- `di/ReminderModule.kt` — yeni
- `reminder/PaymentReminderNotifier.kt` — kararı devretti
- `domain/repository/ReminderStateRepository.kt`, `data/repository/ReminderStateRepositoryImpl.kt`
- `ui/settings/SettingsUiState.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`
- `ui/settings/ReminderPermissionActions.kt`, `ui/common/SettingsRow.kt` — yeni
- `res/values/strings.xml`, `res/values-en/strings.xml`
- `test/fake/FakeReminderStateRepository.kt`, `FakeReminderNotificationStatus.kt` — yeni
- `test/ui/settings/SettingsViewModelReminderTest.kt` — yeni
- `docs/ARCHITECTURE.md` §18, `docs/TESTING.md`, `docs/ROADMAP.md`

**Doğrulama tamamlaması — 2026-09-05**

10c-1 kapanışında iki eksik vardı: notifier değiştiği hâlde enstrümantasyon
paketi yeniden koşulmamıştı, ve satırın ağaçtaki şekli mevcut satırlarla
karşılaştırılmamıştı. İkisi de kapatıldı, **üretim kodu değişmedi**.

**Enstrümantasyon — iki emülatörde de `OK (11 tests)`**

`am instrument` ile koşuldu (Gradle görevi uygulamayı kaldırıyor).

| Emülatör | Sonuç |
|---|---|
| `subtrack_narrow_api29` | `OK (11 tests)` |
| `subtrack_wide_api34` | `OK (11 tests)` |

API 29'da bildirim **gerçekten gitti** — notifier'daki yeni
`checkSelfPermission` satırının eski platformda hatırlatmayı susturmadığının
kanıtı:

```
EXTRA_TITLE   = [Payment reminder: 4 subscriptions]
EXTRA_TEXT    = [DueToday — today, Tomorrow — tomorrow, OneDayLate — 1 day overdue,
                 ThreeDaysLate — 3 days overdue]
channelId     = [payment_reminders]
contentIntent is null = false      FLAG_AUTO_CANCEL set = true
postTime      = 1788600100565  (ikinci koşuda değişmedi)
```

**API 29'da `POST_NOTIFICATIONS` nasıl görünüyor**

```
$ adb shell dumpsys package com.elinacn.subtrack | grep -A2 POST_NOTIFICATIONS
      android.permission.POST_NOTIFICATIONS
      android.permission.WAKE_LOCK
      android.permission.ACCESS_NETWORK_STATE

$ ... | grep -iE "POST_NOTIFICATIONS|install permissions|runtime permissions"
      android.permission.POST_NOTIFICATIONS
    install permissions:
    runtime permissions:
```

İzin yalnızca **istenen izinler** listesinde görünüyor; `install permissions`
ve `runtime permissions` bölümlerinin ikisi de onun için **boş** ve hiçbir
`granted=` satırı yok. Yani API 29'da bu izin ne kurulum ne çalışma zamanı
izni olarak veriliyor — `checkSelfPermission` ona "reddedildi" cevabı verirdi.
Notifier'daki `isRuntimePermissionRequired()` koruması tam olarak bunun için
var, ve yukarıdaki bildirim o korumanın çalıştığının kanıtı.

**Teşhis — satırın ağaçtaki şekli proje geneli bir desen**

API 34'te tek dump alındı:

| Öğe | Düğüm | clickable / focusable | İsim |
|---|---|---|---|
| Ana para birimi chip'i (TRY) | 2 | üst: `true`/`true` (+`checkable`, `checked`) | üstte **yok**, altta "Turkish lira" |
| Döviz Kurları satırı | 2 | üst: `true`/`true` | üstte **yok**, altta "Exchange Rates, Edit the rates…" |
| Ödeme hatırlatmaları satırı | 2 | üst: `true`/`true` | üstte **yok**, altta "Payment reminders, Off — tap to turn on" |
| Geri oku (stok `IconButton`) | 2 | üst: `true`/`true` | üstte **yok**, altta "Back" |
| **Tarih alanı (10a)** | **1** | `true`/`true` | **"Next Payment (optional), Not set"** |

Yani mevcut satırların hepsi yeni satırla **aynı iki-düğüm şeklinde** →
promptun birinci dalı: **kod değiştirilmedi**, bulgu ROADMAP Faz 16'daki
TalkBack maddesine yazıldı.

Tek düğüm veren tek yapı 10a'daki tarih alanı ve sebebi ölçümden anlaşılıyor:
orada `semantics` ve `clickable` **çocuğu olmayan** bir overlay `Box`'a
konuyor, o yüzden tek semantics düğümünde birleşiyorlar. Metin çocukları olan
bir satırda aynı sonuç alınamıyor.

Dikkat çeken nokta: stok Material `IconButton` da (geri oku) aynı iki-düğüm
şeklini veriyor. Bu, davranışın Compose erişilebilirlik köprüsünün normali
olduğuna işaret ediyor — ama TalkBack bu imajlarda olmadığı için üst düğüme
odaklanıp alt düğümün adını okuyup okumadığı **hâlâ doğrulanamadı**.

Kod değişmediği için regresyon listesi yeniden koşulmadı.


**Sonraki faz için not**
- 10c-2: tarihli abonelik kaydedilince bağlamsal olarak sorma. Bayrak ve durum
  makinesi hazır; oradaki tek yeni soru, aynı gün içinde kaç kez sorulacağı.
- İzin yeni verildiğinde o günkü hatırlatmanın gönderilmesi 10b hotfix'iyle
  zaten mümkün; 10c-2'de izin verilir verilmez bir koşu tetiklenmeli mi,
  karara bağlanmalı.

---

## [Faz 10b hotfix] Gösterilmeyen bildirim gün olarak yazılmasın — 2026-09-04

**Durum:** Tamamlandı.

**Sorun**
10b doğrulama turunda bulundu (kayıt yukarıda, "Bulunan ve düzeltilmeyen"
başlığı altında). Worker, seçim boş değilse `notifier.notify(...)` çağırdıktan
sonra günü **koşulsuz** işaretliyordu. Notifier ise bildirimler kapalıyken
hiçbir şey göndermeden çıkıyordu. Sonuç: hiç gösterilmemiş bir bildirim
"gönderildi" olarak kaydediliyor, kullanıcı o gün bildirimleri açsa bile
ertesi güne kadar hiçbir şey görmüyordu.

Bu bir izin akışı sorunu değildi: `areNotificationsEnabled()` kullanıcı
bildirimleri sistem ayarlarından kapattığında **her API sürümünde** `false`
döner, yalnızca API 33+ izin reddinde değil.

**Düzeltme**
- `PaymentReminderNotifier.notify(...)` artık `Boolean` dönüyor: bildirimin
  gerçekten gönderilip gönderilmediği. Kanal kurulumu, metin üretimi,
  `BigTextStyle` ve `PendingIntent` değişmedi.
- Worker günü **yalnızca `true` dönerse** yazıyor. İki durumda da
  `Result.success()` — bildirimlerin kapalı olması bir hata değil, `retry`
  bu işin değiştiremeyeceği bir ayarı beklerken pil harcardı.
- `try/catch` eklenmedi (§9).

**`@FixMethodOrder` kaldırıldı**
10b'de eklenmişti, gerekçesi "bildirimler kapalı" metodunun günü
işaretlemesiydi. Düzeltmeden sonra o metot kaydı hiç kirletmiyor, yani sıra
bağımlılığı **yapısal olarak** kalktı. Ölçüldü: aşağıdaki turda kapalı-izin
koşusu **önce**, asıl koşu **sonra** çalıştı ve asıl koşu bildirimi gönderdi.
Anotasyon kaldırıldı; sınıf KDoc'u ve TESTING.md buna göre yeniden yazıldı.
`pm clear` ön koşulu **duruyor** — sebebi sıra değil, "günde bir bildirim"
kuralının kendisi.

**Test yöntemi seçimi**
Yeni test metodu **yazılmadı**, adb ile ayrılmış tur seçildi. Sebep: izin
durumunu test süreci içinden değiştirmek `UiAutomation.revokeRuntimePermission`
gerektiriyor ve çalışma zamanı izni geri almak süreci öldürebiliyor — testi de
öldürürdü. `kspAndroidTest` eklenmedi, enstrümantasyonun Hilt grafına erişimi
yok; kanıt davranışsal.

**Doğrulama turu — API 34, tek tur, veri arada SİLİNMEDİ**

`pm clear` → `pm revoke POST_NOTIFICATIONS` → üç koşu:

| # | İzin | Nasıl koşturuldu | Sonuç |
|---|---|---|---|
| 1 | `granted=false` | `notificationsDisabled` metodu | `areNotificationsEnabled=false`, worker **SUCCEEDED**, aktif bildirim **0** |
| 2 | `granted=true` (`pm grant`, veri silinmedi) | `datedSubscriptions` metodu, ilk koşu | **Bildirim GELDİ**, `postTime=1788550455107` |
| 3 | `granted=true` | aynı metodun ikinci koşusu | Yeni bildirim yok, `postTime` **değişmedi** (1788550455107) |

**Düzeltmenin kanıtı 2. satırdır.** Eski kodda 1. koşu günü işaretlerdi ve
2. koşu hiçbir şey göndermezdi — 10b turunda tam olarak bu yüzden paket
kırmızı olmuştu.

Gelen bildirimin ham metni, 10b'dekiyle birebir aynı:

```
EXTRA_TITLE = [Payment reminder: 4 subscriptions]
EXTRA_TEXT  = [DueToday — today, Tomorrow — tomorrow, OneDayLate — 1 day overdue,
               ThreeDaysLate — 3 days overdue]
channelId   = [payment_reminders]
contentIntent is null = false      FLAG_AUTO_CANCEL set = true
```

**Diğer doğrulamalar**
- `assembleDebug`, `lintDebug` (**0 hata**, uyarı kümesi değişmedi: 15),
  `testDebugUnitTest` (**112**, 0 hata).
- Tüm enstrümantasyon paketi `am instrument` ile iki emülatörde: **OK (11 tests)**.
- Regresyon listesinden yalnızca **#1** ve **#12** koşuldu, iki emülatörde de
  geçti. Bu bilinçli bir istisna: değişiklik UI'a, Room'a, DataStore anahtar
  isimlerine ve DI grafına dokunmuyor; iki dosyada toplam 25 satır.

**Bilinen sınır (ARCHITECTURE §18'e yazıldı)**
`areNotificationsEnabled()` **uygulama düzeyindedir.** Kullanıcı yalnızca
`payment_reminders` kanalını kapatmışsa fonksiyon hâlâ `true` döner, sistem
bildirimi sessizce düşürür ve gün yine işaretlenir. Kanal bazlı kapatma bu
korumanın dışında; eklenip eklenmeyeceği ayrı karar.

**Belgelere taşınan ölçüm tuzağı**
API 34'te ekranın kenarından başlayan `input swipe`'ın sistem geri jestini
tetiklediği bilgisi PROGRESS'ten `TESTING.md`'nin "Emülatör Testleri"
bölümüne taşındı. Sebep de ölçüldü: `cmd overlay list android` çıktısında dar
AVD'de hiçbir gestural overlay etkin değil (üç tuşlu gezinme), geniş AVD'de
`[x] com.android.internal.systemui.navbar.gestural`. Yani fark API
sürümünün değil **gezinme modunun** sonucu. Ölçülen iki nokta: x=1040
(kenardan 40 px) uygulamadan çıkardı, x=950 (130 px) çalıştı; eşik ikili
aramayla daraltılmadı.

**Değişen dosyalar**
- `reminder/PaymentReminderNotifier.kt` — `notify` artık `Boolean`
- `reminder/PaymentReminderWorker.kt` — gün koşullu yazılıyor
- `androidTest/.../PaymentReminderWorkerTest.kt` — `@FixMethodOrder` kaldırıldı,
  KDoc yeniden yazıldı
- `docs/ARCHITECTURE.md` §18, `docs/TESTING.md`

**Commit'ler**
- `9d143a1` fix: only record the day when a reminder was actually shown
- `3a81fc3` docs: record the reminder-day fix and the edge-swipe measuring trap

**Sonraki faz için not**
- 10c izni runtime'da isteyecek. İzin **yeni verildiğinde** o günkü
  hatırlatmanın hâlâ gönderilebilir olması bu düzeltmeyle sağlandı; 10c'de
  ayrıca izin verilir verilmez bir koşu tetiklenmeli mi, karara bağlanmalı.
- Kanal bazlı kapatmanın tespiti de 10c'nin konusu olabilir.

---

## [Faz 10b] WorkManager + Yerel Bildirim — 2026-09-04

**Durum:** Tamamlandı. İzin isteme akışı (runtime `POST_NOTIFICATIONS`) **yok** — 10c.

**Bağımlılık ölçümü (Görev 0)**
- `work-runtime-ktx` **2.11.2** (en güncel kararlı; 2.12.0 hattı hâlâ
  alpha/beta/rc) ve `androidx.hilt` **1.3.0** (`hilt-work` + `hilt-compiler`)
  mevcut taban üzerinde **ilk denemede** çözüldü. `checkDebugAarMetadata` hiç
  patlamadı, alt sürüme inmek gerekmedi. AGP 9.0.1 / compileSdk 36.1 korundu.
- `androidx.hilt` tek sürüm grubu olduğu için `hilt-work` ve `hilt-compiler`,
  `hilt-navigation-compose` ile **aynı** `version.ref`'i paylaşıyor.
- Dagger'ın `hilt-android-compiler`'ı yerinde kaldı. KSP iki compiler'la
  sorunsuz derledi ve `@HiltWorker`'ı gerçekten işledi:
  `PaymentReminderWorker_AssistedFactory`, `_AssistedFactory_Impl`, `_Factory`,
  `_HiltModule` üretildi.
- Lint uyarı **kümesi** değişmedi; yalnızca iki yeni "1.4.0 mevcut"
  (`GradleDependency`) satırı eklendi. 0 hata.
- Birleşik manifest ölçüldü: `androidx.startup.InitializationProvider`
  probe'tan **önce de vardı** ve emoji2, lifecycle, profileinstaller
  tarafından kullanılıyordu. WorkManager dördüncü olarak katıldı, bu yüzden
  provider'ın kendisi kaldırılamaz.
- WorkManager manifeste dört izin ekliyor: `WAKE_LOCK`,
  `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`.
  Faz 16'ya not düşüldü.
- `stripDebugDebugSymbols`'ün strip edemediği `libandroidx.graphics.path.so`
  WorkManager'dan **gelmiyor** — temiz tabanda da vardı, Compose'un
  `androidx.graphics:graphics-path` bağımlılığından geliyor.

**Yapılanlar**
- `PaymentReminderSelection` — saf, bugünün tarihi parametre. `PaymentCountdown`
  yeniden yazılmadı, çağrıldı. İki eşik adlandırılmış sabit
  (`UPCOMING_WITHIN_DAYS = 1`, `OVERDUE_WITHIN_DAYS = 3`). Sonuç
  `PaymentReminder(subscription, countdown)` taşıyor; çağıran geri sayımı
  ikinci kez hesaplamıyor.
- `ReminderStateRepository` — son bildirim günü epoch day olarak. Ayrı arayüz,
  çünkü bu bir kullanıcı tercihi değil, işin kendi kaydı. Depolama **mevcut**
  `DataStore<Preferences>` örneği; ikinci instance açılmadı (§14).
- `PaymentReminderNotifier` — `NotificationChannelCompat` + tek özet bildirim,
  `BigTextStyle`, `setAutoCancel(true)`, `PendingIntent.FLAG_IMMUTABLE`. Kanal
  her gönderimde yeniden kuruluyor (dil değişince ad güncellensin diye).
- `PaymentReminderWorker` — `@HiltWorker` + `CoroutineWorker`, `try/catch` yok.
- `PaymentReminderScheduler` — `KEEP`, constraint yok, ilk gecikme enjekte
  `Clock`'tan sonraki yerel 09:00.
- `SubTrackApplication` artık `Configuration.Provider`. Üye **property**;
  `javap` ile doğrulandı (`getWorkManagerConfiguration()`).
- Manifest: `POST_NOTIFICATIONS` bildirildi; `WorkManagerInitializer`
  `meta-data`'sı `tools:node="remove"` ile çıkarıldı, provider
  `tools:node="merge"` ile kaldı.
- Bildirim ikonu `res/drawable/ic_notification.xml` — tek path, düz beyaz,
  geçici. Faz 16'da marka ikonuyla yenilenecek.

**Yeniden kullanılan metin**
Gecikme günü için yeni bir plurals açılmadı; 10a'daki `days_overdue`
kullanıldı. Aynı dilde aynı olguyu söylüyor, ikinci bir çeviri çifti tekrar
olurdu. Yeni plurals yalnızca başlık sayısı için (`notification_title`), ve
başlık **fiilsiz**: aynı bildirim hem bugün ödenecek hem gecikmiş abonelik
taşıyabiliyor.

**`cmd jobscheduler run -f` duvarı ve nasıl dolanıldı**
İlk doğrulama denemesi başarısız oldu ve bu, fazın en pahalı bulgusu:

```
$ adb shell cmd jobscheduler run -f com.elinacn.subtrack 0
Running job [FORCED]
WM-WorkerWrapper: Delaying execution for ...PaymentReminderWorker because it is
  being executed before schedule.
WM-WorkerWrapper: Status ... is ENQUEUED; not doing any work and rescheduling
```

JobScheduler'ı zorlamak WorkManager'ın kendi `lastEnqueueTime + initial_delay`
denetimini geçmiyor. Duvar saatini ileri almanın üç yolu da kapalı:
`adb root` → *adbd cannot run as root in production builds*;
`adb shell date …` → *Operation not permitted*;
`setprop persist.sys.timezone` → *failed to set property*.

**Çözüm:** o denetim yalnızca gecikmeli işler için çalışıyor. Gecikmesiz bir
`OneTimeWorkRequest` anında koşuyor. Enstrümantasyon uygulamayla aynı süreçte
koştuğu için `WorkManager.getInstance()` uygulamanın kendi örneğini veriyor ve
worker'ı gerçek `HiltWorkerFactory` üretiyor — sahte fabrika, `work-testing`
bağımlılığı veya `TestListenableWorkerBuilder` kullanılmadı.

**Testler**
- 101 → **112 birim testi** (11 yeni, `PaymentReminderSelectionTest`), 0 hata.
- 9 → **11 enstrümantasyon testi**, iki emülatörde de **11/11**.
- `lintDebug` 0 hata. Uyarı kümesi Görev 0'daki 15 ile aynı.
- Worker için birim testi yazılmadı, `work-testing` eklenmedi.

**Emülatör sonuçları** (cihaz tarihi 4 Eylül 2026, GMT)

Bildirim içeriği iki cihazda **birebir aynı**:

```
EXTRA_TITLE    = [Payment reminder: 4 subscriptions]
EXTRA_TEXT     = [DueToday — today, Tomorrow — tomorrow, OneDayLate — 1 day overdue,
                  ThreeDaysLate — 3 days overdue]
EXTRA_BIG_TEXT = (aynısı)
channelId      = [payment_reminders]
contentIntent is null = false      FLAG_AUTO_CANCEL set = true
```

Ham `%` yok. `TwoDaysOut`, `FourDaysLate` ve `NoDate` metinde **yok**.
İkinci koşuda `postTime` değişmedi (dar: 1788549186749, geniş: 1788549195339 —
ikisi de iki koşuda aynı), aktif bildirim sayısı 1'de kaldı.

Bildirime dokunma: dar cihazda `(360,779)`, geniş cihazda `(587,730)` →
`ResumedActivity: com.elinacn.subtrack/.MainActivity`. Bildirim `mArchive`'e
düştü, yani `autoCancel` çalıştı.

İzin kapalı tur (yalnız API 34, `pm clear` + `pm revoke` sonrası):
`areNotificationsEnabled=false`, worker **SUCCEEDED**, aktif bildirim 0,
`logcat -b crash` boş.

**Bulunan ve düzeltilmeyen — ürün davranışı**
Worker, seçim boş değilse `notifier.notify(...)` çağırdıktan **sonra** günü
işaretliyor; bildirimin gerçekten gösterilip gösterilmediğine bakmıyor.
Sonucu: bildirimler kapalıyken koşan bir gün "bildirildi" sayılıyor, kullanıcı
o gün izni açsa bile ertesi güne kadar hiçbir şey görmüyor.

Bu, testte önce sıra bağımlılığı olarak ortaya çıktı: "bildirimler kapalı"
metodu günü işaretleyince asıl metot bildirim gönderemiyordu. Test tarafı
`@FixMethodOrder(NAME_ASCENDING)` ile sabitlendi ve `pm clear` ön koşulu
belgelendi. **Üretim kodu değiştirilmedi** — davranışın doğru olup olmadığı
ayrı bir karar, muhtemelen 10c'nin konusu.

**Regresyon listesi**
32 maddenin tamamı iki emülatörde de geçti, iki istisnayla: **#15 (koyu tema)**
API 29'da ölçülemiyor (TESTING.md'de kayıtlı sınır), yalnız API 34'te
koşturuldu; **#16 (dil)** API 29'da ayarlar arayüzü gerektiriyor, API 34'te
`cmd locale set-app-locales` ile koşturuldu ve Türkçe metinler geldi
("Aylık Toplam, ₺159,99", "Aboneliklerim"). #15 yalnızca çökme ve ağaç
bütünlüğü olarak doğrulandı; renk kontrastı dump'tan ölçülemez.

Ölçüm sırasında iki tuzak çıktı, ikisi de test yöntemine ait:
- API 34'te ekranın **en sağ kenarından** başlayan kaydırma sistem geri
  jestini tetikliyor ve uygulamadan çıkıyor. Kaydırma testleri kenardan uzak
  başlatılmalı.
- Soğuk açılıştan 3 sn sonra alınan `uiautomator dump` bayat toplam
  gösterebiliyor; 6 sn sonra doğru değer geliyor. Kalıcı bir durum değil.

**Değişen dosyalar**
- `domain/usecase/PaymentReminderSelection.kt` — yeni
- `domain/repository/ReminderStateRepository.kt` — yeni
- `data/repository/ReminderStateRepositoryImpl.kt` — yeni
- `reminder/PaymentReminderNotifier.kt`, `PaymentReminderWorker.kt`,
  `PaymentReminderScheduler.kt` — yeni paket
- `di/RepositoryModule.kt` — yeni bağlama
- `SubTrackApplication.kt` — `Configuration.Provider`
- `AndroidManifest.xml` — izin + initializer kaldırma
- `res/drawable/ic_notification.xml`, `res/values/strings.xml`,
  `res/values-en/strings.xml`
- `gradle/libs.versions.toml`, `app/build.gradle.kts`
- `androidTest/.../PaymentReminderWorkerTest.kt` — yeni
- `test/.../PaymentReminderSelectionTest.kt` — yeni
- `docs/ARCHITECTURE.md` §18, `docs/ROADMAP.md`, `docs/TESTING.md`

**Commit'ler**
- `1da6572` build: add WorkManager and the androidx Hilt worker integration
- `db76073` feat: pick which payments are worth a reminder
- `81760d8` feat: remember the day a reminder was last shown
- `df33817` feat: show the day's payment reminders in one notification
- `4ae0f6b` feat: run the payment reminder once a day
- `5478ea4` docs: record the reminder decisions and the WorkManager trigger limits
- `26b1b70` test: run the reminder worker on device through the real graph
- `eb4ac6e` test: fix the reminder test order so the once-a-day guard cannot hide a run
- `c4ced4c` docs: record phase 10b and the working way to run the reminder job

**Sonraki faz için not**
- 10c runtime izin akışını getirecek. Yukarıdaki "gün işaretleme" davranışı
  orada yeniden değerlendirilmeli: izin yeni verildiğinde o günkü hatırlatma
  kaçıyor.
- Faz 12 tarih ilerletmeyi getirdiğinde 1-3 günlük gecikme penceresinin
  gerekçesi ortadan kalkıyor (ARCHITECTURE §18).

---

## [Faz 10a] Sonraki Ödeme Tarihi — 2026-09-04

**Durum:** Tamamlandı. Bildirim, WorkManager ve izin akışı **yok** — 10b ve 10c.

**Desugaring ölçümü (Görev 0a)**
- `minSdk = 24`, `coreLibraryDesugaringEnabled` **tanımlı değildi**,
  `coreLibraryDesugaring` bağımlılığı **yoktu**.
- Tek satırlık `LocalDate.now()` denemesi: **`assembleDebug` GEÇTİ.** Derleyici
  şikâyet etmiyor çünkü `compileSdk 36` sınıf yolunda `java.time` var. Sorun
  ancak API 24/25 cihazda çalışma anında `NoClassDefFoundError` olurdu.
- **`lintDebug` HATA VERDİ** ve derlemeyi durdurdu:
  `Call requires API level 26, or core library desugaring (current min is 24):
  java.time.LocalDate#now [NewApi]`
- Sonuç sohbete taşındı, desugaring onaylandı, `minSdk 24` korundu.

**Desugaring sürüm seçimi**
`desugar_jdk_libs 2.1.5` seçildi. 2.x hattı AGP 8+/9'un beklediği hat; 2.1.5
AGP 9.0.1'in gerekli AGP sürümünü yükseltmeden kabul ettiği en yeni sürüm.
Doğrulama: `assembleDebug --rerun-tasks` yeni bir `l8DexDesugarLibDebug`
görevi çalıştırdı (desugar edilmiş kütüphanenin dex'lenmesi — açık olduğunun
kanıtı) ve **`lintDebug` temiz geçti**. APK bedeli ~200-400 KB, Faz 16'daki
R8 maddesinde ölçülecek.

**Yapılanlar**
- `Subscription.nextPaymentDate` `Long?` → **`LocalDate?`**. Room şeması
  değişmedi; dönüşüm `SubscriptionMapper`'da, **sistem saat diliminde**.
  Yön açıkça yazıldı: saklanan an o günün yerel takvim günü olarak okunuyor,
  yazarken yerel gece yarısına çevriliyor. UTC okumak Greenwich'in doğu veya
  batısındaki kullanıcı için tarihi bir gün kaydırırdı.
- `PaymentCountdown` domain'de saf: `Upcoming(days)` / `DueToday` /
  `Overdue(days)`. **Bugünün tarihi parametre**, içeride `LocalDate.now()`
  yok. Üç durumu imzasız sayı yerine tip olarak ayırdım — ekran üç farklı şey
  söylüyor, çağıran her seferinde sınırları yeniden keşfetmesin.
- `Clock` Hilt'ten geliyor (`di/TimeModule`). ViewModel'ın "bugün"ü
  enjekte edilebilir olduğu için 10a testleri sabit bir tarihte koşuyor.
- Geri sayım **ViewModel'da** hesaplanıyor, `HomeUiState.countdowns` map'ine
  id ile konuyor. Composable'da hesap yok (§3). Abonelik nesnesinin içine
  konmadı: geri sayım aboneliğin değil, **abonelik ile bugünün** özelliği.
- Ekleme formuna Material3 `DatePicker`. Alan salt okunur — değer yalnızca
  seçiciden gelir, yazılacak bir şey yok. Temizleme diyaloğun içinde
  ("Tarihi temizle"), çünkü alanın üstündeki dokunma katmanı alan içindeki
  bir ikonu erişilemez kılardı.
- Kartta "X gün kaldı". Tarih yoksa **hiçbir şey** yok, yer tutucu yok.

**Renk kararı — `error` rolü**
Gecikmiş durum için `MaterialTheme.colorScheme.error` seçildi; yeni renk
eklenmedi. Kartta metin olarak okunabilen tanımlı roller yalnızca `primary`
(fiyat için kullanılıyor) ve `onSurface` (ad için kullanılıyor);
`secondary`/`tertiary` pastel, beyaz kart üstünde metin olarak okunmuyor.
`error` "bir şey ters" diyen tek anlamsal rol. **Ama şemamızda tanımlı
değil** — Material baseline kırmızısına düşüyor. Faz 14'teki tanımsız rol
listesine eklendi (`outline`, `onSurfaceVariant` ile birlikte).

**Testler**
- 84 → **101 birim testi**, hepsi geçti.
- `PaymentCountdownTest`: yarın, bugün, dün, hafta, yıl, **artık gün**
  (2028-02-28 → 2028-03-01 = 2 gün, 29 Şubat arada) ve yıl dönümü.
  Hepsi kendi tarihlerini adlandırıyor, hiçbiri koşma anına bağlı değil.
- Mapper: `Long? ↔ LocalDate?`, null, **gün ortası an** (13:00 saklanan an
  aynı takvim gününe düşüyor, bir gün kaymıyor).
- ViewModel: tarihli/tarihsiz kayıt, geçmiş tarih kabulü, tam 10 yıl kabul,
  10 yıl + 1 gün reddi (sınır mesajda argüman olarak), geri sayım map'i.
- Enstrümantasyon **9/9, iki emülatörde**.
- `lintDebug` temiz.

**Değişen dosyalar**
- `domain/model/Subscription.kt` — `LocalDate?`
- `domain/usecase/PaymentCountdown.kt` — yeni
- `data/mapper/SubscriptionMapper.kt` — dönüşüm
- `di/TimeModule.kt` — yeni, `Clock`
- `ui/home/HomeUiState.kt`, `HomeViewModel.kt`, `HomeScreen.kt`
- `ui/home/components/AddSubscriptionSheet.kt`, `SubscriptionCard.kt`
- `res/values/strings.xml`, `res/values-en/strings.xml` — plurals dahil
- `app/build.gradle.kts`, `gradle/libs.versions.toml` — desugaring
- `docs/ARCHITECTURE.md` §17, `docs/ROADMAP.md`, `docs/TESTING.md`

**Commit'ler**
- `b373c14` docs: add date handling decisions
- `22f8ecf` build: enable core library desugaring for java.time
- `d5dae83` feat: model the next payment date as a calendar day
- `9f83b47` feat: pick a next payment date and count down to it
- `6d2acd0` test: cover the countdown, date mapping and date validation
- `581f954` fix: name the date field and its error for screen readers

**Emülatörde bulunan ve düzeltilen — erişilebilirlik**
Tarih alanının üstündeki saydam dokunma katmanı alt ağacı birleştiriyor, bu
yüzden alan ekran okuyucuya **isimsiz bir buton** olarak görünüyordu:
`cd=''`. `clickable(onClickLabel = ...)` eylemi adlandırıyor, düğümü değil.
Katmana `semantics { contentDescription }` eklendi; artık
`"Next Payment (optional), Sep 10, 2026"` diye okunuyor. Aynı sebeple hata
metni de ağaçta yoktu — o da açıklamaya katıldı:
`"..., Dec 31, 2040, The date can be at most 10 years ahead"`.

Emülatörde bulunan ikinci hata: açıklama metni ilk turda ham `%1$s`
gösteriyordu, `stringResource` argümanı verilmemişti (9b-2'deki hatanın
aynısı — aynı tuzağa iki kez düşüldü).

**Emülatör test sonuçları** (cihaz tarihi 3-4 Eylül 2026)

| # | Test | Dar (API 29, 360dp) | Geniş (API 34, 411dp) |
|---|---|---|---|
| 1 | Tarih seç, kaydet, gün sayısı | geçti — 3 Eylül'de 10 Eylül seçildi → **"7 days left"** | geçti — 4 Eylül'de 11 Eylül → **"7 days left"** |
| 2 | Tarihsiz kayıt | geçti — "NoDate, TRY 10.00", gösterge yok, çökme yok | — |
| 3 | Geçmiş tarih | geçti — 1 Eylül → **"2 days overdue"** | — |
| 4 | Bugün | geçti — 3 Eylül → **"Due today"** | — |
| 5 | 10 yıldan uzak | geçti — 31.12.2040 girildi, alan hatası, **kaydedilmedi**, çökme yok | — |
| 6 | Sheet layout regresyonu | geçti — aşağıda | geçti — aşağıda |
| 7 | fs2.0 taşma | geçti — seçici `[0,96][720,1232]`, kart satırları büyüyor, kırpılma yok | — |
| 8 | Döndürme, tarih seçili | geçti — "Sep 25, 2026" yatay-dikey korundu | — |
| 9 | Sabit regresyon listesi | **21/21 geçti** | **21/21 geçti** |

**Test 1 elle doğrulama:** cihaz `date` çıktısı `Thu Sep 3 2026`, seçilen gün
`Thursday, September 10, 2026` → 10 − 3 = **7**. Kart "7 days left" dedi.
Geniş cihazda `Fri Sep 4` + `September 11` → 7, aynı sonuç.

**Test 6 — 9b-1 hotfix'i ayakta.** Yeni alan kaydırma bölgesine girdi,
Kaydet butonunun koordinatı **hiçbir kombinasyonda değişmedi**:

| Cihaz | fs | Klavye | Kaydet (10a) | 9b-2 referansı |
|---|---|---|---|---|
| Dar | 1.0 | kapalı | `[48,1104][672,1200]` | **aynı** |
| Dar | 1.0 | açık | `[48,606][672,702]` | **aynı** |
| Geniş | 1.0 | kapalı | `[63,2107][1017,2233]` | **aynı** |
| Geniş | 1.0 | açık | `[63,1287][1017,1413]` | **aynı** |
| Geniş | 2.0 | kapalı | `[63,2092][1017,2232]` | **aynı** |
| Geniş | 2.0 | açık | `[63,1272][1017,1412]` | **aynı** |

Değişen tek şey kaydırma bölgesinin **yukarı doğru büyümesi** (dar fs1.0'da
`[0,513]` → `[0,361]`, geniş fs1.0'da `[0,1328]` → `[0,1128]`): sheet uzadı,
Kaydet yerinde kaldı. Kaydırınca da sabit kalıyor.

**Gözlem — düzeltilmedi**
font_scale 2.0'da uzun abonelik adları kartta kırpılıyor ("TodayDue" →
"TodayDu"). Ad sütununun genişliği bu fazda değişmedi (`weight(1f)` aynı
kaldı, sadece içine ikinci satır eklendi), yani bu **mevcut bir davranış**,
geri sayımın getirdiği bir şey değil. Faz 14 veya 15'te ele alınabilir.

**Karşılaşılan sorunlar**
- Emülatörler oturum boyunca üç kez kendiliğinden kapandı. Bir turda bunu
  fark etmeden ölçüm alındı ve **eski dump dosyaları okundu**; sonuç
  geçersiz sayıldı ve emülatörler yeniden başlatılıp tekrarlandı.
  Ders: `adb devices` boşsa okunan dosya bir öncekinin kalıntısıdır.
- Geniş emülatörde regresyon turunun ilki `uiautomator dump`'ın
  `null root node` dönmesi yüzünden ilk aboneliği ekleyemedi; tur baştan
  koşuldu.

**Sonraki faz için not**
- 10b: WorkManager + bildirim. `PaymentCountdown` ve `Clock` hazır;
  zamanlama `java.time` üzerinden kurulacak (desugaring artık açık).
- Geri sayım her emisyonda hesaplanıyor; **gece yarısını açık geçen bir
  oturum** dünün sayısını gösterir. 10b'nin zamanlayıcısı bunu da
  tazeleyebilir.
- `billingPeriod` hâlâ UI'a bağlı değil ve tarih geçince **ilerletme yok** —
  Faz 12'nin işi (§17).

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

### Hotfix — IME insets: ölçüm ve `adjustResize` kararı (2026-09-03)

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

**Devam — `adjustResize` uygulandı (2026-09-03)**

Yukarıdaki ölçüm `imePadding()` yolunu kapattı. Sohbette verilen karar:
manifestte `MainActivity`'ye `android:windowSoftInputMode="adjustResize"`.
Tek satır, Compose kodunda hiçbir değişiklik yok.

**Neden `enableEdgeToEdge` değil:** ikisi de sorunu çözer, ama edge-to-edge
insets modelini baştan değiştirir — durum ve gezinme çubuğu payları her
ekranda elle uygulanmak zorunda kalır. O iş Faz 16'daki `targetSdk`
yükseltmesine ait ve ROADMAP'e madde olarak eklendi. `adjustResize`
**bilinçli olarak geçicidir**: edge-to-edge'e geçildiğinde sistem onu yok
sayar, o gün kaldırılacak.

**Kur ekranı — asıl sınav.** Klavye üst kenarı artık kaydırma görünümünün alt
sınırından okunuyor, çünkü pencere gerçekten küçülüyor.

| Cihaz | fs | Klavye üstü | Kaydet (kaydırma sonrası) | Varsayılana dön | Kaç fiske |
|---|---|---|---|---|---|
| Dar API 29 | 1.0 | y=**870** | `[32,622][688,718]` | `[32,742][688,838]` | 1 |
| Dar API 29 | 2.0 | y=**870** | `[32,600][688,707]` | `[32,731][688,838]` | 1 |
| Geniş API 34 | 1.0 | y=**1633** | `[42,1308][1038,1434]` | `[42,1466][1038,1592]` | 1 |
| Geniş API 34 | 2.0 | y=**1633** | `[42,1279][1038,1419]` | `[42,1451][1038,1591]` | 1 |

Dördünde de iki buton **tam görünür** ve dokunma alanları eşiğin üstünde
(dar 96px = 48dp, fs2.0'da 107px = 53,5dp; geniş 126px = 48dp, fs2.0'da
140px = 53,3dp). Karşılaştırma için 9b-2'deki hâl: dar fs1.0 ve geniş fs2.0
kombinasyonlarında butonlara **hiç** ulaşılamıyordu, kaydırma payı yoktu.

USD, EUR ve GBP alanları ayrı ayrı odaklandı; üçünde de aynı sonuç — pencere
aynı yere küçülüyor, alanın hangisi olduğu fark etmiyor.

**Ekleme sheet'i — regresyon yok.** Endişe, `ModalBottomSheet`'in kendi
`imePadding()`'i ile pencere küçülmesinin üst üste binmesiydi. Dört
kombinasyon 9b-1 hotfix'indeki değerlerle karşılaştırıldı:

| Cihaz | fs | Klavye | Ölçülen | 9b-1 referansı |
|---|---|---|---|---|
| Dar | 1.0 | kapalı | Scroll `[0,513][720,1056]` Kaydet `[48,1104][672,1200]` | **aynı** |
| Dar | 1.0 | açık | Scroll `[0,144][720,558]` Kaydet `[48,606][672,702]` | **aynı** |
| Dar | 2.0 | kapalı | Scroll `[0,226][720,1045]` Kaydet `[48,1093][672,1200]` | **aynı** |
| Geniş | 1.0 | kapalı | Scroll `[0,1328][1080,2043]` Kaydet `[63,2107][1017,2233]` | **aynı** |
| Geniş | 1.0 | açık | Scroll `[0,508][1080,1223]` Kaydet `[63,1287][1017,1413]` | **aynı** |
| Geniş | 2.0 | kapalı | Scroll `[0,1098][1080,2029]` Kaydet `[63,2092][1017,2232]` | **aynı** |
| Geniş | 2.0 | açık | Scroll `[0,278][1080,1209]` Kaydet `[63,1272][1017,1412]` | **aynı** |

Tek farklı satır dar fs2.0 klavye açık: sheet dibi 782 (9b-1'de 870), Kaydet
`[48,595][672,702]` (9b-1'de `[48,683][672,790]`). **Bu bir regresyon değil,
klavye yüksekliği farkı:** 9b-1 ölçümünde ondalık klavye açıktı, bu turda ad
alanına odaklanıldığı için öneri şeridi olan metin klavyesi açıldı ve daha
uzun. İlişki aynı kaldı — sheet dibi = klavye üst kenarı, Kaydet'in altı
sheet dibinden tam **80px** (SheetBottomPadding 40dp) yukarıda, iki turda da.

Çift küçülme olsaydı sheet dibi klavye üstünün bir klavye boyu daha yukarısına
düşerdi; düşmedi. Sebebi yapısal: sheet kendi dialog penceresinde çiziliyor,
`windowSoftInputMode` Activity'nin penceresine uygulanıyor. İki mekanizma
birbirine değmiyor. Kaydırınca Kaydet yine sabit kalıyor.

**Diğer ölçümler**
- Ana ekran ve ayarlar ekranı düzeni klavye kapalıyken **birebir aynı**
  (`Total Monthly [42,338][1038,633]`, chip'ler `[84,530][152,583]` …).
  Beklenen: `adjustResize` yalnızca klavye açıkken devreye giriyor.
- Döndürme, kur ekranı klavye açıkken: yatayda ekran ve yazılan metin
  ("42.857") korunuyor, viewport 1017'ye küçülüyor, dikeyde geri dönüyor.
- Döndürme, sheet klavye açıkken: yatayda sheet açık kalıyor, metin duruyor,
  Kaydet `[423,787][1977,913]`'te görünür.
- Sabit regresyon listesi **her iki emülatörde** koşuldu, hepsi geçti.
  API 29'da iki madde cihaz kısıtı yüzünden atlandı ve geniş emülatörde
  doğrulandı: koyu tema (`cmd uimode night` API 29'da etkisiz) ve Türkçe
  (`cmd locale` API 33+).

**Doğrulama**
- `assembleDebug --rerun-tasks` geçti, yeni uyarı yok.
- `testDebugUnitTest --rerun-tasks`: **84 test, hepsi geçti.**
- Emülatör ayarları geri alındı.

**Ölçüm sırasında öğrenilen**
Otomasyon notu: ekranın sağ kenarına 60px'ten yakın başlayan `input swipe`,
API 34'te **geri hareketi** olarak yorumlanıyor ve uygulamadan çıkıyor.
Kaydırarak silme testleri kenardan en az 100px içeriden başlatılmalı.

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
