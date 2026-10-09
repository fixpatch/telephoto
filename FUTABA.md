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
- `zoomable`: `ZoomSpec` の最小・最大で丸めない `ZoomableState.zoomToIgnoringLimits()` を追加
  (FutabaViewer の「原寸表示」用。範囲は変えないので、次のピンチやホイールで範囲内へ戻る)
- `flick`: `rememberFlickToDismissState(..., downwardOnly = true)` で下方向だけのスワイプ終了を選べる
  (既定は `false` で従来どおり上下両方向)。上方向から始まるドラッグは取得・消費せず、
  画像のパン・ズームへ渡す。下方向の終了ドラッグを開始した後は原点まで逆戻りできるが、
  ユーザー操作中の Y オフセットは負にならない。距離しきい値・速度による終了判定も下方向だけに制限する。
  しきい値前後の spring による抵抗切り替え・往復時の触覚フィードバックは変更しない。

## FutabaViewer への同梱

ビルドツールの版(AGP 8.13 / Kotlin 2.1)が FutabaViewer(AGP 9.2 / Kotlin 2.4)と違い、composite build
では Android 側が解決できないため、公開物を FutabaViewer のリポジトリへ同梱している。

    scripts/publish-futaba.sh ../futabaviewer/third_party/telephoto-maven 0.20.0-futaba.N

## ネイティブの部分デコーダ

Skia 版の `ImageRegionDecoder` は画像全体を 1/n にデコードした段から切り出すので、原寸の段は
`SkikoSubSamplingDefaults.maxDecodedPixels` に収まる画像しか持てない。`SkikoSubSamplingDefaults.regionDecoderProviders`
にアプリが部分デコーダを登録すると、Skia 版より先に使われる。FutabaViewer は libjpeg-turbo / libpng による
C ライブラリ(FutabaViewer の native/fvregiondecoder)で JPEG / PNG を扱う。
