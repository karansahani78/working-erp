package com.educationerp.document.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Documents on the server's own disk.
 *
 * <p>The right answer for a single-school installation and an honest one for a first run: the
 * layout below the root is the same key a network store would be given, so moving to one later
 * is a change of provider rather than a migration.
 *
 * <p>Every key is resolved and then checked to be inside the root before anything is opened.
 * That is not paranoia about our own generated keys; it is the guarantee that a key which has
 * somehow come from outside cannot name a file outside the directory.
 */
@Component
@ConditionalOnProperty(name = "erp.storage.provider", havingValue = "LOCAL", matchIfMissing = true)
public class LocalObjectStorage implements ObjectStorage {

    private static final Logger LOG = LoggerFactory.getLogger(LocalObjectStorage.class);

    private final Path root;

    public LocalObjectStorage(StorageProperties properties) {
        this.root = Path.of(properties.getLocalRoot()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create the document store at " + root, e);
        }
        // Said out loud, because the root is relative by default and a jar started from a
        // different directory will quietly read and write an empty store of its own.
        LOG.info("Documents are stored on local disk at {}", root);
    }

    @Override
    public String provider() {
        return StorageProperties.LOCAL;
    }

    @Override
    public void put(String key, InputStream content) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        // Written beside the target and moved into place, so a failure halfway cannot leave a
        // half-written document that looks complete.
        Path temporary = Files.createTempFile(target.getParent(), ".upload-", ".part");
        try {
            try (OutputStream out = Files.newOutputStream(temporary)) {
                content.transferTo(out);
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temporary);
            throw e;
        }
    }

    @Override
    public InputStream open(String key) throws IOException {
        Path target = resolve(key);
        if (!Files.exists(target)) {
            throw new IOException("No stored file at " + key);
        }
        return Files.newInputStream(target);
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolve(key));
    }

    @Override
    public long size(String key) {
        try {
            return Files.size(resolve(key));
        } catch (IOException e) {
            return 0L;
        }
    }

    /** The key as a path, refused if it points anywhere outside the root. */
    private Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Refusing a storage key that escapes the store: " + key);
        }
        return target;
    }
}
