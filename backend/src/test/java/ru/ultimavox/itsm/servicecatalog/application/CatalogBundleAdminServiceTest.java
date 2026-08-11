package ru.ultimavox.itsm.servicecatalog.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.ultimavox.itsm.platform.audit.AuditTrail;

@ExtendWith(MockitoExtension.class)
class CatalogBundleAdminServiceTest {
  @Mock JdbcTemplate jdbc;
  @Mock AuditTrail audit;
  private final UUID bundle=UUID.randomUUID();

  @Test void rejectsSelfReferenceWithoutMutation() {
    when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(bundle),eq("default"))).thenReturn(1);
    assertThatThrownBy(() -> service().replace(bundle,List.of(new CatalogBundleAdminService.Component(bundle,1,0)),"admin"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("itself");
    verify(jdbc,never()).update(anyString(),any(Object[].class)); verify(audit,never()).append(any());
  }

  @Test void rejectsDuplicateWithoutMutation() {
    UUID item=UUID.randomUUID();
    when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(bundle),eq("default"))).thenReturn(1);
    var component=new CatalogBundleAdminService.Component(item,1,0);
    assertThatThrownBy(() -> service().replace(bundle,List.of(component,component),"admin"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate");
    verify(jdbc,never()).update(anyString(),any(Object[].class));
  }

  @Test void rejectsTransitiveCycleWithoutMutation() {
    UUID item=UUID.randomUUID();
    when(jdbc.queryForObject(anyString(),eq(Integer.class),any(),eq("default"))).thenReturn(1);
    when(jdbc.queryForObject(anyString(),eq(Boolean.class),eq(item),eq("default"),eq("default"),eq(bundle))).thenReturn(true);
    assertThatThrownBy(() -> service().replace(bundle,List.of(new CatalogBundleAdminService.Component(item,1,0)),"admin"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cycle");
    verify(jdbc,never()).update(anyString(),any(Object[].class));
  }
  private CatalogBundleAdminService service(){return new CatalogBundleAdminService(jdbc,audit);}
}
