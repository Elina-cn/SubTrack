# ROADMAP.md — SubTrack Yol Haritası

Fazlar sırayla yapılır. Bir fazın "Bitti" kriterleri sağlanmadan sonrakine
geçilmez. Tüm çalışma `Elina` branch'inde yapılır; branch açılmaz. Faz bitince
CC, prompt açıkça istediğinde `git tag phase-N-done` atar.

**Durum işaretleri:** ⬜ başlanmadı · 🟡 devam ediyor · ✅ bitti

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

## ⬜ Faz 8 — UX Cilası

- [ ] Boş durum ekranı
- [ ] Yükleme durumu
- [ ] Hata gösterimi (Snackbar)
- [ ] Erişilebilirlik: `contentDescription`, dokunma alanı ≥ 48dp — **not:**
      `SwipeToDeleteRow`'un semantics'i (custom "Sil" eylemi +
      `mergeDescendants`) hotfix serisinde baştan kondu.
- [ ] Koyu tema tüm ekranlarda gözden geçirilsin
- [ ] **Açık tema** gözden geçirilsin — test cihazının varsayılanı koyu tema,
      testler ağırlıklı orada yapılıyor
- [ ] `HomeUiState.isLoading` UI'a bağlansın. Şu an hesaplanıyor ama hiç
      okunmuyor; açılışta "yükleniyor" ile "hiç abonelik yok" ayırt edilemiyor.
- [ ] Dashboard toplamı ekran okuyucuya bağlamlı okunsun (şu an etiket ve tutar
      ayrı düğüm, "0.00 TL" bağlamsız okunuyor)

**Bitti:** Hiçbir durumda boş/kırık ekran yok.

---

## ⬜ Faz 9 — Para Birimi Seçimi

- [ ] Ekleme formuna para birimi seçici (TRY, USD, EUR, GBP)
- [ ] Locale'e göre para formatlama
- [ ] Fiyat biçimlendirmesi `Locale.US`'tan cihaz locale'ine geçsin
      (Türkçede virgül). TalkBack'in karışık dil okumasına da katkısı olabilir.
- [ ] Sabit kur tablosu ile toplam normalizasyonu
- [ ] Ana para birimi tercihi (DataStore)

**Bitti:** Karışık para birimli abonelikler doğru toplanıyor.

---

## ⬜ Faz 10 — Yenilenme Tarihi + Hatırlatma

- [ ] Tarih seçici, sonraki ödeme tarihi
- [ ] "X gün kaldı" göstergesi
- [ ] WorkManager + yerel bildirim
- [ ] `POST_NOTIFICATIONS` izin akışı (Android 13+)

**Bitti:** Yaklaşan ödeme için bildirim geliyor, izin reddedilse de uygulama çalışıyor.

---

## ⬜ Faz 11 — Kategoriler

- [ ] Kategori seçimi, kategoriye göre filtre, kategori bazlı toplam

---

## ⬜ Faz 12 — Ödeme Periyodu

- [ ] Aylık/yıllık/haftalık seçimi
- [ ] Yıllık → aylık maliyet normalizasyonu
- [ ] Aylık/yıllık toplam görünümü arasında geçiş

---

## ⬜ Faz 13 — İstatistik

- [ ] Kategori dağılım grafiği
- [ ] Aylık trend
- [ ] En pahalı abonelikler

---

## ⬜ Faz 14 — Tema Tamamlama

- [ ] Dynamic color (Material You, Android 12+)
- [ ] Manuel tema tercihi (sistem/açık/koyu)
- [ ] Koyu temada kart ↔ arka plan ayrımı 1.29:1, gözden geçirilsin

---

## ⬜ Faz 15 — Düzenleme Ekranı

- [ ] Karta tıklayınca düzenleme, Navigation ile ikinci ekran
- [ ] Satır tıklanabilir olsun; şu an silme dışında eylem yok, TalkBack için
      tek yol custom action

---

## ⬜ Faz 16 — Play Store Hazırlığı

- [ ] Uygulama ikonu (adaptive) ve marka kimliği
- [ ] Release imzalama yapılandırması, keystore güvenliği
- [ ] ProGuard/R8 kuralları, release build testi
- [ ] `isMinifyEnabled = true` (R8) — APK boyutu ve açılış süresi düşer
- [ ] `material-icons-extended` kaldırılsın veya daraltılsın: binlerce ikon
      getiriyor, **beş** tanesi kullanılıyor
- [ ] Şablon testler kaldırılsın (`ExampleUnitTest`, `ExampleInstrumentedTest`
      — dolgu: `2+2=4` ve paket adı kontrolü)
- [ ] `targetSdk` Play'in güncel zorunluluğuna yükseltilsin
- [ ] Gizlilik politikası (çevrimdışı, veri toplanmıyor)
- [ ] Play Console Data Safety formu
- [ ] Mağaza görselleri ve açıklama metni
- [ ] Internal testing → production

**Bitti:** `PROJECT_SPEC.md` §7'deki tüm maddeler işaretli.
