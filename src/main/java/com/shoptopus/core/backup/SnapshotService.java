package com.shoptopus.core.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class SnapshotService {

    private static final Logger log = LoggerFactory.getLogger(SnapshotService.class);
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");

    private final DataSourceProperties mainDataSourceProperties;
    private final String uploadsPath;
    private final String snapshotsDirectory;

    public SnapshotService(
            @Qualifier("mainDataSourceProperties") DataSourceProperties mainDataSourceProperties,
            @Value("${app.media.storage-path}") String uploadsPath,
            @Value("${app.snapshot.directory:${user.dir}/snapshots}") String snapshotsDirectory
    ) {
        this.mainDataSourceProperties = mainDataSourceProperties;
        this.uploadsPath = uploadsPath;
        this.snapshotsDirectory = snapshotsDirectory;
    }

    public Path createSnapshot() {
        Path archivePath = snapshotArchivePath();
        try {
            Files.createDirectories(archivePath.getParent());
            createSnapshot(archivePath);
            return archivePath;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to prepare snapshot archive at " + archivePath, ex);
        }
    }

    public Path createSnapshot(Path archivePath) {
        Path normalizedArchive = archivePath.toAbsolutePath().normalize();
        Path tempDirectory = null;
        try {
            Files.createDirectories(normalizedArchive.getParent());
            tempDirectory = Files.createTempDirectory("shoptopus-snapshot-");
            Path databaseDump = tempDirectory.resolve("database.sql");
            Path metadata = tempDirectory.resolve("snapshot-meta.txt");
            dumpMainDatabase(databaseDump);
            writeMetadata(metadata);
            zipSnapshot(normalizedArchive, databaseDump, metadata, resolveUploadsPath());
            log.info("Created application snapshot at {}", normalizedArchive);
            return normalizedArchive;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create snapshot archive at " + normalizedArchive, ex);
        } finally {
            deleteQuietly(tempDirectory);
        }
    }

    public int deleteSnapshotsOlderThan(Duration retention) {
        if (retention == null || retention.isNegative() || retention.isZero()) {
            return 0;
        }
        Path directory = Path.of(snapshotsDirectory).toAbsolutePath().normalize();
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        Instant cutoff = Instant.now().minus(retention);
        try (var files = Files.list(directory)) {
            List<Path> expired = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".zip"))
                    .filter(path -> isOlderThan(path, cutoff))
                    .sorted()
                    .toList();
            for (Path path : expired) {
                Files.deleteIfExists(path);
                log.info("Deleted expired snapshot {}", path);
            }
            return expired.size();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to clean snapshot directory " + directory, ex);
        }
    }

    private void dumpMainDatabase(Path dumpFile) throws IOException {
        JdbcTarget target = parseJdbcTarget(mainDataSourceProperties.getUrl());
        ProcessBuilder builder = new ProcessBuilder(List.of(
                "pg_dump",
                "--host", target.host(),
                "--port", String.valueOf(target.port()),
                "--username", mainDataSourceProperties.getUsername(),
                "--dbname", target.database(),
                "--clean",
                "--if-exists",
                "--no-owner",
                "--no-privileges",
                "--file", dumpFile.toString()
        ));
        builder.environment().put("PGPASSWORD", passwordOrEmpty(mainDataSourceProperties.getPassword()));
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output;
        try (InputStream stream = process.getInputStream()) {
            output = new String(stream.readAllBytes());
        }
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("pg_dump exited with code " + exitCode + (output.isBlank() ? "" : ": " + output.trim()));
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for pg_dump to finish", ex);
        }
    }

    private void writeMetadata(Path metadataPath) throws IOException {
        String metadata = """
                created_at=%s
                database_url=%s
                uploads_path=%s
                snapshot_directory=%s
                """.formatted(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                mainDataSourceProperties.getUrl(),
                uploadsPath,
                snapshotsDirectory
        );
        Files.writeString(metadataPath, metadata);
    }

    private void zipSnapshot(Path archivePath, Path databaseDump, Path metadataFile, Path uploadsDirectory) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archivePath))) {
            addFile(zip, databaseDump, "database.sql");
            addFile(zip, metadataFile, "snapshot-meta.txt");
            addDirectory(zip, uploadsDirectory, "uploads/");
        }
    }

    private void addFile(ZipOutputStream zip, Path file, String entryName) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zip.putNextEntry(entry);
        Files.copy(file, zip);
        zip.closeEntry();
    }

    private void addDirectory(ZipOutputStream zip, Path directory, String entryPrefix) throws IOException {
        if (directory == null || !Files.exists(directory)) {
            zip.putNextEntry(new ZipEntry(entryPrefix));
            zip.closeEntry();
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted().toList()) {
                if (path.equals(directory)) {
                    zip.putNextEntry(new ZipEntry(entryPrefix));
                    zip.closeEntry();
                    continue;
                }
                String relative = directory.relativize(path).toString().replace('\\', '/');
                String entryName = entryPrefix + relative + (Files.isDirectory(path) ? "/" : "");
                ZipEntry entry = new ZipEntry(entryName);
                zip.putNextEntry(entry);
                if (Files.isRegularFile(path)) {
                    Files.copy(path, zip);
                }
                zip.closeEntry();
            }
        }
    }

    private Path resolveUploadsPath() {
        return Path.of(uploadsPath).toAbsolutePath().normalize();
    }

    private Path snapshotArchivePath() {
        String filename = FILE_TIMESTAMP.format(LocalDateTime.now()) + ".zip";
        return Path.of(snapshotsDirectory).toAbsolutePath().normalize().resolve(filename);
    }

    private boolean isOlderThan(Path path, Instant cutoff) {
        try {
            FileTime modifiedTime = Files.getLastModifiedTime(path);
            return modifiedTime.toInstant().isBefore(cutoff);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read snapshot timestamp for " + path, ex);
        }
    }

    private JdbcTarget parseJdbcTarget(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException("Unsupported PostgreSQL JDBC URL: " + jdbcUrl);
        }
        try {
            URI uri = new URI(jdbcUrl.substring("jdbc:".length()));
            String path = uri.getPath();
            if (path == null || path.isBlank() || path.equals("/")) {
                throw new IllegalStateException("PostgreSQL JDBC URL does not contain a database name: " + jdbcUrl);
            }
            return new JdbcTarget(
                    uri.getHost(),
                    uri.getPort() > 0 ? uri.getPort() : 5432,
                    path.replaceFirst("^/", "")
            );
        } catch (URISyntaxException ex) {
            throw new IllegalStateException("Unable to parse PostgreSQL JDBC URL: " + jdbcUrl, ex);
        }
    }

    private String passwordOrEmpty(String password) {
        return password == null ? "" : password;
    }

    private void deleteQuietly(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ex) {
            log.warn("Unable to clean temporary snapshot directory {}", directory, ex);
        }
    }

    private record JdbcTarget(String host, int port, String database) {
    }
}
