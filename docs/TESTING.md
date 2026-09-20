# TESTING.md — Elle Test Listesi

Her faz sonunda **önce** aşağıdaki sabit liste çalıştırılır, **sonra** Claude
Code'un o faz için söylediği ek adımlar.

Her maddenin yanındaki faz, o davranışın hangi fazda kazanıldığını gösterir.
Bir madde bozulursa önce oraya bakılır — hangi kodun bu davranışı ürettiği
oradan bulunur.

---

## Sabit Regresyon Listesi

**Önce otomatik testler:**

```bash
./gradlew :app:testDebugUnitTest
```

Geçmeden elle teste başlanmaz — kırmızı bir birim testi varken cihazda gözlem
yapmak zaman kaybıdır.

**Yeni API kullanan değişikliklerden sonra lint şart**

```bash
./gradlew :app:lintDebug
```

`assembleDebug` yeni API kullanımını **yakalamaz**: `compileSdk` sınıf yolunda
her şey vardır, derleme sessizce geçer. Sorun ancak `minSdk`'ye yakın bir
cihazda çalışma anında `NoClassDefFoundError` olarak çıkar — yani test
edilmeyen bir API aralığında, yani muhtemelen kullanıcıda.

Faz 10a'da tam olarak bu oldu: `java.time.LocalDate.now()` `assembleDebug`'dan
geçti, `lintDebug` ise `Call requires API level 26, or core library
desugaring (current min is 24) [NewApi]` diye hata verip derlemeyi durdurdu.

Kural: `java.time`, yeni Compose/AndroidX API'si veya platform çağrısı ekleyen
her değişiklikten sonra lint koşturulur. Yalnızca birim testi ve
`assembleDebug` yeterli değildir.

Sonra aşağıdaki liste, sırayla ve tek oturumda çalıştırılır. Bir madde kalırsa sonrakilere devam
etmeden bildirin; sonraki maddeler zaten bozuk bir durumun üstüne binebilir.

| # | Test | Beklenen | Kazanıldığı faz |
|---|---|---|---|
| 1 | Uygulamayı aç | Açılıyor, çökmüyor | 0 · Hilt grafı 4 |
| 2 | FAB'a bas | Sheet açılıyor | 0 |
| 3 | Ad + fiyat gir, Kaydet | Sheet kapanıyor, öğe listeye giriyor | 0 · repository üzerinden 5a |
| 4 | FAB'a tekrar bas | Alanlar **boş** geliyor | 0 |
| 5 | Sheet'i scrim'e dokunarak / geri tuşuyla kapat | Kapanıyor | 0 |
| 6 | Bir kartı hafifçe kaydır (~1/5) bırak | **Silmiyor**, temiz geri yaslanıyor | Hotfix serisi |
| 7 | Aynı satırda 8-10 kez ardışık hafif kaydır | Hiçbiri silmiyor, **birikme yok** | Hotfix serisi |
| 8 | Hızlı kısa fiske | **Silmiyor** (mesafe şartı) | Hotfix serisi |
| 9 | Tam kaydır | **Siliyor** | Hotfix serisi |
| 10 | Ters yöne sürükle | Kart **kıpırdamıyor** | Hotfix serisi |
| 11 | 159,99 ve 59,90 ekle | Toplam tam olarak **219,89**, kuruş hatası yok | 5a |
| 12 | Uygulamayı tamamen kapat, yeniden aç | Liste **duruyor** (Room zinciri) | 5a |
| 13 | Ekran döndür | Liste duruyor, **titremiyor** | 5a |
| 14 | Sheet açık ve metin yazılıyken ekran döndür | Sheet **açık kalıyor**, metin duruyor | 5b |
| 15 | Sistem temasını koyuya al | Tüm metinler okunabilir, kartlar arka plandan ayrışıyor (14a'dan sonra **1,50:1**, önceki 1,29:1 değil). Vurgu rengi **altın**, açık temadaki zümrüt değil; hiçbir yerde mor kalmamış olmalı | 1b · kontrast 1c · palet 14a |
| 16 | Cihaz dilini İngilizceye al | Metinler çevrilmiş geliyor | 1b |
| 17 | Boş ad veya geçersiz fiyatla Kaydet'e bas | Sheet **açık kalıyor**, hata ilgili alanın **altında** | 6 |
| 18 | Bir satırı sil, Snackbar'a dokunma | Birkaç saniyede **kendiliğinden** kayboluyor | 6 |
| 19 | Sil, sonra "Geri al"a bas | Öğe **eski sırasına** dönüyor | 6 |
| 20 | Ayarlar ikonuna bas, geri oku ve sistem geri tuşuyla dön | Ayarlar açılıyor, iki yol da ana ekrana dönüyor, geri yığınında **birikme yok** | 9b |
| 21 | Ayarlarda başka bir para birimi seç, uygulamayı tamamen kapat, yeniden aç | Seçim **duruyor**, toplam o para biriminde | 9b |
| 22 | Ayarlar → Döviz Kurları, USD kurunu değiştir, kaydet, ana ekrana dön | Toplam **yeni kurla** hesaplanıyor | 9b-2 |
| 23 | Kur ekranında `0`, `-5`, `1,23456`, `1000,0001` gir ve kaydet | Dördü de **alan altında** hata veriyor, hiçbiri kaydedilmiyor, **çökme yok** | 9b-2 |
| 24 | Kur değiştir, uygulamayı tamamen kapat, yeniden aç | Kur **duruyor**, "Son düzenleme" tarihi görünüyor | 9b-2 |
| 25 | Kur ekranında "Varsayılana dön" → Sıfırla | Alanlar 42,85 / 46,2 / 53,9'a dönüyor, tarih yerine **"hiç düzenlenmedi"** yazısı geliyor | 9b-2 |
| 26 | Kur alanına yazarken klavye açıkken Kaydet ve "Varsayılana dön"e ulaş | **Tek fiskede** ikisine de ulaşılıyor. API 30+ bunu `imePadding()` ile yapar, altında pencere küçülerek | 9b-2 · 16a edge-to-edge |
| 27 | Ekleme formunda tarih seç, kaydet | Kartta doğru gün sayısı: cihaz tarihi ile seçilen tarih arasındaki **takvim günü** farkı | 10a |
| 28 | Tarih **seçmeden** kaydet | Kayıt oluşuyor, kartta gösterge **yok**, yer tutucu da yok, çökme yok | 10a |
| 29 | Geçmiş bir tarih seç | Kart **bir sonraki ödeme tarihine** göre gün sayısı gösteriyor, "gecikmiş" **demiyor**, çıpa değişmiyor | 10a · 12-2 hotfix |
| 30 | Bugünün tarihini seç | Kart "Bugün ödenecek" diyor | 10a |
| 31 | Seçicinin metin girişinden 10 yıldan uzak bir tarih gir, kaydet | Alan altında hata, **kaydedilmiyor**, sheet açık kalıyor, çökme yok | 10a |
| 32 | Tarih seçili haldeyken sheet açıkken döndür | Tarih **korunuyor** | 10a |

| 33 | Ayarlarda "Ödeme hatırlatmaları" satırı | Üç halden birini söylüyor: **Açık** / **Kapalı — açmak için dokunun** / **Kapalı — sistem ayarlarından açılmalı** | 10c-1 |
| 34 | (API 33+) Temiz kurulumda satıra dokun | **Doğrudan** sistem izin diyaloğu; araya uygulamanın diyaloğu **girmiyor** | 10c-1 |
| 35 | İzni verip satıra bak | **Açık** | 10c-1 |
| 36 | Bir kez reddettikten sonra satıra dokun | Önce **uygulamanın açıklama diyaloğu**, sonra sistem diyaloğu | 10c-1 |
| 37 | Kalıcı reddedildikten sonra satıra dokun | **Sistem bildirim ayarları** açılıyor, izin diyaloğu çıkmıyor | 10c-1 |
| 38 | Sistem ayarlarından bildirimleri aç, geri dön | Satır **uygulama yeniden başlatılmadan** güncelleniyor | 10c-1 |
| 39 | Sistem ayarlarından **yalnızca kanalı** kapat, geri dön | Satır **kapalı** diyor (uygulama izni hâlâ verili olsa bile) | 10c-1 |
| 40 | (API < 33) Satıra dokun | Bir ayar ekranı açılıyor, izin diyaloğu **hiç** çıkmıyor. **Hangi ekran sürüme bağlı:** API 26+ uygulamanın bildirim ekranı, **API 24-25 uygulama detay sayfası** ("App info"), oradan "Notifications" bir dokunuş uzakta | 10c-1 · 16b hotfix |

| 41 | Temiz kurulumda **tarihli** ilk aboneliği kaydet | Sheet kapandıktan **sonra** sistem izin diyaloğu çıkıyor; ikisi üst üste binmiyor | 10c-2 |
| 42 | Temiz kurulumda **tarihsiz** abonelik kaydet | Diyalog **çıkmıyor** | 10c-2 |
| 43 | Reddettikten sonra ikinci tarihli aboneliği kaydet | Diyalog **çıkmıyor** (tek sefer kuralı) | 10c-2 |
| 44 | İzin verilmişken tarihli abonelik kaydet | Diyalog **çıkmıyor** | 10c-2 |
| 45 | Diyalog açıkken ekranı döndür | Diyalog **tek** kalıyor, kapatılınca yeniden çıkmıyor | 10c-2 |
| 46 | (API < 33) Tarihli ilk aboneliği kaydet | İzin diyaloğu **hiç** çıkmıyor, uygulama normal | 10c-2 |

| 47 | Temiz kurulumda uygulamayı aç | Listede **boş durum** görünüyor: ikon + "Henüz abonelik yok" + "Eklemek için + düğmesine dokun". Dashboard kartı (0,00) ve FAB yerinde | 8b |
| 48 | Bir abonelik ekle | Boş durum **kayboluyor**, liste geliyor | 8b |
| 49 | Tek aboneliği sil | Boş durum **geri geliyor**, undo Snackbar'ı ile çakışmıyor | 8b |
| 50 | Veri varken uygulamayı aç | Boş durum **hiç görünmüyor** (yükleme sırasında da) | 8b |

| 51 | Ekleme formunda kategori seçici | Dört chip: Eğlence / Üretkenlik / Sağlık / Diğer. **Diğer** seçili gelir (ağaçta `checked="true"`) | 11a |
| 52 | Kategori seçip kaydet | Kartta kategori adı görünüyor; satır tek odak durağı olarak "Ad, tutar, (geri sayım,) kategori" diye okunuyor | 11a |
| 53 | Kategoriye dokunmadan kaydet | Kayıt oluşuyor, kartta kategori satırı **yok** (Diğer yazılmaz) | 11a |
| 54 | Kaydettikten sonra FAB'a tekrar bas | Kategori **Diğer**'e dönmüş | 11a |
| 55 | Kategori seçili haldeyken sheet açıkken döndür | Seçim **korunuyor** | 11a |

| 56 | Liste üstündeki filtre çubuğu | "Tümü" + dört kategori, beş chip; açılışta **Tümü** seçili | 11b |
| 57 | Bir kategori seç | Liste yalnızca o kategoriyi gösteriyor, **dashboard toplamı da** görünen satırların toplamı | 11b |
| 58 | Hiçbir aboneliği olmayan bir kategoriyi seç | "Bu kategoride abonelik yok / Başka bir kategori seç" — 8b'deki ilk boş durumdan **farklı metin** | 11b |
| 59 | Filtre açıkken bir satırı sil, sonra "Geri al" | Satır dönüyor, filtre **korunuyor**, "Tümü"de sıra da eski hâlinde | 11b |
| 60 | Filtre seçiliyken döndür, sonra uygulamayı tamamen kapat ve aç | Döndürmede **korunuyor**, yeniden açılışta **Tümü**'ye dönüyor (filtre kalıcı değil) | 11b |
| 61 | Filtre çubuğunu yatay kaydır | Ekrandan taşan chip'e ulaşılıyor; kaydırma ekranın **en sağ kenarından başlatılmaz** (aşağıdaki API 34 tuzağı) | 11b |

| 62 | Ekleme formunda periyot seçici | Üç chip: Aylık / Yıllık / Haftalık. **Aylık** seçili gelir; 360dp'de **tek satıra sığar** | 12-1 |
| 63 | Aylık 100,00 + yıllık 1.200,00 + haftalık 10,00 kaydet | Kartlarda periyot yazıyor; toplam **243,33** (100,00 + 100,00 + 43,33) | 12-1 |
| 64 | Dashboard'ın altındaki **Yıllık** chip'ine bas | Başlık "Yıllık Toplam", değer **2.920,00** — aylık figürün 12 katı (2.919,96) **değil**; fark tek yuvarlamadan | 12-1 |
| 65 | Bir kategori filtresi seçip iki görünüme de bak | Yıllık toplam yalnızca **görünen** satırları kapsıyor | 12-1 |
| 66 | Periyot seçip kaydettikten sonra FAB'a tekrar bas | Periyot **Aylık**'a dönmüş | 12-1 |
| 67 | Periyot seçili haldeyken sheet açıkken döndür | Seçim **korunuyor** | 12-1 |
| 68 | Yıllık görünümdeyken uygulamayı tamamen kapat ve aç | **Aylık**'a dönüyor (görünüm kalıcı değil) | 12-1 |

| 69 | Geçmiş tarihli **aylık** abonelik ekle | Kart, çıpanın bir sonraki aya taşınmış hâline sayıyor: çıpa 5 gün önceyse "25 gün kaldı" gibi | 12-2 |
| 70 | Geçmiş tarihli **haftalık** ve **yıllık** abonelik ekle | Aynısı kendi periyoduyla: haftalıkta en fazla 6 gün, yıllıkta bir yıl içinde | 12-2 |
| 71 | **Çok eski** tarih (2+ yıl önce, haftalık) | Doğru gün sayısı, donma yok — hesap adım adım değil, tek aritmetik | 12-2 |
| 72 | **31 Ocak** çıpalı aylık abonelik, Mart'ta bak | Mart **31**'ini gösteriyor, 28'ini değil (ilerletme çıpadan sayılıyor) | 12-2 |
| 73 | Gelecek tarihli abonelik | İlerletme **yok**, tarih olduğu gibi | 12-2 |
| 74 | Bugünün tarihi | "Bugün ödenecek" **korunuyor** | 12-2 |
| 75 | Geçmiş tarihli bir aboneliği kaydettikten sonra veriyi oku | Saklanan tarih **değişmemiş** — ilerletme yalnızca ekranda (`run-as` ile `subtrack.db`) | 12-2 |

| 76 | Ana ekran üst çubuğundaki grafik ikonuna bas | İstatistik ekranı açılıyor; ikon ayarların **solunda** | 13a |
| 77 | Geri oku, sonra sistem geri tuşu | İkisi de ana ekrana dönüyor; bir kez daha geri → uygulamadan çıkıyor, yığında **birikme yok** | 13a |
| 78 | Dört kategoriye yayılmış, karışık para birimli ve periyotlu abonelikler kur | Her kategorinin tutarı elle hesapla **birebir** aynı; figürler aylık | 13a |
| 79 | Ekrandaki yüzdeleri topla | Tam **100** — her pay tek başına yuvarlansaydı 99 veya 101 olurdu | 13a |
| 80 | En pahalı listesi | **Aylık maliyete** göre sıralı, en fazla beş satır; her satırda periyot yazıyor (yıllık abonelik onikide biriyle görünür) | 13a |
| 81 | Ana ekranda kategori filtresi açıkken istatistiğe gir | **Tüm** abonelikler görünüyor; filtreli ana ekran toplamı ile istatistik toplamı farklı olabilir ve bu doğrudur | 13a |
| 82 | Hiç abonelik yokken istatistiğe gir | Boş durum: "Henüz istatistik yok / Bir abonelik ekleyince dağılım burada çıkar" | 13a |
| 83 | Tutarı sıfır olan kategori | Satır **hiç yok** — sıfırlık çubuk çizilmiyor | 13a |
| 84 | TalkBack ile dağılım satırı | **Tek odak durağı**: "Sağlık, 2.002,00 TL, yüzde 75". Canvas ağaçta yok, telafi edilmiş olmalı | 13a |
| 85 | Koyu tema (API 34) | Çubukların dolu kısmı iziyle **ayrı renkte**; %75 ile %2 bakışta ayrılıyor. 14a'dan sonra iz saydam bir çubuk değil, kendi rolü (`outlineVariant`): çubuk `#D4AF37`, iz `#3A5A48`, oran 3,65:1 | 13a · 14a |
| 86 | Tabloda tek ay varken istatistiğe gir | "Aylık Trend" başlığı var, **grafik yok**; yerine "Trend için en az iki ay gerekiyor…" cümlesi. Karşılaştırma da yok | 13b |
| 87 | İki ay kayıtlıyken | Grafik çiziliyor; her sütunun altında kendi ay kısaltması, üstünde bir kez "en yüksek …" | 13b |
| 88 | Altıdan çok ay kayıtlıyken | Yalnızca **son altı ay**; daha eskisi çizilmiyor | 13b |
| 89 | Aralarda **kaydı olmayan** bir ay | O ayın yuvası duruyor, etiketi yazılı, **hiçbir şey çizilmiyor**; sesli okunuşta "… kayıt yok" | 13b |
| 90 | **Sıfır kaydedilmiş** bir ay | Yalnızca **iz** çiziliyor (dolu kısım yok); sesli okunuşta "0,00 TL" — 89'dan farklı | 13b |
| 91 | Bu ay geçen aydan farklı | Grafiğin üstünde ok + cümle: "Geçen aya göre 150,00 TL arttı / azaldı". Yön **sözcükte**, renk ikisinde de aynı | 13b |
| 92 | Bu ay geçen ayla aynı | "Geçen aya göre değişmedi"; **ok yok** | 13b |
| 93 | Geçen ayın kaydı yok | Karşılaştırma **hiç** gösterilmiyor — yer tutucu da, tire de yok | 13b |
| 94 | Ana para birimini değiştir, istatistiğe gir | Grafik yalnızca **yeni** birimdeki ayları çiziyor; altında "Başka para biriminde kaydedilen N ay gösterilmiyor". Eski aylar **çevrilmiyor** | 13b |
| 95 | TalkBack ile grafik | **Tek odak durağı**, bütün aylar tek cümlede: "Aylık trend: Nisan …, Mayıs …, Haziran kayıt yok, …" | 13b |
| 96 | Bir karta dokun | **Düzenleme ekranı** açılıyor; alanlar kayıtlı değerlerle dolu geliyor (ad, fiyat, para birimi, periyot, kategori seçili) | 15 |
| 97 | Geçmiş çıpalı bir aboneliğin kartına dokun | Tarih alanı **çıpayı** gösteriyor — kart "25 gün kaldı" derken ekran kullanıcının girdiği günü açıyor | 15 |
| 98 | Bir alanı değiştir, Kaydet | Ana ekrana dönüyor, satır **yeni değeri** gösteriyor, toplam güncelleniyor, satır **yerinden oynamıyor** (sıralama `createdAt`'e göre) | 15 |
| 99 | Hiçbir şey değiştirmeden geri dön | Hiçbir şey değişmiyor; saklanan satır aynı | 15 |
| 100 | Bir alanı değiştirip geri dön | Değişiklik **sessizce atılıyor**, onay sorulmuyor, hiçbir şey yazılmıyor | 15 |
| 101 | Düzenleme ekranında ekranı döndür | Yazılanlar ve seçilen tarih **korunuyor** | 15 |
| 102 | Boş ad / geçersiz fiyat ile Kaydet | Ekran **açık kalıyor**, hata ilgili alanın altında — ekleme sheet'iyle **aynı** metinler (tek kural kaynağı) | 15 |
| 103 | Bir satırı **hafifçe** kaydır | Ne siliyor ne de düzenleme ekranını açıyor — kaydırma dokunma sayılmıyor | 15 |
| 104 | TalkBack ile bir satır | Hâlâ **tek odak durağı**; dokunma eylemi (Düzenle) ve "Sil" özel eylemi birlikte duruyor | 15 |
| 105 | Düzenleme sonrası `monthly_snapshots` | Tek satır, toplamı **yeni** değer — kaydedici güncellemeyi de görüyor | 15 |

| 106 | Ayarlar → Tema | Üç seçenekli diyalog: **Sistemi takip et** / Açık / Koyu. Satır seçili olanı yazıyor, satır tek odak durağı | 14b |
| 107 | **Açık**'ı seç, sistem temasını koyuya al | Uygulama **direniyor** — açık kalıyor (`#D3E2D8`) | 14b |
| 108 | **Koyu**'yu seç, sistem temasını açığa al | Uygulama **direniyor** — koyu kalıyor (`#0D1A14`) | 14b |
| 109 | **Sistemi takip et**'i seç, sistem temasını değiştir | Uygulama **takip ediyor**, uygulama yeniden başlatılmadan | 14b |
| 110 | Tema seç, uygulamayı tamamen kapat, yeniden aç | Seçim **duruyor**; açılışta **yanlış temada tek kare bile yok** — beyaz açılış penceresinden doğrudan seçilen temaya | 14b |
| 111 | (API 31+) Ayarlar → Duvar kâğıdı renkleri | Anahtar **kapalı** geliyor; açınca palet duvar kâğıdından geliyor | 14b |
| 112 | Duvar kâğıdı renkleri açıkken koyu temayı zorla | İkisi **birbirinden bağımsız**: renkler duvar kâğıdından, aydınlık/karanlık seçimden | 14b |
| 113 | Duvar kâğıdı renkleri açıkken istatistik | Çubuk ile izi **ayrı renkte** (ölçüm 3,7:1 civarı); trend sütunu ile izi de öyle; Snackbar "Geri al" okunuyor | 14b |
| 114 | (API < 31) Duvar kâğıdı renkleri satırı | **Görünüyor ama devre dışı**; alt satır "Android 12 ve üzeri gerekir" diyor. Dokunmak hiçbir şey yazmıyor | 14b |
| 115 | Dashboard, kart, istatistik, trend, karşılaştırma ve kur ekranı | **Hiçbirinde ISO kodu yok** — hepsi ₺ $ € £. Bildirim zaten tutar taşımıyor | 14b |
| 116 | Cihaz dilini İngilizceye al | Hâlâ sembol; sayı biçimi locale'e göre değişiyor ("₺3,060.43" ↔ "3.060,43 ₺") | 14b |
| 117 | (API 29) ₺ karakteri | **Çiziliyor**, tofu kutusu değil — dashboard'daki en büyük punto dahil | 14b |

**96-105 için not:** düzenleme maddeleri ekleme sheet'iyle **aynı** bileşenlerden
kurulu bir formu sınıyor. #102 bilerek ikisini karşılaştırıyor: mesajlar
ayrışırsa tek doğrulama kaynağı kuralı kırılmış demektir (`ARCHITECTURE.md` §22).
#103 ve #104 bu fazın en riskli maddeleri — dokunma, kaydırarak silmenin jestine
eklendi ve satırın tek odak durağı olması korunmalı.

---

## Hangi madde hangi cihazda ölçülemiyor

Faz 16b'de dört cihazda tam tur atıldı ve **bir daha keşfedilmesin diye** buraya
yazıldı. İki ayrı şey var ve karıştırılmamalı:

- **Geçerli değil:** madde o API'de anlamsız. Boş bırakılmaz, "geçerli değil"
  yazılır — atlanmadı, orada yok.
- **Ölçülemedi:** madde geçerli ama o imajda gözlemlenemiyor. Sebebi yazılır.

| Madde | API 24 | API 29 | API 34 | API 36 | Sebep |
|---|---|---|---|---|---|
| #34–#37, #41–#45 | geçerli değil | geçerli değil | ölçülür | ölçülür | Çalışma zamanı bildirim izni API 33+. API 24/29'da bu yolu #46 ve #40 kapsıyor |
| #40, #46 | ölçülür | ölçülür | geçerli değil | geçerli değil | Karşı yön: API 33+ cihazda izin diyaloğu çıkar, bu iki madde API < 33 içindir. **#40 API 24'te 16b'de düşmüştü, 16b hotfix'inde düzeltildi** — açılan ekran orada uygulama detay sayfasıdır, bildirim ekranı değil |
| #39 | geçerli değil | ölçülür | ölçülür | ölçülür | Bildirim kanalları API 26+; Android 7.0'da kanal kavramı yok |
| #107, #109 | **ölçülemedi** | **ölçülemedi** | ölçülür | ölçülür | Sistem koyu teması: API 24'te `cmd uimode` "No shell command implementation" der; API 29'da komut çalışır ama "Night mode: no" döndürüp değeri yazmaz, `settings put secure ui_night_mode 2` de tutmaz |
| #111, #112, #113 | geçerli değil | geçerli değil | ölçülür | ölçülür | Duvar kâğıdı renkleri API 31+ |
| #114 | ölçülür | ölçülür | geçerli değil | geçerli değil | Karşı yön: satırın devre dışı hâli yalnızca API < 31'de görülür |
| #84, #95, #104 | kısmen | kısmen | kısmen | kısmen | Erişilebilirlik **ağacı** okunabiliyor ve maddelerin "tek odak durağı" yarısı böyle ölçülüyor. TalkBack hiçbir imajda kurulu değil; #104'ün "Sil" özel eylemi `uiautomator dump` biçiminde hiç taşınmıyor |
| #50 | kısmen | kısmen | kısmen | kısmen | "Veri varken boş durum **yükleme sırasında da** görünmüyor": kararlı hâl ölçülüyor, açılış karesi ölçülemiyor — en hızlı gözlem aracı 3,3 sn süren `uiautomator dump` |
| #15 | ölçülür ama sistemden değil | ölçülür ama sistemden değil | ölçülür | ölçülür | API 24/29'da sistem teması koyuya alınamadığı için renkler uygulamanın kendi **Koyu** seçeneğiyle ölçülür; ölçülen değer aynı (`#0D1A14` / `#1F3D2D` = 1,50:1) |
| #16 | ölçülür (Diller ekranı) | ölçülür (Diller ekranı) | ölçülür (`cmd locale`) | ölçülür (`cmd locale`) | Aşağıdaki "Cihaz dili" bölümü |
| #69–#75 | ölçülür | ölçülür | ölçülür | ölçülür | **Artık ölçülemez değil** — aşağıdaki "Çıpa tarihi metinle girilir" bölümü |

**#39 ve #69–#75 bu turda "ölçülemez" olmaktan çıktı.** İkisi de yıllarca öyle
yazılmıştı; 16b'de ikisinin de yolu bulundu ve aşağıya yazıldı. Bir maddenin
"ölçülemez" kalması, kimsenin bir daha bakmayacağı anlamına gelmesin.

**86-95 için veri nasıl kurulur — ay dönümü cihazda üretilemiyor**

12a'nın bulgusu burada da geçerli: `google_apis_playstore` imajlarında root
yok, `adb shell date` "Operation not permitted" diyor ve saat host'tan geliyor.
**Yani ay dönümü cihazda bekletilerek veya zorlanarak üretilemez.** Çok aylık
veri, uygulama durdurulup veritabanı `run-as` ile dışarı alınarak, host'ta
düzenlenip geri konarak kurulur:

```bash
adb shell am force-stop com.elinacn.subtrack
adb exec-out run-as com.elinacn.subtrack cat databases/subtrack.db > subtrack.db
# host'ta: monthly_snapshots tablosuna (period, totalInCents, currencyCode, recordedAt)
# satırları eklenir; period = yıl*100 + ay (202608). WAL varsa önce katlanır.
adb push subtrack.db /data/local/tmp/fixture.db
adb shell "cat /data/local/tmp/fixture.db | run-as com.elinacn.subtrack sh -c 'cat > databases/subtrack.db'"
adb shell "run-as com.elinacn.subtrack sh -c 'rm -f databases/subtrack.db-wal databases/subtrack.db-shm'"
```

Cihazda `sqlite3` **yok** (iki imajda da), bu yüzden düzenleme host'ta yapılır.
Uygulama **durdurulmuş** olmalı: Room dışarıdan yapılan yazıyı fark etmez.
Bu bir **test fikstürüdür**; üretim kodunda buna açılmış bir kanca yoktur.

**İçinde bulunulan ayı fikstür belirlemez.** Kaydedici uygulama açılır açılmaz
o ayı kendi hesabıyla yazar (§19); fikstür yalnızca **geçmiş** ayları kurar.
Aynı sebeple **"hiç satır yok" hâli çalışırken görülemez** — ekranda görülebilen
en boş hâl "tek ay"dır.

**Klavye açıkken buton erişilebilirliği — her fazda kontrol edilecek**

Metin alanı olan **her** ekranda, klavye açıkken ekranın alt kısmındaki
eylemlerin erişilebilir olup olmadığı ölçülür. Faz 16a'da tablo üç ekrana ve üç
cihaza çıktı; **Faz 16b'de API 24 eklendi ve bütün satırlar yeniden ölçüldü.**

| Ekran | Cihaz | Klavye kapalı | Klavye açık, tek fiske sonrası | Klavye üstü |
|---|---|---|---|---|
| Ekleme sheet'i | API 24 | `[316,1036][404,1076]` | ölçülmedi (pencere 1184 px) | — |
| Ekleme sheet'i | API 29 | `[316,1132][404,1172]` | `[316,630][404,670]` (fiskesiz) | 784 |
| Ekleme sheet'i | API 34 | `[483,2143][597,2196]` | `[483,1323][597,1376]` (fiskesiz) | 1517 |
| Ekleme sheet'i | API 36 | `[484,2143][597,2196]` | `[484,1323][597,1376]` (fiskesiz) | 1517 |
| Kur ekranı — Kaydet | API 24 | `[316,1044][404,1084]` | `[316,521][404,561]` | 658 |
| Kur ekranı — Kaydet | API 29 | `[316,1044][404,1084]` | `[316,631][404,671]` | 778 |
| Kur ekranı — Kaydet | API 34 | `[483,1344][597,1397]` | `[483,1228][597,1281]` | 1517 |
| Kur ekranı — Kaydet | API 36 | `[484,1344][597,1397]` | `[484,1228][597,1281]` | 1517 |
| Kur ekranı — Varsayılana dön | API 24 | `[260,1164][461,1184]` | `[260,641][461,658]` | 658 |
| Kur ekranı — Varsayılana dön | API 29 | `[260,1164][461,1204]` | `[260,751][461,778]` | 778 |
| Kur ekranı — Varsayılana dön | API 34 | `[410,1502][670,1555]` | `[410,1386][670,1439]` | 1517 |
| Kur ekranı — Varsayılana dön | API 36 | `[410,1502][670,1555]` | `[410,1386][670,1439]` | 1517 |
| Düzenleme ekranı — Kaydet | API 24 | kaydırma gerekiyor (360dp) | ölçülmedi | — |
| Düzenleme ekranı — Kaydet | API 29 | kaydırma gerekiyor (360dp) | `[316,630][404,670]` | 784 |
| Düzenleme ekranı — Kaydet | API 34 | `[483,1660][597,1713]` | `[483,1323][597,1376]` | 1517 |
| Düzenleme ekranı — Kaydet | API 36 | `[483,1660][597,1713]` | `[483,1323][597,1376]` | 1517 |
| Ayarlar ekranı | dördü de | — | — | — |
| İstatistik ekranı | dördü de | — | — | — |

**16a tablosunun kur ekranı satırları 95 px yanlıştı ve sebebi bulundu.** Orada
API 34/36 için klavye **kapalı** değer `[500,1439][580,1492]` yazıyordu; 16b'de
aynı cihazda `[483,1344][597,1397]` ölçüldü. Fark 95 px ve bu, o cihazlardaki
durum çubuğu payının ta kendisi: o iki satır **16a öncesinden**, yani pencere
hâlâ durum çubuğunun altından başlarken kalmış. Klavye **açık** değerler aynı
tabloda 16a'da yeniden ölçülmüştü ve 16b ölçümüyle piksel piksel tuttu — düzeltme
yalnızca kapalı sütununa ait. Ders: edge-to-edge gibi pencerenin başlangıcını
kaydıran bir değişiklikten sonra tablonun **her** sütunu yeniden ölçülür.

x değerleri metin düğümünün genişliğidir ve yazı tipi ölçüsüyle birkaç piksel
oynar; **maddenin ölçütü y'dir** — düğmenin alt kenarı "klavye üstü" değerinin
altında kalıyorsa madde düşer.

**API 24'te klavye penceresi farklı davranıyor.** Orada uygulama penceresi zaten
`[0,0][720,1184]` (gezinme çubuğu ayrı ve opak, edge-to-edge yok); klavye açılınca
kaydırma düğümü `[0,176][720,658]`e daralıyor. Yani klavyenin üst kenarı 658, ve
"Varsayılana dön" fiskeden sonra tam o sınırda duruyor (`[260,641][461,658]`) —
erişilebilir ama payı yok. Yeni bir alt eylem eklenirse önce burası ölçülmeli.

`fs 2.0`'da da ölçüldü: API 36 kur ekranında tek fiskede Kaydet `[465,1043]
[615,1141]`, Varsayılana dön `[277,1215][803,1305]`; API 29'da iki fiske
gerekiyor, Kaydet `[303,524][417,599]`, Varsayılana dön `[163,655][557,730]`.
İkisinde de klavyenin üstünde.

Ölçüm `show_ime_with_hard_keyboard 1` ile yapılır (aşağıdaki bölüm), yoksa
emülatörde klavye hiç çizilmez ve test sessizce yanlış sonuç verir.

**Klavyenin üst kenarı artık kaydırma düğümünden okunmaz.** Edge-to-edge'den
sonra API 30+ cihazlarda pencere küçülmüyor, `android:id/content` klavye açıkken
de tam ekran okunuyor (`[0,0][1080,2400]`). Üst kenar `ekran yüksekliği -
ime.bottom` ile bulunur (API 34/36: 2400 - 883 = 1517) ya da ekran görüntüsünden
sayılır. API 29'da pencere hâlâ küçülüyor, orada eski yöntem geçerli.

**Sheet için ayrıca çift uygulama kontrolü:** `ModalBottomSheet` kendi
`imePadding()`'ini uyguluyor. Kontrol: klavye kapalıyken sheet koordinatları
önceki ölçümle aynı mı, klavye açıkken Kaydet'in alt kenarı ile sheet'in alt
kenarı arasındaki boşluk `SheetBottomPadding` (40dp) mu. 16a'da aynı cihaza
16a öncesi ve sonrası derleme sırayla kurulup ölçüldü — API 29'da dört
koordinatın dördü de aynı çıktı.

**Faz 16a'da ne değişti:** `enableEdgeToEdge()` geldi, `adjustResize` **kaldı**.
İkisi çakışmıyor çünkü platform API 30'dan itibaren `adjustResize`'ı yok
sayıyor; o sürümlerde işi `Modifier.imePadding()` yapıyor, altında bayrak
yapıyor. Bayrağın kaldırılması denendi ve API 29'u kırdı (kayıt
`ARCHITECTURE.md` §16'da). **Her yeni metin alanlı ekran bu tabloya üç cihazda
da eklenir.**

**Not — beklenen davranışlar, hata değil:**
- İlk kurulumda liste **boş** başlar. Seed veri yok; bu durumda **boş durum ekranı**
  görünür (Faz 8b), dashboard kartı ve FAB yerinde kalır.
- Ardışık silmede **yalnızca son işlem** geri alınabilir; tek undo izleniyor.

---

## Emülatör Testleri

Fiziksel cihaz (OPPO A15s, CPH2179, Android 10) iki şeyi ölçemiyor: ekranı
tek boyutta ve **423dp** genişlikte (720x1600 @ 272dpi override), ve TalkBack
açılınca donuyor. Dar ekran ve farklı Android sürümü testleri emülatörde
yapılır.

### AVD'ler

| AVD | Çözünürlük | Yoğunluk | Efektif genişlik | API | Ne için |
|---|---|---|---|---|---|
| `subtrack_min_api24` | 720x1280 | 320 dpi | **360dp** | 24 | **minSdk'nın kendisi.** Desugaring'li `java.time`, Room, DataStore, Compose ve WorkManager'ın taban sürümde koştuğunun kanıtı (Faz 16-0) |
| `subtrack_narrow_api29` | 720x1280 | 320 dpi | **360dp** | 29 | Dar ekran, sığma/sarma testleri. Test cihazıyla aynı Android sürümü. |
| `subtrack_wide_api34` | 1080x2400 | 420 dpi | 411dp | 34 | Güncel Android davranışları, koyu tema, dynamic color |
| `subtrack_edge_api36` | 1080x2400 | 420 dpi | 411dp | 36 | **Zorunlu edge-to-edge.** targetSdk 36 + Android 16; gezinme modu GESTURAL (Faz 16-0) |
| `subtrack_store_api34` | 1080x**1920** | 420 dpi | 411dp | 34 | **Yalnızca mağaza çekimi** (Faz 16e-2). Play en fazla 2:1 kabul ediyor; 1080x2400 = 2,22:1 reddedilir. Ölçüm turlarında kullanılmaz |

360dp keyfi değil: Compose bileşenlerinin sığıp sığmadığı bu eşiğe göre
hesaplanıyor, ve yaygın bütçe telefonlarının genişliği bu. Fiziksel cihaz
423dp olduğu için dar durumu hiç göstermiyor.

**Faz 16b'de dördü de sürüldü ve ölçülen özellikleri şunlar.** Bunlar imajların
kendi ayarları; bir tur başlamadan önce doğrulanır, çünkü her biri bir ölçümü
sessizce değiştirebiliyor.

| AVD | Gezinme | Saat dilimi | Uygulama penceresi | Ekleme sheet'inde periyot sırası |
|---|---|---|---|---|
| `subtrack_min_api24` | üç tuşlu | GMT | `[0,0][720,1184]` — **edge-to-edge yok**, çubuk ayrı ve opak | tek satır |
| `subtrack_narrow_api29` | üç tuşlu | America/New_York | `[0,0][720,1280]` | tek satır |
| `subtrack_wide_api34` | gestural | GMT | `[0,0][1080,2400]` | tek satır |
| `subtrack_edge_api36` | gestural | GMT | `[0,0][1080,2400]` | tek satır |

**Saat dilimi tarih maddelerini doğrudan etkiliyor.** `subtrack_narrow_api29`
America/New_York'ta; host'la arasında yedi saat var, yani gece yarısına yakın
saatlerde cihaz ile host **farklı günde** olur. Tarih fikstürü kurarken çıpa,
cihazın kendi saat diliminde gece yarısına yazılır — host'unkinde değil.
16b'de bir fikstür bu yüzden bir gün geriye düştü ve kart "25 gün" yerine
"24 gün" dedi; hata üründe değil ölçümdeydi.

**API 24'ün gezinme çubuğu opak ve pencerenin dışında.** `enableEdgeToEdge()`
orada pencereyi çubukların altına taşımıyor; FAB API 29'dakinden 96 px yukarıda
duruyor. Koordinat bekleyen hiçbir ölçüm API 29'unkiyle aynı sayıyı vermez.

Oluşturma (yalnızca bir kez gerekir):

```bash
avdmanager create avd -n subtrack_narrow_api29 -k "system-images;android-29;google_apis_playstore;x86_64" --abi x86_64
avdmanager create avd -n subtrack_wide_api34 -k "system-images;android-34;google_apis_playstore;x86_64" -d pixel_6 --abi x86_64

# Faz 16-0'da eklenenler
avdmanager create avd -n subtrack_edge_api36 -k "system-images;android-36.1;google_apis_playstore;x86_64" -d pixel_6
avdmanager create avd -n subtrack_min_api24  -k "system-images;android-24;google_apis;x86_64" -d "Nexus 5"
```

**API 24 imajı `google_apis`, `google_apis_playstore` değil.** O aralıkta Play
Store imajı yalnızca 32-bit `x86` olarak yayınlanmış; 64-bit olanı `google_apis`.
İndirme:

```bash
android sdk install "system-images/android-24/google_apis/x86_64"
```

`sdkmanager` artık bir kabuk üzerinden `android` CLI'ya yönleniyor ve
`"paket;adı"` biçimini noktalı virgülden bölüp "Package not found" diyor —
indirmede yukarıdaki eğik çizgili biçimi kullanın. `avdmanager` noktalı
virgülü doğru anlıyor, orada değişiklik gerekmiyor.

**API 36 gezinme modu GESTURAL olmalı** — edge-to-edge davranışı API'ye değil
gezinme moduna bağlı (13b bulgusu). İmajda varsayılan zaten gestural, doğrulamak
için:

```bash
adb shell cmd overlay list | grep navbar     # [x] ...navbar.gestural olmalı
adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural
```

**Çözünürlük seçimi kasıtlı:** `subtrack_edge_api36` `pixel_6` profiliyle
kuruluyor, yani `subtrack_wide_api34` ile **birebir aynı piksel ızgarası**
(1080x2400 @420). Böylece iki cihazın ekran görüntüleri piksel piksel
karşılaştırılabiliyor ve aradaki fark ekrana değil platforma yazılabiliyor.
`subtrack_min_api24` ise `subtrack_narrow_api29` ile aynı ızgaraya (720x1280
@320 = 360dp) elle ayarlanıyor; `Nexus 5` profili 1080x1920 @480 ile geliyor,
`config.ini`'de `hw.lcd.width/height/density` değiştiriliyor.

`avdmanager` dar profili varsayılan 320x640 @ 160dpi ile kurar; sonra
`~/.android/avd/subtrack_narrow_api29.avd/config.ini` içinde
`hw.lcd.width=720`, `hw.lcd.height=1280`, `hw.lcd.density=320` yapılır.
İki AVD'de de `hw.keyboard=yes` — donanım klavyesi olmadan `adb shell input
text` yazarken soft klavye açılıp düzeni kaydırıyor ve dokunma koordinatları
şaşıyor.

Başlatma (ikisi aynı anda çalışabilir, farklı port):

```bash
emulator -avd subtrack_narrow_api29 -no-snapshot-save -no-boot-anim -gpu swiftshader_indirect
```

### Soft klavye — iki yönlü, ikisi de gerekli

Her iki AVD'de `hw.keyboard=yes`. Bunun iki sonucu var ve **ikisi de tuzak**.

**Normal otomatik testte kapalı olmalı.** Açık kalırsa `adb shell input text`
sırasında klavye açılıp düzeni kaydırır, dokunma koordinatları şaşar.

```bash
adb shell settings put secure show_ime_with_hard_keyboard 0
```

**Klavyeyle ilgili bir şey ölçülecekse açılmalı.** Donanım klavyesi tanımlı
olduğu için yazılım klavyesi **hiç çizilmez**: `dumpsys input_method`
`mInputShown=true` der, IME penceresi de vardır, ama içerik inset'i sıfırdır
ve ekranda yer kaplamaz. Bu yüzden klavye testi **sessizce yanlış sonuç
verir** — hata vermez, sadece ölçtüğün şey gerçek telefonda olan şey değildir.
Faz 9b-1 kapanışında tam olarak bu oldu ve "sheet klavyeyle kaymıyor" diye
yanlış bir bulgu raporlandı.

```bash
adb shell settings put secure show_ime_with_hard_keyboard 1   # ölçümden önce
adb shell settings put secure show_ime_with_hard_keyboard 0   # ölçümden sonra
```

Klavyenin üst kenarı **API 29'da** kaydırma düğümünün
(`android.widget.ScrollView`) alt sınırından okunabilir — pencere orada hâlâ
`adjustResize` ile küçülüyor. **API 30 ve üstünde okunamaz:** edge-to-edge'den
sonra pencere küçülmüyor, `android:id/content` klavye açıkken de tam ekran
veriyor. Orada üst kenar `ekran yüksekliği - ime.bottom` ile bulunur
(API 34/36'da 2400 - 883 = 1517).

### Kaydırma testleri ekranın kenarından başlatılmaz — API 34 tuzağı

Jest tabanlı gezinmede ekranın sol ve sağ kenarındaki dar şerit **sistem geri
jestine** ayrılmıştır. Oradan başlayan bir `adb shell input swipe` uygulamanın
kaydırma bileşenine hiç ulaşmaz; uygulama geri gider ve ölçüm **sessizce
yanlış** çıkar — hata verilmez, sadece test ettiğin şey test edilmez.

Faz 10b doğrulamasında tam olarak bu oldu: geniş emülatörde (1080 px)
`input swipe 1040 ...` uygulamadan çıktı ve `topResumedActivity` launcher'a
döndü. Aynı kaydırma `input swipe 950 ...` ile beklendiği gibi çalıştı, satır
silindi.

| Başlangıç x (1080 px ekran) | Kenardan uzaklık | Sonuç |
|---|---|---|
| 1040 | 40 px | **Sistem geri jesti**, uygulamadan çıkış |
| 950 | 130 px | Uygulamaya ulaştı, satır silindi |

**Eşik ikili aramayla daraltılmadı**; yalnızca bu iki nokta ölçüldü. Android'in
varsayılan geri jesti şeridi kenar başına **20dp**, 420 dpi'da ≈ 52 px — yani
1040 şeridin içinde, 950 dışında kalıyor. Kural: kaydırmayı kenardan
**100 px'den fazla** içeriden başlat.

**İki AVD neden farklı davranıyor, ölçüldü:**

```bash
adb shell cmd overlay list android | grep navbar
```

| AVD | Etkin overlay | Sonuç |
|---|---|---|
| `subtrack_narrow_api29` | hiçbiri (üç tuşlu gezinme) | Geri jesti şeridi **yok**; x=690 (kenardan 30 px) sorunsuz çalıştı |
| `subtrack_wide_api34` | `[x] com.android.internal.systemui.navbar.gestural` | Şerit **var**; kenardan başlayan kaydırma uygulamadan çıkarıyor |

Yani bu, API sürümünün değil **gezinme modunun** sonucudur. Bir AVD'nin modu
değişirse davranış da değişir; şüphede kalınca yukarıdaki komutla bakılır.

### Kaydırarak silme: `input swipe` süresi 700 ms olmalı

`adb shell input swipe x1 y cx2 y 400` bazı koşullarda satırı silmiyor: aynı
mesafe, aynı başlangıç noktası, ama jest fiske (fling) sayılıp mesafe şartına
takılıyor. 700 ms'lik aynı kaydırma her seferinde sildi. Faz 11b
doğrulamasında bir kaydırma 400 ms ile çalıştı, emülatör yeniden başlatıldıktan
sonra aynı komut üç kez üst üste hiçbir şey yapmadı.

Kural: **silme** kaydırmalarında 700 ms kullan. 400 ms ve altı yalnızca
"silmemeli" maddelerinde (liste #8, hızlı kısa fiske) anlamlıdır — orada zaten
silmemesi beklenir, yani sessizce yanlış geçmez.

### Tarih seçicinin onay düğmesi de "Save" diyor

`DatePickerDialog`'un confirm düğmesi ile sheet'in Kaydet düğmesi **aynı
metni** taşıyor. Diyalog kapanırken alınan bir dump'ta iki düğüm de "Save"
diye görünür; ilkine dokunmak boşa gider ve test, kaydetmediğini fark etmeden
devam eder. Faz 12-1 doğrulamasında bu oldu: 29. madde "kart yok" dedi, sebep
üründe değil ölçümdeydi.

Kural: diyalogda Kaydet'e bastıktan sonra **"Select date" başlığının
kaybolmasını bekle**, sonra yeniden dump al.

### Çıpa tarihi takvimden değil, metin girişinden verilir

**#69-#75 bu yüzden artık "ölçülemez" değil.** O maddeler geçmiş ve çok eski
çıpalar istiyor; takvimden aya aya geri gitmek hem uzun hem kırılgan. Seçicinin
kendi **metin giriş modu** her tarihi tek seferde alıyor:

1. Tarih alanına dokun → seçici açılır
2. **"Metin giriş moduna geç"** (klavye ikonu, sağ üst)
3. Alana dokun, `KEYCODE_MOVE_END` + yeterince `KEYCODE_DEL`, sonra rakamları
   ayraçsız yaz: `25092037`
4. Diyaloğun Kaydet'ine bas, **"Tarih seç" başlığı kaybolana kadar bekle**
5. Sonra sheet'in Kaydet'ine bas

Böyle kurulup 16b'de dört cihazda ölçülen çıpalar: `11.09.2026` (geçmiş aylık →
"25 gün kaldı"), `11.09.2026` haftalık → "2 gün kaldı", `11.09.2025` yıllık →
"360 gün kaldı", `01.01.2024` haftalık → "5 gün kaldı", `31.01.2026` aylık →
"14 gün kaldı".

**#72'nin asıl sınavı `31.01.2026` çıpası.** İlerletme çıpadan sayılırsa 31 Ocak
+ 8 ay = **30 Eylül** ("14 gün kaldı"); adım adım kırpılarak sayılsaydı 28 Şubat
üzerinden **28 Eylül** ("12 gün kaldı") çıkardı. İki yol farklı sayı veriyor,
yani madde gerçekten ayırt ediyor. Maddenin "Mart'ta bak" yazan hâli cihaz
saatini ileri almayı gerektirir ve o hâlâ yapılamıyor; ölçtüğü özellik bu
çıpayla ölçülüyor.

**Metin maskesi API 24'te farklı.** Orada ipucu `DDMM/YYYY`, diğerlerinde
`DD.MM.YYYY`; alan yazarken `250/9/2037` gibi tuhaf görünüyor ama ayrıştırma
doğru ("Girilen tarih: 25 Eylül 2037 Cuma"). Maskeye değil, seçicinin başlığında
yazan ayrıştırılmış tarihe bakılır.

### "Geri al"a dokunmak: dump'la yetişilmez, tek shell satırıyla yapılır

Snackbar `Short` süreyle (≈4 sn) duruyor. `uiautomator dump` + `exec-out cat`
çifti ise **3,3 saniye** sürüyor (16b'de ölçüldü). Yani "dump al, düğmeyi bul,
dokun" döngüsü tam da snackbar kapanırken varıyor: 16b'de #19 arka arkaya iki kez
"geri alma çalışmıyor" diye düştü, sonra ürünün değil ölçümün yavaş olduğu
anlaşıldı.

Kural: silme ile dokunuş **tek `adb shell` satırında** gider, aradan dump
geçmez. Düğmenin koordinatı bir önceki turdan bilinir:

```bash
adb shell "input swipe 900 1202 100 1202 700; sleep 1; input tap 942 2053"
```

| Cihaz | "Geri al" (tıklanabilir düğüm) merkezi |
|---|---|
| API 24 | `615,968` |
| API 29 | `615,1064` |
| API 34 / API 36 | `942,2053` |

### Yüklü emülatörde "bayat ağaç" — durum kalmış gibi görünür

12-2 hotfix koşusunda üç madde (#60 filtre, #68 yıllık görünüm, #14 yazılmış
metin) **kalıcı olmuş gibi** göründü. Üçü de yanlış alarmdı: `am start`'tan
sonra uygulama henüz açılış ekranında (splash) beklerken alınan
`uiautomator dump`, force-stop'tan **önceki** ağacı döndürüyor.

- Kanıt: aynı anda alınan `exec-out screencap -p` yalnızca Android robot
  ikonunu (splash) gösteriyordu; 20 saniye sonra ağaç doğru değeri verdi.
- Kural: "durum kalıcı mı" türünden bir maddede ağaç beklenenden **farklı**
  çıkarsa, raporlamadan önce ekran görüntüsü al. Kalan durum gerçekse
  ekranda da görünür.
- `restart_app` artık FAB'ı bekliyor ve bulamazsa **hata veriyor** — sessizce
  eski ağaçla devam etmektense düşmesi iyidir.

### "System UI isn't responding" diyaloğu ölçümü keser

Yüklü emülatörde (özellikle API 34) bu sistem diyaloğu ekranı kaplıyor ve
dump "boş ekran" gibi görünüyor. **Wait**'e dokunmak yetiyor; sürücü
betiklerindeki `wait_for` artık bunu kendisi yapıyor. Diyalog tekrar
tekrar çıkıyorsa `adb reboot` ile cihazı tazele — ürünle ilgisi yok.

### 360dp'de form artık kaydırma istiyor

Periyot sırası (Faz 12-1) formu bir sıra uzattı. 360dp'lik ekranda **kategori
chip'leri ve tarih alanı açılışta ekranın altında kalıyor**; ikisine de
ulaşmak için form kaydırılmalı. Ürün açısından sorun değil — form zaten
kaydırılabilir ve iki alan da opsiyonel — ama otomatik testte "düğüm yok"
hatası olarak çıkar. Ölçüm yaparken önce kaydır.

### Bildirim izni durumunu adb ile kurma ve okuma

Ayarlar'daki "Ödeme hatırlatmaları" satırı üç hal gösterir ve üçü de elle
kurulabilir. Okumak için:

```bash
adb shell dumpsys package com.elinacn.subtrack | grep POST_NOTIFICATIONS
```

`granted=false` + bayraklarda `USER_FIXED` yoksa "hiç sorulmamış veya bir kez
reddedilmiş"; `USER_FIXED` varsa **kalıcı ret**.

Kurmak için:

```bash
adb shell pm clear com.elinacn.subtrack        # hiç sorulmamış hale döner
adb shell pm grant  com.elinacn.subtrack android.permission.POST_NOTIFICATIONS
adb shell pm revoke com.elinacn.subtrack android.permission.POST_NOTIFICATIONS
```

**`pm revoke` uygulama sürecini öldürür.** Ölçüldü: pid `pm revoke`'tan sonra
kayboluyor. Bu yüzden `ON_RESUME` tazelemesi bu komutla gösterilemez —
gösterilecekse bildirimler **sistem ayarları arayüzünden** kapatılıp geri
dönülür, o yol süreci öldürmüyor (aynı pid kalıyor).

Kanal durumunu okumak için:

```bash
adb shell dumpsys notification --noredact | grep -A3 payment_reminders
```

`mImportance=0` kanalın susturulduğu anlamına gelir. **Kanal ancak ilk bildirim
gönderildikten sonra vardır**; oluşturmak için hatırlatma testini bir kez
koşturmak yeterli.

Sistem izin diyaloğunun düğümleri `com.android.permissioncontroller` paketinde
olur; ilk soruda `permission_allow_button` / `permission_deny_button`, ikinci
soruda `permission_deny_and_dont_ask_again_button` çıkar.

### Yazı tipi ölçeği

```bash
adb shell settings put system font_scale 2.0
adb shell settings put system font_scale 1.0
```

Değiştirdikten sonra Activity yeniden başlatılır. **Test bitince 1.0'a geri
alın** — unutulursa sonraki tüm ölçümler yanlış çıkar.

### Cihaz dili — iki yol, API'ye göre

`setprop persist.sys.locale tr-TR` **root ister ve bu imajlarda çalışmaz**
("Failed to set property"). Onun yerine:

**API 33+ (`subtrack_wide_api34`, `subtrack_edge_api36`)** — uygulamaya özel dil:

```bash
adb shell cmd locale set-app-locales com.elinacn.subtrack --locales tr-TR
adb shell cmd locale get-app-locales com.elinacn.subtrack
adb shell cmd locale set-app-locales com.elinacn.subtrack --locales ""   # geri al
```

Hızlı ve tekrarlanabilir, ama **cihazın dili değil uygulamanınki** değişir.
`pm clear` bu ayarı da siler, sonra yeniden verilmeli.

**API 24/29 (`cmd locale` yok: "Can't find service: locale")** — Ayarlar
arayüzünden, gerçek cihaz dili:

```bash
adb shell am start -a android.settings.LOCALE_SETTINGS
```

Sonra: **Dil ekle** → arama → `Türkçe` → `Türkiye`. Yeni dil **2. sıraya** girer;
1. sıraya taşımak için sürüklemek yerine **Diğer seçenekler → Kaldır** ile
İngilizce'yi silmek daha güvenilir (tek dil kalınca o birinci olur). Doğrulama:

```bash
adb shell getprop persist.sys.locale      # tr-TR
```

Geri almak aynı yol, ters yönde. #16 bu yolla ölçüldüğünde **gerçekten cihaz
dili** ölçülmüş olur; API 33+ tarafında ölçülen şey uygulama dilidir ve tur
raporunda böyle yazılır.

**API 24'ün locale verisi farklı.** Aynı tr-TR'de saat 12 saatlik biçimde
yazılıyor ("Son düzenleme: 16 Eyl 2026 ÖS 9:57"), API 29/34/36'da 24 saatlik
("... 19:24"). Uygulamanın değil platformun biçimlendiricisi; madde metni saat
biçimi istemiyor, sayı ve para birimi istiyor.

### Koyu tema

```bash
adb shell cmd uimode night yes
adb shell cmd uimode night no
```

**API 29'da çalışmıyor** — komut "Night mode: no" döndürüp değeri yazmıyor,
`settings put secure ui_night_mode 2` de tutmuyor.

**API 24'te komut hiç yok** — `cmd uimode night yes` "No shell command
implementation" der (servis kayıtlı, kabuk arayüzü yok). Sistem geneli koyu tema
zaten Android 10 ile geldi, yani Android 7.0'da böyle bir ayar **yoktur**.

Sonuç: *sistem temasını değiştirmeyi* gerektiren maddeler (#107, #109) yalnızca
`subtrack_wide_api34` ve `subtrack_edge_api36` üzerinde ölçülür.

**Ama koyu temanın kendisi dört cihazda da ölçülebilir.** Faz 14b'den sonra
uygulamanın kendi **Ayarlar → Tema → Koyu** seçeneği sistemden bağımsız çalışıyor;
API 24 ve 29'da renkler böyle ölçülür ve aynı değerleri verir (`#0D1A14` /
`#1F3D2D` = 1,50:1, çubuk `#D4AF37` / iz `#3A5A48` = 3,65:1). Sistemden
gelmeyen tek şey "uygulama sistemi takip ediyor mu" sorusudur.

**Faz 14a'dan sonra koyu temada ne aranır.** İki şema aynı rolleri farklı hue'lara
veriyor (`ARCHITECTURE.md` §12), o yüzden "koyu tema açık temanın koyusu" değil:

| Nerede | Açık tema | Koyu tema |
|---|---|---|
| Vurgu / ikon / grafik | zümrüt `#0B5C3F` | altın `#D4AF37` |
| Dolu altın yüzey | `tertiary` (üstünde koyu mürekkep) | yok, altın mürekkep |
| Kart / arka plan | `#FFFFFF` / `#D3E2D8` | `#1F3D2D` / `#0D1A14` |

Kural: **açık temada altın metin görünüyorsa hata** (beyazda 2,42:1). Ekranın
herhangi bir yerinde mor/lila bir piksel görünüyorsa, bir rol tanımsız kalmış
demektir — 14a'da bunun iki örneği Snackbar'ın "Geri al"ı ve chip kenarlığıydı.

Karşılaştırma için: `docs/screenshots/phase-14a/` altında her ekranın iki temada
çekilmiş hâli var.

### Tema tercihi — uygulamanın kendi ayarı, sistem ayarı değil

14b'den sonra `cmd uimode night` **tek başına yetmiyor**: uygulamada
"Sistemi takip et" seçili değilse sistem teması değişse de uygulama
değişmez, ve bu **doğru davranış**. Koyu tema maddelerinden önce
Ayarlar → Tema'nın ne dediğine bakın.

Tercih `theme_mode`, duvar kâğıdı anahtarı `dynamic_color` olarak DataStore'da:

```bash
adb exec-out run-as com.elinacn.subtrack cat files/datastore/settings.preferences_pb
```

Dosya **hiç yoksa** henüz hiçbir tercih yazılmamış demektir — API 29'da devre dışı
satıra dokunmanın hiçbir şey yazmadığı böyle doğrulandı.

### Duvar kâğıdı rengini değiştirme — `cmd wallpaper` işe yaramıyor

`adb shell cmd wallpaper` bu imajlarda yalnızca karartma komutları taşıyor;
**duvar kâğıdı atama komutu yok** (`help` çıktısında `set-wallpaper` bulunmuyor) ve
resim seçici etkileşimli. Çalışan yöntem, sistemin duvar kâğıdından çıkardığı
**tohum rengini** doğrudan yazmak — `dynamicLightColorScheme`'in okuduğu girdi
zaten bu:

```bash
# sıcak / kırmızı palet
adb shell "settings put secure theme_customization_overlay_packages '{\"android.theme.customization.system_palette\":\"B33A3A\",\"android.theme.customization.accent_color\":\"B33A3A\",\"android.theme.customization.theme_style\":\"VIBRANT\"}'"

# soğuk / mavi palet
adb shell "settings put secure theme_customization_overlay_packages '{\"android.theme.customization.system_palette\":\"2E4FB3\",\"android.theme.customization.accent_color\":\"2E4FB3\",\"android.theme.customization.theme_style\":\"VIBRANT\"}'"

# geri al
adb shell settings delete secure theme_customization_overlay_packages
```

Yazdıktan sonra birkaç saniye bekleyip uygulamayı yeniden başlatın. **Bu bir
duvar kâğıdı resmi değil, o resimden çıkarılan renk** — üretilen şemalar gerçek
duvar kâğıdıyla üretilenlerin aynısı, ama ölçüm tekrarlanabilir oluyor.
14b'nin ölçümleri bu iki tohumla alındı;
`docs/screenshots/phase-14b/` altında ikisinin ekran görüntüleri var.

### Kaydırarak silme **sona doğru**, yani LTR'de sola

14b turunda üç tur boşa gitti: soldan sağa kaydırma hiçbir şey yapmıyor çünkü
**yapmaması gerekiyor** (liste #10). Silme kaydırması sağdan sola olmalı.

Başlangıç noktası da önemli: API 34'te `x=1030`'dan başlayan bir kaydırma
Google Lens'i açtı. Sağ kenardan **en az 150 px** içeriden başlatın
(360dp'de `x=620→100`, 411dp'de `x=900→200` çalıştı).

### TalkBack

**Şu an yapılamıyor.** Ne API 29 ne API 34 `google_apis_playstore` imajında
Android Accessibility Suite kurulu değil (`pm list packages -u` ve
`/system/priv-app` taramasında iz yok). Kurmak için ya emülatörde bir Google
hesabıyla Play Store'a girilmeli ya da imaj değiştirilmeli. Erişilebilirlik
davranışı bu yüzden yalnızca **erişilebilirlik ağacından** doğrulanabiliyor:

```bash
adb shell uiautomator dump /sdcard/u.xml
adb exec-out cat /sdcard/u.xml
```

Bu ağaç ekran okuyucunun okuduğu şeyin ta kendisi; `contentDescription` ve
düğüm birleşmesi buradan görülür. Görmediği tek şey **özel eylemler**
(`CustomAccessibilityAction`) — dump biçimi eylem listesini içermiyor, o
yüzden "Sil" eyleminin varlığı bu yolla ne doğrulanabilir ne çürütülebilir.

### Ölçüm

`uiautomator dump` bottom sheet açıkken çoğu zaman alttaki pencereyi
döndürüyor, sheet içindeki bileşenleri göremiyor. Piksel ölçümü için ham
ekran görüntüsü kullanılır:

```bash
adb exec-out screencap > ekran.raw
```

Baştaki 12 veya 16 baytlık başlıktan sonra RGBA gelir; `dosya_boyutu -
genişlik*yükseklik*4` başlığın hangisi olduğunu verir. Bu makinede görüntü
kütüphanesi yok ve kurulmuyor.

**Kendiliğinden kaybolan bir şeyin pikseli** (Snackbar gibi) `exec-out` ile
yakalanmıyor: birkaç megabaytın USB üzerinden host'a akması Snackbar'ın ömrünün
kayda değer bir kısmını yiyor. Çözüm, önce cihaz üstüne yazmak:

```bash
adb shell screencap /sdcard/frame.raw   # anlık, aynı karede biter
adb exec-out cat /sdcard/frame.raw > ekran.raw
```

### Otomatik testler

Bugün **330 birim testi** ve **19 enstrümantasyon testi** var. Enstrümantasyon:
8 abonelik DAO'su + **8 aylık anlık görüntü DAO'su (Faz 12a)** + 2 hatırlatma
worker'ı + 1 düzenlenen tarih (Faz 15). Şablon testler Faz 16b'de silindi.

İkisi de iki yoldan koşar ve **iki yol da geçmek zorundadır**:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest

./gradlew :app:installDebug :app:installDebugAndroidTest
adb shell am instrument -w com.elinacn.subtrack.test/androidx.test.runner.AndroidJUnitRunner
```

**Hazırlık gerekmez.** Faz 16b'ye kadar bu paket `pm clear` ve `pm grant`
istiyordu; artık istemiyor. İki hatırlatma sınıfı da ön koşulunu `@Before`
içinde kendisi kuruyor: günün "bildirildi" kaydını siliyor ve API 33+ cihazda
bildirim iznini kendisi veriyor. Ayrıntısı bir alttaki bölümde.

`connectedDebugAndroidTest` bitince uygulamayı **kaldırıyor**. Ekran görüntüsü
veya elle test yapılacaksa testten sonra yeniden kurun.

**Bildirim gözlemlenecekse bu görevi kullanmayın** — uygulama kaldırıldığı için
bildirim de ekrandan gider. Onun yerine iki APK kurulup test doğrudan
çalıştırılır:

```bash
adb shell am instrument -w -e class com.elinacn.subtrack.reminder.PaymentReminderWorkerTest   com.elinacn.subtrack.test/androidx.test.runner.AndroidJUnitRunner
adb logcat -d -s ReminderWorkerTest:V
```

**Bildirim metni okunacaksa uygulamayı açmadan önce okuyun.** `am force-stop`
(ve dolayısıyla `restart_app`) uygulamanın bildirimlerini siler; önce
`dumpsys notification --noredact`, sonra ekran.

**Bildirim kanalını bu test yaratır.** `payment_reminders` kanalı ilk bildirim
gönderilene kadar **yoktur** — sistem ayarlarında "Bu uygulama herhangi bir
bildirim yayınlamadı" yazar. #39'u ölçmek için önce
`PaymentReminderWorkerTest` bir kez koşturulur, sonra kanal kapatılır:

```bash
adb shell "am start -a android.settings.CHANNEL_NOTIFICATION_SETTINGS   --es android.provider.extra.APP_PACKAGE com.elinacn.subtrack   --es android.provider.extra.CHANNEL_ID payment_reminders"
```

Açılan ekranda "Bildirim göster" kapatılır; uygulama izni `granted=true` kalır
ve ayarlardaki satır "Kapalı — sistem ayarlarından açılmalı" demelidir. Bu yol
API 26+ içindir; API 24'te kanal kavramı yok.

### Paketin sıra bağımsızlığı — neye dayanıyor, nasıl kanıtlanır

Faz 16-0'da paket **sırayla koşunca** düşüyordu. İki sınıf da günde-bir
hatırlatma worker'ını sürüyor; hangisi önce koşarsa günü işaretliyor, öteki
erken dönen bir worker buluyor ve gözleyecek bir şey bulamıyordu. Kural doğru,
bozuk olan testlerin birbirinin ön koşuluna yaslanmasıydı.

**Çözüm sıra sabitlemek değil.** `@FixMethodOrder` bağımlılığı gizler, kaldırmaz
— 12-2 hotfix'inde aynı çözüm denenip gerçek sebep bulununca geri alınmıştı.
16b'de her sınıf ön koşulunu kendisi kuruyor:
`androidTest/.../testsupport/ReminderPreconditions.kt`.

**Kaydı neden dosyadan silmek yetmiyor.** DataStore okumayı bellekteki
önbellekten karşılıyor ve dosyayı yalnızca yazma kilidini tutarken yeniden
okuyor. Ölçüldü (DataStore 1.1.7): değer yazıldıktan sonra dosya silinip
okunduğunda **eski değer** geliyor, ancak bir sonraki yazma diskteki yokluğu
görüyor. Worker ise önce okuyor. Yani `settings.preferences_pb` dosyasını silmek
çalışan bir uygulamada hiçbir şey değiştirmez; aynı dosya üzerine ikinci bir
DataStore açmak da çalışma zamanı hatasıdır. Kayda ulaşmanın tek yolu **örneğin
kendisine** ulaşmak, o da Hilt grafiğinde.

Bunun için `app/src/debug/` altında bir `@EntryPoint` var
(`PreferencesStoreEntryPoint`). Debug'da duruyor çünkü bir entry point'in
işlenmesi gerekiyor ve KSP yalnızca app modülünde koşuyor; release derlemesine
girmiyor ve `app/src/main/` değişmedi.

**İzin de aynı yerde veriliyor.** `connectedDebugAndroidTest` koşumdan hemen
önce iki APK'yı yeniden kuruyor ve API 33+ sürümlerde kurulum çalışma zamanı
iznini düşürüyor — elle `pm grant` yapılmış bir cihazda paket geçip Gradle'dan
koşunca "no notification was posted" diye düşüyordu. Test, izni
`UiAutomation.executeShellCommand("pm grant …")` ile kendisi veriyor; o komut
kabuk kullanıcısı olarak koşar, `pm revoke`'un aksine süreci öldürmez ve
CI'da da çalışır.

**Kanıt nasıl alınır.** Paketin tamamı, sırayla ve tek seferde, **iki koşum
yöntemiyle** ve **arka arkaya iki kez** (aradan temizlik geçirmeden) koşturulur:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest   # temizlik yok

./gradlew :app:installDebug :app:installDebugAndroidTest
adb shell am instrument -w com.elinacn.subtrack.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w com.elinacn.subtrack.test/androidx.test.runner.AndroidJUnitRunner
```

İkinci koşum da geçmiyorsa ön koşul kurulumu eksiktir — sıraya bakılmaz.

`reminderWorker_notificationsDisabled_succeedsWithoutNotifying` bildirimler
açıkken `assumeFalse` ile atlanır; "19 test, 1 atlandı" beklenen çıktıdır.
O metodu gerçekten koşturmak için önce
`adb shell pm revoke com.elinacn.subtrack android.permission.POST_NOTIFICATIONS`.

### Periyodik WorkManager işi — gövdesi gecikmesiz bir `OneTimeWorkRequest` ile koşturulur

**Kuyruğa girip girmediği** ve **hangi ayarlarla** girdiği iki yoldan okunur.

Diagnostics yayını iş adını, sınıfını ve durumunu logcat'e yazar:

```bash
adb logcat -c
adb shell am broadcast -a "androidx.work.diagnostics.REQUEST_DIAGNOSTICS" -p com.elinacn.subtrack
adb logcat -d -s WM-DiagnosticsWrkr:V
```

Periyot, ilk gecikme ve constraint'ler bu çıktıda **yok**. Onlar WorkManager'ın
kendi veritabanındadır — `databases/` altında değil, `no_backup/` altında:

```bash
adb exec-out run-as com.elinacn.subtrack cat no_backup/androidx.work.workdb > work.db
adb exec-out run-as com.elinacn.subtrack cat no_backup/androidx.work.workdb-wal > work.db-wal
```

`WorkSpec` tablosunda `interval_duration`, `initial_delay`, `state`,
`period_count` ve `requires_*` sütunları; `WorkName` tablosunda unique ad
karşılığı vardır. **`-wal` dosyası da çekilmeli** — tablolar çoğu zaman ana
dosyaya henüz yazılmamıştır ve yalnız `.db` "file is not a database" der.

JobScheduler tarafı:

```bash
adb shell dumpsys jobscheduler | grep -A25 "JOB #u0a<UID>/0:"
```

`Requires:` satırı constraint'leri, `Run time: earliest=` ilk gecikmeyi gösterir.

**Erken çalıştırmak bu imajlarda mümkün değil.** Denenen ve tükenen yollar:

| Yol | Sonuç |
|---|---|
| `adb shell cmd jobscheduler run -f com.elinacn.subtrack <id>` | Komut çalışır (`Running job [FORCED]`) ama **worker gövdesi koşmaz.** WorkManager kendi denetimini yapar: `WM-WorkerWrapper: Delaying execution for … because it is being executed before schedule. Status … is ENQUEUED; not doing any work and rescheduling for later execution` |
| `adb root` | `adbd cannot run as root in production builds` |
| `adb shell date <MMDDhhmmYYYY>` | `date: cannot set date: Operation not permitted` |
| `adb shell setprop persist.sys.timezone …` | `setprop: failed to set property` |

Yani JobScheduler'ı zorlamak yetmiyor; WorkManager `lastEnqueueTime +
initial_delay` geçmeden çalışmıyor ve o eşiği aşmanın tek yolu duvar saatini
ileri almak.

**Faz 16c düzeltmesi: duvar saati root olmadan da ileri alınabiliyor.** Burada
"çalıştırılamaz" yazıyordu; iki yol bulundu ve dördünde de kullanıldı. Yani
yukarıdaki iki yol işin kuyruğa doğru girdiğini kanıtlar, **gövdesi ise
aşağıdaki yolla gerçekten koşturulur.**

### Duvar saatini root olmadan ileri almanın iki yolu

**API 31+ (34, 36) — tek komut.** `time_detector` servisinin test kancası
`SET_TIME` izni istemiyor:

```bash
adb shell cmd time_detector set_auto_detection_enabled false
adb shell cmd time_detector set_time_state_for_tests --elapsed_realtime $(adb shell cat /proc/uptime | awk '{printf "%d", $1*1000}') --unix_epoch_time 1789635900000 --user_should_confirm_time false
```

`--unix_epoch_time` hedef anı milisaniye cinsinden ister. Bitince
`set_auto_detection_enabled true` ile geri alınır.

> `suggest_manual_time` **çalışmaz**: `uid 2000 does not have
> android.permission.SUGGEST_MANUAL_TIME_AND_ZONE`. Çalışan çağrı
> `set_time_state_for_tests`.

**API 24 ve 29 — Ayarlar arayüzünden.** Bu sürümlerde `time_detector` ya hiç
yok (API 24: *"Can't find service"*) ya da kabuk komutu yok (API 29: *"No
shell command implementation"*). Ama Ayarlar'daki anahtar kapatılınca saat
elle kurulabiliyor:

```bash
adb shell am start -a android.settings.DATE_SETTINGS
```

"Automatic date & time" (API 29'da "Use network-provided time") **kapatılır**,
sonra "Set date" / "Set time" ile tarih ve saat verilir. Saat seçici bir
kadran: önce AM/PM, sonra saat, sonra dakika. Test bitince anahtar **geri
açılır**, cihaz gerçek saate döner.

**Sıra önemli:** iş, uygulamanın ilk açılışında `09:00`'a kuyruklanır. Saati
`09:00`'dan **önceye** kurup uygulamayı açmak, sonra `09:00`'ı geçip
zorlamak gerekir. Hedef saatten **önce** bir kez `cmd jobscheduler run -f`
denemek işi bozar: WorkManager o denemede işi yeniden zamanlar ve sonraki
çalıştırma bir **gün** ileri kayar. O noktadan dönüş, tarihi bir gün ileri
almak (veya `pm clear` ile WorkManager veritabanını silip baştan kurmak).

API 29'da iş, saat `09:00`'a geldiğinde **kendiliğinden** koştu; zorlamaya
gerek kalmadı. Bu, zamanlamanın da doğru olduğunun tek doğrudan kanıtı.

### Çalışan yöntem: gecikmesiz `OneTimeWorkRequest`

WorkManager'ın "zamanından önce çalıştırma" denetimi **yalnızca gecikmeli veya
geri çekilmiş** işler içindir. Bekleyecek hiçbir şeyi olmayan tek seferlik bir
iş anında koşar. Enstrümantasyon testi bunu kullanır:

```kotlin
WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<PaymentReminderWorker>().build())
```

Enstrümantasyon uygulamayla **aynı süreçte** koştuğu için buradaki
`WorkManager`, uygulamanın kendi `Configuration.Provider`'ıyla kurulmuş
örneğidir; worker'ı gerçek `HiltWorkerFactory` üretir. Sahte fabrika,
`work-testing` bağımlılığı veya `TestListenableWorkerBuilder` gerekmez ve
kullanılmaz — koşan sınıf üretimde koşanın aynısıdır.

Periyodik işin **zamanlaması** hâlâ bu yolla doğrulanamaz; o `WorkSpec`
tablosundan ve `dumpsys jobscheduler`'dan okunur.

### Açılış süresi

```bash
adb shell am force-stop com.elinacn.subtrack
adb shell am start -W -n com.elinacn.subtrack/.MainActivity
```

Beş tekrarın medyanı alınır. Debug build ölçümüdür, release değil —
karşılaştırma yalnızca kendi içinde anlamlıdır.

---

## Mağaza Ekran Görüntüleri (Play Console)

Faz 16e-2'de kuruldu. `docs/screenshots/` altındaki diğer klasörler ham ölçüm
çıktısıdır; **mağaza seti ayrı bir AVD'de, sabit bir fikstürle** çekilir ve
`docs/screenshots/store/` altında durur.

### Neden ayrı bir AVD gerekiyor

Play telefon görüntülerinde **en fazla 2:1** en-boy oranı kabul ediyor.
Mevcut geniş AVD'ler 1080x2400 = **2,22:1** veriyor, yani yüklenmeden reddedilir.
Önerilen boyut 1080x1920 (9:16). Kenar başına 320-3840 px, JPEG veya **24-bit
PNG**, **alfa kanalı yok**, dosya başına en fazla 8 MB.

Yoğunluk 420 dpi seçiliyor çünkü 1080 / (420/160) = **411dp** — `subtrack_wide_api34`
ile birebir aynı dp genişliği. Yani layout, ölçüm turlarında test edilmiş
davranışını gösteriyor; mağaza için yeni bir genişlik sınıfı açılmıyor.

### Kurulum

```bash
avdmanager create avd -n subtrack_store_api34 -k "system-images;android-34;google_apis_playstore;x86_64" -d pixel_6 --abi x86_64
```

`pixel_6` profili 1080x2400 ile geliyor; sonra
`~/.android/avd/subtrack_store_api34.avd/config.ini` içinde şunlar değiştirilir:

```ini
hw.lcd.width=1080
hw.lcd.height=1920
hw.lcd.density=420
hw.keyboard=yes
showDeviceFrame=no
```

Başlatma ve doğrulama — **çözünürlük tutmuyorsa çekim yapılmaz**:

```bash
emulator -avd subtrack_store_api34 -no-snapshot-save -no-boot-anim -gpu swiftshader_indirect

adb shell wm size                            # Physical size: 1080x1920
adb shell wm density                         # Physical density: 420
adb shell cmd overlay list android | grep navbar   # [x] ...navbar.gestural
```

Gezinme modu imajda zaten gestural geliyor, değiştirmek gerekmedi. Saat dilimi
GMT — `subtrack_wide_api34` ile aynı, yani tarih fikstürü aynı çıpayla kurulur.

### Durum çubuğunu temizleme — SystemUI demo modu

Play durum çubuğunun düzenli olmasını istiyor. Demo modu bu imajda **çalışıyor**:

```bash
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e fully true
adb shell am broadcast -a com.android.systemui.demo -e command network -e mobile show -e datatype none -e level 4 -e fully true
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
```

**`-e fully true` şart.** O olmadan wifi simgesi "internet yok" ünlemiyle
(`!`) çiziliyor ve sinyal çubuğu yarım kalıyor — ilk turda tam olarak bu oldu.

Bitince demo modundan çıkılır; unutulursa sonraki her ölçüm sahte bir durum
çubuğuyla yapılır:

```bash
adb shell am broadcast -a com.android.systemui.demo -e command exit
adb shell settings put global sysui_demo_allowed 0
```

**Uygulama her yeniden başladığında demo komutları tekrar gönderilir** —
`am force-stop` + `am start` turu SystemUI'yi sıfırlamıyor ama ekran görüntüsü
alınmadan önce komutların gitmiş olduğundan emin olmak gerekiyor.

### Fikstür

16e'deki yolun aynısı (§"86-95 için veri nasıl kurulur"): uygulama durdurulur,
veritabanı `run-as` ile host'a alınır, host'ta `sqlite3` ile yazılır, geri
konur. Cihazda `sqlite3` yok. Fikstür **veritabanına** yazılır; üretim kodunda
buna açılmış bir kanca yoktur.

```bash
adb shell am force-stop com.elinacn.subtrack
adb exec-out run-as com.elinacn.subtrack cat databases/subtrack.db > subtrack.db
# host'ta: subscriptions ve monthly_snapshots yazılır
adb push subtrack.db /data/local/tmp/fixture.db
adb shell "cat /data/local/tmp/fixture.db | run-as com.elinacn.subtrack sh -c 'cat > databases/subtrack.db'"
adb shell "run-as com.elinacn.subtrack sh -c 'rm -f databases/subtrack.db-wal databases/subtrack.db-shm'"
```

**Veritabanı ancak uygulama bir kez açıldıktan sonra var.** Taze kurulumda
`databases/` dizini yok; önce uygulama açılır, sonra fikstür kurulur. WAL
dosyası da çekilir — host'taki `sqlite3` açılışta onu katlar, yoksa ana dosya
"file is not a database" der.

Mağaza setinin fikstürü — tek set, bütün ekranlarda aynı:

| Ad | Tutar | Periyot | Kategori | Sonraki ödeme |
|---|---|---|---|---|
| Netflix | ₺229,99 | aylık | Eğlence | bugün |
| Spotify | ₺87,99 | aylık | Eğlence | +1 gün |
| Gym | ₺1.450,00 | aylık | Sağlık | +5 gün |
| Dropbox | ₺39,90 | **haftalık** | Diğer | +3 gün |
| iCloud | **$2,99** | aylık | Üretkenlik | +12 gün |
| Notion | **€96,00** | **yıllık** | Üretkenlik | +23 gün |

Ana para birimi TRY (varsayılan, DataStore'a yazmak gerekmiyor). Kurlar
`ExchangeRateTable.Default` — USD 42,8500, EUR 46,2000.

`monthly_snapshots`'a **geçmiş beş ay** yazılır (202604-202608); içinde
bulunulan ayı kaydedici uygulama açılır açılmaz kendisi yazıyor (§19), ve
`MonthlyTrend.MAX_MONTHS` = 6 olduğu için pencere böyle dolar:

| period | totalInCents |
|---|---|
| 202604 | 198750 |
| 202605 | 205430 |
| 202606 | 199880 |
| 202607 | 222615 |
| 202608 | 231540 |
| 202609 | *243860 — uygulama yazar* |

Hepsi `TRY`; başka para birimindeki bir satır grafikten düşer ve ekranda
"şu kadar ay başka para biriminde" notu çıkar.

### Dil

`cmd locale` ile uygulama dili (API 33+):

```bash
adb shell cmd locale set-app-locales com.elinacn.subtrack --locales tr-TR
adb shell cmd locale set-app-locales com.elinacn.subtrack --locales en-US
adb shell cmd locale set-app-locales com.elinacn.subtrack --locales ""     # geri al
```

`values/` Türkçe (varsayılan), `values-en/` İngilizce. Emülatörün sistem dili
İngilizce olduğu için **dil verilmezse uygulama İngilizce açılır**.

### Koyu tema

`ThemeMode.Default` = SYSTEM, yani sistem anahtarı yetiyor:

```bash
adb shell cmd uimode night yes
adb shell cmd uimode night no
```

### FAB örtüşmesi — çekimden önce ölçülür

16e'nin bulgusu mağaza çekiminde de geçerli: kayan FAB liste ortasındaki bir
satırın tutarını örtebiliyor. Ana ekran çekiminde bu **olmamalı**.

Ölçüm 16e'nin yöntemiyle, iki bağımsız okumayla yapılır:

- **Koordinat:** `uiautomator dump` FAB'ın kabını değil 24dp'lik ikonunu
  veriyor; kap her kenardan **16dp (=42 px @420dpi)** büyütülerek türetilir.
- **Piksel:** tutar, kartta `colorScheme.primary` renkli **tek** metindir
  (açık temada `#0B5C3F`, koyu temada `#D4AF37`). O renkteki piksel öbekleri
  bulunur ve hiçbirinin FAB kutusuna değmediği doğrulanır.

Kartın baştaki ikonu da `primary` — sayıma girer, ama zaten kartın solunda
olduğu için FAB kutusuna hiç yaklaşmaz.

### Çekim sonrası: alfa kanalı

`adb exec-out screencap -p` **RGBA yazıyor**, Play ise alfa kanalı olan PNG'yi
reddediyor. Kanal her çekimde tamamen opak (255) çıkıyor, yani RGB'ye çevirmek
hiçbir pikseli değiştirmiyor — ama çevirmek **şart**:

```python
from PIL import Image
src = Image.open(path)
assert src.getchannel("A").getextrema() == (255, 255)   # önce opak olduğu kanıtlanır
src.convert("RGB").save(path, "PNG", optimize=True)
```

Sonuç 8 bit/kanal truecolour = **24-bit PNG**, alfa yok.

---

## Release APK ile Test Etme

Faz 16c'de kalıcı hâle geldi. **R8 açıkken bir şeyin kırılıp kırılmadığı debug
build'de görünmez.** Minify, reflection'la bulunan sınıf ve alan adlarını
değiştirir; Room, Hilt, `@HiltWorker`, DataStore ve Compose tarafında bir
kopma varsa **yalnızca release APK cihazda koşarken** ortaya çıkar. Bu yüzden
R8'e dokunan her değişiklikten sonra aşağıdaki tur release APK ile atılır,
debug ile değil.

### 1. Release APK'yı üret

```bash
./gradlew :app:assembleRelease
```

Çıktı: `app/build/outputs/apk/release/app-release-unsigned.apk`.
**Keystore yoksa dosyanın adı bunu söyler ve derleme yine geçer** — imzasız
APK cihaza kurulamaz.

### 2. Kurulabilmesi için imzala

Yayın anahtarı testte kullanılmaz. Test kurulumu için APK, SDK'nın **debug
anahtarıyla** imzalanır; bu yalnızca bir kurulum adımıdır, derleme
yapılandırmasına dokunmaz:

```bash
"$ANDROID_HOME/build-tools/36.1.0/apksigner" sign \
  --ks ~/.android/debug.keystore --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias androiddebugkey \
  --out /tmp/release-signed.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk
"$ANDROID_HOME/build-tools/36.1.0/apksigner" verify --print-certs /tmp/release-signed.apk
```

`verify` çıktısında `CN=Android Debug` görünmeli. Debug APK de aynı anahtarla
imzalı olduğu için ikisi birbirinin üzerine kurulabilir; yine de **önce
kaldırmak** temiz bir başlangıç verir.

### 3. Kur ve sür

```bash
adb -s <serial> uninstall com.elinacn.subtrack
adb -s <serial> install -r /tmp/release-signed.apk
adb -s <serial> logcat -b crash -c
adb -s <serial> shell am start -W -n com.elinacn.subtrack/.MainActivity
```

Uygulamanın **açılması** tek başına bir sonuçtur: Hilt grafı kırıksa süreç
`Application` kurulurken çöker.

### 4. Sürülecek yerler — hepsi reflection kullanıyor

| Yer | Ne yapılır | Kırılırsa nasıl görünür |
|---|---|---|
| Hilt | Uygulamayı aç | Açılışta çöküyor |
| Room | Liste, ekleme, düzenleme, silme, geri al | Açılışta veya ilk sorguda çöküyor |
| Room şeması | `schemas/1.json`'daki `identityHash` ile üretilen `*_Impl` karşılaştırılır | *"Room cannot verify the data integrity"* |
| DataStore | Para birimi, tema, kur değiştir; **tamamen kapatıp aç** | Tercih geri gelmiyor |
| WorkManager + `@HiltWorker` | İşi koştur (yukarıdaki saat yöntemi), bildirimi oku | `Could not instantiate …Worker` |
| Navigation | Dört hedefi de aç | Hedef açılmıyor / çöküyor |
| `java.time` + desugaring | Tarih seç, "gün kaldı" ve "Son düzenleme" satırlarını oku — **API 24'te** | `NoClassDefFoundError` |
| `NumberFormat` / locale | ₺ ve $ biçimlendirmesi, TR ve EN | Yanlış ayraç veya ISO kodu |

Bildirimin gerçekten shade'e ulaştığı okunur:

```bash
adb -s <serial> shell dumpsys notification --noredact | grep -A40 "pkg=com.elinacn.subtrack" | grep "android.title\|android.text"
```

API 24'te `--noredact` yok; orada shade açılıp `uiautomator dump` ile okunur:

```bash
adb -s <serial> shell service call statusbar 1
```

### 5. Tur bitince crash tamponu boş olmalı

```bash
adb -s <serial> logcat -b crash -d
```

Tek satır bile çıkmamalı. Dört cihazda da ayrı ayrı bakılır.

### 6. Debug build'e geri dön

Release APK test için kuruldu, geliştirme için değil:

```bash
adb -s <serial> shell pm clear com.elinacn.subtrack
adb -s <serial> uninstall com.elinacn.subtrack
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

Saat/dil gibi değiştirilen cihaz ayarları da geri alınır.

### Enstrümantasyon paketi release APK'ya karşı koşulamıyor

`testBuildType` ayarlanmadığı için varsayılan `debug`; Gradle'da yalnızca
`connectedDebugAndroidTest` var, `connectedReleaseAndroidTest` **yok**.
Release'e çevirmek uygulama ve test APK'sının **aynı anahtarla** imzalanmasını
(yani gerçek bir keystore) ister ve `ui-test-manifest` `debugImplementation`
ile bağlı olduğu için release varyantında Compose testlerinin Activity'si
olmaz. Bu yüzden release turu **elle** atılır.

---

## AAB ile Test Etme (Play'in Gerçekten Göndereceği Şey)

Faz 16g'de kuruldu. **Release APK turu bunun yerine geçmez.** Play kullanıcıya
universal APK'yı değil, AAB'den türetilmiş **bölünmüş** APK'ları gönderiyor;
base + dil + ABI ayrı ayrı. O ayrımın bozuk olması yalnızca bölünmüş kurulumda
görünür. Bu yüzden yayın öncesi tur AAB'den atılır.

### 1. AAB'yi üret

```bash
./gradlew :app:bundleRelease
```

Çıktı: `app/build/outputs/bundle/release/app-release.aab`.
İmzalama malzemesi `local.properties`'ten okunuyor (§24); dördü de varsa
`signReleaseBundle` görevi koşar ve bundle imzalı çıkar.

### 2. İmzayı doğrula

AAB **JAR imzası** taşır (APK Signature Scheme v2/v3 değil), yani `apksigner`
değil `jarsigner` sorulur:

```bash
jarsigner -verify -verbose:summary -certs app/build/outputs/bundle/release/app-release.aab
```

`jar verified.` ve `CN=ElinaDorothea` görülmeli. Üç uyarı **normaldir** ve
hata değildir: sertifika zinciri "geçersiz" (kendinden imzalı — Android
anahtarları zaten öyledir), imzada zaman damgası yok (Play istemiyor) ve
sertifika 2054-02-02'de dolacak (Play'in istediği 2033-10-22 eşiğinin çok
ötesinde).

### 3. bundletool

Gradle önbelleğindeki `bundletool-*.jar` **kütüphane** sürümüdür,
çalıştırılamaz (`no main manifest attribute`). Çalıştırılabilir olan
`bundletool-all-*.jar`'dır ve GitHub'dan indirilir:

```bash
curl -sSL -o bundletool-all-1.18.3.jar \
  https://github.com/google/bundletool/releases/download/1.18.3/bundletool-all-1.18.3.jar
```

**Bu depoda durmuyor.** Kullanılan kopya `C:\Users\cane7\tools\bundletool-all-1.18.3.jar`
altında; sürüm `java -jar … version` ile doğrulanır.

### 4. APK setini üret ve kur

`build-apks` imzalama malzemesini ister — **şifreler komut satırına elle
yazılmaz**, `local.properties`'ten okunur:

```bash
java -jar bundletool-all-1.18.3.jar build-apks \
  --bundle=app/build/outputs/bundle/release/app-release.aab \
  --output=subtrack.apks \
  --ks="$STORE" --ks-pass="pass:$STOREPW" \
  --ks-key-alias="$ALIAS" --key-pass="pass:$KEYPW"

java -jar bundletool-all-1.18.3.jar install-apks \
  --apks=subtrack.apks --device-id=<serial>
```

> **`install-apks` `ANDROID_HOME` ister.** Ayarlı değilse komut
> `CommandUtils.getAdbPath` içinde yığın izi bırakarak düşer; kurulum
> yapılmadığı hâlde çıktı yanıltıcı olabilir, bu yüzden **her zaman**
> `pm path` ile doğrulanır.

### 5. Kurulumu doğrula — üç parça gelmeli

```bash
adb -s <serial> shell pm path com.elinacn.subtrack
```

Beklenen: `base.apk`, `split_config.<dil>.apk`, `split_config.<abi>.apk`.
**Tek satır dönerse bölünme çalışmamıştır.** Sürüm de buradan okunur:

```bash
adb -s <serial> shell dumpsys package com.elinacn.subtrack | grep -E "versionCode|versionName"
```

API 34'te `minSdk=32` görmek normaldir — bundletool SDK'ya göre varyant
üretiyor, bundle'ın kendi base manifesti `minSdk=24` der (§26).

### 6. AAB içeriğini oku

```bash
java -jar bundletool-all-1.18.3.jar dump manifest --bundle=…/app-release.aab
java -jar bundletool-all-1.18.3.jar dump config   --bundle=…/app-release.aab
java -jar bundletool-all-1.18.3.jar dump resources --bundle=…/app-release.aab --values
unzip -l app-release.aab | grep '\.so$'          # ABI'ler
```

Diller `values-*` klasörlerinde **değil**, `resources.pb` içindedir; bu yüzden
dil listesi `dump resources` çıktısındaki `locale:` niteliklerinden sayılır.

### 7. Turu sür — iki cihazda

`subtrack_min_api24` ve `subtrack_wide_api34`. Her ikisinde: abonelik ekle,
toplamı doğrula, istatistik ve ayarlar ekranlarını aç, bildirimi tetikle
(aşağıdaki bölüm), crash tamponunun boş olduğunu gör.

> **Klavye API 34'te düzeni kaydırıyor.** Geniş cihazda ad alanına yazınca
> klavye açılıyor ve form yukarı kayıyor; önceki ekran görüntüsünden alınan
> koordinatlar artık geçersiz. Her alan dokunuşundan **sonra** ekran
> görüntüsü alınıp koordinat yenilenmezse dokunuşlar yanlış öğeye gider
> (16g'de ad alanına "Spotify89.9" yazıldı ve para birimi EUR'ya atladı).

### 8. Bildirimi AAB kurulumunda tetiklemek

Enstrümantasyon testi burada **kullanılamaz**: test APK'sı debug anahtarıyla,
uygulama yayın anahtarıyla imzalı. Tek yol duvar saatini ileri almak.

**Sıra önemli.** İş uygulamanın ilk açılışında ertesi 09:00'a kuyruklanır;
abonelik **o güne** ödemeli olmalı ve saat 09:00'ı geçtikten **sonra**
zorlanmalıdır.

JobScheduler'ın gecikmesi **elapsed realtime** tabanlıdır — duvar saatini
ileri almak onu tetiklemez, yalnızca WorkManager'ın kendi
`lastEnqueueTime + initial_delay` denetimini açar. İşi asıl koşturan şey
ikisinin birleşimidir:

```bash
# API 31+ (34, 36)
adb shell cmd time_detector set_auto_detection_enabled false
adb shell cmd time_detector set_time_state_for_tests \
  --elapsed_realtime <uptime_ms> --unix_epoch_time <hedef_ms> --user_should_confirm_time false

# API 24 ve 29 — Ayarlar arayüzünden (time_detector yok)
adb shell am start -a android.settings.DATE_SETTINGS
```

Sonra iş zorlanır. **Ad alanı API'ye göre değişiyor:**

```bash
adb shell cmd jobscheduler run -f com.elinacn.subtrack 0                              # API 24
adb shell cmd jobscheduler run -f -n androidx.work.systemjobscheduler com.elinacn.subtrack 0   # API 34
```

API 34'te ad alanı verilmezse komut `Could not find job 0 in package …` der.
Doğru iş kimliği `dumpsys jobscheduler | grep subtrack` ile okunur.

Bildirim **uygulamayı açmadan önce** okunur:

```bash
adb shell dumpsys notification --noredact | grep -E "android.title=|android.text="
```

Beklenen: `Payment reminder: 1 subscription` / `<Ad> — today`.

Tur bitince saat geri alınır (`set_auto_detection_enabled true`, ya da API 24'te
"Automatic date & time" yeniden açılır) ve **cihaz saati gerçek saatle
karşılaştırılarak doğrulanır.**

---

## Yedekle — Geri Yükle Turu (Auto Backup)

Faz 16f'de kuruldu. Kural dosyaları `res/xml/backup_rules.xml` (API ≤30) ve
`res/xml/data_extraction_rules.xml` (API 31+); ikisi birden gerekli, gerekçe
`ARCHITECTURE.md` §25.

### Taşıyıcının adı API'ye göre değişiyor

Yerel test taşıyıcısının bileşen adı **iki sürümde iki türlü**. Önce listele,
sonra listedeki adı birebir seç:

```bash
adb shell bmgr enable true
adb shell bmgr list transports
```

| Cihaz | Bileşen |
|---|---|
| API 31+ (`subtrack_wide_api34`, `subtrack_edge_api36`) | `com.android.localtransport/.LocalTransport` |
| API 24 (`subtrack_min_api24`) | `android/com.android.internal.backup.LocalTransport` |

**Tuzak:** yanlış adı versen de `bmgr transport` "Selected transport ..." diyor
ve hata vermiyor. Seçimin tuttuğunu `bmgr list transports` çıktısındaki `*`
işaretinden doğrula.

```bash
# API 31+
adb shell bmgr transport com.android.localtransport/.LocalTransport
# API 24
adb shell bmgr transport android/com.android.internal.backup.LocalTransport
```

### Tur

```bash
# 1. fikstürü kur (abonelikler, ana para birimi, kur, tema, tarih), sonra:
adb shell bmgr backupnow com.elinacn.subtrack     # "Backup finished with result: Success"
adb shell bmgr list sets                          # jeton: genelde "1"

# 2. temiz cihaz taklidi
adb uninstall com.elinacn.subtrack
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 3. geri yükle
adb shell bmgr restore 1 com.elinacn.subtrack     # API 31+: "restoreFinished: 0"
```

`restoreFinished: 0` **başarı** demek (transport OK). API 24 bu satırı hiç
yazmıyor, yalnızca `done` diyor — orada doğrulama çıktıdan değil, aşağıdaki
dökümden yapılır.

### Doğrulama — uygulamayı açmadan önce

Beyaz listenin tuttuğu **ancak burada** görülür: geri gelen dizinde yalnızca
`databases/` ve `files/datastore/` olmalı, `no_backup/` **boş**:

```bash
adb shell run-as com.elinacn.subtrack ls -R /data/data/com.elinacn.subtrack
```

Veritabanını okumak için (emülatörlerde `sqlite3` **yok**, dosyayı host'a
çekip orada açmak gerekiyor — üç dosya birden, WAL'siz döküm eksik çıkar):

```bash
for f in "" "-wal" "-shm"; do
  adb exec-out run-as com.elinacn.subtrack cat "databases/subtrack.db$f" > "subtrack.db$f"
done
python -c "import sqlite3;c=sqlite3.connect('subtrack.db');[print(r) for r in c.execute('SELECT * FROM subscriptions')];[print(r) for r in c.execute('SELECT * FROM monthly_snapshots')]"
```

Tercihler bayt bayt karşılaştırılır:

```bash
adb exec-out run-as com.elinacn.subtrack cat files/datastore/settings.preferences_pb | od -c
```

### Doğrulama — açtıktan sonra

```bash
adb logcat -c && adb shell am start -n com.elinacn.subtrack/.MainActivity
adb logcat -d -b crash | grep -i elinacn            # boş olmalı
adb shell dumpsys jobscheduler | grep -E "JOB .*subtrack|pkg=com.elinacn.subtrack"
```

İş kaydı **yeni** olmalı (yeni uid, sıfırdan job id): WorkManager veritabanı
`no_backup/` altında olduğu için yedekten gelmiyor, uygulama işi kendisi
yeniden kuruyor. Eski telefonun iş satırının taşınmaması **istenen** davranış.

**Bildirim izni geri gelmez.** API 33+ cihazda tercih geri gelir ama
`POST_NOTIFICATIONS` gelmez ve Ayarlar satırı "sistem ayarlarından açın" der;
Android çalışma zamanı izinlerini hiçbir zaman geri yüklemez. API 32 ve altında
böyle bir izin olmadığı için satır doğrudan "Açık" gelir. İkisi de doğru.

### Tur bitince

```bash
adb shell bmgr transport com.google.android.gms/.backup.BackupTransportService
adb shell pm clear com.elinacn.subtrack
```

---

## İkon Doğrulama (Başlatıcı, Temalı İkon, Durum Çubuğu)

Faz 16d'de kuruldu. İkon üç yerde ayrı ayrı çiziliyor — başlatıcının maskesi
altında, Android 13+ temalı ikon olarak ve durum çubuğunda beyaz siluet
olarak — ve üçü birbirinin yerine geçmiyor. Hepsi **piksel ölçümüyle**
doğrulanıyor, göz kararıyla değil.

### Kurulum

İkonun tazelenmesi için paketi yeniden kurmak yetiyor; başlatıcının önbelleğini
temizlemek gerekmiyor. Eski bir faz farklı bir anahtarla imzalanmış bir sürüm
bırakmışsa `install` **`INSTALL_FAILED_UPDATE_INCOMPATIBLE`** diyor — önce
kaldırın:

```bash
adb uninstall com.elinacn.subtrack
adb uninstall com.elinacn.subtrack.test
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

### Başlatıcı maskesi — hangi şekil, işaret kesiliyor mu

Uygulama çekmecesini açıp ekran görüntüsü alın, sonra karonun siluetini
merkezden 720 ışınla tarayın. **`max/min` oranı şekli söyler:** daire 1,00
civarı, yuvarlatılmış kare ~1,26, düz kare 1,414.

> **Tuzak:** "arka plan rengine benzemeyen piksel" testi API 36'nın
> **gradyanlı** çekmece arkaplanında yanlış sonuç veriyor — köşelerde arka plan
> yeterince kayıyor ve oran 1,26 çıkıyor, yani daire yuvarlatılmış kare gibi
> okunuyor. Karo koyu, çekmece açık: **parlaklık eşiği** kullanın
> (`sum(rgb) < 3×140`), arka planla karşılaştırma değil.

Ölçülen (Faz 16d):

| Cihaz | maske `max/min` | şekil | karo | işaret | pay |
|---|---|---|---|---|---|
| api34 | 1,022 | daire | 51,43dp | 47,30dp | 2,06dp |
| api36 | 1,019 | daire | 60,19dp | 55,01dp | 2,59dp |

Android 16 bunu ayrıca **yazıyla** da söylüyor: uzun basış → *Wallpaper &
style* → **Icons** sayfası "Circle, default" diyor ve orada beş şekil seçeneği
var. API 34'te böyle bir sayfa yok, ölçüm tek yol.

### API 24 — PNG yedeği gerçekten kullanılıyor mu

API 24'te adaptive icon yok. Karonun **ölçeği ve köşe yuvarlaması** hangi
varlığın çizildiğini ele veriyor:

- `mipmap-xhdpi/ic_launcher.png` (96 px) yükleniyor, Launcher3 onu karo
  boyutuna (density 2,0'da 120 px = 60dp) ölçekliyor.
- Köşe: çapraz erişim / eksen erişimi oranı **1,251** ölçüldü; üreticinin
  çizdiği `0,1875 × kenar` yuvarlaması **1,2588** verir. Yani yuvarlatma
  sistemden değil, dosyadan geliyor — API 24 maske uygulamıyor.
- İşaret/karo oranı 0,88 çıkarsa PNG, 0,914 çıkarsa adaptive icon çiziliyor
  demektir. API 24'te 0,88, API 26+'da 0,914 bekleyin.

### Temalı ikon (API 33+) — monochrome katmanı

Temalı ikonlar **yalnızca ana ekranda** uygulanıyor; uygulama çekmecesinde
ikonlar normal kalıyor. Yani önce uygulamayı ana ekrana taşımak gerekiyor.

`google_apis_playstore` imajları **root kabul etmiyor**, bu yüzden
Launcher3'ün `themed_icons` tercihini dosyadan yazmak mümkün değil; ayar
arayüzden açılıyor:

- **API 34:** ana ekranda uzun basış → *Wallpaper & style* → aşağı kaydır →
  **Themed icons** anahtarı.
- **API 36:** ana ekranda uzun basış → *Wallpaper & style* → **Icons** →
  **Themed icons** anahtarı → **Apply**.

Uygulamayı çekmeceden ana ekrana sürüklemek `input swipe` ile olmuyor
(kaydırma sanılıyor); `input motionevent` ile basıp bekleyip taşımak gerekiyor:

```bash
adb shell "input motionevent DOWN 158 2040; sleep 1; \
  input motionevent MOVE 220 1750; input motionevent MOVE 350 1350; \
  input motionevent MOVE 450 1000; sleep 1; input motionevent UP 450 1000"
```

**Ne aranıyor:** paraların arasındaki ayrımların **hâlâ görünüyor** olması.
Kontrol sayısaldır: para halkasının üstünde bir daire boyunca dolaşıp koyu
koşuları sayın, **12** çıkmalı. Ayrım boyanmış olsaydı sistem onu paralarla
aynı renge boyar ve sayı 0 olurdu.

### Bildirim ikonu — durum çubuğunda

Bildirim, günlük işin gövdesini çalıştıran enstrümantasyon testiyle
gönderiliyor:

```bash
adb shell am instrument -w \
  -e class 'com.elinacn.subtrack.reminder.PaymentReminderWorkerTest#reminderWorker_datedSubscriptions_notifiesOnlyTheOnesInsideTheWindow' \
  com.elinacn.subtrack.test/androidx.test.runner.AndroidJUnitRunner
```

> **`./gradlew :app:connectedDebugAndroidTest` bunun yerine geçmiyor.** Gradle
> tur bitince iki APK'yı da **kaldırıyor**, paketle birlikte bildirim de
> gidiyor; ekran görüntüsü alınacak bir şey kalmıyor. İki APK'yı elle kurup
> `am instrument` çağırmak gerekiyor. Bildirimin durduğu
> `adb shell cmd notification list | grep subtrack` ile doğrulanır.

Durum çubuğu ikonu **24dp tuvalinden küçük** çiziyor — ölçülen: api24'te
15,00dp, api34'te 13,71dp, api36'da 12,95dp. Ayrımın bu kadar geniş olmasının
sebebi bu. Kontrol yine sayma: on iki koyu koşu.

| Cihaz | işaret | ayrım genişliği |
|---|---|---|
| api24 (density 2,0) | 15,00dp | 2,06–2,14 px |
| api34 (420dpi) | 13,71dp | 0,49–1,89 px |
| api36 (420dpi) | 12,95dp | 1,43–1,83 px |

> **API 36'nın durum çubuğu açık zeminli, ikonlar beyaz.** Basit bir parlaklık
> eşiği bütün arka planı ikon sanıyor; `min(r,g,b) > 240` ile beyaza yakın
> pikselleri ayırın.

---

## Faza Özel Testler

Sabit liste geçtikten sonra çalıştırılır. Claude Code her faz sonunda bu
bölümü doldurur; test bitince buradaki maddeler silinir, kalıcı hale gelen
davranışlar yukarıdaki sabit listeye taşınır.

**Format:**

```markdown
### [Faz N] — YYYY-AA-GG

a) Ne yapılacak → ne beklenmeli
b) ...

Kritik adım: (hangisi bu fazın asıl sınavı ve neden)
Bilinen eksik: (bu fazda kasıtlı olarak yapılmayan, hata sanılmaması gereken şey)
```

**Bir maddenin sabit listeye taşınma ölçütü:** davranış artık o fazın değil,
uygulamanın kalıcı bir özelliği ise ve sonraki fazlarda bozulabilecekse.

### (şu an boş — bir sonraki faz sonunda doldurulacak)

Faz 13b'nin maddeleri doğrudan sabit listeye (86-95) yazıldı: hepsi kalıcı
davranış ve hepsi sonraki fazlarda bozulabilir.
