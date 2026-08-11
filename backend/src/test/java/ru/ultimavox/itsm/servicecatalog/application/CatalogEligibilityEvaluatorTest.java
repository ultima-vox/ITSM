package ru.ultimavox.itsm.servicecatalog.application;
import static org.assertj.core.api.Assertions.assertThat; import java.util.List; import java.util.Map; import org.junit.jupiter.api.Test;
class CatalogEligibilityEvaluatorTest {
 private final CatalogEligibilityEvaluator evaluator=new CatalogEligibilityEvaluator();
 @Test void matchesTrustedSubjectAndFormEquality(){assertThat(evaluator.matches("subject.department == 'Finance'",Map.of("department","Finance"),Map.of())).isTrue();assertThat(evaluator.matches("form.region == 'EU'",Map.of(),Map.of("region","EU"))).isTrue();}
 @Test void collectionMatchSupportsRoles(){assertThat(evaluator.matches("subject.roles == 'ADMIN'",Map.of("roles",List.of("USER","ADMIN")),Map.of())).isTrue();}
 @Test void inequalityFailsWhenEqual(){assertThat(evaluator.matches("subject.country != 'RU'",Map.of("country","RU"),Map.of())).isFalse();}
 @Test void unknownOrExecutableSyntaxDeniesClosed(){assertThat(evaluator.matches("true",Map.of(),Map.of())).isFalse();assertThat(evaluator.matches("T(java.lang.Runtime).exec('x')",Map.of(),Map.of())).isFalse();assertThat(evaluator.matches("",Map.of(),Map.of())).isFalse();}
}
