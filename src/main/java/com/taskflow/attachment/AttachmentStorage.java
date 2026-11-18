package com.taskflow.attachment;

import com.taskflow.common.error.BadRequestException;
import com.taskflow.common.error.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/** Local filesystem storage for task attachments. */
@Component
public class AttachmentStorage {

    private static final Logger log = LoggerFactory.getLogger(AttachmentStorage.class);

    private final Path root;
    private final com.taskflow.config.StorageProperties properties;

    public AttachmentStorage(com.taskflow.config.StorageProperties properties) {
        this.properties = properties;
        this.root = Paths.get(properties.uploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not create upload directory " + root, ex);
        }
    }

    public void store(MultipartFile file, String storedName) {
        Path target = resolve(storedName);
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store attachment", ex);
        }
    }

    public Resource load(String storedName) {
        Path path = resolve(storedName);
        if (!Files.isRegularFile(path)) {
            throw NotFoundException.of("Attachment file", storedName);
        }
        try {
            return new UrlResource(path.toUri());
        } catch (MalformedURLException ex) {
            throw new IllegalStateException("Invalid attachment path", ex);
        }
    }

    public void deleteQuietly(String storedName) {
        if (storedName == null || storedName.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(storedName));
        } catch (IOException ex) {
            log.warn("Could not delete attachment file {}: {}", storedName, ex.getMessage());
        }
    }

    public long maxFileSizeBytes() {
        return properties.maxFileSizeBytes();
    }

    public java.util.List<String> allowedContentTypes() {
        return properties.allowedContentTypes();
    }

    private Path resolve(String storedName) {
        Path path = root.resolve(storedName).normalize();
        if (!path.startsWith(root)) {
            throw new BadRequestException("Invalid attachment reference");
        }
        return path;
    }
}
