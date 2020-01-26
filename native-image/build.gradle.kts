plugins {
    base
    id("com.palantir.graal") version "0.6.0-69-ga9559b9"
}

evaluationDependsOn(":language")
evaluationDependsOn(":launcher")

graal {
    graalVersion("19.3.1")
    mainClass("brj.BridjeLauncher")
    outputName("brj")

    val languageJar = project(":language").tasks["jar"].outputs.files.singleFile
    val launcherJar = project(":launcher").tasks["jar"].outputs.files.singleFile
    option("-cp")
    option("$languageJar:$launcherJar")

    option("--macro:truffle")
    option("--features=org.graalvm.home.HomeFinderFeature")
    option("-Dorg.graalvm.launcher.relative.home=languages/brj/bin/brj")
    option("-Dorg.graalvm.launcher.classpath=lib/brj/brj-launcher.jar")
    option("--initialize-at-build-time")
    option("--no-fallback")
    option("-H:IncludeResources='.*\\.brj'")
}
