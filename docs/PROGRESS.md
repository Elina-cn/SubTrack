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

## [Faz 16l] Testçi bildirimi — ekleme ekranında titreme (teşhis) — 2026-09-23

**Durum:** Teşhis tamamlandı, düzeltme yapılmadı (versionCode 2 adayı, ayrı tur).
Kod değişmedi; deneyler için yapılan geçici değişiklikler geri alındı, çalışma
ağacında yalnızca bu kayıt var.

### Bildirim (2026-09-23)

**Testçi cihazı (düzeltmeyle netleşti):** Xiaomi Redmi Note 12 Pro 5G, Android 13,
1080×2400, 120 Hz. Videolar 720×1600'e küçültülerek kaydedilmiş; öğe boylarından
yoğunluk ~2,75 (440 dpi, tahmin) → ~393×873 dp. Koyu tema, Türkçe, hareket
çubuğu. İlk turda yazılan "Android 13, 720×1600, ~90 Hz, ~360×800 dp" bilgisi
geçersiz.

**Birinci video (sohbette kare kare ölçüldü):** Sheet açık, form boş, klavye
kapalı, sheet ekranı neredeyse dolduruyor. Sheet'in tamamı tek parça (başlık ile
Kaydet aynı miktarda) video pikseliyle ~215 px (~110 dp) yukarı fırlıyor
(~35 ms), yavaşlayarak iniyor (~170 ms); döngü ~215 ms, seride 6-7 kez, iki seri
(~1,5 sn ve ~1,2 sn). Tepede sheet'in üst kenarı ve başlık durum çubuğunun
arkasında. Form sheet içinde kaymıyor, gerilme izi yok.

**İkinci video (dokunuşlar görünür) + testçinin anlatımı:** Tetikleyici formda
tek kısa yukarı kaydırma (dokunuş ~130 ms). Parmak kalkınca sheet kendi kendine
salınıyor, dokunulmadan ~3 sn sürüyor, genlik hiç azalmıyor; sonraki dokunuşta
~40 ms içinde duruyor ve gerçek dinlenme konumuna dönüyor. Genlik ilk itişe
bağlı: küçük itişte ~8 dp (döngü ~120 ms), büyükte ~32 dp (~170 ms), ilk
videodaki ~110 dp (~215 ms). En alçak nokta bile dinlenme konumunun ~7 dp üstünde.
İlk turdaki "-13 px'lik parmak" yorumu geçersiz: salınım parmak kalktıktan
sonra sürüyor.

**Kullanıcının telefonunda:** OPPO A15s, Android 10, 720×1600 @272 (~423×940 dp),
60 Hz. Art arda hızlı itme ve tek kaydırma + 3 sn dokunmadan bekleme denendi:
titreme yok.

### Görev 0 — kaynak (material3 1.4.0, `material3-android-1.4.0-sources.jar`)

- `ModalBottomSheet.kt:338` — Surface'e `consumeWindowInsets(WindowInsets(top =
  sheetState.offset.toInt().coerceAtLeast(0)))`; `:362` içerik
  `windowInsetsPadding(contentWindowInsets())`; `SheetDefaults.kt:400-402`
  varsayılan `safeDrawing.only(Bottom + Top)`. Sonuç: sheet'in üst kenarı durum
  çubuğu bölgesine girince içeriğe `durum çubuğu − ofset` kadar üst dolgu
  eklenir, yani **sheet'in boyu kendi ofsetine bağlı olur.**
- `ModalBottomSheet.kt:295-309` — çapalar ölçülen boydan: `Expanded at
  max(0, fullHeight − sheetSize.height)`. Boy değişince çapa değişir.
- `AnchoredDraggable.kt:557-575` — `anchoredDrag(targetValue)` bloğu
  `restartable(inputs = { anchors to targetValue })` içinde; çapalar değişince
  süren animasyon iptal edilip yeniden başlatılır.
- `AnchoredDraggable.kt:672-690` — `animateTo(target, velocity)` bloğu
  `animate(prev, targetOffset, velocity, spec)` çağırır; yeniden başlatmada da
  **ilk çağrıdaki `velocity`** (parmağın bırakış hızı) kullanılır, o anki hız
  değil. Yorum satırı aşımı bilerek serbest bırakıyor ("allow the overshoot").
- `AnchoredDraggable.kt:421-435` — `settle(velocity)` → `animateTo(hedef, velocity)`.
  `SheetDefaults.kt:488-491` — formun iç içe kaydırması `onPostFling`'de artan
  hızı `settle`'a verir; `ModalBottomSheet.kt:327-333` — sheet'in kendi
  `draggable`'ı da bırakışta `settle(v)` çağırır.
- `AnchoredDraggable.kt:588-592` — sürükleme adımları `[minAnchor, maxAnchor]`
  aralığına kırpılır; `ModalBottomSheet.kt:331` — animasyon sürerken
  `startDragImmediately = true`.
- `BottomSheetScaffold.kt:470-476` — `verticalScaleUp`: ofset çapanın üstüne
  çıkınca Surface dikeyde büyütülür, altta boşluk görünmez.
- `StandardMotionTokens.kt:20-21` — `DefaultSpatial` yayı ζ 0,9, k 700. Çapadan
  yukarı hızla başlayan tek yay: tepe ≈ 0,0149 sn × hız, yükseliş ~39 ms.

**Uygulama tarafı:** `AddSubscriptionSheet.kt:69-74` varsayılan
`contentWindowInsets` ve jestlerle `ModalBottomSheet`; `:86-91` form
`weight(1f, fill = false) + verticalScroll`, Kaydet dışarıda. `fill = false`
sheet'i içerik boyunda tutar; dinlenmedeki üst kenar "ekran − içerik boyu"na
düşer, yani ekran boyu, durum çubuğu ve yazı ölçeği üst kenarın durum çubuğuna
ne kadar yakın duracağını belirler. `imePadding()` uygulamada değil, kütüphanenin
kök kutusunda (`ModalBottomSheet.kt:186`). `HomeScreen.kt:79`
`skipPartiallyExpanded = true` → yalnız Hidden ve Expanded çapaları var.

### Yeniden üretme

Release derlemesi (kod `v1.0` ile aynı), koyu tema, Türkçe. Ölçüm: ekran kaydı →
kare kare Kaydet'in üst kenarı (içerik) ve sheet'in üst kenarı. Hareket: tek
kısa yukarı kaydırma, parmak kalkar, 3 sn beklenir, tek dokunuş. İtişler dp
cinsinden: küçük 47 dp/130 ms, orta 218 dp/130 ms, hızlı 440 dp/90 ms.
**Pay** = dinlenmedeki sheet üst kenarı − durum çubuğu alt kenarı.

**Düzeltmeden önceki ölçüm (ayrı tutuldu):** 360×800 dp'de (api34, yazılım GPU)
art arda 6 fırlatma kaydı alındı; kayıt 17 fps çıktı ve takip seçili TRY çipine
oturdu — geçersiz, kullanılmadı. O ekranda sheet zaten tam boydu (üst kenar 0).

**Varsayılan yazı ölçeğinde belirti üretilemedi.** API 33, 393×873 dp'de pay
118,5 dp; üç itiş de tek sıçrama (5 / 24 / 72 dp), ~250 ms'de dinlenme. Tek
sıçramanın tepesi bırakış hızıyla birebir: 4 600 px/s → 68 px, 13 300 px/s →
200 px (model 0,0149 × hız). Her döngünün hızlı yükselip yavaş inme biçimi bu yay.

**Pay küçülünce üretildi.** Yazı ölçeğiyle sheet durum çubuğuna yaklaştırıldı
(393×873 dp): 1.15 → pay 36 dp, 1.2 → 28 dp, 1.25 → 19,6 dp, 1.26 ve üstü →
sheet tam boy (üst kenar 0). Pay 19,6 dp'de (api33/34):

| Hareket | Sonuç |
|---|---|
| Formda küçük itiş | tek sıçrama 5 dp (pay aşılmadı) |
| Formda orta itiş | **salınım**, 3 sn, dip 19-20 dp, tepe 31-54 dp; tepe-dip ~30 dp, döngü ~168 ms |
| Formda hızlı itiş | **salınım**, 3 sn, dip 19-20 dp, tepe 115-139 dp; tepe-dip ~112 dp, döngü ~200 ms |
| Tutamaktan itiş | **salınım**, tepe 44-51 dp, döngü ~167 ms |
| Başlıktan itiş | **salınım**, tepe 53-63 dp, döngü ~167 ms |
| Klavye açık, formda hızlı itiş | sheet tam boy; tek sıçrama 29 dp, salınım yok |
| Kısa yavaş aşağı sürükleme (kontrol) | 13 dp iner, geri döner, salınım yok |
| Tam boy sheet (yazı 1.26) | tek sıçrama, salınım yok |

Videodaki değerlerle: orta itiş ~30 dp / 168 ms ↔ testçi ~32 dp / ~170 ms;
hızlı itiş ~112 dp / ~200 ms ↔ ilk video ~110 dp / ~215 ms. Salınım dokunulmadan
sürdü, genlik azalmadı; api36'da 5 sn'den uzun sürdü.

### Dört durum (+ yazı ölçeği)

Yazı 1.0 (istenen tablo):

|  | ~393×873 dp (1080x2400, 440) | ~423×940 dp (720x1600, 272) |
|---|---|---|
| API 29 | yok — pay 165 dp; 3 / 17 / 64 dp tek sıçrama | yok — pay 233 dp; 4 / 24 / 59 dp |
| API 33 | yok — pay 118,5 dp; 5 / 24 / 72 dp | yok — pay 183 dp; 5 / 25 / 73 dp |

Yazı 1.25:

|  | ~393×873 dp | ~423×940 dp |
|---|---|---|
| API 29 | **var** (hızlı): pay 36,7 dp, dip 32-35 dp, tepe ~90-126 dp, döngü ~167 ms; küçük/orta tek sıçrama | yok — pay 134 dp; en sert itiş (600 dp/90 ms) bile 95 dp tek sıçrama |
| API 33 | **var** (orta, hızlı): pay 19,6 dp, dip 19-20 dp | yalnız en sert itişte **var**: pay 84 dp, dip 85 dp, tepe ~210 dp, döngü ~151 ms |

api36 (393×873 dp): yazı 1.0 tek sıçrama; yazı 1.25'te **var** (pay 19,6 dp, en
alçak 54 px = pay). Bu imajda `screenrecord` 3 kare yazdı, 14 ekran görüntüsüyle
5,2 sn örneklendi.

**İki değişken ayrıldı: ikisi de tek başına belirleyici değil.** Android 10,
13, 14 ve 16 aynı davranıyor; gerilme ↔ parlama farkı rol oynamıyor (API 29'da
da var, E2'de de var). Belirleyici olan **pay**: tek sıçrama payı aşarsa salınım
başlar. Pay ekran boyuna, durum çubuğu boyuna (API 29'da 24 dp, bu emülatörlerde
~46 dp) ve içerik boyuna (yazı ölçeği) bağlı. En sert fırlatma (8000 dp/s)
~119 dp sıçratır; kullanıcının telefonuna denk ayarda pay 233 dp, yani orada
hiçbir itiş belirtiyi üretemez. Salınımın dibi payın ta kendisi olduğu için
(aşağıda) testçinin payı ~7 dp: orada ~470 dp/s'lik hafif bir fiske bile yeter.
**Testçinin sheet'i neden durum çubuğuna bu kadar yakın, bilinmiyor** —
393×873 dp'de varsayılan yazıyla pay 118 dp. Olası sebep MIUI'de büyük yazı
veya ekran boyutu ayarı; doğrulanmadı. Videoda dinlenmedeki üst kenarın durum
çubuğunun ~7 dp altında olması beklenir; testçiye yazı boyutu sorulabilir.

60 ↔ 120 Hz: dört imajda 60 Hz'de üretildi, yani yenileme hızı şart değil.
120 Hz denenmedi (emülatörde `-vsync-rate` var); ayrıntılar farklı olabilir.

### Sebep

Bileşen: material3 1.4.0 `ModalBottomSheet`; uygulamanın içerik boylu sheet'i
tetik koşulunu (üst kenar durum çubuğuna yakın) hazırlıyor.

1. Parmak kalkınca yukarı yöndeki bırakış hızı sheet'e ulaşır (formdan
   `onPostFling`, tutamak/başlıktan sheet'in kendi sürüklemesi) → `settle(v)` →
   çapadan yukarı hızla başlayan yay çapanın üstüne taşar (tek sıçrama).
2. Üst kenar durum çubuğu bölgesine girince üst dolgu değişir → sheet'in boyu
   değişir → Expanded çapası değişir → süren animasyon yeniden başlar ve yine
   **ilk bırakış hızıyla** yukarı itilir.

Geçici kayıt deneyiyle (E4) kare kare görüldü: bölgede ofset iki karede bir
değişiyor (her yeniden başlatma bir kare yiyor), H 2346 ↔ 2331-2344 px arasında
gidip geliyor, kararlı döngüde ofset +2…+16 px ile −79…−89 px arasında,
150-167 ms'de bir, sönmeden.

- **Neden sönmüyor:** Her döngüde üst kenar bölgeye geri girince çapa değişiyor
  ve animasyon ilk bırakış hızıyla yeniden başlıyor; kaybedilen enerji her
  seferinde yerine konuyor. Genliği bırakış hızı belirliyor — testçinin "genlik
  itişe bağlı" gözlemi.
- **Neden dokununca duruyor:** Animasyon sürerken sheet'in sürüklemesi dokunuşta
  hemen başlıyor (`startDragImmediately`) ve animasyonu iptal ediyor; ilk
  sürükleme adımı ofseti çapa aralığına kırpıyor, sheet tek karede yerine
  oturuyor (E4: −30,8 → 54,0 px). Parmak kayarsa çapalar tazeleniyor, bırakınca
  gerçek dinlenmeye dönüyor (E4: 54 → 108 → 184 → 182).
- **Neden en alçak nokta dinlenmenin üstünde:** Üst kenar bölgedeyken eklenen
  üst dolgu ofseti birebir telafi ediyor; içerik, üst kenarın bölge sınırına
  değdiği seviyede sabitleniyor = dinlenme − pay. Yeniden itiş tam bölgeye giriş
  karesinde olduğu için dip = pay. Dört payda ölçüldü: 19,6 → 19-20 dp,
  36 → 36-37 dp, 36,7 → 32-35 dp, 84 → 85 dp.

**Emülatörde testçiden farklı bir ayrıntı:** hareketsiz tek dokunuş (`input tap`)
salınımı durduruyor ama sheet dip seviyesinde donup kalıyor: üst kenar durum
çubuğu bölgesinde (54 px), içerik dinlenmenin 20 dp üstünde, animasyon yok;
dakikalarca böyle kaldı, yeni dokunuşlar işe yaramadı (animasyon olmadığı için
dokunuş sürüklemeyi başlatmıyor, küçük kayma dokunma eşiğinin altında). Gerçek
bir sürükleme çözüyor. 54, üst kenar ekran dışındayken hesaplanmış çapa
(2400 − 2346); 54'te boy 2292'ye iniyor ve çapa 108 olmalı, ama durum makinesi
sürükleme olmadan buna geçmiyor — **tam sebebini belirleyemedim.** Testçinin
"dokununca yerine dönüyor" gözlemi, dokunuşunda küçük bir kayma olmasıyla
açıklanabilir; emin değilim.

### Geçici deneyler (commit edilmedi, geri alındı)

API 34, 393×873 dp, yazı 1.25 (pay 19,6 dp), orta ve hızlı itiş:

| Deney | Sonuç |
|---|---|
| E1 `contentWindowInsets` yalnız alt (üst inset yok) | **salınım yok**; tek sıçrama 69 / 200 px, ~250 ms |
| E2 `verticalScroll(…, overscrollEffect = null)` | salınım sürüyor (dip 19 dp) |
| E3 formda `verticalScroll` yok | salınım sürüyor (itiş sheet'in kendi sürüklemesinden) |
| E4 kare kare `logcat` kaydı | yukarıdaki mekanizma |

### Düzeltme seçenekleri (uygulanmadı)

1. **Sheet boyunun ofsete bağlılığını kaldırmak:** `contentWindowInsets`
   yalnız alt; sheet'i durum çubuğundan ofsetten bağımsız bir sınırla uzak
   tutmak (ör. sheet'e durum çubuğu kadar üst pay / en fazla boy). + Sebebe
   gidiyor, E1 doğruladı, küçük değişiklik; donma durumunu da gidermesi beklenir
   (doğrulanmadı). − Sheet tam boy olduğunda (klavye açık, büyük yazı, küçük
   ekran) başlığın durum çubuğu altına girmemesi için yerine bir şey konmalı;
   klavye tablosu (TESTING) ve API 24/29 edge-to-edge yeniden ölçülmeli.
2. **Sheet'i her zaman tam boy yapmak** (`fill = true` / tam yükseklik). +
   Tek değişiklik; tam boy durumda salınım ölçülmedi (yazı 1.26+, klavye açık).
   − Uzun ekranlarda görünüm değişir (üstte boş alan); koddaki "içerik boylu
   sheet" kararına ters.
3. **Yukarı fırlatma hızını sheet'e ulaşmadan yutmak** (formda
   `NestedScrollConnection`, `onPostFling`'de y < 0'ı tüketmek). + Yerleşim
   değişmez. − Kısmi: tutamak ve kaydırma dışı alanlardan itiş yine sheet'in
   kendi sürüklemesinden geçiyor, onlar da tetikliyor.
4. **`sheetGesturesEnabled = false`.** + İki yol da kapanır. − Aşağı kaydırarak
   kapatma kaybolur (scrim ve geri tuşu kalır); kullanıcı davranışı değişir.
5. **Kütüphane güncellemesi / hata bildirimi.** Kök (yeniden başlatmada ilk
   hızın kullanılması + ofsete bağlı inset) material3 1.4.0'da; yeni sürümde
   düzelip düzelmediğine bakılmadı. − BOM değişikliği riski, belirsiz.

Önerilen sıra 1, sonra 2; 3-4 yedek; 5'e paralel bakılabilir. Tek sıçramanın
kendisi (en fazla ~119 dp, ~250 ms'de biter) kütüphanenin varsayılanı ve her
cihazda var; bildirilen belirti değil. Düzeltme turu için güvenilir test
hücresi: API 33, 393×873 dp, yazı 1.25, orta itiş.

### Ortam

- API 33 imajı indirildi (`system-images/android-33/google_apis_playstore/x86_64`
  r09); yeni AVD `subtrack_tester_api33` (pixel_6, `hw.keyboard=yes`).
  TESTING.md'ye eklenmedi.
- `wm size`/`wm density` dört emülatörde `reset`lendi, yazı ölçeği 1.0'a döndü;
  api29 dil (en-US) ve tema (açık) eski hâline, api36 tema ve uygulama dili
  eski hâline alındı. api34'te `cmd uimode night yes` ve uygulama dili tr-TR
  kaldı (başlangıç hâli kaydedilmemişti).
- api29/api34/api36'da uygulama kaldırılıp upload anahtarıyla imzalı release
  kuruldu (eski veriler silindi); sonraki `installDebug` öncesi `adb uninstall`
  gerekir. api29/33/34'te `/sdcard/f16l.mp4` kaldı.
- Ölçüm tuzakları (TESTING'e sonra eklenebilir): API 33'te 60-70 ms'lik, API 36'da 90 ms'lik
  `input swipe` sheet'e hiç ulaşmadı; api36'da `screenrecord` kare yazmıyor;
  `-gpu swiftshader_indirect` kayıtları ~17 fps, `-gpu host` ~20-55 fps.
- Kayıtlar, kareler, izler ve E4 logu repoya girmedi; oturumun geçici
  klasöründe: `%LOCALAPPDATA%\Temp\claude\C--Users-cane7-Documents-GitHub-SubTrack\6eab1e64-5df3-4d2e-bef2-8c4613aec6f3\scratchpad\f16l\`
  (`rec/<hücre>/rec.mp4`, `frames/`, `trace.csv`; `e4_log.txt`; `api36_burst/`).

**Değişen dosyalar**
- `docs/PROGRESS.md` — bu kayıt

**Commit'ler**
- (bu kayıt) docs: diagnose the add sheet jitter reported in closed testing

**Sonraki faz için not**
- Düzeltme ayrı turda, seçenek sohbette seçildikten sonra (versionCode 2 adayı).
- Testçiye yazı boyutu / ekran boyutu ayarı sorulabilir; videoda dinlenmedeki
  üst kenarın durum çubuğuna ~7 dp mesafede olması beklenir.

---

## [Faz 16k-1] Kapalı Test Yayında — 2026-09-23

**Durum:** Tamamlandı. Yalnızca belge; kod değişmedi.

**Yapılanlar**
- Kapalı test yayında: sürüm 1 incelemeyi geçti; 23.09.2026'da Console'un Kontrol panelinde "Kapalı test sürümü yayınlayın" adımı tamamlanmış, yayınlanmamış değişiklik yok.
- 16k kaydındaki "incelemeye gönderildi / incelemede" bilgisi aşıldı; 16k kaydı değiştirilmedi.
- ROADMAP Faz 16 "Kapalı test" maddesi işaretlendi.
- ROADMAP Faz 16 "Bitti": "kapalı test incelemede" → "kapalı test sürüyor: 12 testçinin 14 gün kesintisiz katılımı bekleniyor".
- ROADMAP Faz 17: tanımsız "Karar 3" atfı çıkarıldı; gerekçe 16k kaydına yazılmamıştı, ROADMAP Faz 17'ye eklendi.
- ROADMAP foreground service maddesi: Console'da doğrulandı, 22.09.2026 gönderiminde beyan istenmedi.
- ROADMAP WorkManager izinleri notu: 16j Console'a değil birleşik manifeste bakmıştı; Console ayrı beyan istemedi (22.09.2026), gizlilik politikası doğrulanmadı, madde açık.
- ROADMAP API 24/25 maddesi: 16h-1 listeden çıktı — o tur yalnızca api29 ve api34'te koştu (16h-1 kaydından kontrol edildi).
- ROADMAP kapsam notunun altına sürüm sırası: v1.1 dışa/içe aktarma, ağ v1.2 (`PROJECT_SPEC.md` §4).

**Değişen dosyalar**
- `docs/ROADMAP.md` — yukarıdaki düzeltmeler
- `docs/PROGRESS.md` — bu kayıt

**Commit'ler**
- (bu kayıt) docs: record the live closed test and fix stale roadmap notes

---

## [Faz 16k] Play Console Kurulumu ve v1.0 — 2026-09-23

**Durum:** Tamamlandı. Bu tur yalnızca belge; kod değişmedi. Console işleri
repoda hiç kanıt bırakmıyor (yüklemeler, Data Safety formu, mağaza girişi
UI üzerinden yapılıyor), bu yüzden bu kayıt tamamen kullanıcının verdiği
bilgiye dayanıyor — koddan doğrulanabilen tek şey Görev 0'daki üç kontrol.

### Görev 0 — doğrulama (değiştirmeden)

| Kontrol | Sonuç |
|---|---|
| `git rev-list -n 1 v1.0` | `a16e29977bc48b95d1b8c3bba5de4770c7c62534` — kullanıcının verdiği a16e299 ile eşleşiyor |
| `git ls-remote --tags origin v1.0` | aynı hash ile döndü, boş dönmedi |
| `grep -rn fallbackToDestructiveMigration app/` | eşleşme yok |

Üçü de tuttu. Etiket atılmadı, taşınmadı, silinmedi.

### Console'da yapıldı, repoda kanıt yok

- **AAB:** `versionCode 1` / `versionName 1.0`, kaynak commit `a16e299` (16j).
  Kullanıcı bu commit'e `v1.0` etiketini attı.
- **Play App Signing açık:** uygulama imzalama anahtarı Google'da, bizimki
  upload key (16g).
- **Dahili test:** sürüm 1 yayında. **Kapalı test:** tüm ülkeler, aynı sürüm
  kitaplıktan eklendi, 22.09.2026'da incelemeye gönderildi.
- **Gizlilik politikası:** https://elina-cn.github.io/subtrack-privacy/
  (TR + EN).
- **Data Safety:** toplama yok, paylaşım yok. Hedef kitle 18+, içerik
  derecesi 3+, reklam ve reklam kimliği yok. Gerekçeler `PROJECT_SPEC.md` §5.
- **Mağaza girişi:** en-US (varsayılan) + tr-TR; simge `docs/store/icon-512.png`,
  özellik grafiği `docs/store/feature-graphic.png`, ekran görüntüleri
  `docs/screenshots/store/` (EN 7, TR 5). TR'de koyu tema görüntüsü yok.
- **Console'un tek uyarısı** "yerel kod için hata ayıklama sembolü yok" —
  kabul edildi (yerel kod AndroidX'ten, sembolü elimizde yok; bkz. ROADMAP
  Faz 16'daki native kütüphane maddesi).
- **Üretim erişimi:** 12 testçi 14 gün kesintisiz katılımda kalınca
  başvurulacak.

### Değişen belgeler

- `docs/ARCHITECTURE.md` — "Şema sürümlemesi" bölümünün başına tarihli bir
  yürürlük notu eklendi (migration kuralı artık yürürlükte; tetik ilk Play
  kurulumu, `1.json` dondu); eski "yayın öncesi" paragrafları silinmeden
  "artık geçerli değil" alt başlığı altına alındı.
- `CLAUDE.md` — §6 doğrulama listesine iki madde: `1.json` diff'te
  değişmiş görünüyorsa dur, ve fiziksel test cihazına dokunulmaz (gerekçeler
  `ARCHITECTURE.md`'de).
- `docs/ROADMAP.md` — Faz 16 başlığı 🟡'ya çevrildi, bitiş tanımı üretim
  erişimi koşuluyla genişletildi, PROJECT_SPEC bölüm numarası düzeltildi
  (§7 → §8, dosyadan kontrol edildi), yukarıdaki bilgilerle kapanan maddeler
  işaretlendi, "Internal testing → production" üçe bölündü, 16j için satır
  eklendi. Faz 17 kapsamı `PROJECT_SPEC.md` §4'e ("v1.1") yönlendirildi.
- `docs/PROGRESS.md` — bu kayıt.

### README bulgusu

`README.md` yalnızca okundu, değiştirilmedi. Yayın durumu hakkında gerçeğe
aykırı bir cümle bulunmadı — dosya zaten "no server, no account" gibi nötr
ifadeler kullanıyor, "Play'de yayında" türünden bir iddia yok.

**Commit'ler**

- `127764f` docs: put the schema migration rule into force
- (bu kayıt) docs: record the Play Console setup and the v1.0 release

---

## [Faz 16j] Yayın Öncesi Son Kontrol — RTL Kapatıldı, İzinler ve Mağaza İddiaları Denetlendi — 2026-09-22

**Durum:** Tamamlandı. Tek kod değişikliği manifestteki `supportsRtl`
özniteliği; kaynak kod ve metinler değişmedi.

### Bölüm A — RTL kapatıldı

16i'de ölçülmüştü: `[ar]` gibi sağdan sola bir dil listesinde düzen
aynalanıyor, metin İngilizce kalıyordu. Uygulamanın kaynağı olan iki dil de
(`en`, `tr`) soldan sağa yazılıyor ve aynalanmış düzen hiç çalıştırılmadı —
özellikle kaydırarak silmenin yönü ve `Canvas` ile çizilen iki grafik.

`android:supportsRtl="false"`. Manifestte başka satır değişmedi;
`windowSoftInputMode="adjustResize"` yerinde. Gerekçe `ARCHITECTURE.md` §28.

**Ölçüm — api34, AAB'den kurulan build, cihaz `ar-EG` (`am get-config`:
`ldrtl`):**

| Ekran | 16i (aynalı) | 16j |
|---|---|---|
| Ana ekran | başlık sağda (920), FAB solda (115), çipler ters | **başlık solda (159), FAB sağda (964), çipler artan** |
| Ekleme sheet'i | — | `[en]` ile **birebir aynı** koordinatlar (para çipleri 139/314/489/665) |
| Tarih seçici | "Select date" sağ üstte, düğmeler sol altta | **"Select date" sol üstte, kalem sağda, düğmeler sağ altta** |
| İstatistik | — | Back solda (74), başlıklar sola yaslı, çubuk soldan doluyor |
| Ayarlar | — | Back solda (74), etiketler sola yaslı, para çipleri artan |
| Kaydırarak silme | hiç ölçülmedi | **çalışıyor** — satır sola kayıyor, çöp ikonu sonda |
| Geri alma Snackbar'ı | hiç ölçülmedi | **çıkıyor** — "Subscription deleted" solda, "Undo" sağda |

**api24 (minSdk), `ar-EG`:** aynı sonuç. `config` yine `ldrtl` diyor ama
uygulama soldan sağa: başlık 122'de, FAB 632'de, çipler 81/267/513/694.

**Biçim beklendiği gibi cihazdan gelmeye devam ediyor:** tutar `٠٫٠٠ ₺`
(Arap-Hint rakamları), tarih seçicide ay adı `سبتمبر ٢٠٢٦` ve hafta cumartesi
başlıyor. `supportsRtl` yalnızca düzen yönünü kapatıyor, yerel ayarı değil.

`[tr]` ve `[en]` satırları 16i'deki ağaçlarla **aynı koordinatlarda** çıktı;
metin ve sayı biçimi de aynı (`₺159,99` / `₺159.99`).

### Bölüm B — izin denetimi (AAB'nin birleşik manifesti)

`bundletool dump manifest` + `manifest-merger-release-report.txt`:

| İzin | Nereden geliyor |
|---|---|
| `android.permission.POST_NOTIFICATIONS` | **uygulamanın kendi manifesti** (satır 7); `androidx.work:work-runtime:2.11.2` de aynı satırı getiriyor |
| `android.permission.WAKE_LOCK` | `androidx.work:work-runtime:2.11.2` |
| `android.permission.ACCESS_NETWORK_STATE` | `androidx.work:work-runtime:2.11.2` |
| `android.permission.RECEIVE_BOOT_COMPLETED` | `androidx.work:work-runtime:2.11.2` |
| `android.permission.FOREGROUND_SERVICE` | `androidx.work:work-runtime:2.11.2` |
| `${applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | `androidx.core:core:1.17.0` (ve `androidx.lifecycle:lifecycle-process:2.10.0`). Kendi tanımladığı, `signature` düzeyinde bir izin — başka uygulamaya bir şey açmıyor |

**`android.permission.INTERNET` YOK. `com.google.android.gms.permission.AD_ID`
YOK.** Durma koşulu oluşmadı. Kaynak kodda da ağ çağrısı yok (OkHttp, Retrofit,
`java.net`, `HttpURLConnection`, WebView taraması boş döndü) ve reklam/analitik
bağımlılığı tanımlı değil.

WorkManager'ın getirdiği dört izin 16g'de de kayıtlıydı; izin beyanında ve
gizlilik politikasında bunların açıklanması gereği duruyor.

### Bölüm C — mağaza metni iddia denetimi

| # | İddia | Sonuç | Kanıt |
|---|---|---|---|
| a | Aylık/yıllık/haftalık; yıllık ve haftalık aylığa çevriliyor | **doğru** | `BillingPeriod` `MONTHLY(12)`, `YEARLY(1)`, `WEEKLY(52)`; çevrim `CurrencyConverter` (`paymentsPerYear` × `partsOfAYear`). Testler: `PeriodNormalisationTest.weeklyPrice_isFiftyTwoPaymentsOverTwelveMonths`, `.yearlyPrice_thatDoesNotDivide_roundsHalfUp`, `.mixedPeriods_addUpWithoutDrift` |
| b | TRY, USD, EUR, GBP | **doğru** | `Currency` enum'unda tam olarak bu dördü |
| c | Kurları kullanıcı giriyor, canlı kur yok | **doğru** | `SettingsRepository.setRate` / `resetRates`; `ExchangeRateTable.Default` uygulamayla gelen tahmin. Ağ kodu yok, `INTERNET` izni yok |
| d | Kategoriler ve kategoriye göre filtre | **doğru** | `SubscriptionCategory`: `ENTERTAINMENT`, `PRODUCTIVITY`, `HEALTH`, `OTHER`; `CategoryFilterBar` |
| e | Aylık/yıllık toplam görünümü arasında geçiş | **doğru** | `TotalPeriod` `MONTHLY(12)` / `YEARLY(1)`; cihazda ölçüldü |
| f | İstatistik: kategori dağılımı, en pahalı, aylık trend, geçen aya göre değişim tek satırda | **doğru** | `SubscriptionStatistics.byCategory` / `.mostExpensive`, `MonthlyTrend.series` / `.changeSince`; `MonthlyChangeRow` tek `Row` içinde tek `Text` |
| g | Hatırlatma ödemeden bir gün önce ve ödeme günü | **doğru** | `PaymentReminderSelection.UPCOMING_WITHIN_DAYS = 1L`; `DueToday` veya `Upcoming(days <= 1)`. Testler: `on_paymentIsToday_isSelectedAsDueToday`, `on_paymentIsTomorrow_isSelectedAsUpcoming`, `on_paymentIsTwoDaysAway_isNotSelected` |
| h | Günde en fazla bir bildirim, abonelik başına değil | **doğru** | `PaymentReminderScheduler` günlük periyodik iş; `PaymentReminderWorker` `lastNotifiedDay()` ile aynı günü ikinci kez bildirmiyor; `PaymentReminderNotifier` tek `NOTIFICATION_ID = 1` kullanıyor ve bütün abonelikleri tek gövdede birleştiriyor |
| i | Açık/koyu tema; duvar kâğıdı renkleri yalnızca Android 12+ | **doğru** | `ThemeMode` `SYSTEM`/`LIGHT`/`DARK`; `DynamicColorSupport.isAvailableOnThisBuild()` = `SDK_INT >= VERSION_CODES.S` (API 31 = Android 12) |
| j | Uygulama dilleri Türkçe ve İngilizce | **doğru** | `values/` (İngilizce, varsayılan) + `values-tr/`; `localeFilters = [en, tr]`; AAB'de tek nitelikli locale `tr` |
| k | Hesap/kayıt yok, reklam yok | **doğru** | Kod tabanında kimlik doğrulama yok; reklam/analitik bağımlılığı yok; `AD_ID` izni yok |
| l | Veri cihazda kalıyor; yalnızca Auto Backup ile kullanıcının Google yedeğine gidiyor | **kısmen** | Ağ kodu ve `INTERNET` izni yok, veri Room + DataStore'da. **Ama Auto Backup iki hedef tanımlıyor**, biri metinde geçmiyor |

**(l) için ayrıntı — metni sen düzelteceksin, koda dokunmadım.**
`data_extraction_rules.xml` iki blok içeriyor:

- `<cloud-backup>` — kullanıcının kendi Google yedeği. Metin bunu anlatıyor.
- `<device-transfer>` — **eski telefondan yeni telefona doğrudan aktarım.**
  Aynı iki yol (`database`, `datastore`) burada da listeli.

İkisi de Auto Backup çerçevesinin parçası ve ikisi de kullanıcının kendi
cihazları arasında kalıyor; veri yine üçüncü bir tarafa gitmiyor. Ama "yalnızca
Google yedeğine gidiyor" cümlesi cihazdan cihaza aktarımı kapsamıyor. Öneri:
cümleye "veya yeni telefon kurulumunda doğrudan cihazdan cihaza" eklensin.

### Bölüm D — yayın adayı AAB

Üretim commit'i **`a16e299`** (belge commit'i). Commit edilmedi.

| Alan | Değer |
|---|---|
| Boyut | **4.575.367 B**, 16i'ye göre **+20 B** |
| İmza SHA-256 | `fce85346…26da0` — yükleme anahtarıyla **birebir** |
| `jarsigner -verify` | `jar verified.` |
| Sürüm (AAB manifestinden) | `versionCode=1`, `versionName=1.0` |
| `supportsRtl` (AAB manifestinden) | **`false`** |

16i'de ölçülen ±5 baytlık imza oynaması burada da geçerli; +20 B onun
üstünde, yani fark gerçek ve manifest değişikliğinden geliyor.

### Bölüm E — otomatik testler ve lint

| Koşu | Sonuç |
|---|---|
| `testDebugUnitTest --rerun-tasks` | **330 test, 0 hata, 0 atlanan** |
| `connectedDebugAndroidTest` (`subtrack_wide_api34`) | **19 test, 0 hata, 1 atlanan** (bilinen) |
| `lintDebug --rerun-tasks` | **0 hata, 21 uyarı** — 16i ile aynı, yeni uyarı yok |

**Enstrümantasyon ilk koşuda düştü ve sebebi üründe değildi.** Cihazda 16i'nin
release imzalı AAB kurulumu duruyordu; debug APK onun üzerine kurulamaz. Hemen
sonraki iki koşu geçti. Hipotez sonradan **bilerek doğrulandı**: AAB yeniden
kurulup `connectedDebugAndroidTest` koşulunca aynı hata çıktı —

```
INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.elinacn.subtrack
signatures do not match newer version; ignoring!
Starting 0 tests / Finished 0 tests
```

Kural: enstrümantasyon koşulacaksa cihazda **release imzalı kurulum
bulunmamalı**. Başarısız kurulum paketi de kaldırıyor, yani AAB turu bundan
sonra yeniden kurulumla başlar.

**Değişen dosyalar**
- `app/src/main/AndroidManifest.xml` — `supportsRtl` `true` → `false` + gerekçe yorumu
- `docs/ARCHITECTURE.md` — §28 RTL başlığı karara dönüştü
- `docs/TESTING.md` — tarih seçici diyaloğunun `uiautomator dump`'ta görünmemesi

**Commit'ler**
- `75e86b4` fix: turn off RTL mirroring until an RTL language ships
- `a16e299` docs: record the RTL deferral and the date picker dump blind spot

**Karşılaşılan sorunlar**

- **`bundleRelease` yine host RAM'i tükendiği için düştü** (`hs_err`, "Native
  memory allocation (mmap) failed … G1 virtual space"). Üç emülatör + R8 aynı
  anda sığmıyor; iki emülatör kapatılıp `-Xmx4096m` ile geçti. 16i'de de aynı
  sorun yaşanmıştı — bu artık tekrar eden bir kısıt.
- **XML yorumu başlangıç etiketinin içine yazılamaz.** `supportsRtl` yorumunun
  ilk hâli `<application … >` etiketinin ortasına düştü ve geçersiz XML üretti;
  yorum öğenin üstüne taşındı ve dosya ayrıştırılarak doğrulandı.
- **Arapça arayüzde dil araması Latin harfle sonuç vermiyor.** `[ar]`
  durumundaki Ayarlar'da "Turk" araması boş döndü (liste Arapça adlarla
  sıralı). Yol: önce İngilizceye dön, sıralamayı oradan kur.
- Oturum arasında makine yeniden başladı ve emülatörler kapandı; kurulum ve
  dil ayarı `userdata`'da kalıcı olduğu için tur kaldığı yerden sürdü.

**Bilinen eksikler / sonraki faz için not**

- **Arapça rakamlarla bidi karışması.** `[ar]` cihazda istatistik satırındaki
  tutar ve yüzde, Arap-Hint rakamları LTR paragraf içinde RTL koşu oluşturduğu
  için görsel olarak yer değiştiriyor (`١٠٠ ,₺ ١٥٩,٩٩ %`). Metin İngilizce,
  rakamlar cihazdan. Uygulamanın desteklemediği bir dilde ve `supportsRtl`
  kararıyla ilgisiz; RTL faza girdiğinde bakılacak.
- Tarih seçicide Arapça gün harfleri satırı yedi özdeş glif çiziyor
  (Material3/ICU tarafı, 16i'de de böyleydi).
- Mağaza metninde (l) maddesi cihazdan cihaza aktarımı kapsamıyor — metin
  düzeltmesi kullanıcıda.

---

## [Faz 16i] Varsayılan Kaynak Dili İngilizce Oldu — 2026-09-22

**Durum:** Tamamlandı. Kotlin koduna dokunulmadı; metin içeriği değişmedi
(tek istisna aşağıdaki üç Türkçe `one` girdisi).

**Neden**

16g'nin B6 ölçümü bir yayın engeli buldu. Kök neden dil parçası değil,
**varsayılan kaynak diliydi**: Türkçe metinler nitelikisiz `values/` içindeydi.
Android dil listesini sırayla dener ve hiçbiri eşleşmezse varsayılana düşer,
yani listesinde ne `tr` ne `en` bulunan **her** cihaz uygulamayı Türkçe
görüyordu — uygulama tüm ülkelerde yayınlanacak. Aynı mekanizmanın ters yüzü:
`[tr, en]` listeli bir Türk kullanıcıya İngilizce gidiyordu. Gerekçenin tamamı
`ARCHITECTURE.md` §28.

### Bölüm A — kaynak taşıma

`git mv` ile iki dosya bütün hâlinde yer değiştirdi; git ikisini de **%100
yeniden adlandırma** olarak kaydetti, 0 satır içerik değişimi.

**Taşıma bütünlüğü betikle doğrulandı** (XML ayrıştırıp anahtar anahtar
karşılaştırma):

| Karşılaştırma | Anahtar | Eşleşen | Eksik | Fazla | Beklenmeyen fark |
|---|---|---|---|---|---|
| yeni `values/` ↔ eski `values-en/` | 125 | 125 | 0 | 0 | **0** |
| yeni `values-tr/` ↔ eski `values/` | 125 | 125 | 0 | 0 | **0** |

`values-tr`'de olup varsayılanda olmayan anahtar: **sıfır** (çökme kontrolü).
Tek kasıtlı fark, üç Türkçe `plurals`'a eklenen `one` girdisidir; her biri
kendi `other`'ıyla **bayt bayt aynı**. Türkçede sayıdan sonra isim tekil kalır,
yani iki biçim zaten aynı sözcükler; CLDR `tr`'ye yine de bir `one` kategorisi
veriyor ve lint bunu ancak metinler dilini bildiği bir klasöre girdikten sonra
arıyor. `tools:ignore` kullanılmadı (projede `@SuppressLint` yasak).

**Taranan ve bulunmayanlar:** `translatable="false"` girdi yok; `tools:locale`
hiçbir dosyada yok (bu yüzden eklenmedi); `strings.xml` dışında dile bağlı
kaynak yok — `values/` altındaki `colors.xml` ve `themes.xml` dilden bağımsız,
başka dil nitelikli klasör hiç yok. Test kodunda taşınan dosyaların yoluna
başvuran yer yok, yani görev 3 boş çıktı.

### Bölüm B — paketleme

```kotlin
androidResources { localeFilters += listOf("en", "tr") }
bundle { language { enableSplit = false } }
```

`defaultConfig.resourceConfigurations` ve `resConfigs()` AGP 9'da
`@Deprecated`; mesajları doğrudan `localeFilters`'a yönlendiriyor ve CLAUDE.md
§4 deprecated API yasaklıyor. Önceden tanımlı bir filtre **yoktu**.

Ölçülen etki:

| | 16g | 16i |
|---|---|---|
| AAB'de nitelikli locale | **86** | **1** (`tr`) + nitelikisiz varsayılan (İngilizce) |
| `bundletool dump config` | dil boyutu açık | `"LANGUAGE", "negate": true` |
| APK setindeki parça | `base-master`, `base-<abi>`, dil parçası | **`base-master` + `base-<abi>`** |
| AAB boyutu | 4.657.988 B | **4.575.347 B** (−82.641 B) |

### Bölüm C — yayın adayı AAB

Üretim commit'i **`4726fd4`** (belge commit'i). Commit edilmedi.

| Alan | Değer |
|---|---|
| Boyut | **4.575.347 B**, 16g'ye göre **−82.641 B** |
| İmza SHA-256 | `fce85346…26da0` — yükleme anahtarıyla **birebir**, programla karşılaştırıldı |
| `jarsigner -verify` | `jar verified.` |
| Sürüm (AAB manifestinden) | `versionCode=1`, `versionName=1.0`, `minSdk=24`, `targetSdk=36` |

**AAB boyutu bayt bayt sabit değil.** Altı derleme ölçüldü, sonuç 4.575.344 ile
4.575.353 arasında ±5 bayt oynadı. İçerikten gelmiyor: art arda iki derlemenin
**148 girdisi de** boyut ve CRC olarak eşleşti, fark imza bloğunun uzunluğunda.
Bir turun AAB'si artık "şu kadar bayt" diye doğrulanmaz.

### Bölüm D — dil matrisi (api34, AAB'den kurulan build)

APK seti cihaz dili `[en]` iken **bir kez** kuruldu. `pm path` iki satır:
`base.apk` + `split_config.x86_64.apk` — **dil parçası yok.** Sonraki satırlarda
yalnızca cihazın dil listesi değişti, yeniden kurulum yapılmadı.

| Cihaz listesi (`system_locales`) | Metin | Tarih seçici | Sonuç |
|---|---|---|---|
| `tr-TR` | Türkçe | "Tarih seç", `Eylül 2026`, `P S Ç P C C P` | geçti |
| `tr-TR,en-US` | Türkçe | Türkçe | geçti |
| `en-US` | İngilizce | "Select date", `September 2026` | geçti |
| `de-DE` | **İngilizce** | Başlıklar **İngilizce**, gün harfleri `M D M D F S S` | geçti |
| `de-DE,tr-TR` | Türkçe | Türkçe | geçti |
| `ar-EG` | **İngilizce** | Başlıklar İngilizce, ay `سبتمبر ٢٠٢٦`, rakamlar Arap-Hint | geçti |

`[en]` iken kurulup `[tr]`'ye geçince Türkçe **anında** geldi ve `pm path`
değişmedi — dil parçasının kapatıldığının kanıtı bu.

`[de]` satırındaki İngilizce tarih seçici dil filtresinin kanıtı: 16i'den önce
o diyalog baştan sona Almancaydı.

**api24 (minSdk), `tr-TR,en-US`:** Türkçe. `am get-config` sıralı listeyi
doğruluyor (`tr-rTR,en-rUS`). Kurulum yine iki parça; 16i öncesi o cihazda
`split_config.en.apk` dahil **üç** parça vardı.

### Bölüm E — 117 listesinden metin/biçim/çoğul maddeleri (api34, tr ve en)

Hiçbir ekranda ham anahtar ya da yerine konmamış biçim belirteci görülmedi.

| # | Türkçe cihaz | İngilizce cihaz |
|---|---|---|
| 11 | **`₺219,89`** (159,99 + 59,90) | **`₺219.89`** |
| 16 | tüm metinler Türkçe | tüm metinler İngilizce |
| 17 | "Abonelik adı boş olamaz" / "Fiyat boş olamaz", alan altında | — |
| 27 | **"1 gün kaldı"** (Türkçe tekil doğru) | **"1 day left"** (`one` biçimi) |
| 30 | "Bugün ödenecek" | "Due today" |
| 33 | "Ödeme hatırlatmaları, Açık" | "Payment reminders, On" |
| 47 | "Henüz abonelik yok / Eklemek için + düğmesine dokun" | "No subscriptions yet / Tap + to add one" |
| 64 | **`₺2.638,68`** | **`₺2,638.68`** |
| 79 | "yüzde 100" | "100 percent" |
| 80 | "Netflix, Aylık, ayda ₺159,99" | "Netflix, Monthly, ₺159.99 a month" |
| 82/86 | "Trend için en az iki ay gerekiyor…" | "A trend needs at least two months…" |
| 106 | "Tema, Sistemi takip et" | "Theme, Follow the system" |
| 114 | "Duvar kâğıdı renkleri, Kapalı — uygulamanın kendi paleti" | "Wallpaper colours, Off — the app's own palette" |
| 115 | ₺ $ € £, ISO kodu yok | aynı |
| 116 | `₺219,89` / `₺2.638,68` | `₺219.89` / `₺2,638.68` |
| 25 | "Kurlar hiç düzenlenmedi…", "1 $ = … ₺" | "The rates have never been edited…", "1 $ = … ₺" |

**Türkçe tekil çoğul asıl sınavdı** ve geçti: `one` ile `other` aynı metni
taşıdığı için "1 gün kaldı" doğru çıkıyor, davranış 16i öncesiyle aynı.

### Bölüm F — 16g regresyonunun açıkları kapandı

**#50 neden "kısmen":** madde "boş durum yükleme sırasında **da** görünmüyor"
diyor; kararlı hâl ölçülebiliyor ama açılış karesi ölçülemiyor, çünkü elimizdeki
en hızlı gözlem aracı olan `uiautomator dump` 3,3 saniye sürüyor ve yakalaması
gereken kare ondan kat kat kısa.

**#75 — geçti.** api34 debug build (`run-as` release'de yok). Çıpa 11 Eylül 2026,
aylık; kart **"20 days left"** diyor (sonraki ödeme 11 Ekim). Veritabanındaki
`nextPaymentDate` = `1789084800000` = **2026-09-11T00:00:00Z**, yani girilen
çıpanın kendisi. İlerletme yalnızca ekranda.

**#105 — geçti.** Aynı aboneliğin fiyatı 159,99 → 200,00 yapıldı.
`monthly_snapshots` **tek satır**: `202609 | 20000 | TRY`. Kaydedici
güncellemeyi görüyor.

### Bölüm G — otomatik testler ve lint

| Koşu | Sonuç |
|---|---|
| `testDebugUnitTest --rerun-tasks` | **330 test, 0 hata, 0 atlanan** |
| Aynı paket, JVM varsayılan dili **tr-TR** | **330 test, 0 hata** |
| `connectedDebugAndroidTest` (`subtrack_wide_api34`) | **19 test, 0 hata, 1 atlanan** (bilinen) |
| `lintDebug --rerun-tasks` | **0 hata, 21 uyarı** |

Türkçe koşu repoya dokunmadan yapıldı: geçici bir init script (`-I`) yalnızca
`Test` görevlerine `-Duser.language=tr -Duser.country=TR` verdi; `--info`
çıktısındaki worker komut satırı bunu doğruluyor. Kotlin/KSP bu ayarda hiçbir
hata üretmedi.

**Lint sayısı 22'den 21'e düştü, artmadı.** `MissingQuantity`,
`MissingTranslation`, `ExtraTranslation`, `MissingDefaultResource`
**çıkmıyor**. Düşen uyarı, Türkçe `error_date_too_far`'ın (`%1$d yıl`)
üzerindeki `PluralsCandidate` yanlış alarmıydı: lint artık o dosyanın Türkçe
olduğunu biliyor ve İngilizce sezgisini uygulamıyor.

**İki bilinen uyarı — kabul edildi, susturulmadı:**

1. `values/strings.xml` `statistics_category_description` (`%3$d percent`) —
   **yanlış alarm.** İngilizcede "percent" sayıyla değişmez ("1 percent",
   "5 percent").
2. `values/strings.xml` `error_date_too_far` (`%1$d years`) — argüman
   `SubscriptionInput.MAX_YEARS_AHEAD = 10L` sabiti ve tek çağıran
   `FormErrors.kt`. Değer **her zaman 10**, yani "1 years" hiç oluşamaz.

Plurals'a çevirmiyoruz: hem metin hem Kotlin kodu değişikliği ister
(`stringResource` → `pluralStringResource`) ve bu turun kapsamı dışında.
**Beklenen lint tablosu bundan sonra: 0 hata, 21 uyarı.**

**Yerel ayara bağlı çağrı taraması.** `app/src` altındaki tüm Kotlin kodu
tarandı. Biçimlendirmenin tamamı `LocalConfiguration.current.locales[0]`'ı
açıkça alıp geçiriyor (`MoneyFormatter`, `MonthFormatter`,
`rememberDateFormatter`, `ExchangeRatesScreen.updatedAtText`). Yerel ayarsız
tek çağrı `SubscriptionCard.iconFor`'daki `name.lowercase()` ve o **güvenli**:
Kotlin'in argümansız `lowercase()`'i yerel ayardan bağımsızdır, yani Türkçe
cihazda "ICLOUD" → "ıcloud" olup ikonu kaybetmiyor. Java'nın `toLowerCase()`'i
kullanılsaydı tam bu hata çıkardı. `Locale.forLanguageTag("tr-TR")` sabiti
yalnızca bir `@Preview` içinde.

**Değişen dosyalar**
- `app/src/main/res/values/strings.xml` — İngilizce (eski `values-en/`)
- `app/src/main/res/values-tr/strings.xml` — Türkçe (eski `values/`) + üç `one`
- `app/src/main/res/values-en/` — kaldırıldı
- `app/build.gradle.kts` — `androidResources.localeFilters`, `bundle.language`
- `docs/ARCHITECTURE.md` — §28 yeni, §26 güncellendi
- `docs/TESTING.md` — dil bölümü, AAB parça sayısı, iki yeni ölçüm notu
- `docs/ROADMAP.md` — Faz 16i satırı

**Commit'ler**
- `f4d8ed2` refactor: make English the default string resource locale
- `657aa69` build: keep only en and tr resources and put both in the base
- `4726fd4` docs: record the localisation decisions and what they change

**Karşılaşılan sorunlar**

- **`bundleRelease` iki kez host RAM'i tükendiği için düştü** (`hs_err`
  dosyası, "Native memory allocation (mmap) failed … G1 virtual space"). Üç
  emülatör + Android Studio + R8 aynı anda sığmıyor. Çözüm:
  `-Dorg.gradle.jvmargs=-Xmx4096m` ve derleme sırasında bir emülatörü kapatmak.
- **Tarih seçici diyaloğu `uiautomator dump` çıktısında hiç görünmüyor** —
  ayrı bir pencerede çiziliyor, dump uygulama penceresini döndürüyor. İlk
  denemelerde "seçici açılmadı" sanıldı, oysa açıktı. Bu diyalog **yalnızca
  ekran görüntüsüyle** doğrulanır. (Kapsam dışı olduğu için TESTING'e
  yazılmadı; yazılmaya değer.)
- **`am start`'tan sonraki ilk dokunuş sıklıkla yutuluyor.** Açılış animasyonu
  bitmeden giden `input tap` hiçbir şey yapmıyor; her açılıştan sonra dokunuş
  dump ile doğrulanıp gerekirse tekrarlandı.
- **Cihaz dili kabuktan kurulamıyor.** `setprop persist.sys.locale` "Failed to
  set property" diyor, `adb root` "cannot run as root in production builds",
  `settings put system system_locales` değeri yazıyor ama uygulamıyor
  (`am get-config` eski dilde kalıyor), `cmd locale` yalnızca uygulama dilini
  biliyor. Tek yol Ayarlar arayüzü — matrisin altı satırı böyle kuruldu.
- Oturum ortasında makine yeniden başladı ve üç emülatör de kapandı. Kurulum,
  dil listesi ve tema ayarı `userdata`'da kalıcı olduğu için tur kaldığı
  yerden sürdü.

**Bir önceki tur için düzeltmeler**

- **16g'nin "api29'da koyu temayı kapatmak Ayarlar → Ekran ile yeniden
  başlatmasız çalışıyor, reboot yolu yalnızca açmak için" notu eksikti.**
  Yeniden ölçüldü: anahtar **iki yönde de** yeniden başlatma istemiyor
  (kapatma `2/0x21` → `1/0x11`, açma tam tersi). Kilitli olan Ayarlar değil,
  kabuk komutu: aynı turda `cmd uimode night no` yine iş görmedi. TESTING'e
  tabloyla yazıldı.
- **Bu turun ilk raporunda "iki yeni PluralsCandidate uyarısı" denmişti,
  yanlıştı.** Temel çizgi `HEAD~1`'de ölçülünce ikisinin de taşımadan önce
  `values-en/strings.xml`'de var olduğu görüldü. Taşıma lint'e uyarı eklemedi.
- **ARCHITECTURE §28'in ilk hâli "`[de, tr]`'de metin Türkçe, biçim Almanca
  olur" diyordu; ölçüm bunu çürüttü.** Android, uygulamaya verdiği
  Configuration'ın dil listesini uygulamanın kaynağı olan dillere göre
  süzüyor: eşleşen bir dil varsa `locales[0]` o oluyor ve **biçim de** onunla
  geliyor. `[de, tr]`'de hem metin hem biçim Türkçe. Metin ile biçim yalnızca
  hiçbir dilin eşleşmediği listelerde ayrışıyor (`[de]`, `[ar]`). §28 ölçülen
  tabloyla düzeltildi.

**Bilinen eksikler / sonraki faz için not**

- **RTL denenmemiş bir yol.** `[ar]` listesinde düzen aynalanıyor (`ldrtl`,
  FAB sol alta, çipler ters sırada) ama hiçbir ekran RTL için tasarlanmadı ve
  Arapça çeviri de yok. 16i'de ölçüldü, dokunulmadı. Ayrıca Arapça takvimde
  gün harfleri satırı yedi özdeş glif olarak çiziliyor — Material3/ICU
  tarafında, uygulamanın desteklemediği bir dilde.
- `localeConfig` v1.0'da yok; dil seçimi ayrı bir özellik faza kalıyor.
- Mağaza ekran görüntüleri yeniden çekilmedi — metinler değişmedi.

---

## [Faz 16g-2] Yayın Adayı AAB Yeniden Üretildi ve Üç Cihazda Sürüldü — 2026-09-21

**Durum:** Tamamlandı. Kaynak koda dokunulmadı; bu tur ölçümdür.

**Neden**

16h serisi manifesti (`Theme.SubTrack.Starting`), temayı ve bağımlılıkları
(`core-splashscreen` 1.2.0) değiştirdi. 16g'de üretilen AAB bunların hiçbirini
taşımıyordu, yani Play'e yüklenecek şey bayattı.

### Bölüm A — 16h'den kalan üç belge artığı

1. **`SystemBarsFollowTheTheme` KDoc'u düzeltildi.** "The launch window's own
   style, set in `onCreate`, covers that gap" cümlesi §23 ile çelişiyordu.
   Ölçülen gerçek: devir boyunca durum çubuğunun sahibi splash penceresi ve
   `Theme.SubTrack.Starting`; `onCreate`'teki `auto` stili uygulama
   penceresini kapsıyor — normal yolda devirden sonraki birkaç kare, kapının
   1000 ms son tarihi dolarsa **her** kare. Yalnızca yorum değişti.
2. **TESTING.md'ye api29 sistem koyu tema yöntemi.** `cmd uimode night` bu
   imajda cevap veriyor ama uygulamıyor (`mNightModeLocked=true`); çalışan yol
   `settings put secure ui_night_mode <2|1>` + `adb reboot`, doğrulaması
   `dumpsys uimode` (`mCurUiMode` `0x21` koyu / `0x11` açık). `adb shell stop;
   start` bu imajda mümkün değil ("must be root"), yani tam yeniden başlatma
   gerekiyor. 16h-2'nin ilk turunda bu atlandığı için dört hücrenin yanlış
   sistem temasıyla ölçülüp atıldığı da not edildi.
3. **ROADMAP'e Faz 16h satırı.** 16g'den sonraki boşluk kapandı; satır
   PROGRESS'in 16h/16h-1/16h-2 kayıtlarına ve ARCHITECTURE §23'e yönlendiriyor.

### Bölüm B — yayın adayı AAB

**Üretim.** `./gradlew :app:bundleRelease`, commit **`f30e6c5`** üzerinde
(Bölüm A commit'i). Çıktı `app/build/outputs/bundle/release/app-release.aab`,
**4.657.988 B**. Commit edilmedi (`*.aab` `.gitignore`'da), `git status` temiz.

**İmza — eşleşti.** `keytool -printcert -jarfile` sertifikası:

| Alan | Değer |
|---|---|
| Sahip | `CN=ElinaDorothea, OU=Development, O=SubTrack, L=Denizli, ST=Denizli, C=TR` |
| SHA-256 | `fce85346c7e1a09e68bf86428cb8061bb22f6989c1c35711e4499fb111d26da0` |
| SHA-1 | `cb926a75daf595ceda04c6268e2d9be678e80e8a` |
| Algoritma | SHA384withRSA, 2048-bit RSA |
| Geçerlilik | 2026-09-17 → 2054-02-02 |

Parmak izi 16g'de kaydedilen yükleme anahtarıyla **birebir aynı**; program
olarak karşılaştırıldı. `jarsigner -verify` → `jar verified.`

**Sürüm — AAB'nin kendi manifestinden okundu**, build dosyasından değil:

```
bundletool dump manifest --bundle app-release.aab
→ android:versionCode="1" android:versionName="1.0"
  minSdkVersion="24" targetSdkVersion="36" compileSdkVersion="36"
```

**Üç cihaza kurulum — parça sayısı 16g ile aynı.** `bundletool build-apks` +
`install-apks`, her cihazda **üç parça**:

| Cihaz | `pm path` | `dumpsys package` |
|---|---|---|
| `subtrack_min_api24` | `base.apk` + `split_config.en.apk` + `split_config.x86_64.apk` | versionCode=1 versionName=1.0 minSdk=24 |
| `subtrack_narrow_api29` | aynı üç parça | versionCode=1 versionName=1.0 minSdk=29 |
| `subtrack_wide_api34` | aynı üç parça | versionCode=1 versionName=1.0 minSdk=32 |

`minSdk` sütunu cihaza göre değişiyor çünkü bundletool base APK'nın SDK'ya
göre budanmış varyantını gönderiyor; AAB'nin kendi `uses-sdk`'sı 24.

**Boyut — fark iki kaynağa ayrıldı.** 16g'nin 4.598.466 B'ıyla arasındaki
**+59.522 B** (%1,29) tek başına `core-splashscreen` değil: o ölçümden sonra
16d ikonları da değişti. Ara nokta ölçüldü — `047cb0b` (ikonlar girmiş,
splashscreen girmemiş) ayrı bir worktree'de aynı yapılandırmayla derlendi:

| Commit | AAB | Fark | Kaynak |
|---|---|---|---|
| 16g | 4.598.466 B | — | — |
| `047cb0b` | 4.648.781 B | **+50.315 B** | 16d ikon işi (on iki para halkası + bildirim ikonu) |
| `f30e6c5` | 4.657.988 B | **+9.207 B** | `core-splashscreen` 1.2.0 + 16h kodu |

Yani splashscreen'in bedeli ~9 KB; artışın beşte dördü ikonlardan geliyor.

### Dil parçası riski — ölçüldü, düzeltilmedi

**`localeConfig` manifestte YOK.** AAB'nin birleşik manifestinde
`android:localeConfig` geçmiyor ve `res/xml/` altında `locales_config.xml`
yok. Sonucu cihazda doğrulandı: api34'te Ayarlar → Uygulamalar → SubTrack
sayfasında **"Dil" satırı çıkmıyor**. Yani kullanıcı API 33+'ta sistem
ayarlarından uygulamanın dilini **seçemiyor**; uygulamanın kendi dil ayarı da
yok. Bu duruma ulaşmanın tek yolu `cmd locale set-app-locales`.

**Yön 1 — cihaz yalnızca `tr-TR`, uygulama EN'e çevrildi.** Cihaz dili
Ayarlar'dan tek dile indirildi (`persist.sys.locale=tr-TR`), **sonra**
`build-apks --connected-device` ile üretildi:

```
pm path → base.apk + split_config.tr.apk + split_config.x86_64.apk
```

`split_config.en` **kurulmadı**. `cmd locale set-app-locales … --locales en`
kabul edildi (`Locales for com.elinacn.subtrack for user 0 are [en]`) ama
**metinler Türkçe kaldı** — yalnızca sayı biçimi değişti (`₺0,00` → `₺0.00`).
Yani riskin mekanizması gerçek.

**Yön 2 — cihaz yalnızca `en-US`, uygulama TR'ye çevrildi.** Bu sefer
`split_config.en` kuruldu, ve sonuç **yine İngilizce**: uygulama dili `tr-TR`
verilse de metinler çevrilmedi. Sebep parça değil kaynak niteleyicisi —
Türkçe metinler nitelikisiz `values/`'ta, yani çözümlemede **son** sıradaki
yedek. Dil listesi `[tr-TR, en-US]` olunca `values-en` kazanıyor.

**Sonuç:** uygulama dili tercihinin metinlere etkisi **iki yönde de yok**.
Kullanıcıya açık bir yol olmadığı için (localeConfig yok, uygulama içi dil
seçici yok) bugün kullanıcıya ulaşan bir kusur değil. Karar kullanıcının;
build yapılandırmasına ve kaynaklara dokunulmadı.

### Regresyon — karar kuralının ikinci dalı

PROGRESS'e bakıldı: 117 maddelik listenin **son tam koşusu Faz 16b**
(2026-09-16), **debug build üzerinde** ve **R8 öncesinde** — `isMinifyEnabled`
bir gün sonra 16c'de açıldı. Yani kuralın ikinci dalı geçerli:
**api34'te 117 maddenin tamamı**, api29 ve api24'te alt küme.

**api34 — 117 madde, AAB'den kurulan build üzerinde.**

| Sonuç | Madde |
|---|---|
| **geçti (108)** | #1–#39, #41–#45, #47–#49, #51–#74, #76–#83, #85–#94, #96–#103, #106–#113, #115–#117 |
| **kısmen (4)** | #50 (açılışta sekiz ardışık dump'ın hiçbirinde boş durum yok; ilk kare hâlâ örneklenemiyor), #84, #95, #104 (ağaç tarafı doğru, TalkBack imajda yok) |
| **ölçülemedi (2)** | #75, #105 — ikisi de `run-as` istiyor, release APK'da yok (`package not debuggable`) |
| **geçerli değil (3)** | #40, #46 (API < 33), #114 (API < 31) |

Ölçülen sayılardan bazıları: #11 toplam **219,89**; #63 **243,33**; #64 yıllık
**2.920,00**; #22 kur 50'ye çekilince **643,33**; #21 ana para birimi USD →
**$12,87**; #79 yüzdeler **79+15+6 = 100** ve **70+30 = 100**; #85 çubuk
`#D4AF37` / iz `#3A5A48` = **3,65:1**; #15 arka plan `#0D1A14` / kart
`#1F3D2D` = **1,50:1**, vurgu altın `#D4AF37`, **hiçbir yerde mor yok**
(1025 örneklenmiş renkte sıfır); #113 duvar kâğıdı paletinde çubuk `#B2C5FF`
/ iz `#45464F` = **5,50:1**.

Tarih maddeleri cihaz saatine (21 Eylül 2026) göre yeniden hesaplanıp
doğrulandı: #27 **19 gün**, #29/#69 **20 gün**, #70 haftalık **4 gün** ve
yıllık **355 gün**, #71 (çıpa 3 Ocak 2024) **2 gün**, #72 (çıpa 31 Ocak 2026)
**9 gün** — adım adım kırpılsaydı 7 çıkardı, yani madde hâlâ ayırt ediyor.

Trend maddeleri saat ay ay ileri alınarak sürüldü (Eki → Kas → *(Ara
atlandı)* → Oca → Şub → Mar): #87 iki sütun, #88 yalnızca **son altı ay**
(Eylül düşüyor), #89 Aralık yuvası duruyor ama **hiçbir şey çizilmiyor**,
#90 Şubat'ta **yalnızca iz**, #91 iki yönde de cümle (`₺428.50 less` /
`₺200.00 more`), #92 "Unchanged", #93 karşılaştırma **hiç yok**, #94 para
birimi değişince "4 months recorded in another currency are not shown".

Bildirim api34'te gerçekten tetiklendi (saat 08:30'a alınıp uygulama
açıldıktan sonra 09:05'e alınıp iş zorlandı): `Payment reminder: 1
subscription` / `NotifyMe — today`.

**api29 ve api24 — alt küme (açılış, tema, bildirim, klavye).**

| Alan | Madde | api29 | api24 |
|---|---|---|---|
| Açılış | #1, #12, #47, #110 | geçti | geçti |
| Tema | #15, #85, #106, #108 | geçti | geçti |
| Tema | #107, #109 | geçti | geçerli değil (Android 7.0'da sistem koyu teması yok) |
| Tema | #114 | geçti | geçti |
| Bildirim | #33, #38, #40, #46 | geçti | geçti |
| Bildirim | #39 | geçti | geçerli değil (kanallar API 26+) |
| Klavye | #26 | geçti | geçti |

#110 ikisinde de kare kare ölçüldü ve **16h'nin asıl sınavı budur:** koyu tema
saklıyken, sistem açıkken, açılış kareleri sırayla launcher → splash
(`#0D1A14` zemin + `#D4AF37` işaret, iki kare) → devir → uygulama koyu
(`#0D1A14` / `#1F3D2D` / `#14523A`). **Yanlış temada tek kare yok**, api24 dahil.

#85 üç cihazda da aynı sayıyı veriyor: `#D4AF37` / `#3A5A48`. #26 ikisinde de
klavye açıkken (`mInputShown=true`) tek fiskede hem Kaydet hem "Varsayılana
dön"e ulaşılıyor; api29'da Kaydet `[329,1044][391,1084]`, 16b'nin kaydettiği
dar cihaz koordinatıyla aynı satırda.

Bildirim api29 ve api24'te de tetiklendi, ikisinde de aynı metin. Üç cihazda
`logcat -b crash` **boş (0 satır)**.

**Fiziksel cihaz (B8) yapılamadı** — `P7KROVRSVWYTW869` bu oturum boyunca
`adb devices`'ta hiç görünmedi. AAB'den kurulan build ona kurulmadı.

**Otomatik doğrulamalar**

| Komut | Sonuç |
|---|---|
| `testDebugUnitTest --rerun-tasks` | **330 test, 0 hata, 0 atlanan** |
| `connectedDebugAndroidTest` (api34) | **19 test, 0 hata, 1 atlanan** |
| `lintDebug --rerun-tasks` | **22 bulgu**, hepsi uyarı (12 `GradleDependency`, 3 `InlinedApi`, 3 `PluralsCandidate`, 2 `NewerVersionAvailable`, 1 `RedundantLabel`, 1 `AndroidGradlePluginVersion`) |
| `assembleDebug` (Bölüm A sonrası) | geçti, yeni uyarı yok |

**Değişen dosyalar**
- `app/src/main/java/com/elinacn/subtrack/MainActivity.kt` — yalnızca
  `SystemBarsFollowTheTheme` KDoc'u
- `docs/TESTING.md` — api29 koyu tema yöntemi; #107/#109 satırı güncellendi
- `docs/ROADMAP.md` — Faz 16h satırı
- `docs/PROGRESS.md` — bu kayıt

**Commit'ler**
- `f30e6c5` docs: correct the status bar KDoc and record what 16h left open
- *(bu kayıt)* docs: record the rebuilt release AAB and its verification round

**Karşılaşılan sorunlar**
- **Tarih maskesi cihaz diline bağlı.** api34 `en-US`'teyken seçicinin metin
  girişi `MM/DD/YYYY` istiyor; `11092026` 9 Kasım olarak ayrıştırıldı ve bir
  çıpa yanlış kuruldu. TESTING.md yalnızca `DD.MM.YYYY` / `DDMM/YYYY`
  maskelerini yazıyor. Ölçüm düzeltilip yeniden koşuldu.
- **16g'nin klavye tuzağı aynen tekrarlandı.** Ad alanına yazdıktan sonra
  eski koordinatla dokunmak para birimini EUR'ya atlattı — TESTING.md bunu
  16g'den beri yazıyor. Dokunuş sırası, her alandan sonra yeniden dump
  alacak şekilde değiştirildi.
- **api29'da sistem temasını canlı *kapatmak* mümkün, açmak değil.** Ayarlar →
  Ekran → Koyu tema anahtarı koyudan açığa geçişi anında yapıyor (aynı pid);
  ters yön `mNightModeLocked=true` yüzünden tutmuyor, oraya yeniden başlatma
  gerekiyor. #109 bu sayede api29'da **canlı** ölçüldü.
- **`install-apks` sessizce düşebiliyor.** MSYS altında jar yolu bozulunca
  komut hiçbir şey kurmadan dönüyor; her kurulum `pm path` ile doğrulandı.
- Enstrümantasyonda atlanan test
  **`MonthlySnapshotDaoTest.observeAll_emptyTable_emitsAnEmptyList`**; 16b
  kaydındaki atlanan test (`reminderWorker_notificationsDisabled_…`) bu turda
  koştu. Atlanan **sayısı** değişmedi (1), atlanan **madde** bildirim
  durumuna göre değişiyor.

**Sonraki faz için not**
- AAB Play'e yüklenmeye hazır: imza doğru, sürüm doğru, üç cihazda üç parça
  geliyor. Fiziksel cihaz turu (B8) hâlâ açık.
- Dil parçası bulgusu karar bekliyor: Türkçe metinleri `values-tr`'ye almak
  ve `localeConfig` eklemek iki ayrı karar, ikisi de bu turda **yapılmadı**.
- TESTING.md iki eksik taşıyor: tarih maskesinin cihaz diline bağlı olduğu,
  ve api29'da koyu temayı **kapatmanın** yeniden başlatma gerektirmediği.

---

## [Faz 16h-2] Devir Rampası Kabul Edildi, Son Tarih Yolu Kapatıldı — 2026-09-21

**Durum:** Tamamlandı

**Neden**

16h-1'in "kapsam dışı, karar bekliyor" maddesi karara bağlandı. İki iş var ve
birbirinden bağımsız: **açılış devrindeki görünmez durum çubuğu kusuru kabul
edildi** (kod değişmedi, belgelendi), **ayrı bir gerçek hata kapatıldı**
(`onCreate`'teki geçici stil `dark` → `auto`).

16h-1'in bu konudaki teşhisi ölçümle çürüdü. O kayıt görünmezliğin sebebini
"geçici `SystemBarStyle.dark`'ın uygulama penceresinin ilk karelerinde hâlâ
yürürlükte olması" diye yazıyor. Değil: `dumpsys window` ile okunduğunda `dark`
uygulama penceresine yanlış bayrakla doğuyor ama ~350 ms sonra, **hâlâ splash
ekrandayken** düzeliyor; devir anına gelindiğinde iki stil de doğru bayrağı
taşıyor. Görünmezliğin sebebi başka: zemin tek karede beyaza dönüyor, SystemUI
ikon tonunu ise bir animasyonla götürüyor.

---

### Görev 1 — kusurun mekanizması ve kabulü

Devir boyunca durum çubuğunun sahibi splash penceresi; cevabı "zemin koyu"
olduğu için ikonlar beyaz. Splash düşünce zemin **anında** uygulamanın beyaz
yüzeyine dönüyor, ikon tonu ise beyazdan `#666666`'ya rampalıyor ve rampa beyaz
yüzey ekrana geldikten sonra yürüyor. En kötü kare **1,00:1**, süre 200–350 ms,
**yalnızca açık uygulama temasında**, sistem temasından bağımsız, api29 ve
api34'te aynı.

**Kabul edildi, kodla kapatılmayacak.** Devri geciktirmek çözmüyor (rampa
splash düşünce başlıyor); açık splash zemini sorunu taşıyor, çözmüyor (splash
uygulamanın tercihini okumadan çiziliyor, ayrıca §27'nin tek ikon varyantı
kararını bozar); `SystemBarStyle` ile de kapatılamaz (rampa SystemUI'ın kendi
animasyonu). Gerekçeler ve ölçüm ARCHITECTURE **§23**'te, "Devir rampası —
bilinen, kabul edilmiş davranış" başlığı altında.

**16h-1'in api29 sayısı düzeltildi.** O kayıt api29'da en düşük değerin
4,04:1 olduğunu, yani orada görünmezlik ölçülmediğini söylüyordu. Üç tekrarlı
ince örneklemede api29 da **1,00:1**'e iniyor. Düzeltme §23'e yazıldı; 16h-1
kaydının kendi metni, eski kayıtlar silinmez kuralı gereği olduğu gibi duruyor.

---

### Görev 2 — `onCreate`'teki stil `auto`

Tercih 1000 ms'de gelmezse kapı açılıyor ve uygulama `ThemeMode.Default` ile
çiziyor. O yolda `SystemBarsFollowTheTheme` hiçbir şey uygulamamış oluyor
(`isThemeKnown` false), pencere `onCreate`'teki değerde kalıyor. `Default`
sistemi izlediğine göre onunla anlaşan tek stil de sistemi izleyen stil.

Tercih okuması geçici olarak 5 s geciktirilip ölçüldü (yama turun sonunda
kaldırıldı; `MainViewModel` depoda değişmedi). Kapı açıldıktan sonra, tercih
hâlâ gelmemişken:

| Cihaz | Sistem | `dark` | `auto` |
|---|---|---|---|
| api29 | açık | **1,00:1**, okuma gelene kadar sürüyor | **5,74:1** |
| api29 | koyu | 11,91:1 | 11,91:1 |
| api34 | açık | **1,00:1**, sürüyor | **5,74:1** |
| api34 | koyu | 11,91:1 | 11,91:1 |

Normal açılışta fark yok: iki cihaz × dört kombinasyon × iki stil, her hücre üç
tekrar. Açık temanın hücrelerinde iki stil de aynı rampayı, aynı ton kümesiyle
örnekliyor — tek tek sayılar örnekleme fazına göre oynuyor, ikisi de bazı
koşularda 1,00:1'e iniyor. Koyu temanın hiçbir hücresi 8,65:1'in altına
inmiyor. Kararlı durum her yerde açıkta 5,74:1 / koyuda 11,91:1. Tam tablo
§23'te.

---

### Doğrulama

- `testDebugUnitTest --rerun-tasks` — **330 test, 0 hata, 0 atlanan.**
- `connectedDebugAndroidTest` (api34) — **19 test, 0 hata, 1 atlanan** (bilinen
  `PaymentReminderWorkerTest.reminderWorker_notificationsDisabled_*`).
- `lintDebug --rerun-tasks` — **22 bulgu**, hepsi uyarı, yeni bulgu yok.
- `assembleDebug --rerun-tasks` ve `assembleRelease` geçti; release APK
  **2.184.114 B** — taban değerle birebir aynı, `auto` boyutu değiştirmiyor.
- Splash karesi iki cihazda da **17,87:1**; api34 splash karesinin gövde
  istatistiği 16h-1'in depodaki dosyasıyla aynı (`#0D1A14` %96,8).
- api24 (minSdk) kararlı durum: uygulama açık temada `#656565` üstünde beyaz
  bant **5,83:1**, koyu temada `#FFFFFF` üstünde `#1F3D2D` **11,91:1** — ikisi
  de okunur.
- AAB üretilmedi; 16g ayrı tur.

---

### Ekran görüntüleri

`docs/screenshots/phase-16h-2/` altında dokuz dosya:
`handover-api34-light-{1..7}.png` (rampanın ham kare dizisi, §23'teki tablonun
kanıtı) ve `deadline-api34-{dark,auto}-syslight.png` (son tarih yolunda iki
stilin yan yana görüntüsü).

---

### Kapsam dışı — bildiriliyor, yapılmadı

1. **`SystemBarsFollowTheTheme` KDoc'undaki bir cümle tam doğru değil.**
   "The launch window's own style, set in `onCreate`, covers that gap" diyor;
   §23 o boşluğu kapatan şeyin splash temasının kendisi olduğunu 16h-1'de
   ölçmüştü. Bu turda değiştirilmedi — ifade 16h-1'den beri aynı ve kusur
   `auto`'ya geçişten doğmuyor.
2. **AAB hâlâ bayat.** 16h ve bu turdaki `MainActivity` değişikliği mevcut
   AAB'de yok. 16g yeniden koşulmalı.

**Değişen dosyalar**
- `app/src/main/java/com/elinacn/subtrack/MainActivity.kt` — `onCreate`'teki
  durum çubuğu stili `SystemBarStyle.dark` → `SystemBarStyle.auto`, gerekçe
  yorumu yeniden yazıldı
- `docs/ARCHITECTURE.md` — §23'e "Devir rampası" ve "`onCreate`'teki geçici
  stil `auto`" başlıkları; 16h-1'in api29 sayısının düzeltmesi; §16 ve §23'teki
  bayat `SystemBarStyle.dark` referansları
- `docs/PROGRESS.md` — bu kayıt
- `docs/screenshots/phase-16h-2/` — dokuz kare

**Commit'ler**
- `4008032` fix: follow the system for the provisional status bar style
- *(bu kayıt)* docs: record the accepted handover ramp and the auto decision

**Karşılaşılan sorunlar**
- `cmd uimode night` api29 imajında iş görmüyor (`mNightModeLocked=true`).
  Sistem teması `settings put secure ui_night_mode` + **yeniden başlatma** ile
  sürüldü ve her ölçümden önce `dumpsys uimode` ile doğrulandı. İlk turda bu
  fark edilmeden dört hücre yanlış sistem temasıyla ölçüldü; o veriler atıldı,
  hücreler yeniden koşuldu.
- Emülatörde `screencap` ~150–200 ms sürüyor, rampa ise ondan kısa. Tek zincir
  yetmediği için açılış, cihaz saatiyle damgalanmış birkaç paralel cihaz-içi
  yakalama döngüsüyle örneklendi (~60–100 ms/kare). Sekizden fazla döngüde
  cihaz tıkanıp damgaların sırası bozuluyor; beş döngüde kalındı.
- Release APK'da `run-as` yok, tema tercihi yine ayar ekranından sürüldü
  (16h-1'deki gibi). Ölçüm ekranındaki etiketler `values-en`'den geliyor.

**Sonraki faz için not**
- 16g yeniden koşulup AAB yeniden üretilmeli.

---

## [Faz 16h-1] Durum Çubuğu Açıklandı, Bayat Belge ve Yorumlar Düzeltildi — 2026-09-20

**Durum:** Tamamlandı

**Neden**

16h'nin açık bıraktığı maddeler. Yeni özellik yok, **davranış değişikliği yok.**
`SystemBarStyle` değeri, `MainActivity`, manifest, ikon varlıkları ve
`generate_icons.py` bu turda hiç açılmadı; değişen tek kaynak dosya iki mipmap
XML'inin **yorum satırları**.

Çıkış noktası 16h'nin yan etkisiydi: geçici durum çubuğu stili `light` →
`dark` çevrilmişti ve "açık temada durum çubuğu okunmaz olabilir" şüphesi
kalmıştı. Kullanıcı fiziksel cihazda (OPPO A15s, Android 10, release) iki
temada da okunduğunu doğruladı. **Kusur yok, düzeltilecek bir şey yok** — bu
tur yalnızca *neden* okunduğunu ölçtü.

---

### Görev 1 — durum çubuğu ikon rengini gerçekte ne belirliyor

**`SystemBarStyle` ikon rengi atamıyor.** `androidx.activity` 1.12.4
kaynağında (`EdgeToEdge.kt`) `dark(scrim)` yalnızca `detectDarkMode = { true }`
kuruyor; `enableEdgeToEdge` bunu `statusBarIsDark`'a çevirip ikon için tek bir
şey yapıyor:
`WindowInsetsControllerCompat(...).isAppearanceLightStatusBars = !statusBarIsDark`.
Bayrak **`window.decorView`'a**, yani uygulamanın kendi penceresine yazılıyor.

**Uygulamanın kendi tema tercihi bu değeri eziyor.** `onCreate`'teki
`SystemBarStyle.dark` geçici; tercih gelir gelmez `SystemBarsFollowTheTheme`
`enableEdgeToEdge`'i `auto(...) { darkTheme }` ile yeniden çağırıyor. Dört
durumda hem pencere bayrağı hem piksel okundu (bant = üst `statusBars.top`
şeridi; "ikon çekirdeği" = bandın zeminden parlaklıkça en uzak pikseli):

| Cihaz | Tema | Pencere bayrağı | Bant zemini | İkon çekirdeği | Okunan piksel | Kontrast |
|---|---|---|---|---|---|---|
| api29 | açık | `mSystemUiVisibility=0x2710` | `#FFFFFF` | `#666666` | (106,10) | **5,74:1** |
| api29 | koyu | `0x700` (LIGHT_STATUS_BAR yok) | `#1F3D2D` | `#FFFFFF` | (106,10) | **11,91:1** |
| api34 | açık | `apr=LIGHT_STATUS_BARS` | `#FFFFFF` | `#666666` | (905,48) | **5,74:1** |
| api34 | koyu | `apr=` satırı yok (appearance 0) | `#1F3D2D` | `#FFFFFF` | (905,48) | **11,91:1** |

Açık temanın kanıt değeri: `dark` hâlâ yürürlükte olsaydı ikonlar `#FFFFFF`
üstüne beyaz çizilir, kontrast 1,00:1 olurdu — 16-0'da ölçülen hata tam buydu.
Olmuyor. Cihaz gözlemiyle ölçüm tutuyor.

**Splash karesinde ikon rengini başka bir mekanizma belirliyor.** `dumpsys
window` açılışta iki ayrı pencere gösteriyor: önce `Splash Screen
com.elinacn.subtrack` (`ty=APPLICATION_STARTING`, sistemin başlatma penceresi),
uygulamanınki ancak sonra. `enableEdgeToEdge` ilkine yazamıyor. O pencerenin
kaynağı `Theme.SubTrack.Starting`, ve ne o ne de türediği `Theme.SplashScreen`
`windowLightStatusBar` yazıyor (AAR kaynakları tarandı; yalnızca
`windowLightNavigationBar` var). Bayrak sıfır → ikonlar beyaz → `#0D1A14`
zemin üstünde doğru cevap. Ölçülen: **17,87:1**, api29 ve api34'te aynı.

Yani splash'ı okunur yapan şey `SystemBarStyle.dark` **değil**, splash
temasının kendisi. `dark` yine de yanlış değil; devir karelerinde ekranda
duran zemin koyu.

**16h'nin iki sayısı yanlış kareye atfedilmiş.** 16h kaydı "api29'da koyu
temada bant 11,91:1, açık temada 5,74:1" diyerek bunları `SystemBarStyle.dark`
değişikliğinin doğrulaması olarak sunuyor. **Sayılar doğru** — ikisi de bu
turda birebir yeniden üretildi — ama **splash karesinden gelmiyorlar:** ikisi
de uygulama karesinin ölçüsü, yani `auto { darkTheme }`'in sonucu. Cihaza da
bağlı değiller; api29 ile api34 aynı değerleri veriyor. Depodaki
`docs/screenshots/phase-16h/splash-*-api29.png` iki dosyası da bunu doğruluyor:
ikisinde de zemin `#0D1A14` (%92,7), ikon `#FFFFFF` (%4,6), kontrast
**17,87:1** — ve iki dosya birbirinden yalnızca saat kadar farklı, çünkü
splash'ta temaya bağlı piksel yok. 16h'nin metni düzeltilmedi (eski kayıt
silinmez); düzeltme ARCHITECTURE §23'e ve bu kayda yazıldı.

### Görev 2 — tema değişiminde, yeniden başlatmadan

`SystemBarsFollowTheTheme` bir `DisposableEffect` ve anahtarlarından biri
`darkTheme`. Uygulamanın kendi ayar ekranından açık → koyu → açık sürüldü,
süreç hiç yeniden başlamadan:

| Cihaz | pid (önce) | pid (açık→koyu sonrası) | pid (koyu→açık sonrası) | bant |
|---|---|---|---|---|
| api34 | 5490 | 5490 | 5490 | 5,74 → 11,91 → 5,74 |
| api29 | 7177 | 7177 | 7177 | 5,74 → 11,91 → 5,74 |

**Her iki yönde de okunur kalıyor.** Öncesi/sonrası kareler
`docs/screenshots/phase-16h-1/themeswitch-*`.

### Görev 3–4 — ARCHITECTURE

- **§23** "Açılışta ilk kare TUTULUYOR" bölümü yeniden yazıldı: kaldırılan
  `OnPreDrawListener` yerine `installSplashScreen` + `setKeepOnScreenCondition`,
  1000 ms üst sınırının **neden** var olduğu (DataStore takılırsa pencere hiç
  çizilmesin; sistem onu "does not have a focused window" diye öldürür),
  splash zeminine `#0D1A14` seçilme gerekçesi (altın beyaz üstünde 2,10:1,
  §27'nin "ikon temayı takip etmez" kararının devamı) ve ölçülen kare dizileri.
- **§23**'e yeni bir başlık eklendi: "Durum çubuğu ikon rengini ne belirliyor —
  iki pencere, iki mekanizma". Görev 1'in bütün ölçümleri, 16h düzeltmesi ve
  aşağıdaki geçiş penceresi orada.
- **§16**'nın "Açılıştaki ilk kare" paragrafı bayattı (hâlâ
  `SystemBarStyle.light` ve `#FAFAFA` diyordu); §23'e yönlendiren doğru
  paragrafla değiştirildi.

### Görev 5 — mipmap yorumları

`mipmap-anydpi-v26/ic_launcher.xml` ve `ic_launcher_round.xml` işaretin
65,78dp olduğunu yazıyordu; 16d'den beri 57,888dp. **Yalnızca yorum
değişti.** Kanıt: her iki dosyanın yorumları çıkarılmış ve boşlukları
normalleştirilmiş gövdesi HEAD ile aynı sha256'yı veriyor
(`40f9eb4c0a880b4e…`), ve `git diff` yalnızca yorum satırlarına dokunuyor.

`tools/icon/generate_icons.py:39` de 65.78dp diyor ama **bayat değil**: orada
cümle açıkça `SCALE = 1.0` hâlini anlatıyor ("At 1.0 it spans 134 units").
Dosyaya dokunulmadı, dokunulması da gerekmiyor.

### Görev 6 — api34 ANR'ı tekrarlamadı

`./gradlew --stop` ile derleme daemon'ı kapatıldıktan sonra, arka planda
hiçbir Gradle/R8 işi yokken, release build ile **beş temiz soğuk açılış**
(`force-stop` → `logcat -c` → `am start -W`, sayılmayan bir ısınmadan sonra).
Her koşunun logcat'i `ANR in`, `isn't responding`, `Input dispatching timed
out`, `am_anr` kalıplarıyla tarandı.

| Koşu | TotalTime | ANR kalıbı |
|---|---|---|
| 1 | 582 ms | 0 |
| 2 | 735 ms | 0 |
| 3 | 1059 ms | 0 |
| 4 | 640 ms | 0 |
| 5 | 1672 ms | 0 |

**Tekrarlamadı.** (Daemon kapatılmadan önce yapılan beş koşuda da sıfır:
714/652/746/595/639 ms.) Süreler 16h'nin 499–576 ms'inden geniş ve dağınık;
sebebi ölçüm makinesinde aynı anda Android Studio, üç emülatör ve bir fiziksel
cihazın açık olması. **Bu turda süre bir ölçüt değildi**, yorum yüklemiyorum.

---

### Doğrulama

| Ölçüt | Sonuç |
|---|---|
| Durum çubuğu kontrastı | dört ölçüm, yukarıdaki tablo — api29/api34 × açık/koyu |
| Tema değişimi, yeniden başlatmadan | iki cihaz, iki yön, pid sabit |
| Splash karesinde okunurluk | bant **17,87:1**; işaret `#D4AF37`/`#0D1A14` = **8,50:1**, api29 ve api34 |
| Renk sürekliliği (16h sonucu) | **bozulmamış** — üçüncü renk yok, koyu temada zemin sabit |
| mipmap diff | yorum dışı içerik birebir aynı (sha256 eşleşiyor) |
| `testDebugUnitTest --rerun-tasks` | **330 test, 0 başarısız, 0 hata, 0 atlanan** |
| `lintDebug --rerun-tasks` | **22 bulgu**, hepsi 16h'dekiyle aynı dağılım; mipmap dosyalarında sıfır |
| `assembleRelease` | geçti |
| Release APK | **2 184 114 B** — 16h tabanıyla **birebir aynı** (yorum AAPT2'de düşüyor) |
| AAB | **üretilmedi** — 16g ayrı tur |

Renk sürekliliği yeniden ölçümü (release build, ham kare yakalama, gövdenin
baskın rengi):

| Cihaz / tema | Dizi |
|---|---|
| api34 koyu | `#0D1A14` (%96,8) → `#0D1A14` (%76,3) → `#0D1A14` (%74,5) |
| api34 açık | `#0D1A14` (%96,8) → `#C8D7CC` (%72,5) → `#D3E2D8` (%72,7) |
| api29 koyu | `#0D1A14` (%94,9) → `#0D1A14` (%64,5) |
| api29 açık | `#0D1A14` (%94,9) → `#65736C` (%58,1) → `#D3E2D8` (%62,6) |

Ara değerler 16h'nin ölçtüğü çapraz geçiş kareleriyle **aynı renkler**.

### Ekran görüntüleri

`docs/screenshots/phase-16h-1/` altında on dört dosya:
`statusbar-api{29,34}-{light,dark}.png`,
`themeswitch-api{29,34}-{1-light,2-dark,3-light}.png`,
`splash-api{29,34}.png`, `handover-api34-light-{1,2}.png`.

---

### Kapsam dışı — bildiriliyor, yapılmadı

1. **Açılışta ~100–250 ms'lik bir geçiş penceresinde açık temada durum çubuğu
   ikonları görünmez oluyor.** api34, açık tema, ham kare dizisinde splash'tan
   uygulamaya devirde iki ardışık kare: `#F1F2F1` üstünde beyaz ikon
   (**1,12:1**) ve `#FFFFFF` üstünde beyaz ikon (**1,00:1** — bantta zeminden
   farklı tek piksel yok). Üçüncü kareden itibaren 5,74:1. Yakalama
   çözünürlüğü ~85 ms/kare, yani gerçek süre 100–250 ms mertebesinde; api29'un
   aynı yerinde en düşük değer 4,04:1, yani orada görünmezlik ölçülmedi. Koyu
   temada böyle bir kare yok. Sebebi geçici `SystemBarStyle.dark`'ın, uygulama
   penceresinin ilk çizilen karelerinde hâlâ yürürlükte olması;
   `SystemBarsFollowTheTheme` bir iki kare sonra devralıyor. Kareler
   `handover-api34-light-{1,2}.png`. **Dokunulmadı** — bu turun kuralı davranışı
   değiştirmemekti, ve kararı kullanıcı verecek.
2. **16h'nin metni düzeltilmedi.** Eski kayıtlar silinmez kuralı gereği 16h'nin
   11,91/5,74 cümlesi olduğu gibi duruyor; düzeltme yalnızca ARCHITECTURE §23'e
   ve bu kayda yazıldı.
3. **AAB hâlâ bayat.** 16h'nin uyarısı geçerli: manifest ve tema 16h'de
   değişti, mevcut AAB bu kodu temsil etmiyor. 16g yeniden koşulmalı.

**Değişen dosyalar**
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` — yalnızca yorum
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` — yalnızca yorum
- `docs/ARCHITECTURE.md` — §16'nın bayat paragrafı; §23'ün açılış kapısı
  bölümü yeniden yazıldı, durum çubuğu mekanizması başlığı eklendi
- `docs/PROGRESS.md` — bu kayıt
- `docs/screenshots/phase-16h-1/` — on dört kare

**Commit'ler**
- `a2a9272` docs: correct the stale mark diameter in the adaptive icon comments
- *(bu kayıt)* docs: record what sets the status bar icon colour

**Karşılaşılan sorunlar**
- Splash karesini yakalamak 16h'de geçici bir gecikme yaması gerektirmişti. Bu
  turda yama kullanılmadı: `screencap` PNG yerine **ham** yazdırıldı
  (~85 ms/kare, PNG'de ~250–300 ms) ve kareler cihaz üstünde md5 ile
  tekilleştirilip yalnızca farklı olanlar çekildi. Splash karesi gerçek release
  build'de böyle yakalandı.
- Emülatörlerde `adb root` yok (Google Play imajları), `cmd uimode night` API
  29'da yok; tema uygulamanın kendi ayar ekranından sürüldü.

**Sonraki faz için not**
- Yukarıdaki 1 numaralı bulgu bir karar bekliyor: geçici stil `auto` yapılabilir
  ya da olduğu gibi bırakılabilir. Ölçüm hazır, kod değişmedi.
- 16g yeniden koşulup AAB yeniden üretilmeli.

---

## [Faz 16h] Açılış Ekranı — Beyaz Boşluk Kapatıldı — 2026-09-20

**Durum:** Tamamlandı

**Neden**

16g'nin ölçümü şunu söylüyordu: performans sorunu yok (release soğuk açılış api34'te
596 ms, api24'te 501 ms), ama kullanıcının gördüğü sırada **üç renk** vardı —
sistem splash'ı `#FAFAFA` üzerinde ikonu gösteriyor, sonra **ikonsuz, tamamen boş
bir `#FAFAFA` kare** geliyor (api34'te 173–212 ms, pikselin %99,75'i tek renk),
ancak ondan sonra uygulama kendi `#D3E2D8` zeminini çiziyordu. API 24'te sistem
splash'ı hiç yok, açılış boyunca markasız beyaz duruyordu.

Bu turda **yeni bir açılış ekranı yazılmadı.** Var olan tema kapısı splash'a
bağlandı: bekleme aynı bekleme, ama artık boşluk yerine işaretin kendisi duruyor.
Tercihi okuyan kaynak (`MainViewModel`, `SettingsRepositoryImpl`) **değişmedi**.

**Yapılanlar**

*Görev 1 — bağımlılık*

`gradle/libs.versions.toml`'a `splashscreen = "1.2.0"` girdisi ve
`androidx-core-splashscreen` kütüphanesi eklendi; `app/build.gradle.kts` katalogdan
tüketiyor. **Sürüm gerekçesi:** 1.2.0 en yeni **stabil** sürüm — 1.1.0 ve 1.2.0
hatlarının geri kalan tüm girdileri alpha/beta/rc, 1.0.1 ise targetSdk 31'in kendi
splash attribute'larından önceki 2022 sürümü. Aynı ölçüt katalogdaki `work` girdisinde
de kullanılmıştı. Bağımsız doğrulama: `lintDebug` katalogdaki **eski olan her girdiyi**
işaretliyor (AGP, core-ktx, room, hilt, navigation, datastore, compose-bom…) ama
`core-splashscreen` için tek satır üretmiyor.

Bedeli var: `androidx.appcompat:appcompat-resources:1.7.0` transitive olarak geliyor.

*Görev 2 — tema*

`values/themes.xml` içinde `Theme.SubTrack.Starting`, `Theme.SplashScreen`'den
türetildi. Üç attribute:

| Attribute | Değer |
|---|---|
| `windowSplashScreenBackground` | `@color/ic_launcher_ground` (`#FF0D1A14`) |
| `windowSplashScreenAnimatedIcon` | `@drawable/ic_launcher_foreground` |
| `postSplashScreenTheme` | `@style/Theme.SubTrack` |

Zemin **`colors.xml`'den** okunuyor, `Color.kt`'den değil — ARCHITECTURE §27'nin tek
yönlü kuralı: işaretin renkleri launcher'ın ve Play Store'un erişebileceği yerde sabit,
bir tema renginin sonradan değişmesi işareti sessizce yeniden boyamamalı.

`values-v31` dosyası **yazılmadı, gerekmedi**: kütüphane kendi `values-v31`'inde
compat attribute'larını platform attribute'larına bağlıyor
(`android:windowSplashScreenBackground` → `?windowSplashScreenBackground` vb.).
AAR açılıp doğrulandı.

`windowSplashScreenIconBackgroundColor` **kasıtlı olarak yazılmadı**: ön plan zaten
bu temanın zemini üzerinde duruyor, ikinci bir disk eklemek ikonu Android 12'nin iki
boyut sınıfından küçüğüne düşürürdü (240dp yerine 288dp).

Manifest'te launcher activity'nin teması bu oldu. **`windowSoftInputMode="adjustResize"`
satırına dokunulmadı** — diff'te yalnızca `android:theme` satırı değişik.

`setOnExitAnimationListener` kullanılmadı; varsayılan çıkış yeterli.

*Görev 4–5 — kapı*

`MainActivity.onCreate`'te `super.onCreate` **öncesinde** `installSplashScreen()`.
Dönen nesneye `setKeepOnScreenCondition` veriliyor: tercih okunana kadar `true`,
1000 ms üst sınırıyla. **Eski `OnPreDrawListener` kapısı ve
`holdFirstFrameUntilThemeIsRead()` tamamen kaldırıldı** — iki kapı üst üste kalmadı.

`setKeepOnScreenCondition` `setContent`'ten **sonra** çağrılıyor. Sebep 14b'de
ölçülmüştü: içerik görünümünün içine bir şey konana kadar kendi `ViewTreeObserver`'ı
yok. Kütüphane dinleyicisini tam o görünüme kayıt ediyor, yani aynı kısıta tabi.

Üst sınır mantığı `MainActivity`'de; `MainViewModel` ve repository'ler
**değiştirilmedi**. Gecikmeli `invalidate()` korundu: iptal edilen çizim yeni bir
traversal planlamaz, tercih hiç gelmezse koşulun bir daha okunacağı an olmazdı.

**Yan etki — bilerek yapıldı**

`enableEdgeToEdge`'deki geçici durum çubuğu stili `SystemBarStyle.light` →
**`SystemBarStyle.dark`**. 16h'ye kadar `light` doğruydu, çünkü o karelerde ekranda
duran pencere `Theme.SubTrack`'ten gelen beyaz açılış penceresiydi. Artık splash
duruyor ve zemini `#0D1A14`; `light` bırakılsaydı ikonlar koyu zemine koyu çizilirdi.
Ekran görüntüsüyle doğrulandı: api29'da koyu temada bant **11,91:1**, açık temada
**5,74:1**, iki temada da bandın %7'si zeminden farklı piksel — yani ikonlar gerçekten
çiziliyor (16-0'daki "bant tamamen boş" hatası yok).

---

### Görev 3 — pre-31 ikon boyutu ÖLÇÜLDÜ

Endişe şuydu: kütüphane API 31 altında ikonu kendi çiziyor ve Android 12'nin
maske/ölçek kuralını uygulamıyor; ön plan 108dp tuvalde 57,888dp işaret taşıdığı için
pre-31'de küçük kalabilir.

AAR'dan çıkan kural: ikon arka planı **verilmediğinde** çizim
`splashscreen_icon_size_no_background` = **288dp**; maske 410dp dairede 109dp'lik
kontur, yani görünür daire 301dp — 288dp'lik ikonu kırpmıyor. Android 12+'ın kendi
"ikon arka planı yok" ölçüsü de 288dp. Beklenen işaret çapı:
288 × (57,888 ⁄ 108) = **154,37 dp**.

Ekranda ölçülen (splash karesinde altın piksellerin sınır kutusu, dp'ye çevrilmiş):

| Cihaz | Yol | Yoğunluk | Ölçülen işaret çapı | api34'ten fark |
|---|---|---|---|---|
| api24 (SDK 24) | kütüphane çiziyor | 320dpi | **154,00 dp** | +0,065% |
| api29 (SDK 29) | kütüphane çiziyor | 320dpi | **154,00 dp** | +0,065% |
| api34 (SDK 34) | platform çiziyor | 420dpi | **153,90 dp** | — |
| api36 (SDK 36) | platform çiziyor | 420dpi | **153,90 dp** | 0,00% |

En büyük fark **0,10 dp = %0,065**. Eşik %20'ydi; **bildirilecek bir sapma yok,
düzeltme gerekmedi.** `generate_icons.py` çalıştırılmadı, hiçbir ikon varlığına
dokunulmadı.

Ölçülen 154,00 ile beklenen 154,37 arasındaki 0,37 dp, kenar yumuşatması: sınır
kutusu yalnızca altın eşiğini geçen pikselleri sayıyor.

---

### Doğrulama — soğuk açılış, RELEASE build, beşer koşu

`adb shell am force-stop` → `logcat -c` → `am start -W`, sayılmayan bir ısınma
açılışından sonra. Her hücrede `Displayed` satırı `TotalTime` ile birebir aynı çıktı.

| Cihaz | 16g tabanı (min/medyan/max) | **16h** (min/medyan/max) | medyan farkı |
|---|---|---|---|
| api24 | 432 / **501** / 525 | 355 / **403** / 443 | **−98 ms** |
| api29 | *(16g'de ölçülmedi)* | 543 / **567** / 656 | yeni taban |
| api34 | 583 / **596** / 1397 | 499 / **563** / 576 | **−33 ms** |

16h ham TotalTime değerleri:
- api24: 413, 443, 403, 355, 391
- api29: 567, 656, 561, 543, 618
- api34: 550, 499, 563, 576, 564

**Süre artmadı; iki cihazda da düştü.** Düşüşe yorum yüklemiyorum: ölçüm boş
veritabanıyla ve emülatörde yapıldı, aradaki fark koşu içi dağılımın genişliğiyle
aynı mertebede.

> api34'te ilk ölçüm seti emülatör boot sonrası hâlâ oturuyordu (2122, 1988, 1189,
> 570, 710 — açık bir düşüş trendi). O set atıldı, yük oturduktan sonra tekrarlandı.

---

### Doğrulama — splash → uygulama geçişinde üçüncü renk yok

Ardışık kareler yakalanıp her karenin **baskın rengi** ölçüldü. 16g'nin boş karesi
pikselin %99,75'i tek renkti, yani baskın renk bu soruyu cevaplayacak kadar keskin
bir ölçüt.

**Koyu temada zemin hiç değişmiyor:**

| Cihaz | Dizi |
|---|---|
| api34 | başlatıcı → `#0D1A14` → `#0D1A14` (dört bağımsız açılış) |
| api29 | başlatıcı → `#0D1A14` (%94,9) → `#0D1A14` (%64,7) |
| api24 | başlatıcı → `#0D1A14` (%94,7) → `#0D1A14` (%56,4) |
| api36 | başlatıcı → `#0D1A14` (%96,8) → `#0D1A14` (%73,5) |

Oranın düşmesi zeminin değişmesi değil, üstüne kart ve metin gelmesi.

**Açık temada tek adım var, araya giren renk yok:**

| Cihaz | Dizi |
|---|---|
| api34 | `#0D1A14` (%97,0) → `#C8D7CC` → `#D3E2D8` |
| api29 | `#0D1A14` (%94,9) → `#65736C` → `#D3E2D8` |
| api36 | `#0D1A14` (%96,8) → `#65736C` → `#D3E2D8` |

Aradaki değerler bağımsız bir üçüncü renk değil, **çapraz geçiş karesi**. Kanıt:
`#0D1A14` → `#D3E2D8` doğrusu üzerinde her kanal için aynı `t` çıkıyor.

| Ara renk | t (R, G, B) | kanallar arası sapma |
|---|---|---|
| `#C8D7CC` | 0,944 / 0,945 / 0,939 | **0,0062** |
| `#65736C` | 0,444 / 0,445 / 0,449 | **0,0045** |

**Beyaz kare taraması** — dört cihaz, iki tema, yakalanan **83 kare**, durum çubuğu
bandı kırpılarak `#FAFAFA`/`#FFFFFF` oranı:

| Grup | Kare | En yüksek beyaz oranı |
|---|---|---|
| api34 açık / koyu | 15 / 12 | %10,2 / %8,1 |
| api29 açık / koyu | 8 / 8 | %9,8 / %1,5 |
| api24 açık / koyu | 8 / 8 | %9,8 / %7,7 |
| api36 açık / koyu | 12 / 12 | %10,0 / %0,3 |

Hiçbir karede %10,2'yi geçmiyor. 16g'de ölçülen boş kare **%99,75** (api34) ve
**%87,4** (api24) beyazdı. Boş beyaz kare kalktı.

---

### Doğrulama — kapı üst sınırı

Geçici bir yama `MainActivity`'deki kapı koşulunun ilk yan tümcesini 5 sn boyunca
`true` tuttu — yani "tercih hiç gelmedi" hâli taklit edildi. Repository'ye ve
ViewModel'e dokunulmadı; takılan bir okumanın `true` tutacağı koşul zaten
MainActivity'nin sahibi olduğu koşul. Yama tur sonunda kaldırıldı.

| Cihaz | Kapının açıldığı an | `themeActuallyRead` | simüle takılma hâlâ aktif mi |
|---|---|---|---|
| api34 | **t+1258 ms** | **false** | evet |
| api34 | t+1122 ms | true | evet |
| api34 | t+1003 ms | true | evet |
| api24 | t+1013 ms | true | evet |
| api36 | t+1009 ms | true | evet |

Birinci satır kanıtın kendisi: tercih gerçekten okunmamıştı **ve** simüle takılma
hâlâ "okunmadı" diyordu; kapıyı açan tek şey son tarih olabilirdi.

---

### Doğrulama — testler, lint, R8, boyut

| Ölçüt | Sonuç |
|---|---|
| `testDebugUnitTest --rerun-tasks` | **330 test, 0 başarısız, 0 hata** |
| `connectedDebugAndroidTest` (api34) | **19 test, 0 başarısız, 0 hata, 1 atlanan** |
| `lintDebug` | 22 bulgu — **hepsi 16h öncesinden**, dokunulan dosyalarda sıfır |
| `assembleRelease` | geçti |
| R8 keep kuralı | **gerekmedi** |
| Release APK | 2 178 560 → **2 184 114 B** (+5 554 B, **+%0,25**) |

Atlanan enstrümantasyon testi
`PaymentReminderWorkerTest.reminderWorker_notificationsDisabled_succeedsWithoutNotifying`;
sebebi kendi `assumeFalse("notifications are enabled…")`'ı ve 16h öncesinde de
atlanıyordu — bu turun sonucu değil.

Keep kuralı gerekmemesinin sebebi tahmin değil: AAR'ın `proguard.txt`'si
*"Intentionally empty proguard rules to indicate this library is safe to shrink"*
diyor ve `app/proguard-rules.pro` hâlâ **sıfır** `-keep` satırı taşıyor.

> Not: promptta taban 2,03 MiB deniyordu; 16g'nin commit'inden (`1c2f55e`) yeniden
> üretilen APK 2 178 560 B = 2,0776 MiB ölçüldü. Yukarıdaki fark bu ikisi arasında,
> aynı makinede, aynı yapılandırmayla.

---

### Doğrulama — tema regresyonu

Uygulamanın kendi ayar ekranından sürüldü (API 29'da `cmd uimode night` yok, ama
Faz 14 tercihi var ve ölçülen o).

| Adım | api34 | api29 |
|---|---|---|
| Tema = Açık | `#D3E2D8`, çökme yok | `#D3E2D8`, çökme yok |
| Tema = Koyu | `#0D1A14`, çökme yok | `#0D1A14`, çökme yok |
| Tema = Sistemi takip et | `#D3E2D8`, çökme yok | `#D3E2D8`, çökme yok |
| Duvar kâğıdı renkleri aç/kapa | Açık ↔ Kapalı, çökme yok | *"Android 12 ve üzeri gerekir"* — beklenen |
| Döndürme (yatay ↔ dikey) | `#0D1A14`, çökme yok | `#0D1A14`, çökme yok |

**api29 / gezinme çubuğu yok** (`subtrack_narrow_api29`, `hw.mainKeys=yes`):
`dumpsys window` uygulamaya `app=720x1280` veriyor, yani tam ekran — çubuk için
ayrılmış boşluk yok. Tema değişiminden sonra alt kenarın son 24 pikseli koyu temada
%93,8 `#0D1A14`, açık temada %89,0 `#D3E2D8`; artık bir çubuk gölgesi ya da boşluk
yok. Durum çubuğu bandı iki temada da yukarıdaki kontrast değerleriyle çiziliyor.

---

### Ekran görüntüleri

`docs/screenshots/phase-16h/` altında sekiz dosya:
`splash-{light,dark}-api{24,29,34,36}.png`.

api24'ün iki dosyası **birebir aynı** (MD5 `5F14EA3F7FA53E9AF450B436E0988164`).
Bu bir kopyalama hatası değil, sonucun kendisi: splash'ta temaya bağlı tek piksel
yok, iki çekim aynı dakikaya denk geldiği için saat de aynı. Diğer üç cihazda
çekimler farklı dakikalara denk geldiğinden dosyalar ayrışıyor.

Splash karelerinin bir kısmı, güvenilir yakalanabilmesi için yukarıdaki geçici
gecikme yamasıyla alındı: splash'ın **pikselleri** aynı, yalnızca ekranda kalma
süresi uzun. Renk dizisi ölçümlerinin tamamı gerçek build ile yapıldı (api36'nın
dizisi hem gecikmeli hem gerçek build ile ayrı ayrı doğrulandı).

---

### AAB BAYATLADI

**`AndroidManifest.xml` ve tema değişti; mevcut AAB artık bu kodu temsil etmiyor.
Faz 16g yeniden koşulmalı.** Bu turda AAB **üretilmedi.**

---

### Kapsam dışı — bildiriliyor, yapılmadı

1. **`docs/ARCHITECTURE.md` §23 bayatladı.** 1922–1945 satırları "Açılışta ilk kare
   TUTULUYOR" başlığı altında `MainActivity`'deki `OnPreDrawListener`'ı anlatıyor;
   o dinleyici bu turda kaldırıldı. Mekanizma değişti, karar değişmedi. CLAUDE.md §7
   ARCHITECTURE güncellemesi istiyor, ama bu turun görev listesinde yok — kararı
   kullanıcıya bırakıyorum.
2. **`mipmap-anydpi-v26/ic_launcher.xml` ve `ic_launcher_round.xml` yorumları
   bayat.** İkisi de işaretin 65,78dp olduğunu yazıyor; 16d onu %88'e indirdi ve
   gerçek değer 57,888dp. DOKUNMA listesindeki dosyalar, elleşmedim.
3. **Emülatör ANR'ı.** api34'te duman testi sırasında "Process system isn't
   responding" çıktı; o an arka planda R8 koşuyordu. Build bittikten sonra aynı
   açılış temiz geçti ve sonraki 5+5 ölçümde tekrarlamadı. Uygulama kaynaklı
   görünmüyor ama "kesin değil" diye işaretliyorum.

**Değişen dosyalar**
- `gradle/libs.versions.toml` — `splashscreen = "1.2.0"` ve kütüphane girdisi
- `app/build.gradle.kts` — `implementation(libs.androidx.core.splashscreen)`
- `app/src/main/res/values/themes.xml` — `Theme.SubTrack.Starting`
- `app/src/main/AndroidManifest.xml` — yalnızca activity'nin `android:theme` satırı
- `app/src/main/java/com/elinacn/subtrack/MainActivity.kt` — `installSplashScreen()`,
  `keepSplashScreenUntilThemeIsRead()`, eski pre-draw kapısı silindi,
  geçici durum çubuğu stili `dark`
- `docs/screenshots/phase-16h/` — sekiz açılış karesi

**Karşılaşılan sorunlar**
- İlk `packageDebug` bir kez `IncrementalSplitterRunnable` hatasıyla düştü, ikinci
  koşuda geçti; dosya kilidi, koddan bağımsız.
- `connectedDebugAndroidTest` ilk denemede `INSTALL_FAILED_UPDATE_INCOMPATIBLE`
  verdi — cihazda release kuruluydu, debug imzasıyla çakıştı. Kaldırıldı, geçti.
- Splash penceresi gerçek build'de `screencap`'in kare başına ~250–300 ms'sinden
  kısa; yakalama döngüsü `am start` ile paralel çevrildi ve ikon karesi için geçici
  gecikme yaması kullanıldı.

**Sonraki faz için not**
- 16g yeniden koşulup AAB yeniden üretilmeli.
- Asıl yargı OPPO A15s / Android 10 / 423dp üzerinde verilecek. Bu turda API 29
  emülatörü 360dp'ydi; genişlik farkı splash'ı etkilemez (ikon ekran merkezinde ve
  ekran genişliğinden bağımsız 154 dp) ama gerçek cihazda bakılması yine de anlamlı.

---

## [Ölçüm — soğuk açılış] Teşhis Turu — 2026-09-20

**Durum:** Tamamlandı — **hiçbir davranış değiştirilmedi.** Bu tur yalnızca ölçüm
ve teşhis; optimizasyon, splash ekleme veya bağımlılık ekleme yapılmadı. Ölçüm
için `SubTrackApplication.kt` ve `MainActivity.kt` içine geçici log konuldu,
tur sonunda `git checkout --` ile geri alındı ve `git status` temiz bırakıldı.

---

### Ölçüm ortamı

| | api34 | api24 | api36 |
|---|---|---|---|
| AVD | `subtrack_wide_api34` | `subtrack_min_api24` | `subtrack_edge_api36` |
| Android | 14 (SDK 34) | 7.0 (SDK 24) | 16 (SDK 36) |
| Ekran | 1080×2400 @420dpi | 720×1280 @320dpi | 1080×2400 @420dpi |
| ABI / çekirdek / RAM | x86_64 / 4 / 2 GB | x86_64 / 4 / 2 GB | x86_64 / 4 / 2 GB |

- **Aynı anda tek emülatör** çalıştırıldı. İki emülatör aynı CPU'yu paylaşınca
  soğuk açılış sayıları bozuluyor; her cihaz ölçülürken diğerleri kapalıydı.
- Ölçüm sırasında Gradle çalışmadı. Derlemeler ölçümlerden ayrı yapıldı.
- Animasyon ölçekleri **değiştirilmedi** (`window_animation_scale = 1.0`,
  `transition_animation_scale = 1.0`).
- Yöntem: `adb shell am force-stop` → `logcat -c` → 3 sn bekle →
  `adb shell am start -W -n com.elinacn.subtrack/.MainActivity`.
- Her hücrede **sayılmayan bir ısınma açılışı** var: ilk açılış `subtrack.db` ve
  `settings.preferences_pb` dosyalarını yaratıyor ve tek başına bir aykırı değer
  oluyor (api34 release: ısınma **3110 ms**, sonraki koşular 583–618 ms).
- **Veritabanı her ölçümde boştu** (temiz kurulum). Gerçek kullanıcı verisiyle
  ilk kare daha uzun sürer; aşağıdaki sayılar bir **taban** değeridir.

---

### Görev 1–2 — Soğuk açılış tablosu (iki build × iki cihaz, beşer ölçüm)

`am start -W` çıktısı, ms:

| Cihaz | Build | ham TotalTime | min | **medyan** | max |
|---|---|---|---|---|---|
| api34 | release | 1397, 583, 596, 618, 587 | 583 | **596** | 1397 |
| api34 | debug | 4032, 3156, 3142, 3185, 3774 | 3142 | **3185** | 4032 |
| api24 | release | 432, 525, 465, 509, 501 | 432 | **501** | 525 |
| api24 | debug | 677, 794, 601, 600, 654 | 600 | **654** | 794 |

WaitTime, ms:

| Cihaz | Build | ham WaitTime | min | **medyan** | max |
|---|---|---|---|---|---|
| api34 | release | 1411, 594, 630, 622, 593 | 593 | **622** | 1411 |
| api34 | debug | 4040, 3157, 3145, 3189, 3777 | 3145 | **3189** | 4040 |
| api24 | release | 436, 531, 471, 514, 505 | 436 | **505** | 531 |
| api24 | debug | 682, 801, 605, 605, 660 | 605 | **660** | 801 |

**İki build arasındaki fark (medyan TotalTime):**

| Cihaz | release | debug | oran |
|---|---|---|---|
| api34 | 596 | 3185 | **5,34×** |
| api24 | 501 | 654 | **1,31×** |

Oranın cihazdan cihaza bu kadar değişmesinin sebebi ölçüldü — `dumpsys package`
çıktısındaki dexopt durumu:

| Cihaz | Build | dexopt durumu |
|---|---|---|
| api34 | release | `[status=verify] [reason=install]` |
| api34 | debug | `[status=run-from-apk]`, `[location is error]` |
| api24 | release | `compilation_filter=interpret-only, status=kOatUpToDate` |
| api24 | debug | `compilation_filter=interpret-only, status=kOatUpToDate` |

api34'te debug build `run-from-apk` durumunda: dex her açılışta yeniden
çıkartılıp doğrulanıyor, üstelik **main thread'de**. api24'te iki build de
kurulumda `interpret-only` odex almış, bu yüzden fark 1,31× ile kalıyor.

> ARCHITECTURE §"Açılış performansı release build'de ölçülür" bu projede
> debug/release oranını **~9,5×** (OPPO CPH2179, Android 10) diye kaydetmişti.
> api34'teki ölçüm 5,34×, api24'teki 1,31×. Oran cihaza ve dexopt durumuna bağlı;
> belgedeki "yargıya varmadan önce release'de ölç" kuralı geçerli, ama tek bir
> sabit oran yok.

---

### Görev 3 — logcat `Displayed` ↔ rapor karşılaştırması

Soğuk koşuların **20'sinin 20'sinde** `Displayed` satırı `TotalTime` ile birebir
aynı çıktı. Örnekler:

```
api34 release run 2:  TotalTime: 583
  ActivityTaskManager: Displayed com.elinacn.subtrack/.MainActivity for user 0: +583ms
api34 debug   run 1:  TotalTime: 4032
  ActivityTaskManager: Displayed com.elinacn.subtrack/.MainActivity for user 0: +4s32ms
api24 release run 3:  TotalTime: 465
  ActivityManager: Displayed com.elinacn.subtrack/.MainActivity: +465ms
```

Yani `am start -W`'nin TotalTime'ı ile sistemin kendi ölçtüğü süre **aynı olayı**
ölçüyor: ilk karenin çizildiği an. İkisi arasında açıklanması gereken bir sapma yok.

---

### Görev 4 — Sıcak açılış (home tuşu → geri dönüş)

Process **öldürülmedi**; her koşuda `pidof` ile hayatta olduğu doğrulandı
(api34 release'de beş koşu boyunca pid 6179 sabit). `am start -W` `LaunchState: HOT`
raporluyor.

| Cihaz | Build | ham TotalTime | min | **medyan** | max |
|---|---|---|---|---|---|
| api34 | release | 434, 148, 161, 145, 152 | 145 | **152** | 434 |
| api34 | debug | 343, 194, 199, 202, 181 | 181 | **199** | 343 |
| api24 | release | 33, 34, 25, 27, 33 | 25 | **33** | 34 |
| api24 | debug | 33, 35, 29, 23, 35 | 23 | **33** | 35 |

**Soğuk ↔ sıcak farkı (medyan):**

| Cihaz | Build | soğuk | sıcak | fark | soğuğun sıcak olmayan kısmı |
|---|---|---|---|---|---|
| api34 | release | 596 | 152 | 444 ms | %74,5 |
| api34 | debug | 3185 | 199 | 2986 ms | %93,8 |
| api24 | release | 501 | 33 | 468 ms | %93,4 |
| api24 | debug | 654 | 33 | 621 ms | %95,0 |

**Bu farkın söylediği şey:** debug ile release'in **sıcak** süreleri neredeyse eşit
(api34: 199 ↔ 152; api24: 33 ↔ 33), ama **soğuk** süreleri api34'te 5,34× ayrışıyor.
Activity oluşturma, composition ve çizim iki build'de de benzer maliyette; ayrışma
tamamen process kurulumunda — sınıf yükleme ve dex doğrulama tarafında.

Sıcak açılışta `Displayed` satırı hiç yazılmıyor; activity yeniden yaratılmadığı
için sistemin sayacak bir "ilk kare"si yok. Bu beklenen davranış.

---

### Görev 5 — Ana iş parçacığı taraması

İki bağımsız yöntem kullanıldı:

1. **atrace** (kod değişikliği gerektirmeyen sistem izi):
   `atrace --async_start -c -b 60000 -a com.elinacn.subtrack am wm view gfx res dalvik database disk sched`
2. **Geçici log** (`SubTrackApplication.kt` + `MainActivity.kt`, tur sonunda kaldırıldı):
   her adımın `SystemClock.uptimeMillis()` farkı ve `Thread.currentThread().name`'i,
   artı `StrictMode.ThreadPolicy` ile `detectDiskReads().detectDiskWrites().penaltyLog()`.

#### 5a — Geçici log ile ölçülen adımlar (release build, üç soğuk açılış)

**api34** (o üç açılışın TotalTime'ı: 715 / 633 / 770 ms — enstrümanlı build
temiz build'den bir miktar yavaş; yetkili sayılar Görev 1 tablosundadır):

| Adım | L1 | L2 | L3 | İş parçacığı |
|---|---|---|---|---|
| `super.onCreate()` — Hilt grafı + alan enjeksiyonu | 2 | 2 | 1 | **main** |
| `reminderScheduler.schedule()` — WorkManager kurulum + enqueue | 6 | 4 | 2 | **main** |
| `snapshotRecorder.start()` | 1 | 1 | 2 | **main** |
| **`Application.onCreate` TOPLAM** | **9** | **7** | **5** | **main** |
| Application.onCreate çıkışı → Activity.onCreate girişi | 39 | 50 | 31 | — |
| `super.onCreate()` (Activity) | 2 | 1 | 0 | **main** |
| `enableEdgeToEdge()` | 23 | 11 | 11 | **main** |
| `setContent { }` (dönüş anı) | 1 | 1 | 1 | **main** |
| **`MainActivity.onCreate` TOPLAM** | **26** | **13** | **12** | **main** |
| onCreate çıkışı → ilk `onPreDraw` (ilk composition + ölçüm + yerleşim) | 122 | 115 | 186 | **main** |
| **tema kapısının pencereyi tuttuğu süre** | **205** | **212** | **173** | **main** |
| kapı açıldı → ilk gerçek çizim | 3 | 1 | 3 | **main** |
| reddedilen `onPreDraw` sayısı | 1 | 2 | 1 | — |

**api24** (TotalTime: 383 / 426 / 428 ms):

| Adım | L1 | L2 | L3 | İş parçacığı |
|---|---|---|---|---|
| `super.onCreate()` — Hilt | 7 | 9 | 22 | **main** |
| `reminderScheduler.schedule()` — WorkManager | 27 | 14 | 19 | **main** |
| `snapshotRecorder.start()` | 3 | 4 | 5 | **main** |
| **`Application.onCreate` TOPLAM** | **37** | **27** | **46** | **main** |
| Application çıkışı → Activity girişi | 27 | 36 | 30 | — |
| `super.onCreate()` (Activity) | 3 | 3 | 5 | **main** |
| `enableEdgeToEdge()` | 10 | 6 | 10 | **main** |
| `setContent { }` (dönüş anı) | 5 | 3 | 6 | **main** |
| **`MainActivity.onCreate` TOPLAM** | **18** | **12** | **21** | **main** |
| onCreate çıkışı → ilk `onPreDraw` | 183 | 177 | 178 | **main** |
| **tema kapısının tuttuğu süre** | **0** | **50** | **0** | **main** |
| reddedilen `onPreDraw` sayısı | 0 | 2 | 0 | — |

api24'te üç açılışın ikisinde `frame.firstPreDraw` anında `themeKnown=true` idi —
DataStore okuması ilk composition'dan **önce** bitmiş, kapı hiç kapanmamış.
api34'te üç açılışın üçünde de kapı kapandı ve 173–212 ms tuttu.

#### 5b — Dört soruya doğrudan cevap

| Soru | Cevap | Kanıt |
|---|---|---|
| **Hilt grafının kurulumu** | main thread, **1–2 ms** (api34) / **7–22 ms** (api24) | Geçici log: `app.super.onCreate[Hilt graph+inject]=2ms thread=main` |
| **Room'un ilk açılışı main thread'de mi?** | **Hayır.** Açılış yolunda main thread'de hiç disk okuma/yazma yok | `StrictMode` `detectDiskReads()+detectDiskWrites()+penaltyLog()` ile **0 ihlal** (api34 ve api24). atrace'te iş `WM.task-1` ve `DefaultDispatcher-worker-*` üzerinde; process'te `arch_disk_io_0..3` iş parçacıkları var |
| **DataStore'un ilk okuması main thread'de mi?** | **Hayır.** Aynı StrictMode kanıtı; okuma DataStore'un kendi IO scope'unda | 0 StrictMode ihlali. Okumanın *bittiği* an main thread'de görünür hale geliyor: `frame.gateOpen` |
| **WorkManager başlatıcısı: Startup provider mı, manuel mi?** | **Manuel.** `androidx.work.WorkManagerInitializer` manifest'te `tools:node="remove"` ile kaldırılmış; WorkManager ilk `getInstance()` çağrısında kuruluyor | atrace'te `Startup` bloğunun içinde yalnızca `ProcessLifecycleInitializer` (8,24 ms), `ProfileInstallerInitializer` (2,04 ms) ve `EmojiCompatInitializer` var — WorkManager yok. Kurulum bizim `reminderScheduler.schedule()` çağrımızda, main thread'de, 2–6 ms (api34) / 14–27 ms (api24) |

WorkManager kurulduktan sonraki işini kendi iş parçacıklarına atıyor:
`WM-ForceStopRunnable` ve `WM-SystemJobScheduler: Scheduling work ID …` satırları
`WM.task-1` üzerinde; main thread'de yalnızca
`WM-Schedulers: Created SystemJobScheduler` görünüyor.

#### 5c — atrace: ana iş parçacığının tam dökümü (api34)

Bölüm süreleri, ms. **atrace'in kendisi ölçümü şişirir** — buradaki sayılar
oranları ve sıralamayı gösterir; yetkili toplamlar Görev 1'deki `am start -W`
tablolarıdır.

| Bölüm | release | debug |
|---|---|---|
| `ActivityThreadMain` | 19,84 | 66,73 |
| **`bindApplication`** | **238,08** | **1349,40** |
| ├ `setSystemFontMap` | 48,44 | — |
| ├ `ResourcesManager#applyConfigurationToResources` | 45,73 | 3,47 |
| ├ `OpenDexFilesFromOat` | **41,75** | **617,84** |
| │  └ içinde `Extract dex file` + `Verify dex file` | (yok) | 243,8 + 359,4 |
| ├ `ResourcesManager#getResources` | 34,63 | 11,17 |
| └ `Startup` (androidx.startup provider) | 13,21 | 72,32 |
| **`activityStart`** | **143,58** | **326,55** |
| └ `performCreate:MainActivity` | 78,62 | 195,76 |
| `activityResume` | 41,43 | 13,94 |
| **ilk `Choreographer#doFrame`** | **498,03** | **1782,29** |
| └ `Compose:recompose` (ilk kare içinde) | 69,48 | — |
| └ `measure` / `AndroidOwner:onMeasure` | 286,98 | — |
| └ `TextStringSimpleNode::measure` (26 çağrı toplamı) | 169,7 | — |
| └ `TextLayout:initLayout` (26 çağrı toplamı) | 127,8 | — |

Debug'daki `bindApplication` şişkinliğinin **%46'sı** tek bir kalemden geliyor:
dex'in çıkartılıp doğrulanması (617,84 ms), ki bu `run-from-apk` durumunun
doğrudan sonucu. Debug'ın ilk `doFrame`'i de 1782 ms; içi ağırlıkla Compose
sınıflarının `VerifyClass` çağrıları — release'de bu sınıflar kurulumda
doğrulanmış olduğu için o maliyet yok.

#### 5d — Tema kapısı izde de görünüyor

Release izinde ana iş parçacığındaki `Choreographer#doFrame` bölümleri:

```
  t+ 517.6ms  dur= 498.03ms  Choreographer#doFrame 292094   cizim_yapildi=False
  t+1368.3ms  dur=  36.77ms  Choreographer#doFrame 292338   cizim_yapildi=False
  t+1410.5ms  dur= 128.77ms  Choreographer#doFrame 292698   cizim_yapildi=True
```

İlk **iki** kare hiç çizim üretmiyor. `MainActivity.holdFirstFrameUntilThemeIsRead()`
tercih okunana kadar `onPreDraw`'dan `false` döndürüyor; ilk gerçek çizim üçüncü
karede oluyor. Bu, tasarlandığı gibi çalışan bir davranış (ARCHITECTURE §"Açılışta
ilk kare TUTULUYOR") — ama maliyeti artık ölçülü: api34'te 173–212 ms.

---

### Görev 6 — Açılış anında ekranda ne var

Ekran görüntüleri **repoya eklenmedi** (bu turun kuralı `git status`'un temiz
kalması). Dosyalar oturumun geçici klasöründe, kullanıcıya ayrıca iletildi:
`1_api34_sistem_splash.png`, `2_api34_splash_sonrasi_bos_kare.png`,
`3_api36_sistem_splash.png`, `4_api24_acilis_ani.png`.

| # | Cihaz | Ne görünüyor | Piksel kanıtı |
|---|---|---|---|
| 1 | api34 | **Sistem splash'ı**: `#FAFAFA` zemin, ortada uygulama işareti (koyu zümrüt daire + altın para halkası) | merkez piksel `(13, 26, 20)`; merkez 300×300 kutusunda `(212, 175, 55)` = **#D4AF37** 40.766 piksel; zemin `(250, 250, 250)` 2.445.101 piksel |
| 2 | api34 | **Splash'tan sonra, uygulama çizilmeden önce: ikonsuz, tamamen boş beyaz kare** | 2.592.000 pikselin 2.585.447'si (**%99,75**) `(250, 250, 250)`; merkez 300×300 kutusunda **tek bir renk** var |
| 3 | api36 | Sistem splash'ı, api34 ile aynı: `#FAFAFA` zemin + aynı işaret (kare sönümlenme anında yakalandığı için renkler açılmış) | merkez `(57, 67, 62)`, işaret altını `(219, 189, 91)` 40.766 piksel — api34'teki piksel sayısıyla birebir aynı |
| 4 | api24 | **Sistem splash'ı yok** (Android 12 öncesi). Yalnızca pencere arka planı: ikonsuz, markasız düz beyaz | 921.600 pikselin 805.186'sı `(250, 250, 250)`; gövde tek renk, kalanı sistem çubukları |

`#FAFAFA` bir tercih değil, miras: `Theme.SubTrack`'in ebeveyni
`android:Theme.Material.Light.NoActionBar` ve temada `windowSplashScreenBackground`,
`windowSplashScreenAnimatedIcon` veya `postSplashScreenTheme` tanımlı değil. Sistem
splash'ı bu yüzden `windowBackground`'ı ve `android:icon`'u kullanıyor.

Uygulamanın kendi zemini `#D3E2D8` (`EmeraldBackdrop`, ölçüldü: `(211, 226, 216)`).
Yani kullanıcının gördüğü sıra şu:

- **api34 / api36:** `#FAFAFA` + ikon → **`#FAFAFA`, ikonsuz, boş** → `#D3E2D8` uygulama
- **api24:** `#FAFAFA`, ikonsuz, boş → `#D3E2D8` uygulama

---

### Kapsam dışı — düzeltilmedi, karar kullanıcıya bırakıldı

Bu turda hiçbiri değiştirilmedi. Sıralama etkiye göre:

1. **Splash ile uygulama arasında boş beyaz kare var.** api34'te tema kapısı
   pencereyi 173–212 ms tutuyor ve o süre boyunca ekranda ikonsuz `#FAFAFA`
   duruyor (ekran görüntüsü 2). Sistem splash'ı ikonu gösterip kayboluyor, sonra
   marka taşımayan boş bir beyaz geliyor. "Geç açılıyor" hissinin en olası kaynağı
   bu: süre değil, **boşluğun görünür olması.**
2. **`Theme.SubTrack`'te splash kuralı yok.** `windowSplashScreenBackground` ve
   `postSplashScreenTheme` tanımsız; splash zemini `Theme.Material.Light`'tan
   geliyor ve uygulamanın kendi zemini (`#D3E2D8`) ile aynı değil, dolayısıyla
   splash → uygulama geçişi bir renk sıçraması.
3. **İlk karedeki en büyük tek kalem metin yerleşimi.** api34 release izinde
   `TextStringSimpleNode::measure` 26 çağrıda 169,7 ms, `TextLayout:initLayout`
   26 çağrıda 127,8 ms. Ölçüm boş veritabanıyla yapıldı; liste doluyken bu artar.
4. **Baseline profile kurulmuyor.** logcat: `ProfileInstaller: Skipping profile
   installation for com.elinacn.subtrack`. api34'te dexopt `status=verify`
   seviyesinde kalıyor. Play üzerinden kurulumda durum farklı olabilir;
   emülatördeki bu ölçüm AOT derlenmemiş bir kurulumu temsil ediyor.
5. **Ölçüm artefaktı, davranış değil:** her koşuda `am force-stop` kullanıldığı için
   `WM-ForceStopRunnable: Application was force-stopped, rescheduling.` tetikleniyor
   ve WorkManager fazladan iş yapıyor. Normal kullanımda bu her açılışta olmaz —
   yani gerçek soğuk açılış bu tablodan bir miktar **daha hızlı** olabilir.
6. **`enableEdgeToEdge()` api34'te 11–23 ms**, `Application.onCreate`'in tamamından
   (5–9 ms) daha pahalı. Küçük, ama açılış yolundaki en pahalı tek kendi çağrımız.

---

### Ne yavaş değil

Rapor edilen gecikmenin kaynağı **olmayan** şeyler, ölçülerek elendi:

- `Application.onCreate` bütünüyle **5–9 ms** (api34) / **27–46 ms** (api24).
- Hilt grafının kurulumu **1–2 ms** (api34).
- WorkManager kurulumu + enqueue **2–6 ms** (api34); Startup provider'dan
  çıkarılmış olması çalışıyor ve bir maliyet doğurmuyor.
- Room ve DataStore main thread'e hiç dokunmuyor — **0 StrictMode ihlali**.
- `MainActivity.onCreate` **12–26 ms** (api34).

Soğuk açılışın büyük kısmı process kurulumunda (`bindApplication`, dex, kaynaklar)
ve ilk composition'da geçiyor; bunların ikisi de bizim `onCreate` gövdelerimizin
dışında.

---

**Değişen dosyalar**
- `docs/PROGRESS.md` — bu kayıt. **Başka hiçbir dosya değişmedi**; ölçüm için
  eklenen geçici log `SubTrackApplication.kt` ve `MainActivity.kt`'den geri alındı,
  `git status` temiz.

**Commit'ler**
- (yok — bu tur yalnızca ölçüm; commit kararı kullanıcıda)

**Karşılaşılan sorunlar**
- `adb` PATH'te değil; SDK'dan tam yolla çağrıldı. Git Bash `/sdcard/...` yollarını
  Windows yoluna çevirdiği için `adb pull` başarısız oluyordu — `MSYS_NO_PATHCONV=1`
  ve hedef için Windows yolu kullanıldı.
- Bash kabuğunda `JAVA_HOME` boş; Android Studio'nun JBR'si (`openjdk 21.0.9`)
  elle ayarlandı.
- API 24'ün `date` komutu `%3N` desteklemiyor; ekran görüntüsü zaman damgaları
  `/proc/uptime` üzerinden alındı.
- `screencap` bir kare için ~230–300 ms harcıyor; splash penceresi bundan kısa
  olduğu için doğrudan yakalanamadı. Yakalama döngüsü `am start` ile paralel
  çevrildi ve api24/api36'da `pm clear` ile ilk açılış uzatıldı — kare böyle yakalandı.

**Sonraki faz için not**
- Karar verilecek şey bir optimizasyon değil, bir **görünürlük** sorusu: splash ile
  ilk kare arasındaki 173–212 ms'lik boşlukta ekranda ne duracağı.
- Süreyi kısaltmak istenirse tabloya göre en büyük iki kalem `bindApplication`
  (238 ms) ve ilk `doFrame` (498 ms); ikisi de `Application.onCreate` kodunda değil.
- Ölçümler boş veritabanıyla yapıldı. Kullanıcı verisiyle tekrar ölçmek, özellikle
  metin yerleşimi kalemi için, ayrı bir tur ister.

---

## [Faz 16d düzeltme] İşaret %88 Küçültüldü — 2026-09-20

**Durum:** Tamamlandı — **uygulama kodu, renkler, para sayısı, açı aralığı ve
merkez değişmedi; bildirim ikonuna dokunulmadı.**

**Neden**

16d'de çizilen işaret 108dp tuvalde **65,78dp**'ydi ve Material'ın 66dp anahtar
dairesine **0,218dp** payla giriyordu. Aritmetik olarak sığıyordu, cihazda da
kesilmiyordu (api34'te 2,06dp, api36'da 2,59dp pay ölçülmüştü) — ama maskenin
kenarına yaslanıyor, etrafında nefes alacak zemin kalmıyordu. Sığmak ile iyi
oturmak aynı şey değil.

**Yapılanlar**

*Görev 1 — üretici*

`tools/icon/generate_icons.py` içine tek bir `SCALE = 0.88` sabiti kondu;
halka, para ve ayrım artık ondan türüyor. Merkez, para sayısı, açı aralığı ve
renkler ölçeğin dışında. Yeni değerler:

| Değer | 220 birim | 108dp | ilk hâl (220 / 108dp) |
|---|---|---|---|
| Halka yarıçapı | 45,76 | 22,4640 | 52 / 25,5273 |
| Para yarıçapı | 13,20 | 6,4800 | 15 / 7,3636 |
| Ayrım | 3,52 | 1,7280 | 4 / 1,9636 |
| Kesme yarıçapı | 13,60356 | 6,6781 | 15,45859 / 7,58876 |
| İşaret dış sınırı | 58,96 | 28,9440 | 67 / 32,8909 |

Raster karolardaki `MARK_FRACTION` artık elle yazılan bir sayı değil,
`0,965 × MASKED_FILL` diye türetiliyor — yani `SCALE` değişince kendiliğinden
güncelleniyor. Değeri **0,88 → 0,7759** oldu.

Yeniden üretilenler: `ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`,
on PNG, `docs/store/icon-512.png`. `ic_launcher_background.xml` düz zemin
olduğu için ölçekten etkilenmiyor ve **bayt bayt aynı** kaldı.

*Görev 2 — bildirim ikonu DEĞİŞMEDİ*

`ic_notification.xml` yeniden üretildikten sonra **md5'i doğrulandı, aynı.**
Bildirim değerleri üreticide dp cinsinden ayrı sabitler (`NOTIF_RING = 8,54`,
`NOTIF_COIN = 2,46`, `NOTIF_GAP = 1,3`) ve halka değerlerinden türemiyor. Aynı
çarpanı ona uygulamak, 16d'de ölçülerek kazanılmış 1,3dp'lik ayrımı 1,14dp'ye
indirip o ölçümün cevabını bozardı.

*Görev 3 — yeni ölçümler*

Güvenli alan, 108dp tuvalde:

```
işaret çapı                        57,89dp   (ilk hâl 65,78dp)
66dp anahtar dairesine pay          8,11dp   (ilk hâl 0,218dp)
72dp maskeye pay                   14,11dp   (ilk hâl  4,26dp)
ayrım hattıyla 72dp maskeye pay    12,38dp
220 birim cinsinden: 58,96 < 67,22 -> 8,26 birim pay
```

Para örtüşmesi **3,0828 → 2,7129 birim**; örtüşmenin işarete oranı aynı
(%88'i de örtüşme, hem paralar hem aralık aynı çarpanla küçüldü). Üretilen yol
verisi yine bağımsız bir maske-çıkarma render'ıyla karşılaştırıldı: fark
mürekkebin **%1,82'si** kadar, yani yalnızca kenar yumuşatma. Halka üzerinde
**12/12 ayrım** sayıldı.

*Görev 4 — üç cihazda doğrulama*

| Ölçüm | api24 | api34 | api36 |
|---|---|---|---|
| Maske | yok (PNG) | daire (1,0222) | daire (1,0190) |
| Karo | 60,00dp | 51,43dp | 60,19dp |
| İşaret, ilk hâl | 52,65dp | 47,30dp | 55,01dp |
| İşaret, şimdi | **46,33dp** | **41,55dp** | **48,62dp** |
| Ölçülen oran | **0,880** | **0,878** | **0,884** |
| Maskeye pay, ilk hâl | — | 2,06dp | 2,59dp |
| Maskeye pay, şimdi | — | **4,94dp** | **5,79dp** |
| İşaret / karo | 0,878 → **0,772** | 0,920 → **0,808** | 0,914 → **0,808** |
| Sayılan ayrım | **12/12** | **12/12** | **12/12** |

API 24'te çizilenin hâlâ PNG yedeği olduğu doğrulandı: köşe erişim oranı
**1,256** (üreticinin `0,1875 × kenar` yuvarlaması 1,2588 verir) ve karo hâlâ
60,00dp — küçülen karo değil, içindeki işaret. **Monochrome api34 ve api36'da
elle açılıp bakıldı: ikisinde de on iki para ayrı duruyor.**

`icon-512.png` yeniden doğrulandı: 512×512, RGBA, **alfa her pikselde 255**,
köşeler ve merkez `(13, 26, 20)`, paralar `(212, 175, 55)`, işaret çapı
**396,5 px = karonun %77,45'i**, 41.939 bayt (önce 49.143).

**Değişen dosyalar**
- `tools/icon/generate_icons.py` — `SCALE` sabiti, türetilen `MARK_FRACTION`
- `app/src/main/res/drawable/ic_launcher_foreground.xml` — yeniden üretildi
- `app/src/main/res/drawable/ic_launcher_monochrome.xml` — yeniden üretildi
- `app/src/main/res/mipmap-*/ic_launcher{,_round}.png` — 10 dosya
- `docs/store/icon-512.png` — yeniden üretildi
- `docs/screenshots/phase-16d/*-v2.png` — 10 yeni görüntü, **eskiler duruyor**
- `docs/ARCHITECTURE.md` §27 — geometri tablosuna ilk hâl sütunu eklendi,
  "İşaret neden küçültüldü" başlığı eklendi; `docs/ROADMAP.md`, bu kayıt
- **Değişmeyenler:** `ic_notification.xml`, `ic_launcher_background.xml`,
  `values/colors.xml`, `mipmap-anydpi-v26/*.xml`, manifest, uygulama kodu

**Commit'ler**
- `(bu faz)` refactor: scale the icon mark down to 88 percent
- (bu kayıt) docs: record why the mark was scaled down and what it measures now

**Karşılaşılan sorunlar**
- **`MARK_FRACTION` sessizce yanlış kalabilirdi.** 0,88 elle yazılmış bir sayıydı
  ve işaret küçülünce raster karolar adaptive ikondan büyük görünecekti. Sayı
  artık geometriden türetiliyor; cihazda ölçülen işaret/karo oranı üç cihazda da
  0,772–0,808, yani tasarlanan 0,804 ile uyuşuyor.
- **API 34 emülatörü kilit ekranında uyandı**, ilk ekran görüntüsü tamamen
  siyah çıktı. `KEYCODE_WAKEUP` + yukarı kaydırma gerekiyor; bu TESTING.md'deki
  ikon bölümüne ayrıca eklenmedi, çünkü emülatörün genel davranışı.
- **API 36'da çekmece açma kaydırması arama alanına düştü** ve klavye açıldı.
  Çalışan kaydırma dipten kısa olanı: `input swipe 540 2200 540 500 300`.

**Sonraki faz için not**
- İşaret bir daha ayarlanacaksa tek yer var: üreticideki `SCALE`. Bildirim
  ikonu bilerek o çarpanın dışında; oraya dokunmadan önce 16d'deki ayrım
  ölçümü yeniden okunmalı.
- Ekran görüntülerinin iki takımı da repoda: eski adlar ilk hâl, `-v2` ekli
  olanlar küçültülmüş hâl. Karşılaştırma bitince eski takım silinebilir.

---

## [Faz 16d] Uygulama İkonu — On İki Para Halkası — 2026-09-20

**Durum:** Tamamlandı — **uygulama kodu değişmedi**, yalnızca kaynaklar,
varlıklar ve belgeler.

**Yapılanlar**

*Görev 1 — mevcut durumun envanteri*

`res/` altında bulunanlar ve akıbetleri:

| Dosya | Neydi | Ne oldu |
|---|---|---|
| `drawable/ic_launcher_background.xml` | şablon: `#3DDC84` yeşil + ızgara çizgileri | **yeniden yazıldı** (düz zemin) |
| `drawable/ic_launcher_foreground.xml` | şablon: Android robotu, `aapt:attr` gradyanlarıyla | **yeniden yazıldı** (on iki para) |
| `drawable/ic_notification.xml` | Faz 10b'nin geçici çan silueti | **yeniden yazıldı** |
| `mipmap-anydpi-v26/ic_launcher.xml` | şablon `<adaptive-icon>` | monochrome kendi dosyasını gösteriyor |
| `mipmap-anydpi-v26/ic_launcher_round.xml` | aynısı | aynısı |
| `mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.webp` | şablon raster | **silindi** (5 dosya) |
| `mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher_round.webp` | şablon raster | **silindi** (5 dosya) |

Manifest `@mipmap/ic_launcher` ve `@mipmap/ic_launcher_round`'a işaret ediyor;
`PaymentReminderNotifier.kt:106` `R.drawable.ic_notification` kullanıyor.
**Üçü de olduğu gibi kaldı** — isimler korunduğu için manifeste ve koda
dokunmak gerekmedi. `ic_launcher_monochrome.xml` ve `values/colors.xml` yeni.
Şablondan kalan **on `.webp`** silindi, yerlerine on PNG geldi.

*Görev 2 — geometri, ölçekleme ve güvenli alan*

Verilen 220 birimlik tanım 108dp viewport'a `k = 108/220 = 0,490909` ile
çevrildi:

| Değer | 220 birim | 108dp |
|---|---|---|
| Merkez | 110 | 54 |
| Para yarıçapı | 15 | 7,3636 |
| Halka yarıçapı | 52 | 25,5273 |
| Ayrım | 4 | 1,9636 |
| Komşu kesme yarıçapı | 15,45859 | 7,58876 |
| İşaret yarıçapı (52+15) | 67 | 32,8909 |

Komşu para merkezleri arası `2 × 52 × sin(15°) = 26,9172` birim; iki yarıçap
30, yani paralar **3,0828 birim örtüşüyor**. Isırık yarıçapı
`(26,9172 + 4) / 2 = 15,45859` seçilerek görünen kenarlar arasında tam 4 birim
kaldı — on iki paranın on ikisi de aynı biçimde, simetrik.

Güvenli alan hesabı: 108dp tuvalde maskelenen alan 72dp → 36dp yarıçap →
**73,33 birim**; Material'ın 66dp anahtar dairesi → 33dp → **67,22 birim**.
İşaretin dış sınırı **67 birim**, yani dar olan sınıra bile giriyor:
**65,78dp çap, 66dp daireye 0,218dp payla.**

*Görev 3 — bildirim ikonu ölçüldü, sonra yeniden çizildi*

İşaret 24dp'ye olduğu gibi indirilip **gerçek piksel boyutlarında
rasterlendi** (24/36/48/63/72 px). 4 birimlik ayrım orada **0,657dp** ediyor:
mdpi'de 0,66 px, hdpi'de 0,99 px. Görüntülerde halka xhdpi altında gri bir
simide dönüşüyordu.

İki seçenek de denendi: **(a)** on iki parayı küçültüp aralarını açmak — çok
okunaklı ama işaret "yükleniyor" çemberine dönüşüyor; **(b)** altı paraya
inmek — dokusu duruyor ama on iki ayın anlamı gidiyor. Seçilen yol
**ayrımı kalınlaştırmak**: on iki para kaldı, ayrım **1,3dp** oldu (yaklaşık
iki katı) ve işaret 24dp tuvalde **22dp canlı alana** çizildi.
Halka yarıçapı 8,54dp, para yarıçapı 2,46dp.

*Görev 4 — mağaza karosu*

`docs/store/icon-512.png`: **512×512**, 32 bit RGBA, **alfa her pikselde 255**,
köşeler ve merkez `(13, 26, 20)` = `#0D1A14`, paralar `(212, 175, 55)` =
`#D4AF37`, 49.143 bayt. Maske yok, yuvarlatma yok, şeffaf kenar yok.

*Görev 5 — üç cihazda doğrulama*

| Ölçüm | api24 | api34 | api36 |
|---|---|---|---|
| Başlatıcı maskesi (`max/min`, 720 ışın) | yok (PNG) | **1,022 = daire** | **1,019 = daire** |
| Karo | 60,00dp | 51,43dp | 60,19dp |
| İşaret | 52,7dp | 47,30dp | 55,01dp |
| Maskeye pay | — | **2,06dp** | **2,59dp** |
| Durum çubuğunda işaret | 15,00dp | 13,71dp | 12,95dp |
| Sayılan ayrım | **12/12** | **12/12** | **12/12** |
| Ayrım genişliği | 2,06–2,14 px | 0,49–1,89 px | 1,43–1,83 px |

API 24'te çizilen gerçekten PNG yedeği: köşe yuvarlaması çapraz/eksen oranı
**1,251** ölçüldü, üreticinin çizdiği `0,1875 × kenar` yuvarlaması **1,2588**
verir; ayrıca işaret/karo oranı 0,88 (adaptive ikonda 0,914).

**Temalı ikon (monochrome) api34 ve api36'da elle açılıp doğrulandı.** İkisinde
de on iki para ayrı ayrı duruyor, halka diske dönmüyor. API 36 ayrıca maske
şeklini yazıyla da söylüyor: *Wallpaper & style → Icons* → "Circle, default".

**Değişen dosyalar**
- `tools/icon/generate_icons.py` — **yeni.** Bütün varlıkları tek tanımdan üretir
- `app/src/main/res/values/colors.xml` — **yeni.** İki marka değeri + iki tint
- `app/src/main/res/drawable/ic_launcher_background.xml` — düz zemin
- `app/src/main/res/drawable/ic_launcher_foreground.xml` — on iki para
- `app/src/main/res/drawable/ic_launcher_monochrome.xml` — **yeni.** Aynı yollar, tek renk
- `app/src/main/res/drawable/ic_notification.xml` — ayrı çizim, geniş ayrım
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher{,_round}.xml` — monochrome katmanı
- `app/src/main/res/mipmap-*/ic_launcher{,_round}.png` — **10 yeni**, 10 `.webp` silindi
- `docs/store/icon-512.png` — **yeni.** Play Console karosu
- `docs/screenshots/phase-16d/` — 18 görüntü
- `.gitignore` — `__pycache__/` ve `*.pyc` (yeni Python aracı için)
- `docs/ARCHITECTURE.md` — §27, `docs/ROADMAP.md`, `docs/TESTING.md`, bu kayıt

**Commit'ler**

- `3839450` feat: draw the app icon as a ring of twelve coins
- `0b5f982` feat: redraw the notification icon from the coin ring
- `b5ca7be` feat: add the 512x512 store icon and the three-device icon screenshots
- (bu kayıt) docs: record the icon geometry and its verification on three devices

**Karşılaşılan sorunlar**

- **Yol verisi elle yazılamazdı.** Isırılmış para dört yaylı kapalı bir yol ve
  SVG yay bayrakları (`large-arc`, `sweep`) gözle seçilemiyor. Üretici bayrakları
  yayın orta noktasını sınayarak seçiyor; sonuç, aynı geometrinin bağımsız bir
  maske-çıkarma render'ıyla piksel piksel karşılaştırılarak doğrulandı —
  mürekkebin **%1,3'ü** kadar fark, yani yalnızca kenar yumuşatma.
- **Maske şekli ölçümü ilk turda yanıldı.** "Arka plan rengine benzemeyen
  piksel" testi API 36'nın gradyanlı çekmece arkaplanında köşelerde tetikleniyor
  ve daire `max/min = 1,26` (yuvarlatılmış kare) okunuyordu. Parlaklık eşiğine
  geçilince ikisi de 1,02'ye indi. Yanlış ölçüm **belgeye girmeden** düzeltildi.
- **`./gradlew :app:connectedDebugAndroidTest` bildirim ekran görüntüsü için
  işe yaramıyor:** Gradle tur bitince iki APK'yı da kaldırıyor, bildirim de
  paketle birlikte gidiyor. İki APK elle kurulup `am instrument` çağrıldı.
- **API 24'te eski bir kurulum imza çakışması verdi**
  (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`); önce `uninstall` gerekti.
- **Temalı ikonlar yalnızca ana ekranda uygulanıyor**, çekmecede değil — ve
  `google_apis_playstore` imajları root kabul etmediği için tercih dosyadan
  yazılamıyor. Ayar arayüzden açıldı, uygulama çekmeceden ana ekrana
  `input motionevent` ile sürüklendi (`input swipe` kaydırma sanılıyor).
- **API 36 açılışında "System UI isn't responding" çıktı**; beklenip yeniden
  denendi (TESTING.md'de zaten kayıtlı bir davranış).

**Sonraki faz için not**
- Özellik grafiği (1024×500) **bu fazda üretilmedi**, ayrı bir iş olarak duruyor.
- Mağaza karosu `docs/store/` altında duruyor ama Play Console'a **yüklenmedi**;
  Console'da görünen kırpması orada bir kez daha bakılmalı.
- İkon varlıklarından herhangi biri değişecekse elle düzenlenmez:
  `python tools/icon/generate_icons.py` çalıştırılır. Betiğin başındaki
  sabitler tasarımın kendisidir.

---

## [Faz 16g] Release AAB ve Foreground Service Tipi Denetimi — 2026-09-19

**Durum:** Tamamlandı — **üretim kodu değişmedi**, yalnızca belge.

**Yapılanlar**

*Görev 0 — foreground service denetimi (her şeyden önce)*

Play, Android 14+ hedefleyen uygulamalardan manifestte bildirilen her
foreground service **tipi** için Console'da beyan istiyor. Denetim varsayımla
değil, release birleşik manifestinin satırlarıyla yapıldı. Dört bulgu:

1. **`FOREGROUND_SERVICE` ile başlayan izinler — tek satır, alt tip yok.**
   Birleşik manifestte yalnızca
   `<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />`
   (satır 19). `FOREGROUND_SERVICE_DATA_SYNC`, `_SHORT_SERVICE` ve kardeşleri
   **hiç geçmiyor.** Birleşik manifest yetmez diye **bütün bağımlılık
   manifestleri** de tarandı (`transforms/*/AndroidManifest.xml`) — hiçbirinde
   `FOREGROUND_SERVICE_` dizgesi yok.
2. **`SystemForegroundService`'te `foregroundServiceType` özniteliği yok.**
   Eleman dört öznitelik taşıyor: `name`, `directBootAware`, `enabled`,
   `exported` (satır 91-94). `foregroundServiceType` dizgesi birleşik
   manifestin tamamında geçmiyor; AAB'nin base manifestinde de geçmiyor.
3. **İkisi de `androidx.work:work-runtime:2.11.2`'den geliyor.** Manifest
   merger raporu: izin için satır 365-368 (`work-runtime AndroidManifest.xml:26:5-77`),
   servis için 383-396 (`…:46:9-52:35`). Uygulamanın kendi manifestinde tek bir
   `FOREGROUND_SERVICE` satırı yok.
4. **Kodda foreground service yolu hiç kullanılmıyor.** `app/src/` altında
   `setForeground`, `setExpedited`, `ForegroundInfo`, `OutOfQuotaPolicy` →
   **hit yok**. `OneTimeWorkRequest` yalnızca `androidTest`'te iki satır, ikisi
   de düz `OneTimeWorkRequestBuilder<…>().build()`. `PaymentReminderScheduler`
   tek bir kısıtsız, gecikmeli `PeriodicWorkRequest` kuruyor.

**Karar: Görev 0(c) uygulanmadı.** Alt tip olmadığı için `tools:node="remove"`
ile kaldırılacak bir şey yok; manifeste ve üretim koduna dokunulmadı. Temel
`FOREGROUND_SERVICE` izni de kaldırılmadı — kapsam denetim ve AAB, izin budama
değil; izin kütüphaneden geliyor ve kaldırmak ölçülmemiş bir risk alır.

*Görev 1 — sürüm numaraları*

`versionCode = 1`, `versionName = "1.0"`. Play'in ilk yayın beklentisiyle
birebir; **değiştirilmedi, sorulacak bir sapma çıkmadı.**

*Görev 2-3 — AAB ve imza*

`./gradlew :app:bundleRelease` → BUILD SUCCESSFUL (22 sn, 9 görev koştu).
`signReleaseBundle` çalıştı. `jarsigner -verify` → **`jar verified.`**,
`CN=ElinaDorothea, OU=Development, O=SubTrack, L=Denizli, ST=Denizli, C=TR`,
SHA384withRSA / 2048-bit, geçerlilik 2026-09-17 → **2054-02-02**.

*Görev 4 — bundletool ile iki cihazda kurulum*

APK seti üretildi ve **`subtrack_min_api24`** ile **`subtrack_wide_api34`**'e
kuruldu. İkisinde de `pm path` **üç** parça döndü: `base.apk`,
`split_config.en.apk`, `split_config.x86_64.apk`. Temel tur iki cihazda da
geçti — abonelik ekleme, toplam, istatistik, ayarlar, bildirim, crash tamponu
boş.

*Görev 5-6 — içerik ve yol*

Tek `base` modülü, dört ABI, altı yoğunluk, 86 locale. Base manifest
`versionCode=1 / versionName=1.0 / minSdk=24 / targetSdk=36`.
Yol: `app/build/outputs/bundle/release/app-release.aab` — **commit edilmedi**
(`.gitignore`'da `*.aab`).

**Ölçümler**

| | Değer |
|---|---|
| AAB | **4.598.466 B** |
| 16c universal APK | 2.127.430 B |
| Oran | **2,16 kat** — AAB tüm ABI/dil/yoğunluğu bölünmemiş taşıyor |
| APK seti (`.apks`) | 11.944.603 B |
| Cihaza inen parça | base + `split_config.en` + `split_config.x86_64` |

**Değişen dosyalar**
- `docs/ARCHITECTURE.md` — §26 eklendi (AAB içeriği + FGS denetimi ve kararı)
- `docs/TESTING.md` — "AAB ile Test Etme" bölümü eklendi (8 adım)
- `docs/ROADMAP.md` — Faz 16'ya iki madde işaretlendi
- `docs/PROGRESS.md` — bu kayıt

**Karşılaşılan sorunlar**

- **Gradle önbelleğindeki `bundletool-1.18.3.jar` çalıştırılamıyor**
  (`no main manifest attribute`) — o kütüphane sürümü. Çalıştırılabilir olan
  `bundletool-all-1.18.3.jar` GitHub'dan indirildi ve
  `C:\Users\cane7\tools\bundletool-all-1.18.3.jar` altına kondu. **Depoya
  girmedi.**
- **`install-apks` `ANDROID_HOME` olmadan düşüyor** — `CommandUtils.getAdbPath`
  yığın izi bırakıyor ama çıktı "Success" satırıyla karışabiliyor. Kurulum her
  seferinde `pm path` ile ayrıca doğrulandı.
- **API 34 emülatörü anlık görüntüden açılmadı** — `default_boot` yüklerken
  takıldı, süreç öldürülüp `-no-snapshot-load` ile soğuk açıldı. Ürünle ilgisi
  yok.
- **Duvar saatini ileri almak tek başına işi koşturmuyor.** JobScheduler'ın
  gecikmesi **elapsed realtime** tabanlı; saat sıçraması yalnızca
  WorkManager'ın `lastEnqueueTime + initial_delay` denetimini açıyor. İş ancak
  saat hedefi geçtikten **sonra** `cmd jobscheduler run -f` ile koştu.
- **API 34'te `jobscheduler run` ad alanı istiyor.** Ad alanı verilmeyince
  `Could not find job 0 in package com.elinacn.subtrack`. Doğrusu
  `-n androidx.work.systemjobscheduler`. API 24'te ad alanı yok.
- **API 34'te klavye düzeni kaydırıyor.** Ad alanına yazınca form yukarı
  kayıyor ve önceki ekran görüntüsünün koordinatları geçersiz oluyor; ilk
  denemede ad "Spotify89.9" oldu ve para birimi EUR'ya atladı. Her alan
  dokunuşundan sonra ekran görüntüsü yenilenerek düzeltildi. Ürün hatası
  değil, test yöntemi notu — `TESTING.md`'ye yazıldı.
- **API 24'te `time_detector` yok** (`Can't find service`), saat Ayarlar
  arayüzünden kuruldu. İki cihazda da saat tur sonunda geri alındı ve gerçek
  saatle karşılaştırılarak doğrulandı.
- **`uiautomator dump /sdcard/…` Git Bash'te yol dönüşümüne uğruyor**
  (`/Files/Git/sdcard/ui.xml`). Doğrulama ekran görüntüleriyle yapıldı.

**Doğrulanmayan tek nokta**

Alt tip bildirilmediğine göre Play Console'da foreground service tipi beyan
formunun **açılmaması bekleniyor**. Bu manifest bulgularından çıkarılan bir
sonuç; **Console ekranında görülmedi.** İlk yüklemede doğrulanacak.

**Sonraki faz için not**
- AAB ilk yüklemede Play'in imzalama devrine (Play App Signing) girecek;
  yüklenen anahtar **upload key** olacak. Keystore'un yedeği olmadan sonraki
  sürüm yüklenemez.
- `targetSdk` maddesi hâlâ açık; 36 şu an Play'in eşiğinin üstünde ama madde
  her yayın döneminde tekrar okunmalı.
- Sonraki yüklemede `versionCode` **artırılmalı** — Play aynı değeri ikinci
  kez kabul etmiyor.

---

## [Faz 16e-2] Mağaza Ekran Görüntüleri — 2026-09-19

**Durum:** Tamamlandı — **üretim kodu değişmedi**, yalnızca çekim ve belge.

**Yapılanlar**

*Görev 1 — mağaza AVD'si*

`subtrack_store_api34` kuruldu: **1080x1920, 420 dpi, API 34, gestural**.
Mevcut geniş AVD'ler kullanılamıyordu — Play en fazla 2:1 kabul ediyor,
1080x2400 = 2,22:1 reddedilir. Cihazda doğrulandı, varsayılmadı:

| Ne | Komut | Çıktı |
|---|---|---|
| Çözünürlük | `wm size` | `Physical size: 1080x1920` |
| Yoğunluk | `wm density` | `Physical density: 420` |
| Efektif genişlik | 1080 / (420/160) | **411dp** — `subtrack_wide_api34` ile aynı |
| Gezinme | `cmd overlay list android` | `[x] ...navbar.gestural` |
| API | `getprop ro.build.version.sdk` | `34` |
| Saat dilimi | `getprop persist.sys.timezone` | `GMT` |

411dp kasıtlı: layout, ölçüm turlarında test edilmiş dp genişliğinde kalıyor,
yani mağaza için yeni bir genişlik sınıfı açılmıyor. `pixel_6` profili 1080x2400
ile geldiği için `config.ini` elle düzeltildi; kurulum komutu TESTING.md'de.

*Görev 2 — fikstür*

6 abonelik, dört kategori, `run-as` ile veritabanına yazıldı (16e'deki yol).
Ana para birimi TRY (varsayılan, DataStore'a yazmak gerekmedi).

| Ad | Tutar | Periyot | Kategori | Sonraki ödeme | Kartta |
|---|---|---|---|---|---|
| Netflix | ₺229,99 | aylık | Eğlence | bugün | "Bugün ödenecek" |
| Spotify | ₺87,99 | aylık | Eğlence | +1 gün | "1 gün kaldı" |
| Gym | ₺1.450,00 | aylık | Sağlık | +5 gün | "5 gün kaldı" |
| Dropbox | ₺39,90 | **haftalık** | Diğer | +3 gün | "3 gün kaldı" |
| iCloud | **$2,99** | aylık | Üretkenlik | +12 gün | "12 gün kaldı" |
| Notion | **€96,00** | **yıllık** | Üretkenlik | +23 gün | "23 gün kaldı" |

Dört TRY, bir USD, bir EUR; dört aylık, bir yıllık, bir haftalık. Çoklu para
birimi görünüyor ama okuyanı yormuyor.

`monthly_snapshots`'a geçmiş beş ay (202604-202608) yazıldı; 202609'u kaydedici
uygulama açılır açılmaz kendisi yazdı. `MonthlyTrend.MAX_MONTHS` = 6 olduğu için
pencere tam doldu ve grafik altı sütunla çıktı.

Veritabanı dökümü — fikstür kurulduktan **sonra**, cihazdan geri okundu:

```
(202604, 198750, 'TRY', 1777334400000)
(202605, 205430, 'TRY', 1779926400000)
(202606, 199880, 'TRY', 1782604800000)
(202607, 222615, 'TRY', 1785196800000)
(202608, 231540, 'TRY', 1787875200000)
(202609, 243860, 'TRY', 1789776135582)   <- uygulamanın kendi yazdığı satır
```

**Toplam ₺2.438,60** — yuvarlak değil, gerçekçi. Bu sayı elle hesaplanmadı,
uygulamadan okundu: kur dönüşümünün son kuruşu `CurrencyConverter`'ın kendi
yuvarlamasına bağlı ve elle yapılan hesap bir kuruş şaşıyordu. Ekranda ne
yazıyorsa kayda o geçti.

202609 - 202608 = **+₺123,20**, yani "geçen aya göre" satırı da çıkıyor.

*Görev 3 — durum çubuğu*

SystemUI demo modu bu imajda **çalışıyor**. Saat 09:41, pil %100, wifi ve
sinyal tam dolu, bildirim ikonu yok.

**İlk deneme yarım kaldı:** wifi simgesi "internet yok" ünlemiyle (`!`) ve
sinyal çubuğu yarım çizildi. Sebep eksik `-e fully true`; eklendikten sonra
ikisi de tam doldu. Komutların tamamı TESTING.md'de.

Tur bitince demo modundan çıkıldı ve `sysui_demo_allowed` 0'a alındı.

*Görev 4 — FAB örtüşmesi*

**Liste kaydırılmadı — gerek kalmadı.** Uygulamanın açıldığı konumda FAB zaten
Spotify ile iCloud kartlarının arasındaki boşluğa denk geliyor. Bu konum
seçildi çünkü hem temiz hem de kullanıcının gerçekten gördüğü ilk kare.

Ölçüm 16e'nin iki okumasıyla, üç ana ekran çekiminin **üçünde de**:

| Okuma | Değer |
|---|---|
| FAB ikonu (dump) | `[933,1710][996,1773]` — 63x63 px = 24dp |
| FAB kabı (ikon + her kenardan 16dp = 42 px) | `[891,1668][1038,1815]` — 147x147 px = **56dp** |
| FAB kabının içindeki `primary` piksel | **0** (açık temada `#0B5C3F`, koyuda `#D4AF37`) |
| FAB kutusuna değen tutar öbeği | **0** |

Boşluklar, en yakın iki tutara:

| Çekim | Üstteki tutar (Spotify) | FAB'a uzaklık | Alttaki tutar (iCloud) | FAB'a uzaklık |
|---|---|---|---|---|
| `store-01-home-tr` | `[867,1593][993,1629]` | **39 px = 15dp** | `[892,1890][993,1920]` | **75 px = 29dp** |
| `store-01-home-en` | `[864,1593][993,1622]` | **46 px = 18dp** | `[891,1890][993,1920]` | **75 px = 29dp** |
| `store-06-home-dark-en` | `[864,1593][993,1622]` | **46 px = 18dp** | `[891,1890][993,1920]` | **75 px = 29dp** |

Kart kutusu olarak bakıldığında FAB iki kartın sınırını kesiyor (FAB 56dp,
kartlar arası boşluk 16dp — kesmemesi zaten mümkün değil). Örtüşmenin ölçüsü
kart kutusu değil **tutarın kendisi**, ve ona değmiyor.

*Görev 5-6 — çekimler*

12 görüntü: beş ekran × iki dil, artı iki koyu tema. Açık tema, ham uygulama
ekranı — çerçeve, metin, logo eklenmedi.

**Ekleme sheet'i iki dilde farklı çerçevelendi, sebebiyle:** Türkçe'de kategori
chip'lerinin dördü tek satıra sığıyor (İngilizce'de "Other" alt satıra kayıyor),
yani Türkçe içerik 147 px daha kısa ve "Sonraki Ödeme" alanı kaydırma
penceresinin kenarına denk geliyor — ilk çekimde alanın yazısı **harflerin
ortasından kesildi**. Türkçe sheet 71 px kaydırıldı; alan tamamen görünüyor,
karşılığında başlık kareden çıkıyor. İngilizce'de kaydırma aynı sorunu bu kez
"Subscription Name" alanının üstünde çıkardığı için o çekim kaydırılmadan
bırakıldı. İkisi de temiz; ikisi de "alanlar boş, chip'ler görünür" şartını
karşılıyor.

*Görev 7 — dosyalar ve doğrulama*

`docs/screenshots/store/` — 12 dosya. **Her biri tek tek doğrulandı**, hepsi
geçti:

| Dosya | Boyut | Format | Alfa | Bayt |
|---|---|---|---|---|
| `store-01-home-tr.png` | 1080x1920 | 24-bit PNG | yok | 142.250 |
| `store-02-stats-tr.png` | 1080x1920 | 24-bit PNG | yok | 116.708 |
| `store-03-trend-tr.png` | 1080x1920 | 24-bit PNG | yok | 99.833 |
| `store-04-add-tr.png` | 1080x1920 | 24-bit PNG | yok | 98.794 |
| `store-05-settings-tr.png` | 1080x1920 | 24-bit PNG | yok | 81.213 |
| `store-01-home-en.png` | 1080x1920 | 24-bit PNG | yok | 149.344 |
| `store-02-stats-en.png` | 1080x1920 | 24-bit PNG | yok | 128.280 |
| `store-03-trend-en.png` | 1080x1920 | 24-bit PNG | yok | 106.178 |
| `store-04-add-en.png` | 1080x1920 | 24-bit PNG | yok | 83.419 |
| `store-05-settings-en.png` | 1080x1920 | 24-bit PNG | yok | 81.653 |
| `store-06-home-dark-en.png` | 1080x1920 | 24-bit PNG | yok | 135.969 |
| `store-07-stats-dark-en.png` | 1080x1920 | 24-bit PNG | yok | 128.340 |

En büyük dosya 149 KB — 8 MB sınırının çok altında. PNG başlığından okunan
değer 8 bit/kanal truecolour, yani tam olarak 24-bit.

**Alfa kanalı vardı ve kaldırıldı.** `adb exec-out screencap -p` RGBA yazıyor;
12 dosyanın 12'si de alfa kanalıyla geldi. Kanal her dosyada tamamen opak
(255) olduğu **önce kanıtlandı**, sonra RGBA→RGB çevrildi — yani hiçbir piksel
değişmedi, yalnızca kanal düştü. Dönüşümden sonra her dosya yeniden doğrulandı.

**Değişen dosyalar**
- `docs/screenshots/store/` — 12 yeni görüntü
- `docs/TESTING.md` — `subtrack_store_api34` AVD tablosuna eklendi; yeni
  "Mağaza Ekran Görüntüleri (Play Console)" bölümü: AVD kurulumu, demo modu
  komutları, fikstür kurma yöntemi, dil ve tema değiştirme, FAB ölçümü,
  alfa kanalı kaldırma
- `docs/PROGRESS.md` — bu kayıt

**Karşılaşılan sorunlar**
- **Git Bash'te ters bölü kaçışı yönlendirmeyi yedi.** Yardımcı betikteki
  yönlendirme hedefi çift tırnak içinde kaçışlı dolar işaretine dönüşüp
  dosyayı üst dizine sabit bir adla yazdı; ekran görüntüsü alınmış gibi
  görünüp hiçbir şey kaydedilmedi. Yönlendirme hedefleri POSIX yoluna
  çevrildi; Windows yolu yalnızca `adb push` argümanında kaldı.
- **`adb push` kaynak yolu Windows biçiminde olmalı.** MSYS, POSIX yolunu
  çevirip `remote secure_mkdirs failed` aldı; `MSYS_NO_PATHCONV=1` + Windows
  yolu ile geçti.
- **Elle yapılan toplam hesabı bir kuruş şaştı** (₺2.438,61 dedi, uygulama
  ₺2.438,60 yazdı). Kayda uygulamanın okunan değeri geçti.
- **İlk demo modu turunda wifi ünlemli çıktı** — `-e fully true` eksikti.
- **Türkçe ekleme sheet'inde tarih alanının yazısı kesildi** (yukarıda).

**Sonraki faz için not**
- Fikstür temizlendi (`pm clear`), demo modu kapatıldı, `cmd uimode night no`,
  uygulama dili boşaltıldı, emülatör kapatıldı.
- `subtrack_store_api34` **yalnızca mağaza çekimi içindir.** 1080x1920 gerçek
  bir telefon oranı değil; ölçüm turları eski dört AVD'de kalmalı.
- Ölçüm ve çekim betikleri geçici dizinde kaldı, repoya girmedi. Aynı seti
  yeniden çekmek gerekirse yol TESTING.md'de tam olarak yazılı.
- Play listeleme metni, özellik grafiği (1024x500) ve uygulama simgesi
  (512x512) **hâlâ eksik** — bu tur yalnızca telefon ekran görüntüleriydi.

---

## [Faz 16e] FAB'ın Liste Satırını Örtmesi — Ölçüm ve Karar — 2026-09-19

**Durum:** Tamamlandı — **kod değişmedi**, ölçüm ve karar kayda geçti.

**Yapılanlar**

*Görev 1 — önce ölç*

Sabit fikstür: 7 abonelik, `run-as` ile veritabanına yazıldı (§"86-95 için veri
nasıl kurulur" ile aynı yol). Üç cihaz (API 29, 34, 36) × iki yazı boyutu
(fs 1.0, fs 2.0). Her cihazda üç konum ölçüldü — açılış, liste sonu, ve liste
sonundan yukarı doğru sekiz duraklık tarama — çünkü tek bir konum sorunun
hangi kaydırma konumlarında çıktığını söylemiyor.

Her durakta **iki bağımsız okuma** alındı:

- **Koordinat:** `uiautomator dump`'tan FAB ile satırların kutuları.
- **Piksel:** ekran görüntüsünde tutarın `colorScheme.primary` renkli
  piksellerinin sayısı. Tutar, kartta bu renkteki **tek** metindir, yani
  sayının düşmesi tutarın bir kısmının ekrana ulaşmadığı demektir.

**Dump FAB'ın kabını vermiyor, 24dp'lik ikonunu veriyor.** Kap her kenardan
16dp büyütülerek türetildi; sonuç §16'da kayıtlı `[891,2148][1038,2295]` ile
**birebir** tuttu, yani türetme doğrulandı.

*Bulgu 1 — örtüşme gerçek, altı ölçümün altısında da var*

| Cihaz | fs | Örtülen satır | Satırın kutusu | Tutarın çizilen kısmı |
|---|---|---|---|---|
| API 29 | 1.0 | Netflix | `[0,1033][720,1263]` | 273 / 961 px = **%28,4** |
| API 29 | 2.0 | Netflix | `[0,969][720,1280]` | 2155 / 3331 px = **%64,7** |
| API 34 | 1.0 | Netflix | `[0,2094][1080,2395]` | 473 / 1661 px = **%28,5** |
| API 34 | 2.0 | Netflix | `[0,1928][1080,2392]` | 4195 / 5604 px = **%74,9** |
| API 36 | 1.0 | Netflix | `[0,2095][1080,2396]` | 473 / 1660 px = **%28,5** |
| API 36 | 2.0 | Netflix | `[0,1925][1080,2389]` | 4301 / 5601 px = **%76,8** |

fs 1.0'da tutarın dörtte üçü kayboluyor; fs 2.0'da tutar daha geniş olduğu
için FAB'ın örtemediği kısım büyük kalıyor, ama yine kesiliyor.

*Bulgu 2 — liste **sonu** zaten temiz, yani istenen düzeltme zaten kodda*

| Cihaz | fs 1.0 | fs 2.0 |
|---|---|---|
| API 29 | son satırın altı → FAB'ın üstü **+16 px = 8dp** | **+16 px = 8dp** |
| API 34 | **+21 px = 8dp** | **+21 px = 8dp** |
| API 36 | **+21 px = 8dp** | **+21 px = 8dp** |

`Dimens.ListBottomSpacing` = 80dp, 16a'dan beri `contentPadding`'in alt payına
ekleniyor. FAB'ın istediği 16dp + 56dp = 72dp; 80dp onu 8dp ile geçiyor.
Kartın kendi 8dp alt kenar boşluğuyla birlikte görünen boşluk 16dp.

*Bulgu 3 — hangi koşulda (API 36, fs 1.0)*

- **4 abonelik:** liste yalnızca 129 px kayıyor; son satırın tutarı her
  konumda tam çiziliyor (1662 px). Örtüşme **yok**.
- **5 abonelik:** örtüşme **var** — 473 px, %28.
- **7 abonelik:** kaydırma yolunun kabaca yarısında bir satırın tutarı
  FAB'ın arkasında.

*Görev 2 — yapılmadı, gerekçesiyle*

İstenen düzeltme "`contentPadding`'e FAB'ı geçecek kadar alt boşluk ekle"ydi.
Ölçüm iki şeyi gösterdi: (a) o boşluk zaten var ve zaten yetiyor, (b) örtüşme
liste sonunda değil, **ortasında** oluyor. `contentPadding` yalnızca içeriğin
uçlarda nerede durduğunu belirler; kaydırma sırasında her satır FAB'ın
bandından geçer. Yani boşluğu büyütmek örtüşmeyi değiştirmez, listenin altına
ölü alan ekler.

**Karar: değişiklik yapılmadı, madde kapatıldı.** Kayan FAB'ın içeriği geçici
olarak örtmesi Android'de olağan; liste sonu garantisi duruyor, satır
TalkBack'e tutarıyla birlikte tek parça okunuyor, ve tutarı görmek için bir
parmak ucu kaydırma yetiyor. Bunu tümden kaldırmanın yolu FAB'ı kaydırırken
gizlemek; giriş noktasının kaydırma sırasında yok olması örtülen bir satırdan
pahalı görüldüğü için o yola gidilmedi.

**Değişen dosyalar**
- `docs/ARCHITECTURE.md` §16 — `ListBottomSpacing`'in neyi garanti ettiği,
  neyi etmediği yazıldı; ortadaki örtüşmenin kabul edildiği ve gerekçesi
- `docs/PROGRESS.md` — bu kayıt
- `docs/screenshots/phase-16e/` — 9 görüntü (üç cihaz × iki yazı boyutu
  örtüşme, iki cihaz liste sonu, bir de 4 abonelikli örtüşmesiz hâl)

**Karşılaşılan sorunlar**
- **Üç emülatör aynı anda "Process system isn't responding" getirdi.** Ölçüm
  sırayla, tek emülatörle yapıldı. API 36 açılışında bir kez daha çıktı;
  sistem oturana kadar beklenip tur yeniden koşuldu.
- **İlk turun kaydırmaları boşa gitti:** betik sabit süre bekliyordu, uygulama
  o sürede açılmamıştı ve fiskeler başka bir pencereye düştü. Betik artık
  listenin kendisini bekliyor.
- **`input swipe` mesafesinin tamamı kaydırmaya dönmüyor** — dokunma eşiği
  (~8dp) düşüyor, 60 px'lik fiske ~39 px kaydırıyor. Tarama adımları buna
  göre seçildi.
- **Piksel sayacı başta FAB'ın kendi "+" ikonunu da sayıyordu** (ikon
  `onPrimaryContainer`, tutar `primary`, ikisi aynı yeşil aile). Sayım FAB
  kutusunun dışıyla sınırlandırıldı.

**Sonraki faz için not**
- Ölçüm betikleri geçici dizinde kaldı, repoya girmedi. Aynı ölçüm gerekirse
  yol şu: fikstürü `run-as` ile kur, `uiautomator dump` + `screencap` al,
  FAB ikonunu 16dp büyüterek kabı bul, tutarı `primary` piksel sayısıyla ölç.
- Fikstür kurarken uygulamanın veritabanını **açmış** olması gerekiyor; taze
  kurulumda `databases/` dizini uygulama bir kez açılana kadar yok ve ana
  `.db` dosyası WAL katlanana dek "file is not a database" der.

---

## [Faz 16f] Android Auto Backup — Durum Tespiti ve Yapılandırma — 2026-09-18

**Durum:** Tamamlandı

**Yapılanlar**

*Görev 1 — durum tespiti (kod yazmadan önce)*

- a) `android:allowBackup` manifestte **vardı ve `true`** idi
  (`app/src/main/AndroidManifest.xml:11`). Yazılmasaydı da varsayılan açıktır.
- b) `android:dataExtractionRules` (satır 12) ve `android:fullBackupContent`
  (satır 13) da tanımlıydı; işaret ettikleri iki dosya da vardı **ama ikisi de
  Android Studio şablonuydu** — içleri örnek yorum, tek bir kural yok. Boş
  kural kümesi "platformun alabildiği her şeyi al" demek.
- c) Birleşik manifestte üçü de duruyor ve üçü de **bizim manifestimizden**
  geliyor; hiçbir kütüphane katkı vermiyor.
  `app/build/outputs/logs/manifest-merger-debug-report.txt`:

  ```
  android:fullBackupContent
      ADDED from .../app/src/main/AndroidManifest.xml:13:9-54
  android:allowBackup
      ADDED from .../app/src/main/AndroidManifest.xml:11:9-35
  android:dataExtractionRules
      ADDED from .../app/src/main/AndroidManifest.xml:12:9-65
  ```

  Release birleşik manifesti de aynı üç değeri taşıyor.
- d) **Cihazda ölçüldü: yedekleme fiilen çalışıyordu.** API 34'te, hiçbir
  değişiklik yapılmadan:

  ```
  $ adb shell bmgr backupnow com.elinacn.subtrack
  Running incremental backup for 1 requested packages.
  Package @pm@ with result: Success
  Package com.elinacn.subtrack with progress: 2048/84992
  ...
  Package com.elinacn.subtrack with progress: 87040/84992
  Package com.elinacn.subtrack with result: Success
  Backup finished with result: Success
  ```

  **85 KB uygulama verisi sandık dışına çıktı.** Yani "veriler cihazdan
  çıkmıyor" cümlesi o anda yanlıştı.

*Görev 2 — açık yapılandırma*

- Manifestte değişiklik **gerekmedi**: üç öznitelik de zaten doğru yazılmıştı.
  Değişen, iki XML'in içeriği.
- Yedeğe girenler: `domain="database" path="."` ve
  `domain="file" path="datastore"`. Bir `<include>` yazmak kuralı beyaz listeye
  çevirdiği için adı geçmeyen her şey dışarıda kalıyor.
- **Veritabanı domain'i bütün olarak alınıyor, `subtrack.db` tek başına
  değil.** Ölçüm bunu zorunlu kıldı: fikstürde ana dosya **4096 bayt**,
  `subtrack.db-wal` **107152 bayt**. Room WAL kipinde çalıştığı için işlenmiş
  satırlar WAL'de duruyor; yalnızca ana dosya yedeklenseydi geri yükleme
  neredeyse boş bir veritabanı verecekti.
- **WorkManager için kural yazılmadı, çünkü gerekmiyor.** Cihazda ölçüldü:
  `androidx.work.workdb` zaten `/data/data/com.elinacn.subtrack/no_backup/`
  altında duruyor (WorkManager `getNoBackupFilesDir()` kullanıyor) ve platform
  o dizini hiç yedeklemiyor. Geri yükleme sonrası `no_backup/` **boş** geldi.
- `cache/` ve `code_cache/` platformun kendisi tarafından zaten dışarıda.
  `files/profileInstalled` beyaz liste dışında kaldığı için ayrı bir
  `<exclude>` istemedi.
- `data-extraction-rules`'un **iki bölümü de** yazıldı: `<cloud-backup>` ve
  `<device-transfer>`.

*Görev 3 — cihazda doğrulama (bu fazın asıl sınavı)*

Fikstür iki cihazda da aynı: dört abonelik (TRY/USD/EUR, aylık/yıllık, dört
kategori), Netflix'e 22 Eyl 2026 ödeme tarihi, ana para birimi USD, USD kuru
42.85 → 45.50, tema Koyu, bir de anlık görüntü satırı.

Tur: `bmgr backupnow` → `adb uninstall` → `adb install` → `bmgr restore 1`.

**API 34 — geri yükleme sonrası döküm (uygulama açılmadan):**

```
id | name    | priceInCents | currencyCode | billingPeriod | nextPaymentDate | category      | iconKey | createdAt
1  | Netflix | 14999        | TRY          | MONTHLY       | 1790035200000   | ENTERTAINMENT | None    | 1789753565028
2  | Spotify | 599          | USD          | MONTHLY       | None            | ENTERTAINMENT | None    | 1789753598718
3  | Notion  | 9600         | EUR          | YEARLY        | None            | PRODUCTIVITY  | None    | 1789753634316
4  | Fitness | 75000        | TRY          | MONTHLY       | None            | HEALTH        | None    | 1789753668650

period | totalInCents | currencyCode | recordedAt
202609 | 3389         | USD          | 1789753757162
```

**API 24 — aynı tur, aynı sonuç:**

```
id | name    | priceInCents | currencyCode | billingPeriod | nextPaymentDate | category      | iconKey | createdAt
1  | Netflix | 14999        | TRY          | MONTHLY       | 1790035200000   | ENTERTAINMENT | None    | 1789755000150
2  | Spotify | 599          | USD          | MONTHLY       | None            | ENTERTAINMENT | None    | 1789755034297
3  | Notion  | 9600         | EUR          | YEARLY        | None            | PRODUCTIVITY  | None    | 1789755068866
4  | Fitness | 75000        | TRY          | MONTHLY       | None            | HEALTH        | None    | 1789755102620

period | totalInCents | currencyCode | recordedAt
202609 | 3389         | USD          | 1789755166212
```

Her iki cihazda da yedek öncesi ve sonrası **bire bir aynı** — tutarlar,
tarihler, kategoriler, periyotlar, `createdAt` dahil.

Tercihler de bayt bayt aynı geldi. API 34, 166 bayt:

```
main_currency=USD · reminder_permission_requested=1 · theme_mode=DARK
rate_USD=455000 · rate_EUR=460000 · rate_GBP=539000 · rates_updated_at=...
```

API 24'te aynı dosya 129 bayt: `reminder_permission_requested` anahtarı yok,
çünkü o izin API 33'te geldi. Geri kalan her anahtar aynı.

Geri yüklenen dizin — beyaz listenin kanıtı (iki cihazda da aynı):

```
databases/       subtrack.db, subtrack.db-shm, subtrack.db-wal
files/datastore/ settings.preferences_pb
no_backup/       (boş)
cache/           (boş)
```

**Çökme yok:** iki cihazda da geri yükleme sonrası ilk açılışta crash tamponu
boş. **Bildirim işi yeniden kuruldu:** API 34'te
`JOB androidx.work.systemjobscheduler:u0a209/0`, API 24'te `JOB #u0a91/0` —
ikisi de **yeni** uid ve sıfırdan job id, yani yedekten gelmiş bir iş satırı
değil, uygulamanın kendi kurduğu iş.

Prompt "diagnostics yayını" istedi ama **projede böyle bir yayın yok**; onun
yerine `dumpsys jobscheduler` ve `no_backup/` dökümü kullanıldı.

UI tarafı da doğrulandı: koyu tema (`#0D1A14` tam değer), ana para birimi USD
(toplam `$33.89`), kurlar `45.5 / 46.2 / 53.9` ve "Last edited" damgası,
Netflix "4 days left".

*Regresyon listesi — #1, #11, #12, #20, #22, #106-#117, iki cihazda*

| # | API 34 | API 24 |
|---|---|---|
| 1 Açılış, çökme yok | ✅ crash tamponu boş | ✅ crash tamponu boş |
| 11 159,99 + 59,90 | ✅ `₺219.89` | ✅ `₺219.89` |
| 12 Kapat-aç, liste duruyor | ✅ | ✅ |
| 20 Ayarlar iki yoldan dönüş | ✅ yığında **1** ActivityRecord | ✅ yığında **1** ActivityRecord |
| 22 Kur değişince toplam | ✅ `$33.89` → `$48.31` | ✅ `$33.89` → `$48.31` |
| 106 Tema diyaloğu | ✅ üç seçenek, satır seçiliyi yazıyor | ✅ aynı |
| 107 Açık + sistem koyu | ✅ `#D3E2D8` direniyor | — ölçülemez (API 24'te sistem koyu teması yok) |
| 108 Koyu + sistem açık | ✅ `#0D1A14` direniyor | ✅ `#0D1A14` (sistem tarafı yok) |
| 109 Sistemi takip et | ✅ `#D3E2D8` ↔ `#0D1A14`, yeniden başlatmadan | — ölçülemez |
| 110 Tercih kalıcı, yanlış kare yok | ✅ `#FAFAFA` → `#0D1A14`, arada açık tema karesi yok | ✅ aynı |
| 111 Duvar kâğıdı anahtarı | ✅ kapalı geliyor; açınca `#D3E2D8` → `#FFF8F7` | — (API 31+ maddesi) |
| 112 Renk ve aydınlık bağımsız | ✅ açık `#FFF8F7`, koyu `#1E100F` | — |
| 113 Çubuk/iz ayrımı | ✅ `#C00020` / `#DFBFBD` = **3,78:1**; Snackbar "Undo" `#FFB3AF` / `#1E100F` = **10,84:1**; mor piksel yok | — |
| 114 API<31 satırı devre dışı | — (API 31+ cihaz) | ✅ "Requires Android 12 or newer"; iki kez dokunuldu, tercih dosyası **hiç oluşmadı** |
| 115 ISO kodu yok | ✅ dashboard / istatistik / kur ekranı temiz | ✅ aynı |
| 116 Dil değişimi | ✅ `$42.32` ↔ `$42,32` (uygulama dili, `cmd locale`) | ✅ `$48.31` ↔ `$48,31` (**gerçek cihaz dili**, `persist.sys.locale=tr-TR`) |
| 117 (API 29) ₺ çizimi | — | — (API 29 maddesi) |

`#115`'te düzenleme sheet'indeki TRY/USD/EUR/GBP chip'leri ISO kodu taşıyor,
ama onlar tutar değil **seçici etiketi**; maddenin kapsamı tutar gösteren
ekranlar ve hepsi temiz.

*Doğrulama*

- `./gradlew :app:testDebugUnitTest --rerun-tasks` → **330 test, 0 hata**
  (`UP-TO-DATE` gelmesin diye zorlandı, CLAUDE.md §6)
- `./gradlew :app:lintDebug` → **0 hata, 22 uyarı**; hepsi önceden vardı
  (InlinedApi, bağımlılık sürümleri, PluralsCandidate). Yedeklemeyle ilgili tek
  uyarı yok, yeni uyarı çıkmadı.

**Değişen dosyalar**
- `app/src/main/res/xml/backup_rules.xml` — şablon yorumları yerine gerçek
  kurallar (API ≤30 tarafı)
- `app/src/main/res/xml/data_extraction_rules.xml` — aynısı, artı
  `<device-transfer>` bölümü (API 31+ tarafı)
- `docs/ARCHITECTURE.md` — §25 "Android Auto Backup — Neyin Yedeklendiği"
- `docs/ROADMAP.md` — Faz 16'ya yedekleme maddesi (işaretli), gizlilik
  politikası maddesine ölçüm notu, sona Faz 17 başlığı
- `docs/TESTING.md` — "Yedekle — Geri Yükle Turu (Auto Backup)" bölümü
- `docs/PROGRESS.md` — bu kayıt

**Commit'ler**
- `f3a6ac2` feat: state the backup contents instead of inheriting the defaults
- `docs:` belgeler ayrı commit'te

**Karşılaşılan sorunlar**

- **Yerel taşıyıcının adı API'ye göre değişiyor.** API 24'te bileşen
  `android/com.android.internal.backup.LocalTransport`, API 31+'ta
  `com.android.localtransport/.LocalTransport`. Daha kötüsü: yanlış adı verince
  `bmgr transport` hata vermiyor, "Selected transport ..." deyip geçiyor.
  Seçimin tuttuğu `bmgr list transports` çıktısındaki `*` ile doğrulanmalı.
  TESTING.md'ye yazıldı.
- **`restoreFinished: 0` başarı demek**, hata değil (transport OK). API 24 bu
  satırı hiç yazmıyor, yalnızca `done` diyor — orada doğrulama dökümle yapılır.
- Emülatörlerde `sqlite3` **yok**; veritabanı üç dosya birlikte host'a çekilip
  orada açılmalı. WAL'siz çekilen döküm eksik çıkar.
- API 24'te ekleme formu 360dp'de kaydırma istiyor (TESTING.md'de zaten yazılı)
  ve kategori chip'leri ilk açılışta ekranın altında kalıyor. İlk turda bu
  "chip'ler kırık" sanıldı; kaydırınca hepsi yerinde çıktı — **hata değil.**
- Bottom sheet'te `keyevent 111` (ESC) klavyeyi değil **sheet'i** kapatıyor;
  klavyeyi kapatmak için `keyevent 4` kullanılmalı.

**Gizlilik politikası için not (Görev 4)**

`docs/privacy/index.html` bu promptta **oluşturulmadı ve dokunulmadı.**
Ölçümün politikaya etkisi şu:

> Auto Backup **açık**. Bu yüzden politikada "veriler cihazdan çıkmıyor" ya da
> "veriler yalnızca cihazınızda saklanır" cümlesi **olduğu gibi
> kullanılamaz.** Doğru cümle: *"Abonelik verileriniz ve uygulama
> tercihleriniz, Android'in kendi yedekleme özelliği aracılığıyla **sizin kendi
> Google Drive hesabınıza** kopyalanır. Bu yedeğe yalnızca siz
> erişebilirsiniz; geliştiricinin bu yedeğe erişimi yoktur ve verileriniz
> geliştiriciye ya da üçüncü bir tarafa gönderilmez. Yedeklemeyi Android
> ayarlarından kapatabilirsiniz."*

Data Safety formundaki "veri aktarılıyor mu" sorusu da bu ölçüme göre
yanıtlanmalı: veri Google'ın yedekleme altyapısına gidiyor, ama uygulamanın
kendi sunucusu yok.

**Sonraki faz için not**
- Faz 17 (dışa/içe aktarma) Auto Backup'ın **yerine** değil, yanına geliyor:
  Auto Backup kullanıcının yedeğe elle dokunmasına izin vermiyor, dosyaya
  aktarma o boşluğu dolduracak.
- Şema sürümlemesi kuralı hâlâ açık ve yedekleme onu **büyütüyor**: yayından
  sonra `subtrack.db` yedekten **eski sürümüyle** geri gelebileceği için
  migration zorunlu. Faz 17'den önce okunmalı.
- #113'ün trend sütunu ölçülemedi: trend grafiği en az iki aylık geçmiş
  istiyor, fikstürde tek ay var (uygulama da "A trend needs at least two
  months" diyor). Çubuk/iz ve Snackbar tarafı ölçüldü.

---

## [Faz 16c] R8, İmzalama Yapılandırması ve İkon Ölçümü — 2026-09-17

**Durum:** Tamamlandı. Release derlemesi artık daraltılıyor ve imzalama
yapılandırması yerinde. **Keystore dosyası oluşturulmadı** — o kullanıcının
işi ve fazın kapsamı dışında bırakıldı. Ürün davranışı değişmedi: `app/src`
altında **tek satır** kod değişmedi, değişen yalnızca derleme yapılandırması.

### Boyut tablosu

Aynı kaynak ağacı, üç derleme:

| Derleme | APK | dex (sıkıştırılmamış) | dex (APK içinde) | dex dosyası | `resources.arsc` |
|---|---|---|---|---|---|
| debug | 21.262.638 B — 20,28 MiB | 66.828.336 B — 63,73 MiB | 20.476.262 B — 19,53 MiB | 18 | 533.880 B |
| release, minify **kapalı** | 13.695.885 B — 13,06 MiB | 47.659.940 B — 45,45 MiB | 12.906.420 B — 12,31 MiB | 5 | 529.616 B |
| release, minify **açık** | **2.114.862 B — 2,02 MiB** | **3.235.112 B — 3,09 MiB** | **1.574.191 B — 1,50 MiB** | **2** | **306.388 B** |

APK **%84,6** küçüldü (13,06 → 2,02 MiB). 16-0'da "APK'nın %98,3'ü dex" diye
ölçülen oran artık **%74,4** (1,50 / 2,02 MiB) — dex hâlâ en büyük parça ama
artık tek başına APK değil.

### Görev 1 — İkon daraltması: ölçüldü, değişiklik YOK

16-0 envanterindeki 12 ikon `material-icons-core`'un içeriğiyle karşılaştırıldı
(AAR'ın `classes.jar`'ı açılıp sınıf listesi okundu):

| Core'da var (8) | Yalnızca extended'da (4) |
|---|---|
| `Add`, `AutoMirrored.Filled.ArrowBack`, `AutoMirrored.Filled.List`, `DateRange`, `Delete`, `PlayArrow`, `Settings`, `Star` | `BarChart`, `Cloud`, `ArrowUpward`, `ArrowDownward` |

Core'da bu dördünün karşılığı yok. `BarChart` istatistiğin tek işareti, `Cloud`
bulut aboneliklerini ayıran şey, ok çiftinin core'daki komşusu chevron ve
chevron yön değil açılır/kapanır anlatıyor. Dördü birden karşılanmadığı için
bağımlılık kalkamıyor; ikisini değiştirip diğer ikisi için kütüphaneyi tutmak
**sıfır bayt** kazandırırdı.

Bedel iki kez ölçüldü — `-extended` yerine `-core` konup derlenerek, sonra
değişiklik geri alınarak:

| | minify kapalı | minify açık |
|---|---|---|
| `-extended` ile | 13.695.885 B | 2.114.862 B |
| yalnızca `-core` ile | 9.550.517 B | 2.114.646 B |
| fark | **4.145.368 B (~3,95 MiB)** | **216 B** |

**Sonuç: R8 açıkken bedeli yok.** Son APK'da o kütüphaneden **beş sınıf**
kalıyor (`DateRange`, `Delete`, `PlayArrow`, `Settings`, `Star`); kalan yedisi
çağıranın içine gömülmüş. Bu yüzden ikon daraltması için ayrı bir commit
atılmadı — atılacak bir değişiklik çıkmadı.

Yolda çıkan ve kayda değer bir şey: **`material3` 1.4.0 `material-icons-core`'u
getirmiyor.** `-extended` çıkarılınca `androidx.compose.material.icons` paketi
tamamen kayboluyor (`Unresolved reference 'icons'`), çünkü core ağaca yalnızca
extended'ın bağımlılığı olarak giriyor.

### Görev 2 — R8

`isMinifyEnabled = true` ve `isShrinkResources = true` açıldı.
**`proguard-rules.pro` boş kaldı.** Sıra kasıtlı olarak şuydu: kural yazmadan
derle → release APK'yı dört cihazda sür → kırılanı ölç. Kırılan olmadı.

Kütüphaneler kurallarını kendileri getiriyor:
`build/outputs/mapping/release/configuration.txt` **70'in üzerinde** kural
kaynağı listeliyor — `room-runtime`, `room-ktx`, `hilt-android`, `hilt-work`,
`hilt-navigation-compose`, `datastore-preferences-core`, `work-runtime`,
`navigation-*`, `material3`, `ui-*`, `lifecycle-*` ve R8'in kendi
`coroutines.pro`'su. `missing_rules.txt` hiç oluşmadı.

Dosyanın eski şablon içeriği (WebView/JS arayüzü örneği, yorum satırına alınmış
`-keepattributes`) silindi; yerine neyin neden **yazılmadığı** yazıldı.

### Görev 3 — İmzalama yapılandırması

Dört değer `local.properties`'ten, yoksa ortam değişkeninden okunuyor.
`local.properties` seçildi: zaten `.gitignore`'da, Gradle onu `sdk.dir` için
zaten okuyor, Android Studio ile komut satırı aynı değerleri görüyor. Ortam
değişkeni yedeği ileride `local.properties` bulunmayan bir CI makinesi için.

Keystore yokken ölçülen davranış:

| | Sonuç |
|---|---|
| `assembleRelease` | **Geçiyor**, `app-release-unsigned.apk` üretiyor |
| `apksigner verify` (o dosya) | `DOES NOT VERIFY — Missing META-INF/MANIFEST.MF` |
| `assembleDebug` | **Etkilenmiyor**, 21.262.638 B, debug anahtarıyla imzalı |

Debug anahtarına düşülmüyor: imzasız kalmak, imzasız bir çıktının
yayınlanabilir sanılmasından iyidir.

`.gitignore` doğrulandı: `local.properties`, `*.jks`, `*.keystore`, `*.apk`,
`*.aab` Faz 1a'dan beri içeride.

### Görev 4 — Şablon dosya temizliği

- `android.disallowKotlinSourceSets=false` **hâlâ gerekli.** Satır silinip
  `assembleDebug --rerun-tasks` denendi: *"Kotlin source set 'debug' contains:
  …generated/ksp/debug/kotlin… To suppress this error, set
  android.disallowKotlinSourceSets=false"* diyerek derleme durdu. Geri kondu.
- Kullanılmayan kaynak yok: daraltıcının erişilebilir listesi projenin 125
  metninden 124'ünü içeriyor, kalan `app_name` manifest üzerinden tutuluyor ve
  cihazda uygulama adı olarak göründü.
- `res/xml/backup_rules.xml` ve `data_extraction_rules.xml` hâlâ şablon içerikli
  (hepsi yorum), ama **manifest'ten referans veriliyor** ve bu faz manifest'e
  dokunmuyor. Yedekleme politikası ayrıca karar isteyen bir konu; aşağıya not
  bırakıldı.

### Görev 5 — Release APK dört cihazda

Minify açık release APK, SDK'nın debug anahtarıyla imzalanıp **API 24 / 29 /
34 / 36** emülatörlerine kuruldu ve sürüldü. Sekiz riskli yerin hepsi:

| Yer | API 24 | API 29 | API 34 | API 36 |
|---|---|---|---|---|
| Hilt grafı (açılış) | ✅ | ✅ | ✅ | ✅ |
| Room (liste/ekle/düzenle/sil/geri al) | ✅ | ✅ | ✅ | ✅ |
| Room şema kimliği | ✅ | ✅ | ✅ | ✅ |
| DataStore (para birimi, tema, kur) | ✅ | ✅ | ✅ | ✅ |
| WorkManager + `@HiltWorker` + bildirim | ✅ | ✅ (kendiliğinden) | ✅ | ✅ |
| Compose Navigation (dört hedef) | ✅ | ✅ | ✅ | ✅ |
| `java.time` / desugaring | ✅ | ✅ | ✅ | ✅ |
| `NumberFormat` / locale | ✅ | ✅ | ✅ | ✅ |

`logcat -b crash` dördünde de **boş** (0 satır).

Room şema kimliği statik olarak da karşılaştırıldı:
`schemas/…/1.json` → `ef18d874586948288a87736e03c2b556`, üretilen
`SubTrackDatabase_Impl` (debug ve release) → aynı değer.

117 maddelik listeden istenen alt küme release APK ile sürüldü:

- **#1–#13** (temel akış): dördünde de. `159,99 + 59,90 = 219,89` kuruşu
  kuruşuna; kısmi kaydırmalar silmiyor, tam kaydırma siliyor, "Geri al" öğeyi
  **eski sırasına** koyuyor.
- **#14, #17, #20–#26** (form, ayarlar, kurlar, klavye): `0`, `-5`, `1,23456`,
  `1000,0001` dördü de alan altında hata veriyor ve kaydedilmiyor. Kur ekranı
  klavye açıkken API 34'te `Kaydet [500,1228][580,1281]` — 16b'de ölçülen
  değerle aynı satırda.
- **#27–#32** (tarih): API 24'te "1 gün kaldı", saat bir gün ileri alınınca
  "Bugün ödenecek"; geçmiş tarih + aylık periyot → "23 gün kaldı" (17 Eylül →
  10 Ekim), "gecikmiş" demiyor; metin girişinden `01/01/2040` → *"The date can
  be at most 10 years ahead"*, sheet açık kalıyor; döndürmede ad, fiyat ve
  tarih duruyor.
- **#33–#38, #40, #41** (bildirim): API 34'te tarihli ilk abonelikten sonra
  sistem izin diyaloğu sheet kapandıktan **sonra** çıkıyor; izin sistem
  ayarlarından kapatılınca satır **uygulama yeniden başlatılmadan** "Kapalı —
  açmak için dokunun" oluyor, sonra açıklama diyaloğu → sistem diyaloğu →
  "Açık". **#40:** API 24'te uygulama detay sayfası (`InstalledAppDetails`),
  API 29'da uygulamanın bildirim ekranı (`AppNotificationSettingsActivity`) —
  16b hotfix'i release'de de duruyor.
- **#106–#117** (tema ve para birimi): üç seçenekli tema diyaloğu; **Koyu**
  seçiliyken sistem açığa alınınca uygulama direniyor (`#0D1A14`), **Açık**
  seçiliyken sistem koyuya alınınca yine direniyor (`#D3E2D8`), **Sistemi takip
  et**'te takip ediyor; seçim soğuk başlatmada duruyor. Duvar kâğıdı renkleri
  API 34/36'da açılıp kapanıyor, API 24/29'da satır "Android 12 ve üzeri
  gerekir" diyerek devre dışı. **#117:** ₺ karakteri API 29'da dashboard'ın en
  büyük puntosunda ekran görüntüsünden bakılarak doğrulandı — tofu yok.

Tam tur atılmadı; 16b'de debug ile eksiksiz sürülmüştü.

### Görev 6 — Belgeler

`ARCHITECTURE.md` §24 eklendi; `ROADMAP.md` Faz 16'nın beş maddesi işaretlendi;
`TESTING.md`'ye "Release APK ile Test Etme" bölümü kalıcı olarak yazıldı ve
WorkManager bölümündeki bir yanlış düzeltildi (aşağıda).

**Değişen dosyalar**

- `app/build.gradle.kts` — `isMinifyEnabled`/`isShrinkResources` açıldı;
  `local.properties`/ortam değişkeninden okuyan release `signingConfig` eklendi
- `app/proguard-rules.pro` — şablon içerik silindi, kuralların neden
  yazılmadığı yazıldı
- `docs/ARCHITECTURE.md` — §24 eklendi
- `docs/ROADMAP.md` — Faz 16: imzalama, R8 kuralları, `isMinifyEnabled`,
  `material-icons-extended` ve desugar maddeleri işaretlendi
- `docs/TESTING.md` — "Release APK ile Test Etme" bölümü; "duvar saati
  ileri alınamaz" maddesi düzeltildi
- `docs/PROGRESS.md` — bu kayıt

`app/src` altında **hiçbir dosya değişmedi.**

**Commit'ler**

- `b951010` build: turn on R8 and resource shrinking for release
- `b6425d0` build: read release signing material from local.properties
- (bu kayıt) docs: record the R8, signing and icon measurements of phase 16c

**Karşılaşılan sorunlar**

- **`TESTING.md`'de yazan "duvar saati root olmadan ileri alınamaz" yanlışmış.**
  Orada `adb root`, `adb shell date` ve `setprop persist.sys.timezone`
  denenmiş ve üçü de reddedilmişti — üçü bu fazda da reddetti. Ama iki yol
  daha var: API 31+'ta `cmd time_detector set_time_state_for_tests` izin
  istemiyor, API 24/29'da Ayarlar'daki "Automatic date & time" kapatılınca saat
  elle kurulabiliyor. İkisiyle de worker gerçekten koşturuldu. Bölüm
  düzeltildi.
- **Emülatörün `-timezone` bayrağı bu imajlarda çalışmıyor.** Hem snapshot'tan
  hem soğuk açılışta denendi, misafir saat dilimi `Etc/GMT` kaldı.
- **Hedef saatten önce `cmd jobscheduler run -f` denemek işi bozuyor.** API
  24'te 08:59'da bir kez denendi; WorkManager o denemede işi yeniden zamanladı
  ve sonraki çalıştırma **bir gün** ileri kaydı. Tarihi bir gün ileri alarak
  çıkıldı. Doğru sıra: saati hedeften önceye kur → uygulamayı aç → hedefi geç →
  **sonra** zorla.
- **İlk ölçümde desugar dex'i yanlış dosyayla eşleştirilmişti.** minify kapalı
  release'de son `classes5.dex` sanılmıştı; L8 ara çıktısına bakılınca gerçek
  karşılığın `classes3.dex` (326.932 B) olduğu görüldü. Sayılar buna göre.
- `material-icons-extended`'ı çıkarıp `-core` koymadan derlemek `Unresolved
  reference 'icons'` veriyor — `material3` 1.4.0 core'u getirmiyor.

**Sonraki faz için not**

- **Keystore kullanıcıda.** `local.properties`'e dört satır yazılınca
  `assembleRelease` imzalı çıkacak; yapılandırma hazır, denenmedi çünkü
  denemek anahtar üretmeyi gerektirirdi.
- **Açılış süresi R8 sonrası ölçülmedi.** ROADMAP maddesi "APK boyutu ve açılış
  süresi düşer" diyor; boyut ölçüldü, süre ölçülmedi. `am start -W` ile beş
  tekrarın medyanı alınabilir.
- **`res/xml/backup_rules.xml` ve `data_extraction_rules.xml` hâlâ şablon.**
  İkisi de manifest'ten referanslı, içerikleri tamamen yorum. Yedekleme
  politikası (neyin yedeklenip neyin yedeklenmeyeceği) bir ürün kararı; Play
  hazırlığında gizlilik politikası maddesiyle birlikte ele alınmalı.
- **Enstrümantasyon paketi release APK'ya karşı koşulamıyor** — `testBuildType`
  varsayılanı `debug`, `connectedReleaseAndroidTest` görevi yok. Release'e
  çevirmek gerçek bir keystore ister (uygulama ve test APK'sı aynı anahtarla
  imzalanmalı) ve `ui-test-manifest` `debugImplementation` olduğu için Compose
  testlerinin Activity'si olmaz. İstenirse ayrı bir iş.
- **`mapping.txt` saklanmalı.** R8 açıkken cihazdan gelen yığın izleri
  karışıktır; `build/outputs/mapping/release/mapping.txt` (bu derlemede 37 MB)
  her yayınla birlikte saklanmazsa o sürümün çökme raporları okunamaz. Play
  Console'a yükleme adımı Faz 16'nın kalanında ele alınmalı.

---

## [Faz 16b hotfix] API 26 Altında Bildirim Ayarlarına Doğru Yoldan Gidiliyor — 2026-09-17

**Durum:** Tamamlandı. 16b turunda API 24'te düşen #40 kapandı. Tek bir dal
değişti; izin akışının geri kalanına, `reminder/` altına, domain'e, veriye,
manifeste ve temaya dokunulmadı.

### Ne düzeltildi

Ayarlar'daki "Ödeme hatırlatmaları" satırına dokunmak Android 7.x'te hiçbir şey
yapmıyordu. Artık API 26 altında **doğrudan uygulama detay sayfası**
(`ACTION_APPLICATION_DETAILS_SETTINGS`) açılıyor; API 26 ve üstünde bugünkü
davranış **aynen** duruyor.

### Neden exception yedeği yetmiyordu

Önceki kod "eylemi dene, `ActivityNotFoundException` gelirse detay sayfasına
düş" diyordu. Android 7.0'ın Ayarlar'ı `ACTION_APP_NOTIFICATION_SETTINGS`
eylemini **karşılıyor**, yani `startActivity` başarılı oluyor ve exception hiç
atılmıyor — yedek bu yüzden hiç devreye girmiyordu. Ekran açılıyor, API 24'te
istediği `app_uid` ekstrasını bulamayıp kendini kapatıyor:

```
W NotifiSettingsBase: Missing extras: app_package was com.elinacn.subtrack, app_uid was -1
```

Bir eylemin **adının** var olması o sürümde **çalışacağı** anlamına gelmiyor.
Exception yalnızca eylemi kimsenin karşılamadığı durumu yakalar; aradaki farkı
ancak `Build.VERSION.SDK_INT` kontrolü kapatır.

**`app_uid` gönderilmedi** — desteklenmeyen bir ekrana ikinci bir ekstra ile
girmek belgelenmemiş davranışa bağlanmak olurdu. **Satır devre dışı
bırakılmadı** — kullanıcının bildirimleri açmak için tek yolu o. Exception
yedeği **kaldı**; API 26+ dalında kendi bildirim ekranını taşımayan bir yapıda
yine detay sayfasına düşülüyor, yani iki dal aynı yerde buluşuyor.

### Ölçüm — dört cihaz, `mResumedActivity`

| Cihaz | Açılan ekran | Değişti mi |
|---|---|---|
| API 24 | `com.android.settings/.applications.InstalledAppDetails` | **evet — eskiden hiçbir şey açılmıyordu** |
| API 29 | `com.android.settings/.Settings$AppNotificationSettingsActivity` | hayır |
| API 34 | `com.android.settings/.Settings$AppNotificationSettingsActivity` | hayır |
| API 36 | `com.android.settings/.Settings$AppNotificationSettingsActivity` | hayır |

Dört cihazda da `logcat -s AndroidRuntime:E` boş — çökme yok.

**API 24'te yol uçtan uca sürüldü.** Detay sayfasında "Notifications" satırı var
ve oradan gerçek bildirim anahtarlarına ("Block all") ulaşılıyor. Kapatıp
uygulamaya dönünce satır "Off — turn on in system settings", tekrar açıp dönünce
"On" dedi; süreç kimliği değişmedi (aynı pid 3570). Yani #38'in tazeleme yolu da
bu yeni rotadan çalışıyor.

API 34 ve 36'da değişen dalın gerçekten koşması için izin **kalıcı olarak
reddedilip** (`USER_FIXED`) satıra dokunuldu; #34'ün temiz kurulum yolu
(doğrudan sistem izin diyaloğu, `GrantPermissionsActivity`) ayrıca doğrulandı ve
değişmedi.

### Lint

`ReminderPermissionActions.kt:41` ve `:42`'deki `InlinedApi` uyarıları
(`ACTION_APP_NOTIFICATION_SETTINGS`, `EXTRA_APP_PACKAGE`) **kapandı**.
**24 uyarı → 22.** `@SuppressLint` kullanılmadı.

Dosyada kalan tek `InlinedApi`, `canShowNotificationRationale` içindeki
`Manifest.permission.POST_NOTIFICATIONS` (satır 37). O başka bir sabit ve bu
maddeyle ilgisi yok: her sürümde güvenli bir izin adı dizesi, ve ARCHITECTURE
§18 runtime kontrolünün neden ayrıca sürüme bağlandığını zaten açıklıyor.

### Ek olarak sürülen maddeler

#20 (ayarlar gidiş-dönüş) ve #106-#117 dört cihazda da sürüldü, hepsi geçti:
tema diyaloğu, açık/koyu direnci, sistemi takip etme, yeniden açılışta kalıcılık,
duvar kâğıdı renkleri (API 34/36), devre dışı satır (API 24/29), para birimi
sembolleri ve sayı biçimi. Ölçülen renkler 16b'dekiyle birebir aynı:
`#0D1A14` / `#1F3D2D`, açık temada `#D3E2D8`, duvar kâğıdı açıkken API 34
`#1E100F` ve API 36 `#24020A`.

Tam 117 maddelik tur tekrarlanmadı — 16b'de eksiksiz sürülmüştü ve bu değişiklik
tek bir Intent dalına dokunuyor.

**Değişen dosyalar**
- `app/src/main/java/com/elinacn/subtrack/ui/settings/ReminderPermissionActions.kt` —
  `openNotificationSettings` sürüm dalına ayrıldı, ortak `startAppDetails` çıkarıldı
- `docs/ARCHITECTURE.md` — §18'e "Hangi ayar ekranına gidildiği sürüme bağlıdır"
- `docs/TESTING.md` — #40'ın beklentisi sürüme göre ayrıldı; ölçülemez tablosundaki
  "düştü" kaydı düzeltildi
- `docs/PROGRESS.md` — bu kayıt

**Commit'ler**
- `fix:` send API 24-25 straight to the app details page
- `docs:` explain why the exception fallback could not catch this

**Sonraki faz için not**
- ROADMAP'teki "API 24-25'te bildirim ayarları kısayolu çalışmıyor" maddesi
  kapandı; işaretlenebilir.

---

## [Faz 16b] Test Paketinin Sıra Bağımsızlığı ve Tam Regresyon Turu — 2026-09-16

**Durum:** Kısmen. Sıra bağımlılığı çözüldü ve dört cihazda kanıtlandı, şablon
testler silindi, 117 maddelik liste dört cihazda eksiksiz sürüldü — **bir madde
düştü:** API 24'te #40. Düzeltme yazılmadı; bu faz test ve doğrulama fazıydı.
Ürün kodu (`app/src/main/`) değişmedi.

### Sıra bağımlılığı: ürün doğru, testler birbirine yaslanıyordu

16-0'da paket sırayla koşunca düşüyordu. `PaymentReminderWorkerTest` ve
`EditedDateReminderTest` ikisi de günde-bir hatırlatma worker'ını sürüyor; hangisi
önce koşarsa günü işaretliyor, öteki erken dönen bir worker buluyordu. "Günde bir
bildirim" kuralı doğru çalışıyor (§18) — bozuk olan, her iki sınıfın da dışarıdan
`pm clear` yapılmış bir cihaza yaslanmasıydı.

**`@FixMethodOrder` kullanılmadı.** Sıra sabitlemek bağımlılığı gizler, kaldırmaz;
12-2 hotfix'inde aynı çözüm denenip gerçek sebep bulununca geri alınmıştı. Bunun
yerine her sınıf ön koşulunu `@Before` içinde kendisi kuruyor.

**Kaydı dosyadan silmek neden yetmiyor — ölçüldü.** DataStore 1.1.7 üzerinde
atılabilir bir JVM testiyle:

```
yazdıktan sonra okuma   = 42
dosya silindi
silmeden sonra okuma    = 42     <- okuma bellekteki önbellekten geliyor
sonraki yazmadan sonra  = null   <- yazma diskten yeniden okuyor, kayıt gitmiş
```

Yani `SingleProcessCoordinator` sürümü bellekte tutuyor, `dataStore.data.first()`
diskle hiç konuşmuyor; worker da **önce okuyor**, yazmaya hiç gelmiyor. Aynı dosya
üzerine ikinci bir DataStore açmak zaten çalışma zamanı hatası
(`DataStoreModule`'ün `@Singleton`'ı bunun için var). Kayda ulaşmanın tek yolu
**örneğin kendisine** ulaşmak, o da Hilt grafiğinde.

Çözüm, **kullanıcıya sorulup onaylandıktan sonra**, `app/src/debug/` altında bir
`@EntryPoint` oldu (`PreferencesStoreEntryPoint`). Neden orada: bir entry point'in
annotation ile işlenmesi gerekiyor ve KSP yalnızca app modülünde koşuyor, yani
androidTest'te duramaz; `kspAndroidTest` eklemek yasaklıydı ve yeni bağımlılık
istiyordu. `src/debug` ikisinin de dışında kalıyor — release derlemesine girmiyor,
`app/src/main/` değişmiyor, `build.gradle.kts` ve `libs.versions.toml` değişmiyor.

### API 34'teki izin düşmesi: `pm grant` testin içinde

`connectedDebugAndroidTest` koşumdan hemen önce iki APK'yı yeniden kuruyor ve
API 33+ sürümlerde kurulum çalışma zamanı iznini düşürüyor. Elle `pm grant`
yapılmış cihazda paket geçiyor, aynı paket Gradle'dan koşunca "no notification was
posted" diye düşüyordu — ölçüldü: kurulumdan sonra `granted=false`.

İki yol vardı: paketi hep `am instrument` ile koşturmak, ya da izni test içinde
yeniden vermek. **İkincisi seçildi**, çünkü ilki koşum yöntemini hazırlığa bağlar
ve `connectedDebugAndroidTest`'i kalıcı olarak kullanılmaz kılardı.
`UiAutomation.executeShellCommand("pm grant …")` kabuk kullanıcısı olarak koşar
(izni veren hesap odur), `pm revoke`'un aksine süreci öldürmez, API 33 altında
sessizce atlanır ve **CI'da da çalışır** — kimsenin cihazı elle hazırlaması
gerekmiyor. "Elle `pm grant`" kabul edilebilir bir cevap değildi ve seçilmedi.

### Kanıt — dört cihaz, iki koşum yöntemi, arka arkaya iki kez

| Cihaz | `connectedDebugAndroidTest` | `am instrument` |
|---|---|---|
| `subtrack_min_api24` | 2/2 koşum: 19 test, 0 hata, 1 atlandı | 2/2 koşum: OK (19 test) |
| `subtrack_narrow_api29` | 3/4 koşum: 19 test, 0 hata, 1 atlandı — **1 koşum emülatör çökmesiyle kesildi** | 2/2 koşum: OK (19 test) |
| `subtrack_wide_api34` | 2/2 koşum: 19 test, 0 hata, 1 atlandı | 2/2 koşum: OK (19 test) |
| `subtrack_edge_api36` | 2/2 koşum: 19 test, 0 hata, 1 atlandı | 2/2 koşum: OK (19 test) |

İkinci koşumlar aradan **hiçbir temizlik geçirmeden** yapıldı; `pm clear` da
`pm grant` da yok. API 34'te koşumdan önce izin `granted=false` olarak ölçüldü ve
paket yine geçti.

**API 29'daki tek kesinti test hatası değil.** Gradle çıktısı
`INSTRUMENTATION_ABORTED: System has crashed` + `DeadSystemException: The system
died`; emülatörün system server'ı koşum ortasında çöktü. Aynı cihazda sonraki üç
`connectedDebugAndroidTest` koşumu ve iki `am instrument` koşumu temiz geçti.

Atlanan tek test her cihazda aynı:
`reminderWorker_notificationsDisabled_succeedsWithoutNotifying`, bildirimler
açıkken `assumeFalse` ile atlıyor — beklenen davranış.

### Şablon testler silindi

`ExampleUnitTest` (`2+2=4`) ve `ExampleInstrumentedTest` (paket adı kontrolü)
kaldırıldı. **331 birim testi → 330**, **20 enstrümantasyon metodu → 19.**
Silme sonrası `testDebugUnitTest` ve dört cihazdaki enstrümantasyon koşumları
yeniden alındı.

### Tam regresyon turu — 117 madde × 4 cihaz

468 hücre: **428 geçti, 1 düştü, 4 ölçülemedi, 4 kısmen, 31 geçerli değil.**
"Dokunmadım" hiçbir hücrede sebep değil.

| # | Madde | API 24 | API 29 | API 34 | API 36 |
|---|---|---|---|---|---|
| 1 | Uygulamayı aç | geçti | geçti | geçti | geçti |
| 2 | FAB'a bas | geçti | geçti | geçti | geçti |
| 3 | Ad + fiyat gir, Kaydet | geçti | geçti | geçti | geçti |
| 4 | FAB'a tekrar bas | geçti | geçti | geçti | geçti |
| 5 | Sheet'i scrim'e dokunarak / geri tuşuyla kapat | geçti | geçti | geçti | geçti |
| 6 | Bir kartı hafifçe kaydır (~1/5) bırak | geçti | geçti | geçti | geçti |
| 7 | Aynı satırda 8-10 kez ardışık hafif kaydır | geçti | geçti | geçti | geçti |
| 8 | Hızlı kısa fiske | geçti | geçti | geçti | geçti |
| 9 | Tam kaydır | geçti | geçti | geçti | geçti |
| 10 | Ters yöne sürükle | geçti | geçti | geçti | geçti |
| 11 | 159,99 ve 59,90 ekle | geçti | geçti | geçti | geçti |
| 12 | Uygulamayı tamamen kapat, yeniden aç | geçti | geçti | geçti | geçti |
| 13 | Ekran döndür | geçti | geçti | geçti | geçti |
| 14 | Sheet açık ve metin yazılıyken ekran döndür | geçti | geçti | geçti | geçti |
| 15 | Sistem temasını koyuya al | geçti¹ | geçti¹ | geçti | geçti |
| 16 | Cihaz dilini İngilizceye al | geçti² | geçti² | geçti³ | geçti³ |
| 17 | Boş ad veya geçersiz fiyatla Kaydet'e bas | geçti | geçti | geçti | geçti |
| 18 | Bir satırı sil, Snackbar'a dokunma | geçti | geçti | geçti | geçti |
| 19 | Sil, sonra "Geri al"a bas | geçti | geçti | geçti | geçti |
| 20 | Ayarlar ikonuna bas, geri oku ve sistem geri t… | geçti | geçti | geçti | geçti |
| 21 | Ayarlarda başka bir para birimi seç, uygulamay… | geçti | geçti | geçti | geçti |
| 22 | Ayarlar → Döviz Kurları, USD kurunu değiştir, … | geçti | geçti | geçti | geçti |
| 23 | Kur ekranında 0, -5, 1,23456, 1000,0001 gir ve… | geçti | geçti | geçti | geçti |
| 24 | Kur değiştir, uygulamayı tamamen kapat, yenide… | geçti | geçti | geçti | geçti |
| 25 | Kur ekranında "Varsayılana dön" → Sıfırla | geçti | geçti | geçti | geçti |
| 26 | Kur alanına yazarken klavye açıkken Kaydet ve … | geçti | geçti | geçti | geçti |
| 27 | Ekleme formunda tarih seç, kaydet | geçti | geçti | geçti | geçti |
| 28 | Tarih seçmeden kaydet | geçti | geçti | geçti | geçti |
| 29 | Geçmiş bir tarih seç | geçti | geçti | geçti | geçti |
| 30 | Bugünün tarihini seç | geçti | geçti | geçti | geçti |
| 31 | Seçicinin metin girişinden 10 yıldan uzak bir … | geçti | geçti | geçti | geçti |
| 32 | Tarih seçili haldeyken sheet açıkken döndür | geçti | geçti | geçti | geçti |
| 33 | Ayarlarda "Ödeme hatırlatmaları" satırı | geçti | geçti | geçti | geçti |
| 34 | (API 33+) Temiz kurulumda satıra dokun | — | — | geçti | geçti |
| 35 | İzni verip satıra bak | — | — | geçti | geçti |
| 36 | Bir kez reddettikten sonra satıra dokun | — | — | geçti | geçti |
| 37 | Kalıcı reddedildikten sonra satıra dokun | — | — | geçti | geçti |
| 38 | Sistem ayarlarından bildirimleri aç, geri dön | geçti | geçti | geçti | geçti |
| 39 | Sistem ayarlarından yalnızca kanalı kapat, ger… | — | geçti | geçti | geçti |
| 40 | (API < 33) Satıra dokun | düştü | geçti | — | — |
| 41 | Temiz kurulumda tarihli ilk aboneliği kaydet | — | — | geçti | geçti |
| 42 | Temiz kurulumda tarihsiz abonelik kaydet | — | — | geçti | geçti |
| 43 | Reddettikten sonra ikinci tarihli aboneliği ka… | — | — | geçti | geçti |
| 44 | İzin verilmişken tarihli abonelik kaydet | — | — | geçti | geçti |
| 45 | Diyalog açıkken ekranı döndür | — | — | geçti | geçti |
| 46 | (API < 33) Tarihli ilk aboneliği kaydet | geçti | geçti | — | — |
| 47 | Temiz kurulumda uygulamayı aç | geçti | geçti | geçti | geçti |
| 48 | Bir abonelik ekle | geçti | geçti | geçti | geçti |
| 49 | Tek aboneliği sil | geçti | geçti | geçti | geçti |
| 50 | Veri varken uygulamayı aç | geçti⁴ | geçti⁴ | geçti⁴ | geçti⁴ |
| 51 | Ekleme formunda kategori seçici | geçti | geçti | geçti | geçti |
| 52 | Kategori seçip kaydet | geçti | geçti | geçti | geçti |
| 53 | Kategoriye dokunmadan kaydet | geçti | geçti | geçti | geçti |
| 54 | Kaydettikten sonra FAB'a tekrar bas | geçti | geçti | geçti | geçti |
| 55 | Kategori seçili haldeyken sheet açıkken döndür | geçti | geçti | geçti | geçti |
| 56 | Liste üstündeki filtre çubuğu | geçti | geçti | geçti | geçti |
| 57 | Bir kategori seç | geçti | geçti | geçti | geçti |
| 58 | Hiçbir aboneliği olmayan bir kategoriyi seç | geçti | geçti | geçti | geçti |
| 59 | Filtre açıkken bir satırı sil, sonra "Geri al" | geçti | geçti | geçti | geçti |
| 60 | Filtre seçiliyken döndür, sonra uygulamayı tam… | geçti | geçti | geçti | geçti |
| 61 | Filtre çubuğunu yatay kaydır | geçti | geçti | geçti | geçti |
| 62 | Ekleme formunda periyot seçici | geçti | geçti | geçti | geçti |
| 63 | Aylık 100,00 + yıllık 1.200,00 + haftalık 10,0… | geçti | geçti | geçti | geçti |
| 64 | Dashboard'ın altındaki Yıllık chip'ine bas | geçti | geçti | geçti | geçti |
| 65 | Bir kategori filtresi seçip iki görünüme de bak | geçti | geçti | geçti | geçti |
| 66 | Periyot seçip kaydettikten sonra FAB'a tekrar … | geçti | geçti | geçti | geçti |
| 67 | Periyot seçili haldeyken sheet açıkken döndür | geçti | geçti | geçti | geçti |
| 68 | Yıllık görünümdeyken uygulamayı tamamen kapat … | geçti | geçti | geçti | geçti |
| 69 | Geçmiş tarihli aylık abonelik ekle | geçti | geçti | geçti | geçti |
| 70 | Geçmiş tarihli haftalık ve yıllık abonelik ekle | geçti | geçti | geçti | geçti |
| 71 | Çok eski tarih (2+ yıl önce, haftalık) | geçti | geçti | geçti | geçti |
| 72 | 31 Ocak çıpalı aylık abonelik, Mart'ta bak | geçti | geçti | geçti | geçti |
| 73 | Gelecek tarihli abonelik | geçti | geçti | geçti | geçti |
| 74 | Bugünün tarihi | geçti | geçti | geçti | geçti |
| 75 | Geçmiş tarihli bir aboneliği kaydettikten sonr… | geçti | geçti | geçti | geçti |
| 76 | Ana ekran üst çubuğundaki grafik ikonuna bas | geçti | geçti | geçti | geçti |
| 77 | Geri oku, sonra sistem geri tuşu | geçti | geçti | geçti | geçti |
| 78 | Dört kategoriye yayılmış, karışık para birimli… | geçti | geçti | geçti | geçti |
| 79 | Ekrandaki yüzdeleri topla | geçti | geçti | geçti | geçti |
| 80 | En pahalı listesi | geçti | geçti | geçti | geçti |
| 81 | Ana ekranda kategori filtresi açıkken istatist… | geçti | geçti | geçti | geçti |
| 82 | Hiç abonelik yokken istatistiğe gir | geçti | geçti | geçti | geçti |
| 83 | Tutarı sıfır olan kategori | geçti | geçti | geçti | geçti |
| 84 | TalkBack ile dağılım satırı | geçti⁵ | geçti⁵ | geçti⁵ | geçti⁵ |
| 85 | Koyu tema (API 34) | geçti | geçti | geçti | geçti |
| 86 | Tabloda tek ay varken istatistiğe gir | geçti | geçti | geçti | geçti |
| 87 | İki ay kayıtlıyken | geçti | geçti | geçti | geçti |
| 88 | Altıdan çok ay kayıtlıyken | geçti | geçti | geçti | geçti |
| 89 | Aralarda kaydı olmayan bir ay | geçti | geçti | geçti | geçti |
| 90 | Sıfır kaydedilmiş bir ay | geçti | geçti | geçti | geçti |
| 91 | Bu ay geçen aydan farklı | geçti | geçti | geçti | geçti |
| 92 | Bu ay geçen ayla aynı | geçti | geçti | geçti | geçti |
| 93 | Geçen ayın kaydı yok | geçti | geçti | geçti | geçti |
| 94 | Ana para birimini değiştir, istatistiğe gir | geçti | geçti | geçti | geçti |
| 95 | TalkBack ile grafik | geçti⁵ | geçti⁵ | geçti⁵ | geçti⁵ |
| 96 | Bir karta dokun | geçti | geçti | geçti | geçti |
| 97 | Geçmiş çıpalı bir aboneliğin kartına dokun | geçti | geçti | geçti | geçti |
| 98 | Bir alanı değiştir, Kaydet | geçti | geçti | geçti | geçti |
| 99 | Hiçbir şey değiştirmeden geri dön | geçti | geçti | geçti | geçti |
| 100 | Bir alanı değiştirip geri dön | geçti | geçti | geçti | geçti |
| 101 | Düzenleme ekranında ekranı döndür | geçti | geçti | geçti | geçti |
| 102 | Boş ad / geçersiz fiyat ile Kaydet | geçti | geçti | geçti | geçti |
| 103 | Bir satırı hafifçe kaydır | geçti | geçti | geçti | geçti |
| 104 | TalkBack ile bir satır | kısmen⁶ | kısmen⁶ | kısmen⁶ | kısmen⁶ |
| 105 | Düzenleme sonrası monthly_snapshots | geçti | geçti | geçti | geçti |
| 106 | Ayarlar → Tema | geçti | geçti | geçti | geçti |
| 107 | Açık'ı seç, sistem temasını koyuya al | ölçülemedi⁷ | ölçülemedi⁷ | geçti | geçti |
| 108 | Koyu'yu seç, sistem temasını açığa al | geçti | geçti | geçti | geçti |
| 109 | Sistemi takip et'i seç, sistem temasını değişt… | ölçülemedi⁷ | ölçülemedi⁷ | geçti | geçti |
| 110 | Tema seç, uygulamayı tamamen kapat, yeniden aç | geçti | geçti | geçti | geçti |
| 111 | (API 31+) Ayarlar → Duvar kâğıdı renkleri | — | — | geçti | geçti |
| 112 | Duvar kâğıdı renkleri açıkken koyu temayı zorla | — | — | geçti | geçti |
| 113 | Duvar kâğıdı renkleri açıkken istatistik | — | — | geçti | geçti |
| 114 | (API < 31) Duvar kâğıdı renkleri satırı | geçti | geçti | — | — |
| 115 | Dashboard, kart, istatistik, trend, karşılaştı… | geçti | geçti | geçti | geçti |
| 116 | Cihaz dilini İngilizceye al | geçti | geçti | geçti | geçti |
| 117 | (API 29) ₺ karakteri | geçti | geçti | geçti | geçti |

Toplam hücre: 468 | geçti: 428 | düştü: 1 | ölçülemedi: 4 | geçerli değil: 31 | kısmen: 4

**Dipnotlar**

- ¹ Sistem teması bu imajlarda koyuya alınamıyor (API 24'te `cmd uimode` "No shell
  command implementation", API 29'da komut "Night mode: no" döndürüp yazmıyor).
  Renkler uygulamanın kendi **Koyu** seçeneğiyle ölçüldü ve dört cihazda aynı
  çıktı: arka plan `#0D1A14`, kart `#1F3D2D` → **1,50:1**; vurgu altın `#D4AF37`;
  hiçbir yerde mor yok.
- ² Gerçek **cihaz dili** değişimi (Ayarlar → Diller; `persist.sys.locale` ile
  doğrulandı).
- ³ Uygulamaya özel dil (`cmd locale set-app-locales`). Cihaz geneli locale root
  istiyor, bu imajlarda yapılamıyor — ölçülen şey uygulamanın locale'i takip
  etmesi, cihaz ayarının kendisi değil.
- ⁴ Kararlı hâl ölçüldü: veri varken boş durum hiç görünmüyor. Maddenin "yükleme
  sırasında da" yarısı ölçülemedi — eldeki en hızlı gözlem aracı 3,3 saniye süren
  `uiautomator dump`, açılış karesi o pencereye sığmıyor.
- ⁵ Erişilebilirlik **ağacından** ölçüldü ve ağaç, ekran okuyucunun okuduğu şeyin
  ta kendisi: satırlar tek odak durağı ve beklenen cümleyi veriyor
  ("Eğlence, ₺100,00, yüzde 34" / "Aylık trend: Nisan ₺200,00, Mayıs kayıt yok,
  …"). TalkBack hiçbir imajda kurulu değil, sesli okunuş doğrulanamadı.
- ⁶ #104'ün "tek odak durağı" yarısı ağaçtan geçti; **"Sil" özel eylemi**
  `uiautomator dump` biçiminde hiç taşınmadığı için ne doğrulanabildi ne çürütüldü.
- ⁷ Sistem koyu teması yok veya değiştirilemiyor (yukarıdaki ¹).
- "—" o API'de **geçerli değil**: #34-#37 ve #41-#45 çalışma zamanı bildirim izni
  API 33+ olduğu için, #40 ve #46 tam tersi yönde API < 33 için, #39 bildirim
  kanalları API 26+ olduğu için, #111-#113 duvar kâğıdı renkleri API 31+ olduğu
  için, #114 satırın devre dışı hâli yalnızca API < 31'de görüldüğü için.

### Düşen madde: API 24 / #40

**Beklenen:** "(API < 33) Satıra dokun → Sistem bildirim ayarları açılıyor, izin
diyaloğu hiç çıkmıyor."

**Ölçülen:** Android 7.0'da satıra dokunmak **hiçbir şey yapmıyor.** Dokunuştan
sonra `mResumedActivity` hâlâ `com.elinacn.subtrack/.MainActivity`,
`logcat -s AndroidRuntime:E` boş (çökme yok), ne sistem bildirim ekranı ne de
uygulama detay sayfası açılıyor.

**Sebep (ölçüldü, tahmin değil):** Android 7.0'ın Ayarlar uygulaması
`android.settings.APP_NOTIFICATION_SETTINGS` eylemini **karşılıyor**, yani
`startActivity` başarılı oluyor ve `ActivityNotFoundException` atılmıyor —
`openNotificationSettings` içindeki uygulama-detay yedeği bu yüzden hiç devreye
girmiyor. Ama o ekran API 24'te **iki** ekstra istiyor ve ikincisi gönderilmiyor:

```
W NotifiSettingsBase: Missing extras: app_package was com.elinacn.subtrack, app_uid was -1
```

Aynı satır elle `am start -a android.settings.APP_NOTIFICATION_SETTINGS
--es app_package com.elinacn.subtrack` çalıştırıldığında da çıkıyor, yani
`app_uid` de zorunlu. Uygulama yalnızca `Settings.EXTRA_APP_PACKAGE`
("android.provider.extra.APP_PACKAGE", **API 26 adı**) gönderiyor.

Lint bunu zaten işaret ediyor ve uyarı fazlardır duruyor:
`ReminderPermissionActions.kt:41` ve `:42` — *"Field requires API level 26
(current min is 24) … [InlinedApi]"*.

**Kapsamı:** yalnızca API 24-25. Aynı madde API 29'da geçiyor
(`AppNotificationSettingsActivity` açılıyor). Uygulama çökmüyor, veri kaybı yok;
minSdk cihazında bir ayar kısayolu sessizce çalışmıyor.

**Düzeltme yazılmadı** — bu faz test ve doğrulama fazıydı ve prompt düzeltmeyi
açıkça yasakladı. Turun geri kalanı yine de tamamlandı: bu madde tek yönlü bir
Ayarlar geçişi ve açılmadığı için sonraki maddelerin ön koşulunu kirletmesi
mümkün değil; durup 60 maddeyi ölçmeden bırakmak kazanç getirmezdi.

### Turda ortaya çıkan ölçüm bulguları

- **"Geri al"a dump'la yetişilemiyor.** Snackbar ≈4 sn duruyor,
  `uiautomator dump` + `cat` çifti 3,3 sn sürüyor. #19 iki kez "geri alma
  çalışmıyor" diye düştü, sonra ürünün değil ölçümün yavaş olduğu anlaşıldı.
  Silme ve dokunuş artık tek `adb shell` satırında gidiyor.
- **#69-#75 artık ölçülebiliyor.** Seçicinin **metin giriş modu** her çıpayı tek
  seferde alıyor; ay dönümü beklemeye gerek kalmadı. #72'nin asıl sınavı
  `31.01.2026` çıpası: çıpadan sayılırsa 30 Eylül ("14 gün kaldı"), adım adım
  kırpılsaydı 28 Eylül ("12 gün kaldı") olurdu — dört cihazda da 14 gün.
- **#39 artık ölçülebiliyor.** Kanal ilk bildirimden önce yok; önce
  `PaymentReminderWorkerTest` bir kez koşturulup kanal yaratılıyor, sonra
  `CHANNEL_NOTIFICATION_SETTINGS` ile yalnız kanal kapatılıyor. Uygulama izni
  `granted=true` iken satır "Kapalı — sistem ayarlarından açılmalı" dedi (API 29,
  34, 36).
- **16a klavye tablosunun kur ekranı satırları 95 px yanlıştı.** API 34/36 için
  klavye **kapalı** değerler 16a öncesinden kalmış; 95 px, o cihazlardaki durum
  çubuğu payı. Klavye **açık** değerler 16a'da yeniden ölçülmüştü ve 16b ölçümüyle
  piksel piksel tuttu. Tablo düzeltildi ve API 24 satırları eklendi.
- **API 24'te edge-to-edge yok.** Uygulama penceresi `[0,0][720,1184]`, gezinme
  çubuğu ayrı ve opak; FAB API 29'dakinden 96 px yukarıda. Klavye açılınca
  kaydırma düğümü `[0,176][720,658]`e daralıyor ve "Varsayılana dön" fiskeden
  sonra tam 658'de, yani sınırda duruyor.
- **API 24'ün locale verisi farklı.** Tarih seçicinin metin maskesi `DDMM/YYYY`
  (diğerlerinde `DD.MM.YYYY`) ve saat 12 saatlik biçimde yazılıyor
  ("ÖS 9:57"). İkisi de platformun, uygulamanın değil; ayrıştırma doğru.
- **API 29'un saat dilimi America/New_York.** Host'la arasında yedi saat var;
  tarih fikstürü cihazın kendi saat diliminde gece yarısına yazılmazsa çıpa bir
  gün kayıyor. 16b'de bir ölçüm bu yüzden "24 gün" dedi, hata üründe değildi.

**Değişen dosyalar**
- `app/src/debug/java/com/elinacn/subtrack/debug/PreferencesStoreEntryPoint.kt` —
  yeni; uygulamanın canlı `DataStore<Preferences>` örneğini teste veren debug-only
  entry point
- `app/src/androidTest/java/com/elinacn/subtrack/testsupport/ReminderPreconditions.kt` —
  yeni; günün "bildirildi" kaydını temizleyen ve API 33+ bildirim iznini veren
  ortak ön koşul
- `app/src/androidTest/java/com/elinacn/subtrack/reminder/PaymentReminderWorkerTest.kt` —
  `@Before reset()`, izin verme, KDoc'tan "önce `pm clear` yap" talimatı kalktı
- `app/src/androidTest/java/com/elinacn/subtrack/edit/EditedDateReminderTest.kt` —
  aynısı; "tek başına koştur" talimatı kalktı
- `app/src/test/java/com/elinacn/subtrack/ExampleUnitTest.kt` — silindi
- `app/src/androidTest/java/com/elinacn/subtrack/ExampleInstrumentedTest.kt` — silindi
- `docs/TESTING.md` — AVD tablosuna ölçülen cihaz özellikleri, "hangi madde hangi
  cihazda ölçülemiyor" tablosu, sıra bağımsızlığının koşum yöntemi, çıpayı metinle
  girme, "Geri al"a dump'la yetişilememesi, cihaz dili, düzeltilmiş klavye tablosu
- `docs/PROGRESS.md` — bu kayıt
- `docs/ROADMAP.md` — şablon test maddesi işaretlendi

**Commit'ler**
- `297eb54` test: let the reminder classes set up their own preconditions
- `0e5d71a` test: drop the two tests the project template generated
- `docs:` record the 16b regression tour and the order-independence method

**Sonraki faz için not**
- **API 24 / #40 açık.** Bildirim ayarları kısayolu Android 7.0-7.1'de çalışmıyor.
  Karar verilmesi gereken şey davranış: ya `app_uid` da gönderilecek, ya API 26
  altında doğrudan uygulama detay sayfasına gidilecek, ya da satır o sürümlerde
  hiç dokunulabilir olmayacak. Üçü de ürün kararı; 16b yazmadı.
- `#50`'nin "yükleme karesi" yarısı ve `#84/#95/#104`'ün TalkBack yarısı hâlâ
  ölçülemiyor. İlki araç meselesi (daha hızlı kare yakalama), ikincisi imaj
  meselesi (Accessibility Suite kurulu bir imaj).

---

## [Faz 16a] Edge-to-Edge Geçişi — 2026-09-16

**Durum:** Tamamlandı. Renk, palet, `Dimens`, tipografi değişmedi — bu faz
yalnızca insets. `Color.kt`, `Type.kt`, `Dimens.kt`, `domain/`, `data/`,
`reminder/`, Room, DataStore, jest mantığı ve `SwipeToDeleteRow` **dokunulmadı**.
Ekleme sheet'inin IME davranışına da dokunulmadı ve değişmediği ölçüldü.

### `enableEdgeToEdge`, ve ikonların hangi cevabı takip ettiği

`setContent`'ten **önce** çağrılıyor — decor'un insets'i tüketmesini bu durduruyor
ve pencerenin ilk yerleşiminden önce bilmesi gerekiyor. targetSdk 36 yüzünden
Android 16 bunu zaten dayatıyordu; çağrı, aynı düzeni her sürümde açık hale
getiriyor ve asıl kazancı eski sürümlerde: insets ilk kez Compose'a ulaşıyor.

Sistem çubuğu ikon rengi `SystemBarStyle.auto(...) { darkTheme }` ile kuruluyor
ve `darkTheme`, renk şemasının kurulduğu cevabın **aynısı**. `Theme.kt`'deki
`when (themeMode)` bloğu `isDarkTheme` olarak dışarı alındı; iki yere kopyalansa
zamanla ayrışırdı. Sonuç: ayarlarda koyu tema zorlandığında cihazın gece ayarı
ne derse desin çubuklar da kararıyor. Üst banttaki kontrast piksel sayısı
(16-0'da açık temada **sıfırdı**):

| | API 29 (48px bant) | API 34 (128px) | API 36 (128px) |
|---|---|---|---|
| Tercih açık, sistem açık | 2491 | 3354 | 4319 |
| **Tercih açık, sistem koyu** | 2503 | 3354 | 4301 |
| Tercih koyu | 2503 (beyaz ikon) | 3224 (beyaz ikon) | 4286 (beyaz ikon) |

**İki çubuk farklı stil alıyor ve bu ölçümden çıktı.** İlk hâlde ikisine de aynı
scrim çifti verilmişti; API 24'te durum çubuğu `background` rengiyle opak
doldu ve uygulama çubuğunun üstünde görünür bir dikiş oluştu. Sebep: API 29
altında androidx çubuğu kendisine verilen scrim'le dolduruyor, sisteme
bırakmıyor. Durum çubuğunun scrim'e ihtiyacı yok (ikonları API 23'ten beri
kararabiliyor) → şeffaf. Gezinme çubuğunun var (API 26 altında ikonları hep
beyaz) → `scrim` rolü, iki şemada da siyah. İkisi de paletten geliyor, yeni
renk sabiti yok.

**Açılış göz kırpması geri gelmedi.** `onCreate`'teki ilk çağrı durum çubuğunu
bilerek `SystemBarStyle.light` ile kuruyor: o karelerde ekranda olan pencere
`Theme.SubTrack`'in açık arka planı (`#FAFAFA`), cihaz ne olursa olsun. 14b'nin
ilk-kare kapısıyla çakışmıyor; kare kare tarandı — `#FAFAFA` boyunca ikonlar
siyah (3078 px), tercih gelince `#1F3D2D` üstünde beyaz (3078 px), aralarında
açık temalı uygulama karesi yok.

### `adjustResize` kaldırılmadı — kaldırılması API 29'u kırdı

Prompt bu satırın kaldırılmasını istiyordu ("edge-to-edge'de sistem onu zaten
yok sayıyor"). Bu **API 30'dan itibaren** doğru, API 24-29'da değil. Kaldırıldı,
ölçüldü, geri kondu:

| API 29, kur ekranı, klavye açık (üst kenar y=784) | Kaydet | Varsayılana dön | Fiske işe yarıyor mu |
|---|---|---|---|
| `adjustResize` yokken | y=1044 | y=1164 | **hayır** — görünüm hâlâ 1280 |
| `adjustResize` varken | y=564 | y=684 | evet, tek fiske |

Sebep `WindowInsets.ime`: API 30 altında pencere küçülmediği sürece
raporlanmıyor, yani `imePadding()` orada sıfıra padding uyguluyor — §16'nın 9b-2'de ölçtüğü
sıfırın ta kendisi. İkisi çakışmıyor:
`setDecorFitsSystemWindows(false)` platformun bayrağı API 30'dan itibaren yok
saymasına yol açıyor, yani her sürümde **tam olarak biri** yürürlükte. API 34 ve
36 geri koyduktan sonra pikseli pikseline aynı kaldı, `android:id/content` dahil.

### Ekran başına karar: `contentPadding` mi `padding` mi

`Scaffold` payları veriyor ama **tüketmiyor** (material3 1.4.0 KDoc'u
`Modifier.padding` + `consumeWindowInsets` öneriyor). Karar ekrana göre değişti:

- **Ana ekran** payları `contentPadding` olarak alıyor. `Modifier.padding`
  kaydırma görünümünü kısaltır: liste jest çubuğunun üstünde biter, altında
  hiçbir şeyi kaydırmayan ölü bir şerit kalır, ve alt kenarı geçen satır
  çubuğun altına kayacağına havada kesilir. Ölçüldü (API 34 ve 36): kaydırırken
  satırlar çubuğun altına giriyor, en alttaki satır `[0,1868][1080,2127]`'de
  duruyor — çubuk üst kenarından (2337) **210 px** yukarıda.
- **Ayarlar, kur, düzenleme, istatistik** payları kaydırmanın **dışında**
  tutuyor. Hepsi kullanılacak ya da okunacak bir şeyle bitiyor; jest çubuğunun
  altına kayan bir düğme yarı dokunulabilir.
- **Kur ve düzenleme** ayrıca `consumeWindowInsets` + `imePadding()` alıyor.
  `consumeWindowInsets` olmadan `imePadding` klavyeyi pencere kenarından ölçer
  ve zaten uygulanmış 63 px'i ikinci kez ekler.

**FAB ve Snackbar'ı `Scaffold` kendisi taşıyor** — ölçüldü, varsayılmadı.
API 36'da FAB kutusu `[891,2148][1038,2295]`, alt kenara 105 px = 63 (gezinme)
+ 42 (16dp `FabSpacing`). Snackbar'ın alt kenarı y=2148, yani 252 px = 147
(FAB) + 42 + 63. İkisi de gezinme çubuğunu hesaba katıyor.

### 16-0'ın üç kırığı

| | 16-0 | 16a |
|---|---|---|
| Kur ekranı "Varsayılana dön" | y=1623, klavyenin (1517) arkasında, kaydırma kurtarmıyor | tek fiskede y=1386 |
| Düzenleme ekranı Kaydet | y=1833, tamamen arkada | tek fiskede y=1323 |
| Açık temada durum çubuğu ikonları | 1080x128 bantta **sıfır** beyaz olmayan piksel | 4301-4319 piksel |

### Geriye uyumluluk: çift padding yok, ölçüldü

Aynı cihaza 16a öncesi ve sonrası derleme sırayla kuruldu.

| | API 29 önce → sonra | API 34 önce → sonra |
|---|---|---|
| `android:id/content` | `[0,48][720,1280]` → `[0,0][720,1280]` | `[0,128][1080,2337]` → `[0,0][1080,2400]` |
| Uygulama çubuğu başlığı | `[32,84][212,140]` → **aynı** | `[43,175][276,249]` → **aynı** |
| Dashboard kartı | `[32,208][688,481]` → **aynı** | `[42,338][1038,696]` → **aynı** |
| İlk liste satırı | — | `[0,1157][1080,1458]` → **aynı** |
| FAB alt kenar boşluğu | 64 px → **aynı** | 147 px → **aynı** |
| En alt satır, dinlenmede | 160 px → **aynı** | 273 px → **aynı** |
| Ekleme sheet'i Kaydet | `[329,1132][391,1172]` / `[329,630][391,670]` → **aynı** | — |

Decor'un uyguladığı pay uygulamaya geçti, üstüne binmedi. `content` dışında
değişen tek bir koordinat yok.

**fs 2.0'da insets bozulmuyor:** API 36 kur ekranında klavye kapalıyken Kaydet
`[465,2268][615,2337]` — tam jest çubuğunun üst kenarında duruyor, altına
geçmiyor; klavye açıkken tek fiskede `[465,1043][615,1141]`. API 29'da iki
fiske gerekiyor ama ikisi de klavyenin üstüne geliyor.

**Değişen dosyalar**
- `MainActivity.kt` — `enableEdgeToEdge()` `setContent` öncesinde; `SystemBarsFollowTheTheme` **yeni**, tema bilindiğinde çubuk stillerini yeniden uyguluyor
- `ui/theme/Theme.kt` — `isDarkTheme` dışarı alındı ve public yapıldı; renk tanımlarına dokunulmadı
- `AndroidManifest.xml` — `adjustResize`'ın yorumu değişti, bayrak kaldı
- `ui/home/HomeScreen.kt` — liste payları `contentPadding`'e taşındı
- `ui/settings/rates/ExchangeRatesScreen.kt`, `ui/edit/EditSubscriptionScreen.kt` — `consumeWindowInsets` + `imePadding`
- `ui/settings/SettingsScreen.kt`, `ui/statistics/StatisticsScreen.kt` — pay sırası netleştirildi ve gerekçelendirildi
- `docs/ARCHITECTURE.md` §16 — yeniden yazıldı, eski tablo tarihsel kayıt olarak duruyor
- `docs/TESTING.md` — klavye tablosu üç ekran × üç cihaz; #26'nın gerekçesi; klavye üst kenarının artık nasıl okunacağı
- `docs/ROADMAP.md` — edge-to-edge maddesi işaretlendi, `adjustResize` notuyla
- `docs/screenshots/phase-16a/` — 30 görüntü

**Testler:** birim testi yazılmadı; bu bir layout değişikliği ve `Scaffold` ile
insets davranışı JVM'de gözlemlenemez. Mevcut **331 test yeşil**, `lintDebug`
**0 bulgu**, `assembleDebug` yeni uyarı vermiyor.

**Commit'ler**
- `d6c2325` feat: go edge-to-edge and let the system bars follow the theme
- `3600a33` fix: apply the window insets on each full screen
- `7a56e21` fix: keep adjustResize for the releases that still honour it

**Karşılaşılan sorunlar**
- **Promptun `adjustResize` kaldırma talimatı API 29'da yanlıştı.** Yukarıda
  ölçümle. Talimatın dayandığı cümle ("sistem onu zaten yok sayıyor") minSdk 24
  için değil, Android 15+ için doğru.
- **İlk scrim seçimi API 24'te dikiş bıraktı.** Tek bir stil çiftini iki çubuğa
  da vermek, API 29 altında durum çubuğunu opak dolduruyor. İkiye ayrıldı.
- **§16'nın "iki emülatörde de gezinme çubuğu var" cümlesi yanlış.**
  `subtrack_narrow_api29` AVD'sinde gezinme çubuğu **yok**: `dumpsys window
  displays` `app=720x1280` diyor ve alt banttaki her piksel uygulamanın arka
  planı. O ölçümdeki `navigationBars = 0`'ın ikinci ve hâlâ geçerli sebebi bu.
  Düzeltildi.
- **`uiautomator dump` yeniden kurulumdan hemen sonra bayat ağaç veriyor.**
  API 34'te bir kez `android:id/content`'i `[0,128][1080,2337]` okuttu; aynı
  ölçüm tekrarlandığında `[0,0][1080,2400]` çıktı. TESTING.md'de zaten yazılı
  olan tuzak, ölçüm sırasında bir kez daha yakalandı.
- **Ekran görüntüsü uygulama yeniden başlatılırken alınırsa geçiş
  animasyonunu yakalıyor.** Bir kare yarı saydam çıktı; yakalamadan önceki
  bekleme uzatıldı ve set yeniden alındı.

**Sonraki faz için not**
- Tam 117 maddelik regresyon turu bu fazda **koşulmadı** — promptun kapsamı
  dışındaydı, 16b'de üç cihazda eksiksiz koşulacak. Bu fazda sürülen maddeler:
  #26 (üç cihaz) ve klavye tablosunun her satırı.
- Ekran görüntüleri açık temada üç cihazda, koyu temada API 34 ve 36'da alındı.
  API 29'un koyu teması bu fazda görüntülenmedi; piksel ölçümü yapıldı
  (`#1F3D2D` üstünde 2503 beyaz ikon pikseli), görüntü yok.
- `subtrack_min_api24`'te edge-to-edge kurulum sonrası kabaca doğrulandı
  (durum çubuğu şeffaf + koyu ikon, gezinme çubuğu siyah + beyaz ikon, çökme
  yok) ama koordinat turu yapılmadı; DOĞRULAMA listesi üç cihaz istiyordu.

---

## [Faz 14b] Dynamic Color, Tema Tercihi ve Para Birimi Gösterimi — 2026-09-16

**Durum:** Tamamlandı. **Faz 14 KAPANDI.** `Color.kt` değişmedi — bu faz palet
değiştirmiyor. `Dimens.kt`, `Type.kt`, `AndroidManifest.xml`, `domain/usecase/`,
`reminder/`, Room ve snapshot tarafı da değişmedi; jest mantığına, düzene ve
boyutlara dokunulmadı. `Theme.kt`'deki `by lazy` yapısı olduğu gibi duruyor.

### Dynamic color varsayılan kapalı — ve iki duvar kâğıdıyla ölçüldü

Material You renkleri duvar kâğıdından gelir; açıldığı anda 14a'nın zümrüt-altın
kimliği ve o kimliğe göre ölçülmüş 37 rolün kontrastı geçersiz olur. Bu yüzden
tercih varsayılan olarak **kapalı** ve açmak kullanıcının seçimi.

14a'da eski palette çubuk ile izi çakışıp grafik okunmaz olmuştu; bu fazın asıl
sorusu Material You'da aynı şeyin olup olmadığıydı. **Olmuyor** (API 34):

| Palet | İstatistik çubuğu / iz | Trend sütunu / iz | Snackbar "Geri al" |
|---|---|---|---|
| Sıcak (tohum `#B33A3A`) | `#C00020` / `#DFBFBD` — **3,78:1** | aynı çift | `#FFB3AF` / `#1E100F` — **10,84:1** |
| Soğuk (tohum `#2E4FB3`) | `#004FE6` / `#C4C5D6` — **3,77:1** | aynı çift | `#B6C4FF` / `#11131C` — **10,87:1** |

Kendi paletimizde aynı çift koyu temada `#D4AF37` / `#3A5A48` = **3,65:1** çıktı,
yani 14a'nın kaydettiği değer birebir korundu.

**Duvar kâğıdı nasıl değiştirildi:** `cmd wallpaper` bu imajlarda duvar kâğıdı
atama komutu taşımıyor ve resim seçici etkileşimli. Onun yerine sistemin duvar
kâğıdından çıkardığı **tohum rengi** doğrudan yazıldı
(`settings put secure theme_customization_overlay_packages`) — bu, üretilen
şemanın gerçek girdisi. Komutlar `TESTING.md`'de.

### Tema tercihi üç seçenekli, dynamic color'dan bağımsız

İki ayrı anahtar, iki ayrı soru: hangi hue'lar, ve açık mı koyu mu. Duvar kâğıdı
renkleri açıkken de koyu tema zorlanabiliyor — testle ve cihazda doğrulandı.

Seçim **diyalog** ile yapılıyor, chip veya segmented ile değil. Gerekçe genişlik:
"Sistemi takip et" tek başına 360dp'nin üçte birinden geniş, ve fs 2.0'da üçü
yan yana sığmıyor. Diyalog her seçeneğe tam satır veriyor; ölçüldü, fs 2.0'da
seçenek satırları 75dp / 48dp / 48dp, hiçbiri kırpılmıyor.

### Açılıştaki tema göz kırpması: vardı, ölçüldü, kapatıldı

Sistem açık temadayken saklanan tercih koyu iken ana ekran **açık temada tam
olarak çiziliyordu** — arka plan `#D3E2D8`, uygulama çubuğu beyaz — ve ancak
sonra koyuya dönüyordu. Tek bir tam kare, ama uygulamanın fikir değiştirmesi gibi
görünüyor.

`MainActivity`'de `OnPreDrawListener` ile ilk kare tutuldu. İki ayrıntı ölçümle
düzeltildi:

1. **Dinleyici `setContent`'ten sonra kayıt edilmeli.** Önce kayıt edildiğinde
   hiç çalışmadı ve açık temalı kare (`#D3E2D8`) hâlâ görünüyordu: içerik
   görünümüne bir şey konana kadar kendi `ViewTreeObserver`'ı yok.
2. **Son tarih bir `postDelayed` olmalı, dinleyicinin içinde okunan bir değer
   değil.** Çizimi iptal etmek yeni bir traversal planlamıyor, dinleyici de ancak
   bir traversal olursa çalışıyor — yani dinleyicinin içindeki son tarih, tam da
   onun var olduğu durumda (tercih hiç gelmezse) hiç okunmuyordu. Sonuç hiç
   çizilmeyen bir pencere, sistemin diliyle "does not have a focused window".

Düzeltmeden sonra: beyaz açılış penceresinden **doğrudan** `#0D1A14`'e; arada
açık temalı kare yok. Kare kare taramayla doğrulandı.

### Para birimi: locale sayıyı belirler, işareti belirlemez

`NumberFormat` para işaretini okuyucunun locale'inden alıyor ve glif yoksa ISO
koduna düşüyordu: İngilizce arayüzde toplam "TRY 1.785,45", kart "$10.99".

Artık işaret her locale'de `Currency.symbol` (₺ $ € £); ondalık ayracı, gruplama
ve işaretin hangi tarafta durduğu locale'in kalıyor. Cihazda ham metinle
doğrulandı:

- İngilizce: `Total Monthly, ₺3,060.43` · `Spotify, $10.99` · `Gym, £9.99`
- Türkçe: `Aylık Toplam, ₺3.060,43` · `iCloud, ₺29,99` · `Notion, €12,00`

**Tarama sonucu — biçimlendirme kaç yerde yapılıyor:** tek yerde,
`MoneyFormatter`. Dashboard, kart, istatistik dağılımı, en pahalı listesi, trend
ekseni ve tepe etiketi, "geçen aya göre" cümlesi — hepsi oradan geçiyor.
**Bildirim hiç tutar taşımıyor** (`reminder/` içinde ne `Money` ne `Currency`
geçiyor; metin "ad — bugün/yarın/N gün kaldı"). **Kur ekranı** tutar
biçimlendirmiyor, para birimi *adlandırıyor* — o da artık sembolle
("1 $ = … ₺"). Dashboard'ın çeviri notu ilk turda gözden kaçtı, cihazda
yakalandı ve düzeltildi.

**İstisna, bilerek:** ayarlardaki para birimi seçici chip'leri ISO kodunu yazmaya
devam ediyor. Orada kod bir tutarın yazımı değil, seçilen şeyin kimliği; chip'in
ekran okuyucuya verdiği ad zaten "Türk lirası" / "ABD doları" diyor.

**₺ karakteri API 29'da çiziliyor** — eski font sürümlerinde eksik olabilir diye
ayrıca ölçüldü, dashboard'daki en büyük puntoda dahil tofu kutusu yok.

**Değişen 298 test yok.** Mevcut testlerin hiçbiri biçimlendirilmiş bir para
metnine bakmıyordu — `MoneyFormatter`'ın bu faza kadar hiç testi yoktu — yani
para birimi biçimi değişince bir beklenti kırılmadı. 298 → **331**, hepsi yeşil.

**Değişen dosyalar**
- `domain/model/ThemeMode.kt` — **yeni**, üç değerli tema modu
- `domain/repository/SettingsRepository.kt` — dört yeni üye (tema modu + dynamic color)
- `data/repository/SettingsRepositoryImpl.kt` — `theme_mode` ve `dynamic_color` anahtarları, mevcut DataStore örneğinde
- `ui/theme/Theme.kt` — mod ve dynamic color'a göre şema seçimi; `by lazy` korundu
- `ui/theme/DynamicColorSupport.kt` + `AndroidDynamicColorSupport.kt` — **yeni**, API 31 kapısı tek yerde, `@ChecksSdkIntAtLeast` ile
- `di/ThemeModule.kt` — **yeni**, bağlama
- `MainViewModel.kt` — **yeni**, activity'nin tema durumu
- `MainActivity.kt` — ilk kareyi tutan kapı
- `ui/settings/SettingsUiState.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt` — iki yeni satır ve olayları
- `ui/settings/ThemeModeDialog.kt` — **yeni**, üç seçenekli chooser
- `ui/common/SettingsSwitchRow.kt` — **yeni**, anahtarlı ayar satırı (tek odak durağı)
- `ui/common/MoneyFormatter.kt` — sembol zorlanıyor; ölü `spacedAfterCode` kalktı
- `domain/model/Currency.kt` — `symbol` alanı
- `ui/settings/rates/ExchangeRatesScreen.kt`, `ui/home/HomeScreen.kt` — para birimini sembolle adlandırma
- `ui/statistics/components/MonthlyChangeRow.kt` — `Locale.forLanguageTag`
- `res/values{,-en}/strings.xml` — sekiz yeni metin
- `CLAUDE.md` §9 — gerekçedeki renkler yeni palete güncellendi
- Testler: `MoneyFormatterTest` (yeni), `SettingsViewModelThemeTest` (yeni), `SettingsRepositoryImplTest` (genişletildi), `FakeDynamicColorSupport` (yeni), `FakeSettingsRepository` (genişletildi)

**Commit'ler**
- `4833739` feat: store the theme choice and the wallpaper-colour choice, and hold the first frame
- `3aaa72b` feat: add the theme and wallpaper-colour rows to settings
- `15ea9ca` fix: write every amount with a currency symbol, whatever the locale says
- `ebfd1fc` chore: clear the two phase 14a leftovers
- `29168f2` test: cover the theme preferences and the forced currency symbol
- `761b1b6` docs: add phase 14b dynamic colour screenshots
- `0443481` fix: post the first-frame deadline instead of testing it inside the listener
- `05f32e2` fix: name the currency with its mark in the dashboard's conversion note too

**Karşılaşılan sorunlar**

- **İlk kare kapısı iki kez yanlış kuruldu.** Birincisi `setContent`'ten önce
  kayıt, ikincisi son tarihi dinleyicinin içinde okumak. İkisi de ölçümle
  yakalandı; ikincisi ANR loglarındaki "does not have a focused window"
  satırından çıktı.
- **Dashboard'ın çeviri notu taramada kaçtı.** Kod içi arama `MoneyFormatter`
  çağrılarına bakıyordu, o satır ise para birimini `stringResource`'a *ad* olarak
  veriyordu. Cihazda Türkçeye geçince görüldü.
- **Kaydırarak silme üç tur boşa gitti**: silme yönü sona doğru, yani LTR'de
  **sola**. Sağa kaydırma hiçbir şey yapmıyor çünkü yapmaması gerekiyor
  (liste #10). Ayrıca API 34'te sağ kenardan başlayan kaydırma Google Lens'i
  açıyor. İkisi de `TESTING.md`'ye yazıldı.
- **İki emülatör aynı anda koşarken** hem launcher hem uygulama ANR verdi; tek
  emülatörle tekrarlanmadı. Ölçümler tek emülatörle alındı.

**Sonraki faz için not**
- `#39` (yalnızca kanalı kapatma) sürülemedi: kanal ilk bildirim gönderilene
  kadar oluşmuyor, o da enstrümantasyon istiyor. TalkBack maddeleri (`#84`,
  `#95`, `#104`) bu imajlarda hâlâ yapılamıyor — ağaçtan doğrulandı.
- Dynamic color açıkken 14a'nın kontrast tablosu geçerli değil ve olamaz; ölçüm
  iki tohum için yapıldı, her duvar kâğıdı için yapılamaz. Bu bilinçli bir sınır.

---

## [Faz 14a] Renk Paletinin Yeniden Tasarımı: Zümrüt + Altın — 2026-09-15

**Durum:** Tamamlandı. **Faz 14 KAPANMADI** — dynamic color, manuel tema tercihi
ve para birimi gösterimi 14b'nin işi. `Dimens.kt`, `Type.kt`, `domain/`, `data/`,
`reminder/`, Room, DataStore ve `AndroidManifest.xml` değişmedi; hiçbir boyut,
düzen veya jest mantığına dokunulmadı.

### Temel kural, ve neden ölçüyle kondu

Palet pastel mavi-camgöbeğinden koyu zümrüt + altına geçti. Altın çıpa rengi
tek başına bir sorun taşıyor:

| Ölçüm | Oran | Sonuç |
|---|---|---|
| Altın `#C9A227`, beyaz üstünde | **2,42:1** | Grafik bileşeni için gereken 3:1'i bile geçmiyor |
| Zümrüt `#0B5C3F`, beyaz üstünde | **8,02:1** | Metin eşiğinin (4,5:1) çok üstünde |
| Altın `#D4AF37`, koyu kart `#1F3D2D` üstünde | **5,66:1** | Koyu şemada metin olabiliyor |

Buradan çıkan kural, ve bu fazın omurgası: **açık temada zümrüt
metin/ikon/grafik, altın yalnızca dolu yüzey; koyu temada altın
metin/ikon/grafik, zümrüt ailesi yüzey.** İki şema aynı rollere farklı hue
veriyor — koyu tema açık temanın koyultulmuşu değil (`ARCHITECTURE.md` §12).

### Envanter: "biz kullanmıyoruz" ile "kimse kullanmıyor" aynı şey değil

Kod yazmadan önce `grep colorScheme.` ile tarandı. Kodun adıyla çağırdığı **9**
rol var; bunlardan **2'si** (`error`, `onSurfaceVariant`) şemada tanımsızdı,
yani Material baseline'ından geliyordu. Ama asıl bulgu şu: tanımsız rolleri
çizen bileşenler bizim değil, Material'ın kendi bileşenleri. Snackbar'ın "Geri
al"ı `inversePrimary` istiyor, chip kenarlığı `outline`, ayraç `outlineVariant`.
Hiçbiri kodumuzda geçmiyordu ve üçü de baseline mordan çiziliyordu.

**Karar: şemanın tamamı tanımlanır** — iki şemada da 37 rol. Tanımlamak
ileride sessizce doğru rengi verir, tanımlamamak sessizce baseline verir.

### Kontrast tablosu

Her çift hesaplandı; eşik normal metin 4,5:1, büyük metin ve grafik 3:1.

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

En düşük gereken çift **3,32:1**, eşiğin üstünde. Eşiğin altında tek çift yok;
hiçbir eşik indirilmedi, çıpa renklerin hiçbiri değiştirilmedi.

### Kapanan üç ölçülmüş borç

- **Koyu temada kart ↔ arka plan** 1,29:1 → **1,50:1**. Cihazda `#1F3D2D`
  üstüne `#0D1A14` okunarak doğrulandı.
- **13a'nın çubuk ↔ iz çakışması.** İz artık çubuğun saydamlaştırılmışı değil,
  kendi rolü (`outlineVariant`). Eski gerekçe — koyu şemada `primary` ile
  `primaryContainer` aynı pastel maviydi — yeni palette geçersiz.
- **13b'nin trend sütunu ↔ iz oranı** 3,01:1 → açık 4,40:1, koyu 3,65:1.

Bir borç **kapanmadı ve kapanamaz**: iz ↔ arka plan (açık 1,36:1, koyu 2,33:1).
Cebirle gösterildi — iz hem çubuktan hem arka plandan 3:1 ayrışacaksa, çubuğun
arka plana karşı 9:1'e çıkması gerekir; paletin zümrütü beyazda 8,02:1. "Sıfır
kaydedildi" ile "kayıt yok" ayrımını bu yüzden hâlâ tümüyle cümle taşıyor.

### Cihazda piksel olarak doğrulanan

| Ne | Önce | Sonra | Rol |
|---|---|---|---|
| Snackbar "Geri al" (açık) | mor | `#D4AF37` | `inversePrimary` |
| Snackbar zemini (açık) | nötr gri | `#1F3D2D` | `inverseSurface` |
| Snackbar metni (açık) | — | `#E8EDE9` | `inverseOnSurface` |
| Seçilmemiş chip kenarlığı (açık) | mor-gri | `#5C7F6C` | `outline` |
| Seçilmemiş chip etiketi (açık) | mor-gri | `#35594A` | `onSurfaceVariant` |
| Seçili chip dolgusu / mürekkebi (açık) | — | `#CDE8DA` / `#08301F` | `primaryContainer` |
| Chip kenarlığı / etiketi (koyu) | mor-gri | `#84A694` / `#B9CFC2` | `outline` |
| Dağılım çubuğu / izi (açık) | çubuğun solgunu | `#0B5C3F` / `#A8C7B6` | 4,40:1 |
| Dağılım çubuğu / izi (koyu) | çubuğun solgunu | `#D4AF37` / `#3A5A48` | 3,65:1 |

Ekranın hiçbir yerinde baseline mor kalmadı.

**Yapılanlar**
- `Color.kt` bütünüyle yeniden yazıldı: palet adlandırılmış sabitler hâlinde,
  KDoc kuralı ve ölçüsünü taşıyor
- `Theme.kt` iki şemada da 37 rolü açıkça yazıyor; `by lazy` yapısı korundu
- Çubuk ve trend izleri `outlineVariant`'a geçti, `TRACK_ALPHA` sabitleri silindi
- Beş chip bileşenine `FilterChipDefaults.filterChipBorder(...)` ile
  `outline` kenarlığı verildi
- Her ekran iki temada yeniden gözden geçirildi, 27 ekran görüntüsü alındı

**Değişen dosyalar**
- `ui/theme/Color.kt`, `ui/theme/Theme.kt` — palet ve iki şema
- `ui/statistics/components/CategoryBarRow.kt`, `MonthlyTrendChart.kt` — iz rolü
  ve renkle ilgili KDoc'un düzeltilmesi
- `ui/statistics/components/MonthlyChangeRow.kt`, `ExpensiveSubscriptionRow.kt`,
  `ui/home/components/SubscriptionCard.kt`, `ui/common/EmptyState.kt` — yalnızca
  renk hakkında yanlış kalan yorumlar
- `ui/common/BillingPeriodSelector.kt`, `CategoryFilterBar.kt`,
  `CategorySelector.kt`, `CurrencySelector.kt`, `TotalPeriodToggle.kt` — chip
  kenarlığı
- `docs/screenshots/phase-14a/` — 27 PNG
- `docs/ARCHITECTURE.md`, `docs/ROADMAP.md`, `docs/TESTING.md`, `docs/PROGRESS.md`

**Commit'ler**
- `ed75c25` feat: repaint the app in deep emerald and gold, with every role defined
- `05f64ca` fix: give the chart track a real role, and correct what the code says about colour
- `b3a42b2` fix: draw an unselected chip's border in the role meant for control boundaries
- `b930991` docs: add phase 14a theme screenshots

**Karşılaşılan sorunlar**
- **Chip yaması bir dosyayı ıskaladı.** `CategoryFilterBar` renklerini satır
  içinde değil `filterColors()` yardımcısıyla veriyor; aynı kalıpla yazılan
  yama oraya oturmadı. Cihazda piksel hâlâ `#A8C7B6` çıkınca görüldü, bir
  `filterBorder()` yardımcısı eklendi ve `#5C7F6C` yeniden ölçüldü. Ders:
  "beş dosyaya aynı yamayı uyguladım" derlemenin geçmesiyle doğrulanmıyor.
- **Snackbar pikseli `exec-out screencap` ile yakalanmıyor** — birkaç megabaytın
  USB'den akması Snackbar'ın ömrünü yiyor. Önce cihaza yazıp sonra çekmek
  gerekti (`TESTING.md`'ye eklendi).
- `MonthlyChangeRow`'un preview'inde `Locale("tr","TR")` kullanımdan kalkmış
  API uyarısı veriyor. **Bu fazın işi değil** — uyarı 13b'den (`ccf166f`) beri
  duruyordu, dosya yeniden derlendiği için görünür oldu. Raporlandı,
  düzeltilmedi (CLAUDE.md §3).

**Test sonucu**
- 298 birim testinin hepsi **değişmeden** geçti; hiçbir test eklenmedi veya
  düzenlenmedi — renk değişikliği davranış değiştirmiyor
- `lint`: 0 hata, 24 uyarı (öncekiyle aynı)
- 105 maddelik regresyon listesi iki emülatörde koşturuldu; #15 koyu tema
  maddesi ayrıca piksel ölçümüyle doğrulandı
- Yazı tipi ölçeği 2.0'da hiçbir ekranda kırpılma yok

**Sonraki faz için not**
- **14b'ye kalanlar:** dynamic color, manuel tema tercihi, para birimi
  gösteriminin tutarlılığı. Dynamic color açılırken dikkat: bu paletin kuralı
  "hangi hue mürekkep olabilir" üzerine kurulu ve dynamic color o kararı
  kullanıcının duvar kâğıdına devrediyor — kontrast garantisi Material'ın
  ton paletinden gelmek zorunda kalacak.
- **Faz 16 (ikon ve marka kimliği)** artık bu palete göre yapılacak; altının
  mürekkep olamaması ikon çalışmasında da geçerli. Bildirim ikonu tek renk
  siluet olduğu için özellikle: altın bir bildirim ikonu açık temada kaybolur.
- `docs/screenshots/phase-14a/` bir sonraki palet değişikliğinde karşılaştırma
  tabanı; silinmesin.

---

## [Faz 15] Düzenleme Ekranı — 2026-09-15

**Durum:** Tamamlandı. **Faz 15 KAPANDI.** `reminder/` yalnızca okundu,
`MonthlySnapshotRecorder`'ın hesabına dokunulmadı, Room entity/dao/database ve
`app/schemas/` değişmedi.

### Görev 0 — ortak form, tek kural kaynağı

Bir abonelik artık iki yerden yazılıyor. İkinci bir "fiyat nedir" kopyası,
birinin değiştiği gün ayrışırdı. Bölünme şöyle:

| parça | nerede |
|---|---|
| Altı alan (`SubscriptionFormFields`) | `ui/common/` |
| Ne yazıldığı (`SubscriptionFormState` + tek `Saver`) | `ui/common/` |
| **Kurallar** (`SubscriptionInput`) | `domain/usecase/` |
| **Sözcükler** (`FormErrors.kt`) | `ui/common/` |

Kural bir **sebep** döndürüyor (`NameProblem` / `PriceProblem` / `DateProblem`),
`UiText` değil: domain androidx'siz kalıyor (§1), mesaj değişince kural dosyası
açılmıyor. `HomeViewModel`'daki `parsePrice`, `validateDate`, `MAX_PRICE` ve
`MAX_YEARS_AHEAD` oraya taşındı; ViewModel 383 → 306 satır.

**Refactor'ün kanıtı ölçüm:** çıkarma sonrası ekleme sheet'inin **her
koordinatı** 13b referansıyla birebir aynı çıktı, iki emülatörde de.

```
API 29   drag handle [328,92][392,100] · başlık [200,144][520,191]
         ad [80,279][371,327] · fiyat [80,431][360,479] · Kaydet [329,1132][391,1172]
API 34   drag handle [498,448][582,459] · başlık [334,517][746,579]
         ad [105,695][486,758] · fiyat [105,895][474,958] · Kaydet [500,2143][580,2196]
```

Klavye açıkken (`mInputShown=true`) Kaydet erişilebilir kaldı (API 29
`[329,634][391,674]`, API 34 `[500,1323][580,1376]`), döndürmede yazılan durdu,
ve mevcut **268 testin hepsi değişmeden geçti.**

### Görev 1 — dördüncü hedef, ve ilk argüman

```kotlin
const val EDIT_SUBSCRIPTION_ARG = "subscriptionId"
const val EDIT_SUBSCRIPTION = "edit_subscription/{$EDIT_SUBSCRIPTION_ARG}"
fun editSubscription(id: Long): String = "edit_subscription/$id"
```

Graf `navArgument(...) { type = NavType.LongType }` diyor, yani argüman sınırda
tipli ve `SavedStateHandle`'dan `Long` çıkıyor. ViewModel onu yine de
**nullable** okuyor: `!!` yasak (CLAUDE.md §4) ve `checkNotNull` sadece daha iyi
sözcüklerle fırlatırdı.

**Type-safe rota kararı — §13'ün bu faza bıraktığı soru cevaplandı: yine
kullanılmıyor.** `@Serializable` rotalar kotlinx.serialization derleyici
plugin'i ister; bu faz yeni bağımlılık eklemiyor ve proje AGP 9'da bir derleyici
plugin'ine bir kez yenildi (`@Parcelize`, Faz 0). Tipsiz kalan tek adım rota
metnini kurmak, o da tek fonksiyonda ve atlanamıyor — kayıtlı desen yer tutucu
taşıdığı için doğrudan navigasyona verilemez.

### Görev 2 — satırın tıklanabilir olması

`SwipeToDeleteRow`'un jest mantığına **dokunulmadı**: offset işleme, %50 mesafe
şartı, `onDragStarted`/`onDragStopped` olduğu gibi. Eklenen iki şey:
`draggable`'ın yanında bir `clickable`, ve satırın kendi `clearAndSetSemantics`
bloğunda bir `onClick` — `clearAndSetSemantics` alt ağacı düşürdüğü için aksi
hâlde dokunma parmağa var, ekran okuyucuya yok olurdu.

İkisi birden kazanamaz: sürükleme dokunma eşiğini geçer geçmez hareketi
tüketiyor, tüketilmiş değişiklik de bekleyen tıklamayı iptal ediyor.

### Görev 3 — ekran ve ViewModel

Tek `UiState`, tek `onEvent` (§5). Satır **tek seferlik `getById`** ile okunuyor,
Flow ile değil: Flow bu ekranın kendi kaydı düştüğü anda yeniden yayın yapar ve
kullanıcı yazarken formun üzerine yazardı.

- **Bulunamayan id → bir hâl, çökme değil.** Geri zıplasaydı dokunuş hiç
  işlememiş gibi görünürdü; ekran ne olduğunu söylüyor, çıkmak kullanıcının
  hamlesi.
- **Yarım kalan düzenleme sessizce atılıyor.** Geri tuşu "geri" demektir; ekleme
  sheet'i de Faz 0'dan beri böyle davranıyor ve aynı bileşenlerden kurulu iki
  formdan birinin soru sorması tutarsız olurdu. Kabul edilen bedel: kullanıcı
  kaydedilmemiş alanı yeniden yazar.
- Kaydederken satır **kopyalanıyor**: `id`, `iconKey` ve `createdAt` taşınıyor.
  `createdAt` listenin sıralama anahtarı — düzenlenen satır başa sıçramamalı.

### Görev 4 ve 5 — okundu, yeni bağlantı gerekmedi

`MonthlySnapshotRecorder` bir olaya değil `subscriptions.observeAll()`'a bağlı;
güncelleme de aynı yayını tetikliyor. `PaymentReminderWorker` her koşuda
`observeAll().first()` okuyup çıpaları baştan ilerletiyor. İkisi de cihazda
doğrulandı (aşağıda), hiçbir şey bağlanmadı.

### Testler

268 → **298 birim testi** (17 `EditSubscriptionViewModelTest`, 13
`SubscriptionInputTest`), 0 hata. Mevcut `HomeViewModel` doğrulama testlerinin
hiçbiri kırılmadı — refactor'ün asıl sınavı buydu. `lintDebug` **0 hata, 24
uyarı**; sayı 13b ile aynı, bu fazdan yeni uyarı çıkmadı.

Enstrümantasyon iki emülatörde de geçti: **19 test** (1 yeni
`EditedDateReminderTest`, 2 `PaymentReminderWorkerTest`, 16 DAO).

### Cihaz doğrulaması (API 29 · 360dp, API 34 · 411dp)

**(a) Refactor'ün davranışı değiştirmediği** yukarıda, koordinatlarla.

**(b) Karta dokun → alanlar dolu.** Ham metin (API 29):

```
EditText text='Netflix' · EditText text='159.99'
checked=true ['TRY'] · checked=true ['Monthly'] · checked=true ['Entertainment']
Next Payment (optional), Sep 9, 2026
```

**(c) ÇIPA — bu maddenin kanıtı.** Geçmiş çıpalı abonelik, iki emülatörde de:

| | API 29 | API 34 |
|---|---|---|
| Kart | `Netflix, TRY 159.99, Monthly, 25 days left, Entertainment` | aynı |
| Düzenleme ekranı | `Next Payment (optional), Sep 9, 2026` | `… Sep 10, 2026` |

Kart ilerletilmiş tarihe (Ekim) sayıyor, ekran kullanıcının girdiği Eylül gününü
açıyor.

**(d) Alanı değiştir, kaydet.** 159,99 → 200,00: ana ekran
`Netflix, TRY 200.00`, toplam `TRY 259.90`, ve satır **yerinde** kaldı
(API 34'te hâlâ `[0,1311]`).

**(e) Değişiklik yapmadan geri.** Liste aynı, saklanan çıpa aynı
(`stored=1788912000000 2026-09-09`).

**(f) Alanı değiştirip geri.** Form `NetflixPremium` tutuyorken geri → liste
hâlâ `Netflix`. Onay sorulmadı, hiçbir şey yazılmadı. İki emülatörde de.

**(g) Kaydırarak silme — bu fazın en riskli maddesi.** Kaydırma yönü bileşenin
**bitiş kenarına** (LTR'de sola). İki emülatörde de aynı:

| madde | satır sayısı | düzenleme ekranı açıldı mı |
|---|---|---|
| #6 hafif kaydırma | 2 | **hayır** |
| #7 on kez hafif | 2 | **hayır** |
| #8 hızlı fiske | 2 | **hayır** |
| #10 ters yön | 2 | **hayır** |
| düz dokunma | 2 | **evet**, alanlar dolu |
| #9 tam kaydırma | 1 + Undo | — |

**(h) Düzenleme sonrası snapshot.** İki emülatörde de tek satır:
`(202609, 25990, 'TRY')` — güncelleme kaydediciye kendiliğinden ulaştı.

**(i) Bildirim.** Enstrümantasyonla, iki emülatörde de:

```
before the edit: state=SUCCEEDED, shown=[]
moved the date of id=1 to 2026-09-15
after the edit: state=SUCCEEDED, shown=[Payment reminder: 1 subscription
EditedRow — today]
```

**(j) Döndürme.** Düzenlenen fiyat (`200.00`) ve çıpa tarihi döndürmeden sağ
çıktı, iki emülatörde de.

**(k) Bulunamayan id.** Düzenleme ekranı açıkken uygulama arka plana alındı,
süreç `am kill` ile öldürüldü, satır silindi, uygulama geri getirildi — yani
gerçek süreç ölümü yolu. İki emülatörde de ham metin:

```
This subscription is gone, It may have been deleted. Go back and check the list.
```

**(l) Erişilebilirlik.** Kart hâlâ **tek düğüm**, artık tıklanabilir:

```
class='android.view.View'
content-desc='Netflix, TRY 159.99, Monthly, 25 days left, Entertainment'
clickable='true'  long-clickable='false'  bounds='[0,950][720,1180]'
nodes in region: 1
```

**Bilinen ölçüm sınırı:** `uiautomator dump` özel erişilebilirlik eylemlerini
(custom action) hiç yazmıyor — "Sil" eylemi bu dökümde görünmez, tıklama ise
`clickable='true'` olarak görünür. Aynı sınır Faz 1a'dan beri geçerli; "Sil"
oradan beri TalkBack'le elle doğrulanıyor.

### 105 maddelik sabit regresyon

Liste 95 → **105** madde (Faz 15'in on maddesi). İki emülatörde de koşuldu.

**Kayıtlı istisnalar** (öncekilerle aynı): **API 29** — #15 koyu tema
(`cmd uimode night` bu imajda tutmuyor), #34-#37 ve #41-#45 API 33+ maddeleri.
**API 34** — #40 ve #46 API < 33 maddeleri. **Her ikisi** — #39, kanal
listelenmediği için hâlâ **doğrulanamıyor** (13b'de açılan istisna).

`ui/home/` ve ekleme formu değiştiği için 1-19 ve 51-68 özellikle baştan
koşuldu; hepsi geçti.

### Değişen dosyalar

- `domain/usecase/SubscriptionInput.kt` — yeni; kurallar ve iki tavan
- `ui/common/FormErrors.kt` — yeni; sebep → cümle
- `ui/common/SubscriptionFormState.kt`, `SubscriptionFormFields.kt` — yeni
- `ui/home/components/AddSubscriptionSheet.kt` — ortak alanları kullanıyor
- `ui/home/HomeViewModel.kt` — kural kopyaları çıktı
- `ui/home/components/SwipeToDeleteRow.kt` — `onClick` + semantics eylemi
- `ui/home/HomeScreen.kt`, `HomeScreenPreviews.kt` — `onEditSubscription`
- `ui/edit/` — `EditSubscriptionScreen`, `EditSubscriptionUiState`,
  `EditSubscriptionViewModel` (yeni)
- `ui/navigation/Destination.kt`, `SubTrackNavHost.kt` — dördüncü hedef
- `res/values/strings.xml`, `res/values-en/strings.xml`
- `test/.../SubscriptionInputTest.kt`, `test/.../EditSubscriptionViewModelTest.kt`
- `test/.../fake/FakeSubscriptionRepository.kt` — `updated`, `failOnWrite`
- `androidTest/.../edit/EditedDateReminderTest.kt` — yeni
- `docs/ARCHITECTURE.md` §13 ve yeni §22; `docs/ROADMAP.md`, `docs/TESTING.md`

**Dokunulmayanlar:** `reminder/` (yalnızca okundu), `MonthlySnapshotRecorder`,
`PaymentCountdown`, `NextPaymentDate`, `PaymentReminderSelection`, Room
entity/dao/database, `app/schemas/`, `ui/statistics/`, `ui/settings/`, kur
ekranı, `AndroidManifest.xml`, `Theme.kt`, `Color.kt`.

### Karşılaşılan sorunlar

- **`listSaver` null kabul etmiyor** (`Saveable : Any`). Tarihsiz bir form altı
  string olarak saklanıyor; boş string "tarih yok" demek.
- **API 34 emülatörü iki kez tıkandı** ("System UI isn't responding", "Process
  system isn't responding"); biri `adb reboot` ile, diğeri force-stop ile
  geçildi. Aynı build API 29'da akıcı; emülatör yükü, üründe karşılığı yok.
- **Fikstür, uygulama bir kez açılmadan çalışmıyor:** `pm clear` veritabanı
  dosyasını da siliyor, `run-as cat` boş dosya veriyor ve sqlite "file is not a
  database" diyor. Sıra: `pm clear` → uygulamayı bir kez aç → fikstür.
- **`input text` sonrası sheet kısalıyor** (IME penceresi), Kaydet dökümde
  görünmüyor. Küçük adımlı `input swipe` ile içerik kaydırılıyor; `ESCAPE`
  sheet'i kapatıyor, kullanılmamalı.

### Commit'ler

- `c564bae` refactor: give the six fields and the rules behind them one home each
- `25c281f` feat: a screen for changing a subscription that is already stored
- `d3bf127` feat: give the graph a fourth destination, and its first argument
- `dce5433` feat: open a subscription by tapping its row
- `8bc630a` test: pin the edit screen's refusals and the rules both forms now share
- `9d8ed72` test: run the claim that an edited date reaches the reminder

### Sonraki faz için not

- #39 hâlâ doğrulanmamış; bildirim gönderen bir enstrümantasyon testi yazılırsa
  aynı turda kapatılabilir.
- Faz 14 (tema) ertelenmiş durumda: §12'nin tanımsız rol borcu ve 13b'de ölçülen
  iz saydamlığı tavanı orada bekliyor.

---

## [Faz 13b] Aylık Trend ve "Geçen Aya Göre" — 2026-09-14

**Durum:** Tamamlandı. **Faz 13 KAPANDI.** Snapshot tablosuna yalnızca okuma
yapıldı; yazma tarafı (recorder, upsert, tetikleyici) 12a'da kapanmıştı ve bu
fazda **dokunulmadı**.

### Okuma tarafı — `domain/usecase/MonthlyTrend`

Saf fonksiyonlar; repository, saat, Android yok. Bugünün ayı parametre olarak
geliyor (`YearMonth.now(clock)` ViewModel'de, §17). DAO'ya **yeni sorgu
eklenmedi** — 12a'daki `observeAll()` kullanıldı; pencere, delik doldurma ve
para birimi süzmesi domain'de yapılıyor.

### Para birimi tuzağı — seçim ve gerekçe

Satırlar yazıldıkları andaki para biriminde duruyor (§19). Seçilen yol:
**bugünkü ana para birimindeki ayları çiz, ötekileri çevirme, say ve söyle.**

- **Bugünkü kurla çevirmek** geçmişi hiç doğru olmamış bir sayıyla yeniden
  yazmak olurdu; kurlar elle giriliyor ve düzenlenebiliyor (§15), yani her kur
  düzenlemesi geçmişi sessizce yeniden çizerdi. Tarihsel kur saklamıyoruz.
- **Hepsini aynı eksene koymak** elmayla armut toplamaktı: kullanıcı yalnızca
  bir ayar değiştirmişken grafik uçurum gösterirdi.

**Kullanıcıya görünüşü:** grafik kısalıyor ve altında bir cümle çıkıyor —
"Başka para biriminde kaydedilen 2 ay gösterilmiyor". Aynı kural karşılaştırmaya
da uyuyor: geçen ay başka birimdeyse çıkarma yapılmaz, karşılaştırma **hiç**
gösterilmez. Gerekçenin tamamı `ARCHITECTURE.md` §21'de.

### Pencere: altı ay — ölçüldü, tahmin edilmedi

360dp'de paddingler sonrası 328dp kalıyor, altı sütuna 54dp düşüyor. Cihazda
ölçülen, komşu ay etiketleri arasındaki **en dar** boşluk:

| ekran | fs 1.0 | fs 2.0 |
|---|---|---|
| 360dp (API 29) | 68px ≈ **34dp** | 23px ≈ **11,5dp** |
| 411dp (API 34) | 111px ≈ **42dp** | 53px ≈ **20dp** |

Hiçbirinde üst üste binme veya kırpılma yok. On iki ay bu payı yarıya indirirdi.

### Delikler ve sıfırlar — 12a'nın ödediği ayrım

Kaydı olmayan ay **yuvasını koruyor**, değeri boş. Atlamak iki aylık tırmanışı
bir aylık gibi gösterirdi. Sütun grafiği seçilmesinin asıl sebebi bu üç hâl:
değeri olan ay (iz + dolu), **sıfır kaydedilen** ay (yalnızca iz), **kaydı
olmayan** ay (hiçbir şey). Çizgi grafiği bunu yapamazdı: delikte kopuk çizgi
hata gibi okunur, düz çizgi ise kimsenin kaydetmediği bir sayıyı çizer.

### Karşılaştırma ana ekranda değil (ROADMAP maddesi taşındı)

Dashboard kartı 8a'da `clearAndSetSemantics` ile tek odak durağı ve tek cümle
hâline getirildi; içine ikinci bir değer koymak onu bozardı. Karşılaştırmanın
çalışması (grafik) zaten istatistik ekranında. ROADMAP bu gerekçeyle güncellendi.

Yön **renkle değil sözcükle**: `error` şemada tanımsız (§12), yeşil palette hiç
yok, ve renk tek başına ayırt edemeyen okuyucuya bir şey söylemez. Ok ile metin
aynı renkte; ok dekoratif. Değişim yoksa ok da yok. **Önceki ay yoksa hiçbir şey
yok** — yer tutucu da yok (10a kuralı).

### Testler

235 → **268 birim testi** (33 yeni: 20 `MonthlyTrendTest`, 13
`StatisticsViewModelTest`), 0 hata. `lintDebug` **0 hata, 24 uyarı** — sayı
13a'daki ile aynı, bu fazdan **yeni uyarı çıkmadı**.

Kapsanan hâller: sıfır ay · tek ay · iki ay · çok ay (pencere kırpması) ·
aralarda boşluk · sıfır kaydedilmiş ay · gelecekteki ay · karışık para birimi
(üç varyant) · artış · azalış · değişim yok · önceki ay yok · geçen ay başka
birimde · abonelik yokken geçmişi olan ekran.

### Cihaz doğrulaması — iki emülatörde (API 29 · 360dp, API 34 · 411dp)

Çok aylık veri `run-as` fikstürüyle kuruldu: uygulama durduruldu, `subtrack.db`
dışarı alındı, host'ta `monthly_snapshots` satırları yazıldı, geri kondu, WAL
silindi. **Cihazda `sqlite3` yok** (iki imajda da), bu yüzden düzenleme host'ta.
Üretim koduna test kancası açılmadı. Yöntem `TESTING.md`'ye yazıldı.

**(a) Hiç veri yok.** Ham metin (iki emülatörde de):
`Nothing to chart yet, Add a subscription and the breakdown appears here`.
Trend bölümü **hiç çıkmıyor** — iki şey birden söylemesin diye.
**Bulgu:** "tabloda sıfır satır" hâli çalışırken görülemiyor; kaydedici açılışta
o ayı yazıyor (§19). Temiz kurulumda tabloda `(202609, 0, TRY)` var.

**(b) Tek ay.** Ham metin: `A trend needs at least two months. Each month's
total is recorded as you go, and the chart appears once the second month is in.`
Grafik yok, karşılaştırma yok.

**(c) İki ay.** API 29, Ağustos 200,00 → Eylül 250,00:
`TRY 50.00 more than last month` · grafik düğümü `[32,867][688,1203]`.
Sütunlar (piksel): `156-235` ve `484-563`, ikisi de **80px = 40dp** (tavan).
Etiketler `Aug 175-216`, `Sep 504-543`.

**(d) Altı ay.** API 29: sütunlar `54-118 · 164-227 · 273-337 · 382-446 ·
492-555 · 601-665` (her biri 64-65px = 32,5dp). Etiket boşlukları
`70 · 68 · 76 · 74 · 69` px. API 34: sütunlar 98px, etiket boşlukları 111-121px.
Yedi ay kayıtlıyken en eskisi (Mart) çizilmedi — pencere son altı ay.

**(e) Boşluklu veri.** Nisan, Mayıs, **Temmuz**, Eylül kayıtlı; Haziran ve
Ağustos yok. Sütunlar yalnızca 1., 2., 4. ve 6. yuvalarda; 3. ve 5. yuvada
**hiçbir şey**, ama etiketleri yerinde. Sesli okunuş:
`Monthly trend: April TRY 1,200.00, May TRY 1,450.00, June no record,
July TRY 2,100.00, August no record, September TRY 250.00`.
Ağustos'un kaydı olmadığı için karşılaştırma da **hiç** çıkmadı.

**Sıfır ↔ kayıt yok (API 34, ekran görüntüsüyle):** Mayıs `0` kaydedilmiş →
yalnızca iz; Haziran kaydı yok → hiçbir şey. Okunuşu
`May TRY 0.00, June no record`.

**(f) Karşılaştırmanın üç hâli**, ham metinle:

| hâl | ham metin |
|---|---|
| artış | `TRY 50.00 more than last month` (yukarı ok) |
| azalış | `TRY 150.00 less than last month` (aşağı ok) |
| değişim yok | `Unchanged from last month` (**ok yok**, metin x=32'den başlıyor) |
| önceki ay yok | satır **hiç yok** — grafik yukarı kayıyor |

**(g) Karışık para birimi — bu maddenin kanıtı.** API 29'da gerçek yol:
ayarlardan ana para birimi USD yapıldı. Tablo:

```
(202607, 210000, 'TRY')   <- eski aylar kendi biriminde kaldı
(202608,  40000, 'TRY')
(202609,    583, 'USD')   <- kaydedici içinde bulunulan ayı yeni birimle yazdı
```

Ekranda: grafik **çizilmedi** (yeni birimde tek ay var), yerine "veri toplanıyor"
cümlesi, altında `2 months recorded in another currency are not shown`.
İkinci varyant (iki USD ayı + bir TRY ayı): grafik çizildi, altında tekil biçim
`1 month recorded in another currency is not shown`. API 34'te iki EUR ayı ile
tekrarlandı: `2 months … are not shown`.

**(h) fs 2.0.** İki emülatörde de kırpılma yok; altı etiket de yerinde
(yukarıdaki tablo). Karşılaştırma cümlesi iki satıra sarıyor, ok dikeyde
ortalanıyor. Grafik ve etiketler ekran içinde.

**(i) Koyu tema (API 34).** 13a'nın tuzağına düşülmedi: sütun `(174,198,207)`,
iz `(63,71,76)`, arka plan `(28,32,34)` — üçü de ayrı. Ekran görüntüsüyle
kontrol edildi; sıfır kaydedilmiş ay ile kaydı olmayan ay koyu temada da ayrı
görünüyor.

**Ölçülen kontrast:** sütun/arka plan **3,96:1** (açık), **9,25:1** (koyu);
sütun/kendi izi **3,01:1** (açık), **5,31:1** (koyu). Sonuncusu grafik nesnesi
için istenen 3:1'in tam üstünde, yani **iz daha koyu yapılamaz**. İz/arka plan
1,32:1 kalıyor: "sıfır" ile "kayıt yok" farkını gözle ayırmak zayıf, ayrımı
**cümle** taşıyor. Faz 14'e madde olarak yazıldı.

**(j) Erişilebilirlik.** İki emülatörde de:

```
nodes in the chart subtree: 1
   class=android.view.View  desc='Monthly trend: April TRY 1,200.00, May TRY 1,450.00,
   June TRY 980.00, July TRY 2,100.00, August TRY 200.00, September TRY 250.00'
nodes in the comparison subtree: 1
   class=android.widget.TextView  text='TRY 50.00 more than last month'
```

Ok `contentDescription = null` olduğu için ağaca düğüm eklemiyor.

**Türkçe (API 34, `cmd locale set-app-locales`; ayrıca `wm density 480` ile
360dp'ye indirilerek):** `Aylık Trend` · `Geçen aya göre ₺5.948,00 azaldı` ·
`en yüksek ₺8.000,00` · etiketler `Nis May Haz Tem Ağu Eyl` · okunuş
`Aylık trend: Nisan ₺6.000,00, Mayıs ₺5.200,00, Haziran ₺0,00, Temmuz kayıt yok,
Ağustos ₺8.000,00, Eylül ₺2.052,00`. Uzun cümle 360dp'de üç satıra sarıyor,
kırpılma yok: `Trend için en az iki ay gerekiyor. Her ayın toplamı kaydediliyor;
ikinci ay dolduğunda grafik burada çıkar.` Ay adları `MonthFormatter` ile
locale'den geliyor.

### Cihazın söylediği, akıl yürütmenin söylemediği kusur

**İki aylık grafik iki panel çiziyordu.** Sütun genişliği yuvanın payı olarak
hesaplanıyordu; yuva = genişlik / ay sayısı, yani iki ay **98dp**lik iki blok
demekti. Dar emülatörde ölçüldü (`98-293` ve `426-621`, 196px). Genişliğe tavan
kondu (`TrendBarMaxWidth = 40dp`); altı ay 33dp ve 37dp olduğu için dolu pencere
etkilenmedi. Düzeltme sonrası iki sütun 80px = 40dp.

### 95 maddelik sabit regresyon

Liste 85 → **95** madde (13b'nin on maddesi). İki emülatörde de koşuldu.

**Kayıtlı istisnalar** (önceki fazlarla aynı): **API 29** — #15 koyu tema ve
#16 dil: `cmd uimode night yes` bu imajda tutmuyor (`settings put secure
ui_night_mode 2` de işe yaramadı), `cmd locale` yok; #34-#37, #41-#45 API 33+
maddeleri. **API 34** — #40 ve #46 API < 33 maddeleri.

**Bu turda eklenen istisna:** **#39 (yalnızca kanalı kapat)** iki emülatörde de
**koşulamadı.** Sistem bildirim ayarları uygulama hiç bildirim göndermeden
kanalı listelemiyor ("This app has not posted any notifications"), kanalı adb
ile kapatmanın da yolu yok. Bildirim göndermek worker'ı koşturmayı gerektiriyor;
o da `TESTING.md`'deki enstrümantasyon yolu. Madde **doğrulanmadı**, atlanmadı.

**Sürücü hatası, uygulama hatası değil:** #6-#10 ilk turda yanlış yönde
kaydırılarak koşuldu (bileşen **bitiş kenarına**, yani LTR'de sola kaydırıyor).
Yanlış yön #10'un ta kendisi olduğu için hepsi "silmiyor" diyordu. Doğru yönle
tekrarlandı: #6, #7, #8 silmiyor; #9 siliyor.

### Değişen dosyalar

- `domain/usecase/MonthlyTrend.kt` — yeni; `TrendPoint`, `TrendDirection`,
  `MonthlyChange`, `MonthlyTrendSeries`, `series`/`changeSince`/`peak`
- `ui/statistics/StatisticsUiState.kt` — dört yeni alan + `canDrawTrend`,
  `hasNothingToShow`; `Event` tipi **yok** (13a gerekçesi geçerli)
- `ui/statistics/StatisticsViewModel.kt` — dördüncü Flow (snapshot) ve `Clock`
- `ui/statistics/StatisticsScreen.kt` — trend bölümü, `TrendNotice`
- `ui/statistics/components/MonthlyTrendChart.kt` — yeni
- `ui/statistics/components/MonthlyChangeRow.kt` — yeni
- `ui/common/MonthFormatter.kt` — yeni (locale'den ay adı)
- `ui/theme/Dimens.kt` — trend ölçüleri
- `res/values/strings.xml`, `res/values-en/strings.xml`
- `test/.../domain/usecase/MonthlyTrendTest.kt` — yeni
- `test/.../ui/statistics/StatisticsViewModelTest.kt` — genişletildi
- `docs/ARCHITECTURE.md` yeni §21; `docs/ROADMAP.md` (Faz 13 kapandı),
  `docs/TESTING.md` (86-95 ve fikstür yöntemi)

**Dokunulmayanlar:** `MonthlySnapshotRecorder`, snapshot DAO'nun yazma tarafı,
Room entity/database, `app/schemas/`, `reminder/`, `PaymentCountdown`,
`NextPaymentDate`, `PaymentReminderSelection`, `ui/home/` (DashboardCard dahil),
`ui/settings/`, kur ekranı, `AndroidManifest.xml`, `Theme.kt`, `Color.kt`.

### Karşılaşılan sorunlar

- **Cihazda `sqlite3` yok.** İki imajda da `/system/bin/sqlite3` bulunmuyor;
  fikstür host'ta düzenlenip geri kondu. WAL dosyası ayrıca katlanmalı, yoksa
  geri konan ana dosya bayat kalıyor.
- **`adb shell input swipe` yön hatası** yukarıda anlatıldı; ders: bileşenin
  hangi yöne kaydırdığını kaynaktan doğrula, "silmiyor" sonucunu başarı sanma.
- **API 34 emülatörü ağır yüklendi** (iki emülatör + Play Store güncellemeleri);
  iki kez "isn't responding" diyaloğu çıktı, beklendi ve geçti. Üründe karşılığı
  yok, ölçümleri etkilemedi.
- `uiautomator dump` çıktısı Git Bash'te yol dönüşümüne uğruyor
  (`/sdcard/ui.xml` → Windows yolu); `MSYS_NO_PATHCONV=1` gerekiyor.

### Commit'ler

- `9d58192` feat: read the recorded months as a trend and as a difference from last month
- `fbd6b3a` feat: draw the months as columns, and say in words when there are not enough
- `ccf166f` feat: say how this month compares with last month, in words rather than colour
- `24b40af` fix: stop a two-month chart from drawing two panels

### Sonraki faz için not

- **Faz 14:** iz saydamlığının tavanı ölçüldü (sütun-iz 3,01:1). "Sıfır
  kaydedilmiş ay" ile "kaydı olmayan ay" ayrımı gözle zayıf; palet elden
  geçerken çözülmeli. Kategori başına renk maddesi de orada.
- **#39** hâlâ doğrulanmamış; bildirim gönderen bir enstrümantasyon testi
  yazılırsa aynı turda kapatılabilir.

---

## [Faz 13a] İstatistik Ekranı: Kategori Dağılımı ve En Pahalı Abonelikler — 2026-09-14

**Durum:** Tamamlandı. **Faz 13 AÇIK** — aylık trend ve "geçen aya göre"
karşılaştırması 13b'nin işi. Snapshot tablosuna dokunulmadı.

### Ekran ve giriş noktası

Üçüncü hedef (`Destination.STATISTICS`, düz `String` sabiti — §13). Giriş ana
ekranın üst çubuğundan, **ayarların solundan**: ayarlar Faz 9'dan beri en sağda
ve kullanıcının bildiği hedef yerinden oynatılmadı.

**İkon:** mevcut dokuz isim (PlayArrow, List, ArrowBack, Star, Settings, Delete,
DateRange, Cloud, Add) arasında "istatistik" diye okunan yok — `DateRange` tarih,
`List` liste demek. **`Icons.Default.BarChart`** eklendi; onuncu isim ve ekranın
çizdiği şeyin tam karşılığı. Boş durum da aynı ikonu kullanıyor, on birinciye
gerek kalmadı (Faz 16 ikon daraltmasında sayı önemli).

`hiltViewModel()` yalnızca `composable` bloğunda; ekran durumsuz ve Hilt grafı
olmadan preview ediliyor.

### `Event` tipi yok — bilinçli bir sapma

§5 her ekran için tek `UiState` **ve** tek `onEvent` istiyor. Bu ekranda
yapılacak hiçbir şey yok; boş bir `sealed interface` ve dalsız bir `onEvent`
mimari değil merasim olurdu. §5'e kuralın gerekçesini koruyan bir cümle eklendi
(gerekçe altı lambda yerine tek giriş noktası; lambda yoksa kural da boşta).

### Hesaplar — `domain/usecase/SubscriptionStatistics`

Saf fonksiyonlar; repository, saat, Android yok. Para aritmetiğinin tamamı
**mevcut `CurrencyConverter` zinciri** (12-1) — ikinci bir toplam yazılmadı.
Figürlerin hepsi **aylık**.

- **Kategori dağılımı:** her kategori bir **grup** olarak toplanıyor, satır satır
  değil; her figür bir kez yuvarlanıyor (dashboard toplamıyla aynı kural).
- **En pahalı:** **beş** satır. Liste "büyükler hangileri" sorusunun cevabı;
  360dp'de dağılımın altına sığıyor ve ana ekranın ikinci kopyasına dönüşmüyor.
  Sıralama **aylık maliyete** göre — yıllık 1.200,00 ile aylık 100,00 aynı şeye
  mal olur. Uygulamadaki **satır başına çevrilmiş tek figür**; birbirleriyle
  toplanmadıkları için satır başına yuvarlama kendisiyle çelişen bir toplam
  üretemez.
- **Oranlar:** **en büyük kalan yöntemi**. Her payı tek başına yuvarlamak üç eşit
  üçte biri 33+33+33=99 yapıyor. Tamsayı aritmetiği, `Double` yok (§6).
- **Sıfıra bölme:** toplam sıfırsa hiçbir bölme yapılmaz, hepsi sıfır. Testle
  kapalı.

### Çizim — Compose Canvas, yatay çubuk, tek renk

Grafik kütüphanesi **eklenmedi** (`PROJECT_SPEC.md` §5). Pasta değil çubuk:
360dp'de elle çizilmiş bir pastanın dört etiketi üst üste biner, ve bir çubuğun
uzunluğu **yazılabilir** bir yüzdedir — ekran okuyucu için belirleyici oldu.

Kategori başına renk yok: paletimizde dört ayırt edilebilir **tanımlı** rol yok
ve şimdi icat etmek Faz 14'ün geri alacağı borç olurdu. Ayrımı etiket taşıyor.

**Oran gösterimi hem tutar hem yüzde.** Tutar "ne kadar", yüzde "bunun ne
kadarı" sorusunun cevabı; çubuk ikincisini çiziyor, birincisini hiçbir şey
çizmiyor.

**Sıfır tutarlı kategori satırı yok.** Dört kategori sabit bir sözlük, sabit bir
cevap listesi değil; "Sağlık, 0,00, %0" hiçliğin çubuğunu çizer ve ekran
okuyucuya boş bir durak verir. Faz 11a'da `OTHER`'ı kartlardan uzak tutan
gerekçenin aynısı.

### Erişilebilirlik

`Canvas` erişilebilirlik ağacında **yok**. Her satır `clearAndSetSemantics` ile
tek bir odak durağı ve tek bir cümle. Ölçüldü, iki emülatörde de:

```
nodes in the row subtree: 1 (the row itself included)
   class=android.view.View  desc='Health, TRY 2,002.00, 75 percent'  text=''
nodes in one "most expensive" row: 1
   class=android.view.View  desc='Health1, Weekly, TRY 2,002.00 a month'
```

### Testler

223 → **235 birim testi** (12 yeni), 0 hata. `lintDebug` **0 hata, 24 uyarı** —
yeni olan tek uyarı `statistics_category_description`'daki `PluralsCandidate`
("%1$d percent"); İngilizcede "percent" çekimlenmiyor, yani çoğul biçim aynı
metni ikinci kez yazmak olurdu. Aynı türden iki uyarı zaten listede
(`error_date_too_far`).

### Cihaz doğrulaması — iki emülatörde birebir aynı (API 29 · API 34)

Fikstür: dört kategori, üç para birimi, üç periyot. Elle hesap:

| kategori | içerik | elle | ekranda |
|---|---|---|---|
| Health | 10,00 EUR haftalık → ×52/12 ×46,20 | **2.002,00 TL** | `TRY 2,002.00, 75 percent` |
| Productivity | 10,00 USD aylık ×42,85 | **428,50 TL** | `TRY 428.50, 16 percent` |
| Entertainment | 100,00 aylık + 1.200,00 yıllık | **200,00 TL** | `TRY 200.00, 7 percent` |
| Other | 50,00 + 1,00 aylık | **51,00 TL** | `TRY 51.00, 2 percent` |

**(c) 75 + 16 + 7 + 2 = 100.** Elle hesapta da tabanlar 74+15+7+1 = 97 ediyor ve
artan üç puan en çok kırpılanlara (Prod, Other, Health) gidiyor.

**(d)** En pahalı, ham metinle: `Health1 Weekly 2.002,00` · `Prod1 Monthly
428,50` · `Ent1 Monthly 100,00` · `Ent2 Yearly 100,00` · `Other1 Monthly 50,00`.
Altıncı abonelik ("Small", 1,00) **kesildi**. Eşitlikte ada göre sıra (Ent1 <
Ent2) tutuyor.

**(e) filtre tuzağı — bu maddenin kanıtı:**

| | ekran |
|---|---|
| Ana ekran, Health filtresi açık | `Total Monthly, TRY 2,002.00` — tek satır |
| Aynı anda istatistik ekranı | dört kategori, toplamı **2.681,50** |

**(a)** İkon ekranı açıyor; geri oku ve sistem geri tuşu ikisi de ana ekrana
dönüyor, bir kez daha geri uygulamadan çıkarıyor — yığında birikme yok.

**(f)** Boş durum ham metni: `Nothing to chart yet, Add a subscription and the
breakdown appears here` (tek düğüm).

### Cihazın söylediği, akıl yürütmenin söylemediği iki kusur

1. **Koyu temada her çubuk dolu görünüyordu.** İz için `primaryContainer`
   seçilmişti; koyu şemada `primary` ve `primaryContainer` **ikisi de
   PastelBlue**, yani çubuk ile izi aynı renkti. Ekran görüntüsüyle görüldü. İz
   artık çubuğun kendi renginin saydamlaştırılmış hâli
   (`primary.copy(alpha = 0.24f)`): iki temada da dolu kısma karışamaz, yeni
   renk icat etmez. Düzeltmeden sonra %75 · %16 · %7 · %2 bakışta ayrılıyor.
2. **fs 2.0'da etiket tutara yapışıyordu** — `SpaceBetween`'in dağıtacak boşluğu
   kalmıyor ve "ProductivityTRY 428,50" tek kelime gibi okunuyordu. Etiket artık
   kalan genişliği alıp kendi içinde sarıyor, araya boşluk konuyor.

**(g)** fs 2.0'da kırpılma yok, iki emülatörde de her satır ekran içinde
(API 34: `[42,…][1038,…]`, ekran 1080; API 29: `[32,…][688,…]`, ekran 720).
**Bilinen sınır:** etiket kendi payına sığmadığında kelime ortasından bölünüyor
("Productivit / y"). Üst üste binme veya kırpılma değil; §20'de yazılı.

**(h)** Koyu tema (API 34): düzeltmeden sonra çubuklar ayrışıyor, metinler
okunur.

### 85 maddelik sabit regresyon

Liste 75 → **85** madde (13a'nın on maddesi eklendi). İki emülatörde de koşuldu.
Kayıtlı istisnalar önceki fazlarla aynı (**API 29**: #15 koyu tema ve #16 dil —
`cmd uimode`/`cmd locale` bu imajda yok; #34-#37, #41-#45 API 33+ maddeleri.
**API 34**: #40 ve #46 API < 33 maddeleri).

Ana ekranın üst çubuğu değiştiği için 1-19 özellikle bakıldı; hepsi geçti.

### Değişen dosyalar

- `ui/statistics/` — `StatisticsScreen`, `StatisticsUiState`, `StatisticsViewModel`
- `ui/statistics/components/` — `CategoryBarRow`, `ExpensiveSubscriptionRow`
- `domain/usecase/SubscriptionStatistics.kt` — yeni
- `ui/navigation/Destination.kt`, `SubTrackNavHost.kt` — üçüncü hedef
- `ui/home/HomeScreen.kt` — yalnızca üst çubuğa ikon; `HomeScreenPreviews.kt`
- `ui/common/EmptyState.kt` — `EmptyStatistics` (imza değişmedi)
- `ui/theme/Dimens.kt` — çubuk ölçüleri
- `res/values/strings.xml`, `res/values-en/strings.xml`
- `test/.../StatisticsViewModelTest.kt` — yeni
- `docs/ARCHITECTURE.md` §5, §13 ve yeni §20; `docs/ROADMAP.md`, `docs/TESTING.md`

**Dokunulmayanlar:** `reminder/`, snapshot tarafı, `PaymentCountdown`,
`NextPaymentDate`, `PaymentReminderSelection`, Room entity/dao/database,
`app/schemas/`, `ui/settings/`, kur ekranı, `DashboardCard`, `SubscriptionCard`,
`AndroidManifest.xml`, `Theme.kt`, `Color.kt`.

### Karşılaşılan sorunlar

- **Yüklü emülatörde sheet bir turda açılmıyor.** Sürücü artık sheet'in zaten
  açık olup olmadığına bakıyor (açıkken FAB'a basmak sheet'e basmak demek) ve
  açılmasını 15 saniyeye kadar bekliyor.
- **Cihaz dili Türkçe'ye alınınca** FAB'ın açıklaması da Türkçe; sürücü artık
  iki dili de tanıyor.
- API 34 emülatörü tekrar tıkandı, `adb reboot` ile tazelendi — üründe karşılığı
  yok.

### Commit'ler

- `18551db` feat: add a statistics screen and a way into it
- `1f19024` feat: work out where the money goes and which subscriptions are the big ones
- `c7e94ad` feat: draw the breakdown as bars that a screen reader can also read
- `aef9408` feat: list the dearest subscriptions by what they cost a month
- `4816318` fix: two things the emulator said about the chart that reasoning did not
- `06606d7` docs: record why the chart is hand-drawn, one colour, and readable aloud

### Sonraki faz için not

- **13b:** aylık trend + "geçen aya göre". `monthly_snapshots` hazır; "o ay
  abonelik yoktu" ile "o ay kayıt yok" ayrımı çizilmeli (§19).
- **Faz 14:** kategori başına renklendirme yeniden değerlendirilsin; koyu şemada
  `primary` ile `primaryContainer`'ın aynı renk olması orada çözülmeli.

---

## [Faz 12a] Geçmiş Takibi — Aylık Anlık Görüntüler — 2026-09-14

**Durum:** Tamamlandı. Projenin Faz 2'den beri **ilk şema değişikliği**.
Bu faz **yalnızca veri katmanı** — ekranda hiçbir şey değişmedi.

### Şema: sürüm 1 yeniden üretildi, migration yok

`monthly_snapshots` tablosu sürüm 1'e eklendi. Migration yazılmadı, sürüm
numarası 1 kaldı, `app/schemas/.../1.json` yeniden üretilip commit'e dahil
edildi. Diff'te değişen tek şey `identityHash` ve eklenen tablo;
`subscriptions` tanımına dokunulmadı.

```
CREATE TABLE IF NOT EXISTS `monthly_snapshots` (
  `period` INTEGER NOT NULL, `totalInCents` INTEGER NOT NULL,
  `currencyCode` TEXT NOT NULL, `recordedAt` INTEGER NOT NULL,
  PRIMARY KEY(`period`))
```

Kural (`ARCHITECTURE` *"Şema sürümlemesi"*) **Faz 16'da bitiyor**; o satır
silinmedi, yanına bu fazın uygulandığı not düşüldü.

### Dönem gösterimi: tek `Int`, `yıl * 100 + ay`

Eylül 2026 → `202609`. Seçildi çünkü: **sıralanabilir** (ay iki basamak olduğu
için sayısal sıra takvim sırası), **tekil** (bir değer tek bir ay → doğal
birincil anahtar, upsert ikinci satır açamaz), **zaman dilimsiz** (ay bir
takvim olgusu, an değil; epoch sınırdakilere yanlış ayı verirdi) ve `run-as`
dökümünde **okunabilir** — bu tablonun cihazda doğrulanma biçimi tam olarak bu.
İki sütun bileşik anahtar ve iki sütunlu sıralama isterdi, karşılığında hiçbir
şey kazandırmadan.

Domain tarafı `java.time.YearMonth`. Dönemi çözemeyen bir satır **atlanır** —
uydurma bir ay, grafiğe sahte bir nokta koymaktır.

### Para birimi neden satırda

Kullanıcı ana para birimini değiştirebiliyor; geçen ayın satırı figürünün hangi
birimde olduğunu kendisi taşımalı, yoksa sonraki okuyucu bugünkü tercihle tahmin
eder. Faz 13 bu sütunu okuyacak.

### Upsert: `@Upsert`, `OnConflictStrategy.REPLACE` değil

`REPLACE` çakışmayı **satırı silip yenisini ekleyerek** çözer: silme
tetikleyicilerini ateşler, bağlı satırları götürür. `@Upsert` ekler, anahtar
çakışırsa **yerinde günceller** — satır kimliği korunur, ki "aynı ay, revize
edildi" zaten budur. Enstrümantasyonla sabitlendi.

### Yazma tetikleyicisi: `domain/usecase/MonthlySnapshotRecorder`

**Neden ViewModel değil.** Promptun DOKUNMA listesi `ui/` altındaki hiçbir
dosyaya izin vermiyordu, ama karar zaten bağımsız olarak aynı yere çıkıyordu:
toplam bir ekranın değil saklanan verinin özelliği, ve değişiklik listeden de
gelebilir ayarlardaki para birimi seçicisinden de kur ekranından da.

**Filtre tuzağı — asıl gerekçe.** `HomeUiState.total` kategori filtresini
izliyor, yani filtre açıkken **filtrelenmiş** figür. Oradan kaydetmek yanlış
sayıyı yazmak olurdu. Recorder repository'yi doğrudan okuduğu için filtre
**görünmüyor bile**: hata kaçınılan değil **ulaşılamaz**. Cihazda ölçüldü —
ekran `TRY 25,00` derken tabloya `TRY 125,00` yazıldı.

**Sonsuz döngü neden yok.** Girdiler `subscriptions` tablosu ve DataStore
tercihleri; çıktı **başka bir tablo**. Room yalnızca yazılan tabloyu okuyan
sorguları yeniden çalıştırır ve burada `monthly_snapshots`'ı gözleyen hiçbir
akış yok — `getByPeriod` tek seferlik bir okuma, Flow değil. **Yapısal
garanti.** İkinci hat: değer değişmediyse yazılmaz, yani tabloyu gözleyen bir
akış ileride eklense bile döngü tek turda dururdu.

Aylık figür saklanır (yıllık görünüm gösterim tercihi, veri değil). Ay
`YearMonth.now(clock)` ile enjekte edilmiş saatten okunur. Yazma hatası bilerek
yutulur — söyleyecek ekran yok, ve bir ayın defter kaydı uygulamayı düşürmeye
değmez; `CancellationException` yutulmaz.

Recorder süreç ömrü boyunca yaşıyor; `@ApplicationScope` `CoroutineScope`'u
`di/CoroutineModule` sağlıyor, `SubTrackApplication` başlatıyor — hatırlatma
zamanlayıcısıyla aynı yerde.

### Boş liste: `0` yazılır

Faz 13'ün "o ay hiç abonelik yoktu" ile "o ay kayıt yok" ayrımını yapabilmesi
için yokluğun gerçekten yokluk anlamına gelmesi gerek. Temiz kurulumda ilk
açılış bu yüzden `0`'lık bir satır bırakıyor — "baktım, sıfırdı".

### Testler

202 → **223 birim testi** (11 mapper + 10 recorder), 0 hata.
11 → **19 enstrümantasyon testi** (8 yeni DAO testi), iki emülatörde de OK.
`lintDebug` **0 hata, 23 uyarı** (15'i `libs.versions.toml` sürüm tazeliği).

Recorder testinde `advanceUntilIdle()` **çalışmıyor**: coroutines-test 1.8'den
beri yalnızca *ön plandaki* iş bitene kadar ilerletiyor ve `backgroundScope`
ön plan değil — bekleyen ön plan işi yokken hiç dağıtım yapmadan dönüyor.
Ölçüldü (`advanceUntilIdle` → çalışmadı, `runCurrent`/`yield`/`delay` →
çalıştı) ve `settle()` yardımcısı gerekçesiyle yazıldı. **Bu tuzak mevcut
ViewModel testlerinde de var**, orada ön planda iş olduğu için görünmüyor.

### Cihaz doğrulaması — iki emülatörde birebir aynı (API 29 · API 34)

| | eylem | ekran | tabloda |
|---|---|---|---|
| | temiz kurulum, açılış | boş durum | `202609 · 0 · TRY` |
| (c) | 100,00 ekle | `TRY 100.00` | `202609 · 10000 · TRY` |
| (d) | 50,00 ekle | `TRY 150.00` | `202609 · 15000 · TRY`, **satır sayısı 1** |
| (e) | birini sil | `TRY 100.00` | `202609 · 10000 · TRY`, satır sayısı 1 |
| (f) | sil + geri al | sil anında `TRY 0.00` → `TRY 100.00` | `10000`'e geri, satır sayısı 1 |
| (g) | **Health filtresi açıkken 25,00 ekle** | **`TRY 25.00`** | **`202609 · 12500 · TRY`** |
| (h) | ana para birimi USD | `$2.92` | `202609 · 292 · USD` |
| (i) | kapat-aç (pid değişti) | `$2.92` | aynı satır, **`recordedAt` bile değişmedi** |

(g) bu fazın sınavıydı: ekrandaki **25,00** ile tablodaki **125,00** yan yana.
(i) ayrıca "değer değişmediyse yazma" kuralını cihazda kanıtlıyor.

**Yapılamayan ölçüm:** ay dönümü. Emülatör imajlarında root yok (`su`
bulunamıyor, `date` "Operation not permitted") ve saat host'tan geliyor, yani
cihazı başka bir aya taşıyamadım. "Yeni ay kendi satırını açar, eski satır
para birimiyle birlikte olduğu gibi kalır" iddiası
`MonthlySnapshotDaoTest.upsert_differentMonths_keepsBothRows` ve
`MonthlySnapshotRecorderTest.record_anotherMonth_isItsOwnRowAndLeavesTheFirstAlone`
ile kapalı — cihazda değil, testle.

### 75 maddelik sabit regresyon

İki emülatörde de koşuldu. Kayıtlı istisnalar önceki fazlarla aynı
(**API 29**: #15 koyu tema ve #16 dil — `cmd uimode`/`cmd locale` bu imajda yok;
#34-#37, #41-#45 API 33+ maddeleri. **API 34**: #40 ve #46 API < 33 maddeleri).

**#12 (kapat-aç, veri duruyor) özellikle kontrol edildi** — şema değiştiği için
kritikti: iki emülatörde de `Total Monthly, TRY 219.89` ve iki satır yerinde.
Kurulu bir uygulamanın üzerine yeni tabloyu içeren sürüm kuruldu ve **veri
kaybı olmadı**; tablo eklemek var olan tabloyu bozmuyor.

### Değişen dosyalar

- `data/local/entity/MonthlySnapshotEntity.kt`, `data/local/dao/MonthlySnapshotDao.kt` — yeni
- `data/local/SubTrackDatabase.kt` — tablo eklendi, sürüm 1 kaldı
- `app/schemas/.../1.json` — yeniden üretildi
- `domain/model/MonthlySnapshot.kt`, `domain/repository/MonthlySnapshotRepository.kt` — yeni
- `data/mapper/MonthlySnapshotMapper.kt`, `data/repository/MonthlySnapshotRepositoryImpl.kt` — yeni
- `domain/usecase/MonthlySnapshotRecorder.kt`, `di/CoroutineModule.kt` — yeni
- `di/DatabaseModule.kt`, `di/RepositoryModule.kt`, `SubTrackApplication.kt` — bağlama
- testler: `MonthlySnapshotMapperTest`, `MonthlySnapshotRecorderTest`,
  `MonthlySnapshotDaoTest`, `FakeMonthlySnapshotRepository`
- `docs/ARCHITECTURE.md` §19 ve şema sürümlemesi, `docs/ROADMAP.md`,
  `docs/TESTING.md`

**Dokunulmayanlar:** `ui/` (hiçbir dosya), `reminder/`, `PaymentCountdown`,
`NextPaymentDate`, `PaymentReminderSelection`, `SubscriptionEntity`,
`SubscriptionDao`, `SubscriptionMapper`, Manifest, `Theme.kt`, `Color.kt`,
`Dimens.kt`.

### Karşılaşılan sorunlar

- **Kenardan başlayan swipe silmiyor, geri tuşu oluyor.** API 34'te satırı
  ekranın en sağından sürüklemek sistem "geri" hareketi; sürücü artık 200 px
  içeriden başlıyor. TESTING.md'de #61 için zaten kayıtlıydı, silme için de
  geçerli.
- **Undo snackbar'ı 9 saniye**, veritabanını çekmek 3-4 saniye: silme sonrası
  önce Undo'ya dokunulmalı, sonra tablo okunmalı. Ters sırada snackbar kaçıyor.
- **Cihaz dili Türkçe'ye alınınca** FAB'ın açıklaması da Türkçe oluyor ve
  sürücü "düğüm yok" diyordu; artık iki dili de tanıyor.

### Commit'ler

- `7b6d196` feat: give the database a place to keep each month's total
- `6baf6c8` feat: read monthly totals as a domain series, not as rows
- `1037b70` feat: record what a month costs as soon as it changes
- `e7527ed` test: pin the filter trap, the one-row-per-month rule and the mapper's edges
- `031cdf8` docs: record where a month's total comes from and why it is written when it is

### Açık kalan

- **Ana ekranda "geçen aya göre" karşılaştırması Faz 13'e taşındı.** Bugün
  tabloda tek ay var; karşılaştırma en az iki ay ister. ROADMAP'te gerekçesiyle
  yazılı.

---

## [Faz 12-2 hotfix] Bildirim ilerletilmiş tarihi okuyor, gecikme penceresi kalktı — 2026-09-13

**Durum:** Tamamlandı. 12-2'de ölçülüp sohbete bırakılan iki soru da kapandı:
pencere kaldırıldı, `TESTING.md` #29 yeniden yazıldı.

### Sorun

12-2 ölçümü: kart `NextPaymentDate.onOrAfter` sonucunu, `PaymentReminderSelection`
ise **çıpayı** okuyordu. Aynı aylık abonelik için bildirim "1 day overdue",
kart "29 days left" diyordu. İki doğru yoktu — yanlış olan çıpayı okuyan taraftı.

### Görev 1 — seçim mantığı

`PaymentReminderSelection.on()` artık aboneliğin **periyoduyla** ilerletilmiş
tarihi ölçüyor. `OVERDUE_WITHIN_DAYS` ve gecikme dalı kalktı;
`UPCOMING_WITHIN_DAYS = 1` aynen duruyor. Kural: **bugün ödenecek + 1 gün kalan.**

### Görev 3 — Overdue ulaşılabilir mi: kanıt, sonra kaldırma

`PaymentCountdown.between`'in üretimde **iki** çağıranı var ve Görev 1'den
sonra **ikisi de** ona `NextPaymentDate.onOrAfter` sonucunu veriyor:

| çağıran | verdiği tarih |
|---|---|
| `HomeViewModel` (kart) | `onOrAfter(today, anchor, period)` |
| `PaymentReminderSelection` (bildirim) | `onOrAfter(today, anchor, period)` |

O sonuç hiçbir zaman bugünden önce değil — `NextPaymentDateTest`'teki özellik
testiyle sabitli. Yani **gecikmiş dalı çalışamıyor; çalışan dal her zaman
`DueToday` ya da `Upcoming`.** Kaldırıldı:

- `PaymentCountdown.Overdue` tipi ve `between`'in negatif dalı
- `PaymentReminderNotifier`'daki `days_overdue` dalı
- `SubscriptionCard`'daki `error` renkli geri sayım dalı, `asText` dalı ve
  `SubscriptionCardOverduePreview`
- `SubscriptionRowDescription`'daki `asString` dalı
- `days_overdue` çoğulu — `values` ve `values-en`
- `OVERDUE_WITHIN_DAYS`
- "gecikmiş"ten söz eden kanal açıklaması (iki dilde de yenilendi; kullanıcıya
  görünen ve artık doğru olmayan tek metindi)

`between` geçmiş bir tarihi artık **reddediyor** (`require`), uydurma bir cevap
üretmiyor: çıpayı doğrudan veren bir çağıran `NextPaymentDate`'i atlamıştır.

**Kapsam notu:** promptun DOKUNMA listesinde `ui/home/` vardı ("kart davranışı
değişmiyor"). `SubscriptionCard.kt` ve `SubscriptionRowDescription.kt`
değiştirildi çünkü `when` dallarının kaldırılmasını Görev 3 istiyor ve derleyici
zorunlu kılıyor. Davranış birebir aynı: kaldırılan dallar zaten üretilemeyen bir
durumu çiziyordu.

`MaterialTheme.colorScheme.error` uygulamada **hâlâ kullanılıyor**
(`SwipeToDeleteRow`), yani Faz 14 renk borcu duruyor.

### Görev 2 — testler

176 → **202 birim testi** (5 yeni seçim testi, 2 sözleşme testi), 0 hata.
`lintDebug` **0 hata, 23 uyarı** — 23'ün 15'i `libs.versions.toml`'daki sürüm
tazeliği uyarısı (yeni sürümler yayınlandıkça artıyor); hiçbiri bu değişiklikten
gelmiyor.

Beklentisi değişen testler (hiçbiri silinmedi, her birinin yanında gerekçesi var):

| test | eskiden | şimdi |
|---|---|---|
| `on_theAnchorWasYesterday_...` | `Overdue(1)`, seçilirdi | seçilmiyor — sonraki ödeme 14 Nisan |
| `on_theAnchorWasThreeDaysAgo_...` | `Overdue(3)`, pencerenin kenarı | seçilmiyor — 12 Nisan |
| `on_theAnchorWasFourDaysAgo_...` | seçilmiyordu (pencere dışı) | seçilmiyor (pencere yok) |
| `on_mixedList_...` | OneLate + ThreeLate girerdi | girmiyor; yerine geçmiş çıpalı haftalık **giriyor** |
| `on_theWindowCrossesAMonthBoundary_...` | `Overdue(3)` | yalnızca 1 Nisan |
| `on_theWindowCrossesAYearBoundary_...` | `Overdue(3)` | 1 Ocak + Noel çıpalı haftalık |
| `between_theDateIsBehindToday_...` | `Overdue(1)` | `IllegalArgumentException` |
| `between_theDateIsAYearBehind_...` | `Overdue(365)` | `IllegalArgumentException` |
| `everyPeriod_countsTowards...` | "hiçbiri Overdue değil" | üç periyodun üç ayrı günü (16 / 322 / 4) |

Yeni: geçmiş çıpalı haftalık → yarın → **giriyor**; geçmiş çıpalı aylık → 20 gün
sonra → **girmiyor**; bugüne denk gelen geçmiş çıpa → `DueToday`; aynı çıpa üç
periyotla üç farklı sonuç; seçilen hatırlatma **çıpayı taşımaya devam ediyor**.

Enstrümantasyon fikstürü yeniden yazıldı: `OneDayLate`/`ThreeDaysLate` isimleri
yanıltıcıydı, artık satırlar **nereye vardıklarına** göre adlandırılıyor
(`PassedLandsTomorrow`, `PassedLandsToday`, `PassedLandsFarOff`,
`LongPassedYearly`) ve bildirim metninde "overdue"/"gecik" geçmediği ayrıca
doğrulanıyor.

### Doğrulama — iki emülatörde de birebir aynı

**(a) Ham bildirim metni** (API 29 ve API 34):

```
EXTRA_TITLE=[Payment reminder: 4 subscriptions]
EXTRA_TEXT =[DueToday — today, Tomorrow — tomorrow,
             PassedLandsTomorrow — tomorrow, PassedLandsToday — today]
```

"overdue" **yok**, "gecik" **yok**.

**(b)/(c) Kart ile bildirim, aynı veriden, yan yana:**

| satır | çıpa · periyot | kart | bildirim |
|---|---|---|---|
| DueToday | bugün · aylık | Due today | today |
| Tomorrow | +1 · aylık | 1 day left | tomorrow |
| TwoDaysOut | +2 · aylık | 2 days left | (yok) |
| **PassedLandsTomorrow** | **−6 · haftalık** | **1 day left** | **tomorrow** |
| **PassedLandsToday** | **−7 · haftalık** | **Due today** | **today** |
| **PassedLandsFarOff** | **−3 · aylık** | **27 days left** | **(yok)** |
| LongPassedYearly | −400 · yıllık | 330 days left | (yok) |
| NoDate | — | (gösterge yok) | (yok) |

Çelişki kalmadı. Geçmiş çıpalı iki satır bildirime **girdi** (vardıkları gün
yakın), uzağa varan iki satır **girmedi**.

### (e) 75 maddelik sabit regresyon — ikisinde de

Kayıtlı istisnalar önceki fazlarla aynı (**API 29**: #15 koyu tema ve #16 dil —
`cmd uimode`/`cmd locale` servisleri bu imajda yok; #34-#37, #41-#45 API 33+
maddeleri, karşılıkları #40 ve #46 koşuldu. **API 34**: #40 ve #46 API < 33
maddeleri).

**#29 yeni hâliyle koşuldu ve geçti**, üç iddiası da ölçüldü: çıpa 2026-09-08
(5 gün geçmiş), kart `Late, TRY 40.00, Monthly, 25 days left`, "gecikmiş" yok,
ve `run-as` ile okunan satırda tarih **değişmemiş** (`1788825600000 → 2026-09-08`).
Listenin altındaki 12-2 geçici notu kaldırıldı.

Üç madde ilk okumada "durum kalıcı olmuş" gibi göründü (#60 filtre, #68 yıllık
görünüm, #14 yazılmış metin) — üçü de **bayat ağaç**tı. Ekran görüntüsü
uygulamanın hâlâ açılış ekranında olduğunu gösterdi; uzun beklemeden sonra üçü
de doğru davranışı verdi (#60 → All, #68 → Total Monthly 243,33 + Monthly
seçili, #14 → "Half typed" korunuyor). TESTING.md'ye yazıldı.

### Değişen dosyalar

- `domain/usecase/PaymentReminderSelection.kt` — ilerletilmiş tarih, pencere yok
- `domain/usecase/PaymentCountdown.kt` — `Overdue` kalktı, `require` geldi
- `reminder/PaymentReminderNotifier.kt`, `reminder/PaymentReminderWorker.kt` — dal ve metin
- `ui/home/components/SubscriptionCard.kt`, `SubscriptionRowDescription.kt` — ölü dallar
- `res/values/strings.xml`, `res/values-en/strings.xml` — `days_overdue` silindi, kanal açıklaması
- `test/.../PaymentReminderSelectionTest.kt`, `PaymentCountdownTest.kt`, `HomeViewModelNextPaymentTest.kt`
- `androidTest/.../PaymentReminderWorkerTest.kt` — fikstür
- `docs/ARCHITECTURE.md` §17 ve §18, `docs/ROADMAP.md`, `docs/TESTING.md`

**Dokunulmayanlar:** `NextPaymentDate`, Room şeması, DataStore, Manifest,
`Theme.kt`/`Color.kt`, `ui/settings/`.

### Commit'ler

- `322d9e3` feat: remind about the payment that is next, not the one that has gone
- `cb5c013` refactor: drop the overdue state, which nothing can reach any more
- `4935f86` docs: record why the lateness window went, and what its going costs

### Karşılaşılan sorunlar

- **`pm clear` bildirim iznini de geri alıyor.** API 34'te ilk enstrümantasyon
  koşusu "no notification was posted" dedi; `pm grant`, clear'dan **sonra**
  gelmeli. TESTING.md'ye yazıldı.
- **`am force-stop` uygulamanın bildirimini siliyor**, yani bildirim metni
  uygulama açılmadan önce okunmalı. Yazıldı.
- **API 34 emülatörü "System UI isn't responding" veriyordu**; sürücü artık
  diyaloğu kendi kapatıyor, tekrarlarsa `adb reboot`. Ürünle ilgisi yok.

### Açık kalan

- Hiçbiri. Faz 12 kapalı; tag kullanıcıda.

---

## [Faz 12-2] Tarih İlerletme — 2026-09-13

**Durum:** Tamamlandı. **Faz 12 KAPANDI.** Gecikme penceresine bilerek
dokunulmadı — ölçüldü ve karar sohbete bırakıldı (aşağıda).

### İlerletme — okuma anında, veritabanına yazmadan

`domain/usecase/NextPaymentDate.onOrAfter(today, anchor, period)`: çıpayı,
bugüne ulaşana kadar tam periyotlarla ilerletir. Saklanan tarih **çıpa** olarak
kalıyor; hiçbir yere yazılmıyor. Gerekçe 10a'daki geri sayım kararının aynısı —
ilerletilmiş tarih aboneliğin değil, *abonelik + bugün* ikilisinin özelliği.

**Döngü yok, aritmetik var.** Üç periyot için de aynı şekil: kaç **tam** periyot
geçtiğini `ChronoUnit.WEEKS/MONTHS/YEARS.between` ile sor, çıpaya bir kerede
ekle, gerekirse bir periyot daha ekle. `between` tam birim saydığı için düzeltme
en fazla bir adım. On yıllık haftalık bir çıpa 558 adım değil, bir çıkarma ve
bir toplama.

**Ay sonu tuzağı — çıpadan sayılıyor.** 31 Ocak'tan ayda bir ilerlenirse
28 Şubat'a, oradan 28 Mart'a gidilir ve abonelik sessizce 28'ine taşınır. Kaç ay
geçtiğini sorup çıpaya bir kerede eklemek Mart'ta 31'i geri veriyor. Testle
sabitlendi (`monthly_theClampDoesNotStick_becauseCountingStartsAtTheAnchor`), ve
dört ay boyunca 28 Şubat → 31 Mart → 30 Nisan → 31 Mayıs dizisi de ayrıca.

`plusMonths`/`plusYears`'in takvim mantığı **bilerek** korundu: 31 Ocak + 1 ay =
28 Şubat, 29 Şubat 2028 + 1 yıl = 28 Şubat 2029. Gün sayısına çevrilmedi.

### Geri sayım ve kart

`PaymentCountdown`'a **dokunulmadı**; `HomeViewModel` ona artık ilerletilmiş
tarihi veriyor. Kartta ayrıca bir tarih metni yok — kartın gösterdiği şey geri
sayım, o da ilerletilmiş tarihe göre.

**"Gecikmiş" durumu ana ekranda artık oluşmuyor.** Sayılan tarih hiçbir zaman
geçmişte olmadığı için `Overdue` üretilemez; bugünden geriye kalan tek durum
"bugün ödenecek". Bu bir davranış **değişikliği değil, kararın sonucu** — 
promptun öngördüğü durum — o yüzden durup sorulmadı; `PaymentCountdown.Overdue`
kaldırılmadı, çünkü bildirim tarafı hâlâ çıpayı okuyor ve oradan gelebiliyor.

Çıpa hiçbir yerde kaybolmuyor: Faz 15'in düzenleme ekranı onu gösterecek
(ROADMAP'e madde olarak eklendi).

### Görev 3 — bildirim tarafı: okundu, ölçüldü, değiştirilmedi

**Kod:** `PaymentReminderSelection.on()` `subscription.nextPaymentDate`'i, yani
**çıpayı** okuyor. İlerletme yalnızca `HomeViewModel`'in geri sayım hesabında.

**Ölçüm (iki emülatörde de aynı):** enstrümantasyon paketi `am instrument` ile
koşuldu; yedi abonelikten dördü bildirime girdi ve **ikisi gecikme
penceresinden** geldi. Ham metin:

```
EXTRA_TITLE=[Payment reminder: 4 subscriptions]
EXTRA_TEXT =[DueToday — today, Tomorrow — tomorrow,
             OneDayLate — 1 day overdue, ThreeDaysLate — 3 days overdue]
```

Aynı satırlar için ekran başka bir şey diyor:

| satır | çıpa | bildirim | kart |
|---|---|---|---|
| DueToday | bugün | "today" | Due today |
| Tomorrow | +1 | "tomorrow" | 1 day left |
| TwoDaysOut | +2 | (yok) | 2 days left |
| **OneDayLate** | −1 | **"1 day overdue"** | **29 days left** |
| **ThreeDaysLate** | −3 | **"3 days overdue"** | **27 days left** |
| FourDaysLate | −4 | (yok) | 26 days left |
| NoDate | — | (yok) | (gösterge yok) |

**Sonuç: pencere ölü kod değil, hâlâ tetikleniyor** — ama gerekçesi düştü ve
artık ekranla aynı şeyi söylemiyor. Eşiklere dokunulmadı; seçenekler
`ARCHITECTURE.md` §18'e yazıldı, **karar sohbette**.

### Testler

- 176 → **197 birim testi** (21 yeni), 0 hata. `lintDebug` **0 hata, 20 uyarı**
  (`java.time` kullanıldığı için şart koşuldu).
- `NextPaymentDateTest` (13): gelecek/bugün dokunulmuyor · aylık ilerletme ·
  kısa ay kırpması · **kırpmanın kalıcı olmadığı** · dört aylık dizi · bugüne
  tam denk gelme · artık gün çıpası (2028-02-29) hem sıradan yılda kırpılıyor
  hem **sonraki artık yılda 29'a dönüyor** · on yıl geçmiş yıllık · haftalık ·
  **558 periyotluk** haftalık · ve hepsini kapsayan özellik testi (sonuç asla
  geçmişte değil, bir önceki periyot her zaman geçmişte).
- `HomeViewModelNextPaymentTest` (8): üç periyot, tarihsiz satır, gelecek,
  bugün, **saklanan tarihin değişmediği**, ve `Overdue`'nun artık üretilmediği.
- **Beklentisi değişen tek test:** `HomeViewModelTest.uiState_datedSubscriptions_carryTheirCountdown`
  — 4 gün geçmiş aylık satır için `Overdue(4)` diyordu, artık `Upcoming(27)`
  (11 Mart çıpası, 15 Mart'ta bakınca 11 Nisan). Fazın istediği davranış
  değişikliği; testin yanına gerekçesi yazıldı.

### Emülatör doğrulaması (cihaz tarihi 2026-09-13, iki AVD'de de aynı)

| | periyot | çıpa | beklenen | kart |
|---|---|---|---|---|
| (a) | Monthly | 2026-08-09 (−35g) | 2026-10-09, 26 gün | `26 days left` |
| (b) | Weekly | 2026-08-14 (−30g) | 2026-09-18, 5 gün | `5 days left` |
| (b) | Yearly | 2025-08-09 (−400g) | 2027-08-09, 330 gün | `330 days left` |
| (c) | Weekly | 2024-07-05 (−800g, **115 periyot**) | 2026-09-18, 5 gün | `5 days left` |
| (d) | Monthly | 2026-09-18 (gelecek) | dokunulmaz | `5 days left` |
| (e) | Monthly | bugün | dokunulmaz | `Due today` |

Hepsi elle hesapla birebir. **(c) donma yok:** kaydetten kartın görünmesine
kadar geçen süre 115 periyotluk satırda da 1 periyotluk satırdakiyle aynı
(~4,4 s, ölçümün tamamı uyku + dump); hesap zaten sabit sayıda işlem.

**Çıpa cihazda da yerinde duruyor.** 40 gün geçmiş bir çıpa kaydedildi, kart
`Anchored, TRY 12.00, Monthly, 21 days left` dedi; `run-as` ile okunan satır:

```
Anchored | MONTHLY | 1785801600000 -> 2026-08-04
```

### (g) Sabit regresyon listesi — 68 → 75 madde, ikisinde de koşuldu

12-2'nin yedi maddesi eklendi (69-75). 68 maddenin tamamı iki emülatörde
koşuldu. Kayıtlı istisnalar önceki fazlarla aynı (**API 29**: #15 koyu tema,
#16 dil; #34-#37 ve #41-#45 API 33+ maddeleri — karşılıkları #40 ve #46 koşuldu.
**API 34**: #40 ve #46 API < 33 maddeleri).

**#29 artık geçerli değil ve bilerek güncellenmedi.** "Geçmiş bir tarih seç →
kart 'gecikmiş' diyor, tarih ilerletilmiyor" maddesi iki emülatörde de yeni
davranışı gösterdi: 5 gün geçmiş aylık çıpa → **`Late, TRY 40.00, Monthly,
25 days left`**. Maddenin yerine ne yazılacağı §18'deki pencere kararıyla
birlikte verilmeli; TESTING.md'ye listenin altına bu ölçümü anlatan bir not
eklendi, satırın kendisine dokunulmadı.

### Değişen dosyalar

- `domain/usecase/NextPaymentDate.kt` — yeni
- `ui/home/HomeViewModel.kt` — geri sayım ilerletilmiş tarihe göre
- `test/.../NextPaymentDateTest.kt`, `test/.../HomeViewModelNextPaymentTest.kt` — yeni
- `test/.../HomeViewModelTest.kt` — bir beklenti (yukarıda)
- `docs/ARCHITECTURE.md` §17 ve §18, `docs/ROADMAP.md`, `docs/TESTING.md`

**Dokunulmayanlar:** `reminder/` (yalnızca okundu), `PaymentCountdown`,
`PaymentReminderSelection`, Room şeması, `ui/settings/`.

### Commit'ler

- `974fb50` feat: work out where a payment date has got to by today
- `e9a1135` feat: count towards the payment that is actually next

### Karşılaşılan sorunlar

- **Emülatör yavaşlığı ölçümü bozdu, ürün değil.** `am start` sonrası 6 saniye
  yetmediği için birkaç koşu "düğüm yok" diye düştü; `restart_app` artık saate
  değil, ekrandaki FAB'ın belirmesine bakıyor. Aynı sebepten #68 bir koşuda
  "yıllık görünüm kalmış" gibi göründü — 9 saniye beklenince doğru sonuç
  (`Total Monthly` ve `Monthly checked=true`, pid değişmiş) alındı.
- **uiautomator bayat ağaç döndürebiliyor.** Temizlik sonrası dump "Ayarlar
  ekranı" gösterdi; aynı anda alınan ekran görüntüsü ana ekranı gösteriyordu.
  TESTING.md'de kayıtlı tuzak, tekrar doğrulandı.
- **Tarih seçicideki "Save" ikilemi** (12-1'de yazılmıştı) yine çıktı; takvim
  hücresi aramak yerine **metin girişi** kullanan bir yardımcıya geçildi, artık
  tarihler cihazın kendi tarihinden hesaplanıyor (eski betikler sabit "10 Eylül"
  gibi günlere bakıyordu ve tarih ilerleyince bozuluyordu).

### Rapor edilen, karar bekleyen

> **Kapandı** — ikisi de yukarıdaki **12-2 hotfix** kaydında çözüldü.

- **Gecikme penceresi** (aşağıdaki ölçüm). Bildirim çıpayı, ekran ilerletilmiş
  tarihi okuyor; ikisi aynı satır için farklı şey söylüyor.
- **#29** maddesinin yeni metni.

### Sonraki faz için not

- Faz 15 düzenleme ekranı **çıpayı** göstermeli (ROADMAP'te madde var).
- `PaymentCountdown.Overdue` artık yalnızca bildirim yolundan gelebiliyor; §18
  kararı onu tamamen kaldırırsa kartlardaki `error` rengi kullanımı da gözden
  geçirilmeli (Faz 14 renk borcu). *(12-2 hotfix: kaldırıldı. `error` rolü
  `SwipeToDeleteRow`'da kullanılmaya devam ettiği için borç duruyor.)*

---

## [Faz 12-1 hotfix] Normalizasyon ara değeri BigInteger'a taşındı — 2026-09-06

**Durum:** Tamamlandı. Kullanıcıya dönük hiçbir şey değişmedi — ne bir sayı, ne
bir sınır, ne bir imza.

**Sorun (12-1 doğrulamasında ölçülmüştü)**
Haftalık normalizasyon payı 52 ile çarpıyor: `fiyat × paymentsPerYear × kur`
uygulamadaki en geniş değer. Long'da, fiyat tavanı (1.000.000 birim) ve kur
tavanı (1.000,0000) birlikte, satırların hepsi haftalıkken **177 satırdan**
sonra taşıyordu; normalizasyondan önceki sınır **9.223**'tü. Bugünkü hiçbir
liste oraya yaklaşmıyor, ama pay bu fazda daraldığı için bu fazda kapatıldı.

**Yapılan**
`CurrencyConverter.total(...)` zincirinin ara değeri `BigInteger`:

```
Σ(fiyat × paymentsPerYear) × kaynakKuru / (hedefKuru × parça)
```

Ağırlıklı toplam `BigInteger.ZERO` üzerinden katlanıyor, kur çarpımı ve bölme
de aynı tipte; sonuç tek noktada HALF_UP ile yuvarlanıp `toLong()` ile
`Money`'ye dönüyor. `divideHalfUp`'ın `BigInteger` sürümü Long sürümünün
yanına eklendi (`convert` tek bir tutara bakıyor, orada genişliğe ihtiyaç yok
ve iki `BigInteger` ayırmak boşuna olurdu). `BigInteger.TWO` API 31, minSdk 24
— sabit kendi companion'ımızda.

**Neden davranış değişmiyor:** `BigInteger` de tam sayı bölmesini **sıfıra
doğru kırpıyor**, tıpkı Long gibi. Bölmeden önce paydanın yarısını eklemek bu
yüzden iki tipte de aynı yuvarlamayı veriyor. Tek yuvarlama noktası kuralı
(ARCHITECTURE §6) aynen duruyor.

**Değişmeyenler (bilerek)**
- `MAX_PRICE` (1.000.000 birim) ve `ExchangeRateTable.MAX_RATE` (1.000,0000).
- `CurrencyConverter`'ın public imzaları; `Money` hâlâ `Long` kuruş (§6).
- UI'ın tamamı — bu bir hesap değişikliği.

**Yeni sınır — ara değerin tavanı kalmadı, cevabın tavanı kaldı**
`Money` bir `Long` olduğu için sınır artık **sonucun sığması**. Aynı tavanlarda,
çapaya çevirirken haftalık bir satır ayda 433.333.333.333 kuruş:

| | eski (Long ara değer) | yeni (BigInteger ara değer) |
|---|---|---|
| Aylık görünüm | **177 satır** | **21.284.704 satır** |
| Yıllık görünüm | 177 satır | **1.773.725 satır** |
| Normalizasyon öncesi referans | 9.223 satır | — |

Yani pratikte kalan sınır, listenin bellekte tutulup `LazyColumn` ile çizildiği
bir uygulamanın göremeyeceği yerde: 21 milyon satır. İki sayı da teste
sabitlendi.

**Testler**
- 174 → **176 birim testi** (2 yeni), 0 hata. `lintDebug` **0 hata, 20 uyarı**.
- **12-1'deki 14 normalizasyon testinin hiçbirinin beklentisi değişmedi.**
  Tek dokunulan yer `weeklyNormalisation_costsHeadroom_andWhatIsLeftIsPinned`
  testinin KDoc'u: 9.223 ve 177 assertion'ları aynen duruyor (ikisi de Long
  hakkında doğru olgular ve bu tercihin gerekçesi), ama artık dönüştürücünün
  sınırı olmadıkları yazıldı.
- Yeni: `farPastTheOldLongCeiling_theTotalIsStillExact` — 500 satır, hepsi
  tavan fiyatta, haftalık, kur tavanında. Önce çarpımın Long'a **sığmadığı**
  gösteriliyor (2,6 × 10¹⁹ > 9,22 × 10¹⁸), sonra sonucun kuruşu kuruşuna
  doğru olduğu: **216.666.666.666.667 kuruş**.
- Yeni: `whatIsLeftIsTheAnswerHavingToFitInMoney` — 21.284.704 ve 1.773.725.

**Emülatör (yalnızca (c) ve (d) ile 11. madde, iki AVD'de)**

| | API 29 | API 34 |
|---|---|---|
| (c) 100 aylık + 1.200 yıllık + 10 haftalık | `Total Monthly, TRY 243.33` | `Total Monthly, TRY 243.33` |
| (d) yıllık görünüm | `Total Yearly, TRY 2,920.00` | `Total Yearly, TRY 2,920.00` |
| 11. 159,99 + 59,90 | `Total Monthly, TRY 219.89` | `Total Monthly, TRY 219.89` |

Üç sayı da hotfix öncesiyle **birebir aynı**. Tam regresyon koşulmadı: UI
değişmedi, hesap testlerle kapalı.

**Değişen dosyalar**
- `domain/usecase/CurrencyConverter.kt` — ara değer `BigInteger`, ikinci
  `divideHalfUp`
- `domain/model/ExchangeRateTable.kt` — `MAX_RATE`'in KDoc'u artık doğru sınırı
  anlatıyor (eski 9.223 gerekçesi tarih olarak duruyor)
- `test/.../PeriodNormalisationTest.kt` — iki yeni test, bir KDoc
- `docs/ARCHITECTURE.md` §6

**Commit**
- `a5cc0a4` fix: give the totals room the ceilings cannot use up

---

## [Faz 12-1] Ödeme Periyodu: seçim, normalizasyon, toplam görünümü — 2026-09-06

**Durum:** Tamamlandı. **Faz 12 KAPANMADI** — tarih ilerletme ve hatırlatma
penceresi 12-2'nin işi, ROADMAP'teki o iki madde açık bırakıldı.

### Görev 1 — mevcut durumun tespiti (kod yazmadan önce)

**a) `BillingPeriod`** üç sabit taşıyordu, başka hiçbir şey yoktu:
`MONTHLY`, `YEARLY`, `WEEKLY`. UI'a hiç bağlanmamıştı;
`HomeViewModel.save()` her satıra koşulsuz `BillingPeriod.MONTHLY` yazıyordu.

**b) Veritabanındaki değer — iki emülatörde de `MONTHLY`.** Üç kayıt
oluşturulup `run-as` ile `subtrack.db` (+ `-wal`) çekildi ve okundu:

```
(1, 'Alpha',  10000, 'TRY', 'MONTHLY', 'OTHER')
(2, 'Beta',  120000, 'TRY', 'MONTHLY', 'HEALTH')
(3, 'Gamma',   1000, 'TRY', 'MONTHLY', 'OTHER')
group by billingPeriod -> [('MONTHLY', 3)]
```

Yani mevcut toplamların anlamı değişmiyor: bugüne kadar yazılmış her satır
zaten aylık. Durup sormayı gerektiren bir şey çıkmadı.

> Tuzak: `subtrack.db` tek başına çekilirse **boş** görünür, kayıtlar WAL
> dosyasındadır. `force-stop` checkpoint yapmıyor; `-wal` ve `-shm` de
> çekilmeli (ve `exec-out` ile, `shell` ikiliyi bozuyor).

**c) Taşma hesabı — taşmıyor, ama pay 52 kat daraldı.** Yeni paydaki en geniş
değer `fiyat × paymentsPerYear × kur`:

| | en geniş satır | Long'a sığan satır sayısı |
|---|---|---|
| Normalizasyon öncesi | 10^8 × 10^7 = 1,0 × 10^15 | **9.223** |
| Haftalık normalizasyonla | 10^8 × 52 × 10^7 = 5,2 × 10^16 | **177** |
| Aynısı, uygulamanın kendi kurlarıyla (≤ 53,90) | 2,8 × 10^15 | **3.290** |

`Long.MAX_VALUE = 9,223 × 10^18`. Payda `hedefKuru × 12 ≤ 1,2 × 10^8`, eklenen
yarım `6 × 10^7` — bu bandı değiştirmiyor. Yani **hiçbir tavan
değiştirilmedi**; sınır `PeriodNormalisationTest`'te sabitlendi (9.223 ve 177
birlikte).

177 satırın hepsinin aynı anda tavan fiyatta (1.000.000 birim), haftalık ve
kullanıcının 1.000,0000'e çektiği bir kurda olması gerekiyor. Gerçek bir
listede ulaşılmaz; yine de eski payın yüzde ikisi olduğu için buraya yazıldı.
Payı geri istemenin üç yolu var (fiyat tavanını indirmek, kur tavanını
indirmek, ara değeri `BigInteger`'a taşımak) ve üçü de ürün/mimari kararı —
kendi başıma almadım.

### Normalizasyon

- `BillingPeriod(paymentsPerYear)` — aylık 12, yıllık 1, haftalık 52.
  Fiyatı bununla **çarpmak** yıllık maliyeti verir ve çarpma tamdır.
- `TotalPeriod(partsOfAYear)` — görünüm için ayrı enum (MONTHLY 12, YEARLY 1).
  `BillingPeriod` kullanılmadı: haftalık bir *görünüm* yok, olmayan bir cevabı
  temsil eden sabit taşımak istemedim.
- `CurrencyConverter.totalIn(subs, target, period)` — **yeni aşırı yükleme**,
  mevcut iki parametreli imzaya dokunulmadı (prompt öyle istiyordu). İkisi de
  aynı özel gövdeyi çağırıyor: satırları ağırlıklandır, para birimine göre
  grupla, **tek bölmede** çevir ve böl.
- **52 hafta** kararı ve **tek yuvarlama noktası** kuralı gerekçeleriyle
  `ARCHITECTURE.md` §6'ya yazıldı.

**İki parametreli `totalIn` neden duruyor:** artık üründe çağıran yok. Silmek
imza değiştirmekten büyük bir adım olurdu ve `CurrencyConverterTest`'teki
gruplama/yuvarlama sözleşmesini (aynı özel gövdeyi koruyan testler) götürürdü.
KDoc'una "bu fiyatları toplar, maliyetleri değil; periyotlar karışıksa çıkan
sayının karşılığı yoktur" diye açıkça yazıldı.

### Form

Periyot sırası **para biriminin altına**, kategorinin üstüne kondu: para birimi
ve periyot ikisi de üstteki fiyatın ne demek olduğunu söylüyor (neyle, ne
sıklıkta), kategori ise formda hiçbir şeyi değiştirmiyor ve tarih diyalog açıp
sırayı bitiriyor.

`FlowRow` seçildi (kategorideki gibi), kaydırma değil: form zaten kaydırılıyor,
ikinci satırın kalıcı bir bedeli yok — filtre çubuğundaki gerekçe (liste üstünde
sonsuza kadar duran şerit) burada geçerli değil. **Sığdı, sarma gerekmedi:**

| | chip genişlikleri | toplam | kullanılabilir |
|---|---|---|---|
| API 29 (360dp) | 168 + 142 + 156 | 466 px + boşluklar | 624 px |
| API 34 (411dp) | 217 + 186 + 202 | 605 px + boşluklar | 984 px |

State sheet'in kendi `rememberSaveable`'ında (ARCHITECTURE §5, 11b'de
netleşti); `BillingPeriodSaver`, `CategorySaver`'ın deseni.

### Kartta gösterim

**Adın altında ayrı bir satır, her kartta.** Fiyatın yanına sonek olarak
yazılmadı: fiyat sağdaki sütunda, ad kalanı alıyor; fiyatı uzatmak adın
sütununu daraltırdı ve **fs 2.0'da ad kırpılması zaten bilinen sorun**
(Faz 14). Satır yükseklik harcıyor, kartın fazlası olan şey o.

Kategoriden farklı olarak **koşulsuz**: `OTHER` "cevap yok" demekti, ama
işaretsiz bir kart aylık mı yoksa işaretlenmemiş mi belli olmaz ve parada
örtük kural olmaz.

**Ölçüm — periyot satırı bir `bodySmall` satırı kadar yer alıyor, yatayda
hiçbir şey almıyor:**

| | 1 satır (12-1 öncesi) | 2 satır (ad + periyot) | 3 satır (+ kategori) |
|---|---|---|---|
| API 29 fs 1.0 | 144 px | **166 px** | **198 px** |
| API 29 fs 2.0 | 162 px | **226 px** | **290 px** |
| API 34 fs 1.0 | 189 px | **217 px** | **259 px** |
| API 34 fs 2.0 | 212 px | **296 px** | **380 px** |

fs 2.0'da her satır API 29'da 64 px, API 34'te 84 px ekliyor; ilk ek satır
fs 1.0'da daha ucuz (28/22 px) çünkü ikonun asgari yüksekliği bir kısmını
zaten içeriyordu. **Kırpılma yok, kart uzuyor ve liste kaydırılıyor.** Adın
yatay alanı değişmedi: fiyat metni aynı, `weight(1f)` dağılımı aynı.

Kart hâlâ **tek erişilebilirlik düğümü**; okunan cümle artık
`"Delta, TRY 55.00, Monthly, Health"`.

**Okunan cümle tek formata indi.** `subscription_row_description_dated`
silindi, `..._with_category` → `..._more` diye yeniden adlandırıldı: cümle
artık "bir bilgi daha ekle" formatıyla adım adım kuruluyor (ad+tutar → periyot
→ geri sayım → kategori). Dört kombinasyon dört çeviri isteyecekti.

### Dashboard

Kartın **altında** iki chip (Aylık / Yıllık). İçine konamaz: kart
`clearAndSetSemantics` ile tek odak durağı, çocukları ağaçtan düşüyor —
içerideki bir chip erişilemez olurdu.

Başlık görünümle değişiyor (`total_monthly` / `total_yearly`) ve tutarla
birlikte tek cümle olarak okunuyor: `"Total Yearly, TRY 2,920.00"` — 8a'daki
tek odak durağı bozulmadı.

Görünüm state'i ViewModel'da (sayıyı değiştiriyor, §5), **kalıcı değil**;
filtre kararıyla aynı çizgide.

### Testler

- 149 → **174 birim testi** (25 yeni), 0 hata. `lintDebug` **0 hata, 20 uyarı**
  — sayı değişmedi.
- `PeriodNormalisationTest` (14 test): yıllık ÷ 12 HALF_UP (1.199,00 → 99,92),
  haftalık × 52 ÷ 12 (10,00 → 43,33), karışık liste (243,33), yıllık görünümün
  tamlığı (292.000 kuruş), aylık × 12 ile yıllık arasındaki farkın sınırı
  (≤ 6 kuruş), **tek yuvarlama kanıtı** ve tavanlar.
- **Tek yuvarlama kanıtı:** haftalık 10,00 USD → TRY.
  Birlikte `1.000 × 52 × 428.500 / (10.000 × 12)` = **185.683** kuruş;
  ayrı ayrı `52.000/12 = 4.333` sonra `× 42,85` = **185.669**. Fark **14
  kuruş**, test ikisini yan yana gösteriyor.
- `HomeViewModelBillingPeriodTest` (4) ve `HomeViewModelTotalPeriodTest` (7):
  kaydedilen periyot, varsayılan, görünüm değişimi, filtreyle birlikte çalışma.
- Fake kullanıldı, mock yok (§11).

### Emülatör doğrulaması

**(b) Seçici** — yukarıdaki tabloda; iki AVD'de de **tek satır**.

**(c) Toplam — elle hesapla birebir aynı.** 100,00 aylık + 1.200,00 yıllık +
10,00 haftalık:

```
beklenen: 100,00 + 100,00 + 43,33 = 243,33
ekranda : "Total Monthly, TRY 243.33"   (API 29 ve API 34)
```

**(d) Yıllık görünüm:** `"Total Yearly, TRY 2,920.00"` (iki AVD'de de).
Aylık figürün 12 katı **2.919,96**; fark **4 kuruş**. Sebep tek yuvarlama:
gerçek yıllık maliyet 292.000 kuruş, aylık figür onun yuvarlanmış on ikide
biri (24.333). Ekranda gösterilen, yuvarlanmış sayının katı değil, bölünmemiş
ara değerin kendisi — yani doğru olanı.

**(e) Kartlar:** `"Alpha, TRY 100.00, Monthly"` · `"Beta, TRY 1,200.00, Yearly"`
· `"Gamma, TRY 10.00, Weekly"`.

**(f)** Yukarıdaki yükseklik tablosu.

**(g) Filtre + görünüm birlikte:** Sağlık seçiliyken aylık **55,00**, yıllık
**660,00** (= 55 × 12), "Tümü"ye dönünce aylık **298,33**. İki AVD'de de aynı.

**(h)** Haftalık seçilip kaydedildikten sonra form yeniden açıldığında
**Aylık** seçili geliyor; seçim yapılmışken döndürüldüğünde korunuyor
(API 29 ve API 34).

**(i) Erişilebilirlik:** dashboard tek düğüm
(`[42,338][1038,633] "Total Monthly, TRY 243.33"`), kart tek düğüm, toggle
chip'leri `checkable=true` ve seçili olan `checked=true`, her biri tek odak
durağı.

**(j) Sabit regresyon listesi — 61 → 68 madde, ikisinde de koşuldu.** 12-1'in
yedi maddesi eklendi (62-68). 61 maddenin tamamı iki emülatörde koşuldu ve
geçti; kayıtlı istisnalar 11b'dekilerle aynı (**API 29**: #15 koyu tema,
#16 dil — API 33 öncesi `cmd locale` yok; #34-#37 ve #41-#45 API 33+ maddeleri,
karşılıkları #40 ve #46 koşuldu. **API 34**: #40 ve #46 API < 33 maddeleri).
#39 için kanal yine `am instrument` ile yaratıldı.

**(k)** İki emülatör de silinip yeniden kuruldu, ayarlar geri alındı, ikisi de
temiz boş durumla açılıyor.

### Değişen dosyalar

- `domain/model/BillingPeriod.kt` — `paymentsPerYear`
- `domain/model/TotalPeriod.kt` — yeni
- `domain/usecase/CurrencyConverter.kt` — periyot alan `totalIn` aşırı yüklemesi
- `ui/common/BillingPeriodSelector.kt`, `ui/common/TotalPeriodToggle.kt` — yeni
- `ui/home/components/SubscriptionRowDescription.kt` — yeni (HomeScreen 300
  satırı geçmişti, okunan cümle oraya taşındı)
- `ui/home/HomeUiState.kt` (`monthlyTotal` → `total`, `totalPeriod`),
  `HomeViewModel.kt`, `HomeScreen.kt`, `HomeScreenPreviews.kt`
- `ui/home/components/AddSubscriptionSheet.kt`, `SubscriptionCard.kt`,
  `DashboardCard.kt`
- `res/values/strings.xml`, `res/values-en/strings.xml`
- `test/.../PeriodNormalisationTest.kt`, `HomeViewModelBillingPeriodTest.kt`,
  `HomeViewModelTotalPeriodTest.kt` — yeni; `HomeViewModelTest`,
  `HomeViewModelFilterTest` — alan adı güncellendi
- `docs/ARCHITECTURE.md` §6 ve §17, `docs/ROADMAP.md`, `docs/TESTING.md`

### Commit'ler

- `2cf0459` feat: total what subscriptions cost, not what their prices say
- `b1e1be7` feat: choose how often a subscription is billed
- `1626d45` feat: say on every card how often it is billed
- `94af319` feat: switch the total between a month and a year

### Karşılaşılan sorunlar

- **`HomeScreen.kt` 304 satıra çıktı** (sınır 300). Okunan satır cümlesi
  `SubscriptionRowDescription.kt`'ye taşındı, 269'a indi.
- **Tarih seçicinin onay düğmesi de "Save" diyor.** Diyalog kapanırken alınan
  dump'ta iki "Save" düğümü görünüyor ve yanlış olana dokunuluyor; 29. madde
  bu yüzden "kart yok" dedi. Üründe sorun yok, ölçümdeydi — TESTING.md'ye
  tuzak olarak yazıldı.
- **360dp'de form bir sıra uzadı**, kategori chip'leri ve tarih alanı açılışta
  ekranın altında kalıyor. Ürün açısından sorun değil (form kaydırılabilir,
  ikisi de opsiyonel) ama ölçümden önce kaydırmak gerekiyor; TESTING.md'ye
  yazıldı.

### Rapor edilen, düzeltilmedi

- **Taşma payı 9.223 → 177.** Yukarıda hesabıyla duruyor. Tavan
  değiştirilmedi, karar sohbetin.
- İki parametreli `totalIn` artık üründe çağrılmıyor (yukarıda gerekçesi).

### Sonraki faz için not

- **12-2:** tarih ilerletme + hatırlatma penceresi. §17'deki "kullanıcı
  seçmiyor" önkoşulu artık geçersiz; §18'deki 1-3 günlük pencerenin gerekçesi
  ilerletme gelince ortadan kalkıyor. İkisi birlikte ele alınmalı.
- Faz 14'te kart yeniden ele alınırken fs 2.0'da **üç satırlı** kart
  (ad + periyot + kategori) 290/380 px; tabloyu oradan al.

---

## [Faz 11b] Kategori Filtresi — 2026-09-06

**Durum:** Tamamlandı. **Faz 11 KAPANDI** (ROADMAP'teki dört madde de işaretli).

**Görev 0 — 11a'daki form state tutarsızlığı kapatıldı**
11a kategoriyi `HomeUiState.selectedCategory` + `HomeEvent.SelectCategory`
olarak tutuyordu; formun diğer dört alanı (ad, fiyat, para birimi, tarih)
sheet'in kendi `rememberSaveable`'ındaydı. Kategori sheet'e **indirildi**:

- `AddSubscriptionSheet` artık `rememberSaveable(stateSaver = CategorySaver)`
  ile kendi kategorisini tutuyor. `CategorySaver`, `CurrencySaver`'ın birebir
  deseni — enum adı saklanıyor, tanınmayan ad `OTHER`'a düşüyor.
- `onSave` beş parametreye çıktı; `HomeEvent.Save`'in `category` parametresi
  varsayılanı `OTHER` olarak kaldı, yani eski çağrılar bozulmadı.
- `HomeUiState.selectedCategory` ve `HomeEvent.SelectCategory` **kaldırıldı**.
- Kural `ARCHITECTURE.md` §5'e yazıldı: form alanı composable'da, listeyi veya
  toplamı değiştiren state ViewModel'da. Ayırt edici soru orada.

11a'nın kategori testleri silinmedi, **yeni yüzeye göre yazıldı**: artık
"seçim state'e yansıyor" değil, "kayıt doğru kategoriyle gidiyor" ölçülüyor
(4 test). Chip'in döndürmede korunması ve kayıttan sonra sıfırlanması sheet'in
`rememberSaveable`'ının işi ve cihazda ölçüldü (liste #54, #55).

**Filtre**
- `ui/common/CategoryFilterBar` — beş `FilterChip` ("Tümü" + dört kategori),
  `horizontalScroll`. **`FlowRow` değil:** dört chip 360dp'de zaten tek satıra
  sığmıyordu (11a: 788 px gerekiyor, 624 px var), beşincisi eklenince sarma her
  ekranda kalıcı olarak ikinci bir satır yerdi. Kaydırmanın bedeli yalnızca
  taşan chip'i isteyen kullanıcıya ait.
- Filtre `HomeUiState.categoryFilter`'da; `null` "seçim yok" demek, beşinci bir
  kategori değil. Kalıcı değil — DataStore'a yazılmıyor, süreç ölünce "Tümü".
- ViewModel'da liste **bir kez** süzülüyor, geri sayım ve toplam süzülmüş
  listeden türetiliyor. Yani filtre açıkken dashboard o kategoriyi gösteriyor;
  ayrı bir "kategori toplamı" göstergesi **eklenmedi** (gerekçe ROADMAP'te).
- `hasAnySubscriptions` eklendi: "hiç abonelik yok" ile "bu kategoride yok"
  ayrımını ekran bu alandan yapıyor.

**Boş durum varyantı**
`EmptyState`'in **imzası değişmedi**; `EmptyCategory()` sarmalayıcısı eklendi ve
`EmptySubscriptions()` ile aynı ikonu kullanıyor (`AutoMirrored.Filled.List`)
— iki durum aynı şeyin farklı pencereden görünüşü, ayıran şey metin. Faz 16'nın
daraltacağı ikon kümesi büyümedi.

**Testler**
- 143 → **149 birim testi** (yeni `HomeViewModelFilterTest` 9 test,
  `HomeViewModelCategoryTest` 7 → 4 teste yeniden yazıldı), 0 hata.
- `lintDebug` **0 hata, 20 uyarı** — sayı değişmedi.
- Enstrümantasyon: `PaymentReminderWorkerTest` iki emülatörde de `OK (2 tests)`
  (kanalı yaratmak için koşuldu, aşağıya bak).

**Sahte repository'de bulunan hata — üründe değil, testte**
Undo sıra testleri kırmızı geldi. Sebep: `FakeSubscriptionRepository.observeAll()`
eklenme sırasını döndürüyordu, DAO ise `ORDER BY createdAt DESC` ile ters
sırayı. Yani sahte, gerçek sözleşmeyi taklit etmiyordu. **Düzeltilen sahte
oldu, ürün değil** — fake artık `sortedByDescending { it.createdAt }` veriyor.

**Emülatör doğrulaması — iki AVD'de de tam**

| | API 29 (360dp) | API 34 (411dp) |
|---|---|---|
| Chip'ler tek satıra | sığmıyor, kaydırma gerekiyor | sığmıyor, "Diğer" `[1043,821][1080,947]`'de kırpık |
| Kaydırmadan sonra | beş chip de ulaşılabilir | "Diğer" tam görünür `[862,821][1038,947]` |
| Süzme | her kategori kendi satırını gösterdi | Diğer 10,00 · Sağlık 20,00 · Üretkenlik 30,00 · Eğlence 50,00 (Tümü 110,00) |

- **Boş kategori:** `"No subscriptions in this category, Try another category"`
  — ilk boş durum `"No subscriptions yet, Tap + to add one"`. İkisi de tek
  erişilebilirlik düğümü ve ikisi de `[84,1052][996,1417]` kutusunda.
- **Depo boşken** bir kategori seçilirse yine **ilk** boş durum çıkıyor
  ("...yet, Tap + to add one"), yani `hasAnySubscriptions` dalı cihazda da
  doğru — `uiState_storeIsEmpty_saysSoRegardlessOfTheFilter` testiyle aynı
  sonuç.
- **Sil + geri al (filtre açık):** Üretkenlik seçiliyken tek satır silindi →
  filtre boş durumu + Snackbar; "Geri al" satırı geri getirdi, filtre
  Üretkenlik'te kaldı, toplam 30,00'a döndü. "Tümü"de sıra da eski hâlinde.
- **Döndürme:** filtre korunuyor. Yazı tipi ölçeği değiştirilince (yapılandırma
  değişikliği) hem filtre hem **çubuğun kaydırma konumu** korunuyor.
- **Yeniden başlatma:** "Tümü"ye dönüyor (kalıcı değil, beklenen).
- **fs 2.0:** chip'ler sarmıyor, metin kırpılmıyor ("Entertainment"
  `[271,938][711,1036]`), çubuk sonuna kadar kayıyor ve "Diğer" seçilip
  süzebiliyor. Chip yüksekliği 126 px.
- **Koyu tema:** filtre çubuğu okunur, seçili chip `primaryContainer` zemininde,
  seçilmeyenler çerçeveyle ayrışıyor; ekran görüntüsü alındı.
- **Türkçe:** çubuk `Tümü / Eğlence / Üretkenlik / Sağlık / Diğer` diye geliyor
  (API 34, `cmd locale set-app-locales`).

**Erişilebilirlik — chip başına tek durak**
Filtre chip'i ağaçta bir `View` (odaklanabilir, `checkable=true`, seçiliyse
`checked=true`) ve içinde odaklanamayan iki çocuk: etiket `TextView` ve bir
`CheckBox`. Yani çubuk **beş** durak ekliyor, chip başına bir tane; fazladan
veya etiketsiz düğüm yok. Bu, formdaki kategori ve para birimi chip'lerinin
yapısıyla aynı (stok `FilterChip`).

**Sabit regresyon listesi — 55 → 61 madde, ikisinde de koşuldu**
11b'nin altı maddesi eklendi (56-61). Liste **iki emülatörde de baştan sona**
koşuldu ve geçti. Kayıtlı istisnalar:

- **API 34:** #40 ve #46 yalnızca API < 33 maddeleri.
- **API 29:** #15 ölçülemedi — `cmd uimode night yes` bu imajda "Night mode: no"
  dönüyor, tema değişmiyor. #16 ölçülemedi — `cmd locale` servisi API 33
  öncesinde **yok** ("Can't find service: locale"); cihaz dilini değiştirmek
  çerçeve yeniden başlatması istiyor. Filtre çubuğunun Türkçesi bu yüzden API
  34'te doğrulandı. #34-#37 ve #41-#45 API 33+ davranışları; karşılıkları #40
  ve #46 koşuldu ve geçti.
- #38 ve #39 **iki AVD'de de** koşuldu: sistem ayarından bildirim kapatılıp
  açıldığında satır aynı süreçte (pid değişmeden) güncelleniyor. #39 için kanal
  gerekiyor — kanal ilk bildirimle doğduğu için `PaymentReminderWorkerTest`
  `am instrument` ile koşuldu; kanal kapatılınca satır "Kapalı — sistem
  ayarlarından açılmalı" dedi, uygulama izni açık olsa bile.

**Değişen dosyalar**
- `ui/common/CategoryFilterBar.kt` — yeni
- `ui/common/EmptyState.kt` — `EmptyCategory()` eklendi, `EmptyState` imzası aynı
- `ui/home/HomeUiState.kt`, `HomeViewModel.kt`, `HomeScreen.kt`
- `ui/home/components/AddSubscriptionSheet.kt` — kategori + `CategorySaver`
- `res/values/strings.xml`, `res/values-en/strings.xml`
- `test/fake/FakeSubscriptionRepository.kt` — DAO gibi sıralıyor
- `test/ui/home/HomeViewModelFilterTest.kt` — yeni
- `test/ui/home/HomeViewModelCategoryTest.kt` — yeni yüzeye göre yazıldı
- `docs/ARCHITECTURE.md` §5, `docs/ROADMAP.md`, `docs/TESTING.md`

**Commit'ler**
- `133a373` refactor: let the add form own the category like its other fields
- `768ce32` feat: narrow the list to one category
- `5e7c173` feat: say when a category has nothing in it
- `6d61411` test: cover the category filter and make the fake sort like the DAO

**Karşılaşılan sorunlar**
- **Sahte repository sıralamayı taklit etmiyordu** (yukarıda). Ders: bir fake
  sözleşmeyi taklit ediyorsa, sözleşmenin sırası da sözleşmenin parçasıdır.
- **`input swipe ... 400` bazen silmiyor.** Aynı komut emülatör yeniden
  başlatıldıktan sonra üç kez üst üste hiçbir şey yapmadı, 700 ms ile her
  seferinde sildi. TESTING.md'ye tuzak olarak yazıldı.
- **`connectedDebugAndroidTest` uygulamayı siliyor.** Gradle koşum sonunda hem
  test hem uygulama APK'sını kaldırıyor; kanalı yaratmak için koşulan test,
  ölçülecek kurulumu da götürdü. Çözüm: APK'ları elle kurup `am instrument`
  çağırmak.
- **`pm revoke` kalıcı reddi taklit etmiyor.** Revoke sonrası satır yine
  "Kapalı — açmak için dokunun" diyor; **kalıcı ret** ancak gerçek sistem
  diyaloğunda iki kez reddedilerek üretilebiliyor. (10c-1'deki "pm revoke
  süreci öldürüyor" notunun yanına.)

**Rapor edilen, düzeltilmeyen**
- Depoda hiç abonelik yokken filtre çubuğu **görünmeye devam ediyor**. Promptta
  gizlenmesi istenmedi, kapsam dışı bırakıldı; davranış zararsız çünkü boş
  depoda hangi chip seçilirse seçilsin ekran "Henüz abonelik yok" diyor.
  Faz 14'te ekran yeniden ele alınırken karara bağlanabilir.

**Sonraki faz için not**
- Faz 12 ödeme periyodunu getirince toplam "aylık normalize" olacak; filtre
  süzmeyi ondan **önce** yapıyor, yani sıra değişmemeli ama `totalIn`
  değiştiğinde `HomeViewModelFilterTest`'teki toplam beklentileri gözden
  geçirilmeli.
- Filtre kalıcı yapılmak istenirse yeri `SettingsRepository` değil, ekranın
  kendi `SavedStateHandle`'ı olur — bugünkü karar "kalıcı değil".

---

## [Faz 11a] Kategori Seçimi ve Gösterimi — 2026-09-05

**Durum:** Tamamlandı. **Faz 11 KAPANMADI** — filtre ve kategori bazlı toplam
11b'nin işi, ROADMAP'teki o maddeler açık bırakıldı.

**Domain kontrolü — enum spec'le uyuşuyor, dokunulmadı**
`SubscriptionCategory` dört sabit taşıyor: `ENTERTAINMENT`, `PRODUCTIVITY`,
`HEALTH`, `OTHER`. PROJECT_SPEC §4'teki "Eğlence / Üretkenlik / Sağlık / Diğer"
ile birebir. Durup sormayı gerektiren bir fark yok. Mapper'daki `OTHER`
fallback'ine (ARCHITECTURE §12) dokunulmadı; Room şeması ve `app/schemas/`
değişmedi.

> Not: prompt "beş chip" diyordu, kategori **dört** tane.

**Yapılanlar**
- `ui/common/CategorySelector` — `CurrencySelector`'ın deseni: `FlowRow` +
  `FilterChip`, tek dokunuş. Para birimi chip'lerindeki `contentDescription`
  ezmesi burada **yok**, çünkü o ezme ISO kodunun ("TRY") kelime gibi
  okunmasını düzeltmek içindi; kategori chip'i zaten anlamı olan kelimeyi
  gösteriyor.
- `SubscriptionCategory.labelRes()` — exhaustive `when`, UI katmanında.
  Enum İngilizce kalıyor ve ekrana hiç çıkmıyor; domain'e string resource
  ID'si sokulmadı.
- `HomeUiState.selectedCategory` + `HomeEvent.SelectCategory`. Kayıtta
  repository'ye gidiyor; `clearedErrors` içinde `OTHER`'a dönüyor, yani
  açılış, vazgeçme ve başarılı kayıt üçü de formu sıfırlıyor.
- `SubscriptionCard`'da kategori satırı, **yalnızca `OTHER` değilse**.

**Formdaki yeri — para birimi chip'lerinin hemen altı**
Gerekçe: iki chip sırası formdaki tek "seçim" öğesi, bir arada okunmaları
dağınık durmalarından iyi. Para biriminden **sonra**, çünkü para birimi
üstündeki fiyatın anlamını değiştiriyor; kategori formda başka hiçbir şeyi
değiştirmiyor. Tarihten **önce**, çünkü tarih bir diyalog açıp sırayı bitiriyor.

**Kartta gösterim kararı**
Kart bugün ad, fiyat ve geri sayım taşıyor; dördüncü öğe sıkışıklık riski.
Seçilen çözüm: **adın altında ayrı bir satır, yalnızca kategori `OTHER`
değilken**. Üç gerekçe:

1. Dikey bir satır adın **yatay** alanından hiçbir şey almıyor — fs 2.0'daki
   bilinen ad kırpılması bu yüzden kötüleşemez.
2. `OTHER` "cevap yok" demek; her satıra yazmak listenin tamamında anlamsız
   bir kelime tekrarı olurdu.
3. Aynı kural geri sayımda zaten var: "tarihi olmayan satırlar eski
   yüksekliğini korur, olmayan bir şey için yer ayırmaz".

Renk `onSurface`. `onSurfaceVariant` **bilerek kullanılmadı** — tanımsız ve
Material baseline'ının mor-grisine düşüyor (ARCHITECTURE §12). Hiyerarşiyi
punto farkı taşıyor.

**Erişilebilirlik cümlesi**
Kategori okunan cümleye de eklendi, yoksa duyurulmazdı (8a dersi). Dört ayrı
string yerine iki adımda kuruluyor: ad/fiyat (+ varsa geri sayım) cümleyi
yapıyor, sonra kategori varsa `subscription_row_description_with_category` ile
ekleniyor. Dört kombinasyon dört çeviri gerektirirdi.

**Testler**
- 136 → **143 birim testi** (7 yeni), 0 hata.
- Kapsanan: seçim state'e yansıyor · kayıt doğru kategoriyle gidiyor ·
  seçilmezse `OTHER` · kayıttan sonra `OTHER` · vazgeçmeden sonra `OTHER` ·
  reddedilen kayıtta seçim korunuyor.
- Mapper'ın enum dönüşümü **tekrar edilmedi**: `SubscriptionMapperTest` iki
  yönü ve tanınmayan isim fallback'ini zaten kapsıyor (kontrol edildi).
- `lintDebug` **0 hata, 20 uyarı** — sayı değişmedi.

**Emülatör sonuçları**

**(a) Chip'ler ve yerleşim** — dört chip **tek satıra sığmıyor**, `FlowRow`
sardı. Yatay kaydırma gerekmedi, kırpılma yok.

| | API 29 (360dp) | API 34 (411dp) |
|---|---|---|
| 1. satır | Eğlence `[48,767][291,863]`, Üretkenlik `[307,767][523,863]` | Eğlence `[63,1507][379,1633]`, Üretkenlik `[400,…]`, Sağlık `[702,…][894,1633]` |
| 2. satır | Sağlık `[48,879][195,975]`, Diğer `[211,879][345,975]` | Diğer `[63,1654][239,1780]` |

Tek satır gerekseydi API 29'da 788 px lazımdı, kullanılabilir genişlik 624 px.

**(b)** Eğlence seçilip kaydedildi → kart: **`"Netflix, TRY 159.99, Entertainment"`**
**(c)** Dokunmadan kaydedildi → kart: **`"Plain, TRY 20.00"`** (kategori yok)
**(d)** Kayıttan sonra FAB → `Diğer = checked`, diğer üçü `false`, alanlar boş
**(e)** Seçiliyken döndürüldü → seçim korundu (API 29 Eğlence, API 34 Sağlık)

**(f) fs 2.0 karşılaştırması** — aynı dump içinde kategorisiz satır referans:

| | kategorisiz | kategorili |
|---|---|---|
| API 29 fs 1.0 | 144 px | 166 px |
| API 29 fs 2.0 | 162 px | 290 px |
| API 34 fs 1.0 | 189 px | 217 px |
| API 34 fs 2.0 | 212 px | 380 px |

Kategorili satır fs 2.0'da belirgin biçimde uzuyor. Bu **büyüme**, kırpılma
değil: kart uzuyor, liste kaydırılıyor, hiçbir şey kesilmiyor.
**Yatay kırpılma ölçülemedi** — kart tek birleşik erişilebilirlik düğümü
olduğu için içindeki ad kutusunun sınırları ağaçta yok. Kategorinin adın
yatay alanını almadığı **kod düzeyinde kesin** (aynı `Column` içinde ayrı bir
`Text`, `Row`'daki `weight(1f)` dağılımı değişmiyor), ama bu bir ölçüm değil.

**(g) Erişilebilirlik**
- Chip'ler `checkable="true"`; seçili olan `checked="true"`. Para birimi
  chip'leriyle aynı yapı (9b-2'de doğrulanmıştı).
- Kart hâlâ **tek düğüm** — 8a'daki tek odak durağı bozulmadı. API 29
  `[0,560][720,726]`, API 34 `[0,989][1080,1206]`.
- Dört parçalı cümle de doğrulandı: **`"Dated, TRY 30.00, 6 days left, Health"`**

**(h) Klavye açıkken Kaydet** — yeni alan Kaydet'i itmedi:

| | klavye açık | klavye kapalı |
|---|---|---|
| API 29 | `[329,634][391,674]` | buton kutusu `[48,1104][672,1200]` — **10a referansıyla aynı** |
| API 34 | `[500,1323][580,1376]` | `[500,2143][580,2196]` — 10c-2 ölçümüyle aynı |

**(i) Regresyon — 55 maddelik liste**
Sabit listeye 11a'nın beş maddesi eklendi (51-55), liste 50 → **55**.
50 maddenin tamamı iki emülatörde koşuldu ve geçti. Kayıtlı istisnalar:
**#15/#16** API 29'da ölçülemiyor (API 34'te geçti); **#34-#37, #41-#45**
API 33+ davranışları, API 29'daki karşılığı **#46** ve o geçti; **#40**
yalnızca API < 33 maddesi.

**Değişen dosyalar**
- `ui/common/CategorySelector.kt` — yeni
- `ui/home/HomeUiState.kt`, `HomeViewModel.kt`, `HomeScreen.kt`
- `ui/home/components/AddSubscriptionSheet.kt`, `SubscriptionCard.kt`
- `res/values/strings.xml`, `res/values-en/strings.xml`
- `test/ui/home/HomeViewModelCategoryTest.kt` — yeni
- `docs/ROADMAP.md`, `docs/TESTING.md`

**Bir tutarsızlık — raporlanıyor, düzeltilmedi**
Prompt kategoriyi `HomeUiState` + `HomeEvent` üzerinden istedi ve öyle yapıldı.
Ama formun diğer alanları (ad, fiyat, para birimi, tarih) sheet'in kendi
`rememberSaveable` state'inde duruyor — sheet'in KDoc'u bunu "geçici görsel
durum, ARCHITECTURE §3 izin veriyor" diye gerekçelendiriyor. Yani kategori
kardeşlerinden farklı bir yerde yaşıyor.

İhlal değil (ViewModel her zaman state tutabilir) ve pratikte iki avantajı
oldu: döndürmede seçim bedava korunuyor ve sıfırlama tek yerde. Ama beş form
alanından dördü bir yerde, biri başka yerde. Para birimi ve tarihi de
ViewModel'a taşımak mı, kategoriyi sheet'e indirmek mi — karar sohbetin.

**Sonraki faz için not**
- 11b: filtre, kategori bazlı toplam, filtre boşluğu (`EmptyState` varyantı).
- Faz 14'te kart yeniden ele alınırken fs 2.0'da kategorili satırın yüksekliği
  göz önünde bulundurulmalı; bugün kırpılma yok ama satır iki katına çıkıyor.

---

## [Faz 8b] Boş Durum Ekranı — 2026-09-05

**Durum:** Tamamlandı. **Faz 8'in tamamı kapandı.**

**Yapılanlar**
- `ui/common/EmptyState` — ikon, başlık ve alt satırı **parametre** alan bir
  composable. Faz 11'deki filtre boşluğu aynı bileşenin varyantı olacak;
  o varyant bu fazda yazılmadı.
- `ui/common/EmptySubscriptions` — abonelik listesi için hazır metinlerle
  sarmalayan ince bir katman.
- `HomeScreen`: `!isLoading && subscriptions.isEmpty()` olduğunda boş durum,
  değilse liste. Dashboard kartı ve FAB her iki durumda da yerinde.
- Düğme yok. FAB zaten sağ altta; ikinci bir giriş noktası aynı odaya iki kapı
  açmak olurdu.

**İkon seçimi — yeni ikon eklenmedi**
`Icons.AutoMirrored.Filled.List` seçildi. Gerekçe: bu ikon **zaten pakette**,
`SubscriptionCard` Spotify için kullanıyor. Faz 16'da
`material-icons-extended` daraltılacak ve o listeye yeni bir isim eklememek
önemliydi. Anlamı da doğru: boş bir listenin yerinde bir liste simgesi duruyor.

Mevcut ikon kümesi (9 isim): `ArrowBack`, `List`, `Add`, `Cloud`, `DateRange`,
`Delete`, `PlayArrow`, `Settings`, `Star`. Bu faz **sayıyı artırmadı.**

**Renk rolleri — `onSurfaceVariant` bilerek kullanılmadı**
ARCHITECTURE §12'ye göre o rol şemamızda **tanımsız** ve Material baseline'ının
mor-grisine düşüyor. Bunun yerine:

| Öğe | Rol | Neden |
|---|---|---|
| İkon | `primary` (DeepBlue) | §12: metin/ikon `primary` ailesinden, dolu yüzeyler `*Container`. `primaryContainer` ikon olarak beyaz üstünde 1.78:1 verirdi |
| Başlık | `onSurface` | Tanımlı |
| Alt satır | `onSurface` | Tanımlı. Alpha ile soluklaştırılmadı — DashboardCard'ın 8a'daki dersi: 0.7 alpha kontrastı 3.6:1'e düşürüyor, hiyerarşiyi boyut/ağırlık farkı zaten taşıyor |

Boyutlar `Dimens`'e eklendi: `EmptyStateIconSize = 72.dp`, `EmptyStatePadding = 32.dp`.

**Erişilebilirlik — `clearAndSetSemantics`, `mergeDescendants` değil**
Prompt "8a'daki `mergeDescendants` deseni" diyordu, ama `DashboardCard`'ın
kendi yorumu bunun **yetmediğini** kaydediyor: birleştirme çocukları ağaçta
bırakıyor (erişilebilirlik köprüsü birleştirilmemiş ağacı yürüyor), kart yine
üç durak veriyordu. Orada `clearAndSetSemantics` ile çözülmüştü; burada da o
kullanıldı. Ölçüm doğruladı: iki emülatörde de **tek düğüm**.

**Yükleme ile çakışma**
`isLoading` ve liste **aynı emisyonda** geliyor — `combine` bloğu ikisini
birlikte üretiyor. Yani "yükleniyor bitti ama liste henüz gelmedi" diye bir kare
yok; boş durum yalnızca listenin gerçekten boş olduğu bilindiğinde çiziliyor.
8a'daki 300 ms gecikmeli gösterge de yerinde duruyor. Ölçüldü: veri varken
açılışta boş durum **hiç** görünmüyor (iki dump'ta da 0 eşleşme).

**Testler**
- Birim testi yazılmadı; bu saf UI. **136 test** değişmedi, 0 hata.
- `lintDebug` **0 hata, 20 uyarı** — uyarı sayısı da değişmedi.
- `HomeScreenEmptyPreview` zaten `isLoading = false` ile duruyordu, artık yeni
  boş durumu render ediyor. Bileşenin kendi preview'ı da eklendi.

**Emülatör sonuçları**

| Ölçüm | API 29 (720x1280) | API 34 (1080x2400) |
|---|---|---|
| (a) boş durum düğümü | `[64,624][656,902]` | `[84,884][996,1249]` |
| (a) dashboard | `[32,208][688,433]` "Total Monthly, TRY 0.00" | `[42,338][1038,633]` |
| (a) FAB | `[608,1168][656,1216]` | `[933,2190][996,2253]` |
| (c) abonelik eklendi | Boş durum gitti, liste geldi | aynı |
| (d) silindi | Boş durum geri geldi | aynı |
| (d) Snackbar | `[56,1044][313,1084]` — boş durumun altı 902, **142 px boşluk** | `[74,2026][415,2079]` — üstü 1249, **777 px boşluk** |
| (e) fs 2.0 | `[64,703][656,1044]` — 278 → 341 px, kırpılma yok, FAB'la çakışma yok | `[84,987][996,1434]` — 365 → 447 px, aynı |
| (g) ağaçtaki hâli | **1 düğüm**, "No subscriptions yet, Tap + to add one" | aynı, aynı isim |

**(b) yükleme/boş durum çakışması:** veri varken uygulama kapatılıp açıldı ve
iki ayrı dump alındı; ikisinde de `No subscriptions yet` **0 kez** geçti,
ardından liste göründü.

**(f) koyu tema (API 34):** boş durum ağaçta, uygulama çökmedi
(`logcat -b crash` uygulama için 0 satır). Kullanılan roller `primary` ve
`onSurface`; ikisi de tanımlı ve daha önceki fazlarda ölçülmüş. **Renk
kontrastının kendisi dump'tan ölçülemez** — okunabilirlik iddiası bu ölçümle
kanıtlanmadı, yalnızca çizildiği ve çökmediği doğrulandı.

**Regresyon — 50 maddelik liste**

Sabit listeye 8b'nin dört maddesi eklendi (47-50), liste 46 → **50**.
Tamamı iki emülatörde koşuldu ve geçti. Kayıtlı istisnalar: **#15/#16** API
29'da ölçülemiyor (API 34'te geçti); **#34-#37 ve #41-#45** API 33+ davranışları,
API 29'da karşılığı **#46** ve o geçti; **#40** yalnızca API < 33 maddesi.

**Ölçüm sırasında çıkan engel**
Geniş emülatör taze açılışta iki kez sistem ANR diyaloğu gösterdi
("Process system isn't responding" / "System UI isn't responding"). İlki
"Wait" tuşuna basılmasına rağmen kapanmadı — `system_server` yanıt vermiyordu —
ve emülatör yeniden başlatıldı. Uygulamayla ilgisi yok: diyalog altındaki
`topResumedActivity` bizim `MainActivity`'mizdi ve crash logu boştu.

**Değişen dosyalar**
- `ui/common/EmptyState.kt` — yeni
- `ui/home/HomeScreen.kt` — bağlama
- `ui/theme/Dimens.kt` — iki yeni değer
- `res/values/strings.xml`, `res/values-en/strings.xml`
- `docs/ROADMAP.md`, `docs/TESTING.md`

**Sonraki faz için not**
- Faz 8 ve Faz 10 kapandı. Sırada Faz 11 (kategoriler); oradaki filtre boşluğu
  `EmptyState`'in varyantı olacak, ROADMAP'e yazıldı.
- fs 2.0'da uzun metin kırpılması Faz 14'ün konusu; bu bileşende kırpılma
  görülmedi ama ölçüm yalnızca blok sınırlarından yapıldı, tek tek satırlardan
  değil (`clearAndSetSemantics` çocukları ağaçtan kaldırıyor).

---

## [Faz 10c-2] Bağlamsal İzin İsteği — 2026-09-05

**Durum:** Tamamlandı. **Faz 10c ve Faz 10'un tamamı kapandı.**

**Yapılanlar**
- `HomeUiState`'e tek seferlik `shouldRequestNotificationPermission` bayrağı ve
  `HomeEvent.NotificationRequestHandled`. Yeni `Channel`/`SharedFlow` açılmadı
  (ARCHITECTURE §5).
- `HomeViewModel.armNotificationRequest` — kayıt başarılı olduktan sonra çalışan,
  altı koşullu bir kapı: tarih var mı · bildirimler zaten görünüyor mu · bu
  derlemede runtime izin gerekiyor mu · izin zaten verili mi · daha önce
  sorulmuş mu · listedeki tarihli abonelik sayısı 1 mi.
- Sayım, **mevcut** `repository.observeAll()` bir kez okunarak yapılıyor. Yeni
  DAO sorgusu, yeni repository fonksiyonu, yeni DataStore anahtarı açılmadı.
  `uiState`'ten okunmadı çünkü arkasındaki Flow yeni satır için henüz emit
  etmemiş olabilir.
- `HomeScreen`'e koşulsuz kayıtlı `RequestPermission` launcher'ı ve sıralamayı
  kuran efekt.

**Sıralama nasıl kuruldu**

Sheet'i ağaçta tutan koşulun tersi kullanıldı:

```kotlin
val isAddSheetGone = !uiState.isAddSheetOpen && !sheetState.isVisible
LaunchedEffect(uiState.shouldRequestNotificationPermission, isAddSheetGone) { … }
```

`isAddSheetOpen` durumun kapandığını, `sheetState.isVisible` gizlenme
animasyonunun bittiğini söyler; istek ancak ikisi de sağlandığında gönderiliyor.
Tetik gönderildiği anda `NotificationRequestHandled` ile temizleniyor.

**Bayrak yazımı — prompttan bilinçli sapma**

Prompt "bayrak yazma işi 10c-1'deki akışın sahibinde kalsın, iki yerden
yazılmasın" diyordu; aynı promptun ürün kuralı ise "reddedilirse **bayrak
yazıldığı için** bu yol bir daha çalışmaz" diyordu. İkisi birlikte tutmuyor:
`HomeViewModel` yazmazsa bayrak `false` kalır, Ayarlar satırı kullanıcıya az
önce sorulduğunu bilmez ve açıklama diyaloğunu atlar; tarihli aboneliklerin
hepsi silinip yenisi eklenirse istek tekrar çıkar.

**Aynı anahtar, iki çağrı yeri** seçildi: `HomeViewModel` de
`reminderState.setPermissionRequested()` çağırıyor. İkinci bir anahtar
açılmadı. Gerekçe ARCHITECTURE §18'e yazıldı.

**Testler**
- 127 → **136 birim testi** (9 yeni), 0 hata.
- Kapının her dalı `HomeViewModel`'ın public yüzeyinden test edildi: tarihli ilk
  kayıt · tarihsiz kayıt · tarihli ikinci kayıt · daha önce sorulmuş · zaten
  görünür · izin verili ama bildirim kapalı · API < 33 · tetiğin tüketilmesi ·
  doğrulamada takılan kayıt.
- `lintDebug` **0 hata, 19 → 20 uyarı**. Tek yeni uyarı `HomeScreen.kt:125`,
  `InlinedApi` (`POST_NOTIFICATIONS`, API 33 sabiti) — diğer üçüyle aynı sınıf,
  derleme anında satır içine alınan `String` sabiti.

**Emülatör turu — API 34**

| Adım | Kanıt |
|---|---|
| (a) tarihli ilk abonelik, Kaydet | Diyalog çıktığı anda alınan dump'ta **sheet ağaçta yok** (`New Subscription` sayısı 0). Pencere listesinde yalnızca `GrantPermissionsActivity` ve `MainActivity` var — sheet'in penceresi de yok |
| (b) Allow | `granted=true` · kayıt "Netflix, TRY 159.99, 6 days left" · Ayarlar satırı **"Payment reminders, On"** |
| (c) `pm clear` + **tarihsiz** abonelik | `permissioncontroller` düğüm sayısı **0**, izin hâlâ `granted=false`, kayıt oluştu ("NoDate, TRY 20.00") |
| (d) aynı turda tarihli abonelik | Diyalog **çıktı**, sheet yine ağaçta değil. "Don't allow" → `granted=false, USER_SET` |
| (e) ikinci tarihli abonelik | `permissioncontroller` **0** — tek sefer kuralı tuttu. Kayıt oluştu ("Second, TRY 40.00, 6 days left") |
| (f) döndürme | Diyalog çıktı (`grant_dialog` = 1) → yatay çevrildi → hâlâ **1** → kapatıldı → **0** → dikeye dönüldü → **0**. İkinci diyalog yok |
| — | İzin verilmişken (ENABLED) tarihli abonelik eklendi → `permissioncontroller` **0** |

**(f) nasıl kurgulandı:** tetik anını yakalamak yerine diyalog açıkken
döndürüldü. Kod tetiği gönderdiği anda temizlediği için asıl risk buradadır:
ekran yeniden kurulurken bayrak hâlâ duruyorsa istek ikinci kez gider.
Ölçümde gitmedi.

**Emülatör turu — API 29**

Tarihli ilk abonelik kaydedildi: `permissioncontroller` düğüm sayısı **0**,
kayıt normal oluştu ("Dated, TRY 30.00, 6 days left"), `logcat -b crash` boş.
Beklenen davranış: `CAN_REQUEST` dalı o platformda hiç oluşmuyor.

**Regresyon — 46 maddelik liste**

Sabit listeye 10c-2'nin altı maddesi eklendi (41-46), liste 40 → **46**.
Tamamı iki emülatörde koşuldu ve geçti. Kayıtlı istisnalar: **#15 (koyu tema)**
ve **#16 (dil)** API 29'da ölçülemiyor, API 34'te koşuldu ve geçti;
**#34-#37** yalnızca API 33+ maddeleri, API 29'da konu dışı.

Seçme kanıtlar: #11 toplam tam **219.89** · #19 undo satırı eski sırasına
döndü (`[0,560][720,704]` / `[0,800][1080,989]`) · #23 dört geçersiz kur da
alan altında hata verdi · #26 klavye açıkken `ScrollView` küçüldü ve iki butona
da ulaşıldı · #27 11 Eylül seçildi, cihaz tarihi 5 Eylül → **"6 days left"** ·
#31 31.12.2040 reddedildi, kaydedilmedi · #39 kanal `mImportance=0` iken
uygulama izni `granted=true` kaldı ve satır doğru şekilde kapalı dedi
(pid iki cihazda da değişmedi: 16610 ve 14825).

**Ölçüm sırasında öğrenilen**

`pm revoke` gibi, **sistem ayarlarından bildirimleri kapatmak da API 33+'ta
uygulama sürecini öldürüyor** (pid 14061 → 14350). Açmak öldürmüyor (10c-1'de
6715 → 6715 ölçülmüştü). Yani `ON_RESUME` tazelemesi yalnızca "açma" yönünde
süreç korunarak gösterilebiliyor; kapatma yönünde satır yine doğru güncelleniyor
ama süreç yeni.

**Değişen dosyalar**
- `ui/home/HomeUiState.kt` — tetik alanı + olay
- `ui/home/HomeViewModel.kt` — `armNotificationRequest`
- `ui/home/HomeScreen.kt` — launcher ve sıralama
- `test/ui/home/HomeViewModelReminderTriggerTest.kt` — yeni
- `test/ui/home/HomeViewModelTest.kt` — yeni fake'ler
- `docs/ARCHITECTURE.md` §18, `docs/TESTING.md`, `docs/ROADMAP.md`

**Sonraki faz için not**
- Faz 10 kapandı. Sırada ROADMAP'e göre Faz 11 (kategoriler); Faz 8b (boş durum
  ekranı) hâlâ tasarım kararı bekliyor.
- Faz 12 tarih ilerletmeyi getirdiğinde 1-3 günlük gecikme penceresi yeniden
  değerlendirilecek (ARCHITECTURE §18).
- Ayarlar satırlarının erişilebilirlik ağacında iki düğüm vermesi ROADMAP
  Faz 16'daki TalkBack maddesinde duruyor; bu fazda değişmedi.

---

## [Faz 10c-1] Bildirim Durumu ve İzin Akışı (Ayarlar) — 2026-09-05

**Durum:** Tamamlandı. **Faz 10c KAPANMADI** — bağlamsal tetikleyici (tarihli
abonelik kaydedilince sorma) 10c-2'nin işi, ROADMAP'teki 10c maddesi o bitene
kadar işaretlenmiyor.

**Yapılanlar**
- `ReminderNotificationStatus` — bildirimin fiilen görünüp görünmediği için tek
  kaynak. Kontrol iki aşamalı: uygulama anahtarı **ve** kanal importance'ı.
  Arayüz + `AndroidReminderNotificationStatus` gerçeklemesi, `@Binds` ile
  `di/ReminderModule`'de bağlandı. Arayüz olmasının sebebi test: durum
  makinesinin yedi dalı gerçek bir `NotificationManager` üzerinden
  koşturulamaz.
- `PaymentReminderNotifier` artık kararı bu sınıftan alıyor; kendi içindeki
  `areNotificationsEnabled` kontrolü kaldırıldı. Kanal id'si tek yerde
  (`ReminderNotificationStatus.CHANNEL_ID`).
- `ReminderStateRepository`'ye `wasPermissionRequested` / `setPermissionRequested`.
  Mevcut `DataStore<Preferences>` örneği; yeni repository veya ikinci instance
  yok (§14).
- `SettingsUiState` üç halli `ReminderPermissionState` ve iki eylemli
  `ReminderPermissionAction` kazandı. Karar tamamen ViewModel'da; composable
  yalnızca Activity gerektiren işi yapıyor ve sonucu `onEvent` ile bildiriyor.
  Yeni `Channel`/`SharedFlow` açılmadı.
- Ayarlar ekranına "Ödeme hatırlatmaları" satırı. Kur satırıyla aynı desen;
  ikisi de artık ortak `ui/common/SettingsRow` bileşeninden geliyor.
- `LocalActivity` kullanıldı (activity-compose **1.12.4**, sınıfın varlığı
  `unzip -l classes.jar` ile doğrulandı). `ContextWrapper` zinciri yürüyen
  yardımcı yazılmadı.
- `SettingsScreen.kt` bölünmeden 310 satır olmuştu (CLAUDE.md §4 sınırı 300);
  satır bileşeni `ui/common/SettingsRow.kt`'ye, Activity yardımcıları
  `ui/settings/ReminderPermissionActions.kt`'ye taşındı → 214 satır.

**Karar tablosu** ARCHITECTURE §18'de. Özet sıra: görünüyorsa `ENABLED`;
API < 33 ise `SETTINGS_ONLY`; izin var ama görünmüyorsa `SETTINGS_ONLY`;
hiç sorulmamışsa `CAN_REQUEST`; sorulmuş + rationale varsa `CAN_REQUEST`;
kalan `SETTINGS_ONLY`.

**Testler**
- 112 → **127 birim testi** (15 yeni), 0 hata. Yedi dalın tamamı ve dokunma
  davranışları ViewModel'ın public yüzeyinden test edildi.
- Yeni fake'ler: `FakeReminderStateRepository`, `FakeReminderNotificationStatus`.
  Mock kütüphanesi yok (§11).
- `lintDebug` **0 hata, 19 uyarı** (15 → 19). Dört yeni uyarının hepsi
  `InlinedApi`: `POST_NOTIFICATIONS` (×2, API 33 sabiti),
  `ACTION_APP_NOTIFICATION_SETTINGS` ve `EXTRA_APP_PACKAGE` (API 26 sabitleri).
  Üçü de derleme anında satır içine alınan `String` sabitleri; eski cihazda
  çalışma zamanı sorunu değil, zaten `ActivityNotFoundException` fallback'i ve
  API < 33 dalı bu yüzden var.
- Enstrümantasyon testi bu fazda yazılmadı (izin diyaloğu sistem UI'ı).

**Lint yüzünden kalan ikinci kontrol — kabul edilen sapma**
Prompt "notifier'da iki ayrı kontrol kalmasın" diyordu. Karar tek yerde
(`status.areRemindersVisible()`), ama `notify()` ile **aynı fonksiyonda** bir
`ContextCompat.checkSelfPermission` satırı bırakmak zorunda kaldım: lint'in
`MissingPermission` denetimi arayüzün arkasını görmüyor ve kaldırıldığında
derleme `Call requires permission which may be rejected by user` hatasıyla
duruyor. `@SuppressLint` yasak (CLAUDE.md §4), `@RequiresPermission` ile
yukarı taşımak worker'a yanlış bir söz yazmak olurdu. Satır, sebebi yazılarak
bırakıldı.

**Emülatör turu — API 34, tek tur, sırayla**

| Adım | Kanıt |
|---|---|
| (a) `pm clear` sonrası | `granted=false` · satır: **"Payment reminders, Off — tap to turn on"** |
| (b) satıra dokun | `com.android.permissioncontroller:id/grant_dialog` + `permission_allow_button` / `permission_deny_button`. Uygulamanın diyaloğu **yok** |
| (c) Allow | `granted=true` · satır: **"Payment reminders, On"** |
| (e) bir kez reddedip satıra dokun | **Uygulamanın diyaloğu**: "Allow reminders" / "You get at most one notification a day…" / Cancel · Ask for permission |
| (e devam) sistem diyaloğu | İkinci soruda düğme `permission_deny_and_dont_ask_again_button` |
| (f) kalıcı ret | `granted=false, flags=[USER_SET\|**USER_FIXED**\|…]` · satır: **"Off — turn on in system settings"** · dokununca `com.android.settings/.Settings$AppNotificationSettingsActivity` |
| (d) sistem ayarlarından aç, geri dön | pid **6715 → 6715** (yeniden başlatma yok) · satır: **"Payment reminders, On"** |
| (g) yalnızca kanalı kapat, geri dön | kanal `mImportance=0`, uygulama izni **`granted=true`**, pid **7175 → 7175** · satır: **"Off — turn on in system settings"** |
| (h) fs 2.0 | satır `[42,1214][1038,1560]`, durum metni `[42,1332][1038,1528]` — iki satıra sarıyor, kırpılma yok, üstteki kur satırıyla çakışma yok (`…1211` / `1214…`) |

**(d) hakkında bir düzeltme:** prompt `pm revoke` ile ON_RESUME tazelemesini
göstermeyi istiyordu, ama **`pm revoke` uygulama sürecini öldürüyor** (pid
6503 → yok). Bu yüzden tazeleme sistem ayarları üzerinden gösterildi; o yol
süreci öldürmüyor ve zaten gerçek kullanım senaryosu bu. Ölçüm TESTING.md'ye
yazıldı.

**Emülatör turu — API 29**

Prompt satırın `SETTINGS_ONLY` olmasını bekliyordu; **temiz kurulumda `ENABLED`
çıkıyor** ve bu doğru: API 29'da bildirimler varsayılan açık, durum makinesinin
ilk dalı kazanıyor. `SETTINGS_ONLY` yolu, bildirimler kapatılınca üretildi:

- (i) satıra dokun → `com.android.settings/.Settings$AppNotificationSettingsActivity`,
  ağaçta `permissioncontroller` düğümü sayısı **0** (izin diyaloğu hiç çıkmıyor).
- Sistem ayarlarından bildirimleri kapat, geri dön → pid **7293 → 7293**,
  satır: **"Payment reminders, Off — turn on in system settings"**.

**(j) Erişilebilirlik — bulundu, çözülemedi**

İki emülatörde de satır ağaçta **aynı sınırlarda iki düğüm** veriyor:

```
node class="android.view.View" content-desc=""                          clickable="true"  focusable="true"  bounds="[32,600][688,726]"
node class="android.view.View" content-desc="Payment reminders, Off — turn on in system settings"  clickable="false" focusable="false" bounds="[32,600][688,726]"
```

(API 34'te aynı yapı, `bounds="[42,854][1038,1020]"`.)

Denenenler: `semantics`'i `clickable`'dan **önce** koymak (değişmedi, hâlâ iki
düğüm) ve `Role.Button` eklemek (**üç** düğüme çıkardı). Bırakılan biçim,
adın odaklanabilir düğümün doğrudan altında olduğu iki-düğüm biçimi.

TalkBack'in üst düğüme odaklanıp alttakinin adını okuyup okumadığı **bu
imajlarda doğrulanamıyor** (Android Accessibility Suite yok, TESTING.md'de
kayıtlı). ROADMAP Faz 16'daki TalkBack maddesine eklendi.

**(k) Regresyon — 40 maddelik liste**

Sabit listeye 10c-1'in sekiz maddesi eklendi (33-40), liste 32 → **40**.
Tamamı iki emülatörde koşuldu ve geçti, iki bilinen istisnayla: **#15 (koyu
tema)** API 29'da ölçülemiyor, **#16 (dil)** API 29'da ayarlar arayüzü
gerektirdiği için koşulmadı; ikisi de API 34'te koşuldu ve geçti
("Aylık Toplam", "Aboneliklerim", "Ödeme hatırlatmaları, Kapalı — açmak için
dokunun", "4 gün gecikti", "6 gün kaldı").

Cihaz tarihi 5 Eylül 2026 olduğu için tarih maddeleri buna göre koşuldu:
11 Eylül → **"6 days left"**, 1 Eylül → **"4 days overdue"**, bugün →
**"Due today"**, 31.12.2040 → alan hatası ve **kaydedilmedi**.

Hiçbir metinde ham `%` görülmedi.

**Değişen dosyalar**
- `reminder/ReminderNotificationStatus.kt`, `reminder/AndroidReminderNotificationStatus.kt` — yeni
- `di/ReminderModule.kt` — yeni
- `reminder/PaymentReminderNotifier.kt` — kararı devretti
- `domain/repository/ReminderStateRepository.kt`, `data/repository/ReminderStateRepositoryImpl.kt`
- `ui/settings/SettingsUiState.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`
- `ui/settings/ReminderPermissionActions.kt`, `ui/common/SettingsRow.kt` — yeni
- `res/values/strings.xml`, `res/values-en/strings.xml`
- `test/fake/FakeReminderStateRepository.kt`, `FakeReminderNotificationStatus.kt` — yeni
- `test/ui/settings/SettingsViewModelReminderTest.kt` — yeni
- `docs/ARCHITECTURE.md` §18, `docs/TESTING.md`, `docs/ROADMAP.md`

**Doğrulama tamamlaması — 2026-09-05**

10c-1 kapanışında iki eksik vardı: notifier değiştiği hâlde enstrümantasyon
paketi yeniden koşulmamıştı, ve satırın ağaçtaki şekli mevcut satırlarla
karşılaştırılmamıştı. İkisi de kapatıldı, **üretim kodu değişmedi**.

**Enstrümantasyon — iki emülatörde de `OK (11 tests)`**

`am instrument` ile koşuldu (Gradle görevi uygulamayı kaldırıyor).

| Emülatör | Sonuç |
|---|---|
| `subtrack_narrow_api29` | `OK (11 tests)` |
| `subtrack_wide_api34` | `OK (11 tests)` |

API 29'da bildirim **gerçekten gitti** — notifier'daki yeni
`checkSelfPermission` satırının eski platformda hatırlatmayı susturmadığının
kanıtı:

```
EXTRA_TITLE   = [Payment reminder: 4 subscriptions]
EXTRA_TEXT    = [DueToday — today, Tomorrow — tomorrow, OneDayLate — 1 day overdue,
                 ThreeDaysLate — 3 days overdue]
channelId     = [payment_reminders]
contentIntent is null = false      FLAG_AUTO_CANCEL set = true
postTime      = 1788600100565  (ikinci koşuda değişmedi)
```

**API 29'da `POST_NOTIFICATIONS` nasıl görünüyor**

```
$ adb shell dumpsys package com.elinacn.subtrack | grep -A2 POST_NOTIFICATIONS
      android.permission.POST_NOTIFICATIONS
      android.permission.WAKE_LOCK
      android.permission.ACCESS_NETWORK_STATE

$ ... | grep -iE "POST_NOTIFICATIONS|install permissions|runtime permissions"
      android.permission.POST_NOTIFICATIONS
    install permissions:
    runtime permissions:
```

İzin yalnızca **istenen izinler** listesinde görünüyor; `install permissions`
ve `runtime permissions` bölümlerinin ikisi de onun için **boş** ve hiçbir
`granted=` satırı yok. Yani API 29'da bu izin ne kurulum ne çalışma zamanı
izni olarak veriliyor — `checkSelfPermission` ona "reddedildi" cevabı verirdi.
Notifier'daki `isRuntimePermissionRequired()` koruması tam olarak bunun için
var, ve yukarıdaki bildirim o korumanın çalıştığının kanıtı.

**Teşhis — satırın ağaçtaki şekli proje geneli bir desen**

API 34'te tek dump alındı:

| Öğe | Düğüm | clickable / focusable | İsim |
|---|---|---|---|
| Ana para birimi chip'i (TRY) | 2 | üst: `true`/`true` (+`checkable`, `checked`) | üstte **yok**, altta "Turkish lira" |
| Döviz Kurları satırı | 2 | üst: `true`/`true` | üstte **yok**, altta "Exchange Rates, Edit the rates…" |
| Ödeme hatırlatmaları satırı | 2 | üst: `true`/`true` | üstte **yok**, altta "Payment reminders, Off — tap to turn on" |
| Geri oku (stok `IconButton`) | 2 | üst: `true`/`true` | üstte **yok**, altta "Back" |
| **Tarih alanı (10a)** | **1** | `true`/`true` | **"Next Payment (optional), Not set"** |

Yani mevcut satırların hepsi yeni satırla **aynı iki-düğüm şeklinde** →
promptun birinci dalı: **kod değiştirilmedi**, bulgu ROADMAP Faz 16'daki
TalkBack maddesine yazıldı.

Tek düğüm veren tek yapı 10a'daki tarih alanı ve sebebi ölçümden anlaşılıyor:
orada `semantics` ve `clickable` **çocuğu olmayan** bir overlay `Box`'a
konuyor, o yüzden tek semantics düğümünde birleşiyorlar. Metin çocukları olan
bir satırda aynı sonuç alınamıyor.

Dikkat çeken nokta: stok Material `IconButton` da (geri oku) aynı iki-düğüm
şeklini veriyor. Bu, davranışın Compose erişilebilirlik köprüsünün normali
olduğuna işaret ediyor — ama TalkBack bu imajlarda olmadığı için üst düğüme
odaklanıp alt düğümün adını okuyup okumadığı **hâlâ doğrulanamadı**.

Kod değişmediği için regresyon listesi yeniden koşulmadı.


**Sonraki faz için not**
- 10c-2: tarihli abonelik kaydedilince bağlamsal olarak sorma. Bayrak ve durum
  makinesi hazır; oradaki tek yeni soru, aynı gün içinde kaç kez sorulacağı.
- İzin yeni verildiğinde o günkü hatırlatmanın gönderilmesi 10b hotfix'iyle
  zaten mümkün; 10c-2'de izin verilir verilmez bir koşu tetiklenmeli mi,
  karara bağlanmalı.

---

## [Faz 10b hotfix] Gösterilmeyen bildirim gün olarak yazılmasın — 2026-09-04

**Durum:** Tamamlandı.

**Sorun**
10b doğrulama turunda bulundu (kayıt yukarıda, "Bulunan ve düzeltilmeyen"
başlığı altında). Worker, seçim boş değilse `notifier.notify(...)` çağırdıktan
sonra günü **koşulsuz** işaretliyordu. Notifier ise bildirimler kapalıyken
hiçbir şey göndermeden çıkıyordu. Sonuç: hiç gösterilmemiş bir bildirim
"gönderildi" olarak kaydediliyor, kullanıcı o gün bildirimleri açsa bile
ertesi güne kadar hiçbir şey görmüyordu.

Bu bir izin akışı sorunu değildi: `areNotificationsEnabled()` kullanıcı
bildirimleri sistem ayarlarından kapattığında **her API sürümünde** `false`
döner, yalnızca API 33+ izin reddinde değil.

**Düzeltme**
- `PaymentReminderNotifier.notify(...)` artık `Boolean` dönüyor: bildirimin
  gerçekten gönderilip gönderilmediği. Kanal kurulumu, metin üretimi,
  `BigTextStyle` ve `PendingIntent` değişmedi.
- Worker günü **yalnızca `true` dönerse** yazıyor. İki durumda da
  `Result.success()` — bildirimlerin kapalı olması bir hata değil, `retry`
  bu işin değiştiremeyeceği bir ayarı beklerken pil harcardı.
- `try/catch` eklenmedi (§9).

**`@FixMethodOrder` kaldırıldı**
10b'de eklenmişti, gerekçesi "bildirimler kapalı" metodunun günü
işaretlemesiydi. Düzeltmeden sonra o metot kaydı hiç kirletmiyor, yani sıra
bağımlılığı **yapısal olarak** kalktı. Ölçüldü: aşağıdaki turda kapalı-izin
koşusu **önce**, asıl koşu **sonra** çalıştı ve asıl koşu bildirimi gönderdi.
Anotasyon kaldırıldı; sınıf KDoc'u ve TESTING.md buna göre yeniden yazıldı.
`pm clear` ön koşulu **duruyor** — sebebi sıra değil, "günde bir bildirim"
kuralının kendisi.

**Test yöntemi seçimi**
Yeni test metodu **yazılmadı**, adb ile ayrılmış tur seçildi. Sebep: izin
durumunu test süreci içinden değiştirmek `UiAutomation.revokeRuntimePermission`
gerektiriyor ve çalışma zamanı izni geri almak süreci öldürebiliyor — testi de
öldürürdü. `kspAndroidTest` eklenmedi, enstrümantasyonun Hilt grafına erişimi
yok; kanıt davranışsal.

**Doğrulama turu — API 34, tek tur, veri arada SİLİNMEDİ**

`pm clear` → `pm revoke POST_NOTIFICATIONS` → üç koşu:

| # | İzin | Nasıl koşturuldu | Sonuç |
|---|---|---|---|
| 1 | `granted=false` | `notificationsDisabled` metodu | `areNotificationsEnabled=false`, worker **SUCCEEDED**, aktif bildirim **0** |
| 2 | `granted=true` (`pm grant`, veri silinmedi) | `datedSubscriptions` metodu, ilk koşu | **Bildirim GELDİ**, `postTime=1788550455107` |
| 3 | `granted=true` | aynı metodun ikinci koşusu | Yeni bildirim yok, `postTime` **değişmedi** (1788550455107) |

**Düzeltmenin kanıtı 2. satırdır.** Eski kodda 1. koşu günü işaretlerdi ve
2. koşu hiçbir şey göndermezdi — 10b turunda tam olarak bu yüzden paket
kırmızı olmuştu.

Gelen bildirimin ham metni, 10b'dekiyle birebir aynı:

```
EXTRA_TITLE = [Payment reminder: 4 subscriptions]
EXTRA_TEXT  = [DueToday — today, Tomorrow — tomorrow, OneDayLate — 1 day overdue,
               ThreeDaysLate — 3 days overdue]
channelId   = [payment_reminders]
contentIntent is null = false      FLAG_AUTO_CANCEL set = true
```

**Diğer doğrulamalar**
- `assembleDebug`, `lintDebug` (**0 hata**, uyarı kümesi değişmedi: 15),
  `testDebugUnitTest` (**112**, 0 hata).
- Tüm enstrümantasyon paketi `am instrument` ile iki emülatörde: **OK (11 tests)**.
- Regresyon listesinden yalnızca **#1** ve **#12** koşuldu, iki emülatörde de
  geçti. Bu bilinçli bir istisna: değişiklik UI'a, Room'a, DataStore anahtar
  isimlerine ve DI grafına dokunmuyor; iki dosyada toplam 25 satır.

**Bilinen sınır (ARCHITECTURE §18'e yazıldı)**
`areNotificationsEnabled()` **uygulama düzeyindedir.** Kullanıcı yalnızca
`payment_reminders` kanalını kapatmışsa fonksiyon hâlâ `true` döner, sistem
bildirimi sessizce düşürür ve gün yine işaretlenir. Kanal bazlı kapatma bu
korumanın dışında; eklenip eklenmeyeceği ayrı karar.

**Belgelere taşınan ölçüm tuzağı**
API 34'te ekranın kenarından başlayan `input swipe`'ın sistem geri jestini
tetiklediği bilgisi PROGRESS'ten `TESTING.md`'nin "Emülatör Testleri"
bölümüne taşındı. Sebep de ölçüldü: `cmd overlay list android` çıktısında dar
AVD'de hiçbir gestural overlay etkin değil (üç tuşlu gezinme), geniş AVD'de
`[x] com.android.internal.systemui.navbar.gestural`. Yani fark API
sürümünün değil **gezinme modunun** sonucu. Ölçülen iki nokta: x=1040
(kenardan 40 px) uygulamadan çıkardı, x=950 (130 px) çalıştı; eşik ikili
aramayla daraltılmadı.

**Değişen dosyalar**
- `reminder/PaymentReminderNotifier.kt` — `notify` artık `Boolean`
- `reminder/PaymentReminderWorker.kt` — gün koşullu yazılıyor
- `androidTest/.../PaymentReminderWorkerTest.kt` — `@FixMethodOrder` kaldırıldı,
  KDoc yeniden yazıldı
- `docs/ARCHITECTURE.md` §18, `docs/TESTING.md`

**Commit'ler**
- `9d143a1` fix: only record the day when a reminder was actually shown
- `3a81fc3` docs: record the reminder-day fix and the edge-swipe measuring trap

**Sonraki faz için not**
- 10c izni runtime'da isteyecek. İzin **yeni verildiğinde** o günkü
  hatırlatmanın hâlâ gönderilebilir olması bu düzeltmeyle sağlandı; 10c'de
  ayrıca izin verilir verilmez bir koşu tetiklenmeli mi, karara bağlanmalı.
- Kanal bazlı kapatmanın tespiti de 10c'nin konusu olabilir.

---

## [Faz 10b] WorkManager + Yerel Bildirim — 2026-09-04

**Durum:** Tamamlandı. İzin isteme akışı (runtime `POST_NOTIFICATIONS`) **yok** — 10c.

**Bağımlılık ölçümü (Görev 0)**
- `work-runtime-ktx` **2.11.2** (en güncel kararlı; 2.12.0 hattı hâlâ
  alpha/beta/rc) ve `androidx.hilt` **1.3.0** (`hilt-work` + `hilt-compiler`)
  mevcut taban üzerinde **ilk denemede** çözüldü. `checkDebugAarMetadata` hiç
  patlamadı, alt sürüme inmek gerekmedi. AGP 9.0.1 / compileSdk 36.1 korundu.
- `androidx.hilt` tek sürüm grubu olduğu için `hilt-work` ve `hilt-compiler`,
  `hilt-navigation-compose` ile **aynı** `version.ref`'i paylaşıyor.
- Dagger'ın `hilt-android-compiler`'ı yerinde kaldı. KSP iki compiler'la
  sorunsuz derledi ve `@HiltWorker`'ı gerçekten işledi:
  `PaymentReminderWorker_AssistedFactory`, `_AssistedFactory_Impl`, `_Factory`,
  `_HiltModule` üretildi.
- Lint uyarı **kümesi** değişmedi; yalnızca iki yeni "1.4.0 mevcut"
  (`GradleDependency`) satırı eklendi. 0 hata.
- Birleşik manifest ölçüldü: `androidx.startup.InitializationProvider`
  probe'tan **önce de vardı** ve emoji2, lifecycle, profileinstaller
  tarafından kullanılıyordu. WorkManager dördüncü olarak katıldı, bu yüzden
  provider'ın kendisi kaldırılamaz.
- WorkManager manifeste dört izin ekliyor: `WAKE_LOCK`,
  `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`.
  Faz 16'ya not düşüldü.
- `stripDebugDebugSymbols`'ün strip edemediği `libandroidx.graphics.path.so`
  WorkManager'dan **gelmiyor** — temiz tabanda da vardı, Compose'un
  `androidx.graphics:graphics-path` bağımlılığından geliyor.

**Yapılanlar**
- `PaymentReminderSelection` — saf, bugünün tarihi parametre. `PaymentCountdown`
  yeniden yazılmadı, çağrıldı. İki eşik adlandırılmış sabit
  (`UPCOMING_WITHIN_DAYS = 1`, `OVERDUE_WITHIN_DAYS = 3`). Sonuç
  `PaymentReminder(subscription, countdown)` taşıyor; çağıran geri sayımı
  ikinci kez hesaplamıyor.
- `ReminderStateRepository` — son bildirim günü epoch day olarak. Ayrı arayüz,
  çünkü bu bir kullanıcı tercihi değil, işin kendi kaydı. Depolama **mevcut**
  `DataStore<Preferences>` örneği; ikinci instance açılmadı (§14).
- `PaymentReminderNotifier` — `NotificationChannelCompat` + tek özet bildirim,
  `BigTextStyle`, `setAutoCancel(true)`, `PendingIntent.FLAG_IMMUTABLE`. Kanal
  her gönderimde yeniden kuruluyor (dil değişince ad güncellensin diye).
- `PaymentReminderWorker` — `@HiltWorker` + `CoroutineWorker`, `try/catch` yok.
- `PaymentReminderScheduler` — `KEEP`, constraint yok, ilk gecikme enjekte
  `Clock`'tan sonraki yerel 09:00.
- `SubTrackApplication` artık `Configuration.Provider`. Üye **property**;
  `javap` ile doğrulandı (`getWorkManagerConfiguration()`).
- Manifest: `POST_NOTIFICATIONS` bildirildi; `WorkManagerInitializer`
  `meta-data`'sı `tools:node="remove"` ile çıkarıldı, provider
  `tools:node="merge"` ile kaldı.
- Bildirim ikonu `res/drawable/ic_notification.xml` — tek path, düz beyaz,
  geçici. Faz 16'da marka ikonuyla yenilenecek.

**Yeniden kullanılan metin**
Gecikme günü için yeni bir plurals açılmadı; 10a'daki `days_overdue`
kullanıldı. Aynı dilde aynı olguyu söylüyor, ikinci bir çeviri çifti tekrar
olurdu. Yeni plurals yalnızca başlık sayısı için (`notification_title`), ve
başlık **fiilsiz**: aynı bildirim hem bugün ödenecek hem gecikmiş abonelik
taşıyabiliyor.

**`cmd jobscheduler run -f` duvarı ve nasıl dolanıldı**
İlk doğrulama denemesi başarısız oldu ve bu, fazın en pahalı bulgusu:

```
$ adb shell cmd jobscheduler run -f com.elinacn.subtrack 0
Running job [FORCED]
WM-WorkerWrapper: Delaying execution for ...PaymentReminderWorker because it is
  being executed before schedule.
WM-WorkerWrapper: Status ... is ENQUEUED; not doing any work and rescheduling
```

JobScheduler'ı zorlamak WorkManager'ın kendi `lastEnqueueTime + initial_delay`
denetimini geçmiyor. Duvar saatini ileri almanın üç yolu da kapalı:
`adb root` → *adbd cannot run as root in production builds*;
`adb shell date …` → *Operation not permitted*;
`setprop persist.sys.timezone` → *failed to set property*.

**Çözüm:** o denetim yalnızca gecikmeli işler için çalışıyor. Gecikmesiz bir
`OneTimeWorkRequest` anında koşuyor. Enstrümantasyon uygulamayla aynı süreçte
koştuğu için `WorkManager.getInstance()` uygulamanın kendi örneğini veriyor ve
worker'ı gerçek `HiltWorkerFactory` üretiyor — sahte fabrika, `work-testing`
bağımlılığı veya `TestListenableWorkerBuilder` kullanılmadı.

**Testler**
- 101 → **112 birim testi** (11 yeni, `PaymentReminderSelectionTest`), 0 hata.
- 9 → **11 enstrümantasyon testi**, iki emülatörde de **11/11**.
- `lintDebug` 0 hata. Uyarı kümesi Görev 0'daki 15 ile aynı.
- Worker için birim testi yazılmadı, `work-testing` eklenmedi.

**Emülatör sonuçları** (cihaz tarihi 4 Eylül 2026, GMT)

Bildirim içeriği iki cihazda **birebir aynı**:

```
EXTRA_TITLE    = [Payment reminder: 4 subscriptions]
EXTRA_TEXT     = [DueToday — today, Tomorrow — tomorrow, OneDayLate — 1 day overdue,
                  ThreeDaysLate — 3 days overdue]
EXTRA_BIG_TEXT = (aynısı)
channelId      = [payment_reminders]
contentIntent is null = false      FLAG_AUTO_CANCEL set = true
```

Ham `%` yok. `TwoDaysOut`, `FourDaysLate` ve `NoDate` metinde **yok**.
İkinci koşuda `postTime` değişmedi (dar: 1788549186749, geniş: 1788549195339 —
ikisi de iki koşuda aynı), aktif bildirim sayısı 1'de kaldı.

Bildirime dokunma: dar cihazda `(360,779)`, geniş cihazda `(587,730)` →
`ResumedActivity: com.elinacn.subtrack/.MainActivity`. Bildirim `mArchive`'e
düştü, yani `autoCancel` çalıştı.

İzin kapalı tur (yalnız API 34, `pm clear` + `pm revoke` sonrası):
`areNotificationsEnabled=false`, worker **SUCCEEDED**, aktif bildirim 0,
`logcat -b crash` boş.

**Bulunan ve düzeltilmeyen — ürün davranışı**
Worker, seçim boş değilse `notifier.notify(...)` çağırdıktan **sonra** günü
işaretliyor; bildirimin gerçekten gösterilip gösterilmediğine bakmıyor.
Sonucu: bildirimler kapalıyken koşan bir gün "bildirildi" sayılıyor, kullanıcı
o gün izni açsa bile ertesi güne kadar hiçbir şey görmüyor.

Bu, testte önce sıra bağımlılığı olarak ortaya çıktı: "bildirimler kapalı"
metodu günü işaretleyince asıl metot bildirim gönderemiyordu. Test tarafı
`@FixMethodOrder(NAME_ASCENDING)` ile sabitlendi ve `pm clear` ön koşulu
belgelendi. **Üretim kodu değiştirilmedi** — davranışın doğru olup olmadığı
ayrı bir karar, muhtemelen 10c'nin konusu.

**Regresyon listesi**
32 maddenin tamamı iki emülatörde de geçti, iki istisnayla: **#15 (koyu tema)**
API 29'da ölçülemiyor (TESTING.md'de kayıtlı sınır), yalnız API 34'te
koşturuldu; **#16 (dil)** API 29'da ayarlar arayüzü gerektiriyor, API 34'te
`cmd locale set-app-locales` ile koşturuldu ve Türkçe metinler geldi
("Aylık Toplam, ₺159,99", "Aboneliklerim"). #15 yalnızca çökme ve ağaç
bütünlüğü olarak doğrulandı; renk kontrastı dump'tan ölçülemez.

Ölçüm sırasında iki tuzak çıktı, ikisi de test yöntemine ait:
- API 34'te ekranın **en sağ kenarından** başlayan kaydırma sistem geri
  jestini tetikliyor ve uygulamadan çıkıyor. Kaydırma testleri kenardan uzak
  başlatılmalı.
- Soğuk açılıştan 3 sn sonra alınan `uiautomator dump` bayat toplam
  gösterebiliyor; 6 sn sonra doğru değer geliyor. Kalıcı bir durum değil.

**Değişen dosyalar**
- `domain/usecase/PaymentReminderSelection.kt` — yeni
- `domain/repository/ReminderStateRepository.kt` — yeni
- `data/repository/ReminderStateRepositoryImpl.kt` — yeni
- `reminder/PaymentReminderNotifier.kt`, `PaymentReminderWorker.kt`,
  `PaymentReminderScheduler.kt` — yeni paket
- `di/RepositoryModule.kt` — yeni bağlama
- `SubTrackApplication.kt` — `Configuration.Provider`
- `AndroidManifest.xml` — izin + initializer kaldırma
- `res/drawable/ic_notification.xml`, `res/values/strings.xml`,
  `res/values-en/strings.xml`
- `gradle/libs.versions.toml`, `app/build.gradle.kts`
- `androidTest/.../PaymentReminderWorkerTest.kt` — yeni
- `test/.../PaymentReminderSelectionTest.kt` — yeni
- `docs/ARCHITECTURE.md` §18, `docs/ROADMAP.md`, `docs/TESTING.md`

**Commit'ler**
- `1da6572` build: add WorkManager and the androidx Hilt worker integration
- `db76073` feat: pick which payments are worth a reminder
- `81760d8` feat: remember the day a reminder was last shown
- `df33817` feat: show the day's payment reminders in one notification
- `4ae0f6b` feat: run the payment reminder once a day
- `5478ea4` docs: record the reminder decisions and the WorkManager trigger limits
- `26b1b70` test: run the reminder worker on device through the real graph
- `eb4ac6e` test: fix the reminder test order so the once-a-day guard cannot hide a run
- `c4ced4c` docs: record phase 10b and the working way to run the reminder job

**Sonraki faz için not**
- 10c runtime izin akışını getirecek. Yukarıdaki "gün işaretleme" davranışı
  orada yeniden değerlendirilmeli: izin yeni verildiğinde o günkü hatırlatma
  kaçıyor.
- Faz 12 tarih ilerletmeyi getirdiğinde 1-3 günlük gecikme penceresinin
  gerekçesi ortadan kalkıyor (ARCHITECTURE §18).

---

## [Faz 10a] Sonraki Ödeme Tarihi — 2026-09-04

**Durum:** Tamamlandı. Bildirim, WorkManager ve izin akışı **yok** — 10b ve 10c.

**Desugaring ölçümü (Görev 0a)**
- `minSdk = 24`, `coreLibraryDesugaringEnabled` **tanımlı değildi**,
  `coreLibraryDesugaring` bağımlılığı **yoktu**.
- Tek satırlık `LocalDate.now()` denemesi: **`assembleDebug` GEÇTİ.** Derleyici
  şikâyet etmiyor çünkü `compileSdk 36` sınıf yolunda `java.time` var. Sorun
  ancak API 24/25 cihazda çalışma anında `NoClassDefFoundError` olurdu.
- **`lintDebug` HATA VERDİ** ve derlemeyi durdurdu:
  `Call requires API level 26, or core library desugaring (current min is 24):
  java.time.LocalDate#now [NewApi]`
- Sonuç sohbete taşındı, desugaring onaylandı, `minSdk 24` korundu.

**Desugaring sürüm seçimi**
`desugar_jdk_libs 2.1.5` seçildi. 2.x hattı AGP 8+/9'un beklediği hat; 2.1.5
AGP 9.0.1'in gerekli AGP sürümünü yükseltmeden kabul ettiği en yeni sürüm.
Doğrulama: `assembleDebug --rerun-tasks` yeni bir `l8DexDesugarLibDebug`
görevi çalıştırdı (desugar edilmiş kütüphanenin dex'lenmesi — açık olduğunun
kanıtı) ve **`lintDebug` temiz geçti**. APK bedeli ~200-400 KB, Faz 16'daki
R8 maddesinde ölçülecek.

**Yapılanlar**
- `Subscription.nextPaymentDate` `Long?` → **`LocalDate?`**. Room şeması
  değişmedi; dönüşüm `SubscriptionMapper`'da, **sistem saat diliminde**.
  Yön açıkça yazıldı: saklanan an o günün yerel takvim günü olarak okunuyor,
  yazarken yerel gece yarısına çevriliyor. UTC okumak Greenwich'in doğu veya
  batısındaki kullanıcı için tarihi bir gün kaydırırdı.
- `PaymentCountdown` domain'de saf: `Upcoming(days)` / `DueToday` /
  `Overdue(days)`. **Bugünün tarihi parametre**, içeride `LocalDate.now()`
  yok. Üç durumu imzasız sayı yerine tip olarak ayırdım — ekran üç farklı şey
  söylüyor, çağıran her seferinde sınırları yeniden keşfetmesin.
- `Clock` Hilt'ten geliyor (`di/TimeModule`). ViewModel'ın "bugün"ü
  enjekte edilebilir olduğu için 10a testleri sabit bir tarihte koşuyor.
- Geri sayım **ViewModel'da** hesaplanıyor, `HomeUiState.countdowns` map'ine
  id ile konuyor. Composable'da hesap yok (§3). Abonelik nesnesinin içine
  konmadı: geri sayım aboneliğin değil, **abonelik ile bugünün** özelliği.
- Ekleme formuna Material3 `DatePicker`. Alan salt okunur — değer yalnızca
  seçiciden gelir, yazılacak bir şey yok. Temizleme diyaloğun içinde
  ("Tarihi temizle"), çünkü alanın üstündeki dokunma katmanı alan içindeki
  bir ikonu erişilemez kılardı.
- Kartta "X gün kaldı". Tarih yoksa **hiçbir şey** yok, yer tutucu yok.

**Renk kararı — `error` rolü**
Gecikmiş durum için `MaterialTheme.colorScheme.error` seçildi; yeni renk
eklenmedi. Kartta metin olarak okunabilen tanımlı roller yalnızca `primary`
(fiyat için kullanılıyor) ve `onSurface` (ad için kullanılıyor);
`secondary`/`tertiary` pastel, beyaz kart üstünde metin olarak okunmuyor.
`error` "bir şey ters" diyen tek anlamsal rol. **Ama şemamızda tanımlı
değil** — Material baseline kırmızısına düşüyor. Faz 14'teki tanımsız rol
listesine eklendi (`outline`, `onSurfaceVariant` ile birlikte).

**Testler**
- 84 → **101 birim testi**, hepsi geçti.
- `PaymentCountdownTest`: yarın, bugün, dün, hafta, yıl, **artık gün**
  (2028-02-28 → 2028-03-01 = 2 gün, 29 Şubat arada) ve yıl dönümü.
  Hepsi kendi tarihlerini adlandırıyor, hiçbiri koşma anına bağlı değil.
- Mapper: `Long? ↔ LocalDate?`, null, **gün ortası an** (13:00 saklanan an
  aynı takvim gününe düşüyor, bir gün kaymıyor).
- ViewModel: tarihli/tarihsiz kayıt, geçmiş tarih kabulü, tam 10 yıl kabul,
  10 yıl + 1 gün reddi (sınır mesajda argüman olarak), geri sayım map'i.
- Enstrümantasyon **9/9, iki emülatörde**.
- `lintDebug` temiz.

**Değişen dosyalar**
- `domain/model/Subscription.kt` — `LocalDate?`
- `domain/usecase/PaymentCountdown.kt` — yeni
- `data/mapper/SubscriptionMapper.kt` — dönüşüm
- `di/TimeModule.kt` — yeni, `Clock`
- `ui/home/HomeUiState.kt`, `HomeViewModel.kt`, `HomeScreen.kt`
- `ui/home/components/AddSubscriptionSheet.kt`, `SubscriptionCard.kt`
- `res/values/strings.xml`, `res/values-en/strings.xml` — plurals dahil
- `app/build.gradle.kts`, `gradle/libs.versions.toml` — desugaring
- `docs/ARCHITECTURE.md` §17, `docs/ROADMAP.md`, `docs/TESTING.md`

**Commit'ler**
- `b373c14` docs: add date handling decisions
- `22f8ecf` build: enable core library desugaring for java.time
- `d5dae83` feat: model the next payment date as a calendar day
- `9f83b47` feat: pick a next payment date and count down to it
- `6d2acd0` test: cover the countdown, date mapping and date validation
- `581f954` fix: name the date field and its error for screen readers

**Emülatörde bulunan ve düzeltilen — erişilebilirlik**
Tarih alanının üstündeki saydam dokunma katmanı alt ağacı birleştiriyor, bu
yüzden alan ekran okuyucuya **isimsiz bir buton** olarak görünüyordu:
`cd=''`. `clickable(onClickLabel = ...)` eylemi adlandırıyor, düğümü değil.
Katmana `semantics { contentDescription }` eklendi; artık
`"Next Payment (optional), Sep 10, 2026"` diye okunuyor. Aynı sebeple hata
metni de ağaçta yoktu — o da açıklamaya katıldı:
`"..., Dec 31, 2040, The date can be at most 10 years ahead"`.

Emülatörde bulunan ikinci hata: açıklama metni ilk turda ham `%1$s`
gösteriyordu, `stringResource` argümanı verilmemişti (9b-2'deki hatanın
aynısı — aynı tuzağa iki kez düşüldü).

**Emülatör test sonuçları** (cihaz tarihi 3-4 Eylül 2026)

| # | Test | Dar (API 29, 360dp) | Geniş (API 34, 411dp) |
|---|---|---|---|
| 1 | Tarih seç, kaydet, gün sayısı | geçti — 3 Eylül'de 10 Eylül seçildi → **"7 days left"** | geçti — 4 Eylül'de 11 Eylül → **"7 days left"** |
| 2 | Tarihsiz kayıt | geçti — "NoDate, TRY 10.00", gösterge yok, çökme yok | — |
| 3 | Geçmiş tarih | geçti — 1 Eylül → **"2 days overdue"** | — |
| 4 | Bugün | geçti — 3 Eylül → **"Due today"** | — |
| 5 | 10 yıldan uzak | geçti — 31.12.2040 girildi, alan hatası, **kaydedilmedi**, çökme yok | — |
| 6 | Sheet layout regresyonu | geçti — aşağıda | geçti — aşağıda |
| 7 | fs2.0 taşma | geçti — seçici `[0,96][720,1232]`, kart satırları büyüyor, kırpılma yok | — |
| 8 | Döndürme, tarih seçili | geçti — "Sep 25, 2026" yatay-dikey korundu | — |
| 9 | Sabit regresyon listesi | **21/21 geçti** | **21/21 geçti** |

**Test 1 elle doğrulama:** cihaz `date` çıktısı `Thu Sep 3 2026`, seçilen gün
`Thursday, September 10, 2026` → 10 − 3 = **7**. Kart "7 days left" dedi.
Geniş cihazda `Fri Sep 4` + `September 11` → 7, aynı sonuç.

**Test 6 — 9b-1 hotfix'i ayakta.** Yeni alan kaydırma bölgesine girdi,
Kaydet butonunun koordinatı **hiçbir kombinasyonda değişmedi**:

| Cihaz | fs | Klavye | Kaydet (10a) | 9b-2 referansı |
|---|---|---|---|---|
| Dar | 1.0 | kapalı | `[48,1104][672,1200]` | **aynı** |
| Dar | 1.0 | açık | `[48,606][672,702]` | **aynı** |
| Geniş | 1.0 | kapalı | `[63,2107][1017,2233]` | **aynı** |
| Geniş | 1.0 | açık | `[63,1287][1017,1413]` | **aynı** |
| Geniş | 2.0 | kapalı | `[63,2092][1017,2232]` | **aynı** |
| Geniş | 2.0 | açık | `[63,1272][1017,1412]` | **aynı** |

Değişen tek şey kaydırma bölgesinin **yukarı doğru büyümesi** (dar fs1.0'da
`[0,513]` → `[0,361]`, geniş fs1.0'da `[0,1328]` → `[0,1128]`): sheet uzadı,
Kaydet yerinde kaldı. Kaydırınca da sabit kalıyor.

**Gözlem — düzeltilmedi**
font_scale 2.0'da uzun abonelik adları kartta kırpılıyor ("TodayDue" →
"TodayDu"). Ad sütununun genişliği bu fazda değişmedi (`weight(1f)` aynı
kaldı, sadece içine ikinci satır eklendi), yani bu **mevcut bir davranış**,
geri sayımın getirdiği bir şey değil. Faz 14 veya 15'te ele alınabilir.

**Karşılaşılan sorunlar**
- Emülatörler oturum boyunca üç kez kendiliğinden kapandı. Bir turda bunu
  fark etmeden ölçüm alındı ve **eski dump dosyaları okundu**; sonuç
  geçersiz sayıldı ve emülatörler yeniden başlatılıp tekrarlandı.
  Ders: `adb devices` boşsa okunan dosya bir öncekinin kalıntısıdır.
- Geniş emülatörde regresyon turunun ilki `uiautomator dump`'ın
  `null root node` dönmesi yüzünden ilk aboneliği ekleyemedi; tur baştan
  koşuldu.

**Sonraki faz için not**
- 10b: WorkManager + bildirim. `PaymentCountdown` ve `Clock` hazır;
  zamanlama `java.time` üzerinden kurulacak (desugaring artık açık).
- Geri sayım her emisyonda hesaplanıyor; **gece yarısını açık geçen bir
  oturum** dünün sayısını gösterir. 10b'nin zamanlayıcısı bunu da
  tazeleyebilir.
- `billingPeriod` hâlâ UI'a bağlı değil ve tarih geçince **ilerletme yok** —
  Faz 12'nin işi (§17).

---

## [Faz 9b-2] Düzenlenebilir Döviz Kurları — 2026-09-02

**Durum:** Tamamlandı. Faz 9 kapandı.

**Yapılanlar — belgeler ve dosya bölme**
- `ARCHITECTURE.md`'ye **§15 Döviz Kurları** eklendi; kod ona göre yazıldı.
- `ROADMAP.md` girişi düzeltildi: tag'i **kullanıcı** atar, CC atmaz.
- `HomeScreen.kt` 301 satırdı, CLAUDE.md §4 sınırının bir satır üstünde. Beş
  `@Preview` fonksiyonu ve `previewSubscription` yardımcısı
  `ui/home/HomeScreenPreviews.kt`'ye taşındı: **301 → 201 + 103**. Taşımadan
  sonra `HomeScreen.kt`'de sekiz import ölü kaldı, onlar da silindi.
  (Prompt sekiz preview diyordu; dosyada beş tane vardı.)

**Üst sınır hesabı — `MAX_RATE`**
`CurrencyConverter.convert` içindeki en geniş ara değer
`grupToplamıKuruş × kaynakKuru`, `Long` içinde. Sınırlar:
- Abonelik başına tavan `MAX_PRICE` = 1.000.000 birim = **10⁸ kuruş**
- `Long.MAX_VALUE` = 9.223.372.036.854.775.807 ≈ **9,22 × 10¹⁸**

Bin abonelik aynı para biriminde: grup toplamı 10¹¹ kuruş. Seçilen
`MAX_RATE = 10.000.000` (ölçekli) = **1.000,0000 TRY / yabancı birim** ile
çarpım 10¹¹ × 10⁷ = **10¹⁸** — tavanın **9,2 katı** altında.

Tersinden bakınca daha okunaklı: `Long.MAX_VALUE / (10⁸ × 10⁷) = **9.223
abonelik**. Tek para biriminde, hepsi fiyat tavanında, kur da tavanda. Yoğun
bir kullanıcının listesi 50-100 kalem; **yaklaşık yüz kat pay** var. Bu sayı
yoruma bırakılmadı, `CurrencyConverterTest`'te aritmetik olarak sabitlendi —
`MAX_RATE` düşünmeden yükseltilirse test kırılır.

Kurun kendisi bölen olarak da kullanılıyor. En kötü hâl kaynak `MAX_RATE`,
hedef `MIN_RATE`: bölme çarpımı hiç küçültmüyor, sonuç 10¹⁸'de kalıyor, hâlâ
güvenli. Bu da ayrı bir testte.

**Sıfırlama neden anahtar siliyor**
`resetRates()` `remove()` çağırıyor, `Default`'u geri yazmıyor. Fark ileride
ortaya çıkar: bir sonraki sürüm daha iyi varsayılan kurlarla gelirse,
sıfırlamış kullanıcı **yeni** değerleri alır; bugünün sayılarını yazmış olsaydı
onlara sonsuza kadar takılı kalırdı. Emülatörde doğrulandı — sıfırlamadan sonra
`settings.preferences_pb` **0 bayta** düştü ve tarih göstergesi "hiç
düzenlenmedi"e geri döndü.

**Doğrulama**
- Ölçek altı kur ayrı bir kontrol değil: pozitiflik + en fazla dört ondalık
  kuralları birlikte zaten ≥ 1 ölçek birimi garantiliyor. Yine de dönüşümden
  sonra `scaled < MIN_RATE` kontrolü **duruyor** — koruduğu şey sıfıra bölme,
  yani üslup değil doğruluk meselesi; koda erişilemez olduğu yazıldı.
- Bir alan hatalıysa **hiçbiri yazılmıyor.** Kısmi kayıt, kullanıcıyı hangi
  kutunun kaydedildiğini bilemez hâlde bırakırdı.

**Testler**
- 58 → **84 birim testi**, hepsi geçti. Enstrümantasyon **9/9, iki emülatörde**.
- İki test yazarken `Save`'in aynı karede yapılan tuş vuruşunu görmediği
  ortaya çıktı: `save()` türetilmiş `uiState`'i okuyordu, o da `combine`
  yeniden yayınlayana kadar geride kalıyor. `screenState` önce okunacak şekilde
  düzeltildi — testin bulduğu gerçek bir zamanlama hatasıydı.

**Değişen dosyalar**
- `domain/model/ExchangeRateTable.kt` — `MIN_RATE`, `MAX_RATE` (Default değerleri değişmedi)
- `domain/repository/SettingsRepository.kt`, `data/repository/SettingsRepositoryImpl.kt`
- `ui/settings/rates/` — `ExchangeRatesUiState.kt`, `ExchangeRatesViewModel.kt`, `ExchangeRatesScreen.kt` (yeni)
- `ui/navigation/Destination.kt`, `SubTrackNavHost.kt` — üçüncü hedef
- `ui/settings/SettingsScreen.kt` — kur ekranına giden satır
- `ui/home/HomeViewModel.kt` — toplam artık saklanan tabloyla
- `ui/home/HomeScreen.kt`, `ui/home/HomeScreenPreviews.kt` — bölme
- `ui/theme/Dimens.kt` — `MinTouchTarget`
- `res/values/strings.xml`, `res/values-en/strings.xml`
- `docs/ARCHITECTURE.md`, `docs/ROADMAP.md`, `docs/TESTING.md`

**Commit'ler**
- `641c5e1` docs: add exchange rate decisions and clarify rounding note
- `6d7932a` refactor: move home previews to a separate file
- `ba07464` feat: store editable exchange rates in DataStore
- `2ef9027` feat: add a screen for editing exchange rates by hand
- `64c5d8a` feat: total with the stored rate table instead of the shipped one
- `7cbb2b8` test: cover rate validation, overflow bounds and reset
- `f41a400` fix: pass the anchor code into the exchange rate description
- `ee0d2db` docs: add exchange rate regression checks

**Karşılaşılan sorunlar**
- Açıklama metni ilk turda ekranda ham `%1$s` olarak çıktı: string biçim
  argümanı `stringResource` çağrısına geçilmemişti. Emülatörde görüldü,
  `f41a400` ile düzeltildi.
- API 34 emülatöründe IME servisini `am force-stop` ile yeniden başlatmak
  "System UI isn't responding" diyaloğunu tetikledi. Bir daha yapılmamalı;
  `show_ime_with_hard_keyboard` ayarını değiştirdikten sonra IME'yi
  kapatmadan beklemek yetiyor.

**Emülatör test sonuçları**

| # | Test | Dar (API 29, 360dp) | Geniş (API 34, 411dp) |
|---|---|---|---|
| 1 | Ayarlar → kur ekranı, beş tur | geçti — beş tur, tek geri tuşuyla launcher | geçti — satır `[42,685][1038,851]` = **63dp** |
| 2 | Kur değişince toplam | geçti — 10,00 USD @ 50,0000 → **500,00 TRY** | geçti — aynı |
| 3 | Kalıcılık | geçti — force-stop sonrası 500,00 | geçti |
| 4 | Sıfırlama | geçti — 42,85/46,2/53,9, dosya **0 bayt** | geçti |
| 5 | Doğrulama (0, -5, 1,23456, 1000,0001) | geçti — dördü de alan hatası, hiçbiri kaydedilmedi, **FATAL yok** | — |
| 6 | Klavye erişilebilirliği | **kısmen** — aşağıda | **kısmen** — aşağıda |
| 7 | Döndürme | geçti — "77.7" korundu | geçti — "88.88" korundu |
| 8 | `pm clear` sonrası | geçti — varsayılan kurlar, çökme yok | geçti |

**Test 2 elle hesap:** 1000 kuruş × 500.000 / 10.000, yuvarlama terimi bölenin
yarısı = 5.000 → (500.000.000 + 5.000) / 10.000 = 50.000 kuruş = **500,00 TRY**.
Cihazın gösterdiği değer.

**Bilinen kusur — klavye açıkken butonlar (test 6)**
Uygulama penceresi `windowSoftInputMode` tanımlamadığı için platform
`adjustPan` gibi davranıyor: pencere **küçülmüyor**, kayıyor. Kaydırma
görünümü de küçülmediği için içerik klavyenin altında kalabiliyor ve
kaydırarak kurtarılamıyor.

| Cihaz | fs | Klavye üstü | Kaydet | Varsayılana dön | Kaydırarak ulaşılıyor mu |
|---|---|---|---|---|---|
| Dar | 1.0 | y=**863** | `[32,1016][688,1112]` altta | `[32,1136][688,1232]` altta | **Hayır** — kaydırma payı yok |
| Dar | 2.0 | y=**1079** (pencere kaydı) | iki fiske sonra `[303,825][417,900]` | `[163,956][557,1031]` | **Evet** |
| Geniş | 1.0 | y=**1625** | `[42,1403][1038,1529]` **görünür** | `[42,1561][1038,1687]` kısmen altta | gerek yok |
| Geniş | 2.0 | y=**1625** | kaydırma sonu `[42,1983][1038,2123]` altta | `[42,2155][1038,2295]` altta | **Hayır** |

Her durumda geri tuşu klavyeyi kapatıyor ve ekrandan çıkmıyor; sonra butonlar
erişilebilir. Yani özellik kullanılabilir ama akış pürüzlü. **Düzeltilmedi:**
çözüm `Modifier.imePadding()` ya da manifest'te `adjustResize`, ikisi de bu
promptun DOKUNMA listesinde ve tek ekranın kararı değil. Ayrı prompt konusu.

**Chip erişilebilirlik ölçümü — 9b-1 bulgusu yanlıştı**
Ekleme sheet'i açıkken `uiautomator dump` ve `uiautomator dump --compressed`
alındı; ayarlar ekranının dump'ı ile karşılaştırıldı. **Üçü de birebir aynı
yapıyı veriyor:**

| Düğüm | sheet (normal) | sheet (--compressed) | ayarlar |
|---|---|---|---|
| Tıklanabilir sarmalayıcı `View` | `checkable=true checked=true` | aynı | aynı |
| `contentDescription` taşıyan çocuk | `checkable=false` | aynı | aynı |

Yani fark ne bileşenden ne dump aracından geliyor — **9b-1'de yanlış düğüme
bakılmıştı**: `contentDescription` taşıyan çocuk okunmuş, seçili durumu bildiren
tıklanabilir ebeveyn atlanmıştı. `CurrencySelector` seçili durumu her iki
ekranda da doğru bildiriyor. Ağaçta doğru olması duyurulduğu anlamına gelmez;
TalkBack doğrulaması Faz 16'da duruyor.

**Sabit regresyon listesi**
21 maddenin tamamı geniş emülatörde koşuldu, hepsi geçti. Toplam 219,89 tam
çıktı; USD'ye geçince $3,73 (= 15999 × 10.000 + 214.250, bölü 428.500).
Türkçe metinler `cmd locale set-app-locales tr-TR` ile ayrıca doğrulandı.

**Sonraki faz için not**
- **`ExchangeRateTable.Default` kurları (42,85 / 46,20 / 53,90) hâlâ
  doğrulanmadı.** Artık kullanıcı düzeltebiliyor ve ekran bunların tahmin
  olduğunu açıkça söylüyor, ama yayından önce elle kontrol maddesi duruyor.
- Klavye/inset kararı bir sonraki prompta bırakıldı; `imePadding` ilk kez
  girecekse bu tek ekranın değil projenin kararı.

---

### Hotfix — IME insets: ölçüm ve `adjustResize` kararı (2026-09-03)

**Amaç:** 9b-2'de bulunan "klavye açıkken butonlar altta kalıyor" kusurunu
`Modifier.imePadding()` ile kapatmak, ve Faz 10 / Faz 15 öncesinde bir
strateji kurmak. **Ölçüm olumsuz çıktı, uygulama yapılmadı.**

**a. Kur ekranında kaydırma var mı:** var. `ExchangeRatesScreen.kt`'deki kök
`Column` `verticalScroll(rememberScrollState())` taşıyor. Eksik olan kaydırma
değil, kaydırma görünümünün klavye kadar küçülmemesi.

**b/c. `WindowInsets.ime` ölçümü.** Kur ekranına geçici bir probe konuldu:
`Scaffold`'un dışında, composable gövdesinin başında,
`WindowInsets.ime.getBottom(density)` ve `WindowInsets.navigationBars.getBottom(density)`
logcat'e yazıldı.

| Cihaz | Klavye | `ime` | `navigationBars` |
|---|---|---|---|
| API 29, 360dp | kapalı | 0 | 0 |
| API 29, 360dp | **açık** | **0** | 0 |
| API 34, 411dp | kapalı | 0 | 0 |
| API 34, 411dp | **açık** | **0** | 0 |

Klavye açıkken (`dumpsys input_method` → `mInputShown=true`) tuşa basılarak
**yeniden kompozisyon zorlandı** ve probe yeni satır yazdı — okunan değer
bayat değil, gerçekten sıfır.

**d. Sonuç: `imePadding()` iki API'de de işe yaramaz.** Beklenti API 29'un
sorunlu, API 34'ün sağlam olmasıydı; **ikisi de sıfır** verdi, yani sebep API
sürümü değil. Kanıt `navigationBars = 0`: iki emülatörde de gezinme çubuğu
var, insets uygulamaya ulaşsaydı orada sıfırdan başkası görünürdü. Uygulama
`setDecorFitsSystemWindows(false)` / `enableEdgeToEdge` çağırmadığı için
insets'i decor view tüketiyor, Compose'a hiç ulaşmıyor. Sıfır bir insets'e
padding uygulamak hiçbir şey yapmaz.

Promptun kuralı gereği **durdum**: alternatif (`enableEdgeToEdge` veya
manifestte `adjustResize`) ayrı bir karar ve ikisi de bu promptta yasaktı.
GÖREV 2 atlandı, üretim kodu değişmedi.

**Sheet'lerin neden sorunu yok:** `ModalBottomSheet` içeriğini
`Box(Modifier.fillMaxSize().imePadding())` içine koyuyor (material3 1.4.0,
`ModalBottomSheet.kt:186`) ve kendi dialog penceresinde çiziliyor — o pencere
insets alıyor. Bu bir tesadüf, uygulamanın kararı değil.

**Temizlik ve doğrulama**
- Probe `git checkout` ile birebir geri alındı; `git status` temiz, commit'e
  girmedi.
- `assembleDebug --rerun-tasks` geçti, yeni uyarı yok.
- `testDebugUnitTest --rerun-tasks`: **84 test, hepsi geçti.**
- Üretim kodu değişmediği için emülatör regresyon turu koşulmadı — doğrulanacak
  bir davranış değişikliği yok.
- Emülatör ayarları geri alındı (`show_ime_with_hard_keyboard 0`, `font_scale 1.0`).

**Belgeler**
- `ARCHITECTURE.md` **§16 IME (Klavye) Insets** — ölçüm tablosu, sebep,
  sheet'in neden ayrıcalıklı olduğu ve iki seçenekli açık karar.
- `TESTING.md` — metin alanı olan her ekranda klavye açıkken buton
  erişilebilirliğinin kontrol edileceği madde, ekran listesiyle.

**Not:** API 34 emülatöründe bu turda da "System UI isn't responding"
diyaloğu çıktı, bu sefer force-stop yapmadan, açılışta. Diyaloğu "Wait" ile
kapatmak yetiyor; ölçümü etkilemedi.

**Devam — `adjustResize` uygulandı (2026-09-03)**

Yukarıdaki ölçüm `imePadding()` yolunu kapattı. Sohbette verilen karar:
manifestte `MainActivity`'ye `android:windowSoftInputMode="adjustResize"`.
Tek satır, Compose kodunda hiçbir değişiklik yok.

**Neden `enableEdgeToEdge` değil:** ikisi de sorunu çözer, ama edge-to-edge
insets modelini baştan değiştirir — durum ve gezinme çubuğu payları her
ekranda elle uygulanmak zorunda kalır. O iş Faz 16'daki `targetSdk`
yükseltmesine ait ve ROADMAP'e madde olarak eklendi. `adjustResize`
**bilinçli olarak geçicidir**: edge-to-edge'e geçildiğinde sistem onu yok
sayar, o gün kaldırılacak.

**Kur ekranı — asıl sınav.** Klavye üst kenarı artık kaydırma görünümünün alt
sınırından okunuyor, çünkü pencere gerçekten küçülüyor.

| Cihaz | fs | Klavye üstü | Kaydet (kaydırma sonrası) | Varsayılana dön | Kaç fiske |
|---|---|---|---|---|---|
| Dar API 29 | 1.0 | y=**870** | `[32,622][688,718]` | `[32,742][688,838]` | 1 |
| Dar API 29 | 2.0 | y=**870** | `[32,600][688,707]` | `[32,731][688,838]` | 1 |
| Geniş API 34 | 1.0 | y=**1633** | `[42,1308][1038,1434]` | `[42,1466][1038,1592]` | 1 |
| Geniş API 34 | 2.0 | y=**1633** | `[42,1279][1038,1419]` | `[42,1451][1038,1591]` | 1 |

Dördünde de iki buton **tam görünür** ve dokunma alanları eşiğin üstünde
(dar 96px = 48dp, fs2.0'da 107px = 53,5dp; geniş 126px = 48dp, fs2.0'da
140px = 53,3dp). Karşılaştırma için 9b-2'deki hâl: dar fs1.0 ve geniş fs2.0
kombinasyonlarında butonlara **hiç** ulaşılamıyordu, kaydırma payı yoktu.

USD, EUR ve GBP alanları ayrı ayrı odaklandı; üçünde de aynı sonuç — pencere
aynı yere küçülüyor, alanın hangisi olduğu fark etmiyor.

**Ekleme sheet'i — regresyon yok.** Endişe, `ModalBottomSheet`'in kendi
`imePadding()`'i ile pencere küçülmesinin üst üste binmesiydi. Dört
kombinasyon 9b-1 hotfix'indeki değerlerle karşılaştırıldı:

| Cihaz | fs | Klavye | Ölçülen | 9b-1 referansı |
|---|---|---|---|---|
| Dar | 1.0 | kapalı | Scroll `[0,513][720,1056]` Kaydet `[48,1104][672,1200]` | **aynı** |
| Dar | 1.0 | açık | Scroll `[0,144][720,558]` Kaydet `[48,606][672,702]` | **aynı** |
| Dar | 2.0 | kapalı | Scroll `[0,226][720,1045]` Kaydet `[48,1093][672,1200]` | **aynı** |
| Geniş | 1.0 | kapalı | Scroll `[0,1328][1080,2043]` Kaydet `[63,2107][1017,2233]` | **aynı** |
| Geniş | 1.0 | açık | Scroll `[0,508][1080,1223]` Kaydet `[63,1287][1017,1413]` | **aynı** |
| Geniş | 2.0 | kapalı | Scroll `[0,1098][1080,2029]` Kaydet `[63,2092][1017,2232]` | **aynı** |
| Geniş | 2.0 | açık | Scroll `[0,278][1080,1209]` Kaydet `[63,1272][1017,1412]` | **aynı** |

Tek farklı satır dar fs2.0 klavye açık: sheet dibi 782 (9b-1'de 870), Kaydet
`[48,595][672,702]` (9b-1'de `[48,683][672,790]`). **Bu bir regresyon değil,
klavye yüksekliği farkı:** 9b-1 ölçümünde ondalık klavye açıktı, bu turda ad
alanına odaklanıldığı için öneri şeridi olan metin klavyesi açıldı ve daha
uzun. İlişki aynı kaldı — sheet dibi = klavye üst kenarı, Kaydet'in altı
sheet dibinden tam **80px** (SheetBottomPadding 40dp) yukarıda, iki turda da.

Çift küçülme olsaydı sheet dibi klavye üstünün bir klavye boyu daha yukarısına
düşerdi; düşmedi. Sebebi yapısal: sheet kendi dialog penceresinde çiziliyor,
`windowSoftInputMode` Activity'nin penceresine uygulanıyor. İki mekanizma
birbirine değmiyor. Kaydırınca Kaydet yine sabit kalıyor.

**Diğer ölçümler**
- Ana ekran ve ayarlar ekranı düzeni klavye kapalıyken **birebir aynı**
  (`Total Monthly [42,338][1038,633]`, chip'ler `[84,530][152,583]` …).
  Beklenen: `adjustResize` yalnızca klavye açıkken devreye giriyor.
- Döndürme, kur ekranı klavye açıkken: yatayda ekran ve yazılan metin
  ("42.857") korunuyor, viewport 1017'ye küçülüyor, dikeyde geri dönüyor.
- Döndürme, sheet klavye açıkken: yatayda sheet açık kalıyor, metin duruyor,
  Kaydet `[423,787][1977,913]`'te görünür.
- Sabit regresyon listesi **her iki emülatörde** koşuldu, hepsi geçti.
  API 29'da iki madde cihaz kısıtı yüzünden atlandı ve geniş emülatörde
  doğrulandı: koyu tema (`cmd uimode night` API 29'da etkisiz) ve Türkçe
  (`cmd locale` API 33+).

**Doğrulama**
- `assembleDebug --rerun-tasks` geçti, yeni uyarı yok.
- `testDebugUnitTest --rerun-tasks`: **84 test, hepsi geçti.**
- Emülatör ayarları geri alındı.

**Ölçüm sırasında öğrenilen**
Otomasyon notu: ekranın sağ kenarına 60px'ten yakın başlayan `input swipe`,
API 34'te **geri hareketi** olarak yorumlanıyor ve uygulamadan çıkıyor.
Kaydırarak silme testleri kenardan en az 100px içeriden başlatılmalı.

---

## [Faz 9b-1] Navigation, DataStore ve Ana Para Birimi Tercihi — 2026-08-30

**Durum:** Tamamlandı (9b-2 — kur düzenleme — ayrı prompt)

**Yapılanlar — belgeler ve bağımlılıklar**
- `ARCHITECTURE.md`'ye **§13 Navigation** ve **§14 Kullanıcı Tercihleri** eklendi;
  kod bunlara göre yazıldı, tersi değil.
- **Sürüm tavanı çıkmadı.** `navigation-compose 2.9.5` ve
  `datastore-preferences 1.1.7` ilk denemede geçti; `checkDebugAarMetadata`
  compileSdk/AGP şikayeti **vermedi**, Faz 5a'daki hilt-navigation-compose
  durumu tekrarlanmadı. AGP 9.0.1 ve compileSdk 36.1 olduğu gibi kaldı.

**Yapılanlar — kod**
- `SettingsRepository` (domain) / `SettingsRepositoryImpl` (data). Para birimi
  **ISO kodu** olarak saklanıyor, ordinal değil: ordinal, enum'a bir sabit
  eklendiği gün sessizce anlam değiştirir, dosya ise onu yazan sürümden uzun
  yaşar. Okuma `IOException`'da `emptyPreferences()`'a düşüyor, başka her hata
  yeniden fırlatılıyor.
- `DataStoreModule` `@Singleton`. `RepositoryModule`'e ikinci `@Binds`.
- `ui/navigation/`: `Destination` (düz String rota) + `SubTrackNavHost`.
  `hiltViewModel()` yalnızca `composable` bloklarında; ekranlar durumsuz.
  Ayarlara gidişte `launchSingleTop = true` — çift dokunuş iki kopya yığmasın.
- `MainActivity` yalnızca tema + `SubTrackNavHost`.
- `CurrencySelector` `ui/home/components/` → `ui/common/`, içeriği değişmeden.
- `SettingsViewModel` **iyimser kopya tutmuyor:** dokunuş yazar, ekran store
  yeniden yayınladığı için güncellenir. Yazma başarısız olursa chip'ler yerinde
  kalır — ekranda görünen, gerçekten kayıtlı olandır.
- `HomeViewModel` üç akışı `combine` ediyor; toplam seçili para biriminde.

**Testler**
- 46 → **58 birim testi**, hepsi geçti. Enstrümantasyon **9/9, iki emülatörde de**.
- `SettingsRepositoryImplTest` geçici dosya üzerinde **gerçek DataStore** ile
  çalışıyor, fake ile değil — asıl sınanan şey diskten geri okumak.
- **`multiple DataStores active` hatası testte kasıtlı üretildi.** Aynı dosya
  üzerinde ikinci store açmak `IllegalStateException` fırlatıyor; test ilk
  scope'u iptal ederek geçiyor. Bu, §14'teki "tekillik Hilt'in sorumluluğudur"
  cümlesinin çalışma zamanı kanıtı.

**Değişen dosyalar**
- `domain/repository/SettingsRepository.kt`, `data/repository/SettingsRepositoryImpl.kt` — yeni
- `di/DataStoreModule.kt` — yeni; `di/RepositoryModule.kt` — ikinci binding
- `ui/navigation/Destination.kt`, `ui/navigation/SubTrackNavHost.kt` — yeni
- `ui/settings/SettingsUiState.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt` — yeni
- `ui/common/CurrencySelector.kt` — `ui/home/components/` altından taşındı
- `MainActivity.kt`, `ui/home/HomeScreen.kt`, `HomeViewModel.kt`, `HomeUiState.kt`
- `ui/home/components/AddSubscriptionSheet.kt` — yalnızca import satırı
- `res/values/strings.xml`, `res/values-en/strings.xml`
- `gradle/libs.versions.toml`, `app/build.gradle.kts`
- `docs/ARCHITECTURE.md`, `docs/WORKFLOW.md`, `docs/TESTING.md`

**Commit'ler**
- `2d172e4` docs: add navigation and datastore architecture decisions
- `8de2106` build: add navigation-compose and datastore-preferences
- `b21171f` feat: store the main currency preference in DataStore
- `ae2d16f` feat: add a settings screen behind a navigation host
- `ce4b24b` feat: total subscriptions in the chosen main currency
- `aa8f39f` test: cover the settings repository, view model and currency switch
- `10ef2f4` docs: add navigation and preference regression checks

**Karşılaşılan sorunlar**
- **`b21171f` tek başına derlenmiyor.** `git mv` dosya taşımasını çoktan
  stage'lemişti ve o commit'e girdi; `package` satırının düzeltilmesi bir
  sonraki commit'te kaldı. **Geçmiş düzeltilmedi** — bilinçli karar: taşıma
  içerik taşımıyor, geçmişi yeniden yazmanın riski kazancından büyük.
- Emülatörler oturumlar arasında iki kez kendiliğinden kapandı, yeniden
  başlatıldı. Bu turda ikisi de **İngilizce locale + açık temada** açıldı;
  API 29 bu sefer koyu temada kilitli değildi.

**Emülatör test sonuçları**

| # | Test | Dar (API 29, 360dp) | Geniş (API 34, 411dp) |
|---|---|---|---|
| 1 | Ayarlara gidiş | geçti — ikon 96×96px = **48×48dp** | geçti — 126×126px = **48×48dp** |
| 2 | İki geri yolu + 5 gidiş-gel | geçti — tek geri tuşuyla launcher | geçti |
| 3 | Tercih toplamı değiştiriyor | geçti — TRY 630,91 → **$14,72** | geçti — aynı |
| 4 | Kalıcılık | geçti — `settings.preferences_pb` 24 byte | geçti |
| 5 | Tek instance | temiz — logcat'te `multiple DataStores` yok | temiz |
| 6 | Ayarlarda döndürme | geçti — ekran ve seçim korundu | geçti |
| 7 | Dar + font_scale 2.0 | geçti — chip 80,0/82,0/80,0/83,0dp, kırpılma yok | — |
| 8 | Temiz kurulum (`pm clear`) | geçti — TRY 0.00, çökme yok | geçti |

Sabit regresyon listesi (19 madde) geniş emülatörde bir kez koşuldu, hepsi geçti.
Türkçe metinler `cmd locale set-app-locales tr-TR` ile ayrıca doğrulandı.

Test 8'in kanıtı dikkate değer: `pm clear` sonrası `files/datastore/` **yok**,
ama uygulama TRY ile açılıyor. DataStore dosyayı ilk yazmada oluşturuyor,
okuma varsayılana düşüyor — §14'ün tarif ettiği davranış.

**Bulgu — chip seçim durumu ayarlar ekranında görünüyor**
Hotfix kaydındaki "dört chip de checked=false bildiriyor" maddesinin tersi
ölçüldü: ayarlar ekranında chip'ler `checkable="true"`, seçili olan
`checked="true"` veriyor (dar ve geniş emülatörde, dört ayrı dump). Ekleme
sheet'inin dump'ında ise `checkable="false"` çıkıyor. Yani sorun bileşende
değil, **sheet penceresinin dump'ında** — o pencerenin `uiautomator dump`
çıktısı zaten Faz 9a'dan beri güvenilmez. TalkBack olmadan kesin konuşulamaz;
madde Faz 16'da açık kaldı.

**Bulgu — koyu temada iki tanımsız rol**
`TopAppBar` **palet içinde**: açık temada `#FFFFFF`, koyu temada `#2D3436`,
ikisi de tanımlı `surface`. Ama `outline` ve `onSurfaceVariant` tanımsız
olduğu için Material baseline moru geliyor: chip kenarlığı **#49454F**,
seçilmemiş chip etiketi ve ayarlar açıklama metni **#CAC4D0** (koyu) /
**#49454F** (açık). Kontrast AA geçiyor (9.66:1 ve 6.85:1) — sorun
okunabilirlik değil, palet tutarlılığı. Faz 14'e eklendi, düzeltilmedi.

**IME ölçümü — önceki bulgu yanlıştı**
İlk raporda "sheet API 29'da klavyeyle yukarı kaymıyor" yazılmıştı. **Yanlış.**
Sebep AVD'nin donanım klavyesi (`hw.keyboard`, config `qwerty/v/v`): yazılım
klavyesi hiç çizilmiyordu. `mInputShown=true` görünüyor ama IME penceresinin
`mGivenContentInsets=[0,1232][0,0]` ve dokunma bölgesi boş — ekranda yer
kaplamıyor. `settings put secure show_ime_with_hard_keyboard 1` ile gerçek
telefon durumu üretildi ve yeniden ölçüldü:

| Cihaz | font_scale | Klavye üst kenarı | Save (klavye açıkken) | Erişilebilir mi |
|---|---|---|---|---|
| Dar API 29 | 1.0 | y=**782** | `[329,763][391,782]` — 40px'in 19'u görünür, **kırpık** | Evet — tek fiske, içerik 129px kaydı, Save `[329,634][391,674]`, klavyeden 108px yukarıda |
| Dar API 29 | 2.0 | y=**870** | ağaçta **yok**, viewport dışında | Evet — iki fiske, içerik 328px kaydı, Save `[303,699][417,774]`, klavyeden 96px yukarıda |
| Geniş API 34 | 1.0 | y=**1517** | `[500,1323][580,1376]` — **tam görünür** | Kaydırma gerekmedi |
| Geniş API 34 | 2.0 | y=**1517** | `[465,1293][616,1391]` — **tam görünür** | Kaydırma gerekmedi |

Klavye üst kenarı `ScrollView` düğümünün alt sınırından okundu; pencere
`adjustResize` ile küçüldüğü için ikisi aynı çizgi. Klavye açılınca sheet her
iki cihazda da tam yüksekliğe genişliyor ve `verticalScroll` gerçekten
çalışıyor — Hotfix'te eklenen kaydırma **klavye durumunda da işini görüyor**.
Ama dar ekranda Save **ilk anda kırpık (fs 1.0) veya hiç görünmez (fs 2.0)**
geliyor; kullanıcının kaydırması gerekiyor.

Manifest'te `windowSoftInputMode` **tanımlı değil** (platform varsayılanı;
ölçüm pencerenin pan değil resize ettiğini gösteriyor). Kod tabanında
`imePadding`, `WindowInsets`, `enableEdgeToEdge`, `setDecorFitsSystemWindows`
**hiç geçmiyor**. Düzeltme yapılmadı, ayrı prompt bekliyor.

**Düzeltme — kur hesabı formülü**
Faz 9b-1 raporunda TRY toplamı için yazılan `(1099×428500+214250)/10000`
formülü **yanlıştı**; 47113 verir. Koddaki yuvarlama terimi **bölenin** yarısı,
yani hedef kurun yarısı: `divideHalfUp` içinde `val half = denominator / 2` ve
çağrıda `denominator = rates.rateOf(to)`. TRY'ye çevirirken bölen 10.000,
terim 5.000: `(1099×428500+5000)/10000 = 47092` — cihazın gösterdiği değer.
**Kodda hata yok**, hatalı olan rapordaki elle hesaptı.

**Not — 214250 sayısı bu kayıtta iki kez geçiyor, ikisi de doğru bağlamda.**
Yuvarlama terimi **her zaman bölenin yarısı**, bölen de **hedef** kur:
TRY'ye çevirirken bölen 10.000, terim 5.000; USD'ye çevirirken bölen 428.500,
terim 214.250. Yukarıdaki hata, TRY hedefi için USD'nin terimini kullanmaktı.

**Sonraki faz için not**
- 9b-2: kur düzenleme. `ExchangeRateTable.of()` eksikleri varsayılandan
  dolduruyor, `CurrencyConverter` tabloyu zaten dışarıdan alıyor —
  `SettingsRepository`'ye ikinci bir anahtar grubu eklenecek.
- `ExchangeRateTable.Default` kurları (42,85 / 46,20 / 53,90) **hâlâ
  doğrulanmadı.** 9b-2 bunları düzenlenebilir yapınca tekrar gündeme gelecek.

---

### Hotfix — Kaydet butonu kaydırma alanının dışına alındı (2026-08-30)

**Bulunan:** 9b-1 kapanış ölçümünde, 360dp'de klavye açıkken Kaydet
font_scale 1.0'da 40px'in 19'u görünecek şekilde kırpıktı, font_scale 2.0'da
erişilebilirlik ağacında **hiç yoktu**. Kaydırarak ulaşılıyordu ama
kullanıcıya kaydırması gerektiğini söyleyen hiçbir işaret yoktu. Faz 10, 11 ve
12 bu sheet'e üç alan daha ekleyecek; taşma davranışının onlardan önce
düzeltilmesi gerekiyordu.

**Kaynaktan doğrulananlar** (material3 1.4.0, foundation-layout 1.9.5):
- `ModalBottomSheet.kt:186` — kütüphane içeriği zaten
  `Box(Modifier.fillMaxSize().imePadding())` içine koyuyor. **IME insets'i
  hâlihazırda halledilmiş**, projeye `imePadding` / `WindowInsets` /
  `enableEdgeToEdge` sokmaya gerek yok. Manifest'e de dokunulmadı.
- İçerik `Surface` içindeki bir `Column`'a `ColumnScope` alıcısıyla geçiyor.
  `Surface`'ın yükseklik kısıtı yok, `draggableAnchors` bloğu `sheetSize.height`
  ile `constraints.maxHeight`'ı karşılaştırıyor: içerik **wrap-content**
  ölçülüyor ama üst sınır olarak kullanılabilir yükseklik veriliyor. Dış `Box`
  `fillMaxSize` olduğu için bu sınır **sonlu** — yani `weight` çalışır.

**Seçilen yöntem: `Modifier.weight(1f, fill = false)`.**
`ColumnScope.weight` KDoc'u (`Column.kt:292`) ve `RowColumnMeasurePolicy.kt:198`
birlikte okununca: `mainAxisMin = if (parentData.fill) childMainAxisSize else 0`,
`mainAxisMax = childMainAxisSize`. Yani `fill = true` sheet'i **her zaman tam
ekran** yapardı ve Hotfix serisindeki "sheet içeriğe göre boyutlanıyor"
davranışını bozardı. `fill = false` ile kaydırma bölgesi
`minHeight = 0, maxHeight = kalan alan` kısıtıyla ölçülüyor: form kısaysa
küçülüyor, uzunsa kırpılıp kaydırılıyor. Kaydet weight'siz kardeş olduğu için
`fixedSpace`'e giriyor — yeri **form ölçülmeden önce** ayrılıyor.

Ekstra sarmalayıcıya gerek olmadı: sheet'in `content` lambda'sı zaten
`ColumnScope`, iki kardeş doğrudan oraya kondu. Alan sırası, doğrulama,
hata gösterimi, `CurrencySelector` ve string kaynakları **değişmedi**;
`skipPartiallyExpanded = true` korundu. Kaydet ile seçici arasındaki 24dp
boşluk `Spacer` olmaktan çıkıp butonun üst padding'i oldu — böylece sabit
kalıyor, kaydırılıp gitmiyor.

**Ölçüm** (`show_ime_with_hard_keyboard 1` ile, dört kombinasyonun hepsi):

| Cihaz | fs | Klavye üstü | Kaydet (klavye açık) | Dokunma alanı | Kaydırınca oynuyor mu |
|---|---|---|---|---|---|
| Dar API 29 | 1.0 | y=782 | `[48,606][672,702]` tam görünür | 96px = **48dp** | hayır — üç dump'ta da aynı |
| Dar API 29 | 2.0 | y=870 | `[48,683][672,790]` tam görünür | 107px = **53,5dp** | hayır |
| Geniş API 34 | 1.0 | y=1517 | `[63,1287][1017,1413]` tam görünür | 126px = **48dp** | hayır |
| Geniş API 34 | 2.0 | y=1517 | `[63,1272][1017,1412]` tam görünür | 140px = **53,3dp** | hayır |

Öncesiyle karşılaştırma: dar fs1.0'da Kaydet `[329,763][391,782]`'de kırpıktı,
dar fs2.0'da ağaçta yoktu. Şimdi dördünde de tam görünür ve kaydırmadan
dokunulabilir.

**Klavye kapalıyken sheet yüksekliği değişmedi — dört kombinasyonda da 0dp
fark.** Kaydet metni klavyesiz konumlarda birebir aynı yerde: dar fs1.0
`[329,1132][391,1172]`, dar fs2.0 `[303,1109][417,1184]`, geniş fs1.0
`[500,2143][580,2196]`, geniş fs2.0 `[465,2113][616,2211]`. `fill = false`
seçiminin koruduğu şey tam olarak buydu.

Yatayda (2400x1080) da kontrol edildi: kaydırma bölgesi `[360,190][2040,723]`,
Kaydet `[423,797][1977,902]` — ekranda ve sabit.

**Fonksiyonel:** iki emülatörde de abonelik eklendi, sheet kapandı, satır ve
toplam güncellendi. Dar emülatörde font_scale 2.0'da kaydırmadan kaydedildi —
düzeltmenin asıl kazancı bu.

**Testler:** 58 birim testi geçti, yeni derleme uyarısı yok.
`docs/TESTING.md` sabit regresyon listesi (21 madde) geniş emülatörde bir kez
koşuldu, hepsi geçti.

**Belge:** `docs/TESTING.md`'deki soft klavye bölümü iki yönlü hale getirildi —
ölçümden önce `show_ime_with_hard_keyboard 1`, sonra `0`. Unutulursa klavye
testi hata vermeden yanlış sonuç verir.

**Commit'ler**
- `63026ca` fix: keep the save button out of the sheet's scrolling region

---

## [Faz 9a] Para Birimi Seçimi ve Normalizasyon — 2026-08-30

**Durum:** Tamamlandı (9b — ayarlar ekranı — ayrı prompt)

**Yapılanlar — kur dönüşümü**
- **`Currency`, `ExchangeRateTable`, `CurrencyConverter`** domain'e eklendi.
  Kurlar `Long`, **10.000 ölçekli**, hepsi tek çıpaya (TRY) göre yazılı.
  Böylece **her çift tek adımda** çevriliyor — kaynak kuru bölü hedef kuru,
  ölçek sadeleşiyor — ve USD→EUR gibi bir çapraz dönüşüm iki kez değil
  **bir kez** yuvarlanıyor.
- **Yuvarlama HALF_UP.** Gerekçe: `parsePrice` da HALF_UP kullanıyor; aynı
  tutar girişte bir şey, gösterimde başka bir şey olamaz. HALF_EVEN'in
  koruduğu şey tekrarlı yuvarlamanın yukarı kayması, o da burada olmuyor.
- **`totalIn` önce her para birimi içinde topluyor, sonra çeviriyor.**
  Hata böylece abonelik sayısıyla değil **para birimi sayısıyla** (en fazla
  dört) sınırlı. Kanıt testte sabitlendi: 4 adet 0,01 USD → satır satır
  çevrilirse **1,72 TL**, grup halinde **1,71 TL** (doğrusu 1,714).
- Dönüşüm mantığı saf Kotlin; domain'de tek yabancı import Faz 3'ten kalma
  `kotlinx.coroutines.flow.Flow`.

**Yapılanlar — arayüz**
- **`CurrencySelector`, `FilterChip` sırası.** SegmentedButton kaynağa
  bakılarak elendi: `SegmentedButton.kt:425` her segmentte ikon çizilsin
  çizilmesin **18dp + 8dp** ayırıyor, dört segment ~320dp istiyor ve sheet
  padding'inden sonra 360dp ekrana sığmıyor.
- `MoneyFormatter` — `NumberFormat.getCurrencyInstance(locale)`, para birimi
  açıkça set ediliyor. Kart kendi para biriminde, dashboard normalize edilmiş
  toplamda; karışık listede altına çevrim notu çıkıyor.

**Uyarı — kurlar tahmindir**
`ExchangeRateTable.Default` içindeki 42,85 / 46,20 / 53,90 değerleri
**doğrulanmadı.** Yayından önce elle kontrol edilmeli; kodda tarih ve
"bunlar eskir" notu var.

**Testler**
- 29 → **46 birim testi**, hepsi geçti (`CurrencyConverterTest` 14 yeni).

**Değişen dosyalar**
- `domain/model/Currency.kt`, `domain/model/ExchangeRateTable.kt` — yeni
- `domain/usecase/CurrencyConverter.kt` — yeni
- `domain/model/Subscription.kt` — `currencyCode: String` → `currency: Currency`
- `data/mapper/SubscriptionMapper.kt` — enum dönüşümü, şema değişmedi
- `ui/common/MoneyFormatter.kt` — yeni
- `ui/home/components/CurrencySelector.kt` — yeni
- `ui/home/HomeViewModel.kt`, `HomeUiState.kt`, `HomeScreen.kt`,
  `AddSubscriptionSheet.kt`, `DashboardCard.kt`
- `res/values/strings.xml`, `res/values-en/strings.xml`

**Commit'ler**
- `a709af2` feat: add currency conversion to domain
- `6923adc` feat: add currency selector to add sheet
- `55c837d` feat: show per-subscription currency and normalized total
- `a97d8eb` test: add currency conversion tests

**Elle test sonucu (fiziksel cihaz, OPPO A15s)**
- a, b, c, d, e, g **geçti**.
- h'de chip'ler alt satıra **kaymadı** — beklenen, cihaz 423dp, dar değil.
- f'de bulgu (hata değil): İngilizce dilde toplam **"TRY1,785.45"** diye
  boşluksuz çıkıyordu. `NumberFormat` sembolü locale'e göre seçiyor; kartta
  `$10.99`, toplamda ISO kodu yazması tutarsız görünüyor ama teknik olarak
  doğru. Sembol/kod tutarlılığı kararı **Faz 14'e** madde olarak eklendi;
  okunabilirlik `cac1f63` ile düzeltildi.

**Tag**
- `phase-9a-done`

---

## [Emülatör Kurulumu] Dar Ekran ve Farklı Android Sürümü — 2026-08-30

**Durum:** Tamamlandı (TalkBack kısmı hariç)

**Kurulan AVD'ler**

| AVD | Çözünürlük | Yoğunluk | Efektif genişlik | API |
|---|---|---|---|---|
| `subtrack_narrow_api29` | 720x1280 | 320 dpi | **360dp** | 29 |
| `subtrack_wide_api34` | 1080x2400 (Pixel 6) | 420 dpi | 411dp | 34 |

**En önemli bulgu:** fiziksel cihaz ölçüldü — `init=720x1600 320dpi
base=720x1600 272dpi` → efektif genişlik **423dp**. Yani Faz 9a'da "360dp'ye
sığar mı" diye hesaplanan durum **hiç ekranda görülmemişti**.

**Ölçümler**
- **Chip genişlikleri 360dp'de:** TRY 57,5 · USD 59,0 · EUR 58,0 · GBP 60,0dp.
  Aralar 8,0dp, sol kenar 24,0dp, yükseklik 32,0dp. Dördü **tek satırda**,
  toplam 258,5dp, sağda **77,5dp boş**. Faz 9a'daki "~58dp" tahmini tuttu.
- **font_scale 2.0'da FlowRow sarıyor:** TRY/USD/EUR üst satır (80,0 / 82,0 /
  80,0dp), GBP alt satır (83,0dp). **Kırpılma yok.**
- **Enstrümantasyon testleri 9/9**, iki emülatörde de geçti.
- **Açılış medyanı (5 tekrar):** API 29 **7055 ms**, API 34 **3946 ms**,
  fiziksel cihaz ~8100 ms. Hepsi debug build.
- **Dynamic color paletimizi ezmiyor** — API 34 koyu temada dashboard kartı
  hâlâ PastelBlue. Faz 14 için: sorun Material You değil, tanımsız roller.
- **Uygulamadan tek bir FATAL veya StrictMode ihlali yok.** Logcat'teki
  `FATAL EXCEPTION` `com.google.android.sdksetup`'a ait (Olson timezone),
  StrictMode ihlalleri `android.process.acore` ve `com.google.android.gms`.

**TalkBack — YAPILAMADI**
Her iki `google_apis_playstore` imajında da Android Accessibility Suite
kurulu değil (`pm list packages -u` ve `/system/priv-app` taramasında iz
yok). Kurmanın iki yolu var, ikisi de reddedildi: emülatörde Google hesabına
giriş, ve üçüncü taraf APK indirme. **Faz 16'ya taşındı.**

**Değişen dosyalar**
- `docs/TESTING.md` — "Emülatör Testleri" bölümü eklendi

**Commit'ler**
- `23b2459` docs: document emulator testing setup

**Karşılaşılan sorunlar**
- `cmd uimode night yes` **API 29'da çalışmıyor**; koyu tema testleri API 34'te.
- `uiautomator dump` bottom sheet açıkken çoğunlukla alttaki pencereyi
  döndürüyor. Piksel ölçümü için ham `screencap` tamponu elle çözümlendi
  (makinede görüntü kütüphanesi yok, kurulmadı).
- `connectedDebugAndroidTest` bitince uygulamayı **kaldırıyor**.

---

## [Hotfix] Erişilebilirlik Birleştirme, Dar Ekran Sheet, Para Birimi Boşluğu — 2026-08-30

**Durum:** Tamamlandı

**1. Semantics birleştirme (`de60e47`)**

**Önemli bulgu — önceki teşhis yanlıştı.** `mergeDescendants` aslında
çalışıyordu. Erişilebilirlik ağacı **birleştirilmemiş** ağaçtan kuruluyor:
`SemanticsOwner.kt:157`'deki `getAllUncoveredSemanticsNodesToIntObjectMap`
`unmergedRootSemanticsNode`'dan başlıyor ve `replacedChildren` üzerinden
yürüyor. Yani birleşen bir düğümün çocukları **her zaman** ayrı düğüm olarak
kalıyor; dump birleşmeyi hiç gösteremiyor. `Surface`'ın kendi
`semantics(mergeDescendants = false)` çağrısı da suçlu değil —
`collapsePeer` bayrağı yalnızca OR'luyor, `false` `true`'yu kapatamıyor.

`clearAndSetSemantics`'e geçildi: `getChildren` temizleyen düğüm için boş
liste döndürüyor, alt ağaç gerçekten kayboluyor. Hem **ölçülebilir** hem de
cümlenin sırası bizim kontrolümüzde. Silme özel eylemi korunuyor —
`calculateSemanticsConfiguration` config'i sıfırlayıp sonra o düğümün kendi
bloğunu uyguluyor.

**2. Dar ekranda Kaydet butonu (`f4a6bd9`)**

360x640dp'de sheet yarı açık geliyordu ve Kaydet ekranın altında kalıyordu —
her iki font ölçeğinde. `skipPartiallyExpanded = true` + içeriğe
`verticalScroll`. İkisi birlikte: expand bildirilen durumu çözüyor, scroll
formun ekrandan uzun olduğu hâli garantiliyor.

**3. Para birimi boşluğu (`cac1f63`)**

ISO kodundan sonra **kırılmaz boşluk (U+00A0)**. Boşluk yalnızca sembol
harflerden oluşuyorsa giriyor; sembollü biçimler değişmedi.

**Doğrulama testi sonuçları (her iki emülatörde)**

| # | Test | Dar (API 29, 360dp) | Geniş (API 34, 411dp) |
|---|---|---|---|
| 1 | Kaydet, font_scale 1.0, sürükleme yok | **geçti** — y=1113..1190, ekran altına 89px | — |
| 2 | Kaydet, font_scale 2.0, sürükleme yok | **geçti** — y=1094..1198, ekran altına 81px | — |
| 3 | Sheet içi kaydırma | **geçti** | — |
| 4 | Tutamaçtan kapatma | **geçti** | — |
| 5 | Sheet boyu içeriğe göre, regresyon yok | — | **geçti** — sheet 457dp, Kaydet 178px içeride |
| 6 | Para birimi biçimi (byte seviyesi) | **geçti** | **geçti** |
| 7 | Kaydırarak silme regresyonu | **geçti** | **geçti** |
| 8 | Döndürmede state korunması | **geçti** | **geçti** |
| 9 | TalkBack "Sil" eylemi | **doğrulanamadı** | **doğrulanamadı** |

Ayrıntılar:
- **Kaydırma (3):** font_scale 2.0'da içerik zaten sığdığı için kaydıracak
  taşma yok, koordinatlar sabit kaldı. Taşmayı zorlamak için **font_scale
  3.0**'a çıkıldı; orada "Currency" ekran dışından y=978'e, sonra y=608'e
  geldi ve Kaydet `[267,1064][453,1184]`'te erişilebilir oldu. Yukarı
  kaydırma sheet'i kapatmıyor; içerik en üstteyken aşağı kaydırma kapatıyor
  (ModalBottomSheet'in standart sürükleyip-kapat davranışı).
- **Byte doğrulaması (6):** ISO kodlu tutarlarda `54 52 59 C2 A0 31` —
  yani "TRY" + U+00A0 + rakam. Sembollü tutarlarda sembol doğrudan rakama
  bitişik, boşluk **yok**.
- **Silme (7):** hafif kaydırma (%20) silmedi, **ardışık üç hafif kaydırma
  da silmedi** (Faz 1a'daki birikme düzeltmesi ayakta), tam kaydırma (%86)
  sildi. Toplam 1.785,45 → 630,91 (= 159,99 + 470,92), doğru.
- **Döndürme (8):** yatayda `TestAbonelik` ve `42.50` korundu, EUR chip'i
  piksel düzeyinde hâlâ seçili (PastelBlue dolgu).

**Commit'ler**
- `de60e47` fix: merge semantics on dashboard and subscription rows
- `f4a6bd9` fix: keep the save button reachable on narrow screens
- `cac1f63` fix: add spacing to currency codes

**Bilinen eksikler**
- **Chip seçim durumu erişilebilirlik ağacında görünmüyor.** EUR görsel
  olarak seçiliyken dört chip de `checked="false" selected="false"`
  bildiriyor. Ekran okuyucu kullanıcısına hangi para biriminin etkin olduğu
  söylenmiyor olabilir — TalkBack'siz doğrulanamıyor, Faz 16'ya madde.
- Silme özel eyleminin gerçekten duyurulup çalıştığı **doğrulanamadı**.
- Dar emülatör koyu temada kilitli kaldı (API 29 kısıtı); doğrulama testleri
  orada koyu temada yapıldı. Kaydet butonunun rengi iki temada aynı olduğu
  için ölçümler etkilenmedi.

**Sonraki faz için not**
- Faz 9b: ayarlar ekranı, Navigation, DataStore. `CurrencyConverter` tablosunu
  zaten dışarıdan alıyor; `ExchangeRateTable.of()` eksik kurları varsayılandan
  dolduruyor, yani yalnızca argüman değişecek.

---

## [Faz 8a] Yükleme, Hata ve Erişilebilirlik — 2026-08-29

**Durum:** Tamamlandı (8b — boş durum ekranı — ertelendi)

**Yapılanlar**
- **`isLoading` UI'a bağlandı.** Faz 5a'dan beri hesaplanıyor ama hiç
  okunmuyordu; açılışta "yükleniyor" ile "hiç abonelik yok" ayırt
  edilemiyordu. Yeni `DelayedLoadingIndicator` bileşeni göstergeyi
  **300 ms geciktiriyor**: yerel Room okuması onlarca milisaniye sürüyor ve
  o kadarlık bir spinner ilerleme değil arıza gibi görünür — kırpışma
  kullanıcıya "bir şeyler ters gitti" hissi verir.
- **Gösterge ekran okuyucuya "meşgul" diyor:** `contentDescription` +
  `liveRegion = Polite`. Sessiz bir spinner, görmeyen kullanıcıya hiçbir
  şey anlatmıyor.
- **Dashboard toplamı tek odak durağı oldu** (`semantics(mergeDescendants = true)`).
  Önce etiket ve tutar ayrı düğümdü; tutarın üstüne düşen okuyucu
  "219.89 TL" deyip neyin toplamı olduğunu söylemiyordu.
- **Dokunma alanı taraması:** `SubscriptionCard` 56dp, FAB 56dp, Kaydet
  butonu görsel olarak 40dp — ama Material3 `Surface.kt` içinde
  `minimumInteractiveComponentSize()` uyguluyor, dokunma alanı 48dp.
  Kaynaktan doğrulandı, **değişiklik gerekmedi**.
- **Kontrast ölçümü** (Faz 1c'den sonra eklenen her metin/zemin çifti):
  Snackbar metni 11.65:1, geri al eylemi 7.73:1, alan altı hata metni
  6.54:1, spinner 3.96:1 (spinner metin değil, eşiği 3:1). Açık ve koyu
  temada hepsi AA geçiyor. **Palete dokunulmadı.**
- **`UiText.Resource` argüman alıyor.** Argümanlar `vararg` değil `List`
  olarak tutuluyor: vararg dizi olurdu ve data class eşitliği referansa
  düşerdi — testler eşitliğe dayanıyor.
- **Fiyat üst sınırı mesajı sınırı söylüyor:** 1000000 metne gömülmek
  yerine argüman olarak geçiyor, tavan değişince mesaj ve kod ayrışamıyor.
- Yükleme ve hata durumları için `@Preview`'lar eklendi.

**Değişen dosyalar**
- `ui/common/DelayedLoadingIndicator.kt` — yeni, 300 ms gecikmeli gösterge
- `ui/common/UiText.kt` — `Resource` artık `args: List<Any>` taşıyor
- `ui/home/HomeScreen.kt` — gösterge bağlandı, yükleme/hata preview'ları
- `ui/home/components/DashboardCard.kt` — `mergeDescendants`
- `ui/home/components/AddSubscriptionSheet.kt` — hata durumu preview'ları
- `ui/home/HomeViewModel.kt` — sınır mesajına `MAX_PRICE` argümanı
- `res/values/strings.xml`, `res/values-en/strings.xml` — `loading`,
  argümanlı `error_price_too_large`
- `test/.../ui/home/HomeViewModelTest.kt` — argümanlı mesaj testi (28 → 29)
- `docs/ARCHITECTURE.md`, `docs/ROADMAP.md` — palet borcu kaydı

**Commit'ler**
- `6c73bbe` feat: show loading indicator while subscriptions load
- `88a8aa1` feat: improve accessibility of dashboard and controls
- `df71ec1` feat: add previews for loading and error states
- `06338ac` feat: support arguments in UiText and show the price limit
- `ee64802` docs: record palette debt for unmapped colour roles

**Tag**
- `phase-8a-done`

**Karşılaşılan sorunlar**
- **Yeniden dene (retry) eylemi eklenmedi — bilinçli karar.**
  `error_save_failed` gerçek bir veritabanı reddi demek; aynı veriyle tekrar
  denemek aynı sonucu verir. "Yeniden dene" düğmesi kullanıcıya olmayan bir
  çıkış yolu vaat eder.
- **Palet borcu:** `inversePrimary` ve `inverseSurface` tanımlı değil,
  Material'ın varsayılan moru devreye giriyor. Kontrast sorunlu değil ama
  kimlik yanlış. Tek tek yamamak yerine **Faz 14'te** (tema gözden geçirme)
  bütün olarak ele alınacak.

**Elle test sonucu**
- Yükleme göstergesi hızlı açılışta hiç görünmüyor (beklenen — 300 ms
  eşiğinin altında kalıyor), yapay gecikmede düzgün çıkıyor.
- Fiyat sınırı mesajı sınır değerini gösteriyor.
- **TalkBack testi yapılamadı:** cihazda TalkBack çok donuyor (OPPO A15s,
  düşük donanım + debug build). Erişilebilirlik değişiklikleri kod düzeyinde
  doğru ama **cihazda doğrulanmadı**. Faz 16 öncesi emülatörde bir kez
  düzgün test edilmeli — ROADMAP Faz 16'ya madde olarak eklendi.

**Sonraki faz için not**
- **Faz 8b (boş durum ekranı) ertelendi.** Tasarım kararı bekliyor: ekranda
  ne yazacağı, hangi görselin kullanılacağı ve kullanıcıyı nereye
  yönlendireceği kararlaştırılmadan kod yazmak boşa iş.
- Faz 14'te palet bütün olarak elden geçirilecek (`inversePrimary`,
  `inverseSurface`).

---

## [Faz 7] Test Altyapısı — 2026-08-29

**Durum:** Tamamlandı

**Yapılanlar**
- **Test bağımlılıkları:** JUnit, **Turbine 1.2.1**, `kotlinx-coroutines-test`.
  Sonuncusu uygulamanın gerçekten çözümlediği **coroutines 1.9.0** ile aynı
  sürüme sabitlendi; farklı sürüm dispatcher hatalarını test hatası gibi
  gösterirdi.
- **Birim testleri (28 test, hepsi geçti):**

  | Sınıf | Test |
  |---|---:|
  | `SubscriptionMapperTest` | 8 |
  | `HomeViewModelTest` | 19 |
  | `ExampleUnitTest` (şablon) | 1 |

- **Enstrümantasyon testleri (9 test, hepsi geçti):** `SubscriptionDaoTest` (8)
  + şablon. **Cihazda çalıştırıldı** (OPPO CPH2179, Android 10), in-memory Room
  üzerinde. Son test Faz 1'deki id çakışması hatasını Room tarafından kapatıyor:
  `AUTOINCREMENT` silinen id'yi asla geri vermiyor.
- **`FakeSubscriptionRepository` elle yazıldı**, mock kütüphanesi kullanılmadı
  (ARCHITECTURE §11). Gerçekten satır saklıyor, böylece testler "hangi metot
  çağrıldı"ya değil **ortaya çıkan listeye** bakıyor.
- **Üretim kodunda tek satır değişmedi.**

### Bulgu — `parsePrice` private

Fiyat doğrulaması saf fonksiyon olarak test edilemedi: `parsePrice` `private`
ve üretim kodunu değiştirmek yasaktı. Testler **public yüzeyden** yazıldı —
`onEvent(Save(...))` gönderilip `uiState.priceError` ve
`repository.inserted`'ın boş kaldığı kontrol ediliyor.

**Bu tercih edilen yol.** İmplementasyonu değil davranışı doğruluyor;
`parsePrice` yeniden adlandırılsa veya başka sınıfa taşınsa testler geçerli
kalır. **`internal` yapılmayacak.**

Tek etkisi sıralama: fiyat testleri fake gerektirdiği için önce mapper, sonra
fake, sonra ViewModel yazıldı.

### Testlerin gerçekten çalıştığının kanıtı

İki beklenti kasten bozuldu:
1. `Money(21989)` → `Money(21988)` (toplam aritmetiği)
2. `assertPriceRejected` içinde `isEmpty()` → `isNotEmpty()`

Sonuç: **`28 tests completed, 7 failed`** — biri aritmetik, **altısı** paylaşılan
yardımcıyı kullanan tüm fiyat reddi testlerinden. Geri alındı, tekrar yeşil.

### İki teknik not

- **`WhileSubscribed` testte tuzak.** `uiState` soğuk; kimse toplamadığı sürece
  `.value` başlangıç değerini döndürür. Testlerde
  `backgroundScope.launch { uiState.collect() }` ile bir toplayıcı açılıyor.
  Açılmasaydı her assert `isLoading = true` görürdü ve testler **hiçbir şey
  doğrulamadan yeşil geçerdi** — sessizce işe yaramaz bir test paketi.
- **`Dispatchers.setMain` zorunlu:** `viewModelScope` `Dispatchers.Main`
  kullanıyor, JVM testinde öyle bir şey yok.

### Faz 3'ün dördüncü hantal noktası ödenmedi

Faz 3'te *"testte sahte repository koymak üretim kodunu değiştirmeyi
gerektirir, Hilt'te `@TestInstallIn` ile modül değiştirilir"* demiştik.
**Gerçekleşmedi.** `HomeViewModel` repository'yi constructor'dan aldığı için
fake'i elle geçirmek yetti; Hilt'e hiç dokunulmadı.

`@TestInstallIn` gerçekten **Hilt'in kendi kurduğu grafı** test ederken
gerekecek: Compose UI testleri (`@HiltAndroidTest`) veya uçtan uca testler.
Faz 8'de boş durum ve yükleme ekranları için UI testi yazılırsa orada çıkar.

**Değişen dosyalar**
- `gradle/libs.versions.toml`, `app/build.gradle.kts` — test bağımlılıkları
- `app/src/test/java/.../data/mapper/SubscriptionMapperTest.kt` (yeni)
- `app/src/test/java/.../fake/FakeSubscriptionRepository.kt` (yeni)
- `app/src/test/java/.../ui/home/HomeViewModelTest.kt` (yeni)
- `app/src/androidTest/java/.../data/local/SubscriptionDaoTest.kt` (yeni)
- `.gitignore` — `/.idea/markdown.xml`

**Commit'ler**
- `a114982` build: add test dependencies
- `e1e0c22` test: add mapper tests
- `a322ae5` test: add home view model tests with fake repository
- `b087356` test: add dao instrumentation tests on an in-memory database
- `82dfaf7` chore: ignore markdown.xml

**Tag**
- `phase-7-done`

**Elle test sonucu**
- Üretim kodu değişmediği için faza özel adım yok; `docs/TESTING.md`'deki sabit
  liste yeterli.
- Uygulamanın hâlâ derlenip cihazda açıldığı doğrulandı.

**Bilinen borç**
- Şablon testler (`ExampleUnitTest`, `ExampleInstrumentedTest`) dolgu — sırasıyla
  `2+2=4` ve paket adı kontrolü. Kapsam dışı bırakıldı, Faz 16 temizliğinde
  kaldırılacak.

**Sonraki faz için not**
- Faz 8: boş durum, yükleme göstergesi, hata gösterimi, erişilebilirlik.
  `HomeUiState.isLoading` hâlâ hesaplanıyor ama UI'da okunmuyor — o fazın ilk
  maddesi.

---

## [Faz 6] Girdi Doğrulama, Hata Gösterimi ve Undo — 2026-08-29

**Durum:** Tamamlandı

**Yapılanlar**
- **`UiText` sarmalayıcısı `ui/common/` altına konuldu, `domain/`'e değil.**
  Çözümlemesi `stringResource` gerektiriyor; domain'e koymak
  `androidx.annotation.StringRes` import'u demekti ve **domain Faz 2'den beri
  sıfır import'la derleniyor.**
- **Girdi doğrulama:** boş ad; boş, geçersiz, negatif veya sıfır fiyat; üst
  sınır; ondalık basamak sayısı. Hatalar **alan bazlı** (`nameError`,
  `priceError`) — tepede "bir şeyler yanlış" diyen tek mesaj değil.
- **Sheet durumu ViewModel'a taşındı** (`HomeUiState.isAddSheetOpen` +
  `OpenAddSheet` / `DismissAddSheet`). Gerekçe: *"hatalı girdide sheet
  kapanmasın"* şartı, kapanma kararının **doğrulama sonucuna bağlı** olması
  demek; composable bunu bilemez. Yan kazanç: ViewModel yapılandırma
  değişikliğinden zaten sağ çıktığı için döndürmede sheet açık kalıyor.
- **Undo:** Snackbar ile geri alma. Silmeden önce `getById` ile okunuyor, geri
  alırken **aynı id** ile ekleniyor — öğe listenin sonuna atlamak yerine eski
  sırasına dönüyor.
- **`CancellationException` yeniden fırlatılıyor.** Yakalansaydı iptal edilen
  bir coroutine "veritabanı hatası" olarak raporlanırdı.
- **Faz 1a'daki uyarı kontrol edildi.** *"Undo sonrası kaydırma state'i
  `EndToStart`'ta takılabilir"* deniyordu; `b678e2d`'de `rememberSaveable` →
  düz `remember` değişikliğiyle sızıntı zaten kapanmış. Gereksiz bir `reset()`
  **eklenmedi**, gerekmediği doğrulandı.

### Mimari karar — `Result<T>` kullanılmıyor

Hatalarımızın çoğu veritabanı hatası değil, **girdi hatası**; ViewModel'da
Room'a hiç ulaşmadan yakalanıyor. `Result<T>` bunlara dokunmaz, yalnızca nadir
DB hataları için her çağrıya sarmalayıcı ekler. Doğrulama ViewModel'da, DB
hataları `try/catch` ile yakalanıp `UiText`'e çevriliyor. Sessiz `try/catch`
yasağı korunuyor — yakalanan her hata kullanıcıya ulaşıyor. (ARCHITECTURE §9
bu karara göre güncellendi.)

### Hotfix 1 — Snackbar kaybolmuyordu (`c72a2cc`)

material3'te `showSnackbar`'ın varsayılan süresi **`actionLabel` verilip
verilmediğine göre değişiyor**: etiket yoksa `Short`, varsa `Indefinite`.
"Geri al" butonu eklemek Snackbar'ı farkında olmadan süresiz yapmış.

İki mekanizma birlikte çalışıyordu: `Indefinite` bitmemesini, ViewModel'da
hayatta kalan `pendingUndo` state'i ise döndürmede geri gelmesini sağlıyordu.

Her iki `showSnackbar` çağrısına `duration` **açıkça** verildi.

**Döndürme kararı:** Snackbar kalsın ve sayaç sıfırlansın. Undo penceresi
kullanıcıya verilmiş bir fırsat; telefonu çevirmek ondan vazgeçme kararı değil.
Satır zaten veritabanından silinmiş durumda, `pendingUndo` yalnızca geri koymak
için gerekeni tutuyor.

**Bilinen sınır:** aynı anda **tek** undo izleniyor. Ardışık silmede yalnızca
son işlem geri alınabilir.

### Hotfix 2 — Fiyat üst sınırı (`aac3bd3`)

`999999999` kabul ediliyordu. Eski eşik `Long` taşmasına göre yazılmıştı; taşma
~92 katrilyon kuruşta olduğu için **pratikte hiç tetiklenmiyordu.**
`MAX_PRICE = 1000000` — bir ürün sınırı, en pahalı gerçek aboneliğin binlerce
katı ama kayan bir tuş vuruşunu yakalayacak kadar düşük.

Ayrıca **ikiden fazla ondalık artık sessizce yuvarlanmıyor**, reddediliyor.
`159,999` eskiden sessizce `160,00` oluyordu; para değerini kullanıcıya sormadan
değiştirmek bu kod tabanının `BigDecimal` duruşuyla çelişiyordu. Sondaki sıfırlar
sayılmıyor: `159,990` kabul, `159,999` red.

### Açılış süresi incelemesi — düzeltme yapılmadı

Faz 6 sonrası uygulamanın geç açıldığı bildirildi. **Ölçüldü**
(OPPO CPH2179, Android 10, `adb shell am start -W -S`, 5 tekrar):

| Build | Medyan cold start |
|---|---|
| Debug | **~8100 ms** |
| Release | **~856 ms** |

**Fark 9,5 kat.** Logcat kesin yeri gösterdi:

> `E ANR_LOG : Blocked msg = { what=110 obj=AppBindData{com.elinacn.subtrack} }, cost = 5709 ms`

`what=110` = `BIND_APPLICATION` — APK açma, sınıf yükleyici, dex doğrulama.
8 saniyenin **5,7'si bizim kodumuz çalışmadan önce** geçiyor. Debug build'in
`debuggable` bayrağı ART'ın optimizasyonunu engelliyor (`Late-enabling
-Xcheck:jni`).

**Şüphelerin hepsi çürütüldü:** sheet kapalıyken kompozisyona girmiyor,
`LaunchedEffect`'ler ilk satırda `null` kontrolüyle çıkıyor, Room ilk açılışta
yük getirmiyor (veri temizken 9236 ms, doluyken 8027 ms — fark gürültü içinde),
Hilt ve `Theme.kt` `BIND_APPLICATION`'dan sonra geliyor.

Faz 6 yavaşlamayı **tetikledi ama sebebi değil**: kod arttıkça debug dex
doğrulaması doğrusal büyüyor. Aynı kod release'de 856 ms'de açılıyor.

**Değişen dosyalar**
- `app/src/main/java/com/elinacn/subtrack/ui/common/UiText.kt` (yeni)
- `app/src/main/java/com/elinacn/subtrack/ui/home/HomeUiState.kt` — hata alanları, yeni event'ler
- `app/src/main/java/com/elinacn/subtrack/ui/home/HomeViewModel.kt` — doğrulama, undo, `try/catch`
- `app/src/main/java/com/elinacn/subtrack/ui/home/HomeScreen.kt` — Snackbar altyapısı
- `app/src/main/java/com/elinacn/subtrack/ui/home/components/AddSubscriptionSheet.kt` — `isError` / `supportingText`
- `app/src/main/res/values/strings.xml`, `values-en/strings.xml` — doğrulama ve undo metinleri
- `docs/ARCHITECTURE.md` §9

**Commit'ler**
- `368555f` feat: add UiText wrapper for view model messages
- `3f3dd34` feat: add input validation with inline field errors
- `224de58` feat: add undo for subscription deletion
- `7981262` docs: record error handling decision in architecture
- `c72a2cc` fix: give the undo snackbar a duration so it dismisses itself
- `aac3bd3` fix: cap the price at a product limit and reject extra decimals

**Tag**
- `phase-6-done`

**Elle test sonucu**
- Doğrulama, undo, hata gösterimi, İngilizce çeviriler ve fiyat sınırları —
  hepsi geçti.
- `docs/TESTING.md`'deki 16 maddelik sabit regresyon listesi geçti.
- **Not:** test cihazının varsayılanı **koyu tema**, testler ağırlıklı orada
  yapılıyor. Açık tema Faz 8'de ayrıca gözden geçirilmeli.

**Sonraki faz için not**
- Faz 7: test altyapısı. `HomeViewModel`'ın doğrulama mantığı artık saf ve
  bağımlılıksız test edilebilir durumda — `parsePrice` kuralları ilk yazılacak
  testler.
- Faz 16'ya iki performans maddesi eklendi (`material-icons-extended`, R8).

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
