# Image Processor Implementation Plan

> **For agentic workers:** Execute inline in this session. Spec: `docs/image-processor-design.md` P0.

**Goal:** Ship the P0 image workbench: console entry, three-pane UI, backend sessions, Docker Magick + ExifTool, JVM fallback.

**Architecture:** New Maven module `image-core` with identify / metadata / transform facades. Docker runtime prefers Magick + ExifTool; unit tests use ImageIO + metadata-extractor. Frontend is a single-route workbench at `/image`.

**Tech Stack:** Spring Boot 3.4, ImageIO + TwelveMonkeys, metadata-extractor, Tika core, ProcessBuilder, Vue 3, Naive UI, cropperjs.

## Global Constraints

- Greenfield: no DB table, no legacy fields, temp-dir sessions with 30m TTL.
- Public API prefix `/api/image` (same style as file-parser / docgen); do not hang under `/api/file-parser`.
- OCR stays in file-parser; this module does pixels + metadata only.
- Session ids are unguessable ULIDs.
- P0 formats: input JPEG/PNG/WebP/GIF/BMP/HEIC/TIFF; output JPEG/PNG/WebP.
- max-file-size 50MB, max-pixels 40_000_000, convert is synchronous.
- Magick policy must deny PDF/HTTP/MVG; Alpine packages include HEIC + ExifTool + Perl.
- Frontend: local object URL preview first; HEIC uses server preview; GPS strip default-on when GPS present.
