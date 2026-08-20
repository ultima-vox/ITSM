package ru.ultimavox.itsm.platform.storage.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AttachmentContentDispositionTest {

  @Test
  void ordinaryFilenamePassesThrough() {
    assertThat(AttachmentController.contentDisposition("quarterly report.pdf"))
        .isEqualTo("attachment; filename=\"quarterly report.pdf\"");
  }

  @Test
  void controlCharactersCannotInjectHeaderLines() {
    String injected = "report\r\nSet-Cookie: session=stolen.pdf";

    String header = AttachmentController.contentDisposition(injected);

    assertThat(header).doesNotContain("\r").doesNotContain("\n");
    assertThat(header)
        .isEqualTo("attachment; filename=\"report__Set-Cookie: session=stolen.pdf\"");
  }

  @Test
  void aNulByteIsReplacedRatherThanBreakingThePattern() {
    String withNul = "report" + (char) 0 + ".pdf";

    assertThat(AttachmentController.contentDisposition(withNul))
        .isEqualTo("attachment; filename=\"report_.pdf\"");
  }

  @Test
  void quotesCannotCloseTheHeaderValue() {
    assertThat(AttachmentController.contentDisposition("a\"b.txt"))
        .isEqualTo("attachment; filename=\"ab.txt\"");
  }

  @Test
  void emptyOrMissingNamesFallBack() {
    assertThat(AttachmentController.contentDisposition(null))
        .isEqualTo("attachment; filename=\"file\"");
    assertThat(AttachmentController.contentDisposition("   "))
        .isEqualTo("attachment; filename=\"file\"");
  }
}
