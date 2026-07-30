package ru.ultimavox.itsm.knowledgebase.domain;
import java.time.Instant; import java.util.*;
/** Versioned knowledge aggregate. Publishing requires complete content in the primary Russian locale. */
public record KnowledgeArticle(UUID id, String number, Status status, int version, String ownerSubject, Map<String,Content> translations, Instant nextReviewAt) {
 public KnowledgeArticle { translations=Map.copyOf(translations); }
 public KnowledgeArticle submitForReview(){requireStatus(Status.DRAFT);return copy(Status.IN_REVIEW,version,nextReviewAt);}
 public KnowledgeArticle publish(Instant reviewAt){if(status!=Status.IN_REVIEW)throw new IllegalStateException("Only reviewed articles can be published"); Content russian=translations.get("ru");if(russian==null||russian.title().isBlank()||russian.body().isBlank())throw new IllegalStateException("Primary Russian content is required before publication");return copy(Status.PUBLISHED,version+1,Objects.requireNonNull(reviewAt));}
 public KnowledgeArticle archive(){if(status!=Status.PUBLISHED)throw new IllegalStateException("Only published articles can be archived");return copy(Status.ARCHIVED,version,nextReviewAt);}
 private void requireStatus(Status expected){if(status!=expected)throw new IllegalStateException("Expected status "+expected);}
 private KnowledgeArticle copy(Status next,int nextVersion,Instant reviewAt){return new KnowledgeArticle(id,number,next,nextVersion,ownerSubject,translations,reviewAt);}
 public record Content(String title,String body,String summary) { public Content { Objects.requireNonNull(title);Objects.requireNonNull(body); } }
 public enum Status { DRAFT, IN_REVIEW, PUBLISHED, ARCHIVED }
}
