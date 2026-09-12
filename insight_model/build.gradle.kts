plugins { id("com.android.asset-pack") }

assetPack {
    packName.set("insight_model")
    dynamicDelivery { deliveryType.set("install-time") }
}

// Fetching is a deliberate developer/build-machine action, never app runtime code.
val verifyModel by tasks.registering {
    val model = layout.projectDirectory.file("src/main/assets/usage-insight.gguf")
    inputs.file(model)
    doLast {
        check(model.asFile.isFile) {
            "Run: bash scripts/prepare-insight-model.sh before building an app bundle"
        }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        model.asFile.inputStream().use { stream ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        check(digest.digest().joinToString("") { "%02x".format(it) } ==
            "9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031") {
            "The bundled insight model does not match the pinned model"
        }
    }
}
tasks.configureEach {
    if (name == "generateAssetPackManifest") dependsOn(verifyModel)
}
