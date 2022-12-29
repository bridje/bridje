plugins {
    base
    id("org.graalvm.buildtools.native") version "0.9.19"
}

evaluationDependsOn(":language")
evaluationDependsOn(":launcher")

graalvmNative {
    binaries {
//        named("main") {
//            imageName.set("brj")
//            mainClass.set("brj.BridjeLauncher")
//            requiredVersion.set("22.3")
//        }
    }

// From the old Palantir plugin
//    val languageJar = project(":language").tasks["jar"].outputs.files.singleFile
//    val launcherJar = project(":launcher").tasks["jar"].outputs.files.singleFile
//    option("-cp")
//    option("$languageJar:$launcherJar")
//
//    option("--macro:truffle")
//    option("--features=org.graalvm.home.HomeFinderFeature")
//    option("-Dorg.graalvm.launcher.relative.home=languages/brj/bin/brj")
//    option("-Dorg.graalvm.launcher.classpath=lib/brj/brj-launcher.jar")
//    option("--initialize-at-build-time")
//    option("--no-fallback")
//    option("-H:IncludeResources='.*\\.brj'")
}
