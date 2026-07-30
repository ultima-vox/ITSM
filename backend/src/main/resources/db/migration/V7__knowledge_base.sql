CREATE TABLE knowledge_article (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), number varchar(32) NOT NULL UNIQUE, status varchar(30) NOT NULL,
 version integer NOT NULL, owner_subject varchar(128) NOT NULL, next_review_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX knowledge_article_status_review_idx ON knowledge_article(status, next_review_at);
CREATE TABLE knowledge_article_revision (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), article_id uuid NOT NULL REFERENCES knowledge_article(id), version integer NOT NULL,
 locale varchar(35) NOT NULL, title varchar(400) NOT NULL, summary text, body text NOT NULL, author_subject varchar(128) NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(article_id, version, locale)
);
CREATE INDEX knowledge_article_revision_locale_idx ON knowledge_article_revision(locale, article_id, version DESC);
CREATE TABLE knowledge_feedback (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), article_id uuid NOT NULL REFERENCES knowledge_article(id), revision integer NOT NULL, subject_id varchar(128), helpful boolean NOT NULL, comment text, created_at timestamptz NOT NULL DEFAULT now());
