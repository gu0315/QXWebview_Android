# QX Hybrid Android Maven 发布

## 发布坐标

默认坐标：

```gradle
com.energy.sdk:qx-hybrid:0.1.8
```

可通过 Gradle 属性覆盖：

```properties
QX_HYBRID_GROUP_ID=com.energy.sdk
QX_HYBRID_ARTIFACT_ID=qx-hybrid
QX_HYBRID_VERSION=0.1.8
```

## 配置线上 Maven 仓库

建议把账号密码放到本机 `~/.gradle/gradle.properties` 或 CI 环境变量，不要提交到仓库。

```properties
MAVEN_REPOSITORY_NAME=companyMaven
MAVEN_REPOSITORY_URL=https://your-maven-host/repository/android-releases/
MAVEN_USERNAME=your_username
MAVEN_PASSWORD=your_password_or_token
```

也可以使用环境变量：

```bash
export MAVEN_REPOSITORY_URL=https://your-maven-host/repository/android-releases/
export MAVEN_USERNAME=your_username
export MAVEN_PASSWORD=your_password_or_token
```

## 发布命令

本地验证发布配置：

```bash
./gradlew :qx_hybrid:publishToMavenLocal
```

如果本机默认不是 Java 17，先指定 Java 17：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew :qx_hybrid:publishToMavenLocal
```

发布到线上 Maven：

```bash
./gradlew :qx_hybrid:publishReleasePublicationToCompanyMavenRepository
```

如果 `MAVEN_REPOSITORY_NAME` 使用默认值 `remoteMaven`，命令是：

```bash
./gradlew :qx_hybrid:publishReleasePublicationToRemoteMavenRepository
```

## 宿主工程接入

在宿主工程 `settings.gradle` 增加 Maven 仓库：

```gradle
def mavenUsername = providers.gradleProperty("MAVEN_USERNAME")
        .orElse(providers.environmentVariable("MAVEN_USERNAME"))
        .orNull
def mavenPassword = providers.gradleProperty("MAVEN_PASSWORD")
        .orElse(providers.environmentVariable("MAVEN_PASSWORD"))
        .orNull

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
        maven {
            url = uri("https://your-maven-host/repository/android-releases/")
            credentials {
                username = mavenUsername
                password = mavenPassword
            }
        }
    }
}
```

在宿主 app 的 `build.gradle` 引用：

```gradle
implementation 'com.energy.sdk:qx-hybrid:0.1.8'
```

## 没有 Maven 私服：使用 JitPack

当前仓库已增加根目录 `jitpack.yml`，JitPack 会使用 OpenJDK 17 并执行：

```bash
./gradlew :qx_hybrid:publishToMavenLocal \
  -PQX_HYBRID_GROUP_ID=com.github.gu0315.QXWebview_Android \
  -PQX_HYBRID_ARTIFACT_ID=qx_hybrid \
  -PQX_HYBRID_VERSION=$VERSION
```

发布步骤：

1. 提交代码到 GitHub。
2. 打 tag，例如：

```bash
git tag 0.1.8
git push origin 0.1.8
```

3. 打开 JitPack：

```text
https://jitpack.io/#gu0315/QXWebview_Android/0.1.8
```

4. 点击 `Get it` 触发构建。

宿主工程增加仓库：

```gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

宿主 app 引用：

```gradle
implementation 'com.github.gu0315.QXWebview_Android:qx_hybrid:0.1.8'
```

开发分支临时依赖可以在 JitPack 页面选择对应 branch 后复制 Gradle 坐标。

正式接入建议始终使用 tag，不建议长期依赖分支 `SNAPSHOT`。
