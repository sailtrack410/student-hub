allprojects {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
    }
}

val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}
subprojects {
    project.evaluationDependsOn(":app")
}

// tencent_qq 1.0.0 的 Java 代码引用了旧版 SDK 中
// android.support.v4.app.Fragment 的 login 重载；项目已使用 AndroidX。
// 这里仅给插件补编译期 stub + 提升其 compileSdk，不把 stub 打进 APK。
subprojects {
    if (name == "tencent_qq") {
        afterEvaluate {
            val androidExtension = extensions.findByName("android")
                ?: error("tencent_qq: android extension not found")
            val setCompileSdk = androidExtension.javaClass.methods.firstOrNull {
                it.name == "compileSdkVersion" &&
                    it.parameterTypes.contentEquals(arrayOf<Class<*>>(Integer.TYPE))
            } ?: error("tencent_qq: compileSdkVersion(int) not found; AGP API changed")
            setCompileSdk.invoke(androidExtension, 36)
            val stubJar = rootProject.file("support-v4-stub.jar")
            check(stubJar.isFile) {
                "tencent_qq: 缺少 ${stubJar.path}；该 jar 是 QQ SDK AndroidX 兼容用的编译期 stub"
            }
            dependencies.add("compileOnly", files(stubJar))
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
