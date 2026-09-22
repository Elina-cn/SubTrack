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

**Eylemi olmayan ekranda `Event` tipi de olmaz.** Kuralın gerekçesi altı lambda
yerine tek giriş noktası; yapılacak hiçbir şey yoksa geçirilecek lambda da yok.
İstatistik ekranı (Faz 13a) böyle: boş bir `sealed interface` ve dalsız bir
`onEvent` mimari değil merasim olurdu (§20).

### Form alanları composable'da, listeyi etkileyen state ViewModel'da

Kural iki cümle:

- Yalnızca kendi formunu ilgilendiren **geçici alan** (ad, fiyat, para birimi,
  tarih, kategori) onu çizen composable'ın `rememberSaveable`'ında yaşar ve
  kayıt anında tek bir `Save` event'iyle ViewModel'a geçer.
- Ekranda **ne görüneceğini belirleyen** state (liste, toplam, filtre, hata)
  `UiState`'te yaşar.

Ayırt edici soru: *bu değer değişince listenin veya toplamın gösterdiği şey
değişiyor mu?* Evetse ViewModel'ın, hayırsa formun.

Gerekçe: form alanı ViewModel'a konursa her tuş vuruşu `UiState`'i yeniden
yayınlar ve tüm ekran recompose olur; ayrıca vazgeçme ve sıfırlama akışları
iki ayrı yerden yönetilmek zorunda kalır. Filtre bunun tam tersi: liste ve
toplam ondan **türetiliyor**, yani `UiState`'in dışında duramaz.

Bu, §3'teki "composable `ViewModel` dışı state sahibi olmaz" kuralının
istisnası değil, okunuşu: sheet'in `rememberSaveable`'ı ekranın state'i değil,
henüz kaydedilmemiş bir formun taslağıdır.

**11a'daki tutarsızlık ve 11b'de kapanışı.** 11a kategoriyi
`HomeUiState.selectedCategory` + `HomeEvent.SelectCategory` olarak eklemişti;
formun diğer dört alanı ise sheet'in kendi state'indeydi — beş alandan dördü
bir yerde, biri başka yerde. 11b'nin ilk işi kategoriyi sheet'e indirmek oldu
(`rememberSaveable(stateSaver = CategorySaver)`, `CurrencySaver`'ın deseni),
`Save` event'i beş parametreye çıktı, `selectedCategory` ve `SelectCategory`
kaldırıldı. Aynı fazda eklenen **filtre** ise ViewModel'da kaldı: yukarıdaki
soruya "evet" cevabını veren tek alan o.

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

### Periyot normalizasyonu — toplam maliyeti gösterir, fiyatları değil

Abonelikler farklı saatlere göre ödenir. Toplam tek bir sayıysa hepsinin aynı
birime indirgenmesi gerekir; yoksa yıllık bir fiyatla aylık bir fiyat toplanır
ve çıkan sayının bir karşılığı olmaz (Faz 12-1'e kadar olan davranış buydu).

- `BillingPeriod.paymentsPerYear` — aylık **12**, yıllık **1**, haftalık **52**.
  Fiyatı bununla çarpmak **yıllık maliyeti** verir: çarpma tamdır, bu adımda
  yuvarlama yoktur.
- Aylık figür o yıllık toplamın 12'ye bölünmüşüdür. Yıllık görünüm ise
  bölünmemiş hâlidir — **aylık figürün 12 katı değil.** İkisi de aynı ara
  değerden çıkar; biri diğerinden türetilseydi bir yuvarlama iki kez yansırdı.

**Haftalık 52 sayılır, 365,25 / 7 = 52,18 değil.** Kimse haftanın beşte biri
kadar ödemiyor; o ondalıklar elle kontrol eden kullanıcıya açıklanamaz ve
sonucu, hiçbir abonelik fiyatının zaten taşımadığı bir hassasiyet kadar
oynatır. "Haftalık" ödeyen için yılda 52 ödeme vardır.

### Tek yuvarlama noktası

Bir tutar hem periyottan hem para biriminden geçtiğinde **iki kesir** vardır:
yıllık/12 ve kaynak kuru/hedef kuru. Ayrı ayrı yuvarlanırlarsa yarımlar
birbirini götürmez ve hata kalıcı olur — ölçüldü: haftalık **10,00 USD**'lik
tek bir abonelikte fark **14 kuruş** (1.856,83 yerine 1.856,69), ve liste
büyüdükçe büyür.

Kural: **önce çarp, en sonda bir kez böl.** `CurrencyConverter`'ın periyot alan
`totalIn` aşırı yüklemesi tek bölme yapıyor:

```
Σ(fiyat × paymentsPerYear) × kaynakKuru / (hedefKuru × parça)
```

`parça` aylık görünümde 12, yıllık görünümde 1. Yuvarlama HALF_UP, girilen
fiyatın okunduğu yönle aynı.

**Ara değer `BigInteger`, sonuç hâlâ `Long` kuruş.** Normalizasyon paya 52'ye
kadar bir çarpan sokuyor: haftalık bir fiyat, kur ile çarpılmadan önce zaten
52 ile çarpılmış oluyor ve bu çarpım uygulamadaki en geniş değer. Long'da
ölçüldü — fiyat tavanı (1.000.000 birim) ve kur tavanı (1.000,0000) birlikte,
satırların hepsi haftalıkken **177 satırdan** sonra taşıyordu; normalizasyondan
önce aynı sınır 9.223'tü. Sayı bugünkü hiçbir listenin ulaşamayacağı yerde ama
bu fazda daralan bir paydı, o yüzden kapatıldı: **ara değer `BigInteger`'a
taşındı** (12-1 hotfix).

Kapatma biçimi önemli:

- **Hiçbir tavan değişmedi.** Fiyat tavanı ve kur tavanı aynı; kullanıcıya
  dönük hiçbir sınır oynamadı.
- **İmzalar aynı.** Fiyatlar `Long` kuruş girer, cevap `Long` kuruş çıkar;
  `BigInteger` yalnızca zincirin içinde.
- **Yuvarlama aynı.** `BigInteger` de sıfıra doğru kırpıyor, yani bölmeden önce
  yarım eklemek iki tipte de aynı sonucu veriyor. 12-1'de ölçülen değerlerin
  hepsi (243,33 · 2.920,00 · 14 kuruşluk fark) birebir korundu ve testleri
  beklentileri değişmeden geçti.

**Geriye kalan sınır cevabın `Money`'ye sığması.** Ara değerin tavanı yok ama
`Money` bir `Long`. Aynı tavanlarda, çapaya çevirirken haftalık bir satır ayda
433.333.333.333 kuruşa mal oluyor; yani aylık görünüm **21.284.704**, yıllık
görünüm **1.773.725** satırdan sonra sığmaz. Bellekte tutulan ve `LazyColumn`
ile çizilen bir listenin göremeyeceği bir yer. Bütün bu sayılar
`PeriodNormalisationTest`'te sabitlendi; biri kıpırdarsa test kırılır.

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

### Kapandı (Faz 14a): tanımlanmamış renk rolleri

**Bu borç kapandı.** `colorScheme`'de tanımlamadığımız her rol Material
baseline'ına düşüyordu ve paletin dışında renk üretiyordu. Faz 14a'da **her rol
tanımlandı** — kodun kendi adıyla çağırmadıkları dahil.

**Neden hepsi, sadece kullanılanlar değil.** "Biz kullanmıyoruz" ile "kimse
kullanmıyor" aynı şey değil: rolleri isteyen bileşenler Material'ın kendi
bileşenleri. Envanter bunu gösteriyordu —

| Rol | Kodumuz çağırıyor mu | Kim çiziyordu | Baseline'da ne çıkıyordu |
|---|---|---|---|
| `error` | **evet** (`SwipeToDeleteRow`) | silme zemini ve ikonu | Material kırmızısı |
| `onSurfaceVariant` | **evet** (`SettingsRow`, `SettingsScreen`) | alt satırlar, seçilmemiş chip etiketi | mor-gri |
| `outline` | hayır | `OutlinedTextField` kenarı, chip kenarlığı | mor-gri |
| `inversePrimary` | hayır | Snackbar'ın "Geri al"ı | **mor** |
| `inverseSurface` / `inverseOnSurface` | hayır | Snackbar zemini ve metni | nötr gri |
| `outlineVariant` | hayır | `HorizontalDivider` | mor-gri |
| `surfaceVariant`, `surfaceContainer*`, `scrim`, `secondary*`, `tertiary*`, `errorContainer` | hayır | tarih seçici, sheet, diyalog, elevation | baseline |

Faz 14a envanteri (kod taraması, `grep colorScheme.`):

- **Kullanılan roller (9):** `background` (5 dosya), `error` (1), `onBackground`
  (15), `onPrimaryContainer` (12), `onSurface` (7), `onSurfaceVariant` (2),
  `primary` (7), `primaryContainer` (10), `surface` (3).
- **Kullanılan ama tanımsız olanlar (2):** `error`, `onSurfaceVariant` → ikisi de
  artık tanımlı.
- **Tanımlı ama kodun çağırmadığı (4):** `onPrimary`, `secondary`, `onSecondary`,
  `tertiary` → Material bileşenleri bunları yine de çiziyor.

**Karar: şemanın tamamı tanımlanır** (37 rol, iki şemada da). Kullanılmayan bir
rolü tanımlamak ileride sessizce doğru rengi verir; tanımlamamak sessizce
baseline verir. Bu fazın amacı da tam olarak "hiçbir rol baseline'a düşmesin"di.

**Cihazda doğrulandı** (piksel, `screencap` ham RGBA):

| Ne | Önce | Sonra | Rol |
|---|---|---|---|
| Snackbar "Geri al" | mor | `#D4AF37` | `inversePrimary` |
| Snackbar zemini | nötr gri | `#1F3D2D` | `inverseSurface` |
| Seçilmemiş chip kenarlığı | mor-gri | `#5C7F6C` | `outline` |
| Seçilmemiş chip etiketi | mor-gri | `#35594A` | `onSurfaceVariant` |

**Kural duruyor:** yeni bir `colorScheme` rolü kullanmadan önce `Theme.kt`'de
tanımlı olup olmadığı kontrol edilir. Bugün hepsi tanımlı; ileride Material yeni
bir rol eklerse bu kontrol yine gerekir.

### Faz 14a kontrast tablosu

Her çift hesaplandı; normal metin 4,5:1, büyük metin ve grafik bileşeni 3:1.

| Çift | Açık | Koyu |
|---|---|---|
| `onSurface` / `surface` | 14,45 | 10,05 |
| `onBackground` / `background` | 10,76 | 15,08 |
| `primary` / `surface` | 8,02 | 5,66 |
| `primary` / `background` | 5,98 | 8,50 |
| `onPrimary` / `primary` | 8,02 | 6,87 |
| `onPrimaryContainer` / `primaryContainer` | 11,12 | 8,07 |
| `onSecondary` / `secondary` | 6,29 | 7,65 |
| `onSecondaryContainer` / `secondaryContainer` | 12,05 | 7,24 |
| `onTertiary` / `tertiary` | 5,97 | 8,23 |
| `onTertiaryContainer` / `tertiaryContainer` | 10,83 | 7,03 |
| `onSurfaceVariant` / `surface` | 7,84 | 7,24 |
| `onSurfaceVariant` / `background` | 5,84 | 10,87 |
| `onSurfaceVariant` / `surfaceVariant` | 6,31 | 5,68 |
| `error` / `surface` | 7,92 | 5,85 |
| `onError` / `error` | 7,92 | 8,33 |
| `onErrorContainer` / `errorContainer` | 10,35 | 8,06 |
| `inversePrimary` / `inverseSurface` | 5,66 | 6,77 |
| `inverseOnSurface` / `inverseSurface` | 10,05 | 15,08 |
| `outline` / `surface` *(kenarlık, 3:1)* | 4,46 | 4,46 |
| `outline` / `background` *(kenarlık, 3:1)* | **3,32** | 6,69 |
| `primary` / `primaryContainer` *(ikon, 3:1)* | 6,17 | 4,35 |
| Çubuk / iz *(grafik, 3:1)* | 4,40 | **3,65** |
| `onSurface` / `surfaceContainerHighest` | 11,29 | 7,88 |

En düşük gereken çift: **3,32:1** (kenarlık, 3:1 eşiğinin üstünde). Tek bir çift
bile eşiğin altında değil.

**Ayrışma oranları** (kontrast eşiği değil, okunabilirlik notu): kart/arka plan
açık temada **1,34:1**, koyu temada **1,50:1** — koyu temadaki 1,29:1 borcu
(ROADMAP Faz 14) böylece kapandı, cihazda `#1F3D2D` üstüne `#0D1A14` ölçülerek
doğrulandı.


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

**Faz 12a'da uygulandı.** `monthly_snapshots` tablosu sürüm 1'e eklendi,
migration yazılmadı, sürüm numarası 1 kaldı ve
`app/schemas/com.elinacn.subtrack.data.local.SubTrackDatabase/1.json` yeniden
üretilip commit'e dahil edildi. Değişen tek şey `identityHash` ve eklenen tablo
oldu; `subscriptions` tanımına dokunulmadı.

> Bu, projenin Faz 2'den beri **ilk şema değişikliği**. Kuralın bir sonraki
> sınavı yayın öncesi, Faz 16'da.

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

### Mimari karar: iki hue, ve rolleri şemalar arasında yer değiştiriyor

**Faz 14a'da palet değişti:** pastel mavi-camgöbeği → **koyu zümrüt + altın.**
Gerekçe ürün kararı: uygulama para takip ediyor, palet bunu söylesin. Faz 1c'den
beri yürürlükte olan "maviyi iki role böl" kuralı **silinmedi, genişletildi** —
artık ayrım yalnızca roller arasında değil, **iki şema arasında** da geçiyor.

**Kural ve ölçüsü.** Altın dolu bir yüzey olarak güzel, mürekkep olarak
kullanılamaz:

| Ölçüm | Oran | Sonuç |
|---|---|---|
| Altın `#C9A227`, beyaz üstünde | **2,42:1** | Grafik bileşeni için gereken 3:1'i bile geçmiyor |
| Zümrüt `#0B5C3F`, beyaz üstünde | **8,02:1** | Metin eşiğinin (4,5:1) çok üstünde |
| Altın `#D4AF37`, koyu kart `#1F3D2D` üstünde | **5,66:1** | Koyu şemada metin olabiliyor |

Buradan çıkan kural:

- **Açık şema:** zümrüt metin/ikon/grafik, altın **yalnızca dolu yüzey**
  (`tertiary`). Üstüne `onTertiary` = `#08301F` geliyor, 5,97:1.
- **Koyu şema:** altın metin/ikon/grafik (`primary`), **zümrüt ailesi yüzey**
  (`primaryContainer`, `surface`, `background`).

Aynı mantığın devamı: bir rengi hem zemin hem mürekkep yapma. Yeni renk
eklenirken de geçerli — metin olacak renk kontrast hesabı yapılmış olmalı, dolu
yüzey gerekiyorsa `*Container` rolleri kullanılmalı.

**Çıpa renkler** (değiştirilmez; türetilen bir renk kontrastı geçmiyorsa o renk
değişir, çıpa değil):

| | Açık | Koyu |
|---|---|---|
| `primary` | `#0B5C3F` zümrüt | `#D4AF37` altın |
| `primaryContainer` | `#CDE8DA` | `#14523A` |
| `background` | `#D3E2D8` | `#0D1A14` |
| `surface` | `#FFFFFF` | `#1F3D2D` |
| `error` | `#9B2226` | `#F2A0A0` |

Renkler `Color.kt`'de adlandırılmış sabitler; hiçbir dosyada hardcoded hex yok
(CLAUDE.md §4). Şemalar `by lazy` — bkz. *"renk şemaları `by lazy` ile kurulur"*.


**Sürükleme `Animatable` ile değil düz `mutableFloatStateOf` ile yapılır.**
`Animatable`, `snapTo` ve `animateTo` çağrılarını tek mutex ile koruyor;
sürükleme deltaları kuyruğa girdiğinde bekleyen bir `snapTo`, yerleşme
animasyonunu iptal edip `onDelete`'i düşürüyordu. Yerleşme için `animate()`
suspend fonksiyonu kullanılır — tek doğruluk kaynağı, yarış yok.

---

## 13. Navigation

- Tek Activity, tek `NavHost` (`ui/navigation/SubTrackNavHost.kt`).
- Hedefler: **ana ekran**, **ayarlar**, **kur ekranı**, **istatistik** (Faz 13a),
  **düzenleme** (Faz 15). İstatistiğe giriş ana ekranın üst çubuğından, ayarlar
  ikonunun **solundan** — ayarlar Faz 9'dan beri en sağda ve kullanıcının bildiği
  hedef yerinden oynatılmadı. Düzenlemeye giriş satırın kendisinden.
- Rotalar düz `String` sabiti (`ui/navigation/Destination.kt`).

### Type-safe rota — Faz 15'te bakıldı, yine kullanılmıyor

§13 bu soruyu "argüman alan hedef yok, yani kazancı yok" diye ertelemişti. Faz
15'te argüman alan bir hedef geldi ve soru yeniden soruldu. Cevap değişmedi:

1. **Derleyici plugin'i gerekiyor.** Type-safe rotalar `@Serializable` sınıflar
   demek, o da kotlinx.serialization plugin'i demek. Bu faz yeni bağımlılık
   eklemiyor ve proje AGP 9'da bir derleyici plugin'ine bir kez yenildi
   (`@Parcelize`, Faz 0).
2. **Argüman zaten sınırda tipli.** Hedef argümanı `NavType.LongType` olarak
   tanımlıyor; `SavedStateHandle`'dan `Long` olarak çıkıyor, `String` olarak
   değil.
3. **Tipsiz kalan tek adım rota metnini kurmak** ve o da tek bir fonksiyonda:
   `Destination.editSubscription(id)`. Kayıtlı desen (`EDIT_SUBSCRIPTION`)
   içinde yer tutucu taşıdığı için doğrudan navigasyona verilemez — yani
   fonksiyonu atlamanın yolu yok.

Serialization başka bir iş için build'e girerse bu yeniden değerlendirilir.

### Argümanlı hedef nasıl kuruluyor

```kotlin
const val EDIT_SUBSCRIPTION_ARG = "subscriptionId"
const val EDIT_SUBSCRIPTION = "edit_subscription/{$EDIT_SUBSCRIPTION_ARG}"
fun editSubscription(id: Long): String = "edit_subscription/$id"
```

Graf tarafında `navArgument(EDIT_SUBSCRIPTION_ARG) { type = NavType.LongType }`.
ViewModel argümanı `SavedStateHandle`'dan **nullable** okur: `!!` yasak (CLAUDE.md
§4), `checkNotNull` de yalnızca daha iyi sözcüklerle fırlatır. Argümanı olmayan
bir çağrı "bulunamayan abonelik" hâline düşer — ekranın zaten çizdiği bir durum.
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

## 16. Insets: Sistem Çubukları ve Klavye

**Durum: Faz 16a'da edge-to-edge'e geçildi.** Bölüm iki katman kaydeder:
edge-to-edge öncesi ölçüm (tarihsel, artık geçersiz) ve bugünkü düzen.

### Tarihsel kayıt — 9b-2'deki ölçüm ve neden geçersizleşti

Faz 9b-2 hotfix'inde kur ekranına geçici bir probe konup
`WindowInsets.ime.getBottom(density)` ve
`WindowInsets.navigationBars.getBottom(density)` okundu:

| Cihaz | Klavye | `WindowInsets.ime` | `WindowInsets.navigationBars` |
|---|---|---|---|
| API 29, 360dp | kapalı | 0 | 0 |
| API 29, 360dp | **açık** (`mInputShown=true`) | **0** | 0 |
| API 34, 411dp | kapalı | 0 | 0 |
| API 34, 411dp | **açık** (`mInputShown=true`) | **0** | 0 |

Klavye açıkken tuşa basılarak yeniden kompozisyon zorlandı; okunan değer bayat
değildi, gerçekten sıfırdı. O zamanki çıkarım — "uygulama
`setDecorFitsSystemWindows(false)` çağırmadığı için insets'i decor tüketiyor,
Compose'a hiç ulaşmıyor" — doğruydu ve `android:id/content`'in API 29'da
`[0,48][720,1280]` okunması bunu doğruluyordu.

**Bu tablo iki sebeple geçersizdir.**

1. **Faz 16a `enableEdgeToEdge()` çağırıyor.** Decor artık insets'i tüketmiyor;
   üç cihazda da Compose gerçek değerleri görüyor.
2. **`navigationBars = 0` okumasının ikinci bir sebebi vardı ve o sebep hâlâ
   duruyor.** 9b-2 kaydı "bu iki emülatörde de gezinme çubuğu var" diyordu;
   **`subtrack_narrow_api29` AVD'sinde gezinme çubuğu yok.** `dumpsys window
   displays` `app=720x1280` diyor, yani uygulama alanı ekranın tamamı, ve alt
   banttaki her piksel uygulamanın kendi arka planı (`#D3E2D8`). Bu AVD'de alt
   insets edge-to-edge'den sonra da sıfır — ölçüm hatası değil, cihazın
   gerçeği.

### Bugünkü ölçüm — üç cihaz, klavye açık ve kapalı

Değerler cihaz üstü koordinat ölçümünden geliyor (`uiautomator dump` + piksel
taraması); API 36 satırındaki insets sayıları Faz 16-0'ın geçici probe'uyla
alınmıştı.

| Cihaz | `statusBars.top` | `navigationBars.bottom` | `ime.bottom` kapalı | `ime.bottom` açık |
|---|---|---|---|---|
| API 29, 720x1280 @320 | 48 px | **0** (bu AVD'de çubuk yok) | 0 | **0** — API 30 altı raporlamıyor |
| API 34, 1080x2400 @420 | 128 px | 63 px | 0 | 883 px |
| API 36, 1080x2400 @420 | 128 px | 63 px | 0 | 883 px |

`android:id/content`, 16a öncesi ve sonrası:

| Cihaz | Önce | Sonra |
|---|---|---|
| API 29 | `[0,48][720,1280]` | `[0,0][720,1280]` |
| API 34 | `[0,128][1080,2337]` | `[0,0][1080,2400]` |
| API 36 | `[0,0][1080,2400]` | `[0,0][1080,2400]` — targetSdk 36 yüzünden zaten öyleydi |

**Çift uygulama yok.** Aynı cihazda 16a öncesi ve sonrası derleme sırayla
kurulup ölçüldü; `content` dışındaki **her koordinat aynı kaldı** — API 34'te
uygulama çubuğu başlığı `[43,175][276,249]`, ilk kart `[42,338][1038,696]`,
ilk liste satırı `[0,1157][1080,1458]`, en alt satır dinlenme konumunda alt
kenardan 273 px; API 29'da başlık `[32,84][212,140]`, kart
`[32,208][688,481]`, en alt satır alt kenardan 160 px. Decor'un uyguladığı pay
uygulamaya geçti, üstüne binmedi.

### Sistem çubuğu ikonları temayı takip eder, cihazı değil

Edge-to-edge, çubukların arkasına uygulamanın kendi arka planını koyar; sistem
altta ne olduğunu artık bilemez, söylenmesi gerekir. `MainActivity`
`enableEdgeToEdge`'i `SystemBarStyle.auto(...) { darkTheme }` ile yeniden
çağırır ve `darkTheme`, renk şemasının kurulduğu cevabın **aynısıdır**
(`Theme.kt`'deki `isDarkTheme`, tam da iki kopyanın ayrışmaması için ayrı
fonksiyon). Ayarlarda koyu tema zorlandığında cihazın gece ayarı ne derse desin
çubuklar da kararır.

Üst banttaki kontrast piksel sayısı (16-0'da açık temada **sıfırdı**):

| | API 29 (48 px bant) | API 34 (128 px bant) | API 36 (128 px bant) |
|---|---|---|---|
| Tercih açık, sistem açık | 2491 | 3354 | 4319 |
| **Tercih açık, sistem koyu** | 2503 | 3354 | 4301 |
| Tercih koyu | 2503 (beyaz ikon) | 3224 (beyaz ikon) | 4286 (beyaz ikon) |

**İki çubuk farklı stil alır, ve bu ölçümden çıktı.** API 29 altında androidx
çubuğu kendisine verilen scrim'le **doldurur**, sisteme bırakmaz. API 24'te
durum çubuğuna `background` rengi verildiğinde uygulama çubuğunun üstünde
görünür bir dikiş oluştu. Durum çubuğunun scrim'e ihtiyacı yok — ikonları API
23'ten beri kararabiliyor — bu yüzden şeffaf. Gezinme çubuğunun var: API 26
altında ikonları her zaman beyazdır ve açık bir çubuk onları yutar; `scrim`
rolü iki şemada da siyah, o sürümlerin kendi çubuğunun rengi. API 26-28 arası
ikonlar temayı takip edebildiği için `background` çubuğu uygulamanın devamı
yapar. API 29'dan itibaren ikisi de şeffaf, kontrastı sistem zorlar.

**Açılıştaki ilk kare.** `onCreate`'teki ilk `enableEdgeToEdge` çağrısı durum
çubuğunu `SystemBarStyle.auto` ile kurar (16h'ye kadar `light`, 16h ile `dark`,
16h-2'den beri `auto`). Bu değer **yukarıdaki tabloyu kurmuyor**: uygulamanın kararlı
karelerindeki ikon rengini `SystemBarsFollowTheTheme` belirliyor, ve splash
karelerindeki ikon rengini uygulamanın penceresi değil sistemin başlatma
penceresi belirliyor. İki pencerenin nasıl ayrıştığı, hangi bayrağın neyi
söylediği ve dört ölçüm **§23'te** — 16h'nin bu konudaki iki sayısının
düzeltmesiyle birlikte.

### Ekranların insets'i nasıl aldığı

`Scaffold` çubuk paylarını `PaddingValues` olarak verir ama **tüketmez**
(material3 1.4.0; `Scaffold.kt` KDoc'u `Modifier.padding` + `consumeWindowInsets`
öneriyor). `TopAppBar` üst payı kendi alır, `Scaffold` da FAB ile Snackbar'ı
alt payla birlikte yukarı taşır — ölçüldü, API 36'da FAB kutusu
`[891,2148][1038,2295]`, alt kenara 105 px = 63 (gezinme) + 42 (16dp
`FabSpacing`); Snackbar'ın alt kenarı 2148, yani 252 px = 147 (FAB) + 42 + 63.
Geri kalan karar ekran başına verildi:

- **Ana ekran** payları `contentPadding` olarak alır. `Modifier.padding` kaydırma
  görünümünü kısaltır: liste jest çubuğunun üstünde biter, altında hiçbir şey
  kaydırmayan ölü bir şerit kalır ve alt kenarı geçen satır çubuğun altına
  kayacağına havada kesilir. `contentPadding` görünümü tam bırakır, yalnızca
  içeriği iter. Ölçüldü (API 36 ve 34): kaydırırken satırlar çubuğun altına
  giriyor, en alttaki satır `[0,1868][1080,2127]`'de duruyor — çubuğun üst
  kenarından (2337) **210 px** yukarıda.
- **Ayarlar, kur, düzenleme, istatistik** paylarını kaydırmanın **dışında**
  tutar. Hepsi kullanılacak ya da okunacak bir şeyle biter; jest çubuğunun
  altına kayan bir düğme yarı dokunulabilirdir.
- **Kur ve düzenleme** ayrıca `consumeWindowInsets(paddingValues)` +
  `imePadding()` alır. `consumeWindowInsets` olmadan `imePadding` klavyeyi
  pencere kenarından ölçer ve zaten uygulanmış alt payı ikinci kez ekler.

### `ListBottomSpacing` neyi garanti eder, neyi etmez

`Dimens.ListBottomSpacing` (80dp) ana ekranın `contentPadding`'ine
`Scaffold`'un alt payının **üstüne** eklenir. Garantisi tek ve dar:
**liste sonuna kadar kaydırıldığında son satır FAB'ın altında kalmaz.**
FAB'ın istediği pay 16dp (`FabSpacing`) + 56dp (kap) = 72dp; 80dp onu 8dp ile
geçer. Faz 16e'de üç cihazda ve iki yazı boyutunda ölçüldü, altısında da aynı:
son satırın alt kenarı ile FAB'ın üst kenarı arasında **8dp** var (API 29'da
16 px, API 34 ve 36'da 21 px).

**Kaydırmanın ortasında bir satırın örtülmesi bu garantinin dışındadır ve
kabul edilmiştir.** FAB sabit durur, liste akar; her satır zorunlu olarak
FAB'ın bandından geçer. `contentPadding` yalnızca içeriğin **uçlarda** nerede
durduğunu belirler, bu yüzden alt boşluğu büyütmek ortadaki örtüşmeyi
değiştirmez — yalnızca listenin sonuna ölü alan ekler. 16e'de ölçülen:
API 36'da 7 abonelikle kaydırma yolunun yaklaşık yarısında bir satırın tutarı
FAB'ın arkasında kalıyor, en kötü konumda tutarın **%28'i** çiziliyor.

Kabul edilmesinin gerekçesi: liste sonu zaten temiz, satır TalkBack'e tek
parça olarak tutarıyla birlikte okunuyor (`SwipeToDeleteRow`'un
`clearAndSetSemantics`'i), ve tutarı görmek için bir parmak ucu kaydırma
yetiyor. Bunu tümden kaldırmanın yolu FAB'ı kaydırırken gizlemekten geçer;
o karar alınmadı, çünkü giriş noktasının kaydırma sırasında yok olması
örtülen bir satırdan daha pahalı görüldü.

### Klavye: iki mekanizma, hiç çakışmadan

`AndroidManifest.xml`'deki `windowSoftInputMode="adjustResize"` **duruyor**, ama
artık 9b-2'deki geçici çözüm değil, işin API 30 altı yarısı.

- **API 30 ve üstü:** `setDecorFitsSystemWindows(false)` platformun
  `SOFT_INPUT_ADJUST_RESIZE`'ı yok saymasına yol açar; `WindowInsets.ime` gerçek
  değer verir ve iş `Modifier.imePadding()`'e düşer.
- **API 30 altı:** bayrak hâlâ geçerli ve tek çalışan şey o. `WindowInsets.ime`
  o sürümlerde pencere küçülmediği sürece raporlanmaz.

Bayrağın kaldırılması denendi ve **API 29'u kırdı**: kur ekranında Kaydet
y=1044, Varsayılana dön y=1164, klavye y=784'ten başlıyor, ve fiske hiçbir şeyi
oynatmıyor çünkü kaydırma görünümü hâlâ 1280 yüksekliğinde. Geri konduktan
sonra tek fiskede Kaydet y=564, Varsayılana dön y=684. API 34 ve 36 pikseli
pikseline aynı kaldı.

### Klavye açıkken erişilebilirlik — üç ekran, üç cihaz

| Ekran | Cihaz | Klavye kapalı | Klavye açık, tek fiske sonrası | Klavye üst kenarı |
|---|---|---|---|---|
| Kur | API 29 | `[329,1044][391,1084]` | `[329,564][391,604]` | 784 |
| Kur | API 34 | `[500,1439][580,1492]` | `[500,1228][580,1281]` | 1517 |
| Kur | API 36 | `[500,1439][580,1492]` | `[500,1228][580,1281]` | 1517 |
| Düzenleme | API 29 | kaydırma gerekiyor (360dp) | `[329,630][391,670]` | 784 |
| Düzenleme | API 34 | `[500,1807][580,1860]` | `[500,1323][580,1376]` | 1517 |
| Düzenleme | API 36 | `[500,1807][580,1860]` | `[500,1323][580,1376]` | 1517 |
| Ekleme sheet'i | API 29 | `[329,1132][391,1172]` | `[329,630][391,670]` (fiskesiz) | 784 |
| Ekleme sheet'i | API 34 | `[500,2143][580,2196]` | `[500,1323][580,1376]` (fiskesiz) | 1517 |
| Ekleme sheet'i | API 36 | `[500,2143][580,2196]` | `[500,1323][580,1376]` (fiskesiz) | 1517 |

### Sheet'lerin neden etkilenmediği

`ModalBottomSheet` içeriğini `Box(Modifier.fillMaxSize().imePadding())` içine
koyar (material3 1.4.0, `ModalBottomSheet.kt:186`). Sheet kendi penceresinde
(dialog) çizilir ve o pencere insets alır; bu yüzden ekleme sheet'i klavyeyle
her zaman doğru davrandı. **Bu bir tesadüftür, uygulamanın bir kararı
değil** — aynı kütüphane kolaylığı normal ekranlarda yoktur, ve 16a'nın bütün
işi tam olarak o eksiği kapatmaktı.

Sheet 16a'da **dokunulmadı** ve ölçümle de değişmediği gösterildi: aynı
cihazda 16a öncesi ve sonrası derlemelerde API 29 koordinatları
`[329,1132][391,1172]` (kapalı) ve `[329,630][391,670]` (açık) — birebir aynı.

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
- **Tarih ilerletme: okuma anında hesaplanır, veritabanına yazılmaz**
  (Faz 12-2). Saklanan tarih **çıpadır** — kullanıcının verdiği gün, olduğu
  yerde kalır. Kartın saydığı şey `NextPaymentDate.onOrAfter(bugün, çıpa,
  periyot)`: çıpanın, bugüne ulaşana kadar tam periyotlarla ilerletilmiş hâli.

  **Neden yazılmıyor:** ilerletilmiş tarih aboneliğin değil, *abonelik + bugün*
  ikilisinin özelliği — geri sayımın parametre olarak `today` almasıyla aynı
  gerekçe (yukarıda). Yazmak, kullanıcının girdiği veriyi arka planda
  değiştirmek olurdu ve tek yönlüdür: çıpa kaybolunca "aslında hangi gün
  demiştim" sorusunun cevabı da kaybolur. Okuma anında hesaplamak ise saf bir
  fonksiyon: girdisi belli, testi kolay, geri alınacak bir şey yok.

  **Çıpadan sayılır, son adımdan değil.** 31 Ocak'tan ayda bir ilerlerken
  28 Şubat'a, oradan 28 Mart'a gidilir ve abonelik sessizce 28'ine taşınır.
  Kaç tam periyot geçtiği sorulup çıpaya bir kerede eklendiğinde Mart yine
  31'ini alır. Aynı yaklaşım döngüyü de ortadan kaldırıyor: on yıl öncesine
  ait haftalık bir çıpa 558 adım değil, bir çıkarma ve bir toplama.

  **Sonucu:** "gecikmiş" durumu **hiçbir yerde oluşmuyor** — sayılan tarih
  hiçbir zaman geçmişte değil. Bugünden geriye kalan tek durum, bugün ödenmesi
  gereken ve henüz ödenmemiş olan. 12-2 hotfix'inde bildirim de aynı
  ilerletilmiş tarihe bağlandığı için `PaymentCountdown.Overdue` ulaşılamaz hâle
  geldi ve **kaldırıldı** (§18). `PaymentCountdown.between` artık geçmiş bir
  tarihi reddediyor: çıpayı doğrudan veren bir çağıran bu adımı atlamıştır.

  Düzenleme ekranı (Faz 15) **çıpayı** gösterecek, ilerletilmiş tarihi değil.
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

Bildirilenler: **bugün ödenecek** ve **1 gün kalan**. Tarihi olmayan abonelik
bildirilmez. Abonelik başına ayrı bildirim yok — tek özet bildirim, sabit id,
günde en fazla bir tane. Bildirimde tutar yazmaz.

Ölçülen şey **çıpa değil, çıpanın bugüne ulaşmış hâli**: seçim de kartla aynı
`NextPaymentDate.onOrAfter(bugün, çıpa, periyot)` sonucuna bakar (§17). Aynı
satır için ekranla bildirimin farklı şey söylemesi böylece yapısal olarak
imkânsız.

Başlık kapsanan abonelik **sayısını** söyler ve fiil kullanmaz. Aynı bildirim
hem bugün ödenecek hem yarın ödenecek bir aboneliği taşıyabildiği için
"yenileniyor" gibi tek duruma bağlanan bir başlık zamanın yarısında yanlış
olurdu.

### Gecikme penceresi neden vardı, neden kalktı

Pencere (1-3 gün) 10b'de, §17'deki "tarih ilerletilmez" kuralı için konmuştu:
gecikmiş durum kalıcı olduğu için, pencere olmasa bildirim de kalıcı olurdu ve
kullanıcı her gün aynı satırı görürdü.

**12-2 ilerletmeyi getirince gerekçe düştü.** Ölçüm şunu gösterdi: bildirim
çıpayı, ekran ilerletilmiş tarihi okuyordu, ve aynı aylık abonelik için
bildirim "1 gün gecikti" derken kart "29 gün kaldı" diyordu. İki doğru yoktu;
biri yanlıştı ve yanlış olan çıpayı okuyan taraftı — kullanıcının ödemesi
gereken bir sonraki gün, geçmiş bir tarih değil.

`OVERDUE_WITHIN_DAYS` kaldırıldı, `UPCOMING_WITHIN_DAYS = 1` kaldı; tek eşik
hâlâ `PaymentReminderSelection` içinde adlandırılmış sabit, koda gömülü sayı
değil.

**KABUL EDİLEN BEDEL — bir günü kaçıran döngü kaybolur.** Pencere, işi bir gün
geç koşan bir worker için ikinci bir şans işlevi de görüyordu. Artık yok:
ödeme günü geçtiği anda tarih bir sonraki periyoda atlar, dolayısıyla o
döngünün bildirimi bir daha üretilemez. Doze altında kayan, cihazın kapalı
olduğu ya da `KEEP` yüzünden atlanan bir gün, o ödeme için sessiz kalır.
Bilerek kabul edildi: bugünkü kural iki günlük bir pencereye (bugün + yarın)
bakıyor, yani bir günün kaçması ödemeyi tamamen kaçırmak değil — yarınki
bildirim, bugün kaçırılan "yarın ödenecek" satırını "bugün ödenecek" olarak
yakalar. Kaçan tek durum, o iki günün **ikisinin de** kaçırılması.

İleride bu bedel ödenmek istenmezse doğru çözüm **"gecikmiş durumu" değil**:
gecikmiş durumu ilerletmeyle birlikte anlamsız (tarih hiçbir zaman geçmişte
değil). Doğru soru "son N günde bir ödeme günü geçti mi" — yani çıpanın bir
**önceki** periyodunun bugüne uzaklığı. `NextPaymentDate` bunu zaten bir
çıkarmayla verebilir (`sonuç - 1 periyot`), yeni alan veya yazma gerektirmez.

### `PaymentCountdown.Overdue` kaldırıldı (12-2 hotfix, Görev 3)

Kanıt: `PaymentCountdown.between`'in üretimde **iki** çağıranı var —
`HomeViewModel` ve `PaymentReminderSelection` — ve hotfix'ten sonra ikisi de
ona `NextPaymentDate.onOrAfter` sonucunu veriyor. O sonuç hiçbir zaman bugünden
önce değil (`NextPaymentDateTest.theAnswer_isAlwaysTheFirstDueDateNotBeforeToday`
ile sabitli), dolayısıyla `days < 0` dalı ulaşılamaz hâle geldi.

Kaldırılanlar: `PaymentCountdown.Overdue` tipi ve `between`'in negatif dalı ·
bildirimdeki `days_overdue` dalı · kartın `error` renkli geri sayım dalı ve
`asText`/`asString` karşılıkları · `SubscriptionCardOverduePreview` ·
`days_overdue` çoğulu (`values` ve `values-en`) · `OVERDUE_WITHIN_DAYS` ·
"gecikmiş"ten söz eden kanal açıklaması (iki dilde de yenilendi).

`between` artık geçmiş bir tarihi **reddediyor** (`require`), uydurma bir
cevap üretmiyor: çıpayı doğrudan veren bir çağıran `NextPaymentDate`'i
atlamıştır ve bu, para hakkında yanlış bir sayı göstermektense hatanın
kendisinde patlaması gereken bir sözleşme ihlalidir.

`MaterialTheme.colorScheme.error` uygulamada **hâlâ kullanılıyor**
(`SwipeToDeleteRow`), yani Faz 14'teki renk borcu aynen duruyor — yalnızca
kartlardaki kullanımı düştü.

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

### Hangi ayar ekranına gidildiği sürüme bağlıdır

`ACTION_APP_NOTIFICATION_SETTINGS` **API 26'da geldi.** API 26 ve üstünde
uygulamanın kendi bildirim ekranı açılır; altında doğrudan
`ACTION_APPLICATION_DETAILS_SETTINGS` — uygulamanın Ayarlar'daki sayfası —
açılır. Dal `Build.VERSION.SDK_INT` ile açıkça yazılmıştır; bunun bir Compat
karşılığı yok.

**Neden exception yedeği bunu yakalayamıyor — 16b'de ölçüldü.** Önceki hâl
"eylemi dene, `ActivityNotFoundException` gelirse detay sayfasına düş" idi ve
Android 7.0'da **hiç çalışmadı**: o sürümün Ayarlar'ı eylemi karşılıyor, yani
`startActivity` başarılı oluyor ve exception atılmıyor. Ekran açılıyor, kendi
istediği `app_uid` ekstrasını bulamayıp kapanıyor, kullanıcıya satır hiçbir şey
yapmamış gibi görünüyor:

```
W NotifiSettingsBase: Missing extras: app_package was com.elinacn.subtrack, app_uid was -1
```

Bir eylemin **adının** var olması, o sürümde **çalışacağı** anlamına gelmiyor;
exception yalnızca eylemi hiç kimsenin karşılamadığı durumu yakalar. Aradaki
farkı ancak sürüm kontrolü kapatır.

**`app_uid` gönderilmedi.** Desteklenmeyen bir ekrana ikinci bir ekstra ile
girmek belgelenmemiş davranışa bağlanmaktır; o ekran API 26 öncesinde
belgelenmiş bir giriş noktası değil.

**Satır devre dışı bırakılmadı.** Kullanıcının bildirimleri açmak için tek yolu
o. Detay sayfası bildirim ayarlarına bir dokunuş uzak ("Notifications" satırı) —
bedel bu, ve ölü bir satırdan iyi.

Exception yedeği **duruyor**: API 26+ dalında, kendi bildirim ekranını
taşımayan bir yapıda yine detay sayfasına düşülür. İki dal da aynı yerde
buluşuyor. Bu **açık** bir fallback'tir, kodda yakalanıp gerekçesiyle
yazılmıştır — §9'un yasakladığı sessiz `try/catch` değil.

Ölçüm (16b hotfix, dört cihaz, `mResumedActivity`):

| Cihaz | Açılan ekran |
|---|---|
| API 24 | `com.android.settings/.applications.InstalledAppDetails` |
| API 29 | `com.android.settings/.Settings$AppNotificationSettingsActivity` |
| API 34 | `com.android.settings/.Settings$AppNotificationSettingsActivity` |
| API 36 | `com.android.settings/.Settings$AppNotificationSettingsActivity` |

Lint'in `InlinedApi` uyarıları (`ACTION_APP_NOTIFICATION_SETTINGS` ve
`EXTRA_APP_PACKAGE`) bu sürüm dalıyla **kapandı**: 24 uyarı 22'ye indi,
`@SuppressLint` kullanılmadı.

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

## 19. Geçmiş Takibi — Aylık Anlık Görüntüler

Aylık toplamın zaman içindeki kaydı. `PROJECT_SPEC.md` §1'deki "geçen aya göre
ne değişti?" vaadinin veri tarafı; Faz 13'teki trend grafiği bu tabloyu okuyacak.

### Yazma zamanı: her değişiklikte üzerine yazma

Üç seçenek vardı:

| seçenek | neden seçilmedi |
|---|---|
| **Uygulama açılışında** | Kullanıcı bir ay uygulamayı hiç açmazsa o ay için satır oluşmaz. Grafikte delik kalır ve delik "harcama yoktu" diye okunur — yanlış bir cevap, hem de sessizce. |
| **Ay dönümünde** | Zamanlayıcı gerekir. WorkManager dakika garantisi vermez (§18), Doze altında kayar; kaçan bir dönüm o ayı tamamen kaybettirir. |
| **Her değişiklikte** ✅ | Yazma, toplamı değiştiren eylemin kendisine bağlı. Kullanıcı uygulamayı açmadıysa toplam da değişmemiştir; kaydedilecek yeni bir şey yoktur. |

**Kural:** toplamı etkileyen bir şey değiştiğinde, **içinde bulunulan ay için
tek satır upsert** edilir. Abonelik eklemek, silmek, geri almak; ana para
birimini veya bir kuru değiştirmek — hepsi aynı yola çıkar.

**KABUL EDİLEN BEDEL:** ay içinde ekleyip silen bir kullanıcıda o ayın kaydı
**son duruma** göre kalır, ortalamaya değil. Bilerek: "geçen aya göre ne
değişti" sorusunun cevabı ay sonundaki durumdur, ay boyunca dolaşılan yol değil.

İkinci, küçük bir bedel: ay dönümü uygulama açıkken geçerse yeni ayın satırı o
ayki **ilk değişiklikte** açılır, gece yarısında değil. Kayıt yine doğru aya
yazılır — yalnızca biraz geç.

### Nerede: `domain/usecase/MonthlySnapshotRecorder`, ViewModel'de değil

Toplam bir ekranın değil, **saklanan verinin** özelliği. Değişiklik listeden de
gelebilir, ayarlardaki para birimi seçicisinden de, kur ekranından da; üçünde de
kaydedilmeli. Bir ViewModel yalnızca kendi ekranının gördüğünü kaydederdi.

**Filtre tuzağı — asıl sebep.** Ana ekranın toplamı kategori filtresini izler
(§4). `HomeUiState.total` filtre açıkken **filtrelenmiş** figürdür. Oradan
kaydetmek, filtre açıkken yanlış sayıyı yazmak demekti. Recorder repository'yi
doğrudan okuduğu için filtre **görünmüyor bile**: hata kaçınılan değil,
**ulaşılamaz** bir hâle geldi. Cihazda ölçüldü — ekran `TRY 25,00` derken
tabloya `TRY 125,00` yazıldı.

Kaydedilen figür her zaman **aylık**tır. Yıllık görünüm bir gösterim tercihidir
(§6), veri değil; iki span saklamak ikisinin çelişmesine izin vermek olurdu.

Bugünün ayı enjekte edilmiş `Clock`'tan (`YearMonth.now(clock)`) okunur, asla
`LocalDate.now()` ile değil (§17).

### Neden sonsuz döngü olmuyor

Girdiler `subscriptions` tablosu ve DataStore tercihleri; çıktı **başka bir
tablo**. Room, yalnızca yazılan tabloyu okuyan sorguları yeniden çalıştırır ve
burada `monthly_snapshots`'ı gözleyen hiçbir akış yok —
`MonthlySnapshotRepository.getByPeriod` tek seferlik bir okuma, Flow değil.
Yani yazılan anlık görüntü yeni bir emisyon üretemez. **Yapısal garanti.**

İkinci savunma hattı: değer değişmediyse yazılmaz. Bir yeniden adlandırma
listeyi yeniden yayınlar ama toplamı oynatmaz; o durumda satır elden geçmez ve
`recordedAt` da yerinde kalır. İleride tabloyu gözleyen bir akış eklense bile
döngü tek turda dururdu.

Recorder süreç ömrü boyunca yaşayan bir gözlemci; `@ApplicationScope`
`CoroutineScope`'u `di/CoroutineModule` sağlıyor ve `SubTrackApplication`
başlatıyor — hatırlatma zamanlayıcısıyla aynı yerde.

Yazma hatası **bilerek yutulur**: söyleyecek bir ekran yok (kullanıcının yaptığı
işin arkasında koşuyor) ve bir ayın defter kaydını kaybetmek, uygulamayı
düşürmekten çok daha küçük bir zarar. `CancellationException` yutulmaz.

### Boş liste: sıfır yazılır, atlanmaz

Faz 13'ün **"o ay hiç abonelik yoktu"** ile **"o ay kayıt yok"** ayrımını
yapabilmesi gerek. Bu ancak yokluğun gerçekten yokluk anlamına gelmesiyle
mümkün: liste boşsa o ay `0` olarak kaydedilir. Temiz kurulumda uygulamanın ilk
açılışı bu yüzden `0`'lık bir satır bırakır — "baktım, sıfırdı" demek.

### Dönem gösterimi: tek `Int`, `yıl * 100 + ay`

Eylül 2026 → `202609`.

- **Sıralanabilir:** ay her zaman iki basamak olduğu için sayısal sıra takvim
  sırasıdır; `ORDER BY period` tek sütunla yeter.
- **Tekil:** bir değer tek bir takvim ayı demek, dolayısıyla doğal birincil
  anahtar. Upsert var olan satıra iner, aynı ay için ikinci satır açılamaz.
- **Zaman dilimi yok:** ay bir takvim olgusudur, bir an değil. Epoch millis
  okunurken zaman dilimi ister ve sınırdakiler için yanlış ayı verir.
- **İki sütun değil:** bileşik anahtar ve iki sütunlu `ORDER BY` gerekirdi,
  karşılığında hiçbir şey kazandırmadan.
- `run-as` ile alınan bir dökümde **okunabilir** kalır; bu tablonun cihazda
  doğrulanma biçimi tam olarak budur.

Domain tarafında `java.time.YearMonth` — gün yok, çünkü gün diye bir bilgi yok.
Dönemi çözemeyen bir satır (elle düzenlenmiş, ya da yeni bir sürümün yazdığı)
**atlanır**: uydurma bir ay grafiğe sahte bir nokta koymaktan iyidir.

### Para birimi neden saklanıyor

Kullanıcı ana para birimini değiştirebilir. Geçen ayın satırı, figürünün hangi
birimde olduğunu **kendisi** taşımalı; yoksa sonraki okuyucu tahmin eder ve
bugünkü tercihle tahmin eder. Faz 13 bu sütunu okuyacak.

İçinde bulunulan ayın satırı, para birimi değişince yeni birimiyle yeniden
yazılır (kural gereği: toplamı etkileyen bir değişiklik). Geçmiş ayların
satırlarına dokunulmaz.

### `@Upsert`, `OnConflictStrategy.REPLACE` değil

`REPLACE`, SQLite'ta çakışmayı **satırı silip yenisini eklemek** suretiyle
çözer: silme tetikleyicilerini ateşler ve bağlı satırları da götürür. `@Upsert`
ekler, anahtar çakışırsa yerinde günceller — satır kimliğini korur. "Aynı ay,
revize edildi" zaten budur.

---

## 20. İstatistik Ekranı ve Grafik Çizimi

Faz 13a: kategori dağılımı ve en pahalı abonelikler. Faz 13b: aylık trend ve
"geçen aya göre" karşılaştırması (§21). Ekran snapshot tablosunu **yalnızca
okur**; yazma tarafı 12a'da kapandı (§19).

### Grafik kütüphanesi yok — Compose Canvas

`PROJECT_SPEC.md` §5 üçüncü parti SDK'ları kapsam dışı bırakıyor ve bir çubuk
listesi bir kütüphaneyi hak etmiyor: çizilen şey dolu bir dikdörtgen ile onun
altındaki iz. `Canvas` zaten Compose'un içinde, APK'ya bir şey eklemiyor ve
Faz 16'daki küçültme çalışmasına yeni bir bağımlılık taşımıyor.

### Pasta değil, yatay çubuk

360dp'de elle çizilmiş bir pastanın dört etiketi üst üste biner ve dilimlerin
oranı ancak açıyla okunur. Çubuk listesi hem daha okunur hem de **metne
çevrilebilir**: bir çubuğun uzunluğu bir yüzdedir ve yüzde yazılabilir. Ekran
okuyucu için bu belirleyici oldu (aşağıya bakın).

### Kategori başına renk YOK

Paletimizde dört ayırt edilebilir **tanımlı** rol yoktu; 13a yazılırken `outline`
ve `onSurfaceVariant` da tanımsızdı ve Material baseline'ına düşüyordu.
Dört grafik rengi icat etmek, Faz 14'ün palet çalışmasında geri alınacak bir borç
olurdu. Ayrımı **etiket** taşıyor, çubuk yalnızca büyüklüğü.

> **Faz 14a'da yeniden bakıldı, karar değişmedi.** Roller artık tanımlı, ama
> kategori başına renk hâlâ yok: paletin kuralı tek bir hue'nun mürekkep
> olabileceğini söylüyor (§12), dört kategori için dört eşit okunaklı renk
> üretmek o kuralı bozar. Ayrım etikette kalıyor.

**İz (track) rengi ölçümle seçildi.** İlk deneme `primaryContainer`'dı ve yanlış
çıktı: **koyu şemada `primary` ve `primaryContainer` ikisi de PastelBlue**, yani
çubuk ile izi aynı renk oldu ve her kategori dolu göründü. API 34'te ekran
görüntüsüyle görüldü, akıl yürütmeyle değil. İz o gün çubuğun kendi renginin
saydamlaştırılmış hâli oldu (`primary.copy(alpha = 0.24f)`).

**Faz 14a'da düzeltildi: iz artık saydamlık değil, tanımlı bir rol
(`outlineVariant`).** Saydamlığın gerekçesi çakışmaydı, çakışmayı doğuran da
koyu şemadaki aynı-renk sorunuydu; yeni palette `primary` altın,
`primaryContainer` zümrüt, sorun kalmadı. Ölçülen: çubuk↔iz açık temada
**4,40:1**, koyu temada **3,65:1** — ikisi de grafik bileşeni eşiği 3:1'in
üstünde, eski solgun izin koyu temada veremediği bir şey. Üstünde metin olmadığı
için bu, dashboard kartının reddettiği kontrast takası değil.

**Kapanmayan kısım: iz ↔ arka plan** (açık 1,36:1, koyu 2,33:1). Bir izin hem
çubuktan hem arka plandan 3:1 ayrışması için çubuğun arka plana karşı 9:1'e
çıkması gerekir; paletin zümrütü beyazda 8,02:1 ve bu çıpa değişmiyor. "Sıfır
kaydedildi" ile "kayıt yok" ayrımını bu yüzden tümüyle cümle taşıyor (§21).

### Oran gösterimi: hem tutar hem yüzde

Tutar "ne kadar" sorusunun, yüzde "bunun ne kadarı" sorusunun cevabı. Çubuk
ikincisini çiziyor, birincisini hiçbir şey çizmiyor. Yalnız yüzde bırakmak
okuyucuyu bilinmeyen bir bütünün oranlarıyla baş başa bırakırdı; yalnız tutar
bırakmak çubuğu kendi değerinin yazılı olmadığı tek yer hâline getirirdi.

### Yüzdeler toplamı her zaman 100

Her payı tek başına yuvarlamak üç eşit üçte biri 33 + 33 + 33 = 99 yapıyor ve
parçaları bütünü etmeyen bir dağılım güvenilmez okunur. **En büyük kalan
yöntemi** kullanılıyor: her pay aşağı yuvarlanır, artan puanlar en çok kırpılana
verilir. Tamsayı aritmetiği; `Double` paranın yanına girmiyor (§6). Toplam
sıfırsa hiçbir bölme yapılmaz, hepsi sıfırdır.

### Sıfır tutarlı kategori satırı yok

Dört kategori sabit bir **sözlük**, sabit bir cevap listesi değil. "Sağlık,
0,00, %0" satırı hiçliğin çubuğunu çizer ve ekran okuyucuya kullanıcının sağlık
aboneliği olmadığını söyleyen fazladan bir durak verir — ekranın sorusu bu
değil. Aynı gerekçe Faz 11a'da `OTHER`'ı kartlardan uzak tutmuştu.

### Erişilebilirlik: Canvas görünmez, satır konuşur

`Canvas` erişilebilirlik ağacında **yok**; çizdiği oran hiçbir ekran okuyucuya
ulaşmaz. Bu yüzden her satır `clearAndSetSemantics` ile **tek bir odak durağı**
ve tek bir cümle: "Sağlık, 2.002,00 TL, yüzde 75". Ölçüldü — satır alt ağacında
**tek düğüm** var (iki emülatörde de).

`mergeDescendants` yetmezdi: birleştirme çocukları ağaçta bırakır ve delege
birleştirilmemiş ağacı gezer — 8a'da dashboard kartı tam olarak buna takılmıştı.

### En pahalı: beş satır, aylık maliyete göre

Beş, çünkü liste "büyükler hangileri" sorusunun cevabı; listenin tamamının
değil. 360dp'de dağılımın altına sığıyor ve bölüm ana ekranın ikinci bir
kopyasına dönüşmüyor.

Sıralama **aylık maliyete** göre, karttaki fiyata göre değil: yıllık 1.200,00
ile aylık 100,00 aynı şeye mal olur ve listede yan yana durmalıdır. Bu yüzden
her satırda **periyot yazıyor** — yoksa kartta 1.200,00 gösteren abonelik burada
100,00 görünür ve iki sayı çelişki gibi okunur.

Bu, uygulamada **satır başına çevrilmiş tek figür**. Birbirleriyle toplanmadığı
için satır başına yuvarlama, kendisiyle çelişen bir toplam üretemez (§6).

### Filtre tuzağı — üçüncü kez

Ana ekranın toplamı kategori filtresini izler. İstatistik ekranı **her zaman
tüm abonelikleri** gösterir ve bunu filtreyi hiç görmeyen bir kaynaktan okuyarak
yapar: `StatisticsViewModel` repository'ye bakar, `HomeUiState`'e değil. 12a'daki
snapshot kaydedicisiyle aynı çözüm, aynı gerekçe. Cihazda ölçüldü: filtreliyken
ana ekran `TRY 2.002,00` derken istatistik ekranı dört kategoriyi ve
`TRY 2.681,50`'yi gösteriyordu.

### Bu ekranın `Event` tipi yok

§5 her ekran için tek `UiState` ve tek `onEvent` istiyor; gerekçesi bir
composable'a altı ayrı lambda geçirmemek. Bu ekranda **yapılacak hiçbir şey
yok** — okur, eylemez. Boş bir `sealed interface` ve dalsız bir `onEvent`
mimari değil merasim olurdu. Geri gitme, diğer ekranlardaki gibi kendi
lambda'sıyla geliyor.

### Bilinen sınır: fs 2.0'da etiket kelime ortasından bölünüyor

Yazı ölçeği 2.0'da kategori etiketi kendi payına sığmadığı için kelime
ortasından kırılıyor ("Productivit / y"). Kırpılma veya üst üste binme **yok**,
iki emülatörde de ölçüldü. İlk hâlinde `SpaceBetween` yerleştirmeye boşluk
bırakmıyordu ve etiket tutara yapışıyordu ("ProductivityTRY 428,50") — etiket
artık kalan genişliği alıp kendi içinde sarıyor ve araya bir boşluk konuyor.
Etikete daha fazla pay vermek tutarı sıkıştırırdı; büyük ölçeklerde satırı alt
alta yığmak bir kırılma noktası ister ve bu fazın kapsamını aşar.

---

## 21. Aylık Trend ve "Geçen Aya Göre"

Faz 13b. `monthly_snapshots` tablosunun **okuma** tarafı: `MonthlyTrend`
(domain, saf fonksiyonlar) satırları bir seriye çeviriyor, `MonthlyTrendChart`
onu çiziyor, `MonthlyChangeRow` iki ayı karşılaştırıyor. Bu faz tabloya
yazmıyor — yazma 12a'da kapandı (§19).

### Karışık para birimi: çevrilmiyor, dışarıda bırakılıyor ve söyleniyor

Bir satır **yazıldığı andaki** para biriminde saklanıyor (§19). Kullanıcı ana
para birimini değiştirirse tabloda iki birim birden olur. Üç yol vardı:

| yol | neden seçilmedi |
|---|---|
| **Bugünkü kurla çevirmek** | Geçmişi hiç doğru olmamış bir sayıyla yeniden yazmak olurdu. Kurlar elle giriliyor ve düzenlenebiliyor (§15); her kur düzenlemesi geçmiş ayları sessizce yeniden çizerdi. Tarihsel kur saklamıyoruz, dolayısıyla doğrusu zaten elimizde yok. |
| **Hepsini aynı eksene koymak** | Elmayla armut toplamak. 5.000 TL'lik ay ile 120 USD'lik ay yan yana çizilince kullanıcı yalnızca bir **ayar** değiştirmişken grafik uçurum gösterir. |
| **Ana para birimindekileri çizmek** ✅ | Eksen tek bir şey demek. Uydurulan sayı yok. Kendi kendini iyileştiriyor: altı ay sonra eski birimdeki satırlar pencereden zaten çıkmış olur. |

**Kullanıcıya nasıl görünüyor:** grafik yalnızca bugünkü ana para birimindeki
ayları çiziyor, geri kalanlar **sayılıyor** ve grafiğin altında bir cümleyle
söyleniyor: *"Başka para biriminde kaydedilen 2 ay gösterilmiyor"*. Sessizce
kısalan bir grafik, hiçbir şey söylemeden yanıltan bir grafiktir.

Aynı kural karşılaştırmaya da uyuyor: geçen ay başka bir birimdeyse çıkarma
yapılmaz ve **karşılaştırma hiç gösterilmez** (aşağıya bakın). Para birimi
değiştirildikten hemen sonra ekran "veri toplanıyor" hâline döner — doğrusu bu:
yeni birimde henüz bir ay vardır.

### Karşılaştırma ana ekranda değil, istatistik ekranında

ROADMAP "ana ekranda geçen aya göre" diyordu; **taşındı.** Ana ekranın
dashboard kartı `clearAndSetSemantics` ile **tek bir odak durağı** ve tek bir
cümledir (8a). İçine ikinci bir değer koymak ya o cümleyi uzatır ya da ikinci
bir durak açar — 8a'da bile bile kapatılan şey. Karşılaştırmanın bir de
**çalışması** var: trend grafiği. İkisini yan yana koymak, cevabı kendi
gerekçesinin yanında bırakıyor.

Hesap `MonthlyTrend.changeSince` içinde; composable'da aritmetik yok (§4).

### Pencere: son altı ay

360dp'de ekran paddingleri sonrası 328dp kalıyor; altı sütuna **54dp** düşüyor
ve üç harfli ay kısaltması yazı ölçeği 2.0'da bile sığıyor. Ölçüldü: 360dp'de
komşu etiketler arası en dar boşluk fs 1.0'da 34dp, **fs 2.0'da 11,5dp**;
411dp'de 42dp ve 20dp. On iki ay bu payı yarıya indirir ve etiketler normal
ölçekte bile çakışır. Altı ay ayrıca yarım yıl — bakarken akılda tutulabilen
bir aralık.

### Delikler yerinde kalıyor

Bir ayın satırı yoksa o ay yine bir yuva alır, değeri boş olur. Atlamak kalan
sütunları yan yana sıkıştırır ve **iki aylık bir tırmanışı bir aylık gibi**
gösterir — eksen zamansa, aralık da zaman olmalı. Bugün her değişiklikte
yazıldığı için delik beklenmiyor; bir ay hiç açılmayan uygulama delik bırakır.

Serinin **başındaki** boş aylar kırpılıyor: grafik kayıtların başladığı yerden
başlıyor, boşlukla değil.

### Çizgi değil sütun — üç hâl gerektiği için

Bir çizginin, değeri olmayan bir ayla yapacağı her şey yanlış: kopuk çizgi
çizim hatası gibi okunur, delik boyunca düz giden çizgi ise **kimsenin
kaydetmediği bir sayıyı** çizer. Sütun grafiği yuvayı boş bırakır. Asıl sebep
ise 12a'nın parasını ödediği ayrım: sütun üç hâli ayırabiliyor —

| hâl | çizim | sesli okunuş |
|---|---|---|
| Değeri olan ay | iz + dolu kısım | "Nisan 1.200,00 TL" |
| **Sıfır** kaydedilen ay | yalnızca iz | "Haziran 0,00 TL" |
| **Kaydı olmayan** ay | hiçbir şey | "Temmuz kayıt yok" |

### İz rengi yükseltilemez — ölçülen kontrast

13b'de ölçülen: sütun açık temada arka plana karşı **3,96:1**, koyu temada
**9,25:1**; sütun **kendi izine** karşı açık temada **3,01:1** — 3:1 sınırının
tam üstünde, yani sınırın kendisi kadar iyi.

**Faz 14a'da tekrar okundu ve iyileşti.** İz artık saydamlık değil,
`outlineVariant`: sütun↔iz açık temada **4,40:1**, koyu temada **3,65:1**;
sütun↔arka plan açık **5,98:1**, koyu **8,50:1**.

Ama başlıktaki sınır **duruyor ve kalıcı.** İz hem sütundan hem arka plandan
3:1 ayrışamıyor (iz↔arka plan açık 1,36:1, koyu 2,33:1) ve bu bir renk seçme
meselesi değil: iki koşul birlikte sütunun arka plana karşı **9:1**'e çıkmasını
gerektiriyor, paletin zümrütü beyazda 8,02:1 ve çıpa değişmiyor (§12). Yani
"sıfır kaydedilmiş ay" ile "kaydı olmayan ay" farkını gözle ayırmak hâlâ zayıf,
ayrımı **cümle** taşıyor.

### Eksen etiketleri: ölçek bir kez, aylar birer kez

Her sütunun üstüne tutar yazmak 328dp'ye altı tutar sığdırmak demek — hiçbir
yazı ölçeğinde olmuyor. Soldan bir tutar ekseni ise fs 2.0'da çizim alanının
üçte birini yer. Bunun yerine **ölçeğin tepesi** grafiğin üstünde bir kez
yazılıyor ("en yüksek 8.000,00 TL"), aylar da kendi sütunlarının altında birer
kez. İkisi birbirine giremez.

Sütun genişliğinin **tavanı var** (`Dimens.TrendBarMaxWidth`). Yuvanın payı
olarak bırakılınca iki aylık grafik 98dp'lik iki panel çiziyordu (dar
emülatörde ölçüldü); artık sütunun genişliği "kaç ayın var" demiyor.

Bir aya ait tutar sıfırdan büyük ama piksele yuvarlanınca sıfır oluyorsa en az
`Dimens.TrendBarMinHeight` kadar çiziliyor — yoksa "az" ile "hiç" aynı görünür.

### Artış/azalış: renk değil, ok ve cümle

Kırmızı/yeşil iki kere birden yok. 13b'de gerekçe "`error` tanımsız, yeşil
palette hiç yok"du; **Faz 14a ikisini de değiştirdi ve karar yine aynı kaldı,
hatta güçlendi:** artık paletin nötr mürekkebi zümrüt, yani *yeşil*. Yeşil bir
ok "bu iyi gitti" değil "bu uygulama" demektir, kırmızı bir ok da sıradan bir ay
için "bir şey bozuldu" der. Zaten yanlış araç: renk tek başına ayırt edemeyen
okuyucuya bir şey söylemez ve harcamanın artması bilerek abonelik ekleyen biri
için kötü haber değildir. Ok ile metin **aynı**
renkte (`primary`), yön **sözcükle** yazılıyor: *"Geçen aya göre 150,00 TL
arttı"*. Ok dekoratif (`contentDescription = null`), çünkü cümle zaten yönü
söylüyor; ekran okuyucu bilgiyi bir kez duyuyor. Değişim yoksa ok da yok: yana
bakan bir ok setimizde yok ve tire, azalma gibi okunur.

**Önceki ay yoksa hiçbir şey gösterilmiyor** — yer tutucu da, tire de yok.
10a'daki "tarihi olmayan abonelikte gösterge yok" kuralının aynısı.

### "Yeterli veri yok" istisna değil, varsayılan

Yeni kullanıcıda tabloda **tek ay** vardır ve ikinci ay gelene kadar öyle kalır.
Bu yüzden sıfır ve tek aylık hâller grafiğin kendisi kadar özenli: grafik
çizilmiyor, yerine iki hâlde de doğru olan **tek** bir cümle yazılıyor
("Trend için en az iki ay gerekiyor…"). Bölüm içinde bir paragraf, ikinci bir
resimli boş durum değil — ekranın zaten bir boş durumu var.

Temiz kurulumda **sıfır satırlı hâl çalışırken görülemiyor:** kaydedici
uygulama açılır açılmaz içinde bulunulan ayı yazıyor (§19). Ekranda görülen
hâl "tek ay"dır; sıfır satır yalnızca birim testinde kurulabiliyor.

### Yükleme: tek gösterge, tek parça ekran

Snapshot okuması dördüncü bir Flow olarak aynı `combine`'a giriyor; `combine`
her kaynağı beklediği için `isLoading` trendi de kapsıyor ve ekran tek parça
geliyor. Grafiğin üstüne ikinci bir gösterge koymak, tek veritabanından tek
seferde yüklenen bir sayfaya iki dönen çember koymak olurdu. 8a'daki 300 ms
gecikme sayesinde olağan okumada hiç gösterge çıkmıyor.

---

## 22. Düzenleme Ekranı ve Ortak Form

Faz 15. Kaydedilmiş bir aboneliği değiştirmek, ve iki formun aynı kurallara
uyması.

### Sheet değil ekran

Düzenleme **yarım kalabilir**. Bottom sheet'te kapatmanın üç yolu var —
scrim'e dokunma, aşağı sürükleme, geri tuşu — ve hiçbiri "bu düzenleme
hakkında bir karar" değil; üçü de sadece "kapat" demek. Bir hedefin tek çıkışı
var ve sistem geri tuşu zaten onu söylüyor. Geri yığını da böylece belirsiz
kalmıyor: ekran yığında, sheet ise yığının dışında bir durumdu.

### Ortak form bileşenleri, tek doğrulama kaynağı

İki form aynı tabloya yazıyor. Kural şu: **aynı şeyi iki yerde tarif etme.**

| parça | nerede | neden orada |
|---|---|---|
| Altı alan (`SubscriptionFormFields`) | `ui/common/` | İki ekran birebir aynı formu çiziyor; ikinci bir kopya görünüm ve davranışın ayrışacağı ilk yer olurdu. |
| Ne yazıldığı (`SubscriptionFormState`) | `ui/common/` | Altı `rememberSaveable` yerine tek tutucu ve tek `Saver`; §5 form alanlarını zaten composable'a bırakıyor. |
| **Kurallar** (`SubscriptionInput`) | `domain/usecase/` | Sıfır fiyat hangi form yazarsa yazsın abonelik değil. Bu bir **ekran** değil **abonelik** gerçeği. |
| **Sözcükler** (`FormErrors.kt`) | `ui/common/` | String kaynağı domain'e giremez (§1); kural bir *sebep* döndürür, ui onu cümleye çevirir. |

Bölünme kasıtlı: `SubscriptionInput` `NameProblem`/`PriceProblem`/`DateProblem`
döndürüyor, `UiText` değil. Domain androidx'siz kalıyor, mesaj değişince kural
dosyası açılmıyor, kural değişince iki ekran birden değişiyor.

**Refactor'ün kanıtı ölçümdür.** Çıkarma sonrası ekleme sheet'inin her
koordinatı 13b'deki referansla **birebir aynı** çıktı (iki emülatörde de),
klavye açıkken Kaydet hâlâ erişilebilir, döndürmede yazılan duruyor ve mevcut
268 testin hepsi değişmeden geçti.

### Fiyat alanına geri yazarken nokta kullanılıyor

Saklanan tutar düzenleme formuna `159.99` diye dönüyor: alanın kendi etiketi
iki dilde de örnek olarak `159.99` gösteriyor ve parser noktayı da virgülü de
kabul ediyor. Yerelleştirilmiş para `MoneyFormatter`'dan geçer — o sembol ve
binlik ayırıcı yazar, ikisini de parser geri alamaz.

### Çıpa gösterilir, ilerletilmiş tarih değil

Kart "25 gün kaldı" derken düzenleme ekranı kullanıcının girdiği **10 Eylül**'ü
açıyor. İkisi farklı sorulara cevap veriyor (§17) ve ilerletilmiş tarihi forma
koymak, kullanıcının hiç girmediği bir günü kaydetmeyi teklif etmek olurdu.
İki emülatörde de ölçüldü.

### Satır okunur, izlenmez

Ekran satırı tek seferlik `getById` ile okuyor, Flow ile değil. Flow, bu ekranın
**kendi** kaydı düştüğü anda yeniden yayın yapardı ve olmaması gereken tek şey
bu: kullanıcı yazarken formun üzerine yazmak. Ana ekran tabloyu izlemeye devam
ediyor; bu ekranın buna ihtiyacı yok.

Kaydederken satır **kopyalanıyor**, yeniden kurulmuyor: `id`, `iconKey` ve
`createdAt` olduğu gibi taşınıyor. `createdAt` listenin sıralama anahtarı —
düzenlenen abonelik listenin başına sıçramamalı.

### Bulunamayan abonelik bir hâldir, çökme değil

Silinen bir satırın düzenleme ekranı açılabilir: kart kaydırılırken ekran
açılıyor olabilir, ya da geri yığını satırdan uzun yaşayabilir. Ekran geri
zıplasaydı dokunuş hiç işlememiş gibi görünür ve kullanıcı tekrar dokunurdu;
bunun yerine ne olduğunu **söylüyor** ve çıkmak kullanıcının hamlesi oluyor.

### Yarım kalan düzenleme sessizce atılır

Geri tuşu "geri" demektir. Ekleme sheet'i de Faz 0'dan beri yarım kalan girişi
kapanınca atıyor; aynı bileşenlerden kurulu iki formdan birinin soru sorması
tutarsız olurdu. Onay diyaloğu, kullanıcının bilerek yaptığı bir jestin önüne
modal koymak demek; kabul edilen bedel, kullanıcının gördüğü hâliyle
kaydedilmemiş bir alanı yeniden yazması.

**Kaydet her zaman yazar** — değişmemiş bir formla Kaydet'e basmak da yazar.
Geri dönmek yazmaz; maddenin sorduğu da buydu.

### Silme bu ekranda yok

Kaydırarak silme zaten var ve kendi geri alma'sını taşıyor. İkinci bir kapı,
"peki bunu geri alabilir miyim" sorusuna ikinci bir cevap gerektirirdi; tek bir
eylem için iki geri alma davranışı, tek bir silme yolundan kötüdür.

### Tıklama jesti sürüklemenin yanında durur

`SwipeToDeleteRow`'un jest mantığına dokunulmadı (§12'deki offset birikmesi ve
%50 mesafe şartı olduğu gibi). Tıklama `draggable`'ın **yanına** bir modifier
olarak kondu. İkisi birden kazanamaz: sürükleme dokunma eşiğini geçer geçmez
hareketi tüketiyor, tüketilmiş bir değişiklik de bekleyen tıklamayı iptal
ediyor. Yani bir şey ifade edecek kadar uzun bir kaydırma asla aynı zamanda
dokunma değil.

**Eylem satırın kendi `semantics`'inde tanımlı**, `clickable`'a bırakılmadı:
`clearAndSetSemantics` alt ağacı düşürüyor, dolayısıyla dokunma parmağa var,
ekran okuyucuya yok olurdu. Satır hâlâ **tek düğüm** — iki emülatörde de
ölçüldü — ve artık hem dokunma hem "Sil" eylemini taşıyor.

### Bildirim ve anlık görüntü: yeni bağlantı gerekmedi

İkisi de tabloyu okuyor, olayları değil:

- `MonthlySnapshotRecorder` `subscriptions.observeAll()`'u topluyor; ekleme,
  silme ve **güncelleme** aynı yayını tetikliyor. Cihazda doğrulandı:
  düzenlemeden sonra `monthly_snapshots` tek satır kaldı ve toplamı yeni
  değere döndü.
- `PaymentReminderWorker` her koşuda `observeAll().first()` okuyup çıpaları
  baştan ilerletiyor. Enstrümantasyonla koşuldu: bir ay ötedeki satır hiçbir
  şey göstermezken, aynı satırın tarihi bugüne çekilince bildirim
  `EditedRow — today` diyor.

---

## 23. Tema Tercihi, Dynamic Color ve Para Birimi Gösterimi

Faz 14b. 14a paleti kurdu; bu bölüm kullanıcının o paletle ne yapabileceğini
ve para birimi işaretinin nereden geldiğini kayda geçiriyor.

### Dynamic color varsayılan KAPALI

Material You renkleri duvar kâğıdından üretilir. Bu açıkken 14a'da kurulan
zümrüt-altın kimliği **ve o kimliğe göre ölçülmüş 37 rolün kontrastı** ortadan
kalkar; ikisi de ürünün kendisi, yanında duran süs değil. Bu yüzden:

- Tercih yoksa **kapalı**. `observeDynamicColor()` anahtar yokken `false`
  döndürüyor; bu bir kolaylık değil, ürün kararı.
- Açmak kullanıcının seçimi. Açtığında palet artık bizim değil, o yüzden
  14a'nın kontrast tablosu da geçerli değil.
- Yalnızca **API 31+**. Altında `dynamicLightColorScheme` yok; tercih okunur,
  saklanır ve etkisiz kalır, uygulama kendi paletine düşer. Bu bir hata değil,
  beklenen davranış — ve ayarlar satırı bunu **yazıyor** (aşağıya bakın).

**14b'de ölçüldü (API 34, iki farklı duvar kâğıdı paleti):** dynamic color
açıkken grafik okunabilirliği bozulmuyor. Sıcak/kırmızı tohumda istatistik
çubuğu `#C00020` ile izi `#DFBFBD` **3,78:1**; soğuk/mavi tohumda `#004FE6`
ile `#C4C5D6` **3,77:1**. Trend sütunu her iki palette de aynı çifti
kullanıyor. Snackbar'ın "Geri al"ı sıcakta 10,84:1, soğukta 10,87:1. Yani
14a'da eski palette yaşanan çubuk-iz çakışması Material You'da tekrarlamıyor;
üretilen paletler rolleri zaten ayırıyor.

### Tema modu ile dynamic color BİRBİRİNDEN BAĞIMSIZ

İki ayrı anahtar, iki ayrı soru: **hangi hue'lar** (dynamic color) ve
**açık mı koyu mu** (tema modu). Duvar kâğıdı renklerini açan bir kullanıcı
koyu temayı zorlamaya devam edebilmeli. Tek bir "tema" ayarı bu ikisini
birleştirseydi, Material You'yu açmak aydınlık/karanlık kararını sessizce
sisteme geri verirdi.

Tema modu üç değerli: `SYSTEM` (varsayılan) / `LIGHT` / `DARK`. `SYSTEM`
"açık"ın süslü hâli değil; sistem değiştikçe uygulama da değişsin diye verilen
sürekli bir talimat. `isSystemInDarkTheme()` yalnızca o dalda okunuyor, böylece
zorlanmış bir tema sistem dönerken yeniden derlenmiyor.

### Yeni repository AÇILMADI

Her ikisi de **mevcut** `SettingsRepository`'ye eklendi ve §14'teki tek
`DataStore<Preferences>` örneğini kullanıyor. Gerekçe: ayrı bir
`ThemeRepository` aynı dosyanın üstüne ikinci bir arayüz koyardı ve yeni bir
tercihin hangisine gideceğini söyleyen bir kural olmazdı. Ana para biriminin
deseni birebir tekrarlandı — ad ile saklama (ordinal değil), bilinmeyen değerde
varsayılana düşme, `IOException`'da `emptyPreferences()`.

`DynamicColorSupport` ise ayrı bir arayüz, çünkü `Build.VERSION.SDK_INT`
cihaz dışında sıfır okur ve ViewModel testlenemez hâle gelir. Desen
`ReminderNotificationStatus` ile aynı (§18). Sürüm sayısı tek bir yerde:
`isAvailableOnThisBuild()`, `@ChecksSdkIntAtLeast` ile işaretli ki lint
koruma çağrısını takip edebilsin.

### Açılışta ilk kare TUTULUYOR — kapı 16h'den beri splash'ta

**Ölçüldü (API 34, sistem açık temada, saklanan tercih koyu):** tutma
olmadan ana ekran **açık temada tam olarak çiziliyordu** — arka plan
`#D3E2D8`, uygulama çubuğu beyaz — ve ancak ondan sonra koyuya dönüyordu. Tek
bir tam kare, ama kullanıcıya uygulamanın fikir değiştirdiği gibi görünüyor.
**Karar bu ve değişmedi: tema tahmin edilmiyor, okunması bekleniyor** — her
tahmin biri için yanlış. Değişen yalnızca beklenirken ekranda ne durduğu.

**Mekanizma (Faz 16h).** 14b'nin `OnPreDrawListener`'ı **kaldırıldı**; yerine
`androidx.core:core-splashscreen` 1.2.0 geldi ve kapı sistemin splash'ına
bağlandı. `MainActivity.onCreate`:

- `installSplashScreen()` `super.onCreate`'ten **önce**. Kütüphanenin şartı,
  tercih değil: çağrı activity'nin temasını `Theme.SubTrack.Starting`'ten
  `postSplashScreenTheme`'e çeviriyor ve bunu pencere kurulmadan yapması
  gerekiyor. Sonra çağrılsaydı başlangıç teması uygulamanın kalıcı teması
  olurdu.
- `setKeepOnScreenCondition { tercih yok && son tarih geçmedi }`
  `setContent`'ten **sonra**. Sebep 14b'de ölçülmüştü ve kütüphane de aynı
  kısıta tabi: içerik görünümünün içine bir şey konana kadar kendi
  `ViewTreeObserver`'ı yok, kütüphane kendi dinleyicisini tam o görünüme
  kayıt ediyor.
- **Tek kapı var.** Eski pre-draw dinleyicisi silindi; üst üste iki kapı aynı
  okumayı iki kez beklerdi.
- Gecikmeli `invalidate()` korundu: iptal edilen bir çizim yeni traversal
  planlamaz, yani tercih hiç gelmezse koşulun bir daha okunacağı an olmaz.
  Ana looper'a atılan gecikmeli mesaj her iki yolda da çalışır.

**Üst sınır 1000 ms — bütçe değil, failure path.** Tercih hiç gelmezse
(DataStore takılırsa; `SettingsRepositoryImpl`'in `IOException` yedeğinin
kapsamadığı bir hâl) kapı son tarihle açılıyor ve uygulama varsayılan temayla
geliyor. Yanlış tema düzeltilebilir, hiç çizilmeyen bir pencere düzeltilemez:
sistem onu "does not have a focused window" diye raporlayıp uygulamayı
öldürür. 16g gerçek okumayı **173–212 ms** ölçtü, yani bir saniye normal
kullanımda erişilemeyecek kadar uzak. 16h son tarihi simüle takılmayla ayrıca
doğruladı: api34'te kapı **t+1258 ms**'de, tercih hâlâ okunmamışken açıldı.
Son tarih `MainActivity`'de, ViewModel'de değil — "bu pencere ne kadar boş
kalabilir" pencereye ait bir soru, tercihe ait değil.

**Splash zemini `#0D1A14`, iki şemada da.** Ön plan on iki altın para ve altın
beyaz üstünde 2,10:1 (§27); orada şekil taşıyamaz, yani açık bir splash
işareti gizlerdi. Bu, §27'nin "ikon sistem temasına göre değişmez" kararının
devamı, ve zemin `colors.xml`'deki `ic_launcher_ground`'dan okunuyor,
`Color.kt`'den değil. Bedeli kabul edildi: koyu temada splash uygulamaya
**renk değişmeden** devrediyor, açık temada sonda tek bir koyu→açık adım var.
Bu turda release build ile, ham kare yakalamayla yeniden ölçüldü (gövdenin
baskın rengi, durum çubuğu bandı kırpılarak):

| Cihaz / tema | Kare dizisi |
|---|---|
| api34 koyu | `#0D1A14` (%96,8) → `#0D1A14` (%76,3) → `#0D1A14` (%74,5) |
| api34 açık | `#0D1A14` (%96,8) → `#C8D7CC` (%72,5) → `#D3E2D8` (%72,7) |
| api29 koyu | `#0D1A14` (%94,9) → `#0D1A14` (%64,5) |
| api29 açık | `#0D1A14` (%94,9) → `#65736C` (%58,1) → `#D3E2D8` (%62,6) |

Aradaki `#C8D7CC` ve `#65736C` bağımsız bir üçüncü renk değil, çapraz geçiş
karesi — 16h bunu kanal başına aynı `t` çıkararak göstermişti. İşaretin kendisi
splash karesinde iki cihazda ve iki temada da **`#D4AF37` / `#0D1A14` =
8,50:1**; altın piksel oranı api34'te %2,78, api29'da %4,52 ve iki tema
arasında birebir aynı.

### Durum çubuğu ikon rengini ne belirliyor — iki pencere, iki mekanizma

`SystemBarStyle` **ikon rengini söylemez**; "arkamdaki zemin koyu mu" der.
`androidx.activity` 1.12.4, `EdgeToEdge.kt`:

- `SystemBarStyle.dark(scrim)` → `detectDarkMode = { true }`
- `SystemBarStyle.light(scrim, darkScrim)` → `detectDarkMode = { false }`
- `SystemBarStyle.auto(l, d) { ... }` → verilen lambda

`enableEdgeToEdge` bu cevabı `statusBarIsDark`'a çeviriyor ve ikon için tek
yaptığı şu:
`WindowInsetsControllerCompat(...).isAppearanceLightStatusBars = !statusBarIsDark`.
Yani `dark` → bayrak **kapalı** → ikonlar beyaz; `light` → bayrak açık →
ikonlar koyu. Bayrak **`window.decorView`'a**, yani uygulamanın kendi
penceresine yazılıyor; başka bir pencereye ulaşamaz.

**1. Uygulama penceresi — bayrağı composable koyuyor.** `onCreate`'teki stil
(16h'de `dark`, 16h-2'den beri `auto`) geçicidir ve kararlı hiçbir kareye
ulaşmaz: tercih gelir gelmez `SystemBarsFollowTheTheme` `enableEdgeToEdge`'i
`auto(...) { darkTheme }` ile yeniden çağırıp bayrağı temaya bağlar. Bu turda dört durumda hem bayrak hem
piksel okundu. Bant = ekranın üst `statusBars.top` şeridi (api29'da 48 px,
api34'te 128 px); "ikon çekirdeği" bandın zeminden parlaklıkça **en uzak**
pikseli, koordinatıyla birlikte:

| Cihaz | Tema | Pencere bayrağı | Bant zemini | İkon çekirdeği | Okunan piksel | Kontrast |
|---|---|---|---|---|---|---|
| api29 | açık | `mSystemUiVisibility=0x2710` (LIGHT_STATUS_BAR) | `#FFFFFF` | `#666666` | (106,10) | **5,74:1** |
| api29 | koyu | `0x700` — LIGHT_STATUS_BAR yok | `#1F3D2D` | `#FFFFFF` | (106,10) | **11,91:1** |
| api34 | açık | `apr=LIGHT_STATUS_BARS` | `#FFFFFF` | `#666666` | (905,48) | **5,74:1** |
| api34 | koyu | `apr=` satırı yok (appearance 0) | `#1F3D2D` | `#FFFFFF` | (905,48) | **11,91:1** |

Açık temanın kanıt değeri şurada: `dark` hâlâ yürürlükte olsaydı bayrak kapalı
kalır, ikonlar `#FFFFFF` üstüne beyaz çizilir, kontrast 1,00:1 olurdu — 16-0'da
ölçülen hata tam buydu ("üst bantta beyaz olmayan piksel yok"). Olmuyor; geçici
stili composable'ın çağrısı eziyor.

**2. Splash penceresi uygulamanın penceresi DEĞİL.** Açılış sırasında
`dumpsys window` iki ayrı pencere gösteriyor ve sırası ölçüldü: önce
`Splash Screen com.elinacn.subtrack` (`ty=APPLICATION_STARTING` — sistemin
başlatma penceresi), uygulamanınki (`com.elinacn.subtrack/.MainActivity`)
ancak birkaç örnekleme sonra. Başlatma penceresi uygulama kodu çalışmadan
kuruluyor ve kaynağı `Theme.SubTrack.Starting`; o tema da türediği
`Theme.SplashScreen` de `windowLightStatusBar` **yazmıyor** (AAR'ın kaynakları
tarandı: yalnızca `windowLightNavigationBar` var). Bayrak sıfır kalıyor —
api29'da `mSystemUiVisibility=0x0` diye okundu, api34'te o pencerede appearance
satırı hiç basılmıyor — yani ikonlar beyaz, ki splash zemini `#0D1A14` olduğu
için doğru cevap. Ölçülen: splash karesinde bant `#0D1A14` üstünde `#FFFFFF`,
**17,87:1**, api29 ve api34'te aynı.

Yani **splash'ın okunur olmasını sağlayan şey `onCreate`'teki stil değil,
splash temasının kendisi.** 16h'nin `dark` tercihi de bu yüzden bir şey
kazandırmıyordu; 16h-2 onu `auto` ile değiştirdi ve gerekçesi bu başlıkta
değil, aşağıdaki son tarih yolunda.

> **16h'nin iki sayısı yanlış kareye yazılmış.** 16h kaydı "api29'da koyu temada
> bant 11,91:1, açık temada 5,74:1" diyerek bunları `SystemBarStyle.dark`
> değişikliğinin doğrulaması olarak sunuyor. Sayılar doğru — bu turda ikisi de
> birebir yeniden üretildi — ama **splash karesinden gelmiyorlar:** ikisi de
> uygulama karesinin ölçüsü, yani `auto { darkTheme }`'in sonucu, `dark`'ın
> değil. Cihaza da bağlı değiller; api29 ile api34 aynı değerleri veriyor.
> Splash karesinin gerçek değeri iki temada da **17,87:1**, ve depodaki
> `docs/screenshots/phase-16h/splash-*-api29.png` dosyaları da bunu söylüyor
> (zemin `#0D1A14` %92,7, ikon `#FFFFFF` %4,6, iki dosyada da).

**Tema değişince — uygulama yeniden başlatılmadan.** `SystemBarsFollowTheTheme`
bir `DisposableEffect` ve anahtarlarından biri `darkTheme`, yani ayar değişince
yeniden koşuyor. Ölçüldü: ayarlardan açık → koyu → açık, süreç aynı kalarak
(api34 `pid 5490`, api29 `pid 7177`; üç ölçümün üçünde de aynı pid), bant her
adımda yukarıdaki tablonun değerine oturuyor. Ekran görüntüleri
`docs/screenshots/phase-16h-1/themeswitch-*`.

**Açılışta ölçülen bir geçiş penceresi var.** 16h-1'de bulundu, 16h-2'de
mekanizması çözüldü ve **kabul edildi**; aşağıdaki iki başlık onun kaydı.

> **16h-1'in api29 sayısı yanlış.** O tur "api29'un aynı yerinde ölçülen en
> düşük değer **4,04:1**" diyor. Kaba örneklemenin sonucu: 16h-2 aynı yeri üç
> tekrarla yeniden ölçtüğünde api29 da **1,00:1**'e iniyor (`#FFFFFF` üstünde
> `#FFFFFF`). Kusur iki cihazda da aynı, api29'da daha hafif değil.

### Devir rampası — bilinen, kabul edilmiş davranış

**Kusur.** Uygulama **açık** temadayken, splash'tan uygulamaya devirde durum
çubuğu ikonları kısa süre görünmez oluyor. En kötü kare **1,00:1** — bantta
zeminden farklı tek piksel yok — ve bu api29 ile api34'te aynı. Süre, ham kare
dizisinde iki örnek arası ~100 ms iken 200–350 ms mertebesinde ölçüldü. Sistem
temasından bağımsız: sistem açıkken de koyuyken de aynı.

**Mekanizma — zemin anında değişiyor, ikon tonu rampalıyor.** Devir boyunca
durum çubuğunun sahibi splash penceresi; onun cevabı "arkamdaki zemin koyu"
olduğu için ikonlar beyaz (bir önceki başlıktaki 2. madde). Splash düşünce
zemin **tek karede** uygulamanın beyaz yüzeyine dönüyor, ama SystemUI ikon
tonunu beyazdan `#666666`'ya bir animasyonla götürüyor ve o animasyon beyaz
yüzey ekrana geldikten **sonra** yürüyor. Aradaki kareler önce beyaz üstünde
beyaz, sonra beyaz üstünde giderek koyulaşan gri.

Rampanın ham kare dizisi — api34, açık uygulama teması, sistem açık, release
build; dosyalar `docs/screenshots/phase-16h-2/handover-api34-light-*.png`:

| Dosya | t | Bant zemini | İkon çekirdeği | Kontrast |
|---|---|---|---|---|
| `-1` | +1847 ms | `#0D1A14` (splash) | `#FFFFFF` | 17,87:1 |
| `-2` | +1908 ms | `#202C26` (çapraz geçiş) | `#FFFFFF` | 14,49:1 |
| `-3` | +2114 ms | `#565F5B` (çapraz geçiş) | `#FFFFFF` | 6,60:1 |
| `-4` | +2273 ms | `#C7CAC9` (çapraz geçiş) | `#FFFFFF` | **1,65:1** |
| `-5` | +2295 ms | `#FFFFFF` | `#FFFFFF` | **1,00:1** |
| `-6` | +2553 ms | `#FFFFFF` | `#6D6D6D` | 5,17:1 |
| `-7` | +2565 ms | `#FFFFFF` | `#666666` | 5,74:1 |

Dizide iki örnek daha var, ama dosyaları bir öncekiyle **bayt bayt aynı**
olduğu için depoya alınmadı: t+2241 ms `-3` ile, t+2398 ms `-5` ile aynı. Yani
1,00:1 tek bir kare değil, en az 100 ms duran bir hâl. Tek bir zincirin
çözemediği ara tonlar tekrarlardan toplandı; iki cihaz ve iki stil birlikte
aynı eğriyi veriyor: `#FFFFFF` 1,00 → `#D3D3D3` 1,50 → `#BCBCBC` 1,90 →
`#A9A9A9` 2,35 → `#9A9A9A` 2,81 → `#8A8A8A` 3,45 → `#6D6D6D` 5,17 → `#666666`
5,74.

Kareler tek bir açılıştan, cihazın kendi saatiyle damgalanmış birkaç paralel
yakalama döngüsünden geliyor: `screencap` bu emülatörde ~200 ms sürüyor, tek
döngü rampayı çözemeyecek kadar kaba.

**Koyu uygulama temasında yok.** Ölçülen en kötü kare iki cihazda da
**11,91:1** (api29'da bir koşuda çapraz geçiş karesi 8,65:1). Orada
uygulamanın yüzeyi de koyu, yani rampanın gideceği yer beyazın kendisi.

**Neden kodla kapatılmadı.** Üç yol da çıkmaz:

- **Devri geciktirmek çözmüyor.** Rampa splash penceresi düşünce başlıyor.
  Splash ne kadar tutulursa tutulsun zemin düştüğü anda değişiyor, ikon tonu
  sonradan rampalıyor; gecikme kusuru ötelemekten başka bir şey yapmıyor.
- **Açık temada açık splash zemini sorunu taşıyor, çözmüyor.** Splash
  uygulamanın kendi tercihini okumadan çiziliyor — Android 10'da sistem
  temasını izliyor. "Sistem koyu + uygulama açık" kombinasyonu kör kalırdı,
  "sistem açık + uygulama koyu" ise yeni bir kör pencere açardı. Üstelik
  §27'nin "ikon sistem temasına göre değişmez" kararını ve tek işaret
  varyantını bozmak demek.
- **`SystemBarStyle` ile kapatılamaz.** Bayrak uygulamanın penceresine
  yazılıyor ve o pencere devir anında zaten doğru bayrağı taşıyor (aşağıdaki
  başlık). Rampa SystemUI'ın kendi animasyonu; uygulamanın ulaşabileceği bir
  yerde değil.

Koyu splash'tan açık arayüze geçişte bu, genel platform davranışı. Kullanıcı
fiziksel cihazda fark etmedi. **Karar: kusur kabul edildi, kodla
kapatılmayacak.**

### `onCreate`'teki geçici stil `auto` — karar son tarih yoluna ait

16h-2'ye kadar `SystemBarStyle.dark(TRANSPARENT)`'tı, artık
`SystemBarStyle.auto(TRANSPARENT, TRANSPARENT)`. Gerekçe **normal açılış
değil**, son tarih yolu.

**Normal açılışta iki stil arasında fark yok — ölçüldü.** İki cihaz × dört
kombinasyon × iki stil, her hücre üç tekrar, release build. "İlk karelerin en
kötüsü" splash karesinden itibaren, launcher kareleri hariç:

| Cihaz | Sistem | Uygulama | `dark` — üç koşu | `auto` — üç koşu | Kararlı |
|---|---|---|---|---|---|
| api29 | açık | açık | 1,64 · 2,12 · 2,24 | 2,43 · **1,00** · 1,90 | 5,74:1 |
| api29 | açık | koyu | 8,65 · 11,91 · 11,91 | 11,91 · 11,91 · 11,91 | 11,91:1 |
| api29 | koyu | açık | 1,94 · 1,50 · 2,61 | **1,00** · 1,47 · 1,45 | 5,74:1 |
| api29 | koyu | koyu | 11,91 · 11,91 · 11,91 | 11,91 · 11,91 · 11,91 | 11,91:1 |
| api34 | açık | açık | 1,08 · 1,02 · 1,02 | 5,74 · **1,00** · 1,03 | 5,74:1 |
| api34 | açık | koyu | 11,91 · 11,91 · 11,91 | 11,91 · 11,91 · 11,91 | 11,91:1 |
| api34 | koyu | açık | 1,04 · 1,02 · 1,12 | 1,02 · 1,03 · 1,01 | 5,74:1 |
| api34 | koyu | koyu | 11,91 · 11,91 · 11,91 | 11,91 · 11,91 · 11,91 | 11,91:1 |

Açık hücrelerdeki tek tek sayılar stil farkı değil **örnekleme fazı**: rampa
sürekli ve her koşuda başka bir noktasına denk geliniyor, o yüzden bir hücrenin
en düşük değeri stile değil şansa bağlı — nitekim iki stil de bazı koşularda
1,00:1'e, bazılarında 2'nin üstüne denk geliyor. Anlamlı olan, iki stilin
topladığı ton kümesinin aynı eğri olması ve hiçbir stilin diğerinde olmayan bir
kareyi üretmemesi. Koyu uygulama temasının hiçbir hücresi ise 8,65:1'in altına
inmiyor. Yani `auto`'nun normal açılışta ne kazandırdığı ne kaybettirdiği var;
kazanç aşağıdaki son tarih yolunda.

**Sebebi pencere bayrağında görünüyor.** `dumpsys window` ile açılış boyunca
okunan appearance, api34, uygulama açık temada:

| Stil | Uygulama penceresi doğduğunda | Sonra |
|---|---|---|
| `dark` | `apr=LIGHT_NAVIGATION_BARS` (t+256 ms) — durum çubuğu bayrağı **yok** | t+601 ms'de `LIGHT_STATUS_BARS` ekleniyor |
| `auto` | `apr=LIGHT_STATUS_BARS LIGHT_NAVIGATION_BARS` (t+192 ms) | değişmiyor |

`dark` yanlış bayrakla doğup ~350 ms sonra düzeliyor, ama o 350 ms boyunca
ekrandaki pencere hâlâ splash — bayrak bir kareye ulaşmıyor. Devir anına
gelindiğinde iki stil de aynı bayrağı taşıyor; tablonun aynı çıkmasının sebebi
bu. Uygulama **koyu** temadayken durum aynanın öbür tarafı: bu sefer `auto`
doğarken yanlış cevabı veriyor (sistem açıkken `LIGHT_STATUS_BARS`), o da bir
kareye ulaşmıyor — koyu satırlarda hiçbir bozulma ölçülmedi.

**Farkı yaratan yer: son tarih yolu.** Tercih 1000 ms'de gelmezse kapı
açılıyor ve uygulama `ThemeMode.Default` ile çiziyor. O yolda
`SystemBarsFollowTheTheme` henüz hiçbir şey uygulamamış oluyor (`isThemeKnown`
false), yani pencere `onCreate`'teki değerde **kalıyor**. `Default` sistemi
izlediğine göre onunla anlaşan tek stil de sistemi izleyen stil. Tercih
okuması 5 s geciktirilerek ölçüldü (geçici kod, turun sonunda kaldırıldı);
aşağıdaki değerler kapı açıldıktan sonra, tercih hâlâ gelmemişken:

| Cihaz | Sistem | `dark` | `auto` |
|---|---|---|---|
| api29 | açık | `#FFFFFF` üstünde `#FFFFFF` — **1,00:1**, okuma gelene kadar sürüyor | **5,74:1** |
| api29 | koyu | 11,91:1 | 11,91:1 |
| api34 | açık | **1,00:1**, sürüyor | **5,74:1** |
| api34 | koyu | 11,91:1 | 11,91:1 |

Buradaki 1,00:1 rampanın geçici çukuru değil: ölçüm penceresinin sonuna kadar
(api34'te t+3,9 s) hiç değişmiyor, çünkü onu değiştirecek çağrı hiç gelmiyor.
Ekran görüntüleri
`docs/screenshots/phase-16h-2/deadline-api34-dark-syslight.png` ve
`...-auto-syslight.png` — gövde iki dosyada da `#D3E2D8`, fark yalnızca durum
çubuğunda.

Navigasyon çubuğu stiline dokunulmadı; `onCreate` zaten yalnızca durum
çubuğunu veriyor.

### Para birimi: HER YERDE SEMBOL

`NumberFormat` para işaretini okuyucunun locale'inden alır ve o locale'de o
para birimi için glif yoksa **üç harfli ISO koduna** düşer. Sonuç tutarsızdı:
İngilizce arayüzde toplam "TRY 1.785,45", kart "$10.99" diyordu — aynı ekranda
iki farklı yazım.

Kural: **locale sayıyı belirler, para birimi işareti belirlemez.** Ondalık
ayracı, binlik gruplama ve işaretin sayının hangi tarafında durduğu okuyucunun;
işaretin kendisi her locale'de `Currency.symbol`.

- İşaret `domain/model/Currency` üzerinde, `strings.xml`'de değil. Çevrilebilir
  metin değil: lira her dilde lira, ve çeviri dosyası onu değiştirmeye davettir.
- Platformdan da okunmuyor; platformun cevabı zaten düzeltilen sorunun kaynağı.
- Uygulama noktası tek: `MoneyFormatter`, `DecimalFormatSymbols.currencySymbol`
  üzerinden. Para birimi atandıktan **sonra** yazılıyor, çünkü para birimi
  atamak sembolü locale'in kendi cevabıyla geri yazar.
- Kur ekranı tutar biçimlendirmiyor, para birimi **adlandırıyor** — o da aynı
  işaretlerle ("1 $ = … ₺"). Çıpayı "TRY" diye anan, toplamı "₺" ile yazan bir
  uygulama okuyucudan tek şey için iki ad tutmasını ister.
- **İstisna:** ayarlardaki para birimi seçici chip'leri ISO kodunu yazmaya
  devam ediyor (TRY / USD / EUR / GBP). Orada kod bir tutarın yazımı değil,
  seçilen şeyin kimliği; chip'in ekran okuyucuya verdiği ad zaten tam adı
  söylüyor (§ Faz 9b).

**₺ karakteri API 29'da çiziliyor** — eski font sürümlerinde eksik olabilir
diye ayrıca ölçüldü.

---

## 24. R8, Kaynak Daraltma ve İmzalama

Faz 16c. Release derlemesi bu faza kadar `isMinifyEnabled = false` ile
çıkıyordu; APK'nın **%98,3'ü dex**ti. Bu bölüm neyin açıldığını, hangi kuralın
neden **yazılmadığını** ve imzalama malzemesinin nereden okunduğunu kayda
geçiriyor.

### Ölçüm — R8 öncesi ve sonrası

Aynı kaynak ağacı, üç derleme:

| Derleme | APK | dex (sıkıştırılmamış) | dex (APK içinde) | dex dosyası | `resources.arsc` |
|---|---|---|---|---|---|
| debug | 20,28 MiB | 63,73 MiB | 19,53 MiB | 18 | 533.880 B |
| release, minify **kapalı** | 13,06 MiB | 45,45 MiB | 12,31 MiB | 5 | 529.616 B |
| release, minify **açık** | **2,02 MiB** | **3,09 MiB** | **1,50 MiB** | **2** | **306.388 B** |

APK 13,06 → 2,02 MiB; dex 45,45 → 3,09 MiB. `isShrinkResources = true`
`resources.arsc`'yi 529.616 → 306.388 B'ye indiriyor. Silinenlerin neredeyse
tamamı kütüphanelerin kendi kaynakları: projenin 125 metninden **124'ü**
daraltıcının erişilebilir listesinde, kalan biri (`app_name`) manifest
üzerinden tutuluyor ve cihazda uygulama adı olarak doğrulandı.

### `proguard-rules.pro` neden BOŞ

Yöntem sırayla şuydu: **önce kural yazmadan derle, sonra release APK'yı
cihazda sür, kırılanı ölç.** Hiçbir şey kırılmadı, bu yüzden tek bir `-keep`
yazılmadı.

Sebebi, kuralların zaten gelmesi: R8 birleşik yapılandırmayı
`build/outputs/mapping/release/configuration.txt` dosyasına yazıyor ve orada
**70'in üzerinde kural kaynağı** listeleniyor — `room-runtime`, `room-ktx`,
`hilt-android`, `hilt-work`, `hilt-navigation-compose`,
`datastore-preferences-core`, `work-runtime`, `navigation-*`, `material3`,
`ui-*`, `lifecycle-*` ve R8'in kendi `coroutines.pro`'su. Kütüphaneler
reflection kullandıkları yerleri kendi AAR'larında koruyor.

Önleyici kural yazmak, hiçbir zaman silinmeye cesaret edilemeyen **ölü kural**
bırakır: gerçekten gereksiz olduğu anlaşılsa bile kaldırmanın neyi kıracağını
kimse bilemez. O yüzden dosya boş; ileride eklenecek her kuralın yanına
**hangi yığın izini** kapattığı yazılacak.

### Cihazda doğrulanan yerler

Minify açık release APK dört cihaza da kuruldu (API 24 / 29 / 34 / 36) ve
reflection kullanan her yol tek tek sürüldü:

| Yer | Nasıl sürüldü | Sonuç |
|---|---|---|
| Hilt grafı | Uygulama açılışı | Dördünde de açılıyor |
| Room | Açılış, liste, ekleme, düzenleme, silme, geri al | Dördünde de |
| Room şema kimliği | `schemas/1.json` ↔ üretilen `SubTrackDatabase_Impl` | `ef18d874586948288a87736e03c2b556`, eşleşiyor |
| DataStore | Para birimi, tema, kur; hepsi soğuk başlatma sonrası | Dördünde de duruyor |
| WorkManager + `@HiltWorker` | İş koşturuldu, bildirim shade'de okundu | Dördünde de SUCCESS |
| Compose Navigation | Ana / istatistik / ayarlar / kur / düzenleme | Dördünde de |
| `java.time` + desugaring | Tarih seçici, "gün kaldı", "Son düzenleme" | **API 24 dahil** |
| `NumberFormat` / locale | ₺ ve $ biçimlendirmesi, TR ve EN | Dördünde de |

**En riskli yer `@HiltWorker`'dı** — assisted injection, worker sınıfını
**adıyla** aranan bir multibinding haritasından kuruyor; R8 adı değiştirirse
bu ancak çalışma anında patlar. API 29'da iş **zamanı geldiğinde kendiliğinden**
koştu (`Worker result SUCCESS`), diğer üçünde cihaz saati ileri alınıp
`cmd jobscheduler run -f` ile sürüldü. Dördünde de bildirim shade'e ulaştı.

### `material-icons-extended` kalıyor — ölçüldü

Kullanılan **12 ikonun 8'i** `material-icons-core`'da var (`Add`,
`AutoMirrored.Filled.ArrowBack`, `AutoMirrored.Filled.List`, `DateRange`,
`Delete`, `PlayArrow`, `Settings`, `Star`); **4'ü yalnızca `-extended`'da**:
`BarChart`, `Cloud`, `ArrowUpward`, `ArrowDownward`.

Core'da bu dördünün karşılığı **yok**. `BarChart` istatistik girişinin ve boş
durumunun tek işareti; `Cloud` bulut depolama aboneliklerini diğerlerinden
ayıran şey; yukarı/aşağı okların core'daki en yakın komşusu
`KeyboardArrowUp`/`KeyboardArrowDown`, o da yön değil **açılır/kapanır**
anlatan bir chevron. Dördünü birden karşılamayan bir değişim bağımlılığı
kaldırmaya yetmez — ikisini değiştirip diğer ikisi için `-extended`'ı tutmak
**sıfır bayt** kazandırır.

Ayrıca `material3` 1.4.0 artık `material-icons-core`'u **getirmiyor**; core
ağaca yalnızca `-extended`'ın bağımlılığı olarak giriyor. Yani bırakmak
"extended'ı sil" değil, "extended'ı sil, core'u açıkça ekle" demek.

Bedeli ölçüldü:

| | minify kapalı | minify açık |
|---|---|---|
| `-extended` ile | 13.695.885 B | 2.114.862 B |
| yalnızca `-core` ile | 9.550.517 B | 2.114.646 B |
| fark | **4.145.368 B (~3,95 MiB)** | **216 B** |

R8 kapalıyken `-extended` gerçekten pahalı; **R8 açıkken bedeli yok.** Son
APK'da `androidx.compose.material.icons` altından **beş sınıf** kalıyor
(`DateRange`, `Delete`, `PlayArrow`, `Settings`, `Star`), kalanlar çağıranın
içine gömülmüş. 216 B'lik fark kütüphanenin değil, deneyde yerine konan yedek
ikonların yol verisinin farkı.

**Karar:** `material-icons-extended` kalıyor. ROADMAP Faz 16'daki "kaldırılsın
veya daraltılsın" maddesinin cevabı: **R8 daraltıyor.**

### `desugar_jdk_libs` bedeli — R8 sonrası ölçüldü

Faz 10a'da "~200-400 KB" diye tahmin edilmişti; ölçülen değer:

| | dex (sıkıştırılmamış) | APK içinde |
|---|---|---|
| minify kapalı | 326.932 B | 144.680 B |
| minify açık | 278.384 B | 128.900 B |

APK'daki `classes2.dex` tam olarak bu dosya. R8 desugar kütüphanesinden
yalnızca ~15 KB kırpıyor; beklenen, çünkü L8 zaten uygulamanın kullandığı
yüzeye göre daraltıyor. `minSdk 24` ile `java.time` kullanmanın bedeli
**~126 KiB** — tahmin doğru taraftaymış, biraz cömertmiş.

### İmzalama — malzeme `local.properties`'ten

Release `signingConfig`'in dört değeri (`storeFile`, `storePassword`,
`keyAlias`, `keyPassword`) **önce `local.properties`'ten**, orada yoksa
**ortam değişkenlerinden** okunuyor.

`local.properties` seçildi çünkü: zaten `.gitignore`'da (Faz 1a), Gradle onu
`sdk.dir` için **zaten okuyor**, ve Android Studio ile komut satırı aynı
değerleri kabuk profili kurmadan görüyor. Ortam değişkeni yedeği bugün için
değil: `local.properties` bulunmayan bir CI makinesinin aynı dört değeri
yapılandırmayı değiştirmeden verebilmesi için açık bırakılmış bir kapı.

| `local.properties` | Ortam değişkeni |
|---|---|
| `subtrack.storeFile` | `SUBTRACK_STORE_FILE` |
| `subtrack.storePassword` | `SUBTRACK_STORE_PASSWORD` |
| `subtrack.keyAlias` | `SUBTRACK_KEY_ALIAS` |
| `subtrack.keyPassword` | `SUBTRACK_KEY_PASSWORD` |

`signingConfig` **yalnızca** dört değer de varken ve keystore dosyası gerçekten
mevcutken oluşturuluyor; aksi halde `signingConfigs.findByName("release")`
`null` döndürüyor ve build type imzasız kalıyor. Ölçüldü:

- Keystore yokken `assembleRelease` **hata vermiyor**, `app-release-unsigned.apk`
  üretiyor. `apksigner verify` bu dosya için *"DOES NOT VERIFY — Missing
  META-INF/MANIFEST.MF"* diyor; imzasız olduğu açık.
- `assembleDebug` **etkilenmiyor**: debug build kendi `signingConfig`'ini
  SDK'nın debug anahtarıyla kullanmaya devam ediyor, çıktı boyutu değişmiyor.

**Debug anahtarına düşülmüyor.** Eksik keystore'da release'i debug anahtarıyla
imzalamak "çalışıyor gibi" görünür ve imzasız bir çıktının yayınlanabilir
sanılmasına kapı açar; imzasız kalmak bu yüzden hata değil, kasıt.

Keystore dosyası ve şifreler **kullanıcı tarafından** oluşturulur; bu depo
hiçbir anahtar malzemesi taşımaz. `.gitignore`'da `*.jks`, `*.keystore`,
`*.apk`, `*.aab` ve `local.properties` Faz 1a'dan beri var; 16c'de yeniden
doğrulandı.

### Enstrümantasyon paketi release APK'ya karşı koşulamıyor

`testBuildType` ayarlanmadığı için varsayılan `debug`: Gradle'da yalnızca
`connectedDebugAndroidTest` var, `connectedReleaseAndroidTest` **yok**.
Release'e çevirmek iki şey ister ve ikisi de bu fazın dışında:

1. Uygulama ile test APK'sı **aynı anahtarla** imzalanmak zorunda, yani gerçek
   bir keystore (kullanıcıda).
2. `ui-test-manifest` `debugImplementation` ile bağlı; release varyantında
   Compose testlerinin çalışacağı test Activity'si olmaz.

Bu yüzden 16c'de release APK **elle** sürüldü (yöntem `TESTING.md`'de).

---

## 25. Android Auto Backup — Neyin Yedeklendiği

Telefon değiştiren kullanıcı verisini kaybetmemeli. İlk adım Android'in kendi
Auto Backup'ı: Room veritabanı ve DataStore tercihleri **kullanıcının kendi
Google Drive hesabına** kopyalanıyor, yeni cihazda uygulama kurulunca geri
geliyor. Sunucu yok, hesap yok, kod yok — yalnızca yapılandırma.

### Varsayılan zaten açıktı

`android:allowBackup` Android'de varsayılan olarak **açık**. Manifestte üç
öznitelik de (`allowBackup`, `fullBackupContent`, `dataExtractionRules`) Faz
1'den beri duruyordu, ama işaret ettikleri iki XML **Android Studio'nun
şablonuydu**: içleri yorum satırıydı, kural yoktu. Boş kural kümesi "platformun
almak istediği her şeyi al" demek.

Bu bir teori değil, ölçüm: 16f'de yerel taşıyıcıyla alınan yedek **85 KB**
uygulama verisini sandık dışına çıkardı. Yani "veriler cihazdan çıkmıyor"
cümlesi o anda **yanlıştı**. Gizlilik politikasının metni bu ölçüme göre
yazılacak (PROGRESS, Faz 16f).

### İki dosya, biri diğerinin yerine geçmiyor

| Dosya | Manifest özniteliği | Hangi sürüm okur |
|---|---|---|
| `res/xml/backup_rules.xml` | `android:fullBackupContent` | API 30 ve altı |
| `res/xml/data_extraction_rules.xml` | `android:dataExtractionRules` | API 31 ve üstü |

minSdk 24 olduğu için **ikisi de gerekli**. Platform yalnızca kendi sürümüne
uyanı okur, diğerini görmezden gelir; birini silmek o tarafta yapılandırmayı
yeniden varsayılana düşürür.

`data-extraction-rules` ayrıca iki bölüm ister ve ikisi de yazıldı:
`<cloud-backup>` (Drive'a yedek) ve `<device-transfer>` (eski telefondan yeni
telefona doğrudan aktarım). İçerikleri aynı — kullanıcının yazdığı her şey iki
yolda da taşınmalı.

### Neler giriyor

```
databases/          → subtrack.db + -wal + -shm   (abonelikler, anlık görüntüler)
files/datastore/    → settings.preferences_pb     (ana para birimi, kurlar, tema)
```

**Bir `<include>` yazmak kuralı beyaz listeye çevirir:** adı geçmeyen her şey
dışarıda kalır. Yarın eklenen bir dosya, biri bilerek karar verene kadar
yedeğe girmez. İstenen duruş bu.

### Veritabanı domain'i neden bütün olarak giriyor

`subtrack.db` tek başına değil, `domain="database" path="."` ile **tüm domain**
alınıyor. Sebebi Room'un WAL kipi: işlenmiş satırlar henüz ana dosyaya
katlanmamış olabilir. 16f'nin ölçülen fikstüründe ana dosya **4 KB**, WAL
**103 KB** idi — yani `subtrack.db` tek başına yedeklenseydi geri yükleme
neredeyse **boş bir veritabanı** verirdi. `-shm` SQLite'ın kendi yeniden
ürettiği türev bir indeks; asıl önemli çiftin yanında taşınması zararsız.

### Neler dışarıda ve neden

| Yol | Durum | Gerekçe |
|---|---|---|
| `no_backup/androidx.work.workdb` | Platform zaten almıyor | WorkManager veritabanını **kendisi** `no_backup/` altına koyuyor (`getNoBackupFilesDir()`). Kural yazmaya gerek yok: yeni cihaz kendi işini kendi kuruyor, eski telefonun iş satırlarını miras almıyor. 16f'de geri yükleme sonrası `no_backup/` **boş** geldi ve iş yeniden kuruldu (yeni uid, yeni job id). |
| `cache/`, `code_cache/` | Platform zaten almıyor | Auto Backup önbellek dizinlerini hiç taşımaz. |
| `files/profileInstalled` | Beyaz liste dışı | ProfileInstaller'ın kurulum işareti; yeni cihaza taşınmasının bir değeri yok. Ayrı bir `<exclude>` gerekmiyor, beyaz liste zaten dışarıda bırakıyor. |

### Geri yüklemenin taşımadığı tek şey: çalışma zamanı izni

API 33+ cihazda geri yükleme sonrası **tercih** geri geliyor ama
`POST_NOTIFICATIONS` izni gelmiyor — Android çalışma zamanı izinlerini hiçbir
zaman geri yüklemez. Ayarlar satırı bunu doğru bildiriyor ("sistem ayarlarından
açın"). API 32 ve altında böyle bir izin kavramı olmadığı için satır doğrudan
"Açık" geliyor. İkisi de beklenen davranış, hata değil.

---

## 26. App Bundle (AAB) ve Foreground Service Tipi

Faz 16g. Play Console APK değil **AAB** istiyor. Bu bölüm iki şeyi kayda
geçiriyor: bundle'ın ne taşıdığı ve Android 14+ için Play'in istediği
**foreground service tipi beyanının bu uygulamada neden gerekmediği**.

### Foreground service denetimi — dört bulgu

Play, Android 14+ hedefleyen uygulamalardan manifestte bildirilen **her
foreground service tipi** için Console'da beyan istiyor; bazı tipler tanıtım
videosu da gerektiriyor. Denetim varsayımla değil, **release birleşik
manifestinin satırlarıyla** yapıldı.

**1 — `FOREGROUND_SERVICE` ile başlayan izinler.** Birleşik manifestte
(`app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml:19`)
tek bir satır var:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

**Alt tip yok.** `FOREGROUND_SERVICE_DATA_SYNC`, `_SHORT_SERVICE` ve
kardeşlerinin hiçbiri manifestte geçmiyor. Yalnızca birleşik manifest değil,
**bağımlılıkların kendi manifestleri de** tarandı (`transforms/*/AndroidManifest.xml`):
hiçbirinde `FOREGROUND_SERVICE_` dizgesi yok.

**2 — `SystemForegroundService` elemanı.** Var, ama `foregroundServiceType`
özniteliği **yok** (satır 91-94):

```xml
<service
    android:name="androidx.work.impl.foreground.SystemForegroundService"
    android:directBootAware="false"
    android:enabled="@bool/enable_system_foreground_service_default"
    android:exported="false" />
```

Dört öznitelik: `name`, `directBootAware`, `enabled`, `exported`. Beşinci yok.
`foregroundServiceType` dizgesi birleşik manifestin **tamamında** geçmiyor;
AAB'nin base manifestinde de geçmiyor (`bundletool dump manifest`).

**3 — Nereden geliyor.** İkisi de tek bir kütüphaneden, manifest merger
raporunun söylediğine göre (`build/outputs/logs/manifest-merger-release-report.txt`):

| Eleman | Kaynak | Rapor satırı |
|---|---|---|
| `uses-permission#...FOREGROUND_SERVICE` | `androidx.work:work-runtime:2.11.2` | 365-368 (`AndroidManifest.xml:26:5-77`) |
| `service#...SystemForegroundService` | `androidx.work:work-runtime:2.11.2` | 383-396 (`AndroidManifest.xml:46:9-52:35`) |

Yani ikisi de **bizim yazdığımız bir şey değil**; WorkManager'ın AAR'ı
getiriyor. Uygulamanın kendi `src/main/AndroidManifest.xml`'inde tek bir
`FOREGROUND_SERVICE` satırı yok.

**4 — Kodda foreground service yolu hiç kullanılmıyor.** `app/src/` altında
arama:

| Aranan | Sonuç |
|---|---|
| `setForeground` | hit yok |
| `setExpedited` | hit yok |
| `ForegroundInfo` | hit yok |
| `OutOfQuotaPolicy` | hit yok |
| `OneTimeWorkRequest` | yalnızca `androidTest`'te iki satır, ikisi de düz `OneTimeWorkRequestBuilder<…>().build()` |

`PaymentReminderScheduler` tek bir iş kuruyor: kısıtsız, gecikmeli bir
`PeriodicWorkRequest`. Expedited iş yok, dolayısıyla WorkManager'ın
`SystemForegroundService`'i başlatacağı yol hiç tetiklenmiyor.

### Karar: beyan yazılmayacak, manifest değiştirilmeyecek

**Alt tip olmadığı için Görev 0(c) uygulanmadı** — `tools:node="remove"` ile
kaldırılacak bir şey yok. Üretim koduna ve manifeste dokunulmadı.

Alt tip bildirilmediğine göre Play Console'da foreground service tipi beyan
formunun **açılmaması bekleniyor**. Bu, manifest bulgularından çıkarılan bir
sonuç; Console ekranında **henüz doğrulanmadı** — ilk yükleme sırasında
görülecek.

**Temel `FOREGROUND_SERVICE` izni kaldırılmadı.** İki sebep: (a) 16g'nin
kapsamı denetim ve AAB'dir, izin budama değil; (b) izin WorkManager'ın kendi
AAR'ından geliyor ve kütüphanenin ileride expedited iş yolunu kullanması
hâlinde gereken izin odur. Kaldırmak bugün ölçülmemiş bir risk alır, bugün
ölçülmüş bir kazanç getirmez.

> **Android 14+ notu.** API 34'ten itibaren tip bildirmeden foreground service
> başlatmak `MissingForegroundServiceTypeException` atıyor. SubTrack hiç
> foreground service başlatmadığı için bu istisnanın yolu da açılmıyor —
> API 34 cihazda AAB'den kurulup sürülen tur (abonelik, toplam, istatistik,
> ayarlar, bildirim) çökmesiz geçti.

### AAB içeriği — ne taşıyor

`./gradlew :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`.
Üç ölçüm var ve karıştırılmamalı: 16g'de **4.598.466 B**, 16h'nin açılış ekranı
ve ikonlarıyla yeniden üretildiğinde **4.657.988 B**, Faz 16i'nin dil
filtresinden sonra **~4.575.35 KB**, yani yaklaşık **−82,6 KB**.

Son rakam **bayt bayt sabit değil**: 16i'de altı derleme ölçüldü ve sonuç
4.575.344 ile 4.575.353 arasında, ±5 bayt oynadı. Oynama içerikten gelmiyor —
iki derlemenin **148 girdisi de** boyut ve CRC olarak birebir eşleşti, fark
yalnızca imza bloğunun uzunluğunda. Bu yüzden bir turun AAB'si "şu kadar bayt
olmalı" diye doğrulanmaz; doğrulanacak şey imzanın parmak izi, sürüm ve
içeriktir. Her turun kendi ölçtüğü tam rakam `PROGRESS.md`'de. 16c'nin universal APK'sı
2.127.430 B idi; bundle hâlâ iki katından büyük ve bu beklenen durum: AAB bütün
ABI'leri ve yoğunlukları **bölünmemiş** taşıyor, Play kullanıcıya bunlardan
yalnızca cihaza uyanları gönderiyor. **Diller artık bölünmüyor** — 16i'den beri
iki dil de her cihaza gidiyor (§28).

| | İçerik |
|---|---|
| Modül | Tek `base` — dinamik özellik modülü yok |
| ABI | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` (iki `.so`: `libandroidx.graphics.path`, `libdatastore_shared_counter`) |
| Yoğunluk | `mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi` + `anydpi-v26` |
| Dil | Kaynak tablosunda **tek nitelikli locale: `tr`**, artı nitelikisiz varsayılan (İngilizce). 16i'den önce 86 idi |
| Base manifest | `versionCode=1`, `versionName=1.0`, `minSdk=24`, `targetSdk=36` |

**86 dil 16i'de ikiye indi.** Uygulamanın **kendi** metinleri her zaman iki
yapılandırmadaydı; 16i'den önce bunlar varsayılan (Türkçe) ve `en`, bugün
varsayılan (İngilizce) ve `tr`. Kalan 84 locale AndroidX ve Material'ın kendi
çevirileriydi (`string/autofill` gibi) ve `androidResources.localeFilters` ile
düştüler (§28). Ölçüm: `bundletool dump resources` çıktısında artık **tek**
`locale:` niteliği var, `tr`.

Play'in dil bölünmesine de artık başvurulmuyor. `bundletool dump config`
bunu doğruluyor:

```json
"splitsConfig": { "splitDimension": [ { "value": "LANGUAGE", "negate": true } ] }
```

`negate: true`, o boyutta bölme **yapılmayacak** demek; iki dil de `base`'de
gidiyor. Gerekçesi §28'de.

### Kurulabilirlik — APK testi bunun yerine geçmiyor

Play kullanıcıya AAB'den **türetilmiş bölünmüş APK'ları** gönderiyor, 16c'de
sürülen universal APK'yı değil. Bu yüzden tur `bundletool` ile üretilen APK
setiyle, iki cihazda ayrıca sürüldü:

```
bundletool build-apks --bundle=app-release.aab --output=subtrack.apks --ks=…
bundletool install-apks --apks=subtrack.apks --device-id=…
```

16g'de her iki cihaza da **üç** parça kurulmuştu:

| Cihaz | `pm path` çıktısı (16g) |
|---|---|
| `subtrack_min_api24` | `base.apk`, `split_config.en.apk`, `split_config.x86_64.apk` |
| `subtrack_wide_api34` | `base.apk`, `split_config.en.apk`, `split_config.x86_64.apk` |

**16i'den sonra iki parça bekleniyor**: `base.apk` + `split_config.<abi>.apk`.
Dil parçası üretilmiyor, yani `split_config.en.apk` satırı düşüyor. Bu turun
cihaz cihaz `pm path` çıktıları `PROGRESS.md`'deki 16i girdisinde.

> **`dumpsys package` API 34'te `minSdk=32` diyor.** Bu bir çelişki değil:
> bundletool SDK'ya göre birkaç **varyant** üretiyor (`base-*_2`, `_3`
> son ekleri) ve API 34 cihaza 16 KB hizalama gibi yeni optimizasyonları
> taşıyan varyant gidiyor. Bundle'ın kendi base manifesti `minSdk=24` diyor ve
> API 24 cihaz gerçekten kurulup çalıştı — teslim doğru varyantı seçiyor.

---

## 27. Uygulama İkonu — On İki Para Halkası

Faz 16d. Şablon ikonu (yeşil kare + Android robotu) gitti; yerine 14a'nın
paletinden gelen marka işareti kondu: **koyu zümrüt zemin üzerinde halka
biçiminde dizilmiş on iki altın para.** Halka aylık döngüyü, on iki parça yılın
aylarını, paraların örtüşmesi hem birikimi hem pul dokusunu anlatıyor.

Bu bölüm beş kararı kayda geçiriyor: geometrinin ne olduğu, **neden bir kez
küçültüldüğü**, ikonun neden **temaya uymadığı**, paraların arasındaki ayrımın
neden **boya değil boşluk** olduğu ve bildirim ikonunun neden **ayrı bir
çizim** olduğu.

### Geometri — tek tanım, 220 birimlik tuval

İşaret bir kere, 220 birimlik kare bir tuvalde tanımlanıyor; bütün hedefler
oradan ölçekleniyor. Ölçek çarpanı 108dp tuval için `k = 108/220 = 0,490909`.

İşaret ayrıca **tek bir `SCALE` çarpanıyla** büyütülüp küçültülebiliyor;
`SCALE = 0,88` (aşağıda "İşaret neden küçültüldü"). Halka, para ve ayrım aynı
çarpanla ölçeklendiği için oranları hiçbir zaman ayrışamıyor. **Merkez, para
sayısı, açı aralığı ve renkler ölçeğin dışında** — onlar sabit.

| Değer | 220 birim | 108dp viewport | *ilk hâl (SCALE = 1)* |
|---|---|---|---|
| Merkez | 110 | **54** | *110 · 54* |
| Para yarıçapı | 13,20 | **6,4800** | *15 · 7,3636* |
| Paranın merkeze uzaklığı | 45,76 | **22,4640** | *52 · 25,5273* |
| Paralar arası ayrım | 3,52 | **1,7280** | *4 · 1,9636* |
| Komşu kesme yarıçapı | 13,60356 | **6,6781** | *15,45859 · 7,58876* |
| İşaretin dış sınırı | 58,96 | **28,9440** | *67 · 32,8909* |
| Ayrım hattıyla | 60,72 | **29,8080** | *69 · 33,8727* |
| Güvenli alan sınırı | 73 | **35,8364** | *(ölçeklenmez)* |

**Paralar örtüşüyor, ve bu kasıtlı.** İki komşu paranın merkezleri arasındaki
uzaklık `2 × 45,76 × sin(15°) = 23,6871` birim; iki para yarıçapı ise 26,40.
Yani paralar **2,7129 birim** iç içe geçiyor (ilk hâlde 3,0828). Örtüşme
işaretin kendisi — birikim ve pul dokusu oradan geliyor; ölçek değişse de
örtüşmenin işarete oranı aynı kalıyor.

Her para, kendi diskinden komşularının açtığı iki ısırığın çıkarılmasıyla
çiziliyor. Isırık yarıçapı `(pitch + ayrım) / 2 = 13,60356` seçilerek iki
paranın görünen kenarları arasında **tam olarak 3,52 birim** zemin
bırakılıyor, üstelik iki tarafa da simetrik. Sonuç dört yaylı kapalı bir yol:
paranın kendi çemberinden iki yay, komşuların kesme çemberlerinden iki yay.

### Güvenli alan — hesap

Adaptive icon 108dp tuvalde çizilir, başlatıcı ortadaki **72dp**'yi maskeler.
Material'ın anahtar çizgisi bundan daha dar: içteki **66dp çaplı daire**.

```
72dp maske  -> 36dp yarıçap -> 36 / 0,490909 = 73,33 birim
66dp anahtar -> 33dp yarıçap -> 33 / 0,490909 = 67,22 birim
```

İşaretin dış sınırı **58,96 birim**, yani 66dp anahtar dairesine **8,26 birim**
payla giriyor. 108dp karşılığıyla: işaret **57,89dp** çapında; 66dp daireye
**8,11dp**, 72dp maskeye **14,11dp** pay kalıyor. Hiçbir maske — daire,
squircle veya yuvarlatılmış kare — işareti kesemiyor.

### İşaret neden küçültüldü — `SCALE = 0,88`

İlk hâlde (`SCALE = 1`) işaret **65,78dp** çapındaydı ve 66dp anahtar
dairesine **0,218dp** payla giriyordu. Aritmetik olarak sığıyordu ve cihazda
da kesilmiyordu — api34'te 2,06dp, api36'da 2,59dp pay ölçüldü. **Ama sıkışık
duruyordu:** işaret maskenin kenarına yaslanmış, etrafında nefes alacak zemin
kalmamıştı. Sığmak ile iyi oturmak aynı şey değil.

Bütün geometri bu yüzden merkez sabit kalacak şekilde **%88** ölçeklendi.
Sonuç, üç cihazda ölçülmüş hâliyle:

| | api24 | api34 | api36 |
|---|---|---|---|
| İşaret, ilk hâl | 52,65dp | 47,30dp | 55,01dp |
| İşaret, %88 sonrası | **46,33dp** | **41,55dp** | **48,62dp** |
| Maskeye pay, ilk hâl | *(maske yok)* | 2,06dp | 2,59dp |
| Maskeye pay, şimdi | *(maske yok)* | **4,94dp** | **5,79dp** |
| İşaret / karo | 0,878 → **0,772** | 0,920 → **0,808** | 0,914 → **0,808** |

Ölçek tek bir sabit olduğu için bir daha ayarlanması gerekirse tek satır
değişiyor ve on iki paranın hiçbiri orantısını kaybetmiyor. **Bildirim ikonu
bu ölçeğin dışında** — gerekçesi aşağıda.

### İkon temayı takip ETMİYOR

Uygulamanın her yeri `MaterialTheme.colorScheme`'den renk alıyor ve açık/koyu
şemada roller yer değiştiriyor (§12). İkon bunu **yapamaz ve yapmamalı**:

- **Yapamaz.** İkonu başlatıcı, durum çubuğu ve Play çiziyor; hiçbirinin
  soracağı bir Compose teması yok.
- **Yapmamalı.** Marka işareti sabittir. Açık temada da aynı koyu zümrüt zemin
  kalıyor.

Altın zemin üstünde **8,50:1** — grafik nesne için gereken 3:1'in çok üstünde.
(Aynı altın beyaz üstünde 2,10:1, yani §12'nin "altın mürekkep olamaz" kuralı
burada da geçerli; ikonda altın mürekkep değil, koyu zemin üstünde duran
**dolgu**.)

### Renkler `colors.xml`'de, `Color.kt`'de değil

İki marka değeri yeni bir `app/src/main/res/values/colors.xml` dosyasında
duruyor; vektörler onlara `@color/ic_launcher_ground` ve
`@color/ic_launcher_coin` diye başvuruyor. Hardcoded renk yasağı vektör
drawable'lar için de geçerli ve karşılığı budur: hex değeri **bir kez**, kaynak
tablosunda.

`Color.kt`'ye sabit **eklenmedi.** Değerler `EmeraldNight` ve `GoldBright` ile
birebir aynı, ama bu çoğaltma bilinçli ve tek yönlü: ileride bir tema rengi
ayarlanırken uygulamanın işareti sessizce değişmemeli, ve işaret de temayı
kısıtlamamalı. `Color.kt` Compose temasının paleti; ikon o sistemin dışında.

### Ayrım boya değil, boşluk — monochrome bu yüzden çalışıyor

Paraların arasındaki 4 birimlik ayrımı zemin renginde bir hat olarak boyamak
ön planda çalışırdı. **Monochrome katmanında çalışmazdı.** Android 13+ temalı
ikonlarda sistem katmanın yalnızca **alfa kanalını** alıp tek renge boyuyor;
boyanmış bir ayrım da paralarla aynı rengi alır ve halka düz bir diske döner.

Bu yüzden ısırıklar gerçek delik: ayrım yerinde hiçbir şey çizilmiyor.
Aynı yol verisi hem ön planda hem monochrome'da kullanılabiliyor.
`ic_launcher_monochrome.xml` ayrı bir dosya (tek düz renk, brifingin istediği
gibi) ama aynı üreticiden çıkıyor, yani ikisi ayrışamaz.

### Bildirim ikonu neden ayrı bir çizim

Durum çubuğu ikonu uygulama ikonunun küçültülmüş hâli **değil**. İşareti 24dp
tuvale olduğu gibi indirince 4 birimlik ayrım **0,657dp**'ye düşüyor:

| Yoğunluk | Ayrım | Para çapı |
|---|---|---|
| mdpi (1×) | 0,66 px | 4,93 px |
| hdpi (1,5×) | 0,99 px | 7,39 px |
| xhdpi (2×) | 1,31 px | 9,85 px |
| 420dpi (2,625×) | 1,72 px | 12,93 px |

xhdpi altında ayrım kapanıyor; gerçek piksellerde denendi ve halka gri bir
simide dönüşüyor. İki çıkış yolu vardı — **ayrımı kalınlaştırmak** veya **para
sayısını azaltmak.** Kalınlaştırmak seçildi: **on iki, işaretin anlamının
kendisi** (ayda bir para). Altıya inmek dokuyu korur, anlamı kaybederdi.

Bildirim ikonu bu yüzden on iki parayı koruyup ayrımı yaklaşık **iki katına**
(1,3dp) çıkarıyor ve bedelini işareti 24dp tuvalde **22dp'lik canlı alana**
çizerek ödüyor:

| Değer | 24dp viewport |
|---|---|
| Merkez | 12 |
| Paranın merkeze uzaklığı | 8,54 |
| Para yarıçapı | 2,46 |
| Ayrım | 1,3 |
| İşaret yarıçapı | 11,0 |

Isırık genişlediği için paralar hafifçe badem biçimi alıyor; bu boyutta
görünmüyor. Ölçümler `TESTING.md`'de, cihaz görüntüleri
`docs/screenshots/phase-16d/` altında.

**Bu değerler `SCALE`'den etkilenmiyor ve etkilenmemeli.** Uygulama ikonu
küçültüldüğünde (`SCALE = 0,88`) bildirim ikonuna dokunulmadı: o işaret zaten
24dp'lik bir tuvalde ve zaten sıkışık boyutlarda çalışıyor, ayrımı da tam
bunun için kalınlaştırılmıştı. Aynı çarpanı ona da uygulamak, kazanılmış
1,3dp'lik ayrımı 1,14dp'ye indirip ölçümün cevabını bozardı. Üreticide bu
yüzden bildirim değerleri dp cinsinden **ayrı sabitler**, halka değerlerinden
türetilmiyor.

### Raster varlıklar ve üretici

API 26 altında adaptive icon yok, başlatıcı `mipmap-*/ic_launcher.png` ve
`ic_launcher_round.png` dosyalarını olduğu gibi çiziyor — maske de
uygulanmıyor, yuvarlatma dosyanın içinde olmak zorunda. Kare karo
`0,1875 × kenar` köşe yarıçapıyla, yuvarlak karo daire olarak üretiliyor.

Bu karolarda işaret karonun **%77,6'sını** kaplıyor (ilk hâlde %88'di). Sayı
keyfi değil, hesaplanıyor: adaptive ikonda işaret 72dp maskenin 57,89dp'sini,
yani **%80,4'ünü** dolduruyor; maskesiz karoda aynı görünürlüğü tutturmak için
gereken oran bu, üstüne karoyu biraz içeride tutan 0,965'lik bir pay. Üretici
oranı `MARK_FRACTION = 0,965 × MASKED_FILL` diye türetiyor, yani `SCALE`
değişince kendiliğinden güncelleniyor — elle ayarlanacak bir sayı değil.
Play'in 512×512 karosu da aynı oranı kullanıyor (396,5 px işaret, 512 px
karo), böylece üç yüzey aynı görünüyor.

Bütün varlıklar `tools/icon/generate_icons.py` tarafından tek tanımdan
üretiliyor: vektörler, beş yoğunlukta PNG ve mağaza karosu. Elle düzenlenen
dosya yok — vektörlerin başında da bunu söyleyen bir yorum var.

---

## 28. Yerelleştirme — Varsayılan Dil, Dil Filtresi ve Dil Parçası

Faz 16i'de kuruldu. Bu bölüm **hangi cihazda hangi dilin çıktığını** anlatır;
metnin kendisi değil, metnin seçilme yolu.

### Android dili nasıl seçer

Kaynak çözümlemesi Android 7.0'dan (API 24, yani `minSdk`'miz) beri cihazın
**dil listesini sırayla** dener. Liste `[tr, en]` ise önce `values-tr`, sonra
`values-en` aranır. **Listedeki hiçbir dile uyan klasör yoksa nitelikisiz
`values/` kullanılır** — varsayılan bir yedek değil, son duraktır.

Bu, "varsayılan klasöre hangi dili koyduğun" sorusunu bir ürün kararı yapar:
oraya koyduğun dil, **uygulamanın konuşmadığı her dili konuşan herkese**
gösterilecek dildir.

### 16i'den önce ne yanlıştı

Türkçe metinler `values/` içindeydi, İngilizce `values-en/` içinde. İki ayrı
sonuç doğuruyordu ve ikisi de yayın engeliydi:

| Cihazın dil listesi | Eşleşen klasör | Çıkan dil | Doğrusu |
|---|---|---|---|
| `[de]`, `[pt]`, `[id]`, `[zh]` … | yok → varsayılan | **Türkçe** | İngilizce |
| `[tr]` | yok → varsayılan | Türkçe | Türkçe ✓ |
| `[tr, en]` | `values-en` | **İngilizce** | Türkçe |
| `[en]` | `values-en` | İngilizce | İngilizce ✓ |

Birinci satır: `tr` de `en` de listesinde olmayan **her** cihaz uygulamayı
Türkçe görüyordu. Uygulama tüm ülkelerde yayınlanacak, yani bu azınlık bir
durum değil, çoğunluk.

Üçüncü satır aynı mekanizmanın ters yüzü: Türkçe konuşan ama listesinde
İngilizce de bulunan bir kullanıcıya İngilizce gidiyordu, çünkü `tr` için
klasör yoktu ve sıradaki `en` eşleşiyordu. 16g'deki "cihaz İngilizce,
uygulama Türkçe" gözlemi de buydu.

**Bu AAB'ye özgü bir sorun değildi.** Aynı davranış APK'da da vardı; kaynak
çözümlemesi paketleme biçimine bakmaz.

### Karar: varsayılan İngilizce

`values/` artık İngilizce, `values-tr/` Türkçe. `values-en/` kaldırıldı —
İngilizce nitelikli klasörde tutulsaydı iki yerde dururdu ve ikisi ayrışabilirdi.

Metin içeriği taşınırken **değişmedi**: iki dosya bütün hâlinde yer değiştirdi,
git ikisini de %100 yeniden adlandırma olarak kaydetti. Tek ekleme, Türkçe
çoğullardaki üç `one` girdisidir (aşağıda).

Sonuç, aynı tablonun düzelmiş hâli:

| Cihazın dil listesi | Eşleşen klasör | Çıkan dil |
|---|---|---|
| `[tr]` | `values-tr` | Türkçe |
| `[tr, en]` | `values-tr` | Türkçe |
| `[en]` | yok → varsayılan | İngilizce |
| `[de]` | yok → varsayılan | İngilizce |
| `[de, tr]` | `values-tr` | Türkçe |

Beşinci satır listenin **sırayla** denendiğini gösteriyor: `de` için klasör
yok, sıradaki `tr` eşleşiyor.

### Kütüphane dilleri `en` ve `tr` ile sınırlı

```kotlin
androidResources {
    localeFilters += listOf("en", "tr")
}
```

Uygulama iki dilli, bağımlılıkları değil. Material3'ün tarih seçicisi ve
altındaki AndroidX kütüphaneleri Google'ın desteklediği her dil için çeviri
taşıyor. Filtre olmadan Almanca bir cihazda **İngilizce bir uygulamanın içinde
Almanca bir tarih seçicisi** çıkıyordu — uygulamanın konuşmadığını iddia ettiği
bir dilde karma arayüz.

**DSL seçimi.** Eski yazılış `defaultConfig.resourceConfigurations` ve
`resConfigs()`; AGP 9'da ikisi de `@Deprecated` ve mesajları doğrudan
`androidResources.localeFilters`'a yönlendiriyor. CLAUDE.md §4 deprecated API
yasakladığı için yeni yazılış kullanıldı.

### Dil parçası kapalı — her iki dil de `base`'de

```kotlin
bundle { language { enableSplit = false } }
```

Filtreden sonra bölünecek şey yalnızca **kendi** Türkçe metinlerimiz kalıyor,
onlarca kilobayt. Karşılığında dil parçasının bir maliyeti var: parça,
cihazın **kurulum anındaki** diline göre gönderiliyor. Türkçeyi sonradan ekleyen
bir kullanıcı, Play ek parçayı indirene kadar İngilizce görmeye devam ediyor.
İki dil de `base`'de olunca cihazın kendi kaynak çözümlemesi ayar değişir
değişmez doğru cevabı veriyor ve indirilecek bir şey kalmıyor.

Bu, §26'daki "üç parça" tablosunu **iki parçaya** indiriyor: `base.apk` +
`split_config.<abi>.apk`. Dil parçası artık üretilmiyor ve bu, kapatmanın
kanıtıdır.

### `localeConfig` v1.0'da yok

`localeConfig` (ve AGP'nin `generateLocaleConfig`'i) kullanıcıya sistem
ayarlarından **uygulamaya özel dil** seçtiren şeydir. Bu bir **özellik**, ve
16i bir düzeltme turu. Düzeltmeden sonra kullanıcı zaten cihaz diline göre
doğru dili alıyor; uygulamanın dilini cihazınkinden ayırmak isteyen kullanıcı
ayrı bir faza kalıyor.

Pratik sonucu: bugün uygulamanın dilini kullanıcıya ulaşan hiçbir yol
değiştiremez, tek girdi cihazın dil listesidir.

### Metnin dili ile biçimin dili ayrışabilir — ve bu doğrudur

İki ayrı mekanizma var ve karıştırılmamalı:

- **Metin** kaynaklardan gelir: yukarıdaki tabloya göre `values/` ya da
  `values-tr/`.
- **Ay ve gün adları, tarih kalıbı, sayı ve para biçimi** ise
  `LocalConfiguration.current.locales[0]`, yani cihazın **birinci** diline göre
  biçimlenir (`MoneyFormatter`, `MonthFormatter`, `rememberDateFormatter`).

Ama `locales[0]` cihazın dil listesinin birincisi **değil**: Android uygulamaya
verdiği Configuration'ın dil listesini, uygulamanın gerçekten kaynağı olan
dillere göre süzüyor. Kural 16i'de ölçüldü:

- Listede uygulamanın bildiği bir dil **varsa**, `locales[0]` o dil olur —
  metin de biçim de onunla gelir.
- Listede uygulamanın bildiği bir dil **yoksa**, `locales[0]` cihazın kendi
  birinci dili olarak kalır — metin varsayılandan (İngilizce) gelir, biçim
  o yabancı dile göre kurulur.

Ölçülen üç hâl:

| Cihazın listesi | Metin | Biçim | Ekranda |
|---|---|---|---|
| `[de]` | İngilizce | **Almanca** | `Total Monthly, 0,00 ₺`; seçicide gün harfleri `M D M D F S S`, hafta pazartesi başlıyor |
| `[de, tr]` | Türkçe | **Türkçe** | `Aylık Toplam, ₺0,00`; seçicide `Eylül 2026`, gün harfleri `P S Ç P C C P` |
| `[ar]` | İngilizce | **Arapça** | `Total Monthly, ٠٫٠٠ ₺`; ay adı `سبتمبر ٢٠٢٦`, rakamlar Arap-Hint |

Yani metin ile biçim yalnızca uygulamanın **tanımadığı** bir dilde ayrışıyor.
`[de, tr]` gibi eşleşen bir dil bulunan listede ikisi de Türkçe olur.

Tarih seçicinin **kendi** başlık ve düğme metinleri kaynaklardan gelir, yani
dil filtresine tabidir: `[de]` ve `[ar]` cihazlarda "Select date" / "Selected
date" diye İngilizce çıkar, takvimin ay ve gün adları ise o cihazın dilinde.
Bu ayrım filtrenin çalıştığının en görünür kanıtıdır — 16i'den önce `[de]`
cihazda bu diyalog baştan sona Almancaydı.

### Sağdan sola diller — `supportsRtl` kapalı

**Ölçüm (16i).** Manifestte `android:supportsRtl="true"` olduğu sürece `[ar]`
gibi bir listede düzen **aynalanıyordu**: config `ldrtl` diyor, başlık sağa,
ikonlar sola, FAB sol alta geçiyor, kategori çipleri ters sırada diziliyor.
Metin İngilizce kalıyor, çünkü Arapça kaynak yok — yani ortaya **soldan sağa
yazılmış metnin sağdan sola dizilmiş bir düzende** durduğu bir ekran çıkıyor.

**Karar (16j): `android:supportsRtl="false"`.** Uygulamanın kaynağı olan iki
dil de (`en`, `tr`) soldan sağa yazılıyor, ve aynalanmış düzen **hiç
denenmedi**. İki yer özellikle riskli:

- **Kaydırarak silme kendi bileşenimiz** (§12). Jestin yönü koda gömülü;
  aynalanmış düzende "sona doğru" kaydırmanın ne anlama geldiği ölçülmedi.
- **Kategori dağılımı ve aylık trend `Canvas` ile çiziliyor** (§20, §21).
  Çubuklar ve sütunlar bir eksen boyunca elle yerleştiriliyor; o eksenin
  aynalandığında ne yaptığı ölçülmedi.

Hiç çalıştırılmamış bir düzeni kullanıcıya göndermek, aynalamamaktan daha kötü
bir bahis. `false` ile sağdan sola dil kullanan bir kullanıcı İngilizce metni
**tanıdık soldan sağa düzende** görüyor; kaybettiği şey yalnızca aynalama.

**Bu bir erteleme, RTL'e karşı bir karar değil.** Sağdan sola bir çeviri
eklendiği gün öznitelik `true`'ya döner ve düzen **o dille birlikte** tasarlanıp
ölçülür. Tek başına `true` yapmak bugün olduğu gibi yine çevirisiz bir aynalama
üretir.

**Biçim etkilenmiyor.** `supportsRtl` yalnızca düzen yönünü kapatır; ay ve gün
adları, rakamlar ve para biçimi cihazın diline göre gelmeye devam eder. `[ar]`
cihazda tarih seçicide Arapça ay adı ve Arap-Hint rakamları görmek **beklenen**
davranıştır, aynalama kalkmış olsa bile.

### Türkçe çoğullarda `one` neden var ve neden `other` ile aynı

Türkçede sayıdan sonra isim tekil kalır — "1 gün", "5 gün" — yani iki çoğul
biçim **aynı sözcüklerdir**. Buna rağmen CLDR `tr` diline bir `one` kategorisi
verir, ve lint bunu ancak metinler `values-tr/` gibi dilini bildiği bir
klasöre girdikten sonra arar (nitelikisiz `values/` içindeyken hangi dil
olduğunu bilemediği için sormuyordu).

Üç Türkçe `plurals` girdisinin `one` ve `other` değerleri bu yüzden **bayt
bayt aynı**. Çalışma zamanı davranışı değişmedi: Android eksik bir niceliği
zaten `other`'a düşürüyordu. Kazanç, denetimin açık kalması — ileride eklenecek
bir Türkçe çoğul, eksik nicelikle sessizce geçmez.

`tools:ignore` ile susturma yolu bilerek kullanılmadı: projede `@SuppressLint`
yasak ve `tools:ignore` onun XML karşılığıdır.
