package com.moodtools.hub.networking

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipFile

/** OBBs are data, not APK splits. Never extract an archive-supplied path. */
internal object GameExpansionFiles {
    data class Expansion(val entry: String, val name: String, val size: Long, val crc: Long)

    fun inspect(archive: ZipFile, packageName: String): List<Expansion> {
        requirePackage(packageName)
        val entries = archive.entries().asSequence()
            .filter { !it.isDirectory && it.name.endsWith(".obb", ignoreCase = true) }.toList()
        require(entries.size <= 2) { "A game package may contain only a main and a patch OBB" }
        val result = entries.map { entry ->
            val name = entry.name.substringAfterLast('/')
            require(validName(name, packageName) &&
                (entry.name == name || entry.name == "Android/obb/$packageName/$name")) {
                "The package contains an invalid or foreign OBB path"
            }
            require(entry.size in 1..MAX_FILE_BYTES && entry.crc in 0..0xffffffffL) {
                "The package contains an empty or oversized OBB"
            }
            Expansion(entry.name, name, entry.size, entry.crc)
        }
        require(result.map { it.name.substringBefore('.') }.distinct().size == result.size) {
            "The package contains duplicate OBB files"
        }
        return result
    }

    // ponytail: serialize OBB transfers; use per-package locks only if parallel installs are added.
    @Synchronized fun stage(
        archive: ZipFile, packageName: String, directory: File,
        isCancelled: () -> Boolean = { false }, onProgress: (Long, Long) -> Unit = { _, _ -> }
    ) {
        val entries = inspect(archive, packageName)
        stageEntries(entries, directory, isCancelled, onProgress) { expansion ->
            archive.getInputStream(archive.getEntry(expansion.entry))
        }
    }

    /** Copy only this package's expansion files; never rename or delete installed files. */
    @Synchronized fun preserveInstalled(
        source: File, packageName: String, directory: File,
        isCancelled: () -> Boolean = { false }, onProgress: (Long, Long) -> Unit = { _, _ -> }
    ) {
        requirePackage(packageName)
        require(!Files.isSymbolicLink(source.toPath())) { "Invalid installed OBB folder" }
        // exists()/listFiles() can conceal access denial as an absent/empty folder.
        val files = try {
            Files.newDirectoryStream(source.toPath()).use { stream -> stream.map { it.toFile() }.toList() }
        } catch (_: NoSuchFileException) {
            return
        }
        val obbs = files.filter { it.name.endsWith(".obb", ignoreCase = true) }
        require(obbs.size <= 2) { "A game folder may contain only a main and a patch OBB" }
        val entries = obbs.map { file ->
            require(validName(file.name, packageName) && !Files.isSymbolicLink(file.toPath()) &&
                file.isFile && file.canonicalFile.parentFile == source.canonicalFile) { "Invalid installed OBB file" }
            val size = file.length()
            require(size in 1..MAX_FILE_BYTES) { "The installed OBB is empty or oversized" }
            Expansion(file.name, file.name, size, fileCrc(file, isCancelled))
        }
        require(entries.map { it.name.substringBefore('.') }.distinct().size == entries.size) {
            "The game folder contains duplicate OBB files"
        }
        stageEntries(entries, directory, isCancelled, onProgress) { expansion ->
            safeChild(source, expansion.name).inputStream()
        }
    }

    private fun stageEntries(
        entries: List<Expansion>, directory: File,
        isCancelled: () -> Boolean, onProgress: (Long, Long) -> Unit,
        openInput: (Expansion) -> InputStream
    ) {
        if (entries.isEmpty()) return
        val parent = requireNotNull(directory.parentFile)
        check(parent.mkdirs() || parent.isDirectory) { "Could not create OBB storage" }
        recover(directory)
        val index = entries.joinToString("\n") { "${it.name}\t${it.size}\t${it.crc}" }
        val inventory = File(directory, "inventory.txt")
        if (inventory.isFile && inventory.length() <= 2048 && inventory.readText() == index &&
            entries.all { expansion ->
                val file = safeChild(directory, expansion.name)
                file.isFile && file.length() == expansion.size && fileCrc(file, isCancelled) == expansion.crc
            }) {
            val total = entries.sumOf { it.size }
            onProgress(total, total)
            return
        }
        val total = entries.sumOf { it.size }
        check(parent.usableSpace >= total) { "Not enough free storage to prepare game OBB files" }
        val incoming = File(parent, "${directory.name}-incoming-${UUID.randomUUID()}")
        check(incoming.mkdir()) { "Could not create OBB staging storage" }
        try {
            var completed = 0L
            var reportedPercent = -1L
            entries.forEach { expansion ->
                openInput(expansion).use { input ->
                    copyVerified(input, incoming, expansion, isCancelled) { count ->
                        completed += count
                        val percent = completed * 100 / total
                        if (percent != reportedPercent) {
                            reportedPercent = percent
                            onProgress(completed, total)
                        }
                    }
                }
            }
            // Publish the inventory last. An interrupted stage must never become launchable.
            FileOutputStream(File(incoming, "inventory.txt")).use {
                it.write(index.toByteArray(Charsets.UTF_8))
                it.fd.sync()
            }
            cancelled(isCancelled)
            val backup = File(parent, "${directory.name}-previous")
            if (directory.exists()) move(directory, backup)
            try { move(incoming, directory) }
            catch (error: Throwable) {
                if (backup.exists()) move(backup, directory)
                throw error
            }
            deleteFlatDirectory(backup)
            // A newer complete download replaces old retained versions, never installed game data.
            parent.listFiles()?.filter {
                it.name.toLongOrNull()?.let { old -> old < directory.name.toLong() } == true &&
                    it.canonicalFile.parentFile == parent.canonicalFile
            }?.forEach(::deleteFlatDirectory)
        } finally { deleteFlatDirectory(incoming) }
    }

    @Synchronized fun deliver(
        directory: File, packageName: String, destination: File,
        isCancelled: () -> Boolean = { false }, onProgress: (Long, Long) -> Unit = { _, _ -> }
    ) {
        requirePackage(packageName)
        recover(directory)
        val inventory = File(directory, "inventory.txt")
        if (!inventory.isFile) return
        require(inventory.length() <= 2048) { "Invalid OBB inventory" }
        val entries = inventory.readLines().map { line ->
            val fields = line.split('\t')
            require(fields.size == 3 && validName(fields[0], packageName)) { "Invalid OBB inventory" }
            Expansion(fields[0], fields[0], fields[1].toLong(), fields[2].toLong()).also {
                require(it.size in 1..MAX_FILE_BYTES && it.crc in 0..0xffffffffL)
            }
        }
        require(entries.size in 1..2 && entries.map { it.name }.distinct().size == entries.size)
        check(destination.mkdirs() || destination.isDirectory) {
            "Android denied OBB folder access. Allow this launcher to install unknown apps, then reopen it. Android 12L or older also needs storage permission."
        }
        val total = entries.sumOf { it.size }
        var completed = 0L
        var reportedPercent = -1L
        entries.forEach { expansion ->
            cancelled(isCancelled)
            val target = safeChild(destination, expansion.name)
            val receipt = File(directory, "delivery-${destination.absolutePath.hashCode()}-${expansion.name}.txt")
            val expectedReceipt = "${expansion.size}:${expansion.crc}:${target.lastModified()}"
            if (target.isFile && target.length() == expansion.size && receipt.isFile &&
                receipt.length() <= 100 && receipt.readText() == expectedReceipt) {
                completed += expansion.size
                onProgress(completed, total)
                return@forEach
            }
            val source = safeChild(directory, expansion.name)
            require(source.isFile && source.length() == expansion.size) { "The retained OBB is incomplete; download the game again" }
            check(destination.usableSpace >= expansion.size) { "Not enough free storage to install game OBB files" }
            source.inputStream().use { input ->
                copyVerified(input, destination, expansion, isCancelled) { count ->
                    completed += count
                    val percent = completed * 100 / total
                    if (percent != reportedPercent) {
                        reportedPercent = percent
                        onProgress(completed, total)
                    }
                }
            }
            receipt.writeText("${expansion.size}:${expansion.crc}:${target.lastModified()}")
        }
    }

    private fun copyVerified(
        input: InputStream, directory: File, expansion: Expansion,
        isCancelled: () -> Boolean, onBytes: (Int) -> Unit
    ) {
        val target = safeChild(directory, expansion.name)
        val temporary = File.createTempFile("jester-obb-", ".part", directory)
        try {
            val crc = CRC32()
            var written = 0L
            FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    cancelled(isCancelled)
                    val count = input.read(buffer)
                    if (count < 0) break
                    written += count
                    require(written <= expansion.size) { "The OBB exceeds its declared size" }
                    output.write(buffer, 0, count)
                    crc.update(buffer, 0, count)
                    onBytes(count)
                }
                output.fd.sync()
            }
            require(written == expansion.size && crc.value == expansion.crc) { "OBB integrity verification failed" }
            cancelled(isCancelled)
            move(temporary, target)
        } finally { temporary.delete() }
    }

    private fun move(source: File, target: File) {
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun deleteFlatDirectory(directory: File) {
        // Only flat generations created by stage(); never follow a child directory or symlink.
        if (Files.isSymbolicLink(directory.toPath())) return
        directory.listFiles()?.filter { it.isFile && it.canonicalFile.parentFile == directory.canonicalFile }
            ?.forEach { it.delete() }
        directory.delete()
    }

    private fun recover(directory: File) {
        val parent = requireNotNull(directory.parentFile)
        val backup = File(parent, "${directory.name}-previous")
        if (!directory.exists() && File(backup, "inventory.txt").isFile) move(backup, directory)
        if (directory.isDirectory) deleteFlatDirectory(backup)
        // Only stage() creates these flat temporary generations, including after process death.
        parent.listFiles()?.filter {
            it.name.startsWith("${directory.name}-incoming-") &&
                it.canonicalFile.parentFile == parent.canonicalFile
        }?.forEach(::deleteFlatDirectory)
    }

    private fun fileCrc(file: File, isCancelled: () -> Boolean): Long {
        val crc = CRC32()
        file.inputStream().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                cancelled(isCancelled)
                val count = input.read(buffer)
                if (count < 0) break
                crc.update(buffer, 0, count)
            }
        }
        return crc.value
    }

    private fun safeChild(directory: File, name: String): File = File(directory, name).also {
        require(it.canonicalFile.parentFile == directory.canonicalFile) { "OBB path escaped its destination" }
    }

    private fun validName(name: String, packageName: String): Boolean =
        Regex("(?:main|patch)\\.[0-9]{1,10}\\.${Regex.escape(packageName)}\\.obb").matches(name)

    internal fun requirePackage(packageName: String) {
        require(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+").matches(packageName)) { "Invalid OBB package name" }
    }

    private fun cancelled(isCancelled: () -> Boolean) {
        if (isCancelled()) throw GameInstallCancelledException()
    }

    private const val MAX_FILE_BYTES = 2L * 1024 * 1024 * 1024
}
