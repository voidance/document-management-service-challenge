package com.clara.ops.challenge.document_management_service_challenge.repository;

import com.clara.ops.challenge.document_management_service_challenge.model.Tag;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Integer> {

  List<Tag> findAllByNameIn(Collection<String> names);
}
