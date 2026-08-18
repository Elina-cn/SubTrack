# ROADMAP.md — SubTrack Yol Haritası

Fazlar sırayla yapılır. Bir fazın "Bitti" kriterleri sağlanmadan sonrakine
geçilmez. Tüm çalışma `Elina` branch'inde yapılır; branch açılmaz. Faz bitince
kullanıcı `git tag phase-N-done` ile işaretler.

**Durum işaretleri:** ⬜ başlanmadı · 🟡 devam ediyor · ✅ bitti

---

## ⬜ Faz 0 — Acil Düzeltmeler

Uygulama fiilen kullanılamaz durumdaydı; bu fazda çalışır hale getirildi.

- [ ] `onDismissRequest` boş — bottom sheet kapanmıyor (MainActivity.kt:159)
- [ ] Kaydet butonu sheet'i kapatmıyor (MainActivity.kt:194)
- [ ] Kaydetme sonrası form alanları temizlensin
- [ ] `remember` → `rememberSaveable` (ekran döndürmede veri kaybı) — geçici,
      Faz 5'te Room devralacak
- [ ] `ANALYSIS_REPORT.md` → `docs/archive/` altına taşınsın

> `SubTrack.md` maddesi kaldırıldı: bu dosya hiç repoya eklenmemişti, içeriği
> gerçeği yansıtmadığı için kullanıcı bilerek dışarıda bıraktı.

**Bitti:** Uygulama açılıyor, abonelik eklenebiliyor, sheet kapanıyor,
ekran döndürmede liste duruyor.

---

## ⬜ Faz 1 — Altyapı Temizliği

- [ ] `gradle/libs.versions.toml` (version catalog) kurulumu/doğrulaması
- [ ] Compose BOM ↔ `activity-compose` ↔ `lifecycle` sürüm çakışması
      `./gradlew :app:dependencies` ile doğrulansın, gerekirse hizalansın
- [x] Kotlin plugin durumu netleşti — **Faz 0'da doğrulandı:** AGP 9 Kotlin
      plugin'lerini yerleşik getiriyor, `version.ref` verilirse *"already on
      the classpath with an unknown version"* hatası çıkıyor. Sürümsüz alias
      ile uygulanmalı.
- [ ] KSP eklentisi eklensin (kapt kullanılmayacak) — **dikkat:** yukarıdaki
      kısıt burada da geçerli olabilir, sürüm çakışması beklenmeli
- [ ] Kod içi Türkçe yorumlar İngilizceye çevrilsin (CLAUDE.md §2)
- [ ] `Theme.kt` gerçekten devreye alınsın: hardcoded renkler →
      `MaterialTheme.colorScheme`
- [ ] `Type.kt` `MaterialTheme(typography = ...)` ile bağlansın
- [ ] `Dimens.kt` oluşturulsun, hardcoded `dp` değerleri oradan gelsin
- [ ] Tüm kullanıcı metinleri `strings.xml`'e taşınsın + `values-en/`
- [ ] Kullanılmayan import/kod temizliği
- [ ] `.gitignore`: `.idea/` altındaki makineye özel dosyalar
      (`emulatorDisplays.xml` vb.) hariç tutulsun

**Bitti:** Sıfır hardcoded metin, sıfır hardcoded renk. Koyu tema fiilen
çalışıyor. Temiz derleme, sıfır uyarı.

---

## ⬜ Faz 2 — Domain + Room Şeması

- [ ] `domain/model/`: `Subscription`, `Money`, `BillingPeriod`,
      `SubscriptionCategory`
- [ ] `data/local/entity/SubscriptionEntity` — v1.5'e kadar tüm alanlar dahil:
      `id`, `name`, `priceInCents`, `currencyCode`, `billingPeriod`,
      `nextPaymentDate`, `category`, `iconKey`, `createdAt`
- [ ] `SubscriptionDao`: `observeAll(): Flow<List<...>>`, `insert`, `deleteById`,
      `getById`, `update`
- [ ] `SubTrackDatabase` (`@Database`, version 1)
- [ ] `SubscriptionMapper` — Entity ↔ Domain

**Bitti:** Room derleniyor, KSP kod üretiyor, henüz UI'a bağlı değil.

---

## ⬜ Faz 3 — Repository Katmanı (manuel DI)

- [ ] `domain/repository/SubscriptionRepository` arayüzü
- [ ] `data/repository/SubscriptionRepositoryImpl`
- [ ] Nesneler `SubTrackApplication` içinde elle kurulsun

**Bitti:** Repository çalışıyor. Manuel DI'ın ne kadar hantal olduğu görülmüş
durumda — Hilt'in gerekçesi anlaşıldı.

---

## ⬜ Faz 4 — Hilt

- [ ] Hilt eklentisi ve bağımlılıkları
- [ ] `@HiltAndroidApp`, `@AndroidEntryPoint`
- [ ] `DatabaseModule` (`@Provides`), `RepositoryModule` (`@Binds`)
- [ ] Manuel kurulum kodu silinsin

**Bitti:** Uygulama Hilt üzerinden ayağa kalkıyor, elle nesne kurulumu yok.

---

## ⬜ Faz 5 — ViewModel + UiState + UI Bağlama

- [ ] `HomeUiState` ve `HomeEvent`
- [ ] `HomeViewModel` (`@HiltViewModel`), `stateIn` ile Flow → StateFlow
- [ ] Aylık toplam hesabı ViewModel'a taşınsın (`derivedStateOf` kaldırılsın)
- [ ] `MainActivity`'deki `mutableStateListOf` tamamen kaldırılsın
- [ ] `collectAsStateWithLifecycle` ile bağlantı
- [ ] `MainActivity` parçalansın: `HomeScreen`, `SubscriptionCard`, `AddSheet`

**Bitti:** Veri Room'dan geliyor, uygulama kapanıp açılınca duruyor,
`MainActivity` 50 satırın altında.

---

## ⬜ Faz 6 — CRUD Tamamlama

- [ ] Ekleme repository üzerinden
- [ ] Girdi doğrulama: boş ad, geçersiz/negatif fiyat → hata mesajı
- [ ] Fiyat parse mantığı ViewModel'da (`String` → `Long` kuruş)
- [ ] Kaydırarak silme repository'yi tetiklesin
- [ ] Silme sonrası Snackbar ile geri al (undo)

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
- [ ] Erişilebilirlik: `contentDescription`, dokunma alanı ≥ 48dp
- [ ] Koyu tema tüm ekranlarda gözden geçirilsin

**Bitti:** Hiçbir durumda boş/kırık ekran yok.

---

## ⬜ Faz 9 — Para Birimi Seçimi

- [ ] Ekleme formuna para birimi seçici (TRY, USD, EUR, GBP)
- [ ] Locale'e göre para formatlama
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
