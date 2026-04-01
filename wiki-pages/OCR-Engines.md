# OCR Engines

![New](https://img.shields.io/badge/type-new%20feature-brightgreen?style=for-the-badge)
![PaddleOCR](https://img.shields.io/badge/PaddleOCR-primary-blue?style=for-the-badge)
![Tesseract](https://img.shields.io/badge/Tesseract-fallback-grey?style=for-the-badge)

> OculiX introduces a pluggable OCR architecture with PaddleOCR as primary engine and Tesseract as fallback.

---

## Architecture

```
OCREngine (interface)
  ├── PaddleOCREngine   → HTTP client, zero external deps
  │     └── PaddleOCRClient  → JSON parsing, connection handling (629 lines)
  └── TesseractEngine   → Tess4J wrapper (247 lines)
```

Auto-detection: PaddleOCR is tried first. If unavailable, falls back to Tesseract.

## PaddleOCR Integration

| Component | Description |
|-----------|-------------|
| `PaddleOCREngine` | Engine adapter (629 lines) |
| `PaddleOCRClient` | Zero-dependency HTTP client with manual JSON parsing |
| Protocol | HTTP REST API to a PaddleOCR server |

## Utilities

| Class | Purpose |
|-------|---------|
| `AmountVariantGenerator` | Generates tolerance variants for monetary formats (e.g. `1,234.56` ↔ `1234.56` ↔ `1 234,56`) |
| `TextNormalizer` | Accent stripping, case-insensitive comparison, whitespace normalization |

## Key Classes

All in `com.sikulix.ocr.*`:

| File | Lines | Purpose |
|------|-------|---------|
| `OCREngine.java` | — | Interface for pluggable engines |
| `PaddleOCREngine.java` | 629 | PaddleOCR implementation |
| `PaddleOCRClient.java` | 629 | HTTP client, zero-dep |
| `TesseractEngine.java` | 247 | Tesseract fallback |
| `AmountVariantGenerator.java` | 93 | Monetary format tolerance |
| `TextNormalizer.java` | 77 | Text normalization |
