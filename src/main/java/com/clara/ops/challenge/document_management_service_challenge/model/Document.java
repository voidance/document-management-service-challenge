package com.clara.ops.challenge.document_management_service_challenge.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@Getter
@Table(name = "document")
public class Document {

  @PrePersist
  public void prePersist() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "user_name", nullable = false)
  private String user;

  @Column(name = "document_name", nullable = false)
  private String documentName;

  @Column(name = "minio_path", nullable = false, columnDefinition = "TEXT")
  private String minioPath;

  @Column(name = "file_size", nullable = false)
  private Long fileSize;

  @Column(name = "file_type")
  private String fileType;

  @Column(name = "created_at", updatable = false, nullable = false)
  private Instant createdAt;

  @ManyToMany
  @JoinTable(
      name = "document_tags",
      joinColumns = @JoinColumn(name = "document_id"),
      inverseJoinColumns = @JoinColumn(name = "tag_id"))
  private Set<Tag> tags = new HashSet<>();

  public Document(
      String user,
      String documentName,
      String minioPath,
      Long fileSize,
      String fileType,
      Set<Tag> tags) {
    this.user = user;
    this.documentName = documentName;
    this.minioPath = minioPath;
    this.fileSize = fileSize;
    this.fileType = fileType;
    this.tags = tags != null ? tags : new HashSet<>();
  }
}
