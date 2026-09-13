# ROADMAP.md — SubTrack Yol Haritası

Fazlar sırayla yapılır. Bir fazın "Bitti" kriterleri sağlanmadan sonrakine
geçilmez. Tüm çalışma `Elina` branch'inde yapılır; branch açılmaz. Faz bitince
**kullanıcı** tag atar: `git tag phase-N-done`. CC tag atmaz
(CLAUDE.md §5, WORKFLOW.md §6).

**Durum işaretleri:** ⬜ başlanmadı · 🟡 devam ediyor · ✅ bitti

> **Kapsam notu (2026-08-29):** Faz 9-15 artık **v1.0 kapsamındadır** — sonraki
> sürümler değil, bu sürümün kalanı. `PROJECT_SPEC.md` §4 yeniden yazıldı;
> düzenleme, kur yönetimi, yenileme tarihi, hatırlatma, kategoriler, ödeme
> periyodu, geçmiş takibi ve istatistik v1.0'a alındı. Ağ bağlantısı v1.1'e,
> hesap ve senkronizasyon v2.0'a taşındı.

---

## ✅ Faz 0 — Acil Düzeltmeler

Uygulama fiilen kullanılamaz durumdaydı; bu fazda çalışır hale getirildi.

- [x] `onDismissRequest` boş — bottom sheet kapanmıyor (MainActivity.kt:159)
- [x] Kaydet butonu sheet'i kapatmıyor (MainActivity.kt:194)
- [x] Kaydetme sonrası form alanları temizlensin
- [x] `remember` → `rememberSaveable` (ekran döndürmede veri kaybı) — geçici,
      Faz 5'te Room devralacak
- [x] `ANALYSIS_REPORT.md` → `docs/archive/` altına taşınsın

> `SubTrack.md` maddesi kaldırıldı: bu dosya hiç repoya eklenmemişti, içeriği
> gerçeği yansıtmadığı için kullanıcı bilerek dışarıda bıraktı.

**Bitti:** Uygulama açılıyor, abonelik eklenebiliyor, sheet kapanıyor,
ekran döndürmede liste duruyor.

---

## ✅ Faz 1 — Altyapı Temizliği

Üç promptta yürütüldü: **1a build altyapısı**, **1b tema ve metinler**,
**1c kontrast düzeltmeleri.**

- [x] `gradle/libs.versions.toml` (version catalog) kurulumu/doğrulaması
- [x] Compose BOM ↔ `activity-compose` ↔ `lifecycle` sürüm çakışması
      `./gradlew :app:dependencies` ile doğrulandı — **çakışma gerçekti.**
      BOM 2025.11.01'e alındı, her şey Compose 1.9.5 / material3 1.4.0'a hizalandı.
- [x] Kotlin plugin durumu netleşti — **Faz 0'da doğrulandı:** AGP 9 Kotlin
      plugin'lerini yerleşik getiriyor, `version.ref` verilirse *"already on
      the classpath with an unknown version"* hatası çıkıyor. Sürümsüz alias
      ile uygulanmalı.
- [x] KSP eklentisi eklendi (kapt kullanılmadı) — **not:** yukarıdaki kısıt
      KSP için geçerli değilmiş, AGP 9 KSP'yi getirmiyor. Açık sürüm gerekti:
      `2.2.10-2.0.2`. Ayrıca `gradle.properties`'e
      `android.disallowKotlinSourceSets=false` eklendi (geçici, bkz.
      ARCHITECTURE §12).
- [x] Kod içi Türkçe yorumlar İngilizceye çevrildi (CLAUDE.md §2)
- [x] Kotlin sürüm tutarsızlığı giderildi: `libs.versions.toml` 2.0.21 →
      2.2.10, AGP 9'un gerçekten kullandığı derleyiciyle hizalandı
- [x] `Theme.kt` devreye alındı: hardcoded renkler →
      `MaterialTheme.colorScheme`. Koyu temaya `DarkBackground` eklendi,
      eskiden `background` ve `surface` aynı renkti.
- [x] `Type.kt` `MaterialTheme(typography = ...)` ile bağlandı
- [x] `Dimens.kt` oluşturuldu, hardcoded `dp` değerleri oradan geliyor
- [x] Tüm kullanıcı metinleri `strings.xml`'e taşındı + `values-en/`
- [x] `SwipeToDeleteRow`'daki "Sil" metni `strings.xml`'e taşındı
- [x] Kullanılmayan import/kod temizliği — `colors.xml` tamamen silindi
      (sıfır referans), `welcome_message` kaldırıldı
- [x] `.gitignore`: `.idea/` altındaki makineye özel dosyalar
      (`emulatorDisplays.xml` vb.) hariç tutuldu
- [x] `.gitignore` tamamlandı: `local.properties` tekilleşti, `.kotlin/`,
      `*.apk`, `*.aab`, `*.jks`, `*.keystore` eklendi
- [x] **1c:** açık tema kontrastı düzeltildi. `primary` → `DeepBlue`
      (metin aksanı, 1.78:1 → 5.41:1), `primaryContainer` → `PastelBlue`
      (dolu yüzeyler, görünüm aynı), `background` → `SoftBlueGray`
      (kart ayrışması 1.05:1 → 1.37:1). Tüm çiftler WCAG AA geçiyor.

**Bitti:** Sıfır hardcoded metin, sıfır hardcoded renk. Koyu tema fiilen
çalışıyor. Temiz derleme, sıfır uyarı.

---

## ✅ Faz 2 — Domain + Room Şeması

- [x] `domain/model/`: `Subscription`, `Money`, `BillingPeriod`,
      `SubscriptionCategory` — katmanda tek bir `import` yok, saf Kotlin
- [x] `data/local/entity/SubscriptionEntity` — v1.5'e kadar tüm alanlar dahil:
      `id`, `name`, `priceInCents`, `currencyCode`, `billingPeriod`,
      `nextPaymentDate`, `category`, `iconKey`, `createdAt`
- [x] `SubscriptionDao`: `observeAll(): Flow<List<...>>`, `insert`, `deleteById`,
      `getById`, `update`
- [x] `SubTrackDatabase` (`@Database`, version 1, `exportSchema = true`)
- [x] `SubscriptionMapper` — Entity ↔ Domain
- [x] Room 2.8.4 katalogda yoktu, eklendi; `room-compiler` `ksp()` ile bağlandı

**Bitti:** Room derleniyor, KSP kod üretiyor, henüz UI'a bağlı değil.

---

## ✅ Faz 3 — Repository Katmanı (manuel DI)

- [x] `domain/repository/SubscriptionRepository` arayüzü
- [x] `data/repository/SubscriptionRepositoryImpl` — entity sınırı burada
      kapanıyor, `data/` dışında hiç bahsi yok
- [x] Nesneler `SubTrackApplication` içinde elle kuruldu (`by lazy`),
      manifest'e `android:name` ile kaydedildi

**Bitti:** Repository çalışıyor. Manuel DI'ın ne kadar hantal olduğu görülmüş
durumda — Hilt'in gerekçesi anlaşıldı.

---

## ✅ Faz 4 — Hilt

- [x] Hilt eklentisi ve bağımlılıkları — 2.60.1, `hilt-compiler` `ksp()` ile.
      **Not:** plugin sadece `:app`'te tanımlı, root'ta değil; KSP ile aynı
      scope'ta olması zorunlu (bkz. ARCHITECTURE §12).
- [x] `@HiltAndroidApp`, `@AndroidEntryPoint`
- [x] `DatabaseModule` (`@Provides`), `RepositoryModule` (`@Binds`)
- [x] Manuel kurulum kodu silindi — `SubTrackApplication` boş gövde

**Bitti:** Uygulama Hilt üzerinden ayağa kalkıyor, elle nesne kurulumu yok.

---

## ✅ Faz 5 — ViewModel + UiState + UI Bağlama

İki promptta yürütüldü: **5a veri akışı**, **5b dosya parçalama.**

- [x] `HomeUiState` ve `HomeEvent`
- [x] `HomeViewModel` (`@HiltViewModel`), `stateIn` ile Flow → StateFlow
- [x] Aylık toplam hesabı ViewModel'a taşındı (`derivedStateOf` kaldırıldı)
- [x] `MainActivity`'deki `mutableStateListOf` tamamen kaldırıldı — monoton id
      sayacı, yerel `Subscription` data class'ı ve seed veri de gitti
- [x] `collectAsStateWithLifecycle` ile bağlantı
- [x] `androidx.hilt:hilt-navigation-compose` eklendi — **1.3.0**, çünkü 1.4.0
      compileSdk 37 / AGP 9.1.0 istiyor (bkz. ARCHITECTURE §12)
- [x] **5b:** `MainActivity` parçalandı — 380 → **31 satır**. `HomeScreen`,
      `DashboardCard`, `SubscriptionCard`, `AddSubscriptionSheet`,
      `SwipeToDeleteRow` ayrı dosyalarda; hiçbiri 300 satırı geçmiyor.
- [x] **5b:** `showBottomSheet` `rememberSaveable`'a çevrildi; form alanları da
      döndürmede korunuyor
- [x] **5b:** Preview'lar çalışıyor — `HomeScreen` durumsuzlaştı, altı preview
      eklendi. Ayrıca `Theme.kt`'deki şemalar `by lazy`'ye alındı (class-init
      hatası tüm preview'ları öldürüyordu, bkz. ARCHITECTURE §12).
- [x] **5b:** `SubscriptionCard` dokunma alanı ölçüldü: **56dp**, 48dp eşiğini
      8dp payla geçiyor. `Dimens` değiştirilmedi.

**Bitti:** Veri Room'dan geliyor, uygulama kapanıp açılınca duruyor,
`MainActivity` 50 satırın altında. ✅ (31 satır)

---

## ✅ Faz 6 — CRUD Tamamlama

- [x] Ekleme repository üzerinden
- [x] Girdi doğrulama: boş ad, geçersiz/negatif/sıfır fiyat, üst sınır ve
      ondalık basamak → **alan bazlı** hata mesajı (`nameError`, `priceError`)
- [x] Fiyat parse mantığı ViewModel'da (`String` → `Long` kuruş, `BigDecimal`
      üzerinden)
- [x] Kaydırarak silme repository'yi tetikliyor — **Faz 5a'da mecburen
      yapıldı:** `mutableStateListOf` kalkınca `remove(sub)` de kalkmak
      zorundaydı.
- [x] Silme sonrası Snackbar ile geri al (undo) — öğe **eski sırasına**
      dönüyor (aynı id ile ekleniyor)
- [x] Repository hata yönetimi karara bağlandı: **`Result<T>` kullanılmıyor.**
      Doğrulama ViewModel'da, DB hataları `try/catch` ile yakalanıp `UiText`'e
      çevriliyor. ARCHITECTURE §9 güncellendi. (Faz 3'ten ertelenmişti.)

**Bitti:** Ekleme/silme kalıcı, hatalı girdi engelleniyor, yanlış silme
geri alınabiliyor.

---

## ✅ Faz 7 — Test Altyapısı

- [x] JUnit, Turbine 1.2.1, coroutines-test kurulumu — sonuncusu uygulamanın
      çözümlediği coroutines 1.9.0 ile aynı sürüme sabitlendi
- [x] `FakeSubscriptionRepository` elle yazıldı (mock kütüphanesi yok).
      **Not:** `FakeSubscriptionDao` gerekmedi — DAO gerçek in-memory Room
      üzerinde test ediliyor, sahtesine ihtiyaç kalmadı.
- [x] `HomeViewModel` testleri (19): yükleme, toplam hesabı, doğrulama, silme,
      undo. Fiyat doğrulaması public yüzeyden test edildi (`parsePrice` private,
      bkz. ARCHITECTURE §11)
- [x] `SubscriptionMapper` testleri (8)
- [x] `SubscriptionDao` enstrümantasyon testi (8, in-memory DB, cihazda koştu)

**Bitti:** `./gradlew :app:testDebugUnitTest` geçiyor (28 test), enstrümantasyon
cihazda geçiyor (9 test), kritik yollar kapsanmış.

---

## ✅ Faz 8 — UX Cilası

İki promptta yürütüldü: **8a** yükleme/hata/erişilebilirlik, **8b** boş durum
ekranı. **Hepsi bitti.**

- [x] **8b:** Boş durum ekranı — soluk ikon + başlık + alt satır, düğme yok
      (FAB zaten sağ altta). `ui/common/EmptyState` metinleri parametre alır;
      Faz 11'deki filtre boşluğu aynı bileşenin varyantı olacak
- [x] **8a:** Yükleme durumu — 300 ms gecikmeli gösterge, kırpışmıyor
- [x] **8a:** Hata gösterimi (Snackbar) — Faz 6'da kurulmuştu, doğrulandı
- [x] **8a:** Erişilebilirlik: `contentDescription`, dokunma alanı ≥ 48dp.
      Ölçüldü: `SubscriptionCard` 56dp, FAB 56dp, Kaydet butonu görsel 40dp ama
      Material3 dokunma alanını 48dp'ye kendisi genişletiyor. `SwipeToDeleteRow`
      semantics'i hotfix serisinde baştan konmuştu.
- [x] **8a:** Koyu tema gözden geçirildi — yeni çiftlerin hepsi AA geçiyor
- [x] **8a:** Açık tema gözden geçirildi — Faz 1c'den sonra eklenen tüm
      metin/zemin çiftleri ölçüldü, palete dokunulmadı
- [x] **8a:** `HomeUiState.isLoading` UI'a bağlandı (Faz 5a'dan beri ölüydü)
- [x] **8a:** Dashboard toplamı `mergeDescendants` ile tek odak durağı oldu —
      "Aylık Toplam, 219.89 TL" birlikte okunuyor

**Bitti:** Hiçbir durumda boş/kırık ekran yok.

---

## ✅ Faz 9 — Para Birimi Seçimi

Üç promptta yürütüldü: **9a** para birimi ve normalizasyon, **9b-1** ayarlar
ekranı + Navigation + DataStore, **9b-2** düzenlenebilir kurlar. **Hepsi bitti.**

- [x] **9a:** Ekleme formuna para birimi seçici (TRY, USD, EUR, GBP) —
      `FilterChip` sırası, tek dokunuş
- [x] **9a:** Locale'e göre para formatlama — `NumberFormat`, para birimi
      açıkça set ediliyor
- [x] **9a:** Fiyat biçimlendirmesi `Locale.US`'tan cihaz locale'ine geçti
- [x] **9a:** Sabit kur tablosu ile toplam normalizasyonu — tek çıpa, her
      çift tek adımda, HALF_UP
- [x] **9b:** Kurlar ayarlardan elle düzenlenebilsin — v1.0'da otomatik
      güncelleme yok, elle giriş onun yerini tutuyor (bkz. PROJECT_SPEC §5).
      Ayrı kur ekranı, alan bazlı doğrulama, varsayılana dönme, son düzenleme
      tarihi. Üst sınır `Long` taşmasına göre belirlendi (ARCHITECTURE §15).
- [x] **9b:** Ana para birimi tercihi (DataStore) — ayarlar ekranı, Navigation,
      Preferences DataStore; toplam seçilen para biriminde hesaplanıyor

**Bitti:** Karışık para birimli abonelikler doğru toplanıyor.

---

## ✅ Faz 10 — Yenilenme Tarihi + Hatırlatma

Dört promptta yürütüldü: **10a** tarih seçici ve geri sayım, **10b** WorkManager
+ bildirim, **10c-1** Ayarlar'daki izin akışı, **10c-2** bağlamsal izin isteği.
**Hepsi bitti.**

- [x] **10a:** Tarih seçici, sonraki ödeme tarihi — opsiyonel, geçmiş tarih
      kabul, üst sınır 10 yıl (ARCHITECTURE §17)
- [x] **10a:** "X gün kaldı" göstergesi — gelecek, bugün ve gecikmiş; tarih
      yoksa gösterge çıkmaz. Tarih geçince **ilerletme yok**, o Faz 12'nin işi
      (12-2'de geldi; "gecikmiş" hâli o fazda anlamsızlaşıp kaldırıldı)
- [x] **10b:** WorkManager + yerel bildirim — günde bir kez, yerel 09:00 hedefi,
      tek özet bildirim. Bildirilenler: bugün, 1 gün kalan, 1-3 gün gecikmiş
      (ARCHITECTURE §18; gecikme penceresi 12-2 hotfix'inde kaldırıldı)
- [x] **10c-1:** `POST_NOTIFICATIONS` izin akışı, Ayarlar'dan — üç halli durum
      makinesi (açık / istenebilir / yalnızca sistem ayarları), kanal düzeyi
      tespit dahil (ARCHITECTURE §18)
- [x] **10c-2:** Bağlamsal istek — tarihi olan **ilk** abonelik kaydedildiğinde,
      sheet kapandıktan sonra, yalnızca bir kez

**Bitti:** Yaklaşan ödeme için bildirim geliyor, izin reddedilse de uygulama çalışıyor.

---

## ✅ Faz 11 — Kategoriler

İki promptta yürütüldü: **11a** kategori seçimi ve gösterimi, **11b** filtre.

- [x] **11a:** Kategori seçimi — ekleme formunda `FilterChip` sırası
      (para birimi seçicisinin deseni), varsayılan `OTHER`, zorunlu değil
- [x] **11a:** Kartta gösterim — kategori yalnızca `OTHER` değilse çizilir,
      erişilebilirlik cümlesine de eklenir
- [x] **11b:** Kategoriye göre filtre, kategori bazlı toplam —
      `ui/common/CategoryFilterBar`, listenin üstünde yatay kayan beş chip
      ("Tümü" + dört kategori). Filtre `HomeUiState.categoryFilter`'da yaşar,
      kalıcı değildir, uygulama açılışında "Tümü"ye döner.
- [x] **11b:** **Filtre hiçbir şeyle eşleşmediğinde boş durum** — Faz 8b'deki
      `ui/common/EmptyState` bileşeninin varyantı (`EmptyCategory`). Bileşenin
      imzası değişmedi; yalnızca iki yeni metin eklendi.

**"Kategori bazlı toplam" böyle okundu:** filtre seçiliyken dashboard
toplamı **görünen satırların** toplamıdır; ayrı bir "kategori toplamı"
göstergesi eklenmedi. Ekranda iki toplam olsaydı hangisinin ne olduğu
sorulurdu; tek toplamın filtreyi izlemesi aynı bilgiyi tek yerde veriyor.

**Bitti:** Abonelikler kategoriyle kaydediliyor, kartta görünüyor, listeyi
kategoriye göre daraltmak mümkün ve boş kalan kategori kendini söylüyor.

---

## ✅ Faz 12 — Ödeme Periyodu

İki promptta yürütüldü: **12-1** seçim, normalizasyon ve toplam görünümü,
**12-2** tarih ilerletme.

- [x] **12-1:** Aylık/yıllık/haftalık seçimi — formda üçüncü chip sırası,
      varsayılan `MONTHLY`, zorunlu (kartta her zaman görünür)
- [x] **12-1:** Yıllık → aylık maliyet normalizasyonu —
      `BillingPeriod.paymentsPerYear` ile çarpma, tek yuvarlama
      (`ARCHITECTURE.md` §6)
- [x] **12-1:** Aylık/yıllık toplam görünümü arasında geçiş — dashboard'ın
      altında iki chip; yıllık figür aylık figürün 12 katı değil, aynı
      bölünmemiş ara değer
- [x] **12-2:** **Tarih ilerletme** — `NextPaymentDate.onOrAfter`, okuma
      anında hesaplanır, veritabanına **yazılmaz**; saklanan tarih çıpa olarak
      kalır. Çıpadan sayıldığı için ay sonu kaybolmuyor ve uzun geçmiş
      tarihlerde döngü yok (`ARCHITECTURE.md` §17).
- [x] **12-2:** **Hatırlatmadaki gecikme penceresi gözden geçirildi** — ölçüldü
      ve raporlandı: bildirim çıpayı, ekran ilerletilmiş tarihi okuyordu.
- [x] **12-2 hotfix:** **Bildirim de ilerletilmiş tarihi okuyor, gecikme
      penceresi kalktı.** Kural artık "bugün ödenecek + 1 gün kalan".
      `PaymentCountdown.Overdue` böylece ulaşılamaz hâle geldi ve kaldırıldı.
      Kabul edilen bedel (worker bir günü kaçırırsa o döngünün bildirimi
      kaybolur) `ARCHITECTURE.md` §18'de yazılı.

**Bitti:** Abonelikler periyoduyla kaydediliyor, toplam gerçek maliyeti
gösteriyor ve geçmiş bir ödeme tarihi kendi döngüsünde bir sonraki güne
taşınıyor.

---

## ✅ Faz 12a — Geçmiş Takibi

Aylık toplamın zaman içindeki anlık görüntüleri. `PROJECT_SPEC.md` §1'deki
"geçen aya göre ne değişti?" vaadinin veri tarafı.

- [x] `MonthlySnapshotEntity` — dönem, toplam (kuruş), para birimi, kayıt zamanı.
      Dönem tek bir `Int`: `yıl * 100 + ay` (202609), birincil anahtar —
      sıralanabilir, tekil, `run-as` dökümünde okunabilir (`ARCHITECTURE.md` §19)
- [x] `MonthlySnapshotDao` — `observeAll` (en eski ay önce), `@Upsert`,
      `getByPeriod`
- [x] Anlık görüntünün ne zaman yazılacağı karara bağlandı: **her değişiklikte
      üzerine yazma.** Üç seçeneğin gerekçesi ve kabul edilen bedel §19'da
- [x] Domain modeli + mapper, mevcut desene uygun; `MonthlySnapshotRecorder`
      veri katmanında — ekranın filtreli toplamına erişemediği için filtre tuzağı
      **ulaşılamaz**
- [x] **Faz 2'den beri ilk şema değişikliği:** sürüm 1 yeniden üretildi,
      migration yazılmadı, `app/schemas/1.json` commit'e dahil

> **Şema sürümü — uygulandı:** Sürüm 1 **yeniden üretildi**, migration
> yazılmadı. Uygulama yayınlanmadığı için korunacak kullanıcı verisi yoktu.
> Kural ve gerekçesi ARCHITECTURE *"Şema sürümlemesi"* başlığında; **yayından
> sonra migration zorunlu hale geliyor** ve o satır Faz 16'da tekrar okunacak.

---

## ⬜ Faz 13 — İstatistik

- [ ] Kategori dağılım grafiği
- [ ] Aylık trend — **Faz 12a'daki anlık görüntüleri okur**, o faz olmadan
      gösterecek veri yok
- [ ] **Ana ekranda "geçen aya göre" karşılaştırması** — Faz 12a'dan taşındı.
      Karşılaştırma en az **iki ayrı ayın** verisini gerektiriyor; 12a'nın
      bittiği gün tabloda tek ay vardı, dolayısıyla gösterilecek bir şey yoktu.
      Veri tarafı hazır, kalan iş gösterim
- [ ] "O ay hiç abonelik yoktu" ile "o ay kayıt yok" **ayrı çizilmeli** — 12a
      boş listeyi bilerek `0` olarak kaydediyor (§19), grafik bunu yokluktan
      ayırmalı
- [ ] Eski kayıtların **kendi para biriminde** yazıldığı unutulmamalı — satır
      `currencyCode` taşıyor, bugünkü tercihle yorumlanmamalı
- [ ] En pahalı abonelikler

---

## ⬜ Faz 14 — Tema Tamamlama

- [ ] Dynamic color (Material You, Android 12+)
- [ ] Manuel tema tercihi (sistem/açık/koyu)
- [ ] Koyu temada kart ↔ arka plan ayrımı 1.29:1, gözden geçirilsin
- [ ] **Renk paleti bütün olarak yeniden ele alınacak.** Tanımlanmamış roller
      Material baseline'ına düşüyor ve palet dışı renkler çıkıyor: Snackbar'daki
      "Geri al" eylemi `inversePrimary` tanımsız olduğu için **mor** görünüyor
      (#D0BCFF / #6750A4). Aynı sebeple Faz 9b-1'de ölçülen iki rol daha:
      `outline` tanımsız, chip kenarlığı koyu temada **#49454F**; `onSurfaceVariant`
      tanımsız, seçilmemiş chip etiketi ve ayarlar açıklama metni koyu temada
      **#CAC4D0**, açık temada **#49454F**. İkisi de mor-gri, kontrast AA geçiyor
      (koyu 9.66:1, açık 6.85:1) — sorun okunabilirlik değil, palet tutarlılığı.
      Faz 10a'da üçüncüsü eklendi: `error` tanımsız, Material baseline
      kırmızısına düşüyor. Kartlardaki "gecikmiş" kullanımı 12-2 hotfix'inde
      düştü ama rol hâlâ kullanılıyor (`SwipeToDeleteRow`), yani borç duruyor.
      Paletimiz mavi-camgöbeği ailesinde. Tüm `colorScheme`
      rolleri gözden geçirilip eksikler tanımlanmalı, sadece bunlar değil.
- [ ] Para birimi gösterimi tutarlı hale getirilsin: `NumberFormat` locale'e göre
      bazen sembol bazen ISO kodu yazıyor (EN dilinde toplam "TRY 1.785,45",
      kart "$10.99"). Her yerde sembol mü zorlanacak, her yerde kod mu —
      karar verilecek.

---

## ⬜ Faz 15 — Düzenleme Ekranı

- [ ] Karta tıklayınca düzenleme, Navigation ile ikinci ekran
- [ ] Satır tıklanabilir olsun; şu an silme dışında eylem yok, TalkBack için
      tek yol custom action
- [ ] **Tarih alanı ÇIPAYI göstermeli**, karttaki ilerletilmiş tarihi değil.
      Kart "23 gün kaldı" derken düzenleme ekranı kullanıcının girdiği günü
      açar; ikisi farklı sorulara cevap veriyor (`ARCHITECTURE.md` §17).

---

## ⬜ Faz 16 — Play Store Hazırlığı

- [ ] Uygulama ikonu (adaptive) ve marka kimliği. **Bildirim ikonu da bu işin
      parçası:** `res/drawable/ic_notification.xml` Faz 10b'de konan geçici bir
      siluet, marka çalışmasıyla birlikte yenilenecek.
- [ ] Release imzalama yapılandırması, keystore güvenliği
- [ ] ProGuard/R8 kuralları, release build testi
- [ ] `isMinifyEnabled = true` (R8) — APK boyutu ve açılış süresi düşer
- [ ] `material-icons-extended` kaldırılsın veya daraltılsın: binlerce ikon
      getiriyor, **beş** tanesi kullanılıyor
- [ ] Şablon testler kaldırılsın (`ExampleUnitTest`, `ExampleInstrumentedTest`
      — dolgu: `2+2=4` ve paket adı kontrolü)
- [ ] `targetSdk` Play'in güncel zorunluluğuna yükseltilsin
- [ ] **Edge-to-edge'e geçilsin** — `targetSdk` yükseltmesiyle aynı işin
      parçası, Android 15'te zaten zorunlu. `enableEdgeToEdge()` eklenecek,
      durum ve gezinme çubuğu payları her ekranda elle uygulanacak, klavye
      için `Modifier.imePadding()` kullanılacak. Bugünkü geçici çözüm olan
      manifestteki `windowSoftInputMode="adjustResize"` o zaman **kaldırılacak**
      — edge-to-edge'de sistem onu zaten yok sayıyor. Geçişten sonra
      `TESTING.md`'deki klavye tablosundaki her satır yeniden ölçülmeli;
      gerekçe ve ölçümler `ARCHITECTURE.md` §16'da.
- [ ] Gizlilik politikası — v1.0 çevrimdışı, veri toplanmıyor. **v1.1'de ağ
      eklendiğinde politika ve Data Safety formu güncellenecek**
      (PROJECT_SPEC §4)
- [ ] Ücretlendirme kararı verilsin (peşin / ücretsiz+premium / ücretsiz) —
      kod tarafında etkisi yok, buraya kadar bekleyebilir (PROJECT_SPEC §5)
- [ ] ARCHITECTURE'daki **"Şema sürümlemesi"** kuralı okunsun. Yayından sonra
      migration zorunlu hale geliyor, istisnası yok.
- [ ] TalkBack testi bir kez düzgün yapılsın. Test cihazında (OPPO A15s)
      TalkBack donuyor. **Emülatör de çözüm olmadı:** kurulu iki
      `google_apis_playstore` imajının ikisinde de Android Accessibility
      Suite yok. Google hesabıyla giriş ve üçüncü taraf APK indirme
      reddedildi — **nasıl çözüleceği ayrıca kararlaştırılacak.**
      Bekleyen maddeler: satır ve kart tek odak durağı mı okunuyor,
      "Sil" özel eylemi görünüp çalışıyor mu, para birimi chip'lerinin
      seçili durumu duyuruluyor mu. **Faz 10c-1'de bir tane daha
      eklendi ve teşhis edildi: tıklanabilir satırlar tek odak durağı
      vermiyor, bu tüm satırlar için ele alınacak.**

      Ağaçta aynı sınırlarda **iki düğüm** çıkıyor: biri odaklanabilir ve
      tıklanabilir ama isimsiz, diğeri isimli ama eylemsiz. API 34'te ölçüldü
      ve **proje geneli bir desen** olduğu görüldü - yeni satırın getirdiği bir
      gerileme değil:

      | Öğe | Düğüm | Şekil |
      |---|---|---|
      | Para birimi chip'i (9a) | 2 | `clickable+checkable`, isimsiz → isimli çocuk |
      | Döviz Kurları satırı | 2 | aynı |
      | Ödeme hatırlatmaları satırı (10c-1) | 2 | aynı |
      | Geri oku (stok `IconButton`) | 2 | aynı |
      | **Tarih alanı (10a)** | **1** | `clickable` + `semantics` **çocuksuz** bir overlay `Box`'ta |

      Yani tek düğüm veren tek yapı, 10a'daki **çocuksuz overlay** desenidir:
      semantics ve clickable aynı, çocuğu olmayan öğeye konduğunda tek düğümde
      birleşiyor. Metin çocukları olan bir satırda `semantics`/`clickable`
      sırasını değiştirmek işe yaramadı, `Role.Button` eklemek düğüm sayısını
      **üçe** çıkardı.

      Karar bu maddede verilecek: ya tüm tıklanabilir satırlar overlay desenine
      geçirilir, ya da TalkBack'in isimsiz üst düğüme odaklanıp alt düğümün
      adını okuduğu doğrulanıp mevcut yapı kabul edilir. **İkincisi bu
      imajlarda ölçülemiyor** - Accessibility Suite yok. Stok `IconButton`'ın
      da aynı şekli vermesi, davranışın Compose erişilebilirlik köprüsünün
      normali olduğuna işaret ediyor, ama bu bir gözlem, kanıt değil. **Ağaç tarafı Faz 9b-2'de kapandı:**
      ekleme sheet'i ve ayarlar ekranı, normal ve `--compressed` dump'ta
      birebir aynı yapıyı veriyor — tıklanabilir sarmalayıcı
      `checkable="true"`, seçili olan `checked="true"`. Hotfix'teki ters
      bulgu yanlış düğüme bakmaktan gelmişti. Geriye yalnızca **TalkBack'in
      bunu gerçekten seslendirdiği** doğrulaması kaldı.
- [ ] Strip edilemeyen native kütüphaneler: DataStore'un
      `libdatastore_shared_counter.so`'su ve Compose'un `androidx.graphics:graphics-path`
      üzerinden gelen `libandroidx.graphics.path.so`'su. `stripDebugDebugSymbols`
      ikisini de strip edemiyor, olduğu gibi paketleniyor. Release APK boyutu
      ölçülürken göz önünde bulundurulsun. (İkincisi Faz 10b Görev 0'da fark
      edildi; WorkManager'dan gelmiyor, temiz tabanda da vardı.)
- [ ] **API 24/25 üzerinde bir kez test edilsin** — `minSdk` 24 iddiası hiç
      doğrulanmadı. Fiziksel cihaz API 29, emülatörler 29 ve 34; 24/25 için
      ayrı bir AVD kurulması gerekiyor. Faz 10a'da `java.time` yüzünden
      bu aralıkta çökeceği ortaya çıktı ve desugaring ile kapatıldı, ama
      aralığın kendisi hâlâ hiç çalıştırılmadı.
- [ ] **`desugar_jdk_libs` APK bedeli R8 sonrası ölçülsün** — bugün
      ~200-400 KB tahmin ediliyor, `isMinifyEnabled = true` ile ne kaldığı
      ölçülmedi. Yukarıdaki R8 maddesiyle birlikte yapılır.
- [ ] **WorkManager'ın manifeste otomatik eklediği izinler Play Console'da
      görünecek:** `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`,
      `FOREGROUND_SERVICE`. Dördü de kütüphaneden geliyor, uygulama kodu
      istemiyor. Birleşik manifestte ölçüldü (Faz 10b Görev 0). İzin beyanında
      ve gizlilik politikasında bunlar da açıklanmalı.
- [ ] Play Console Data Safety formu
- [ ] Mağaza görselleri ve açıklama metni
- [ ] Internal testing → production

**Bitti:** `PROJECT_SPEC.md` §7'deki tüm maddeler işaretli.
