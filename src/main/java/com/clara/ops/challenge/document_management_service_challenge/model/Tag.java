package com.clara.ops.challenge.document_management_service_challenge.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Entity
@Table(name = "tag")
@RequiredArgsConstructor
@NoArgsConstructor
@Getter
public class Tag {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @NonNull @Column(unique = true, nullable = false)
  private String name;
}
