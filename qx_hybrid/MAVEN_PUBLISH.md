# QX Hybrid Android Maven 发布

## 方案:GitHub 文件式 Maven 仓库

SDK 的 Maven 产物托管在独立的 GitHub 仓库,使用方直接用 raw 地址当 Maven 源,不需要 Maven 私服,也不需要任何 token。

```text
仓库:  https://github.com/gu0315/qx-hybrid-maven
raw：  https://raw.githubusercontent.com/gu0315/qx-hybrid-maven/main
本机克隆: ~/qx-hybrid-maven
```

发布坐标:

```gradle
com.energy.sdk:qx-hybrid:<版本号>
```

版本号通过 Gradle 参数 `-PQX_HYBRID_VERSION` 传入,`qx_hybrid/build.gradle` 里的默认值只是兜底。

## 发布新版本

本机克隆里有一键脚本 `~/qx-hybrid-maven/publish.sh`,流程是「构建 → 发布到本地仓库目录 → git 提交 → push 到 GitHub」:

```bash
cd ~/qx-hybrid-maven && ./publish.sh 0.1.11
```

脚本会拦截已发布过的版本号,确认要覆盖时加 `-f`:

```bash
cd ~/qx-hybrid-maven && ./publish.sh 0.1.9 -f
```

不用脚本时,等价的手动流程是:

```bash
cd /Users/guqianxiang/Desktop/chery/App/chery_android && ./gradlew :qx_hybrid:publishReleasePublicationToRemoteMavenRepository -PMAVEN_REPOSITORY_URL="file://$HOME/qx-hybrid-maven" -PQX_HYBRID_VERSION=0.1.11
```

```bash
cd ~/qx-hybrid-maven && git add com/ && git commit -m "release qx-hybrid 0.1.11" && git push origin main
```

一次发多个版本时,**最后发最高的那个版本**,否则 `maven-metadata.xml` 的 `<latest>` / `<release>` 会被写成低版本。

## 覆盖已发布的版本

文件式仓库直接覆盖 `com/energy/sdk/qx-hybrid/<版本号>/` 下的 aar、sources.jar、module 和各自的校验和文件即可,pom 一般不变。

但使用方的 Gradle 缓存不会自动失效,**必须同时通知对方刷新**:

```bash
./gradlew --refresh-dependencies
```

或者直接删缓存目录:

```bash
rm -rf ~/.gradle/caches/modules-2/files-2.1/com.energy.sdk
```

## 宿主工程接入

在使用方工程的 `settings.gradle`:

```gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // ① 本 SDK 仓库
        maven { url 'https://raw.githubusercontent.com/gu0315/qx-hybrid-maven/main' }

        // ② 传递依赖来源，必须有
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }   // Android-BLE(com.github.aicareles)在这里
        // 国内网络可加阿里云镜像加速：
        // maven { url 'https://maven.aliyun.com/repository/public' }
    }
}
```

在 app 模块的 `build.gradle`:

```gradle
implementation 'com.energy.sdk:qx-hybrid:0.1.10'
```

以下传递依赖会自动带入,不用手写:`androidx.appcompat`、`com.google.android.material`、
`androidx.core:core-ktx`、`androidx.webkit:webkit`、`androidx.exifinterface`、
`com.google.zxing:core`、`com.journeyapps:zxing-android-embedded`、
`com.github.aicareles:Android-BLE`、`com.google.code.gson:gson`、`kotlin-stdlib`。

## 本地验证

本机默认 Java 不是 17 时,先指定 Java 17:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

验证 AAR 构建:

```bash
./gradlew :qx_hybrid:assembleRelease
```

验证 Maven 发布产物(落到 `~/.m2/repository/com/energy/sdk/qx-hybrid/<版本号>/`):

```bash
./gradlew :qx_hybrid:publishReleasePublicationToMavenLocal -PQX_HYBRID_VERSION=0.1.11
```

## 附:公司 GitLab 方案(当前未启用)

仓库里还留着 `.gitlab-ci.yml` 和根目录的 `publish-to-gitlab.sh`,是早期发到公司 GitLab Package Registry
(`https://paas-gitlab.mychery.com`,project id `11853`)的方案。

**注意:本工程的 `origin` 是 GitHub(`gu0315/QXWebview_Android`),没有配 gitlab remote,
所以打 tag 推 origin 不会触发 `.gitlab-ci.yml` 的发布任务。** 要走 GitLab 只能在公司环境手动执行:

```bash
export GITLAB_MAVEN_USER='<deploy-token 用户名>' && export GITLAB_MAVEN_TOKEN='<deploy-token>'
```

```bash
cd /Users/guqianxiang/Desktop/chery/App/chery_android && ./publish-to-gitlab.sh 0.1.11
```
