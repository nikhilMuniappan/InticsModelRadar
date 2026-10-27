-- V5 — mark re-uploads (quantizations / format conversions / mirrors).
--
-- Hugging Face is full of accounts that re-publish other people's models in another
-- format. Counting them as new releases inflates the release-momentum chart and lets
-- a prolific quantizer outrank the labs actually shipping models, so analytics need
-- to be able to exclude them.
--
-- The flag is maintained in Java at sync time (DerivativeClassifier, driven by
-- model-radar.quality in application.yml). This migration adds the column and
-- backfills the rows we already hold, using the same rules expressed in SQL, so the
-- dashboard is correct immediately rather than after the next sync.

ALTER TABLE tracked_models
    ADD COLUMN derivative BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill: known mirror accounts...
UPDATE tracked_models
SET derivative = TRUE
WHERE lower(author) IN (
    'mradermacher', 'thebloke', 'bartowski', 'quantfactory', 'lonestriker',
    'richarderkhov', 'mlx-community', 'second-state', 'tensorblock', 'devquasar',
    'nm-testing', 'lewdiculous', 'featherless-ai-quantized'
);

-- ...and ids naming a quantization or conversion format. Matched against the repo
-- name only (the part after "author/") so an org name can't condemn every model.
UPDATE tracked_models
SET derivative = TRUE
WHERE derivative = FALSE
  AND regexp_replace(id, '^[^/]*/', '') ~* (
      '(^|[-_.])gguf([-_.]|$)'
      || '|(^|[-_.])ggml([-_.]|$)'
      || '|(^|[-_.])awq([-_.]|$)'
      || '|(^|[-_.])gptq([-_.]|$)'
      || '|(^|[-_.])exl2([-_.]|$)|exllama'
      || '|(^|[-_.])mlx([-_.]|$)'
      || '|imatrix'
      || '|(^|[-_.])i1[-_.]'
      || '|(^|[-_.])[2-8]bit([-_.]|$)'
      || '|(^|[-_.])(int4|int8|fp8|fp4|nf4|w8a8|w4a16|w8a16)([-_.]|$)'
      || '|(^|[-_.])bnb([-_.]|$)|bitsandbytes'
      || '|quantiz'
      || '|(^|[-_.])q[2-8]_[0-9kmsl]'
  );

-- Analytics filter on this constantly; keep the scans cheap.
CREATE INDEX idx_tracked_models_derivative ON tracked_models (derivative);
CREATE INDEX idx_tracked_models_created_at ON tracked_models (created_at);
