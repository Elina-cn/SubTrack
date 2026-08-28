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
