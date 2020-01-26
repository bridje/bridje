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
    option("--initialize-at-build-time")
    option("-H:IncludeResources='.*\\.brj'")
}
