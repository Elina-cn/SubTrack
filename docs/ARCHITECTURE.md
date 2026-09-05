# ARCHITECTURE.md — SubTrack Mimarisi

Bu belge bağlayıcıdır. Buradaki bir karardan sapmak gerekiyorsa önce bu belge
güncellenir, sonra kod yazılır.

---

## 1. Genel Yapı

Üç katmanlı yapı kullanıyoruz. Kural tek yönlü bağımlılıktır:

```
   UI  ──────►  Domain  ◄──────  Data
(Compose)     (saf Kotlin)      (Room)
```

- **UI**, Domain'i bilir. Data'yı **bilmez**.
- **Data**, Domain'i bilir. UI'yı **bilmez**.
- **Domain** hiçbir şeyi bilmez — içinde `android.*` veya `androidx.*`
  import'u olmaz. Saf Kotlin.

**Neden:** Room'u yarın değiştirsek UI'a dokunmayız; UI'ı değiştirsek
veritabanına dokunmayız. Test yazarken de her katman tek başına test edilebilir.

---

## 2. Paket Yapısı

```
com.elinacn.subtrack/
├── SubTrackApplication.kt          @HiltAndroidApp
├── MainActivity.kt                 @AndroidEntryPoint, sadece setContent
│
├── domain/                         SAF KOTLIN — android import yasak
│   ├── model/
│   │   ├── Subscription.kt         Uygulamanın konuştuğu model
│   │   ├── BillingPeriod.kt        enum: MONTHLY, YEARLY, WEEKLY
│   │   ├── SubscriptionCategory.kt enum
│   │   └── Money.kt                value class, kuruş cinsinden Long
│   ├── repository/
│   │   └── SubscriptionRepository.kt   interface (implementasyon değil)
│   └── usecase/                    (gerektiğinde; başta boş)
│
├── data/
│   ├── local/
│   │   ├── entity/SubscriptionEntity.kt   @Entity
│   │   ├── dao/SubscriptionDao.kt         @Dao
│   │   └── SubTrackDatabase.kt            @Database
│   ├── mapper/
│   │   └── SubscriptionMapper.kt   Entity ↔ Domain dönüşümü
│   └── repository/
│       └── SubscriptionRepositoryImpl.kt  interface'in gerçeklemesi
│
├── di/
│   ├── DatabaseModule.kt           Room nesnelerini sağlar
│   └── RepositoryModule.kt         interface → impl bağlaması
│
└── ui/
    ├── theme/                      Color.kt, Theme.kt, Type.kt, Dimens.kt
    ├── common/                     Ekranlar arası ortak composable'lar
    ├── home/
    │   ├── HomeScreen.kt           Composable
    │   ├── HomeViewModel.kt        @HiltViewModel
    │   ├── HomeUiState.kt          data class + event sealed interface
    │   └── components/             SubscriptionCard.kt, AddSheet.kt, ...
    └── navigation/
        └── SubTrackNavHost.kt
```

---

## 3. Katman Sorumlulukları

### Domain
- Uygulamanın "gerçeği". Veritabanı sütunu değil, iş kavramı.
- `Subscription` burada tanımlıdır; `SubscriptionEntity` **değildir**.
- Repository **arayüzü** burada durur, gerçeklemesi Data'da.

### Data
- Room entity'leri, DAO, veritabanı, mapper.
- `SubscriptionRepositoryImpl`, DAO'dan gelen `Flow<List<SubscriptionEntity>>`'yi
  mapper'dan geçirip `Flow<List<Subscription>>` olarak yukarı verir.
- **Entity asla UI katmanına çıkmaz.**

### UI
- Composable'lar aptal olmalı: state alır, event yollar. Hesap yapmaz.
- ViewModel tüm state'i tek bir `UiState` içinde tutar.
- Composable'da `remember { mutableStateOf }` sadece **geçici görsel durum**
  için kullanılır (ör. sheet açık mı). Veri state'i ViewModel'dadır.

---

## 4. State Akışı

```
Room (Flow)
   ↓
DAO: Flow<List<SubscriptionEntity>>
   ↓  mapper
Repository: Flow<List<Subscription>>
   ↓  stateIn(viewModelScope, WhileSubscribed(5_000), UiState.Loading)
ViewModel: StateFlow<HomeUiState>
   ↓  collectAsStateWithLifecycle()
Composable: HomeUiState
```

Kullanıcı etkileşimi ters yönde gider:

```
Composable → onEvent(HomeEvent.Delete(id)) → ViewModel
           → viewModelScope.launch { repository.delete(id) }
           → Room → Flow tetiklenir → liste kendiliğinden güncellenir
```

**Önemli:** Silme sonrası listeyi elle güncellemiyoruz. Room'un Flow'u
değişikliği kendisi yayınlıyor. Bu tek yönlü veri akışının (UDF) özüdür.

---

## 5. UiState Deseni

Her ekranın tek bir state sınıfı ve tek bir event arayüzü olur.

```kotlin
// HomeUiState.kt
data class HomeUiState(
    val subscriptions: List<Subscription> = emptyList(),
    val monthlyTotal: Money = Money.ZERO,
    val isLoading: Boolean = true,
    val errorMessage: UiText? = null,
)

sealed interface HomeEvent {
    data class Delete(val id: Long) : HomeEvent
    data class Save(val name: String, val rawPrice: String, val currency: String) : HomeEvent
    data object DismissError : HomeEvent
}
```

ViewModel yalnızca şunu açar:

```kotlin
val uiState: StateFlow<HomeUiState>
fun onEvent(event: HomeEvent)
```

`MutableStateFlow` **private** kalır. Composable'a 6 tane ayrı lambda
geçirmiyoruz, tek `onEvent` yeterli.

---

## 6. Para Birimi Kuralı

```kotlin
@JvmInline
value class Money(val cents: Long)
```

- Depolama: `Long`, kuruş cinsinden. 149,99 TL → `14999`
- Kullanıcı girdisi: `String` → doğrulanır → `Long`'a çevrilir (ViewModel'da)
- Gösterim: `NumberFormat.getCurrencyInstance(locale)` ile formatlanır
- **`Double` ile para hesabı yapmak yasaktır.** Ondalık kayan nokta hataları
  finansal veride kabul edilemez.

Farklı para birimleri toplanırken sabit bir dönüşüm tablosu kullanılır
(v1'de canlı kur yok). Dönüşüm mantığı Domain katmanındadır.

---

## 7. Bağımlılık Yönetimi (DI)

**Faz 3'e kadar:** Manuel constructor injection. Nesneler `Application`
sınıfında elle kurulur. Amaç: DI'ın hangi problemi çözdüğünü görmek.

**Faz 4'ten sonra:** Hilt.

```kotlin
@Module @InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SubTrackDatabase = ...

    @Provides
    fun provideSubscriptionDao(db: SubTrackDatabase): SubscriptionDao = db.subscriptionDao()
}
```

Repository bağlaması `@Binds` ile yapılır (interface → impl).

---

## 8. Thread Kuralları

- Room `suspend` fonksiyonları zaten IO thread'de çalışır — `withContext`
  gereksiz, eklemeyin.
- Room `Flow` sorguları da arka planda çalışır.
- Ağır hesaplama (istatistik toplama) gerekirse `Dispatchers.Default`.
- ViewModel'da her zaman `viewModelScope`. **`GlobalScope` yasak.**

---

## 9. Hata Yönetimi

**Repository `Result<T>` döndürmez.** (Karar Faz 6'da verildi, Faz 3'ten
ertelenmişti.) İmzalar düz kalır: `suspend fun insert(...): Long` gibi.

**Gerekçe:** Hatalarımızın çoğu veritabanı hatası değil, **girdi hatası** — boş
ad, sayı olmayan fiyat, negatif tutar. Bunlar ViewModel'da, Room'a hiç
ulaşmadan yakalanıyor. `Result<T>` bu hataların hiçbirine dokunmaz; yalnızca
nadir DB hataları için her çağrı yerine bir sarmalayıcı açma yükü getirir.

### Nerede ne yapılır

| Hata türü | Nerede yakalanır | Nasıl gösterilir |
|---|---|---|
| Girdi hatası (boş ad, geçersiz fiyat) | ViewModel, doğrulama sırasında | `UiState.nameError` / `priceError` — **ilgili alanın altında** |
| Veritabanı hatası | ViewModel, `try/catch` ile | `UiState.errorMessage` → Snackbar |

- **Alan hatası ile genel hata ayrıdır.** Yanlış olan alan belliyse hata o alanın
  altında görünür; tepede "bir şeyler yanlış" diyen tek bir mesaj yeterli değil.
- Kullanıcıya gösterilecek metin `UiText` sarmalayıcısıyla taşınır
  (string resource ID'si veya düz metin) — ViewModel `Context` bilmez.
  `UiText` `ui/common/` altındadır; çözümlemesi Compose'a bağlı olduğu için
  domain'e konmadı.
- **Sessiz `try/catch { }` yasaktır.** Yakalanan her hata `errorMessage`'a
  dönüşüp kullanıcıya ulaşır.
- `CancellationException` yakalanmaz, **yeniden fırlatılır.** Coroutine iptali
  bir hata değildir; yutulursa iptal edilmiş bir iş veritabanı hatası gibi
  raporlanır.

---

## 10. İsimlendirme

| Tip | Kural | Örnek |
|---|---|---|
| Composable | PascalCase, isim tamlaması | `SubscriptionCard` |
| ViewModel | `<Ekran>ViewModel` | `HomeViewModel` |
| UiState | `<Ekran>UiState` | `HomeUiState` |
| Event | `<Ekran>Event` | `HomeEvent` |
| Entity | `<Model>Entity` | `SubscriptionEntity` |
| DAO fonksiyonu | fiil + isim | `observeAll`, `insert`, `deleteById` |
| Test | `metod_kosul_beklenenSonuc` | `save_emptyName_returnsError` |
| Boolean | `is`/`has`/`should` öneki | `isLoading`, `hasSubscriptions` |

---

## 11. Test Stratejisi

| Katman | Test tipi | Araç |
|---|---|---|
| ViewModel | Birim | JUnit + Turbine + fake repository |
| Repository | Birim | JUnit + fake DAO |
| DAO | Enstrümantasyon | Room in-memory database |
| Mapper | Birim | JUnit, saf fonksiyon |
| Composable | (v1'de zorunlu değil) | Compose UI test |

Kural: **fake yaz, mock kütüphanesi kullanma.** Interface'ler zaten
Domain'de tanımlı, elle fake yazmak hem daha okunur hem daha hızlı.

### ViewModel testleri public yüzeyden yazılır

Testler `onEvent` gönderip `uiState`'e bakar. **Bir fonksiyonu test etmek için
görünürlüğü değiştirilmez** — `private` olan `private` kalır.

Gerekçe: davranış test edilir, implementasyon değil. `HomeViewModel.parsePrice`
private olduğu için fiyat doğrulaması `onEvent(Save(...))` üzerinden test edildi;
fonksiyon yarın yeniden adlandırılsa veya başka bir sınıfa taşınsa testler
geçerli kalır.

### `stateIn` + `WhileSubscribed` test ederken toplayıcı şart

`WhileSubscribed` ile kurulan bir `StateFlow` **soğuktur**: kimse toplamadığı
sürece `.value` yalnızca `initialValue` döndürür.

Testte `backgroundScope.launch { uiState.collect() }` ile bir toplayıcı
açılmalı. Açılmazsa testler başlangıç değerini görür ve **hiçbir şey
doğrulamadan yeşil geçer** — sessizce işe yaramaz bir test paketi, ki bu
başarısız testten daha tehlikelidir.

---

## 12. Mevcut Koddan Taşınacak Borçlar

Analiz raporundan (Ağustos 2026) gelen, mimariyi ilgilendiren maddeler:

- `MainActivity.kt` her şeyi barındırıyor (data class dahil) → parçalanacak
- State `remember { mutableStateListOf }` içinde, `rememberSaveable` bile değil
- `Theme.kt`'deki koyu tema tanımlı ama kullanılmıyor (renkler hardcoded)
- `Type.kt` tamamen ölü kod (`MaterialTheme`'e `typography` geçilmemiş)
- `derivedStateOf`'un `remember(list.size)` key'i amacını boşa çıkarıyor
- Form ekranında 5 hardcoded Türkçe metin
- Compose BOM 2024.09.00 ile `activity-compose` 1.12.4 arasında olası sürüm
  uyuşmazlığı — `./gradlew :app:dependencies` ile doğrulanacak

### Faz 1a'da eklenen teknik borç

- `gradle.properties`'teki `android.disallowKotlinSourceSets=false` geçicidir.
  KSP 2.0.2 ürettiği kaynakları `kotlin.sourceSets` ile kaydediyor, AGP 9'un
  yerleşik Kotlin'i bunu yasaklıyor; bu anahtar AGP'nin kendi önerdiği geçiş
  yolu. KSP `android.sourceSets` DSL'ine geçince kaldırılıp denenmeli.
- `MainActivity`'deki monoton id sayacı geçicidir. Faz 5'te Room'un
  `@PrimaryKey(autoGenerate = true)` alanı devralacak — `AUTOINCREMENT`
  silinen id'leri asla geri kullanmadığı için sayaç gereksiz kalacak.

### Mimari karar: kaydırarak silme kendi bileşenimizde

material3'ün `SwipeToDismissBox`'ı **kullanılmıyor**. Yerine `SwipeToDeleteRow`
adlı özel bileşen var (şimdilik `MainActivity.kt` içinde, Faz 5'te
`ui/home/components/` altına taşınacak).

**Gerekçe:** `AnchoredDraggable`'ın fling/anchor mantığı ardışık kaydırmada
offset biriktiriyor. Eşik altı kaydırmada `settledValue` hiç değişmediği için
kütüphanenin `enabled = settledValue == Settled` koruması devreye girmiyor;
geri yaslanma animasyonu ile kullanıcı sürüklemesi aynı
`MutatePriority.Default`'ta olduğundan yeni jest animasyonu bulunduğu yerde
iptal ediyor ve kalan offset bir sonraki kaydırmanın üstüne biniyor. Public
API'de bunu engellemenin yolu yok: `gesturesEnabled`'ı offset'e bağlamak
devam eden jesti öldürüyor, effect'ten `snapTo` kullanıcının parmağını
eziyor, `flingBehavior` da dışarıdan enjekte edilemiyor.

Kendi bileşenimizde animasyonun sahibi biz olduğumuz için jest başında offset
sıfırlanıyor (`onDragStarted`), böylece birikme yapısal olarak imkânsız.

**Silme kuralı:** tek şart, kart genişliğinin **%50**'si kadar mesafe. Hız hiç
oy kullanmıyor. (`computeTarget`'ın üç dalından ikisi `positionalThreshold`'u
hiç okumuyordu; hızlı fiskenin silmesinin sebebi buydu.)

### Bilinen borç: tanımlanmamış renk rolleri

`colorScheme`'de **tanımlamadığımız her rol Material baseline değerine düşer** ve
paletimizin dışında renkler üretir.

Tespit edilenler:

| Rol | Düştüğü değer | Nerede görünüyor |
|---|---|---|
| `inversePrimary` | #D0BCFF / #6750A4 (mor) | Snackbar'ın eylem düğmesi ("Geri al") |
| `inverseSurface` | #322F35 / #E6E0E9 (nötr gri) | Snackbar zemini |

Kontrastları AA'yı geçiyor, yani erişilebilirlik sorunu değil — **kimlik**
sorunu. Paletimiz mavi-camgöbeği ailesinde, oradan mor çıkıyor.

**Faz 14'te tüm roller gözden geçirilecek**, sadece bu ikisi yamanmayacak.

**Yeni bir `colorScheme` rolü kullanmadan önce `Theme.kt`'de tanımlı olup
olmadığı kontrol edilmeli.** Tanımsızsa ya tanımlanır ya da o rolü kullanan
bileşenin palet dışına çıkacağı bilinerek kullanılır.

### Snackbar süresi her zaman açıkça verilir

material3'te `showSnackbar`'ın varsayılan süresi **`actionLabel` verilip
verilmediğine göre değişir**:

```kotlin
duration: SnackbarDuration =
    if (actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Indefinite
```

Yani bir eylem düğmesi eklemek, Snackbar'ı farkında olmadan **süresiz** yapar.
Faz 6'da "Geri al" butonu eklendiğinde tam olarak bu oldu. **`duration` her
çağrıda açıkça yazılmalı**, varsayılana bırakılmamalı.

### Açılış performansı release build'de ölçülür

Bu projede debug build, release'e göre **~9,5 kat** yavaş açılıyor
(ölçüm: OPPO CPH2179 / Android 10, medyan **8100 ms** ve **856 ms**).

Sebep kod değil, `debuggable` bayrağı: ART uygulamanın dex'ini optimize
edemiyor ve süre `BIND_APPLICATION` aşamasında harcanıyor — yani uygulamanın
kendi kodu daha çalışmadan. Ölçüm yöntemi:

```bash
adb shell am start -W -S -n com.elinacn.subtrack/.MainActivity
```

**Açılış süresiyle ilgili bir yargıya varmadan önce release build'de ölçün.**
Debug build'deki yavaşlık normaldir ve optimize edilmemelidir.

### Mimari karar: renk şemaları `by lazy` ile kurulur

`Theme.kt`'deki `DarkColorScheme` ve `LightColorScheme`, top-level `val` değil
**`by lazy`**'dir. **Bu yapı bozulmamalı.**

**Gerekçe:** Top-level `val` sınıf yüklenirken hemen çalışır. JVM'de bir static
initializer bir kez patlarsa sınıf **kalıcı olarak** "hatalı" işaretlenir ve
sonraki her erişim `NoClassDefFoundError: Could not initialize class ThemeKt`
verir — asıl hata bir daha görünmez. Faz 5b'de altı preview'ın tamamı tam olarak
böyle ölüyordu ve gerçek sebep maskeleniyordu.

`by lazy` ile sınıf yüklenirken çalışacak bir şey kalmıyor; bir sorun varsa
kendi mesajıyla, gerçekten oluştuğu yerde görünüyor. Davranış aynı: aynı
renkler, aynı roller, şemalar yine bir kez üretilip önbelleğe alınıyor.

### Preview bozulduğunda ilk yapılacak

Compose preview'ları render olmuyorsa, koda dokunmadan önce Android Studio'da
**Build → Clean Project**, ardından **File → Invalidate Caches / Restart**.

Gradle derlemesi tertemiz geçerken Studio'nun sahte syntax hataları göstermesi
veya *"No preview found"* demesi **indeks bozukluğudur**, kod hatası değil.
Faz 5b'de tema düzeltmesinden sonra tam olarak bu yaşandı: 56 uydurma hata,
Gradle tarafında sıfır sorun.

### Bağımlılık sürüm tabanı ve AGP eşiği

Proje **AGP 9.0.1 / compileSdk 36.1** üzerinde duruyor.

`hilt-navigation-compose` **1.3.0**'da tutuluyor: 1.4.0, `checkDebugAarMetadata`
aşamasında derlemeyi durduruyor ve **compileSdk 37 ile AGP 9.1.0** istiyor.

**Bu tek bir kütüphanenin sorunu değil, bir eşik.** Yeni androidx sürümleri
giderek aynı tabanı isteyecek. Faz 9 (DataStore) veya Faz 10 (WorkManager)
bunu zorunlu kılarsa, ROADMAP'te Faz 16'da duran **AGP yükseltmesi öne
çekilecek.** Yükseltme kendi başına bir faz gibi ele alınmalı: AGP 9.0 → 9.1
geçişi Faz 1a ve Faz 4'te yaşadığımız plugin sürümü/scope sorunlarını yeniden
açabilir.

### Mimari karar: Hilt plugin'i sadece `:app`'te tanımlıdır

Hilt Gradle plugin'i **root build dosyasında bildirilmez** — diğer plugin'lerin
aksine `apply false` satırı yoktur. Yalnızca `app/build.gradle.kts` içinde.

**Gerekçe:** Hilt'in plugin'i KSP'nin task sınıfını arıyor. İkisi farklı
scope'ta tanımlanırsa farklı class loader'lara düşüyorlar ve arama boşa
çıkıyor; yapılandırma şu hatayla patlıyor:

> `The KSP plugin was detected to be applied but its task class could not be
> found. ... the Hilt Gradle Plugin is using a different class loader because
> it was declared at the root while KSP was declared in a sub-project.`
> ([google/dagger#3965](https://github.com/google/dagger/issues/3965))

KSP `:app`'te durduğu için Hilt de orada duruyor. **Root'a "tutarlılık" adına
geri eklenmemeli** — root dosyasında bunu hatırlatan bir yorum var.

### Şema sürümlemesi

**Uygulama yayınlanana kadar** şema değişikliklerinde migration yazılmaz:
sürüm 1 yeniden üretilir ve `app/schemas/1.json` güncellenir.

**Gerekçe:** Hiçbir kullanıcıda veri yok, korunacak bir şey yok. Migration
yazmak var olmayan veriyi korumak için emek harcamaktır.

**Play Store'a çıktıktan sonra bu kural biter.** Her şema değişikliği migration
gerektirir, istisnası yoktur — o noktadan sonra cihazlarda gerçek veri vardır ve
onu bozmak geri alınamaz.

> Bu satır **Faz 16'da tekrar okunmalı.** Yayın anı kuralın değiştiği andır.

Faz 12a'daki geçmiş takibi tablosu bu kural kapsamında sürüm 1'e eklenecek.

### Mimari karar: şema dışa aktarılır

`SubTrackDatabase` `exportSchema = true` ile tanımlıdır. Room her sürüm için
`app/schemas/` altına bir JSON yazar ve **bu dosyalar commit'e dahildir.**

**Gerekçe:** Migration doğrulaması bu JSON'a dayanır. Kapalı olsaydı Room,
entity ilerledikten sonra önceki sürümün nasıl göründüğünü bilemez ve yazılan
migration'ın doğru olduğunu denetleyemezdi. Faz 7'deki DAO enstrümantasyon
testlerinin de referansı bu dosya olacak. Bedeli sürüm başına tek bir JSON.

Şema konumu `app/build.gradle.kts` içinde KSP argümanıyla verilir
(`room.schemaLocation`). Room Gradle plugin'i kullanılmadı — AGP 9 ile yaşanan
plugin sürüm çakışmalarından sonra bir eklenti daha eklemek yerine, mevcut KSP
argümanı tercih edildi.

### Mimari karar: enum dönüşümü fallback ile yapılır

`SubscriptionMapper`, veritabanındaki metni enum'a çevirirken **`enumValueOf`
kullanmaz.** O fonksiyon tanımadığı bir isimde exception fırlatır; elle
düzenlenmiş ya da daha yeni bir uygulama sürümünün yazdığı tek bir bozuk satır,
tüm listenin okunmasını çökertirdi.

Bunun yerine isim eşleştirilir ve eşleşme yoksa varsayılana düşülür
(`BillingPeriod.MONTHLY`, `SubscriptionCategory.OTHER`). Bu, §9'un yasakladığı
sessiz `try/catch` değildir: davranış kodda açıkça yazılıdır ve kaybedilen şey
yalnızca o tek alandır, satırın tamamı değil.

### Mimari karar: mavi iki role bölünmüştür

| Rol | Renk | Kullanım |
|---|---|---|
| `primary` | `DeepBlue` #46707F | Metin ve ikon aksanı: abonelik fiyatı, kart ikonu |
| `primaryContainer` | `PastelBlue` #AEC6CF | Dolu yüzeyler: dashboard kartı, FAB, Kaydet butonu |
| `onPrimaryContainer` | `DarkText` #2D3436 | O yüzeylerin üstündeki yazı ve ikon |

**Bu ayrım kasıtlıdır.** `PastelBlue` dolu bir yüzey olarak güzel çalışıyor
(üstünde `DarkText` ile 7.11:1), ama *metin rengi* olarak beyaz kart üzerinde
yalnızca **1.78:1** veriyordu — fiyatlar silik görünüyordu. Tek bir maviyi
koyulaştırmak dashboard kartını ve FAB'ı da değiştirirdi; ikiye bölmek pastel
kimliği yüzeylerde korurken metnin WCAG AA eşiğini geçmesini sağlıyor.

**Yeni renk eklenirken bu ayrıma uyulmalı:** metin/ikon olarak kullanılacak bir
renk `primary` ailesinden ve kontrast hesabı yapılmış olmalı; dolu bir yüzey
gerekiyorsa `*Container` rolleri kullanılmalı. Bir rengi hem zemin hem metin
olarak kullanmak bu paletle çalışmıyor.

Koyu şemada `primary` `PastelBlue` olarak kalır — koyu yüzey üstünde zaten
7.11:1 veriyor, koyulaştırmaya gerek yok.

**Sürükleme `Animatable` ile değil düz `mutableFloatStateOf` ile yapılır.**
`Animatable`, `snapTo` ve `animateTo` çağrılarını tek mutex ile koruyor;
sürükleme deltaları kuyruğa girdiğinde bekleyen bir `snapTo`, yerleşme
animasyonunu iptal edip `onDelete`'i düşürüyordu. Yerleşme için `animate()`
suspend fonksiyonu kullanılır — tek doğruluk kaynağı, yarış yok.

---

## 13. Navigation

- Tek Activity, tek `NavHost` (`ui/navigation/SubTrackNavHost.kt`).
- Rotalar düz `String` sabiti (`ui/navigation/Destination.kt`). Type-safe rota
  v1.0'da kullanılmıyor: argümansız iki hedef için kazancı yok ve AGP 9'da ek
  derleyici plugin'i riski var (bkz. Faz 0, @Parcelize). Faz 15'te yeniden bakılacak.
- `hiltViewModel()` yalnızca `composable` bloğunda çağrılır. Ekran composable'ları
  durumsuz kalır; preview'lar Hilt grafına ihtiyaç duymaz.
- ViewModel ömrü `NavBackStackEntry`'ye bağlıdır — hedef geri yığınında durduğu
  sürece ViewModel yaşar.

## 14. Kullanıcı Tercihleri (DataStore)

- **Preferences DataStore** kullanılır, Proto değil.
- Room ile aynı desen: arayüz domain'de, gerçekleme data'da, `DataStore<Preferences>`
  Hilt'ten `@Singleton`. Aynı dosya için ikinci instance çalışma zamanı hatasıdır;
  tekillik Hilt'in sorumluluğundadır.
- Okuma hatasında (`IOException`) `emptyPreferences()`'a düşülür, varsayılanlar
  kullanılır. Bu açık bir fallback, sessiz `try/catch` değil (§9). Diğer hata
  tipleri yeniden fırlatılır.
- Varsayılan ana para birimi: `TRY`.

## 15. Döviz Kurları

- Kurlar DataStore'da `Long`, **10.000 ölçekli**, para birimi başına bir
  anahtar. Domain'deki `ExchangeRateTable` ile aynı gösterim (§6, Faz 9a).
- **TRY çıpadır**, değeri sabit 10.000 ve düzenlenemez. Diğer kurlar
  "1 birim yabancı para = kaç TRY" anlamındadır; arayüz yönü açıkça yazar.
- Anahtarı olmayan para birimi `ExchangeRateTable.Default`'a düşer.
- **Sıfırlama anahtarları siler, varsayılanı yazmaz.** Böylece ileride
  varsayılanlar güncellenirse sıfırlayan kullanıcı yeni değerleri alır.
- Girdi `BigDecimal` üzerinden ayrıştırılır (§6). En fazla dört ondalık —
  ölçek 10.000, fazlası temsil edilemez ve sessizce yuvarlanamaz.
- **Sıfır veya ölçek altı kur reddedilir.** `CurrencyConverter` hedef kuru
  bölen olarak kullanır; sıfır kur sıfıra bölmedir. Doğrulama bunu kesin
  engeller, alt sınır bir ölçek birimidir.
- Üst sınır `Long` taşmasına göre belirlenir ve testle sabitlenir.
- Son düzenleme zamanı epoch millis olarak saklanır ve kullanıcıya gösterilir.
  Hiç düzenlenmemişse varsayılanların tahmin olduğu söylenir.

## 16. IME (Klavye) Insets

**Durum: ölçüldü, geçici bir çözümle kapatıldı.** Bu bölüm hem ölçümü hem de
ondan çıkan kararı kaydeder; kalıcı çözüm Faz 16'ya bağlı.

### Ölçülen

Faz 9b-2 hotfix'inde, kur ekranına geçici bir probe konup
`WindowInsets.ime.getBottom(density)` ve `WindowInsets.navigationBars.getBottom(density)`
okundu. Probe `Scaffold`'un dışında, composable gövdesinin en başındaydı.

| Cihaz | Klavye | `WindowInsets.ime` | `WindowInsets.navigationBars` |
|---|---|---|---|
| API 29, 360dp | kapalı | 0 | 0 |
| API 29, 360dp | **açık** (`mInputShown=true`) | **0** | 0 |
| API 34, 411dp | kapalı | 0 | 0 |
| API 34, 411dp | **açık** (`mInputShown=true`) | **0** | 0 |

Klavye açıkken tuşa basılarak **yeniden kompozisyon zorlandı** ve probe yeni
satır yazdı — yani okunan değer bayat değil, gerçekten sıfır.

### Neden sıfır

Beklenen, API 29'da `WindowInsets.ime`'in güvenilmez olması, API 34'te
çalışmasıydı. **İkisinde de sıfır çıktı**, yani sebep API sürümü değil.

Kanıt `navigationBars = 0`: bu iki emülatörde de gezinme çubuğu var, insets
uygulamaya ulaşsaydı orada sıfırdan başka bir şey görünürdü. Uygulama
`setDecorFitsSystemWindows(false)` / `enableEdgeToEdge` çağırmadığı için
pencere eski moddadır: insets'i decor view tüketir, Compose'a **hiç**
ulaşmaz. Aynı durum dump'ta da görünür — `android:id/content` API 29'da
`[0,48][720,1280]`, yani durum çubuğu payı zaten decor tarafından uygulanmış.

**Sonuç: `Modifier.imePadding()` bu kod tabanında iki API'de de işe
yaramaz — sıfır bir insets'e padding uygulamak hiçbir şey yapmaz.**
Bu yüzden uygulanmadı.

### Sheet'lerin neden sorunu yok

`ModalBottomSheet` içeriğini `Box(Modifier.fillMaxSize().imePadding())` içine
koyar (material3 1.4.0, `ModalBottomSheet.kt:186`). Sheet kendi penceresinde
(dialog) çizilir ve o pencere insets alır; bu yüzden ekleme sheet'i klavyeyle
doğru davranır. **Bu bir tesadüftür, uygulamanın bir kararı değil:** aynı
kütüphane kolaylığı normal ekranlarda yoktur.

### Karardan önceki davranış

`AndroidManifest.xml`'de `windowSoftInputMode` tanımlı değildi; platform
`adjustPan` gibi davranıyordu. Pencere küçülmez, kayar. Kaydırma görünümü de
küçülmediği için içerik klavyenin altında kalabilir ve kaydırarak
kurtarılamaz. Ölçülen dört kombinasyon `PROGRESS.md`'deki 9b-2 kaydında.

Kullanıcı için çıkış yolu var: geri tuşu klavyeyi kapatır, ekrandan çıkmaz.

### Verilen karar: `windowSoftInputMode="adjustResize"`

`MainActivity` manifestte `android:windowSoftInputMode="adjustResize"` alır.
Pencere klavye kadar **küçülür**; kaydırma görünümü de onunla küçülür, yani
altta kalan içerik kaydırılarak erişilebilir hâle gelir.

**Neden bu, `enableEdgeToEdge` değil:** ikisi de sorunu çözer, ama
`adjustResize` yalnızca pencere boyutlandırmasını değiştirir ve tek satırdır.
`enableEdgeToEdge` insets modelini baştan değiştirir; durum ve gezinme çubuğu
payları her ekranda elle uygulanmak zorunda kalır, yani her ekranın yeniden
ölçülmesi gerekir. O iş Faz 16'daki `targetSdk` yükseltmesine ait — Android
15'te edge-to-edge zaten zorunlu.

**Bu bilinçli olarak geçici bir çözümdür.** `adjustResize`, uygulama
edge-to-edge'e geçtiğinde sistem tarafından **yok sayılır**. Faz 16'da
`enableEdgeToEdge` gelince bu satır kaldırılacak ve yerine `imePadding()`
konacak; o noktada yukarıdaki ölçüm tekrarlanmalı, çünkü `WindowInsets.ime`
o zaman sıfırdan farklı okunmaya başlayacak.

### Uygulandıktan sonra ölçülen

`adjustResize` ile kur ekranında kaydırma görünümü klavye üst kenarında
kesiliyor ve butonlara **tek fiskede** ulaşılıyor; dört kombinasyonun
hiçbirinde erişilemez buton kalmadı. Sayılar `PROGRESS.md`'deki hotfix
kaydında.

**Sheet'lerde çift uygulama yok.** Endişe, `ModalBottomSheet`'in kendi
`imePadding()`'i ile pencere küçülmesinin üst üste binip fazladan boşluk
yaratmasıydı. Ölçüldü: sheet'in klavyeli ve klavyesiz bütün koordinatları
9b-1 hotfix'indeki değerlerle **birebir aynı** kaldı. Sebebi yapısal — sheet
kendi dialog penceresinde çizilir ve `windowSoftInputMode` Activity'nin
penceresine uygulanır, o pencereye değil. İki mekanizma birbirine değmiyor.

## 17. Tarih İşleme

- Saklama: epoch millis `Long` (§6, Room şeması değişmiyor).
- Domain: `java.time.LocalDate`. Dönüşüm `SubscriptionMapper`'da, sistem
  saat diliminde.
- **Gerekçe:** "X gün kaldı" takvim günü farkıdır, zaman damgası farkı
  değil. 23:00 ile ertesi gün 01:00 arası iki saattir ama bir gündür.
  `Instant` üzerinden hesaplamak bu farkı kaybeder.
- `java.time` saf Java'dır, domain saflığını bozmaz (§1).
- `nextPaymentDate` NULLABLE. Tarih opsiyoneldir; boşsa arayüzde gösterge
  çıkmaz.
- **Tarih geçtiğinde otomatik ilerletme yapılmaz**, "gecikmiş" gösterilir.
  Ne kadar ilerleyeceği `billingPeriod`'a bağlıdır ve o alan Faz 12'ye
  kadar kullanıcı tarafından seçilmiyor; aylık varsaymak yıllık
  aboneliklerde veriyi sessizce bozar. İlerletme Faz 12'nin işidir.
- Geçmiş tarih kabul edilir. Üst sınır bugünden 10 yıl ileridir — kayan
  tuş vuruşunu yakalayan bir ürün sınırı (`MAX_PRICE` ile aynı mantık).
- `java.time` API 26'da geldi, `minSdk` 24. Core library desugaring açık
  (`desugar_jdk_libs`). minSdk 24 KORUNUYOR: API 24/25 hiç test edilmedi,
  yükseltmek yayın öncesi kullanıcı kaybetmek olur. Desugaring'in APK
  bedeli Faz 16'daki R8 ölçümünde değerlendirilecek.
- **Uyarı:** `assembleDebug` bu hatayı YAKALAMAZ — `compileSdk` sınıf
  yolunda `java.time` vardır, sorun ancak API 24/25 cihazda çalışma anında
  `NoClassDefFoundError` olarak çıkar. Yakalayan `lintDebug`'dır.
  `java.time` veya başka yeni API kullanan her değişiklikten sonra lint
  koşturulmalı.

---
## 18. Hatırlatma ve Bildirim

### Ürün kuralı

Bildirilenler: **bugün ödenecek**, **1 gün kalan**, ve gecikmesi **1-3 gün**
olanlar. Tarihi olmayan abonelik bildirilmez. Abonelik başına ayrı bildirim
yok — tek özet bildirim, sabit id, günde en fazla bir tane. Bildirimde tutar
yazmaz.

Başlık kapsanan abonelik **sayısını** söyler ve fiil kullanmaz. Aynı bildirim
hem bugün ödenecek hem üç gün gecikmiş bir aboneliği taşıyabildiği için
"yenileniyor" gibi tek duruma bağlanan bir başlık zamanın yarısında yanlış
olurdu.

### Gecikme penceresi neden var

§17 gereği **tarih geçtiğinde otomatik ilerletme yapılmıyor**. Yani gecikmiş
durum kalıcıdır; pencere olmasaydı bildirim de kalıcı olurdu ve kullanıcı her
gün aynı satırı görürdü.

İki eşik de `PaymentReminderSelection` içinde adlandırılmış sabittir
(`UPCOMING_WITHIN_DAYS`, `OVERDUE_WITHIN_DAYS`), koda gömülü sayı değil.

> **Faz 12 uyarısı:** ödeme periyodu gelip tarih ilerletilmeye başlandığında
> gecikmiş durum artık kalıcı olmayacak. Pencerenin gerekçesi o gün ortadan
> kalkar; eşikler yeniden değerlendirilmeli.

### Neden periyodik tarama, exact alarm değil

`setExactAndAllowWhileIdle` Android 12+'ta `SCHEDULE_EXACT_ALARM` izin rejimine
giriyor ve Play bu izni gerçekten saniyesi önemli işler için istiyor. Bir
abonelik hatırlatması o eşiği hak etmiyor.

**Bedeli açıkça kabul edildi:** WorkManager dakika garantisi vermez. Hedef
yerel 09:00'dır, taahhüt değil — Doze altında saatlerce kayabilir. Hatırlatma
için bu kabul edilebilir; ödeme anına bağlı bir iş olsaydı olmazdı.

### `KEEP` zorunludur

İş uygulamanın **her açılışında** `enqueueUniquePeriodicWork` ile kuyruğa
konuyor. `UPDATE` veya `REPLACE` ilk gecikmeyi her seferinde sıfırlar; yani
uygulamayı her gün açan bir kullanıcıda iş **hiç çalışmaz**. `KEEP` bir tercih
değil, tek doğru politika.

Constraint eklenmiyor: iş yerel veriyi okuyup bildirim gönderiyor, ağ da şarj
da boşta cihaz da gerekmiyor. Herhangi birini istemek yalnızca geciktirirdi.

### `InitializationProvider` paylaşılıyor

WorkManager'ın otomatik başlatıcısı kaldırıldı — worker'ın Hilt'ten gelen
fabrikaya ihtiyacı var, bu yüzden `Application` `Configuration.Provider`
uyguluyor ve WorkManager talep üzerine kuruluyor.

**Ama provider'ın kendisi kaldırılamaz.** `androidx.startup`'ın tek
`InitializationProvider`'ı emoji2, lifecycle ve profileinstaller tarafından da
kullanılıyor (Faz 10b Görev 0'da birleşik manifestte ölçüldü). Kaldırılırsa o
üç initializer de ölür. Yalnızca `androidx.work.WorkManagerInitializer`
`meta-data` satırı `tools:node="remove"` ile çıkarılır, provider
`tools:node="merge"` ile durur.

`Configuration.Provider` üyesi **property**'dir (`workManagerConfiguration`);
androidx.work 2.9'da fonksiyondan property'ye döndü.

### Gün yalnızca bildirim gerçekten gösterildiğinde işaretlenir

`PaymentReminderNotifier.notify(...)` **`Boolean` döner**: bildirimin ekrana
ulaşıp ulaşmadığı. Worker günü yalnızca `true` dönerse yazar.

**Gerekçe:** bildirimler kapalıyken hiçbir şey gösterilmiyor. Günü yine de
işaretlemek, o günün hatırlatmasını kalıcı olarak yutardı — kullanıcı bir saat
sonra bildirimleri açsa bile ertesi güne kadar hiçbir şey görmezdi. Kayıt,
"iş koştu"nun değil "kullanıcıya söylendi"nin kaydıdır.

Her iki durumda da `Result.success()` dönülür. Bildirimlerin kapalı olması bir
hata değil, kullanıcının tercihi olabilir; `retry` yalnızca bu işin
değiştiremeyeceği bir ayarı beklerken pil harcardı.

**Bilinen sınır — Faz 10c-1'de KAPANDI, aşağıdaki "Kanal düzeyi tespit"
başlığına bakın. Kayıt olarak bırakılıyor:** `areNotificationsEnabled()`
**uygulama düzeyindedir.**
Kullanıcı yalnızca `payment_reminders` **kanalını** kapatmışsa bu fonksiyon
hâlâ `true` döner, `notify()` çağrılır, sistem bildirimi sessizce düşürür ve
gün yine de işaretlenir. Yani kanal bazlı kapatma bu koruma tarafından
yakalanmıyor. Kanal durumu `NotificationManagerCompat.getNotificationChannel`
ile okunabilir; bunun eklenip eklenmeyeceği ayrı bir karar.

### Bildirim durumu neden ayrı repository

Son bildirim günü **kullanıcı tercihi değil**, işin kendisi hakkında tuttuğu
kayıttır ve hiçbir ekranda görünmez. Bu yüzden `SettingsRepository`'nin
arayüzüne eklenmedi, ayrı ve küçük bir `ReminderStateRepository` aldı.

Depolama aynı: Hilt'in verdiği **mevcut** `DataStore<Preferences>` örneği
kullanılıyor. Aynı dosya için ikinci bir instance çalışma zamanı hatasıdır
(§14), ve tek bir `Long` için ikinci bir dosya ileride taşınacak fazladan bir
dosya olurdu. Gün **epoch day** olarak saklanır, epoch millis değil: "bugün
gönderildi mi" bir takvim sorusudur.

### Bildirim durumu üç haldir

Kullanıcının bildirimleri açması gereken durum ikiye ayrılır ve iki farklı
düğme ister. Bu yüzden durum `Boolean` değil, üç halli bir tiptir
(`ReminderPermissionState`):

| Hal | Anlamı | Satıra dokununca |
|---|---|---|
| `ENABLED` | Bildirimler görünüyor | Sistem bildirim ayarları açılır (kapatmak için) |
| `CAN_REQUEST` | Kapalı, uygulama izni kendisi isteyebilir | İzin istenir |
| `SETTINGS_ONLY` | Kapalı, yalnızca sistem ayarları açabilir | Sistem bildirim ayarları açılır |

Karar tablosu, sırayla:

1. Bildirimler görünüyorsa → `ENABLED`. (Bu dal her API sürümünü kapsar;
   eski sürümlerde tutulacak izin yok, yalnızca anahtar var.)
2. Bu derlemede runtime izin gerekmiyorsa (API < 33) → `SETTINGS_ONLY`.
   İstenecek bir şey yok.
3. İzin var ama bildirim görünmüyorsa → `SETTINGS_ONLY`. Uygulama anahtarı
   veya kanal kapalı; ikisi de kullanıcının kendi tercihi.
4. İzin yok ve **hiç sorulmamışsa** → `CAN_REQUEST`.
5. İzin yok, sorulmuş ve sistem hâlâ gerekçe göstermemize izin veriyorsa →
   `CAN_REQUEST`.
6. Kalan durum → `SETTINGS_ONLY`. Sorulmuş, gerekçe hakkı yok: kalıcı ret.

### "Hiç sorulmadı" ile "kalıcı reddedildi" neden bizim bayrağımızla ayrılır

`shouldShowRequestPermissionRationale` **iki durumda da `false` döner**: izin
hiç istenmemişse ve kullanıcı "bir daha sorma" ile reddetmişse. Sistem bu ikisini
ayırt edecek bir API vermiyor.

Ayrımı DataStore'daki kalıcı bir bayrak tutar
(`ReminderStateRepository.wasPermissionRequested`). Bayrak **istek gönderildiği
anda** yazılır, cevabından bağımsız — kullanıcının bir kez sorulmuş olması,
"evet" demesine bağlı değil.

Bu bayrak da bir kullanıcı tercihi değil, işin kendi kaydı; bu yüzden yeni bir
repository açılmadı, mevcut `ReminderStateRepository`'ye eklendi ve aynı
DataStore dosyasını kullanıyor (§14, ikinci instance yasak).

### İlk soruda araya diyalog konmaz

`CAN_REQUEST` + hiç sorulmamış → **doğrudan** sistem izin diyaloğu.
`CAN_REQUEST` + sorulmuş → önce bir cümlelik açıklama, sonra sistem diyaloğu.

Gerekçe: ilk seferde sistem diyaloğunun önüne konan fazladan bir ekran, izni
kazandırmaktan çok reddi artırır. İkinci soruda kullanıcı zaten bir kez hayır
demiştir; orada bir cümle açıklama borcumuzdur.

### Kanal düzeyi tespit — 10b'deki "bilinen sınır" kapandı

10b'de şu sınır kaydedilmişti: `areNotificationsEnabled()` uygulama
düzeyindedir, kullanıcı yalnızca `payment_reminders` kanalını kapatmışsa bu
yakalanmaz ve gün yine işaretlenir.

**Bu sınır kapandı.** Kontrol artık iki aşamalı ve tek bir yerde
(`ReminderNotificationStatus`):

1. `NotificationManagerCompat.areNotificationsEnabled()`
2. `getNotificationChannelCompat(CHANNEL_ID)?.importance != IMPORTANCE_NONE`

Kanal henüz oluşmamışsa engel sayılmaz — bildirim gönderilene kadar kanal
yoktur ve var olmayan bir kanal kullanıcının susturduğu bir kanal değildir.
API 26 altında da kanal kavramı yok, Compat orada da `null` döndürüyor; bu
yüzden elle `Build.VERSION` dallanması yazılmadı.

Emülatörde ölçüldü: kanal kapatıldığında `mImportance=0` oluyor, uygulama izni
`granted=true` kalıyor ve satır doğru şekilde `SETTINGS_ONLY`'ye düşüyor.

**Tek elle yazılan sürüm kontrolü**, "bu derlemede runtime izin gerekiyor mu"
sorusudur. Hiçbir Compat sınıfı bunu cevaplamıyor ve ayarlar ekranının iki
yolundan hangisinin mümkün olduğunu bilmesi gerekiyor.

### Notifier'daki ikinci izin kontrolü lint içindir

`PaymentReminderNotifier` kararı `ReminderNotificationStatus`'tan alır. Buna ek
olarak, `notify()` çağrısıyla **aynı fonksiyonda** bir
`ContextCompat.checkSelfPermission` satırı vardır.

Bunun sebebi lint'tir: `MissingPermission` denetimi arayüzün arkasını göremiyor
ve izni çağrı yerinde görmek istiyor. Kaldırıldığında derleme
`Call requires permission which may be rejected by user` hatasıyla duruyor.
Karar tek yerdedir; bu satır aynı sorunun aracın görebileceği biçimde
tekrarıdır, ikinci bir görüş değil. `@SuppressLint` yasak olduğu için
(CLAUDE.md §4) alternatifi yok.

### İzin bağlamsal olarak da istenir — tarihli ilk abonelikte

Kullanıcı Ayarlar'a kendiliğinden gitmez. Bu yüzden izin, hatırlatmanın ilk kez
anlam kazandığı anda da istenir: **tarihi olan ilk abonelik kaydedildiğinde.**

Tetiklenme koşulları, hepsi "isteme" sebebi:

| Koşul | Neden |
|---|---|
| Kaydedilen abonelikte tarih var | Tarihsiz abonelik için hatırlatılacak bir şey yok |
| Listedeki tarihli abonelik sayısı **1** | Bu, o ilk tarihli abonelik; sonrakilerde an geçmiş olur |
| Bildirimler görünmüyor | Görünüyorsa istenecek bir şey yok |
| Bu derlemede runtime izin gerekiyor | API < 33'te istenecek izin yok |
| İzin verili değil | Verili ama bildirim kapalıysa istek hiçbir şeyi değiştirmez |
| Daha önce hiç sorulmamış | Aşağıdaki gerekçe |

Yani yalnızca `CAN_REQUEST` dalında ve yalnızca **bir kez** istenir.
`ENABLED` ve `SETTINGS_ONLY` durumlarında hiçbir şey yapılmaz.

**Neden tek sefer:** sistem ikinci retten sonra kapıyı kapatıyor
(`USER_FIXED`), ondan sonra istek diyaloğu hiç görünmüyor. Elimizde bir tane
şans var ve o şans, özelliğin yeni yararlı hâle geldiği ana harcanıyor.
Reddedilirse ısrar edilmez; kullanıcının yolu Ayarlar satırıdır.

**Bayrak tek anahtardır, iki yerden yazılır.** Aynı
`ReminderStateRepository.wasPermissionRequested` kaydı hem Ayarlar akışında hem
burada yazılır — istek gönderildiği anda, cevabından bağımsız. İki ayrı anahtar
açılmaz: Ayarlar satırının "bir kez soruldu" bilgisine ihtiyacı var ve bu
istek de bir sorudur. Yazılmasaydı, kullanıcı burada reddettikten sonra Ayarlar
satırı hâlâ "hiç sorulmadı" sanıp açıklama diyaloğunu atlardı.

### İstek, sheet tamamen kapandıktan sonra gönderilir

Ekleme sheet'i kendi penceresinde çizilir ve kapanırken bir animasyonu vardır.
İstek o sırada gönderilirse sistem diyaloğu kapanmakta olan sheet'in üstüne
biner ve ikisi de kırpılır.

Sıralama, sheet'i ağaçta tutan koşulun tersiyle kurulur:

```kotlin
val isAddSheetGone = !uiState.isAddSheetOpen && !sheetState.isVisible
```

`isAddSheetOpen` durumun kapandığını, `sheetState.isVisible` gizlenme
animasyonunun bittiğini söyler. İstek yalnızca ikisi de sağlandığında gönderilir.
Emülatörde ölçüldü: diyalog ağaçtayken sheet ne erişilebilirlik ağacında ne de
pencere listesinde var.

Tetik, gönderildiği anda `NotificationRequestHandled` olayıyla temizlenir.
ViewModel döndürmede yaşadığı için temizlenmeseydi ekran yeniden kurulduğunda
istek ikinci kez gönderilirdi. Ölçüldü: diyalog açıkken döndürmek ikinci bir
diyalog üretmiyor, kapatıldıktan sonra geri döndürmek de üretmiyor.

**İlk soruda araya açıklama diyaloğu girmez** — Ayarlar akışındaki kuralın
aynısı. Bu zaten tanım gereği ilk sorudur.

### Sistem ayarlarından dönüşte durum tazelenir

Ekran `ON_RESUME`'da durumu yeniden okur. Olmasaydı kullanıcı sistem
ayarlarından bildirimleri açıp geri döndüğünde satır hâlâ "kapalı" derdi.
Emülatörde ölçüldü: süreç kimliği değişmeden (aynı pid) satır "Açık"a
dönüyor.

`ACTION_APP_NOTIFICATION_SETTINGS` bulunamazsa
`ACTION_APPLICATION_DETAILS_SETTINGS`'e düşülür. Bu **açık** bir fallback'tir,
kodda `ActivityNotFoundException` yakalanıp gerekçesiyle yazılmıştır — §9'un
yasakladığı sessiz `try/catch` değil.

### `POST_NOTIFICATIONS`

İzin manifestte bildirilmiştir; **runtime isteği Faz 10c'nin işidir.** Gönderim
öncesi `NotificationManagerCompat.areNotificationsEnabled()` kontrol edilir —
API 33+'ta reddedilmiş izin zaten bunu `false` yapar, yani 10c gelene kadar
beklenen davranış sessiz kalmaktır.

Ayrıca lint'in `MissingPermission` hatası için **API 33 ve üstünde koşullu**
bir `ContextCompat.checkSelfPermission` kontrolü var. Koşul şarttır: izin API
33'ün altında tanımlı değildir ve orada `checkSelfPermission` "reddedildi"
cevabı verip hatırlatmayı her eski cihazda susturur.

Kanal her gönderimden önce yeniden oluşturulur. Var olan bir kanalı oluşturmak
işlemsizdir; asıl kazanç, cihaz dili değişince kanal adının ve açıklamasının
güncellenmesidir.

---
