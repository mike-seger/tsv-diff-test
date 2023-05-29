package com.net128.test;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
public class PathUtils {

	public static void deleteDirectory(Path path) {
		try {
			Files.walkFileTree(path, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					try { Files.delete(file); } catch (IOException e) { throw new RuntimeException(e); }
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
					try { Files.delete(dir); } catch (IOException e) { throw new RuntimeException(e); }
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (Exception e) { throw new RuntimeException(e); }
	}

	public static void copyDirectory(Path sourcePath, Path targetPath) throws IOException {
		Files.walk(sourcePath).forEach(source -> {
			try {
				var target = targetPath.resolve(sourcePath.relativize(source));
				if (Files.isDirectory(source)) {
					if (!Files.exists(target)) {
						Files.createDirectory(target);
					}
				} else {
					Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
				}
			} catch (IOException e) {
				throw new RuntimeException(e.getMessage(), e);
			}
		});
	}

	public static void unzipFile(InputStream inputStream, Path outputPath) throws IOException {
		try (ZipInputStream zis = new ZipInputStream(inputStream)) {
			ZipEntry zipEntry = zis.getNextEntry();
			while (zipEntry != null) {
				var newFilePath = outputPath.resolve(zipEntry.getName());
				if (zipEntry.isDirectory()) {
					Files.createDirectories(newFilePath);
				} else {
					Files.createDirectories(newFilePath.getParent());
					Files.copy(zis, newFilePath);
				}
				zipEntry = zis.getNextEntry();
			}
		}
	}

	public static void deleteDirectoryContents(Path path) throws IOException {
		Files.walkFileTree(path, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				if (!file.toString().contains(".git")) {
					Files.delete(file);
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				if (!dir.equals(path) && !dir.toString().contains(".git")) {
					Files.delete(dir);
				}
				return FileVisitResult.CONTINUE;
			}
		});
	}

	public static void cleanupTempDirs(Path ... tempDirs) {
		Stream.of(tempDirs).filter(Objects::nonNull).forEach(PathUtils::deleteDirectory);
	}
}
