# TESTING.md — Elle Test Listesi

Her faz sonunda **önce** aşağıdaki sabit liste çalıştırılır, **sonra** Claude
Code'un o faz için söylediği ek adımlar.

Her maddenin yanındaki faz, o davranışın hangi fazda kazanıldığını gösterir.
Bir madde bozulursa önce oraya bakılır — hangi kodun bu davranışı ürettiği
oradan bulunur.

---

## Sabit Regresyon Listesi

Sırayla ve tek oturumda çalıştırılır. Bir madde kalırsa sonrakilere devam
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
| 15 | Sistem temasını koyuya al | Tüm metinler okunabilir, kartlar arka plandan ayrışıyor | 1b · kontrast 1c |
| 16 | Cihaz dilini İngilizceye al | Metinler çevrilmiş geliyor | 1b |
| 17 | Boş ad veya geçersiz fiyatla Kaydet'e bas | Sheet **açık kalıyor**, hata ilgili alanın **altında** | 6 |
| 18 | Bir satırı sil, Snackbar'a dokunma | Birkaç saniyede **kendiliğinden** kayboluyor | 6 |
| 19 | Sil, sonra "Geri al"a bas | Öğe **eski sırasına** dönüyor | 6 |

**Not — beklenen davranışlar, hata değil:**
- İlk kurulumda liste **boş** başlar. Seed veri yok; boş durum ekranı Faz 8'de.
- Ardışık silmede **yalnızca son işlem** geri alınabilir; tek undo izleniyor.

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
