plugins {
	val kotlinVersion = "1.6.10"
	kotlin("jvm") version kotlinVersion
	kotlin("plugin.serialization") version kotlinVersion

	id("net.mamoe.mirai-console") version "2.11.0-M2"
}

group = "my.ktbot"
version = "1.0"

repositories {
	mavenLocal()
	maven("https://maven.aliyun.com/repository/public") // 阿里云国内代理仓库
	mavenCentral()
}

dependencies {
	implementation("org.xerial:sqlite-jdbc:3.36.0.3")
	implementation("org.ktorm:ktorm-core:3.4.1")
	implementation("org.ktorm:ktorm-support-sqlite:3.4.1")
	implementation("org.jetbrains.kotlin:kotlin-reflect:1.4.0")
	implementation("io.ktor:ktor-client-serialization-jvm:1.6.8")
	implementation("io.ktor:ktor-client-json:1.6.8")
	compileOnly("org.jetbrains:annotations:23.0.0")
	implementation("ch.qos.logback:logback-classic:1.2.11")
	// implementation("org.fusesource.jansi:jansi:2.4.0")
	// implementation("org.apache.logging.log4j:log4j-api:2.17.1")
	// implementation("org.apache.logging.log4j:log4j-core:2.17.1")
	// api("net.mamoe:mirai-logging-log4j2:2.9.2")
	// implementation("org.reflections:reflections:0.10.2")
}
mirai {
	noCoreApi = false
	noTestCore = false
	noConsole = false
	dontConfigureKotlinJvmDefault = false
	publishingEnabled = false
	jvmTarget = JavaVersion.VERSION_17
	configureShadow {
		dependencyFilter.include {
			println(it.name)
			it.moduleGroup == "io.ktor" && it.moduleName in arrayOf(
				"ktor-client-serialization-jvm",
				"ktor-client-json"
			)
		}
	}
}
kotlin {
	sourceSets {
		all {
			languageSettings.optIn("kotlin.RequiresOptIn")
		}
	}
}
tasks.create("build2Jar") {
	val pluginPath = "${rootDir}/plugins/"
	doFirst {
		File(pluginPath).listFiles()?.forEach {
			if (it.isFile) {
				println("Delete File:${it.name}")
				it.delete()
			}
		}
	}
	group = "mirai"
	dependsOn += "buildPlugin"
	doLast {
		copy {
			println("Copy File:${pluginPath}")
			from("${buildDir}/mirai")
			into(pluginPath)
		}
	}
}