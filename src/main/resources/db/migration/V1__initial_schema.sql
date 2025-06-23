CREATE SCHEMA IF NOT EXISTS document_schema;

SET SCHEMA 'document_schema';

CREATE TABLE tag (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL
);


CREATE TABLE document (
    id SERIAL PRIMARY KEY,
    user_name VARCHAR(255) NOT NULL,
    document_name VARCHAR(255) NOT NULL,
    minio_path TEXT NOT NULL,
    file_size BIGINT NOT NULL,
    file_type VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE document_tags (
    document_id INT NOT NULL,
    tag_id INT NOT NULL,
    PRIMARY KEY (document_id, tag_id),
    FOREIGN KEY (document_id) REFERENCES document(id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES tag(id) ON DELETE CASCADE
);


CREATE INDEX idx_document_user_id ON document(user_name);

CREATE INDEX idx_document_document_name ON document(document_name);

CREATE INDEX idx_document_tags_tag_id ON document_tags(tag_id);

CREATE INDEX idx_document_tags_document_id ON document_tags(document_id);
