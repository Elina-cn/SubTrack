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

## ⬜ Faz 5 — ViewModel + UiState + UI Bağlama

- [ ] `HomeUiState` ve `HomeEvent`
- [ ] `HomeViewModel` (`@HiltViewModel`), `stateIn` ile Flow → StateFlow
- [ ] Aylık toplam hesabı ViewModel'a taşınsın (`derivedStateOf` kaldırılsın)
- [ ] `MainActivity`'deki `mutableStateListOf` tamamen kaldırılsın
- [ ] `collectAsStateWithLifecycle` ile bağlantı
- [ ] `MainActivity` parçalansın: `HomeScreen`, `SubscriptionCard`, `AddSheet`
- [ ] `androidx.hilt:hilt-navigation-compose` bağımlılığı eklensin
      (`hiltViewModel()` fonksiyonu için gerekli)

**Bitti:** Veri Room'dan geliyor, uygulama kapanıp açılınca duruyor,
`MainActivity` 50 satırın altında.

---

## ⬜ Faz 6 — CRUD Tamamlama

- [ ] Ekleme repository üzerinden
- [ ] Girdi doğrulama: boş ad, geçersiz/negatif fiyat → hata mesajı
- [ ] Fiyat parse mantığı ViewModel'da (`String` → `Long` kuruş)
- [ ] Kaydırarak silme repository'yi tetiklesin — **not:** hotfix serisinde
      `SwipeToDeleteRow` yazıldı. Kalan iş: `onDelete` callback'ini
      `HomeEvent.Delete`'e bağlamak ve undo eklemek.
- [ ] Silme sonrası Snackbar ile geri al (undo)
- [ ] Repository hata yönetimi karara bağlansın: `Result<T>`, `DataError`
      tipi, veya exception + ViewModel'da yakalama. ARCHITECTURE §9
      güncellensin. (Faz 3'ten ertelendi.)

**Bitti:** Ekleme/silme kalıcı, hatalı girdi engelleniyor, yanlış silme
geri alınabiliyor.

---

## ⬜ Faz 7 — Test Altyapısı

- [ ] JUnit, Turbine, coroutines-test kurulumu
- [ ] `FakeSubscriptionRepository`, `FakeSubscriptionDao`
- [ ] `HomeViewModel` testleri: yükleme, toplam hesabı, doğrulama, silme
- [ ] `SubscriptionMapper` testleri
- [ ] `SubscriptionDao` enstrümantasyon testi (in-memory DB)

**Bitti:** `./gradlew test` geçiyor, kritik yollar kapsanmış.

---

## ⬜ Faz 8 — UX Cilası

- [ ] Boş durum ekranı
- [ ] Yükleme durumu
- [ ] Hata gösterimi (Snackbar)
- [ ] Erişilebilirlik: `contentDescription`, dokunma alanı ≥ 48dp — **not:**
      `SwipeToDeleteRow`'un semantics'i (custom "Sil" eylemi +
      `mergeDescendants`) hotfix serisinde baştan kondu.
- [ ] Koyu tema tüm ekranlarda gözden geçirilsin

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

---

## ⬜ Faz 16 — Play Store Hazırlığı

- [ ] Uygulama ikonu (adaptive) ve marka kimliği
- [ ] Release imzalama yapılandırması, keystore güvenliği
- [ ] ProGuard/R8 kuralları, release build testi
- [ ] `targetSdk` Play'in güncel zorunluluğuna yükseltilsin
- [ ] Gizlilik politikası (çevrimdışı, veri toplanmıyor)
- [ ] Play Console Data Safety formu
- [ ] Mağaza görselleri ve açıklama metni
- [ ] Internal testing → production

**Bitti:** `PROJECT_SPEC.md` §7'deki tüm maddeler işaretli.
