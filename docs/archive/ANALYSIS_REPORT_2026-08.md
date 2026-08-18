# SubTrack — Kod Tabanı Analiz Raporu

Tarih: 2026-08-18
Branch: `Elina` | HEAD: `d06cc5e`
Yöntem: Tüm kaynak dosyalar okundu, `./gradlew assembleDebug` ve `./gradlew :app:compileDebugKotlin --rerun-tasks` çalıştırıldı.

---

## 1. Proje Yapısı

```
SubTrack/
├── ANALYSIS_REPORT.md            (bu dosya)
├── build.gradle.kts              (root, 5 satır)
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
├── gradle/
│   ├── libs.versions.toml
│   ├── gradle-daemon-jvm.properties
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/elinacn/subtrack/
        │   │   ├── MainActivity.kt
        │   │   └── ui/theme/
        │   │       ├── Color.kt
        │   │       ├── Theme.kt
        │   │       └── Type.kt
        │   └── res/
        │       ├── drawable/            (ic_launcher_background, ic_launcher_foreground)
        │       ├── mipmap-*/            (launcher ikonları, 6 yoğunluk + anydpi-v26)
        │       ├── values/              (colors.xml, strings.xml, themes.xml)
        │       ├── values-en/           (strings.xml)
        │       └── xml/                 (backup_rules.xml, data_extraction_rules.xml)
        ├── test/java/com/elinacn/subtrack/
        │   └── ExampleUnitTest.kt
        └── androidTest/java/com/elinacn/subtrack/
            └── ExampleInstrumentedTest.kt
```

Toplam **6 Kotlin dosyası, 409 satır**. Bunun 4'ü üretim kodu (370 satır), 2'si Android Studio şablon testi (39 satır).

| Dosya | Satır | Görevi |
|---|---:|---|
| `app/src/main/java/com/elinacn/subtrack/MainActivity.kt` | 291 | Activity, veri modeli, ekranın tamamı ve tüm composable'lar — projenin neredeyse tüm kodu tek dosyada. |
| `app/src/main/java/com/elinacn/subtrack/ui/theme/Theme.kt` | 36 | `SubTrackTheme` wrapper'ı ve açık/koyu `ColorScheme` tanımları. |
| `app/src/main/java/com/elinacn/subtrack/ui/theme/Type.kt` | 34 | `Typography` nesnesi tanımlar — **hiçbir yerde kullanılmıyor** (bkz. §5). |
| `app/src/main/java/com/elinacn/subtrack/ui/theme/Color.kt` | 9 | 4 adet pastel renk sabiti (`PastelBlue`, `PastelMint`, `PastelGray`, `DarkText`). |
| `app/src/test/java/com/elinacn/subtrack/ExampleUnitTest.kt` | 16 | Şablon testi: `assertEquals(4, 2 + 2)`. Uygulama kodunu test etmiyor. |
| `app/src/androidTest/java/com/elinacn/subtrack/ExampleInstrumentedTest.kt` | 23 | Şablon testi: paket adının `com.elinacn.subtrack` olduğunu doğrular. |

`MainActivity.kt` içindeki üst düzey bildirimler:

| Bildirim | Satır | Not |
|---|---|---|
| `data class Subscription(id: Int, name: String, price: Double)` | 33–37 | Tek veri modeli. Room entity'si **değil**, düz data class. |
| `class MainActivity : ComponentActivity` | 39–48 | Tek Activity. |
| `fun MainScreen()` | 52–219 | **168 satır** — ekran, state, liste, swipe-to-dismiss ve bottom sheet formunun tamamı. |
| `fun getIconForSubscription(name)` | 223–232 | Abonelik adına göre ikon seçer. |
| `fun DashboardCard(totalAmount)` | 234–259 | Üstteki toplam tutar kartı. |
| `fun SubscriptionCard(name, price)` | 261–285 | Tek abonelik satırı. |
| `fun SubTrackPreview()` | 287–291 | `@Preview`. |

---

## 2. Teknik Konfigürasyon

### Sürümler

| Bileşen | Sürüm | Kaynak |
|---|---|---|
| Android Gradle Plugin (AGP) | **9.0.1** | `gradle/libs.versions.toml:2` |
| Kotlin (compose plugin sürümü) | **2.0.21** | `gradle/libs.versions.toml:9` |
| Compose BOM | **2024.09.00** | `gradle/libs.versions.toml:10` |
| Gradle Wrapper | **9.2.1** | `gradle/wrapper/gradle-wrapper.properties:5` |
| Gradle Daemon JDK (toolchain) | **21** (foojay ile otomatik indiriliyor) | `gradle/gradle-daemon-jvm.properties` |

### SDK ve JVM

| Ayar | Değer | Kaynak |
|---|---|---|
| `compileSdk` | **36** (`minorApiLevel = 1` → 36.1, AGP 9'un yeni DSL'i) | `app/build.gradle.kts:8-12` |
| `minSdk` | **24** (Android 7.0) | `app/build.gradle.kts:16` |
| `targetSdk` | **36** | `app/build.gradle.kts:17` |
| `sourceCompatibility` / `targetCompatibility` | **JavaVersion.VERSION_11** | `app/build.gradle.kts:34-35` |
| Kotlin `jvmTarget` | **Açıkça ayarlanmamış** | — |

`kotlinOptions { jvmTarget }` veya `kotlin { jvmToolchain() }` bloğu yok; Kotlin JVM hedefi AGP'nin `compileOptions`'tan türettiği değere bırakılmış. Şu an derleme geçiyor, ama IDE `.idea/compiler.xml`'de bytecode hedefi 21, Gradle toolchain'i 21, `compileOptions` ise 11 — üç ayrı yerde üç farklı rakam var (bkz. §5).

### Version Catalog

**Kullanılıyor.** `gradle/libs.versions.toml` mevcut ve 15 bağımlılığın 14'ü `libs.*` alias'ı ile referans veriliyor.

**Tek istisna:** `app/build.gradle.kts:58` —
```kotlin
implementation("androidx.compose.material:material-icons-extended")
```
Doğrudan string literal olarak yazılmış, catalog'a eklenmemiş. Sürüm belirtilmemiş, Compose BOM'dan geliyor.

### Tüm Bağımlılıklar

| Konfigürasyon | Bağımlılık | Sürüm |
|---|---|---|
| `implementation` | `androidx.core:core-ktx` | 1.17.0 |
| `implementation` | `androidx.lifecycle:lifecycle-runtime-ktx` | 2.10.0 |
| `implementation` | `androidx.activity:activity-compose` | 1.12.4 |
| `implementation` | `platform(androidx.compose:compose-bom)` | 2024.09.00 |
| `implementation` | `androidx.compose.ui:ui` | BOM |
| `implementation` | `androidx.compose.ui:ui-graphics` | BOM |
| `implementation` | `androidx.compose.ui:ui-tooling-preview` | BOM |
| `implementation` | `androidx.compose.material3:material3` | BOM |
| `implementation` | `androidx.compose.material:material-icons-extended` | BOM (catalog dışı) |
| `testImplementation` | `junit:junit` | 4.13.2 |
| `androidTestImplementation` | `androidx.test.ext:junit` | 1.3.0 |
| `androidTestImplementation` | `androidx.test.espresso:espresso-core` | 3.7.0 |
| `androidTestImplementation` | `platform(compose-bom)` | 2024.09.00 |
| `androidTestImplementation` | `androidx.compose.ui:ui-test-junit4` | BOM |
| `debugImplementation` | `androidx.compose.ui:ui-tooling` | BOM |
| `debugImplementation` | `androidx.compose.ui:ui-test-manifest` | BOM |

Room, Navigation, ViewModel (`lifecycle-viewmodel-compose`), Hilt, Koin, Retrofit, DataStore — **hiçbiri yok**.

### Uygulanan Gradle Plugin'leri

```
com.android.application            (9.0.1)
org.jetbrains.kotlin.plugin.compose (2.0.21)
```

`org.jetbrains.kotlin.android` plugin'i **uygulanmamış** — buna rağmen Kotlin derleniyor. AGP 9'un Kotlin desteğini yerleşik olarak sağladığı anlaşılıyor (`:app:compileDebugKotlin` görevi mevcut ve başarıyla çalışıyor). *Bu mekanizmanın tam olarak nasıl çalıştığından emin değilim*, ama pratikte sorun çıkarmıyor.

### kapt mı KSP mi?

**İkisi de yok.** Projede hiçbir annotation processor kullanılmıyor — ne `kapt`, ne `com.google.devtools.ksp`. Room eklendiğinde **KSP** eklenmesi gerekecek (kapt Kotlin 2.x'te bakım modunda).

---

## 3. Mimari Durum

### State Nerede Tutuluyor

**Tamamen composable içinde.** ViewModel yok.

`MainActivity.kt:53-72` — `MainScreen()` composable'ının gövdesinde 5 ayrı state:

```kotlin
var showBottomSheet by remember { mutableStateOf(false) }        // satır 54
var subscriptionName by remember { mutableStateOf("") }          // satır 55
var subscriptionPrice by remember { mutableStateOf("") }         // satır 56
val sheetState = rememberModalBottomSheetState()                 // satır 57
val subscriptionList = remember { mutableStateListOf(...) }      // satır 60-65
```

Kritik nokta: `remember` kullanılmış, `rememberSaveable` **değil**. Ayrıca kalıcı depolama yok. Sonuç: **ekran döndürmede ve process death'te tüm kullanıcı verisi kayboluyor**, liste satır 62-63'teki hardcoded Netflix/Spotify'a geri dönüyor.

### ViewModel / Repository / DI

| Katman | Durum |
|---|---|
| ViewModel | **Yok** — tek bir `ViewModel` alt sınıfı bile yok |
| Repository | **Yok** |
| UseCase / Domain katmanı | **Yok** |
| DI (Hilt / Koin / manuel) | **Yok** — hiçbir DI kütüphanesi bağımlılıklarda geçmiyor |
| `Application` alt sınıfı | **Yok** — `AndroidManifest.xml`'de `android:name` yok |

Mimari fiilen **tek katmanlı**: UI doğrudan kendi belleğindeki listeyi tutuyor ve mutasyona uğratıyor.

### Navigation

**Kullanılmıyor.** `androidx.navigation:navigation-compose` bağımlılığı yok, `NavHost` / `NavController` / `composable(route=...)` çağrısı yok.

**Ekran sayısı: 1** (`MainScreen`). İkinci bir "ekran" gibi davranan `ModalBottomSheet` var (satır 158-218) ama o da aynı composable'ın içinde, koşullu render ile.

### Composable Organizasyonu

**Tek dosyada, ayrılmamış.** 5 composable'ın tamamı + veri modeli + Activity `MainActivity.kt` içinde (291 satır). Sadece tema dosyaları (`ui/theme/`) ayrılmış.

`MainScreen()` tek başına 168 satır ve şunların hepsinden sorumlu: state sahipliği, toplam hesaplama, Scaffold/FAB, LazyColumn, swipe-to-dismiss davranışı, bottom sheet formu, form doğrulama ve liste mutasyonu.

### Tema / Renk Tanımları

| Dosya | İçerik | Kullanım durumu |
|---|---|---|
| `ui/theme/Color.kt` | `PastelBlue`, `PastelMint`, `PastelGray`, `DarkText` | `MainActivity`'de doğrudan kullanılıyor |
| `ui/theme/Theme.kt` | `SubTrackTheme`, `LightColorScheme`, `DarkColorScheme` | `SubTrackTheme` sarmalayıcı olarak kullanılıyor, ama **renk şeması UI'a hiç ulaşmıyor** |
| `ui/theme/Type.kt` | `Typography` | **Hiç kullanılmıyor** — `MaterialTheme()` çağrısına geçilmemiş |
| `res/values/colors.xml` | 7 şablon rengi (purple_*, teal_*, black, white) | **Hiçbiri referans edilmiyor** |
| `res/values/themes.xml` | `Theme.SubTrack` (parent: `android:Theme.Material.Light.NoActionBar`) | Manifest'te kullanılıyor |

Bu bölümdeki en önemli bulgu: `Theme.kt`'de düzgün bir `DarkColorScheme` tanımlanmış (satır 10-16) ama `MainActivity` renkleri `MaterialTheme.colorScheme.*` üzerinden değil, doğrudan sabitlerden (`PastelGray`, `PastelBlue`, `Color.White`) alıyor. **Koyu tema tanımlı ama fiilen ölü kod** (bkz. §5).

---

## 4. Room Durumu

### Özet: Room projede hiç yok.

Tüm kod tabanında (`*.kt`, `*.kts`, `*.toml`, `*.xml`, `*.properties`) şu terimlerin **hiçbiri geçmiyor**: `room`, `@Entity`, `@Dao`, `@Database`, `RoomDatabase`, `ksp`, `kapt`.

### `Subscription` Entity'sinin Alanları

Entity **yok**. Mevcut olan tek şey `MainActivity.kt:33-37`'deki düz data class:

```kotlin
data class Subscription(
    val id: Int,
    val name: String,
    val price: Double
)
```

| Alan | Tip | Room için gerekli değişiklik |
|---|---|---|
| `id` | `Int` | `@PrimaryKey(autoGenerate = true)` gerekli; şu an ID'ler `maxOfOrNull { it.id } + 1` ile elle üretiliyor (satır 200) |
| `name` | `String` | Olduğu gibi kullanılabilir |
| `price` | `Double` | Room destekler, ama para için `Double` uygun değil (bkz. §5) |

Ayrıca abonelik takibi için beklenen alanların hiçbiri yok: yenilenme tarihi, faturalama periyodu (aylık/yıllık), para birimi, kategori, ikon/renk, notlar.

### `SubscriptionDao` Fonksiyonları

**DAO dosyası yok.** Dönüş tipi raporlanacak fonksiyon yok.

### `@Database` Sınıfı

**Yok.** Dolayısıyla instance oluşturma mekanizması (`Room.databaseBuilder`, singleton, `Application` sınıfı, DI modülü) de yok.

### Eksik Parçaların Net Listesi

Room'u çalışır hale getirmek için sıfırdan eklenmesi gerekenler:

1. **KSP plugin'i** — `libs.versions.toml`'a `com.google.devtools.ksp`, root `build.gradle.kts`'e `apply false`, `app/build.gradle.kts`'e `alias(libs.plugins.ksp)`
2. **Bağımlılıklar** — `androidx.room:room-runtime`, `androidx.room:room-ktx`, ve `ksp(libs.androidx.room.compiler)`
3. **Entity** — `Subscription`'ı `MainActivity.kt`'den ayrı bir dosyaya taşı, `@Entity(tableName = "subscriptions")` ve `@PrimaryKey(autoGenerate = true)` ekle
4. **DAO** — `SubscriptionDao` arayüzü: `@Query("SELECT * FROM subscriptions") fun getAll(): Flow<List<Subscription>>`, `@Insert suspend fun insert(sub: Subscription)`, `@Delete suspend fun delete(sub: Subscription)`
5. **Database** — `@Database(entities = [Subscription::class], version = 1) abstract class SubTrackDatabase : RoomDatabase()`
6. **Instance sağlayıcı** — `Application` alt sınıfı içinde singleton `Room.databaseBuilder(...)`, veya Hilt `@Module`
7. **Repository** — DAO'yu saran katman
8. **ViewModel** — `viewModelScope` içinde `suspend` DAO çağrıları, DAO `Flow`'unu `StateFlow`'a dönüştürme
9. **UI değişikliği** — `MainActivity.kt:60-65`'teki `remember { mutableStateListOf(...) }` yerine `viewModel.subscriptions.collectAsStateWithLifecycle()`; satır 118 ve 198'deki doğrudan liste mutasyonları ViewModel çağrılarına dönüşmeli
10. **`lifecycle-viewmodel-compose`** bağımlılığı (`viewModel()` composable'ı için) — şu an sadece `lifecycle-runtime-ktx` var

---

## 5. Sorunlar ve Riskler

### Derlemeyi Engelleyecek Hatalar

**Yok.** `assembleDebug` ve zorlanmış `compileDebugKotlin --rerun-tasks` temiz geçiyor, tek bir uyarı bile üretmiyor (bkz. §7).

### Bulgular

**[Kritik] — `MainActivity.kt:159` — `ModalBottomSheet`'in `onDismissRequest`'i boş: `onDismissRequest = { }`**
Sheet kapanma isteğini yok sayıyor. `showBottomSheet` hiçbir zaman `false`'a dönmüyor. Geri tuşu, scrim'e dokunma ve aşağı sürükleme — hiçbiri çalışmıyor. FAB'a bir kez basıldıktan sonra bottom sheet **kalıcı olarak açık kalıyor** ve kullanıcı liste ekranına dönemiyor. Uygulama ilk ekleme işleminden sonra fiilen kullanılamaz hale geliyor.

**[Kritik] — `MainActivity.kt:194-215` — "Kaydet" butonu sheet'i kapatmıyor**
`onClick` bloğu aboneliği ekliyor ve form alanlarını sıfırlıyor (satır 206-207), ama `showBottomSheet = false` satırı yok. Satır 205'teki `// Resetle ve Kapat` yorumu niyeti belirtiyor ama "Kapat" kısmı uygulanmamış. Yukarıdaki hatayla birleşince kullanıcının sheet'ten çıkmasının hiçbir yolu kalmıyor.

**[Kritik] — `MainActivity.kt:60-65` — Veri kalıcılığı yok, `rememberSaveable` bile kullanılmamış**
`remember { mutableStateListOf(...) }` state'i yalnızca kompozisyon ömrü boyunca tutuyor. Ekran döndürme, dil değişikliği, tema değişikliği veya arka planda process kill → kullanıcının eklediği tüm abonelikler siliniyor ve liste satır 62-63'teki hardcoded Netflix/Spotify'a resetleniyor. Uygulamanın temel işlevi (abonelik takibi) kalıcı depolama olmadan anlamını yitiriyor.

**[Orta] — `MainActivity.kt:68-72` — `derivedStateOf` kendi amacını boşa çıkarıyor**
```kotlin
val totalMonthlyPrice by remember(subscriptionList.size) { derivedStateOf { ... } }
```
`subscriptionList.size` `MainScreen`'in gövdesinde okunuyor; bu, `MainScreen`'i snapshot state olarak liste boyutuna abone ediyor. Her ekleme/silmede **tüm `MainScreen` yeniden besteleniyor** — `derivedStateOf`'un önlemesi gereken şey tam olarak buydu. Ayrıca `derivedStateOf` zaten liste okumalarını kendisi takip ettiği için `remember` key'i gereksiz. Doğrusu: `remember { derivedStateOf { subscriptionList.sumOf { it.price } } }`.

**[Orta] — `MainActivity.kt:114-121` — `confirmValueChange` içinde yan etki**
```kotlin
confirmValueChange = { value ->
    if (value == SwipeToDismissBoxValue.EndToStart) { subscriptionList.remove(sub); true } else false
}
```
`confirmValueChange` saf bir karar fonksiyonu olarak tasarlanmıştır; jest yerleşme (settle) animasyonu sırasında birden fazla kez çağrılabilir. Liste mutasyonunu burada yapmak, çıkış animasyonu tamamlanmadan öğenin ağaçtan kaldırılmasına ve görsel sıçramaya yol açar. Doğrusu: `LaunchedEffect(dismissState.currentValue)` içinde silmek.

**[Orta] — `MainActivity.kt:75, 210, 241, 267, 282` — Koyu tema tanımlı ama fiilen ölü**
`Theme.kt:10-16`'da eksiksiz bir `DarkColorScheme` var ve `SubTrackTheme` `isSystemInDarkTheme()` ile doğru şekilde seçim yapıyor. Ancak UI renklerini `MaterialTheme.colorScheme.*`'tan değil, doğrudan sabitlerden alıyor: `containerColor = PastelGray` (75), `PastelBlue` (210, 241), `Color.White` (267), `color = DarkText` (281). Sonuç: sistem koyu moda geçtiğinde uygulama **hiç değişmiyor** — beyaz kartlar ve açık zemin kalıyor. `res/values/themes.xml:4`'teki `android:Theme.Material.Light.NoActionBar` de bunu pekiştiriyor (zorla Light).

**[Orta] — `ui/theme/Type.kt:10` + `ui/theme/Theme.kt:32-35` — `Typography` tanımlanmış ama kullanılmıyor**
`MaterialTheme(colorScheme = colorScheme, content = content)` çağrısında `typography` parametresi geçilmemiş. `Type.kt` dosyasının tamamı (34 satır) ölü kod; tanımlanan `bodyLarge` stili hiçbir metne uygulanmıyor.

**[Orta] — `MainActivity.kt:83, 170, 176, 186, 214` — Hardcoded Türkçe metinler, `values-en` çevirisini devre dışı bırakıyor**
`"Ekle"` (83), `"Yeni Abonelik Ekle"` (170), `"Abonelik Adı"` (176), `"Fiyat (Örn: 159.99)"` (186), `"Kaydet"` (214) doğrudan string literal. Proje `res/values-en/strings.xml` ile çoklu dil desteğine hazırlanmış ve satır 101/246'da `stringResource` doğru kullanılmış — ama form ekranının tamamı bunu atlıyor. İngilizce cihazda arayüz yarı Türkçe kalıyor. Ayrıca `add_subscription` ve `welcome_message` string'leri tanımlı ama hiç kullanılmıyor.

**[Orta] — `MainActivity.kt:223` — `getIconForSubscription` gereksiz yere `@Composable`**
Fonksiyon sadece bir `when` ifadesi; composition'dan hiçbir şey okumuyor (`CompositionLocal`, `remember`, state yok). `@Composable` işareti gereksiz bir recomposition scope yaratıyor ve fonksiyonun composable olmayan koddan (örn. ViewModel veya unit test) çağrılmasını engelliyor. Düz fonksiyon olmalı.

**[Orta] — `app/build.gradle.kts:34-35` vs `gradle/gradle-daemon-jvm.properties` vs `.idea/compiler.xml` — Üç farklı JVM hedefi**
`compileOptions` Java 11, Gradle daemon toolchain'i 21, IDE bytecode hedefi 21. Kotlin `jvmTarget` hiç ayarlanmamış. Şu an derleme geçiyor, ancak bu üçlü tutarsızlık Kotlin ve Java hedefleri ayrıştığında klasik "Inconsistent JVM-target compatibility" hatasına dönüşür — özellikle ileride bir annotation processor (Room/KSP) veya Java kaynak dosyası eklendiğinde. `kotlin { jvmToolchain(21) }` ile tek noktadan sabitlenmeli.

**[Orta] — `gradle/libs.versions.toml:10` — Compose BOM (2024.09.00) diğer bağımlılıklara göre çok eski**
BOM Eylül 2024 tarihli (compose-ui 1.7.x'i sabitliyor), ama `activity-compose` 1.12.4, `lifecycle-runtime-ktx` 2.10.0 ve AGP 9.0.1 çok daha yeni. Bu yeni AndroidX kütüphaneleri geçişli olarak daha yeni Compose sürümleri talep edip BOM'un sabitlemesini geçersiz kılabilir; sonuç öngörülemeyen sürüm karışımı olur. *Şu an fiilen bir çakışma olup olmadığından emin değilim* — `./gradlew :app:dependencies` çıktısı ile doğrulanmalı.

**[Orta] — `app/build.gradle.kts:58` — Version catalog tutarsızlığı**
`implementation("androidx.compose.material:material-icons-extended")` catalog dışında, düz string olarak yazılmış; diğer 14 bağımlılığın hepsi `libs.*` kullanıyor. Ayrıca bu kütüphane sadece `MainActivity.kt:229`'daki `Icons.Default.Cloud` için gerekiyor — `Add`, `Delete`, `PlayArrow`, `Star`, `AutoMirrored.Filled.List` zaten çekirdek ikon setinde var. Tek bir ikon için binlerce ikonluk bir bağımlılık ekleniyor.

**[Düşük] — `MainActivity.kt:36, 70` — Para birimi için `Double` kullanımı**
`price: Double` ve `sumOf { it.price }` kayan nokta birikimli hata üretir (örn. `0.1 + 0.2 != 0.3`). Liste büyüdükçe toplam kuruş düzeyinde sapabilir. Para için `BigDecimal` ya da `Long` (kuruş cinsinden) tercih edilmeli. Satır 32'deki `// Double kullanımı hesaplamalar için kritiktir` yorumu bu konuda **yanıltıcı** — durum tam tersi.

**[Düşük] — `MainActivity.kt:196-197` — Sessiz doğrulama hatası**
`toDoubleOrNull() ?: 0.0` — geçersiz fiyat girişi (örn. "abc") kullanıcıya hiçbir şey söylemeden **0.00 TL** olarak kaydediliyor. Benzer şekilde satır 196'daki `if (subscriptionName.isNotBlank())` koşulu sağlanmazsa buton hiçbir şey yapmıyor, hata mesajı da göstermiyor — kullanıcı butonun bozuk olduğunu düşünür. `isError` + destekleyici metin ile geri bildirim verilmeli.

**[Düşük] — `MainActivity.kt:97, 149` — Para formatı tutarsız ve tekrarlanmış**
`String.format(Locale.US, "%.2f TL", ...)` — Türk Lirası birimi ile ABD ondalık/binlik ayracı karışımı (10000.5 → `10000.50 TL`, beklenen `10.000,50 ₺`). Aynı format mantığı iki yerde kopyalanmış. `NumberFormat.getCurrencyInstance(Locale("tr","TR"))` kullanılmalı ve tek yere çıkarılmalı.

**[Düşük] — `res/values/colors.xml:3-9` — Kullanılmayan şablon kaynakları**
`purple_200`, `purple_500`, `purple_700`, `teal_200`, `teal_700`, `black`, `white` — 7 rengin hiçbiri ne kodda ne de XML'de referans ediliyor. Android Studio şablonundan kalma ölü kaynak.

**[Düşük] — `MainActivity.kt:26-27` — Gereksiz import**
`import androidx.compose.runtime.getValue` ve `setValue` — satır 15'teki `import androidx.compose.runtime.*` wildcard'ı bunları zaten kapsıyor. Ayrıca dosyada 4 ayrı wildcard import var (satır 7, 13, 14, 15) ki bu isim çakışmalarını gizleyebilir.

**[Düşük] — `app/build.gradle.kts:25-31` — Release build yapılandırılmamış**
`isMinifyEnabled = false`, `isShrinkResources` ayarlanmamış, `signingConfig` yok. Release APK'sı küçültülmemiş ve imzalanmamış olarak üretilir. Yayına hazır değil.

**[Düşük] — `.gitignore` — `.idea/` yeterince kapsanmamış**
11 adet `.idea/*.xml` dosyası takip ediliyor (`deviceManager.xml`, `deploymentTargetSelector.xml`, `runConfigurations.xml`, `appInsightsSettings.xml` dahil). Bunlar makineye özgü IDE durumu; birden fazla geliştirici olduğunda sürekli merge çakışması üretir. Hâlihazırda `.idea/emulatorDisplays.xml` takip edilmiyor **ve** ignore da edilmiyor — `git status` sürekli kirli görünüyor. Ayrıca `local.properties` iki kez listelenmiş (satır 3 ve son satır).

### Memory Leak / ANR / Main Thread I/O

**Şu an bu risklerin hiçbiri mevcut değil** — ancak bu bir başarı değil, eksikliğin yan etkisi:

- **Main thread I/O:** Yok. Projede hiç I/O yok — ne veritabanı, ne dosya, ne ağ çağrısı. Room eklendiğinde DAO çağrıları `suspend` yapılmaz veya `allowMainThreadQueries()` kullanılırsa bu risk **anında** ortaya çıkacak.
- **ANR:** Yok. Uzun süren işlem yok; tek hesaplama `sumOf` (satır 70) ve O(n) ID üretimi (satır 200) — her ikisi de ihmal edilebilir.
- **Memory leak:** Yok. `Context`/`Activity` referansı tutan uzun ömürlü nesne yok, statik referans yok, kayıt edilip kaldırılmayan listener yok. Tüm state kompozisyon ömrüne bağlı.
- **Coroutine/scope sızıntısı:** Yok — projede hiç coroutine kullanılmıyor.

### Deprecated API Kullanımı

**Derleyici tek bir deprecation uyarısı üretmedi** (`--rerun-tasks` ile zorlanmış temiz derlemede, §7).

Olumlu bir nokta: `MainActivity.kt:227`'de `Icons.AutoMirrored.Filled.List` kullanılmış — deprecated olan `Icons.Default.List` değil. Doğru tercih.

İki dikkat noktası (deprecated değil ama dikkat gerektiriyor):
- `MainActivity.kt:50` — `@OptIn(ExperimentalMaterial3Api::class)`: `ModalBottomSheet`, `rememberModalBottomSheetState` ve `rememberSwipeToDismissBoxState` deneysel API'ler. Compose sürümü yükseltildiğinde imzaları kırılabilir.
- `res/values/themes.xml:4` — `android:Theme.Material.Light.NoActionBar`: framework Material 1 teması. Deprecated değil, ancak Material 3 Compose uygulamasında `Theme.Material3.DayNight.NoActionBar` beklenirdi; mevcut hali sistem çubuklarını koyu modda da açık renkte bırakıyor.

---

## 6. Test ve Git

### Testler

**Gerçek test yok.** Sadece Android Studio'nun oluşturduğu 2 şablon dosyası var:

| Dosya | Test | Ne doğruluyor |
|---|---|---|
| `app/src/test/.../ExampleUnitTest.kt:13` | `addition_isCorrect()` | `assertEquals(4, 2 + 2)` — Kotlin'in toplama yaptığını |
| `app/src/androidTest/.../ExampleInstrumentedTest.kt:16` | `useAppContext()` | Paket adının `com.elinacn.subtrack` olduğunu |

Uygulama mantığının **hiçbiri** test edilmiyor. Test edilmesi gereken ve edilmeyen davranışlar: toplam tutar hesaplama (`sumOf`, satır 70), fiyat parse etme ve virgül/nokta dönüşümü (satır 197), ID üretimi (satır 200), ekleme/silme akışı, boş isim doğrulaması (satır 196).

Ayrıca `androidx.compose.ui:ui-test-junit4` ve `ui-test-manifest` bağımlılıkları eklenmiş (`app/build.gradle.kts:55, 57`) ama **tek bir Compose UI testi yazılmamış** — altyapı kurulu, kullanılmıyor.

### .gitignore

Kısmen doğru; şablon temel alınmış ve genişletilmemiş.

**Doğru olanlar:** `/build`, `/app/build` (`app/.gitignore`), `.gradle`, `local.properties` (takip edilmediği `git ls-files` ile doğrulandı), `*.iml`, `.DS_Store`, `.cxx`, `/captures`

**Sorunlar:**
- `.idea/` dizini seçici şekilde ignore edilmiş; 11 IDE ayar dosyası takip ediliyor (§5)
- `.idea/emulatorDisplays.xml` ne takip ediliyor ne ignore ediliyor → çalışma ağacı sürekli kirli
- `local.properties` iki kez yazılmış (`/local.properties` ve `local.properties`)
- Eksik: `*.apk`, `*.aab`, `.kotlin/` (Kotlin 2.x oturum dizini), `*.keystore`, `*.jks`

Toplam takip edilen dosya: 51.

### Git Geçmişi

**Toplam commit sayısı: 5**

| Hash | Tarih | Yazar | Mesaj |
|---|---|---|---|
| `d06cc5e` | 2026-03-01 | Elina/Esma | Add dynamic subscription list, total calculation, and swipe-to-dismiss |
| `276e3bf` | 2026-02-26 | Elina | yorum satırı silindi |
| `7776623` | 2026-02-26 | Elina | yorum satiri eklendi |
| `c761512` | 2026-02-26 | Elina | yorum satiri eklendi |
| `1b8144e` | 2026-02-26 | Elina | Initial commit |

Gözlemler: 5 commit'in 3'ü ("yorum satiri eklendi/silindi") içerik taşımıyor — muhtemelen Git alıştırması. Gerçek çalışma tek bir commit'te (`d06cc5e`). Yazar adı iki farklı biçimde görünüyor (`Elina` ve `Elina/Esma`) — `git config user.name` değişmiş. Ana dal `master`, aktif dal `Elina`; `master`'a henüz merge yapılmamış.

**Çalışma ağacı durumu:** `.idea/emulatorDisplays.xml` takip edilmiyor (yukarıya bakınız). Bunun dışında temiz.

---

## 7. Şu An Derleniyor mu

### Evet — derleme başarılı.

**Komut 1:**
```
./gradlew assembleDebug --console=plain --no-daemon
```
```
BUILD SUCCESSFUL in 2m 7s
35 actionable tasks: 7 executed, 28 up-to-date
```

İlk çalıştırmada `:app:compileDebugKotlin` görevi `UP-TO-DATE` idi (önceki derlemeden önbelleğe alınmış), yani Kotlin kodu fiilen yeniden derlenmedi. Sonucun güvenilir olması için derleme zorlandı:

**Komut 2 (zorlanmış yeniden derleme):**
```
./gradlew :app:compileDebugKotlin --rerun-tasks --console=plain --no-daemon
```
```
BUILD SUCCESSFUL in 34s
6 actionable tasks: 6 executed
```

**Sonuç:** Kotlin kaynak kodu sıfırdan, önbelleksiz derlendi. **Hata yok, uyarı da yok** — ne deprecation, ne kullanılmayan değişken, ne de tip uyarısı. Debug APK üretildi.

Ortam notları:
- Gradle 9.2.1 wrapper'dan indirildi ve doğrulandı
- `PATH` üzerindeki JDK 8 (`1.8.0_491`) olmasına rağmen derleme geçti; Gradle `gradle-daemon-jvm.properties`'teki `toolchainVersion=21` ayarı gereğince JDK 21'i foojay üzerinden kendisi sağladı
- `org.jetbrains.kotlin.android` plugin'i uygulanmamış olmasına rağmen `:app:compileDebugKotlin` mevcut ve çalışıyor (§2)

**Önemli ayrım:** Proje **derleniyor**, ancak bu doğru çalıştığı anlamına gelmiyor. §5'te listelenen üç Kritik bulgu (kapanmayan bottom sheet, kapatmayan Kaydet butonu, veri kalıcılığının olmaması) çalışma zamanı hatalarıdır ve derleyici bunları yakalayamaz. Uygulama derlenir, kurulur, açılır — ve FAB'a ilk basıştan sonra kullanıcı bottom sheet'te kilitli kalır.

---

## Özet Değerlendirme

| Alan | Durum |
|---|---|
| Derleme | Geçiyor, uyarısız |
| Çalışma zamanı kullanılabilirliği | **Bozuk** — bottom sheet kapanmıyor (Kritik ×2) |
| Veri kalıcılığı | **Yok** — Room hiç eklenmemiş, `rememberSaveable` bile kullanılmamış |
| Mimari | Katmansız — ViewModel, Repository, DI yok; tüm state 168 satırlık bir composable içinde |
| Test kapsamı | Sıfır (yalnızca 2 şablon testi) |
| Çoklu dil | Yarım — `values-en` var, form ekranı hardcoded Türkçe |
| Koyu tema | Tanımlı ama fiilen ölü (renkler tema şemasını atlıyor) |

**Bulgu dağılımı:** 3 Kritik, 9 Orta, 7 Düşük.

En yüksek öncelikli 3 düzeltme:
1. `MainActivity.kt:159` — `onDismissRequest = { showBottomSheet = false }`
2. `MainActivity.kt:208` civarı — Kaydet `onClick`'ine `showBottomSheet = false` ekle
3. Room + ViewModel katmanını kur (§4'teki 10 maddelik liste)
