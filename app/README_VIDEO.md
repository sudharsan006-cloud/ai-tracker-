# Video Onboarding Preparation Guide

For smooth scrubbing in the Flutter Web onboarding, the video file must be prepared with keyframe on every single frame and compressed to under 15 MB.

### Video Placement:
Place the output file at:
`app/web/videos/onboarding.mp4`

> **IMPORTANT**: Do NOT add this file to `pubspec.yaml` `assets:`. Files in `web/` are automatically served only by Flutter Web and will NOT be included in Android APK builds.

### Encoding Command:
Run the following ffmpeg command:

```bash
ffmpeg -i input.mp4 -an -vf "scale=1280:-2" -c:v libx264 -g 1 -keyint_min 1 -crf 26 -preset slow -pix_fmt yuv420p -movflags +faststart onboarding.mp4
```

If the file is over 15 MB, raise `-crf` to 29 or reduce scale to `960:-2`:

```bash
ffmpeg -i input.mp4 -an -vf "scale=960:-2" -c:v libx264 -g 1 -keyint_min 1 -crf 29 -preset slow -pix_fmt yuv420p -movflags +faststart onboarding.mp4
```
