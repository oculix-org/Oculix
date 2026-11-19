/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;

/** Save a complete workspace copy, publishing it only once every file and open buffer is written. */
public final class WorkspaceSnapshot {
  private WorkspaceSnapshot() { }

  public static Path saveAs(Path source, Path destination, Map<Path, String> openBuffers) throws IOException {
    Path root = source.toRealPath();
    Path target = destination.toAbsolutePath().normalize();
    if (!Files.isDirectory(root)) throw new NotDirectoryException(root.toString());
    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException(target.toString());
    Path parent = target.getParent();
    if (parent == null || !Files.isDirectory(parent)) throw new NotDirectoryException(String.valueOf(parent));
    if (parent.toRealPath().startsWith(root)) throw new IOException("The destination must be outside the source workspace.");
    Path stage = Files.createTempDirectory(parent, ".oculix-save-");
    try {
      Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
        @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
          Path relative = root.relativize(dir);
          if (!relative.toString().isEmpty()) Files.createDirectory(stage.resolve(relative));
          return FileVisitResult.CONTINUE;
        }
        @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Files.copy(file, stage.resolve(root.relativize(file)), LinkOption.NOFOLLOW_LINKS,
              StandardCopyOption.COPY_ATTRIBUTES);
          return FileVisitResult.CONTINUE;
        }
      });
      for (Map.Entry<Path, String> buffer : openBuffers.entrySet()) {
        Path relative = buffer.getKey();
        Path file = stage.resolve(relative).normalize();
        if (relative.isAbsolute() || !file.startsWith(stage) || file.equals(stage)) {
          throw new IOException("Open buffer is outside the workspace: " + relative);
        }
        for (Path dir = file.getParent(); dir != null && !dir.equals(stage); dir = dir.getParent()) {
          if (Files.isSymbolicLink(dir)) throw new IOException("Open buffer is inside a linked directory: " + relative);
        }
        if (Files.isSymbolicLink(file)) Files.delete(file); // Write only inside our private staging folder.
        Files.writeString(file, buffer.getValue(), StandardCharsets.UTF_8);
      }
      return Files.move(stage, target); // Never replace an existing destination.
    } finally {
      if (Files.exists(stage)) {
        Files.walkFileTree(stage, new SimpleFileVisitor<Path>() {
          @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.delete(file); return FileVisitResult.CONTINUE;
          }
          @Override public FileVisitResult postVisitDirectory(Path dir, IOException ex) throws IOException {
            if (ex != null) throw ex;
            Files.delete(dir); return FileVisitResult.CONTINUE;
          }
        });
      }
    }
  }
}
