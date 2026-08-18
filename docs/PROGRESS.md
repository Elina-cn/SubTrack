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

## [Faz 0] Proje Altyapısı ve Belgelendirme — 2026-08-18

**Durum:** Devam ediyor

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
