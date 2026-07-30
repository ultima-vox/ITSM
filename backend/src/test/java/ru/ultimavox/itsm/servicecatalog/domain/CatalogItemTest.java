package ru.ultimavox.itsm.servicecatalog.domain;
import static org.assertj.core.api.Assertions.*; import java.util.*; import org.junit.jupiter.api.Test;
class CatalogItemTest { private CatalogItem draft(Map<String,CatalogItem.Translation> text){return new CatalogItem(UUID.randomUUID(),"access-sap",CatalogItem.Status.DRAFT,UUID.randomUUID(),UUID.randomUUID(),text,List.of(new CatalogItem.EligibilityRule("subject.department == 'Finance'","catalog.access-sap.not-eligible")));}
 @Test void publishing_requires_russian_content(){assertThatThrownBy(()->draft(Map.of("en",new CatalogItem.Translation("SAP access","Request access","Access"))).publish()).hasMessageContaining("Russian");}
 @Test void publishes_a_complete_offering(){assertThat(draft(Map.of("ru",new CatalogItem.Translation("Доступ к SAP","Получить доступ","Доступы"))).publish().status()).isEqualTo(CatalogItem.Status.PUBLISHED);}
}
