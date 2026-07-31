package ru.ultimavox.itsm.knowledgebase.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.ultimavox.itsm.knowledgebase.application.KnowledgeQuery;
import ru.ultimavox.itsm.knowledgebase.application.RecordHelpfulnessVote;
import ru.ultimavox.itsm.platform.authorization.AccessControl;

@RestController
@RequestMapping("/api/v1/knowledge")
@Tag(name = "Knowledge Base")
class KnowledgeController {
  private final KnowledgeQuery query;
  private final RecordHelpfulnessVote vote;
  private final AccessControl access;

  KnowledgeController(KnowledgeQuery query, RecordHelpfulnessVote vote, AccessControl access) {
    this.query = query;
    this.vote = vote;
    this.access = access;
  }

  @GetMapping("/articles")
  @Operation(summary = "List published articles; optional title search")
  List<KnowledgeQuery.ArticleSummary> list(
      Authentication authentication,
      @RequestParam(required = false) String q,
      @RequestParam(required = false, defaultValue = "ru") String locale
  ) {
    access.require(authentication.getName(), "knowledge.read", "knowledge-article", null);
    return query.searchPublished(q, locale);
  }

  @GetMapping("/articles/{idOrSlug}")
  @Operation(summary = "Get article by id or slug")
  KnowledgeQuery.ArticleDetail get(
      Authentication authentication,
      @PathVariable String idOrSlug,
      @RequestParam(required = false, defaultValue = "ru") String locale
  ) {
    access.require(authentication.getName(), "knowledge.read", "knowledge-article", idOrSlug);
    return query.findByIdOrSlug(idOrSlug, locale)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found"));
  }

  @PostMapping("/articles/{id}/votes")
  @Operation(summary = "Record a helpfulness vote for an article revision")
  RecordHelpfulnessVote.Voted vote(
      Authentication authentication,
      @PathVariable UUID id,
      @Valid @RequestBody VoteRequest body
  ) {
    access.require(authentication.getName(), "knowledge.vote", "knowledge-article", id.toString());
    try {
      return vote.vote(id, Boolean.TRUE.equals(body.helpful()), body.comment(), authentication.getName());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
    } catch (IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    }
  }

  record VoteRequest(@NotNull Boolean helpful, @Size(max = 2000) String comment) {}
}
