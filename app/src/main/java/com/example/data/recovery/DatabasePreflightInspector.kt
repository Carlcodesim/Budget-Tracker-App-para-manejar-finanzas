package com.example.data.recovery

import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * Non-invasive SQLite preflight.
 *
 * This class NEVER opens SQLite, Room, or a writable file descriptor. It reads
 * only the fixed 100-byte SQLite database header from the original file and
 * checks for sidecar presence. Integrity and WAL-consistent snapshot validation
 * must happen later on an independent copy.
 */
object DatabasePreflightInspector {

    private const val SQLITE_HEADER_LENGTH = 100
    private const val SQLITE_PAGE_SIZE_OFFSET = 16
    private const val SQLITE_READ_VERSION_OFFSET = 18
    private const val SQLITE_WRITE_VERSION_OFFSET = 19
    private const val SQLITE_RESERVED_BYTES_OFFSET = 20
    private const val SQLITE_PAYLOAD_FRACTION_OFFSET = 21
    private const val SQLITE_DATABASE_PAGES_OFFSET = 28
    private const val SQLITE_USER_VERSION_OFFSET = 60
    private const val SQLITE_ENCODING_OFFSET = 56
    private const val CURRENT_SCHEMA_VERSION = 14

    private val sqliteMagic = byteArrayOf(
        0x53, 0x51, 0x4c, 0x69, 0x74, 0x65, 0x20, 0x66,
        0x6f, 0x72, 0x6d, 0x61, 0x74, 0x20, 0x33, 0x00
    )

    fun inspect(databaseFile: File): DatabasePreflightResult {
        val walFile = File(databaseFile.path + "-wal")
        val shmFile = File(databaseFile.path + "-shm")
        val journalFile = File(databaseFile.path + "-journal")

        return try {
            val hasWal = walFile.exists()
            val hasShm = shmFile.exists()
            val hasJournal = journalFile.exists()
            val sidecars = SidecarState(hasWal, hasShm, hasJournal)

            if (!databaseFile.exists()) {
                val condition = if (hasWal || hasShm || hasJournal) {
                    DatabaseCondition.INCONSISTENT
                } else {
                    DatabaseCondition.ABSENT
                }
                return result(
                    condition = condition,
                    reason = if (condition == DatabaseCondition.ABSENT) "DATABASE_ABSENT" else "SIDECAR_WITHOUT_DATABASE",
                    message = if (condition == DatabaseCondition.ABSENT)
                        "No se encontró el archivo de base de datos."
                    else
                        "Hay archivos auxiliares SQLite sin el archivo principal. Se requiere revisión segura.",
                    sidecars = sidecars
                )
            }

            if (!databaseFile.isFile || !databaseFile.canRead()) {
                return result(
                    DatabaseCondition.IO_ERROR,
                    "DATABASE_NOT_READABLE",
                    "El archivo principal no es un archivo regular legible.",
                    sidecars = sidecars,
                    fileSize = safeLength(databaseFile)
                )
            }

            val fileSize = databaseFile.length()
            if (fileSize == 0L) {
                return result(
                    DatabaseCondition.CORRUPT,
                    "DATABASE_EMPTY",
                    "El archivo principal está vacío.",
                    sidecars = sidecars,
                    fileSize = fileSize
                )
            }

            val header = ByteArray(SQLITE_HEADER_LENGTH)
            FileInputStream(databaseFile).use { input ->
                var offset = 0
                while (offset < header.size) {
                    val read = input.read(header, offset, header.size - offset)
                    if (read < 0) break
                    offset += read
                }
                if (offset < header.size) {
                    return result(
                        DatabaseCondition.CORRUPT,
                        "TRUNCATED_SQLITE_HEADER",
                        "El encabezado SQLite está incompleto.",
                        sidecars = sidecars,
                        fileSize = fileSize
                    )
                }
            }

            if (!header.copyOfRange(0, sqliteMagic.size).contentEquals(sqliteMagic)) {
                return result(
                    DatabaseCondition.CORRUPT,
                    "INVALID_SQLITE_SIGNATURE",
                    "El archivo no contiene la firma estándar de SQLite.",
                    sidecars = sidecars,
                    fileSize = fileSize
                )
            }

            val pageSizeField = unsignedShort(header, SQLITE_PAGE_SIZE_OFFSET)
            val pageSize = if (pageSizeField == 1) 65536 else pageSizeField
            val readVersion = header[SQLITE_READ_VERSION_OFFSET].toInt() and 0xff
            val writeVersion = header[SQLITE_WRITE_VERSION_OFFSET].toInt() and 0xff
            val reservedBytes = header[SQLITE_RESERVED_BYTES_OFFSET].toInt() and 0xff
            val encoding = unsignedInt(header, SQLITE_ENCODING_OFFSET).toInt()
            val declaredPages = unsignedInt(header, SQLITE_DATABASE_PAGES_OFFSET)
            val schemaVersion = unsignedInt(header, SQLITE_USER_VERSION_OFFSET).toInt()

            val validPageSize = pageSize in 512..65536 && (pageSize and (pageSize - 1)) == 0
            val validJournalVersions = readVersion in 1..2 && writeVersion in 1..2
            val usablePageSize = pageSize - reservedBytes >= 480
            val validPayloadFractions =
                (header[SQLITE_PAYLOAD_FRACTION_OFFSET].toInt() and 0xff) == 64 &&
                    (header[SQLITE_PAYLOAD_FRACTION_OFFSET + 1].toInt() and 0xff) == 32 &&
                    (header[SQLITE_PAYLOAD_FRACTION_OFFSET + 2].toInt() and 0xff) == 32
            val validEncoding = encoding in 1..3

            if (!validPageSize || !validJournalVersions || !usablePageSize ||
                !validPayloadFractions || !validEncoding
            ) {
                return result(
                    DatabaseCondition.CORRUPT,
                    "INVALID_SQLITE_HEADER_FIELDS",
                    "El encabezado SQLite contiene campos estructurales inválidos.",
                    schemaVersion = schemaVersion,
                    pageSize = pageSize,
                    sidecars = sidecars,
                    fileSize = fileSize
                )
            }

            // A hot rollback journal or WAL/SHM sidecars can change how the main
            // database file must be interpreted. Classify this before judging page
            // counts or alignment, which may be affected by uncheckpointed commits.
            if (hasJournal) {
                return result(
                    DatabaseCondition.INCONSISTENT,
                    "ROLLBACK_JOURNAL_PRESENT",
                    "Se detectó un journal SQLite. Se requiere una instantánea aislada y validación.",
                    schemaVersion = schemaVersion,
                    pageSize = pageSize,
                    sidecars = sidecars,
                    fileSize = fileSize
                )
            }

            if (hasWal || hasShm) {
                return result(
                    DatabaseCondition.UNKNOWN,
                    "WAL_SIDECAR_REQUIRES_CONSISTENT_SNAPSHOT",
                    "Se detectaron archivos WAL/SHM. El archivo principal por sí solo no se considera una copia completa.",
                    schemaVersion = schemaVersion,
                    pageSize = pageSize,
                    sidecars = sidecars,
                    fileSize = fileSize
                )
            }

            if (fileSize < pageSize || (fileSize % pageSize != 0L)) {
                return result(
                    DatabaseCondition.CORRUPT,
                    "INCOMPLETE_DATABASE_PAGES",
                    "El archivo no contiene páginas SQLite completas.",
                    schemaVersion = schemaVersion,
                    pageSize = pageSize,
                    sidecars = sidecars,
                    fileSize = fileSize
                )
            }

            val actualPages = fileSize / pageSize
            if (declaredPages != 0L && declaredPages > actualPages) {
                return result(
                    DatabaseCondition.CORRUPT,
                    "DECLARED_PAGE_COUNT_EXCEEDS_FILE",
                    "El encabezado declara más páginas de las que contiene el archivo.",
                    schemaVersion = schemaVersion,
                    pageSize = pageSize,
                    sidecars = sidecars,
                    fileSize = fileSize
                )
            }

            when {
                schemaVersion > CURRENT_SCHEMA_VERSION -> result(
                    DatabaseCondition.FUTURE_VERSION,
                    "FUTURE_SCHEMA_VERSION",
                    "La base usa una versión de esquema más reciente que esta aplicación.",
                    schemaVersion,
                    pageSize,
                    sidecars,
                    fileSize
                )
                schemaVersion in 9 until CURRENT_SCHEMA_VERSION -> result(
                    DatabaseCondition.LEGACY_MIGRATION_CANDIDATE,
                    "LEGACY_SCHEMA_REQUIRES_COPY_MIGRATION_REHEARSAL",
                    "La versión tiene una ruta de migración registrada, pero solo podrá probarse sobre una copia independiente.",
                    schemaVersion,
                    pageSize,
                    sidecars,
                    fileSize
                )
                schemaVersion in 1..8 -> result(
                    DatabaseCondition.LEGACY_UNSUPPORTED,
                    "LEGACY_SCHEMA_WITH_MISSING_MIGRATION_PATH",
                    "No existe una ruta de migración completa registrada para esta versión.",
                    schemaVersion,
                    pageSize,
                    sidecars,
                    fileSize
                )
                schemaVersion == CURRENT_SCHEMA_VERSION -> result(
                    DatabaseCondition.HEADER_RECOGNIZED,
                    "HEADER_RECOGNIZED_INTEGRITY_NOT_CERTIFIED",
                    "El encabezado coincide con el esquema esperado. La integridad todavía no está certificada.",
                    schemaVersion,
                    pageSize,
                    sidecars,
                    fileSize
                )
                else -> result(
                    DatabaseCondition.UNKNOWN,
                    "UNKNOWN_SCHEMA_VERSION",
                    "La versión de esquema no pudo clasificarse con seguridad.",
                    schemaVersion,
                    pageSize,
                    sidecars,
                    fileSize
                )
            }
        } catch (_: SecurityException) {
            result(
                DatabaseCondition.IO_ERROR,
                "DATABASE_ACCESS_DENIED",
                "Android no permitió leer los metadatos del archivo."
            )
        } catch (_: IOException) {
            result(
                DatabaseCondition.IO_ERROR,
                "DATABASE_READ_FAILED",
                "No fue posible leer el encabezado de la base de datos."
            )
        } catch (_: Exception) {
            result(
                DatabaseCondition.IO_ERROR,
                "PREFLIGHT_FAILED_CLOSED",
                "La inspección preliminar falló; el acceso permanece bloqueado."
            )
        }
    }

    private fun unsignedShort(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or
            (bytes[offset + 1].toInt() and 0xff)

    private fun unsignedInt(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xff) shl 24) or
            ((bytes[offset + 1].toLong() and 0xff) shl 16) or
            ((bytes[offset + 2].toLong() and 0xff) shl 8) or
            (bytes[offset + 3].toLong() and 0xff)

    private fun safeLength(file: File): Long = try {
        file.length()
    } catch (_: Exception) {
        0L
    }

    private fun result(
        condition: DatabaseCondition,
        reason: String,
        message: String,
        schemaVersion: Int? = null,
        pageSize: Int? = null,
        sidecars: SidecarState = SidecarState(),
        fileSize: Long = 0L
    ) = DatabasePreflightResult(
        condition = condition,
        reasonCode = reason,
        message = message,
        schemaVersion = schemaVersion,
        pageSize = pageSize,
        fileSize = fileSize,
        hasWal = sidecars.hasWal,
        hasShm = sidecars.hasShm,
        hasJournal = sidecars.hasJournal
    )

    private data class SidecarState(
        val hasWal: Boolean = false,
        val hasShm: Boolean = false,
        val hasJournal: Boolean = false
    )
}

data class DatabasePreflightResult(
    val condition: DatabaseCondition,
    val reasonCode: String,
    val message: String,
    val schemaVersion: Int? = null,
    val pageSize: Int? = null,
    val fileSize: Long = 0L,
    val hasWal: Boolean = false,
    val hasShm: Boolean = false,
    val hasJournal: Boolean = false
)

/**
 * In-memory startup report only; it is recalculated on every process start.
 * This report is informational and never grants database access.
 */
object DatabaseStartupState {
    @Volatile
    private var report: DatabasePreflightResult? = null

    fun publish(value: DatabasePreflightResult) {
        report = value
    }

    fun current(): DatabasePreflightResult? = report
}
