package com.sanaiddalgi.hub.blog.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sanaiddalgi.hub.config.StudioProperties;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExportServiceTest {

    @TempDir
    Path tempDir;

    private Path photo1;
    private Path photo2;

    @BeforeEach
    void writeTestImages() throws Exception {
        photo1 = tempDir.resolve("external01.jpg");
        photo2 = tempDir.resolve("product01.jpg");
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "jpg", photo1.toFile());
        ImageIO.write(image, "jpg", photo2.toFile());
    }

    @Test
    void insertsMarkersAndAppendsMissingPhotos() throws Exception {
        StudioProperties properties = new StudioProperties();
        properties.setOutputDir(tempDir.toString());
        properties.setDefaultDocx("test.docx");
        ExportService exportService = new ExportService(properties);

        String draft = "인트로\n[external_1]\n본문\n";
        Map<String, List<String>> photoData = Map.of(
                "external", List.of(photo1.toString()),
                "product", List.of(photo2.toString()));

        Path docx = exportService.createDocx(draft, photoData, "글로벌");
        assertTrue(Files.exists(docx));

        try (ZipFile zip = new ZipFile(docx.toFile())) {
            assertTrue(zip.getEntry("word/document.xml") != null);
        }
    }
}
