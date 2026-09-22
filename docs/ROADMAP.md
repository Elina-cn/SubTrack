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

## ✅ Faz 13 — İstatistik

**13a + 13b bitti, faz kapandı.**

- [x] **13a:** Kategori dağılım grafiği — Compose Canvas ile yatay çubuklar,
      pasta değil; kategori başına renk yok, ayrımı etiket taşıyor. Yüzdeler en
      büyük kalan yöntemiyle **her zaman 100** ediyor (`ARCHITECTURE.md` §20)
- [x] **13a:** En pahalı abonelikler — aylık maliyete göre sıralı, ilk beş
- [x] **13a:** Üçüncü hedef ve ana ekranın üst çubuğundan giriş (§13)
- [x] **13b:** Aylık trend — **Faz 12a'daki anlık görüntüleri okuyor**, son
      **altı ay**, Compose Canvas ile sütunlar. Altı, 360dp'de ölçüldü:
      etiketler fs 2.0'da bile çakışmıyor (`ARCHITECTURE.md` §21)
- [x] **13b: "Geçen aya göre" karşılaştırması — ana ekranda değil, istatistik
      ekranında.** Bu madde 12a'dan "ana ekran" diye taşınmıştı; **taşındı**,
      çünkü dashboard kartı 8a'da `clearAndSetSemantics` ile **tek odak durağı
      ve tek cümle** hâline getirildi ve içine ikinci bir değer koymak onu
      bozardı. Karşılaştırmanın çalışması (trend grafiği) zaten istatistik
      ekranında; cevabı gerekçesinin yanına koymak daha doğru. Gerekçe §21'de
- [x] **13b:** "O ay hiç abonelik yoktu" ile "o ay kayıt yok" **ayrı çiziliyor**
      — sıfır kaydedilen ay yalnızca iz, kaydı olmayan ay hiçbir şey; ekran
      okuyucu ikisini sözcükle ayırıyor ("0,00 TL" ↔ "kayıt yok")
- [x] **13b:** Eski kayıtlar **kendi para biriminde** yorumlanıyor — bugünkü
      ana para biriminde olmayan aylar **çevrilmiyor**, çizilmiyor ve
      **sayılarak kullanıcıya söyleniyor**. Gerekçe ve seçilmeyen iki yol §21'de
- [x] **13b:** Yeterli veri yok hâli istisna değil **varsayılan** olarak ele
      alındı; sıfır ve tek aylık durumlar tek bir cümleyle kapatıldı

> **Renklendirme Faz 14'e bırakıldı.** 13a'da çubuklar tek renk çizildi: paletimizde
> dört ayrı **tanımlı** rol yok ve şimdi icat etmek Faz 14'ün geri alacağı bir
> borç olurdu (§20). Palet bütün olarak ele alınırken kategori başına renk
> yeniden değerlendirilsin. Koyu şemada `primary` ile `primaryContainer`'ın aynı
> renk olduğu da orada çözülmeli — 13a bunu çubuk izini saydamlaştırarak geçti.
>
> **Faz 14'e ikinci madde:** 13b'de iz saydamlığının bir **tavanı** olduğu
> ölçüldü (sütun–iz kontrastı açık temada 3,01:1, sınırın tam üstünde). Bu
> yüzden "sıfır kaydedilmiş ay" ile "kaydı olmayan ay" gözle zor ayrılıyor;
> ayrımı şimdilik cümle taşıyor. Palet elden geçerken bu da çözülsün (§21).

---

## ✅ Faz 14 — Tema Tamamlama

**14a ve 14b bitti, faz kapandı.**

- [x] **14a: Renk paleti bütün olarak yeniden ele alındı.** Pastel mavi-camgöbeği
      → **koyu zümrüt + altın**; gerekçe ürün kararı, uygulama para takip ediyor.
      Temel kural ölçümle kondu: altın beyazda **2,42:1** (grafik için gereken
      3:1'i bile geçmiyor), zümrüt **8,02:1**. Bu yüzden açık temada zümrüt
      mürekkep / altın yalnızca dolu yüzey, koyu temada altın mürekkep / zümrüt
      ailesi yüzey (`ARCHITECTURE.md` §12)
- [x] **14a: Tanımlanmamış roller borcu kapandı.** Şemanın tamamı (37 rol, iki
      şemada da) tanımlandı — kodun adıyla çağırmadıkları dahil, çünkü onları
      Material'ın kendi bileşenleri çiziyor. Cihazda piksel olarak doğrulandı:
      Snackbar'ın "Geri al"ı artık mor değil **`#D4AF37`**, zemini **`#1F3D2D`**;
      chip kenarlığı **`#5C7F6C`**, seçilmemiş chip etiketi **`#35594A`**
- [x] **14a: Koyu temada kart ↔ arka plan** 1,29:1 → **1,50:1**; cihazda
      `#1F3D2D` üstüne `#0D1A14` ölçülerek doğrulandı
- [x] **14a:** Her çift için kontrast hesaplandı ve belgelendi; en düşük gereken
      çift 3,32:1 (kenarlık), eşiğin altında tek çift yok. Tablo §12'de
- [x] **14a:** Grafik izi artık tanımlı bir rol (`outlineVariant`), saydamlık
      değil — eski gerekçe (koyu şemada `primary` ile `primaryContainer` aynı
      renkti) yeni palette geçersiz, ölçüldü: 4,35:1
- [x] **14b: Dynamic color (Material You, Android 12+), varsayılan KAPALI.** Gerekçe
      ürün kararı: duvar kâğıdından gelen renkler 14a'nın zümrüt-altın kimliğini ve o
      kimliğe göre ölçülmüş 37 rolun kontrastını geçersiz kılar (`ARCHITECTURE.md` §23).
      API 34'te **iki farklı duvar kâğıdı paletiyle** ölçüldü: çubuk–iz oranı sıcak
      tohumda **3,78:1**, soğuk tohumda **3,77:1**; Snackbar "Geri al" 10,84:1 ve
      10,87:1. 14a'da eski palette yaşanan çakışma tekrarlamıyor
- [x] **14b: Manuel tema tercihi** — sistemi takip et (varsayılan) / açık / koyu.
      Dynamic color'dan **bağımsız**: duvar kâğıdı renkleri açıkken de koyu tema
      zorlanabiliyor. API 31 altında dynamic color satırı görünür ama devre dışı ve
      nedenini yazıyor — API 29'da ölçüldü
- [x] **14b: Açılıştaki tema göz kırpması ölçüldü ve kapatıldı.** Tutma olmadan ana
      ekran **açık temada tam çiziliyordu** (`#D3E2D8`, beyaz çubuk) sonra koyuya
      dönüyordu. `MainActivity`'de `OnPreDrawListener` ile ilk kare tutuluyor;
      ölçüm sonrası beyaz açılış penceresinden **doğrudan** koyuya geçiyor
- [x] **14b: Para birimi HER YERDE SEMBOL** (₺ $ € £). Locale sayıyı belirlemeye devam
      ediyor, para birimi işaretini belirlemiyor. Tek nokta `MoneyFormatter`;
      bildirim zaten tutar taşımıyor, kur ekranı da artık sembolle adlandırıyor.
      ₺ karakteri **API 29'da çiziliyor**, ekran görüntüsüyle doğrulandı
- [x] **14b:** İki borç kapandı — `CLAUDE.md` §9 artık yeni paleti gerekçe gösteriyor,
      `MonthlyChangeRow` preview'ı `Locale.forLanguageTag`'e geçti

---

## ✅ Faz 15 — Düzenleme Ekranı

- [x] Karta tıklayınca düzenleme, Navigation ile **dördüncü hedef**. Sheet
      değil ekran: yarım kalmış bir düzenlemede sheet'in scrim'i, sürüklemesi
      ve geri tuşu üç ayrı "kapat" demek ve hiçbiri düzenleme hakkında bir
      karar değil (`ARCHITECTURE.md` §13)
- [x] Satır tıklanabilir; jest mantığına dokunulmadı. Tıklama `draggable`'ın
      **yanında** bir modifier, ve eylem satırın kendi `semantics`'inde
      tanımlı — `clearAndSetSemantics` alt ağacı düşürdüğü için aksi hâlde
      dokunma parmağa var, ekran okuyucuya yok olurdu. Ölçüldü: satır hâlâ
      **tek düğüm**, artık hem dokunma hem "Sil" eylemiyle
- [x] **Tarih alanı ÇIPAYI gösteriyor**, karttaki ilerletilmiş tarihi değil.
      İki emülatörde de ölçüldü: kart "25 gün kaldı" derken düzenleme ekranı
      kullanıcının girdiği **10 Eylül**'ü açıyor (`ARCHITECTURE.md` §17).
      12-2'nin bu fazı bekleyen notu **karşılandı**
- [x] Ekleme sheet'i ile ortak form bileşenleri ve **tek doğrulama kaynağı**;
      kural domain'de, sözcükler ui'da (§5)
- [x] Silme bu ekranda **yok** — kaydırarak silme kendi geri alma'sıyla zaten
      var, ikinci kapı ikinci bir geri alma davranışı demek olurdu

> **Type-safe rota yine kullanılmadı** — §13'ün bu faza bıraktığı soru cevaplandı.
> `@Serializable` rotalar kotlinx.serialization derleyici plugin'i istiyor, bu faz
> yeni bağımlılık eklemiyor ve proje AGP 9'da bir derleyici plugin'ine (`@Parcelize`,
> Faz 0) bir kez yenildi. Argüman zaten `NavType.LongType` ile sınırda tipli;
> tipsiz kalan tek adım rota metnini kurmak ve o tek fonksiyonda. Gerekçe §13'te.

---

## 🟡 Faz 16 — Play Store Hazırlığı

- [x] **Uygulama ikonu (adaptive) ve marka kimliği** — Faz 16d. Şablon ikonu
      (yeşil kare + Android robotu) gitti; işaret 14a'nın paletinden:
      **koyu zümrüt `#0D1A14` zemin üzerinde on iki altın `#D4AF37` para**,
      halka biçiminde. Altın burada mürekkep değil **dolgu**, zemin üstünde
      **8,50:1** (§12 kuralı korunuyor). İşaret 220 birimlik tek bir tanımdan,
      `tools/icon/generate_icons.py` ile üretiliyor: iki vektör katman +
      monochrome, beş yoğunlukta PNG yedeği, 512×512 mağaza karosu.
      108dp tuvalde işaret **57,89dp**, Material'ın 66dp anahtar dairesine
      **8,11dp** payla giriyor — hiçbir maske kesmiyor (api34 ve api36'da
      ölçüldü: ikisi de **daire**, pay 4,94dp ve 5,79dp). İlk çizim 65,78dp'ydi
      ve 0,218dp payla sığıyordu; sığmasına rağmen sıkışık durduğu için bütün
      geometri tek bir `SCALE` sabitiyle **%88** küçültüldü. **Şablondan kalan
      on `.webp` silindi.** Ayrıntı `ARCHITECTURE.md` §27, doğrulama yöntemi
      `TESTING.md`, görüntüler `docs/screenshots/phase-16d/` (ilk hâl ile
      karşılaştırma için `-v2` ekli dosyalar).
- [x] **Bildirim ikonu yenilendi** — Faz 16d. Faz 10b'nin geçici çan silueti
      gitti; durum çubuğu artık aynı işareti taşıyor ama **ayrı bir çizim**
      olarak. Ölçüm gerekçesi: işareti 24dp'ye olduğu gibi indirince paralar
      arası 4 birimlik ayrım **0,657dp**'ye düşüyor ve xhdpi altında kapanıyor.
      Para sayısını azaltmak yerine **ayrım iki katına** (1,3dp) çıkarıldı —
      on iki, işaretin anlamının kendisi. Üç cihazda on iki ayrımın da
      göründüğü doğrulandı.
- [x] **Release imzalama yapılandırması, keystore güvenliği** — Faz 16c.
      Dört değer (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`)
      **önce `local.properties`'ten**, yoksa ortam değişkenlerinden okunuyor;
      derleme betiğinde şifre yok. `signingConfig` yalnızca dördü de varken ve
      keystore dosyası gerçekten mevcutken kuruluyor; yoksa `assembleRelease`
      **hata vermeden** `app-release-unsigned.apk` üretiyor ve `assembleDebug`
      etkilenmiyor. **Debug anahtarına düşülmüyor** (gerekçe
      `ARCHITECTURE.md` §24). **Keystore dosyasını kullanıcı oluşturacak** —
      bu madde yapılandırmayı kapsıyor, anahtarı değil.
- [x] **Release AAB üretildi ve iki cihazda kurulup sürüldü** — Faz 16g.
      `./gradlew :app:bundleRelease` → `app-release.aab`, **4.598.466 B**
      (16c'nin universal APK'sı 2.127.430 B; AAB tüm ABI/dil/yoğunluğu
      bölünmemiş taşıdığı için 2,16 kat büyük, Play kullanıcıya bunu
      göndermiyor). `jarsigner` **`jar verified.`** ve `CN=ElinaDorothea`
      diyor. `bundletool` ile üretilen APK seti `subtrack_min_api24` ve
      `subtrack_wide_api34`'e kuruldu; ikisinde de **base + `split_config.en`
      + `split_config.x86_64`** geldi, yani Play'in teslim modeli birebir
      çalışıyor. Temel tur (abonelik, toplam, istatistik, ayarlar, bildirim)
      iki cihazda da geçti. **Commit edilmedi** — `*.aab` `.gitignore`'da.
      Komutlar `TESTING.md`, içerik dökümü `ARCHITECTURE.md` §26.
- [x] **Foreground service tipi denetlendi — beyan GEREKMİYOR** — Faz 16g.
      Release birleşik manifestinde `FOREGROUND_SERVICE` ile başlayan **tek**
      izin var, **alt tip yok** (`_DATA_SYNC`, `_SHORT_SERVICE` vb. hiçbir
      bağımlılığın manifestinde de geçmiyor). `SystemForegroundService`
      elemanında `foregroundServiceType` özniteliği **yok**; ikisi de
      `androidx.work:work-runtime:2.11.2`'den geliyor. Kodda `setForeground`,
      `setExpedited`, `ForegroundInfo`, `OutOfQuotaPolicy` **hiç geçmiyor** —
      tek iş kısıtsız bir `PeriodicWorkRequest`. Alt tip olmadığı için
      **manifest değiştirilmedi** (`tools:node="remove"` gereksiz) ve Play
      Console'da beyan formunun açılmaması bekleniyor — **bu tek nokta
      Console'da henüz doğrulanmadı.** Ayrıntı `ARCHITECTURE.md` §26.
- [x] **ProGuard/R8 kuralları, release build testi** — Faz 16c.
      `proguard-rules.pro` **boş kaldı**: önce kuralsız derlendi, minify açık
      release APK dört cihazda (API 24/29/34/36) sürüldü, hiçbir şey kırılmadı.
      Room, Hilt, WorkManager, DataStore, Navigation, Compose ve coroutines
      kendi kurallarını AAR'larında getiriyor (70+ kaynak,
      `configuration.txt`). Önleyici kural yazılmadı; gerekçe §24'te.
- [x] **`isMinifyEnabled = true` (R8)** — Faz 16c. `isShrinkResources = true`
      da açıldı. APK **13,06 → 2,02 MiB**, dex **45,45 → 3,09 MiB** (beş
      dosyadan ikiye), `resources.arsc` 529.616 → 306.388 B. Açılış süresi
      ayrıca ölçülmedi.
- [x] **`material-icons-extended` ölçüldü ve KALIYOR** — Faz 16c. Kullanılan
      **12** ikonun (16-0 envanteri; eski "beş" sayısı yanlıştı) 8'i
      `material-icons-core`'da, 4'ü (`BarChart`, `Cloud`, `ArrowUpward`,
      `ArrowDownward`) yalnızca `-extended`'da ve core'da karşılıkları yok.
      Bedeli: minify kapalıyken **3,95 MiB**, minify açıkken **216 B**.
      Yani sorunun cevabı kaldırmak değil, **R8 zaten daraltıyor** — son
      APK'da o kütüphaneden beş sınıf kalıyor. Ayrıntı ve karar §24'te.
- [x] **`desugar_jdk_libs` bedeli ölçüldü** — Faz 16c. Desugar dex'i minify
      kapalıyken APK'da 144.680 B, açıkken **128.900 B**. Faz 10a'daki
      "~200-400 KB" tahmini doğru taraftaymış, biraz cömertmiş.
- [x] **Şablon testler kaldırıldı** — Faz 16b. `ExampleUnitTest` (`2+2=4`) ve
      `ExampleInstrumentedTest` (paket adı kontrolü) silindi; **331 birim testi
      → 330**, **20 enstrümantasyon metodu → 19**. İkincisi her pakette bir cihaz
      kurulumu ve koşumu maliyeti getiriyordu.
- [x] `targetSdk` Play'in güncel zorunluluğuna yükseltilsin — **36**, Play AAB'yi
      bu hedefle kabul etti (Faz 16k). Eşik her yıl yükseliyor; bu madde her
      yayın döneminde tekrar okunmalı, kapanışı kalıcı değil.
- [x] **Edge-to-edge'e geçildi** — Faz 16a. `enableEdgeToEdge()` `setContent`
      öncesinde çağrılıyor, sistem çubuğu ikonları tema tercihini takip ediyor,
      paylar `Scaffold` üzerinden her ekranda uygulandı (ana ekranda
      `contentPadding`, form ekranlarında kaydırmanın dışında), kur ve
      düzenleme ekranlarına `imePadding()` geldi.
      **`windowSoftInputMode="adjustResize"` kaldırılmadı.** Kaldırılması
      denendi ve API 29'u kırdı: `WindowInsets.ime` API 30 altında pencere
      küçülmediği sürece raporlanmıyor, yani `imePadding()` orada sıfıra
      padding uyguluyor. Platform API 30'dan itibaren bayrağı zaten yok
      sayıyor, bu yüzden ikisi çakışmıyor — bayrak API 24-29'un, `imePadding()`
      API 30+'ın yarısı. Klavye tablosunun her satırı üç cihazda yeniden
      ölçüldü (`TESTING.md`); ölçümler `ARCHITECTURE.md` §16'da.
- [x] **Açılış ekranı ve durum çubuğu tamamlandı** — Faz 16h (+16h-1, 16h-2).
      Kapı `core-splashscreen` 1.2.0'a bağlandı: tema tercihi okunana kadar
      ekranda duran şey artık boş beyaz launch penceresi değil, ikonun kendi
      `#0D1A14` zemini üzerindeki işaret — 16g'nin ölçtüğü 173-212 ms'lik
      `#FAFAFA` kare kalktı. 16h-1 durum çubuğu ikonlarının **kimden**
      geldiğini ölçtü: devir boyunca sahibi splash penceresi ve
      `Theme.SubTrack.Starting` (bant iki temada da 17,87:1), `onCreate`'teki
      stil değil. 16h-2 o stili `SystemBarStyle.dark` → **`auto`** yaptı;
      kazanç yalnızca kapının 1000 ms son tarihi dolup tercih hiç gelmeyen
      yolda görülüyor — sistem açıkken `dark` 1,00:1 (beyaz üstüne beyaz ikon)
      veriyordu, `auto` 5,74:1. Splash'tan uygulamaya devirde ~350 ms'lik bir
      rampa ölçüldü ve **kabul edildi** (platform davranışı; gerekçe ve
      reddedilen üç seçenek §23'te). Ölçümler `ARCHITECTURE.md` §23, kayıt ve
      ekran görüntüleri `PROGRESS.md`'deki 16h / 16h-1 / 16h-2 girdileri.
- [x] **Varsayılan kaynak dili İngilizce oldu, dil paketlemesi yeniden
      kuruldu** — Faz 16i. 16g'nin B6 ölçümü bir yayın engeli buldu: Türkçe
      metinler nitelikisiz `values/` içinde olduğu için, dil listesinde ne `tr`
      ne `en` bulunan **her** cihaz uygulamayı Türkçe görüyordu — uygulama tüm
      ülkelerde yayınlanacak. Aynı mekanizmanın ters yüzü olarak `[tr, en]`
      listeli bir Türk kullanıcıya İngilizce gidiyordu. `values/` artık
      İngilizce, `values-tr/` Türkçe; hiçbir çeviri değişmedi, iki dosya bütün
      hâlinde yer değiştirdi. Kütüphane dilleri `androidResources.localeFilters`
      ile `en` + `tr`'ye indirildi (Material3'ün tarih seçicisi Almanca cihazda
      Almanca geliyordu), dil parçası kapatıldı ve iki dil de `base`'e girdi.
      `localeConfig` bilerek eklenmedi: dil seçimi bir özellik, bu bir düzeltme
      turu. Karar ve dil matrisi `ARCHITECTURE.md` §28, kayıt `PROGRESS.md`'deki
      16i girdisi.
- [x] **Yayın öncesi son kontrol: RTL kapatıldı, izinler ve mağaza iddiaları
      denetlendi** — Faz 16j. `android:supportsRtl="false"` — uygulamanın iki
      kaynağı da (`en`, `tr`) soldan sağa yazılıyor ve aynalanmış düzen hiç
      çalıştırılmadı. WorkManager'ın getirdiği dört izin (`WAKE_LOCK`,
      `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`)
      dışında beklenmeyen izin yok, `INTERNET` ve `AD_ID` hâlâ yok. Mağaza
      metnindeki her iddia kodla eşleştirilip doğrulandı — tek istisna Auto
      Backup'ın cihazdan cihaza aktarım hedefinin metinde geçmemesi (öneri
      `PROGRESS.md`'deki 16j kaydında). Kayıt `PROGRESS.md`'deki 16j girdisi.
- [x] **Test paketi sıra bağımsız hâle geldi ve tam regresyon turu atıldı** —
      Faz 16b. Paket dört cihazda, iki koşum yöntemiyle, arka arkaya iki kez
      geçiyor; 117 maddelik liste dört cihazda eksiksiz sürüldü (468 hücre).
      Ön koşullar artık testin içinde kuruluyor, `pm clear`/`pm grant` gerekmiyor.
      Ayrıntı ve tablo `PROGRESS.md`'de.
- [x] **API 24-25'te bildirim ayarları kısayolu düzeltildi** — 16b'de bulundu,
      16b hotfix'inde kapandı. Android 7.x'te satıra dokunmak hiçbir şey
      yapmıyordu: o sürümlerin Ayarlar'ı `APP_NOTIFICATION_SETTINGS`'i
      karşıladığı için `ActivityNotFoundException` atılmıyor ve yedek yol hiç
      tetiklenmiyordu, ama ekran `app_uid` ekstrasını da istediği için kendini
      kapatıyordu. Artık API 26 altında **doğrudan uygulama detay sayfasına**
      gidiliyor; `app_uid` gönderilmiyor (belgelenmemiş davranış) ve satır
      devre dışı bırakılmıyor (bildirimleri açmanın tek yolu o). API 26+
      davranışı aynen kaldı. Lint'in iki `InlinedApi` uyarısı da kapandı.
- [x] **Android Auto Backup açıkça yapılandırıldı** — Faz 16f. İki şablon XML
      de gerçek kurallarla değiştirildi: yedeğe yalnızca Room veritabanı
      (`domain="database"` bütün olarak, WAL yüzünden) ve DataStore tercihleri
      giriyor. WorkManager'ın veritabanı `no_backup/` altında olduğu için zaten
      dışarıda. `fullBackupContent` ve `dataExtractionRules` ikisi de gerekli
      (minSdk 24). Yedekle-geri yükle turu API 24 ve API 34'te uçtan uca
      sürüldü: veritabanı bire bir, tercihler bayt bayt aynı geldi, çökme yok,
      bildirim işi yeniden kuruldu. Ayrıntı `ARCHITECTURE.md` §25,
      ölçümler `PROGRESS.md`.
- [x] Gizlilik politikası — yayınlandı:
      https://elina-cn.github.io/subtrack-privacy/ (TR + EN). **v1.1'de**
      (dışa/içe aktarma) politikaya dosya konumu cümlesi eklenecek; **v1.2'de**
      ağ eklendiğinde politika ve Data Safety formu yeniden güncellenecek
      (PROJECT_SPEC §4). **16f'nin ölçümü politikanın metnini değiştiriyordu:**
      Auto Backup açık, yani veri kullanıcının kendi Google Drive'ına
      kopyalanıyor. "Veriler cihazdan çıkmıyor" cümlesi olduğu gibi
      kullanılamaz — doğru cümle `PROGRESS.md`'deki 16f kaydında. (Faz 16k: URL
      Console dışında bir kaynaktan geldi, repoda kanıt yok.)
- [x] Ücretlendirme kararı verilsin (peşin / ücretsiz+premium / ücretsiz) —
      **ücretsiz.** v1.0 uygulama içi satın alma ve reklam olmadan yayınlandı;
      Play'de ücretsiz yayınlanan bir uygulama sonradan ücretliye çevrilemez.
      Karar ve gerekçesi `PROJECT_SPEC.md` §5.
- [x] ARCHITECTURE'daki **"Şema sürümlemesi"** kuralı okunsun. Yayından sonra
      migration zorunlu hale geliyor, istisnası yok. **Okundu (Faz 16k) —
      kural yürürlüğe girdi**, bkz. `ARCHITECTURE.md` "Şema sürümlemesi" ve
      `CLAUDE.md` §6'ya eklenen iki yeni madde.
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

      > **Not (Faz 16k):** Bu maddeye ne bu belge turunda ne `PROGRESS.md`'de
      > yeni kanıt geldi — açık kalıyor.
- [x] Strip edilemeyen native kütüphaneler: DataStore'un
      `libdatastore_shared_counter.so`'su ve Compose'un `androidx.graphics:graphics-path`
      üzerinden gelen `libandroidx.graphics.path.so`'su. `stripDebugDebugSymbols`
      ikisini de strip edemiyor, olduğu gibi paketleniyor. Release APK boyutu
      ölçülürken göz önünde bulundurulsun. (İkincisi Faz 10b Görev 0'da fark
      edildi; WorkManager'dan gelmiyor, temiz tabanda da vardı.) **Faz 16k:**
      Play Console'un tek uyarısı tam olarak buydu ("yerel kod için hata
      ayıklama sembolü yok") — kabul edildi, yerel kod AndroidX'ten ve sembolü
      elimizde yok.
- [x] **API 24/25 üzerinde bir kez test edilsin** — `minSdk` 24 iddiası artık
      doğrulandı. `subtrack_min_api24` AVD kuruldu ve Faz 16b'nin 117 maddelik
      tam regresyonuna (dört cihaz, 468 hücre), Faz 16c'nin dört cihazlı
      release APK turuna, Faz 16g/16h/16h-1/16h-2'nin açılış ölçümlerine ve
      Faz 16j'nin RTL/izin turuna dahil edildi. API 24'te düşen tek madde
      (#40, bildirim ayarları kısayolu) 16b hotfix'inde kapandı.
- [x] **`desugar_jdk_libs` APK bedeli R8 sonrası ölçülsün** — bu madde
      yukarıdaki, Faz 16c'de işaretlenmiş **`desugar_jdk_libs` bedeli ölçüldü**
      maddesiyle aynı soruyu soruyor ve orada zaten cevaplandı (minify kapalı
      144.680 B, minify açık 128.900 B). Ayrı bir ölçüm yapılmadı, ona gönderme.
- [ ] **WorkManager'ın manifeste otomatik eklediği izinler Play Console'da
      görünecek:** `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`,
      `FOREGROUND_SERVICE`. Dördü de kütüphaneden geliyor, uygulama kodu
      istemiyor. Birleşik manifestte ölçüldü (Faz 10b Görev 0). İzin beyanında
      ve gizlilik politikasında bunlar da açıklanmalı.

      > **Not (Faz 16k):** Faz 16j izin beyanının Console tarafını doğruladı
      > ama gizlilik politikası metninin bu dördünü içerdiğine dair ne bu
      > turda ne PROGRESS'te kanıt var — açık kalıyor.
- [x] Play Console Data Safety formu — dolduruldu: toplama yok, paylaşım yok;
      hedef kitle 18+, içerik derecesi 3+, reklam ve reklam kimliği yok
      (gerekçeler PROJECT_SPEC §5). (Faz 16k: Console'da yapıldı, repoda kanıt
      yok.)
- [x] Mağaza görselleri ve açıklama metni — tamamlandı. Ekran görüntüleri
      (Faz 16e-2): `docs/screenshots/store/`, İngilizce 7 + Türkçe 5 (Türkçe
      tarafında koyu tema görüntüsü yok). Simge `docs/store/icon-512.png`,
      özellik grafiği `docs/store/feature-graphic.png`. Mağaza girişi en-US
      (varsayılan) + tr-TR. (Faz 16k: Console'da yapıldı, repoda yalnızca
      görsel dosyaları var.)
- [x] Internal testing — sürüm 1 (`versionCode 1` / `versionName 1.0`, kaynak
      commit `a16e299`, kullanıcının `v1.0` etiketiyle işaretlediği) yayında
      (Faz 16k).
- [ ] Kapalı test — aynı sürüm, tüm ülkeler, 22.09.2026'da incelemeye
      gönderildi; **Google incelemesinde.**
- [ ] Üretim erişimi ve yayın — 12 testçi 14 gün kesintisiz katılımda kalınca
      başvurulacak (Faz 16k).

**Bitti:** `PROJECT_SPEC.md` §8'deki "Bitti" tanımının tamamı karşılandı **ve**
üretim erişimi alınıp bu sürüm üretimde yayına girdi. İlk koşul 22 Eylül
2026'da sağlandı; ikincisi henüz sağlanmadı (kapalı test incelemede) — bu
yüzden faz 🟡 kalıyor.

---

## ⬜ Faz 17 — Dışa/İçe Aktarma

Kullanıcının verisini dosyaya yazması ve geri yüklemesi. Auto Backup (Faz 16f)
kullanıcının kendi Drive'ına yedekliyor ve kullanıcı o yedeğe elle
dokunamıyor; bu faz veriyi kullanıcının **elinde tutabileceği bir dosyaya**
çıkarmakla ilgili.

Kapsam `PROJECT_SPEC.md` §4 "v1.1 — Veri taşınabilirliği" bölümünde karara
bağlandı. Faz 17, Karar 3 gereği **üretim erişimiyle başlar**; o güne kadar
koda yalnızca kapalı test geri bildiriminden gelen düzeltmeler girer
(`versionCode 2`'den başlayarak).
