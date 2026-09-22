# CLAUDE.md — SubTrack

Bu dosya Claude Code'un her oturumda uyması gereken kuralları içerir.
Bir talimat bu dosyayla çelişirse, dur ve kullanıcıya sor.

---

## 1. Proje Kimliği

- **Uygulama:** SubTrack — aylık/yıllık abonelik takip uygulaması
- **Paket:** `com.elinacn.subtrack`
- **Platform:** Android, Jetpack Compose, Kotlin
- **Hedef:** Google Play Store'da yayınlanacak gerçek bir ürün
- **Ayrıntılı kapsam:** `docs/PROJECT_SPEC.md`
- **Mimari kararlar:** `docs/ARCHITECTURE.md`
- **Görev sırası:** `docs/ROADMAP.md`

---

## 2. İletişim Kuralları

- **Konuşma dili: Türkçe.** Açıklamalar, özetler, sorular — hepsi Türkçe.
- **Kod dili: İngilizce.** Sınıf/fonksiyon/değişken isimleri, kod içi yorumlar,
  commit mesajları, dosya isimleri — hepsi İngilizce.
- **Kullanıcıya görünen metinler Türkçe**, ama koda gömülmez → `strings.xml`.

### Açıklama üslubu

Kod yazmadan önce **kısa** bir açıklama yap:
- Ne yapacaksın (1-2 cümle)
- Neden bu yaklaşım (1-2 cümle)
- Yeni bir kavram giriyorsa (Flow, Hilt, StateFlow vb.) 2-3 cümlelik tanım

**Detaya girme.** Uzun teori anlatma, alternatiflerin karşılaştırmasını yapma,
madde madde ders verme. Kullanıcı bu kavramlarla ilk kez karşılaşıyor;
kısa ve somut açıklama işe yarar, uzun açıklama yormaz.

Kötü: "Coroutines, Kotlin'in yapılandırılmış eşzamanlılık modelidir. Suspend
fonksiyonlar... [15 satır]"

İyi: "`suspend` işareti, bu fonksiyonun arka planda çalışıp bittiğinde geri
döneceğini söyler — böylece veritabanı yazarken arayüz donmaz."

---

## 3. Çalışma Şekli

- **Planlama sohbette yapılır, uygulama sende.** Sana gelen prompt zaten
  planlanmıştır. Onay bekleme, doğrudan uygula.
- **İstisna:** Prompt belirsizse, `docs/` altındaki belgelerle çelişiyorsa
  veya belgelerde karşılığı olmayan bir mimari karar gerektiriyorsa
  **dur ve sor.** Kendi kafana göre mimari karar alma.
- **Kapsam dışına çıkma.** Promptta istenmeyen dosyaya dokunma, "bu arada
  şunu da düzelttim" yapma. Fark ettiğin sorunları raporla, düzeltme.
- Her görev sonunda **1-3 cümlelik özet** ver: ne değişti, hangi dosyalar.

---

## 4. Kod Standartları

### Genel
- Kotlin resmi stil rehberi. `internal`/`private` görünürlüğü cömertçe kullan.
- Bir dosya = bir sorumluluk. 300 satırı geçen dosyayı böl.
- Composable fonksiyonlar `PascalCase`, `Modifier` her zaman ilk opsiyonel parametre.
- Deprecated API kullanma. Zorunluysa nedenini yorum olarak yaz.

### Yasaklar
- ❌ `!!` operatörü
- ❌ Hardcoded kullanıcı metni (mutlaka `stringResource`)
- ❌ Hardcoded renk (mutlaka `MaterialTheme.colorScheme`)
- ❌ Hardcoded boyut/spacing (ortak `Dimens` nesnesi)
- ❌ Para için `Double` veya `Float` (kuruş cinsinden `Long` kullan)
- ❌ Composable içinde iş mantığı, hesaplama veya veri dönüşümü
- ❌ Composable içinde `ViewModel` dışı state sahipliği
- ❌ `GlobalScope`
- ❌ Main thread'de I/O

### Zorunluluklar
- ✅ Room için **KSP** (kapt değil)
- ✅ Bağımlılıklar `gradle/libs.versions.toml` üzerinden
- ✅ Flow → UI dönüşümü `stateIn` + `collectAsStateWithLifecycle`
- ✅ Her ekranın tek bir `UiState` data class'ı
- ✅ ViewModel dışarıya `StateFlow` verir, `MutableStateFlow` sızdırmaz
- ✅ Yeni public sınıf/fonksiyon → tek satırlık KDoc

### Gizli bilgi

**Gizli bilgi repoya girmez.** Şifre, anahtar, token ve API anahtarı
`local.properties`'ten veya ortam değişkeninden okunur; koda yazılmaz ve
commit edilmez. Bu kural v1.1'de döviz kuru servisi eklenirken de geçerlidir.

---

## 5. Git Kuralları

- Küçük adımlarda birkaç adımda bir, büyük/riskli adımlardan sonra **mutlaka** commit.
- Conventional Commits, İngilizce, imperative:
  `feat:` `fix:` `refactor:` `chore:` `docs:` `test:` `build:`
  - Örnek: `fix: close bottom sheet after saving subscription`
  - Örnek: `feat: add Room database with subscription entity`
- İçerik taşımayan commit atma (boş yorum satırı ekle/sil gibi).
- `git add .` yerine ilgili dosyaları açıkça ekle.
- **Asla** `git push --force`, `git reset --hard`, branch silme yapma — sorulmadıkça.
- **Branch açma.** Tüm commit'ler `Elina` branch'ine gider.
- Faz sonu tag'ini (`git tag phase-N-done`) **KULLANICI atar. Sen tag atmazsın,
  prompt istese bile.** Branch yok; tag'ler tek geri dönüş noktası ve o noktayı
  kodu yazan değil, test edip kabul eden işaretler.
- Tag eklemek geçmişi değiştirmez; tag **silme/taşıma** yine sorulmadan yapılmaz.

---

## 6. Doğrulama

Her kod değişikliğinden sonra, bildirmeden önce:

1. `./gradlew :app:assembleDebug` çalıştır
2. Kotlin görevi `UP-TO-DATE` gelirse **güvenme** — `--rerun-tasks` ile zorla
3. Yeni uyarı çıktıysa raporla
4. Test varsa `./gradlew :app:testDebugUnitTest`
5. `git diff`'te `app/schemas/1.json` değişmiş görünüyor ve `version` artmamışsa
   **dur** — migration kuralı ihlal edilmiş demektir (gerekçe:
   `docs/ARCHITECTURE.md` "Şema sürümlemesi")
6. Fiziksel test cihazına dokunma: kurulum, kaldırma, `pm clear`, test koşumu
   yapma — cihazdaki sürüm Play'in imzasını taşır ve yalnızca dahili test
   kanalından güncellenir (gerekçe: `docs/ARCHITECTURE.md` "Şema sürümlemesi")

Derleme geçmesi doğruluk kanıtı değildir. Çalışma zamanı davranışını
(state akışı, lifecycle, boş liste durumu) mantıken kontrol et ve
kullanıcıya elle test etmesi gereken adımları söyle.

---

## 7. Belge Bakımı

- Faz bitince `docs/PROGRESS.md` dosyasına kayıt ekle (format dosyanın içinde).
- Mimari bir karar değişirse `docs/ARCHITECTURE.md` güncellensin.
- `docs/ROADMAP.md`'de tamamlanan maddeleri işaretle.
- `docs/archive/` altındaki dosyalar tarihsel kayıttır — **değiştirme.**
- Belgeleri kendiliğinden yeniden yazma; sadece güncelle.

---

## 8. Dürüstlük

- Emin olmadığın şeyi "emin değilim" diye işaretle.
- Bir şey çalışmıyorsa çalışıyormuş gibi rapor etme.
- Kullanıcının isteği hatalıysa veya belgelerle çelişiyorsa **söyle**,
  sessizce uygulama.
- "Tamamlandı" demeden önce gerçekten tamamlandığından emin ol.

---

## 9. UI UX Pro Max skill kullanımı

Kurulum: `~/.claude/skills/ui-ux-pro-max/` (kullanıcı düzeyi, projede değil).
Script tam yolla çağrılır, `${CLAUDE_PLUGIN_ROOT}` yoktur.

**Kullanılacak sorgular:**
- `--domain ux` — erişilebilirlik, dokunma alanı, form, navigasyon,
  boş durum, hata metni, anti-pattern taraması
- `--stack jetpack-compose` — stack'e özel öneriler
- `--domain icons` — ikon erişilebilirliği

**YASAK: `--design-system` modu.** Palet, tipografi ve efekt üretir.
Bunlar `docs/ARCHITECTURE.md`'de karara bağlandı ve kontrast
değerleriyle ölçüldü (primary = Emerald #0B5C3F açık temada, Gold
#D4AF37 koyu temada; primaryContainer = #CDE8DA açık temada).
`--persist` da kullanılmaz.

Skill'den somut renk veya font önerisi gelirse **uygulama**, sohbete ilet.
Yapısal kurallar (semantic token kullanımı, satır yüksekliği oranları,
dokunma alanı ölçüleri) uygulanabilir.

Skill'in kendi kuralı da bunu destekliyor: çıktısı tavsiyedir, repo
kurallarını ezmez.
