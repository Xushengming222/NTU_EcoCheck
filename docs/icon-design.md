# 1.0.7 原创图标

主图把展开的书页与中间的闪电形留白结合，青色开放圆环表示电流与校园徽章的意象，底色为 `#0052D9`。这是学生工具的原创图形，未复制学校官方校徽、校名或建筑轮廓。

使用内置 `image_gen` 生成透明 PNG，未使用 CLI/API 回退模式。生成源图保存在 [branding/icon-generated.png](branding/icon-generated.png)。仅通过缩放和居中放置，转换为保留 alpha 的 Android 自适应前景，未用脚本重画图形。

- 应用资源：[../app/src/main/res/drawable-nodpi/ic_launcher_mark.png](../app/src/main/res/drawable-nodpi/ic_launcher_mark.png)，432×432 PNG。
- 高分辨率透明前景：[branding/icon-foreground.png](branding/icon-foreground.png)。
- 蓝底预览：[branding/icon-preview-512.png](branding/icon-preview-512.png)。
- 圆形遮罩预览：[branding/icon-circle-512.png](branding/icon-circle-512.png)。
- 生成及尺寸记录：[branding/icon-assets.json](branding/icon-assets.json)。

可见图形限制在 108dp 前景中央约 60% 区域，供圆形和圆角方形启动器遮罩裁切。Android 13 主题图标使用同一前景的 alpha 形状。

## 最终生成提示词

```text
Use case: logo-brand.
Asset type: production Android adaptive app icon foreground, 1024 by 1024 PNG with genuinely transparent background.
Primary request: design a distinctive original icon for a Nantong University student dormitory electricity balance and payment companion.
Subject: a single bold white emblem combining an open academic book and a dynamic lightning bolt in its central negative-space fold; a restrained cyan open circular orbit around the book suggests a university crest and flowing electricity. Unified, beautifully balanced silhouette, strong recognition at 48px.
Style: premium flat graphic design, crisp vector-like edges, confident geometric construction, slight rounded corners, simple and memorable.
Palette: ivory white main mark with a small electric cyan accent (#70E5FF). It will be placed on a solid saturated university-blue background (#0052D9) by Android.
Composition: centered symmetrical book with a slight dynamic lightning gesture; ALL visible artwork must fit inside the central 60% of the square canvas (leaving at least 20% fully transparent margin on every edge), suitable for Android circle and squircle adaptive masks. Occupy that safe central area generously.
Text: none. No letters, numbers, words, tiny inscriptions, labels or watermark.
Constraints: original student utility identity; use only abstract academic visual language, do not reproduce any actual university seal, official logo, institutional name, coat of arms or building silhouette. No blue tile behind the artwork, no opaque background, no shadows outside the mark, no mockup, no phone, no multiple options. One finished icon foreground only.
```
