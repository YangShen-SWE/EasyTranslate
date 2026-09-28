# Floating window assets

- `cat.png`: minimal outlined cat; generated specifically for this project with OpenAI ImageGen.
- `desk.png`: mint succulent and coffee mug; generated specifically for this project with OpenAI ImageGen.
- Images have transparent outer backgrounds and graphite interiors so they remain visible over light applications.
- `FloatingViewController` uses an ImageView viewport to remove generation padding without modifying the source image.
- UI icons in `../icons` are unmodified SVGs from https://github.com/lucide-icons/lucide/tree/main/icons (retrieved 2026-09-28); their ISC and inherited MIT license notices are preserved in `../icons/LICENSE.txt`.

`CompanionMotion` splits these same PNGs into ImageView viewports for independent paw and steam animation. The head, plant and cup stay still; the source PNGs are unchanged. Turning effects off restores the resting artwork.
