package ru.ultimavox.itsm.knowledgebase.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.ultimavox.itsm.knowledgebase.application.KnowledgeCommands;
import ru.ultimavox.itsm.knowledgebase.application.KnowledgeQuery;
import ru.ultimavox.itsm.knowledgebase.application.RecordHelpfulnessVote;
import ru.ultimavox.itsm.platform.authorization.AccessControl;

@RestController
@RequestMapping("/api/v1/knowledge")
@Tag(name = "Knowledge Base")
class KnowledgeController {
  private final KnowledgeQuery query;
  private final KnowledgeCommands commands;
  private final RecordHelpfulnessVote vote;
  private final AccessControl access;

  KnowledgeController(
      KnowledgeQuery query,
      KnowledgeCommands commands,
      RecordHelpfulnessVote vote,
      AccessControl access
  ) {
    this.query = query;
    this.commands = commands;
    this.vote = vote;
    this.access = access;
  }

  @GetMapping("/articles")
  @Operation(summary = "List articles; publishedOnly=false for CMS (requires knowledge.write)")
  List<KnowledgeQuery.ArticleSummary> list(
      Authentication authentication,
      @RequestParam(required = false) @jakarta.validation.constraints.Size(max = 2000) String q,
      @RequestParam(required = false, defaultValue = "ru") String locale,
      @RequestParam(required = false, defaultValue = "true") boolean publishedOnly
  ) {
    String actor = authentication.getName();
    if (publishedOnly) {
      access.require(actor, "knowledge.read", "knowledge-article", null);
    } else {
      access.require(actor, "knowledge.write", "knowledge-article", null);
    }
    return query.search(q, locale, publishedOnly);
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

  @PostMapping("/articles")
  @Operation(summary = "Create a draft knowledge article")
  ResponseEntity<KnowledgeQuery.ArticleDetail> create(
      Authentication authentication,
      @Valid @RequestBody CreateArticleRequest body
  ) {
    access.require(authentication.getName(), "knowledge.write", "knowledge-article", null);
    try {
      KnowledgeQuery.ArticleDetail created = commands.create(
          new KnowledgeCommands.CreateCommand(
              body.title(),
              body.body(),
              body.summary(),
              body.slug(),
              body.locale()
          ),
          authentication.getName()
      );
      return ResponseEntity.status(HttpStatus.CREATED).body(created);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  @PutMapping("/articles/{id}")
  @Operation(summary = "Update article content (draft in place; published creates new draft version)")
  KnowledgeQuery.ArticleDetail update(
      Authentication authentication,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateArticleRequest body
  ) {
    access.require(authentication.getName(), "knowledge.write", "knowledge-article", id.toString());
    try {
      return commands.update(
          id,
          new KnowledgeCommands.UpdateCommand(
              body.title(),
              body.body(),
              body.summary(),
              body.locale(),
              body.versionNote()
          ),
          authentication.getName()
      );
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
    }
  }

  @PostMapping("/articles/{id}/publish")
  @Operation(summary = "Publish a draft or in-review article")
  KnowledgeQuery.ArticleDetail publish(Authentication authentication, @PathVariable UUID id) {
    access.require(authentication.getName(), "knowledge.write", "knowledge-article", id.toString());
    try {
      return commands.publish(id, authentication.getName());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
    } catch (IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    }
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

  @GetMapping("/articles/{id}/votes/me")
  @Operation(summary = "Read current user's vote for an article")
  VoteResponse readMyVote(
      Authentication authentication,
      @PathVariable UUID id
  ) {
    access.require(authentication.getName(), "knowledge.read", "knowledge-article", id.toString());
    Boolean helpful = vote.readMyVote(id, authentication.getName());
    return new VoteResponse(helpful);
  }

  record VoteResponse(Boolean helpful) {}

  record VoteRequest(@NotNull Boolean helpful, @Size(max = 2000) String comment) {}

  record CreateArticleRequest(
      @NotBlank @Size(max = 400) String title,
      @NotBlank @Size(max = 100000) String body,
      @Size(max = 2000) String summary,
      @Size(max = 200) String slug,
      @Size(max = 35) String locale
  ) {}

  record UpdateArticleRequest(
      @Size(max = 400) String title,
      @Size(max = 100000) String body,
      @Size(max = 2000) String summary,
      @Size(max = 35) String locale,
      @Size(max = 2000) String versionNote
  ) {}
}
