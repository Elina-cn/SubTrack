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
