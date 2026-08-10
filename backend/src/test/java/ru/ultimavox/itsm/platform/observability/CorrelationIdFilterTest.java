package ru.ultimavox.itsm.platform.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {
  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  @Test
  void propagatesValidCorrelationIdAndClearsThreadContext() throws Exception {
    UUID expected = UUID.randomUUID();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.HEADER, expected.toString());
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<UUID> observed = new AtomicReference<>();

    filter.doFilter(request, response, (req, res) -> {
      observed.set(CorrelationContext.current().orElseThrow());
      assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo(expected.toString());
    });

    assertThat(observed.get()).isEqualTo(expected);
    assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(expected.toString());
    assertThat(CorrelationContext.current()).isEmpty();
    assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
  }

  @Test
  void replacesMalformedCorrelationId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.HEADER, "not-a-uuid");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {});

    assertThat(UUID.fromString(response.getHeader(CorrelationIdFilter.HEADER))).isNotNull();
  }
}
