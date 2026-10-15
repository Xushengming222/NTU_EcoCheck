# NTU 电费查询与缴费增强版

![应用图标](docs/branding/icon-circle-192.png)

基于 **[UWPBOOM/NTU_EcoCheck](https://github.com/UWPBOOM/NTU_EcoCheck)** 开发，原项目及宿舍基础数据归原作者贡献。本增强版由 **[Xushengming222](https://github.com/Xushengming222)** 维护，增加网页版缴费、登录验证保护、手机支付适配和原创图标。非学校官方应用。

**[下载 APK / 查看版本](https://github.com/Xushengming222/NTU_EcoCheck/releases)** · [完整修改说明](CHANGES.md) · [原项目 README](docs/upstream-README.md)

## 相比参考项目改了什么

| 功能 | 参考项目 | 本增强版 |
| --- | --- | --- |
| 查询 UI 与历史 | 居中弹窗、五步进度、电量结果与历史 | 保留正常查询体验 |
| 一键缴费 | 无首页缴费入口 | 新增提示弹窗、学校官方网页版缴费和宿舍自动填写 |
| 滑块及登录错误 | 可能因重复提交导致频繁登录 | 检测后停止自动提交，提示用户人工处理，并限制自动提交频率 |
| 手机缴费页面 | 桌面布局可能缩小或截断 | 适配金额输入、支付方式、蓝色确认按钮和二维码 |
| 支付码处理 | 无 | 本机识别、保存付款码，打开对应平台继续扫码 |
| 图标与授权介绍 | 原版闪电图标及许可入口 | 原创图标、原作者署名、增强版介绍、离线 Apache 2.0 正文和 NOTICE |

## 使用

1. 填写南通大学统一身份认证账号、密码，选择校区、楼栋、房间。
2. 点击“一键查询”查看电量；正常流程保持原版弹窗与结果样式。
3. 点击“一键缴费”，先阅读提示，点击“已了解，继续缴费”后进入学校网页版平台。
4. 核对自动填写的宿舍信息，再自行输入金额、选择支付方式、确认订单。
5. 将付款码保存至相册，在对应的微信或支付宝“扫一扫”中从相册选择图片付款；支付结果以官方订单为准。

出现滑块、验证码或登录错误时按官方提示人工完成。应用不会自动解验证码或持续重复提交。此工具仅适配南通大学的认证和缴费系统，其他学校无法直接使用。

## 安装与验证

最低 Android 8.0。应用名称为“NTU电费查询”，版本号为 `1.0.9`，不附加测试或构建后缀。包名 `com.ntu.electricity.payment`，可与原版共存，并可覆盖此前同签名的增强版以保留配置。

本地构建、表单与布局测试、JVM 单元测试已执行。开发环境未使用真实学校账号创建或支付订单，也未完成 Android 真机上的完整支付链路验收。发布说明会列出验证范围。

## 构建

使用 JDK 17+、Android SDK 34、Gradle Wrapper。用 Android Studio 打开本仓库，配置 SDK 后执行：

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

网页流程测试：

```powershell
cd tools/payment-tests
npm ci --ignore-scripts
npm test
```

实现细节见 [缴费集成说明](docs/payment-integration.md)，图标来源和生成提示词见 [图标设计](docs/icon-design.md)。发布包使用增强版自己的测试签名，不使用参考项目的签名密钥。

## License

按参考项目应用内声明的 **Apache License 2.0** 分发，完整正文见 [LICENSE](LICENSE)，署名见 [NOTICE](NOTICE)。原作者 UWPBOOM 和参考项目链接保留；修改过的代码文件注明维护者与变更，详细差异见 [CHANGES.md](CHANGES.md)。

参考 README 的“仅供南通大学校内使用”说明本应用只适配该校系统，不增加与 Apache 2.0 相冲突的再分发限制。原 README 原文保存在 [docs/upstream-README.md](docs/upstream-README.md)。
