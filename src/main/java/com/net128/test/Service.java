package com.net128.test;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.springframework.beans.factory.annotation.Value;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@org.springframework.stereotype.Service
public class Service {
	public Service(@Value("${com.net128.test.cleanup:true}") boolean cleanup) {
		this.cleanup = cleanup;
	}
	private final boolean cleanup;

	public String performGitDiff(String id1, String id2, int contextLines) throws IOException {
		var tempDir1 = Files.createTempDirectory("file1");
		var tempDir2 = Files.createTempDirectory("file2");
		try {
			unzipFile(getClass().getResourceAsStream("/static/data/data"+id1+".zip"), tempDir1);
			unzipFile(getClass().getResourceAsStream("/static/data/data"+id2+".zip"), tempDir2);
			return performGitDiffOnFS(tempDir1, tempDir2, contextLines);
		} finally {
			if(cleanup) {
				deleteDirectory(tempDir1);
				deleteDirectory(tempDir2);
			}
		}
	}

	public String performGitDiff(InputStream input1, InputStream input2, int contextLines) throws IOException {
		var tempDir1 = Files.createTempDirectory("file1");
		var tempDir2 = Files.createTempDirectory("file2");
		try {
			unzipFile(input1, tempDir1);
			unzipFile(input2, tempDir2);
			return performGitDiffOnFS(tempDir1, tempDir2, contextLines);
		} finally {
			if(cleanup) {
				deleteDirectory(tempDir1);
				deleteDirectory(tempDir2);
			}
		}
	}

	public String performGitDiffOnFS(Path tempDir1, Path tempDir2, int contextLines) {
		Path gitDir = null;
		String diffResult;
		try {
			gitDir = Files.createTempDirectory("gitDir");
			try(var git = Git.init().setDirectory(gitDir.toFile()).call()) {
				copyDirectory(tempDir1, gitDir);
				git.add().addFilepattern(".").call();
				git.commit().setMessage("First commit").call();

				var oldHead = git.getRepository().resolve("HEAD^{tree}");
				deleteDirectoryContents(gitDir);
				copyDirectory(tempDir2, gitDir);

				git.add().addFilepattern(".").call();
				git.commit().setMessage("Second commit").call();

				var head = git.getRepository().resolve("HEAD^{tree}");

				try (var reader = git.getRepository().newObjectReader()) {
					var oldTreeIter = new CanonicalTreeParser();
					oldTreeIter.reset(reader, oldHead);
					var newTreeIter = new CanonicalTreeParser();
					newTreeIter.reset(reader, head);

					var diffs = git.diff().setNewTree(newTreeIter).setOldTree(oldTreeIter).call();
					var out = new ByteArrayOutputStream();
					try (var formatter = new DiffFormatter(out)) {
						formatter.setRepository(git.getRepository());
						formatter.setContext(contextLines);
						for (var diffEntry : diffs) formatter.format(diffEntry);
						diffResult = out.toString(StandardCharsets.UTF_8);
					}
				}
				return diffResult;
			}
		} catch (IOException | GitAPIException e) {
			e.printStackTrace();
			if(cleanup) {
				try { if (gitDir != null) deleteDirectory(gitDir); }
				catch (IOException e2) { e2.printStackTrace(); }
			}
			return null;
		}
	}

	private static void deleteDirectory(Path path) throws IOException {
		Files.walkFileTree(path, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static void copyDirectory(Path sourcePath, Path targetPath) throws IOException {
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

	private static void unzipFile(InputStream inputStream, Path outputPath) throws IOException {
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

	private static void deleteDirectoryContents(Path path) throws IOException {
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
}
