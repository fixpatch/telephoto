# FutabaViewer 向け fork

[saket/telephoto](https://github.com/saket/telephoto) の trunk(0.19.0 の後)から分岐し、
`SubSamplingImage()` / `ZoomableImage()` / Coil 3 連携を Android 以外(desktop・iOS・web)でも
使えるようにしたもの。ブランチは `futaba`。

## 変更点

- `sub-sampling-image`: `SubSamplingImage` 本体を commonMain へ移した。Android 固有の部分は expect/actual
- `sub-sampling-image`: Skia(skiko)用の `ImageRegionDecoder` を追加(skikoMain = desktop/iOS/web)
  - skiko の `Codec` は部分デコードを持たないので、画像全体を 1/n にデコードした「段」からタイルを切り出す
  - JPEG は 1/2・1/4・1/8、WebP は任意の大きさへデコード段階で縮める(PNG 等は原寸で一度デコード)
  - 保持する画素数は `maxDecodedPixels`(既定 4096x4096)で上限を掛ける
  - EXIF の向きは段を作る時に適用する
- `zoomable-image:core` / `zoomable-image:coil3`: KMP 化
- `zoomable-image:coil3`: 画像から Painter を作る処理を差し替えられる
  `ZoomableImageSource.coil(model, imageLoader, painterFactory)` を追加

## FutabaViewer への同梱

ビルドツールの版(AGP 8.13 / Kotlin 2.1)が FutabaViewer(AGP 9.2 / Kotlin 2.4)と違い、composite build
では Android 側が解決できないため、公開物を FutabaViewer のリポジトリへ同梱している。

    scripts/publish-futaba.sh ../futabaviewer/third_party/telephoto-maven 0.20.0-futaba.N

## ネイティブの部分デコーダ

Skia 版の `ImageRegionDecoder` は画像全体を 1/n にデコードした段から切り出すので、原寸の段は
`SkikoSubSamplingDefaults.maxDecodedPixels` に収まる画像しか持てない。`SkikoSubSamplingDefaults.regionDecoderProviders`
にアプリが部分デコーダを登録すると、Skia 版より先に使われる。FutabaViewer は libjpeg-turbo / libpng による
C ライブラリ(FutabaViewer の native/fvregiondecoder)で JPEG / PNG を扱う。
