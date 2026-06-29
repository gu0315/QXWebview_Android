# QX Hybrid Android Maven 发布

## 推荐方案：GitLab Package Registry

公司有 GitLab 时，不需要单独搭 Maven 私服。GitLab 项目自带 Maven Package Registry，可以发布 Android AAR。

默认发布坐标：

```gradle
com.energy.sdk:qx-hybrid:0.1.8
```

## 版本号

默认版本是 `0.1.8`，也可以通过 Gradle 参数覆盖：

```bash
./gradlew :qx_hybrid:publishToMavenLocal -PQX_HYBRID_VERSION=0.1.9
```

GitLab CI 发布时会自动使用 tag 作为版本号：

```bash
git tag 0.1.8
git push origin 0.1.8
```

## GitLab CI 自动发布

仓库根目录已增加 `.gitlab-ci.yml`。

普通分支 push 会执行：

```bash
./gradlew :qx_hybrid:assembleRelease
```

tag push 会发布到当前 GitLab 项目的 Maven Package Registry：

```bash
./gradlew :qx_hybrid:publishReleasePublicationToGitLabRepository \
  -PQX_HYBRID_VERSION="$CI_COMMIT_TAG" \
  -PMAVEN_REPOSITORY_NAME=GitLab \
  -PMAVEN_REPOSITORY_URL="$CI_API_V4_URL/projects/$CI_PROJECT_ID/packages/maven" \
  -PMAVEN_USERNAME=gitlab-ci-token \
  -PMAVEN_PASSWORD="$CI_JOB_TOKEN"
```

发布成功后，包地址格式是：

```text
https://<gitlab-host>/api/v4/projects/<PROJECT_ID>/packages/maven
```

`PROJECT_ID` 在 GitLab 项目首页或 Settings 页面可以看到。

## 宿主工程接入

在宿主工程 `settings.gradle` 增加 GitLab Maven 仓库：

```gradle
def gitLabMavenUsername = providers.gradleProperty("GITLAB_MAVEN_USERNAME")
        .orElse(providers.environmentVariable("GITLAB_MAVEN_USERNAME"))
        .orNull
def gitLabMavenToken = providers.gradleProperty("GITLAB_MAVEN_TOKEN")
        .orElse(providers.environmentVariable("GITLAB_MAVEN_TOKEN"))
        .orNull

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
        maven {
            url = uri("https://<gitlab-host>/api/v4/projects/<PROJECT_ID>/packages/maven")
            credentials(HttpHeaderCredentials) {
                name = "Private-Token"
                value = gitLabMavenToken
            }
            authentication {
                header(HttpHeaderAuthentication)
            }
        }
    }
}
```

如果公司 GitLab 不支持 Header Token，也可以用用户名和 Token：

```gradle
maven {
    url = uri("https://<gitlab-host>/api/v4/projects/<PROJECT_ID>/packages/maven")
    credentials {
        username = gitLabMavenUsername
        password = gitLabMavenToken
    }
}
```

在宿主 app 的 `build.gradle` 引用：

```gradle
implementation 'com.energy.sdk:qx-hybrid:0.1.8'
```

建议把访问 token 放到宿主工程开发机的 `~/.gradle/gradle.properties`：

```properties
GITLAB_MAVEN_USERNAME=your_gitlab_username
GITLAB_MAVEN_TOKEN=your_access_token
```

## 本地验证

本机默认 Java 不是 17 时，先指定 Java 17：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

验证 AAR 构建：

```bash
./gradlew :qx_hybrid:assembleRelease
```

验证 Maven 发布产物：

```bash
./gradlew :qx_hybrid:publishToMavenLocal
```

生成的本地 Maven 路径：

```text
~/.m2/repository/com/energy/sdk/qx-hybrid/<version>/
```

## 手动发布到 GitLab

不走 CI 时，也可以手动发布：

```bash
./gradlew :qx_hybrid:publishReleasePublicationToGitLabRepository \
  -PQX_HYBRID_VERSION=0.1.8 \
  -PMAVEN_REPOSITORY_NAME=GitLab \
  -PMAVEN_REPOSITORY_URL=https://<gitlab-host>/api/v4/projects/<PROJECT_ID>/packages/maven \
  -PMAVEN_USERNAME=your_gitlab_username \
  -PMAVEN_PASSWORD=your_access_token
```

如果 GitLab 只能在公司电脑访问，就在公司电脑执行手动发布或推 tag 触发 CI。
