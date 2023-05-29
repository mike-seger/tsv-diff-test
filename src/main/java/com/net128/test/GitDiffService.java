package com.net128.test;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

import static com.net128.test.PathUtils.*;

@Service
@Slf4j
public class GitDiffService {
	public GitDiffService(@Value("${com.net128.test.cleanup:true}") boolean cleanup) {
		this.cleanup = cleanup;
	}
	private final boolean cleanup;

	public String performGitDiff(String id1, String id2, int contextLines) throws IOException {
		return performGitDiff(getClass().getResourceAsStream("/static/data/data"+id1+".zip"), getClass().getResourceAsStream("/static/data/data"+id2+".zip"), contextLines);
	}

	public String performGitDiff(InputStream input1, InputStream input2, int contextLines) throws IOException {
		Path tempDir1 = null;
		Path tempDir2 = null;
		try {
			tempDir1 = Files.createTempDirectory("file1");
			tempDir2 = Files.createTempDirectory("file2");
			unzipFile(input1, tempDir1);
			unzipFile(input2, tempDir2);
			return performGitDiffOnFS(tempDir1, tempDir2, contextLines);
		} finally { cleanupDirs(tempDir1, tempDir2); }
	}

	private String performGitDiffOnFS(Path tempDir1, Path tempDir2, int contextLines) {
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
					var oldTreeParser = new CanonicalTreeParser();
					oldTreeParser.reset(reader, oldHead);
					var newTreeParser = new CanonicalTreeParser();
					newTreeParser.reset(reader, head);

					var diffs = git.diff().setNewTree(newTreeParser).setOldTree(oldTreeParser).call();
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
			log.error("Failed to perform git diff", e);
			return null;
		} finally { cleanupDirs(gitDir); }
	}

	private void cleanupDirs(Path ... dirs) {
		if (cleanup) cleanupTempDirs(dirs);
		else log.info("Not cleaning up: "+List.of(dirs));
	}
}
