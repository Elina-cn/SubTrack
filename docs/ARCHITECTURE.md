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
