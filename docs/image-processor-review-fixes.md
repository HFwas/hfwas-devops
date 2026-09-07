# 图片处理模块：评审问题与修复

对照 `941e559` 代码评审。本文是实施清单；修完后状态见文末。

约定：geometry / crop 以 **摆正后的像素** 为准（与 `docs/image-processor-design.md` 一致）。服务端预览最长边 2048，裁剪框在预览像素里，转换必须映射回原图像素。

## Critical

### C1. Cropper 默认整框 + 预览坐标系当原图裁

- **现象：** `autoCropArea: 1` 且 `ready()` 立刻 `emit`。`buildRequest` 只要 `width > 0` 就带 crop。服务端预览最长边 2048，crop 却作用在原图像素上。未动手裁切时，HEIC / TIFF / Orientation≠1 的 JPEG 会被切成左上角一块。
- **位置：** `frontend/src/modules/image/components/ImageCanvas.vue`、`useImageSession.ts` `buildRequest`；后端 Magick `-crop` / `GeometryOps.applyGeometry`。
- **修复：**
  1. 裁剪框约等于当前画布图（容差 2px）→ **不传 crop**。
  2. 真实裁切：按 `orientedSize / displayedSize` 放大到摆正后的原图像素；用户旋转 90/270 时先交换宽高再映射。
  3. 会话 VO 下发 `orientedWidth` / `orientedHeight` / `previewWidth` / `previewHeight`，避免前端猜 Orientation。
  4. 回归：服务端预览会话 + Cropper 默认整框，导出尺寸等于摆正后的原图（可再套 maxSide），不得变成预览尺寸。

## Important

### I1. `hasAlpha` 写死 false

- **位置：** `ImageMetadataService.read` 把 `hasAlpha` 传 `false`；identify 的 `Dimensions` 未带 alpha。
- **修复：** ImageIO 从 `ColorModel.hasAlpha()` 读取；Magick identify 增加 `%A`。结果写入 `IdentifyResult` / `ImageSession`，元数据读取使用该值。

### I2. Magick `time` 策略 30s vs convert-timeout 60s

- **位置：** `backend/image-core/src/main/resources/imagemagick/policy.xml` `resource name="time"`。
- **修复：** `time` 改为 60，与 `image-processor.engines.convert-timeout` 对齐。

### I3. 无会话级 convert 锁；TTL 与 job 竞态

- **现象：** 同一 session 连续 convert 会抢 `result` / `proc-out`；过期驱逐会删目录，后台 job 仍在写。
- **修复：** 每 session 一把锁包住 `convertSync`；驱逐/删除先 `cancelled`，锁内再删目录；`ImageConvertJobService.evictSession` 把 queued/running 标为 failed（「会话不存在或已过期」），供轮询拿到失败而不是 404。

### I4. 队列探测 `Promise.all` vs `maxConcurrent: 2`

- **位置：** `useImageSession.addFiles` 对所有上传并行 `probeItem`。
- **修复：** `mapPool(..., 2)`，与后端 `maxConcurrent` 同量级。

### I5. Magick `runUnlocked` 无测试

- **位置：** `ImageMagickTransformer.readSize` 必须走 `runUnlocked`，否则 Semaphore 重入死锁。
- **修复：** Mock `NativeProcessRunner`：convert 先 `run` 再 `runUnlocked`。另测 `runUnlocked` 在 limiter 已被占满时仍能执行。

### I6. HTTP / limiter / storage / job 测试缺口

- **修复：** MockMvc 测 health；`ImageWorkLimiter` 许可为 1 时第二任务等待；`sanitizeExt` / 路径穿越；job 驱逐标 failed；`waitForConvert` 超时（前端已有失败路径，补 timeout）。

### I7. `batchConvertImages` 死代码

- **原因：** 批量转换每张图 geometry 不同，不能共用一个 `batch-convert` body。
- **修复：** 删除前端未使用的 `batchConvertImages`；后端 batch API 保留给同参数多 session。

## Minor

### M1. `Ulids.encode` 残留思考注释

- **修复：** 删掉无意义注释。

### M2. ImageCanvas 每次旋转把整图重编码为 JPEG data URL

- **修复：** 画布最长边限制 2048；无旋转/翻转且已小于上限时直接用 `src`，避免二次 JPEG。Cropper 坐标始终带 `displayedWidth/Height`，由 C1 放大回原图。

### M3. 历史忽略 tenant；`require()` 不校验 userId

- **修复：** `listRecent` 在当前租户非空时按 `tenantId` 过滤；`require()` 在 session 与当前用户都绑定了 userId（及 tenantId）时不一致则 404。匿名 / 测试 `CurrentUserAccessor == null` 仍跳过。

### M4. `frontend/tsconfig.tsbuildinfo` 不应当作源码

- **修复：** `frontend/.gitignore` 加入 `*.tsbuildinfo`。

### M5. `ImageIoTransformerMemoryTest` 只断言 subsample 算术

- **修复：** 增加真实 preview 写出，断言输出边长被压到 maxSide，而不仅是因子计算。

## 注释（评审：过薄）

短 Javadoc，不叙述过程：

- `ImageProcessorController`：模块职责、`/api/image` 前缀。
- `ImageSessionService`：`shouldAsync`、`generatePreview`、`require`。
- `ImageMagickTransformer.readSize` / `runUnlocked`：为何不能再 `run()`。
- `ImageCanvas.vue`：crop 是当前画布像素，转换前必须映射到摆正后的原图。

## 实施顺序

1. C1 裁剪映射 + 回归测试  
2. I1 alpha、I2 policy  
3. I3 session 锁 / job 驱逐  
4. I4 探测并发、I7 删死代码  
5. M2 画布、注释、M1 Ulids  
6. M3 隔离、M4 gitignore  
7. I5 / I6 / M5 测试  

## 状态

已全部落地（2026-09-07）：

| ID | 修复 |
|---|---|
| C1 | `cropForConvert` 丢弃整框；预览像素按 `orientedWidth/Height` 放大；VO 下发 oriented/preview 尺寸 |
| I1 | identify 读 alpha，写入 session / metadata |
| I2 | Magick policy `time` = 60 |
| I3 | session convert 锁；驱逐标 cancelled；job 不覆盖 failed |
| I4 | `mapPool(probe, 2)` |
| I5 | Magick convert 测 `run` + `runUnlocked(identify)` |
| I6 | MockMvc health、limiter、storage、job、NativeProcessRunner、waitForConvert 超时 |
| I7 | 删除未使用的 `batchConvertImages` |
| M1 | 去掉 Ulids 残留注释与死代码 |
| M2 | 画布最长边 2048；无旋转且已小于上限不二次 JPEG |
| M3 | history 按 tenant；`require` 校验 user/tenant |
| M4 | `frontend/.gitignore` 加入 `*.tsbuildinfo`（已跟踪文件下次提交时 `git rm --cached`） |
| M5 | ImageIO preview 写出断言输出边长 |
| 注释 | Controller / SessionService / Magick `readSize` / ImageCanvas |

