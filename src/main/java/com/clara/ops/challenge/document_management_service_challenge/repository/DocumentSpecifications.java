package com.clara.ops.challenge.document_management_service_challenge.repository;

import com.clara.ops.challenge.document_management_service_challenge.model.Document;
import com.clara.ops.challenge.document_management_service_challenge.model.Tag;
import jakarta.persistence.criteria.Join;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;

public class DocumentSpecifications {
  public static Specification<Document> userEquals(String user) {
    return (root, query, cb) -> user == null ? null : cb.equal(root.get("user"), user);
  }

  public static Specification<Document> documentNameEquals(String documentName) {
    return (root, query, cb) ->
        documentName == null ? null : cb.equal(root.get("documentName"), documentName);
  }

  public static Specification<Document> nameLike(String name) {
    return (root, query, cb) ->
        name == null
            ? null
            : cb.like(cb.lower(root.get("documentName")), "%" + name.toLowerCase() + "%");
  }

  public static Specification<Document> hasAnyTag(Set<String> tagNames) {
    return (root, query, cb) -> {
      if (tagNames == null || tagNames.isEmpty()) return null;
      Join<Document, Tag> tagJoin = root.join("tags");
      return tagJoin.get("name").in(tagNames);
    };
  }
}
