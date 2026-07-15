package com.sanaiddalgi.hub.blog.service;

import com.sanaiddalgi.hub.config.StudioProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** 웹 업로드 사진을 output/web_uploads/current/ 에 카테고리별 저장. */
@Component
public class MediaService {

    private final StudioProperties properties;

    public MediaService(StudioProperties properties) {
        this.properties = properties;
    }

    public Map<String, List<String>> saveUploadedPhotos(Map<String, List<MultipartFile>> uploads) throws IOException {
        Path sessionDir = clearUploadDir();
        Map<String, List<String>> photoData = new LinkedHashMap<>();
        for (String category : StudioProperties.PHOTO_CATEGORIES) {
            photoData.put(category, new ArrayList<>());
            List<MultipartFile> files = uploads.getOrDefault(category, List.of());
            Path targetDir = sessionDir.resolve(category);
            Files.createDirectories(targetDir);
            int index = 1;
            for (MultipartFile file : files) {
                if (file.isEmpty()) {
                    continue;
                }
                String original = file.getOriginalFilename();
                String ext = ".jpg";
                if (original != null && original.contains(".")) {
                    ext = original.substring(original.lastIndexOf('.')).toLowerCase();
                }
                Path path = targetDir.resolve(String.format("%02d%s", index, ext)).toAbsolutePath();
                Files.createDirectories(path.getParent());
                file.transferTo(path);
                photoData.get(category).add(path.toAbsolutePath().toString());
                index++;
            }
        }
        return photoData;
    }


    /** 이전 업로드 세션 삭제 후 새 current/ 디렉터리 준비. */
    public Path clearUploadDir() throws IOException {
        Path sessionDir = Path.of(properties.getWebUploadDir(), "current");
        if (Files.exists(sessionDir)) {
            try (Stream<Path> walk = Files.walk(sessionDir)) {
                walk.sorted((a, b) -> b.compareTo(a))
                        .filter(p -> !p.equals(sessionDir))
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                                // best effort
                            }
                        });
            }
        }
        for (String category : StudioProperties.PHOTO_CATEGORIES) {
            Files.createDirectories(sessionDir.resolve(category));
        }
        return sessionDir;
    }

    public int countPhotos(Map<String, List<String>> photoData) {
        return photoData.values().stream().mapToInt(List::size).sum();
    }

    public String photoMimeType(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }
}
