import org.apache.tools.ant.taskdefs.condition.Os

group = "dev.gitlive"
version = project.property("version") as String

plugins {
    `maven-publish`
    signing
    kotlin("native.cocoapods") version "1.4.31"
    kotlin("multiplatform") version "1.4.31"
}

repositories {
    mavenLocal()
    google()
    jcenter()
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
        testRuns["test"].executionTask.configure { useJUnit() }
    }

    js(BOTH) {
        browser()
        nodejs()
        compilations.all {
            kotlinOptions {
                sourceMap = true
                sourceMapEmbedSources = "always"
                moduleKind = "umd"
            }
        }
    }

    iosArm64()
    iosX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
            }
        }
        val jsMain by getting {
            dependencies {
            }
        }
        val jvmMain by getting {
            dependencies {
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("junit:junit:4.12")
                implementation("org.assertj:assertj-core:3.11.1")
            }
        }
    }
}


fun SigningExtension.whenRequired(block: () -> Boolean) {
    setRequired(block)
}

tasks {

    val copyPackageJson by registering(Copy::class) {
        from(file("package.json"))
        into(file("$buildDir/node_module"))
    }

    val copyJS by registering(Copy::class) {
        from(file("$buildDir/classes/kotlin/js/main/${project.name}.js"))
        into(file("$buildDir/node_module"))
    }

    val copySourceMap by registering(Copy::class) {
        from(file("$buildDir/classes/kotlin/js/main/${project.name}.js.map"))
        into(file("$buildDir/node_module"))
    }

    val copyReadMe by registering(Copy::class) {
        from(file("$buildDir/README.md"))
        into(file("$buildDir/node_module"))
    }

    val publishToNpm by registering(Exec::class) {
        doFirst {
            mkdir("$buildDir/node_module")
        }
        dependsOn(copyPackageJson, copyJS, copySourceMap, copyReadMe)
        workingDir("$buildDir/node_module")
        if(Os.isFamily(Os.FAMILY_WINDOWS)) {
            commandLine("cmd", "/c", "npm publish")
        } else {
            commandLine("npm", "publish")
        }
    }

    val updateVersion by registering(Exec::class) {
        commandLine("npm", "--allow-same-version", "--no-git-tag-version", "--prefix", projectDir, "version", "${project.property("version")}")
    }

    val prepareForGithubNpmPublish by registering(Copy::class) {
        val from = file("package.json")
        from.writeText(
            from.readText()
                .replace("https://registry.npmjs.org/","https://npm.pkg.github.com/")
                .replace("\"name\": \"kotlin-diff-utils\",", "\"name\": \"@gitliveapp/kotlin-diff-utils\",")
        )
    }
}

val javadocJar by tasks.creating(Jar::class) {
    archiveClassifier.value("javadoc")
}

var shouldSign = true

tasks.withType<Sign>().configureEach {
    onlyIf { shouldSign }
}

tasks.named("publishToMavenLocal").configure {
    shouldSign = false
}

publishing {
    repositories {
        maven {
            url = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2")
            credentials {
                username = project.findProperty("sonatypeUsername") as String? ?: System.getenv("sonatypeUsername")
                password = project.findProperty("sonatypePassword") as String? ?: System.getenv("sonatypePassword")
            }
        }
        maven {
            name = "GitHubPackages"
            url  = uri("https://maven.pkg.github.com/gitliveapp/packages")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
            }
        }
    }

    publications.all {
        this as MavenPublication

        artifact(javadocJar)

        pom {
            name.set("kotlin-diff-utils")
            description.set("The DiffUtils library for computing diffs, applying patches, generationg side-by-side view in Java.")
            url.set("https://github.com/GitLiveApp/kotlin-diff-utils")
            inceptionYear.set("2009")

            scm {
                url.set("https://github.com/GitLiveApp/kotlin-diff-utils")
                connection.set("scm:git:https://github.com/GitLiveApp/kotlin-diff-utils.git")
                developerConnection.set("scm:git:https://github.com/GitLiveApp/kotlin-diff-utils.git")
                tag.set("HEAD")
            }

            issueManagement {
                system.set("GitHub Issues")
                url.set("https://github.com/GitLiveApp/kotlin-diff-utils/issues")
            }

            developers {
                developer {
                    name.set("Tobias Warneke")
                    email.set("t.warneke@gmx.net")
                }
            }

            licenses {
                license {
                    name.set("The Apache Software License, Version 2.0")
                    url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    distribution.set("repo")
                    comments.set("A business-friendly OSS license")
                }
            }

        }
    }
}

signing {
    whenRequired { gradle.taskGraph.hasTask("publish") }
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications)
}
