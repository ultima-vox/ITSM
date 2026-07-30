package ru.ultimavox.itsm.servicecatalog.domain;
import java.util.*;
/** Localized service offering; fulfillment is delegated to a metadata workflow after eligibility is evaluated. */
public record CatalogItem(UUID id, String key, Status status, UUID formDefinitionId, UUID workflowDefinitionId, Map<String,Translation> translations, List<EligibilityRule> eligibilityRules) {
 public CatalogItem { translations=Map.copyOf(translations); eligibilityRules=List.copyOf(eligibilityRules); }
 public CatalogItem publish(){if(status!=Status.DRAFT&&status!=Status.RETIRED)throw new IllegalStateException("Only draft or retired catalog items may be published"); Translation russian=translations.get("ru");if(russian==null||russian.name().isBlank()||russian.description().isBlank())throw new IllegalStateException("Russian catalog content is required");if(formDefinitionId==null||workflowDefinitionId==null)throw new IllegalStateException("A form and workflow are required");return new CatalogItem(id,key,Status.PUBLISHED,formDefinitionId,workflowDefinitionId,translations,eligibilityRules);}
 public CatalogItem retire(){if(status!=Status.PUBLISHED)throw new IllegalStateException("Only published catalog items may be retired");return new CatalogItem(id,key,Status.RETIRED,formDefinitionId,workflowDefinitionId,translations,eligibilityRules);}
 public record Translation(String name,String description,String category) { public Translation { Objects.requireNonNull(name);Objects.requireNonNull(description); } }
 public record EligibilityRule(String expression, String denialMessageKey) {} public enum Status { DRAFT, PUBLISHED, RETIRED }
}
